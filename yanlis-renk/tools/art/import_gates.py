"""Kapı görsellerini boyanabilir gövde + boyanmayan süs katmanlarına ayırır.
Gövde = düşük doygunluklu beyaz/gri kapsül pikselleri (direkler ve amblem hariç).
Kullanım: python3 -I tools/art/import_gates.py <klasör> <android/assets>"""
import sys, os, glob, json, math
from PIL import Image, ImageFilter

src, assets = sys.argv[1], sys.argv[2]
os.makedirs(os.path.join(assets, 'gates'), exist_ok=True)

def find(p):
    return glob.glob(os.path.join(src, '**', p + '*.png'), recursive=True)[0]

def canvas(im):
    bb = im.getchannel('A').point(lambda v: 255 if v > 10 else 0).getbbox(); im = im.crop(bb)
    s = min(1024 / im.width, 256 / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    c = Image.new('RGBA', (1024, 256), (0, 0, 0, 0)); c.alpha_composite(im, ((1024 - im.width) // 2, (256 - im.height) // 2))
    return c, ((1024 - im.width) // 2, im.width)

def gold(p): r, g, b, a = p; return a > 128 and r > 200 and 120 < g < 225 and b < 110
def red(p): r, g, b, a = p; return a > 128 and r > 190 and g < 110 and b < 110

def post_bounds(im):
    """Sol/sağ direklerin iç kenarı: yalnızca kenar bölgelerindeki altın halkalara bakılır."""
    px = im.load(); w, h = im.size
    left = [x for x in range(0, int(w * 0.25)) if sum(gold(px[x, y]) for y in range(h)) > 3]
    right = [x for x in range(int(w * 0.80), w) if sum(gold(px[x, y]) for y in range(h)) > 3]
    return (max(left) if left else int(w * 0.15)), (min(right) if right else int(w * 0.85))

def ring(im, test, x0, x1):
    """Belirli renkteki halkanın merkezi ve dış yarıçapı."""
    px = im.load(); w, h = im.size
    pts = [(x, y) for x in range(x0, x1) for y in range(h) if test(px[x, y])]
    if len(pts) < 50: return None
    # merkez: halkanın sınır kutusunun ortası (kütle merkezi zil/gölge yüzünden kayabilir)
    xs = sorted(p[0] for p in pts); ys = sorted(p[1] for p in pts)
    lo, hi = int(len(xs) * 0.01), int(len(xs) * 0.99) - 1
    cx = (xs[lo] + xs[hi]) / 2; cy = (ys[lo] + ys[hi]) / 2
    return cx, cy, max(xs[hi] - xs[lo], ys[hi] - ys[lo]) / 2

meta = {}
for key, name in [('31', 'normal'), ('32', 'ters'), ('33', 'zaman'), ('35', 'sahte')]:
    im, (x0, cw) = canvas(Image.open(find(key)).convert('RGBA'))
    w, h = im.size; px = im.load()
    pl, pr = post_bounds(im)
    blob = None
    if name in ('normal', 'ters'): blob = ring(im, gold, int(w * 0.32), int(w * 0.68))
    if name == 'zaman': blob = ring(im, red, int(w * 0.55), pr - 2)
    body = Image.new('L', (w, h), 0); bp = body.load()
    # kapsülün dikey aralığı: direklerin arasında, amblem dışındaki bir sütundan ölçülür
    probe = int(pl + (pr - pl) * 0.18)
    ys = [y for y in range(h) if px[probe, y][3] > 128]
    ytop, ybot = (min(ys), max(ys)) if ys else (0, h - 1)
    excl = 0 if not blob else (blob[2] + 4)
    for y in range(ytop, ybot + 1):
        for x in range(pl - 55, pr + 56):
            r, g, b, a = px[x, y]
            if a < 10 or gold(px[x, y]): continue
            if blob and math.hypot(x - blob[0], y - blob[1]) <= excl: continue
            if max(r, g, b) - min(r, g, b) <= 40 and min(r, g, b) >= 95: bp[x, y] = a
    # direk sütunlarını (beyaz silindir) gövdeden çıkar: kapsül dışındaki satırlarda da beyaz olan sütunlar direktir
    for x in list(range(pl - 55, pl + 1)) + list(range(pr, pr + 56)):
        above = sum(1 for y in range(0, max(0, ytop - 4)) if px[x, y][3] > 128 and not gold(px[x, y]))
        below = sum(1 for y in range(min(h, ybot + 4), h) if px[x, y][3] > 128 and not gold(px[x, y]))
        if above > 6 and below > 2:
            for y in range(h): bp[x, y] = 0
    body = body.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
    A = im.getchannel('A')
    body_img = im.copy(); body_img.putalpha(body)
    deco = im.copy(); deco.putalpha(Image.eval(Image.merge('L', [A]).point(lambda v: v), lambda v: v))
    deco_a = Image.new('L', (w, h)); da = deco_a.load(); Ap = A.load()
    for y in range(h):
        for x in range(w): da[x, y] = max(0, Ap[x, y] - bp[x, y])
    deco.putalpha(deco_a)
    body_img.save(os.path.join(assets, 'gates', f'{name}_body.png'), optimize=True)
    deco.save(os.path.join(assets, 'gates', f'{name}_deco.png'), optimize=True)
    meta[name] = {'postL': pl / w, 'postR': pr / w, 'contentL': x0 / w, 'contentW': cw / w,
                  'blob': [blob[0] / w, blob[1] / h, blob[2] / w] if blob else None}
    print(name, 'direk', pl, pr, 'amblem', blob and [round(v) for v in blob])
# yalnızca direkler (amblemsiz) — normal kapının süs katmanından amblemi sil
d = Image.open(os.path.join(assets, 'gates', 'normal_deco.png')); dp = d.load()
pl, pr = meta['normal']['postL'] * 1024, meta['normal']['postR'] * 1024
for y in range(256):
    for x in range(int(pl) + 2, int(pr) - 1): dp[x, y] = (0, 0, 0, 0)
d.save(os.path.join(assets, 'gates', 'posts_deco.png'), optimize=True)
# meta yalnızca bilgi amaçlı yazdırılır
print(json.dumps(meta))

# amblemsiz kapılar için ortası dolu gövde: amblem bölgesini soldaki kapsül sütunuyla doldur
nb = Image.open(os.path.join(assets, 'gates', 'normal_body.png')).convert('RGBA'); nbp = nb.load()
src_img, _ = canvas(Image.open(find('31')).convert('RGBA')); sp = src_img.load()
m = meta['normal']['blob']; cx, r = m[0] * 1024, m[2] * 1024 + 8
x_src = int(cx - r - 6)
for x in range(int(cx - r - 4), int(cx + r + 5)):
    for y in range(256):
        if nbp[x, y][3] == 0 and nbp[x_src, y][3] > 0:
            nbp[x, y] = nbp[x_src, y]
nb.save(os.path.join(assets, 'gates', 'normal_body_full.png'), optimize=True)
print('normal_body_full hazır')
