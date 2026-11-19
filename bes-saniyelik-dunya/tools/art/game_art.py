"""Oyun varlıkları: karakter parçaları, aksesuarlar, toplanabilirler, engeller, parçacıklar."""
import math
from PIL import Image, ImageDraw, ImageFilter
from common import Canvas, hexc, shade, mix, S

INK = '#253858'
W = '#FFFFFF'

# ----------------------------------------------------------- karakter (renklendirilebilir gri tonlar)
B = 160  # gövde boyutu


def body():
    c = Canvas(B, B)
    lay = c.layer()
    c.rrect((6, 6, B - 6, B - 6), 58, fill=W, img=lay)
    c.gradient_fill(lay, '#FFFFFF', '#D2D2D2')
    c.rrect((6, 6, B - 6, B - 6), 58, outline='#8C8C8C', width=7)
    return c.done()


def body_shine():
    c = Canvas(B, B)
    c.ellipse((30, 20, 86, 50), fill=(255, 255, 255, 150))
    c.ellipse((96, 26, 110, 38), fill=(255, 255, 255, 120))
    return c.done()


def belly():
    c = Canvas(B, B)
    c.ellipse((40, 72, 120, 146), fill=W)
    return c.done()


def pattern(kind):
    c = Canvas(B, B)
    mask = c.layer()
    c.rrect((12, 12, B - 12, B - 12), 52, fill=W, img=mask)
    lay = c.layer()
    if kind == 'dots':
        for (x, y, r) in [(40, 46, 9), (112, 40, 7), (124, 96, 9), (34, 104, 7), (76, 128, 8), (86, 30, 5)]:
            c.circle(x, y, r, fill=W, img=lay)
    elif kind == 'stripes':
        for i in range(4):
            y = 30 + i * 30
            c.rrect((0, y, B, y + 12), 6, fill=W, img=lay)
    elif kind == 'stars':
        for (x, y, r) in [(40, 44, 12), (118, 50, 9), (122, 112, 11), (38, 110, 8)]:
            c.star(x, y, r, r * 0.45, fill=W, img=lay)
    elif kind == 'panel':
        c.rrect((48, 92, 112, 132), 10, fill=W, img=lay)
        c.circle(62, 112, 6, fill=(150, 150, 150, 255), img=lay)
        c.circle(82, 112, 6, fill=(150, 150, 150, 255), img=lay)
        c.circle(100, 112, 6, fill=(150, 150, 150, 255), img=lay)
    elif kind == 'swirl':
        for i in range(3):
            c.arc((20 + i * 16, 20 + i * 16, 140 - i * 16, 140 - i * 16), 200 + i * 40, 340 + i * 40, W, 9, img=lay)
    out = Image.new('RGBA', lay.size, (0, 0, 0, 0))
    out.paste(lay, (0, 0), Image.composite(lay, Image.new('RGBA', lay.size, (0, 0, 0, 0)), mask.split()[3]).split()[3])
    c.img = out
    return c.done()


def rainbow_pattern():
    c = Canvas(B, B)
    mask = c.layer()
    c.rrect((10, 10, B - 10, B - 10), 54, fill=W, img=mask)
    lay = c.layer()
    cols = ['#FF8FA8', '#FFC07A', '#FFE27A', '#9BE38A', '#7FD8F7', '#B9A2FF']
    for i, col in enumerate(cols):
        c.rrect((0, 18 + i * 22, B, 40 + i * 22), 0, fill=hexc(col, 170), img=lay)
    out = Image.new('RGBA', lay.size, (0, 0, 0, 0))
    out.paste(lay, (0, 0), mask.split()[3])
    c.img = out
    return c.done()


def eye_white():
    c = Canvas(48, 56)
    c.ellipse((4, 4, 44, 52), fill=W, outline=INK, width=4)
    return c.done()


def pupil():
    c = Canvas(28, 28)
    c.circle(14, 14, 12, fill=INK)
    c.circle(9, 9, 4.5, fill=W)
    c.circle(18, 18, 2, fill=W)
    return c.done()


