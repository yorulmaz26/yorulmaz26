"""Ses efektleri ve harita müziklerini programatik olarak sentezler (lisans sorunu yok).
Kullanım: python3 tools/audio/gen_audio.py <assets_dir>"""
import os, sys, subprocess, wave, random
import numpy as np

SR = 22050
assets = sys.argv[1]
os.makedirs(os.path.join(assets, 'sfx'), exist_ok=True)
os.makedirs(os.path.join(assets, 'music'), exist_ok=True)
tmp = os.path.join(assets, '..', 'build', 'audio_tmp'); os.makedirs(tmp, exist_ok=True)

def t(d): return np.arange(int(SR * d)) / SR
def env(n, a=0.005, r=0.1, sus=1.0):
    e = np.ones(n) * sus
    na = max(1, int(a * SR)); nr = max(1, min(n, int(r * SR)))
    e[:na] = np.linspace(0, 1, na) * sus if sus else np.linspace(0, 1, na)
    e[-nr:] *= np.linspace(1, 0, nr)
    return e
def osc(kind, f, dur):
    tt = t(dur)
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(f, tt.shape)) / SR if np.ndim(f) else 2 * np.pi * f * tt
    if kind == 'sine': return np.sin(ph)
    if kind == 'tri': return 2 / np.pi * np.arcsin(np.sin(ph))
    if kind == 'square': return np.tanh(3 * np.sin(ph)) * 0.8
    if kind == 'saw': return 2 * ((ph / (2 * np.pi)) % 1) - 1
    if kind == 'bell': return np.sin(ph) * 0.7 + np.sin(ph * 2.01) * 0.2 * np.exp(-tt * 6) + np.sin(ph * 3) * 0.1 * np.exp(-tt * 9)
def sweep(f0, f1, dur): return np.linspace(f0, f1, int(SR * dur))
def noise(dur): return np.random.default_rng(1).uniform(-1, 1, int(SR * dur))

