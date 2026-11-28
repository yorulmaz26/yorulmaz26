#!/usr/bin/env python3
"""Prismway / Işık Yolu yayın paketi medya üreticisi (6 dil).

Girdi: PromoCaptureTest kayıtları (gerçek oyun görüntüsü), dil başına bir klasör:
  ./gradlew :app:testDebugUnitTest --tests '*PromoCaptureTest*' -PpromoDir=KAYIT/tr -PpromoLang=tr
Metinler: RELEASE_PACKAGE/06_Store_Descriptions/store_texts.<dil>.json

  python3 tools/magaza/release_media.py --rec KAYIT --fonts ROBOTO --sounds app/src/main/res/raw \
      --texts RELEASE_PACKAGE/06_Store_Descriptions --out RELEASE_PACKAGE --langs tr,en,de,it,fr,es [--only shots,video,...]

Üretilenler (out altında):
  04_PlayStore/App_Icon/app_icon_512.png
  04_PlayStore/Feature_Graphics/feature_graphic_<tr|en>.png
  04_PlayStore/Phone_Screenshots/<DİL>/<dil>_0N_<ad>.png          (8 × 6 dil, 1080×1920, başlıklı)
  05_Trailers/Vertical/trailer_<tr|en>_vertical.mp4              (30 sn, 1080×1920, Play/YouTube sürümü, eylem çağrısı yok)
  05_Trailers/Horizontal/trailer_<tr|en>_horizontal.mp4          (30 sn, 1920×1080)
  05_Trailers/Social_Media/trailer_<tr|en>_vertical_social_cta{1,2}.mp4 (kapanışta "Hemen Oyna!" vb.)
  05_Trailers/Subtitles/trailer_<tr|en>.srt
  05_Trailers/Social_Media/youtube_cover_<tr|en>_1280x720.png, reels_cover_<tr|en>_1080x1920.png
  05_Trailers/Source_Files/timeline_<dil>.json                    (düzenlenebilir zaman çizelgesi)
  07_Social_Media/<Turkish|English>/*.png                         (8 biçim)
Turkish/English klasörlerine ilgili dildeki videoların kopyası konur.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import magaza as M  # noqa: E402
import logos as LG  # noqa: E402

FPS = 30
XF = 8  # bölümler arası geçiş (kare)
END_FRAMES = 96  # marka kapanışı ~3,2 sn
GOLD, TURQ, WHITE, DIM = M.GOLD, M.TURQ, M.WHITE, M.DIM
SHOT_NAMES = ["01_mantik", "02_prizma", "03_yildizlar", "04_dunyalar", "05_kapilar", "06_bloklar", "07_gunluk", "08_gorunumler"]
SHOT_SLUG = ["light", "prism", "stars", "worlds", "keys", "stones", "daily", "skins"]
NAME_TR, NAME_EN, STUDIO = "Işık Yolu", "Prismway", "Magic Lumos Software"


def brand_names(lang):
    return (NAME_TR, NAME_EN.upper()) if lang == "tr" else (NAME_EN, "IŞIK YOLU")


def fit_font(fonts, name, text, maxw, start, minsize=24):
    d = ImageDraw.Draw(Image.new("L", (4, 4)))
    s = start
    while s > minsize:
        f = M.font(fonts, name, s)
        if d.textlength(text, font=f) <= maxw: return f
        s -= 2
    return M.font(fonts, name, minsize)


def brand_block(fonts, lang, main_size, theme="dark"):
    """İki dilli ad bloğu (şeffaf RGBA): ana ad büyük ışıltılı, ikinci ad küçük aralıklı."""
    main, second = brand_names(lang)
    fm = M.font(fonts, "Roboto-Black.ttf", main_size)
    fs = M.font(fonts, "Roboto-Medium.ttf", max(12, int(main_size * 0.36)))
    m = LG.gradient_text(main, fm, [(0xFF, 0xE0, 0x8A), WHITE, (0x7D, 0xF0, 0xE0)])
    s = LG.gradient_text(second, fs, [(0xBF, 0xD3, 0xF2)], letter=0.32)
    pad = int(main_size * 0.25)
    w, h = max(m.width, s.width) + pad * 2, m.height + s.height + pad
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    lay.alpha_composite(m, ((w - m.width) // 2, pad // 2))
    lay.alpha_composite(s, ((w - s.width) // 2, pad // 2 + m.height - int(main_size * 0.06)))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out = Image.alpha_composite(out, LG.glow_of(lay, main_size * 0.09, TURQ, 0.45))
    return Image.alpha_composite(out, lay)


def paste_center(canvas, img, cx, y):
    canvas.alpha_composite(img, (int(cx - img.width / 2), int(y)))


def texts(tdir, lang):
    return json.load(open(os.path.join(tdir, f"store_texts.{lang}.json"), encoding="utf-8"))


def caption(d, fonts, W, y, title, sub, maxw, tsize=76, ssize=38, color=WHITE):
    """Ortalanmış başlık + altın çizgi + alt satır; her görselde aynı çizgi yüksekliği."""
    ft = fit_font(fonts, "Roboto-Black.ttf", title, maxw, tsize)
    asc, desc = M.font(fonts, "Roboto-Black.ttf", tsize).getmetrics()
    M.text_center(d, W / 2, y, title, ft, color)
    line_y = y + asc + desc + 12
    d.rounded_rectangle([W / 2 - 60, line_y, W / 2 + 60, line_y + 7], radius=4, fill=GOLD)
    fs = M.font(fonts, "Roboto-Medium.ttf", ssize)
    lines = M.wrap(d, sub, fs, maxw)
    yy = line_y + 30
    for ln in lines[:2]:
        M.text_center(d, W / 2, yy, ln, fs, DIM)
        yy += int(ssize * 1.25)


# ---------------------------------------------------------------------------------------------------------------
# görseller

def screenshots(rec, fonts, tx, lang, out):
    od = os.path.join(out, "04_PlayStore", "Phone_Screenshots", lang.upper())
    os.makedirs(od, exist_ok=True)
    W, H = 1080, 1920
    bg = M.background(W, H, 7)
    head = 330
    sh = H - head - 60
    sw = int(sh * 1080 / 1920)
    box = ((W - sw) // 2, head, sw, sh)
    phone = M.phone_layer((W, H), box, 46)
    for i, name in enumerate(SHOT_NAMES):
        src = Image.open(os.path.join(rec, "shots", name + ".png")).convert("RGB")
        c = Image.alpha_composite(bg.copy().convert("RGBA"), phone).convert("RGB")
        M.paste_screen(c, src, box, 40)
        t, s = tx["screenshot_captions"][i]
        caption(ImageDraw.Draw(c), fonts, W, 70, t, s, W - 120)
        c.save(os.path.join(od, f"{lang}_{i + 1:02d}_{SHOT_SLUG[i]}.png"), optimize=True)
    print("ekran görüntüleri", lang, len(SHOT_NAMES))


def feature_graphic(rec, fonts, tx, lang, out):
    od = os.path.join(out, "04_PlayStore", "Feature_Graphics")
    os.makedirs(od, exist_ok=True)
    W, H = 1024, 500
    c = M.background(W, H, 3).convert("RGBA")
    shot = Image.open(os.path.join(rec, "shots", "02_prizma.png")).convert("RGB")
    # sağda oyun ekranının tahta bölümü (kırpılmış, esnetilmeden)
    sw = 330
    scale = sw / shot.width
    crop_h = int(H / scale) - 40
    top = int(shot.height * 0.16)
    part = shot.crop((0, top, shot.width, min(shot.height, top + crop_h)))
    part = part.resize((sw, int(part.height * scale)), Image.LANCZOS)
    box = (W - sw - 64, (H - part.height) // 2, sw, part.height)
    c = Image.alpha_composite(c, M.phone_layer((W, H), box, 26))
    c.paste(part, (box[0], box[1]), M.rounded_mask((sw, part.height), 24))
    em = LG.emblem(150)
    c.alpha_composite(em, (64, 60))
    bb = brand_block(fonts, lang, 92)
    c.alpha_composite(bb, (64 + 0, 210))
    d = ImageDraw.Draw(c)
    f = fit_font(fonts, "Roboto-Medium.ttf", tx["slogan"], 520, 34)
    d.text((80, 410), tx["slogan"], font=f, fill=DIM)
    c.convert("RGB").save(os.path.join(od, f"feature_graphic_{lang}.png"), optimize=True)
    print("öne çıkan görsel", lang)


def app_icon(rec, out):
    od = os.path.join(out, "04_PlayStore", "App_Icon")
    os.makedirs(od, exist_ok=True)
    im = Image.open(os.path.join(rec, "icon", "icon_512.png")).convert("RGBA")
    assert im.size == (512, 512)
    im.save(os.path.join(od, "app_icon_512.png"), optimize=True)


# ---------------------------------------------------------------------------------------------------------------
# video

class Clip(M.Clip):
    def __init__(self, promo, name):
        super().__init__(promo, name)
        meta = json.load(open(os.path.join(self.dir, "events.json"), encoding="utf-8"))
        self.uptime = meta.get("uptime")

    def dialog_frame(self):
        """'Tamamlandı' penceresinin açıldığı kare: oyunda kazanmadan 1100 ms sonra (kayıttaki saat değerlerinden)."""
        w = self.win_frame()
        if w is None: return None
        if not self.uptime: return w + 22
        for i in range(w, len(self.uptime)):
            if self.uptime[i] - self.uptime[w] >= 1100: return i
        return self.frames


def tap_overlay(size, clip, i, x, y, sx, sy, ring=1.0):
    ov = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for e in clip.events:
        if e["k"] not in ("tap", "drag"): continue
        t = i - e["f"]
        if -5 <= t <= 10:
            p = (t + 5) / 15.0
            if e["k"] == "drag":
                q = min(1.0, max(0.0, (t + 2) / 8.0))
                px, py = e["x"] + (e["x2"] - e["x"]) * q, e["y"] + (e["y2"] - e["y"]) * q
            else:
                px, py = e["x"], e["y"]
            vx, vy = x + px * sx, y + py * sy
            r = (22 + 26 * p) * ring
            a = int(230 * (1 - p))
            d.ellipse([vx - r, vy - r, vx + r, vy + r], outline=(255, 255, 255, a), width=max(3, int(5 * ring)))
            if t <= 2:
                rr = 16 * ring
                d.ellipse([vx - rr, vy - rr, vx + rr, vy + rr], fill=(255, 255, 255, int(150 * (1 - p))))
    return ov


class Vertical:
    """1080×1920: üstte büyük başlık, altta oyun ekranı (%84, oranı korunur, kenarlarda oyunun arka planı)."""
    W, H = 1080, 1920

    def __init__(self, fonts):
        self.fonts = fonts
        self.bg = M.background(self.W, self.H, 21).convert("RGBA")
        s = 0.84
        self.sw, self.sh = int(1080 * s), int(1920 * s)
        self.x, self.y = (self.W - self.sw) // 2, self.H - self.sh - 46
        self.base = Image.alpha_composite(self.bg, M.phone_layer((self.W, self.H), (self.x, self.y, self.sw, self.sh), 40))
        self.mask = M.rounded_mask((self.sw, self.sh), 38)

    def caption_layer(self, title, sub, a=1.0, dy=0):
        lay = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        ft = fit_font(self.fonts, "Roboto-Black.ttf", title, self.W - 100, 74)
        col = tuple(list(WHITE) + [int(255 * a)])
        M.text_center(d, self.W / 2, 62 + dy, title, ft, col)
        if sub:
            fs = fit_font(self.fonts, "Roboto-Medium.ttf", sub, self.W - 120, 40)
            M.text_center(d, self.W / 2, 160 + dy, sub, fs, tuple(list(DIM) + [int(255 * a)]))
        return lay

    def frames(self, clip, start, end, title, sub):
        sx, sy = self.sw / clip.w, self.sh / clip.h
        for k, i in enumerate(range(start, end)):
            c = self.base.copy()
            scr = clip.frame(i).convert("RGB").resize((self.sw, self.sh), Image.LANCZOS)
            c.paste(scr, (self.x, self.y), self.mask)
            c = Image.alpha_composite(c, tap_overlay((self.W, self.H), clip, i, self.x, self.y, sx, sy, 0.84))
            a = min(1.0, (k + 1) / 8.0)
            c = Image.alpha_composite(c, self.caption_layer(title, sub, a, int(14 * (1 - a))))
            yield c.convert("RGB")

    def end_card(self, n, lang, tagline, cta=None):
        em = LG.emblem(520)
        bb = brand_block(self.fonts, lang, 150)
        ff = M.font(self.fonts, "Roboto-Medium.ttf", 40)
        for i in range(n):
            t = min(1.0, i / 18.0)
            e = 1 - (1 - t) ** 3
            c = self.bg.copy()
            glow = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
            ImageDraw.Draw(glow).ellipse([self.W / 2 - 330, 420, self.W / 2 + 330, 1080], fill=GOLD + (int(60 * e),))
            c = Image.alpha_composite(c, glow.filter(ImageFilter.GaussianBlur(90)))
            sz = int(520 * (0.88 + 0.12 * e))
            c.alpha_composite(em.resize((sz, sz), Image.LANCZOS), (self.W // 2 - sz // 2, 740 - sz // 2))
            b = bb.copy()
            b.putalpha(b.split()[3].point(lambda v: int(v * min(1.0, max(0.0, (i - 4) / 12.0)))))
            paste_center(c, b, self.W / 2, 1010)
            d = ImageDraw.Draw(c)
            a2 = min(1.0, max(0.0, (i - 12) / 12.0))
            if cta:
                fc = fit_font(self.fonts, "Roboto-Black.ttf", cta, 820, 78)
                tw = d.textlength(cta, font=fc)
                bx0, by0 = self.W / 2 - tw / 2 - 60, 1420
                pill = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
                pd = ImageDraw.Draw(pill)
                pd.rounded_rectangle([bx0, by0, self.W - bx0, by0 + 130], radius=65, fill=GOLD + (int(255 * a2),))
                M.text_center(pd, self.W / 2, by0 + 22, cta, fc, (0x10, 0x18, 0x27, int(255 * a2)))
                c = Image.alpha_composite(c, pill)
                d = ImageDraw.Draw(c)  # stüdyo adı birleştirilmiş kareye yazılır
            else:
                ft = fit_font(self.fonts, "Roboto-Bold.ttf", tagline, 900, 54)
                M.text_center(d, self.W / 2, 1440, tagline, ft, TURQ + (int(255 * a2),))
            M.text_center(d, self.W / 2, 1780, STUDIO, ff, (0xBF, 0xD3, 0xF2, int(200 * a2)))
            yield c.convert("RGB")


class Horizontal(Vertical):
    """1920×1080: solda başlık, sağda oyun ekranı (oranı korunur)."""
    W, H = 1920, 1080

    def __init__(self, fonts, lang):
        self.fonts, self.lang = fonts, lang
        self.bg = M.background(self.W, self.H, 11).convert("RGBA")
        self.sh = 960
        self.sw = int(self.sh * 1080 / 1920)
        self.x, self.y = self.W - self.sw - 230, (self.H - self.sh) // 2
        self.base = Image.alpha_composite(self.bg, M.phone_layer((self.W, self.H), (self.x, self.y, self.sw, self.sh), 44))
        self.mask = M.rounded_mask((self.sw, self.sh), 40)
        bb = brand_block(fonts, lang, 54)
        self.base.alpha_composite(LG.emblem(110), (112, 70))
        self.base.alpha_composite(bb, (220, 78))

    def caption_layer(self, title, sub, a=1.0, dy=0):
        lay = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
        d = ImageDraw.Draw(lay)
        x0, maxw = 130, self.x - 130 - 110
        ft = M.font(self.fonts, "Roboto-Black.ttf", 88)
        fs = M.font(self.fonts, "Roboto-Medium.ttf", 46)
        tl = M.wrap(d, title, ft, maxw)
        sl = M.wrap(d, sub, fs, maxw) if sub else []
        total = len(tl) * 104 + 40 + len(sl) * 60
        y = (self.H - total) // 2 + 30 + dy
        for ln in tl:
            d.text((x0, y), ln, font=ft, fill=WHITE + (int(255 * a),)); y += 104
        d.rounded_rectangle([x0, y + 4, x0 + 120, y + 12], radius=4, fill=GOLD + (int(255 * a),)); y += 40
        for ln in sl:
            d.text((x0, y), ln, font=fs, fill=DIM + (int(255 * a),)); y += 60
        return lay

    def end_card(self, n, lang, tagline, cta=None):
        em = LG.emblem(380)
        bb = brand_block(self.fonts, lang, 130)
        ff = M.font(self.fonts, "Roboto-Medium.ttf", 36)
        for i in range(n):
            t = min(1.0, i / 18.0)
            e = 1 - (1 - t) ** 3
            c = self.bg.copy()
            sz = int(380 * (0.88 + 0.12 * e))
            c.alpha_composite(em.resize((sz, sz), Image.LANCZOS), (self.W // 2 - sz // 2, 300 - sz // 2))
            b = bb.copy()
            b.putalpha(b.split()[3].point(lambda v: int(v * min(1.0, max(0.0, (i - 4) / 12.0)))))
            paste_center(c, b, self.W / 2, 490)
            d = ImageDraw.Draw(c)
            a2 = min(1.0, max(0.0, (i - 12) / 12.0))
            ft = fit_font(self.fonts, "Roboto-Bold.ttf", cta or tagline, 1300, 54)
            M.text_center(d, self.W / 2, 800, cta or tagline, ft, (GOLD if cta else TURQ) + (int(255 * a2),))
            M.text_center(d, self.W / 2, 960, STUDIO, ff, (0xBF, 0xD3, 0xF2, int(200 * a2)))
            yield c.convert("RGB")


def clip_sfx(clip, start, end):
    m = {"rotate": "rotate", "push": "push", "win": "win"}
    return [(e["f"] - start, m[e["k"]]) for e in clip.events if e["k"] in m and start <= e["f"] < end]


def timeline(rec, tx):
    """Senaryo: 0–3 çekici an, 3–8 mekanik, 8–15 deneyim, 15–22 çeşitlilik, 22–27 başarı, 27–30 marka.
    Her öğe: (klip, başlangıç karesi, bitiş karesi, başlık, alt satır, ses olayları)."""
    tc = tx["trailer_captions"]
    C = {n: Clip(rec, n) for n in ["f_mantik", "a_temel", "b_prizma", "y_karma", "d_blok", "h_bolumler", "e_kapi", "g_gorunum", "c_renk"]}

    def ending(name, n, after=20):
        c = C[name]
        w = c.win_frame()
        e = min(c.frames, (w if w is not None else c.frames) + after)
        d = c.dialog_frame()
        if d is not None: e = min(e, d)  # pencere açılmadan kes
        s = max(0, e - n)
        return (name, s, e)

    def middle(name, n, skip=10):
        c = C[name]
        s = min(skip, max(0, c.frames - n))
        return (name, s, min(c.frames, s + n))

    caps = tx["screenshot_captions"]
    c = C["c_renk"]
    w = c.win_frame()
    success = ("c_renk", max(0, w - 90), c.frames, tc["success"], "")
    head = [
        ending("f_mantik", 98) + (tc["hook"], ""),                      # 0–3 sn: en etkileyici an (beş hedef birden yanar)
        ending("a_temel", 158) + (tc["mechanic"], tc["mechanic2"]),     # 3–8 sn: temel mekanik
        ending("b_prizma", 110) + (tc["experience"], ""),
        ending("y_karma", 92) + (tc["experience2"], ""),                # 8–15 sn: deneyim
        ending("d_blok", 108) + (caps[5][0], ""),
    ]
    tail = [
        ending("e_kapi", 70) + (caps[4][0], ""),                        # 15–22 sn: çeşitlilik
        ending("g_gorunum", 64) + (caps[7][0], ""),
        success,                                                         # 22–27 sn: başarı
    ]
    # dünyalar/bölüm seçimi klibinin uzunluğu, kapanış kartı ~3,2 sn olacak biçimde hesaplanır
    fixed = sum(e - s0 for (_, s0, e, _, _) in head + tail)
    n_segs = len(head) + len(tail) + 1
    h_len = 900 - END_FRAMES - (fixed - XF * n_segs)
    h_len = max(50, min(C["h_bolumler"].frames - 12, h_len))
    plan = head + [middle("h_bolumler", h_len) + (tc["variety"], "")] + tail
    return C, plan


def build_segments(view, C, plan, lang, tagline, cta, total=900):
    segs, used = [], 0
    for (name, s, e, title, sub) in plan:
        clip = C[name]
        ev = clip_sfx(clip, s, e)
        if name == "c_renk":
            w = clip.win_frame()
            ev += [(w + 23 - s, "star"), (w + 32 - s, "star"), (w + 41 - s, "star")]
        segs.append(((lambda cl=clip, a=s, b=e, t=title, u=sub: view.frames(cl, a, b, t, u)), ev, e - s, title, sub))
        used += e - s
    used -= XF * len(segs)  # geçişler (kapanış kartıyla birlikte len(segs) geçiş)
    end_len = total - used
    if not (85 <= end_len <= 120):
        raise SystemExit(f"kapanış kartı {end_len / FPS:.2f} sn olurdu (3–4 sn olmalı); planı ayarla")
    segs.append(((lambda: view.end_card(end_len, lang, tagline, cta)), [(0, "intro")], end_len, "END", ""))
    return segs


def render(segs, path, sounds, W, H, total=900):
    from collections import deque
    raw, wav = path + ".rgb", path + ".wav"
    sfx, count, tail, last = [], 0, [], None
    with open(raw, "wb") as f:
        def put(fr):
            nonlocal count, last
            f.write(fr.tobytes()); count += 1; last = fr
        for si, (gen, events, _, _, _) in enumerate(segs):
            offset = count
            for (fi, name) in events:
                sfx.append((offset + fi, name))
            buf = deque()
            for k, fr in enumerate(gen()):
                if k < len(tail):
                    fr = Image.blend(tail[k], fr, (k + 1) / (len(tail) + 1))
                buf.append(fr)
                if len(buf) > XF:
                    put(buf.popleft())
            if si < len(segs) - 1:
                tail = list(buf)
            else:
                for fr in buf: put(fr)
        while count < total and last is not None:
            put(last)
    if count != total:
        os.remove(raw)
        raise SystemExit(f"{path}: {count} kare ≠ {total}")
    M.mix_audio(wav, sounds, total / FPS, sfx)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
                    "-i", raw, "-i", wav, "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p",
                    "-profile:v", "high", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", f"{total / FPS:.3f}", path], check=True)
    os.remove(raw); os.remove(wav)
    print(path, f"{count / FPS:.2f} sn")


def srt_and_timeline(segs, path_srt, path_json, lang, end_text=""):
    """Altyazı: her bölümün başlığı, gerçek başlangıç/bitiş zamanıyla (geçişler dahil)."""
    def ts(fr):
        ms = int(round(fr * 1000 / FPS))
        return f"{ms // 3600000:02d}:{ms // 60000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"
    t, items = 0, []
    for k, (_, _, n, title, sub) in enumerate(segs):
        start, end = t, t + n
        items.append({"start": start / FPS, "end": end / FPS, "title": title, "sub": sub})
        t = end - XF
    for k in range(1, len(items)):  # geçişin ortası: bir altyazı biter, sonraki başlar (çakışma yok)
        mid = items[k]["start"] + XF / 2 / FPS
        items[k - 1]["end"] = mid
        items[k]["start"] = mid
    lines, idx, prev = [], 1, None
    for it in items:
        if it["title"] == "END":
            if not end_text: continue
            text = end_text  # marka kapanışı: ekrandaki ad ve slogan
        else:
            text = it["title"] + ("\n" + it["sub"] if it["sub"] else "")
        if prev and prev["text"] == text:
            prev["end"] = it["end"]; continue
        prev = {"start": it["start"], "end": it["end"], "text": text}
        lines.append(prev)
    with open(path_srt, "w", encoding="utf-8") as f:
        for i, l in enumerate(lines, 1):
            f.write(f"{i}\n{ts(l['start'] * FPS)} --> {ts(l['end'] * FPS - 1)}\n{l['text']}\n\n")
    json.dump({"language": lang, "fps": FPS, "crossfade_frames": XF, "segments": items}, open(path_json, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)


def videos(rec, fonts, sounds, tx, lang, out):
    vd = os.path.join(out, "05_Trailers")
    for sub in ("Vertical", "Horizontal", "Social_Media", "Subtitles", "Source_Files"):
        os.makedirs(os.path.join(vd, sub), exist_ok=True)
    C, plan = timeline(rec, tx)
    tag = tx["trailer_captions"]["brand_tagline"]
    v = Vertical(fonts)
    segs = build_segments(v, C, plan, lang, tag, None)
    main, second = ("Işık Yolu", "Prismway") if lang == "tr" else ("Prismway", "Işık Yolu")
    srt_and_timeline(segs, os.path.join(vd, "Subtitles", f"trailer_{lang}.srt"), os.path.join(vd, "Source_Files", f"timeline_{lang}.json"), lang,
                     end_text=f"{main} · {second}\n{tag}")
    render(segs, os.path.join(vd, "Vertical", f"trailer_{lang}_vertical.mp4"), sounds, v.W, v.H)
    for k in (1, 2):
        cta = tx["trailer_captions"][f"cta{k}"]
        render(build_segments(v, C, plan, lang, tag, cta), os.path.join(vd, "Social_Media", f"trailer_{lang}_vertical_social_cta{k}.mp4"), sounds, v.W, v.H)
    h = Horizontal(fonts, lang)
    render(build_segments(h, C, plan, lang, tag, None), os.path.join(vd, "Horizontal", f"trailer_{lang}_horizontal.mp4"), sounds, h.W, h.H)
    folder = os.path.join(vd, "Turkish" if lang == "tr" else "English")
    os.makedirs(folder, exist_ok=True)
    for src in (os.path.join(vd, "Vertical", f"trailer_{lang}_vertical.mp4"), os.path.join(vd, "Horizontal", f"trailer_{lang}_horizontal.mp4"),
                os.path.join(vd, "Subtitles", f"trailer_{lang}.srt")):
        shutil.copy(src, folder)


# ---------------------------------------------------------------------------------------------------------------
# kapaklar ve sosyal medya

def hero_frame(rec, name="y_karma"):
    c = Clip(rec, name)
    w = c.win_frame()
    return c.frame((w if w is not None else c.frames - 1) + 8).convert("RGB")


def board_crop(img, top=0.158, bottom=0.845):
    """Oyun ekranından yalnızca tahta kartı: üstteki yıldız satırının altından alttaki düğmelerin üstüne kadar."""
    w, h = img.size
    return img.crop((0, int(h * top), w, int(h * bottom)))


def compose(W, H, rec, fonts, tx, lang, layout, seed=5, shot="02_prizma", cta=None, title=None, sub=None):
    c = M.background(W, H, seed).convert("RGBA")
    d = ImageDraw.Draw(c)
    scr = Image.open(os.path.join(rec, "shots", shot + ".png")).convert("RGB")
    if layout == "square":  # 1:1, 4:5: üstte ad, altta oyun tahtası
        bb = brand_block(fonts, lang, int(W * 0.105))
        paste_center(c, bb, W / 2, H * 0.05)
        part = board_crop(scr, 0.17, 0.78)
        ph = int(H * 0.62); pw = int(part.width * ph / part.height)
        part = part.resize((pw, ph), Image.LANCZOS)
        x, y = (W - pw) // 2, int(H * 0.27)
        c = Image.alpha_composite(c, M.phone_layer((W, H), (x, y, pw, ph), 30))
        c.paste(part, (x, y), M.rounded_mask((pw, ph), 28))
        d = ImageDraw.Draw(c)
        f = fit_font(fonts, "Roboto-Bold.ttf", title or tx["slogan"], W - 120, int(W * 0.045))
        M.text_center(d, W / 2, y + ph + int(H * 0.025), title or tx["slogan"], f, TURQ)
    elif layout == "tall":  # 9:16: tam oyun ekranı
        bb = brand_block(fonts, lang, int(W * 0.12))
        paste_center(c, bb, W / 2, H * 0.045)
        ph = int(H * (0.60 if cta else 0.66)); pw = int(ph * 1080 / 1920)  # eylem çağrısı varsa yer açılır
        x, y = (W - pw) // 2, int(H * 0.185)
        c = Image.alpha_composite(c, M.phone_layer((W, H), (x, y, pw, ph), 40))
        c.paste(scr.resize((pw, ph), Image.LANCZOS), (x, y), M.rounded_mask((pw, ph), 38))
        d = ImageDraw.Draw(c)
        t1 = title or tx["slogan"]
        f = fit_font(fonts, "Roboto-Black.ttf", t1, W - 120, int(W * 0.062))
        M.text_center(d, W / 2, y + ph + int(H * (0.025 if cta else 0.03)), t1, f, WHITE)
        if sub:
            fs = fit_font(fonts, "Roboto-Medium.ttf", sub, W - 140, int(W * 0.036))
            M.text_center(d, W / 2, y + ph + int(H * (0.068 if cta else 0.075)), sub, fs, DIM)
        if cta:
            fc = fit_font(fonts, "Roboto-Black.ttf", cta, W - 300, int(W * 0.055))
            tw = d.textlength(cta, font=fc)
            by = y + ph + int(H * 0.118)  # slogan ve alt satırın altında, üst üste binmez
            d.rounded_rectangle([W / 2 - tw / 2 - 50, by, W / 2 + tw / 2 + 50, by + int(W * 0.1)], radius=int(W * 0.05), fill=GOLD)
            M.text_center(d, W / 2, by + int(W * 0.018), cta, fc, (0x10, 0x18, 0x27))
    else:  # wide: 16:9 ve 1.91:1 — solda ad + slogan, sağda tahta
        em = LG.emblem(int(H * 0.30))
        c.alpha_composite(em, (int(W * 0.05), int(H * 0.08)))
        bb = brand_block(fonts, lang, int(H * 0.15))
        c.alpha_composite(bb, (int(W * 0.04), int(H * 0.38)))
        d = ImageDraw.Draw(c)
        t1 = title or tx["slogan"]
        f = fit_font(fonts, "Roboto-Bold.ttf", t1, int(W * 0.5), int(H * 0.065))
        d.text((int(W * 0.065), int(H * 0.74)), t1, font=f, fill=TURQ)
        if sub:
            fs = fit_font(fonts, "Roboto-Medium.ttf", sub, int(W * 0.5), int(H * 0.045))
            d.text((int(W * 0.065), int(H * 0.84)), sub, font=fs, fill=DIM)
        part = board_crop(scr, 0.15, 0.82)
        ph = int(H * 0.86); pw = int(part.width * ph / part.height)
        part = part.resize((pw, ph), Image.LANCZOS)
        x, y = W - pw - int(W * 0.06), (H - ph) // 2
        c = Image.alpha_composite(c, M.phone_layer((W, H), (x, y, pw, ph), 26))
        c.paste(part, (x, y), M.rounded_mask((pw, ph), 24))
    d = ImageDraw.Draw(c)
    fstd = M.font(fonts, "Roboto-Medium.ttf", max(14, int(min(W, H) * 0.022)))
    tw = d.textlength(STUDIO, font=fstd)
    d.text((W - tw - int(W * 0.03), H - int(min(W, H) * 0.045)), STUDIO, font=fstd, fill=(0xBF, 0xD3, 0xF2, 170))
    return c.convert("RGB")


def covers_and_social(rec, fonts, tx, lang, out):
    vd = os.path.join(out, "05_Trailers", "Social_Media")
    os.makedirs(vd, exist_ok=True)
    tc = tx["trailer_captions"]
    compose(1280, 720, rec, fonts, tx, lang, "wide", 9, "02_prizma", title=tc["hook"], sub=tc["variety"]).save(
        os.path.join(vd, f"youtube_cover_{lang}_1280x720.png"), optimize=True)
    compose(1080, 1920, rec, fonts, tx, lang, "tall", 13, "02_prizma", title=tc["hook"], sub=tc["variety"]).save(
        os.path.join(vd, f"reels_cover_{lang}_1080x1920.png"), optimize=True)
    sd = os.path.join(out, "07_Social_Media", "Turkish" if lang == "tr" else "English")
    os.makedirs(sd, exist_ok=True)
    caps = tx["screenshot_captions"]
    items = [
        ("instagram_post_1080x1080", 1080, 1080, "square", "01_mantik", None, caps[0][0], None),
        ("instagram_portrait_1080x1350", 1080, 1350, "square", "02_prizma", None, caps[1][0], None),
        ("instagram_story_1080x1920", 1080, 1920, "tall", "05_kapilar", None, caps[4][0], caps[4][1]),
        ("reels_cover_1080x1920", 1080, 1920, "tall", "02_prizma", None, tc["hook"], tc["variety"]),
        ("tiktok_cover_1080x1920", 1080, 1920, "tall", "06_bloklar", None, tc["mechanic"], tc["mechanic2"]),
        ("youtube_thumbnail_1280x720", 1280, 720, "wide", "02_prizma", None, tc["hook"], tc["variety"]),
        ("facebook_1200x630", 1200, 630, "wide", "01_mantik", None, tx["slogan"], tc["variety"]),
        ("launch_poster_1080x1920", 1080, 1920, "tall", "01_mantik", tc["cta2"], tx["slogan"], tc["variety"]),
    ]
    for k, (name, W, H, lay, shot, cta, t, s) in enumerate(items):
        compose(W, H, rec, fonts, tx, lang, lay, 30 + k, shot, cta=cta, title=t, sub=s).save(os.path.join(sd, f"{name}_{lang}.png"), optimize=True)
    print("sosyal medya", lang, len(items))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rec", required=True, help="dil klasörlerini içeren kayıt klasörü (rec/<dil>)")
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--sounds", required=True)
    ap.add_argument("--texts", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--langs", default="tr,en,de,it,fr,es")
    ap.add_argument("--only", default="shots,feature,icon,video,social")
    a = ap.parse_args()
    only = set(a.only.split(","))
    for lang in a.langs.split(","):
        rec = os.path.join(a.rec, lang)
        tx = texts(a.texts, lang)
        if "shots" in only: screenshots(rec, a.fonts, tx, lang, a.out)
        if lang in ("tr", "en"):
            if "feature" in only: feature_graphic(rec, a.fonts, tx, lang, a.out)
            if "video" in only: videos(rec, a.fonts, a.sounds, tx, lang, a.out)
            if "social" in only: covers_and_social(rec, a.fonts, tx, lang, a.out)
        if "icon" in only and lang == "tr": app_icon(rec, a.out)


if __name__ == "__main__":
    main()
