"""Magic Lumos logosunu animasyon katmanlarına ayırır: harfler, SOFTWARE, köşeli parantezler, kod işaretleri ve devre çizgileri.
Kullanım: python3 tools/intro/extract_logo.py <logo.jpg> <assets/intro>"""
import sys, os
import numpy as np
from PIL import Image, ImageFilter

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
im = np.asarray(Image.open(src).convert('RGB')).astype(np.float32)
H, W, _ = im.shape
# yerel arka plan rengi (bulanık, yazısız tahmin): koyu pikselleri medyanla doldur
lum = im.mean(axis=2)
bg = np.array([10, 28, 48], np.float32)

def key(x0, y0, x1, y1, lo, hi, name, pad=4):
    x0 -= pad; y0 -= pad; x1 += pad; y1 += pad
    reg = im[y0:y1, x0:x1]
    l = reg.mean(axis=2)
    a = np.clip((l - lo) / (hi - lo), 0, 1)
    a = a * a * (3 - 2 * a)
    col = np.where(a[..., None] > 0.02, (reg - bg * (1 - a[..., None])) / np.maximum(a[..., None], 0.02), reg)
    col = np.clip(col, 0, 255)
    rgba = np.dstack([col, a * 255]).astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(os.path.join(out, name + '.png'))
    return (name, x0, y0, x1 - x0, y1 - y0)

parts = []
main = [(306, 385), (396, 474), (479, 547), (564, 577), (593, 660), (700, 749), (758, 820), (839, 916), (931, 1007), (1016, 1071)]
for i, (a, b) in enumerate(main):
    parts.append(key(a, 332, b + 1, 420, 80, 150, 'letter_%d' % i))
soft = [(479, 487), (506, 532), (549, 584), (603, 626), (640, 668), (682, 733), (746, 780), (798, 825), (845, 869), (889, 897)]
for i, (a, b) in enumerate(soft):
    parts.append(key(a, 444, b + 1, 494, 70, 140, 'soft_%d' % i))
parts.append(key(270, 308, 292, 446, 60, 120, 'bracket_l'))
parts.append(key(1082, 308, 1102, 446, 60, 120, 'bracket_r'))
parts.append(key(545, 288, 760, 334, 60, 120, 'tags'))

# devre çizgileri katmanı: yazı bölgeleri hariç, sönük çizgiler
a = np.clip((lum - 32) / 70, 0, 1)
mask = np.ones_like(a)
mask[300:500, 250:1110] = 0
mask = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(10))).astype(np.float32) / 255
a = a * mask
# kenarları yumuşat (portre ekranda kopyalar dikişsiz birleşsin)
yy, xx = np.mgrid[0:H, 0:W]
edge = np.minimum.reduce([xx / (W * 0.12), (W - 1 - xx) / (W * 0.12), yy / (H * 0.18), (H - 1 - yy) / (H * 0.18), np.ones_like(a)])
a = a * np.clip(edge, 0, 1)
col = np.clip((im - bg * (1 - a[..., None])) / np.maximum(a[..., None], 0.05), 0, 255)
Image.fromarray(np.dstack([col, a * 255]).astype(np.uint8), 'RGBA').resize((W // 2, H // 2), Image.LANCZOS).save(os.path.join(out, 'circuits.png'))
parts.append(('circuits', 0, 0, W, H))

with open(os.path.join(out, 'layout.txt'), 'w') as f:
    f.write('# ad x y w h  (kaynak logo koordinatları %dx%d)\n' % (W, H))
    for p in parts: f.write('%s %d %d %d %d\n' % p)
print(len(parts), 'katman')
