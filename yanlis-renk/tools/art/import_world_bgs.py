"""Dünya arka planlarını (NN_ad.png) assets/bg/world_N.jpg olarak 720x1280 JPG'ye çevirir.
Kullanım: python3 -I tools/art/import_world_bgs.py <klasör> <android/assets>"""
import sys, os, glob, re
from PIL import Image
src, assets = sys.argv[1], sys.argv[2]
os.makedirs(os.path.join(assets, 'bg'), exist_ok=True)
for f in sorted(glob.glob(os.path.join(src, '**', '*.png'), recursive=True)):
    m = re.match(r'(\d+)_', os.path.basename(f))
    if not m: continue
    n = int(m.group(1))
    if not 1 <= n <= 20: continue
    out = os.path.join(assets, 'bg', f'world_{n}.jpg')
    Image.open(f).convert('RGB').resize((720, 1280), Image.LANCZOS).save(out, quality=86, optimize=True)
    print(out)
