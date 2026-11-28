#!/usr/bin/env python3
"""Teslim paketi otomatik denetimi: görsel ölçüleri/biçimleri, video süre ve kodekleri, çeviriler, mağaza metinleri."""
import glob, json, os, subprocess, sys
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")); P = os.path.join(ROOT, "RELEASE_PACKAGE")
ok = []; bad = []
def check(cond, msg): (ok if cond else bad).append(msg)
def img(path, size=None, alpha_ok=False, maxkb=None):
    im = Image.open(path); rel = os.path.relpath(path, P)
    if size: check(im.size == size, f"{rel}: {im.size} (beklenen {size})")
    if not alpha_ok: check(im.mode in ("RGB", "L"), f"{rel}: saydamlık yok ({im.mode})")
    if maxkb: check(os.path.getsize(path) <= maxkb * 1024, f"{rel}: {os.path.getsize(path)//1024} KB ≤ {maxkb} KB")
img(f"{P}/04_PlayStore/App_Icon/app_icon_512.png", (512, 512), alpha_ok=True, maxkb=1024)
for l in ("tr", "en"): img(f"{P}/04_PlayStore/Feature_Graphics/feature_graphic_{l}.png", (1024, 500))
for L in ("TR", "EN", "DE", "IT", "FR", "ES"):
    fs = sorted(glob.glob(f"{P}/04_PlayStore/Phone_Screenshots/{L}/*.jpg")); check(len(fs) == 8, f"{L}: {len(fs)}/8 telefon ekran görüntüsü")
    for f in fs: img(f, (1920, 1080), maxkb=8192)
for d, s in (("Tablet_7_Inch", (1920, 1200)), ("Tablet_10_Inch", (2560, 1600))):
    fs = glob.glob(f"{P}/04_PlayStore/{d}/*.jpg"); check(len(fs) >= 4, f"{d}: {len(fs)} görüntü (en az 4)")
    for f in fs: img(f, s)
sizes = {"instagram_post": (1080, 1080), "instagram_portrait": (1080, 1350), "instagram_story": (1080, 1920), "reels_cover": (1080, 1920),
         "tiktok_cover": (1080, 1920), "youtube_thumbnail": (1280, 720), "facebook": (1200, 630), "launch_poster": (1080, 1920)}
for folder in ("Turkish", "English"):
    fs = glob.glob(f"{P}/07_Social_Media/{folder}/*.jpg"); check(len(fs) == 8, f"sosyal {folder}: {len(fs)}/8")
    for f in fs:
        k = next(k for k in sizes if os.path.basename(f).startswith(k)); img(f, sizes[k])
for f in sorted(glob.glob(f"{P}/05_Trailers/*/*.mp4")):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_name,width,height,r_frame_rate", "-show_entries", "format=duration", "-of", "json", f], capture_output=True, text=True).stdout
    j = json.loads(out); v = [s for s in j["streams"] if s["codec_name"] == "h264"]; a = [s for s in j["streams"] if s["codec_name"] == "aac"]
    dur = float(j["format"]["duration"]); rel = os.path.relpath(f, P)
    want = (1920, 1080) if "horizontal" in f else (1080, 1920)
    check(bool(v) and bool(a), f"{rel}: H.264 + AAC")
    check(v and (v[0]["width"], v[0]["height"]) == want, f"{rel}: {want[0]}x{want[1]}")
    check(abs(dur - 30.0) < 0.05, f"{rel}: süre {dur:.2f} sn")
    check(v and v[0]["r_frame_rate"] == "30/1", f"{rel}: 30 fps")
for z in glob.glob(f"{P}/ZIP/*.zip"): check(os.path.getsize(z) <= 30 * 1024 * 1024, f"{os.path.basename(z)}: {os.path.getsize(z)/1048576:.1f} MB ≤ 30 MB")
r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "i18n", "check_i18n.py")], capture_output=True, text=True); check(r.returncode == 0, "dil dosyaları: 0 eksik/hatalı çeviri")
r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "i18n", "check_store_texts.py")], capture_output=True, text=True); check(r.returncode == 0, "mağaza metinleri karakter sınırları")
for k in ("SIFRE", "storePassword", "keyPassword", "BEGIN PRIVATE KEY"):
    hits = [f for f in glob.glob(f"{P}/**/*", recursive=True) if os.path.isfile(f) and f.endswith((".md", ".txt", ".json", ".properties")) and k in open(f, errors="ignore").read()]
    check(not hits, f"pakette gizli bilgi yok ({k})")
check(not glob.glob(f"{P}/**/*.jks", recursive=True) and not glob.glob(f"{P}/**/*.keystore", recursive=True), "pakette imza anahtarı dosyası yok")
print(f"GEÇTİ {len(ok)} · KALDI {len(bad)}")
for b in bad: print("  KALDI:", b)
sys.exit(1 if bad else 0)
