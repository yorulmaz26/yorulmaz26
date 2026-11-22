"""Öneri servisinin okuma katmanı: kayıtlı çalıştırmalardan API yanıtları üretir.

Bu katman hiçbir olasılığı yeniden hesaplamaz; MODEL A/B olasılıkları, mod kararları ve denetim
sonuçları veritabanında kaydedildiği haliyle sunulur (adım 15: kullanıcının moduna göre sıralama).
"""
from __future__ import annotations

import sqlite3
from collections import Counter

from ..config import SETTINGS, VERSION
from ..db.database import DEFAULT_MODE, MODE_CODES, SELECTION_MODES, from_iso, loads, to_iso, utcnow
from ..domain.markets import FAMILY_NAMES, get_market
from ..leagues import LEAGUES_BY_ID, display_name
from ..models.model_a import ROBUSTNESS_SCENARIOS
from ..selection.checks import (BALANCED_CONDITIONS, CHECK_LABELS, CORE_CHECKS, ODDS_CHECKS, Check, describe,
                                fmt_num, fmt_pct, unpack)
from ..selection.modes import (BALANCED, BEST_VALUE, EMPTY_MESSAGES, HIGHEST_PROBABILITY, USES_ODDS,
                               normalize_mode)
from ..selection.history import QUANTILES

DISCLAIMER = ("Tahminler olasılık tahminidir; yüksek isabet oranı veya pozitif beklenen değer kazanç garantisi "
              "değildir. 18 yaş altı kullanamaz.")
MODE_META = {m["code"]: m for m in SELECTION_MODES}
UNC_LABELS = {"low": "Düşük", "medium": "Orta", "high": "Yüksek"}


class NotFound(Exception):
    pass


def _now() -> str:
    return to_iso(utcnow())


def _age(at: str, since: str | None) -> float | None:
    if not since:
        return None
    return max(0.0, (from_iso(at) - from_iso(since)).total_seconds())


def is_synthetic(conn: sqlite3.Connection) -> bool:
    return conn.execute("SELECT 1 FROM fixtures WHERE is_synthetic=1 LIMIT 1").fetchone() is not None


def latest_run(conn: sqlite3.Connection, kind: str = "prematch") -> sqlite3.Row | None:
    return conn.execute(
        "SELECT * FROM prediction_runs WHERE kind=? AND status='finished' ORDER BY as_of DESC, id DESC LIMIT 1", (kind,)
    ).fetchone()


# ───────────────────────── ayarlar ─────────────────────────

def get_settings(conn: sqlite3.Connection, user_id: str) -> dict:
    row = conn.execute("SELECT default_mode FROM user_settings WHERE user_id=?", (user_id,)).fetchone()
    return {"user_id": user_id, "default_mode": row[0] if row else DEFAULT_MODE}


def put_settings(conn: sqlite3.Connection, user_id: str, default_mode: str) -> dict:
    mode = normalize_mode(default_mode)
    conn.execute(
        "INSERT INTO user_settings(user_id, default_mode, updated_at) VALUES (?,?,?)"
        " ON CONFLICT(user_id) DO UPDATE SET default_mode=excluded.default_mode, updated_at=excluded.updated_at",
        (user_id, mode, _now()),
    )
    return get_settings(conn, user_id)


# ───────────────────────── modlar ─────────────────────────

def _config(conn: sqlite3.Connection, mode: str, at: str) -> sqlite3.Row | None:
    return conn.execute(
        "SELECT * FROM selection_mode_configs WHERE mode=? AND data_cutoff_at<=? ORDER BY data_cutoff_at DESC, id DESC"
        " LIMIT 1", (mode, at),
    ).fetchone()


def _thresholds_payload(cfg: sqlite3.Row | None) -> dict:
    if cfg is None:
        return {"status": "not_established", "values": {}, "source": "chronological_validation",
                "validation_window": None, "validation_metrics": {},
                "reason": "Henüz kronolojik doğrulama yapılmadı."}
    metrics = loads(cfg["validation_metrics"], {})
    return {
        "status": cfg["status"],
        "values": loads(cfg["thresholds"], {}),
        "source": cfg["source"],
        "validation_window": {"start": cfg["validation_start"], "end": cfg["validation_end"]},
        "validation_metrics": metrics,
        "reason": metrics.get("reason"),
        "config_id": cfg["id"],
    }


