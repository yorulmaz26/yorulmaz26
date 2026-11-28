#!/usr/bin/env python3
"""
Işık Yolu — Play Store malzemesi: başlıklı ekran görüntüleri, öne çıkan görsel ve 30/60 sn tanıtım videoları.

Girdi: PromoCaptureTest'in kayıt klasörü (clips/*/NNNNN.jpg + events.json, shots/*.png, icon/icon_512.png).
Reklam yoktur: kayıt reklam sağlayıcısı kapalıyken yapılır.

Kullanım:
  python3 tools/magaza/magaza.py --promo KAYIT --fonts ROBOTO_KLASORU --sounds app/src/main/res/raw --out magaza
"""
import argparse
import json
import math
import os
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FPS = 30
TOP, BOTTOM = (0x3D, 0x4D, 0x92), (0x21, 0x30, 0x62)
GOLD, TURQ, PURPLE, WHITE, DIM = (0xF5, 0xC5, 0x42), (0x20, 0xB8, 0xA6), (0x6D, 0x5D, 0xFB), (255, 255, 255), (0xC6, 0xD1, 0xE8)

# ---------------------------------------------------------------------------------------------------------------
# yardımcılar


def font(dirpath, name, size):
    return ImageFont.truetype(os.path.join(dirpath, name), size)


