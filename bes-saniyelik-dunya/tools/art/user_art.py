"""Kullanıcının ürettiği görselleri (art_input/) oyuna hazır hale getirir.

- Beyaz arka plan artıklarını temizler, zemin üst karolarının üstünü şeffaf yapar
- Karo kenarlarını dikişsiz birleştirir (çapraz geçiş)
- Şeffaf olmayan paralaks katmanlarının üstünü gökyüzüne eritir
- Karakter karelerini kırpar, ayak çizgisine hizalar ve bütün görünümleri aynı boya getirir
- Süsleri ayırır ve tutarlı dünya ölçeğine getirir
Bu modül gen_art.py tarafından kullanılır; tek başına çalışmaz.
"""
import os, glob
import numpy as np
from PIL import Image, ImageFilter

SRC = None  # art_input klasörü
_files = {}


def init(src_dir):
    """Bütün parçalardaki dosyaları toplar; sonra gelen (duzeltilmis) öncekinin üzerine yazar."""
    global SRC
    SRC = src_dir
    order = ['parca1', 'parca2', 'parca3', 'parca4', 'duzeltilmis']
    for part in order:
        for f in glob.glob(os.path.join(src_dir, part, '**', '*.png'), recursive=True):
            if '/references/' in f:
                continue
            _files[os.path.basename(f)] = f
    # ana karakter: hero_sheet = Pati
    if 'hero_pati_sheet.png' not in _files and 'hero_sheet.png' in _files:
        _files['hero_pati_sheet.png'] = _files['hero_sheet.png']
    return len(_files)


def has(name):
    return name in _files


def load(name):
    return Image.open(_files[name]).convert('RGBA')


# ------------------------------------------------------------------ yardımcılar
def arr(im):
    return np.asarray(im).astype(np.float32)


def img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), 'RGBA')


def clean_white(im, thr=236):
    """Kenara bağlı, opak, beyaza yakın pikselleri (silinmemiş arka plan) şeffaf yapar."""
    a = arr(im)
    h, w = a.shape[:2]
    rgb = a[..., :3]
    white = (rgb.min(axis=2) > thr) & (a[..., 3] > 20)
    seed = np.zeros_like(white)
    seed[0, :] = white[0, :]; seed[-1, :] = white[-1, :]; seed[:, 0] = white[:, 0]; seed[:, -1] = white[:, -1]
    mask = _flood(white, seed)
    if mask.any():
        soft = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(np.float32) / 255
        a[..., 3] = a[..., 3] * (1 - soft)
    # çok düşük alfalı sis artıklarını kaldır
    a[..., 3] = np.where(a[..., 3] < 22, 0, a[..., 3])
    return img(a)


