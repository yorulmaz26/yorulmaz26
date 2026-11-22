#!/usr/bin/env python3
"""football-data.co.uk dosyalarını `veri/football-data/` klasörüne değiştirmeden kopyalar (GitHub yansısı).

Bazı ağlarda football-data.co.uk'a erişilemediği için program, dosyaları bu klasörün GitHub adresinden de
alabilir (`akilli_tahmin/data/football_data_feed.py`, `MIRROR_URL`). Bu betik her gün çalıştırılır; yalnızca
içeriği değişen dosyaları yazar ve değişen dosyaların listesini basar.

Kapsam: 12 ana lig için son 3 sezon dosyası + `fixtures.csv`; 4 ek lig dosyası + `new_league_fixtures.csv`.

Kullanım:
    python scripts/mirror_football_data.py            # indir ve yaz
    python scripts/mirror_football_data.py --check    # yalnızca karşılaştır, yazma
Çıkış kodu: 0 başarılı, 1 en az bir dosya indirilemedi (diğerleri yine yazılır).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from akilli_tahmin.data.football_data_feed import (  # noqa: E402
    EXTRA_LEAGUES, MAIN_DIVISIONS, Fetcher, _looks_like_csv, plan_files)
from akilli_tahmin.db.database import to_iso, utcnow  # noqa: E402

OUT = ROOT / "veri" / "football-data"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="Yalnızca karşılaştır, dosya yazma")
    a = ap.parse_args(argv)
    plans = plan_files(to_iso(utcnow()), tuple(MAIN_DIVISIONS), 3, tuple(EXTRA_LEAGUES))
    fetcher = Fetcher(None, mirrors=())  # yalnızca asıl kaynak (yansı kendinden beslenmez)
    changed, failed = [], []
    for p in plans:
        try:
            data = fetcher.get_bytes(p.path)  # baytlar olduğu gibi kopyalanır
        except RuntimeError as exc:
            failed.append(f"{p.path}: {exc}")
            continue
        if len(data) < 50 or not _looks_like_csv(data.decode("utf-8-sig", errors="replace")):
            failed.append(f"{p.path}: beklenen CSV değil")
            continue
        dst = OUT / p.path
        if dst.exists() and dst.read_bytes() == data:
            continue
        changed.append(p.path)
        if not a.check:
            dst.parent.mkdir(parents=True, exist_ok=True)
            tmp = dst.with_suffix(dst.suffix + ".tmp")
            tmp.write_bytes(data)
            tmp.replace(dst)
    print(f"{len(plans)} dosya denendi, {len(changed)} değişti, {len(failed)} başarısız")
    for c in changed:
        print("  değişti:", c)
    for f in failed:
        print("  HATA:", f, file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