def modes(conn: sqlite3.Connection, user_id: str) -> dict:
    run = latest_run(conn)
    at = run["data_cutoff_at"] if run else _now()
    out = []
    for m in sorted(SELECTION_MODES, key=lambda x: x["sort_order"]):
        out.append({
            "code": m["code"], "name": m["name"], "short_name": m["short_name"], "description": m["description"],
            "is_default": bool(m["is_default"]), "requires_odds": bool(m["requires_odds"]), "ranking": m["ranking"],
            "thresholds": _thresholds_payload(_config(conn, m["code"], at)),
        })
    return {"default_mode": DEFAULT_MODE, "user_default_mode": get_settings(conn, user_id)["default_mode"],
            "modes": out}


# ───────────────────────── öneriler ─────────────────────────

_ITEM_SQL = """
SELECT c.id AS cid, c.selection_mode, c.rank, c.rank_score, c.probability_lower, c.ev_lower, c.mode_checks,
       c.eligible, c.selected, c.config_id,
       e.*, e.id AS eid,
       f.kickoff_at, f.league_id, f.status AS fixture_status, f.is_synthetic,
       ht.name AS home_name, at.name AS away_name,
       fe.freshness, fe.validity,
       pa.raw_probability AS pa_raw_p, pa.calibrated_probability AS pa_cal_p, pa.probability_lower AS pa_lo,
       pa.probability_upper AS pa_hi,
       pb.raw_probability AS pb_raw_p, pb.calibrated_probability AS pb_cal_p, pb.probability_lower AS pb_lo,
       pb.probability_upper AS pb_hi,
       o.decimal_odds AS snap_odds, o.source AS odds_source, o.captured_at AS odds_captured_at,
       s.label AS source_label, s.source_type, s.is_iddaa
FROM selection_mode_candidates c
JOIN candidate_evaluations e ON e.id = c.evaluation_id
JOIN fixtures f ON f.id = c.fixture_id
JOIN teams ht ON ht.id = f.home_team_id
JOIN teams at ON at.id = f.away_team_id
LEFT JOIN fixture_evaluations fe ON fe.run_id = c.run_id AND fe.fixture_id = c.fixture_id
LEFT JOIN predictions pa ON pa.id = e.model_a_prediction_id
LEFT JOIN predictions pb ON pb.id = e.model_b_prediction_id
LEFT JOIN odds_snapshots o ON o.id = e.odds_snapshot_id
LEFT JOIN odds_sources s ON s.code = o.source
"""


def _decision_for(conn: sqlite3.Connection, r: sqlite3.Row) -> sqlite3.Row | None:
    return conn.execute(
        "SELECT * FROM selection_mode_decisions WHERE selection_mode=? AND fixture_id=? AND market_id=? AND outcome_id=?",
        (r["selection_mode"], r["fixture_id"], r["market_id"], r["outcome_id"]),
    ).fetchone()


def _audit_for(conn: sqlite3.Connection, cid: int) -> sqlite3.Row | None:
    return conn.execute("SELECT * FROM selection_audits WHERE candidate_id=? ORDER BY id DESC LIMIT 1", (cid,)).fetchone()


def _fixture_payload(r: sqlite3.Row) -> dict:
    lg = LEAGUES_BY_ID.get(r["league_id"])
    return {
        "id": r["fixture_id"], "home": r["home_name"], "away": r["away_name"],
        "league": {"id": r["league_id"], "name": display_name(r["league_id"]), "country": lg.country if lg else ""},
        "kickoff_at": r["kickoff_at"],
    }


def _market_payload(market_id: str, outcome_id: str) -> tuple[dict, dict]:
    mk = get_market(market_id)
    return ({"id": mk.id, "name": mk.name, "family": mk.family},
            {"id": outcome_id, "name": mk.outcome(outcome_id).name})


