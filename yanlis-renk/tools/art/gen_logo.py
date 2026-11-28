#!/usr/bin/env python3
"""Marka logoları: orijinal "Yanlış Renk" balon harf logosunun stiline uygun "Miscolor" (İngilizce) logosu,
iki dilli kombinasyonlar ve açık/koyu zeminli sürümler.
Kullanım: python3 tools/art/gen_logo.py <çıktı_klasörü>   (ayrıca oyun içi android/assets/ui2/logo_en.png üretir)"""
import os, sys, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = os.path.join(ROOT, "android/assets/fonts/Nunito-ExtraBold.ttf")
TR_LOGO = os.path.join(ROOT, "android/assets/ui2/logo.png")
ICON = os.path.join(ROOT, "store/icon_full_square.png")
# oyunun dört rengi (Kırmızı → Mavi → Yeşil → Sarı)
GAME = [(255, 70, 72), (46, 128, 255), (52, 196, 84), (255, 196, 30)]

def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

def glossy_letter(ch, color, size=420, angle=0.0):
    """Tek harf: dolgun gövde, 3B alt derinlik, dikey degrade, koyu kenar, yumuşak parlak vurgu. RGBA döndürür."""
    font = ImageFont.truetype(FONT, size)
    pad = size // 3
    l, t, r, b = font.getbbox(ch)
    W, H = r - l + pad * 2, b - t + pad * 2
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).text((pad - l, pad - t), ch, font=font, fill=255)
    if angle: m = m.rotate(angle, resample=Image.BICUBIC, expand=False)
    a = np.asarray(m, dtype=np.float32) / 255.0
    inside = a > 0.5
    dist_out = ndimage.distance_transform_edt(~inside)
    body = dist_out <= size * 0.018          # hafif şişirme (i noktası ayrı kalır)
    body = ndimage.gaussian_filter(body.astype(np.float32), size * 0.008) > 0.5
    din = ndimage.distance_transform_edt(body)
    rows = np.where(body.any(axis=1))[0]
    y0, y1 = rows.min(), rows.max()
    yy = np.clip((np.arange(H)[:, None] - y0) / max(1, y1 - y0), 0, 1) * np.ones((1, W))
    c = np.array(color, dtype=np.float32) / 255.0
    light = c + (1 - c) * 0.30
    dark = c * 0.62
    col = light[None, None, :] * (1 - yy[..., None]) ** 1.2 + c[None, None, :] * (1 - (1 - yy[..., None]) ** 1.2)
    rim = np.clip(1 - din / (size * 0.05), 0, 1)[..., None] ** 1.5
    col = col * (1 - 0.5 * rim) + dark[None, None, :] * 0.5 * rim
    # yumuşak parlak vurgu: iç bölgenin üst kısmı, yukarıdan aşağı sönümlenir
    er = din > size * 0.045
    hl = (np.roll(er, -int(size * 0.025), axis=0) & er).astype(np.float32)
    hl *= np.clip(1 - yy / 0.42, 0, 1) ** 1.3
    hlf = ndimage.gaussian_filter(hl, size * 0.02) * 0.75
    col = col * (1 - hlf[..., None]) + hlf[..., None]
    # küçük parlak nokta (sol üst)
    alpha = ndimage.gaussian_filter(body.astype(np.float32), 1.0)
    face = np.dstack([np.clip(col, 0, 1) * 255, alpha * 255]).astype(np.uint8)
    # 3B derinlik: gövdenin koyu kopyası aşağıda
    depth = int(size * 0.05)
    deep = np.zeros((H + depth, W, 4), np.uint8)
    dk = (c * 0.5 * 255).astype(np.uint8)
    for k in range(depth, 0, -1):
        sl = deep[k:k + H]
        mask = alpha > 0.5
        sl[mask, 0:3] = dk; sl[mask, 3] = 255
    im = Image.fromarray(deep, "RGBA")
    im.alpha_composite(Image.fromarray(face, "RGBA"), (0, 0))
    return im.crop(im.getbbox())

