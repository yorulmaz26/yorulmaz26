#!/usr/bin/env python3
"""Marka görselleri: iki dilli logo, ikincil ad yazıları, logo paketi.

Kaynak: art_input/parca2/game_logo.png (kronometre amblemi + "Saniyelik Dünya"). İngilizce logo aynı amblem ve aynı
yazı stiliyle (turkuaz/pembe dolgu, beyaz iç kontur, lacivert dış kontur) "Stopwatch Shuffle" olarak çizilir.
Yazı tipi: Nunito Black (SIL OFL, tools/brand/fonts).
Kullanım: python3 tools/brand/brand.py [çıktı_klasörü_logolar]
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONT = os.path.join(ROOT, "tools", "brand", "fonts", "Nunito-Variable.ttf")
SRC = os.path.join(ROOT, "art_input", "parca2", "game_logo.png")
TEAL = (32, 184, 192); PINK = (248, 136, 160); WHITE = (250, 250, 250); NAVY = (32, 56, 88)
EN_NAME = ("Stopwatch", "Shuffle"); TR_NAME = ("Saniyelik", "Dünya")

def font(size, weight=1000):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([weight])
    except Exception: pass
    return f

def word(text, size, fill, white=None, navy=None, shadow=True):
    """Logo yazısı: dolgu + beyaz iç kontur + lacivert dış kontur + hafif gölge (saydam arka plan)."""
    f = font(size)
    white = white if white is not None else max(3, size // 11)
    navy = navy if navy is not None else max(2, size // 17)
    pad = white + navy + 12
    l, t, r, b = f.getbbox(text)
    w, h = r - l + 2 * pad, b - t + 2 * pad + 8
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    org = (pad - l, pad - t)
    if shadow:
        sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((org[0], org[1] + max(3, size // 22)), text, font=f, fill=(20, 40, 70, 90), stroke_width=white + navy, stroke_fill=(20, 40, 70, 90))
        out.alpha_composite(sh.filter(ImageFilter.GaussianBlur(max(1, size // 40))))
    d = ImageDraw.Draw(out)
    d.text(org, text, font=f, fill=NAVY, stroke_width=white + navy, stroke_fill=NAVY)
    d.text(org, text, font=f, fill=WHITE, stroke_width=white, stroke_fill=WHITE)
    d.text(org, text, font=f, fill=fill)
    return out.crop(out.getbbox())

def emblem():
    """Kronometre + 5 amblemi (özgün logodan)."""
    im = Image.open(SRC).convert("RGBA")
    return im.crop((95, 0, 622, im.height)).crop(im.crop((95, 0, 622, im.height)).getbbox())

def two_line(lines, size):
    a = word(lines[0], size, TEAL); b = word(lines[1], size, PINK)
    w = max(a.width, b.width); gap = -int(size * 0.12)
    out = Image.new("RGBA", (w, a.height + b.height + gap), (0, 0, 0, 0))
    out.alpha_composite(a, (0, 0)); out.alpha_composite(b, (0, a.height + gap))
    return out

def logo(lines, height=840):
    """Ana logo: amblem + iki satır yazı (özgün Türkçe logonun yerleşimi)."""
    em = emblem()
    em = em.resize((int(em.width * height * 0.78 / em.height), int(height * 0.78)), Image.LANCZOS)
    txt = two_line(lines, int(height * 0.25))
    gap = int(height * 0.03)
    w = em.width + gap + txt.width + 40
    out = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    out.alpha_composite(em, (20, (height - em.height) // 2))
    out.alpha_composite(txt, (20 + em.width + gap, (height - txt.height) // 2 + int(height * 0.02)))
    return out.crop(out.getbbox())

def original_tr():
    im = Image.open(SRC).convert("RGBA")
    return im.crop(im.getbbox())

def one_line(text, size, two_color=True):
    """İkincil ad: tek satır, ortak taban çizgisi; son kelime pembe, öncekiler turkuaz."""
    f = font(size)
    white = max(3, size // 11); navy = max(2, size // 17); pad = white + navy + 12
    l, t, r, b = f.getbbox(text)
    w, h = r - l + 2 * pad, b - t + 2 * pad + 8
    org = (pad - l, pad - t)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sh = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(sh).text((org[0], org[1] + max(3, size // 22)), text, font=f, fill=(20, 40, 70, 90), stroke_width=white + navy, stroke_fill=(20, 40, 70, 90))
    out.alpha_composite(sh.filter(ImageFilter.GaussianBlur(max(1, size // 40))))
    d = ImageDraw.Draw(out)
    d.text(org, text, font=f, fill=NAVY, stroke_width=white + navy, stroke_fill=NAVY)
    d.text(org, text, font=f, fill=WHITE, stroke_width=white, stroke_fill=WHITE)
    head, _, last = text.rpartition(" ")
    if two_color and head:
        d.text(org, head, font=f, fill=TEAL)
        d.text((org[0] + f.getlength(head + " "), org[1]), last, font=f, fill=PINK)
    else:
        d.text(org, text, font=f, fill=TEAL)
    return out.crop(out.getbbox())

def stack(main, sub, sub_ratio=0.62, gap=-6):
    sw = int(main.width * sub_ratio)
    sub = sub.resize((sw, int(sub.height * sw / sub.width)), Image.LANCZOS)
    out = Image.new("RGBA", (max(main.width, sub.width), main.height + sub.height + gap), (0, 0, 0, 0))
    out.alpha_composite(main, ((out.width - main.width) // 2, 0))
    out.alpha_composite(sub, ((out.width - sub.width) // 2, main.height + gap))
    return out

def on_bg(im, color, pad=60, glow=None):
    out = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), color + (255,))
    if glow:
        g = Image.new("RGBA", out.size, (0, 0, 0, 0))
        mask = im.split()[3].filter(ImageFilter.MaxFilter(9))
        layer = Image.new("RGBA", im.size, glow + (150,)); layer.putalpha(mask.point(lambda v: v * 150 // 255))
        g.alpha_composite(layer, (pad, pad)); out.alpha_composite(g.filter(ImageFilter.GaussianBlur(14)))
    out.alpha_composite(im, (pad, pad))
    return out.convert("RGB")

def fit_width(im, w):
    return im.resize((w, int(im.height * w / im.width)), Image.LANCZOS)

def game_assets():
    """Oyunun kullandığı marka görselleri (assets/brand)."""
    d = os.path.join(ROOT, "assets", "brand"); os.makedirs(d, exist_ok=True)
    fit_width(logo(EN_NAME), 1000).save(os.path.join(d, "logo_en.png"), optimize=True)
    fit_width(one_line("Stopwatch Shuffle", 140), 760).save(os.path.join(d, "sub_en.png"), optimize=True)
    fit_width(one_line("5 Saniyelik Dünya", 140), 760).save(os.path.join(d, "sub_tr.png"), optimize=True)

def package(out):
    """Teslim paketi: Türkçe, İngilizce, iki dilli, saydam, açık ve koyu arka plan, yazısız amblem."""
    tr = original_tr(); en = logo(EN_NAME)
    sub_en = one_line("Stopwatch Shuffle", 160); sub_tr = one_line("5 Saniyelik Dünya", 160)
    bi_tr = stack(tr, sub_en); bi_en = stack(en, sub_tr)
    sets = {"Turkish": {"logo_tr": tr}, "English": {"logo_en": en}, "Bilingual": {"logo_bilingual_tr_main": bi_tr, "logo_bilingual_en_main": bi_en}}
    for folder, items in sets.items():
        p = os.path.join(out, folder); os.makedirs(p, exist_ok=True)
        for name, im in items.items():
            im.save(os.path.join(p, name + "_transparent.png"), optimize=True)
            on_bg(im, (236, 246, 255)).save(os.path.join(p, name + "_light_bg.png"), optimize=True)
            on_bg(im, (24, 36, 62), glow=(255, 255, 255)).save(os.path.join(p, name + "_dark_bg.png"), optimize=True)
    tp = os.path.join(out, "Transparent"); os.makedirs(tp, exist_ok=True)
    for name, im in {"logo_tr": tr, "logo_en": en, "logo_bilingual_tr_main": bi_tr, "logo_bilingual_en_main": bi_en,
                     "name_en_one_line": sub_en, "name_tr_one_line": sub_tr, "emblem_stopwatch_no_text": emblem()}.items():
        im.save(os.path.join(tp, name + ".png"), optimize=True)

if __name__ == "__main__":
    game_assets()
    if len(sys.argv) > 1: package(sys.argv[1])
    print("tamam")
