#!/usr/bin/env python3
"""Kapak görseli (1024x500), sosyal medya görselleri ve video kapakları.

Kullanım: python3 promo_images.py <dil tr|en> <ekran_görüntüsü_klasörü (s1..s8.png)> <çıktı_kökü>
Hepsi gerçek oyun görüntüsü, oyunun kahraman çizimi ve logosuyla oluşturulur; şeffaflık yoktur.
"""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
from compose_video import sky, font, caption_img, NAVY

def logo(lang):
    name = "logo_bilingual_tr_main.png" if lang == "tr" else "logo_bilingual_en_main.png"
    return Image.open(os.path.join(ROOT, "RELEASE_PACKAGE", "03_Logos", "Transparent", name))

def hero():
    sh = Image.open(os.path.join(ROOT, "art_input", "parca4", "hero_sheet.png")).convert("RGBA")
    f = sh.crop((512, 0, 1024, 512)); return f.crop(f.getbbox())

def fitw(im, w): return im.resize((w, int(im.height * w / im.width)), Image.LANCZOS)
def fith(im, h): return im.resize((int(im.width * h / im.height), h), Image.LANCZOS)

def card(shot, w, radius=26):
    s = fitw(shot.convert("RGB"), w)
    out = Image.new("RGBA", (w + 16, s.height + 16), (0, 0, 0, 0))
    ImageDraw.Draw(out).rounded_rectangle((0, 0, out.width - 1, out.height - 1), radius + 6, fill=(255, 255, 255, 255))
    m = Image.new("L", s.size, 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, s.width - 1, s.height - 1), radius, fill=255)
    out.paste(s, (8, 8), m); return out

