#!/usr/bin/env python3
"""Tanıtım videosu kurgusu: gerçek oyun kayıtlarından (MediaTour RECORD) 30 saniyelik video üretir.

Kullanım: python3 compose_video.py <kurgu.json> <kayıt_klasörü> <dil> <biçim> <çıktı.mp4> [--cta]
  biçim: vertical (1080x1920) | horizontal (1920x1080)
  --cta: sosyal medya sürümü; kapanışta harekete geçirici çağrı (captions.json: cta)
Kurgu dosyası (düzenlenebilir kaynak): bölümler [{clip, start, dur, caption}], kapanış süresi.
Ses: music.py ile üretilen özgün müzik. Çıktı: H.264 + AAC, 30 kare/sn. Altyazı: <çıktı>.srt
"""
import json, os, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
FONT = os.path.join(ROOT, "tools", "brand", "fonts", "Nunito-Variable.ttf")
NAVY = (32, 56, 88); FPS = 30; SRC_W, SRC_H = 1920, 1080
CROP = (380, 0, 1460, 1080)  # dikey sürüm için kare kesit: oyuncu ve kural sayacı içeride, yan HUD kutuları dışarıda

def font(size, w=1000):
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([w])
    except Exception: pass
    return f

def wrap(text, f, maxw):
    words = text.split(); lines = []; cur = ""
    for w in words:
        t = (cur + " " + w).strip()
        if f.getlength(t) <= maxw or not cur: cur = t
        else: lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

