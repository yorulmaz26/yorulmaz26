"""Zemin karoları, tünel kenarları, su ve ışık efektleri."""
import random
from artlib import *

T = 64  # karo boyutu (px)

TERRAIN = {
    "SOFT_SOIL": "#C98A55", "HARD_SOIL": "#A46C42", "CLAY": "#C7714C", "SAND": "#E8C67E", "ROCK": "#9C928A",
    "WATER_SOIL": "#86A7AC", "FUNGUS": "#B38D5C", "MINERAL": "#A47350", "FOSSIL": "#BB8A5A", "RUBBLE": "#B4855A",
    "RARE_VEIN": "#8F8794", "ANCIENT_NEST": "#B99068", "CREATURE_DEN": "#A07E5E", "WATER_POCKET": "#6FB7D6", "FILL": "#A9784E",
}


def build():
    for name, base in TERRAIN.items():
        for v in range(3):
            rnd = random.Random(hash((name, v)) & 0xffff)
            seed = 11 + v * 17 + len(name)
            freq = {"SAND": 0.11, "ROCK": 0.045, "CLAY": 0.05}.get(name, 0.07)
            defs, body = textured_tile(T, T, base, seed, freq, dark=0.5 if name != "SAND" else 0.35)
            extra = ""
            if name == "SOFT_SOIL":
                extra = pebbles(rnd, 2, T, T, shade(base, 0.8), rmin=1.8, rmax=3)
            elif name == "HARD_SOIL":
                extra = pebbles(rnd, 4, T, T, shade(base, 0.72), rmin=2.5, rmax=5)
            elif name == "CLAY":
                for i in range(3):
                    y = 14 + i * 18 + rnd.uniform(-3, 3)
                    extra += f'<path d="M0,{y:.1f} Q{T / 2},{y + rnd.uniform(-4, 4):.1f} {T},{y:.1f}" stroke="{shade(base, 1.18)}" stroke-width="2.5" fill="none" opacity="0.55"/>'
            elif name == "SAND":
                for _ in range(26):
                    extra += f'<circle cx="{rnd.uniform(3, T - 3):.1f}" cy="{rnd.uniform(3, T - 3):.1f}" r="{rnd.uniform(0.6, 1.3):.1f}" fill="{shade(base, rnd.choice([0.8, 1.15]))}"/>'
            elif name == "ROCK":
                extra = (f'<path d="M{rnd.uniform(12, 30):.0f},6 L{rnd.uniform(24, 40):.0f},26 L{rnd.uniform(18, 34):.0f},44 L{rnd.uniform(30, 46):.0f},58" stroke="{shade(base, 0.6)}" stroke-width="1.6" fill="none" stroke-linejoin="round"/>'
                         + pebbles(rnd, 2, T, T, shade(base, 1.12), rmin=5, rmax=8))
            elif name == "WATER_SOIL":
                for _ in range(3):
                    x, y = rnd.uniform(12, 52), rnd.uniform(12, 52)
                    extra += (f'<path d="M{x:.1f},{y - 6:.1f} Q{x + 5:.1f},{y + 1:.1f} {x:.1f},{y + 4:.1f} Q{x - 5:.1f},{y + 1:.1f} {x:.1f},{y - 6:.1f} Z" fill="#5FB5E6" stroke="#3A8CC0" stroke-width="0.8"/>'
                              f'<circle cx="{x - 1.5:.1f}" cy="{y - 0.5:.1f}" r="1.2" fill="#fff" opacity="0.8"/>')
            elif name == "FUNGUS":
                for _ in range(3):
                    extra += mushroom(rnd.uniform(14, 50), rnd.uniform(16, 50), rnd.uniform(0.55, 0.8), rnd.choice(["#F28A3C", "#F4B04A", "#E9705A"]))
            elif name == "MINERAL":
                for _ in range(3):
                    extra += crystal(rnd.uniform(14, 50), rnd.uniform(16, 50), rnd.uniform(0.7, 1.0), "#2EC4C0", "#A8F2EE", rnd.uniform(-25, 25))
            elif name == "RARE_VEIN":
                for _ in range(3):
                    extra += crystal(rnd.uniform(14, 50), rnd.uniform(16, 50), rnd.uniform(0.8, 1.1), "#A77BE6", "#E7D7FF", rnd.uniform(-25, 25))
            elif name == "FOSSIL":
                extra = bone(32 + rnd.uniform(-6, 6), 32 + rnd.uniform(-6, 6), 1.1, rnd.uniform(-40, 40))
                extra += f'<path d="M14,48 q6,-6 12,0 q6,6 12,0" stroke="#F1E6CF" stroke-width="2" fill="none" opacity="0.7"/>'
            elif name == "RUBBLE":
                extra = pebbles(rnd, 7, T, T, shade(base, 0.85), rmin=3, rmax=6)
            elif name == "ANCIENT_NEST":
                extra = (f'<ellipse cx="32" cy="34" rx="18" ry="11" fill="{shade(base, 0.7)}"/><ellipse cx="32" cy="32" rx="16" ry="9" fill="{shade(base, 0.85)}"/>'
                         f'<ellipse cx="26" cy="33" rx="3" ry="4.5" fill="#FFF6E6" stroke="#D9C2A0"/><ellipse cx="36" cy="34" rx="3" ry="4.5" fill="#FFF6E6" stroke="#D9C2A0"/>')
            elif name == "CREATURE_DEN":
                web = "".join(f'<line x1="32" y1="33" x2="{32 + 22 * math.cos(a):.1f}" y2="{33 + 18 * math.sin(a):.1f}" stroke="#fff" stroke-width="0.8" opacity="0.6"/>' for a in [i * 1.047 for i in range(6)])
                extra = (f'<ellipse cx="32" cy="34" rx="17" ry="13" fill="#4A3426" opacity="0.85"/>' + web
                         + '<ellipse cx="32" cy="33" rx="10" ry="7" fill="none" stroke="#fff" stroke-width="0.7" opacity="0.5"/>'
                         '<circle cx="28" cy="33" r="1.8" fill="#FFD25A"/><circle cx="36" cy="33" r="1.8" fill="#FFD25A"/>')
            elif name == "WATER_POCKET":
                for i in range(3):
                    y = 16 + i * 15
                    extra += f'<path d="M8,{y} q8,-4 16,0 t16,0 t16,0" stroke="#fff" stroke-width="2" fill="none" opacity="0.55" stroke-linecap="round"/>'
            elif name == "FILL":
                for i in range(4):
                    extra += f'<rect x="{2 + (i % 2) * 16}" y="{3 + i * 16}" width="{T}" height="12" rx="3" fill="{shade(base, 1.08)}" stroke="{shade(base, 0.8)}" stroke-width="1"/>'
            add(f"tile_{name}_{v}", T, T, body + extra, defs, kind="tile")

    # Keşfedilmemiş toprak: sisli, desatüre
    for v in range(3):
        defs, body = textured_tile(T, T, "#9A6B47", 90 + v * 13, 0.05, dark=0.45, light=0.2)
        add(f"tile_UNEXPLORED_{v}", T, T, body, defs, kind="tile")
    # Tünel zemini (arka duvar): açık, sıcak
    for v in range(3):
        defs, body = textured_tile(T, T, "#EFD3A6", 40 + v * 9, 0.045, dark=0.18, light=0.25)
        add(f"tile_TUNNEL_{v}", T, T, body, defs, kind="tile")
    # Oda duvar kağıdı desenleri (beyaz, renklendirilir)
    add("pattern_dots", T, T, "".join(f'<circle cx="{8 + (i % 4) * 16 + (8 if (i // 4) % 2 else 0)}" cy="{8 + (i // 4) * 16}" r="2.2" fill="#fff" opacity="0.55"/>' for i in range(16)), kind="tile")
    add("pattern_stripes", T, T, "".join(f'<rect x="{i * 16}" y="0" width="7" height="{T}" fill="#fff" opacity="0.28"/>' for i in range(4)), kind="tile")
    add("pattern_bricks", T, T, "".join(f'<rect x="{(i % 2) * 16 + j * 32 - 16}" y="{i * 16 + 1}" width="30" height="14" rx="3" fill="#fff" opacity="0.22"/>' for i in range(4) for j in range(3)), kind="tile")

    # Tünel kenar gölgeleri ve yuvarlak köşeler (beyaz/siyah alfa, uçta renklendirilir)
    add("edge_top", T, 20, '<rect width="64" height="20" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#3a2414", 0.55), (1, "#3a2414", 0)]), kind="tile")
    add("edge_side", 14, T, '<rect width="14" height="64" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#3a2414", 0.35), (1, "#3a2414", 0)], 0, 0, 1, 0), kind="tile")
    add("edge_bottom", T, 12, '<rect width="64" height="12" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#3a2414", 0), (1, "#3a2414", 0.22)]), kind="tile")
    # Köşe: sol-üst köşede dolgu (çeyrek daire dışı) — toprak rengine boyanır
    add("corner_fill", 24, 24, '<path d="M0,0 L24,0 Q4,4 0,24 Z" fill="#fff"/>')
    add("corner_rim", 24, 24, '<path d="M24,0 Q4,4 0,24" stroke="#fff" stroke-width="3" fill="none" opacity="0.6"/>')
    # Katı zeminin açıklığa bakan alt kenarı: aydınlık dudak
    add("lip_light", T, 10, '<rect width="64" height="10" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#ffffff", 0), (0.7, "#fff4dc", 0.55), (1, "#fff4dc", 0.0)]), kind="tile")
    add("rim_dark", T, 6, '<rect width="64" height="6" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#2a1a0e", 0.0), (1, "#2a1a0e", 0.45)]), kind="tile")

    # Su
    add("water", T, T, '<rect width="64" height="64" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#7FD0F2", 0.75), (1, "#3E9ED0", 0.85)]), kind="tile")
    add("water_top", T, 8, '<path d="M0,5 q8,-4 16,0 t16,0 t16,0 t16,0 V8 H0 Z" fill="#ffffff" opacity="0.75"/>', kind="tile")

    # Işık ve yardımcılar
    add("glow", 128, 128, '<circle cx="64" cy="64" r="64" fill="url(#ID_g)"/>', rad("ID_g", [(0, "#FFF1C2", 0.9), (0.45, "#FFE29A", 0.35), (1, "#FFE29A", 0)]))
    add("soft", 128, 128, '<circle cx="64" cy="64" r="64" fill="url(#ID_g)"/>', rad("ID_g", [(0, "#ffffff", 1), (0.6, "#ffffff", 0.6), (1, "#ffffff", 0)]))
    add("disc", 128, 128, '<circle cx="64" cy="64" r="62" fill="#fff"/>')
    add("ring", 128, 128, '<circle cx="64" cy="64" r="58" stroke="#fff" stroke-width="6" fill="none"/>')
    add("white", 8, 8, '<rect width="8" height="8" fill="#fff"/>', kind="tile")
    add("stripes", T, T, "".join(f'<path d="M{i * 16 - 64},64 L{i * 16},0 L{i * 16 + 7},0 L{i * 16 - 57},64 Z" fill="#fff"/>' for i in range(9)), kind="tile")
    add("vignette", 256, 256, '<rect width="256" height="256" fill="url(#ID_g)"/>', rad("ID_g", [(0, "#2a1a0e", 0), (0.7, "#2a1a0e", 0), (1, "#2a1a0e", 0.55)]))
    add("shadow_blob", 64, 24, '<ellipse cx="32" cy="12" rx="30" ry="10" fill="url(#ID_g)"/>', rad("ID_g", [(0, "#2a1a0e", 0.45), (1, "#2a1a0e", 0)]))
    add("sky", 8, 256, '<rect width="8" height="256" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#6EC3EE", 1), (0.6, "#B4E3F7", 1), (1, "#F4FBFF", 1)]), kind="tile")
    add("drop", 6, 24, '<rect x="2" y="0" width="2" height="24" rx="1" fill="url(#ID_g)"/>', lin("ID_g", [(0, "#ffffff", 0), (1, "#C9E8FF", 0.95)]))
    add("snow", 16, 16, '<circle cx="8" cy="8" r="6" fill="url(#ID_g)"/>', rad("ID_g", [(0, "#ffffff", 1), (1, "#ffffff", 0)]))
