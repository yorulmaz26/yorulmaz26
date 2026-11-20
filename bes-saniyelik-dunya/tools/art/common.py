"""Ortak çizim yardımcıları: süper örneklemeli (kenar yumuşatmalı) şekiller, gölgeler, atlas paketleyici."""
import math
from PIL import Image, ImageDraw, ImageFilter, ImageChops

S = 4  # süper örnekleme katsayısı


def hexc(h, a=255):
    if isinstance(h, tuple):
        return h if len(h) == 4 else h + (a,)
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    c1 = hexc(c1); c2 = hexc(c2)
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def shade(c, f):
    """f<1 koyulaştırır, f>1 açar."""
    c = hexc(c)
    if f < 1:
        return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])
    t = f - 1
    return (int(c[0] + (255 - c[0]) * t), int(c[1] + (255 - c[1]) * t), int(c[2] + (255 - c[2]) * t), c[3])


class Canvas:
    """W x H boyutlu, S kat büyük çizilip küçültülen tuval."""

    def __init__(self, w, h, bg=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.img = Image.new('RGBA', (w * S, h * S), bg)

    def layer(self):
        return Image.new('RGBA', self.img.size, (0, 0, 0, 0))

    def d(self, img=None):
        return ImageDraw.Draw(img or self.img)

    @staticmethod
    def sc(v):
        return [x * S for x in v]

    def paste(self, layer):
        self.img = Image.alpha_composite(self.img, layer)

    # --- temel şekiller (koordinatlar son boyut pikselinde)
    def rrect(self, box, r, fill=None, outline=None, width=0, img=None):
        d = self.d(img)
        d.rounded_rectangle(self.sc(box), radius=r * S, fill=hexc(fill) if fill else None,
                            outline=hexc(outline) if outline else None, width=int(width * S))

    def ellipse(self, box, fill=None, outline=None, width=0, img=None):
        self.d(img).ellipse(self.sc(box), fill=hexc(fill) if fill else None,
                            outline=hexc(outline) if outline else None, width=int(width * S))

    def circle(self, cx, cy, r, fill=None, outline=None, width=0, img=None):
        self.ellipse((cx - r, cy - r, cx + r, cy + r), fill, outline, width, img)

    def poly(self, pts, fill=None, outline=None, width=0, img=None):
        d = self.d(img)
        p = [(x * S, y * S) for x, y in pts]
        d.polygon(p, fill=hexc(fill) if fill else None)
        if outline and width:
            d.line(p + [p[0]], fill=hexc(outline), width=int(width * S), joint='curve')

    def line(self, pts, fill, width, img=None):
        d = self.d(img)
        p = [(x * S, y * S) for x, y in pts]
        d.line(p, fill=hexc(fill), width=int(width * S), joint='curve')
        r = width * S / 2
        for x, y in (p[0], p[-1]):
            d.ellipse((x - r, y - r, x + r, y + r), fill=hexc(fill))

    def arc(self, box, start, end, fill, width, img=None):
        self.d(img).arc(self.sc(box), start, end, fill=hexc(fill), width=int(width * S))

    def star(self, cx, cy, r1, r2, n=5, rot=-90, fill=None, outline=None, width=0, img=None, round_=True):
        pts = []
        for i in range(n * 2):
            a = math.radians(rot + i * 180 / n)
            r = r1 if i % 2 == 0 else r2
            pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
        self.poly(pts, fill, outline, width, img)

    def gradient_fill(self, mask_layer, top, bottom, horizontal=False):
        """mask_layer'ın alfa kanalı içine dikey (veya yatay) degrade boyar."""
        w, h = self.img.size
        grad = Image.new('RGBA', (1, 256) if not horizontal else (256, 1))
        for i in range(256):
            c = mix(top, bottom, i / 255)
            grad.putpixel((0, i) if not horizontal else (i, 0), c)
        grad = grad.resize((w, h), Image.BILINEAR)
        alpha = mask_layer.split()[3]
        out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        out.paste(grad, (0, 0), alpha)
        self.paste(out)

    def shadow(self, draw_fn, offset=(0, 4), blur=6, color=(37, 56, 88, 70)):
        lay = self.layer()
        draw_fn(lay)
        a = lay.split()[3]
        sh = Image.new('RGBA', lay.size, color)
        sh.putalpha(ImageChops.multiply(a, Image.new('L', a.size, color[3])))
        sh = sh.filter(ImageFilter.GaussianBlur(blur * S))
        base = Image.new('RGBA', lay.size, (0, 0, 0, 0))
        base.paste(sh, (int(offset[0] * S), int(offset[1] * S)), sh)
        self.paste(base)

    def done(self):
        return self.img.resize((self.w, self.h), Image.LANCZOS)


def shape_layer(c, fn):
    lay = c.layer()
    fn(lay)
    return lay


class Packer:
    """Basit raf paketleyici, libGDX TextureAtlas (legacy) formatında yazar."""

    def __init__(self, name, size=2048, pad=3, filt='Linear,Linear'):
        self.name, self.size, self.pad, self.filt = name, size, pad, filt
        self.items = []

    OVERRIDE = None  # (atlas, ad, görsel, split) -> (görsel, split): kullanıcı görselleri için kanca

    def add(self, name, img, split=None, extrude=0):
        if Packer.OVERRIDE is not None:
            r = Packer.OVERRIDE(self.name, name, img, split)
            if r is not None:
                img, split = r
        self.items.append((name, img, split, extrude))

    def write(self, out_dir):
        import os
        items = sorted(self.items, key=lambda t: (-t[1].height, -t[1].width))
        pages = []
        cur = None
        x = y = shelf = 0
        placed = []
        page_idx = 0

        def new_page():
            return Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0)), []

        page, placed = new_page()
        for name, img, split, ext in items:
            w, h = img.width + 2 * ext, img.height + 2 * ext
            if x + w + self.pad > self.size:
                x = 0; y += shelf + self.pad; shelf = 0
            if y + h + self.pad > self.size:
                pages.append((page, placed)); page, placed = new_page(); x = y = shelf = 0
            if ext:
                big = Image.new('RGBA', (w, h))
                big.paste(img.resize((w, h), Image.NEAREST), (0, 0))
                big.paste(img, (ext, ext))
                # kenarları uzat
                for i in range(ext):
                    big.paste(img.crop((0, 0, img.width, 1)), (ext, i))
                    big.paste(img.crop((0, img.height - 1, img.width, img.height)), (ext, h - 1 - i))
                    big.paste(big.crop((ext, 0, ext + 1, h)), (i, 0))
                    big.paste(big.crop((ext + img.width - 1, 0, ext + img.width, h)), (w - 1 - i, 0))
                page.paste(big, (x + self.pad, y + self.pad))
            else:
                page.paste(img, (x + self.pad, y + self.pad))
            placed.append((name, x + self.pad + ext, y + self.pad + ext, img.width, img.height, split))
            x += w + self.pad
            shelf = max(shelf, h)
        pages.append((page, placed))
        lines = []
        for i, (pg, pl) in enumerate(pages):
            # gereksiz boşluğu kırp (2'nin kuvveti yükseklik)
            used = max((py + ph for _, _, py, _, ph, _ in pl), default=1) + self.pad
            hh = 1
            while hh < used: hh *= 2
            pg = pg.crop((0, 0, self.size, min(self.size, hh)))
            fname = f"{self.name}{'' if i == 0 else i + 1}.png"
            pg.save(os.path.join(out_dir, fname), optimize=True)
            lines += ["", fname, f"size: {pg.width}, {pg.height}", "format: RGBA8888", f"filter: {self.filt}", "repeat: none"]
            for name, px, py, pw, ph, split in pl:
                lines.append(name)
                lines.append("  rotate: false")
                lines.append(f"  xy: {px}, {py}")
                lines.append(f"  size: {pw}, {ph}")
                if split:
                    lines.append(f"  split: {split[0]}, {split[1]}, {split[2]}, {split[3]}")
                lines.append(f"  orig: {pw}, {ph}")
                lines.append("  offset: 0, 0")
                lines.append("  index: -1")
        with open(os.path.join(out_dir, self.name + ".atlas"), "w") as f:
            f.write("\n".join(lines) + "\n")
        return len(pages)