def caption_img(text, maxw, size):
    while True:
        f = font(size); lines = wrap(text, f, maxw)
        if len(lines) <= 2 or size <= 40: break
        size -= 6
    lh = int(size * 1.12); h = lh * len(lines) + 40
    im = Image.new("RGBA", (maxw + 60, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for i, ln in enumerate(lines):
        x = (im.width - f.getlength(ln)) / 2; y = 14 + i * lh
        d.text((x, y), ln, font=f, fill=(255, 255, 255), stroke_width=max(6, size // 9), stroke_fill=NAVY)
        d.text((x, y), ln, font=f, fill=(255, 255, 255))
    return im

def sky(w, h):
    top, bot = np.array([110, 198, 250]), np.array([220, 242, 255])
    t = np.linspace(0, 1, h)[:, None, None]
    arr = (top + (bot - top) * t).astype(np.uint8).repeat(w, 1)
    im = Image.fromarray(arr, "RGB")
    cl = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(cl)
    for (cx, cy, r) in [(0.15, 0.12, 0.09), (0.25, 0.14, 0.11), (0.8, 0.1, 0.1), (0.9, 0.13, 0.08), (0.2, 0.85, 0.12), (0.75, 0.88, 0.13)]:
        R = r * w; d.ellipse((cx * w - R * 1.7, cy * h - R * 0.7, cx * w + R * 1.7, cy * h + R * 0.7), fill=(255, 255, 255, 160))
    cl = cl.filter(ImageFilter.GaussianBlur(w // 80))
    im.paste(cl, (0, 0), cl)
    return im

def read_segment(path, start, dur):
    cmd = ["ffmpeg", "-v", "error", "-ss", f"{start:.3f}", "-i", path, "-t", f"{dur:.3f}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-r", str(FPS), "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    n = len(raw) // (SRC_W * SRC_H * 3)
    frames = np.frombuffer(raw[: n * SRC_W * SRC_H * 3], np.uint8).reshape(n, SRC_H, SRC_W, 3)
    need = int(round(dur * FPS))
    if n < need: frames = np.concatenate([frames, np.repeat(frames[-1:], need - n, 0)])
    return frames[:need]

class Composer:
    def __init__(self, fmt, lang, captions):
        self.fmt = fmt; self.lang = lang; self.cap = captions
        self.W, self.H = (1080, 1920) if fmt == "vertical" else (1920, 1080)
        self.cache = {}

    def cap_img(self, text):
        if text not in self.cache:
            self.cache[text] = caption_img(text, self.W - 140, 84 if self.fmt == "vertical" else 66)
        return self.cache[text]

    def frame(self, src, caption, full=False):
        if self.fmt == "vertical":
            # arka plan: aynı karenin bulanık, büyütülmüş hali (siyah kenar yok), ortada kare oyun görüntüsü
            sq = Image.fromarray(src).crop(CROP)
            if full:  # menü ekranları: tam kare oranı korunarak küçültülür, boşluk bulanık görüntüyle dolar
                whole = Image.fromarray(src)
                sq = whole.resize((1080, 1080), Image.BILINEAR).filter(ImageFilter.GaussianBlur(18))
                sq = Image.blend(sq, Image.new("RGB", sq.size, (235, 246, 255)), 0.3)
                fw = whole.resize((1080, 608), Image.LANCZOS); sq.paste(fw, (0, (1080 - 608) // 2))
            bg = sq.resize((self.H, self.H), Image.BILINEAR).crop(((self.H - self.W) // 2, 0, (self.H - self.W) // 2 + self.W, self.H))
            bg = bg.resize((self.W // 6, self.H // 6)).filter(ImageFilter.GaussianBlur(4)).resize((self.W, self.H), Image.BILINEAR)
            bg = Image.blend(bg, Image.new("RGB", bg.size, (235, 246, 255)), 0.25)
            y0 = 430
            card = Image.new("L", (1080, 1080), 0); ImageDraw.Draw(card).rounded_rectangle((0, 0, 1079, 1079), 36, fill=255)
            border = Image.new("L", (1080, 1096), 0); ImageDraw.Draw(border).rounded_rectangle((0, 0, 1079, 1095), 40, fill=255)
            bg.paste((255, 255, 255), (0, y0 - 8), border)
            bg.paste(sq, (0, y0), card)
            if caption:
                c = self.cap_img(caption); bg.paste(c, ((self.W - c.width) // 2, (y0 - c.height) // 2), c)
            return bg
        im = Image.fromarray(src).copy()
        if caption:
            c = self.cap_img(caption)
            pill = Image.new("RGBA", (c.width + 40, c.height + 10), (0, 0, 0, 0))
            ImageDraw.Draw(pill).rounded_rectangle((0, 0, pill.width - 1, pill.height - 1), 40, fill=(32, 56, 88, 120))
            x = (self.W - pill.width) // 2; y = self.H - pill.height - 40
            im.paste(pill, (x, y), pill); im.paste(c, (x + 20, y + 5), c)
        return im

    def end_card(self, t, cta):
        bg = sky(self.W, self.H)
        logo = Image.open(os.path.join(ROOT, "RELEASE_PACKAGE", "03_Logos", "Transparent", "logo_bilingual_tr_main.png" if self.lang == "tr" else "logo_bilingual_en_main.png"))
        lw = int(self.W * (0.86 if self.fmt == "vertical" else 0.52))
        k = min(1.0, 0.85 + t * 0.5)  # hafif büyüyerek belirme
        w = int(lw * k); logo = logo.resize((w, int(logo.height * w / logo.width)), Image.LANCZOS)
        cy = int(self.H * (0.40 if self.fmt == "vertical" else 0.42))
        bg.paste(logo, ((self.W - logo.width) // 2, cy - logo.height // 2), logo)
        d = ImageDraw.Draw(bg)
        tag = self.cap["tagline"]; f = font(64 if self.fmt == "vertical" else 54)
        ty = cy + logo.height // 2 + 30
        d.text(((self.W - f.getlength(tag)) / 2, ty), tag, font=f, fill=(255, 255, 255), stroke_width=8, stroke_fill=NAVY)
        if cta:
            txt = self.cap["cta"]; fb = font(70 if self.fmt == "vertical" else 58)
            bw = int(fb.getlength(txt)) + 120; bh = int(fb.size * 1.7)
            bx = (self.W - bw) // 2; by = ty + 120
            d.rounded_rectangle((bx, by + 10, bx + bw, by + bh + 10), bh // 2, fill=(46, 140, 60))
            d.rounded_rectangle((bx, by, bx + bw, by + bh), bh // 2, fill=(115, 214, 92))
            d.text((bx + 60, by + (bh - fb.size) / 2 - 8), txt, font=fb, fill=(255, 255, 255), stroke_width=5, stroke_fill=(46, 120, 50))
        fs = font(40, 800); brand = "Magic Lumos Software"
        d.text(((self.W - fs.getlength(brand)) / 2, self.H - 110), brand, font=fs, fill=NAVY)
        return bg

def srt_time(s):
    ms = int(round(s * 1000)); h, ms = divmod(ms, 3600000); m, ms = divmod(ms, 60000); sec, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{sec:02d},{ms:03d}"

def main():
    edl_path, clips, lang, fmt, out = sys.argv[1:6]; cta = "--cta" in sys.argv or "--launch" in sys.argv
    edl = json.load(open(edl_path, encoding="utf-8"))
    caps = json.load(open(os.path.join(HERE, "video_captions.json"), encoding="utf-8"))[lang]
    if "--launch" in sys.argv: caps["cta"] = caps["cta_launch"]  # yayından sonra kullanılacak sürüm
    comp = Composer(fmt, lang, caps)
    music = os.path.join(os.path.dirname(out), "_music.wav")
    subprocess.run([sys.executable, os.path.join(HERE, "music.py"), music, str(edl["total"])], check=True, capture_output=True)
    enc = subprocess.Popen(["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{comp.W}x{comp.H}", "-r", str(FPS), "-i", "-",
                            "-i", music, "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p",
                            "-profile:v", "high", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", out], stdin=subprocess.PIPE)
    srt = []; t = 0.0; prev = None; XF = 6
    for seg in edl["segments"]:
        frames = read_segment(os.path.join(clips, seg["clip"] + ".mp4"), seg["start"], seg["dur"])
        text = caps["captions"].get(seg.get("caption", ""), "")
        if text: srt.append((t, t + seg["dur"], text))
        for i, fr in enumerate(frames):
            img = comp.frame(fr, text, seg.get("fit") == "full")
            if prev is not None and i < XF: img = Image.blend(prev, img, (i + 1) / (XF + 1))
            enc.stdin.write(img.tobytes())
            last = img
        prev = last; t += seg["dur"]
    n_end = int(round((edl["total"] - t) * FPS))
    for i in range(n_end):
        img = comp.end_card(i / FPS, cta)
        if i < XF: img = Image.blend(prev, img, (i + 1) / (XF + 1))
        enc.stdin.write(img.tobytes())
    srt.append((t, edl["total"], caps["tagline"] + (" " + caps["cta"] if cta else "")))
    enc.stdin.close(); enc.wait(); os.remove(music)
    with open(os.path.splitext(out)[0] + ".srt", "w", encoding="utf-8") as f:
        for i, (a, b, x) in enumerate(srt, 1): f.write(f"{i}\n{srt_time(a)} --> {srt_time(b)}\n{x}\n\n")
    print("tamam", out)

if __name__ == "__main__":
    main()
