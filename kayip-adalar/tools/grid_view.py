# Bir pano bölgesini 2x büyütüp 20 px'lik koordinat ızgarasıyla gösterir (kutu seçimi için).
import sys
from PIL import Image, ImageDraw
src, x0, y0, x1, y1, out = sys.argv[1], *map(int, sys.argv[2:6]), sys.argv[6]
z = 2
im = Image.open(src).convert('RGB').crop((x0, y0, x1, y1)).resize(((x1 - x0) * z, (y1 - y0) * z), Image.NEAREST)
d = ImageDraw.Draw(im)
for x in range((x0 // 20) * 20, x1, 20):
    if x < x0: continue
    X = (x - x0) * z
    d.line([(X, 0), (X, im.height)], fill=(255, 0, 255) if x % 100 == 0 else (120, 0, 120), width=1)
    if x % 100 == 0 or x % 20 == 0 and (x // 20) % 2 == 0: d.text((X + 2, 2), str(x), fill=(255, 255, 0))
for y in range((y0 // 20) * 20, y1, 20):
    if y < y0: continue
    Y = (y - y0) * z
    d.line([(0, Y), (im.width, Y)], fill=(255, 0, 255) if y % 100 == 0 else (120, 0, 120), width=1)
    d.text((2, Y + 2), str(y), fill=(0, 255, 255))
im.save(out)
