#!/usr/bin/env python3
"""Procedural art generator for "Yanlis Renk".

All art is original and drawn with signed distance fields (numpy), rendered
at 4x supersampling and downscaled with LANCZOS (premultiplied alpha).

Run:  python3 -I tools/art/gen_icons.py   (from the yanlis-renk directory or anywhere)
"""
import math
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "android", "assets")
RES = os.path.join(ROOT, "android", "res")
STORE = os.path.join(ROOT, "store")
FONT_SRC = "/tmp/claude-0/fonts/Nunito-ExtraBold.ttf"
OFL_SRC = "/tmp/claude-0/fonts/OFL.txt"
SHEET = "/tmp/claude-0/-home-user-yorulmaz26/ec4f7c4f-e81f-5727-a460-175f3b68da2b/scratchpad/icons_sheet.png"

SS = 4
F = np.float32


def H(h, a=None):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


WHITE = (1.0, 1.0, 1.0)
BLACK = (0.0, 0.0, 0.0)
DARK = H("#253858")
LBLUE = H("#87CEFA")
TURQ = H("#58D6D0")
YEL = H("#FFD54F")
ORANGE = H("#FF9F43")
PINK = H("#FFB6C9")
PGREEN = H("#73D65C")
BG = H("#F8FBFF")
GRED = H("#FF5A5F")
GBLUE = H("#3D8BFF")
GGREEN = H("#3CC46B")
GYEL = H("#FFC933")
GREY = H("#DDE3EE")
STEEL = H("#8EA3C4")
SILVER = H("#C3CEDF")
GOLD = H("#FFC933")
GOLD_D = H("#F0A020")
PURPLE = H("#9B7BFF")
CORAL = H("#FF7A8A")

OW = 4.5  # outline width at final size (icons)


# ---------------------------------------------------------------- SDF prims
def circle(cx, cy, r):
    return lambda X, Y: np.sqrt((X - cx) ** 2 + (Y - cy) ** 2) - r


def box(cx, cy, w, h, r=0.0):
    hw, hh = w / 2.0 - r, h / 2.0 - r

    def f(X, Y):
        qx = np.abs(X - cx) - hw
        qy = np.abs(Y - cy) - hh
        return (np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
                + np.minimum(np.maximum(qx, qy), 0) - r)
    return f


def ellipse(cx, cy, a, b):
    def f(X, Y):
        px, py = (X - cx), (Y - cy)
        k0 = np.sqrt((px / a) ** 2 + (py / b) ** 2)
        k1 = np.sqrt((px / (a * a)) ** 2 + (py / (b * b)) ** 2) + 1e-6
        return k0 * (k0 - 1.0) / k1
    return f


def seg(ax, ay, bx, by, r):
    def f(X, Y):
        px, py = X - ax, Y - ay
        ex, ey = bx - ax, by - ay
        t = np.clip((px * ex + py * ey) / (ex * ex + ey * ey + 1e-9), 0, 1)
        return np.sqrt((px - ex * t) ** 2 + (py - ey * t) ** 2) - r
    return f


def polyline(pts, r, closed=False):
    pts = list(pts)
    if closed:
        pts = pts + [pts[0]]

    def f(X, Y):
        d = None
        for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
            px, py = X - ax, Y - ay
            ex, ey = bx - ax, by - ay
            t = np.clip((px * ex + py * ey) / (ex * ex + ey * ey + 1e-9), 0, 1)
            dd = (px - ex * t) ** 2 + (py - ey * t) ** 2
            d = dd if d is None else np.minimum(d, dd)
        return np.sqrt(d) - r
    return f


def poly(pts):
    v = np.asarray(pts, dtype=np.float64)
    n = len(v)

    def f(X, Y):
        d = (X - v[0, 0]) ** 2 + (Y - v[0, 1]) ** 2
        s = np.ones_like(X)
        for i in range(n):
            j = i - 1
            ex, ey = v[j, 0] - v[i, 0], v[j, 1] - v[i, 1]
            wx, wy = X - v[i, 0], Y - v[i, 1]
            t = np.clip((wx * ex + wy * ey) / (ex * ex + ey * ey), 0, 1)
            bx, by = wx - ex * t, wy - ey * t
            d = np.minimum(d, bx * bx + by * by)
            c1 = Y >= v[i, 1]
            c2 = Y < v[j, 1]
            c3 = ex * wy > ey * wx
            flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
            s = np.where(flip, -s, s)
        return s * np.sqrt(d)
    return f


def arc(cx, cy, R, th, a0, a1):
    """Ring segment with round caps; angles in degrees, y-down (clockwise)."""
    span = (a1 - a0) % 360.0
    e0 = (cx + R * math.cos(math.radians(a0)), cy + R * math.sin(math.radians(a0)))
    e1 = (cx + R * math.cos(math.radians(a1)), cy + R * math.sin(math.radians(a1)))

    def f(X, Y):
        dx, dy = X - cx, Y - cy
        ang = (np.degrees(np.arctan2(dy, dx)) - a0) % 360.0
        ring = np.abs(np.sqrt(dx * dx + dy * dy) - R) - th / 2.0
        c0 = np.sqrt((X - e0[0]) ** 2 + (Y - e0[1]) ** 2) - th / 2.0
        c1 = np.sqrt((X - e1[0]) ** 2 + (Y - e1[1]) ** 2) - th / 2.0
        return np.where(ang <= span, ring, np.minimum(c0, c1))
    return f


def ucapsule(ax, ay, bx, by, ra, rb):
    def f(X, Y):
        px, py = X - ax, Y - ay
        qbx, qby = bx - ax, by - ay
        h = qbx * qbx + qby * qby
        qx = np.abs((px * qby - py * qbx) / h)
        qy = (px * qbx + py * qby) / h
        b = ra - rb
        c = (math.sqrt(h - b * b), b)
        k = c[0] * qy - c[1] * qx
        m = c[0] * qx + c[1] * qy
        n = qx * qx + qy * qy
        return np.where(k < 0, np.sqrt(h * n) - ra,
                        np.where(k > c[0], np.sqrt(np.maximum(h * (n + 1 - 2 * qy), 0)) - rb, m - ra))
    return f


def union(*fs):
    def f(X, Y):
        d = fs[0](X, Y)
        for g in fs[1:]:
            d = np.minimum(d, g(X, Y))
        return d
    return f


def inter(*fs):
    def f(X, Y):
        d = fs[0](X, Y)
        for g in fs[1:]:
            d = np.maximum(d, g(X, Y))
        return d
    return f


def sub(a, *bs):
    def f(X, Y):
        d = a(X, Y)
        for b in bs:
            d = np.maximum(d, -b(X, Y))
        return d
    return f


def offset(a, r):
    return lambda X, Y: a(X, Y) - r


def rot(a, deg, cx, cy):
    c, s = math.cos(math.radians(-deg)), math.sin(math.radians(-deg))

    def f(X, Y):
        dx, dy = X - cx, Y - cy
        return a(cx + dx * c - dy * s, cy + dx * s + dy * c)
    return f


def move(a, tx, ty):
    return lambda X, Y: a(X - tx, Y - ty)


def scale(a, k, cx, cy):
    return lambda X, Y: a(cx + (X - cx) / k, cy + (Y - cy) / k) * k


def halfplane(nx, ny, c):
    """region nx*x + ny*y < c (normalised)."""
    l = math.hypot(nx, ny)
    return lambda X, Y: (nx * X + ny * Y - c) / l


def star_pts(cx, cy, R, r, n=5, a0=-90.0):
    pts = []
    for i in range(2 * n):
        rr = R if i % 2 == 0 else r
        a = math.radians(a0 + i * 180.0 / n)
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def star(cx, cy, R, r, n=5, round_=3.0, a0=-90.0):
    k = (R - round_) / R
    return offset(poly(star_pts(cx, cy, R * k, r * k, n, a0)), round_)