def eye_happy():
    c = Canvas(48, 40)
    c.arc((6, 8, 42, 44), 200, 340, INK, 6)
    return c.done()


def eye_x():
    c = Canvas(44, 44)
    c.line([(10, 10), (34, 34)], INK, 6); c.line([(34, 10), (10, 34)], INK, 6)
    return c.done()


def mouth(kind):
    c = Canvas(64, 48)
    if kind == 'smile':
        c.arc((14, 0, 50, 30), 20, 160, INK, 5)
    elif kind == 'open':
        c.ellipse((16, 6, 48, 40), fill='#6B2E3E')
        c.ellipse((22, 22, 42, 40), fill='#FF8FA8')
        c.rrect((16, 4, 48, 14), 4, fill=(0, 0, 0, 0))
        c.ellipse((16, 6, 48, 40), outline=INK, width=4)
    elif kind == 'o':
        c.ellipse((24, 10, 40, 30), fill='#6B2E3E', outline=INK, width=4)
    elif kind == 'sad':
        c.arc((16, 16, 48, 44), 200, 340, INK, 5)
    elif kind == 'grin':
        c.poly([(12, 8), (52, 8), (32, 36)], fill='#6B2E3E')
        c.ellipse((22, 18, 42, 34), fill='#FF8FA8')
        c.line([(12, 8), (52, 8)], INK, 4)
    return c.done()


def cheek():
    c = Canvas(30, 20)
    c.ellipse((2, 2, 28, 18), fill=(255, 130, 160, 120))
    return c.done()


def foot():
    c = Canvas(44, 28)
    lay = c.layer()
    c.ellipse((2, 2, 42, 26), fill=W, img=lay)
    c.gradient_fill(lay, '#FFFFFF', '#CFCFCF')
    c.ellipse((2, 2, 42, 26), outline='#8C8C8C', width=4)
    return c.done()