def _mode_checks(r: sqlite3.Row) -> tuple[list[Check], dict]:
    mc = loads(r["mode_checks"], {})
    return unpack(mc.get("checks")), mc


def _core_checks(r: sqlite3.Row, mode: str | None = None) -> list[Check]:
    """Kalite Kapısı kontrolleri; oran kontrolleri yalnızca oran gerektiren modlarda listelenir."""
    mode = mode or r["selection_mode"]
    failed = {c.code: c for c in unpack(loads(r["gate_failed"], []))}
    codes = CORE_CHECKS + (ODDS_CHECKS if mode in USES_ODDS else ())
    return [failed.get(code) or Check(code, True, None, None, "Sağlandı.") for code in codes]


def _negative_reason(code: str, detail: str | None = None) -> str:
    text = f"Sağlanmayan koşul: {CHECK_LABELS.get(code, code)}."
    if detail and detail not in ("Sağlanmadı.",):
        text += f" {detail.rstrip('.')}."
    return text


def _uncertainty(r: sqlite3.Row, th: dict) -> dict:
    rel = r["relative_width"]
    limit = th.get("max_relative_width")
    if rel is None:
        level = "high"
    elif limit:
        level = "low" if rel <= limit / 2 else "medium" if rel <= limit else "high"
    else:
        level = "low" if rel <= 0.15 else "medium" if rel <= 0.35 else "high"
    return {"level": level, "label": UNC_LABELS[level], "interval_width": r["interval_width"],
            "relative_width": rel, "disagreement": r["disagreement"], "market_deviation": r["market_deviation"],
            "quantiles": loads(r["uncertainty_metrics"], {}).get("q")}


def _robustness(r: sqlite3.Row, th: dict, mode: str, checks: list[Check]) -> dict:
    scen = loads(r["robustness"], {}).get("s") or []
    p = r["estimated_probability"]
    rob_check = next((c for c in checks if c.code == "robustness"), None)
    items, ok_count = [], 0
    p_low = r["probability_lower"]
    for sc, ps in zip(ROBUSTNESS_SCENARIOS, scen):
        items.append({"name": sc.label, "probability": ps})
        if ps is None or p is None or p_low is None:
            continue
        pl = p_low + (ps - p)
        ok = True
        if mode in (HIGHEST_PROBABILITY, BALANCED) and th.get("min_probability_lower") is not None:
            ok &= pl >= th["min_probability_lower"]
        if mode in (BEST_VALUE, BALANCED) and th.get("min_ev_lower") is not None and r["decimal_odds"]:
            ok &= pl * r["decimal_odds"] + (r["p_void"] or 0) - 1 > th["min_ev_lower"]
        ok_count += int(ok)
    shifts = [abs(ps - p) for ps in scen if ps is not None and p is not None]
    robust = bool(rob_check and rob_check.passed)
    return {"status": "robust" if robust else "fragile", "label": "Dayanıklı" if robust else "Kırılgan",
            "score": (ok_count / len(items)) if items else None, "max_shift": max(shifts) if shifts else None,
            "scenarios": items}


def _odds_payload(r: sqlite3.Row, now: str) -> dict | None:
    # Doğrulamadan geçmeyen (eski, doğrulanmamış, yaklaşık zamanlı) oran "gerçek oran" olarak gösterilmez.
    if r["snap_odds"] is None or not r["odds_valid"]:
        return None
    return {
        "decimal": r["snap_odds"], "source": r["odds_source"], "source_label": r["source_label"],
        "is_iddaa": bool(r["is_iddaa"]) and r["source_type"] == "iddaa",
        "captured_at": r["odds_captured_at"], "age_seconds": _age(now, r["odds_captured_at"]),
        "margin": r["market_margin"], "fair_probability": r["fair_probability"], "snapshot_id": r["odds_snapshot_id"],
    }


