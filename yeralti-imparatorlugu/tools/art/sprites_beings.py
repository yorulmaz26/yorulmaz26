"""Karıncalar, yavrular ve diğer canlılar (tepeden görünüm, sağa bakar)."""
import math
from artlib import *

OUT = "#3B3B3B"


def leg_paths(ax, ay, side, base_deg, swing, l1=17, l2=19, width=1.0):
    a = math.radians(base_deg + swing)
    kx = ax + math.cos(a) * l1
    ky = ay + side * math.sin(a) * l1
    b = a + math.radians(28 if base_deg < 90 else -18)
    fx = kx + math.cos(b) * l2
    fy = ky + side * math.sin(b) * l2
    d = f"M{ax:.1f},{ay:.1f} L{kx:.1f},{ky:.1f} L{fx:.1f},{fy:.1f}"
    return (f'<path d="{d}" stroke="{OUT}" stroke-width="{4.6 * width:.1f}" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="{d}" stroke="#8E8E8E" stroke-width="{2.4 * width:.1f}" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')


def legs(frame, cx, cy, spread=6, width=1.0, scale=1.0):
    phase = [0, 1, 0, -1][frame % 4]
    out = []
    bases = [48, 92, 132]
    for p in range(3):
        for side in (-1, 1):
            group_a = (p % 2 == 0) == (side > 0)
            sw = (14 if group_a else -14) * phase
            ax = cx + (6 - p * 6) * scale
            out.append(leg_paths(ax, cy + side * spread * 0.5, side, bases[p], sw, 17 * scale, 19 * scale, width))
    return "".join(out)


def body_grad(id_, light="#F4F4F4", mid="#C4C4C4", dark="#707070"):
    return rad(id_, [(0, light, 1), (0.55, mid, 1), (1, dark, 1)], cx=0.42, cy=0.35, r=0.75)


def antenna(hx, hy, side, frame, length=1.0):
    wig = [0, 2, 0, -2][frame % 4] * side
    x1, y1 = hx + 6, hy + side * 6
    x2, y2 = hx + 14 * length, hy + side * (16 * length) + wig
    x3, y3 = hx + 26 * length, hy + side * (14 * length) + wig
    return (f'<path d="M{x1},{y1} L{x2:.1f},{y2:.1f} L{x3:.1f},{y3:.1f}" stroke="{OUT}" stroke-width="2.6" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<path d="M{x1},{y1} L{x2:.1f},{y2:.1f} L{x3:.1f},{y3:.1f}" stroke="#9A9A9A" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')


