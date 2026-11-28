#!/usr/bin/env python3
"""Yayın paketi medya denetimi: her görselin boyutu/biçimi/alfa kanalı/dosya boyutu, sayıları, bozuk dosya kontrolü;
videoların süresi, çözünürlüğü, FPS'i, kodekleri; altyazılar; ZIP boyutları. Sonuç: 08_Test_Reports/Media_QA.md

  python3 tools/magaza/media_qa.py RELEASE_PACKAGE
"""
import glob
import json
import os
import re
import subprocess
import sys

from PIL import Image

LANGS = ["TR", "EN", "DE", "IT", "FR", "ES"]


def img(path):
    with Image.open(path) as im:
        im.verify()
    im = Image.open(path)
    alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    return im.size, im.mode, alpha, os.path.getsize(path)


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,codec_name,profile,width,height,r_frame_rate,pix_fmt,sample_rate,bit_rate",
                          "-show_entries", "format=duration,size", "-of", "json", path], capture_output=True, text=True).stdout
    return json.loads(out)


def main():
    base = sys.argv[1]
    rows, errors = [], []

    def check(cond, msg):
        if not cond: errors.append(msg)
        return cond

    def rel(p): return os.path.relpath(p, base)

    # simge
    p = os.path.join(base, "04_PlayStore/App_Icon/app_icon_512.png")
    if check(os.path.exists(p), "simge yok"):
        s, m, a, b = img(p)
        ok = s == (512, 512) and m == "RGBA" and b <= 1024 * 1024
        check(ok, f"simge kuralı: {s} {m} {b}")
        rows.append((rel(p), f"{s[0]}×{s[1]}", m, f"{b / 1024:.0f} KB", "512×512, 32 bit, ≤1024 KB", ok))
    # öne çıkan görseller
    for l in ("tr", "en"):
        p = os.path.join(base, f"04_PlayStore/Feature_Graphics/feature_graphic_{l}.png")
        if check(os.path.exists(p), f"öne çıkan görsel yok: {l}"):
            s, m, a, b = img(p)
            ok = s == (1024, 500) and not a and m == "RGB"
            check(ok, f"öne çıkan görsel kuralı: {l} {s} {m}")
            rows.append((rel(p), f"{s[0]}×{s[1]}", m, f"{b / 1024:.0f} KB", "1024×500, alfasız", ok))
    # telefon ekran görüntüleri
    for L in LANGS:
        fs = sorted(glob.glob(os.path.join(base, f"04_PlayStore/Phone_Screenshots/{L}/*.png")))
        check(len(fs) == 8, f"{L}: {len(fs)} telefon görüntüsü (8 olmalı)")
        bad = []
        for f in fs:
            s, m, a, b = img(f)
            if s != (1080, 1920) or a or b > 8 * 1024 * 1024: bad.append(os.path.basename(f))
        check(not bad, f"{L}: uygun olmayan görüntüler {bad}")
        rows.append((f"04_PlayStore/Phone_Screenshots/{L}/ ({len(fs)} dosya)", "1080×1920", "RGB", f"≤ {max((os.path.getsize(f) for f in fs), default=0) / 1024 / 1024:.1f} MB",
                     "8 adet, 9:16, alfasız, ≤8 MB", not bad and len(fs) == 8))
    # tabletler
    for folder, size in (("Tablet_7_Inch", (1200, 1920)), ("Tablet_10_Inch", (1600, 2560))):
        for L in LANGS:
            fs = sorted(glob.glob(os.path.join(base, f"04_PlayStore/{folder}/{L}/*.png")))
            bad = []
            for f in fs:
                s, m, a, b = img(f)
                lo, hi = min(s), max(s)
                if s != size or a or lo < 1080 or hi > 7680 or hi > 2.3 * lo: bad.append(os.path.basename(f))
            check(len(fs) >= 4 and not bad, f"{folder}/{L}: {len(fs)} dosya, sorunlu {bad}")
            rows.append((f"04_PlayStore/{folder}/{L}/ ({len(fs)} dosya)", f"{size[0]}×{size[1]}", "RGB", "", "≥4 adet, 1080–7680 px, oran ≤2,3", len(fs) >= 4 and not bad))
    # logolar
    for f in sorted(glob.glob(os.path.join(base, "03_Logos/*/*.png"))):
        s, m, a, b = img(f)
        want_alpha = "transparent" in f
        ok = (a == want_alpha) and min(s) >= 1000
        check(ok, f"logo: {rel(f)} {s} {m}")
        rows.append((rel(f), f"{s[0]}×{s[1]}", m, f"{b / 1024:.0f} KB", "şeffaf sürüm alfalı, diğerleri opak; ≥1000 px", ok))
    # sosyal medya
    want = {"instagram_post_1080x1080": (1080, 1080), "instagram_portrait_1080x1350": (1080, 1350), "instagram_story_1080x1920": (1080, 1920),
            "reels_cover_1080x1920": (1080, 1920), "tiktok_cover_1080x1920": (1080, 1920), "youtube_thumbnail_1280x720": (1280, 720),
            "facebook_1200x630": (1200, 630), "launch_poster_1080x1920": (1080, 1920)}
    for folder, l in (("Turkish", "tr"), ("English", "en")):
        for k, size in want.items():
            p = os.path.join(base, f"07_Social_Media/{folder}/{k}_{l}.png")
            if check(os.path.exists(p), f"sosyal medya eksik: {rel(p)}"):
                s, m, a, b = img(p)
                ok = s == size
                check(ok, f"sosyal medya boyutu: {rel(p)} {s}")
                rows.append((rel(p), f"{s[0]}×{s[1]}", m, f"{b / 1024:.0f} KB", f"{size[0]}×{size[1]}", ok))
    for l in ("tr", "en"):
        for k, size in (("youtube_cover", (1280, 720)), ("reels_cover", (1080, 1920))):
            p = os.path.join(base, f"05_Trailers/Social_Media/{k}_{l}_{size[0]}x{size[1]}.png")
            if check(os.path.exists(p), f"kapak eksik: {rel(p)}"):
                s, m, a, b = img(p)
                check(s == size, f"kapak boyutu {rel(p)} {s}")
                rows.append((rel(p), f"{s[0]}×{s[1]}", m, f"{b / 1024:.0f} KB", f"{size[0]}×{size[1]}", s == size))
    # videolar
    vrows = []
    vids = {"Vertical/trailer_{l}_vertical.mp4": (1080, 1920), "Horizontal/trailer_{l}_horizontal.mp4": (1920, 1080),
            "Social_Media/trailer_{l}_vertical_social_cta1.mp4": (1080, 1920), "Social_Media/trailer_{l}_vertical_social_cta2.mp4": (1080, 1920)}
    for l in ("tr", "en"):
        for pat, size in vids.items():
            p = os.path.join(base, "05_Trailers", pat.format(l=l))
            if not check(os.path.exists(p), f"video eksik: {rel(p)}"): continue
            d = probe(p)
            v = [s for s in d["streams"] if s["codec_type"] == "video"][0]
            au = [s for s in d["streams"] if s["codec_type"] == "audio"]
            dur = float(d["format"]["duration"])
            ok = (v["codec_name"] == "h264" and (v["width"], v["height"]) == size and v["r_frame_rate"] in ("30/1", "60/1")
                  and abs(dur - 30.0) < 0.05 and au and au[0]["codec_name"] == "aac" and v.get("pix_fmt") == "yuv420p")
            check(ok, f"video kuralı: {rel(p)}")
            vrows.append((rel(p), f"{v['width']}×{v['height']}", f"{dur:.2f} sn", f"{v['codec_name']} {v.get('profile', '')}", v["r_frame_rate"],
                          au[0]["codec_name"] if au else "-", f"{int(d['format']['size']) / 1024 / 1024:.1f} MB", ok))
    # altyazılar
    for l in ("tr", "en"):
        p = os.path.join(base, f"05_Trailers/Subtitles/trailer_{l}.srt")
        if check(os.path.exists(p), f"altyazı eksik {l}"):
            t = open(p, encoding="utf-8").read()
            n = len(re.findall(r"^\d+\s*$", t, re.M))
            times = re.findall(r"(\d\d):(\d\d):(\d\d),(\d\d\d) --> (\d\d):(\d\d):(\d\d),(\d\d\d)", t)
            ms = [(int(a) * 3600 + int(b) * 60 + int(c)) * 1000 + int(d2) for (a, b, c, d2, *_r) in times]
            me = [(int(e) * 3600 + int(f) * 60 + int(g)) * 1000 + int(h) for (*_s, e, f, g, h) in times]
            ok = n >= 5 and all(s < e for s, e in zip(ms, me)) and all(me[i] <= ms[i + 1] for i in range(len(ms) - 1)) and max(me) <= 30000
            check(ok, f"altyazı biçimi {l}")
            rows.append((rel(p), f"{n} satır", "SRT UTF-8", "", "çakışmasız, ≤30 sn", ok))
    # zip
    zrows = []
    for z in sorted(glob.glob(os.path.join(base, "ZIP/*.zip"))):
        mb = os.path.getsize(z) / 1024 / 1024
        check(mb <= 30, f"ZIP 30 MB'ı aşıyor: {z}")
        zrows.append((rel(z), f"{mb:.1f} MB", mb <= 30))

    L = ["# Medya denetimi (otomatik)", "", f"Komut: `python3 tools/magaza/media_qa.py RELEASE_PACKAGE` · Sonuç: **{'TÜMÜ UYGUN' if not errors else str(len(errors)) + ' SORUN'}**", ""]
    if errors: L += ["## Sorunlar", ""] + [f"- {e}" for e in errors] + [""]
    L += ["## Görseller", "", "| Dosya | Boyut | Kip | Dosya boyutu | Kural | Uygun |", "|---|---|---|---|---|---|"]
    L += [f"| `{a}` | {b} | {c} | {d} | {e} | {'✓' if f else '✗'} |" for a, b, c, d, e, f in rows]
    L += ["", "## Videolar", "", "| Dosya | Çözünürlük | Süre | Video | FPS | Ses | Boyut | Uygun |", "|---|---|---|---|---|---|---|---|"]
    L += [f"| `{r[0]}` | {r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} | {r[6]} | {'✓' if r[7] else '✗'} |" for r in vrows]
    L += ["", "## ZIP arşivleri (≤ 30 MB)", "", "| Arşiv | Boyut | Uygun |", "|---|---|---|"]
    L += [f"| `{a}` | {b} | {'✓' if c else '✗'} |" for a, b, c in zrows]
    counts = {
        "telefon ekran görüntüsü": len(glob.glob(os.path.join(base, "04_PlayStore/Phone_Screenshots/*/*.png"))),
        "tablet ekran görüntüsü": len(glob.glob(os.path.join(base, "04_PlayStore/Tablet_*/*/*.png"))),
        "logo": len(glob.glob(os.path.join(base, "03_Logos/*/*.png"))),
        "sosyal medya görseli": len(glob.glob(os.path.join(base, "07_Social_Media/*/*.png"))),
        "video kapağı": len(glob.glob(os.path.join(base, "05_Trailers/Social_Media/*.png"))),
        "video (benzersiz)": len(glob.glob(os.path.join(base, "05_Trailers/Vertical/*.mp4")) + glob.glob(os.path.join(base, "05_Trailers/Horizontal/*.mp4"))
                                 + glob.glob(os.path.join(base, "05_Trailers/Social_Media/*.mp4"))),
    }
    L += ["", "## Sayılar", ""] + [f"- {k}: {v}" for k, v in counts.items()]
    os.makedirs(os.path.join(base, "08_Test_Reports"), exist_ok=True)
    open(os.path.join(base, "08_Test_Reports", "Media_QA.md"), "w", encoding="utf-8").write("\n".join(L) + "\n")
    print("sorun yok" if not errors else "\n".join(errors))
    print(counts)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
