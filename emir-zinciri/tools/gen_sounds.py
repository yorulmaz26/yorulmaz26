"""Oyun ses efektlerini sentezler (telifsiz, yeniden üretilebilir). Kullanım: python3 tools/gen_sounds.py"""
import math, os, random, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sounds")
random.seed(7)

def env(i, n, a=0.01, r=0.6):
    t = i / RATE; dur = n / RATE
    att = min(1.0, t / a) if a > 0 else 1.0
    rel = max(0.0, 1.0 - max(0.0, t - (dur - dur * r)) / (dur * r)) if r > 0 else 1.0
    return att * rel

def tone(freqs, dur, wave_="sine", vol=0.5, a=0.005, r=0.7, slide=0.0, noise=0.0):
    n = int(RATE * dur); out = []
    ph = 0.0
    for i in range(n):
        k = i / n
        f = freqs[min(int(k * len(freqs)), len(freqs) - 1)] * (1 + slide * k)
        ph += 2 * math.pi * f / RATE
        if wave_ == "sine": s = math.sin(ph)
        elif wave_ == "square": s = 1.0 if math.sin(ph) >= 0 else -1.0
        elif wave_ == "tri": s = 2 / math.pi * math.asin(math.sin(ph))
        else: s = math.sin(ph)
        s = s * (1 - noise) + (random.random() * 2 - 1) * noise
        out.append(s * vol * env(i, n, a, r))
    return out

def mix(*parts):
    n = max(len(p) for p in parts)
    return [sum(p[i] for p in parts if i < len(p)) for i in range(n)]

def seq(*parts):
    out = []
    for p in parts: out += p
    return out

def save(name, data):
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in data))

save("click", tone([1200], 0.05, "sine", 0.35, r=0.9))
save("card", tone([660, 990], 0.09, "tri", 0.4, r=0.8))
save("move", tone([220], 0.11, "tri", 0.35, slide=0.3, noise=0.15))
save("turn", tone([330, 440], 0.09, "sine", 0.3))
save("jump", tone([300], 0.22, "tri", 0.4, slide=1.2))
save("bump", tone([140], 0.16, "square", 0.25, slide=-0.4, noise=0.3))
save("pick", tone([520, 780], 0.12, "tri", 0.35))
save("drop", tone([520, 300], 0.13, "tri", 0.35))
save("push", tone([160], 0.16, "tri", 0.4, noise=0.35))
save("press", tone([880], 0.07, "square", 0.2, r=0.9))
save("door", mix(tone([180], 0.3, "tri", 0.3, slide=0.6, noise=0.2), tone([90], 0.3, "sine", 0.2)))
save("energy", seq(tone([660], 0.06, "sine", 0.35), tone([880], 0.06, "sine", 0.35), tone([1320], 0.1, "sine", 0.35)))
save("signal", mix(tone([1000, 1500, 1000], 0.18, "sine", 0.3)))
save("fall", tone([500], 0.45, "tri", 0.4, slide=-0.8))
save("star", tone([1046, 1568], 0.18, "tri", 0.35, r=0.8))
save("win", seq(tone([523], 0.1, "tri", 0.4), tone([659], 0.1, "tri", 0.4), tone([784], 0.1, "tri", 0.4), tone([1046], 0.35, "tri", 0.45)))
save("fail", seq(tone([392], 0.14, "tri", 0.4), tone([330], 0.14, "tri", 0.4), tone([262], 0.32, "tri", 0.4, slide=-0.1)))
save("hit", mix(tone([220], 0.12, "square", 0.25, slide=-0.5, noise=0.4)))
save("hurt", tone([300, 180], 0.18, "square", 0.25, noise=0.25))
save("shield", tone([700, 900], 0.16, "sine", 0.3, slide=0.2))
save("defeat", seq(tone([440], 0.08, "square", 0.25), tone([220], 0.08, "square", 0.25), tone([110], 0.2, "tri", 0.35, noise=0.3)))
print("ok")
