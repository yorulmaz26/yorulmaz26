"""Kullanıcının ürettiği görselleri oyun varlıklarına dönüştürür.
Kullanım: python3 -I tools/art/import_user_art.py <açılmış_zip_kökü> <android/assets> <store>
"""
import sys, os, glob
from collections import deque
from PIL import Image, ImageFilter, ImageEnhance, ImageOps

src, assets, store = sys.argv[1], sys.argv[2], sys.argv[3]

def find(prefix):
    m = glob.glob(os.path.join(src, '*', prefix + '*.png'))
    if not m: raise SystemExit('bulunamadı: ' + prefix)
    return m[0]

def out(rel):
    p = os.path.join(assets, rel); os.makedirs(os.path.dirname(p), exist_ok=True); return p

def trim(im, pad=6):
    bb = im.getchannel('A').point(lambda a: 255 if a > 10 else 0).getbbox()
    if bb: im = im.crop((max(0, bb[0]-pad), max(0, bb[1]-pad), min(im.width, bb[2]+pad), min(im.height, bb[3]+pad)))
    return im

def pot_canvas(im, w, h, anchor='center'):
    """Görseli w×h (2'nin kuvveti) şeffaf tuvale sığdırır → mobilde mipmap ile keskin küçülür."""
    im = im.copy(); im.thumbnail((w, h), Image.LANCZOS)
    c = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    y = (h - im.height) // 2 if anchor == 'center' else h - im.height
    c.alpha_composite(im, ((w - im.width) // 2, y)); return c

def fit(im, maxside):
    im = im.copy(); im.thumbnail((maxside, maxside), Image.LANCZOS); return im

def remove_fake_bg(im, sat_max=28, light_min=150, keep_border=0):
    """Kenardan başlayarak düşük doygunluklu açık (beyaz/gri dama) pikselleri şeffaf yapar."""
    im = im.convert('RGBA'); w, h = im.size; px = im.load()
    def bgish(p):
        r, g, b, a = p
        return max(r, g, b) - min(r, g, b) <= sat_max and min(r, g, b) >= light_min
    seen = bytearray(w * h); q = deque()
    for x in range(w):
        q.append((x, 0)); q.append((x, h-1))
    for y in range(h):
        q.append((0, y)); q.append((w-1, y))
    mask = Image.new('L', (w, h), 255); mp = mask.load()
    while q:
        x, y = q.popleft(); i = y * w + x
        if seen[i]: continue
        seen[i] = 1
        if not bgish(px[x, y]): continue
        mp[x, y] = 0
        for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
            nx, ny = x+dx, y+dy
            if 0 <= nx < w and 0 <= ny < h and not seen[ny*w+nx]: q.append((nx, ny))
    if keep_border:
        mask = mask.filter(ImageFilter.MaxFilter(keep_border*2+1))
    mask = mask.filter(ImageFilter.GaussianBlur(0.8))
    im.putalpha(Image.composite(im.getchannel('A'), Image.new('L', (w, h), 0), mask) if im.mode == 'RGBA' else mask)
    return im

def split_columns_or_rows(im, axis):
    """Saydam boşluklarla ayrılmış parçaları böler (axis 0: yatay, 1: dikey)."""
    a = im.getchannel('A'); w, h = im.size
    n = w if axis == 0 else h
    occ = []
    for i in range(n):
        box = (i, 0, i+1, h) if axis == 0 else (0, i, w, i+1)
        occ.append(a.crop(box).getextrema()[1] > 30)
    parts, start = [], None
    for i, o in enumerate(occ + [False]):
        if o and start is None: start = i
        if not o and start is not None:
            if i - start > 8: parts.append((start, i))
            start = None
    res = []
    for s, e in parts:
        box = (s, 0, e, h) if axis == 0 else (0, s, w, e)
        res.append(trim(im.crop(box), 2))
    return res

report = []

# 11–20 dünya arka planları → 720x1280 JPG
for n in range(11, 21):
    im = Image.open(find(str(n))).convert('RGB').resize((720, 1280), Image.LANCZOS)
    im.save(out(f'bg/world_{n}.jpg'), quality=86, optimize=True); report.append(f'bg/world_{n}.jpg')

# 21–30 karakterler
chars = {21: 'char_klasik', 22: 'char_top', 23: 'char_robot', 24: 'char_panda', 25: 'char_kedi',
         26: 'char_uzayli', 27: 'char_kristal', 28: 'char_bulut', 29: 'char_yildiz', 30: 'char_kral'}
for n, cid in chars.items():
    im = Image.open(find(str(n))).convert('RGBA')
    im = pot_canvas(trim(im), 512, 512, anchor='bottom')
    im.save(out(f'chars/{cid}.png'), optimize=True); report.append(f'chars/{cid}.png {im.size}')

# oyun nesneleri
def obj(n, name, w, h):
    raw = trim(Image.open(find(str(n))).convert('RGBA'))
    im = pot_canvas(raw, w, h); im.save(out(f'objects/{name}.png'), optimize=True)
    report.append(f'objects/{name}.png {im.size}'); return raw
obj(36, 'gate_hidden', 1024, 256)
obj(37, 'coin', 256, 256)
dia = obj(38, 'diamond', 256, 256)
shi = obj(39, 'shield', 256, 256)
obj(40, 'pad_shift', 512, 128)

def icon(im, name):
    c = Image.new('RGBA', (128, 128), (0, 0, 0, 0)); t = fit(im, 112)
    c.alpha_composite(t, ((128 - t.width)//2, (128 - t.height)//2)); c.save(out(f'icons/{name}.png'), optimize=True)
    report.append(f'icons/{name}.png')
icon(dia, 'diamond'); icon(shi, 'shield')

# arayüz
logo = trim(remove_fake_bg(Image.open(find('41'))), 4)
logo = pot_canvas(logo, 512, 512)
logo.save(out('ui2/logo.png'), optimize=True); report.append(f'ui2/logo.png {logo.size}')

Image.open(find('42')).convert('RGB').resize((720, 1280), Image.LANCZOS).save(out('ui2/menu_bg.jpg'), quality=86)
Image.open(find('45')).convert('RGB').resize((720, 1280), Image.LANCZOS).save(out('ui2/map_bg.jpg'), quality=86)
report += ['ui2/menu_bg.jpg', 'ui2/map_bg.jpg']

btns = split_columns_or_rows(remove_fake_bg(Image.open(find('43'))), 1)
report.append(f'buton sayısı: {len(btns)} {[b.size for b in btns]}')
# beyaz (boyanabilir) parlak buton: yeşil butonun parlaklığından türetilir
g = btns[0]
lum = ImageOps.grayscale(g.convert('RGB'))
lum = ImageOps.autocontrast(lum, cutoff=1)
lum = lum.point(lambda v: int(150 + v * 105 / 255))  # 150..255 → boyandığında renk korunur, parlama beyaz kalır
wb = Image.merge('RGBA', (lum, lum, lum, g.getchannel('A')))
wb = wb.resize((wb.width * 3, wb.height * 3), Image.LANCZOS)
wb.save(out('ui2/button.png')); report.append(f'ui2/button.png {wb.size}')

panel = trim(remove_fake_bg(Image.open(find('44'))), 2)
panel = panel.resize((panel.width * 2, panel.height * 2), Image.LANCZOS)
panel.save(out('ui2/panel.png')); report.append(f'ui2/panel.png {panel.size}')

stars = trim(remove_fake_bg(Image.open(find('46')), sat_max=40, light_min=200), 2)
stars.save(out('ui2/stars_reward.png')); report.append(f'ui2/stars_reward.png {stars.size}')

chest = trim(remove_fake_bg(Image.open(find('47'))), 2)
icon(chest, 'box'); chest.save(out('ui2/chest.png')); report.append(f'ui2/chest.png {chest.size}')

hearts = split_columns_or_rows(remove_fake_bg(Image.open(find('48')), sat_max=24, light_min=200), 0)
report.append(f'kalp sayısı: {len(hearts)}')
if len(hearts) >= 2:
    icon(hearts[0], 'heart_full'); icon(hearts[1], 'heart_empty')

fg = Image.open(find('49')).convert('RGB').resize((1024, 500), Image.LANCZOS)
fg.save(os.path.join(store, 'feature_graphic.png')); report.append('store/feature_graphic.png')

print('\n'.join(report))
