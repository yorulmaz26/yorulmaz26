"""Desteklenen 21 futbol organizasyonu.

v6.2'deki özgün liste depoda bulunmadığından bu liste yeniden kurulmuştur (bkz. docs/VARSAYIMLAR.md).
Kodlar kararlıdır; liste değiştiğinde yalnızca bu dosya güncellenir.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class League:
    id: str
    name: str
    country: str
    type: str  # "league" | "cup" | "continental"
    #: Lig kupası/eleme turu olan organizasyonlarda tek maçlık normal süre kuralları geçerlidir.
    football_data_code: str | None = None  # football-data.co.uk dosya kodu (varsa)


LEAGUES: tuple[League, ...] = (
    League("tr-super-lig", "Süper Lig", "Türkiye", "league", "T1"),
    League("tr-1-lig", "TFF 1. Lig", "Türkiye", "league"),
    League("tr-kupa", "Türkiye Kupası", "Türkiye", "cup"),
    League("en-premier-league", "Premier League", "İngiltere", "league", "E0"),
    League("en-championship", "Championship", "İngiltere", "league", "E1"),
    League("es-la-liga", "La Liga", "İspanya", "league", "SP1"),
    League("it-serie-a", "Serie A", "İtalya", "league", "I1"),
    League("de-bundesliga", "Bundesliga", "Almanya", "league", "D1"),
    League("fr-ligue-1", "Ligue 1", "Fransa", "league", "F1"),
    League("nl-eredivisie", "Eredivisie", "Hollanda", "league", "N1"),
    League("pt-primeira-liga", "Primeira Liga", "Portekiz", "league", "P1"),
    League("be-pro-league", "Pro League", "Belçika", "league", "B1"),
    League("sco-premiership", "Premiership", "İskoçya", "league", "SC0"),
    League("gr-super-league", "Super League", "Yunanistan", "league", "G1"),
    League("at-bundesliga", "Bundesliga", "Avusturya", "league"),
    League("ch-super-league", "Super League", "İsviçre", "league"),
    League("dk-superliga", "Superliga", "Danimarka", "league"),
    League("br-serie-a", "Série A", "Brezilya", "league"),
    League("uefa-champions-league", "UEFA Şampiyonlar Ligi", "Avrupa", "continental"),
    League("uefa-europa-league", "UEFA Avrupa Ligi", "Avrupa", "continental"),
    League("uefa-conference-league", "UEFA Konferans Ligi", "Avrupa", "continental"),
)

LEAGUES_BY_ID = {league.id: league for league in LEAGUES}

assert len(LEAGUES) == 21, "Sistem 21 futbol organizasyonunu destekler"
assert len(LEAGUES_BY_ID) == 21, "Lig kodları benzersiz olmalı"


def get_league(league_id: str) -> League:
    try:
        return LEAGUES_BY_ID[league_id]
    except KeyError as exc:  # pragma: no cover - savunma
        raise KeyError(f"Bilinmeyen organizasyon: {league_id}") from exc
