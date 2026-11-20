"""Arayüz varlıkları: butonlar, paneller, simgeler, mekanik/güçlendirme rozetleri, logo."""
import math
from PIL import Image, ImageFont, ImageDraw, ImageFilter
from common import Canvas, hexc, shade, mix, S

INK = '#253858'
WHITE = '#FFFFFF'

BUTTON_COLORS = {
    'yellow': '#FFD54F', 'orange': '#FF9F43', 'pink': '#FF8FB1', 'turq': '#58D6D0',
    'green': '#73D65C', 'blue': '#6FB7F5', 'purple': '#B388FF', 'white': '#FFFFFF', 'gray': '#C9D3E0', 'red': '#FF6B6B',
}


def button(color, w=120, h=120, r=40):
    c = Canvas(w, h)
    lip = 10
    base = hexc(color)
    dark = shade(base, 0.78)
    c.shadow(lambda l: c.rrect((6, 10, w - 6, h - 4), r, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=4, color=(37, 56, 88, 60))
    c.rrect((6, 8, w - 6, h - 6), r, fill=dark)
    lay = c.layer()
    c.rrect((6, 6, w - 6, h - 6 - lip), r, fill=(255, 255, 255, 255), img=lay)
    c.gradient_fill(lay, shade(base, 1.18), base)
    # parlama
    c.rrect((18, 12, w - 18, 30), 9, fill=(255, 255, 255, 80))
    return c.done(), (r + 6, r + 6, r + 10, r + 16)


def panel(w=160, h=160, r=44, color='#FFFFFF', border='#E3ECF7'):
    c = Canvas(w, h)
    c.shadow(lambda l: c.rrect((10, 12, w - 10, h - 10), r, fill=(0, 0, 0, 255), img=l), offset=(0, 5), blur=7, color=(37, 56, 88, 55))
    c.rrect((10, 8, w - 10, h - 12), r, fill=color, outline=border, width=3)
    return c.done(), (r + 12, r + 12, r + 10, r + 14)


def flat_rrect(w, h, r, color, outline=None, ow=0):
    c = Canvas(w, h)
    c.rrect((1, 1, w - 1, h - 1), r, fill=color, outline=outline, width=ow)
    return c.done(), (r + 2, r + 2, r + 2, r + 2)


def soft_circle(sz=64):
    img = Image.new('RGBA', (sz, sz))
    for y in range(sz):
        for x in range(sz):
            d = math.hypot(x - sz / 2 + 0.5, y - sz / 2 + 0.5) / (sz / 2)
            a = max(0.0, 1 - d)
            img.putpixel((x, y), (255, 255, 255, int(255 * a * a)))
    return img


# ------------------------------------------------------------------ simgeler
def icon_canvas(sz=96):
    return Canvas(sz, sz)


def glyph(name, sz=96, color=WHITE):
    """Butonlarda kullanılan beyaz sade simgeler."""
    c = Canvas(sz, sz)
    k = sz / 96
    def P(*pts): return [(x * k, y * k) for x, y in pts]
    w = 11 * k
    if name == 'play':
        c.poly(P((34, 22), (34, 74), (76, 48)), fill=color)
        c.line(P((34, 22), (34, 74), (76, 48), (34, 22)), color, 8 * k)
    elif name == 'pause':
        c.rrect((26 * k, 22 * k, 42 * k, 74 * k), 5 * k, fill=color)
        c.rrect((54 * k, 22 * k, 70 * k, 74 * k), 5 * k, fill=color)
    elif name == 'home':
        c.poly(P((48, 16), (82, 46), (14, 46)), fill=color)
        c.line(P((48, 16), (82, 46), (14, 46), (48, 16)), color, 6 * k)
        c.rrect((24 * k, 42 * k, 72 * k, 80 * k), 6 * k, fill=color)
        c.rrect((41 * k, 56 * k, 55 * k, 80 * k), 4 * k, fill=(0, 0, 0, 0))
        d = ImageDraw.Draw(c.img)
        d.rounded_rectangle(c.sc((41 * k, 56 * k, 55 * k, 82 * k)), radius=4 * k * S, fill=(0, 0, 0, 0))
    elif name == 'retry':
        c.arc((20 * k, 20 * k, 76 * k, 76 * k), 40, 330, color, w)
        c.poly(P((64, 14), (82, 30), (60, 38)), fill=color)
    elif name == 'next':
        c.line(P((22, 48), (70, 48)), color, w)
        c.line(P((52, 28), (72, 48), (52, 68)), color, w)
    elif name == 'back':
        c.line(P((26, 48), (74, 48)), color, w)
        c.line(P((44, 28), (24, 48), (44, 68)), color, w)
    elif name == 'close':
        c.line(P((28, 28), (68, 68)), color, w)
        c.line(P((68, 28), (28, 68)), color, w)
    elif name == 'check':
        c.line(P((22, 50), (40, 68), (74, 30)), color, w + 2 * k)
    elif name == 'plus':
        c.line(P((48, 22), (48, 74)), color, w)
        c.line(P((22, 48), (74, 48)), color, w)
    elif name == 'settings':
        cx = cy = 48 * k
        for i in range(8):
            a = math.radians(i * 45)
            c.rrect((cx + math.cos(a) * 30 * k - 8 * k, cy + math.sin(a) * 30 * k - 8 * k, cx + math.cos(a) * 30 * k + 8 * k, cy + math.sin(a) * 30 * k + 8 * k), 3 * k, fill=color)
        c.circle(cx, cy, 28 * k, fill=color)
        c.circle(cx, cy, 11 * k, fill=hexc('#6FB7F5'))
    elif name in ('music', 'music_off'):
        c.circle(32 * k, 70 * k, 12 * k, fill=color)
        c.circle(68 * k, 62 * k, 12 * k, fill=color)
        c.rrect((38 * k, 20 * k, 45 * k, 70 * k), 3 * k, fill=color)
        c.rrect((74 * k, 14 * k, 81 * k, 62 * k), 3 * k, fill=color)
        c.poly(P((38, 20), (81, 12), (81, 26), (38, 34)), fill=color)
        if name == 'music_off': c.line(P((16, 16), (82, 82)), '#FF6B6B', 9 * k)
    elif name in ('sound', 'sound_off'):
        c.poly(P((16, 38), (32, 38), (52, 20), (52, 76), (32, 58), (16, 58)), fill=color)
        if name == 'sound':
            c.arc((40 * k, 30 * k, 72 * k, 66 * k), -50, 50, color, 7 * k)
            c.arc((44 * k, 18 * k, 88 * k, 78 * k), -50, 50, color, 7 * k)
        else:
            c.line(P((62, 34), (82, 62)), color, 8 * k); c.line(P((82, 34), (62, 62)), color, 8 * k)
    elif name == 'vibrate':
        c.rrect((32 * k, 14 * k, 64 * k, 82 * k), 8 * k, fill=color)
        c.rrect((38 * k, 22 * k, 58 * k, 66 * k), 3 * k, fill=hexc('#6FB7F5'))
        c.line(P((20, 34), (14, 48), (20, 62)), color, 5 * k)
        c.line(P((76, 34), (82, 48), (76, 62)), color, 5 * k)
    elif name == 'lock':
        c.arc((30 * k, 14 * k, 66 * k, 54 * k), 180, 360, color, 10 * k)
        c.rrect((30 * k, 30 * k, 66 * k, 34 * k), 1, fill=color)
        c.rrect((22 * k, 40 * k, 74 * k, 82 * k), 10 * k, fill=color)
        c.circle(48 * k, 58 * k, 6 * k, fill='#9AA9C0')
        c.rrect((45 * k, 58 * k, 51 * k, 70 * k), 2 * k, fill='#9AA9C0')
    elif name == 'info':
        c.circle(48 * k, 24 * k, 8 * k, fill=color)
        c.rrect((40 * k, 38 * k, 56 * k, 80 * k), 6 * k, fill=color)
    elif name == 'tap':
        # el simgesi
        c.circle(48 * k, 30 * k, 16 * k, outline=color, width=4 * k)
        c.rrect((40 * k, 24 * k, 54 * k, 64 * k), 7 * k, fill=color)
        c.rrect((30 * k, 50 * k, 72 * k, 86 * k), 14 * k, fill=color)
    elif name == 'hold':
        c.rrect((40 * k, 30 * k, 54 * k, 64 * k), 7 * k, fill=color)
        c.rrect((30 * k, 50 * k, 72 * k, 86 * k), 14 * k, fill=color)
        for r in (16, 24):
            c.arc((47 * k - r * k, 32 * k - r * k, 47 * k + r * k, 32 * k + r * k), 200, 340, color, 4 * k)
    elif name == 'run':
        c.circle(56 * k, 18 * k, 9 * k, fill=color)
        c.line(P((52, 32), (44, 56)), color, 10 * k)
        c.line(P((44, 56), (60, 68), (58, 86)), color, 8 * k)
        c.line(P((44, 56), (32, 70), (18, 70)), color, 8 * k)
        c.line(P((50, 38), (66, 50), (78, 44)), color, 7 * k)
        c.line(P((50, 38), (34, 42), (26, 32)), color, 7 * k)
    return c.done()


def badge(name, sz=104):
    """Renkli rozet simgeleri (para birimleri, mekanikler, güçlendirmeler, menü)."""
    c = Canvas(sz, sz)
    k = sz / 104
    cx = cy = sz / 2

    def disc(col, r=46):
        c.shadow(lambda l: c.circle(cx, cy + 2 * k, r * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3, color=(37, 56, 88, 60))
        lay = c.layer(); c.circle(cx, cy, r * k, fill=(255, 255, 255, 255), img=lay)
        c.gradient_fill(lay, shade(col, 1.25), shade(col, 0.92))
        c.circle(cx, cy, r * k, outline=shade(col, 0.75), width=4 * k)
        c.ellipse((cx - 26 * k, cy - 40 * k, cx + 22 * k, cy - 22 * k), fill=(255, 255, 255, 70))

    def g(name2, scale=0.62, color=WHITE, dy=0):
        gl = glyph(name2, int(sz * scale), color)
        c.img.alpha_composite(gl.resize((gl.width * S, gl.height * S), Image.LANCZOS), (int((sz - gl.width) / 2 * S), int(((sz - gl.height) / 2 + dy) * S)))

    if name == 'coin':
        disc('#FFC93C', 44)
        c.circle(cx, cy, 30 * k, outline='#FFF1B8', width=5 * k)
        c.rrect((cx - 7 * k, cy - 20 * k, cx + 7 * k, cy + 20 * k), 5 * k, fill='#E39B12')
    elif name == 'gem':
        pts = [(cx, cy + 42 * k), (cx - 40 * k, cy - 8 * k), (cx - 22 * k, cy - 34 * k), (cx + 22 * k, cy - 34 * k), (cx + 40 * k, cy - 8 * k)]
        c.shadow(lambda l: c.poly(pts, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.poly(pts, fill='#5ED3F3')
        c.poly([(cx, cy + 42 * k), (cx - 40 * k, cy - 8 * k), (cx + 40 * k, cy - 8 * k)], fill='#3BB4E0')
        c.poly([(cx - 22 * k, cy - 34 * k), (cx, cy - 8 * k), (cx - 40 * k, cy - 8 * k)], fill='#9BE8FF')
        c.poly([(cx, cy - 8 * k), (cx + 22 * k, cy - 34 * k), (cx + 40 * k, cy - 8 * k)], fill='#7FDDFA')
        c.poly([(cx - 22 * k, cy - 34 * k), (cx + 22 * k, cy - 34 * k), (cx, cy - 8 * k)], fill='#C8F4FF')
    elif name in ('star', 'star_empty', 'star_gray'):
        col = {'star': '#FFD54F', 'star_empty': '#E3E9F2', 'star_gray': '#C9D3E0'}[name]
        c.shadow(lambda l: c.star(cx, cy + 3 * k, 46 * k, 22 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 2), blur=3, color=(37, 56, 88, 50))
        lay = c.layer(); c.star(cx, cy + 3 * k, 46 * k, 22 * k, fill=WHITE, img=lay)
        c.gradient_fill(lay, shade(col, 1.15), shade(col, 0.9))
        c.star(cx, cy + 3 * k, 46 * k, 22 * k, outline=shade(col, 0.75), width=4 * k)
        if name == 'star': c.star(cx - 8 * k, cy - 6 * k, 12 * k, 6 * k, fill=(255, 255, 255, 140))
    elif name == 'heart':
        def hp(l, col):
            c.circle(cx - 18 * k, cy - 8 * k, 22 * k, fill=col, img=l); c.circle(cx + 18 * k, cy - 8 * k, 22 * k, fill=col, img=l)
            c.poly([(cx - 38 * k, cy + 2 * k), (cx + 38 * k, cy + 2 * k), (cx, cy + 40 * k)], fill=col, img=l)
        c.shadow(lambda l: hp(l, (0, 0, 0, 255)), offset=(0, 3), blur=3)
        lay = c.layer(); hp(lay, (255, 255, 255, 255)); c.gradient_fill(lay, '#FF8FA8', '#FF4F70')
        c.ellipse((cx - 30 * k, cy - 22 * k, cx - 12 * k, cy - 8 * k), fill=(255, 255, 255, 120))
    elif name == 'heart_empty':
        def hp(l, col):
            c.circle(cx - 18 * k, cy - 8 * k, 22 * k, fill=col, img=l); c.circle(cx + 18 * k, cy - 8 * k, 22 * k, fill=col, img=l)
            c.poly([(cx - 38 * k, cy + 2 * k), (cx + 38 * k, cy + 2 * k), (cx, cy + 40 * k)], fill=col, img=l)
        lay = c.layer(); hp(lay, (255, 255, 255, 255)); c.gradient_fill(lay, '#E8EDF5', '#CBD5E3')
    elif name == 'trophy':
        c.shadow(lambda l: c.rrect((cx - 30 * k, cy - 36 * k, cx + 30 * k, cy + 10 * k), 18 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.arc((cx - 46 * k, cy - 34 * k, cx - 10 * k, cy + 2 * k), 90, 270, '#F2B41C', 7 * k)
        c.arc((cx + 10 * k, cy - 34 * k, cx + 46 * k, cy + 2 * k), 270, 90, '#F2B41C', 7 * k)
        lay = c.layer(); c.rrect((cx - 30 * k, cy - 38 * k, cx + 30 * k, cy + 12 * k), 22 * k, fill=WHITE, img=lay)
        c.rrect((cx - 30 * k, cy - 38 * k, cx + 30 * k, cy - 14 * k), 4 * k, fill=WHITE, img=lay)
        c.gradient_fill(lay, '#FFE27A', '#F5B41F')
        c.rrect((cx - 7 * k, cy + 8 * k, cx + 7 * k, cy + 26 * k), 3 * k, fill='#E59E12')
        c.rrect((cx - 24 * k, cy + 24 * k, cx + 24 * k, cy + 40 * k), 6 * k, fill='#FF9F43')
        c.star(cx, cy - 14 * k, 14 * k, 6 * k, fill='#FFF6C8')
    elif name == 'gift':
        c.shadow(lambda l: c.rrect((cx - 38 * k, cy - 14 * k, cx + 38 * k, cy + 40 * k), 10 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.rrect((cx - 36 * k, cy - 8 * k, cx + 36 * k, cy + 40 * k), 10 * k, fill='#FF8FB1')
        c.rrect((cx - 42 * k, cy - 24 * k, cx + 42 * k, cy - 2 * k), 8 * k, fill='#FF6F91')
        c.rrect((cx - 8 * k, cy - 24 * k, cx + 8 * k, cy + 40 * k), 2 * k, fill='#FFD54F')
        c.ellipse((cx - 30 * k, cy - 46 * k, cx - 2 * k, cy - 22 * k), outline='#FFD54F', width=7 * k)
        c.ellipse((cx + 2 * k, cy - 46 * k, cx + 30 * k, cy - 22 * k), outline='#FFD54F', width=7 * k)
    elif name == 'calendar':
        c.shadow(lambda l: c.rrect((cx - 38 * k, cy - 30 * k, cx + 38 * k, cy + 40 * k), 12 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.rrect((cx - 38 * k, cy - 30 * k, cx + 38 * k, cy + 40 * k), 12 * k, fill=WHITE)
        c.rrect((cx - 38 * k, cy - 30 * k, cx + 38 * k, cy - 6 * k), 12 * k, fill='#FF6F91')
        c.rrect((cx - 38 * k, cy - 18 * k, cx + 38 * k, cy - 6 * k), 1, fill='#FF6F91')
        for i in range(3):
            for j in range(2):
                c.rrect((cx - 28 * k + i * 20 * k, cy + 2 * k + j * 17 * k, cx - 16 * k + i * 20 * k, cy + 13 * k + j * 17 * k), 3 * k, fill='#9EC9E8')
        c.rrect((cx - 24 * k, cy - 40 * k, cx - 16 * k, cy - 22 * k), 4 * k, fill=INK)
        c.rrect((cx + 16 * k, cy - 40 * k, cx + 24 * k, cy - 22 * k), 4 * k, fill=INK)
    elif name == 'scroll':
        c.shadow(lambda l: c.rrect((cx - 32 * k, cy - 38 * k, cx + 32 * k, cy + 38 * k), 10 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.rrect((cx - 32 * k, cy - 38 * k, cx + 32 * k, cy + 38 * k), 10 * k, fill='#FFF3D6')
        c.rrect((cx - 38 * k, cy - 44 * k, cx + 38 * k, cy - 32 * k), 6 * k, fill='#FFB86B')
        c.rrect((cx - 38 * k, cy + 32 * k, cx + 38 * k, cy + 44 * k), 6 * k, fill='#FFB86B')
        for i in range(3):
            c.circle(cx - 18 * k, cy - 16 * k + i * 18 * k, 5 * k, fill='#73D65C')
            c.rrect((cx - 8 * k, cy - 19 * k + i * 18 * k, cx + 22 * k, cy - 13 * k + i * 18 * k), 3 * k, fill='#C9B48A')
    elif name == 'puzzle':
        def pz(l, col):
            c.rrect((cx - 30 * k, cy - 26 * k, cx + 26 * k, cy + 34 * k), 8 * k, fill=col, img=l)
            c.circle(cx - 2 * k, cy - 30 * k, 12 * k, fill=col, img=l)
            c.circle(cx + 30 * k, cy + 4 * k, 12 * k, fill=col, img=l)
        c.shadow(lambda l: pz(l, (0, 0, 0, 255)), offset=(0, 3), blur=3)
        lay = c.layer(); pz(lay, (255, 255, 255, 255)); c.gradient_fill(lay, '#C6A8FF', '#9C6BFF')
        c.ellipse((cx - 24 * k, cy - 20 * k, cx - 4 * k, cy - 6 * k), fill=(255, 255, 255, 110))
    elif name == 'person':
        disc('#6FB7F5')
        c.circle(cx, cy - 10 * k, 15 * k, fill=WHITE)
        c.ellipse((cx - 26 * k, cy + 8 * k, cx + 26 * k, cy + 46 * k), fill=WHITE)
    elif name == 'bag':
        c.shadow(lambda l: c.rrect((cx - 36 * k, cy - 18 * k, cx + 36 * k, cy + 40 * k), 14 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.arc((cx - 20 * k, cy - 40 * k, cx + 20 * k, cy), 180, 360, '#E07B1A', 7 * k)
        lay = c.layer(); c.rrect((cx - 36 * k, cy - 20 * k, cx + 36 * k, cy + 40 * k), 14 * k, fill=WHITE, img=lay)
        c.gradient_fill(lay, '#FFC07A', '#FF9F43')
        c.circle(cx, cy + 10 * k, 12 * k, fill='#FFD54F')
    elif name == 'map':
        c.shadow(lambda l: c.rrect((cx - 40 * k, cy - 32 * k, cx + 40 * k, cy + 34 * k), 8 * k, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        c.poly([(cx - 40 * k, cy - 28 * k), (cx - 14 * k, cy - 36 * k), (cx + 14 * k, cy - 28 * k), (cx + 40 * k, cy - 36 * k), (cx + 40 * k, cy + 30 * k), (cx + 14 * k, cy + 38 * k), (cx - 14 * k, cy + 30 * k), (cx - 40 * k, cy + 38 * k)], fill='#A8E6A1')
        c.poly([(cx - 14 * k, cy - 36 * k), (cx + 14 * k, cy - 28 * k), (cx + 14 * k, cy + 38 * k), (cx - 14 * k, cy + 30 * k)], fill='#8FD98A')
        c.line([(cx - 30 * k, cy + 20 * k), (cx - 8 * k, cy), (cx + 10 * k, cy + 10 * k), (cx + 28 * k, cy - 16 * k)], '#FF6F91', 4 * k)
        c.circle(cx + 28 * k, cy - 18 * k, 7 * k, fill='#FF6F91')
    elif name == 'infinity':
        disc('#58D6D0')
        c.ellipse((cx - 34 * k, cy - 14 * k, cx + 2 * k, cy + 14 * k), outline=WHITE, width=8 * k)
        c.ellipse((cx - 2 * k, cy - 14 * k, cx + 34 * k, cy + 14 * k), outline=WHITE, width=8 * k)
    elif name == 'crown':
        pts = [(cx - 40 * k, cy - 18 * k), (cx - 20 * k, cy + 2 * k), (cx, cy - 30 * k), (cx + 20 * k, cy + 2 * k), (cx + 40 * k, cy - 18 * k), (cx + 32 * k, cy + 30 * k), (cx - 32 * k, cy + 30 * k)]
        c.shadow(lambda l: c.poly(pts, fill=(0, 0, 0, 255), img=l), offset=(0, 3), blur=3)
        lay = c.layer(); c.poly(pts, fill=WHITE, img=lay); c.gradient_fill(lay, '#FFE27A', '#F5B41F')
        for x in (-40, 0, 40): c.circle(cx + x * k, cy - (18 if x else 30) * k, 7 * k, fill='#FF6F91')
    elif name == 'bolt':
        disc('#FF9F43')
        c.poly([(cx + 6 * k, cy - 34 * k), (cx - 22 * k, cy + 6 * k), (cx - 2 * k, cy + 6 * k), (cx - 8 * k, cy + 34 * k), (cx + 22 * k, cy - 8 * k), (cx + 2 * k, cy - 8 * k)], fill=WHITE)
    # mekanik rozetleri
    elif name.startswith('m_'):
        cols = {'m_normal': '#73D65C', 'm_gravity': '#6FB7F5', 'm_fast': '#FF9F43', 'm_ice': '#7FD8F7', 'm_ghost': '#B388FF',
                'm_bouncy': '#FF8FB1', 'm_small': '#58D6D0', 'm_control': '#FF7EB6', 'm_slow': '#9FA8FF', 'm_shift': '#FFC93C'}
        disc(cols[name])
        W = WHITE
        if name == 'm_normal':
            c.circle(cx, cy, 16 * k, fill=W)
            for i in range(8):
                a = math.radians(i * 45)
                c.line([(cx + math.cos(a) * 24 * k, cy + math.sin(a) * 24 * k), (cx + math.cos(a) * 33 * k, cy + math.sin(a) * 33 * k)], W, 6 * k)
        elif name == 'm_gravity':
            c.line([(cx - 12 * k, cy + 26 * k), (cx - 12 * k, cy - 22 * k)], W, 8 * k)
            c.poly([(cx - 26 * k, cy - 12 * k), (cx - 12 * k, cy - 32 * k), (cx + 2 * k, cy - 12 * k)], fill=W)
            c.line([(cx + 14 * k, cy - 26 * k), (cx + 14 * k, cy + 22 * k)], W, 8 * k)
            c.poly([(cx, cy + 12 * k), (cx + 14 * k, cy + 32 * k), (cx + 28 * k, cy + 12 * k)], fill=W)
        elif name == 'm_fast':
            for dx in (-14, 8):
                c.poly([(cx + dx * k - 10 * k, cy - 24 * k), (cx + dx * k + 14 * k, cy), (cx + dx * k - 10 * k, cy + 24 * k), (cx + dx * k - 2 * k, cy)], fill=W)
        elif name == 'm_ice':
            for i in range(3):
                a = math.radians(i * 60 + 90)
                c.line([(cx - math.cos(a) * 30 * k, cy - math.sin(a) * 30 * k), (cx + math.cos(a) * 30 * k, cy + math.sin(a) * 30 * k)], W, 6 * k)
                for sgn in (1, -1):
                    bx, by = cx + sgn * math.cos(a) * 18 * k, cy + sgn * math.sin(a) * 18 * k
                    for t in (-1, 1):
                        b = a + t * math.radians(40) + (0 if sgn > 0 else math.pi)
                        c.line([(bx, by), (bx + math.cos(b) * 10 * k, by + math.sin(b) * 10 * k)], W, 4 * k)
        elif name == 'm_ghost':
            c.circle(cx, cy - 6 * k, 22 * k, fill=W)
            c.rrect((cx - 22 * k, cy - 6 * k, cx + 22 * k, cy + 22 * k), 2, fill=W)
            for i in range(3): c.circle(cx - 15 * k + i * 15 * k, cy + 22 * k, 8 * k, fill=W)
            c.ellipse((cx - 12 * k, cy - 12 * k, cx - 4 * k, cy - 2 * k), fill='#7C4DFF')
            c.ellipse((cx + 4 * k, cy - 12 * k, cx + 12 * k, cy - 2 * k), fill='#7C4DFF')
        elif name == 'm_bouncy':
            c.rrect((cx - 24 * k, cy + 18 * k, cx + 24 * k, cy + 28 * k), 4 * k, fill=W)
            c.line([(cx - 16 * k, cy + 16 * k), (cx + 16 * k, cy + 6 * k), (cx - 16 * k, cy - 4 * k), (cx + 16 * k, cy - 14 * k)], W, 6 * k)
            c.rrect((cx - 24 * k, cy - 26 * k, cx + 24 * k, cy - 16 * k), 4 * k, fill=W)
        elif name == 'm_small':
            c.rrect((cx - 12 * k, cy - 4 * k, cx + 12 * k, cy + 20 * k), 7 * k, fill=W)
            for sx, sy in ((-1, -1), (1, -1)):
                c.line([(cx + sx * 30 * k, cy + sy * 30 * k), (cx + sx * 16 * k, cy + sy * 16 * k)], W, 6 * k)
                c.poly([(cx + sx * 12 * k, cy + sy * 12 * k), (cx + sx * 24 * k, cy + sy * 12 * k), (cx + sx * 12 * k, cy + sy * 24 * k)], fill=W)
        elif name == 'm_control':
            c.line([(cx - 26 * k, cy - 10 * k), (cx + 22 * k, cy - 10 * k)], W, 7 * k)
            c.poly([(cx + 14 * k, cy - 22 * k), (cx + 32 * k, cy - 10 * k), (cx + 14 * k, cy + 2 * k)], fill=W)
            c.line([(cx + 26 * k, cy + 14 * k), (cx - 22 * k, cy + 14 * k)], W, 7 * k)
            c.poly([(cx - 14 * k, cy + 2 * k), (cx - 32 * k, cy + 14 * k), (cx - 14 * k, cy + 26 * k)], fill=W)
        elif name == 'm_slow':
            c.poly([(cx - 20 * k, cy - 28 * k), (cx + 20 * k, cy - 28 * k), (cx, cy)], fill=W)
            c.poly([(cx, cy), (cx - 20 * k, cy + 28 * k), (cx + 20 * k, cy + 28 * k)], fill=W)
            c.rrect((cx - 24 * k, cy - 32 * k, cx + 24 * k, cy - 26 * k), 3 * k, fill=W)
            c.rrect((cx - 24 * k, cy + 26 * k, cx + 24 * k, cy + 32 * k), 3 * k, fill=W)
            c.poly([(cx - 10 * k, cy + 24 * k), (cx + 10 * k, cy + 24 * k), (cx, cy + 12 * k)], fill='#9FA8FF')
        elif name == 'm_shift':
            c.rrect((cx - 26 * k, cy - 6 * k, cx + 26 * k, cy + 8 * k), 5 * k, fill=W)
            c.poly([(cx - 10 * k, cy - 14 * k), (cx, cy - 30 * k), (cx + 10 * k, cy - 14 * k)], fill=W)
            c.poly([(cx - 10 * k, cy + 16 * k), (cx, cy + 32 * k), (cx + 10 * k, cy + 16 * k)], fill=W)
    elif name.startswith('p_'):
        cols = {'p_shield': '#58D6D0', 'p_magnet': '#FF6B6B', 'p_double': '#6FB7F5', 'p_invincible': '#FFC93C', 'p_life': '#FF8FB1',
                'p_coinx2': '#FF9F43', 'p_finder': '#B388FF', 'p_guard': '#73D65C'}
        disc(cols[name])
        W = WHITE
        if name == 'p_shield':
            c.poly([(cx, cy - 30 * k), (cx + 26 * k, cy - 18 * k), (cx + 22 * k, cy + 10 * k), (cx, cy + 30 * k), (cx - 22 * k, cy + 10 * k), (cx - 26 * k, cy - 18 * k)], fill=W)
            c.poly([(cx, cy - 20 * k), (cx + 16 * k, cy - 12 * k), (cx + 13 * k, cy + 6 * k), (cx, cy + 19 * k)], fill='#A8EEEA')
        elif name == 'p_magnet':
            c.arc((cx - 24 * k, cy - 26 * k, cx + 24 * k, cy + 22 * k), 0, 180, W, 13 * k)
            c.rrect((cx - 24 * k, cy - 28 * k, cx - 11 * k, cy), 2, fill=W)
            c.rrect((cx + 11 * k, cy - 28 * k, cx + 24 * k, cy), 2, fill=W)
            c.rrect((cx - 24 * k, cy - 30 * k, cx - 11 * k, cy - 20 * k), 2, fill='#FFE0E0')
            c.rrect((cx + 11 * k, cy - 30 * k, cx + 24 * k, cy - 20 * k), 2, fill='#FFE0E0')
        elif name == 'p_double':
            for dy in (-10, 10):
                c.line([(cx - 20 * k, cy + dy * k + 10 * k), (cx, cy + dy * k - 8 * k), (cx + 20 * k, cy + dy * k + 10 * k)], W, 8 * k)
        elif name == 'p_invincible':
            c.star(cx, cy + 2 * k, 30 * k, 13 * k, fill=W)
        elif name == 'p_life':
            c.circle(cx - 11 * k, cy - 6 * k, 13 * k, fill=W); c.circle(cx + 11 * k, cy - 6 * k, 13 * k, fill=W)
            c.poly([(cx - 23 * k, cy), (cx + 23 * k, cy), (cx, cy + 26 * k)], fill=W)
            c.line([(cx + 22 * k, cy - 30 * k), (cx + 22 * k, cy - 12 * k)], '#FF4F70', 5 * k)
            c.line([(cx + 13 * k, cy - 21 * k), (cx + 31 * k, cy - 21 * k)], '#FF4F70', 5 * k)
        elif name == 'p_coinx2':
            c.circle(cx - 6 * k, cy, 20 * k, fill='#FFE27A', outline=W, width=4 * k)
            f = ImageFont.truetype(FONT, int(28 * k * S))
            d = ImageDraw.Draw(c.img)
            d.text(((cx + 14 * k) * S, (cy + 8 * k) * S), "x2", font=f, fill=hexc(W), anchor='mm', stroke_width=int(3 * k * S), stroke_fill=hexc('#E07B1A'))
        elif name == 'p_finder':
            c.circle(cx - 4 * k, cy - 4 * k, 18 * k, outline=W, width=7 * k)
            c.line([(cx + 9 * k, cy + 9 * k), (cx + 24 * k, cy + 24 * k)], W, 9 * k)
            c.star(cx - 4 * k, cy - 4 * k, 9 * k, 4 * k, fill='#FFE27A')
        elif name == 'p_guard':
            c.rrect((cx - 18 * k, cy - 28 * k, cx - 12 * k, cy + 28 * k), 3 * k, fill=W)
            c.poly([(cx - 12 * k, cy - 28 * k), (cx + 24 * k, cy - 18 * k), (cx - 12 * k, cy - 6 * k)], fill=W)
            c.circle(cx + 12 * k, cy + 14 * k, 12 * k, fill='#FFE27A')
    else:
        raise ValueError(name)
    return c.done()


FONT = None


def logo(font_path, w=900, h=420):
    """Başlık logosu: '5 Saniyelik Dünya'."""
    c = Canvas(w, h)
    d = ImageDraw.Draw(c.img)
    big = ImageFont.truetype(font_path, 250 * S)
    mid = ImageFont.truetype(font_path, 104 * S)
    # dairesel 5 rozet
    cx, cy = 170, 190
    c.shadow(lambda l: c.circle(cx, cy + 6, 138, fill=(0, 0, 0, 255), img=l), offset=(0, 8), blur=10, color=(37, 56, 88, 70))
    lay = c.layer(); c.circle(cx, cy, 138, fill=WHITE, img=lay); c.gradient_fill(lay, '#FFE27A', '#FF9F43')
    c.circle(cx, cy, 138, outline='#FFFFFF', width=12)
    # saat dilimleri
    for i in range(12):
        a = math.radians(i * 30)
        c.line([(cx + math.cos(a) * 112, cy + math.sin(a) * 112), (cx + math.cos(a) * 124, cy + math.sin(a) * 124)], (255, 255, 255, 200), 6)
    d = ImageDraw.Draw(c.img)
    d.text((cx * S, (cy + 8) * S), "5", font=big, fill=hexc('#FFFFFF'), anchor='mm', stroke_width=14 * S, stroke_fill=hexc('#F0791E'))
    # yazılar
    def txt(x, y, s, col, stroke, f):
        lay2 = c.layer(); dd = ImageDraw.Draw(lay2)
        dd.text((x * S, (y + 8) * S), s, font=f, fill=(37, 56, 88, 90), anchor='lm', stroke_width=12 * S, stroke_fill=(37, 56, 88, 90))
        lay2 = lay2.filter(ImageFilter.GaussianBlur(4 * S)); c.paste(lay2)
        dd = ImageDraw.Draw(c.img)
        dd.text((x * S, y * S), s, font=f, fill=hexc(col), anchor='lm', stroke_width=12 * S, stroke_fill=hexc(stroke))
    txt(330, 130, "Saniyelik", '#FFFFFF', '#FF6F91', mid)
    txt(330, 260, "Dünya", '#FFFFFF', '#3BB4E0', ImageFont.truetype(font_path, 150 * S))
    return c.done()


def level_node(color, sz=136):
    """Bölüm haritası için yuvarlak düğme: gölge, kalın beyaz kenar, degrade dolgu, parlama."""
    c = Canvas(sz, sz)
    cx = cy = sz / 2
    r = sz * 0.42
    c.shadow(lambda l: c.circle(cx, cy + 5, r + 6, fill=(0, 0, 0, 255), img=l), offset=(0, 4), blur=5, color=(37, 56, 88, 80))
    c.circle(cx, cy + 4, r + 6, fill=shade(color, 0.7))
    c.circle(cx, cy, r + 6, fill='#FFFFFF')
    lay = c.layer(); c.circle(cx, cy, r - 2, fill='#FFFFFF', img=lay)
    c.gradient_fill(lay, shade(color, 1.22), shade(color, 0.86))
    c.circle(cx, cy, r - 2, outline=shade(color, 0.72), width=3)
    c.ellipse((cx - r * 0.62, cy - r * 0.82, cx + r * 0.5, cy - r * 0.28), fill=(255, 255, 255, 95))
    return c.done()
