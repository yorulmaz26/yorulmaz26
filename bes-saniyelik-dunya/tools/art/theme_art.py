"""10 haritanın açık renkli temaları: zemin karoları, platformlar, süslemeler ve paralaks arka planlar."""
import math, random
from PIL import Image, ImageDraw, ImageFilter
from common import Canvas, hexc, shade, mix, S

W = '#FFFFFF'
T = 128  # karo boyutu

THEMES = {
    1: dict(name='yesil_vadi', sky=('#7EC8F7', '#E4F6FF'), top='#73D65C', top2='#9BE38A', fill='#D49A62', fill2='#C2854F', dots='#E8B888',
            far='#B5E8B0', mid='#86D97E', farkind='hills', midkind='hills', decor=['tree', 'bush', 'flower_r', 'flower_y', 'tree_round', 'rock'], ambient='leaf'),
    2: dict(name='ters_gokyuzu', sky=('#A9D9FF', '#FFE4F2'), top='#FFFFFF', top2='#EAF4FF', fill='#DDEBFF', fill2='#C9DDFA', dots='#FFFFFF',
            far='#F4E6FF', mid='#FFFFFF', farkind='islands', midkind='clouds', decor=['balloon', 'cloud_small', 'rainbow', 'island_small', 'star_deco', 'balloon2'], ambient='bubble'),
    3: dict(name='hiz_colu', sky=('#FFD58A', '#FFF5DE'), top='#FFD27A', top2='#FFE3A6', fill='#F2B860', fill2='#E4A64C', dots='#FFD9A0',
            far='#FFE7B8', mid='#F9C987', farkind='dunes', midkind='mesas', decor=['cactus', 'cactus2', 'rock', 'palm', 'sign_arrow', 'skull_rock'], ambient='dot'),
    4: dict(name='buz_kralligi', sky=('#BDE7FF', '#F3FBFF'), top='#FFFFFF', top2='#EAF7FF', fill='#A7DFF7', fill2='#8DD0EE', dots='#D6F3FF',
            far='#E2F3FF', mid='#C7E8FA', farkind='mountains', midkind='icebergs', decor=['pine_snow', 'snowman', 'crystal', 'crystal2', 'pine_snow2', 'ice_rock'], ambient='snow'),
    5: dict(name='hayalet_ormani', sky=('#DCCEFF', '#FAF3FF'), top='#9BE3B0', top2='#C2F0CF', fill='#C7AEE8', fill2='#B597DD', dots='#E3D2FF',
            far='#EAD9FF', mid='#D3BDF5', farkind='forest', midkind='forest', decor=['mushroom_glow', 'mushroom_glow2', 'lantern', 'tree_magic', 'bush_purple', 'firefly_jar'], ambient='glowdot'),
    6: dict(name='ziplayan_sehir', sky=('#8FD0FF', '#FFF0DA'), top='#FF9F43', top2='#FFC07A', fill='#FFD7B0', fill2='#F7C49A', dots='#FFE6CC',
            far='#D6E6FF', mid='#FFC9D6', farkind='skyline', midkind='buildings', decor=['lamp', 'tree_pot', 'bench', 'hydrant', 'building_small', 'flower_box'], ambient='confetti'),
    7: dict(name='minik_dunya', sky=('#B5EEDD', '#FFF8E4'), top='#73D65C', top2='#A8EE90', fill='#B98758', fill2='#A57449', dots='#D2A273',
            far='#D2F5C8', mid='#A8E6A1', farkind='giantflowers', midkind='giantgrass', decor=['giant_flower', 'giant_mushroom', 'ladybug', 'grass_tall', 'droplet', 'giant_flower2'], ambient='leaf'),
    8: dict(name='ayna_diyari', sky=('#D3E4FF', '#FDEBFF'), top='#CDEBFF', top2='#F1FAFF', fill='#E3D9FF', fill2='#D3C6FA', dots='#FFFFFF',
            far='#EEE6FF', mid='#DCEBFF', farkind='prisms', midkind='crystals', decor=['mirror', 'crystal', 'prism', 'crystal2', 'mirror2', 'rainbow'], ambient='crystal'),
    9: dict(name='zaman_sehri', sky=('#FFDFAE', '#FFF7EA'), top='#E7B96D', top2='#F5D59E', fill='#F2D8AE', fill2='#E6C694', dots='#FFF0D2',
            far='#F7E3C4', mid='#EBCB98', farkind='clocktowers', midkind='gears', decor=['clock_post', 'gear_deco', 'hourglass', 'gear_deco2', 'lamp', 'clock_tower_small'], ambient='dot'),
    10: dict(name='kaos_adalari', sky=('#FFC9E2', '#CFF4FF'), top='#8BE07A', top2='#B7F0A8', fill='#FFC2D6', fill2='#CBB8FF', dots='#FFFFFF',
             far='#FFE2F0', mid='#D9F0FF', farkind='islands', midkind='clouds', decor=['portal', 'crystal', 'giant_flower', 'gear_deco', 'balloon', 'island_small'], ambient='star'),
}


