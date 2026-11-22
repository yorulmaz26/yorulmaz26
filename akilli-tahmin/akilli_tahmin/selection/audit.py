"""Bağımsız seçim denetimi.

Seçim motorunun kodunu kullanmadan, yalnızca veritabanına yazılmış kayıtlardan yola çıkarak her
seçilmiş adayı yeniden doğrular. Denetim sonucu (geçti/kaldı ve her kontrol) `selection_audits`
tablosuna kalıcı olarak yazılır; denetimden kalan aday karar olarak kaydedilmez.

Not: Denetim *aynı verinin* farklı bir kod yoluyla yeniden doğrulanmasıdır; MODEL A ve MODEL B'nin
aynı veriden etkilenebileceği gerçeğini ortadan kaldırmaz (iki model bağımsız doğrulama sayılmaz).
"""
from __future__ import annotations

import json
import math
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone

from ..config import Settings

AUDITOR_VERSION = "audit-1.1"
ODDS_WORDS = ("odds", "oran", "price", "implied", "bookmaker", "margin")
QKEY = {0.6: "0.4", 0.7: "0.3", 0.8: "0.2", 0.9: "0.1"}


@dataclass
class AuditCheck:
    code: str
    label: str
    passed: bool
    detail: str


@dataclass
class AuditResult:
    passed: bool
    audit_id: int
    checks: list[AuditCheck]

    def failed_details(self) -> list[str]:
        return [f"{c.label}: {c.detail}" for c in self.checks if not c.passed]


