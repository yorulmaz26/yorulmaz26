"""Arayüz simgelerini üretir (assets/ui/*.png). Şekiller 4x çizilip küçültülerek kenar yumuşatılır.
Çalıştırma: python3 tools/generate_icons.py  (Pillow gerekir). Çıktılar depoya eklenir; oyun yalnızca PNG'leri okur."""
import math, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "ui")
S = 512          # çizim boyutu
SIZE = 128       # çıktı boyutu

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)

def new():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)

def save(im, name):
    os.makedirs(OUT, exist_ok=True)
    im.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(OUT, name + ".png"))

def blob(d, box, fill, outline=None, w=14):
    d.ellipse(box, fill=fill, outline=outline or shade(fill, 0.62), width=w)

def poly(d, pts, fill, w=14, outline=None):
    d.polygon(pts, fill=fill)
    d.line(pts + [pts[0]], fill=outline or shade(fill, 0.62), width=w, joint="curve")

def highlight(d, box, a=110):
    d.ellipse(box, fill=(255, 255, 255, a))

# ---------------------------------------------------------------- Kaynaklar
def food():
    im, d = new(); c = hexc("8BC34A")
    blob(d, (90, 130, 420, 450), c); highlight(d, (150, 180, 230, 260))
    d.line((256, 140, 270, 70), fill=hexc("7A5434"), width=22)
    poly(d, [(270, 100), (380, 60), (340, 140)], hexc("4E9F45"))
    return im

def water():
    im, d = new(); c = hexc("4FC3F7")
    pts = [(256, 50)] + [(256 + 150 * math.sin(a), 310 + 150 * math.cos(a)) for a in [i / 30 * math.pi * 2 - math.pi * 0.62 for i in range(0, 23)]]
    poly(d, pts, c); highlight(d, (175, 250, 235, 330), 140)
    return im

def wood():
    im, d = new(); c = hexc("A1724E")
    d.rounded_rectangle((70, 170, 400, 350), 80, fill=c, outline=shade(c, 0.6), width=14)
    blob(d, (310, 160, 460, 360), hexc("E7C9A3"))
    for r in (55, 30): d.ellipse((385 - r, 260 - r, 385 + r, 260 + r), outline=shade(hexc("E7C9A3"), 0.7), width=8)
    d.line((120, 220, 300, 220), fill=shade(c, 0.8), width=10)
    return im

def stone():
    im, d = new(); c = hexc("A0A4AA")
    poly(d, [(70, 400), (110, 250), (220, 170), (330, 200), (400, 300), (380, 400)], c)
    poly(d, [(250, 400), (300, 300), (400, 270), (460, 340), (450, 400)], shade(c, 1.1))
    d.line((150, 260, 230, 210), fill=(255, 255, 255, 120), width=14)
    return im

def metal():
    im, d = new(); c = hexc("8E9DB3")
    poly(d, [(70, 360), (140, 220), (370, 220), (440, 360)], c)
    poly(d, [(140, 220), (190, 150), (420, 150), (370, 220)], shade(c, 1.2))
    d.line((170, 250, 330, 250), fill=(255, 255, 255, 130), width=14)
    return im

def cloth():
    im, d = new(); c = hexc("F48FB1")
    d.rounded_rectangle((80, 150, 380, 380), 50, fill=c, outline=shade(c, 0.62), width=14)
    blob(d, (330, 150, 450, 380), shade(c, 0.9))
    d.ellipse((370, 240, 410, 290), fill=shade(c, 0.6))
    for x in (150, 230, 310): d.line((x, 160, x, 370), fill=shade(c, 1.12), width=10)
    return im

def energy():
    im, d = new(); c = hexc("FFD54F")
    poly(d, [(300, 40), (120, 290), (240, 290), (200, 470), (400, 200), (275, 200)], c)
    return im

def gold():
    im, d = new(); c = hexc("F6C445")
    for i, y in enumerate((300, 230, 160)):
        d.ellipse((90, y, 420, y + 120), fill=shade(c, 0.85 + i * 0.08), outline=shade(c, 0.6), width=14)
    d.ellipse((200, 185, 310, 230), outline=shade(c, 0.6), width=10)
    highlight(d, (130, 175, 220, 205), 120)
    return im

def tools():
    im, d = new()
    d.line((150, 420, 330, 200), fill=hexc("8D6E63"), width=46)
    poly(d, [(220, 90), (420, 230), (380, 290), (180, 150)], hexc("9AA3AD"))
    return im

