#!/usr/bin/env python3
"""Işık Yolu / Prismway logo paketi.

Amblem, oyundaki LogoView ile aynı geometriden (108 birimlik kare) çizilir: kaynaktan çıkan ışın iki aynadan
yansıyıp hedef halkaya ulaşır. Yazı tipi oyunun arayüzündeki Roboto (Apache 2.0).

  python3 tools/magaza/logos.py --fonts <Roboto klasörü> --out RELEASE_PACKAGE/03_Logos

Çıktılar (hepsi PNG):
  Turkish/    logo_tr_dark.png, logo_tr_light.png, logo_tr_transparent.png        (Işık Yolu büyük, PRISMWAY küçük)
  English/    logo_en_dark.png, logo_en_light.png, logo_en_transparent.png        (Prismway büyük, IŞIK YOLU küçük)
  Bilingual/  logo_bilingual_{dark,light,transparent}.png (yan yana), logo_bilingual_stacked_{dark,light}.png
  Transparent/ emblem_transparent_1024.png (yazısız amblem), emblem_on_dark_1024.png, emblem_on_light_1024.png
               ve yukarıdaki logoların şeffaf kopyaları
"""
import argparse
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

GOLD, TURQ, PURPLE, WHITE = (0xF5, 0xC5, 0x42), (0x20, 0xB8, 0xA6), (0x6D, 0x5D, 0xFB), (255, 255, 255)
NAVY, NAVY2 = (0x22, 0x30, 0x5E), (0x3D, 0x4D, 0x92)
TOP, BOTTOM = (0x3D, 0x4D, 0x92), (0x21, 0x30, 0x62)
NAME_TR, NAME_EN = "Işık Yolu", "Prismway"


def font(d, name, size):
    return ImageFont.truetype(os.path.join(d, name), size)