def _flood(passable, seed):
    """İkili maske üzerinde 4-komşu taşma doldurma. Büyük görsellerde hız için önce
    küçültülmüş maskede doldurur, sonra tam çözünürlükte sınırı rafine eder."""
    h, w = passable.shape
    f = 1
    while h // f * (w // f) > 160_000:
        f *= 2
    if f > 1:
        hp, wp = h // f * f, w // f * f
        sp = passable[:hp, :wp].reshape(hp // f, f, wp // f, f).mean(axis=(1, 3)) > 0.5
        ss = seed[:hp, :wp].reshape(hp // f, f, wp // f, f).any(axis=(1, 3))
        small = _flood_iter(sp, ss)
        big = np.zeros_like(passable)
        big[:hp, :wp] = np.repeat(np.repeat(small, f, axis=0), f, axis=1)
        # sınırda tam çözünürlükte birkaç adım genişlet
        cur = big & passable
        for _ in range(f * 2):
            nxt = cur.copy()
            nxt[1:, :] |= cur[:-1, :]; nxt[:-1, :] |= cur[1:, :]; nxt[:, 1:] |= cur[:, :-1]; nxt[:, :-1] |= cur[:, 1:]
            cur = nxt & passable
        return cur
    return _flood_iter(passable, seed)


def _flood_iter(passable, seed):
    cur = seed & passable
    while True:
        nxt = cur.copy()
        nxt[1:, :] |= cur[:-1, :]; nxt[:-1, :] |= cur[1:, :]; nxt[:, 1:] |= cur[:, :-1]; nxt[:, :-1] |= cur[:, 1:]
        nxt &= passable
        if (nxt == cur).all():
            return cur
        cur = nxt


def trim(im, pad=2):
    a = np.asarray(im)[..., 3]
    ys, xs = np.where(a > 12)
    if len(xs) == 0:
        return im
    x0, x1 = max(0, xs.min() - pad), min(im.width, xs.max() + 1 + pad)
    y0, y1 = max(0, ys.min() - pad), min(im.height, ys.max() + 1 + pad)
    return im.crop((x0, y0, x1, y1))


def trim_main(im, gap=6, axis_cols=True):
    """Dikeyde en çok içeriğe sahip bitişik satır grubunu tutar; ince ayrık çizgileri atar."""
    a = np.asarray(im)[..., 3]
    occ = (a > 30).sum(axis=1)
    rows = occ > 5
    runs, y = [], 0
    while y < len(rows):
        if rows[y]:
            s0 = y
            while y < len(rows) and (rows[y] or rows[y:y + gap].any()):
                y += 1
            runs.append((s0, y))
        y += 1
    if not runs:
        return im
    best = max(runs, key=lambda r: occ[r[0]:r[1]].sum())
    out = im.crop((0, best[0], im.width, best[1]))
    if axis_cols:
        return trim_main(out.transpose(Image.TRANSPOSE), gap, False).transpose(Image.TRANSPOSE)
    return trim(out)


def fit_h(im, h):
    s = h / im.height
    return im.resize((max(1, int(im.width * s)), int(h)), Image.LANCZOS)


def fit_w(im, w):
    s = w / im.width
    return im.resize((int(w), max(1, int(im.height * s))), Image.LANCZOS)


def crop_white_borders(im, thr=240, frac=0.9):
    """Kenarlardaki tamamen beyaz satır/sütunları kırpar (kaydırılmış görseller)."""
    a = arr(im)
    white = a[..., :3].min(axis=2) > thr
    def cut(lines):
        n = 0
        for row in lines:
            if row.mean() > frac: n += 1
            else: break
        return n
    t = cut(white); b = cut(white[::-1]); l = cut(white.T); r = cut(white.T[::-1])
    if t + b + l + r == 0:
        return im
    return im.crop((l, t, im.width - r, im.height - b)).resize(im.size, Image.LANCZOS)


def fix_dark_lines(im):
    """Kesim artığı koyu yatay bantları giderir. Alt yarıda bant varsa altındaki (başka karoya ait)
    kısım atılıp karo yeniden boyutlandırılır; diğer bantlar komşu satırlarla doldurulur."""
    a = arr(im)
    lum = a[..., :3].mean(axis=2).mean(axis=1)
    H = a.shape[0]
    med = np.median(lum)
    dark = lum < med * 0.6
    ys = np.where(dark)[0]
    if len(ys) == 0:
        return im
    first_low = [y for y in ys if y > H * 0.5]
    if first_low:
        cut = min(first_low)
        return img(a[:cut]).resize(im.size, Image.LANCZOS)
    for y in ys:
        lo, hi = max(0, y - 3), min(H - 1, y + 3)
        a[y] = (a[lo] + a[hi]) / 2
    return img(a)


def seam_h(im, frac=0.12):
    """Sağ kenarı sol kenara çapraz geçişle bağlar → yatayda dikişsiz."""
    a = arr(im); w = a.shape[1]; k = max(4, int(w * frac))
    out = a.copy()
    for i in range(k):
        t = (i + 1) / (k + 1)
        out[:, w - k + i] = a[:, w - k + i] * (1 - t) + a[:, i] * t
    return img(out)


def seam_v(im, frac=0.12):
    a = arr(im); h = a.shape[0]; k = max(4, int(h * frac))
    out = a.copy()
    for i in range(k):
        t = (i + 1) / (k + 1)
        out[h - k + i] = a[h - k + i] * (1 - t) + a[i] * t
    return img(out)


def strip_top_whites(im, frac=0.22):
    """Dolgu karolarının üstündeki beyaz şeritleri kırpıp kalan dokuyu tekrar boyutlandırır."""
    a = arr(im)
    lum = a[..., :3].min(axis=2).mean(axis=1)
    lim = int(im.height * frac)
    cut = 0
    for y in range(lim):
        if lum[y] > 215: cut = y + 1
    if cut == 0:
        return im
    return im.crop((0, cut, im.width, im.height)).resize(im.size, Image.LANCZOS)


# ------------------------------------------------------------------ karolar
def ground_top(m, size=256):
    n = 'map%02d_ground_top.png' % m
    if not has(n): return None
    im = crop_white_borders(load(n))
    im = fix_dark_lines(im)
    # üstteki beyaz gökyüzü / arka plan şeffaf olsun
    a = arr(im)
    white = a[..., :3].min(axis=2) > 232
    seed = np.zeros_like(white); seed[0, :] = True
    mask = _flood(white, seed)
    soft = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5))).astype(np.float32) / 255
    a[..., 3] *= (1 - soft)
    im = seam_h(img(a))
    return im.resize((size, size), Image.LANCZOS)


def ground_fill(m, size=256):
    n = 'map%02d_ground_fill.png' % m
    if not has(n): return None
    im = strip_top_whites(crop_white_borders(load(n)))
    im = fix_dark_lines(im)
    a = arr(im); a[..., 3] = 255
    im = seam_v(seam_h(img(a)))
    return im.resize((size, size), Image.LANCZOS)