def waste():
    im, d = new(); c = hexc("9E9D89")
    poly(d, [(110, 190), (400, 190), (370, 440), (140, 440)], c)
    d.rounded_rectangle((80, 140, 430, 200), 20, fill=shade(c, 1.1), outline=shade(c, 0.6), width=12)
    for x in (190, 256, 320): d.line((x, 230, x - 5 * (x - 256) // 64, 410), fill=shade(c, 0.7), width=12)
    return im

def crystal():
    im, d = new(); c = hexc("B388FF")
    poly(d, [(256, 40), (390, 200), (256, 470), (120, 200)], c)
    poly(d, [(256, 40), (300, 200), (256, 470), (210, 200)], shade(c, 1.15), w=6)
    d.line((120, 200, 390, 200), fill=shade(c, 0.65), width=10)
    return im

def rare_ore():
    im, d = new(); c = hexc("8A6F64")
    poly(d, [(80, 380), (130, 200), (260, 130), (400, 190), (440, 360), (300, 430)], c)
    for (x, y, r) in ((200, 240, 40), (320, 300, 50), (250, 360, 30)):
        d.ellipse((x - r, y - r, x + r, y + r), fill=hexc("E57373"), outline=shade(hexc("E57373"), 0.6), width=8)
    return im

def ancient():
    im, d = new(); c = hexc("C5A46D")
    cx, cy = 256, 256
    pts = []
    for i in range(16):
        a = i / 16 * math.pi * 2; r = 200 if i % 2 == 0 else 150
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    poly(d, pts, c)
    d.ellipse((186, 186, 326, 326), fill=shade(c, 0.7))
    d.ellipse((226, 226, 286, 286), fill=hexc("FFF1D6"))
    return im

def tech():
    im, d = new(); c = hexc("26C6DA")
    for i in range(5):
        x = 140 + i * 58
        d.line((x, 70, x, 140), fill=hexc("7E8EA3"), width=18); d.line((x, 370, x, 440), fill=hexc("7E8EA3"), width=18)
    d.rounded_rectangle((100, 130, 410, 380), 40, fill=hexc("37474F"), outline=hexc("263238"), width=14)
    d.rounded_rectangle((180, 200, 330, 310), 20, fill=c)
    return im

def light_mat():
    im, d = new(); c = hexc("B2DFDB")
    poly(d, [(120, 440), (180, 260), (330, 90), (400, 120), (300, 300)], c)
    d.line((140, 430, 350, 120), fill=shade(c, 0.6), width=12)
    return im

def care():
    im, d = new(); c = hexc("81C784")
    poly(d, [(256, 450), (100, 280), (120, 150), (210, 120), (256, 180), (300, 120), (390, 150), (410, 280)], c)
    d.line((256, 190, 256, 400), fill=shade(c, 0.65), width=12)
    highlight(d, (150, 170, 220, 230), 120)
    return im

# ---------------------------------------------------------------- Menü (koyu kahve, tek renk)
INK = hexc("5B4430")
def ink_new():
    return new()

def nav_build():
    im, d = new()
    poly(d, [(120, 260), (256, 130), (392, 260)], INK, w=1)
    d.rectangle((150, 255, 362, 420), fill=INK)
    d.rectangle((230, 320, 282, 420), fill=(0, 0, 0, 0))
    return im

def nav_carrier():
    im, d = new()
    d.ellipse((90, 170, 400, 380), fill=INK)
    d.ellipse((360, 220, 470, 320), fill=INK)
    for x in (130, 220, 300): d.rounded_rectangle((x, 330, x + 50, 430), 20, fill=INK)
    d.ellipse((400, 245, 430, 275), fill=(255, 255, 255, 255))
    return im

def nav_fleet():
    im, d = new()
    for (ox, oy, s) in ((30, 40, 0.62), (150, 190, 0.9)):
        d.ellipse((ox, oy + 40 * s, ox + 280 * s, oy + 210 * s), fill=INK)
        d.ellipse((ox + 250 * s, oy + 85 * s, ox + 350 * s, oy + 175 * s), fill=INK)
        for lx in (30, 120, 200):
            d.rounded_rectangle((ox + lx * s, oy + 170 * s, ox + (lx + 45) * s, oy + 260 * s), int(16 * s), fill=INK)
        d.ellipse((ox + 300 * s, oy + 115 * s, ox + 325 * s, oy + 140 * s), fill=(255, 255, 255, 255))
    return im

def nav_map():
    im, d = new()
    poly(d, [(80, 140), (190, 100), (320, 140), (430, 100), (430, 380), (320, 420), (190, 380), (80, 420)], INK, w=1)
    d.line((190, 110, 190, 370), fill=(255, 255, 255, 200), width=10); d.line((320, 150, 320, 410), fill=(255, 255, 255, 200), width=10)
    return im

def nav_economy():
    im, d = new()
    for i, h in enumerate((140, 220, 300)):
        d.rounded_rectangle((110 + i * 105, 420 - h, 180 + i * 105, 420), 14, fill=INK)
    d.line((90, 440, 430, 440), fill=INK, width=18)
    return im

def nav_trade():
    im, d = new()
    d.line((256, 80, 256, 420), fill=INK, width=22); d.line((110, 160, 400, 160), fill=INK, width=20)
    for x in (110, 400):
        d.pieslice((x - 80, 190, x + 80, 330), 0, 180, fill=INK); d.line((x, 160, x - 70, 260), fill=INK, width=8); d.line((x, 160, x + 70, 260), fill=INK, width=8)
    d.rounded_rectangle((170, 410, 342, 450), 14, fill=INK)
    return im

def nav_defense():
    im, d = new()
    poly(d, [(256, 60), (420, 120), (400, 300), (256, 450), (112, 300), (92, 120)], INK, w=1)
    d.line((256, 130, 256, 380), fill=(255, 255, 255, 200), width=14)
    return im

def nav_population():
    im, d = new()
    for (x, r) in ((170, 55), (342, 55), (256, 70)):
        d.ellipse((x - r, 150 - r + (20 if r == 70 else 40), x + r, 150 + r + (20 if r == 70 else 40)), fill=INK)
    d.pieslice((100, 300, 240, 500), 180, 360, fill=INK); d.pieslice((272, 300, 412, 500), 180, 360, fill=INK)
    d.pieslice((166, 270, 346, 520), 180, 360, fill=INK)
    return im

def nav_research():
    im, d = new()
    poly(d, [(210, 80), (300, 80), (300, 210), (410, 420), (100, 420), (210, 210)], INK, w=1)
    d.rectangle((190, 70, 320, 95), fill=INK)
    d.ellipse((200, 330, 240, 370), fill=(255, 255, 255, 200)); d.ellipse((270, 300, 300, 330), fill=(255, 255, 255, 200))
    return im

def nav_diplomacy():
    im, d = new()
    d.rounded_rectangle((110, 110, 400, 420), 30, fill=INK)
    for y in (180, 240, 300): d.line((160, y, 350, y), fill=(255, 255, 255, 200), width=14)
    d.ellipse((300, 330, 380, 410), fill=hexc("E9724C"))
    return im

def nav_explore():
    im, d = new()
    d.ellipse((90, 90, 422, 422), outline=INK, width=34)
    poly(d, [(256, 130), (300, 256), (256, 382), (212, 256)], INK, w=1)
    d.ellipse((236, 236, 276, 276), fill=(255, 255, 255, 220))
    return im

def nav_goals():
    im, d = new()
    d.pieslice((140, 60, 372, 330), 0, 180, fill=INK); d.rectangle((140, 60, 372, 200), fill=INK)
    d.arc((60, 100, 190, 250), 90, 270, fill=INK, width=24); d.arc((322, 100, 452, 250), 270, 90, fill=INK, width=24)
    d.rectangle((230, 320, 282, 390), fill=INK); d.rounded_rectangle((160, 385, 352, 440), 14, fill=INK)
    return im

def nav_market():
    im, d = new()
    d.rounded_rectangle((100, 180, 412, 440), 30, fill=INK)
    d.arc((170, 80, 342, 280), 180, 360, fill=INK, width=26)
    d.ellipse((230, 280, 282, 332), fill=(255, 255, 255, 210))
    return im

def nav_settings():
    im, d = new()
    cx = cy = 256
    for i in range(8):
        a = i / 8 * math.pi * 2
        d.line((cx + 120 * math.cos(a), cy + 120 * math.sin(a), cx + 200 * math.cos(a), cy + 200 * math.sin(a)), fill=INK, width=70)
    d.ellipse((cx - 150, cy - 150, cx + 150, cy + 150), fill=INK)
    d.ellipse((cx - 60, cy - 60, cx + 60, cy + 60), fill=(0, 0, 0, 0))
    return im

RES = {"food": food, "water": water, "wood": wood, "stone": stone, "metal": metal, "cloth": cloth, "energy": energy, "gold": gold,
       "tools": tools, "waste": waste, "crystal": crystal, "rare_ore": rare_ore, "ancient_part": ancient, "tech_part": tech,
       "light_material": light_mat, "care_essence": care}
NAV = {"build": nav_build, "carrier": nav_carrier, "fleet": nav_fleet, "map": nav_map, "economy": nav_economy, "trade": nav_trade,
       "defense": nav_defense, "population": nav_population, "research": nav_research, "diplomacy": nav_diplomacy,
       "exploration": nav_explore, "goals": nav_goals, "market": nav_market, "settings": nav_settings}

if __name__ == "__main__":
    for k, f in RES.items(): save(f(), "res_" + k)
    for k, f in NAV.items(): save(f(), "nav_" + k)
    # önizleme tablosu
    names = ["res_" + k for k in RES] + ["nav_" + k for k in NAV]
    sheet = Image.new("RGBA", (SIZE * 8, SIZE * 4), (255, 249, 238, 255))
    for i, n in enumerate(names):
        sheet.alpha_composite(Image.open(os.path.join(OUT, n + ".png")), ((i % 8) * SIZE, (i // 8) * SIZE))
    sheet.save(os.path.join(os.path.dirname(__file__), "icons_preview.png"))
    print(len(names), "simge üretildi")