def accessory(name):
    """Önceden renklendirilmiş aksesuarlar; 160x120 tuval, alt kenar başın üstüne oturur (y=120 ≈ gövde üstü + 28)."""
    c = Canvas(180, 130)
    cx = 90
    base = 118
    if name == 'leaf':
        c.line([(cx, base), (cx + 4, base - 22)], '#3E9E3A', 6)
        c.poly([(cx + 4, base - 22), (cx + 40, base - 52), (cx + 44, base - 26), (cx + 12, base - 14)], fill='#73D65C', outline='#3E9E3A', width=4)
    elif name == 'sprout':
        for a in (-1, 1):
            c.ellipse((cx + a * 18 - 18, base - 34, cx + a * 18 + 18, base - 10), fill='#5CC96B', outline='#2F8A3B', width=4)
        c.rrect((cx - 4, base - 26, cx + 4, base), 3, fill='#2F8A3B')
    elif name == 'cap':
        c.ellipse((cx - 56, base - 54, cx + 56, base + 14), fill='#FF6B6B', outline='#B83E3E', width=5)
        c.rrect((cx - 4, base - 20, cx + 86, base - 4), 8, fill='#FF6B6B', outline='#B83E3E', width=5)
        c.circle(cx, base - 54, 8, fill='#FFE27A')
    elif name == 'bow':
        c.poly([(cx, base - 18), (cx - 44, base - 40), (cx - 44, base + 2)], fill='#FF8FB1', outline='#D9577E', width=4)
        c.poly([(cx, base - 18), (cx + 44, base - 40), (cx + 44, base + 2)], fill='#FF8FB1', outline='#D9577E', width=4)
        c.circle(cx, base - 18, 12, fill='#FF6F91', outline='#D9577E', width=4)
    elif name == 'wizard':
        c.poly([(cx - 52, base), (cx + 52, base), (cx + 10, base - 110)], fill='#7C4DFF', outline='#4E2BB8', width=5)
        c.rrect((cx - 62, base - 12, cx + 62, base + 6), 9, fill='#9E7BFF', outline='#4E2BB8', width=4)
        c.star(cx - 6, base - 50, 12, 5, fill='#FFE27A'); c.star(cx + 16, base - 26, 8, 3.5, fill='#FFE27A')
    elif name == 'crown':
        pts = [(cx - 46, base - 46), (cx - 24, base - 22), (cx, base - 58), (cx + 24, base - 22), (cx + 46, base - 46), (cx + 40, base), (cx - 40, base)]
        lay = c.layer(); c.poly(pts, fill=W, img=lay); c.gradient_fill(lay, '#FFE27A', '#F5B41F')
        c.poly(pts, outline='#C98A0E', width=4)
        for x, y in ((cx - 46, base - 46), (cx, base - 58), (cx + 46, base - 46)): c.circle(x, y, 7, fill='#FF6F91')
    elif name == 'cat_ears':
        for a in (-1, 1):
            c.poly([(cx + a * 22, base), (cx + a * 64, base), (cx + a * 52, base - 52)], fill='#FFA06B', outline='#B8612F', width=5)
            c.poly([(cx + a * 32, base - 4), (cx + a * 56, base - 4), (cx + a * 50, base - 34)], fill='#FFC9B0')
    elif name == 'bunny_ears':
        for a in (-1, 1):
            c.ellipse((cx + a * 30 - 18, base - 100, cx + a * 30 + 18, base + 8), fill='#F6E3FF', outline='#B79BD9', width=5)
            c.ellipse((cx + a * 30 - 8, base - 86, cx + a * 30 + 8, base - 6), fill='#FFB6C9')
    elif name == 'antenna':
        c.rrect((cx - 4, base - 50, cx + 4, base + 4), 3, fill='#5C8DB8')
        c.circle(cx, base - 54, 13, fill='#FF6B6B', outline='#B83E3E', width=4)
        c.circle(cx - 4, base - 58, 4, fill=(255, 255, 255, 200))
    elif name == 'helmet':
        c.rrect((cx - 20, base - 34, cx + 20, base - 6), 10, fill='#FF8A65', outline='#C25B3A', width=4)
        c.rrect((cx - 4, base - 62, cx + 4, base - 30), 3, fill='#9AA9C0')
        c.circle(cx, base - 64, 9, fill='#FFE27A', outline='#C98A0E', width=3)
    elif name == 'flower':
        for i in range(8):
            a = math.radians(i * 45)
            c.ellipse((cx + 26 + math.cos(a) * 18 - 12, base - 40 + math.sin(a) * 18 - 12, cx + 26 + math.cos(a) * 18 + 12, base - 40 + math.sin(a) * 18 + 12), fill=W, outline='#E3D9C2', width=3)
        c.circle(cx + 26, base - 40, 12, fill='#FFD54F', outline='#E0A80F', width=3)
    elif name == 'headband':
        c.rrect((cx - 66, base - 22, cx + 66, base - 4), 9, fill='#FF6F91', outline='#C9476A', width=4)
        c.poly([(cx + 50, base - 14), (cx + 86, base - 30), (cx + 80, base - 6)], fill='#FF6F91', outline='#C9476A', width=3)
    elif name == 'scarf':
        pass  # gövde önünde çizilir (scarf_front)
    elif name == 'mushroom':
        c.ellipse((cx - 70, base - 66, cx + 70, base + 16), fill='#FF6B6B', outline='#B83E3E', width=5)
        c.rrect((cx - 72, base - 4, cx + 72, base + 16), 6, fill=(0, 0, 0, 0))
        d = ImageDraw.Draw(c.img); d.rectangle(c.sc((0, base - 2, 180, 130)), fill=(0, 0, 0, 0))
        for (x, y, r) in [(cx - 34, base - 30, 12), (cx + 6, base - 48, 14), (cx + 40, base - 22, 10)]:
            c.circle(x, y, r, fill=W)
    elif name == 'bee_antenna':
        for a in (-1, 1):
            c.line([(cx + a * 16, base), (cx + a * 30, base - 46)], '#4E3B2A', 6)
            c.circle(cx + a * 32, base - 50, 10, fill='#4E3B2A')
    elif name == 'top_hat':
        c.rrect((cx - 64, base - 16, cx + 64, base + 2), 8, fill='#6D4C41', outline='#3E2A23', width=4)
        c.rrect((cx - 40, base - 78, cx + 40, base - 12), 8, fill='#6D4C41', outline='#3E2A23', width=4)
        c.rrect((cx - 40, base - 34, cx + 40, base - 22), 2, fill='#FFC93C')
    elif name == 'star_glasses':
        pass  # yüz önünde (glasses_front)
    elif name == 'halo':
        c.ellipse((cx - 50, base - 60, cx + 50, base - 34), outline='#FFE27A', width=10)
        c.ellipse((cx - 50, base - 60, cx + 50, base - 34), outline=(255, 255, 255, 140), width=3)
    elif name == 'headphones':
        c.arc((cx - 70, base - 60, cx + 70, base + 80), 200, 340, '#6FB7F5', 12)
    return c.done()


