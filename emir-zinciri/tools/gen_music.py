"""Menü ve oyun için sakin, döngüye uygun müzik sentezler (telifsiz). Kullanım: python3 tools/gen_music.py (ffmpeg gerekir)."""
import numpy as np, os, subprocess, tempfile, wave
RATE = 32000
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "music")
BPM = 96
BEAT = 60 / BPM

def note(f): return 440.0 * 2 ** ((f - 69) / 12)

def voice(freq, dur, kind="tri", vol=0.2, attack=0.01, release=0.25):
    t = np.arange(int(RATE * dur)) / RATE
    if kind == "tri": w = 2 / np.pi * np.arcsin(np.sin(2 * np.pi * freq * t))
    elif kind == "sq": w = np.sign(np.sin(2 * np.pi * freq * t)) * 0.5
    else: w = np.sin(2 * np.pi * freq * t)
    env = np.minimum(1, t / attack) * np.clip(1 - np.maximum(0, t - (dur - release)) / release, 0, 1)
    return w * env * vol

def track(chords, melody, bars, name):
    total = int(RATE * bars * 4 * BEAT)
    buf = np.zeros(total + RATE)
    def add(x, start):
        i = int(start * RATE); buf[i:i + len(x)] += x[: max(0, len(buf) - i)]
    for b in range(bars):
        root, kind = chords[b % len(chords)]
        tones = [root, root + (3 if kind == "m" else 4), root + 7]
        add(voice(note(root - 24), 4 * BEAT, "sine", 0.22, release=0.4), b * 4 * BEAT)          # bas
        for k in range(8):                                                                         # arpej
            add(voice(note(tones[k % 3] + (12 if k % 4 == 3 else 0)), BEAT / 2, "tri", 0.07, release=0.15), (b * 4 + k / 2) * BEAT)
        for k in range(4):                                                                         # hafif vuruş
            n = np.random.default_rng(b * 4 + k).standard_normal(int(RATE * 0.05)) * np.linspace(0.05, 0, int(RATE * 0.05))
            add(n, (b * 4 + k) * BEAT)
    for (start, length, pitch) in melody:
        add(voice(note(pitch), length * BEAT, "sq", 0.045, release=length * BEAT * 0.5), start * BEAT)
    buf = buf[:total]
    buf = buf / max(1e-6, np.max(np.abs(buf))) * 0.6
    # Döngü sınırında tık olmasın diye kısa yumuşatma
    fade = int(RATE * 0.02); buf[:fade] *= np.linspace(0, 1, fade); buf[-fade:] *= np.linspace(1, 0, fade)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "w") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
            w.writeframes((buf * 32767).astype("<i2").tobytes())
        os.makedirs(OUT, exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "2", os.path.join(OUT, name + ".ogg")], check=True)
        os.unlink(tmp.name)

C, F, G, Am, Dm, Em = (60, ""), (65, ""), (67, ""), (69, "m"), (62, "m"), (64, "m")
mel1 = [(b * 4 + o, l, p) for b, ph in enumerate([[(0, 1, 72), (1, 1, 76), (2, 2, 79)], [(0, 2, 77), (2, 2, 76)], [(0, 1, 74), (1, 1, 72), (2, 2, 71)], [(0, 4, 72)]] * 4) for (o, l, p) in ph]
track([C, Am, F, G], mel1, 16, "menu")
mel2 = [(b * 4 + o, l, p) for b, ph in enumerate([[(0, 2, 69), (2, 2, 72)], [(0, 1, 74), (1, 1, 72), (2, 2, 69)], [(0, 2, 67), (2, 2, 64)], [(0, 4, 69)]] * 4) for (o, l, p) in ph]
track([Am, F, C, G, Am, Dm, Em, Am], mel2, 16, "game")
print("ok")
