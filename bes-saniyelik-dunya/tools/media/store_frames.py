#!/usr/bin/env python3
"""Mağaza ekran görüntüsü çerçevesi: gerçek oyun görüntüsü (oranı korunarak küçültülür) + üstte dile göre başlık.

Kullanım: python3 store_frames.py <ham_görüntü_klasörü> <dil> <çıktı_klasörü>
Ham klasörde s1.png..s8.png (1920x1080 oyun görüntüsü) beklenir. Çıktı: 1920x1080 JPEG, saydamlık yok.
"""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.join(HERE, "..", "brand", "fonts", "Nunito-Variable.ttf")
CAPTIONS = json.load(open(os.path.join(HERE, "captions.json"), encoding="utf-8"))
NAVY = (32, 56, 88)

def font(size, w=1000):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f

def background(w, h):
    """Oyunun açık gökyüzü renkleri: üstten açık maviye geçiş, hafif bulut lekeleri."""
    top, bot = (126, 206, 250), (214, 240, 255)
    bg = Image.new("RGB", (w, h))
    px = bg.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3))
        for x in range(w): px[x, y] = c
    clouds = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(clouds)
    for (cx, cy, r) in [(180, 90, 70), (260, 110, 90), (1640, 70, 80), (1740, 100, 70), (980, 40, 60), (1080, 60, 50)]:
        d.ellipse((cx - r * 1.6, cy - r * 0.7, cx + r * 1.6, cy + r * 0.7), fill=(255, 255, 255, 150))
    bg.paste(clouds.filter(ImageFilter.GaussianBlur(12)), (0, 0), clouds.filter(ImageFilter.GaussianBlur(12)))
    return bg

def fit_text(text, maxw, size):
    while size > 30:
        f = font(size)
        if f.getlength(text) <= maxw: return f
        size -= 4
    return font(size)

def frame(shot, caption, out, W=1920, H=1080):
    bg = background(W, H)
    band = 190
    scale = min((W - 120) / shot.width, (H - band - 40) / shot.height)
    sw, sh = int(shot.width * scale), int(shot.height * scale)
    x, y = (W - sw) // 2, band
    # gölge + yuvarlak köşe
    mask = Image.new("L", (sw, sh), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), 28, fill=255)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x - 4, y + 10, x + sw + 4, y + sh + 18), 32, fill=(30, 60, 100, 110))
    bg.paste(shadow.filter(ImageFilter.GaussianBlur(14)), (0, 0), shadow.filter(ImageFilter.GaussianBlur(14)))
    border = Image.new("L", (sw + 16, sh + 16), 0); ImageDraw.Draw(border).rounded_rectangle((0, 0, sw + 15, sh + 15), 34, fill=255)
    bg.paste((255, 255, 255), (x - 8, y - 8), border)
    bg.paste(shot.resize((sw, sh), Image.LANCZOS).convert("RGB"), (x, y), mask)
    d = ImageDraw.Draw(bg)
    f = fit_text(caption, W - 160, 84)
    tw = f.getlength(caption)
    ty = (band - f.size) // 2 - 6
    d.text(((W - tw) / 2, ty), caption, font=f, fill=(255, 255, 255), stroke_width=12, stroke_fill=NAVY)
    d.text(((W - tw) / 2, ty), caption, font=f, fill=(255, 255, 255))
    bg.save(out, "JPEG", quality=92, optimize=True)

if __name__ == "__main__":
    src, lang, dst = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(dst, exist_ok=True)
    caps = CAPTIONS[lang]
    for i in range(1, 9):
        p = os.path.join(src, f"s{i}.png")
        frame(Image.open(p), caps[i - 1], os.path.join(dst, f"screenshot_{i:02d}_{lang}.jpg"))
    print("tamam", lang)