def sparkle_pts(cx, cy, r, p=0.55, n=72):
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        c, s = math.cos(t), math.sin(t)
        x = math.copysign(abs(c) ** (2 / p), c)
        y = math.copysign(abs(s) ** (2 / p), s)
        pts.append((cx + r * x, cy + r * y))
    return pts


def sparkle(cx, cy, r, p=0.55):
    return offset(poly(sparkle_pts(cx, cy, r - 1.5, p)), 1.5)


def heart_shape(x0, y0, x1, y1, round_=3.0):
    pts = []
    for i in range(160):
        t = 2 * math.pi * i / 160
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((x, y))
    pts = np.array(pts)
    mn, mx = pts.min(0), pts.max(0)
    w, h = (x1 - x0) - 2 * round_, (y1 - y0) - 2 * round_
    k = min(w / (mx[0] - mn[0]), h / (mx[1] - mn[1]))
    c = (mn + mx) / 2
    pts = (pts - c) * k + np.array([(x0 + x1) / 2, (y0 + y1) / 2])
    return offset(poly(pts), round_)


# ---------------------------------------------------------------- canvas
class Canvas:
    def __init__(self, w, h, ss=SS):
        self.w, self.h, self.ss = w, h, ss
        ys = (np.arange(h * ss, dtype=F) + 0.5) / ss
        xs = (np.arange(w * ss, dtype=F) + 0.5) / ss
        self.X, self.Y = np.meshgrid(xs, ys)
        self.P = np.zeros((h * ss, w * ss, 3), F)  # premultiplied rgb
        self.A = np.zeros((h * ss, w * ss), F)

    def cov(self, sd):
        return np.clip(0.5 - sd * self.ss, 0, 1).astype(F)

    def over(self, col, a):
        if isinstance(col, tuple):
            col = np.array(col, F)[None, None, :]
        a = a.astype(F)
        self.P = col * a[..., None] + self.P * (1 - a[..., None])
        self.A = a + self.A * (1 - a)

    def fill(self, f, col, alpha=1.0):
        self.over(col, self.cov(f(self.X, self.Y)) * alpha)

    def paint(self, f, col, ol=OW, olcol=DARK, olalpha=1.0, gloss=0.0, shade=0.0,
              gloss_alpha=0.45, shade_alpha=0.14, alpha=1.0):
        X, Y = self.X, self.Y
        sd = f(X, Y)
        if ol > 0:
            self.over(olcol, self.cov(sd - ol) * olalpha * alpha)
        self.over(col, self.cov(sd) * alpha)
        if shade > 0:
            sh = np.maximum(sd, -f(X, Y + shade))
            self.over(BLACK, self.cov(sh) * shade_alpha * alpha)
        if gloss > 0:
            ins = 3.0 * (self.w / 128.0) if self.w > 128 else 3.0
            g = np.maximum(sd + ins, -(f(X, Y - gloss) + ins))
            self.over(WHITE, self.cov(g) * gloss_alpha * alpha)
        return sd

    def vgrad(self, c1, c2, y0, y1):
        t = np.clip((self.Y - y0) / (y1 - y0), 0, 1)[..., None]
        return (np.array(c1, F) * (1 - t) + np.array(c2, F) * t).astype(F)

    def hgrad(self, c1, c2, x0, x1):
        t = np.clip((self.X - x0) / (x1 - x0), 0, 1)[..., None]
        return (np.array(c1, F) * (1 - t) + np.array(c2, F) * t).astype(F)

    def dgrad(self, c1, c2, x0, y0, x1, y1):
        dx, dy = x1 - x0, y1 - y0
        t = np.clip(((self.X - x0) * dx + (self.Y - y0) * dy) / (dx * dx + dy * dy), 0, 1)[..., None]
        return (np.array(c1, F) * (1 - t) + np.array(c2, F) * t).astype(F)

    def image_ss(self):
        a = np.clip(self.A, 0, 1)
        rgb = np.where(a[..., None] > 1e-6, self.P / np.maximum(a[..., None], 1e-6), 0)
        arr = np.dstack([np.clip(rgb, 0, 1), a])
        return Image.fromarray((arr * 255 + 0.5).astype(np.uint8), "RGBA")

    def image(self, size=None):
        im = self.image_ss()
        size = size or (self.w, self.h)
        return downscale(im, size)


def downscale(im, size):
    return im.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path, optimize=True)


# ---------------------------------------------------------------- icons
ICONS = {}


def icon(name):
    def deco(fn):
        ICONS[name] = fn
        return fn
    return deco


def badge(cv, col, r=50):
    cv.paint(circle(64, 64, r), cv.vgrad(mix(col, WHITE, 0.18), mix(col, BLACK, 0.06), 14, 114),
             gloss=9, shade=5)


@icon("heart_full")
def _(cv):
    cv.paint(heart_shape(13, 16, 115, 112), cv.vgrad(mix(GRED, WHITE, 0.15), mix(GRED, BLACK, 0.05), 16, 112),
             gloss=10, shade=6)
    cv.fill(ellipse(38, 40, 8, 5.5), WHITE, 0.85)


@icon("heart_empty")
def _(cv):
    cv.paint(heart_shape(13, 16, 115, 112), GREY, gloss=9, shade=6, gloss_alpha=0.6)


@icon("coin")
def _(cv):
    cv.paint(circle(64, 64, 50), cv.vgrad(H("#FFE27A"), GOLD_D, 14, 114), shade=6)
    cv.fill(circle(64, 64, 38), mix(GOLD_D, BLACK, 0.05))
    cv.fill(circle(64, 66, 36), cv.vgrad(H("#FFD54F"), H("#FFC12E"), 30, 100))
    st = star(64, 66, 24, 10.5, round_=2.5)
    cv.fill(move(st, 0, 1.5), mix(GOLD_D, BLACK, 0.12))
    cv.fill(st, H("#FFE89A"))
    g = sub(circle(64, 64, 46), circle(64, 72, 46))
    cv.fill(g, WHITE, 0.4)


@icon("diamond")
def _(cv):
    outer = [(34, 26), (94, 26), (116, 52), (64, 112), (12, 52)]
    gem = offset(poly(outer), 1.5)
    cv.paint(gem, TURQ, shade=0)
    cv.fill(poly([(34, 26), (94, 26), (116, 52), (12, 52)]), H("#A6F0EC"))
    cv.fill(poly([(46, 26), (82, 26), (90, 52), (38, 52)]), H("#D2FAF7"))
    cv.fill(poly([(38, 52), (90, 52), (64, 112)]), H("#7FE3DE"))
    cv.fill(poly([(90, 52), (116, 52), (64, 112)]), H("#3FBFB9"))
    lw = 1.6
    for a, b in [((12, 52), (116, 52)), ((46, 26), (38, 52)), ((82, 26), (90, 52)),
                 ((38, 52), (64, 112)), ((90, 52), (64, 112))]:
        cv.fill(inter(seg(a[0], a[1], b[0], b[1], lw), gem), DARK, 0.55)
    # re-stroke outer outline crisp
    cv.fill(sub(offset(gem, OW), gem), DARK)
    cv.fill(sparkle(38, 38, 9), WHITE, 0.95)


@icon("star_full")
def _(cv):
    s = star(64, 67, 55, 26, round_=6)
    cv.paint(s, cv.vgrad(H("#FFE27A"), H("#FFC12E"), 14, 114), gloss=10, shade=6)


@icon("star_empty")
def _(cv):
    cv.paint(star(64, 67, 55, 26, round_=6), GREY, gloss=9, shade=6, gloss_alpha=0.6)


