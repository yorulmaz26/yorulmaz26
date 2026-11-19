"""Yüzey sahnesi: tepeler, çimen şeridi, ağaçlar, çalılar, bulutlar, güneş, çiçekler ve biyom süsleri."""
import math, random
from artlib import *


def build():
    # Uzak ve yakın tepeler (yatay tekrarlanır)
    def hills(name, h, color1, color2, amp, seed):
        rnd = random.Random(seed)
        pts = []
        n = 8
        base = [rnd.uniform(0.25, 0.75) for _ in range(n)]
        base.append(base[0])
        d = f"M0,{h} "
        for i in range(n + 1):
            x = i * 512 / n
            y = h - amp * base[i] - 8
            if i == 0:
                d += f"L0,{y:.1f} "
            else:
                px = (i - 0.5) * 512 / n
                d += f"Q{px:.1f},{h - amp * (base[i - 1] + base[i]) / 2 - 18:.1f} {x:.1f},{y:.1f} "
        d += f"L512,{h} Z"
        add(name, 512, h, f'<path d="{d}" fill="url(#ID_g)"/>', lin("ID_g", [(0, color1, 1), (1, color2, 1)]), kind="tile")
    hills("hills_far", 160, "#A8D8C8", "#CDEBDF", 110, 3)
    hills("hills_near", 128, "#8ACB74", "#B5E09C", 80, 9)
    # Çimen şeridi: üstte çimen, altta toprak dudağı (yatay tekrar)
    rnd = random.Random(4)
    blades = ""
    for i in range(22):
        x = i * 64 / 22 + rnd.uniform(-1, 1)
        hgt = rnd.uniform(8, 16)
        blades += f'<path d="M{x:.1f},22 Q{x + rnd.uniform(-3, 3):.1f},{22 - hgt / 2:.1f} {x + rnd.uniform(-4, 4):.1f},{22 - hgt:.1f}" stroke="{rnd.choice(["#5DBB4A", "#6CCB54", "#4EA83E"])}" stroke-width="2.6" fill="none" stroke-linecap="round"/>'
    add("grass_strip", 64, 40, blades + '<rect x="0" y="20" width="64" height="12" fill="url(#ID_g)"/><rect x="0" y="31" width="64" height="9" fill="url(#ID_s)"/>',
        lin("ID_g", [(0, "#6CCB54", 1), (1, "#4E9E3A", 1)]) + lin("ID_s", [(0, "#7A5A34", 0.7), (1, "#7A5A34", 0)]), kind="tile")
    add("snow_strip", 64, 40, '<path d="M0,22 Q8,14 16,20 T32,18 T48,20 T64,22 V32 H0 Z" fill="#fff"/><path d="M0,30 H64 V32 H0 Z" fill="#DDEBF5"/><rect x="0" y="32" width="64" height="8" fill="url(#ID_s)"/>',
        lin("ID_s", [(0, "#7A5A34", 0.5), (1, "#7A5A34", 0)]), kind="tile")
    add("sand_strip", 64, 40, '<rect x="0" y="18" width="64" height="14" fill="url(#ID_g)"/>' + "".join(f'<circle cx="{rnd.uniform(2, 62):.1f}" cy="{rnd.uniform(20, 30):.1f}" r="1" fill="#C9A060"/>' for _ in range(12)) + '<rect x="0" y="31" width="64" height="9" fill="url(#ID_s)"/>',
        lin("ID_g", [(0, "#F2D898", 1), (1, "#DDB870", 1)]) + lin("ID_s", [(0, "#7A5A34", 0.6), (1, "#7A5A34", 0)]), kind="tile")
    # Ağaçlar
    add("tree", 160, 224, '<ellipse cx="80" cy="218" rx="52" ry="8" fill="#000" opacity="0.18"/>'
        '<path d="M72,220 C74,180 70,150 64,130 L96,130 C90,150 86,180 88,220 Z" fill="url(#ID_t)" stroke="#5A3418" stroke-width="2.4"/>'
        '<path d="M80,150 C70,140 60,138 52,140 M82,160 C94,150 104,150 112,154" stroke="#5A3418" stroke-width="6" stroke-linecap="round" fill="none"/>'
        + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#ID_l)" stroke="#2E7A34" stroke-width="2.4"/>' for x, y, r in [(52, 112, 34), (108, 110, 34), (80, 76, 44), (46, 74, 26), (116, 72, 26), (80, 124, 30)])
        + '<circle cx="66" cy="60" r="14" fill="#fff" opacity="0.2"/><circle cx="100" cy="94" r="9" fill="#fff" opacity="0.15"/>',
        lin("ID_t", [(0, "#A8784A", 1), (1, "#6E4626", 1)], 0, 0, 1, 0) + rad("ID_l", [(0, "#A6E688", 1), (1, "#4EA846", 1)], cx=0.4, cy=0.35))
    add("pine", 112, 224, '<ellipse cx="56" cy="218" rx="38" ry="7" fill="#000" opacity="0.18"/><rect x="50" y="170" width="12" height="50" fill="#7A4E2A" stroke="#5A3418" stroke-width="2"/>'
        + "".join(f'<path d="M56,{y} L{56 + w},{y + 56} H{56 - w} Z" fill="url(#ID_l)" stroke="#2E6A3A" stroke-width="2.4" stroke-linejoin="round"/>' for y, w in [(14, 30), (48, 40), (84, 50), (118, 54)]),
        lin("ID_l", [(0, "#6EC27A", 1), (1, "#2E8A4A", 1)]))
    add("bush", 112, 72, '<ellipse cx="56" cy="66" rx="48" ry="6" fill="#000" opacity="0.16"/>' + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#ID_l)" stroke="#2E7A34" stroke-width="2"/>' for x, y, r in [(30, 46, 20), (56, 36, 26), (84, 46, 20)])
        + '<circle cx="46" cy="28" r="3.5" fill="#FF8FB1"/><circle cx="72" cy="34" r="3.5" fill="#FFD25A"/><circle cx="34" cy="44" r="3" fill="#FF8FB1"/>', rad("ID_l", [(0, "#A6E688", 1), (1, "#4EA846", 1)], cx=0.4, cy=0.3))
    add("rock", 112, 64, '<ellipse cx="56" cy="58" rx="50" ry="6" fill="#000" opacity="0.18"/><path d="M10,58 C8,36 26,16 50,14 C80,12 104,30 104,58 Z" fill="url(#ID_g)" stroke="#6E665E" stroke-width="2.4"/><path d="M30,28 C40,20 56,18 66,22" stroke="#fff" stroke-width="4" fill="none" opacity="0.4" stroke-linecap="round"/><path d="M60,58 L66,40 L80,34" stroke="#6E665E" stroke-width="2" fill="none"/>',
        lin("ID_g", [(0, "#D2CCC4", 1), (1, "#9A928A", 1)]))
    for i, (c, cc) in enumerate([("#FF8FB1", "#FFD25A"), ("#FFFFFF", "#FFC93C"), ("#9A8CF0", "#FFF2B0")]):
        add(f"flower_{i}", 32, 56, '<path d="M16,56 Q14,40 16,22" stroke="#4E9E3A" stroke-width="3" fill="none"/><path d="M16,40 C10,38 6,32 8,28 C14,30 16,34 16,40 Z" fill="#6CCB54"/>'
            + "".join(f'<circle cx="{16 + 7 * math.cos(a):.1f}" cy="{16 + 7 * math.sin(a):.1f}" r="6" fill="{c}" stroke="{shade(c, 0.8)}" stroke-width="1"/>' for a in [k * 1.256 for k in range(5)])
            + f'<circle cx="16" cy="16" r="4.5" fill="{cc}"/>')
    add("cactus", 72, 120, '<ellipse cx="36" cy="116" rx="26" ry="4" fill="#000" opacity="0.16"/><rect x="26" y="20" width="20" height="96" rx="10" fill="url(#ID_g)" stroke="#2E6A3A" stroke-width="2.4"/>'
        '<path d="M26,66 H16 C10,66 8,62 8,56 V40 C8,36 16,36 16,40 V54 H26" fill="url(#ID_g)" stroke="#2E6A3A" stroke-width="2.4"/><path d="M46,56 H56 V34 C56,30 64,30 64,34 V54 C64,62 60,64 56,64 H46" fill="url(#ID_g)" stroke="#2E6A3A" stroke-width="2.4"/>'
        '<circle cx="36" cy="18" r="6" fill="#FF8FB1"/>', lin("ID_g", [(0, "#8ED47A", 1), (1, "#4E9E4A", 1)], 0, 0, 1, 0))
    add("reeds", 64, 120, "".join(f'<path d="M{10 + i * 11},120 Q{8 + i * 11},70 {12 + i * 11},{20 + (i % 2) * 14}" stroke="#5E9A4A" stroke-width="3.4" fill="none"/>' for i in range(5))
        + "".join(f'<rect x="{9 + i * 22}" y="{22 + i * 4}" width="7" height="22" rx="3.5" fill="#8A5A30"/>' for i in range(3)))
    add("pond", 224, 48, '<ellipse cx="112" cy="26" rx="108" ry="20" fill="#5E9A4A"/><ellipse cx="112" cy="24" rx="100" ry="16" fill="url(#ID_g)"/><path d="M60,20 q20,-6 40,0 M130,26 q16,-5 32,0" stroke="#fff" stroke-width="3" fill="none" opacity="0.6" stroke-linecap="round"/>'
        '<ellipse cx="160" cy="18" rx="12" ry="5" fill="#6CCB54" stroke="#3E8A3A"/>', lin("ID_g", [(0, "#9EDCF4", 1), (1, "#4EA8D8", 1)]))
    add("bricks", 128, 72, '<ellipse cx="64" cy="68" rx="58" ry="5" fill="#000" opacity="0.16"/>' + "".join(f'<rect x="{8 + c * 38 + (19 if r % 2 else 0)}" y="{8 + r * 20}" width="36" height="18" rx="3" fill="url(#ID_g)" stroke="#8A3E2A" stroke-width="2"/>' for r in range(3) for c in range(3 - (r % 2)))
        + '<path d="M84,40 l10,-20 l8,6" stroke="#5E9A4A" stroke-width="3" fill="none"/>', lin("ID_g", [(0, "#F0967A", 1), (1, "#C9604A", 1)]))
    add("can", 80, 64, '<ellipse cx="40" cy="58" rx="34" ry="5" fill="#000" opacity="0.16"/><g transform="rotate(-80 40 40)"><rect x="24" y="8" width="32" height="56" rx="6" fill="url(#ID_g)" stroke="#2E5E8A" stroke-width="2"/><rect x="24" y="24" width="32" height="18" fill="#fff" opacity="0.85"/><ellipse cx="40" cy="10" rx="16" ry="4" fill="#C8D0D8" stroke="#7E8890"/></g>',
        lin("ID_g", [(0, "#6EB4F0", 1), (1, "#2E74C0", 1)], 0, 0, 1, 0))
    add("cloud", 224, 112, '<path d="M36,92 C8,92 8,58 36,58 C34,28 76,16 96,40 C108,14 156,16 160,48 C192,44 212,68 196,92 Z" fill="url(#ID_g)"/><path d="M50,72 C60,62 80,60 92,66" stroke="#fff" stroke-width="6" fill="none" opacity="0.8" stroke-linecap="round"/>',
        lin("ID_g", [(0, "#FFFFFF", 0.98), (1, "#E4EEF6", 0.95)]))
    add("sun", 192, 192, '<circle cx="96" cy="96" r="92" fill="url(#ID_h)"/><circle cx="96" cy="96" r="40" fill="url(#ID_s)"/>',
        rad("ID_h", [(0, "#FFF3B0", 0.9), (0.45, "#FFE680", 0.35), (1, "#FFE680", 0)]) + rad("ID_s", [(0, "#FFFBE0", 1), (1, "#FFD23C", 1)], cx=0.45, cy=0.4))
    add("rival_flag", 56, 88, '<rect x="6" y="4" width="5" height="84" rx="2" fill="#6A4224"/><path d="M11,8 C24,2 36,14 52,8 V36 C36,42 24,30 11,36 Z" fill="url(#ID_g)" stroke="#7A1E14" stroke-width="2"/><circle cx="30" cy="22" r="5" fill="#fff" opacity="0.8"/>',
        lin("ID_g", [(0, "#F0705A", 1), (1, "#B8382A", 1)]))
    add("home_flag", 56, 88, '<rect x="6" y="4" width="5" height="84" rx="2" fill="#6A4224"/><path d="M11,8 C24,2 36,14 52,8 V36 C36,42 24,30 11,36 Z" fill="url(#ID_g)" stroke="#13705F" stroke-width="2"/><circle cx="30" cy="22" r="5" fill="#FFD25A"/>',
        lin("ID_g", [(0, "#4ED8C4", 1), (1, "#1C9282", 1)]))