def ant(kind, frame, stripes=False):
    """kind: worker | soldier | scout. Gri tonlu (oyunda sınıf rengiyle boyanır)."""
    W = H = 128
    cy = 64
    head_r = {"worker": 11, "soldier": 15, "scout": 10}[kind]
    abd_rx = {"worker": 22, "soldier": 21, "scout": 19}[kind]
    defs = body_grad("ID_b") + rad("ID_eye", [(0, "#555", 1), (1, "#111", 1)])
    sh = f'<ellipse cx="62" cy="{cy + 6}" rx="46" ry="16" fill="#000" opacity="0.18"/>'
    lg = legs(frame, 63, cy, width=1.1 if kind == "soldier" else 1.0)
    abd = (f'<ellipse cx="{58 - abd_rx - 2}" cy="{cy}" rx="{abd_rx}" ry="{abd_rx * 0.74:.1f}" fill="url(#ID_b)" stroke="{OUT}" stroke-width="2"/>'
           + "".join(f'<path d="M{58 - abd_rx - 2 + o},{cy - abd_rx * 0.7:.1f} Q{58 - abd_rx - 2 + o - 5},{cy} {58 - abd_rx - 2 + o},{cy + abd_rx * 0.7:.1f}" stroke="#5A5A5A" stroke-width="1.4" fill="none" opacity="0.45"/>' for o in (-8, 0, 8))
           + (f'<path d="M{36},{cy - 13} Q{30},{cy} {36},{cy + 13}" stroke="#fff" stroke-width="4" fill="none" opacity="0.9"/><path d="M{26},{cy - 11} Q{20},{cy} {26},{cy + 11}" stroke="#fff" stroke-width="4" fill="none" opacity="0.9"/>' if stripes else "")
           + f'<ellipse cx="{58 - abd_rx * 1.35:.1f}" cy="{cy - abd_rx * 0.33:.1f}" rx="{abd_rx * 0.42:.1f}" ry="{abd_rx * 0.2:.1f}" fill="#fff" opacity="0.55"/>')
    pet = f'<ellipse cx="57" cy="{cy}" rx="4.5" ry="3.5" fill="url(#ID_b)" stroke="{OUT}" stroke-width="1.6"/>'
    thx = (f'<ellipse cx="66" cy="{cy}" rx="10" ry="7" fill="url(#ID_b)" stroke="{OUT}" stroke-width="1.8"/>'
           f'<ellipse cx="64" cy="{cy - 3}" rx="5" ry="2" fill="#fff" opacity="0.5"/>')
    if kind == "soldier":
        thx += f'<path d="M62,{cy - 6} l-3,-5 l6,3 Z M62,{cy + 6} l-3,5 l6,-3 Z" fill="#7a7a7a" stroke="{OUT}" stroke-width="1"/>'
    hx = 77 + head_r * 0.6
    ants = antenna(hx, cy, -1, frame) + antenna(hx, cy, 1, frame)
    head = (f'<ellipse cx="{hx:.1f}" cy="{cy}" rx="{head_r * 1.05:.1f}" ry="{head_r:.1f}" fill="url(#ID_b)" stroke="{OUT}" stroke-width="2"/>'
            f'<ellipse cx="{hx + head_r * 0.25:.1f}" cy="{cy - head_r * 0.62:.1f}" rx="{head_r * 0.32:.1f}" ry="{head_r * 0.22:.1f}" fill="url(#ID_eye)"/>'
            f'<ellipse cx="{hx + head_r * 0.25:.1f}" cy="{cy + head_r * 0.62:.1f}" rx="{head_r * 0.32:.1f}" ry="{head_r * 0.22:.1f}" fill="url(#ID_eye)"/>'
            f'<circle cx="{hx + head_r * 0.32:.1f}" cy="{cy - head_r * 0.68:.1f}" r="{head_r * 0.09:.1f}" fill="#fff"/>'
            f'<circle cx="{hx + head_r * 0.32:.1f}" cy="{cy + head_r * 0.56:.1f}" r="{head_r * 0.09:.1f}" fill="#fff"/>'
            f'<ellipse cx="{hx - head_r * 0.2:.1f}" cy="{cy - head_r * 0.35:.1f}" rx="{head_r * 0.4:.1f}" ry="{head_r * 0.18:.1f}" fill="#fff" opacity="0.55"/>')
    mr = head_r * (1.25 if kind == "soldier" else 0.8)
    mand = "".join(f'<path d="M{hx + head_r * 0.85:.1f},{cy + s * head_r * 0.3:.1f} q{mr * 0.6:.1f},{s * mr * 0.15:.1f} {mr:.1f},{-s * mr * 0.25:.1f}" stroke="{OUT}" stroke-width="{3.4 if kind == "soldier" else 2.4}" fill="none" stroke-linecap="round"/>' for s in (-1, 1))
    return W, H, defs, sh + lg + abd + pet + thx + ants + mand + head


