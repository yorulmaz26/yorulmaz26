"""Geçmiş zaman (point-in-time) veri erişimi.

Bütün sorgular bir `cutoff` (zaman kesimi) alır ve yalnızca o anda bilinen bilgiyi döndürür:
- Sonuçlar: `result_recorded_at <= cutoff`
- Oranlar: `captured_at <= cutoff`
MODEL A'ya giden yol (`match_records`) oran tablolarına hiç dokunmaz.
"""
from __future__ import annotations

import sqlite3
from collections import defaultdict
from dataclasses import dataclass

import numpy as np

from ..models.model_a import MatchRecord
from ..odds.odds import OddsQuote


class FootballDataView:
    """MODEL A ve futbol özellikleri için yalnızca futbol verisi sunan görünüm."""

    def __init__(self, conn: sqlite3.Connection):
        self.conn = conn
        self._cache: dict[str, list[MatchRecord]] = {}

    def all_records(self, league_id: str) -> list[MatchRecord]:
        if league_id not in self._cache:
            rows = self.conn.execute(
                "SELECT id, league_id, home_team_id, away_team_id, kickoff_at, ft_home, ft_away, ht_home, ht_away,"
                " result_recorded_at FROM fixtures WHERE league_id=? AND status='finished'"
                " AND result_recorded_at IS NOT NULL ORDER BY kickoff_at",
                (league_id,),
            ).fetchall()
            self._cache[league_id] = [MatchRecord(*tuple(r)) for r in rows]
        return self._cache[league_id]

    def match_records(self, league_id: str, cutoff: str) -> list[MatchRecord]:
        return [r for r in self.all_records(league_id) if r.result_recorded_at <= cutoff and r.kickoff_at < cutoff]

    def invalidate(self) -> None:
        self._cache.clear()


@dataclass(frozen=True)
class FootballFeatures:
    """MODEL B'nin futbol sinyalleri (yalnızca kesimden önceki maçlardan)."""

    form_diff: float      # son 5 maç averaj farkı (ev − deplasman)
    goal_dev: float       # iki takımın son 5 maç toplam gol ortalaması − lig ortalaması
    home_n: int
    away_n: int
    last_result_at: str | None


def football_features(records: list[MatchRecord], home: int, away: int, last_n: int = 5) -> FootballFeatures:
    by_team: dict[int, list[tuple[int, int]]] = defaultdict(list)
    totals = []
    last = None
    for r in records:  # kronolojik
        by_team[r.home_team_id].append((r.ft_home, r.ft_away))
        by_team[r.away_team_id].append((r.ft_away, r.ft_home))
        totals.append(r.ft_home + r.ft_away)
        last = r.result_recorded_at if last is None or r.result_recorded_at > last else last
    league_avg = float(np.mean(totals[-200:])) if totals else 2.6

    def form(team: int) -> tuple[float, float, int]:
        games = by_team.get(team, [])[-last_n:]
        if not games:
            return 0.0, league_avg, 0
        gd = float(np.mean([g - c for g, c in games]))
        tot = float(np.mean([g + c for g, c in games]))
        return gd, tot, len(games)

    hgd, htot, hn = form(home)
    agd, atot, an = form(away)
    return FootballFeatures(hgd - agd, (htot + atot) / 2 - league_avg, hn, an, last)


# ───────────────────────── oranlar ─────────────────────────

def latest_quotes(conn: sqlite3.Connection, fixture_ids: list[int], at: str, *, live: bool = False) -> dict:
    """Her (fikstür, market, seçenek) için `at` anında bilinen en son oran.

    Dönüş: {fixture_id: {(market_id, outcome_id): OddsQuote}}
    """
    if not fixture_ids:
        return {}
    out: dict[int, dict] = defaultdict(dict)
    qmarks = ",".join("?" for _ in fixture_ids)
    rows = conn.execute(
        f"""
        SELECT o.id, o.fixture_id, o.market_id, o.outcome_id, o.decimal_odds, o.source, s.label, s.source_type,
               s.is_iddaa, s.verified, o.captured_at, o.is_live, o.timestamp_precision
        FROM odds_snapshots o JOIN odds_sources s ON s.code = o.source
        WHERE o.fixture_id IN ({qmarks}) AND o.captured_at <= ? AND o.is_live = ?
        ORDER BY o.captured_at
        """,
        (*fixture_ids, at, 1 if live else 0),
    ).fetchall()
    for r in rows:
        out[r[1]][(r[2], r[3])] = OddsQuote(
            snapshot_id=r[0], fixture_id=r[1], market_id=r[2], outcome_id=r[3], decimal_odds=float(r[4]),
            source=r[5], source_label=r[6], source_type=r[7], is_iddaa=bool(r[8]), source_verified=bool(r[9]),
            captured_at=r[10], is_live=bool(r[11]), timestamp_precision=r[12],
        )
    return out