def _reason_summary(mode: str, checks: list[Check], r: sqlite3.Row, th: dict) -> str:
    level = th.get("level")
    lvl = f"%{int(level * 100)} güven" if level else ""
    parts = []
    if mode in (HIGHEST_PROBABILITY, BALANCED):
        parts.append(f"Kalibre olasılığın ihtiyatlı alt sınırı ({lvl}) {fmt_num(r['probability_lower'])} ≥ "
                     f"{fmt_num(th.get('min_probability_lower'))}")
    if mode in (BEST_VALUE, BALANCED):
        parts.append(f"EV alt sınırı {fmt_pct(r['ev_lower'])} > {fmt_pct(th.get('min_ev_lower'))}; "
                     f"MODEL A {fmt_pct(r['ev_model_a'])}, MODEL B {fmt_pct(r['ev_model_b'])} ayrı ayrı pozitif")
    parts.append("belirsizlik, model uyuşmazlığı ve dayanıklılık senaryoları eşikleri sağlıyor")
    if mode == HIGHEST_PROBABILITY:
        parts.append("sıralama ham olasılığa değil, kalibrasyon ve belirsizlikten geçen alt sınıra göredir")
    return "; ".join(parts) + "."


def _warnings(mode: str, r: sqlite3.Row, odds: dict | None) -> list[str]:
    w = []
    if mode in (BEST_VALUE, BALANCED):
        w.append("Avantaj yalnızca model tahmini doğruysa geçerlidir.")
        w.append("Pozitif EV kazanç garantisi değildir.")
    if mode == HIGHEST_PROBABILITY:
        w.append("Yüksek isabet olasılığı pozitif getiri garantisi değildir; düşük oranlarda marj getiriyi eritebilir.")
    if odds is not None and not odds["is_iddaa"]:
        w.append(f"Oran kaynağı: {odds['source_label']} — İddaa oranı değildir.")
    if r["is_synthetic"]:
        w.append("Sentetik demo verisi: takımlar, sonuçlar ve oranlar kurgusaldır.")
    return w


def _related(conn: sqlite3.Connection, r: sqlite3.Row, mc: dict) -> list[dict]:
    out = []
    for mid, oid, corr in mc.get("related", []):
        cid = conn.execute(
            "SELECT id FROM selection_mode_candidates WHERE run_id=? AND selection_mode=? AND fixture_id=?"
            " AND market_id=? AND outcome_id=?", (r["run_id"], r["selection_mode"], r["fixture_id"], mid, oid),
        ).fetchone()
        mk = get_market(mid)
        out.append({"candidate_id": cid[0] if cid else None, "market": mid, "outcome": oid,
                    "label": f"{mk.name} · {mk.outcome(oid).name}", "correlation": corr})
    return out


