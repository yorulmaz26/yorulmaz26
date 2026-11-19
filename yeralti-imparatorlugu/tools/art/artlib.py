"""Yeraltı İmparatorluğu çizim kütüphanesi: SVG parçaları üretir.

Her çizim bir SVG gövdesidir. Kimlik çakışmalarını önlemek için tanımlarda "ID_" öneki kullanılır;
paketleyici bunu çizimin adıyla değiştirir.
"""
import math
import random

SPRITES = []  # (ad, gen, yük, defs, gövde, tür, split)


def add(name, w, h, body, defs="", kind="sprite", split=None):
    SPRITES.append((name, w, h, defs, body, kind, split))


def lin(id_, stops, x1=0, y1=0, x2=0, y2=1):
    s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in stops)
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rad(id_, stops, cx=0.5, cy=0.5, r=0.5, fx=None, fy=None):
    s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in stops)
    f = f' fx="{fx}" fy="{fy}"' if fx is not None else ""
    return f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}"{f}>{s}</radialGradient>'


def noise_filter(id_, freq, octaves, seed, alpha_mul, alpha_add, color="0 0 0"):
    r, g, b = color.split()
    return (f'<filter id="{id_}" x="0" y="0" width="100%" height="100%" filterUnits="userSpaceOnUse">'
            f'<feTurbulence type="fractalNoise" baseFrequency="{freq}" numOctaves="{octaves}" seed="{seed}" stitchTiles="stitch"/>'
            f'<feColorMatrix type="matrix" values="0 0 0 0 {r}  0 0 0 0 {g}  0 0 0 0 {b}  0 0 0 {alpha_mul} {alpha_add}"/></filter>')


def shadow_filter(id_, dy=2, blur=2, opacity=0.35):
    return (f'<filter id="{id_}" x="-30%" y="-30%" width="160%" height="160%">'
            f'<feDropShadow dx="0" dy="{dy}" stdDeviation="{blur}" flood-color="#3a2414" flood-opacity="{opacity}"/></filter>')


def shade(hexc, f):
    hexc = hexc.lstrip("#")
    r, g, b = int(hexc[0:2], 16), int(hexc[2:4], 16), int(hexc[4:6], 16)
    if f >= 1:
        r, g, b = [int(c + (255 - c) * (f - 1)) for c in (r, g, b)]
    else:
        r, g, b = [int(c * f) for c in (r, g, b)]
    return "#%02x%02x%02x" % (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))


def textured_tile(w, h, base, seed, freq=0.06, dark=0.55, light=0.35):
    """Kenarları birleşen (dikişsiz) dokulu karo zemini."""
    defs = (noise_filter("ID_nd", freq, 4, seed, -2.6 * dark, 1.25 * dark)
            + noise_filter("ID_nl", freq * 2.2, 2, seed + 7, -3.0 * light, 1.4 * light, "1 1 1"))
    body = (f'<rect width="{w}" height="{h}" fill="{base}"/>'
            f'<rect width="{w}" height="{h}" filter="url(#ID_nd)" opacity="0.9"/>'
            f'<rect width="{w}" height="{h}" filter="url(#ID_nl)" opacity="0.5"/>')
    return defs, body


def pebbles(rnd, n, w, h, color, margin=10, rmin=2.5, rmax=5.5):
    out = []
    for _ in range(n):
        x = rnd.uniform(margin, w - margin)
        y = rnd.uniform(margin, h - margin)
        rx = rnd.uniform(rmin, rmax)
        ry = rx * rnd.uniform(0.6, 0.9)
        out.append(f'<ellipse cx="{x:.1f}" cy="{y + 1:.1f}" rx="{rx:.1f}" ry="{ry:.1f}" fill="#000" opacity="0.18"/>'
                   f'<ellipse cx="{x:.1f}" cy="{y:.1f}" rx="{rx:.1f}" ry="{ry:.1f}" fill="{color}"/>'
                   f'<ellipse cx="{x - rx * 0.3:.1f}" cy="{y - ry * 0.35:.1f}" rx="{rx * 0.45:.1f}" ry="{ry * 0.3:.1f}" fill="#fff" opacity="0.35"/>')
    return "".join(out)


def crystal(x, y, s, c1, c2, rot=0):
    return (f'<g transform="translate({x},{y}) rotate({rot}) scale({s})">'
            f'<path d="M0,-10 L6,-2 L4,8 L-4,8 L-6,-2 Z" fill="{c1}" stroke="{shade(c1, 0.6)}" stroke-width="0.8"/>'
            f'<path d="M0,-10 L6,-2 L0,0 Z" fill="{c2}" opacity="0.9"/><path d="M0,-10 L-6,-2 L0,0 Z" fill="#fff" opacity="0.35"/>'
            f'<path d="M-6,-2 L0,0 L-4,8 Z" fill="#000" opacity="0.12"/></g>')


def mushroom(x, y, s, cap="#F28A3C", spots=True):
    return (f'<g transform="translate({x},{y}) scale({s})">'
            f'<ellipse cx="0" cy="9" rx="6" ry="2" fill="#000" opacity="0.18"/>'
            f'<path d="M-2.5,0 Q-3,7 -3.2,9 L3.2,9 Q3,7 2.5,0 Z" fill="#FFF3DD" stroke="#D9B98C" stroke-width="0.7"/>'
            f'<path d="M-9,1 Q-9,-9 0,-9.5 Q9,-9 9,1 Q0,3 -9,1 Z" fill="{cap}" stroke="{shade(cap, 0.65)}" stroke-width="0.8"/>'
            f'<path d="M-6,-4 Q-4,-8 1,-8.4" stroke="#fff" stroke-width="1.6" fill="none" opacity="0.5" stroke-linecap="round"/>'
            + (f'<circle cx="-3.5" cy="-3" r="1.6" fill="#FFF6E8"/><circle cx="3" cy="-5.5" r="1.2" fill="#FFF6E8"/><circle cx="4.5" cy="-1.2" r="1" fill="#FFF6E8"/>' if spots else "")
            + '</g>')


def bone(x, y, s, rot, color="#F7EEDB"):
    return (f'<g transform="translate({x},{y}) rotate({rot}) scale({s})" stroke="{shade(color, 0.7)}" stroke-width="0.8">'
            f'<rect x="-9" y="-2" width="18" height="4" rx="2" fill="{color}"/>'
            f'<circle cx="-9" cy="-2.5" r="3" fill="{color}"/><circle cx="-9" cy="2.5" r="3" fill="{color}"/>'
            f'<circle cx="9" cy="-2.5" r="3" fill="{color}"/><circle cx="9" cy="2.5" r="3" fill="{color}"/></g>')