def emblem(size, light_bg=False, ss=4):
    """Amblemi size×size şeffaf katman olarak çizer (ss kat süper örnekleme)."""
    S = size * ss
    PAD = 0.09  # parıltı kenarda kesilmesin: 108 birimlik çizim kenarlardan %9 içeride
    k = S * (1 - 2 * PAD) / 108.0
    off = S * PAD
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    pts = [(24 * k, 72 * k), (58 * k, 72 * k), (58 * k, 36 * k), (84 * k, 36 * k)]
    gd.line(pts, fill=GOLD + (150,), width=int(15 * k), joint="curve")
    gd.ellipse([(88 - 17) * k, (36 - 17) * k, (88 + 17) * k, (36 + 17) * k], fill=TURQ + (120,))
    glow = glow.filter(ImageFilter.GaussianBlur(5 * k))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)

    def cap_line(width, color):
        d.line(pts, fill=color, width=int(width * k), joint="curve")
        r = width * k / 2
        for (x, y) in (pts[0], pts[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)

    cap_line(13, GOLD + (90,))
    cap_line(5.5, GOLD + (255,))
    cap_line(2.0, WHITE + (255,))
    # aynalar: -45° dönük yuvarlak çubuklar
    mirror = (0x7C, 0x8F, 0xBE) if light_bg else (0xE3, 0xEA, 0xF6)
    for cy in (72, 36):
        m = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(m).rounded_rectangle([46 * k, (cy - 2) * k, 70 * k, (cy + 2.5) * k], radius=2 * k, fill=mirror + (255,))
        m = m.rotate(45, center=(58 * k, cy * k), resample=Image.BICUBIC)
        img = Image.alpha_composite(img, m)
    d = ImageDraw.Draw(img)
    # kaynak
    d.rounded_rectangle([10 * k, 62 * k, 34 * k, 82 * k], radius=5 * k, fill=PURPLE + (255,))
    d.ellipse([(22 - 4.5) * k, (72 - 4.5) * k, (22 + 4.5) * k, (72 + 4.5) * k], fill=WHITE + (255,))
    # hedef
    d.ellipse([(88 - 9) * k, (36 - 9) * k, (88 + 9) * k, (36 + 9) * k], outline=TURQ + (255,), width=int(4 * k))
    d.ellipse([(88 - 4) * k, (36 - 4) * k, (88 + 4) * k, (36 + 4) * k], fill=(0xBF, 0xFF, 0xF6, 255))
    shifted = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shifted.alpha_composite(img, (int(off), int(off)))
    return shifted.resize((size, size), Image.LANCZOS)


def gradient_text(text, f, colors, letter=0.0, stroke=0):
    """Yatay altın → beyaz → turkuaz geçişli yazı (oyundaki Ui.gradientText gibi)."""
    tmp = ImageDraw.Draw(Image.new("L", (10, 10)))
    w = int(sum(tmp.textlength(ch, font=f) for ch in text) + letter * f.size * (len(text) - 1)) + 4 + stroke * 2
    asc, desc = f.getmetrics()
    h = asc + desc + 4 + stroke * 2
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    x = 2 + stroke
    for ch in text:
        md.text((x, 2 + stroke), ch, font=f, fill=255, stroke_width=stroke, stroke_fill=255)
        x += tmp.textlength(ch, font=f) + letter * f.size
    if len(colors) == 1:
        fill = Image.new("RGBA", (w, h), colors[0] + (255,))
    else:
        xs = np.linspace(0, 1, w)
        stops = np.linspace(0, 1, len(colors))
        rgb = np.stack([np.interp(xs, stops, [c[i] for c in colors]) for i in range(3)], axis=-1)
        arr = np.repeat(rgb[None, :, :], h, axis=0).astype(np.uint8)
        fill = Image.fromarray(arr, "RGB").convert("RGBA")
    fill.putalpha(mask)
    return fill


def background(w, h):
    y = np.linspace(0, 1, h)[:, None]
    top, bot = np.array(TOP, float), np.array(BOTTOM, float)
    img = (top[None, None, :] * (1 - y[..., None]) + bot[None, None, :] * y[..., None]) * np.ones((h, w, 1))
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    for (cx, cy, r, col, a) in [(0.15, 0.2, 0.6, TURQ, 0.18), (0.85, 0.85, 0.6, GOLD, 0.12), (0.8, 0.1, 0.5, PURPLE, 0.18)]:
        dd = np.sqrt(((xx / w - cx) * w / max(w, h)) ** 2 + ((yy / h - cy) * h / max(w, h)) ** 2) / r
        g = np.clip(1 - dd, 0, 1) ** 2 * a
        img = img * (1 - g[..., None]) + np.array(col, float)[None, None, :] * g[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def light_background(w, h):
    y = np.linspace(0, 1, h)[:, None]
    a, b = np.array((0xF7, 0xF9, 0xFF), float), np.array((0xE6, 0xEC, 0xFA), float)
    img = (a[None, None, :] * (1 - y[..., None]) + b[None, None, :] * y[..., None]) * np.ones((h, w, 1))
    return Image.fromarray(img.astype(np.uint8), "RGB").convert("RGBA")


def glow_of(layer, radius, color, alpha):
    a = layer.split()[3].filter(ImageFilter.GaussianBlur(radius))
    g = Image.new("RGBA", layer.size, color + (0,))
    g.putalpha(a.point(lambda v: int(v * alpha)))
    return g


def wordmark(fonts, main, second, theme, scale=1.0):
    """Ana ad + ikincil ad bloğu (şeffaf)."""
    fm = font(fonts, "Roboto-Black.ttf", int(230 * scale))
    fs = font(fonts, "Roboto-Medium.ttf", int(84 * scale))
    if theme == "light":
        m = gradient_text(main, fm, [NAVY, NAVY2])
        s = gradient_text(second, fs, [(0xC9, 0x8F, 0x1E)], letter=0.32)
    else:
        m = gradient_text(main, fm, [(0xFF, 0xE0, 0x8A), WHITE, (0x7D, 0xF0, 0xE0)])
        s = gradient_text(second, fs, [(0xBF, 0xD3, 0xF2)], letter=0.32)
    gap = int(10 * scale)
    w = max(m.width, s.width) + int(80 * scale)
    h = m.height + gap + s.height + int(60 * scale)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    lay.alpha_composite(m, ((w - m.width) // 2, int(20 * scale)))
    lay.alpha_composite(s, ((w - s.width) // 2, int(20 * scale) + m.height + gap))
    if theme != "light":
        out = Image.alpha_composite(out, glow_of(lay, 22 * scale, TURQ, 0.45))
    return Image.alpha_composite(out, lay)


def stacked(fonts, main, second, theme, size=2048):
    """Kare logo: amblem üstte, adlar altta."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if theme == "dark": canvas = background(size, size)
    elif theme == "light": canvas = light_background(size, size)
    em = emblem(int(size * 0.5), light_bg=(theme == "light"))
    wm = wordmark(fonts, main, second, "light" if theme == "light" else "dark")
    maxw = int(size * 0.9)
    if wm.width > maxw: wm = wm.resize((maxw, int(wm.height * maxw / wm.width)), Image.LANCZOS)
    total = em.height + wm.height
    y0 = (size - total) // 2
    canvas.alpha_composite(em, ((size - em.width) // 2, y0))
    canvas.alpha_composite(wm, ((size - wm.width) // 2, y0 + em.height - int(size * 0.02)))
    return canvas


def horizontal(fonts, theme, h=1000):
    """İki dilli yan yana logo: amblem | Işık Yolu  ·  Prismway (eşit ağırlık)."""
    fm = font(fonts, "Roboto-Black.ttf", 190)
    if theme == "light":
        a = gradient_text(NAME_TR, fm, [NAVY, NAVY2]); b = gradient_text(NAME_EN, fm, [NAVY2, (0x6D, 0x5D, 0xFB)])
    else:
        a = gradient_text(NAME_TR, fm, [(0xFF, 0xE0, 0x8A), WHITE]); b = gradient_text(NAME_EN, fm, [WHITE, (0x7D, 0xF0, 0xE0)])
    em = emblem(int(h * 0.86), light_bg=(theme == "light"))
    sep = 70
    w = em.width + 60 + a.width + sep * 2 + b.width + 160
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if theme == "dark": canvas = background(w, h)
    elif theme == "light": canvas = light_background(w, h)
    x = 80
    canvas.alpha_composite(em, (x, (h - em.height) // 2))
    x += em.width + 60
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ty = (h - a.height) // 2
    lay.alpha_composite(a, (x, ty))
    x += a.width + sep
    d = ImageDraw.Draw(lay)
    d.rounded_rectangle([x - 6, h * 0.30, x + 6, h * 0.70], radius=6, fill=(GOLD if theme != "light" else (0xC9, 0x8F, 0x1E)) + (230,))
    x += sep
    lay.alpha_composite(b, (x, ty))
    if theme != "light": canvas = Image.alpha_composite(canvas, glow_of(lay, 18, TURQ, 0.4))
    return Image.alpha_composite(canvas, lay)


def save(img, path, opaque=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    (img.convert("RGB") if opaque else img).save(path, optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    o = a.out
    up_tr = "IŞIK YOLU"  # Türkçe büyük harf (Python upper() "I" yerine "I" verir ama ş/ı için açık yazıldı)
    for theme in ("dark", "light", "transparent"):
        save(stacked(a.fonts, NAME_TR, NAME_EN.upper(), theme), f"{o}/Turkish/logo_tr_{theme}.png", theme != "transparent")
        save(stacked(a.fonts, NAME_EN, up_tr, theme), f"{o}/English/logo_en_{theme}.png", theme != "transparent")
        save(horizontal(a.fonts, theme), f"{o}/Bilingual/logo_bilingual_{theme}.png", theme != "transparent")
    for theme in ("dark", "light"):
        # iki dilli dikey: iki ad eşit boyda alt alta
        size = 2048
        canvas = background(size, size) if theme == "dark" else light_background(size, size)
        em = emblem(int(size * 0.46), light_bg=(theme == "light"))
        fm = font(a.fonts, "Roboto-Black.ttf", 200)
        cols = [NAVY, NAVY2] if theme == "light" else [(0xFF, 0xE0, 0x8A), WHITE, (0x7D, 0xF0, 0xE0)]
        t1, t2 = gradient_text(NAME_TR, fm, cols), gradient_text(NAME_EN, fm, cols)
        y = (size - em.height - t1.height - t2.height - 40) // 2
        canvas.alpha_composite(em, ((size - em.width) // 2, y)); y += em.height
        lay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        lay.alpha_composite(t1, ((size - t1.width) // 2, y)); y += t1.height
        ImageDraw.Draw(lay).rounded_rectangle([size / 2 - 120, y + 8, size / 2 + 120, y + 22], radius=7,
                                              fill=(GOLD if theme == "dark" else (0xC9, 0x8F, 0x1E)) + (230,))
        lay.alpha_composite(t2, ((size - t2.width) // 2, y + 40))
        if theme == "dark": canvas = Image.alpha_composite(canvas, glow_of(lay, 22, TURQ, 0.45))
        save(Image.alpha_composite(canvas, lay), f"{o}/Bilingual/logo_bilingual_stacked_{theme}.png", True)
    em = emblem(1024)
    save(em, f"{o}/Transparent/emblem_transparent_1024.png")
    save(emblem(1024, light_bg=True), f"{o}/Transparent/emblem_transparent_light_bg_1024.png")
    bg = background(1024, 1024); bg.alpha_composite(emblem(820), (102, 102)); save(bg, f"{o}/Transparent/emblem_on_dark_1024.png", True)
    lb = light_background(1024, 1024); lb.alpha_composite(emblem(820, light_bg=True), (102, 102)); save(lb, f"{o}/Transparent/emblem_on_light_1024.png", True)
    for src in (f"{o}/Turkish/logo_tr_transparent.png", f"{o}/English/logo_en_transparent.png", f"{o}/Bilingual/logo_bilingual_transparent.png"):
        Image.open(src).save(f"{o}/Transparent/" + os.path.basename(src))
    print("ok")


if __name__ == "__main__":
    main()