def save(name, x, folder='sfx', q=4):
    x = np.clip(x / max(1e-6, np.max(np.abs(x))) * 0.85, -1, 1)
    wav = os.path.join(tmp, name + '.wav')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    out = os.path.join(assets, folder, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', str(q), out], check=True)

def cat(*parts): return np.concatenate(parts)
def note(f, d, kind='square', a=0.005, r=0.08, vol=1.0):
    x = osc(kind, f, d); return x * env(len(x), a, r) * vol

# ---------------- efektler
save('jump', osc('square', sweep(330, 720, 0.16), 0.16) * env(int(SR * .16), .003, .1) * 0.6)
# yumuşak çan tınısı (kare dalga kulağı yoruyordu)
save('coin', cat(note(1319, .05, 'bell', r=.04), note(1760, .16, 'bell', r=.15)) * 0.5)
save('star', cat(*[note(f, .07, 'bell', r=.06) for f in (784, 988, 1175, 1568)], note(2093, .3, 'bell', r=.28)))
x = osc('sine', sweep(300, 1200, .35), .35) * env(int(SR * .35), .02, .2)
save('rule', x + cat(np.zeros(int(SR * .2)), note(1568, .15, 'bell', r=.14)) * 0.7)
save('warn', note(880, .08, 'sine', r=.06) * 0.8)
save('complete', cat(*[note(f, .12, 'square', r=.08) for f in (523, 659, 784)], note(1047, .5, 'square', r=.45)) +
     cat(*[note(f / 2, .12, 'tri', r=.08) for f in (523, 659, 784)], note(523, .5, 'tri', r=.45)))
save('fail', cat(note(440, .15, 'square', r=.1), note(370, .15, 'square', r=.1), osc('square', sweep(311, 180, .4), .4) * env(int(SR * .4), .005, .3)) * 0.7)
save('click', note(1200, .03, 'sine', r=.025) + note(600, .03, 'tri', r=.025) * .5)
save('reward', cat(*[note(f, .08, 'bell', r=.07) for f in (659, 784, 1047, 1319, 1568, 2093)]))
save('spring', osc('sine', sweep(200, 900, .25), .25) * env(int(SR * .25), .002, .2) * (1 + 0.3 * np.sin(t(.25) * 2 * np.pi * 30)))
save('powerup', cat(*[note(f, .06, 'square', r=.05) for f in (523, 784, 659, 1047, 784, 1319)]) * 0.6)
save('checkpoint', cat(note(784, .1, 'bell', r=.08), note(1175, .3, 'bell', r=.28)))
n = noise(.12); save('land', n * env(len(n), .001, .1) * np.exp(-t(.12) * 30) * 0.4 + osc('sine', sweep(160, 60, .12), .12) * env(int(SR * .12), .001, .1))
n = noise(.4); save('death', n * env(len(n), .002, .35) * np.exp(-t(.4) * 8) * 0.5 + osc('square', sweep(600, 120, .4), .4) * env(int(SR * .4), .002, .3) * .5)
save('boost', osc('saw', sweep(200, 600, .3), .3) * env(int(SR * .3), .01, .2) * 0.4)
save('shield', osc('sine', sweep(900, 400, .3), .3) * env(int(SR * .3), .002, .25) * (1 + 0.5 * np.sin(t(.3) * 2 * np.pi * 18)))

# ---------------- müzik
NOTES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
def midi_f(m): return 440 * 2 ** ((m - 69) / 12)
MAJ = [0, 2, 4, 5, 7, 9, 11]
def chord(root, quality):  # derece tabanlı
    return [root, root + (4 if quality == 'M' else 3), root + 7]

TRACKS = [
    # (bpm, tonik midi, ilerleme [(derece, nitelik)], melodi enstrümanı, bas, davul yoğunluğu, ad)
    (112, 60, [(0, 'M'), (7, 'M'), (9, 'm'), (5, 'M')], 'square', 'tri', 1, 'map_01'),
    (96, 65, [(0, 'M'), (9, 'm'), (5, 'M'), (7, 'M')], 'bell', 'sine', 0, 'map_02'),
    (138, 62, [(0, 'm'), (10, 'M'), (8, 'M'), (7, 'M')], 'square', 'tri', 2, 'map_03'),
    (100, 67, [(0, 'M'), (4, 'm'), (5, 'M'), (7, 'M')], 'bell', 'tri', 0, 'map_04'),
    (104, 64, [(0, 'm'), (8, 'M'), (3, 'M'), (10, 'M')], 'tri', 'sine', 1, 'map_05'),
    (126, 60, [(0, 'M'), (5, 'M'), (7, 'M'), (5, 'M')], 'square', 'tri', 2, 'map_06'),
    (118, 69, [(0, 'M'), (7, 'M'), (5, 'M'), (7, 'M')], 'bell', 'tri', 1, 'map_07'),
    (108, 63, [(0, 'M'), (9, 'm'), (4, 'm'), (5, 'M')], 'bell', 'sine', 1, 'map_08'),
    (92, 62, [(0, 'M'), (5, 'M'), (9, 'm'), (7, 'M')], 'tri', 'tri', 1, 'map_09'),
    (132, 64, [(0, 'M'), (7, 'M'), (9, 'm'), (5, 'M')], 'square', 'tri', 2, 'map_10'),
    (120, 60, [(0, 'M'), (9, 'm'), (5, 'M'), (7, 'M')], 'bell', 'tri', 1, 'menu'),
    (144, 62, [(0, 'm'), (10, 'M'), (8, 'M'), (7, 'M')], 'square', 'tri', 2, 'endless'),
]

for bpm, tonic, prog, lead, bassk, drums, name in TRACKS:
    rng = random.Random(name)
    beat = 60 / bpm
    bars = 8
    total = bars * 4 * beat
    N = int(SR * total) + SR
    mix = np.zeros(N)
    def add(x, start, vol=1.0):
        i = int(start * SR); j = min(N, i + len(x)); mix[i:j] += x[:j - i] * vol
    penta = [0, 2, 4, 7, 9]
    # motif: 2 ölçülük, tekrar edilerek varyasyonlanır
    motif = []
    for s in range(8):
        motif.append(rng.choice([0, 1, 2, 3, 4, 5, 7]) if rng.random() < 0.8 else None)
    deg = 2
    for bar in range(bars):
        root, q = prog[bar % len(prog)]
        ch = chord(root, q)
        bt = bar * 4 * beat
        # bas
        for b in range(4):
            m = tonic - 24 + ch[0] + (7 if b == 2 else 0)
            add(note(midi_f(m), beat * 0.9, bassk, a=.01, r=.12), bt + b * beat, 0.55)
        # akor pad (arpej)
        for s in range(8):
            m = tonic + ch[s % 3] + (12 if s % 4 == 3 else 0)
            add(note(midi_f(m), beat * 0.45, 'tri', a=.01, r=.15), bt + s * beat / 2, 0.16)
        # melodi
        for s in range(8):
            if motif[s] is None and rng.random() < 0.6: continue
            if s % 2 == 0:
                target = ch[rng.randrange(3)] % 12
                choices = [p for p in penta + [12, 14, 16] if p % 12 == target] or penta
                m = tonic + 12 + rng.choice(choices)
            else:
                deg = max(0, min(7, deg + rng.choice([-1, 1, 1, 2, -2])))
                m = tonic + 12 + (penta + [12, 14, 16])[deg]
            dur = beat * (0.48 if rng.random() < 0.75 else 0.95)
            add(note(midi_f(m), dur, lead, a=.006, r=.1), bt + s * beat / 2, 0.32 if lead != 'bell' else 0.42)
        # davul
        if drums:
            for b in range(4):
                k = osc('sine', sweep(130, 45, .15), .15) * env(int(SR * .15), .001, .12)
                if b % 2 == 0 or drums > 1: add(k, bt + b * beat, 0.7)
                hh = noise(.04) * env(int(SR * .04), .001, .035)
                add(hh, bt + b * beat + beat / 2, 0.12)
                if b % 2 == 1:
                    sn = noise(.12) * env(int(SR * .12), .001, .1) * np.exp(-t(.12) * 25)
                    add(sn, bt + b * beat, 0.3)
    loop = mix[:int(SR * total)]
    # döngü noktası: kuyruğu başa karıştır
    tail = mix[int(SR * total):int(SR * total) + SR // 2]
    loop[:len(tail)] += tail
    save(name, loop, 'music', q=3)
    print('müzik', name, '%.1f sn' % total)
print('bitti')
