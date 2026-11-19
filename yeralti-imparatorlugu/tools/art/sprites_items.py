"""Kaynak ikonları, yüzey kaynakları, oda eşyaları ve süsler."""
import math, random
from artlib import *

S = 64


def icon_resource(name):
    d = ""
    if name == "FOOD":  # böcek eti / yiyecek parçası
        d = lin("ID_g", [(0, "#FF9A7A", 1), (1, "#D9503A", 1)])
        b = ('<path d="M12,38 C10,22 26,12 40,16 C54,20 56,38 46,48 C36,56 14,54 12,38 Z" fill="url(#ID_g)" stroke="#8E2E1E" stroke-width="2.4"/>'
             '<path d="M20,30 C24,22 32,20 38,22" stroke="#fff" stroke-width="3.5" fill="none" opacity="0.6" stroke-linecap="round"/>'
             '<circle cx="36" cy="38" r="3" fill="#FFD7C8" opacity="0.8"/><circle cx="26" cy="42" r="2" fill="#FFD7C8" opacity="0.8"/>')
    elif name == "SEED":
        d = lin("ID_g", [(0, "#FFE29A", 1), (1, "#D4A24A", 1)], 0, 0, 1, 1)
        b = ('<path d="M32,6 C46,16 48,40 32,58 C16,40 18,16 32,6 Z" fill="url(#ID_g)" stroke="#8E6420" stroke-width="2.4"/>'
             '<path d="M32,12 L32,52" stroke="#A8782E" stroke-width="2" opacity="0.7"/><path d="M26,18 C24,26 24,34 26,42" stroke="#fff" stroke-width="3" fill="none" opacity="0.6" stroke-linecap="round"/>')
    elif name == "MUSHROOM":
        b = mushroom(32, 34, 2.8, "#F08A3C")
    elif name == "LEAF":
        d = lin("ID_g", [(0, "#8EE07A", 1), (1, "#3E9E44", 1)], 0, 0, 1, 1)
        b = ('<path d="M10,54 C8,26 26,8 56,8 C56,38 40,56 10,54 Z" fill="url(#ID_g)" stroke="#2A6E30" stroke-width="2.4"/>'
             '<path d="M12,52 C26,38 38,26 52,12" stroke="#2A6E30" stroke-width="2.2" fill="none"/>'
             '<path d="M24,40 L22,28 M32,32 L34,20 M40,24 L46,24 M30,36 L40,38" stroke="#2A6E30" stroke-width="1.6" opacity="0.8"/>'
             '<path d="M18,40 C18,28 26,18 38,14" stroke="#fff" stroke-width="3" fill="none" opacity="0.4" stroke-linecap="round"/>')
    elif name == "WOOD":
        d = lin("ID_g", [(0, "#D49A62", 1), (1, "#8A5A30", 1)])
        b = ('<g transform="rotate(-28 32 32)"><rect x="6" y="22" width="52" height="20" rx="9" fill="url(#ID_g)" stroke="#5A3418" stroke-width="2.4"/>'
             '<ellipse cx="50" cy="32" rx="7" ry="9" fill="#E8C08A" stroke="#5A3418" stroke-width="2"/><ellipse cx="50" cy="32" rx="3.5" ry="4.5" fill="none" stroke="#A8743E" stroke-width="1.4"/>'
             '<path d="M14,28 L38,28 M12,36 L34,36" stroke="#5A3418" stroke-width="1.4" opacity="0.5"/><path d="M14,25 L40,25" stroke="#fff" stroke-width="2.4" opacity="0.4"/></g>')
    elif name == "SOIL":
        d = lin("ID_g", [(0, "#D19A64", 1), (1, "#8A5A36", 1)])
        b = ('<path d="M6,52 C10,34 22,26 32,26 C44,26 54,36 58,52 Z" fill="url(#ID_g)" stroke="#5A3820" stroke-width="2.4"/>'
             '<path d="M18,40 C22,32 30,28 36,30" stroke="#fff" stroke-width="3" fill="none" opacity="0.35" stroke-linecap="round"/>'
             + pebbles(random.Random(3), 4, 64, 64, "#7A4E2E", margin=22, rmin=1.8, rmax=3))
    elif name == "WATER":
        d = lin("ID_g", [(0, "#9EE0FF", 1), (1, "#2E8ECE", 1)])
        b = ('<path d="M32,6 C40,20 52,30 52,42 C52,54 42,60 32,60 C22,60 12,54 12,42 C12,30 24,20 32,6 Z" fill="url(#ID_g)" stroke="#1E5E92" stroke-width="2.4"/>'
             '<path d="M22,40 C22,32 26,26 30,22" stroke="#fff" stroke-width="4" fill="none" opacity="0.7" stroke-linecap="round"/>')
    elif name in ("MINERAL", "RARE_MINERAL"):
        c1, c2 = ("#2EC4C0", "#A8F2EE") if name == "MINERAL" else ("#A77BE6", "#EADCFF")
        b = crystal(32, 34, 2.6, c1, c2) + (crystal(16, 46, 1.3, c1, c2, -20) if name == "RARE_MINERAL" else "")
    elif name == "FOSSIL":
        b = bone(32, 32, 2.0, -35)
    elif name == "RESIN":
        d = rad("ID_g", [(0, "#FFE08A", 1), (0.7, "#F0A830", 1), (1, "#B86E10", 1)], cx=0.4, cy=0.4)
        b = ('<path d="M32,8 C42,22 52,32 52,42 C52,54 42,60 32,60 C22,60 12,54 12,42 C12,32 22,22 32,8 Z" fill="url(#ID_g)" stroke="#8A4E08" stroke-width="2.4"/>'
             '<circle cx="26" cy="38" r="5" fill="#fff" opacity="0.6"/><circle cx="38" cy="48" r="2.4" fill="#fff" opacity="0.5"/>')
    elif name == "ENZYME":
        d = lin("ID_g", [(0, "#C6F27A", 1), (1, "#5EAE2E", 1)])
        b = ('<path d="M24,6 H40 V22 L54,50 C56,56 52,60 46,60 H18 C12,60 8,56 10,50 L24,22 Z" fill="#E9F6FF" stroke="#4A6E80" stroke-width="2.4"/>'
             '<path d="M16,40 H48 L54,50 C56,56 52,60 46,60 H18 C12,60 8,56 10,50 Z" fill="url(#ID_g)"/>'
             '<circle cx="26" cy="48" r="3" fill="#fff" opacity="0.7"/><circle cx="36" cy="52" r="2" fill="#fff" opacity="0.7"/><rect x="22" y="4" width="20" height="5" rx="2" fill="#4A6E80"/>'
             '<path d="M28,24 L18,46" stroke="#fff" stroke-width="3" opacity="0.6" stroke-linecap="round"/>')
    elif name == "GENETIC":
        b = ""
        for i in range(11):
            t = i / 10 * math.pi * 2
            y = 8 + i * 4.8
            x1, x2 = 32 + math.sin(t) * 14, 32 - math.sin(t) * 14
            b += f'<line x1="{x1:.1f}" y1="{y:.1f}" x2="{x2:.1f}" y2="{y:.1f}" stroke="#C9B6E8" stroke-width="2.4"/>'
        p1 = " ".join(f"{32 + math.sin(i / 10 * math.pi * 2) * 14:.1f},{8 + i * 4.8:.1f}" for i in range(11))
        p2 = " ".join(f"{32 - math.sin(i / 10 * math.pi * 2) * 14:.1f},{8 + i * 4.8:.1f}" for i in range(11))
        b += f'<polyline points="{p1}" stroke="#E8508E" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/><polyline points="{p2}" stroke="#3E9EE0" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
    elif name == "ROYAL_ESSENCE":
        d = rad("ID_g", [(0, "#FFF4B8", 1), (0.6, "#FFC93C", 1), (1, "#C98A10", 1)], cx=0.4, cy=0.4)
        b = ('<path d="M32,10 C42,24 52,32 52,44 C52,55 42,60 32,60 C22,60 12,55 12,44 C12,32 22,24 32,10 Z" fill="url(#ID_g)" stroke="#8A5A08" stroke-width="2.4"/>'
             '<path d="M20,8 L26,16 L32,6 L38,16 L44,8 L42,20 H22 Z" fill="#FFD94A" stroke="#8A5A08" stroke-width="1.8"/>'
             '<circle cx="26" cy="40" r="5" fill="#fff" opacity="0.6"/>')
    return d, b


