"""Uygulama simgesini üretir: android/res/mipmap-*/ic_launcher.png ve docs/play/icon-512.png (4x çizim + LANCZOS)."""
import os
from PIL import Image, ImageDraw
ROOT = os.path.join(os.path.dirname(__file__), "..")

def draw(S):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    k = S / 512
    def b(*v): return tuple(int(x * k) for x in v)
    w = max(2, int(10 * k))
    # Arka plan: gökyüzü degrade, yuvarlatılmış kare
    bg = Image.new("RGBA", (S, S))
    bd = ImageDraw.Draw(bg)
    for y in range(S):
        t = y / S; bd.line([(0, y), (S, y)], fill=(int(126 + 70 * t), int(205 + 30 * t), int(238 + 8 * t), 255))
    mask = Image.new("L", (S, S), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), int(S * 0.22), fill=255)
    im.paste(bg, (0, 0), mask)
    d.ellipse(b(40, 330, 480, 470), fill=(140, 200, 110, 255))                      # zemin
    d.ellipse(b(90, 390, 430, 455), fill=(60, 90, 50, 70))                          # gölge
    for x in (130, 215, 300, 370):                                                   # bacaklar
        d.rounded_rectangle(b(x, 300, x + 58, 430), int(22 * k), fill=(108, 176, 138, 255), outline=(70, 120, 90, 255), width=w)
        d.rounded_rectangle(b(x + 30, 305, x + 50, 420), int(10 * k), fill=(150, 210, 175, 255))
    d.ellipse(b(340, 210, 480, 330), fill=(140, 203, 164, 255), outline=(70, 120, 90, 255), width=w)   # baş
    d.ellipse(b(398, 238, 440, 280), fill=(255, 255, 255, 255)); d.ellipse(b(410, 246, 436, 272), fill=(43, 58, 51, 255))
    d.ellipse(b(420, 248, 430, 258), fill=(255, 255, 255, 255))
    d.ellipse(b(370, 285, 395, 300), fill=(255, 150, 150, 160))
    d.ellipse(b(70, 215, 420, 345), fill=(170, 124, 71, 255), outline=(120, 85, 45, 255), width=w)     # kabuk kenarı
    d.ellipse(b(75, 175, 415, 300), fill=(199, 154, 98, 255), outline=(150, 110, 60, 255), width=w)
    d.ellipse(b(95, 180, 395, 285), fill=(191, 229, 154, 255))                                          # güverte
    # Binalar
    d.polygon([b(150, 215)[0:2], b(205, 160)[0:2], b(260, 215)[0:2]], fill=(242, 140, 56, 255), outline=(170, 90, 30, 255))
    d.rectangle(b(160, 214, 250, 262), fill=(255, 241, 214, 255), outline=(170, 140, 100, 255), width=max(1, w // 2))
    d.rectangle(b(195, 232, 215, 262), fill=(150, 110, 70, 255))
    d.ellipse(b(275, 243, 330, 267), fill=(100, 175, 215, 255))
    d.rectangle(b(275, 205, 330, 255), fill=(127, 200, 234, 255), outline=(70, 140, 180, 255), width=max(1, w // 2))
    d.ellipse(b(275, 193, 330, 217), fill=(179, 229, 252, 255), outline=(70, 140, 180, 255), width=max(1, w // 2))
    d.polygon([b(108, 238)[0:2], b(135, 212)[0:2], b(162, 238)[0:2]], fill=(46, 196, 182, 255))
    d.rectangle(b(114, 237, 156, 268), fill=(255, 233, 199, 255))
    d.line(b(205, 160, 205, 120), fill=(110, 80, 50, 255), width=max(2, w // 2))
    d.polygon([b(205, 120)[0:2], b(245, 130)[0:2], b(205, 142)[0:2]], fill=(246, 196, 69, 255))
    return im

def make(size, path):
    big = draw(size * 4)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    big.resize((size, size), Image.LANCZOS).save(path)

for k, v in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    make(v, os.path.join(ROOT, "android", "res", f"mipmap-{k}", "ic_launcher.png"))
make(512, os.path.join(ROOT, "docs", "play", "icon-512.png"))
make(512, os.path.join(ROOT, "docs", "icon-512.png"))
print("simge üretildi")