@icon("lock")
def _(cv):
    shackle = sub(box(64, 48, 58, 72, 29), box(64, 48, 32, 46, 16))
    shackle = inter(shackle, halfplane(0, 1, 70))
    cv.paint(shackle, cv.vgrad(H("#DCE4F0"), SILVER, 14, 70), gloss=5)
    body = box(64, 82, 88, 64, 16)
    cv.paint(body, cv.vgrad(H("#6FA8FF"), H("#3D7BE6"), 50, 114), gloss=8, shade=6)
    kh = union(circle(64, 77, 8.5), poly([(59, 80), (69, 80), (71, 98), (57, 98)]))
    cv.fill(offset(kh, 0.5), DARK)


@icon("play")
def _(cv):
    badge(cv, PGREEN)
    tri = offset(poly([(54, 44), (54, 84), (86, 64)]), 5)
    cv.fill(move(tri, 0, 3), mix(PGREEN, BLACK, 0.3), 0.5)
    cv.fill(tri, WHITE)


@icon("pause")
def _(cv):
    badge(cv, GBLUE)
    bars = union(box(51, 64, 15, 42, 5), box(77, 64, 15, 42, 5))
    cv.fill(move(bars, 0, 3), mix(GBLUE, BLACK, 0.35), 0.5)
    cv.fill(bars, WHITE)


def gear_shape(cx, cy, R, teeth=8, tw=20, th=18, hole=14):
    parts = [circle(cx, cy, R)]
    for k in range(teeth):
        parts.append(rot(box(cx, cy - R - th / 2 + 4, tw, th + 8, 5), k * 360.0 / teeth, cx, cy))
    return sub(union(*parts), circle(cx, cy, hole))


@icon("settings")
def _(cv):
    g = gear_shape(64, 64, 36, teeth=8, tw=21, th=14, hole=14)
    cv.paint(g, cv.vgrad(H("#A9BAD6"), H("#7088B0"), 14, 114), gloss=8, shade=6)
    cv.fill(sub(circle(64, 64, 22), circle(64, 64, 14)), H("#5E7499"), 0.6)


@icon("shop")
def _(cv):
    cv.paint(arc(64, 44, 20, 8, 180, 360), cv.vgrad(H("#E37A1C"), H("#C9650F"), 20, 50), ol=OW)
    bag = offset(poly([(28, 44), (100, 44), (106, 108), (22, 108)]), 6)
    cv.paint(bag, cv.vgrad(H("#FFB46A"), H("#FF8C2A"), 40, 114), gloss=8, shade=6)
    cv.fill(circle(44, 56, 4.5), DARK)
    cv.fill(circle(84, 56, 4.5), DARK)
    cv.paint(arc(64, 56, 20, 6, 20, 160), WHITE, ol=0)
    cv.fill(star(64, 88, 13, 6, round_=2), YEL)


@icon("trophy")
def _(cv):
    gold = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 14, 110)
    for cx, a0, a1 in ((30, 90, 290), (98, 250, 90)):
        cv.paint(arc(cx, 42, 15, 8, a0, a1), gold)
    cup = offset(inter(circle(64, 34, 34), halfplane(0, -1, -16)), 2)
    cv.paint(box(64, 84, 16, 26, 3), gold)
    cv.paint(box(64, 103, 58, 16, 6), cv.vgrad(H("#C98A3B"), H("#9C6728"), 95, 112), gloss=4)
    cv.paint(box(64, 94, 38, 10, 4), gold)
    cv.paint(cup, gold, gloss=9, shade=6)
    cv.fill(star(64, 42, 15, 6.5, round_=2), H("#FFF2B8"))


@icon("missions")
def _(cv):
    cv.paint(box(64, 68, 80, 96, 14), cv.vgrad(H("#A6DCFC"), H("#62B6F2"), 20, 116), gloss=8, shade=5)
    cv.fill(box(64, 72, 60, 74, 7), WHITE)
    cv.paint(box(64, 22, 38, 18, 7), cv.vgrad(H("#DCE4F0"), SILVER, 14, 30), ol=OW)
    for i, y in enumerate((52, 72, 92)):
        done = i < 2
        cv.fill(box(46, y, 14, 14, 4), PGREEN if done else GREY)
        if done:
            cv.fill(polyline([(41, y), (45, y + 4), (51, y - 4)], 2), WHITE)
        cv.fill(seg(60, y, 82, y, 3.2), H("#C9D3E3"))


@icon("gift")
def _(cv):
    yel = cv.vgrad(H("#FFE27A"), H("#FFC12E"), 14, 60)
    cv.paint(rot(ellipse(46, 32, 16, 10), 25, 60, 40), yel)
    cv.paint(rot(ellipse(82, 32, 16, 10), -25, 68, 40), yel)
    body = box(64, 86, 80, 52, 8)
    lid = box(64, 54, 94, 24, 8)
    cv.paint(body, cv.vgrad(H("#FFC6D4"), H("#FF9AB4"), 60, 114), gloss=0, shade=6)
    cv.fill(inter(box(64, 86, 18, 60), body), yel)
    cv.paint(lid, cv.vgrad(H("#FFD0DB"), H("#FFA9BF"), 42, 66), gloss=6)
    cv.fill(inter(box(64, 54, 18, 30), lid), yel)
    cv.fill(sub(offset(lid, OW), lid), DARK)
    cv.paint(circle(64, 40, 8), yel)


@icon("profile")
def _(cv):
    bd = circle(64, 64, 50)
    cv.paint(bd, cv.vgrad(H("#7FE3DE"), H("#3FBFB9"), 14, 114))
    skin = H("#FFE1C7")
    body = inter(circle(64, 120, 38), bd)
    cv.fill(offset(body, OW * 0.8), DARK)
    cv.fill(body, WHITE)
    cv.fill(inter(offset(circle(64, 120, 38), -6), bd, halfplane(0, -1, -90)), H("#FFD54F"))
    cv.paint(circle(64, 54, 22), skin, ol=OW * 0.8)
    cv.fill(inter(circle(64, 50, 23.5), halfplane(0, 1, 46)), H("#6B4A3A"))
    cv.fill(circle(56, 57, 3.2), DARK)
    cv.fill(circle(72, 57, 3.2), DARK)
    cv.fill(arc(64, 60, 7, 2.6, 30, 150), DARK)
    cv.fill(ellipse(51, 64, 4, 2.5), PINK, 0.9)
    cv.fill(ellipse(77, 64, 4, 2.5), PINK, 0.9)
    cv.fill(sub(circle(64, 64, 46), circle(64, 71, 46)), WHITE, 0.35)


def arrow_left(cx, cy, L=40, head=18, r=7):
    x0, x1 = cx - L / 2, cx + L / 2
    return union(seg(x0, cy, x1, cy, r), seg(x0, cy, x0 + head, cy - head, r), seg(x0, cy, x0 + head, cy + head, r))


@icon("back")
def _(cv):
    badge(cv, GBLUE)
    a = arrow_left(62, 64, 40, 18, 7)
    cv.fill(move(a, 0, 3), mix(GBLUE, BLACK, 0.35), 0.5)
    cv.fill(a, WHITE)


@icon("close")
def _(cv):
    badge(cv, CORAL)
    x = union(seg(46, 46, 82, 82, 7.5), seg(82, 46, 46, 82, 7.5))
    cv.fill(move(x, 0, 3), mix(CORAL, BLACK, 0.35), 0.5)
    cv.fill(x, WHITE)


@icon("check")
def _(cv):
    badge(cv, PGREEN)
    c = polyline([(42, 65), (57, 80), (87, 48)], 7.5)
    cv.fill(move(c, 0, 3), mix(PGREEN, BLACK, 0.35), 0.5)
    cv.fill(c, WHITE)


