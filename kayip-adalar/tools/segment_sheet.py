# Koyu lacivert referans panosundaki nesneleri bulur ve numaralı bir önizleme üretir.
# Kullanım: python3 -I segment_sheet.py <pano.png> <çıktı_önizleme.png> <bileşenler.json>
import sys, json
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

src, out_png, out_json = sys.argv[1:4]
im = Image.open(src).convert('RGB')
A = np.asarray(im).astype(int)
r, g, b = A[..., 0], A[..., 1], A[..., 2]
mx = A.max(axis=2)
bg = (mx < 100) & (b >= r - 5) & (b >= g - 25)
fg = ~bg
fg = ndi.binary_opening(fg, iterations=1)
grp = ndi.binary_dilation(fg, iterations=3)
lab, n = ndi.label(grp)
objs = ndi.find_objects(lab)
comps = []
for i, sl in enumerate(objs):
    if sl is None: continue
    y0, y1, x0, x1 = sl[0].start, sl[0].stop, sl[1].start, sl[1].stop
    w, h = x1 - x0, y1 - y0
    area = int((lab[sl] == i + 1).sum())
    if area < 150 or w < 10 or h < 10: continue
    if (w > 200 and h < 14) or (h > 200 and w < 14): continue
    comps.append(dict(id=len(comps), x=int(x0), y=int(y0), w=int(w), h=int(h), area=area))
json.dump(comps, open(out_json, 'w'))
prev = im.copy()
d = ImageDraw.Draw(prev)
for c in comps:
    d.rectangle([c['x'], c['y'], c['x'] + c['w'], c['y'] + c['h']], outline=(255, 0, 255))
    d.text((c['x'] + 2, c['y'] + 1), str(c['id']), fill=(255, 255, 0))
prev.save(out_png)
print(len(comps), 'bileşen')