def background(w, h, seed=0):
    """Oyunun arka plan geçişi + yumuşak ışık lekeleri (turkuaz, altın, mor)."""
    y = np.linspace(0, 1, h)[:, None]
    top, bot = np.array(TOP, float), np.array(BOTTOM, float)
    img = (top[None, None, :] * (1 - y[..., None]) + bot[None, None, :] * y[..., None]) * np.ones((h, w, 1))
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    for (cx, cy, r, col, a) in [(0.12, 0.18, 0.55, TURQ, 0.22), (0.88, 0.85, 0.6, GOLD, 0.16), (0.75, 0.1, 0.5, PURPLE, 0.20)]:
        d = np.sqrt(((xx / w - cx) * w / max(w, h)) ** 2 + ((yy / h - cy) * h / max(w, h)) ** 2) / r
        g = np.clip(1 - d, 0, 1) ** 2 * a
        img = img * (1 - g[..., None]) + np.array(col, float)[None, None, :] * g[..., None]
    rng = np.random.default_rng(seed)  # küçük yıldız noktaları
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(out, "RGBA")
    for _ in range(int(w * h / 9000)):
        x, yv, s = rng.uniform(0, w), rng.uniform(0, h), rng.uniform(1, 2.6)
        d.ellipse([x - s, yv - s, x + s, yv + s], fill=(255, 255, 255, int(rng.uniform(40, 120))))
    return out


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def phone_layer(canvas_size, box, radius):
    """Telefon gövdesi (gölge + koyu çerçeve + ince kenar). box = ekranın yeri (x, y, w, h)."""
    # Play önerisi: cihaz görüntüsü yok. Yalnızca ekranın altında yumuşak gölge ve ince açık kenar.
    x, y, w, h = box
    layer = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    sh = Image.new("RGBA", canvas_size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([x + 6, y + 18, x + w + 6, y + h + 18], radius=radius, fill=(5, 8, 24, 140))
    layer = Image.alpha_composite(layer, sh.filter(ImageFilter.GaussianBlur(24)))
    ImageDraw.Draw(layer).rounded_rectangle([x - 3, y - 3, x + w + 3, y + h + 3], radius=radius + 3, outline=(0x7C, 0x8F, 0xBE, 200), width=3)
    return layer


def paste_screen(canvas, img, box, radius):
    x, y, w, h = box
    scr = img.convert("RGB").resize((w, h), Image.LANCZOS)
    canvas.paste(scr, (x, y), rounded_mask((w, h), radius))


def text_center(draw, cx, y, s, f, fill):
    l, t, r, b = draw.textbbox((0, 0), s, font=f)
    draw.text((cx - (r - l) / 2 - l, y), s, font=f, fill=fill)
    return b - t


def wrap(draw, s, f, maxw):
    words, lines, cur = s.split(), [], ""
    for wd in words:
        trial = (cur + " " + wd).strip()
        if draw.textlength(trial, font=f) <= maxw: cur = trial
        else: lines.append(cur); cur = wd
    if cur: lines.append(cur)
    return lines


# ---------------------------------------------------------------------------------------------------------------
# ekran görüntüleri

SHOTS = [
    ("01_mantik", "Işığı yönlendir", "Aynaları döndür, bütün hedefleri yak"),
    ("02_prizma", "Prizmalar ve renkler", "Beyaz ışığı renklerine ayır"),
    ("03_yildizlar", "Yıldızları topla", "En az hamleyle bitir, 3 yıldız kazan"),
    ("04_dunyalar", "5 dünya, 500 bölüm", "Kolaydan ustalığa adım adım"),
    ("05_kapilar", "Anahtarlar ve kapılar", "Anahtarı ışıkla yak, kapı açılsın"),
    ("06_bloklar", "Taşları kaydır", "Işığa yol açmak için blokları it"),
    ("07_gunluk", "Her gün yeni bulmaca", "Seriyi sürdür, ödülleri topla"),
    ("08_gorunumler", "Kendi tarzın", "Ayna, tahta ve ışın görünümleri"),
]


def screenshots(promo, fonts, out):
    os.makedirs(os.path.join(out, "ekran-goruntuleri"), exist_ok=True)
    os.makedirs(os.path.join(out, "ekran-goruntuleri-yalin"), exist_ok=True)
    W, H = 1080, 1920
    bg = background(W, H, 7)
    ft, fs = font(fonts, "Roboto-Black.ttf", 76), font(fonts, "Roboto-Medium.ttf", 38)
    head = 330
    sh = H - head - 60
    sw = int(sh * 1080 / 1920)
    box = ((W - sw) // 2, head, sw, sh)
    phone = phone_layer((W, H), box, 46)
    for i, (name, title, sub) in enumerate(SHOTS, 1):
        src = Image.open(os.path.join(promo, "shots", name + ".png")).convert("RGB")
        src.save(os.path.join(out, "ekran-goruntuleri-yalin", f"{i:02d}_{name[3:]}.png"))
        c = bg.copy().convert("RGBA")
        c = Image.alpha_composite(c, phone).convert("RGB")
        paste_screen(c, src, box, 40)
        d = ImageDraw.Draw(c)
        asc, desc = ft.getmetrics()
        text_center(d, W / 2, 70, title, ft, WHITE)
        line_y = 70 + asc + desc + 12  # her görselde aynı yükseklik
        d.rounded_rectangle([W / 2 - 60, line_y, W / 2 + 60, line_y + 7], radius=4, fill=GOLD)
        text_center(d, W / 2, line_y + 30, sub, fs, DIM)
        c.save(os.path.join(out, "ekran-goruntuleri", f"{i:02d}_{name[3:]}.png"))
    print("ekran görüntüleri:", len(SHOTS))


# ---------------------------------------------------------------------------------------------------------------
# öne çıkan görsel ve simge

def feature_graphic(promo, fonts, out):
    W, H = 1024, 500
    c = background(W, H, 3).convert("RGBA")
    icon = Image.open(os.path.join(promo, "icon", "icon_512.png")).convert("RGBA").resize((150, 150), Image.LANCZOS)
    m = rounded_mask((150, 150), 34)
    c.paste(icon, (70, 120), m)
    d = ImageDraw.Draw(c)
    d.text((70, 290), "Işık Yolu", font=font(fonts, "Roboto-Black.ttf", 84), fill=WHITE)
    d.text((74, 392), "Işığı yönlendir. Bulmacayı çöz.", font=font(fonts, "Roboto-Medium.ttf", 30), fill=DIM)
    # sağda oyun ekranı (üst kısmı), eğik değil: telefon çerçevesi içinde
    shot = Image.open(os.path.join(promo, "shots", "01_mantik.png")).convert("RGB")
    sw, shh = 330, int(330 * 1920 / 1080)
    box = (W - sw - 70, 60, sw, shh)
    c = Image.alpha_composite(c, phone_layer((W, H), box, 30))
    paste_screen(c, shot, box, 26)
    c = c.convert("RGB")
    c.save(os.path.join(out, "one-cikan-gorsel_1024x500.png"))
    Image.open(os.path.join(promo, "icon", "icon_512.png")).convert("RGBA").save(os.path.join(out, "simge_512.png"))  # Play: 32 bit PNG
    print("öne çıkan görsel ve simge")


# ---------------------------------------------------------------------------------------------------------------
# video

class Clip:
    def __init__(self, promo, name):
        self.dir = os.path.join(promo, "clips", name)
        meta = json.load(open(os.path.join(self.dir, "events.json"), encoding="utf-8"))
        self.frames, self.events, self.w, self.h = meta["frames"], meta["events"], meta["w"], meta["h"]

    def win_frame(self):
        w = [e["f"] for e in self.events if e["k"] == "win"]
        return w[0] if w else None

    def frame(self, i):
        i = max(0, min(self.frames - 1, i))
        return Image.open(os.path.join(self.dir, f"{i:05d}.jpg"))


class Video:
    W, H = 1920, 1080

    def __init__(self, promo, fonts, sounds):
        self.promo, self.fonts, self.sounds = promo, fonts, sounds
        self.bg = background(self.W, self.H, 11)
        sh = 960
        sw = int(sh * 1080 / 1920)
        self.box = (self.W - sw - 230, (self.H - sh) // 2, sw, sh)
        self.base = Image.alpha_composite(self.bg.convert("RGBA"), phone_layer((self.W, self.H), self.box, 44))
        self.icon = Image.open(os.path.join(promo, "icon", "icon_512.png")).convert("RGBA")
        self.ft, self.fs = font(fonts, "Roboto-Black.ttf", 92), font(fonts, "Roboto-Medium.ttf", 46)
        self.fw = font(fonts, "Roboto-Bold.ttf", 40)
        # küçük marka (sol üst)
        ic = self.icon.resize((84, 84), Image.LANCZOS)
        d = ImageDraw.Draw(self.base)
        self.base.paste(ic, (130, 96), rounded_mask((84, 84), 20))
        d.text((236, 112), "Işık Yolu", font=self.fw, fill=WHITE)

    def caption_layer(self, title, sub):
        layer = self.base.copy()
        d = ImageDraw.Draw(layer)
        x0, maxw = 130, self.box[0] - 130 - 110
        tl = wrap(d, title, self.ft, maxw)
        sl = wrap(d, sub, self.fs, maxw)
        total = len(tl) * 108 + 40 + len(sl) * 62
        y = (self.H - total) // 2
        for ln in tl:
            d.text((x0, y), ln, font=self.ft, fill=WHITE); y += 108
        d.rounded_rectangle([x0, y + 4, x0 + 120, y + 12], radius=4, fill=GOLD); y += 40
        for ln in sl:
            d.text((x0, y), ln, font=self.fs, fill=DIM); y += 62
        return layer

    def clip_frames(self, clip, title, sub, start=0, end=None):
        """Bir klibin karelerini (telefon içinde) başlıkla üretir; dokunuşlar halka olarak çizilir."""
        layer = self.caption_layer(title, sub)
        end = clip.frames if end is None else min(end, clip.frames)
        x, y, w, h = self.box
        sx, sy = w / clip.w, h / clip.h
        mask = rounded_mask((w, h), 40)
        taps = [e for e in clip.events if e["k"] in ("tap", "drag")]
        for i in range(start, end):
            c = layer.copy()
            scr = clip.frame(i).convert("RGB").resize((w, h), Image.LANCZOS)
            c.paste(scr, (x, y), mask)
            ov = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
            d = ImageDraw.Draw(ov)
            for e in taps:
                t = i - e["f"]
                if -5 <= t <= 10:
                    p = (t + 5) / 15.0
                    if e["k"] == "drag":
                        q = min(1.0, max(0.0, (t + 2) / 8.0))
                        px, py = e["x"] + (e["x2"] - e["x"]) * q, e["y"] + (e["y2"] - e["y"]) * q
                    else:
                        px, py = e["x"], e["y"]
                    vx, vy = x + px * sx, y + py * sy
                    r = 22 + 26 * p
                    a = int(230 * (1 - p))
                    d.ellipse([vx - r, vy - r, vx + r, vy + r], outline=(255, 255, 255, a), width=5)
                    if t <= 2:
                        d.ellipse([vx - 16, vy - 16, vx + 16, vy + 16], fill=(255, 255, 255, int(150 * (1 - p))))
            c = Image.alpha_composite(c, ov)
            yield c.convert("RGB")

    def card_frames(self, seconds, lines, end_card=False):
        """Başlık / kapanış kartı: simge + ad + slogan, hafif büyüyerek belirir."""
        n = int(round(seconds * FPS))
        base = self.bg.copy().convert("RGBA")
        for i in range(n):
            t = min(1.0, i / (0.7 * FPS))
            ease = 1 - (1 - t) ** 3
            c = base.copy()
            d = ImageDraw.Draw(c)
            size = int(230 * (0.85 + 0.15 * ease))
            ic = self.icon.resize((size, size), Image.LANCZOS)
            glow = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
            gd = ImageDraw.Draw(glow)
            gx, gy = self.W // 2, 300
            gd.ellipse([gx - 260, gy - 260, gx + 260, gy + 260], fill=(0xF5, 0xC5, 0x42, int(70 * ease)))
            c = Image.alpha_composite(c, glow.filter(ImageFilter.GaussianBlur(70)))
            c.paste(ic, (gx - size // 2, gy - size // 2), rounded_mask((size, size), size // 4))
            d = ImageDraw.Draw(c)
            y = 470
            for k, (s, f, col) in enumerate(lines):
                a = min(1.0, max(0.0, (i - 6 - 5 * k) / 12.0))
                col2 = tuple(int(v * a + bv * (1 - a)) for v, bv in zip(col, (0x30, 0x42, 0x7E)))
                y += text_center(d, self.W / 2, y, s, f, col2) + (34 if k == 0 else 24)
            if end_card and i > 18:
                a = min(1.0, (i - 18) / 14.0)
                pill = Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))
                pd = ImageDraw.Draw(pill)
                # Play görsel kuralları: "hemen indir" gibi eylem çağrısı yok; yalnızca slogan
                pd.rounded_rectangle([self.W / 2 - 70, y + 34, self.W / 2 + 70, y + 42], radius=4, fill=GOLD + (int(255 * a),))
                text_center(pd, self.W / 2, y + 66, "Işığı yönlendir. Bulmacayı çöz.", font(self.fonts, "Roboto-Bold.ttf", 46), TURQ + (int(255 * a),))
                c = Image.alpha_composite(c, pill)
            yield c.convert("RGB")


def render(video, segments, path_mp4, sounds, total_seconds, xfade=10):
    """segments: [(kare üreteci fabrikası, ses olayları [(kare, ad)])]. Bölümler arası xfade karelik geçiş.
    Kareler akış hâlinde yazılır (bellekte yalnızca geçiş kadar kare tutulur)."""
    from collections import deque
    total = int(round(total_seconds * FPS))
    tmp_audio, raw = path_mp4 + ".wav", path_mp4 + ".rgb"
    sfx, count, tail, last = [], 0, [], None
    with open(raw, "wb") as f:
        def put(fr):
            nonlocal count, last
            f.write(fr.tobytes()); count += 1; last = fr
        for si, (gen, events) in enumerate(segments):
            offset = count  # önceki bölümün kuyruğu henüz yazılmadı: bu bölümün ilk karesi (karışmış hâliyle) 'count' konumuna yazılır
            for (fi, name) in events:
                sfx.append((offset + fi, name))
            buf = deque()
            for k, fr in enumerate(gen()):
                if k < len(tail):  # geçiş: önceki bölümün son kareleriyle karıştır
                    fr = Image.blend(tail[k], fr, (k + 1) / (len(tail) + 1))
                buf.append(fr)
                if len(buf) > xfade:
                    put(buf.popleft())
            if si < len(segments) - 1:
                tail = list(buf)
            else:
                for fr in buf: put(fr)
                tail = []
        while count < total and last is not None:  # süreyi tam tutturmak için son kare
            put(last)
    if count > total:
        os.remove(raw)
        raise SystemExit(f"{path_mp4}: {count} kare > {total}; bölümleri kısalt")
    mix_audio(tmp_audio, sounds, total / FPS, sfx)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{Video.W}x{Video.H}", "-r", str(FPS),
                    "-i", raw, "-i", tmp_audio, "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", f"{total / FPS:.3f}", path_mp4], check=True)
    os.remove(raw)
    os.remove(tmp_audio)
    print(path_mp4, f"{count / FPS:.2f} sn ({count} kare)")


def read_wav(path):
    with wave.open(path, "rb") as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        data = np.frombuffer(w.readframes(n), dtype=np.int16 if sw == 2 else np.uint8).astype(np.float32)
        if sw == 1: data = (data - 128) * 256
        data = data.reshape(-1, ch)
        if ch == 1: data = np.repeat(data, 2, axis=1)
        if sr != 44100:
            idx = np.linspace(0, len(data) - 1, int(len(data) * 44100 / sr))
            data = np.stack([np.interp(idx, np.arange(len(data)), data[:, c]) for c in range(2)], axis=1)
        return data / 32768.0


def sound_path(sounds, name):
    """res/raw sesi: WAV ise doğrudan, OGG ise (müzik, APK boyutu için) geçici WAV'a çözülür."""
    wav = os.path.join(sounds, name + ".wav")
    if os.path.exists(wav): return wav
    ogg = os.path.join(sounds, name + ".ogg")
    out = os.path.join(tempfile.gettempdir(), f"magaza_{name}_{os.getpid()}.wav")
    if not os.path.exists(out):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", ogg, "-ac", "1", out], check=True)
    return out


def mix_audio(path, sounds, seconds, sfx):
    sr = 44100
    n = int(seconds * sr)
    music = read_wav(sound_path(sounds, "music"))
    reps = int(math.ceil(n / len(music)))
    mix = np.tile(music, (reps, 1))[:n] * 0.55
    fade_in, fade_out = int(0.6 * sr), int(1.8 * sr)
    mix[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    mix[-fade_out:] *= np.linspace(1, 0, fade_out)[:, None]
    cache = {}
    vol = {"rotate": 0.5, "push": 0.6, "win": 0.75, "intro": 0.6, "star": 0.5}
    for (fi, name) in sfx:
        if name not in cache: cache[name] = read_wav(os.path.join(sounds, name + ".wav"))
        s = cache[name] * vol.get(name, 0.5)
        at = int(fi / FPS * sr)
        if at >= n: continue
        k = min(len(s), n - at)
        mix[at:at + k] += s[:k]
    peak = np.max(np.abs(mix))
    if peak > 0.98: mix *= 0.98 / peak
    pcm = (mix * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def clip_events(clip, start=0, end=None):
    end = clip.frames if end is None else end
    m = {"rotate": "rotate", "push": "push", "win": "win"}
    return [(e["f"] - start, m[e["k"]]) for e in clip.events if e["k"] in m and start <= e["f"] < end]


def videos(promo, fonts, sounds, out, xfade=10):
    v = Video(promo, fonts, sounds)
    names = ["menu", "a_temel", "b_prizma", "c_renk", "d_blok", "e_kapi", "f_mantik", "x_usta", "z_blok2", "y_karma",
             "g_gorunum", "g2_gorunum", "h_bolumler", "i_gunluk"]
    C = {n: Clip(promo, n) for n in names}
    title = [("Işık Yolu", font(fonts, "Roboto-Black.ttf", 140), WHITE), ("Işığı yönlendir. Bulmacayı çöz.", font(fonts, "Roboto-Medium.ttf", 54), DIM)]
    endc = [("Işık Yolu", font(fonts, "Roboto-Black.ttf", 130), WHITE),
            ("500 bölüm · 5 dünya · internetsiz oyna", font(fonts, "Roboto-Medium.ttf", 50), DIM)]
    AFTER_WIN = 20  # hedefler yandıktan sonra ~0,7 sn kutlama; 'Kusursuz!' penceresi win+22'de açılır

    def clip(name, t, s, start=0, cut_end=0):
        c = C[name]
        e = c.frames - cut_end
        return (e - start, lambda: v.clip_frames(c, t, s, start, e), clip_events(c, start, e))

    def play(name, t, s, start=0):
        """Bölüm klibi: çözüm biter bitmez (pencere açılmadan) kesilir."""
        c = C[name]
        e = min(c.frames, c.win_frame() + AFTER_WIN)
        return (e - start, lambda: v.clip_frames(c, t, s, start, e), clip_events(c, start, e))

    def stars(name, t, s):
        """Tamamlandı penceresi bir kez, üç yıldız dolana dek."""
        c = C[name]
        st = c.win_frame() + 18
        return (c.frames - st, lambda: v.clip_frames(c, t, s, st, c.frames), clip_events(c, st, c.frames) + [(5, "star"), (14, "star"), (23, "star")])  # yıldızlar win+23/+32/+41

    def card(sec, lines, end_card=False, sound=None):
        n = int(round(sec * FPS))
        return (n, lambda: v.card_frames(n / FPS, lines, end_card), [(0, sound)] if sound else [])

    def build(specs, seconds, path):
        """Kapanış kartının süresi, video tam istenen süre olacak biçimde hesaplanır."""
        total = int(round(seconds * FPS))
        n = len(specs) + 1
        used = sum(sp[0] for sp in specs) - xfade * (n - 1)
        end_len = total - used
        if end_len < int(3.0 * FPS) or end_len > int(5.5 * FPS):
            raise SystemExit(f"{path}: kapanış kartı {end_len / FPS:.1f} sn olurdu; bölüm seçimini ayarla")
        segs = [(gen, ev) for (_, gen, ev) in specs] + [card(end_len / FPS, endc, end_card=True)[1:]]
        render(v, segs, path, sounds, seconds, xfade)

    os.makedirs(out, exist_ok=True)
    s60 = [
        card(2.6, title, sound="intro"),
        play("a_temel", "Aynaları döndür", "Işığı hedefe ulaştır"),
        play("b_prizma", "Prizmalar", "Beyaz ışık renklerine ayrılır"),
        play("c_renk", "Renkleri karıştır", "Kırmızı ile yeşil sarıyı yakar"),
        stars("c_renk", "Yıldızları topla", "En az hamleyle bitir, 3 yıldız kazan"),
        play("d_blok", "Taşları kaydır", "Işığa yol aç"),
        play("e_kapi", "Anahtarlar ve kapılar", "Anahtarı ışıkla yak, kapı açılsın"),
        play("f_mantik", "Mantık bölümleri", "Önce düşün, sonra dokun"),
        play("x_usta", "Usta bölümleri", "Prizma, süzgeç ve kesişen ışınlar"),
        play("z_blok2", "Her bölüm yeni bir bulmaca", "Bloklar, aynalar ve renkler bir arada"),
        play("y_karma", "Hepsi bir arada", "Prizma, süzgeç ve taşlar aynı bölümde"),
        play("g_gorunum", "Kendi tarzın", "Ayna, tahta ve ışın görünümleri"),
        play("g2_gorunum", "Kendi tarzın", "Ayna, tahta ve ışın görünümleri", 6),
        clip("h_bolumler", "5 dünya, 500 bölüm", "Kolaydan ustalığa"),
        clip("i_gunluk", "Her gün yeni bulmaca", "Seriyi sürdür, ödülleri topla"),
        clip("menu", "İnternetsiz oyna", "Her yerde, her an"),
    ]
    build(s60, 60.0, os.path.join(out, "tanitim-60sn.mp4"))
    s30 = [
        card(2.2, title, sound="intro"),
        play("a_temel", "Aynaları döndür", "Işığı hedefe ulaştır", 6),
        play("b_prizma", "Prizmalar ve renkler", "Beyaz ışık renklerine ayrılır", 6),
        play("c_renk", "Renkleri karıştır", "Kırmızı ile yeşil sarıyı yakar", 6),
        stars("c_renk", "Yıldızları topla", "En az hamleyle bitir, 3 yıldız kazan"),
        play("d_blok", "Taşları kaydır", "Işığa yol aç", 6),
        play("e_kapi", "Anahtarlar ve kapılar", "Anahtarı ışıkla yak, kapı açılsın", 6),
        play("f_mantik", "Mantık bölümleri", "Önce düşün, sonra dokun", 6),
        clip("h_bolumler", "5 dünya, 500 bölüm", "Kolaydan ustalığa", 10, 10),
    ]
    build(s30, 30.0, os.path.join(out, "tanitim-30sn.mp4"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--promo", required=True)
    ap.add_argument("--fonts", required=True)
    ap.add_argument("--sounds", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--only", default="all", choices=["all", "shots", "video"])
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    if a.only in ("all", "shots"):
        screenshots(a.promo, a.fonts, a.out)
        feature_graphic(a.promo, a.fonts, a.out)
    if a.only in ("all", "video"):
        videos(a.promo, a.fonts, a.sounds, os.path.join(a.out, "video"))


if __name__ == "__main__":
    main()