def note_shape():
    return union(rot(ellipse(42, 92, 15, 11), -20, 42, 92), rot(ellipse(90, 82, 15, 11), -20, 90, 82),
                 box(53.5, 60, 8, 64, 2), box(101.5, 50, 8, 64, 2),
                 offset(poly([(51, 26), (104, 16), (104, 36), (51, 46)]), 1.5))


@icon("music_on")
def _(cv):
    cv.paint(note_shape(), cv.vgrad(H("#6FA8FF"), H("#2F74E8"), 14, 106), gloss=7, shade=5)


@icon("music_off")
def _(cv):
    cv.paint(note_shape(), cv.vgrad(H("#B9C6DA"), H("#93A5C2"), 14, 106), gloss=7, shade=5)
    cv.paint(seg(22, 22, 106, 106, 6), GRED)


def speaker_shape():
    return offset(union(box(30, 64, 22, 34, 3), poly([(32, 50), (64, 24), (64, 104), (32, 78)])), 3)


@icon("sound_on")
def _(cv):
    col = cv.vgrad(H("#6FA8FF"), H("#2F74E8"), 20, 108)
    cv.paint(arc(66, 64, 24, 8, -50, 50), col)
    cv.paint(arc(66, 64, 42, 8, -50, 50), col)
    cv.paint(speaker_shape(), col, gloss=7, shade=5)


@icon("sound_off")
def _(cv):
    col = cv.vgrad(H("#B9C6DA"), H("#93A5C2"), 20, 108)
    cv.paint(speaker_shape(), col, gloss=7, shade=5)
    cv.paint(union(seg(82, 50, 108, 78, 5.5), seg(108, 50, 82, 78, 5.5)), GRED)


@icon("infinity")
def _(cv):
    pts = []
    for i in range(121):
        t = 2 * math.pi * i / 120
        d = 1 + math.sin(t) ** 2
        pts.append((64 + 44 * math.cos(t) / d, 64 + 44 * math.sin(t) * math.cos(t) / d * 1.25))
    f = polyline(pts, 9.5)
    cv.paint(f, cv.hgrad(TURQ, PURPLE, 16, 112), gloss=6, shade=4)


@icon("map")
def _(cv):
    xs = [16, 45, 83, 112]
    tops = [30, 20, 30, 20]
    bots = [108, 98, 108, 98]
    panels = []
    for i in range(3):
        panels.append(poly([(xs[i], tops[i]), (xs[i + 1], tops[i + 1]), (xs[i + 1], bots[i + 1]), (xs[i], bots[i])]))
    m = offset(union(*panels), 2)
    cv.paint(m, H("#B8EBA8"), shade=5)
    cv.fill(inter(panels[1], m), H("#93DC86"))
    cv.fill(inter(offset(panels[0], -0.1), m, circle(28, 84, 14)), H("#87CEFA"), 0.9)
    for a, b in [((45, 20), (45, 98)), ((83, 30), (83, 108))]:
        cv.fill(seg(a[0], a[1], b[0], b[1], 1.3), DARK, 0.45)
    dots = [(28, 92), (36, 78), (48, 70), (60, 74), (70, 66)]
    for x, y in dots:
        cv.fill(circle(x, y, 2.6), GRED)
    pin = union(circle(80, 42, 14), poly([(68, 49), (92, 49), (80, 70)]))
    cv.paint(offset(pin, 0.5), GRED, gloss=5)
    cv.fill(circle(80, 42, 5.5), WHITE)
    cv.fill(sub(offset(m, OW), m), DARK)


@icon("calendar")
def _(cv):
    body = box(64, 70, 94, 86, 14)
    cv.paint(body, WHITE, shade=5)
    cv.fill(inter(body, halfplane(0, 1, 48)), cv.vgrad(H("#FF7E82"), GRED, 26, 48))
    cv.fill(inter(seg(10, 48, 118, 48, 1.6), body), DARK, 0.5)
    cv.fill(inter(sub(body, offset(body, -1)), halfplane(0, 1, 48)), WHITE, 0)
    for x in (44, 84):
        cv.paint(seg(x, 18, x, 36, 5.5), cv.vgrad(H("#DCE4F0"), SILVER, 14, 40))
    for r in range(2):
        for c in range(3):
            x, y = 40 + c * 24, 66 + r * 22
            col = YEL if (r == 1 and c == 1) else H("#CFE6FA")
            cv.fill(box(x, y, 15, 13, 4), col)
    cv.fill(sub(offset(body, OW), body), DARK)
    cv.fill(sub(inter(body, halfplane(0, 1, 46)), circle(64, 120, 160)), WHITE, 0)
    cv.fill(inter(offset(body, -3), halfplane(0, 1, 32), halfplane(0, -1, -28)), WHITE, 0.3)


@icon("crown")
def _(cv):
    gold = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 20, 108)
    for x, y in ((16, 38), (64, 22), (112, 38)):
        cv.paint(circle(x, y, 7), gold, ol=OW)
    c = offset(poly([(18, 42), (42, 68), (64, 28), (86, 68), (110, 42), (100, 104), (28, 104)]), 3)
    cv.paint(c, gold, gloss=8, shade=0)
    band = inter(c, halfplane(0, -1, -86))
    cv.fill(band, H("#FFB52E"))
    cv.fill(inter(seg(0, 86, 128, 86, 1.5), c), DARK, 0.5)
    cv.fill(circle(64, 96, 5.5), GRED)
    cv.fill(circle(42, 96, 4.5), GBLUE)
    cv.fill(circle(86, 96, 4.5), GGREEN)
    cv.fill(sub(offset(c, OW), c), DARK)


def shield_shape():
    pts = [(64, 14)]
    pts += [(64 + 44 * math.sin(math.radians(a)), 14 + 6 * (1 - math.cos(math.radians(a)))) for a in range(10, 91, 20)]
    pts += [(108, 28), (106, 58)]
    for i in range(1, 12):
        t = i / 12
        x = 106 * (1 - t) + 64 * t - 10 * math.sin(math.pi * t)
        y = 58 + (114 - 58) * t - 6 * math.sin(math.pi * t)
        pts.append((x + 10 * math.sin(math.pi * t) * 1.6, y))
    pts.append((64, 114))
    right = pts[1:]
    left = [(128 - x, y) for x, y in reversed(right[:-1])]
    return offset(poly([pts[0]] + right + left), 4)


@icon("shield")
def _(cv):
    s = shield_shape()
    cv.paint(s, cv.vgrad(WHITE, H("#E3EAF5"), 10, 116), shade=5)
    inner = offset(s, -9)
    cv.fill(inner, cv.vgrad(H("#6FA8FF"), H("#2F74E8"), 20, 108))
    cv.fill(inter(inner, halfplane(1, 0, 64)), WHITE, 0.16)
    cv.fill(inner, WHITE, 0)
    cv.fill(sub(offset(inner, 1.6), inner), DARK, 0.5)
    cv.fill(star(64, 60, 18, 8, round_=2.5), WHITE, 0.95)


@icon("bolt")
def _(cv):
    b = offset(poly([(72, 10), (28, 70), (58, 70), (46, 118), (100, 52), (70, 52), (90, 10)]), 3)
    cv.paint(b, cv.vgrad(H("#FFE27A"), H("#FFB52E"), 10, 118), gloss=7, shade=5)


@icon("puzzle")
def _(cv):
    cx, cy = 58, 66
    p = union(box(cx, cy, 64, 64, 9), circle(cx, cy - 36, 13), circle(cx + 36, cy, 13))
    p = sub(p, circle(cx - 36, cy, 12), circle(cx, cy + 36, 12))
    cv.paint(p, cv.vgrad(H("#93E57F"), H("#4FBF3A"), 16, 104), gloss=8, shade=6)