def queen(frame):
    W = H = 192
    cy = 96
    defs = (rad("ID_b", [(0, "#FFD39A", 1), (0.5, "#C6702E", 1), (1, "#6E3412", 1)], cx=0.4, cy=0.35, r=0.75)
            + lin("ID_w", [(0, "#ffffff", 0.75), (1, "#CDEBFF", 0.35)]) + rad("ID_eye", [(0, "#555", 1), (1, "#111", 1)]))
    sh = f'<ellipse cx="96" cy="{cy + 10}" rx="80" ry="26" fill="#000" opacity="0.2"/>'
    lg = legs(frame, 104, cy, spread=10, width=1.4, scale=1.45).replace("#8E8E8E", "#B8642A").replace(OUT, "#4A2410")
    abd = (f'<ellipse cx="58" cy="{cy}" rx="44" ry="31" fill="url(#ID_b)" stroke="#4A2410" stroke-width="2.4"/>'
           + "".join(f'<path d="M{58 + o},{cy - 29} Q{50 + o},{cy} {58 + o},{cy + 29}" stroke="#4A2410" stroke-width="2.2" fill="none" opacity="0.55"/>' for o in (-20, -6, 8, 22))
           + f'<ellipse cx="44" cy="{cy - 12}" rx="20" ry="7" fill="#fff" opacity="0.45"/>')
    wings = "".join(f'<path d="M108,{cy + s * 4} C80,{cy + s * 46} 30,{cy + s * 52} 22,{cy + s * 34} C40,{cy + s * 22} 80,{cy + s * 12} 108,{cy + s * 4} Z" fill="url(#ID_w)" stroke="#9CC6E6" stroke-width="1.4"/>'
                    f'<path d="M100,{cy + s * 8} C80,{cy + s * 30} 50,{cy + s * 38} 32,{cy + s * 34}" stroke="#9CC6E6" stroke-width="1" fill="none"/>' for s in (-1, 1))
    pet = f'<ellipse cx="102" cy="{cy}" rx="6" ry="5" fill="url(#ID_b)" stroke="#4A2410" stroke-width="2"/>'
    thx = f'<ellipse cx="116" cy="{cy}" rx="15" ry="11" fill="url(#ID_b)" stroke="#4A2410" stroke-width="2.2"/><ellipse cx="113" cy="{cy - 4}" rx="7" ry="3" fill="#fff" opacity="0.5"/>'
    hx = 142
    head = (f'<ellipse cx="{hx}" cy="{cy}" rx="15" ry="14" fill="url(#ID_b)" stroke="#4A2410" stroke-width="2.2"/>'
            f'<ellipse cx="{hx + 4}" cy="{cy - 9}" rx="5" ry="3.4" fill="url(#ID_eye)"/><ellipse cx="{hx + 4}" cy="{cy + 9}" rx="5" ry="3.4" fill="url(#ID_eye)"/>'
            f'<circle cx="{hx + 5}" cy="{cy - 10}" r="1.4" fill="#fff"/><circle cx="{hx + 5}" cy="{cy + 8}" r="1.4" fill="#fff"/>')
    ants = antenna(hx, cy, -1, frame, 1.4) + antenna(hx, cy, 1, frame, 1.4)
    return W, H, defs, sh + wings + lg + abd + pet + thx + ants + head