def _item(conn: sqlite3.Connection, r: sqlite3.Row, now: str, cfg_cache: dict) -> dict:
    from ..performance.tracker import history_for

    mode = r["selection_mode"]
    cfg = cfg_cache.setdefault(r["config_id"], conn.execute(
        "SELECT * FROM selection_mode_configs WHERE id=?", (r["config_id"],)).fetchone() if r["config_id"] else None)
    th = loads(cfg["thresholds"], {}) if cfg else {}
    checks, mc = _mode_checks(r)
    market, outcome = _market_payload(r["market_id"], r["outcome_id"])
    dec = _decision_for(conn, r)
    audit = _audit_for(conn, r["cid"])
    odds = _odds_payload(r, now)
    fresh = loads(r["freshness"], {}) or {}
    validity = loads(r["validity"], {}) or {}
    core = _core_checks(r)
    data_ok = all(c.passed for c in core if c.code in ("data_fresh", "data_sufficient"))
    odds_age = odds["age_seconds"] if odds else None
    odds_fresh_now = odds_age is not None and odds_age <= SETTINGS.data.max_prematch_odds_age_seconds
    odds_matter = mode in USES_ODDS
    fresh_ok = data_ok and (odds_fresh_now or not odds_matter)
    reasons = []
    if now >= r["kickoff_at"]:
        reasons.append("Maç başladı.")
    if mode in (BEST_VALUE, BALANCED) and not odds_fresh_now:
        reasons.append("Oran anlık görüntüsü güncelliğini yitirdi; EV yeniden hesaplanmalı.")
    newer = conn.execute("SELECT 1 FROM fixtures WHERE league_id=? AND result_recorded_at>? AND result_recorded_at<=?"
                         " LIMIT 1", (r["league_id"], r["data_cutoff_at"], now)).fetchone()
    if newer:
        reasons.append("Zaman kesiminden sonra yeni sonuç verisi geldi; tahmin yenilenmeli.")
    lg = LEAGUES_BY_ID.get(r["league_id"])
    return {
        "id": r["cid"], "decision_id": dec["id"] if dec else None, "rank": r["rank"], "rank_score": r["rank_score"],
        "decision": ({"id": dec["id"], "first_decision": dec["candidate_id"] == r["cid"],
                      "recorded_odds": dec["decimal_odds"], "recorded_probability": dec["estimated_probability"],
                      "selection_timestamp": dec["selection_timestamp"],
                      "note": None if dec["candidate_id"] == r["cid"] else
                      "Performans, bu seçimin ilk kararındaki oran ve olasılıkla sayılır."} if dec else None),
        "fixture": _fixture_payload(r), "market": market, "outcome": outcome, "odds": odds,
        "model_a": {"prediction_id": r["model_a_prediction_id"], "raw": r["pa_raw_p"], "calibrated": r["pa_cal_p"],
                    "lower": r["pa_lo"], "upper": r["pa_hi"], "ev": r["ev_model_a"] if odds else None},
        "model_b": {"prediction_id": r["model_b_prediction_id"], "raw": r["pb_raw_p"], "calibrated": r["pb_cal_p"],
                    "lower": r["pb_lo"], "upper": r["pb_hi"], "ev": r["ev_model_b"] if odds else None},
        "estimated_probability": r["estimated_probability"], "void_probability": r["p_void"],
        "probability_lower": r["probability_lower"], "probability_method": r["probability_method"],
        "expected_value": ({"point": r["estimated_ev"], "lower": r["ev_lower"], "model_a": r["ev_model_a"],
                            "model_b": r["ev_model_b"]} if odds else None),
        "uncertainty": _uncertainty(r, th),
        "robustness": _robustness(r, th, mode, checks),
        "data_freshness": {"status": "fresh" if fresh_ok else "stale",
                           "label": "Güncel" if fresh_ok else "Eski",
                           "last_result_at": fresh.get("last_result_at"), "odds_age_seconds": odds_age,
                           "team_matches": fresh.get("team_matches")},
        "validity": {"status": "expired" if reasons else "valid", "label": "Süresi doldu" if reasons else "Geçerli",
                     "valid_until": validity.get("valid_until", r["kickoff_at"]), "reasons": reasons,
                     "data_cutoff_at": r["data_cutoff_at"]},
        "history": history_for(conn, mode, market["family"], r["league_id"], r["evaluated_at"]),
        "reason_summary": _reason_summary(mode, checks, r, th),
        "audit": ({"status": audit["status"], "label": "Denetimden geçti" if audit["status"] == "passed"
                   else "Denetimden kaldı", "audited_at": audit["audited_at"], "audit_id": audit["id"]}
                  if audit else {"status": "pending", "label": "Bekliyor", "audited_at": None}),
        "related_selections": _related(conn, r, mc),
        "warnings": _warnings(mode, r, odds),
        "league_name": lg.name if lg else r["league_id"],
    }