SOURCES = {
    "LEAF_BUSH": None, "SEED_PILE": None, "DEAD_INSECT": None, "TWIGS": None, "DEW": None, "RESIN_DROP": None, "CRUMBS": None, "PEBBLES": None,
}


def source(name):
    W = H = 96
    rnd = random.Random(name)
    sh = '<ellipse cx="48" cy="88" rx="38" ry="7" fill="#000" opacity="0.18"/>'
    if name == "LEAF_BUSH":
        d = lin("ID_g", [(0, "#9AE67E", 1), (1, "#3E9E44", 1)], 0, 0, 1, 1)
        b = sh + '<path d="M48,90 L48,50" stroke="#5A8A30" stroke-width="4"/>'
        for (x, y, r, rot) in [(30, 52, 1.0, -40), (64, 48, 1.0, 35), (48, 34, 1.1, 0), (24, 72, 0.9, -70), (70, 70, 0.9, 70)]:
            b += f'<g transform="translate({x},{y}) rotate({rot}) scale({r})"><path d="M0,16 C-14,6 -12,-14 0,-18 C12,-14 14,6 0,16 Z" fill="url(#ID_g)" stroke="#2A6E30" stroke-width="2"/><path d="M0,14 L0,-14" stroke="#2A6E30" stroke-width="1.4"/></g>'
    elif name == "SEED_PILE":
        d = lin("ID_g", [(0, "#FFE29A", 1), (1, "#C9963E", 1)])
        b = sh + "".join(f'<ellipse cx="{x}" cy="{y}" rx="7" ry="9" fill="url(#ID_g)" stroke="#8E6420" stroke-width="1.6" transform="rotate({rnd.randint(-40, 40)} {x} {y})"/>'
                         for x, y in [(30, 80), (44, 82), (58, 80), (70, 82), (36, 68), (50, 66), (64, 68), (44, 54), (56, 54)])
    elif name == "DEAD_INSECT":
        d = rad("ID_g", [(0, "#A89ACF", 1), (1, "#4E3E7A", 1)], cx=0.4, cy=0.35)
        b = (sh + "".join(f'<path d="M{34 + i * 12},70 q{-6 + i * 2},12 {-10 + i * 3},16" stroke="#3A2E5A" stroke-width="3" fill="none" stroke-linecap="round"/>' for i in range(3))
             + '<ellipse cx="44" cy="66" rx="26" ry="16" fill="url(#ID_g)" stroke="#2E2340" stroke-width="2.4"/><circle cx="74" cy="64" r="10" fill="url(#ID_g)" stroke="#2E2340" stroke-width="2.4"/>'
             '<path d="M44,52 L44,80" stroke="#2E2340" stroke-width="1.6"/><ellipse cx="36" cy="60" rx="10" ry="4" fill="#fff" opacity="0.4"/>'
             '<path d="M80,58 q8,-10 12,-12 M80,70 q8,6 12,6" stroke="#2E2340" stroke-width="2" fill="none"/>')
    elif name == "TWIGS":
        d = lin("ID_g", [(0, "#C98E5A", 1), (1, "#7A4E2A", 1)])
        b = sh + ('<path d="M8,82 L88,66" stroke="#5A3418" stroke-width="9" stroke-linecap="round"/><path d="M8,82 L88,66" stroke="url(#ID_g)" stroke-width="6" stroke-linecap="round"/>'
                  '<path d="M16,70 L80,86" stroke="#5A3418" stroke-width="8" stroke-linecap="round"/><path d="M16,70 L80,86" stroke="url(#ID_g)" stroke-width="5" stroke-linecap="round"/>'
                  '<path d="M50,74 L64,52" stroke="#5A3418" stroke-width="6" stroke-linecap="round"/><path d="M50,74 L64,52" stroke="url(#ID_g)" stroke-width="3.5" stroke-linecap="round"/>'
                  '<circle cx="64" cy="50" r="5" fill="#7ED45E" stroke="#2A6E30" stroke-width="1.4"/>')
    elif name == "DEW":
        d = lin("ID_g", [(0, "#C8F0FF", 0.95), (1, "#4EAEE0", 0.95)])
        b = sh + "".join(f'<g transform="translate({x},{y}) scale({s})"><path d="M0,-12 C6,-4 10,2 10,6 C10,12 5,15 0,15 C-5,15 -10,12 -10,6 C-10,2 -6,-4 0,-12 Z" fill="url(#ID_g)" stroke="#2E7EB4" stroke-width="1.4"/><circle cx="-4" cy="4" r="2.4" fill="#fff"/></g>'
                         for x, y, s in [(30, 74, 1.2), (52, 70, 1.5), (72, 78, 1.0), (44, 84, 0.8)])
    elif name == "RESIN_DROP":
        d = rad("ID_g", [(0, "#FFE08A", 1), (1, "#D08A18", 1)], cx=0.4, cy=0.4)
        b = (sh + '<rect x="34" y="8" width="28" height="82" rx="6" fill="#9A6A3E" stroke="#5A3418" stroke-width="2.4"/>'
             '<path d="M40,16 L40,84 M50,12 L50,86" stroke="#7A4E2A" stroke-width="2" opacity="0.6"/>'
             + "".join(f'<path d="M{x},{y - 8} C{x + 6},{y} {x + 6},{y + 6} {x},{y + 8} C{x - 6},{y + 6} {x - 6},{y} {x},{y - 8} Z" fill="url(#ID_g)" stroke="#8A4E08" stroke-width="1.4"/>' for x, y in [(48, 40), (44, 64)]))
    elif name == "CRUMBS":
        d = lin("ID_g", [(0, "#FFE2A8", 1), (1, "#D8A060", 1)])
        b = sh + "".join(f'<path d="M{x - 9},{y} l4,-8 l12,-2 l5,8 l-5,7 l-12,1 Z" fill="url(#ID_g)" stroke="#9A6A30" stroke-width="1.6" transform="rotate({rnd.randint(-30, 30)} {x} {y})"/><path d="M{x - 4},{y - 3} l6,-1" stroke="#fff" stroke-width="1.6" opacity="0.6"/>'
                         for x, y in [(30, 80), (54, 82), (70, 74), (42, 66), (60, 62)])
    elif name == "PEBBLES":
        d = ""
        b = sh + pebbles(rnd, 6, 96, 96, "#B9B2AA", margin=24, rmin=7, rmax=11) + crystal(56, 66, 1.0, "#2EC4C0", "#A8F2EE", 15)
    return W, H, d, b


