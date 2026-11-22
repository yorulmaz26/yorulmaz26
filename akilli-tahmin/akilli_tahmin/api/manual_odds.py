"""Oranı elle girme: kullanıcının bir bahis sitesinde (ör. İddaa) gördüğü oranı sistemin olasılıklarıyla karşılaştırır.

- Girilen oran `manual` kaynağıyla, giriş anına tarihlenmiş (kesin zaman damgası) olarak kaydedilir; kullanıcının
  beyanıdır, resmi İddaa bağlantısı değildir ve İddaa olarak gösterilmez.
- Olasılıklar yeniden hesaplanmaz: en son maç öncesi çalıştırmanın kayıtlı MODEL A / MODEL B kalibre olasılıkları,
  belirsizlik yüzdelikleri ve dayanıklılık değerleri kullanılır (oran MODEL A'yı hiçbir zaman etkilemez).
- Her seçenek için EV = p × oran − 1, başa baş oranı ve belirsizlik altındaki ihtiyatlı EV hesaplanır; üç modun
  kurulu eşikleriyle bu oranla seçim yapılıp yapılmayacağı (karar kaydı oluşturmadan) gösterilir.
- Bu bir öneri değildir; pozitif EV kazanç garantisi değildir.
"""
from __future__ import annotations

import math
import sqlite3

from ..config import SETTINGS
from ..db.database import MODE_CODES, SELECTION_MODES, insert_many, loads, to_iso, transaction, utcnow
from ..domain.markets import MARKETS_BY_ID, get_market
from ..leagues import display_name
from ..models.logistic import logit
from ..odds.odds import expected_value, fair_probabilities, overround
from ..selection.checks import CHECK_LABELS, describe, unpack
from ..selection.engine import _config_from_row
from ..selection.history import QUANTILES
from ..selection.modes import CandidateMetrics, evaluate_mode
from .service import latest_run

SOURCE = "manual"
SOURCE_LABEL = "Elle girilen oran (kullanıcı)"
MAX_ODDS = 1000.0
MODE_NAMES = {m["code"]: m["short_name"] for m in SELECTION_MODES}
DISCLAIMER = ("Bu bir öneri değildir. Hesap, sistemin olasılık tahminiyle girdiğiniz oranı karşılaştırır; avantaj "
              "yalnızca model tahmini doğruysa geçerlidir ve pozitif beklenen değer kazanç garantisi değildir.")


class ManualOddsError(ValueError):
    pass


def _now() -> str:
    return to_iso(utcnow())


def _fixtures(conn: sqlite3.Connection, run_id: int, now: str) -> list[sqlite3.Row]:
    return conn.execute(
        """SELECT DISTINCT f.id, f.kickoff_at, f.league_id, ht.name AS home, at.name AS away
           FROM candidate_evaluations e JOIN fixtures f ON f.id = e.fixture_id
           JOIN teams ht ON ht.id = f.home_team_id JOIN teams at ON at.id = f.away_team_id
           WHERE e.run_id = ? AND f.kickoff_at > ? ORDER BY f.kickoff_at, f.id""", (run_id, now)).fetchall()