def creature(kind, frame):
    W = H = 128
    cy = 64
    ph = 1 if frame else -1
    if kind == "SPIDER":
        defs = rad("ID_b", [(0, "#9C8BC0", 1), (0.6, "#5E4B7A", 1), (1, "#2E2340", 1)], cx=0.4, cy=0.35)
        lg = ""
        for i in range(4):
            for s in (-1, 1):
                a = math.radians(35 + i * 37 + ph * (8 if (i + (s > 0)) % 2 else -8))
                kx, ky = 64 - math.cos(a) * 26, cy + s * (math.sin(a) * 24 + 4)
                fx, fy = 64 - math.cos(a) * 52, cy + s * (math.sin(a) * 34 + 18)
                d = f"M64,{cy} Q{kx:.0f},{ky - s * 14:.0f} {kx:.0f},{ky:.0f} L{fx:.0f},{fy:.0f}"
                lg += f'<path d="{d}" stroke="#2E2340" stroke-width="4" fill="none" stroke-linecap="round"/><path d="{d}" stroke="#7D6A9E" stroke-width="2" fill="none" stroke-linecap="round"/>'
        body = (f'<ellipse cx="64" cy="{cy + 6}" rx="44" ry="18" fill="#000" opacity="0.18"/>' + lg
                + f'<ellipse cx="50" cy="{cy}" rx="22" ry="18" fill="url(#ID_b)" stroke="#2E2340" stroke-width="2"/>'
                f'<path d="M40,{cy - 8} l6,4 l6,-4 l6,4" stroke="#E9D86A" stroke-width="2" fill="none"/>'
                f'<ellipse cx="78" cy="{cy}" rx="13" ry="11" fill="url(#ID_b)" stroke="#2E2340" stroke-width="2"/>'
                + "".join(f'<circle cx="{86 + (i % 2) * 3}" cy="{cy - 5 + i * 3.4:.1f}" r="1.7" fill="#E84A4A"/>' for i in range(4))
                + '<ellipse cx="44" cy="54" rx="9" ry="4" fill="#fff" opacity="0.35"/>')
    elif kind == "CENTIPEDE":
        defs = rad("ID_b", [(0, "#F2A67C", 1), (0.6, "#C0603A", 1), (1, "#6A2C14", 1)], cx=0.4, cy=0.35)
        body = f'<ellipse cx="64" cy="{cy + 5}" rx="56" ry="10" fill="#000" opacity="0.18"/>'
        for i in range(9):
            x = 12 + i * 12
            y = cy + math.sin(i * 0.9 + frame * 1.6) * 5
            for s in (-1, 1):
                body += f'<path d="M{x},{y:.1f} l{-4 + ph * s * 2},{s * 13}" stroke="#4A1E0E" stroke-width="2.4" stroke-linecap="round"/>'
            body += f'<ellipse cx="{x}" cy="{y:.1f}" rx="7.5" ry="7" fill="url(#ID_b)" stroke="#4A1E0E" stroke-width="1.6"/>'
        body += f'<path d="M116,{cy - 3} q8,-6 10,-12 M116,{cy + 3} q8,6 10,12" stroke="#4A1E0E" stroke-width="2" fill="none"/>'
    elif kind == "WORM":
        defs = lin("ID_b", [(0, "#FFC2C8", 1), (1, "#E07A88", 1)])
        pts = [(10 + i * 9, cy + math.sin(i * 0.7 + frame * 2) * 6) for i in range(12)]
        d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)
        body = (f'<path d="{d}" stroke="#000" stroke-opacity="0.15" stroke-width="18" fill="none" stroke-linecap="round" transform="translate(0,4)"/>'
                f'<path d="{d}" stroke="#B85B6A" stroke-width="16" fill="none" stroke-linecap="round"/>'
                f'<path d="{d}" stroke="url(#ID_b)" stroke-width="13" fill="none" stroke-linecap="round"/>'
                + "".join(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="6.3" fill="none" stroke="#C86878" stroke-width="0.8" opacity="0.7"/>' for x, y in pts[::2])
                + f'<rect x="{pts[7][0] - 5:.1f}" y="{pts[7][1] - 7:.1f}" width="10" height="14" rx="5" fill="#E98C9A"/>')
    elif kind == "BEETLE_LARVA":
        defs = rad("ID_b", [(0, "#FFFBEA", 1), (1, "#E9CF92", 1)], cx=0.4, cy=0.35)
        body = f'<ellipse cx="64" cy="{cy + 6}" rx="40" ry="14" fill="#000" opacity="0.16"/>'
        for i in range(6):
            body += f'<ellipse cx="{30 + i * 11}" cy="{cy + (i % 2) * 2 + frame}" rx="9" ry="{13 - abs(i - 2.5):.1f}" fill="url(#ID_b)" stroke="#C9A86A" stroke-width="1.4"/>'
        body += f'<circle cx="98" cy="{cy}" r="8" fill="#C98A3A" stroke="#7A4A1A" stroke-width="1.4"/>'
    elif kind == "CAVE_HUNTER":
        defs = rad("ID_b", [(0, "#B8A6E0", 1), (0.55, "#6A5A8E", 1), (1, "#2E2440", 1)], cx=0.4, cy=0.3)
        lg = legs(frame, 70, cy, spread=14, width=1.4, scale=1.5).replace("#8E8E8E", "#5A4A7A")
        body = (f'<ellipse cx="62" cy="{cy + 8}" rx="50" ry="20" fill="#000" opacity="0.2"/>' + lg
                + f'<ellipse cx="54" cy="{cy}" rx="38" ry="26" fill="url(#ID_b)" stroke="#2E2440" stroke-width="2.4"/>'
                f'<path d="M20,{cy} L90,{cy}" stroke="#2E2440" stroke-width="2"/>'
                f'<ellipse cx="40" cy="{cy - 12}" rx="16" ry="5" fill="#fff" opacity="0.35"/>'
                f'<ellipse cx="96" cy="{cy}" rx="12" ry="11" fill="url(#ID_b)" stroke="#2E2440" stroke-width="2"/>'
                f'<path d="M104,{cy - 5} q14,-8 18,-20 M104,{cy + 5} q14,8 18,20" stroke="#2E2440" stroke-width="3.4" fill="none" stroke-linecap="round"/>')
    elif kind == "CAVE_SALAMANDER":
        defs = lin("ID_b", [(0, "#C9F0FF", 1), (1, "#6CC4E8", 1)])
        sw = 6 * ph
        body = (f'<ellipse cx="64" cy="{cy + 6}" rx="52" ry="12" fill="#000" opacity="0.15"/>'
                f'<path d="M14,{cy + sw} Q34,{cy - sw} 52,{cy}" stroke="url(#ID_b)" stroke-width="10" fill="none" stroke-linecap="round"/>'
                + "".join(f'<path d="M{x},{cy} l{4 * ph},{s * 16}" stroke="#6CC4E8" stroke-width="4" stroke-linecap="round"/>' for x in (60, 88) for s in (-1, 1))
                + f'<ellipse cx="72" cy="{cy}" rx="24" ry="10" fill="url(#ID_b)" stroke="#3A8EB4" stroke-width="1.6"/>'
                f'<ellipse cx="100" cy="{cy}" rx="12" ry="10" fill="url(#ID_b)" stroke="#3A8EB4" stroke-width="1.6"/>'
                + "".join(f'<circle cx="{x}" cy="{cy - 2 + (x % 3)}" r="2" fill="#FFB84A"/>' for x in (60, 70, 80))
                + f'<circle cx="104" cy="{cy - 5}" r="2" fill="#1A3A4A"/><circle cx="104" cy="{cy + 5}" r="2" fill="#1A3A4A"/>')
    elif kind == "TERMITE":
        w, h, d, b = ant("worker", frame)
        defs = d.replace("#F4F4F4", "#FFF8E8").replace("#C4C4C4", "#EEDDB8").replace("#707070", "#B89A68")
        return w, h, defs, b
    else:
        raise ValueError(kind)
    return W, H, defs, body


def build():
    for f in range(4):
        for kind in ("worker", "soldier", "scout"):
            w, h, d, b = ant(kind, f)
            add(f"ant_{kind}_{f}", w, h, b, d)
        w, h, d, b = ant("worker", f, stripes=True)
        add(f"ant_striped_{f}", w, h, b, d)
    for f in range(2):
        w, h, d, b = queen(f * 2)
        add(f"queen_{f}", w, h, b, d)
    for kind in ("SPIDER", "CENTIPEDE", "WORM", "BEETLE_LARVA", "CAVE_HUNTER", "CAVE_SALAMANDER", "TERMITE"):
        for f in range(2):
            w, h, d, b = creature(kind, f * 2 if kind == "TERMITE" else f)
            add(f"creature_{kind}_{f}", w, h, b, d)
    # Yavrular
    add("egg", 32, 40, '<ellipse cx="16" cy="22" rx="11" ry="15" fill="url(#ID_g)" stroke="#E2D2B0" stroke-width="1.4"/><ellipse cx="12" cy="15" rx="4" ry="6" fill="#fff" opacity="0.9"/>',
        rad("ID_g", [(0, "#FFFFFF", 1), (1, "#F3E7CC", 1)], cx=0.4, cy=0.35))
    add("larva", 56, 36, "".join(f'<ellipse cx="{10 + i * 8}" cy="{19 + (i % 2)}" rx="7" ry="{11 - abs(i - 2.5) * 1.2:.1f}" fill="url(#ID_g)" stroke="#E0C690" stroke-width="1.2"/>' for i in range(6)) + '<circle cx="50" cy="18" r="5" fill="#E9B870" stroke="#B88440"/>',
        rad("ID_g", [(0, "#FFFDF2", 1), (1, "#F5DFA8", 1)], cx=0.4, cy=0.35))
    add("pupa", 56, 36, '<ellipse cx="28" cy="18" rx="24" ry="13" fill="url(#ID_g)" stroke="#B98A50" stroke-width="1.6"/>' + "".join(f'<path d="M{12 + i * 8},7 q-3,11 0,22" stroke="#B98A50" stroke-width="1.2" fill="none" opacity="0.7"/>' for i in range(5)) + '<ellipse cx="20" cy="12" rx="9" ry="3" fill="#fff" opacity="0.5"/>',
        lin("ID_g", [(0, "#F7DDA8", 1), (1, "#D8A868", 1)]))
    add("corpse", 64, 40, '<g opacity="0.85" transform="rotate(8 32 20)"><ellipse cx="22" cy="22" rx="13" ry="9" fill="#A79C92" stroke="#6E655E" stroke-width="1.6"/><ellipse cx="38" cy="22" rx="7" ry="5" fill="#A79C92" stroke="#6E655E" stroke-width="1.4"/><circle cx="50" cy="22" r="6" fill="#A79C92" stroke="#6E655E" stroke-width="1.4"/>'
        + "".join(f'<path d="M{34 + i * 4},26 l{-4 + i * 3},10" stroke="#6E655E" stroke-width="2" stroke-linecap="round"/>' for i in range(3)) + '</g>')