def tile_fill(th, seed=1):
    c = Canvas(T, T)
    c.rrect((0, 0, T, T), 0, fill=th['fill'])
    rnd = random.Random(seed)
    # yatayda ve dikeyde tekrar eden desen (kenarları sarar)
    for _ in range(9):
        x, y, r = rnd.uniform(0, T), rnd.uniform(0, T), rnd.uniform(5, 11)
        for dx in (-T, 0, T):
            for dy in (-T, 0, T):
                c.ellipse((x + dx - r, y + dy - r * 0.7, x + dx + r, y + dy + r * 0.7), fill=th['fill2'])
    for _ in range(6):
        x, y, r = rnd.uniform(0, T), rnd.uniform(0, T), rnd.uniform(2, 4)
        for dx in (-T, 0, T):
            for dy in (-T, 0, T):
                c.circle(x + dx, y + dy, r, fill=th['dots'])
    return c.done()


def tile_top(th, seed=2, ceiling=False):
    """Yüzey karosu: üst kısımda çim/kar/kum şeridi, altında dolgu. ceiling=True ise ters çevrilmiş."""
    base = tile_fill(th, seed)
    c = Canvas(T, T)
    c.img.paste(base.resize((T * S, T * S)), (0, 0))
    top, top2 = th['top'], th['top2']
    lay = c.layer()
    # dalgalı şerit (kenarları sarar: dalga periyodu T'yi böler)
    pts = [(0, 0)]
    for i in range(33):
        x = i * T / 32
        y = 34 + 6 * math.sin(i / 32 * 2 * math.pi * 2) + 3 * math.sin(i / 32 * 2 * math.pi * 3)
        pts.append((x, y))
    pts.append((T, 0))
    c.poly(pts, fill=W, img=lay)
    c.gradient_fill(lay, top2, top)
    # alt kenarda koyu çizgi
    edge = [(x, y + 1) for x, y in pts[1:-1]]
    c.line(edge, shade(top, 0.8), 4)
    # tepe parlaması
    c.rrect((0, 4, T, 10), 3, fill=(255, 255, 255, 110))
    rnd = random.Random(seed + 5)
    name = th['name']
    if name in ('yesil_vadi', 'minik_dunya', 'kaos_adalari', 'hayalet_ormani'):
        for _ in range(7):
            x = rnd.uniform(6, T - 6)
            c.poly([(x - 4, 14), (x, 0), (x + 4, 14)], fill=shade(top, 1.15))
    if name in ('buz_kralligi',):
        for _ in range(5):
            x = rnd.uniform(8, T - 8); c.circle(x, 22, 3, fill='#D6F3FF')
    if name == 'ziplayan_sehir':
        for i in range(4): c.rrect((i * 32 + 2, 14, i * 32 + 30, 30), 4, fill=shade(top, 1.12))
    if name == 'zaman_sehri':
        for x in (16, 80): c.circle(x, 20, 5, fill='#C99A4E')
    if name == 'ayna_diyari':
        for x in (20, 84): c.poly([(x, 30), (x + 14, 6), (x + 22, 30)], fill=(255, 255, 255, 140))
    img = c.done()
    if ceiling:
        img = img.transpose(Image.FLIP_TOP_BOTTOM)
    return img


def block_parts(th):
    """Havada duran platform: sol, orta, sağ parçalar (128x96)."""
    out = {}
    for part in ('l', 'm', 'r'):
        c = Canvas(T, 96)
        x0 = 6 if part == 'l' else 0
        x1 = T - 6 if part == 'r' else T
        rl = 26 if part == 'l' else 0
        rr = 26 if part == 'r' else 0
        lay = c.layer()
        d = ImageDraw.Draw(lay)
        # orta gövde
        box = (x0, 6, x1, 90)
        if part == 'm':
            d.rectangle(c.sc(box), fill=(255, 255, 255, 255))
        else:
            d.rounded_rectangle(c.sc(box), radius=26 * S, fill=(255, 255, 255, 255))
            if part == 'l': d.rectangle(c.sc((x0 + 40, 6, x1, 90)), fill=(255, 255, 255, 255))
            else: d.rectangle(c.sc((x0, 6, x1 - 40, 90)), fill=(255, 255, 255, 255))
        c.gradient_fill(lay, th['fill'], th['fill2'])
        # üst şerit
        top = c.layer(); d2 = ImageDraw.Draw(top)
        if part == 'm': d2.rectangle(c.sc((x0, 6, x1, 36)), fill=(255, 255, 255, 255))
        else:
            d2.rounded_rectangle(c.sc((x0, 6, x1, 36)), radius=14 * S, fill=(255, 255, 255, 255))
            if part == 'l': d2.rectangle(c.sc((x0 + 30, 6, x1, 36)), fill=(255, 255, 255, 255))
            else: d2.rectangle(c.sc((x0, 6, x1 - 30, 36)), fill=(255, 255, 255, 255))
        c.gradient_fill(top, th['top2'], th['top'])
        c.rrect((x0 + (10 if part == 'l' else 0), 10, x1 - (10 if part == 'r' else 0), 15), 2, fill=(255, 255, 255, 110))
        # alt gölge çizgisi
        c.rrect((x0 + (16 if part == 'l' else 0), 82, x1 - (16 if part == 'r' else 0), 90), 3, fill=hexc(shade(th['fill2'], 0.85), 200))
        out[part] = c.done()
    return out