@icon("sparkle")
def _(cv):
    col = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 10, 118)
    cv.paint(sparkle(52, 70, 46), col, gloss=8, shade=5)
    cv.paint(sparkle(98, 30, 19), col, gloss=4)
    cv.paint(sparkle(102, 96, 12), col)


def curved_arrows(cx, cy, R, th, head):
    parts = []
    for a0, a1 in ((200, 330), (20, 150)):
        parts.append(arc(cx, cy, R, th, a0, a1))
        a = math.radians(a1)
        ex, ey = cx + R * math.cos(a), cy + R * math.sin(a)
        tx, ty = -math.sin(a), math.cos(a)
        nx, ny = math.cos(a), math.sin(a)
        tip = (ex + tx * head * 1.05, ey + ty * head * 1.05)
        b1 = (ex + nx * head - tx * 2, ey + ny * head - ty * 2)
        b2 = (ex - nx * head - tx * 2, ey - ny * head - ty * 2)
        parts.append(offset(poly([tip, b1, b2]), 2.5))
    return union(*parts)


@icon("refresh")
def _(cv):
    cv.paint(curved_arrows(64, 64, 36, 13, 17), cv.vgrad(H("#7FE3DE"), H("#2FB5AE"), 14, 114), gloss=6, shade=5)


@icon("home")
def _(cv):
    cv.paint(box(90, 34, 13, 30, 3), H("#E37A1C"))
    bodyf = box(64, 82, 76, 56, 6)
    cv.paint(bodyf, H("#FFF1C9"), shade=5)
    roof = offset(poly([(64, 18), (112, 58), (16, 58)]), 5)
    cv.paint(roof, cv.vgrad(H("#FF8A8E"), H("#F04A50"), 14, 64), gloss=7)
    cv.paint(box(64, 96, 24, 34, 9), cv.vgrad(H("#6FA8FF"), H("#2F74E8"), 80, 112), ol=3.5)
    cv.fill(circle(70, 96, 2.2), YEL)


def cube_face(cv, cx, cy, s, eye_ol):
    """Cute face for a cube of size s centred at (cx,cy)."""
    ex = s * 0.19
    ey = cy - s * 0.04
    er = s * 0.13
    for sx in (-1, 1):
        x = cx + sx * ex
        cv.paint(ellipse(x, ey, er, er * 1.12), WHITE, ol=eye_ol)
        cv.fill(circle(x + s * 0.02, ey + s * 0.025, er * 0.6), DARK)
        cv.fill(circle(x + s * 0.045, ey - s * 0.01, er * 0.22), WHITE)
    cv.fill(arc(cx, cy + s * 0.12, s * 0.11, s * 0.045, 25, 155), DARK)
    for sx in (-1, 1):
        cv.fill(ellipse(cx + sx * s * 0.33, cy + s * 0.15, s * 0.075, s * 0.045), PINK, 0.9)


@icon("character")
def _(cv):
    c = box(64, 66, 96, 92, 24)
    cv.paint(c, cv.vgrad(H("#FF7E82"), H("#F0454B"), 18, 114), gloss=10, shade=7)
    cube_face(cv, 64, 66, 96, 3.5)


@icon("costume")
def _(cv):
    hatc = cv.vgrad(H("#8E86FF"), H("#5B52D6"), 14, 96)
    cv.paint(ellipse(64, 96, 54, 15), H("#5B52D6"))
    crown = offset(poly([(36, 22), (92, 22), (88, 92), (40, 92)]), 6)
    cv.paint(crown, hatc, gloss=8)
    band = inter(crown, halfplane(0, 1, 92), halfplane(0, -1, -74))
    cv.fill(band, PINK)
    cv.fill(inter(seg(0, 74, 128, 74, 1.5), crown), DARK, 0.5)
    cv.fill(sub(offset(crown, OW), crown, ellipse(64, 96, 54, 15)), DARK)
    cv.fill(star(64, 46, 13, 6, round_=2), YEL)
    cv.fill(sparkle(96, 26, 8), YEL)


@icon("info")
def _(cv):
    badge(cv, GBLUE)
    i = union(circle(64, 40, 8.5), seg(64, 60, 64, 88, 7.5))
    cv.fill(move(i, 0, 3), mix(GBLUE, BLACK, 0.35), 0.5)
    cv.fill(i, WHITE)


@icon("clock")
def _(cv):
    yel = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 10, 50)
    cv.paint(seg(38, 104, 30, 116, 4), STEEL)
    cv.paint(seg(90, 104, 98, 116, 4), STEEL)
    cv.paint(rot(ellipse(30, 30, 17, 12), -40, 30, 30), yel)
    cv.paint(rot(ellipse(98, 30, 17, 12), 40, 98, 30), yel)
    cv.paint(box(64, 22, 12, 12, 3), STEEL)
    cv.paint(circle(64, 68, 42), cv.vgrad(H("#FF7E82"), H("#F0454B"), 26, 110), gloss=8, shade=5)
    cv.paint(circle(64, 68, 31), WHITE, ol=2.5)
    for k in range(12):
        a = math.radians(k * 30)
        r0 = 25 if k % 3 == 0 else 26.5
        cv.fill(seg(64 + r0 * math.cos(a), 68 + r0 * math.sin(a), 64 + 28 * math.cos(a), 68 + 28 * math.sin(a), 1.3 if k % 3 else 1.8), DARK, 0.6)
    cv.fill(seg(64, 68, 64, 48, 3.5), DARK)
    cv.fill(seg(64, 68, 79, 75, 3.5), DARK)
    cv.fill(circle(64, 68, 4.5), GRED)


@icon("medal")
def _(cv):
    cv.paint(offset(poly([(30, 12), (54, 12), (76, 62), (52, 62)]), 1.5), cv.vgrad(H("#6FA8FF"), H("#2F74E8"), 10, 60))
    cv.paint(offset(poly([(98, 12), (74, 12), (52, 62), (76, 62)]), 1.5), cv.vgrad(H("#FF7E82"), H("#F0454B"), 10, 60))
    gold = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 50, 116)
    cv.paint(circle(64, 82, 33), gold, gloss=7, shade=5)
    cv.fill(sub(circle(64, 82, 24), circle(64, 82, 21)), GOLD_D, 0.8)
    cv.fill(star(64, 83, 15, 6.5, round_=2), H("#FFF2B8"))


@icon("flag")
def _(cv):
    top, bot = [], []
    for i in range(41):
        x = 36 + 74 * i / 40
        w = 5 * math.sin((x - 36) / 74 * 2 * math.pi)
        top.append((x, 18 + w))
        bot.append((x, 74 + w))
    cloth = offset(poly(top + list(reversed(bot))), 2)
    cv.paint(cloth, WHITE)
    cell = 74 / 5.0

    def checks(X, Y):
        w = 5 * np.sin((X - 36) / 74 * 2 * np.pi)
        u = (X - 36) / cell
        v = (Y - 18 - w) / (56 / 4.0)
        k = (np.floor(u) + np.floor(v)) % 2
        d = np.where(k > 0.5, -1.0, 1.0).astype(F)
        return d
    cv.fill(inter(cloth, checks), DARK, 0.85)
    cv.fill(sub(offset(cloth, OW), cloth), DARK)
    cv.fill(inter(cloth, lambda X, Y: -(Y - 18 - 5 * np.sin((X - 36) / 74 * 2 * np.pi) - 9) * 1.0), WHITE, 0.25)
    cv.paint(seg(30, 18, 30, 116, 5), cv.hgrad(H("#C3CEDF"), STEEL, 24, 36))
    cv.paint(circle(30, 14, 7), YEL)