def options(conn: sqlite3.Connection, *, now: str | None = None) -> dict:
    now = now or _now()
    run = latest_run(conn)
    markets = [{"id": mk.id, "name": mk.name, "family": mk.family,
                "outcomes": [{"id": o.id, "name": o.name} for o in mk.outcomes]} for mk in MARKETS_BY_ID.values()]
    if run is None:
        return {"run_id": None, "generated_at": None, "fixtures": [], "markets": markets,
                "max_age_minutes": SETTINGS.data.max_prematch_odds_age_seconds // 60}
    fx = [{"id": r["id"], "home": r["home"], "away": r["away"], "kickoff_at": r["kickoff_at"],
           "league": {"id": r["league_id"], "name": display_name(r["league_id"])}}
          for r in _fixtures(conn, run["id"], now)]
    return {"run_id": run["id"], "generated_at": run["finished_at"], "fixtures": fx, "markets": markets,
            "max_age_minutes": SETTINGS.data.max_prematch_odds_age_seconds // 60}


def _mode_config(conn: sqlite3.Connection, mode: str, at: str):
    row = conn.execute("SELECT * FROM selection_mode_configs WHERE mode=? AND data_cutoff_at<=?"
                       " ORDER BY data_cutoff_at DESC, id DESC LIMIT 1", (mode, at)).fetchone()
    return _config_from_row(row) if row is not None else None


def _f(x) -> float | None:
    try:
        v = float(x)
    except (TypeError, ValueError):
        return None
    return v if math.isfinite(v) else None


def evaluate(conn: sqlite3.Connection, fixture_id: int, market_id: str, odds: dict[str, float], *,
             now: str | None = None) -> dict:
    now = now or _now()
    run = latest_run(conn)
    if run is None:
        raise ManualOddsError("Henüz maç öncesi değerlendirme yok; önce veriyi güncelleyin.")
    if market_id not in MARKETS_BY_ID:
        raise ManualOddsError(f"Bilinmeyen market: {market_id}")
    mk = get_market(market_id)
    valid_ids = {o.id for o in mk.outcomes}
    if not odds:
        raise ManualOddsError("En az bir seçenek için oran girin.")
    clean: dict[str, float] = {}
    for oid, v in odds.items():
        if oid not in valid_ids:
            raise ManualOddsError(f"{mk.name} marketinde '{oid}' seçeneği yok.")
        price = _f(v)
        if price is None or not (1.0 < price <= MAX_ODDS):
            raise ManualOddsError(f"Geçersiz oran ({mk.outcome(oid).name}): 1,01 ile {int(MAX_ODDS)} arasında olmalı.")
        clean[oid] = round(price, 2)
    fx = conn.execute(
        """SELECT f.*, ht.name AS home, at.name AS away FROM fixtures f JOIN teams ht ON ht.id=f.home_team_id
           JOIN teams at ON at.id=f.away_team_id WHERE f.id=?""", (fixture_id,)).fetchone()
    if fx is None:
        raise ManualOddsError("Maç bulunamadı.")
    if fx["kickoff_at"] <= now or fx["status"] != "scheduled":
        raise ManualOddsError("Maç başlamış veya planlı değil; oran değerlendirilemez.")
    rows = {r["outcome_id"]: r for r in conn.execute(
        "SELECT * FROM candidate_evaluations WHERE run_id=? AND fixture_id=? AND market_id=?",
        (run["id"], fixture_id, market_id))}
    if not rows:
        raise ManualOddsError("Bu maç için güncel değerlendirme yok (son çalıştırmada bulunmuyor).")

    # Kayıt: kullanıcının beyanı, giriş anına tarihli kesin zaman damgasıyla.
    with transaction(conn):
        insert_many(conn, "odds_snapshots", [
            {"fixture_id": fixture_id, "market_id": market_id, "outcome_id": oid, "decimal_odds": price,
             "source": SOURCE, "captured_at": now, "is_live": 0, "minute": None, "timestamp_precision": "exact",
             "ingested_at": now} for oid, price in clean.items()])

    complete = mk.exhaustive and mk.family != "dnb" and set(clean) == valid_ids
    fair: dict[str, float] = {}
    margin = None
    if complete:
        order = [o.id for o in mk.outcomes]
        fp = fair_probabilities([clean[o] for o in order])
        fair = dict(zip(order, fp))
        margin = overround([clean[o] for o in order])

    configs = {m: _mode_config(conn, m, now) for m in MODE_CODES}
    bal = configs.get("balanced")
    level = bal.thresholds.level if bal is not None and bal.established else 0.8
    qkey = f"{round(1 - level, 1):.1f}"
    outcomes = []
    for o in mk.outcomes:
        if o.id not in clean:
            continue
        price = clean[o.id]
        r = rows.get(o.id)
        pa, pb = _f(r["p_a_cal"]), _f(r["p_b_cal"])
        p, pv = _f(r["estimated_probability"]), float(r["p_void"] or 0.0)
        q = loads(r["uncertainty_metrics"], {}).get("q", {})
        p_low = _f(q.get(qkey))
        core_failed = [c for c in unpack(loads(r["gate_failed"], [])) if c.code not in ("odds_valid", "odds_settlement_rules")]
        item = {
            "id": o.id, "name": o.name, "odds": price, "fair_probability": fair.get(o.id),
            "model_a": pa, "model_b": pb, "estimated_probability": p, "probability_lower": p_low,
            "confidence_level": level, "void_probability": pv,
            "break_even_odds": round((1 - pv) / p, 3) if p else None,
            "ev": expected_value(p, price, pv) if p is not None else None,
            "ev_lower": expected_value(p_low, price, pv) if p_low is not None else None,
            "ev_model_a": expected_value(pa, price, pv) if pa is not None else None,
            "ev_model_b": expected_value(pb, price, pv) if pb is not None else None,
            "quality_issues": [{"code": c.code, "label": CHECK_LABELS.get(c.code, c.code), "detail": c.detail}
                               for c in core_failed],
        }
        if p is None or core_failed:
            item["verdict"], item["verdict_label"] = "unavailable", "Değerlendirilemez"
        elif item["ev_lower"] is not None and item["ev_lower"] > 0 and (item["ev_model_a"] or 0) > 0 \
                and (item["ev_model_b"] or 0) > 0:
            item["verdict"], item["verdict_label"] = "value", "Avantajlı görünüyor"
        elif item["ev"] is not None and item["ev"] > 0:
            item["verdict"], item["verdict_label"] = "uncertain", "Belirsiz (avantaj belirsizlik altında desteklenmiyor)"
        else:
            item["verdict"], item["verdict_label"] = "no_value", "Avantajsız"
        # Modların bu oranla vereceği karar (karar kaydı oluşturulmaz).
        mdev = None
        if fair.get(o.id) is not None and p is not None:
            mdev = abs(float(logit(p)) - float(logit(fair[o.id])))
        metrics = CandidateMetrics(
            core_pass=not core_failed and p is not None, odds_valid=True, p_est=p, p_void=pv,
            p_q=[_f(q.get(f"{qq:.1f}")) for qq in QUANTILES], width=_f(r["interval_width"]),
            rel_width=_f(r["relative_width"]), dis=_f(r["disagreement"]), market_dev=mdev,
            ev_a=item["ev_model_a"], ev_b=item["ev_model_b"], odds=price, robust_down=_f(r["robust_down"]),
            core_failed=[c.code for c in core_failed])
        modes = []
        for m in MODE_CODES:
            cfg = configs.get(m)
            if cfg is None:
                modes.append({"code": m, "name": MODE_NAMES[m], "status": "not_established", "eligible": False,
                              "failed": [{"code": "thresholds_established",
                                          "label": CHECK_LABELS["thresholds_established"],
                                          "detail": "Eşikler henüz kronolojik doğrulamayla belirlenmedi."}]})
                continue
            ev_ = evaluate_mode(m, metrics, cfg)
            failed = [{"code": c.code, "label": CHECK_LABELS.get(c.code, c.code),
                       "detail": c.detail or describe(c.code, False, c.value, c.threshold)}
                      for c in ev_.checks if not c.passed]
            if not metrics.core_pass:
                failed = [{"code": c.code, "label": CHECK_LABELS.get(c.code, c.code), "detail": c.detail}
                          for c in core_failed] + failed
            modes.append({"code": m, "name": MODE_NAMES[m], "status": cfg.status,
                          "eligible": bool(ev_.eligible and metrics.core_pass),
                          "reason": (cfg.metrics or {}).get("reason") if not cfg.established else None,
                          "failed": failed})
        item["modes"] = modes
        outcomes.append(item)

    notes = [DISCLAIMER,
             f"Girdiğiniz oran '{SOURCE_LABEL}' olarak kaydedildi; İddaa'dan otomatik doğrulanmadı.",
             f"Olasılıklar {run['data_cutoff_at']} tarihli değerlendirmeden alınmıştır; oranlar değişirse yeniden girin."]
    if not complete:
        notes.append("Marketin bütün seçenekleri girilmediği için marj ve marjdan arındırılmış olasılık hesaplanamadı.")
    return {
        "fixture": {"id": fx["id"], "home": fx["home"], "away": fx["away"], "kickoff_at": fx["kickoff_at"],
                    "league": {"id": fx["league_id"], "name": display_name(fx["league_id"])}},
        "market": {"id": mk.id, "name": mk.name}, "entered_at": now, "source_label": SOURCE_LABEL,
        "complete_market": complete, "margin": margin, "run_id": run["id"],
        "outcomes": outcomes, "notes": notes,
    }


def history(conn: sqlite3.Connection, limit: int = 20) -> dict:
    rows = conn.execute(
        """SELECT o.fixture_id, o.market_id, o.outcome_id, o.decimal_odds, o.captured_at, f.kickoff_at, f.league_id,
                  ht.name AS home, at.name AS away
           FROM odds_snapshots o JOIN fixtures f ON f.id=o.fixture_id
           JOIN teams ht ON ht.id=f.home_team_id JOIN teams at ON at.id=f.away_team_id
           WHERE o.source=? ORDER BY o.captured_at DESC, o.id DESC LIMIT ?""", (SOURCE, limit)).fetchall()
    items = []
    for r in rows:
        mk = get_market(r["market_id"])
        items.append({"fixture": {"id": r["fixture_id"], "home": r["home"], "away": r["away"],
                                  "kickoff_at": r["kickoff_at"], "league": {"id": r["league_id"],
                                                                            "name": display_name(r["league_id"])}},
                      "market": {"id": mk.id, "name": mk.name},
                      "outcome": {"id": r["outcome_id"], "name": mk.outcome(r["outcome_id"]).name},
                      "odds": r["decimal_odds"], "entered_at": r["captured_at"]})
    return {"items": items}