def recommendations(conn: sqlite3.Connection, mode: str, *, now: str | None = None, league: str | None = None,
                    date: str | None = None) -> dict:
    mode = normalize_mode(mode)
    now = now or _now()
    run = latest_run(conn)
    meta = MODE_META[mode]
    base = {"mode": mode, "mode_name": meta["name"], "disclaimer": DISCLAIMER, "is_synthetic_data": is_synthetic(conn)}
    if run is None:
        return {**base, "run_id": None, "generated_at": None, "data_cutoff_at": None,
                "message": EMPTY_MESSAGES[mode], "summary": {"fixtures_scanned": 0, "candidates": 0, "eligible": 0,
                                                             "selected": 0, "fixtures_without_selection": 0},
                "items": []}
    where = ["c.run_id=?", "c.selection_mode=?", "c.selected=1"]
    params: list = [run["id"], mode]
    if league:
        where.append("f.league_id=?")
        params.append(league)
    if date:
        where.append("substr(f.kickoff_at,1,10)=?")
        params.append(date)
    rows = conn.execute(_ITEM_SQL + " WHERE " + " AND ".join(where) + " ORDER BY c.rank_score DESC, c.id",
                        params).fetchall()
    cfg_cache: dict = {}
    items = []
    for r in rows:
        audit = _audit_for(conn, r["cid"])
        if audit is None or audit["status"] != "passed":
            continue  # bağımsız denetimden geçmeyen aday gösterilmez
        items.append(_item(conn, r, now, cfg_cache))
    for i, it in enumerate(items, start=1):
        it["rank"] = i
    swhere = ["c.run_id=?", "c.selection_mode=?"]
    sparams: list = [run["id"], mode]
    if league:
        swhere.append("f.league_id=?")
        sparams.append(league)
    if date:
        swhere.append("substr(f.kickoff_at,1,10)=?")
        sparams.append(date)
    stats = conn.execute(
        "SELECT COUNT(*), SUM(c.eligible), COUNT(DISTINCT c.fixture_id) FROM selection_mode_candidates c"
        " JOIN fixtures f ON f.id=c.fixture_id WHERE " + " AND ".join(swhere), sparams).fetchone()
    with_sel = len({it["fixture"]["id"] for it in items})
    message = None if items else EMPTY_MESSAGES[mode]
    if not items and not (stats[2] or 0):
        message = ("Değerlendirilecek yaklaşan maç yok (önümüzdeki günler için veri kaynağında maç listelenmemiş"
                   " olabilir); seçim üretilmedi.")
    return {
        **base, "run_id": run["id"], "generated_at": run["finished_at"], "data_cutoff_at": run["data_cutoff_at"],
        "message": message,
        "summary": {"fixtures_scanned": stats[2] or 0, "candidates": stats[0] or 0, "eligible": stats[1] or 0,
                    "selected": len(items), "fixtures_without_selection": (stats[2] or 0) - with_sel},
        "items": items,
    }


def _candidate_row(conn: sqlite3.Connection, cid: int) -> sqlite3.Row:
    r = conn.execute(_ITEM_SQL + " WHERE c.id=?", (cid,)).fetchone()
    if r is None:
        raise NotFound(f"Aday bulunamadı: {cid}")
    return r


