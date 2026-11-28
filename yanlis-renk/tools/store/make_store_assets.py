#!/usr/bin/env python3
"""Google Play ve sosyal medya görselleri. Tüm oyun görüntüleri gerçek oyun çıktısıdır
(desktop ScreenshotRunner, 1080x1920 / 1200x1920 / 1600x2560). Bu betik yalnızca çerçeve, başlık ve logo ekler.
Kullanım: python3 tools/store/make_store_assets.py <ham_telefon_klasörü> <ham_tablet_klasörü> <RELEASE_PACKAGE>"""
import os, sys, json, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = os.path.join(ROOT, "android/assets/fonts/Nunito-ExtraBold.ttf")
NAVY = (37, 56, 88)
LANGS = ["tr", "en", "de", "it", "fr", "es"]
SHOTS = ["05b_oyun_alan1", "05c_oyun_ters", "05f_oyun_ayna", "03_harita", "11_karakterler", "26b_iki_kisilik_oyun", "06_sonsuz", "13_gorevler"]
SHOT_NAMES = ["gameplay_tap", "gameplay_rules", "gameplay_mechanics", "level_map", "characters", "two_player", "endless", "missions"]
NB = " "
CAPTIONS = {
    "tr": ["Dokun, rengini değiştir!", "Aynı mı, farklı mı? Hızlı karar ver!", "Ayna, hafıza ve hileli kapılar", "20 dünyada 500 bölüm",
           "30 sevimli karakter topla", "Tek telefonda 2 kişilik kapışma", "Sonsuz modda rekorunu kır", "Günlük görevler ve ödüller"],
    "en": ["Tap to switch colors!", "Same or different? Decide fast!", "Mirrors, memory & tricky gates", "500 levels in 20 worlds",
           "Collect 30 cute characters", "2-player duel on one phone", "Beat your best in Endless Mode", "Daily missions & rewards"],
    "de": ["Tippen und Farbe wechseln!", "Gleich oder anders? Schnell entscheiden!", "Spiegel, Gedächtnis & Trick-Tore", "500 Level in 20 Welten",
           "Sammle 30 süße Figuren", "2-Spieler-Duell auf einem Handy", "Knacke deinen Rekord im Endlosmodus", "Tägliche Missionen & Belohnungen"],
    "it": ["Tocca e cambia colore!", "Uguale o diverso? Decidi in fretta!", "Specchi, memoria e porte ingannevoli", "500 livelli in 20 mondi",
           "Colleziona 30 personaggi adorabili", "Sfida a 2 sullo stesso telefono", "Batti il tuo record in modalità infinita", "Missioni e premi giornalieri"],
    "fr": [f"Touche pour changer de couleur{NB}!", f"Pareil ou différent{NB}? Décide vite{NB}!", "Miroirs, mémoire et portes piégées", "500 niveaux dans 20 mondes",
           "Collectionne 30 personnages mignons", "Duel à deux sur un seul téléphone", "Bats ton record en mode infini", "Missions et récompenses quotidiennes"],
    "es": ["¡Toca para cambiar de color!", "¿Igual o diferente? ¡Decide rápido!", "Espejos, memoria y puertas trampa", "500 niveles en 20 mundos",
           "Colecciona 30 personajes adorables", "Duelo a 2 en un solo móvil", "Supera tu récord en el modo infinito", "Misiones y premios diarios"],
}
TAGLINE = {"tr": "Renkler seni kandırmasın!", "en": "Don't let the colors fool you!"}
CTA = {"tr": "Maceraya Başla!", "en": "Start Your Adventure!"}
HOOK = {"tr": "Doğru renk hangisi?", "en": "Which color is right?"}


def font(size): return ImageFont.truetype(FONT, size)

def gradient(w, h, top=(135, 206, 250), bottom=(248, 251, 255)):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)))
    return im

def fit(im, w, h):
    k = min(w / im.width, h / im.height)
    return im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)

def rounded(im, r, border=0, bcol=(255, 255, 255)):
    im = im.convert("RGBA")
    W, H = im.width + border * 2, im.height + border * 2
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    m = Image.new("L", (W, H), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, W - 1, H - 1], r + border, fill=255)
    out.paste(Image.new("RGBA", (W, H), bcol + (255,)), (0, 0), m)
    m2 = Image.new("L", im.size, 0); ImageDraw.Draw(m2).rounded_rectangle([0, 0, im.width - 1, im.height - 1], r, fill=255)
    out.paste(im, (border, border), m2)
    return out

def shadowed(base, im, x, y, blur=24, off=14, alpha=90):
    sh = Image.new("RGBA", (im.width + blur * 4, im.height + blur * 4), (0, 0, 0, 0))
    a = im.split()[3].point(lambda v: alpha if v > 0 else 0)
    sh.paste(Image.new("RGBA", im.size, (30, 50, 90, 255)), (blur * 2, blur * 2), a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(sh, (x - blur * 2, y - blur * 2 + off))
    base.alpha_composite(im, (x, y))

def wrap_text(d, text, f, maxw):
    words, lines, cur = text.split(" "), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=f) <= maxw or not cur: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur)
    return lines

