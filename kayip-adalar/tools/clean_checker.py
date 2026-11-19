# Açık renkli "sahte saydamlık" dama deseni zeminini kenarlardan taşma dolgusuyla temizler.
# Kullanım: python3 -I clean_checker.py <girdi.png> <çıktı.png>
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

src, dst = sys.argv[1], sys.argv[2]
A = np.asarray(Image.open(src).convert('RGB')).astype(int)
mn, mx = A.min(axis=2), A.max(axis=2)
cand = (mn > 200) & (mx - mn < 14)          # beyaz / açık gri, doygunluğu düşük
lab, n = ndi.label(cand)
edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
bg = np.isin(lab, list(edge))
bg = ndi.binary_dilation(bg, iterations=1) & (mn > 175)
fg = ~bg
fg = ndi.binary_opening(fg, iterations=1)
lab2, n2 = ndi.label(fg)
if n2:
    sizes = ndi.sum(fg, lab2, range(1, n2 + 1))
    keep = np.isin(lab2, [i + 1 for i, s in enumerate(sizes) if s > 0.02 * sizes.max()])
else:
    keep = fg
keep = ndi.binary_fill_holes(keep)
alpha = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7))
im = Image.fromarray(A.astype(np.uint8)).convert('RGBA'); im.putalpha(alpha)
bb = im.getbbox(); im.crop(bb).save(dst, optimize=True)
print(dst, im.crop(bb).size)