def decision_explanation(conn: sqlite3.Connection, cid: int) -> dict:
    r = _candidate_row(conn, cid)
    mode = r["selection_mode"]
    cfg = conn.execute("SELECT * FROM selection_mode_configs WHERE id=?", (r["config_id"],)).fetchone() \
        if r["config_id"] else None
    th = loads(cfg["thresholds"], {}) if cfg else {}
    core = _core_checks(r)
    mode_checks, mc = _mode_checks(r)
    audit = _audit_for(conn, cid)
    audit_checks = [{"code": a[0], "label": a[1], "passed": bool(a[2]), "detail": a[3]}
                    for a in loads(audit["checks"], [])] if audit else []
    all_checks = core + [c for c in mode_checks if c.code not in {x.code for x in core}]
    if audit:
        all_checks.append(Check("audit", audit["status"] == "passed", None, None,
                                (f"Bağımsız denetimden geçti (denetim #{audit['id']})." if audit["status"] == "passed"
                                 else f"Bağımsız denetimden kaldı (denetim #{audit['id']}).")))
    dec = _decision_for(conn, r)
    selected = bool(r["selected"]) and audit is not None and audit["status"] == "passed"
    payload = {
        "id": cid, "mode": mode, "selected": selected, "decision_id": dec["id"] if dec else None,
        "summary": _reason_summary(mode, mode_checks, r, th) if selected else
        "Bu aday seçilmedi; ayrıntılar için 'Neden elendi?' bölümüne bakın.",
        "config_id": r["config_id"], "thresholds": th,
        "checks": [c.as_api() for c in all_checks],
        "audit": {"status": audit["status"] if audit else "pending", "checks": audit_checks},
        "models": {"A": {"raw": r["pa_raw_p"], "calibrated": r["pa_cal_p"]},
                   "B": {"raw": r["pb_raw_p"], "calibrated": r["pb_cal_p"]},
                   "combination": r["probability_method"]},
    }
    if mode == BALANCED:
        by_code = {c.code: c for c in all_checks}
        established = not any(c.code == "thresholds_established" and not c.passed for c in mode_checks)
        conds = []
        for no, label, codes in BALANCED_CONDITIONS:
            present = [by_code[c] for c in codes if c in by_code]
            if any(not c.passed for c in present):
                passed = False
            elif r["eligible"] or (present and len(present) == len(codes)):
                passed = True
            elif not established and not present:
                passed = None  # eşikler kurulmadığı için bu koşul değerlendirilmedi
            else:
                # uygun olmayan adaylarda yalnızca sağlanmayan mod kontrolleri saklanır; yoksa sağlanmıştır
                passed = True if established else None
            conds.append({"no": no, "label": label, "codes": list(codes), "passed": passed})
        payload["conditions"] = conds
    return payload


def rejection_reasons(conn: sqlite3.Connection, cid: int) -> dict:
    r = _candidate_row(conn, cid)
    rej = conn.execute("SELECT * FROM selection_mode_rejections WHERE candidate_id=?", (cid,)).fetchone()
    market, outcome = _market_payload(r["market_id"], r["outcome_id"])
    failed = [c for c in _core_checks(r) if not c.passed]
    mode_checks, _ = _mode_checks(r)
    failed += [c for c in mode_checks if not c.passed and c.code not in {x.code for x in failed}]
    if rej is not None:
        codes = loads(rej["failed_checks"], [])
        if "audit" in codes:
            failed.append(Check("audit", False, None, None, rej["reason_text"] or "Bağımsız denetimden kaldı."))
        if "nested_duplicate" in codes and not any(c.code == "nested_duplicate" for c in failed):
            failed.append(Check("nested_duplicate", False, None, None, describe("nested_duplicate", False, None, None)))
    fx = _fixture_payload(r)
    return {
        "id": cid, "mode": r["selection_mode"], "selected": rej is None and bool(r["selected"]),
        "fixture": {"id": fx["id"], "home": fx["home"], "away": fx["away"], "kickoff_at": fx["kickoff_at"]},
        "market": {"id": market["id"], "name": market["name"]}, "outcome": outcome,
        "failed_checks": [c.as_api() for c in failed],
        "reason_text": (rej["reason_text"] or _negative_reason(rej["primary_reason_code"]))
        if rej else "Aday seçildi; elenme gerekçesi yok.",
    }