@icon("palette")
def _(cv):
    p = sub(union(ellipse(64, 66, 52, 46)), circle(86, 92, 11), circle(110, 100, 18))
    cv.paint(p, cv.vgrad(H("#FFE3B8"), H("#F3C27E"), 18, 114), gloss=8, shade=6)
    for (x, y), c in zip([(38, 52), (60, 36), (86, 42), (36, 80)], [GRED, GBLUE, GYEL, GGREEN]):
        cv.paint(circle(x, y, 9.5), c, ol=2.5)
        cv.fill(circle(x - 3, y - 3, 2.5), WHITE, 0.7)


@icon("trail")
def _(cv):
    tails = [((84, 44), (16, 66), 17, 5, PINK), ((84, 44), (42, 110), 17, 5, TURQ), ((84, 44), (20, 100), 20, 6, ORANGE)]
    for (a, b, ra, rb, c) in tails:
        f = ucapsule(a[0], a[1], b[0], b[1], ra, rb)
        cv.paint(f, cv.dgrad(c, mix(c, WHITE, 0.35), a[0], a[1], b[0], b[1]), gloss=4)
    cv.paint(circle(86, 42, 24), cv.vgrad(H("#FFE27A"), H("#FFB52E"), 18, 66), gloss=6, shade=4)
    cv.fill(sparkle(86, 42, 12), WHITE, 0.9)


@icon("magic")
def _(cv):
    wand = seg(26, 104, 74, 56, 7)
    cv.paint(wand, H("#4A5A80"))
    cv.fill(inter(wand, seg(26, 104, 34, 96, 9)), WHITE)
    cv.fill(inter(wand, seg(62, 68, 74, 56, 9)), WHITE)
    cv.fill(sub(offset(wand, OW), wand), DARK)
    cv.paint(rot(star(84, 44, 32, 14, round_=4), 12, 84, 44), cv.vgrad(H("#FFE27A"), H("#FFB52E"), 12, 76), gloss=6, shade=4)
    cv.paint(sparkle(30, 30, 12), PINK, ol=3)
    cv.paint(sparkle(110, 92, 9), TURQ, ol=3)
    cv.fill(circle(50, 18, 3.5), YEL)


@icon("box")
def _(cv):
    wood = cv.vgrad(H("#FFB46A"), H("#E8782A"), 50, 112)
    lid = inter(box(64, 56, 96, 52, 22), halfplane(0, 1, 58))
    body = box(64, 84, 96, 48, 8)
    cv.paint(body, wood, shade=6)
    cv.paint(lid, cv.vgrad(H("#FFC383"), H("#F08A38"), 26, 58), gloss=7)
    g = cv.vgrad(H("#FFE27A"), H("#FFB52E"), 26, 110)
    for x in (34, 94):
        cv.fill(inter(box(x, 70, 12, 100), union(body, lid)), g)
    cv.fill(inter(seg(0, 58, 128, 58, 2.2), union(body, lid)), DARK)
    cv.fill(sub(offset(union(body, lid), OW), union(body, lid)), DARK)
    cv.paint(box(64, 64, 22, 24, 6), g, ol=3)
    cv.fill(union(circle(64, 61, 3.5), box(64, 66, 3.5, 9, 1.5)), DARK)


@icon("plus")
def _(cv):
    p = union(box(64, 64, 30, 96, 10), box(64, 64, 96, 30, 10))
    cv.paint(p, cv.vgrad(H("#93E57F"), H("#4FBF3A"), 16, 112), gloss=8, shade=6)


@icon("minus")
def _(cv):
    p = box(64, 64, 96, 30, 10)
    cv.paint(p, cv.vgrad(H("#FF9AA6"), H("#F05A6A"), 49, 79), gloss=7, shade=5)


@icon("film")
def _(cv):
    f = box(64, 64, 100, 80, 12)
    cv.paint(f, H("#55658A"), shade=5)
    for i in range(6):
        x = 26 + i * 15.2
        cv.fill(box(x, 32, 8, 8, 2), WHITE)
        cv.fill(box(x, 96, 8, 8, 2), WHITE)
    cv.fill(box(44, 64, 34, 36, 4), H("#87CEFA"))
    cv.fill(box(84, 64, 34, 36, 4), H("#FFD54F"))


# ---------------------------------------------------------------- UI shapes
def ui_assets(out):
    os.makedirs(out, exist_ok=True)
    cv = Canvas(96, 96)
    cv.fill(box(48, 48, 96, 96, 32), WHITE)
    save(cv.image(), os.path.join(out, "panel.png"))

    cv = Canvas(96, 112)
    cv.fill(box(48, 62, 96, 100, 32), H("#CDD4E1"))
    cv.fill(box(48, 50, 96, 100, 32), WHITE)
    save(cv.image(), os.path.join(out, "button.png"))

    cv = Canvas(64, 64)
    cv.fill(box(32, 32, 64, 64, 32), WHITE)
    save(cv.image(), os.path.join(out, "pill.png"))

    cv = Canvas(128, 128)
    cv.fill(circle(64, 64, 64), WHITE)
    save(cv.image(), os.path.join(out, "circle.png"))

    cv = Canvas(128, 128)
    cv.fill(lambda X, Y: np.abs(np.sqrt((X - 64) ** 2 + (Y - 64) ** 2) - 57) - 7, WHITE)
    save(cv.image(), os.path.join(out, "ring.png"))

    def radial(w, h, fn):
        ys, xs = np.mgrid[0:h, 0:w]
        a = fn((xs + 0.5 - w / 2) / (w / 2), (ys + 0.5 - h / 2) / (h / 2))
        return a

    def white_alpha(a, rgb=(255, 255, 255)):
        h, w = a.shape
        arr = np.zeros((h, w, 4), np.uint8)
        arr[..., :3] = rgb
        arr[..., 3] = np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8)
        return Image.fromarray(arr, "RGBA")

    a = radial(256, 256, lambda x, y: np.clip(1 - np.sqrt(x * x + y * y), 0, 1) ** 2)
    save(white_alpha(a), os.path.join(out, "glow.png"))
    a = radial(32, 32, lambda x, y: np.clip(1 - np.sqrt(x * x + y * y) / 0.95, 0, 1) ** 1.5)
    a = np.clip(a * 1.25, 0, 1)
    save(white_alpha(a), os.path.join(out, "particle.png"))
    a = radial(128, 64, lambda x, y: 0.35 * np.clip(1 - np.sqrt(x * x + y * y), 0, 1) ** 1.2)
    save(white_alpha(a, (0, 0, 0)), os.path.join(out, "shadow.png"))
    g = np.repeat(np.linspace(1, 0, 256)[:, None], 4, axis=1)
    save(white_alpha(g), os.path.join(out, "gradient_v.png"))
    save(Image.new("RGBA", (4, 4), (255, 255, 255, 255)), os.path.join(out, "white.png"))

    # glyphs: white with dark translucent outline
    def glyph(name, f, ow=6):
        cv = Canvas(96, 96)
        cv.paint(f, WHITE, ol=ow, olalpha=0.55)
        save(cv.image(), os.path.join(out, name + ".png"))

    glyph("shape_circle", circle(48, 48, 34))
    glyph("shape_square", box(48, 48, 66, 66, 10))
    glyph("shape_triangle", offset(poly([(48, 14), (84, 78), (12, 78)]), 6))
    glyph("shape_star", star(48, 51, 40, 18, round_=5))
    eq = union(box(48, 36, 62, 15, 7.5), box(48, 60, 62, 15, 7.5))
    glyph("sym_equal", eq)
    glyph("sym_notequal", union(eq, seg(60, 16, 36, 80, 7)))
    glyph("sym_next", union(seg(16, 48, 76, 48, 8), seg(78, 48, 56, 26, 8), seg(78, 48, 56, 70, 8)))
    glyph("sym_prev", union(seg(80, 48, 20, 48, 8), seg(18, 48, 40, 26, 8), seg(18, 48, 40, 70, 8)))
    # repeat: counter-clockwise arrow
    R = 27
    a1 = 205
    ar = math.radians(a1)
    ex, ey = 48 + R * math.cos(ar), 50 + R * math.sin(ar)
    tx, ty = -math.sin(ar), math.cos(ar)  # clockwise tangent
    nx, ny = math.cos(ar), math.sin(ar)
    tip = (ex - tx * 18, ey - ty * 18)
    head = offset(poly([tip, (ex + nx * 16 + tx * 2, ey + ny * 16 + ty * 2), (ex - nx * 16 + tx * 2, ey - ny * 16 + ty * 2)]), 2)
    glyph("sym_repeat", union(arc(48, 50, R, 13, a1 + 8, a1 - 50 + 360), head))
    q = union(arc(48, 34, 16, 13, 180, 400), seg(48 + 16 * math.cos(math.radians(40)), 34 + 16 * math.sin(math.radians(40)), 48, 58, 6.5),
              seg(48, 54, 48, 60, 6.5), circle(48, 79, 8))
    glyph("sym_question", q)
    lp = offset(poly([(14, 24), (44, 30), (44, 78), (14, 72)]), 4)
    rp = offset(poly([(82, 24), (52, 30), (52, 78), (82, 72)]), 4)
    book = union(lp, rp, box(48, 54, 10, 50, 3))
    cv = Canvas(96, 96)
    cv.paint(book, WHITE, ol=6, olalpha=0.55)
    cv.fill(seg(48, 32, 48, 80, 1.6), DARK, 0.45)
    for y in (40, 50, 60):
        cv.fill(seg(22, y, 38, y + 3, 1.4), DARK, 0.3)
        cv.fill(seg(58, y + 3, 74, y, 1.4), DARK, 0.3)
    save(cv.image(), os.path.join(out, "sym_word.png"))
    handle = seg(70, 18, 50, 46, 6.5)
    ferrule = seg(50, 46, 44, 55, 8.5)
    tipf = offset(poly([(36, 50), (52, 60), (30, 84), (18, 86), (22, 74)]), 3)
    cv = Canvas(96, 96)
    cv.paint(union(handle, ferrule, tipf), WHITE, ol=6, olalpha=0.55)
    cv.fill(inter(seg(36, 46, 56, 58, 1.4), union(ferrule, tipf)), DARK, 0.35)
    cv.fill(inter(seg(42, 40, 58, 50, 1.4), union(handle, ferrule)), DARK, 0.35)
    save(cv.image(), os.path.join(out, "sym_paint.png"))


