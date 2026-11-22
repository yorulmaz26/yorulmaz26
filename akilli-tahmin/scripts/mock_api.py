#!/usr/bin/env python3
"""Akıllı Tahmin — arayüz geliştirme için küçük sahte (mock) API sunucusu.

Yalnızca standart kütüphane kullanır. Statik dosyaları (akilli_tahmin/web/static)
"/" ve "/static/..." altında, docs/API.md sözleşmesine uyan sabit JSON yanıtlarını
(tests/web/mock_api.json) "/api/v1/..." altında sunar. Gerçek backend yerine
geçmez; yalnızca UI geliştirme ve duman testleri içindir.

Oran Gir (POST /api/v1/manual-odds): yanıt, maç başına sabit beklenen gol değerlerinden
(MODEL A / MODEL B, bağımsız Poisson) türetilen olasılıklarla hesaplanır (EV, başa baş oranı,
marj, üç modun kontrolleri). Girilen oranlar yalnızca bellekte tutulur (GET .../history).

Kullanım:
    python3 scripts/mock_api.py --port 8765 [--host 127.0.0.1] [--delay 300]
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import mimetypes
import re
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

ROOT = Path(__file__).resolve().parent.parent
STATIC_DIR = ROOT / "akilli_tahmin" / "web" / "static"
DATA_FILE = ROOT / "tests" / "web" / "mock_api.json"
MODES = ("highest_probability", "best_value", "balanced")
PERIODS = ("7d", "30d", "90d", "all")

_lock = threading.Lock()
_settings: dict[str, str] = {}          # user_id -> default_mode
_notification_permission: dict[str, bool] = {}
_manual_history: list[dict] = []   # POST /manual-odds ile girilen oranlar (en yeni başta)


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class ApiError(Exception):
    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def load_data() -> dict:
    with DATA_FILE.open(encoding="utf-8") as fh:
        return json.load(fh)


def first(qs: dict, key: str, default=None):
    vals = qs.get(key)
    return vals[0] if vals else default


def as_int(value, name: str, default=None):
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        raise ApiError(422, f"'{name}' tam sayı olmalıdır.")


def check_mode(mode: str | None, user: str) -> str:
    if mode is None or mode == "":
        return _settings.get(user, "balanced")
    if mode not in MODES:
        raise ApiError(422, f"Geçersiz seçim modu: {mode}")
    return mode


def check_period(period: str | None) -> str:
    period = period or "all"
    if period in PERIODS:
        return period
    if re.fullmatch(r"season:\d{4}", period):
        return "all"  # mock: sezon filtresi tüm dönem verisiyle yanıtlanır
    raise ApiError(422, f"Geçersiz dönem: {period}")


# ---------------------------------------------------------------- Oran Gir: Poisson olasılıkları + mod kontrolleri
MANUAL_CHECK_LABELS = {
    "thresholds_established": "Eşikler kronolojik doğrulamayla belirlendi",
    "prob_lower": "Kalibre olasılığın ihtiyatlı alt sınırı",
    "ev_models_positive": "MODEL A ve MODEL B ayrı ayrı pozitif EV",
    "ev_lower": "Belirsizlik altında EV avantajı",
    "uncertainty": "Belirsizlik kabul edilebilir düzeyde",
    "market_reference": "Marjdan arındırılmış piyasa referansıyla uyum",
    "disagreement": "Model uyuşmazlığı kontrolü",
    "robustness": "Tahmin dayanıklılık testi",
}
HALF_SHARE = 0.45  # ilk yarıya düşen gol payı
MAX_GOALS = 10


def _logit(p: float) -> float:
    p = min(max(p, 1e-6), 1 - 1e-6)
    return math.log(p / (1 - p))


def _z(level: float) -> float:
    """Tek yönlü standart normal yüzdelik (küçük tablo; mock için yeterli)."""
    table = {0.7: 0.5244, 0.75: 0.6745, 0.8: 0.8416, 0.85: 1.0364, 0.9: 1.2816, 0.95: 1.6449}
    return table.get(round(level, 2), 0.8416)


def _tr(v: float | None, digits: int = 3) -> str:
    return "—" if v is None else f"{v:.{digits}f}".replace(".", ",")


def _tr_pct(v: float | None) -> str:
    if v is None:
        return "—"
    s = f"%{abs(v) * 100:.1f}".replace(".", ",")
    return ("−" + s) if v < 0 and round(abs(v) * 100, 1) != 0 else s


def _poisson(lam: float) -> list[float]:
    return [math.exp(-lam) * lam ** k / math.factorial(k) for k in range(MAX_GOALS + 1)]


def _score_matrix(lh: float, la: float) -> dict[tuple[int, int], float]:
    ph, pa = _poisson(lh), _poisson(la)
    return {(i, j): ph[i] * pa[j] for i in range(MAX_GOALS + 1) for j in range(MAX_GOALS + 1)}


def _res(h: int, a: int) -> str:
    return "1" if h > a else ("X" if h == a else "2")


def market_probabilities(market_id: str, lh: float, la: float) -> dict[str, tuple[float, float]]:
    """Seçenek → (kazanma olasılığı, iade olasılığı)."""
    ft = _score_matrix(lh, la)
    out: dict[str, float] = {}

    def add(key: str, p: float):
        out[key] = out.get(key, 0.0) + p

    voids: dict[str, float] = {}
    if market_id in ("1X2", "DC", "DNB") or market_id.startswith("EH_"):
        shift = int(market_id[3:]) if market_id.startswith("EH_") else 0
        for (h, a), p in ft.items():
            r = _res(h + shift, a)
            if market_id == "DC":
                for k in ("1X", "12", "X2"):
                    if r in k:
                        add(k, p)
            elif market_id == "DNB":
                if r == "X":
                    voids["1"] = voids.get("1", 0.0) + p
                    voids["2"] = voids.get("2", 0.0) + p
                else:
                    add(r, p)
            else:
                add(r, p)
    elif market_id.startswith(("OU_", "HOME_OU_", "AWAY_OU_", "HT_OU_")):
        line = float(market_id.rsplit("_", 1)[1])
        mat = _score_matrix(lh * HALF_SHARE, la * HALF_SHARE) if market_id.startswith("HT_") else ft
        for (h, a), p in mat.items():
            goals = h if market_id.startswith("HOME_") else a if market_id.startswith("AWAY_") else h + a
            add("OVER" if goals > line else "UNDER", p)
    elif market_id == "BTTS":
        for (h, a), p in ft.items():
            add("YES" if h > 0 and a > 0 else "NO", p)
    elif market_id == "HT_1X2":
        for (h, a), p in _score_matrix(lh * HALF_SHARE, la * HALF_SHARE).items():
            add(_res(h, a), p)
    elif market_id == "HTFT":
        first = _score_matrix(lh * HALF_SHARE, la * HALF_SHARE)
        second = _score_matrix(lh * (1 - HALF_SHARE), la * (1 - HALF_SHARE))
        for (h1, a1), p1 in first.items():
            if p1 < 1e-9:
                continue
            for (h2, a2), p2 in second.items():
                add(_res(h1, a1) + "/" + _res(h1 + h2, a1 + a2), p1 * p2)
    elif market_id == "TG":
        for (h, a), p in ft.items():
            t = h + a
            add("0-1" if t <= 1 else "2-3" if t <= 3 else "4-5" if t <= 5 else "6+", p)
    elif market_id == "CS":
        for (h, a), p in ft.items():
            add(f"{h}-{a}" if h <= 4 and a <= 4 else "OTHER_" + _res(h, a), p)
    elif market_id == "OE":
        for (h, a), p in ft.items():
            add("ODD" if (h + a) % 2 else "EVEN", p)
    else:
        raise ApiError(422, "Bu maç için güncel değerlendirme yok (son çalıştırmada bulunmuyor).")
    total = sum(out.values()) + (voids.get("1", 0.0) if market_id == "DNB" else 0.0)
    return {k: (v / total, voids.get(k, 0.0) / total) for k, v in out.items()}


def _manual_mode(code: str, cfg: dict | None, m: dict, core_failed: list[dict]) -> dict:
    """Bir modun girilen oranla seçim yapıp yapmayacağı (selection/modes.py'nin basitleştirilmiş benzeri)."""
    names = {"highest_probability": "En Güvenilir", "best_value": "En Avantajlı", "balanced": "Dengeli"}
    core = [dict(c) for c in core_failed]
    if not cfg or cfg.get("status") != "established":
        reason = (cfg or {}).get("reason") or "Eşikler henüz kronolojik doğrulamayla belirlenmedi."
        return {"code": code, "name": names[code], "status": "not_established", "eligible": False, "reason": reason,
                "failed": core + [{"code": "thresholds_established", "label": MANUAL_CHECK_LABELS["thresholds_established"],
                                   "detail": reason}]}
    th = cfg["thresholds"]
    lvl = f"%{int(th['level'] * 100)} güven"
    p_low = max(0.001, m["p"] - _z(th["level"]) * m["sd"])
    p_rob = p_low + min(0.0, m["robust_down"])
    ev_low, ev_rob = p_low * m["odds"] + m["pv"] - 1, p_rob * m["odds"] + m["pv"] - 1
    uses_p, uses_odds = code in ("highest_probability", "balanced"), code in ("best_value", "balanced")
    checks: list[tuple[str, bool, str]] = []
    if uses_p:
        thr = th["min_probability_lower"]
        checks.append(("prob_lower", p_low >= thr and p_low > 0.5, f"Olasılık alt sınırı ({lvl}) {_tr(p_low)} < {_tr(thr)}"))
    if uses_odds:
        checks.append(("ev_models_positive", m["ev_a"] > 0 and m["ev_b"] > 0,
                       f"EV MODEL A {_tr_pct(m['ev_a'])}, MODEL B {_tr_pct(m['ev_b'])}; ikisi de pozitif olmalı"))
        checks.append(("ev_lower", ev_low > th["min_ev_lower"], f"EV alt sınırı ({lvl}) {_tr_pct(ev_low)} ≤ {_tr_pct(th['min_ev_lower'])}"))
    rw = m["rel_width"]
    checks.append(("uncertainty", rw is not None and rw <= th["max_relative_width"],
                   f"Göreli belirsizlik (%80 aralık / p) {_tr(rw)} > {_tr(th['max_relative_width'])}"))
    if uses_odds:
        md = m["market_dev"]
        checks.append(("market_reference", md is not None and md <= th["max_market_deviation"],
                       f"Marjdan arındırılmış piyasa olasılığı yok veya modelden sapma {_tr(md)} > {_tr(th['max_market_deviation'])}"))
    checks.append(("disagreement", m["dis"] <= th["max_disagreement"],
                   f"MODEL A/B uyuşmazlığı (logit farkı) {_tr(m['dis'])} > {_tr(th['max_disagreement'])}"))
    rob_ok = (not uses_p or p_rob >= th["min_probability_lower"]) and (not uses_odds or ev_rob > th["min_ev_lower"])
    checks.append(("robustness", rob_ok, "Alternatif model kurulumlarından en az birinde eşik sağlanmıyor"
                   f" (en kötü kayma {_tr(m['robust_down'])})"))
    failed = core + [{"code": c, "label": MANUAL_CHECK_LABELS[c], "detail": d} for c, ok, d in checks if not ok]
    return {"code": code, "name": names[code], "status": "established", "eligible": not failed, "reason": None,
            "failed": failed}


class Api:
    def __init__(self, data: dict):
        self.data = data

    def handle(self, method: str, path: str, qs: dict, body, user: str):
        d = self.data
        if method == "GET":
            if path == "/recommendations/modes":
                resp = copy.deepcopy(d["modes"])
                resp["user_default_mode"] = _settings.get(user, resp.get("default_mode", "balanced"))
                return resp
            if path == "/recommendations":
                mode = check_mode(first(qs, "mode"), user)
                resp = copy.deepcopy(d["recommendations"][mode])
                league = first(qs, "league")
                if league:
                    resp["items"] = [i for i in resp["items"] if i["fixture"]["league"]["id"] == league]
                return resp
            if path == "/recommendations/rejections":
                return self.rejections(qs, user)
            if path == "/recommendations/modes/comparison":
                period = check_period(first(qs, "period"))
                return copy.deepcopy(d["comparison"][period])
            if path == "/recommendations/modes/performance":
                mode = check_mode(first(qs, "mode"), user)
                period = check_period(first(qs, "period"))
                resp = copy.deepcopy(d["mode_performance"][mode])
                by_period = resp.pop("by_period", {})  # mock'a özgü yardımcı alan; yanıtta yer almaz
                resp["period"] = period
                if period != "all":
                    resp["overall"] = resp["windows"][period]
                    resp.update(by_period.get(period, {}))
                return resp
            m = re.fullmatch(r"/recommendations/(\d+)/decision-explanation", path)
            if m:
                item = d["decision_explanations"].get(m.group(1))
                if item is None:
                    raise ApiError(404, "Seçim bulunamadı.")
                return item
            m = re.fullmatch(r"/recommendations/(\d+)/rejection-reasons", path)
            if m:
                return self.rejection_reasons(int(m.group(1)))
            if path == "/performance/selection-modes":
                return d["performance_selection_modes"]
            if path == "/settings":
                return {"user_id": user, "default_mode": _settings.get(user, "balanced")}
            if path == "/radar/config":
                return d["radar_config"]
            if path == "/radar/live":
                return self.radar_live(user)
            if path == "/radar/alerts":
                limit = max(1, min(500, as_int(first(qs, "limit"), "limit", 50)))
                return {"items": d["radar_alerts"]["items"][:limit]}
            if path == "/leagues":
                return d["leagues"]
            if path == "/system/status":
                return d["system_status"]
            if path == "/manual-odds/options":
                return self.manual_options()
            if path == "/manual-odds/history":
                limit = as_int(first(qs, "limit"), "limit", 20)
                if not 1 <= limit <= 200:
                    raise ApiError(422, "'limit' 1 ile 200 arasında olmalıdır.")
                return self.manual_history(limit)
        if method == "PUT" and path == "/settings":
            mode = (body or {}).get("default_mode") if isinstance(body, dict) else None
            if mode not in MODES:
                raise ApiError(422, "default_mode geçerli bir seçim modu olmalıdır.")
            with _lock:
                _settings[user] = mode
            return {"user_id": user, "default_mode": mode}
        if method == "POST" and path == "/radar/notifications/permission":
            granted = (body or {}).get("granted") if isinstance(body, dict) else None
            if not isinstance(granted, bool):
                raise ApiError(422, "granted alanı true/false olmalıdır.")
            with _lock:
                _notification_permission[user] = granted
            return {"user_id": user, "granted": granted}
        if method == "POST" and path == "/manual-odds":
            return self.manual_evaluate(body)
        raise ApiError(404, "Not Found")

    def rejections(self, qs: dict, user: str) -> dict:
        """Elenen adaylar: total, reason_counts ve fixtures kayıtlardan türetilir (gerçek API ile aynı anlam).

        `fixtures` limit/offset'ten bağımsız olarak o moddaki elenen adayı olan BÜTÜN maçları listeler.
        """
        mode = check_mode(first(qs, "mode"), user)
        limit = as_int(first(qs, "limit"), "limit", 50)
        offset = as_int(first(qs, "offset"), "offset", 0)
        if not 1 <= limit <= 500:
            raise ApiError(422, "'limit' 1 ile 500 arasında olmalıdır.")
        if offset < 0:
            raise ApiError(422, "'offset' negatif olamaz.")
        fixture_id = as_int(first(qs, "fixture_id"), "fixture_id")
        src = self.data["rejections"][mode]
        all_items = src["items"]
        fixtures: dict[int, dict] = {}
        for it in all_items:
            fx = it["fixture"]
            entry = fixtures.setdefault(fx["id"], {"id": fx["id"], "home": fx.get("home"), "away": fx.get("away"),
                                                   "kickoff_at": fx.get("kickoff_at"), "count": 0})
            entry["count"] += 1
        items = all_items if fixture_id is None else [i for i in all_items if i["fixture"]["id"] == fixture_id]
        counts: dict[str, dict] = {}
        for it in items:
            for fc in it["failed_checks"]:
                entry = counts.setdefault(fc["code"], {"code": fc["code"], "label": fc["label"], "count": 0})
                entry["count"] += 1
        page = []
        for it in items[offset:offset + limit]:
            row = copy.deepcopy(it)
            row["failed_checks"] = [{"code": fc["code"], "label": fc["label"], "detail": fc.get("detail")}
                                    for fc in it["failed_checks"]]
            page.append(row)
        return {
            "mode": mode,
            "run_id": src.get("run_id"),
            "total": len(items),
            "offset": offset,
            "limit": limit,
            "reason_counts": sorted(counts.values(), key=lambda c: -c["count"]),
            "fixtures": sorted(fixtures.values(), key=lambda f: (f["kickoff_at"] or "", f["id"])),
            "items": page,
        }

    def rejection_reasons(self, cid: int) -> dict:
        for mode, src in self.data["rejections"].items():
            for it in src["items"]:
                if it["id"] == cid:
                    return {
                        "id": cid, "mode": mode, "selected": False,
                        "fixture": {k: it["fixture"].get(k) for k in ("id", "home", "away")},
                        "market": it["market"], "outcome": it["outcome"],
                        "failed_checks": [{"code": fc["code"], "label": fc["label"], "passed": False,
                                           "detail": fc.get("detail"), "value": fc.get("value"),
                                           "threshold": fc.get("threshold")} for fc in it["failed_checks"]],
                        "reason_text": it.get("reason_text"),
                    }
        raise ApiError(404, "Elenen aday bulunamadı.")

    def radar_live(self, user: str) -> dict:
        resp = copy.deepcopy(self.data["radar_live"])
        resp["evaluated_at"] = _now_iso()
        granted = _notification_permission.get(user, False)
        for item in resp["items"]:
            for cond in item["conditions"]:
                if cond["code"] == "notification_permission":
                    cond["passed"] = granted
                    cond["detail"] = "İzin verildi" if granted else "Kullanıcı bildirim izni vermedi"
            if not granted and item.get("alarm") and not item.get("blocked_reason"):
                item["alarm"] = False
                item["alert_id"] = None
                item["blocked_reason"] = "Sağlanmayan koşul: Bildirim izni."
        return resp


    # -- oranı elle girme (Oran Gir) ------------------------------------
    # Olasılıklar maç başına sabit beklenen gol değerlerinden (MODEL A / MODEL B) bağımsız Poisson
    # varsayımıyla türetilir. Gerçek backend'in hesabı değildir; yalnızca arayüzün çevrimdışı geliştirilmesi
    # için akla yatkın, kendi içinde tutarlı (EV, başa baş oranı, marj, mod kontrolleri) bir yanıt üretir.
    def manual_options(self) -> dict:
        mo = self.data["manual_odds"]
        return {
            "run_id": mo["run_id"], "generated_at": mo["generated_at"],
            "fixtures": [{k: f[k] for k in ("id", "home", "away", "kickoff_at", "league")} for f in mo["fixtures"]],
            "markets": [{k: m[k] for k in ("id", "name", "family", "outcomes")} for m in mo["markets"]],
            "max_age_minutes": mo["max_age_minutes"],
        }

    def _manual_lookup(self):
        mo = self.data["manual_odds"]
        return mo, {f["id"]: f for f in mo["fixtures"]}, {m["id"]: m for m in mo["markets"]}

    def manual_history(self, limit: int) -> dict:
        mo, fixtures, markets = self._manual_lookup()
        with _lock:
            rows = list(_manual_history)
        rows += sorted(mo["history"], key=lambda r: r["entered_at"], reverse=True)
        items = []
        for r in rows[:limit]:
            fx, mk = fixtures[r["fixture_id"]], markets[r["market_id"]]
            oc = next(o for o in mk["outcomes"] if o["id"] == r["outcome_id"])
            items.append({"fixture": {k: fx[k] for k in ("id", "home", "away", "kickoff_at", "league")},
                          "market": {"id": mk["id"], "name": mk["name"]}, "outcome": dict(oc),
                          "odds": r["odds"], "entered_at": r["entered_at"]})
        return {"items": items}

    def manual_evaluate(self, body) -> dict:
        mo, fixtures, markets = self._manual_lookup()
        if not isinstance(body, dict):
            raise ApiError(422, "Geçersiz istek.")
        fixture_id, market_id, odds = body.get("fixture_id"), body.get("market_id"), body.get("odds")
        if isinstance(fixture_id, bool) or not isinstance(fixture_id, int) or not isinstance(market_id, str) \
                or not isinstance(odds, dict) or len(odds) > 40:
            raise ApiError(422, "Geçersiz istek.")
        if market_id not in markets:
            raise ApiError(422, f"Bilinmeyen market: {market_id}")
        mk = markets[market_id]
        names = {o["id"]: o["name"] for o in mk["outcomes"]}
        if not odds:
            raise ApiError(422, "En az bir seçenek için oran girin.")
        clean: dict[str, float] = {}
        for oid, v in odds.items():
            if oid not in names:
                raise ApiError(422, f"{mk['name']} marketinde '{oid}' seçeneği yok.")
            if isinstance(v, bool) or not isinstance(v, (int, float)):
                raise ApiError(422, "Geçersiz istek.")
            if not (math.isfinite(v) and 1.0 < v <= 1000.0):
                raise ApiError(422, f"Geçersiz oran ({names[oid]}): 1,01 ile 1000 arasında olmalı.")
            clean[oid] = round(float(v), 2)
        fx = fixtures.get(fixture_id)
        if fx is None:
            raise ApiError(422, "Maç bulunamadı.")
        now = _now_iso()
        with _lock:
            # Gerçek API gibi: en son eklenen kayıt en başta (captured_at DESC, id DESC).
            for oid in [o["id"] for o in mk["outcomes"] if o["id"] in clean]:
                _manual_history.insert(0, {"fixture_id": fixture_id, "market_id": market_id, "outcome_id": oid,
                                           "odds": clean[oid], "entered_at": now})
            del _manual_history[200:]

        probs_a = market_probabilities(market_id, *fx["goals"]["model_a"])
        probs_b = market_probabilities(market_id, *fx["goals"]["model_b"])
        order = [o["id"] for o in mk["outcomes"]]
        complete = mk["exhaustive"] and mk["family"] != "dnb" and set(clean) == set(order)
        fair: dict[str, float] = {}
        margin = None
        if complete:
            inv = {o: 1.0 / clean[o] for o in order}
            total = sum(inv.values())
            fair = {o: inv[o] / total for o in order}
            margin = total - 1.0
        cfgs = mo["modes"]
        bal = cfgs.get("balanced", {})
        level = bal["thresholds"]["level"] if bal.get("status") == "established" else 0.8
        z_level = _z(level)
        issues_by_market = fx.get("quality_issues", {}).get(market_id, [])
        outcomes = []
        for oid in order:
            if oid not in clean:
                continue
            price = clean[oid]
            pa, pva = probs_a[oid]
            pb, _ = probs_b[oid]
            p, pv = min(pa, pb), pva
            sd = 0.012 + 0.06 * math.sqrt(max(p * (1 - p), 0.0))
            p_low = max(0.001, p - z_level * sd)
            ev = lambda q: q * price + pv - 1.0  # noqa: E731
            item = {
                "id": oid, "name": names[oid], "odds": price, "fair_probability": fair.get(oid),
                "model_a": pa, "model_b": pb, "estimated_probability": p, "probability_lower": p_low,
                "confidence_level": level, "void_probability": pv,
                "break_even_odds": round((1 - pv) / p, 3) if p > 0 else None,
                "ev": ev(p), "ev_lower": ev(p_low), "ev_model_a": ev(pa), "ev_model_b": ev(pb),
                "quality_issues": copy.deepcopy(issues_by_market),
            }
            if issues_by_market:
                item["verdict"], item["verdict_label"] = "unavailable", "Değerlendirilemez"
            elif item["ev_lower"] > 0 and item["ev_model_a"] > 0 and item["ev_model_b"] > 0:
                item["verdict"], item["verdict_label"] = "value", "Avantajlı görünüyor"
            elif item["ev"] > 0:
                item["verdict"], item["verdict_label"] = "uncertain", "Belirsiz (avantaj belirsizlik altında desteklenmiyor)"
            else:
                item["verdict"], item["verdict_label"] = "no_value", "Avantajsız"
            metrics = {
                "p": p, "pv": pv, "sd": sd, "odds": price, "ev_a": item["ev_model_a"], "ev_b": item["ev_model_b"],
                "dis": abs(_logit(pa) - _logit(pb)), "rel_width": 2 * 1.2816 * sd / p if p > 0 else None,
                "market_dev": abs(_logit(p) - _logit(fair[oid])) if oid in fair else None,
                "robust_down": fx.get("robust_down", 0.0),
            }
            item["modes"] = [_manual_mode(code, cfgs.get(code), metrics, issues_by_market) for code in MODES]
            outcomes.append(item)
        notes = [
            "Bu bir öneri değildir. Hesap, sistemin olasılık tahminiyle girdiğiniz oranı karşılaştırır; avantaj "
            "yalnızca model tahmini doğruysa geçerlidir ve pozitif beklenen değer kazanç garantisi değildir.",
            "Girdiğiniz oran 'Elle girilen oran (kullanıcı)' olarak kaydedildi; İddaa'dan otomatik doğrulanmadı.",
            f"Olasılıklar {mo['data_cutoff_at']} tarihli değerlendirmeden alınmıştır; oranlar değişirse yeniden girin.",
            "Sahte (mock) sunucu: olasılıklar sabit beklenen gol değerlerinden türetilmiştir; gerçek model çıktısı değildir.",
        ]
        if not complete:
            notes.append("Marketin bütün seçenekleri girilmediği için marj ve marjdan arındırılmış olasılık hesaplanamadı.")
        return {
            "fixture": {k: fx[k] for k in ("id", "home", "away", "kickoff_at", "league")},
            "market": {"id": mk["id"], "name": mk["name"]}, "entered_at": now,
            "source_label": "Elle girilen oran (kullanıcı)", "complete_market": complete, "margin": margin,
            "run_id": mo["run_id"], "outcomes": outcomes, "notes": notes,
        }


class Handler(BaseHTTPRequestHandler):
    server_version = "AkilliTahminMock/6.3"
    api: Api
    delay_ms = 0
    quiet = False

    def log_message(self, fmt, *args):  # noqa: D401 - stdlib imzası
        if not self.quiet:
            sys.stderr.write("[mock] %s - %s\n" % (self.address_string(), fmt % args))

    # -- helpers ---------------------------------------------------------
    def _send(self, status: int, body: bytes, content_type: str, extra: dict | None = None):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _json(self, status: int, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self._send(status, body, "application/json; charset=utf-8")

    def _static(self, rel: str):
        target = (STATIC_DIR / rel).resolve()
        if STATIC_DIR.resolve() not in target.parents and target != STATIC_DIR.resolve():
            return self._json(404, {"detail": "Not Found"})
        if not target.is_file():
            return self._json(404, {"detail": "Not Found"})
        ctype = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        if ctype.startswith("text/") or ctype in ("application/javascript", "application/json"):
            ctype += "; charset=utf-8"
        self._send(200, target.read_bytes(), ctype)

    def _dispatch(self, method: str):
        url = urlsplit(self.path)
        path = url.path
        if path.startswith("/api/v1"):
            if self.delay_ms:
                time.sleep(self.delay_ms / 1000)
            sub = path[len("/api/v1"):].rstrip("/") or "/"
            body = None
            if method in ("POST", "PUT"):
                # Gerçek sunucu gibi: durum değiştiren istekler yalnızca application/json kabul eder
                # (CORS-basit, içerik türü olmayan çapraz site isteklerini engeller).
                ctype = (self.headers.get("Content-Type") or "").split(";")[0].strip().lower()
                if ctype != "application/json":
                    return self._json(415, {"detail": "İstek gövdesi application/json olmalıdır."})
            length = int(self.headers.get("Content-Length") or 0)
            if length:
                raw = self.rfile.read(length)
                try:
                    body = json.loads(raw.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    return self._json(422, {"detail": "Geçersiz JSON gövdesi."})
            user = (self.headers.get("X-User-Id") or "local").strip() or "local"
            try:
                payload = self.api.handle(method, sub, parse_qs(url.query), body, user)
            except ApiError as exc:
                return self._json(exc.status, {"detail": exc.detail})
            return self._json(200, payload)
        if method not in ("GET", "HEAD"):
            return self._json(405, {"detail": "Method Not Allowed"})
        if path in ("/", "/index.html"):
            return self._static("index.html")
        if path.startswith("/static/"):
            return self._static(path[len("/static/"):])
        return self._json(404, {"detail": "Not Found"})

    def do_GET(self):  # noqa: N802
        self._dispatch("GET")

    def do_HEAD(self):  # noqa: N802
        self._dispatch("GET")

    def do_POST(self):  # noqa: N802
        self._dispatch("POST")

    def do_PUT(self):  # noqa: N802
        self._dispatch("PUT")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Akıllı Tahmin sahte API sunucusu (yalnızca UI geliştirme).")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--delay", type=int, default=0, help="Her API yanıtına eklenecek gecikme (ms).")
    parser.add_argument("--quiet", action="store_true", help="İstek günlüğünü kapat.")
    args = parser.parse_args(argv)

    Handler.api = Api(load_data())
    Handler.delay_ms = max(0, args.delay)
    Handler.quiet = args.quiet
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Akıllı Tahmin mock API: http://{args.host}:{args.port}/ (statik: {STATIC_DIR})", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