def scarf_front():
    c = Canvas(B, 70)
    c.rrect((10, 10, B - 10, 36), 13, fill='#FF9F43', outline='#C26A1E', width=4)
    c.rrect((96, 24, 124, 66), 8, fill='#FF9F43', outline='#C26A1E', width=4)
    for x in range(24, B - 20, 22): c.rrect((x, 14, x + 8, 32), 2, fill='#FFC07A')
    return c.done()


def glasses_front():
    c = Canvas(B, 70)
    for x in (48, 112):
        c.star(x, 36, 28, 14, fill=hexc('#FF7EB6', 220), outline='#C93E80', width=4)
    c.line([(70, 34), (90, 34)], '#C93E80', 5)
    return c.done()


def headphone_cups():
    c = Canvas(B + 40, 70)
    for x in (20, B + 20):
        c.rrect((x - 18, 6, x + 18, 64), 14, fill='#6FB7F5', outline='#3F7FC0', width=4)
    return c.done()


def helmet_glass():
    c = Canvas(B + 20, B + 20)
    c.rrect((4, 4, B + 16, B + 16), 66, fill=hexc('#CFEFFF', 70), outline=hexc('#9EC9E8', 230), width=6)
    c.ellipse((26, 18, 80, 48), fill=(255, 255, 255, 140))
    return c.done()