# ---------------------------------------------------------------- launcher
def draw_character(cv, cx, cy, s, ol, tilt=-8.0, badge=True):
    c = rot(box(cx, cy, s, s, s * 0.26), tilt, cx, cy)

    def region(kind):
        def f(X, Y):
            # rotate into cube frame
            a = math.radians(-tilt)
            dx0, dy0 = X - cx, Y - cy
            dx = dx0 * math.cos(a) - dy0 * math.sin(a)
            dy = dx0 * math.sin(a) + dy0 * math.cos(a)
            if kind == "top":
                return (dy + np.abs(dx)) / math.sqrt(2)
            if kind == "right":
                return (np.abs(dy) - dx) / math.sqrt(2)
            if kind == "bottom":
                return (np.abs(dx) - dy) / math.sqrt(2)
            return (dx + np.abs(dy)) / math.sqrt(2)
        return f

    # shadow
    cv.fill(ellipse(cx, cy + s * 0.6, s * 0.42, s * 0.07), DARK, 0.18)
    cv.fill(offset(c, ol), DARK)
    cols = {"top": GRED, "right": GBLUE, "bottom": GGREEN, "left": GYEL}
    for k, col in cols.items():
        cv.fill(inter(c, region(k)), cv.vgrad(mix(col, WHITE, 0.12), mix(col, BLACK, 0.04), cy - s / 2, cy + s / 2))
    # seams
    def seams(X, Y):
        a = math.radians(-tilt)
        dx0, dy0 = X - cx, Y - cy
        dx = dx0 * math.cos(a) - dy0 * math.sin(a)
        dy = dx0 * math.sin(a) + dy0 * math.cos(a)
        return np.minimum(np.abs(dx - dy), np.abs(dx + dy)) / math.sqrt(2) - s * 0.008
    cv.fill(inter(seams, c), WHITE, 0.55)
    # gloss and shade
    X, Y = cv.X, cv.Y
    sd = c(X, Y)
    ins = s * 0.04
    g = np.maximum(sd + ins, -(c(X, Y - s * 0.1) + ins))
    cv.over(WHITE, cv.cov(g) * 0.42)
    sh = np.maximum(sd, -c(X, Y + s * 0.06))
    cv.over(BLACK, cv.cov(sh) * 0.13)
    # face
    def R(px, py):
        a = math.radians(tilt)
        return (cx + px * math.cos(a) - py * math.sin(a), cy + px * math.sin(a) + py * math.cos(a))
    ex, ey, er = s * 0.2, -s * 0.05, s * 0.14
    for sx in (-1, 1):
        x, y = R(sx * ex, ey)
        cv.paint(ellipse(x, y, er, er * 1.15), WHITE, ol=ol * 0.75)
        px, py = R(sx * ex + s * 0.025, ey + s * 0.03)
        cv.fill(circle(px, py, er * 0.62), DARK)
        hx, hy = R(sx * ex + s * 0.06, ey - s * 0.015)
        cv.fill(circle(hx, hy, er * 0.24), WHITE)
        hx, hy = R(sx * ex - s * 0.005, ey + s * 0.075)
        cv.fill(circle(hx, hy, er * 0.11), WHITE)
    mx, my = R(0, s * 0.13)
    cv.fill(arc(mx, my, s * 0.12, s * 0.05, 25 + tilt, 155 + tilt), DARK)
    for sx in (-1, 1):
        x, y = R(sx * s * 0.34, s * 0.16)
        cv.fill(rot(ellipse(x, y, s * 0.075, s * 0.045), tilt, x, y), PINK, 0.95)
    if badge:
        bx, by, br = cx + s * 0.47, cy - s * 0.47, s * 0.2
        cv.paint(circle(bx, by, br), WHITE, ol=ol)
        w, h, bw = br * 1.05, br * 0.22, br * 0.11
        eq = union(box(bx, by - br * 0.22, w, h, h / 2), box(bx, by + br * 0.22, w, h, h / 2))
        sl = seg(bx + br * 0.22, by - br * 0.6, bx - br * 0.22, by + br * 0.6, bw)
        cv.fill(union(eq, sl), DARK)


def launcher_bg(cv, shape):
    W = cv.w
    cv.fill(shape, cv.dgrad(H("#9ED8FB"), H("#4FCFC8"), 0, 0, W, W))
    # soft decorative bubbles
    for (x, y, r, a) in [(0.18, 0.2, 0.12, 0.25), (0.86, 0.78, 0.16, 0.18), (0.82, 0.16, 0.06, 0.3), (0.14, 0.86, 0.07, 0.25)]:
        cv.fill(inter(circle(x * W, y * W, r * W), shape), WHITE, a)
    cv.fill(inter(shape, sub(shape, move(shape, 0, W * 0.035))), WHITE, 0.0)


def launcher(round_):
    S = 512
    cv = Canvas(S, S)
    if round_:
        shape = circle(S / 2, S / 2, S / 2 - 8)
    else:
        shape = box(S / 2, S / 2, S - 24, S - 24, S * 0.22)
    launcher_bg(cv, shape)
    draw_character(cv, S / 2 - 6, S / 2 + 4, S * 0.5, ol=S * 0.022)
    return cv.image_ss()