def shadowed(bg, im, xy, blur=16, off=12):
    sh = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    a = im.split()[3].point(lambda v: v * 110 // 255)
    layer = Image.new("RGBA", im.size, (20, 45, 80, 255)); layer.putalpha(a)
    sh.paste(layer, (xy[0], xy[1] + off), layer)
    sh = sh.filter(ImageFilter.GaussianBlur(blur)); bg.paste(sh, (0, 0), sh); bg.paste(im, xy, im)

def text_center(bg, txt, y, size, color=(255, 255, 255), stroke=True):
    d = ImageDraw.Draw(bg); f = font(size)
    while f.getlength(txt) > bg.width - 60 and size > 24: size -= 4; f = font(size)
    x = (bg.width - f.getlength(txt)) / 2
    if stroke: d.text((x, y), txt, font=f, fill=color, stroke_width=max(4, size // 9), stroke_fill=NAVY)
    else: d.text((x, y), txt, font=f, fill=color)

def portrait(W, H, lang, shots, caps, cta=None, stack=1):
    bg = sky(W, H).convert("RGBA")
    lg = fitw(logo(lang), int(W * 0.82)); shadowed(bg, lg, ((W - lg.width) // 2, int(H * 0.04)), off=6)
    y = int(H * 0.04) + lg.height + int(H * 0.02)
    cw = int(W * 0.86)
    for i in range(stack):
        c = card(shots[i], cw); shadowed(bg, c, ((W - c.width) // 2, y)); y += c.height + int(H * 0.015)
    hr = fith(hero(), int(H * (0.16 if stack == 1 else 0.12)))
    shadowed(bg, hr, (W - hr.width - int(W * 0.02), y - hr.height + int(H * 0.02)))
    text_center(bg, caps["tagline"], y + int(H * 0.02), int(W * 0.065))
    if cta: text_center(bg, cta, y + int(H * 0.11), int(W * 0.06), (255, 245, 160))
    text_center(bg, "Magic Lumos Software", H - int(H * 0.05), int(W * 0.035), NAVY, stroke=False)
    return bg.convert("RGB")

def landscape(W, H, lang, shot, caps, title=None):
    bg = sky(W, H).convert("RGBA")
    c = card(shot, int(W * 0.52)); shadowed(bg, c, (W - c.width - int(W * 0.04), (H - c.height) // 2))
    lg = fitw(logo(lang), int(W * 0.40)); shadowed(bg, lg, (int(W * 0.03), int(H * 0.08)), off=6)
    hr = fith(hero(), int(H * 0.36)); shadowed(bg, hr, (int(W * 0.04), H - hr.height - int(H * 0.06)))
    d = ImageDraw.Draw(bg); f = font(int(H * 0.06)); t = title or caps["tagline"]
    lines = []; cur = ""
    for w in t.split():
        tt = (cur + " " + w).strip()
        if f.getlength(tt) < W * 0.27: cur = tt
        else: lines.append(cur); cur = w
    lines.append(cur)
    y = int(H * 0.08) + lg.height + int(H * 0.04)
    for ln in lines:
        d.text((int(W * 0.04) + hr.width + 10, y), ln, font=f, fill=(255, 255, 255), stroke_width=max(4, f.size // 9), stroke_fill=NAVY); y += int(f.size * 1.15)
    return bg.convert("RGB")

def feature_graphic(lang):
    art = Image.open(os.path.join(ROOT, "art_input", "parca2", "feature_graphic.png")).convert("RGBA")
    lg = fitw(logo(lang), 430)
    glow = Image.new("RGBA", art.size, (0, 0, 0, 0)); ImageDraw.Draw(glow).ellipse((-60, 40, 520, 470), fill=(255, 255, 255, 120))
    art.paste(glow.filter(ImageFilter.GaussianBlur(40)), (0, 0), glow.filter(ImageFilter.GaussianBlur(40)))
    shadowed(art, lg, (24, (500 - lg.height) // 2 - 10), blur=10, off=5)
    return art.convert("RGB")

if __name__ == "__main__":
    lang, shots_dir, out = sys.argv[1:4]
    caps = json.load(open(os.path.join(HERE, "video_captions.json"), encoding="utf-8"))[lang]
    shots = [Image.open(os.path.join(shots_dir, f"s{i}.png")) for i in range(1, 9)]
    L = lang.upper(); folder = "Turkish" if lang == "tr" else "English"
    fg = os.path.join(out, "04_PlayStore", "Feature_Graphics"); os.makedirs(fg, exist_ok=True)
    feature_graphic(lang).save(os.path.join(fg, f"feature_graphic_{lang}.png"), optimize=True)
    sm = os.path.join(out, "07_Social_Media", folder); os.makedirs(sm, exist_ok=True)
    portrait(1080, 1080, lang, [shots[0]], caps).save(os.path.join(sm, f"instagram_post_1080x1080_{lang}.jpg"), quality=92)
    portrait(1080, 1350, lang, [shots[0]], caps).save(os.path.join(sm, f"instagram_portrait_1080x1350_{lang}.jpg"), quality=92)
    portrait(1080, 1920, lang, [shots[0], shots[2]], caps, stack=2).save(os.path.join(sm, f"instagram_story_1080x1920_{lang}.jpg"), quality=92)
    portrait(1080, 1920, lang, [shots[0], shots[1]], caps, cta=caps["cta"], stack=2).save(os.path.join(sm, f"reels_cover_1080x1920_{lang}.jpg"), quality=92)
    portrait(1080, 1920, lang, [shots[2], shots[0]], caps, cta=caps["cta"], stack=2).save(os.path.join(sm, f"tiktok_cover_1080x1920_{lang}.jpg"), quality=92)
    landscape(1280, 720, lang, shots[0], caps).save(os.path.join(sm, f"youtube_thumbnail_1280x720_{lang}.jpg"), quality=92)
    landscape(1200, 630, lang, shots[2], caps).save(os.path.join(sm, f"facebook_1200x630_{lang}.jpg"), quality=92)
    soon = {"tr": "Yakında Google Play'de", "en": "Coming soon to Google Play"}[lang]
    portrait(1080, 1920, lang, [shots[0], shots[6]], caps, cta=soon, stack=2).save(os.path.join(sm, f"launch_poster_1080x1920_{lang}.jpg"), quality=92)
    tr = os.path.join(out, "05_Trailers", "Social_Media"); os.makedirs(tr, exist_ok=True)
    landscape(1280, 720, lang, shots[0], caps).save(os.path.join(tr, f"youtube_cover_{lang}.jpg"), quality=92)
    portrait(1080, 1920, lang, [shots[0], shots[2]], caps, cta=caps["cta"], stack=2).save(os.path.join(tr, f"reels_cover_{lang}.jpg"), quality=92)
    print("tamam", lang)