# ----------------------------------------------------------- toplanabilirler
def coin(frame):
    widths = [1.0, 0.78, 0.42, 0.14, 0.42, 0.78]
    f = widths[frame]
    c = Canvas(96, 96)
    w = max(6, 40 * f)
    cx, cy = 48, 48
    c.shadow(lambda l: c.ellipse((cx - w, cy - 40, cx + w, cy + 42), fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3, color=(37, 56, 88, 50))
    lay = c.layer(); c.ellipse((cx - w, cy - 40, cx + w, cy + 40), fill=W, img=lay)
    c.gradient_fill(lay, '#FFE680', '#F5AE1B')
    c.ellipse((cx - w, cy - 40, cx + w, cy + 40), outline='#D68A0C', width=4)
    if f > 0.3:
        c.ellipse((cx - w * 0.7, cy - 28, cx + w * 0.7, cy + 28), outline='#FFF1B8', width=4)
        c.rrect((cx - 6 * f, cy - 16, cx + 6 * f, cy + 16), 4 * f, fill='#E39B12')
        c.ellipse((cx - w * 0.6, cy - 34, cx - w * 0.1, cy - 18), fill=(255, 255, 255, 140))
    return c.done()


def star_item():
    c = Canvas(128, 128)
    cx, cy = 64, 66
    glow = c.layer(); c.circle(cx, cy, 56, fill=(255, 236, 140, 110), img=glow)
    c.paste(glow.filter(ImageFilter.GaussianBlur(10 * S)))
    lay = c.layer(); c.star(cx, cy, 52, 25, fill=W, img=lay); c.gradient_fill(lay, '#FFF08A', '#FFB300')
    c.star(cx, cy, 52, 25, outline='#E08A00', width=5)
    c.ellipse((cx - 16, cy - 6, cx - 8, cy + 6), fill=INK); c.ellipse((cx + 8, cy - 6, cx + 16, cy + 6), fill=INK)
    c.arc((cx - 10, cy + 2, cx + 10, cy + 16), 20, 160, INK, 3)
    c.ellipse((cx - 30, cy + 4, cx - 18, cy + 12), fill=(255, 140, 160, 130)); c.ellipse((cx + 18, cy + 4, cx + 30, cy + 12), fill=(255, 140, 160, 130))
    return c.done()


def piece_item():
    c = Canvas(128, 128)
    cx, cy = 64, 66
    glow = c.layer(); c.circle(cx, cy, 54, fill=(190, 150, 255, 120), img=glow)
    c.paste(glow.filter(ImageFilter.GaussianBlur(10 * S)))
    def pz(l, col):
        c.rrect((cx - 32, cy - 26, cx + 28, cy + 34), 10, fill=col, img=l)
        c.circle(cx - 2, cy - 30, 13, fill=col, img=l)
        c.circle(cx + 32, cy + 4, 13, fill=col, img=l)
    lay = c.layer(); pz(lay, (255, 255, 255, 255)); c.gradient_fill(lay, '#D9C4FF', '#9C6BFF')
    c.ellipse((cx - 24, cy - 18, cx - 6, cy - 6), fill=(255, 255, 255, 150))
    c.star(cx + 2, cy + 8, 12, 5, fill='#FFF3A8')
    return c.done()


def bubble():
    c = Canvas(128, 128)
    c.circle(64, 64, 58, fill=(255, 255, 255, 60), outline=(255, 255, 255, 230), width=6)
    c.arc((20, 20, 108, 108), 200, 250, (255, 255, 255, 255), 8)
    return c.done()


# ----------------------------------------------------------- engeller
def spike():
    c = Canvas(128, 72)
    pts = [(6, 70), (64, 4), (122, 70)]
    c.shadow(lambda l: c.poly(pts, fill=(0, 0, 0, 255), img=l), offset=(0, 2), blur=2, color=(37, 56, 88, 50))
    lay = c.layer(); c.poly(pts, fill=W, img=lay); c.gradient_fill(lay, '#F3F6FB', '#AEBBD0', horizontal=True)
    c.poly([(64, 4), (122, 70), (64, 70)], fill=hexc('#93A2BB', 120))
    c.line([(6, 70), (64, 4), (122, 70)], '#6E7F9C', 5)
    c.line([(40, 50), (60, 16)], (255, 255, 255, 200), 4)
    return c.done()


def saw():
    c = Canvas(256, 256)
    cx = cy = 128
    pts = []
    n = 14
    for i in range(n * 2):
        a = math.radians(i * 180 / n)
        r = 124 if i % 2 == 0 else 98
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    c.shadow(lambda l: c.poly(pts, fill=(0, 0, 0, 255), img=l), offset=(0, 4), blur=4, color=(37, 56, 88, 60))
    lay = c.layer(); c.poly(pts, fill=W, img=lay); c.gradient_fill(lay, '#F4F7FC', '#B7C3D6')
    c.poly(pts, outline='#7E8CA6', width=5)
    c.circle(cx, cy, 66, fill='#FF9F43', outline='#C9711E', width=6)
    c.circle(cx, cy, 24, fill='#FFE27A', outline='#C9711E', width=5)
    for i in range(4):
        a = math.radians(i * 90 + 45)
        c.circle(cx + math.cos(a) * 44, cy + math.sin(a) * 44, 8, fill='#FFC07A')
    c.ellipse((60, 40, 120, 70), fill=(255, 255, 255, 110))
    return c.done()


def spring(compressed):
    c = Canvas(128, 112)
    top = 46 if compressed else 8
    c.rrect((8, 92, 120, 110), 8, fill='#7E8CA6')
    n = 3
    y0, y1 = 92, top + 18
    for i in range(n * 2):
        ya = y0 + (y1 - y0) * i / (n * 2); yb = y0 + (y1 - y0) * (i + 1) / (n * 2)
        xa = 30 if i % 2 == 0 else 98; xb = 98 if i % 2 == 0 else 30
        c.line([(xa, ya), (xb, yb)], '#C9D3E0', 9)
    lay = c.layer(); c.rrect((4, top, 124, top + 24), 12, fill=W, img=lay); c.gradient_fill(lay, '#FF8FB1', '#FF4F7A')
    c.rrect((4, top, 124, top + 24), 12, outline='#C2385F', width=4)
    c.rrect((20, top + 5, 70, top + 10), 3, fill=(255, 255, 255, 150))
    return c.done()


def boost():
    c = Canvas(256, 64)
    lay = c.layer(); c.rrect((2, 8, 254, 60), 20, fill=W, img=lay); c.gradient_fill(lay, '#8EF0EA', '#2BB5AE')
    c.rrect((2, 8, 254, 60), 20, outline='#1E8C86', width=4)
    for i in range(4):
        x = 30 + i * 56
        c.poly([(x, 16), (x + 26, 34), (x, 52), (x + 12, 34)], fill='#FFF3A8')
    return c.done()


def flag(up):
    c = Canvas(96, 200)
    c.rrect((14, 14, 24, 196), 4, fill='#9AA9C0')
    c.circle(19, 12, 9, fill='#FFD54F', outline='#C98A0E', width=3)
    if up:
        pts = [(24, 20), (92, 40), (24, 64)]
        lay = c.layer(); c.poly(pts, fill=W, img=lay); c.gradient_fill(lay, '#9BE38A', '#4FBF45')
        c.poly(pts, outline='#2F8A3B', width=4)
        c.star(46, 42, 9, 4, fill=W)
    else:
        pts = [(24, 120), (70, 136), (24, 152)]
        c.poly(pts, fill='#D9E2EE', outline='#9AA9C0', width=4)
    c.rrect((4, 186, 36, 198), 5, fill='#7E8CA6')
    return c.done()


def goal_gate():
    c = Canvas(320, 420)
    cols = ['#FF8FA8', '#FFC07A', '#FFE27A', '#9BE38A', '#7FD8F7', '#B9A2FF']
    for i, col in enumerate(cols):
        r = 150 - i * 14
        c.arc((160 - r, 60 - r + 150, 160 + r, 60 + r + 150), 180, 360, col, 15)
    for x in (12, 278):
        lay = c.layer(); c.rrect((x, 190, x + 30, 416), 10, fill=W, img=lay); c.gradient_fill(lay, '#FFFFFF', '#D9E2EE', horizontal=True)
        c.rrect((x, 190, x + 30, 416), 10, outline='#9AA9C0', width=4)
    # damalı bayrak
    for i in range(6):
        for j in range(3):
            col = INK if (i + j) % 2 == 0 else W
            c.rrect((112 + i * 16, 18 + j * 16, 128 + i * 16, 34 + j * 16), 0, fill=col)
    c.rrect((108, 14, 212, 70), 6, outline=INK, width=4)
    c.rrect((104, 14, 110, 120), 3, fill='#9AA9C0')
    for (x, y) in ((40, 120), (280, 120), (160, 108)):
        c.star(x, y, 14, 6, fill='#FFE27A')
    return c.done()


def chaser():
    c = Canvas(256, 256)
    lay = c.layer()
    for (x, y, r) in [(60, 20, 70), (40, 110, 80), (70, 200, 76), (150, 70, 60), (150, 170, 66), (200, 120, 50), (200, 240, 44), (210, 10, 40)]:
        c.circle(x, y, r, fill=W, img=lay)
    for (x, y, r) in [(60, 276, 70), (210, 266, 40)]:
        c.circle(x, y - 256, r, fill=W, img=lay)
    c.gradient_fill(lay, '#C9A8FF', '#FF8FC8', horizontal=True)
    # gözler
    for (x, y) in ((150, 90), (180, 96)):
        c.ellipse((x - 10, y - 14, x + 10, y + 14), fill=W, outline='#7C4DFF', width=3)
        c.circle(x + 3, y + 2, 5, fill=INK)
    return c.done()


def ghost_tile():
    c = Canvas(128, 80)
    c.rrect((3, 3, 125, 77), 18, fill=hexc('#C9A8FF', 210), outline='#8A5CF5', width=5)
    c.rrect((14, 10, 114, 22), 6, fill=(255, 255, 255, 110))
    for x in (40, 88):
        c.ellipse((x - 6, 34, x + 6, 50), fill='#6A3FD9')
    return c.done()


def shift_tile():
    c = Canvas(128, 80)
    lay = c.layer(); c.rrect((3, 3, 125, 77), 16, fill=W, img=lay); c.gradient_fill(lay, '#FFE680', '#FFB938')
    c.rrect((3, 3, 125, 77), 16, outline='#D18A10', width=5)
    c.poly([(54, 34), (64, 18), (74, 34)], fill=W); c.poly([(54, 46), (64, 62), (74, 46)], fill=W)
    return c.done()


def move_tile():
    c = Canvas(128, 80)
    lay = c.layer(); c.rrect((3, 3, 125, 77), 14, fill=W, img=lay); c.gradient_fill(lay, '#BFE3FF', '#6FB7F5')
    c.rrect((3, 3, 125, 77), 14, outline='#3F7FC0', width=5)
    for x in (18, 110): c.circle(x, 40, 6, fill='#E8F4FF', outline='#3F7FC0', width=2)
    c.rrect((36, 34, 92, 46), 6, fill=(255, 255, 255, 150))
    return c.done()


# ----------------------------------------------------------- parçacıklar
def particle(kind):
    c = Canvas(64, 64)
    if kind == 'dust':
        for (x, y, r) in [(24, 36, 16), (40, 30, 14), (34, 42, 13)]:
            c.circle(x, y, r, fill=W)
    elif kind == 'sparkle':
        c.poly([(32, 2), (38, 26), (62, 32), (38, 38), (32, 62), (26, 38), (2, 32), (26, 26)], fill=W)
    elif kind == 'star':
        c.star(32, 33, 28, 12, fill=W)
    elif kind == 'heart':
        c.circle(22, 26, 14, fill=W); c.circle(42, 26, 14, fill=W)
        c.poly([(9, 31), (55, 31), (32, 56)], fill=W)
    elif kind == 'bubble':
        c.circle(32, 32, 26, fill=(255, 255, 255, 70), outline=W, width=4)
        c.arc((14, 14, 50, 50), 200, 250, W, 5)
    elif kind == 'note':
        c.circle(22, 46, 11, fill=W); c.rrect((29, 10, 35, 46), 2, fill=W); c.poly([(35, 10), (52, 18), (52, 26), (35, 20)], fill=W)
    elif kind == 'confetti':
        c.rrect((20, 12, 44, 52), 4, fill=W)
    elif kind == 'crystal':
        c.poly([(32, 4), (52, 32), (32, 60), (12, 32)], fill=W)
    elif kind == 'glow':
        return None
    elif kind == 'ring':
        c.circle(32, 32, 28, outline=W, width=5)
    elif kind == 'leaf':
        c.poly([(8, 40), (30, 10), (56, 22), (34, 52)], fill=W)
    elif kind == 'snow':
        c.circle(32, 32, 14, fill=W)
    elif kind == 'line':
        c.rrect((2, 28, 62, 36), 4, fill=W)
    elif kind == 'dot':
        c.circle(32, 32, 20, fill=W)
    return c.done()


def arrow():
    c = Canvas(64, 64)
    c.poly([(8, 22), (36, 22), (36, 8), (60, 32), (36, 56), (36, 42), (8, 42)], fill=W)
    return c.done()


def sign_board():
    c = Canvas(160, 120)
    c.shadow(lambda l: c.rrect((8, 8, 152, 104), 24, fill=(0, 0, 0, 255), img=l), offset=(0, 5), blur=5, color=(37, 56, 88, 60))
    c.rrect((8, 6, 152, 106), 24, fill='#FFFDF5', outline='#FFC07A', width=6)
    return c.done(), (34, 34, 34, 36)