def low_tile(th):
    """Dar geçit tavanı (altı yumuşak saçaklı)."""
    c = Canvas(T, T)
    lay = c.layer(); c.rrect((0, 0, T, T), 0, fill=W, img=lay); c.gradient_fill(lay, th['fill2'], th['fill'])
    for i in range(4):
        x = i * 32 + 16
        c.ellipse((x - 18, T - 20, x + 18, T + 8), fill=th['top'])
    c.rrect((0, T - 6, T, T), 0, fill=shade(th['top'], 0.85))
    return c.done()


# ----------------------------------------------------------- süslemeler (alttan hizalı)
def decor(kind, th, seed=0):
    rnd = random.Random(seed)
    if kind in ('tree', 'tree_round', 'tree_magic', 'tree_pot'):
        c = Canvas(220, 300)
        trunk = '#B07A4F' if kind != 'tree_magic' else '#A98BD6'
        if kind == 'tree_pot':
            c.rrect((80, 240, 140, 298), 10, fill='#FF8F6B', outline='#D66845', width=4)
            c.rrect((104, 170, 116, 246), 5, fill=trunk)
            c.circle(110, 140, 54, fill='#73D65C', outline='#4FAF45', width=5)
            c.circle(92, 124, 14, fill=(255, 255, 255, 80))
            return c.done()
        c.rrect((96, 170, 124, 298), 10, fill=trunk, outline=shade(trunk, 0.8), width=4)
        leaf = {'tree': '#5CC96B', 'tree_round': '#8FDB6A', 'tree_magic': '#FF9ED2'}[kind]
        blobs = [(110, 110, 80), (60, 150, 52), (160, 150, 52), (110, 60, 56)] if kind != 'tree_round' else [(110, 120, 88)]
        for (x, y, r) in blobs: c.circle(x, y, r, fill=shade(leaf, 0.85))
        for (x, y, r) in blobs: c.circle(x, y - 6, r - 6, fill=leaf)
        for (x, y, r) in blobs[:2]: c.circle(x - r * 0.35, y - r * 0.4, r * 0.25, fill=(255, 255, 255, 70))
        if kind == 'tree':
            for _ in range(4): c.circle(rnd.uniform(50, 170), rnd.uniform(60, 170), 8, fill='#FF6F91')
        if kind == 'tree_magic':
            for _ in range(5): c.star(rnd.uniform(50, 170), rnd.uniform(50, 170), 9, 4, fill='#FFF3A8')
        return c.done()
    if kind in ('bush', 'bush_purple'):
        c = Canvas(200, 120)
        col = '#6FD36A' if kind == 'bush' else '#C9A8FF'
        for (x, y, r) in [(60, 80, 46), (110, 64, 56), (150, 84, 42)]: c.circle(x, y, r, fill=shade(col, 0.85))
        for (x, y, r) in [(60, 76, 40), (110, 60, 50), (150, 80, 36)]: c.circle(x, y, r, fill=col)
        c.rrect((10, 112, 190, 120), 4, fill=(0, 0, 0, 0))
        return c.done()
    if kind in ('flower_r', 'flower_y', 'giant_flower', 'giant_flower2', 'flower_box'):
        big = kind.startswith('giant')
        c = Canvas(160 if not big else 260, 180 if not big else 420)
        cx = c.w / 2
        petal = {'flower_r': '#FF8FB1', 'flower_y': '#FFD54F', 'giant_flower': '#FF8FB1', 'giant_flower2': '#B9A2FF', 'flower_box': '#FF6F91'}[kind]
        h = c.h
        top = 70 if not big else 110
        r = 22 if not big else 46
        if kind == 'flower_box':
            c.rrect((20, 130, 140, 178), 10, fill='#B07A4F', outline='#8A5A35', width=4)
            for x in (45, 80, 115):
                c.rrect((x - 3, 90, x + 3, 132), 2, fill='#4FAF45')
                for i in range(5):
                    a = math.radians(i * 72)
                    c.circle(x + math.cos(a) * 11, 84 + math.sin(a) * 11, 9, fill=petal)
                c.circle(x, 84, 7, fill='#FFE27A')
            return c.done()
        c.rrect((cx - (4 if not big else 9), top, cx + (4 if not big else 9), h), 4, fill='#4FAF45')
        c.ellipse((cx, h - (60 if not big else 160), cx + (40 if not big else 100), h - (40 if not big else 110)), fill='#73D65C')
        for i in range(6):
            a = math.radians(i * 60)
            c.circle(cx + math.cos(a) * r * 1.1, top + math.sin(a) * r * 1.1, r * 0.75, fill=petal, outline=shade(petal, 0.85), width=3)
        c.circle(cx, top, r * 0.7, fill='#FFE27A', outline='#E0A80F', width=3)
        return c.done()
    if kind in ('rock', 'ice_rock', 'skull_rock'):
        c = Canvas(160, 100)
        col = {'rock': '#C9D3E0', 'ice_rock': '#BFE8FA', 'skull_rock': '#E8C48E'}[kind]
        c.ellipse((10, 30, 150, 110), fill=shade(col, 0.85))
        c.ellipse((16, 26, 140, 100), fill=col)
        c.ellipse((40, 36, 80, 52), fill=(255, 255, 255, 110))
        return c.done()
    if kind in ('cactus', 'cactus2'):
        c = Canvas(160, 260)
        col = '#6ACB7A'
        c.rrect((60, 40, 100, 258), 20, fill=col, outline='#3E9E5A', width=5)
        if kind == 'cactus':
            c.rrect((20, 100, 50, 180), 15, fill=col, outline='#3E9E5A', width=5); c.rrect((30, 160, 70, 182), 10, fill=col)
            c.rrect((110, 80, 140, 150), 15, fill=col, outline='#3E9E5A', width=5); c.rrect((90, 130, 125, 152), 10, fill=col)
        c.circle(80, 36, 12, fill='#FF8FB1')
        for y in range(70, 240, 34): c.rrect((76, y, 84, y + 14), 3, fill='#9BE3A8')
        return c.done()
    if kind == 'palm':
        c = Canvas(240, 320)
        for i in range(8): c.circle(120 + i * 3, 310 - i * 30, 14, fill='#C9925A', outline='#A87442', width=3)
        for a in (-150, -110, -70, -30, 10):
            r = math.radians(a)
            c.line([(140, 80), (140 + math.cos(r) * 60, 80 + math.sin(r) * 40), (140 + math.cos(r) * 100, 90 + math.sin(r) * 30 + 30)], '#4FBF45', 16)
        c.circle(136, 92, 10, fill='#8A5A35'); c.circle(150, 96, 10, fill='#8A5A35')
        return c.done()
    if kind == 'sign_arrow':
        c = Canvas(160, 200)
        c.rrect((74, 60, 86, 198), 5, fill='#B07A4F')
        c.poly([(16, 30), (120, 30), (148, 58), (120, 86), (16, 86)], fill='#FF9F43', outline='#C9711E', width=5)
        c.poly([(50, 46), (76, 58), (50, 70)], fill=W); c.poly([(86, 46), (112, 58), (86, 70)], fill=W)
        return c.done()
    if kind in ('pine_snow', 'pine_snow2'):
        c = Canvas(200, 300)
        c.rrect((90, 240, 110, 298), 6, fill='#A87442')
        for i, (y, w) in enumerate([(40, 50), (100, 75), (160, 95)]):
            c.poly([(100, y - 30), (100 - w, y + 80), (100 + w, y + 80)], fill='#5CB88A')
            c.poly([(100, y - 30), (100 - w * 0.5, y + 20), (100 + w * 0.5, y + 20)], fill=W)
        return c.done()
    if kind == 'snowman':
        c = Canvas(160, 240)
        c.circle(80, 180, 56, fill=W, outline='#BFE3F7', width=5)
        c.circle(80, 92, 40, fill=W, outline='#BFE3F7', width=5)
        c.circle(68, 84, 5, fill='#253858'); c.circle(92, 84, 5, fill='#253858')
        c.poly([(80, 94), (110, 100), (80, 104)], fill='#FF9F43')
        c.rrect((40, 118, 120, 132), 7, fill='#FF6F91')
        c.rrect((56, 26, 104, 60), 6, fill='#6FB7F5'); c.rrect((44, 54, 116, 62), 4, fill='#6FB7F5')
        return c.done()
    if kind in ('crystal', 'crystal2'):
        c = Canvas(180, 220)
        cols = ['#A7E8FF', '#C9B6FF', '#FFC2E0'] if kind == 'crystal' else ['#C9B6FF', '#A7E8FF', '#FFE2A8']
        for (x, h, w, col) in [(60, 150, 34, cols[1]), (120, 120, 30, cols[2]), (90, 200, 42, cols[0])]:
            pts = [(x, 218 - h), (x + w, 218 - h * 0.7), (x + w * 0.8, 218), (x - w * 0.8, 218), (x - w, 218 - h * 0.7)]
            c.poly(pts, fill=col, outline=shade(col, 0.8), width=4)
            c.poly([(x, 218 - h), (x + w, 218 - h * 0.7), (x, 218 - h * 0.55)], fill=(255, 255, 255, 120))
        return c.done()
    if kind in ('mushroom_glow', 'mushroom_glow2', 'giant_mushroom'):
        big = kind == 'giant_mushroom'
        c = Canvas(200 if not big else 300, 200 if not big else 380)
        cx = c.w / 2; h = c.h
        cap = {'mushroom_glow': '#FF9ED2', 'mushroom_glow2': '#8FE8F0', 'giant_mushroom': '#FF6B6B'}[kind]
        glow = c.layer(); c.circle(cx, h * 0.38, c.w * 0.45, fill=hexc(cap, 110), img=glow)
        c.paste(glow.filter(ImageFilter.GaussianBlur(14 * S)))
        c.rrect((cx - c.w * 0.12, h * 0.38, cx + c.w * 0.12, h - 2), 18, fill='#FFF3E6', outline='#E3CDB5', width=4)
        c.ellipse((cx - c.w * 0.45, h * 0.12, cx + c.w * 0.45, h * 0.56), fill=cap, outline=shade(cap, 0.8), width=5)
        d = ImageDraw.Draw(c.img); d.rectangle(c.sc((0, h * 0.42, c.w, h * 0.6)), fill=(0, 0, 0, 0))
        c.rrect((cx - c.w * 0.12, h * 0.4, cx + c.w * 0.12, h - 2), 18, fill='#FFF3E6', outline='#E3CDB5', width=4)
        c.rrect((cx - c.w * 0.46, h * 0.38, cx + c.w * 0.46, h * 0.43), 6, fill=shade(cap, 0.85))
        for (x, y, r) in [(-0.2, 0.25, 0.07), (0.15, 0.2, 0.06), (0.25, 0.33, 0.05), (-0.05, 0.32, 0.05)]:
            c.circle(cx + x * c.w, y * h, r * c.w, fill=W)
        return c.done()
    if kind == 'lantern':
        c = Canvas(120, 260)
        c.rrect((54, 60, 66, 258), 5, fill='#9AA9C0')
        glow = c.layer(); c.circle(60, 60, 50, fill=(255, 240, 160, 150), img=glow); c.paste(glow.filter(ImageFilter.GaussianBlur(12 * S)))
        c.rrect((34, 30, 86, 92), 14, fill='#FFF3A8', outline='#E0A80F', width=5)
        c.rrect((28, 20, 92, 34), 6, fill='#B79BD9')
        return c.done()
    if kind == 'firefly_jar':
        c = Canvas(120, 140)
        c.rrect((20, 30, 100, 138), 22, fill=hexc('#E8F8FF', 190), outline='#9EC9E8', width=5)
        c.rrect((30, 16, 90, 34), 6, fill='#C9A8FF')
        for _ in range(4): c.circle(rnd.uniform(40, 80), rnd.uniform(60, 120), 7, fill='#FFF59D')
        return c.done()
    if kind == 'lamp':
        c = Canvas(120, 320)
        c.rrect((54, 70, 66, 318), 5, fill='#6D7FA0')
        c.rrect((40, 300, 80, 318), 6, fill='#6D7FA0')
        glow = c.layer(); c.circle(60, 56, 44, fill=(255, 240, 160, 140), img=glow); c.paste(glow.filter(ImageFilter.GaussianBlur(10 * S)))
        c.circle(60, 56, 26, fill='#FFF3A8', outline='#E0A80F', width=5)
        c.rrect((36, 24, 84, 36), 6, fill='#6D7FA0')
        return c.done()
    if kind == 'bench':
        c = Canvas(220, 110)
        c.rrect((10, 20, 210, 40), 8, fill='#FF9F43', outline='#C9711E', width=4)
        c.rrect((10, 52, 210, 70), 8, fill='#FF9F43', outline='#C9711E', width=4)
        for x in (30, 180): c.rrect((x, 66, x + 12, 108), 4, fill='#6D7FA0')
        return c.done()
    if kind == 'hydrant':
        c = Canvas(100, 140)
        c.rrect((26, 30, 74, 138), 16, fill='#FF6B6B', outline='#C24545', width=4)
        c.rrect((12, 64, 88, 82), 8, fill='#FF6B6B', outline='#C24545', width=4)
        c.ellipse((30, 12, 70, 40), fill='#FF6B6B', outline='#C24545', width=4)
        return c.done()
    if kind in ('building_small', 'clock_tower_small'):
        c = Canvas(200, 320)
        col = '#FFB6C9' if kind == 'building_small' else '#E7B96D'
        c.rrect((20, 60, 180, 318), 14, fill=col, outline=shade(col, 0.8), width=5)
        if kind == 'building_small':
            for i in range(3):
                for j in range(4):
                    c.rrect((42 + i * 44, 84 + j * 54, 70 + i * 44, 116 + j * 54), 6, fill='#E8F6FF', outline='#9EC9E8', width=3)
            c.poly([(10, 66), (100, 6), (190, 66)], fill='#FF8F6B', outline='#C9603E', width=5)
        else:
            c.circle(100, 130, 50, fill=W, outline='#A87442', width=6)
            c.line([(100, 130), (100, 96)], '#253858', 6); c.line([(100, 130), (124, 140)], '#253858', 6)
            c.poly([(10, 66), (100, 0), (190, 66)], fill='#B07A4F', outline='#8A5A35', width=5)
            c.rrect((70, 230, 130, 318), 30, fill='#B07A4F')
        return c.done()
    if kind == 'balloon' or kind == 'balloon2':
        c = Canvas(140, 260)
        col = '#FF8FB1' if kind == 'balloon' else '#7FD8F7'
        c.line([(70, 140), (64, 200), (74, 258)], '#9AA9C0', 3)
        c.ellipse((20, 20, 120, 140), fill=col, outline=shade(col, 0.82), width=5)
        c.ellipse((40, 36, 64, 70), fill=(255, 255, 255, 140))
        c.poly([(62, 138), (78, 138), (70, 150)], fill=shade(col, 0.82))
        return c.done()
    if kind == 'cloud_small':
        c = Canvas(220, 120)
        for (x, y, r) in [(60, 70, 40), (110, 50, 50), (160, 72, 38)]: c.circle(x, y, r, fill='#EAF4FF')
        for (x, y, r) in [(60, 66, 36), (110, 46, 46), (160, 68, 34)]: c.circle(x, y, r, fill=W)
        return c.done()
    if kind == 'rainbow':
        c = Canvas(300, 170)
        cols = ['#FF8FA8', '#FFC07A', '#FFE27A', '#9BE38A', '#7FD8F7', '#B9A2FF']
        for i, col in enumerate(cols):
            r = 140 - i * 14
            c.arc((150 - r, 160 - r, 150 + r, 160 + r), 180, 360, col, 14)
        for x in (20, 280):
            for (dx, dy, r) in [(-14, 0, 16), (0, -8, 20), (16, 0, 16)]: c.circle(x + dx, 160 + dy, r, fill=W)
        return c.done()
    if kind == 'island_small':
        c = Canvas(220, 200)
        c.poly([(20, 90), (200, 90), (130, 196), (90, 196)], fill=th['fill'], outline=shade(th['fill'], 0.8), width=5)
        c.rrect((14, 70, 206, 100), 14, fill=th['top'], outline=shade(th['top'], 0.8), width=4)
        c.circle(150, 50, 26, fill='#73D65C'); c.rrect((146, 60, 154, 74), 3, fill='#B07A4F')
        return c.done()
    if kind == 'star_deco':
        c = Canvas(120, 200)
        c.line([(60, 60), (60, 198)], '#C9D3E0', 4)
        c.star(60, 50, 40, 18, fill='#FFE27A', outline='#E0A80F', width=4)
        return c.done()
    if kind == 'ladybug':
        c = Canvas(140, 100)
        c.ellipse((16, 20, 124, 100), fill='#FF6B6B', outline='#C24545', width=5)
        c.ellipse((10, 34, 46, 70), fill='#253858')
        c.line([(70, 22), (70, 98)], '#C24545', 4)
        for (x, y) in [(52, 50), (92, 46), (60, 78), (98, 76)]: c.circle(x, y, 8, fill='#253858')
        return c.done()
    if kind == 'grass_tall':
        c = Canvas(160, 280)
        for i, (x, h, lean) in enumerate([(40, 260, -30), (80, 220, 10), (120, 250, 30), (60, 180, 20)]):
            c.poly([(x - 10, 280), (x + lean, 280 - h), (x + 10, 280)], fill=['#5CC96B', '#73D65C', '#8FDB6A', '#4FBF45'][i])
        return c.done()
    if kind == 'droplet':
        c = Canvas(100, 140)
        c.poly([(50, 6), (86, 80), (14, 80)], fill='#9FE2FF')
        c.circle(50, 92, 38, fill='#9FE2FF')
        c.ellipse((32, 70, 48, 96), fill=(255, 255, 255, 160))
        return c.done()
    if kind in ('mirror', 'mirror2'):
        c = Canvas(160, 260)
        frame = '#FFC2E0' if kind == 'mirror' else '#C9B6FF'
        c.ellipse((20, 10, 140, 210), fill=frame, outline=shade(frame, 0.8), width=6)
        lay = c.layer(); c.ellipse((34, 24, 126, 196), fill=W, img=lay); c.gradient_fill(lay, '#F1FAFF', '#BFE3FF')
        c.line([(56, 60), (90, 40)], (255, 255, 255, 220), 8); c.line([(52, 92), (104, 62)], (255, 255, 255, 180), 6)
        c.rrect((70, 206, 90, 258), 6, fill=shade(frame, 0.85))
        return c.done()
    if kind == 'prism':
        c = Canvas(200, 200)
        c.poly([(100, 10), (190, 190), (10, 190)], fill=hexc('#E9F6FF', 230), outline='#9EC9E8', width=6)
        for i, col in enumerate(['#FF8FA8', '#FFE27A', '#9BE38A', '#7FD8F7']):
            c.line([(140, 110 + i * 10), (200, 120 + i * 18)], col, 6)
        return c.done()
    if kind in ('gear_deco', 'gear_deco2'):
        c = Canvas(200, 200)
        col = '#E7B96D' if kind == 'gear_deco' else '#C9D3E0'
        n = 10
        pts = []
        for i in range(n * 2):
            a = math.radians(i * 180 / n)
            r = 92 if i % 2 == 0 else 74
            pts.append((100 + math.cos(a) * r, 100 + math.sin(a) * r))
        c.poly(pts, fill=col, outline=shade(col, 0.78), width=5)
        c.circle(100, 100, 30, fill=shade(col, 1.25), outline=shade(col, 0.78), width=5)
        return c.done()
    if kind == 'hourglass':
        c = Canvas(120, 200)
        c.rrect((14, 6, 106, 22), 6, fill='#B07A4F'); c.rrect((14, 178, 106, 194), 6, fill='#B07A4F')
        c.poly([(24, 22), (96, 22), (60, 100), (96, 178), (24, 178), (60, 100)], fill=hexc('#E9F6FF', 220), outline='#9EC9E8', width=4)
        c.poly([(36, 160), (84, 160), (90, 176), (30, 176)], fill='#FFD27A')
        c.poly([(42, 40), (78, 40), (60, 80)], fill='#FFD27A')
        return c.done()
    if kind == 'clock_post':
        c = Canvas(140, 320)
        c.rrect((62, 110, 78, 318), 6, fill='#6D4C41')
        c.circle(70, 70, 58, fill=W, outline='#E7B96D', width=10)
        for i in range(12):
            a = math.radians(i * 30)
            c.circle(70 + math.cos(a) * 42, 70 + math.sin(a) * 42, 3, fill='#253858')
        c.line([(70, 70), (70, 36)], '#253858', 5); c.line([(70, 70), (94, 78)], '#253858', 5)
        return c.done()
    if kind == 'portal':
        c = Canvas(200, 260)
        for i, col in enumerate(['#FF7EB6', '#B388FF', '#58D6D0', '#FFE27A']):
            c.ellipse((20 + i * 16, 20 + i * 20, 180 - i * 16, 250 - i * 20), outline=col, width=12)
        return c.done()
    raise ValueError(kind)


