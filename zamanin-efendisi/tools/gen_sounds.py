import math, wave, struct, random, sys, os
out = sys.argv[1]
SR = 22050
def write(name, samples):
    with wave.open(os.path.join(out, name + ".wav"), "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in samples))
def tone(freqs, dur, shape="sine", vol=0.5, attack=0.005, decay=None, sweep=0.0):
    n = int(SR * dur); res = []
    ph = 0.0
    for i in range(n):
        t = i / SR
        f = freqs + sweep * t
        ph += 2 * math.pi * f / SR
        v = math.sin(ph) if shape == "sine" else (1 if math.sin(ph) > 0 else -1) * 0.5 if shape == "square" else (2 * ((ph / (2*math.pi)) % 1) - 1) * 0.6
        env = min(1, t / attack) * math.exp(-t * (decay if decay else 6 / dur))
        res.append(v * env * vol)
    return res
def seq(*parts):
    r = []
    for p in parts: r += p
    return r
def mix(a, b):
    n = max(len(a), len(b)); return [(a[i] if i < len(a) else 0) + (b[i] if i < len(b) else 0) for i in range(n)]
random.seed(3)
write("click", tone(880, 0.06, vol=0.35, decay=60))
write("move", tone(420, 0.08, vol=0.3, decay=40, sweep=600))
write("build", mix(tone(220, 0.18, "square", 0.25, decay=25), [random.uniform(-1,1)*0.25*math.exp(-i/600) for i in range(3000)]))
write("switch", mix(tone(300, 0.45, vol=0.35, decay=6, sweep=1400), tone(600, 0.45, vol=0.2, decay=6, sweep=2000)))
write("win", seq(tone(523, 0.12, vol=0.4, decay=12), tone(659, 0.12, vol=0.4, decay=12), tone(784, 0.12, vol=0.4, decay=12), tone(1046, 0.4, vol=0.45, decay=5)))
write("lose", seq(tone(392, 0.18, "square", 0.25, decay=10), tone(330, 0.18, "square", 0.25, decay=10), tone(262, 0.4, "square", 0.25, decay=5)))
write("hit", mix(tone(160, 0.15, "square", 0.3, decay=30, sweep=-300), [random.uniform(-1,1)*0.35*math.exp(-i/400) for i in range(2500)]))
write("paradox", mix(tone(700, 0.5, vol=0.3, decay=5, sweep=-900), tone(707, 0.5, vol=0.3, decay=5, sweep=-880)))
write("grow", seq(tone(660, 0.07, vol=0.3, decay=30), tone(880, 0.1, vol=0.3, decay=25)))
