"""Uygulama simgesini Android mipmap'lerine ve uyarlanabilir simgeye dönüştürür.
Kullanım: python3 -I tools/art/import_app_icon.py <simge.png> <android/res> <store>"""
import sys, os
import numpy as np
from PIL import Image

src, res, store = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert('RGBA')
a = np.array(im).astype(np.float32)
H, W = a.shape[:2]
# kenar çerçevesinin hemen içindeki mavi bandın ortanca rengi → arka plan rengi
band = np.concatenate([a[int(H*.08):int(H*.11), int(W*.3):int(W*.7)].reshape(-1, 4), a[int(H*.3):int(H*.7), int(W*.08):int(W*.11)].reshape(-1, 4)])
bgc = tuple(int(v) for v in np.median(band[:, :3], axis=0))
print('arka plan rengi', '#%02X%02X%02X' % bgc)

# yuvarlak köşeli çerçeveyi kırp ve kenarları arka plan rengine yumuşakça kaynaştır (karesel geçiş)
ins = int(W * 0.045)
c = im.crop((ins, ins, W - ins, H - ins)).resize((1024, 1024), Image.LANCZOS)
ca = np.array(c).astype(np.float32)
yy, xx = np.mgrid[0:1024, 0:1024] / 1023.0 - 0.5
d = (np.abs(xx) ** 6 + np.abs(yy) ** 6) ** (1 / 6)          # süperelips mesafe
fade = np.clip((0.5 - d) / 0.06, 0, 1)                       # dış %6'da sön
bgarr = np.zeros_like(ca); bgarr[..., :3] = bgc; bgarr[..., 3] = 255
mix = ca * fade[..., None] + bgarr * (1 - fade[..., None])
mix[..., 3] = 255
full = Image.fromarray(mix.astype(np.uint8), 'RGBA')       # kare, kenarı arka planla birleşik simge

# uyarlanabilir simge: 108dp katman, görünen bölge ortadaki 72dp; içerik dairesel maskeye sığsın diye %88
L = 432
fg = Image.new('RGBA', (L, L), (0, 0, 0, 0))
s = int(L * 72 / 108 * 0.88)
ff = full.resize((s, s), Image.LANCZOS)
# ön katmanın da kenarı saydamlaşsın (arka planla dikişsiz)
fa = np.array(ff).astype(np.float32)
yy, xx = np.mgrid[0:s, 0:s] / (s - 1.0) - 0.5
d = (np.abs(xx) ** 6 + np.abs(yy) ** 6) ** (1 / 6)
fa[..., 3] = 255 * np.clip((0.5 - d) / 0.05, 0, 1)
ff = Image.fromarray(fa.astype(np.uint8), 'RGBA')
fg.alpha_composite(ff, ((L - s) // 2, (L - s) // 2))
os.makedirs(os.path.join(res, 'drawable-nodpi'), exist_ok=True)
fg.save(os.path.join(res, 'drawable-nodpi', 'ic_launcher_foreground.png'), optimize=True)
open(os.path.join(res, 'values', 'ic_launcher_background.xml'), 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#%02X%02X%02X</color>\n</resources>\n' % bgc)

# eski tip simgeler: kare = orijinal yuvarlak köşeli simge; yuvarlak = dairesel kırpım
orig = im.crop(im.getchannel('A').getbbox())
for folder, px in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    d = os.path.join(res, 'mipmap-' + folder); os.makedirs(d, exist_ok=True)
    orig.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher.png'), optimize=True)
    big = full.resize((px * 4, px * 4), Image.LANCZOS)
    m = Image.new('L', big.size, 0)
    from PIL import ImageDraw
    ImageDraw.Draw(m).ellipse((0, 0, big.width - 1, big.height - 1), fill=255)
    big.putalpha(m)
    big.resize((px, px), Image.LANCZOS).save(os.path.join(d, 'ic_launcher_round.png'), optimize=True)
orig.resize((512, 512), Image.LANCZOS).save(os.path.join(store, 'icon_512.png'))
full.save(os.path.join(store, 'icon_full_square.png'))
print('tamam')