def build():
    for r in ["FOOD", "SEED", "MUSHROOM", "LEAF", "WOOD", "SOIL", "WATER", "MINERAL", "FOSSIL", "RESIN", "ENZYME", "RARE_MINERAL", "GENETIC", "ROYAL_ESSENCE"]:
        d, b = icon_resource(r)
        add(f"res_{r}", S, S, f'<g filter="url(#ID_sh)">{b}</g>', d + shadow_filter("ID_sh", 2, 1.5, 0.3))
    for s in SOURCES:
        w, h, d, b = source(s)
        add(f"src_{s}", w, h, b, d)
    # Oda eşyaları
    add("bed", 64, 32, '<ellipse cx="32" cy="22" rx="30" ry="9" fill="#000" opacity="0.15"/><rect x="4" y="8" width="56" height="16" rx="8" fill="url(#ID_g)" stroke="#8A9AC8" stroke-width="2"/><ellipse cx="16" cy="13" rx="9" ry="5" fill="#fff"/><path d="M28,10 C36,14 48,14 56,10" stroke="#fff" stroke-width="2" opacity="0.6" fill="none"/>',
        lin("ID_g", [(0, "#DCE6FF", 1), (1, "#A8B8E8", 1)]))
    add("queen_bed", 160, 48, '<ellipse cx="80" cy="36" rx="76" ry="10" fill="#000" opacity="0.15"/><path d="M6,34 C6,14 40,8 80,8 C120,8 154,14 154,34 Z" fill="url(#ID_g)" stroke="#C9704A" stroke-width="2.4"/><path d="M20,22 C50,14 110,14 140,22" stroke="#fff" stroke-width="3" opacity="0.6" fill="none"/>'
        + "".join(f'<circle cx="{20 + i * 20}" cy="34" r="3" fill="#FFD25A"/>' for i in range(7)), lin("ID_g", [(0, "#FFC9B0", 1), (1, "#E8866A", 1)]))
    add("shelf", 128, 24, '<rect x="2" y="4" width="124" height="10" rx="3" fill="url(#ID_g)" stroke="#6A4224" stroke-width="2"/><rect x="8" y="14" width="6" height="10" fill="#8A5A30"/><rect x="114" y="14" width="6" height="10" fill="#8A5A30"/>',
        lin("ID_g", [(0, "#D49A62", 1), (1, "#9A6A3A", 1)]))
    add("desk", 96, 48, '<rect x="6" y="18" width="84" height="10" rx="3" fill="#B07A48" stroke="#6A4224" stroke-width="2"/><rect x="12" y="28" width="6" height="18" fill="#8A5A30"/><rect x="78" y="28" width="6" height="18" fill="#8A5A30"/>'
        '<rect x="20" y="4" width="16" height="14" rx="2" fill="#FFF6E5" stroke="#B9A070" stroke-width="1.4"/><path d="M24,8 h8 M24,12 h6" stroke="#B9A070"/><path d="M54,18 l6,-14 h8 l6,14 Z" fill="#CFF4FF" stroke="#4A8EB4" stroke-width="1.6"/><path d="M56,18 l4,-6 h12 l4,6 Z" fill="#7ED45E"/>')
    add("anvil", 64, 40, '<path d="M8,12 H56 L48,20 H40 L44,34 H20 L24,20 H16 Z" fill="url(#ID_g)" stroke="#3E3E46" stroke-width="2"/><path d="M12,14 H50" stroke="#fff" stroke-width="2" opacity="0.5"/>',
        lin("ID_g", [(0, "#A8AEBA", 1), (1, "#5E6470", 1)]))
    add("trap_spikes", 64, 24, "".join(f'<path d="M{4 + i * 12},24 L{10 + i * 12},2 L{16 + i * 12},24 Z" fill="url(#ID_g)" stroke="#4E4E56" stroke-width="1.4"/>' for i in range(5)),
        lin("ID_g", [(0, "#E8EAF0", 1), (1, "#7E8490", 1)]))
    add("banner", 48, 72, '<rect x="6" y="2" width="4" height="70" fill="#6A4224"/><path d="M10,6 H44 L36,22 L44,38 H10 Z" fill="url(#ID_g)" stroke="#2E3E8A" stroke-width="2"/><path d="M20,14 l6,8 l8,-12" stroke="#fff" stroke-width="3" fill="none" stroke-linecap="round"/>',
        lin("ID_g", [(0, "#8EA2F0", 1), (1, "#4A5EC8", 1)]))
    add("flag", 48, 72, '<rect x="6" y="2" width="4" height="70" fill="#6A4224"/><path d="M10,6 C22,0 30,12 44,6 V30 C30,36 22,24 10,30 Z" fill="#fff" stroke="#888" stroke-width="1.4"/>')
    add("fan", 64, 64, '<circle cx="32" cy="32" r="28" fill="#E3F4F7" stroke="#6AAAB8" stroke-width="2.4"/>' + "".join(f'<path d="M32,32 C{32 + 20 * math.cos(a):.1f},{32 + 20 * math.sin(a):.1f} {32 + 24 * math.cos(a + 0.6):.1f},{32 + 24 * math.sin(a + 0.6):.1f} {32 + 10 * math.cos(a + 0.9):.1f},{32 + 10 * math.sin(a + 0.9):.1f} Z" fill="#8ECAD6" stroke="#4A8A98" stroke-width="1.2"/>' for a in [0, 2.09, 4.19]) + '<circle cx="32" cy="32" r="5" fill="#4A8A98"/>')
    add("cross", 48, 48, '<rect x="17" y="4" width="14" height="40" rx="4" fill="#E8505A" stroke="#A82A34" stroke-width="2"/><rect x="4" y="17" width="40" height="14" rx="4" fill="#E8505A" stroke="#A82A34" stroke-width="2"/><rect x="19" y="6" width="5" height="16" rx="2" fill="#fff" opacity="0.5"/>')
    add("scroll", 64, 48, '<rect x="10" y="6" width="44" height="36" rx="3" fill="#FFF3D8" stroke="#B9965E" stroke-width="2"/><circle cx="10" cy="24" r="6" fill="#E8D0A0" stroke="#B9965E" stroke-width="2"/><circle cx="54" cy="24" r="6" fill="#E8D0A0" stroke="#B9965E" stroke-width="2"/><path d="M18,16 h28 M18,24 h24 M18,32 h26" stroke="#B9965E" stroke-width="2"/>')
    add("pedestal", 48, 48, '<rect x="8" y="30" width="32" height="16" rx="2" fill="#E8E0D2" stroke="#A89A88" stroke-width="2"/><rect x="4" y="26" width="40" height="6" rx="2" fill="#F4EEE4" stroke="#A89A88" stroke-width="1.6"/>')
    add("globe", 64, 64, '<circle cx="32" cy="30" r="24" fill="url(#ID_g)" stroke="#2E6E9E" stroke-width="2.4"/><path d="M16,22 C24,16 28,26 36,20 C42,16 46,24 50,22 M14,36 C22,32 30,42 40,36 C44,34 48,38 52,36" stroke="#5EB45E" stroke-width="5" fill="none" stroke-linecap="round"/><rect x="28" y="54" width="8" height="8" fill="#8A5A30"/><rect x="18" y="58" width="28" height="4" rx="2" fill="#8A5A30"/>',
        rad("ID_g", [(0, "#BEE8FF", 1), (1, "#4E9ED8", 1)], cx=0.4, cy=0.35))
    add("crown", 48, 32, '<path d="M4,28 L6,8 L16,18 L24,4 L32,18 L42,8 L44,28 Z" fill="url(#ID_g)" stroke="#9A6A08" stroke-width="2" stroke-linejoin="round"/><circle cx="24" cy="20" r="3" fill="#E84A5A"/><circle cx="12" cy="23" r="2" fill="#4AB0E8"/><circle cx="36" cy="23" r="2" fill="#4AB0E8"/>',
        lin("ID_g", [(0, "#FFE680", 1), (1, "#E8A820", 1)]))
    add("crown_flower", 48, 32, "".join(f'<g transform="translate({8 + i * 11},{18 - (i % 2) * 4})">' + "".join(f'<circle cx="{4 * math.cos(a):.1f}" cy="{4 * math.sin(a):.1f}" r="3.4" fill="{c}"/>' for a in [k * 1.256 for k in range(5)]) + '<circle r="2.2" fill="#FFD25A"/></g>' for i, c in enumerate(["#FF8FB1", "#FFB0D0", "#FF8FB1", "#FFB0D0"])))
    add("crown_crystal", 48, 32, crystal(14, 20, 0.9, "#2EC4C0", "#A8F2EE", -15) + crystal(24, 16, 1.2, "#2EC4C0", "#A8F2EE") + crystal(34, 20, 0.9, "#2EC4C0", "#A8F2EE", 15))
    add("statue", 64, 96, '<rect x="10" y="78" width="44" height="16" rx="3" fill="#B9B2AA" stroke="#7E776E" stroke-width="2"/><ellipse cx="32" cy="60" rx="14" ry="18" fill="url(#ID_g)" stroke="#8E877E" stroke-width="2"/><ellipse cx="32" cy="36" rx="8" ry="7" fill="url(#ID_g)" stroke="#8E877E" stroke-width="2"/><circle cx="32" cy="22" r="8" fill="url(#ID_g)" stroke="#8E877E" stroke-width="2"/><path d="M24,14 L26,6 L30,11 L32,4 L34,11 L38,6 L40,14 Z" fill="#FFD94A" stroke="#9A6A08"/>',
        lin("ID_g", [(0, "#F2EEE8", 1), (1, "#BDB5AA", 1)]))
    add("lamp", 48, 48, '<circle cx="24" cy="22" r="22" fill="url(#ID_l)"/>' + mushroom(24, 26, 1.6, "#7FE8C8", spots=False), rad("ID_l", [(0, "#BFFFE8", 0.8), (1, "#BFFFE8", 0)]))
    add("mound", 160, 56, '<path d="M4,56 C30,10 130,10 156,56 Z" fill="url(#ID_g)" stroke="#7A4E2A" stroke-width="2.4"/>' + pebbles(random.Random(5), 6, 160, 56, "#8A5A36", margin=30, rmin=2.5, rmax=4) + '<ellipse cx="80" cy="52" rx="14" ry="6" fill="#4A2E18"/>',
        lin("ID_g", [(0, "#D7A06A", 1), (1, "#9A6A40", 1)]))