def rejections(conn: sqlite3.Connection, mode: str, *, fixture_id: int | None = None, limit: int = 50,
               offset: int = 0) -> dict:
    mode = normalize_mode(mode)
    run = latest_run(conn)
    if run is None:
        return {"mode": mode, "run_id": None, "total": 0, "offset": offset, "limit": limit, "fixtures": [],
            "reason_counts": [], "items": []}
    where, params = "j.run_id=? AND j.selection_mode=?", [run["id"], mode]
    if fixture_id is not None:
        where += " AND j.fixture_id=?"
        params.append(fixture_id)
    counts = Counter()
    for (codes,) in conn.execute(f"SELECT failed_checks FROM selection_mode_rejections j WHERE {where}", params):
        for c in set(loads(codes, [])):
            counts[c] += 1
    total = conn.execute(f"SELECT COUNT(*) FROM selection_mode_rejections j WHERE {where}", params).fetchone()[0]
    fixtures = [
        {"id": f[0], "home": f[1], "away": f[2], "kickoff_at": f[3], "count": f[4]}
        for f in conn.execute(
            """SELECT j.fixture_id, ht.name, at.name, fx.kickoff_at, COUNT(*)
               FROM selection_mode_rejections j JOIN fixtures fx ON fx.id = j.fixture_id
               JOIN teams ht ON ht.id = fx.home_team_id JOIN teams at ON at.id = fx.away_team_id
               WHERE j.run_id=? AND j.selection_mode=? GROUP BY j.fixture_id ORDER BY fx.kickoff_at, j.fixture_id""",
            (run["id"], mode))
    ]
    rows = conn.execute(
        f"""SELECT j.*, e.estimated_probability, e.decimal_odds, c.ev_lower, f.kickoff_at, ht.name AS home_name,
                   at.name AS away_name, e.gate_failed, c.mode_checks
            FROM selection_mode_rejections j
            JOIN selection_mode_candidates c ON c.id = j.candidate_id
            JOIN candidate_evaluations e ON e.id = c.evaluation_id
            JOIN fixtures f ON f.id = j.fixture_id
            JOIN teams ht ON ht.id = f.home_team_id JOIN teams at ON at.id = f.away_team_id
            WHERE {where}
            ORDER BY (e.estimated_probability IS NULL), c.eligible DESC, e.estimated_probability DESC
            LIMIT ? OFFSET ?""", (*params, limit, offset)).fetchall()
    items = []
    for r in rows:
        market, outcome = _market_payload(r["market_id"], r["outcome_id"])
        gate = [c for c in unpack(loads(r["gate_failed"], [])) if mode in USES_ODDS or c.code not in ODDS_CHECKS]
        seen = {c.code for c in gate}
        failed = gate + [c for c in unpack(loads(r["mode_checks"], {}).get("checks"))
                         if not c.passed and c.code not in seen]
        codes = loads(r["failed_checks"], [])
        for code in ("audit", "nested_duplicate"):
            if code in codes and not any(c.code == code for c in failed):
                failed.append(Check(code, False, None, None, r["reason_text"] or describe(code, False, None, None)))
        items.append({
            "id": r["candidate_id"],
            "fixture": {"id": r["fixture_id"], "home": r["home_name"], "away": r["away_name"], "kickoff_at": r["kickoff_at"]},
            "market": {"id": market["id"], "name": market["name"]}, "outcome": outcome,
            "estimated_probability": r["estimated_probability"], "odds": r["decimal_odds"], "ev_lower": r["ev_lower"],
            "failed_checks": [{"code": c.code, "label": CHECK_LABELS.get(c.code, c.code), "detail": c.detail} for c in failed],
            "reason_text": r["reason_text"] or _negative_reason(r["primary_reason_code"]),
        })
    return {
        "mode": mode, "run_id": run["id"], "total": total, "offset": offset, "limit": limit,
        "fixtures": fixtures,
        "reason_counts": [{"code": c, "label": CHECK_LABELS.get(c, c), "count": n} for c, n in counts.most_common()],
        "items": items,
    }


def leagues() -> dict:
    from ..leagues import LEAGUES

    return {"items": [{"id": lg.id, "name": lg.name, "country": lg.country, "type": lg.type, "active": True}
                      for lg in LEAGUES]}


def system_status(conn: sqlite3.Connection) -> dict:
    run = latest_run(conn)
    return {
        "version": VERSION, "prediction_models": ["MODEL_A", "MODEL_B"], "selection_modes": len(MODE_CODES),
        "selection_mode_codes": list(MODE_CODES), "default_mode": DEFAULT_MODE, "is_synthetic_data": is_synthetic(conn),
        "last_run": {"id": run["id"], "finished_at": run["finished_at"], "data_cutoff_at": run["data_cutoff_at"]}
        if run else None,
        "radar_thresholds": {"model_a": SETTINGS.radar.model_a_min_probability,
                             "model_b": SETTINGS.radar.model_b_min_probability},
        "families": FAMILY_NAMES, "quantiles": list(QUANTILES),
    }
