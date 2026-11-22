"""Futbol market kataloğu ve sonuçlandırma kuralları.

Her market seçeneği, skor ızgarası üzerinde tanımlı bir *kazanma* ve (varsa) *iade* koşuludur.
Izgara dört boyutludur: ilk yarı ev/deplasman golleri ve maç sonu ev/deplasman golleri.
Aynı koşullar hem olasılık hesaplamada (model ızgarası) hem de gerçek sonucun
sonuçlandırılmasında kullanılır; böylece tahmin ile sonuçlandırma arasında kural farkı oluşmaz.

Bütün marketler yalnızca normal süreyi (90 dakika + uzatma dakikaları) kapsar.
Uzatma devreleri ve penaltı atışları sonuçlandırmaya dahil edilmez.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import numpy as np

# Izgara: (hh, ha, fh, fa) dizileri. hh/ha = ilk yarı golleri, fh/fa = maç sonu golleri.
Grid = tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]
Predicate = Callable[[Grid], np.ndarray]

SETTLEMENT_RULE_VERSION = "standard-v1"

SETTLEMENT_RULE_TEXT = (
    "Yalnızca normal süre (90 dakika ve hakemin eklediği uzatma dakikaları) geçerlidir. "
    "Uzatma devreleri ve penaltı atışları sonuca dahil edilmez. "
    "Ertelenen, iptal edilen veya tamamlanmayan maçlarda bahis iade (void) edilir. "
    "İlk yarı marketleri yalnızca ilk yarının normal süresine göre sonuçlandırılır. "
    "Beraberlikte iade marketinde maç berabere biterse bahis iade edilir."
)


@dataclass(frozen=True)
class OutcomeDef:
    id: str
    name: str
    win: Predicate
    void: Predicate | None = None


@dataclass(frozen=True)
class MarketDef:
    id: str
    name: str
    family: str
    outcomes: tuple[OutcomeDef, ...]
    needs_halftime: bool = False
    line: float | None = None
    #: Seçenekler birbirini dışlar ve (iade dahil) toplam olasılık 1 olmalıdır.
    exhaustive: bool = True

    def outcome(self, outcome_id: str) -> OutcomeDef:
        for o in self.outcomes:
            if o.id == outcome_id:
                return o
        raise KeyError(f"{self.id} marketinde {outcome_id} seçeneği yok")


FAMILY_NAMES = {
    "result": "Maç Sonucu",
    "double_chance": "Çifte Şans",
    "dnb": "Beraberlikte İade",
    "totals": "Alt/Üst",
    "btts": "Karşılıklı Gol",
    "team_totals": "Takım Alt/Üst",
    "halftime": "İlk Yarı Sonucu",
    "halftime_totals": "İlk Yarı Alt/Üst",
    "htft": "İlk Yarı/Maç Sonucu",
    "goal_range": "Toplam Gol Aralığı",
    "correct_score": "Skor",
    "handicap": "Handikaplı Maç Sonucu",
    "odd_even": "Tek/Çift",
}


def _fmt_line(x: float) -> str:
    return f"{x:.1f}".replace(".", ",")


def _result(fh, fa, side):
    if side == "1":
        return fh > fa
    if side == "X":
        return fh == fa
    return fh < fa


def _build() -> tuple[MarketDef, ...]:
    m: list[MarketDef] = []

    m.append(MarketDef("1X2", "Maç Sonucu", "result", (
        OutcomeDef("1", "Ev Sahibi", lambda g: g[2] > g[3]),
        OutcomeDef("X", "Beraberlik", lambda g: g[2] == g[3]),
        OutcomeDef("2", "Deplasman", lambda g: g[2] < g[3]),
    )))
    m.append(MarketDef("DC", "Çifte Şans", "double_chance", (
        OutcomeDef("1X", "1-X", lambda g: g[2] >= g[3]),
        OutcomeDef("12", "1-2", lambda g: g[2] != g[3]),
        OutcomeDef("X2", "X-2", lambda g: g[2] <= g[3]),
    ), exhaustive=False))
    m.append(MarketDef("DNB", "Beraberlikte İade", "dnb", (
        OutcomeDef("1", "Ev Sahibi", lambda g: g[2] > g[3], void=lambda g: g[2] == g[3]),
        OutcomeDef("2", "Deplasman", lambda g: g[2] < g[3], void=lambda g: g[2] == g[3]),
    )))
    for line in (0.5, 1.5, 2.5, 3.5, 4.5, 5.5):
        m.append(MarketDef(f"OU_{line}", f"Alt/Üst {_fmt_line(line)}", "totals", (
            OutcomeDef("OVER", "Üst", lambda g, L=line: (g[2] + g[3]) > L),
            OutcomeDef("UNDER", "Alt", lambda g, L=line: (g[2] + g[3]) < L),
        ), line=line))
    m.append(MarketDef("BTTS", "Karşılıklı Gol", "btts", (
        OutcomeDef("YES", "Var", lambda g: (g[2] > 0) & (g[3] > 0)),
        OutcomeDef("NO", "Yok", lambda g: (g[2] == 0) | (g[3] == 0)),
    )))
    for side, idx, label in (("HOME", 2, "Ev Sahibi"), ("AWAY", 3, "Deplasman")):
        for line in (0.5, 1.5, 2.5):
            m.append(MarketDef(f"{side}_OU_{line}", f"{label} Alt/Üst {_fmt_line(line)}", "team_totals", (
                OutcomeDef("OVER", "Üst", lambda g, L=line, i=idx: g[i] > L),
                OutcomeDef("UNDER", "Alt", lambda g, L=line, i=idx: g[i] < L),
            ), line=line))
    m.append(MarketDef("HT_1X2", "İlk Yarı Sonucu", "halftime", (
        OutcomeDef("1", "Ev Sahibi", lambda g: g[0] > g[1]),
        OutcomeDef("X", "Beraberlik", lambda g: g[0] == g[1]),
        OutcomeDef("2", "Deplasman", lambda g: g[0] < g[1]),
    ), needs_halftime=True))
    for line in (0.5, 1.5, 2.5):
        m.append(MarketDef(f"HT_OU_{line}", f"İlk Yarı Alt/Üst {_fmt_line(line)}", "halftime_totals", (
            OutcomeDef("OVER", "Üst", lambda g, L=line: (g[0] + g[1]) > L),
            OutcomeDef("UNDER", "Alt", lambda g, L=line: (g[0] + g[1]) < L),
        ), needs_halftime=True, line=line))
    htft = []
    for ht in ("1", "X", "2"):
        for ft in ("1", "X", "2"):
            htft.append(OutcomeDef(
                f"{ht}/{ft}", f"{ht}/{ft}",
                lambda g, a=ht, b=ft: _result(g[0], g[1], a) & _result(g[2], g[3], b),
            ))
    m.append(MarketDef("HTFT", "İlk Yarı/Maç Sonucu", "htft", tuple(htft), needs_halftime=True))
    m.append(MarketDef("TG", "Toplam Gol Aralığı", "goal_range", (
        OutcomeDef("0-1", "0-1 Gol", lambda g: (g[2] + g[3]) <= 1),
        OutcomeDef("2-3", "2-3 Gol", lambda g: ((g[2] + g[3]) >= 2) & ((g[2] + g[3]) <= 3)),
        OutcomeDef("4-5", "4-5 Gol", lambda g: ((g[2] + g[3]) >= 4) & ((g[2] + g[3]) <= 5)),
        OutcomeDef("6+", "6+ Gol", lambda g: (g[2] + g[3]) >= 6),
    )))
    cs = []
    for h in range(5):
        for a in range(5):
            cs.append(OutcomeDef(f"{h}-{a}", f"{h}-{a}", lambda g, H=h, A=a: (g[2] == H) & (g[3] == A)))
    over4 = lambda g: (g[2] > 4) | (g[3] > 4)  # noqa: E731
    cs.append(OutcomeDef("OTHER_1", "Diğer (ev sahibi kazanır)", lambda g: over4(g) & (g[2] > g[3])))
    cs.append(OutcomeDef("OTHER_X", "Diğer (berabere)", lambda g: over4(g) & (g[2] == g[3])))
    cs.append(OutcomeDef("OTHER_2", "Diğer (deplasman kazanır)", lambda g: over4(g) & (g[2] < g[3])))
    m.append(MarketDef("CS", "Skor", "correct_score", tuple(cs)))
    for hcp in (-1, 1):
        sign = f"{hcp:+d}"
        m.append(MarketDef(f"EH_{sign}", f"Handikaplı Maç Sonucu ({sign})", "handicap", (
            OutcomeDef("1", "Ev Sahibi", lambda g, k=hcp: (g[2] + k) > g[3]),
            OutcomeDef("X", "Beraberlik", lambda g, k=hcp: (g[2] + k) == g[3]),
            OutcomeDef("2", "Deplasman", lambda g, k=hcp: (g[2] + k) < g[3]),
        ), line=float(hcp)))
    m.append(MarketDef("OE", "Tek/Çift", "odd_even", (
        OutcomeDef("ODD", "Tek", lambda g: ((g[2] + g[3]) % 2) == 1),
        OutcomeDef("EVEN", "Çift", lambda g: ((g[2] + g[3]) % 2) == 0),
    )))
    return tuple(m)


MARKETS: tuple[MarketDef, ...] = _build()
MARKETS_BY_ID = {mk.id: mk for mk in MARKETS}


def get_market(market_id: str) -> MarketDef:
    return MARKETS_BY_ID[market_id]


def all_outcome_keys() -> list[tuple[str, str]]:
    return [(mk.id, o.id) for mk in MARKETS for o in mk.outcomes]


@dataclass(frozen=True)
class MatchResult:
    status: str  # finished | postponed | abandoned | cancelled
    ft_home: int | None
    ft_away: int | None
    ht_home: int | None = None
    ht_away: int | None = None


def settle(market_id: str, outcome_id: str, result: MatchResult) -> str:
    """Gerçek sonucu, tahminde kullanılan aynı koşullarla sonuçlandırır.

    Dönüş: "won" | "lost" | "void" | "unresolved".
    """
    market = get_market(market_id)
    outcome = market.outcome(outcome_id)
    if result.status in ("postponed", "abandoned", "cancelled"):
        return "void"
    if result.status != "finished" or result.ft_home is None or result.ft_away is None:
        return "unresolved"
    if market.needs_halftime and (result.ht_home is None or result.ht_away is None):
        return "unresolved"
    hh = result.ht_home if result.ht_home is not None else 0
    ha = result.ht_away if result.ht_away is not None else 0
    g: Grid = (np.array([hh]), np.array([ha]), np.array([result.ft_home]), np.array([result.ft_away]))
    if outcome.void is not None and bool(outcome.void(g)[0]):
        return "void"
    return "won" if bool(outcome.win(g)[0]) else "lost"


def implied_subset(grid: Grid, a: tuple[str, str], b: tuple[str, str], weights: np.ndarray) -> bool:
    """`a` olayı `b` olayının alt kümesi mi (olasılığı pozitif her skor için)?

    Mantıksal içerme ızgara üzerinde kesin olarak sınanır (sayısal eşik yoktur):
    Üst 2,5 kazanırsa Üst 1,5 de kazanır → iç içe seçimlerdir.
    """
    ma, oa = get_market(a[0]), get_market(a[0]).outcome(a[1])
    mb, ob = get_market(b[0]), get_market(b[0]).outcome(b[1])
    del ma, mb
    support = weights > 0
    wa = oa.win(grid) & support
    wb = ob.win(grid) & support
    return bool(np.all(~wa | wb))