def star_img(size, color=(255, 205, 40)):
    S = size * 4
    im = Image.new("L", (S, S), 0)
    pts = []
    for k in range(10):
        r = S * (0.48 if k % 2 == 0 else 0.22)
        a = math.radians(-90 + k * 36)
        pts.append((S / 2 + r * math.cos(a), S / 2 + r * math.sin(a)))
    ImageDraw.Draw(im).polygon(pts, fill=255)
    im = im.filter(ImageFilter.GaussianBlur(S * 0.03)).point(lambda v: 255 if v > 110 else 0)
    im = im.resize((size, size), Image.LANCZOS)
    a = np.asarray(im, dtype=np.float32) / 255
    c = np.array(color, np.float32)
    yy = np.linspace(0, 1, size)[:, None] * np.ones((1, size))
    col = (c + (255 - c) * 0.4)[None, None] * (1 - yy[..., None]) + (c * 0.9)[None, None] * yy[..., None]
    return Image.fromarray(np.dstack([col, a * 255]).astype(np.uint8), "RGBA")

def outline_and_shadow(im, ow, shadow=True):
    """Tüm logo çevresine beyaz kalın kontur + yumuşak gölge."""
    pad = ow * 3
    W, H = im.width + pad * 2, im.height + pad * 2
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    base.alpha_composite(im, (pad, pad))
    a = np.asarray(base)[..., 3] > 40
    d = ndimage.distance_transform_edt(~a)
    ring = ndimage.gaussian_filter((d <= ow).astype(np.float32), 1.2)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if shadow:
        sh = ndimage.gaussian_filter((d <= ow).astype(np.float32), ow * 0.6)
        sh = np.roll(sh, int(ow * 0.7), axis=0)
        shim = Image.fromarray(np.dstack([np.full_like(sh, 70), np.full_like(sh, 90), np.full_like(sh, 140), sh * 120]).astype(np.uint8), "RGBA")
        out.alpha_composite(shim)
    white = Image.fromarray(np.dstack([np.full(ring.shape, 255), np.full(ring.shape, 255), np.full(ring.shape, 255), ring * 255]).astype(np.uint8), "RGBA")
    out.alpha_composite(white)
    out.alpha_composite(base)
    return out.crop(out.getbbox())

def word_logo(text, size=420, colors=None, tilt=True):
    colors = colors or GAME
    letters = []
    for i, ch in enumerate(text):
        ang = (5 if i % 2 == 0 else -4) if tilt else 0
        letters.append(glossy_letter(ch, colors[i % len(colors)], size, ang))
    gap = int(-size * 0.035)
    W = sum(l.width for l in letters) + gap * (len(letters) - 1)
    H = max(l.height for l in letters) + int(size * 0.12)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = 0
    base_line = H - int(size * 0.06)
    for i, l in enumerate(letters):
        bob = int(size * 0.03 * math.sin(i * 1.7))
        # küçük harfler tabana, büyükler yukarı uzar
        canvas.alpha_composite(l, (x, base_line - l.height + bob))
        x += l.width + gap
    return canvas

def miscolor_logo(size=420):
    w = word_logo("Miscolor", size)
    st1, st2 = star_img(int(size * 0.42)), star_img(int(size * 0.28))
    W, H = w.width + int(size * 0.5), w.height + int(size * 0.5)
    c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    c.alpha_composite(w, (int(size * 0.25), int(size * 0.3)))
    c.alpha_composite(st1.rotate(-14, resample=Image.BICUBIC), (W - st1.width - int(size * 0.02), int(size * 0.02)))
    c.alpha_composite(st2.rotate(12, resample=Image.BICUBIC), (int(size * 0.02), H - st2.height - int(size * 0.1)))
    return outline_and_shadow(c.crop(c.getbbox()), int(size * 0.07))

def fit(im, W, H):
    k = min(W / im.width, H / im.height)
    return im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)