def draw_caption(img, text, box, size=96, color=NAVY, min_size=48, max_lines=2):
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    while True:
        f = font(size)
        lines = wrap_text(d, text, f, x1 - x0)
        lh = int(size * 1.18)
        if (len(lines) <= max_lines and lh * len(lines) <= y1 - y0) or size <= min_size: break
        size -= 4
    total = lh * len(lines)
    y = y0 + (y1 - y0 - total) // 2
    for ln in lines:
        w = d.textlength(ln, font=f)
        d.text(((x0 + x1 - w) / 2, y), ln, font=f, fill=color, stroke_width=max(2, size // 14), stroke_fill=(255, 255, 255))
        y += lh
    return lines, size

def logo_for(lang):
    p = "Bilingual/logo_bilingual_tr_main_transparent.png" if lang == "tr" else "Bilingual/logo_bilingual_en_main_transparent.png"
    return Image.open(os.path.join(ROOT, "RELEASE_PACKAGE/03_Logos", p)).convert("RGBA")

def phone_screenshot(raw, caption, out):
    W, H = 1080, 1920
    bg = gradient(W, H).convert("RGBA")
    draw_caption(bg, caption, (60, 50, W - 60, 330), size=92)
    shot = Image.open(raw).convert("RGB")
    s = rounded(fit(shot, 860, 1530), 46, border=12)
    shadowed(bg, s, (W - s.width) // 2, 360)
    bg.convert("RGB").save(out, optimize=True)

def feature_graphic(lang, raw_dir, out):
    W, H = 1024, 500
    bg = Image.open(os.path.join(ROOT, "android/assets/bg/world_1.jpg")).convert("RGB")
    bg = bg.resize((W, int(bg.height * W / bg.width)))
    bg = bg.crop((0, (bg.height - H) // 2, W, (bg.height - H) // 2 + H)).filter(ImageFilter.GaussianBlur(3)).convert("RGBA")
    veil = Image.new("RGBA", (W, H), (255, 255, 255, 70)); bg.alpha_composite(veil)
    lg = fit(logo_for(lang), 520, 330)
    shadowed(bg, lg, 40 + (520 - lg.width) // 2, 40, blur=10, off=6, alpha=60)
    d = ImageDraw.Draw(bg)
    draw_caption(bg, TAGLINE["tr" if lang == "tr" else "en"], (40, 385, 560, 470), size=40, max_lines=1)
    for i, name in enumerate(["05c_oyun_ters", "05b_oyun_alan1"]):
        sh = Image.open(os.path.join(raw_dir, lang, name + ".png")).convert("RGB")
        ph = rounded(fit(sh, 190, 338), 22, border=6).rotate((8, -6)[i], expand=True, resample=Image.BICUBIC)
        shadowed(bg, ph, 600 + i * 190, 60 + i * 40, blur=10, off=8)
    bg.convert("RGB").save(out, optimize=True)

def social(lang, raw_dir, outdir):
    os.makedirs(outdir, exist_ok=True)
    g = lambda n: Image.open(os.path.join(raw_dir, lang, n + ".png")).convert("RGB")
    logo = logo_for(lang)
    L = "tr" if lang == "tr" else "en"

    def poster(W, H, shots, cap, cta=None, name=None, shot_h=None):
        bg = gradient(W, H).convert("RGBA")
        lg = fit(logo, int(W * 0.7), int(H * 0.2))
        shadowed(bg, lg, (W - lg.width) // 2, int(H * 0.035), blur=10, off=6, alpha=50)
        top = int(H * 0.035) + lg.height + int(H * 0.01)
        draw_caption(bg, cap, (40, top, W - 40, top + int(H * 0.09)), size=int(min(W, H) * 0.062), max_lines=2)
        y = top + int(H * 0.1)
        bottom = H - (int(H * 0.13) if cta else int(H * 0.04))
        sh_h = bottom - y
        n = len(shots)
        sw = min(int((W - 80) / n) - 20, int(sh_h * 9 / 16))
        tot = n * sw + (n - 1) * 24
        for i, s in enumerate(shots):
            ph = rounded(fit(s, sw, sh_h), 28, border=8)
            shadowed(bg, ph, (W - tot) // 2 + i * (sw + 24), y + (sh_h - ph.height) // 2, blur=12, off=8)
        if cta:
            d = ImageDraw.Draw(bg)
            f = font(int(min(W, H) * 0.055)); tw = d.textlength(cta, font=f)
            bw, bh = tw + 120, int(min(W, H) * 0.1)
            bx, by = (W - bw) / 2, H - int(H * 0.115)
            d.rounded_rectangle([bx, by + 8, bx + bw, by + bh + 8], bh // 2, fill=(46, 140, 60))
            d.rounded_rectangle([bx, by, bx + bw, by + bh], bh // 2, fill=(92, 196, 88))
            d.text((W / 2 - tw / 2, by + bh / 2), cta, font=f, fill=(255, 255, 255), anchor="lm")
        bg.convert("RGB").save(os.path.join(outdir, name), quality=93) if name.endswith(".jpg") else bg.convert("RGB").save(os.path.join(outdir, name))

    poster(1080, 1080, [g("05b_oyun_alan1"), g("05c_oyun_ters")], TAGLINE[L], name=f"instagram_post_{L}_1080x1080.png")
    poster(1080, 1350, [g("05f_oyun_ayna"), g("11_karakterler")], CAPTIONS[lang][2], name=f"instagram_portrait_{L}_1080x1350.png")
    poster(1080, 1920, [g("05b_oyun_alan1")], HOOK[L], cta=CTA[L], name=f"instagram_story_{L}_1080x1920.png")
    poster(1080, 1920, [g("05c_oyun_ters")], HOOK[L], name=f"reels_cover_{L}_1080x1920.jpg")
    poster(1080, 1920, [g("26b_iki_kisilik_oyun")], CAPTIONS[lang][5], name=f"tiktok_cover_{L}_1080x1920.jpg")
    poster(1080, 1920, [g("02_ana_menu")], TAGLINE[L], cta=CTA[L], name=f"launch_poster_{L}_1080x1920.png")
    # yatay formatlar: logo solda, ekranlar sağda
    for (W, H, nm) in ((1280, 720, f"youtube_thumbnail_{L}_1280x720.jpg"), (1200, 630, f"facebook_post_{L}_1200x630.png")):
        bg = gradient(W, H).convert("RGBA")
        lg = fit(logo, int(W * 0.44), int(H * 0.55))
        shadowed(bg, lg, int(W * 0.04), int(H * 0.08), blur=10, off=6, alpha=50)
        draw_caption(bg, HOOK[L] if "youtube" in nm else TAGLINE[L], (int(W * 0.02), int(H * 0.68), int(W * 0.5), int(H * 0.92)), size=int(H * 0.08), max_lines=2)
        for i, n in enumerate(["05c_oyun_ters", "05b_oyun_alan1", "06_sonsuz"]):
            ph = rounded(fit(g(n), int(H * 0.47), int(H * 0.84)), 20, border=6).rotate((-5, 0, 5)[i], expand=True, resample=Image.BICUBIC)
            shadowed(bg, ph, int(W * 0.5) + i * int(W * 0.15), int(H * 0.06) + (i % 2) * 20, blur=10, off=8)
        bg.convert("RGB").save(os.path.join(outdir, nm), quality=93)

def main(raw_dir, tab_dir, pkg):
    ps = os.path.join(pkg, "04_PlayStore")
    for sub in ("App_Icon", "Feature_Graphics", "Tablet_7_Inch", "Tablet_10_Inch"): os.makedirs(os.path.join(ps, sub), exist_ok=True)
    icon = Image.open(os.path.join(ROOT, "store/icon_full_square.png")).convert("RGBA").resize((512, 512), Image.LANCZOS)
    icon.save(os.path.join(ps, "App_Icon/app_icon_512.png"), optimize=True)
    for lang in ("tr", "en"):
        feature_graphic(lang, raw_dir, os.path.join(ps, f"Feature_Graphics/feature_graphic_{lang}.png"))
    for lang in LANGS:
        d = os.path.join(ps, "Phone_Screenshots", lang.upper()); os.makedirs(d, exist_ok=True)
        for i, (s, nm) in enumerate(zip(SHOTS, SHOT_NAMES)):
            phone_screenshot(os.path.join(raw_dir, lang, s + ".png"), CAPTIONS[lang][i], os.path.join(d, f"{i + 1:02d}_{nm}_{lang}.png"))
    # tabletler: oyunun tablet en-boy oranında gerçek görüntüleri (başlık eklenmez)
    for size, folder in (("1200x1920", "Tablet_7_Inch"), ("1600x2560", "Tablet_10_Inch")):
        for lang in ("en", "tr"):
            src = os.path.join(tab_dir, f"{size}_{lang}")
            for i, n in enumerate(["05b_oyun_alan1", "05f_oyun_ayna", "03_harita", "02_ana_menu", "11_karakterler"]):
                Image.open(os.path.join(src, n + ".png")).convert("RGB").save(os.path.join(ps, folder, f"{i + 1:02d}_{n}_{lang}_{size}.png"), optimize=True)
    with open(os.path.join(ps, "Phone_Screenshots", "CAPTIONS.json"), "w", encoding="utf-8") as f:
        json.dump(CAPTIONS, f, ensure_ascii=False, indent=1)
    for lang, folder in (("tr", "Turkish"), ("en", "English")):
        social(lang, raw_dir, os.path.join(pkg, "07_Social_Media", folder))
    print("mağaza görselleri tamam")

if __name__ == "__main__":
    main(*sys.argv[1:4])