def _ts(s: str) -> datetime:
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def audit_candidate(conn: sqlite3.Connection, candidate_id: int, settings: Settings) -> AuditResult:
    checks: list[AuditCheck] = []

    def add(code: str, label: str, ok: bool, detail: str = "") -> None:
        checks.append(AuditCheck(code, label, bool(ok), detail or ("Uygun." if ok else "Uygun değil.")))

    c = conn.execute("SELECT * FROM selection_mode_candidates WHERE id=?", (candidate_id,)).fetchone()
    e = conn.execute("SELECT * FROM candidate_evaluations WHERE id=?", (c["evaluation_id"],)).fetchone() if c else None
    if c is None or e is None:
        add("records", "Kayıt bütünlüğü", False, "Aday veya değerlendirme kaydı bulunamadı.")
        return _store(conn, candidate_id, c["selection_mode"] if c else "balanced", checks)
    mode = c["selection_mode"]
    uses_odds = mode in ("best_value", "balanced")
    uses_prob = mode in ("highest_probability", "balanced")
    fx = conn.execute("SELECT * FROM fixtures WHERE id=?", (c["fixture_id"],)).fetchone()
    pa = conn.execute("SELECT * FROM predictions WHERE id=?", (e["model_a_prediction_id"],)).fetchone()
    pb = conn.execute("SELECT * FROM predictions WHERE id=?", (e["model_b_prediction_id"],)).fetchone() \
        if e["model_b_prediction_id"] else None

    same = (e["fixture_id"] == c["fixture_id"] and e["market_id"] == c["market_id"]
            and e["outcome_id"] == c["outcome_id"] and e["run_id"] == c["run_id"])
    add("records", "Kayıt bütünlüğü", same and fx is not None, "Aday, değerlendirme ve fikstür aynı seçimi göstermeli.")

    ok = (pa is not None and pb is not None and pa["model"] == "A" and pb["model"] == "B"
          and all(p["fixture_id"] == c["fixture_id"] and p["market_id"] == c["market_id"]
                  and p["outcome_id"] == c["outcome_id"] for p in (pa, pb)))
    add("predictions", "MODEL A ve MODEL B tahmin kayıtları", ok, "İki modelin tahmini de aynı seçim için kayıtlı olmalı.")

    iso_ok = False
    if pa is not None:
        mv = conn.execute("SELECT uses_odds, params FROM model_versions WHERE id=?", (pa["model_version_id"],)).fetchone()
        manifest = (pa["input_manifest"] or "").lower()
        iso_ok = (pa["uses_odds"] == 0 and (mv is None or mv["uses_odds"] == 0)
                  and not any(w in manifest for w in ODDS_WORDS))
    add("model_a_isolation", "MODEL A oran izolasyonu", iso_ok, "MODEL A girdilerinde oran bulunmamalı.")

    chrono = (fx is not None and e["data_cutoff_at"] <= e["evaluated_at"] < fx["kickoff_at"]
              and pa is not None and pa["data_cutoff_at"] <= e["data_cutoff_at"]
              and (pb is None or pb["data_cutoff_at"] <= e["data_cutoff_at"]))
    add("chronology", "Zaman kesimi ve sıralama", chrono,
        "Veri kesimi ≤ değerlendirme anı < başlama saati; tahminler kesimden sonra üretilmemeli.")

    leak_ok = True
    if pb is not None and pb["model_version_id"]:
        mvb = conn.execute("SELECT data_cutoff_at FROM model_versions WHERE id=?", (pb["model_version_id"],)).fetchone()
        leak_ok = mvb is not None and mvb["data_cutoff_at"] <= e["data_cutoff_at"]
    add("model_b_temporal", "MODEL B zaman sızıntısı yok", leak_ok, "MODEL B kesimden sonra eğitilmiş olamaz.")

    add("quality_gate", "Tahmin Kalite Kapısı", e["core_pass"] == 1, "Moddan bağımsız kalite kontrolleri geçilmeli.")

    run = conn.execute("SELECT kind FROM prediction_runs WHERE id=?", (c["run_id"],)).fetchone()
    allow_approx = run is not None and run["kind"] == "backtest"
    o = None
    if uses_odds:
        snap = conn.execute(
            "SELECT o.*, s.verified, s.source_type, s.is_iddaa FROM odds_snapshots o JOIN odds_sources s"
            " ON s.code=o.source WHERE o.id=?", (e["odds_snapshot_id"],)).fetchone() if e["odds_snapshot_id"] else None
        odds_ok = False
        detail = "Oran anlık görüntüsü yok."
        if snap is not None:
            age = (_ts(e["evaluated_at"]) - _ts(snap["captured_at"])).total_seconds()
            odds_ok = (snap["fixture_id"] == c["fixture_id"] and snap["market_id"] == c["market_id"]
                       and snap["outcome_id"] == c["outcome_id"] and 0 <= age <= settings.data.max_prematch_odds_age_seconds
                       and snap["verified"] == 1 and snap["is_live"] == 0
                       and (snap["timestamp_precision"] == "exact" or allow_approx)
                       and snap["decimal_odds"] > 1.0 and abs(snap["decimal_odds"] - (e["decimal_odds"] or 0)) < 1e-9)
            detail = f"Kaynak {snap['source']}, yaş {int(age)} sn."
            o = snap["decimal_odds"]
            label_ok = (snap["is_iddaa"] == 1) == (snap["source_type"] == "iddaa")
            add("odds_label", "Oran kaynağı etiketi doğru", label_ok, "İddaa dışı oran İddaa olarak işaretlenemez.")
            rule = conn.execute("SELECT 1 FROM market_settlement_rules WHERE market_id=? AND source=? AND verified=1",
                                (c["market_id"], snap["source"])).fetchone()
            add("odds_rules", "Oran kaynağı sonuçlandırma kuralı", rule is not None, "Doğrulanmış kural gerekli.")
        add("odds", "Gerçek, doğrulanmış ve güncel oran", odds_ok, detail)

    rule = conn.execute("SELECT 1 FROM market_settlement_rules WHERE market_id=? AND source='standard' AND verified=1",
                        (c["market_id"],)).fetchone()
    add("rules", "Market sonuçlandırma kuralı", rule is not None, "Doğrulanmış standart kural gerekli.")

    # Bağımsız EV ve birleşim yeniden hesabı
    pac, pbc, pe, pv = e["p_a_cal"], e["p_b_cal"], e["estimated_probability"], e["p_void"] or 0.0
    comb_ok = pe is not None and pac is not None and pbc is not None
    if comb_ok and e["probability_method"] == "conservative_min":
        comb_ok = abs(pe - min(pac, pbc)) < 1e-9
    elif comb_ok:
        comb_ok = 0.0 <= pe <= 1.0
    add("combination", "MODEL A/B birleşim kuralı", comb_ok,
        "Doğrulanmış birleştirici yoksa iki kalibre olasılığın küçüğü kullanılmalı (ortalama alınmaz).")
    if uses_odds and o is not None:
        ev_re = pe * o + pv - 1.0
        add("ev_recompute", "EV bağımsız yeniden hesap", e["estimated_ev"] is not None and abs(ev_re - e["estimated_ev"]) < 1e-7,
            f"EV = p × oran − 1 = {ev_re:.5f}")

    # Eşiklerin yeniden uygulanması
    cfg = conn.execute("SELECT * FROM selection_mode_configs WHERE id=?", (c["config_id"],)).fetchone() if c["config_id"] else None
    th_ok = False
    detail = "Kronolojik eşik yapılandırması yok."
    if cfg is not None and cfg["status"] == "established" and cfg["mode"] == mode:
        th = json.loads(cfg["thresholds"])
        chrono_cfg = (cfg["validation_end"] or "") <= e["data_cutoff_at"] and cfg["data_cutoff_at"] <= e["data_cutoff_at"]
        q = json.loads(e["uncertainty_metrics"]).get("q", {})
        p_low = q.get(QKEY.get(round(th["level"], 1), ""))
        down = e["robust_down"]
        conds = [chrono_cfg, p_low is not None, down is not None]
        if p_low is not None and down is not None:
            p_rob = p_low + min(0.0, down)
            conds.append(e["relative_width"] is not None and e["relative_width"] <= th["max_relative_width"] + 1e-12)
            conds.append(e["disagreement"] is not None and e["disagreement"] <= th["max_disagreement"] + 1e-12)
            if uses_prob:
                conds.append(p_low >= th["min_probability_lower"] - 1e-12 and p_rob >= th["min_probability_lower"] - 1e-12)
            if uses_odds:
                odds_v = o if o is not None else float("nan")
                ev_low = p_low * odds_v + pv - 1.0
                ev_rob = p_rob * odds_v + pv - 1.0
                conds.append(math.isfinite(ev_low) and ev_low > th["min_ev_lower"] and ev_rob > th["min_ev_lower"])
                conds.append((e["ev_model_a"] or -1) > 0 and (e["ev_model_b"] or -1) > 0)
                conds.append(e["market_deviation"] is not None and th.get("max_market_deviation") is not None
                             and e["market_deviation"] <= th["max_market_deviation"] + 1e-12)
            detail = f"Düzey %{int(th['level'] * 100)}; olasılık alt sınırı {p_low:.4f}."
        th_ok = all(conds)
    add("thresholds", "Mod eşiklerinin yeniden uygulanması", th_ok, detail)

    return _store(conn, candidate_id, mode, checks)


def _store(conn: sqlite3.Connection, candidate_id: int, mode: str, checks: list[AuditCheck]) -> AuditResult:
    passed = all(ch.passed for ch in checks)
    now = conn.execute("SELECT evaluated_at FROM candidate_evaluations e JOIN selection_mode_candidates c"
                       " ON c.evaluation_id=e.id WHERE c.id=?", (candidate_id,)).fetchone()
    audited_at = now[0] if now else datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    cur = conn.execute(
        "INSERT INTO selection_audits(candidate_id, selection_mode, status, checks, auditor_version, audited_at)"
        " VALUES (?,?,?,?,?,?)",
        (candidate_id, mode, "passed" if passed else "failed",
         json.dumps([[ch.code, ch.label, int(ch.passed), ch.detail] for ch in checks], ensure_ascii=False),
         AUDITOR_VERSION, audited_at),
    )
    return AuditResult(passed, int(cur.lastrowid), checks)
