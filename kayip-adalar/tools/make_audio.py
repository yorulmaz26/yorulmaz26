# Basit ses efektleri ve döngüsel müzik sentezler (lisans gerektirmeyen, tamamen üretilmiş sesler).
import math, wave, struct, os, random
SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sfx")

def write(name, samples):
    with wave.open(os.path.join(OUT, name + ".wav"), "w") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 30000)) for s in samples))

def tone(freqs, dur, vol=0.5, shape="sine", attack=0.005, decay=None, slide=0.0):
    n = int(SR * dur); out = []
    for i in range(n):
        t = i / SR
        env = min(1, t / attack) * (math.exp(-t / decay) if decay else max(0, 1 - t / dur))
        s = 0
        for f in freqs:
            ff = f * (1 + slide * t / dur)
            ph = 2 * math.pi * ff * t
            s += math.sin(ph) if shape == "sine" else (1 if math.sin(ph) > 0 else -1) * 0.4 + math.sin(ph) * 0.6
        out.append(s / len(freqs) * env * vol)
    return out

def seq(parts):
    out = []
    for p in parts: out += p
    return out

write("tap", tone([880], 0.06, 0.35, decay=0.02))
write("slide", [s * 0.6 for s in tone([300], 0.16, 0.5, slide=0.8, decay=0.06)])
write("rotate", tone([520, 780], 0.12, 0.4, slide=0.5, decay=0.05))
write("bridge", seq([tone([660], 0.07, 0.45, decay=0.05), tone([990], 0.12, 0.45, decay=0.07)]))
write("combo", seq([tone([523, 659], 0.09, 0.45), tone([659, 784], 0.09, 0.45), tone([784, 1046], 0.25, 0.5, decay=0.12)]))
write("win", seq([tone([523], 0.12, 0.5), tone([659], 0.12, 0.5), tone([784], 0.12, 0.5), tone([1046, 784, 523], 0.5, 0.55, decay=0.3)]))
write("lose", seq([tone([440], 0.18, 0.45), tone([392], 0.18, 0.45), tone([330], 0.4, 0.45, decay=0.25)]))
write("coin", seq([tone([988], 0.06, 0.4, decay=0.04), tone([1319], 0.18, 0.4, decay=0.08)]))
random.seed(3)
write("hit", [ (random.uniform(-1, 1) * 0.5 + math.sin(2*math.pi*120*i/SR) * 0.5) * math.exp(-i / (SR * 0.04)) * 0.6 for i in range(int(SR * 0.15))])
write("error", tone([220, 233], 0.18, 0.35, shape="square", decay=0.1))
write("unlock", seq([tone([587], 0.08, 0.45), tone([880], 0.08, 0.45), tone([1175], 0.3, 0.45, decay=0.15)]))

# 16 saniyelik neşeli döngü: pentatonik arpej + yumuşak bas
notes = [523, 587, 659, 784, 880, 1046]
chords = [[262, 330, 392], [220, 262, 330], [175, 220, 262], [196, 247, 294]]
music = [0.0] * (SR * 16)
beat = SR // 4
for b in range(64):
    ch = chords[(b // 16) % 4]
    f = ch[b % 3] * 2 if b % 4 != 3 else notes[(b * 3) % len(notes)]
    st = b * beat
    for i in range(int(beat * 0.9)):
        t = i / SR
        if st + i < len(music):
            music[st + i] += math.sin(2 * math.pi * f * t) * math.exp(-t / 0.18) * 0.22
    if b % 4 == 0:
        for i in range(beat * 4):
            t = i / SR
            if st + i < len(music):
                music[st + i] += math.sin(2 * math.pi * ch[0] / 2 * t) * 0.12 * (1 - i / (beat * 4))
write("music", music)
print("ok")
