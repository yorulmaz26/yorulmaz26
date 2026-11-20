"""Karakter görsellerini (NN_ad.png) assets/chars/char_<id>.png olarak 512x512 POT tuvale yerleştirir.
Ana gövdeye bağlı olmayan küçük kopuk parçalar (kesimden kalan kırıntılar) silinir.
Kullanım: python3 -I tools/art/import_characters.py <klasör> <android/assets> <ad=id>...
"""
import sys, os, glob
from collections import deque
from PIL import Image

src, assets = sys.argv[1], sys.argv[2]
mapping = dict(a.split('=') for a in sys.argv[3:])

def keep_main(im):
    a = im.getchannel('A')
    small = a.resize((a.width // 4, a.height // 4), Image.BILINEAR)
    w, h = small.size; px = small.load()
    lab = [[0] * w for _ in range(h)]; sizes = {}; cur = 0
    for y in range(h):
        for x in range(w):
            if px[x, y] > 40 and not lab[y][x]:
                cur += 1; n = 0; q = deque([(x, y)]); lab[y][x] = cur
                while q:
                    cx, cy = q.popleft(); n += 1
                    for dx, dy in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(1,-1),(-1,1)):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and not lab[ny][nx] and px[nx, ny] > 40:
                            lab[ny][nx] = cur; q.append((nx, ny))
                sizes[cur] = n
    if not sizes: return im
    big = max(sizes.values())
    keep = {k for k, v in sizes.items() if v >= big * 0.12}
    mask = Image.new('L', (w, h), 0); mp = mask.load()
    for y in range(h):
        for x in range(w):
            if lab[y][x] in keep: mp[x, y] = 255
    from PIL import ImageFilter
    mask = mask.resize(a.size, Image.NEAREST).filter(ImageFilter.MaxFilter(9))
    out = im.copy()
    out.putalpha(Image.composite(a, Image.new('L', a.size, 0), mask))
    return out

for f in sorted(glob.glob(os.path.join(src, '**', '*.png'), recursive=True)):
    name = os.path.basename(f)
    key = next((k for k in mapping if k in name), None)
    if not key: continue
    im = keep_main(Image.open(f).convert('RGBA'))
    bb = im.getchannel('A').point(lambda v: 255 if v > 10 else 0).getbbox()
    im = im.crop((max(0, bb[0]-4), max(0, bb[1]-4), min(im.width, bb[2]+4), min(im.height, bb[3]+4)))
    s = 512 / max(im.size)
    im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)  # küçükleri büyüt, büyükleri küçült
    c = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); c.alpha_composite(im, ((512 - im.width) // 2, 512 - im.height))
    out = os.path.join(assets, 'chars', mapping[key] + '.png'); c.save(out, optimize=True); print(out, im.size)