def platform_parts(m, cell_w=256):
    """Platform görselini sol/orta/sağ parçalara böler; yürünen üst kenar görselin üstüdür."""
    n = 'map%02d_platform.png' % m
    if not has(n): return None
    im = clean_white(load(n))
    w3 = im.width // 3
    cells = [im.crop((i * w3, 0, (i + 1) * w3, im.height)) for i in range(3)]
    a = np.asarray(im)[..., 3]
    occ = (a > 30).sum(axis=1)
    # hücre sınırı çizgileri: çok ince ayrık satırları yok say, en büyük gövdeyi bul
    rows = occ > im.width * 0.02
    runs, y = [], 0
    while y < len(rows):
        if rows[y]:
            s0 = y
            while y < len(rows) and rows[y]: y += 1
            runs.append((s0, y))
        y += 1
    y0, y1 = max(runs, key=lambda r: occ[r[0]:r[1]].sum())
    out = []
    for i, c in enumerate(cells):
        c = c.crop((0, y0, c.width, y1))
        # sol ve sağ uçlarda boşluğu kırp, ortayı tam genişlikte bırak
        ca = np.asarray(c)[..., 3]
        xs = np.where(ca.max(axis=0) > 20)[0]
        if len(xs):
            if i == 0: c = c.crop((xs.min(), 0, c.width, c.height))
            elif i == 2: c = c.crop((0, 0, xs.max() + 1, c.height))
        if i == 1: c = seam_h(c, 0.15)
        h = int(c.height * cell_w / w3)
        out.append(c.resize((max(8, int(c.width * cell_w / w3)), max(8, h)), Image.LANCZOS))
    return out  # [sol, orta, sağ]; yükseklik/genişlik oranı görselin kendisinden


def decor_props(m, px_per_unit=110, tallest_units=3.0):
    """Süs sayfasından 6 nesne çıkarır; en uzun nesne yaklaşık 3 karo olur."""
    n = 'map%02d_decor.png' % m
    if not has(n): return None
    im = clean_white(load(n))
    a = np.asarray(im)[..., 3]
    col = (a > 30).sum(axis=0) > 3
    runs = []
    x = 0
    while x < len(col):
        if col[x]:
            s = x
            while x < len(col) and (col[x] or (x + 12 < len(col) and col[x:x + 12].any())):
                x += 1
            runs.append((s, x))
        x += 1
    runs = [r for r in runs if r[1] - r[0] > 30]
    if not (4 <= len(runs) <= 8):
        cw = im.width // 6
        runs = [(i * cw, (i + 1) * cw) for i in range(6)]
    runs = sorted(sorted(runs, key=lambda r: r[1] - r[0], reverse=True)[:6])
    props = [trim_main(im.crop((r0, 0, r1, im.height))) for r0, r1 in runs]
    props = [p for p in props if p.height > 20]
    hmax = max(p.height for p in props)
    out = []
    for p in props:
        units = max(0.9, tallest_units * p.height / hmax)
        out.append(fit_h(p, int(units * px_per_unit)))
    return out