# ----------------------------------------------------------- paralaks arka planlar (yatay sarmal)
BW, BH = 2048, 640


def background(th, layer, seed=0):
    c = Canvas(BW, BH)
    kind = th['farkind'] if layer == 'far' else th['midkind']
    col = th['far'] if layer == 'far' else th['mid']
    rnd = random.Random(seed + (1 if layer == 'far' else 2) * 1000)
    lay = c.layer()

    def wrap(fn):
        for dx in (-BW, 0, BW):
            fn(dx)

    base_y = BH * (0.55 if layer == 'far' else 0.68)
    if kind in ('hills', 'giantgrass', 'forest'):
        k1, k2 = rnd.randint(2, 4), rnd.randint(5, 8)
        pts = [(0, BH)]
        for i in range(257):
            x = i * BW / 256
            t = x / BW * 2 * math.pi
            y = base_y - 60 * math.sin(t * k1 + seed) - 30 * math.sin(t * k2 + 1.3)
            pts.append((x, y))
        pts.append((BW, BH))
        c.poly(pts, fill=W, img=lay)
        c.gradient_fill(lay, shade(col, 1.08), shade(col, 0.95))
        if kind == 'forest' or kind == 'giantgrass':
            trees = c.layer()
            for i in range(26):
                x = rnd.uniform(0, BW); h = rnd.uniform(120, 240); r = rnd.uniform(40, 70)
                def t_(dx, x=x, h=h, r=r):
                    if kind == 'forest':
                        c.rrect((x + dx - 8, base_y - h + r, x + dx + 8, BH), 6, fill=shade(col, 0.9), img=trees)
                        c.circle(x + dx, base_y - h + r, r, fill=shade(col, 0.92), img=trees)
                    else:
                        c.poly([(x + dx - 16, BH), (x + dx + rnd.uniform(-30, 30), base_y - h), (x + dx + 16, BH)], fill=shade(col, 0.9), img=trees)
                wrap(t_)
            c.paste(trees)
    elif kind in ('mountains', 'icebergs', 'prisms', 'crystals'):
        n = 9
        for i in range(n):
            x = i * BW / n + rnd.uniform(-40, 40)
            h = rnd.uniform(200, 360) * (0.8 if layer == 'mid' else 1.0)
            w = rnd.uniform(180, 300)
            def m_(dx, x=x, h=h, w=w):
                c.poly([(x + dx - w, BH), (x + dx, base_y - h + 120), (x + dx + w, BH)], fill=col, img=lay)
                if kind == 'mountains':
                    c.poly([(x + dx - w * 0.28, base_y - h + 120 + h * 0.28), (x + dx, base_y - h + 120), (x + dx + w * 0.28, base_y - h + 120 + h * 0.28),
                            (x + dx + w * 0.1, base_y - h + 120 + h * 0.22), (x + dx - w * 0.08, base_y - h + 120 + h * 0.3)], fill=W, img=lay)
                elif kind in ('prisms', 'crystals'):
                    c.poly([(x + dx, base_y - h + 120), (x + dx + w, BH), (x + dx + w * 0.3, BH)], fill=shade(col, 1.08), img=lay)
            wrap(m_)
        c.paste(lay)
    elif kind in ('dunes', 'mesas'):
        pts = [(0, BH)]
        for i in range(257):
            x = i * BW / 256; t = x / BW * 2 * math.pi
            y = base_y - 40 * math.sin(t * 3 + seed) - 20 * math.sin(t * 7)
            pts.append((x, y))
        pts.append((BW, BH))
        c.poly(pts, fill=col, img=lay)
        if kind == 'mesas':
            for i in range(6):
                x = rnd.uniform(0, BW); w = rnd.uniform(120, 220); h = rnd.uniform(120, 200)
                wrap(lambda dx, x=x, w=w, h=h: c.rrect((x + dx - w / 2, base_y - h, x + dx + w / 2, BH), 18, fill=shade(col, 0.95), img=lay))
        c.paste(lay)
    elif kind in ('skyline', 'buildings', 'clocktowers'):
        x = 0
        while x < BW:
            w = rnd.uniform(90, 170); h = rnd.uniform(160, 340) * (0.75 if layer == 'mid' else 1.0)
            colr = col if layer == 'far' else rnd.choice(['#FFC9D6', '#FFE2A8', '#C9E8FF', '#D9F5C8', '#E8D9FF'])
            def b_(dx, x=x, w=w, h=h, colr=colr):
                c.rrect((x + dx, base_y - h + 60, x + dx + w - 10, BH), 12, fill=colr, img=lay)
                if kind == 'clocktowers' and rnd.random() < 0.5:
                    c.poly([(x + dx - 6, base_y - h + 66), (x + dx + w / 2 - 5, base_y - h - 10), (x + dx + w + 2 - 10, base_y - h + 66)], fill=shade(colr, 0.95), img=lay)
                    c.circle(x + dx + w / 2 - 5, base_y - h + 120, w * 0.25, fill=shade(colr, 1.15), img=lay)
                if layer == 'mid':
                    for j in range(int(h // 60)):
                        c.rrect((x + dx + 18, base_y - h + 90 + j * 56, x + dx + w - 28, base_y - h + 110 + j * 56), 5, fill=(255, 255, 255, 150), img=lay)
            wrap(b_)
            x += w
        c.paste(lay)
    elif kind in ('clouds', 'islands'):
        for i in range(10 if kind == 'clouds' else 6):
            x = rnd.uniform(0, BW); y = rnd.uniform(BH * 0.3, BH * 0.75); s = rnd.uniform(0.7, 1.4)
            def cl(dx, x=x, y=y, s=s):
                if kind == 'clouds':
                    for (ox, oy, r) in [(-70, 10, 50), (0, -20, 70), (80, 10, 52), (30, 20, 50)]:
                        c.circle(x + dx + ox * s, y + oy * s, r * s, fill=hexc(col, 230), img=lay)
                else:
                    c.poly([(x + dx - 120 * s, y), (x + dx + 120 * s, y), (x + dx + 40 * s, y + 110 * s), (x + dx - 30 * s, y + 120 * s)], fill=shade(col, 0.95), img=lay)
                    c.rrect((x + dx - 130 * s, y - 22 * s, x + dx + 130 * s, y + 10 * s), 16 * s, fill=shade('#B7F0A8', 1.0), img=lay)
                    c.circle(x + dx + 50 * s, y - 50 * s, 34 * s, fill='#A8E6A1', img=lay)
            wrap(cl)
        c.paste(lay)
    elif kind == 'giantflowers':
        for i in range(12):
            x = rnd.uniform(0, BW); h = rnd.uniform(220, 420); r = rnd.uniform(40, 70)
            colr = rnd.choice(['#FFD6E4', '#FFF0B8', '#E6DCFF'])
            def f_(dx, x=x, h=h, r=r, colr=colr):
                c.rrect((x + dx - 8, BH - h, x + dx + 8, BH), 6, fill=shade(col, 0.92), img=lay)
                for k in range(6):
                    a = math.radians(k * 60)
                    c.circle(x + dx + math.cos(a) * r, BH - h + math.sin(a) * r, r * 0.7, fill=colr, img=lay)
            wrap(f_)
        pts = [(0, BH)] + [(i * BW / 128, BH - 70 - 20 * math.sin(i / 128 * 2 * math.pi * 5)) for i in range(129)] + [(BW, BH)]
        c.poly(pts, fill=col, img=lay)
        c.paste(lay)
    elif kind == 'gears':
        for i in range(9):
            x = rnd.uniform(0, BW); y = rnd.uniform(BH * 0.45, BH * 0.9); r = rnd.uniform(60, 130)
            def g_(dx, x=x, y=y, r=r):
                pts = []
                for k in range(24):
                    a = math.radians(k * 15)
                    rr = r if k % 2 == 0 else r * 0.82
                    pts.append((x + dx + math.cos(a) * rr, y + math.sin(a) * rr))
                c.poly(pts, fill=col, img=lay)
                c.circle(x + dx, y, r * 0.35, fill=shade(col, 1.12), img=lay)
            wrap(g_)
        c.paste(lay)
    else:
        raise ValueError(kind)
    img = c.done()
    # üst kısım tamamen saydam: yalnızca silüet
    return img


def sky_strip(th):
    img = Image.new('RGBA', (4, 256))
    for y in range(256):
        c = mix(th['sky'][0], th['sky'][1], y / 255)
        for x in range(4): img.putpixel((x, y), c)
    return img
