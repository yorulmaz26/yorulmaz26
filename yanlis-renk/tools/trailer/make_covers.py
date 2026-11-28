#!/usr/bin/env python3
"""Video kapakları: YouTube (1280x720) ve Reels (1080x1920), TR/EN. Gerçek oyun karelerinden üretilir."""
import os, subprocess, sys, json
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = os.path.join(ROOT, "android/assets/fonts/Nunito-ExtraBold.ttf")
LOGO = {"tr": os.path.join(ROOT, "RELEASE_PACKAGE/03_Logos/Bilingual/logo_bilingual_tr_main_transparent.png"),
        "en": os.path.join(ROOT, "RELEASE_PACKAGE/03_Logos/Bilingual/logo_bilingual_en_main_transparent.png")}

def frame(video, t, out):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-i", video, "-frames:v", "1", out], check=True)
    return Image.open(out).convert("RGB")

def fit(im, w, h):
    k = min(w / im.width, h / im.height)
    return im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)

def text_pill(img, text, cx, cy, size):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, size)
    l, t, r, b = d.textbbox((0, 0), text, font=f)
    w, h = r - l, b - t
    px, py = size * 0.6, size * 0.35
    d.rounded_rectangle([cx - w / 2 - px, cy - h / 2 - py, cx + w / 2 + px, cy + h / 2 + py], radius=int(h / 2 + py), fill=(255, 255, 255, 240))
    d.text((cx - w / 2 - l, cy - h / 2 - t), text, font=f, fill=(37, 56, 88))

def main(raw, out):
    os.makedirs(os.path.join(out, "Social_Media"), exist_ok=True)
    for lang in ("tr", "en"):
        cap = json.load(open(os.path.join(ROOT, f"tools/trailer/captions_{lang}.json"), encoding="utf-8"))
        v = os.path.join(raw, f"{lang}_store.mp4")
        g1 = frame(v, 6.6, os.path.join(raw, f"cov_{lang}_a.png"))
        g2 = frame(v, 13.0, os.path.join(raw, f"cov_{lang}_b.png"))
        g3 = frame(v, 0.9, os.path.join(raw, f"cov_{lang}_c.png"))
        logo = Image.open(LOGO[lang]).convert("RGBA")
        # YouTube 1280x720: bulanık zemin + 3 gerçek oyun karesi + logo
        yt = g1.resize((1280, 2276)).crop((0, 778, 1280, 1498)).filter(ImageFilter.GaussianBlur(18)).convert("RGBA")
        for i, g in enumerate((g3, g1, g2)):
            s = fit(g, 300, 533)
            card = Image.new("RGBA", (s.width + 12, s.height + 12), (255, 255, 255, 255)); card.paste(s, (6, 6))
            yt.alpha_composite(card.rotate((-5, 0, 5)[i], expand=True, resample=Image.BICUBIC), (700 + i * 185 - 40, 95 + (i % 2) * 30))
        lg = fit(logo, 620, 420); yt.alpha_composite(lg, (40, 60))
        text_pill(yt, cap["hook"], 360, 600, 48)
        yt.convert("RGB").save(os.path.join(out, "Social_Media", f"youtube_cover_{lang}_1280x720.jpg"), quality=92)
        # Reels kapağı 1080x1920: oyun karesi + üstte logo + ortada slogan (güvenli alan içinde)
        rl = g1.convert("RGBA")
        ov = Image.new("RGBA", rl.size, (255, 255, 255, 0)); d = ImageDraw.Draw(ov)
        d.rectangle([0, 0, 1080, 760], fill=(255, 255, 255, 150)); rl.alpha_composite(ov)
        lg = fit(logo, 860, 560); rl.alpha_composite(lg, ((1080 - lg.width) // 2, 120))
        text_pill(rl, cap["hook"], 540, 820, 64)
        rl.convert("RGB").save(os.path.join(out, "Social_Media", f"reels_cover_{lang}_1080x1920.jpg"), quality=92)
    print("kapaklar tamam")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