def subtitle(text, height, color=(37, 56, 88), outline=(255, 255, 255)):
    font = ImageFont.truetype(FONT, height)
    l, t, r, b = font.getbbox(text)
    ow = max(2, height // 9)
    im = Image.new("RGBA", (r - l + ow * 4, b - t + ow * 4), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((ow * 2 - l, ow * 2 - t), text, font=font, fill=color + (255,), stroke_width=ow, stroke_fill=outline + (255,))
    return im

def stack(main, sub, gap=0):
    W = max(main.width, sub.width)
    im = Image.new("RGBA", (W, main.height + sub.height + gap), (0, 0, 0, 0))
    im.alpha_composite(main, ((W - main.width) // 2, 0))
    im.alpha_composite(sub, ((W - sub.width) // 2, main.height + gap))
    return im

def on_bg(im, size, bg):
    W, H = size
    c = Image.new("RGBA", (W, H), bg + (255,))
    f = fit(im, int(W * 0.86), int(H * 0.86))
    c.alpha_composite(f, ((W - f.width) // 2, (H - f.height) // 2))
    return c

def main(out):
    os.makedirs(out, exist_ok=True)
    en = miscolor_logo(420)
    tr = Image.open(TR_LOGO).convert("RGBA")
    tr = tr.crop(tr.getbbox())
    # orijinal logodaki yarı saydam beyaz bulut koyu zeminde iz bırakıyor: kırp ve beyaz kontur ekle
    arr = np.asarray(tr).copy(); arr[..., 3] = np.where(arr[..., 3] < 90, 0, arr[..., 3]); tr = Image.fromarray(arr, "RGBA")
    tr = outline_and_shadow(fit(tr.crop(tr.getbbox()), 1400, 1400), 30)
    # oyun içi Türkçe logo (kenar boşlukları kırpılmış, beyaz konturlu)
    fit(tr, 900, 720).save(os.path.join(ROOT, "android/assets/ui2/logo_tr.png"), optimize=True)
    # oyun içi (İngilizce ana logo)
    game_en = fit(en, 1024, 600)
    game_en.save(os.path.join(ROOT, "android/assets/ui2/logo_en.png"), optimize=True)
    d = {k: os.path.join(out, k) for k in ("Turkish", "English", "Bilingual", "Transparent")}
    for p in d.values(): os.makedirs(p, exist_ok=True)
    fit(tr, 2048, 2048).save(os.path.join(d["Turkish"], "logo_tr_transparent.png"))
    fit(en, 2400, 1400).save(os.path.join(d["English"], "logo_en_transparent.png"))
    LIGHT, DARK = (248, 251, 255), (27, 36, 64)
    for name, im in (("tr", tr), ("en", en)):
        folder = d["Turkish" if name == "tr" else "English"]
        on_bg(im, (2048, 1152), LIGHT).convert("RGB").save(os.path.join(folder, f"logo_{name}_light_bg.png"))
        on_bg(im, (2048, 1152), DARK).convert("RGB").save(os.path.join(folder, f"logo_{name}_dark_bg.png"))
    # iki dilli: TR ana + EN alt / EN ana + TR alt
    tr_main = fit(tr, 1500, 1100)
    bi_tr = stack(tr_main, subtitle("Miscolor", 150), gap=-40)
    en_main = fit(en, 1700, 900)
    bi_en = stack(en_main, subtitle("Yanlış Renk", 130), gap=10)
    bi_tr.save(os.path.join(d["Bilingual"], "logo_bilingual_tr_main_transparent.png"))
    bi_en.save(os.path.join(d["Bilingual"], "logo_bilingual_en_main_transparent.png"))
    for nm, im in (("tr_main", bi_tr), ("en_main", bi_en)):
        on_bg(im, (2048, 1536), LIGHT).convert("RGB").save(os.path.join(d["Bilingual"], f"logo_bilingual_{nm}_light_bg.png"))
        on_bg(im, (2048, 1536), DARK).convert("RGB").save(os.path.join(d["Bilingual"], f"logo_bilingual_{nm}_dark_bg.png"))
    # şeffaf set + metinsiz simge
    fit(tr, 1024, 1024).save(os.path.join(d["Transparent"], "logo_tr_1024.png"))
    fit(en, 1600, 900).save(os.path.join(d["Transparent"], "logo_en_1600.png"))
    fit(bi_en, 1600, 1200).save(os.path.join(d["Transparent"], "logo_bilingual_en_main_1600.png"))
    fit(bi_tr, 1600, 1200).save(os.path.join(d["Transparent"], "logo_bilingual_tr_main_1600.png"))
    Image.open(ICON).convert("RGBA").save(os.path.join(d["Transparent"], "icon_textless_1024.png"))
    print("logolar:", out)

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "RELEASE_PACKAGE/03_Logos"))
