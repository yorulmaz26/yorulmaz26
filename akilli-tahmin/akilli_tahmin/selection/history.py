"""Geçmiş tahmin ve aday deposu (geçmiş zaman doğruluğuyla).

Kalibrasyon, MODEL B eğitimi, karar birleştiricisi ve mod eşiklerinin kronolojik ayarı bu depodan
beslenir. Her kayıt, üretildiği zaman kesimini (`data_cutoff_at`) ve sonucun bilindiği anı
(`known_at`) taşır; `view(cutoff)` yalnızca ikisi de kesimden önce olan satırları döndürür.
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass, field

import numpy as np

from ..db.database import loads
from ..domain.markets import MatchResult, settle
from ..models.model_b import INDEX
from ..models.score_grid import G, outcome_index

K = len(INDEX.keys)
QUANTILES = (0.1, 0.2, 0.3, 0.4, 0.5, 0.9)
#: Mod eşik ayarında denenen tek yönlü güven düzeyleri (→ alt yüzdelik 1 − düzey).
CONFIDENCE_LEVELS = (0.7, 0.8, 0.9)


def level_to_q_index(level: float) -> int:
    return QUANTILES.index(round(1 - level, 1))


def outcome_vector(result: MatchResult) -> np.ndarray:
    """(K,) sonuç: 1 kazandı, 0 kaybetti, NaN iade/sonuçlanamadı."""
    y = np.full(K, np.nan)
    if result.status != "finished" or result.ft_home is None:
        return y
    hh = result.ht_home if result.ht_home is not None else None
    ha = result.ht_away if result.ht_away is not None else None
    if hh is not None and ha is not None:
        h2, a2 = result.ft_home - hh, result.ft_away - ha
        if max(hh, ha, h2, a2) < G and min(h2, a2) >= 0:
            idx = ((hh * G + ha) * G + h2) * G + a2
            _, win, void = outcome_index()
            y = np.where(void[:, idx] > 0, np.nan, win[:, idx])
            return y
    for i, (m, o) in enumerate(INDEX.keys):
        s = settle(m, o, result)
        y[i] = 1.0 if s == "won" else 0.0 if s == "lost" else np.nan
    return y


@dataclass
class FixtureRecord:
    fixture_id: int
    league_id: str
    kickoff_at: str
    data_cutoff_at: str
    pa_raw: np.ndarray
    pb_raw: np.ndarray
    pa_cal: np.ndarray
    pb_cal: np.ndarray
    fair: np.ndarray
    signal: np.ndarray
    p_est: np.ndarray
    p_void: np.ndarray
    p_q: np.ndarray            # (len(QUANTILES) × K) tahmini olasılık yüzdelikleri
    width: np.ndarray
    dis: np.ndarray
    market_dev: np.ndarray
    ev_a: np.ndarray
    ev_b: np.ndarray
    odds: np.ndarray
    robust_down: np.ndarray
    core_pass: np.ndarray
    odds_valid: np.ndarray
    y: np.ndarray | None = None
    known_at: str | None = None


@dataclass
class HistoryView:
    cutoff: str
    records: list[FixtureRecord]

    def stacked(self, attr: str) -> np.ndarray:
        if not self.records:
            return np.empty((0,))
        return np.concatenate([getattr(r, attr) for r in self.records])

    def keys_family(self) -> np.ndarray:
        return np.tile(INDEX.family, len(self.records))

    def fixture_ids(self) -> np.ndarray:
        return np.repeat([r.fixture_id for r in self.records], K)

    def kickoffs(self) -> np.ndarray:
        return np.repeat([r.kickoff_at for r in self.records], K)

    def cutoffs(self) -> np.ndarray:
        return np.repeat([r.data_cutoff_at for r in self.records], K)

    def known(self) -> np.ndarray:
        return np.repeat([r.known_at for r in self.records], K)

    def quantiles(self) -> np.ndarray:
        if not self.records:
            return np.empty((len(QUANTILES), 0))
        return np.concatenate([r.p_q for r in self.records], axis=1)


@dataclass
class History:
    records: dict[int, FixtureRecord] = field(default_factory=dict)

    def add(self, rec: FixtureRecord) -> None:
        prev = self.records.get(rec.fixture_id)
        # Aynı maç için birden çok çalıştırma varsa, başlama saatinden önceki en son değerlendirme tutulur.
        if prev is None or (rec.data_cutoff_at >= prev.data_cutoff_at and rec.data_cutoff_at < rec.kickoff_at):
            if prev is not None and prev.y is not None:
                rec.y, rec.known_at = prev.y, prev.known_at
            self.records[rec.fixture_id] = rec

    def set_outcome(self, fixture_id: int, result: MatchResult, known_at: str) -> None:
        rec = self.records.get(fixture_id)
        if rec is not None:
            rec.y = outcome_vector(result)
            rec.known_at = known_at

    def view(self, cutoff: str, window_days: int | None = None) -> HistoryView:
        from ..db.database import iso_add

        start = iso_add(cutoff, days=-window_days) if window_days else ""
        recs = [
            r for r in self.records.values()
            if r.y is not None and r.known_at is not None and r.known_at <= cutoff
            and r.data_cutoff_at <= cutoff and r.data_cutoff_at < r.kickoff_at and r.kickoff_at >= start
        ]
        recs.sort(key=lambda r: r.kickoff_at)
        return HistoryView(cutoff, recs)

    # ───────────── veritabanından yükleme ─────────────
    @classmethod
    def from_db(cls, conn: sqlite3.Connection) -> "History":
        """Kaydedilmiş değerlendirmelerden geçmişi yeniden kurar (üretim çalıştırmaları için)."""
        hist = cls()
        pos = {k: i for i, k in enumerate(INDEX.keys)}
        rows = conn.execute(
            """
            SELECT e.*, f.league_id, f.kickoff_at, f.status, f.ft_home, f.ft_away, f.ht_home, f.ht_away,
                   f.result_recorded_at
            FROM candidate_evaluations e JOIN fixtures f ON f.id = e.fixture_id
            WHERE e.data_cutoff_at < f.kickoff_at
            ORDER BY e.fixture_id, e.data_cutoff_at
            """
        ).fetchall()
        cur_fid, cur_cut, buf = None, None, []

        def flush():
            if not buf:
                return
            r0 = buf[0]
            arr = {name: np.full(K, np.nan) for name in (
                "pa_raw", "pb_raw", "pa_cal", "pb_cal", "fair", "signal", "p_est", "p_void", "width", "dis", "market_dev",
                "ev_a", "ev_b", "odds", "robust_down")}
            pq = np.full((len(QUANTILES), K), np.nan)
            core = np.zeros(K, bool)
            ov = np.zeros(K, bool)
            for r in buf:
                i = pos.get((r["market_id"], r["outcome_id"]))
                if i is None:
                    continue
                arr["pa_raw"][i] = r["p_a_raw"]
                arr["pb_raw"][i] = _nan(r["p_b_raw"])
                arr["pa_cal"][i] = _nan(r["p_a_cal"])
                arr["pb_cal"][i] = _nan(r["p_b_cal"])
                arr["fair"][i] = _nan(r["fair_probability"])
                arr["signal"][i] = _nan(r["football_signal"]) if r["football_signal"] is not None else 0.0
                arr["p_est"][i] = _nan(r["estimated_probability"])
                arr["p_void"][i] = r["p_void"] or 0.0
                arr["width"][i] = _nan(r["interval_width"])
                arr["dis"][i] = _nan(r["disagreement"])
                arr["market_dev"][i] = _nan(r["market_deviation"])
                arr["ev_a"][i] = _nan(r["ev_model_a"])
                arr["ev_b"][i] = _nan(r["ev_model_b"])
                arr["odds"][i] = _nan(r["decimal_odds"])
                arr["robust_down"][i] = _nan(r["robust_down"])
                core[i] = bool(r["core_pass"])
                ov[i] = bool(r["odds_valid"])
                q = loads(r["uncertainty_metrics"], {}).get("q")
                if q:
                    pq[:, i] = [q.get(f"{qq:.1f}", np.nan) if q.get(f"{qq:.1f}") is not None else np.nan
                                for qq in QUANTILES]
            rec = FixtureRecord(
                fixture_id=r0["fixture_id"], league_id=r0["league_id"], kickoff_at=r0["kickoff_at"],
                data_cutoff_at=r0["data_cutoff_at"], p_q=pq, core_pass=core, odds_valid=ov, **arr,
            )
            if r0["status"] in ("finished", "postponed", "abandoned", "cancelled") and r0["result_recorded_at"]:
                rec.y = outcome_vector(MatchResult(r0["status"], r0["ft_home"], r0["ft_away"],
                                                   r0["ht_home"], r0["ht_away"]))
                rec.known_at = r0["result_recorded_at"]
            hist.add(rec)

        for r in rows:
            key = (r["fixture_id"], r["data_cutoff_at"])
            if (cur_fid, cur_cut) != key:
                flush()
                buf = []
                cur_fid, cur_cut = key
            buf.append(r)
        flush()
        return hist


def _nan(v):
    return np.nan if v is None else float(v)
