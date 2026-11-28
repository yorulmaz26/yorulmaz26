#!/usr/bin/env python3
"""Yayın paketini tamamlar: tablet görüntülerini ve video kaynak kliplerini yerleştirir, ZIP arşivlerini (≤ 30 MB) üretir.

  python3 tools/magaza/package_media.py --rec KAYIT --out RELEASE_PACKAGE     # tablet + kaynak klipler
  python3 tools/magaza/package_media.py --zip-only --out RELEASE_PACKAGE      # yalnızca ZIP arşivleri
"""
import argparse
import glob
import os
import shutil
import subprocess
import zipfile

from PIL import Image

LANGS = ["tr", "en", "de", "it", "fr", "es"]
LIMIT = 30 * 1024 * 1024
TRAILER_CLIPS = ["f_mantik", "a_temel", "b_prizma", "y_karma", "d_blok", "h_bolumler", "e_kapi", "g_gorunum", "c_renk"]
TABLET_NAMES = {"01_menu": "menu", "02_oyun_mantik": "gameplay_logic", "03_oyun_prizma": "gameplay_prism", "04_bolumler": "levels",
                "05_gunluk": "daily"}


def tablets(rec, out):
    n = 0
    for lang in LANGS:
        for sub, folder in (("tablet7", "Tablet_7_Inch"), ("tablet10", "Tablet_10_Inch")):
            src = os.path.join(rec, lang, sub)
            if not os.path.isdir(src): continue
            od = os.path.join(out, "04_PlayStore", folder, lang.upper())
            os.makedirs(od, exist_ok=True)
            for i, (k, v) in enumerate(TABLET_NAMES.items(), 1):
                f = os.path.join(src, k + ".png")
                if os.path.exists(f):
                    # kayıt ARGB'dir (tam opak): Play için alfasız 24 bit PNG
                    Image.open(f).convert("RGB").save(os.path.join(od, f"{lang}_{sub}_{i:02d}_{v}.png"), optimize=True); n += 1
    print("tablet görüntüleri:", n)


def source_clips(rec, out):
    """Fragmanlarda kullanılan ham oynanış klipleri (düzenlenebilir kaynak): 1080×1920, 30 FPS, H.264."""
    for lang in ("tr", "en"):
        od = os.path.join(out, "05_Trailers", "Source_Files", f"clips_{lang}")
        os.makedirs(od, exist_ok=True)
        for c in TRAILER_CLIPS:
            d = os.path.join(rec, lang, "clips", c)
            if not os.path.isdir(d): continue
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", "30", "-i", os.path.join(d, "%05d.jpg"), "-c:v", "libx264",
                            "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p", os.path.join(od, f"{c}.mp4")], check=True)
            shutil.copy(os.path.join(d, "events.json"), os.path.join(od, f"{c}.events.json"))
    print("kaynak klipler hazır")


def zips(out):
    zd = os.path.join(out, "ZIP")
    os.makedirs(zd, exist_ok=True)
    for old in glob.glob(os.path.join(zd, "*.zip")): os.remove(old)
    groups = [("03_Logos", ["03_Logos"]),
              ("04_PlayStore_Icon_FeatureGraphics", ["04_PlayStore/App_Icon", "04_PlayStore/Feature_Graphics"])]
    groups += [(f"04_PlayStore_Phone_Screenshots_{l.upper()}", [f"04_PlayStore/Phone_Screenshots/{l.upper()}"]) for l in LANGS]
    groups += [("04_PlayStore_Tablet_7_Inch", ["04_PlayStore/Tablet_7_Inch"]), ("04_PlayStore_Tablet_10_Inch", ["04_PlayStore/Tablet_10_Inch"])]
    groups += [("07_Social_Media_Turkish", ["07_Social_Media/Turkish"]), ("07_Social_Media_English", ["07_Social_Media/English"]),
               ("05_Trailers_Covers_Subtitles", ["05_Trailers/Subtitles", "05_Trailers/Social_Media/*.png"])]
    for name, parts in groups:
        files = []
        for p in parts:
            pat = os.path.join(out, p)
            if any(ch in p for ch in "*?"): files += sorted(glob.glob(pat))
            else:
                for root, _, fs in os.walk(pat):
                    files += [os.path.join(root, f) for f in sorted(fs)]
        if not files: continue
        part, size, idx, batch = 1, 0, 0, []
        chunks = []
        for f in files:
            s = os.path.getsize(f)
            if batch and size + s > LIMIT * 0.97:
                chunks.append(batch); batch, size = [], 0
            batch.append(f); size += s
        if batch: chunks.append(batch)
        for k, chunk in enumerate(chunks, 1):
            zname = f"{name}.zip" if len(chunks) == 1 else f"{name}_part{k}.zip"
            zp = os.path.join(zd, zname)
            with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
                for f in chunk: z.write(f, os.path.relpath(f, out))
            mb = os.path.getsize(zp) / 1024 / 1024
            assert mb <= 30, zp
            print(f"{zname}: {len(chunk)} dosya, {mb:.1f} MB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rec")
    ap.add_argument("--out", required=True)
    ap.add_argument("--zip-only", action="store_true")
    a = ap.parse_args()
    if not a.zip_only:
        tablets(a.rec, a.out)
        source_clips(a.rec, a.out)
    else:
        zips(a.out)


if __name__ == "__main__":
    main()