def foreground():
    S = 432
    cv = Canvas(S, S)
    draw_character(cv, S / 2 - 6, S / 2 + 6, 156, ol=432 * 0.019)
    return cv.image()


def feature_graphic(font_path):
    W, Hh = 1024, 500
    cv = Canvas(W, Hh)
    cv.fill(lambda X, Y: X * 0 - 1, cv.dgrad(H("#BFE6FD"), H("#8FE3DE"), 0, 0, W, Hh))
    # floating decoration
    import random
    rnd = random.Random(7)
    deco_cols = [GRED, GBLUE, GGREEN, GYEL, PINK, ORANGE]
    spots = [(70, 70), (180, 420), (930, 80), (980, 400), (520, 450), (620, 40), (380, 60), (860, 460), (40, 300)]
    for i, (x, y) in enumerate(spots):
        col = deco_cols[i % len(deco_cols)]
        r = rnd.uniform(14, 26)
        kind = i % 3
        if kind == 0:
            f = circle(x, y, r)
        elif kind == 1:
            f = star(x, y, r * 1.3, r * 0.6, round_=3)
        else:
            f = rot(box(x, y, r * 1.7, r * 1.7, r * 0.45), rnd.uniform(-30, 30), x, y)
        cv.paint(f, col, ol=4, alpha=0.9, gloss=r * 0.25)
    for (x, y, r) in [(300, 120, 10), (760, 130, 9), (700, 420, 12), (150, 180, 8)]:
        cv.fill(sparkle(x, y, r * 1.6), WHITE, 0.9)
    cv.fill(ellipse(250, 260, 190, 190), WHITE, 0.25)
    draw_character(cv, 250, 245, 230, ol=9)
    im = cv.image_ss()
    # title text at supersampled resolution
    s = SS
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = ImageFont.truetype(font_path, 120 * s)
    title = "Yanlış Renk"
    tx, ty = 470 * s, 250 * s
    bb = d.textbbox((0, 0), title, font=font, anchor="ls")
    tw = bb[2] - bb[0]
    maxw = (1000 - 470) * s
    if tw > maxw:
        font = ImageFont.truetype(font_path, int(120 * s * maxw / tw))
    dark = tuple(int(c * 255) for c in DARK)
    d.text((tx, ty + 10 * s), title, font=font, anchor="ls", fill=dark + (90,), stroke_width=14 * s, stroke_fill=dark + (90,))
    d.text((tx, ty), title, font=font, anchor="ls", fill=(255, 255, 255, 255), stroke_width=14 * s, stroke_fill=dark + (255,))
    sub_font = ImageFont.truetype(font_path, 42 * s)
    tag = "Renkler yalan söyler!"
    d.text((tx + 6 * s, ty + 78 * s), tag, font=sub_font, anchor="ls", fill=dark + (255,))
    # colourful underline dots
    for i, c in enumerate([GRED, GBLUE, GGREEN, GYEL]):
        x = tx + (16 + i * 40) * s
        y = ty + 120 * s
        r = 13 * s
        d.ellipse([x - r - 4 * s, y - r - 4 * s, x + r + 4 * s, y + r + 4 * s], fill=dark + (255,))
        d.ellipse([x - r, y - r, x + r, y + r], fill=tuple(int(v * 255) for v in c) + (255,))
    im = Image.alpha_composite(im, layer)
    return downscale(im, (W, Hh)).convert("RGB")


def check_font(path):
    font = ImageFont.truetype(path, 64)
    missing = font.getmask("").getbbox(), bytes(font.getmask(""))
    results = {}
    for ch in "şığİŞĞüçöÜÇÖ":
        m = font.getmask(ch)
        same = (m.getbbox() == missing[0] and bytes(m) == missing[1])
        results[ch] = not same and m.getbbox() is not None
    return results


# ---------------------------------------------------------------- main
def main():
    only = sys.argv[1:]
    icon_dir = os.path.join(ASSETS, "icons")
    os.makedirs(icon_dir, exist_ok=True)
    imgs = {}
    for name, fn in ICONS.items():
        if only and name not in only and "all" not in only and only != ["icons"]:
            continue
        cv = Canvas(128, 128)
        fn(cv)
        im = cv.image()
        save(im, os.path.join(icon_dir, name + ".png"))
        imgs[name] = im
        print("icon", name)
    if only and only != ["all"]:
        make_sheet(icon_dir)
        return

    ui_assets(os.path.join(ASSETS, "ui"))
    print("ui done")

    fonts = os.path.join(ASSETS, "fonts")
    os.makedirs(fonts, exist_ok=True)
    shutil.copyfile(FONT_SRC, os.path.join(fonts, "Nunito-ExtraBold.ttf"))
    shutil.copyfile(OFL_SRC, os.path.join(fonts, "OFL.txt"))
    res = check_font(FONT_SRC)
    print("font glyph check:", res)
    if not all(res.values()):
        print("WARNING: missing glyphs", [k for k, v in res.items() if not v])

    sq = launcher(False)
    rd = launcher(True)
    for d, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(downscale(sq, (px, px)), os.path.join(RES, "mipmap-" + d, "ic_launcher.png"))
        save(downscale(rd, (px, px)), os.path.join(RES, "mipmap-" + d, "ic_launcher_round.png"))
    save(downscale(sq, (512, 512)), os.path.join(STORE, "icon_512.png"))
    save(foreground(), os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@color/ic_launcher_background" />\n'
           '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(RES, "mipmap-anydpi-v26", n), "w") as fh:
            fh.write(xml)
    os.makedirs(os.path.join(RES, "values"), exist_ok=True)
    with open(os.path.join(RES, "values", "ic_launcher_background.xml"), "w") as fh:
        fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                 '    <color name="ic_launcher_background">#87CEFA</color>\n</resources>\n')
    print("launcher done")
    save(feature_graphic(FONT_SRC), os.path.join(STORE, "feature_graphic.png"))
    print("feature graphic done")
    make_sheet(icon_dir)


def make_sheet(icon_dir):
    names = list(ICONS.keys())
    cols = 8
    cell = 150
    rows = (len(names) + cols - 1) // cols
    ui_dir = os.path.join(ASSETS, "ui")
    ui_names = sorted(n for n in os.listdir(ui_dir) if n.startswith(("shape_", "sym_"))) if os.path.isdir(ui_dir) else []
    ui_rows = (len(ui_names) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, (rows + ui_rows) * cell + 10), tuple(int(c * 255) for c in BG) + (255,))
    d = ImageDraw.Draw(sheet)
    try:
        f = ImageFont.truetype(FONT_SRC, 13)
    except Exception:
        f = None
    for i, n in enumerate(names):
        p = os.path.join(icon_dir, n + ".png")
        if not os.path.exists(p):
            continue
        im = Image.open(p)
        x, y = (i % cols) * cell, (i // cols) * cell
        sheet.alpha_composite(im, (x + 11, y + 4))
        d.text((x + cell / 2, y + 140), n, fill=(37, 56, 88, 255), font=f, anchor="ms")
    for i, n in enumerate(ui_names):
        im = Image.open(os.path.join(ui_dir, n))
        x, y = (i % cols) * cell, (rows + i // cols) * cell + 10
        tile = Image.new("RGBA", (110, 110), (255, 160, 80, 255))
        tile.alpha_composite(im, (7, 7))
        sheet.alpha_composite(tile, (x + 20, y + 4))
        d.text((x + cell / 2, y + 132), n[:-4], fill=(37, 56, 88, 255), font=f, anchor="ms")
    os.makedirs(os.path.dirname(SHEET), exist_ok=True)
    sheet.save(SHEET)
    print("sheet", SHEET)


if __name__ == "__main__":
    main()