def sky(m):
    n = 'map%02d_sky.png' % m
    if not has(n): return None
    im = load(n).convert('RGB')
    # Yatay ekran: dikey gökyüzünden 16:9 bant (üst-orta bölge), kaynak genişliğinde (kayıpsız)
    w = im.width
    h = int(w * 9 / 16)
    y0 = max(0, min(im.height - h, int(im.height * 0.38) - h // 2))
    return im.crop((0, y0, w, y0 + h))


def parallax(m, layer, w=2048, h=1024):
    """(rgb, alfa) çifti döndürür. Şeffaf olmayan katmanlarda üst kısım gökyüzüne eritilir."""
    n = 'map%02d_%s.png' % (m, layer)
    if not has(n): return None
    im = clean_white(load(n))
    a = arr(im)
    # Üst kenara bağlı açık gökyüzü piksellerini (soluk mavi/pembe/beyaz) şeffaf yap: oyunun kendi gökyüzü görünsün
    rgb = a[..., :3]
    bright = rgb.mean(axis=2)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    skyish = (bright > 205) & (sat < 70) & (a[..., 3] > 0)
    H0 = a.shape[0]
    # içeriğin başladığı satır (bazı katmanlar ortadan başlayan düz kenarlı şeritler)
    cov = (a[..., 3] > 128).mean(axis=1)
    rows = np.where(cov > 0.3)[0]
    ctop = int(rows[0]) if len(rows) else 0
    upper = np.zeros_like(skyish); upper[: ctop + int((H0 - ctop) * 0.45), :] = True  # yalnızca içeriğin üst bölgesi
    seed = np.zeros_like(skyish); seed[0, :] = True
    sky_mask = _flood((skyish | (a[..., 3] < 20)) & upper, seed)
    soft = np.asarray(Image.fromarray((sky_mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(6))).astype(np.float32) / 255
    a[..., 3] *= (1 - soft)
    # her katmanın en üstü yumuşak geçişle erir (dikdörtgen kenar görünmesin)
    top_ramp = np.clip((np.arange(H0) - ctop) / max(1.0, (H0 - ctop) * 0.2), 0, 1)
    a[..., 3] *= top_ramp[:, None]
    if (a[..., 3] < 10).mean() < 0.05:  # hâlâ opak: üstü daha geniş erit
        H = a.shape[0]
        ramp = np.clip((np.arange(H) / H - (0.05 if layer == 'far' else 0.15)) / 0.38, 0, 1)
        ramp = ramp * ramp * (3 - 2 * ramp)
        a[..., 3] = a[..., 3] * ramp[:, None]
    im = seam_h(img(a), 0.1).resize((w, h), Image.LANCZOS)
    rgb = Image.new('RGB', im.size, (200, 225, 245))
    rgb.paste(im.convert('RGB'), (0, 0))
    return rgb, im.split()[3]


# ------------------------------------------------------------------ karakterler
HERO_FRAME_H = 200  # kaynak kare çözünürlüğüne yakın: telefonda küçültülerek (keskin) çizilir  # bekleme karesi hedef yüksekliği (piksel)


def hero_frames(skin_id):
    """16 kareyi kırpar, ayak çizgisine (alt orta) hizalar ve görünümler arası aynı boya ölçekler."""
    n = 'hero_%s_sheet.png' % skin_id
    if not has(n): return None
    im = load(n)
    cw, ch = im.width // 8, im.height // 2
    frames = []
    for i in range(16):
        c = im.crop(((i % 8) * cw, (i // 8) * ch, (i % 8 + 1) * cw, (i // 8 + 1) * ch))
        c = clean_white(c)
        a = arr(c)
        haze = (a[..., 3] < 130) & (a[..., :3].min(axis=2) > 195)
        a[..., 3] = np.where(haze | (a[..., 3] < 40), 0, a[..., 3]); c = img(a)
        frames.append(c)
    # ölçek: koşu ve bekleme karelerinin medyan yüksekliği
    def bbox(c):
        al = np.asarray(c)[..., 3]; ys, xs = np.where(al > 40)
        return (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1) if len(xs) else (0, 0, c.width, c.height)
    ref = np.median([bbox(frames[i])[3] - bbox(frames[i])[1] for i in (0, 1, 2, 3, 4, 5, 15)])
    s = HERO_FRAME_H / max(1, ref)
    out = []
    for c in frames:
        x0, y0, x1, y1 = bbox(c)
        cx = (x0 + x1) / 2
        half = max(cx - x0, x1 - cx) + 2
        box = (int(cx - half), max(0, y0 - 2), int(cx + half), y1)
        piece = c.crop(box)
        out.append(piece.resize((max(4, int(piece.width * s)), max(4, int(piece.height * s))), Image.LANCZOS))
    return out


# ------------------------------------------------------------------ nesneler ve arayüz
def obj(name, max_side):
    if not has(name): return None
    im = trim(clean_white(load(name)))
    s = max_side / max(im.size)
    if s < 1:
        im = im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)
    return im


def ui_plain(name, size):
    """Simge/glif: arka planı olduğu gibi, kare boyuta küçültür."""
    if not has(name): return None
    im = load(name)
    return im.resize((size, size), Image.LANCZOS)


def coin_frames(size=96):
    if not has('coin_sheet.png'): return None
    im = clean_white(load('coin_sheet.png'))
    cw = im.width // 6
    cells = [im.crop((i * cw, 0, (i + 1) * cw, im.height)) for i in range(6)]
    a = np.asarray(im)[..., 3]
    ys = np.where(a.max(axis=1) > 20)[0]
    y0, y1 = ys.min(), ys.max() + 1
    out = []
    for c in cells:
        c = c.crop((0, y0, c.width, y1))
        side = max(c.size)
        sq = Image.new('RGBA', (side, side))
        sq.paste(c, ((side - c.width) // 2, (side - c.height) // 2))
        out.append(sq.resize((size, size), Image.LANCZOS))
    return out


def button_patch(name, size=120):
    if not has(name): return None
    im = load(name).resize((size, size), Image.LANCZOS)
    k = int(size * 0.36)
    return im, (k, k, k, int(size * 0.4))


def panel_patch(size=160):
    if not has('panel.png'): return None
    im = load('panel.png').resize((size, size), Image.LANCZOS)
    k = int(size * 0.3)
    return im, (k, k, k, k)
