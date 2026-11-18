#!/usr/bin/env python3
"""
Procedural audio generator for "Yanlis Renk".

Every sound effect and music loop shipped in android/assets/{sfx,music} is
synthesized from scratch by this script (numpy only, no samples). Output is
written as 16-bit WAV into a temporary directory, then encoded to OGG Vorbis
with ffmpeg.

Usage:
    python3 -I tools/audio/gen_audio.py [WAV_TMP_DIR]

Design notes
  * Tones: sine / band-limited triangle / soft saw with quick attack and
    exponential decay, light detuned chorus, gentle FFT low-pass.
  * SFX: 44.1 kHz mono, peak-normalized to -3 dBFS, short fades.
  * Music: 44.1 kHz stereo, exact whole number of bars at a fixed tempo.
    Notes that ring past the loop end are folded back onto the start, and
    reverb / delay are applied as *circular* convolutions, so the file is
    perfectly periodic and loops without a click.
"""
import math
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
PEAK = 10 ** (-3 / 20)  # -3 dBFS

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(os.path.dirname(HERE))
SFX_DIR = os.path.join(PROJECT, "android", "assets", "sfx")
MUSIC_DIR = os.path.join(PROJECT, "android", "assets", "music")

TWO_PI = 2 * np.pi


# --------------------------------------------------------------------------
# basic helpers
# --------------------------------------------------------------------------
def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def tarr(n):
    return np.arange(n) / SR


def nsamp(sec):
    return max(1, int(round(sec * SR)))


def phase_of(freq):
    """freq: scalar or per-sample array -> running phase (radians)."""
    if np.isscalar(freq):
        return None
    return TWO_PI * np.cumsum(freq) / SR


def additive(f0, n, partials, freq_curve=None):
    """partials: list of (ratio, amp, tau or None)."""
    t = tarr(n)
    if freq_curve is None:
        ph = TWO_PI * f0 * t
    else:
        ph = TWO_PI * np.cumsum(freq_curve) / SR
    y = np.zeros(n)
    for ratio, amp, tau in partials:
        if f0 * ratio >= 16000:
            continue
        p = np.sin(ph * ratio)
        if tau is not None:
            p = p * np.exp(-t / tau)
        y += amp * p
    return y


def tri_partials(f0, limit=9000.0, maxk=15):
    out = []
    k = 1
    while k <= maxk and f0 * k < limit:
        out.append((k, (8 / np.pi ** 2) * ((-1) ** ((k - 1) // 2)) / k ** 2))
        k += 2
    return out


def saw_partials(f0, cutoff=2500.0, limit=9000.0, maxk=24):
    out = []
    for k in range(1, maxk + 1):
        if f0 * k >= limit:
            break
        roll = 1.0 / (1.0 + (f0 * k / cutoff) ** 2)
        out.append((k, 0.55 * ((-1) ** (k + 1)) / k * roll))
    return out


def wave_from(ph, parts):
    y = np.zeros_like(ph)
    for k, a in parts:
        y += a * np.sin(ph * k)
    return y


def env_exp(n, attack, tau):
    t = tarr(n)
    a = np.minimum(1.0, t / attack) if attack > 0 else np.ones(n)
    return a * np.exp(-np.maximum(0, t - attack) / tau)


def env_gate(n, attack, gate, release, decay_to=1.0, decay_tau=0.3):
    """attack -> (optional decay toward sustain) -> hold until gate -> release."""
    t = tarr(n)
    a = np.minimum(1.0, t / max(attack, 1e-4))
    s = decay_to + (1 - decay_to) * np.exp(-np.maximum(0, t - attack) / decay_tau)
    e = a * s
    rel = t > gate
    if np.any(rel):
        g_level = e[min(n - 1, int(gate * SR))]
        e[rel] = g_level * np.exp(-(t[rel] - gate) / release)
    return e


def fft_filter(x, lo=None, hi=None, order=2, circular=False):
    n = len(x)
    N = n if circular else n + 4096
    X = np.fft.rfft(x, N)
    f = np.fft.rfftfreq(N, 1 / SR)
    H = np.ones_like(f)
    if hi:
        H /= np.sqrt(1 + (f / hi) ** (2 * order))
    if lo:
        H /= np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** (2 * order))
    return np.fft.irfft(X * H, N)[:n]


def svf_band(x, fc_curve, q=0.8):
    """time-varying Chamberlin state-variable band-pass (for sweeps)."""
    low = band = 0.0
    y = np.zeros_like(x)
    qq = 1.0 / q
    fcs = 2 * np.sin(np.pi * np.minimum(fc_curve, SR / 6) / SR)
    for i in range(len(x)):
        f = fcs[i]
        low += f * band
        high = x[i] - low - qq * band
        band += f * high
        y[i] = band
    return y


def noise(n, rng):
    return rng.standard_normal(n)


def fades(x, fin=0.003, fout=0.010):
    x = x.copy()
    a = min(len(x), nsamp(fin))
    b = min(len(x), nsamp(fout))
    x[:a] *= np.linspace(0, 1, a)
    x[len(x) - b:] *= np.linspace(1, 0, b) ** 2
    return x


def normalize(x, peak=PEAK):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def write_wav(path, data):
    data = np.asarray(data)
    if data.ndim == 1:
        ch = 1
        inter = data
    else:
        ch = data.shape[0]
        inter = data.T.reshape(-1)
    pcm = np.clip(np.round(inter * 32767), -32768, 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(ch)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def read_wav(path):
    with wave.open(path, "rb") as w:
        ch = w.getnchannels()
        raw = w.readframes(w.getnframes())
    a = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    return a.reshape(-1, ch).T


# --------------------------------------------------------------------------
# instruments (return mono arrays)
# --------------------------------------------------------------------------
def inst_marimba(f, vel=1.0, tau=0.28, length=0.9):
    n = nsamp(length)
    y = additive(f, n, [(1, 1.0, tau), (4.0, 0.35, tau * 0.18),
                        (10.0, 0.08, tau * 0.05), (2.0, 0.08, tau * 0.5)])
    return vel * y * env_exp(n, 0.002, 10.0)


def inst_bell(f, vel=1.0, tau=0.9, length=None):
    length = length or min(3.0, tau * 4)
    n = nsamp(length)
    y = additive(f, n, [(1, 1.0, tau), (1.0019, 0.35, tau),
                        (2.0, 0.30, tau * 0.6), (2.76, 0.28, tau * 0.45),
                        (4.07, 0.12, tau * 0.25), (5.40, 0.08, tau * 0.18)])
    return vel * 0.6 * y * env_exp(n, 0.0015, 100.0)


def inst_pluck(f, vel=1.0, tau=0.25, length=None, limit=6000):
    length = length or min(1.5, tau * 5)
    n = nsamp(length)
    parts = [(k, a, tau / (1 + 0.5 * (k - 1))) for k, a in tri_partials(f, limit)]
    return vel * additive(f, n, parts) * env_exp(n, 0.002, 100.0)


def vib_curve(f, n, rate=5.2, depth=0.004, delay=0.15):
    t = tarr(n)
    amt = np.clip((t - delay) / 0.2, 0, 1) * depth
    return f * (1 + amt * np.sin(TWO_PI * rate * t))


def inst_flute(f, gate, vel=1.0):
    n = nsamp(gate + 0.3)
    fc = vib_curve(f, n, 5.0, 0.005, 0.12)
    ph = TWO_PI * np.cumsum(fc) / SR
    y = np.sin(ph) + 0.22 * np.sin(2 * ph) + 0.07 * np.sin(3 * ph)
    return vel * y * env_gate(n, 0.025, gate, 0.12, 0.85, 0.2)


def inst_chip(f, gate, vel=1.0):
    n = nsamp(gate + 0.2)
    fc = vib_curve(f, n, 6.0, 0.006, 0.12)
    ph = TWO_PI * np.cumsum(fc) / SR
    y = wave_from(ph, tri_partials(f, 8000))
    return vel * y * env_gate(n, 0.004, gate, 0.06, 0.65, 0.12)


def inst_synth(f, gate, vel=1.0, cutoff=2600):
    n = nsamp(gate + 0.25)
    fc = vib_curve(f, n, 5.5, 0.004, 0.18)
    ph = TWO_PI * np.cumsum(fc) / SR
    y = wave_from(ph, saw_partials(f, cutoff))
    return vel * y * env_gate(n, 0.006, gate, 0.09, 0.6, 0.15)


def inst_bass(f, gate, vel=1.0):
    n = nsamp(gate + 0.12)
    t = tarr(n)
    ph = TWO_PI * f * t
    y = np.sin(ph) + 0.35 * np.sin(2 * ph) * np.exp(-t / 0.25) \
        + 0.18 * np.sin(3 * ph) * np.exp(-t / 0.12) + 0.08 * np.sin(4 * ph) * np.exp(-t / 0.08)
    y = np.tanh(1.3 * y) / np.tanh(1.3)
    return vel * y * env_gate(n, 0.004, gate, 0.05, 0.7, 0.25)


def inst_pad(freqs, gate, vel=1.0):
    n = nsamp(gate + 0.5)
    t = tarr(n)
    y = np.zeros(n)
    for f in freqs:
        for det in (-8, 0, 8):
            fd = f * 2 ** (det / 1200)
            ph = TWO_PI * fd * t + det
            y += wave_from(ph, tri_partials(fd, 2500, 7))
    y /= max(1, len(freqs)) * 2.0
    return vel * y * env_gate(n, 0.25, gate, 0.35, 0.8, 0.6)


LEAD_GATED = {"flute": inst_flute, "chip": inst_chip, "synth": inst_synth}


def render_lead(kind, f, gate, vel):
    if kind in LEAD_GATED:
        return LEAD_GATED[kind](f, gate, vel)
    if kind == "marimba":
        return inst_marimba(f, vel, tau=0.3)
    if kind == "bell":
        return inst_bell(f, vel * 0.9, tau=0.6)
    if kind == "pluck":
        return inst_pluck(f, vel, tau=0.22)
    raise ValueError(kind)


# drums ----------------------------------------------------------------------
def drum_kick(rng, soft=1.0):
    n = nsamp(0.4)
    t = tarr(n)
    fc = 48 + 105 * np.exp(-t / 0.028)
    ph = TWO_PI * np.cumsum(fc) / SR
    y = np.sin(ph) * np.exp(-t / 0.16) * np.minimum(1, t / 0.001)
    y += 0.08 * soft * np.sin(TWO_PI * 1800 * t) * np.exp(-t / 0.003)
    return np.tanh(1.4 * y) / np.tanh(1.4)


def drum_hat(rng, tau=0.025):
    n = nsamp(max(0.12, tau * 6))
    y = fft_filter(noise(n, rng), lo=7000, hi=13000)
    return normalize(y * env_exp(n, 0.0005, tau), 1.0)


def drum_shaker(rng):
    n = nsamp(0.12)
    y = fft_filter(noise(n, rng), lo=5000, hi=11000)
    return normalize(y * env_exp(n, 0.008, 0.035), 1.0)


def drum_clap(rng):
    n = nsamp(0.35)
    t = tarr(n)
    e = np.zeros(n)
    for off in (0.0, 0.010, 0.021):
        m = t >= off
        e[m] += np.exp(-(t[m] - off) / 0.005)
    m = t >= 0.028
    e[m] += 0.8 * np.exp(-(t[m] - 0.028) / 0.085)
    y = fft_filter(noise(n, rng), lo=900, hi=3800) * e
    return normalize(y, 1.0)


def drum_swish(rng, length=1.6):
    n = nsamp(length)
    y = fft_filter(noise(n, rng), lo=4500, hi=12000)
    return normalize(y * env_exp(n, 0.01, 0.45), 1.0)


# --------------------------------------------------------------------------
# reverb / delay
# --------------------------------------------------------------------------
def make_ir(rng, length, tau, lp=6000, predelay=0.012):
    n = nsamp(length)
    t = tarr(n)
    ir = rng.standard_normal(n) * np.exp(-t / tau)
    ir = fft_filter(ir, lo=200, hi=lp)
    ir[: nsamp(predelay)] = 0
    ir *= np.linspace(1, 0, n) ** 0.5
    return ir / np.sqrt(np.sum(ir ** 2))


def circ_conv(x, ir):
    N = len(x)
    h = np.zeros(N)
    m = min(N, len(ir))
    h[:m] = ir[:m]
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h), N)


def lin_conv(x, ir):
    N = len(x) + len(ir)
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[: len(x)]


def circ_delay(x, d_samples, fb, lp=3500):
    """feedback delay as an exact periodic (circular) filter."""
    N = len(x)
    w = TWO_PI * np.fft.rfftfreq(N)  # radians/sample
    z = np.exp(-1j * w * d_samples)
    f = np.fft.rfftfreq(N, 1 / SR)
    damp = 1 / np.sqrt(1 + (f / lp) ** 2)
    H = z * damp / (1 - fb * z * damp)
    return np.fft.irfft(np.fft.rfft(x) * H, N)


# --------------------------------------------------------------------------
# music
# --------------------------------------------------------------------------
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def deg_to_midi(root, d):
    return root + 12 * (d // 7) + MAJOR[d % 7]


class Mixer:
    def __init__(self, n, tail):
        self.n = n
        self.tail = tail
        size = n + tail
        self.bus = {k: np.zeros((2, size)) for k in ("dry", "rev", "dly")}

    def add(self, sig, t_sec, gain=1.0, pan=0.0, rev=0.0, dly=0.0):
        pos = int(round(t_sec * SR)) % self.n
        size = self.n + self.tail
        end = min(pos + len(sig), size)
        s = sig[: end - pos] * gain
        th = (pan + 1) * np.pi / 4
        gl, gr = np.cos(th) * 1.41421, np.sin(th) * 1.41421
        for name, amt in (("dry", 1.0), ("rev", rev), ("dly", dly)):
            if amt <= 0:
                continue
            b = self.bus[name]
            b[0, pos:end] += s * gl * amt
            b[1, pos:end] += s * gr * amt

    def folded(self, name):
        b = self.bus[name]
        out = b[:, : self.n].copy()
        out[:, : self.tail] += b[:, self.n:]
        return out


RHYTHMS = [
    [(0, 2), (2, 2), (4, 4), (8, 2), (10, 2), (12, 4)],
    [(0, 3), (3, 3), (6, 2), (8, 4), (12, 2), (14, 2)],
    [(0, 4), (4, 2), (6, 2), (8, 6), (14, 2)],
    [(0, 2), (2, 1), (3, 3), (6, 2), (8, 2), (10, 2), (12, 4)],
    [(2, 2), (4, 2), (6, 2), (8, 4), (12, 4)],
    [(0, 6), (6, 2), (8, 2), (10, 2), (12, 4)],
    [(0, 2), (2, 2), (4, 2), (6, 2), (8, 3), (11, 3), (14, 2)],
]
CADENCES = [[(0, 4), (4, 4), (8, 8)], [(0, 2), (2, 2), (4, 12)], [(0, 3), (3, 3), (6, 10)]]

BASS_PATTERNS = {
    "half": [(0, "r", 7), (8, "f", 7)],
    "bouncy": [(0, "r", 3), (6, "r", 2), (8, "o", 3), (12, "f", 2), (14, "r", 2)],
    "eighths": [(s, "o" if s in (6, 14) else "r", 2) for s in range(0, 16, 2)],
    "octave": [(s, "r" if (s // 2) % 2 == 0 else "o", 2) for s in range(0, 16, 2)],
    "whole": [(0, "r", 12), (12, "f", 4)],
    "oompah": [(0, "r", 3), (8, "f", 3)],
    "walk": [(0, "r", 4), (4, "t", 4), (8, "f", 4), (12, "o", 4)],
}

DRUMS = {
    "soft": dict(kick=[0, 8], clap=[4, 12], hat=[], shaker=list(range(0, 16, 2))),
    "bouncy": dict(kick=[0, 6, 8], clap=[4, 12], hat=list(range(2, 16, 4)), shaker=list(range(16))),
    "play": dict(kick=[0, 7, 8, 10], clap=[4, 12], hat=list(range(0, 16, 2)), shaker=list(range(1, 16, 2))),
    "half": dict(kick=[0, 10], clap=[8], hat=[], shaker=list(range(0, 16, 2))),
    "steady": dict(kick=[0, 8, 11], clap=[4, 12], hat=list(range(0, 16, 2)), shaker=[]),
    "four": dict(kick=[0, 4, 8, 12], clap=[4, 12], hat=[2, 6, 10, 14], shaker=list(range(1, 16, 2))),
    "drive": dict(kick=[0, 4, 8, 12], clap=[4, 12], hat=list(range(16)), shaker=[]),
}


def make_melody(rng, nbars, chords_by_bar):
    """Returns list of (bar, step, len, degree) for a motif-based melody."""
    r = rng.permutation(len(RHYTHMS))
    motifs = {}
    for name, idx in (("a", r[0]), ("b", r[1]), ("c", r[2]), ("d", r[3])):
        rh = RHYTHMS[idx]
        cont = rng.choice([-2, -1, -1, 1, 1, 2, 3, -3], size=len(rh))
        motifs[name] = (rh, cont)
    cads = [CADENCES[i] for i in rng.permutation(len(CADENCES))]
    cad_cont = [list(rng.choice([-1, 1, 2, -2], size=3)) for _ in range(2)]
    nphr = nbars // 4
    shapes = {3: ["A", "B", "A2"], 4: ["A", "A2", "B", "A2"], 5: ["A", "A2", "B", "B", "A2"]}
    plan = shapes.get(nphr, (["A", "A2", "B", "A2"] * 4)[:nphr])
    phrase_bars = {"A": ["a", "b", "a", "cad0"], "A2": ["a", "b", "a", "cad1"],
                   "B": ["c", "d", "c", "cad0"]}
    notes = []
    cur = 4  # 5th degree of lead octave
    for p, ph in enumerate(plan):
        for i, m in enumerate(phrase_bars[ph]):
            bar = p * 4 + i
            chord = chords_by_bar[bar]
            ctones = {(chord + o) % 7 for o in (0, 2, 4)}
            if m.startswith("cad"):
                ci = int(m[-1])
                rh = cads[ci]
                cont = cad_cont[ci]
            else:
                rh, cont = motifs[m]
            for j, (st, ln) in enumerate(rh):
                cur += int(cont[j])
                cur = max(-1, min(11, cur))
                if st % 4 == 0 or j == len(rh) - 1:
                    cands = [d for d in range(-2, 13) if d % 7 in ctones]
                    if m.startswith("cad") and j == len(rh) - 1:
                        cands = [d for d in cands if d % 7 in ({chord % 7} if ci == 1 else ctones)]
                    cur = min(cands, key=lambda d: (abs(d - cur), d))
                notes.append((bar, st, ln, cur))
    return notes


TRACKS = {
    "menu": dict(bpm=100, root=65, prog=[0, 5, 3, 4], clen=1, lead=[("flute", 0.40)],
                 comp=["broken", "pad"], bass="half", drums="soft", rev=(2.2, 0.55), wet=0.22,
                 seed=11, swing=0.08, hat_vel=0.6),
    "garden": dict(bpm=118, root=67, prog=[0, 3, 4, 0, 5, 3, 1, 4], clen=1,
                   lead=[("marimba", 0.42), ("pluck", 0.12)], comp=["stab", "broken"], bass="bouncy",
                   drums="bouncy", rev=(1.6, 0.4), wet=0.16, seed=23, swing=0.12),
    "candy": dict(bpm=126, root=70 - 12, prog=[0, 5, 1, 4], clen=1, lead=[("chip", 0.34), ("bell", 0.14)],
                  comp=["stab", "bellarp"], bass="octave", drums="play", rev=(1.5, 0.4), wet=0.16,
                  seed=37, swing=0.0, lead_up=12),
    "sky": dict(bpm=110, root=63, prog=[0, 4, 5, 3], clen=2, lead=[("flute", 0.36)],
                comp=["pad", "bellarp"], bass="whole", drums="half", rev=(3.0, 0.8), wet=0.32,
                seed=41, swing=0.0, dly=0.3),
    "crystal": dict(bpm=120, root=64, prog=[3, 4, 2, 5], clen=1, lead=[("bell", 0.42), ("flute", 0.12)],
                    comp=["broken", "bellarp16", "pad"], bass="half", drums="steady", rev=(2.4, 0.6),
                    wet=0.26, seed=53, swing=0.0),
    "space": dict(bpm=128, root=60, prog=[0, 5, 3, 4], clen=1, lead=[("synth", 0.30), ("chip", 0.10)],
                  comp=["arp16", "pad"], bass="eighths", drums="four", rev=(2.0, 0.5), wet=0.22,
                  seed=67, swing=0.0, dly=0.25),
    "carnival": dict(bpm=136, root=69 - 12, prog=[0, 3, 4, 0, 0, 3, 1, 4], clen=1,
                     lead=[("marimba", 0.36), ("chip", 0.18)], comp=["oompah", "broken"], bass="oompah",
                     drums="play", rev=(1.4, 0.35), wet=0.14, seed=79, swing=0.0, lead_up=12),
    "final": dict(bpm=140, root=62, prog=[0, 4, 5, 3, 0, 4, 3, 4], clen=1,
                  lead=[("synth", 0.30), ("bell", 0.16)], comp=["pad", "stab", "arp16"], bass="octave",
                  drums="drive", rev=(2.2, 0.55), wet=0.2, seed=97, swing=0.0, crash=True),
    "endless": dict(bpm=132, root=57 + 12 - 12, prog=[5, 3, 0, 4], clen=1,
                    lead=[("chip", 0.34), ("pluck", 0.12)], comp=["arp16", "stab"], bass="eighths",
                    drums="drive", rev=(1.6, 0.4), wet=0.16, seed=101, swing=0.0, lead_up=12, crash=True),
}


def render_music(name, cfg):
    rng = np.random.default_rng(cfg["seed"])
    bpm = cfg["bpm"]
    beat = 60.0 / bpm
    step = beat / 4
    bar_s = beat * 4
    nbars = int(math.ceil(27.0 / bar_s / 4) * 4)
    loop_s = nbars * bar_s
    n = int(round(loop_s * SR))
    mix = Mixer(n, nsamp(4.0))
    root = cfg["root"]
    swing = cfg.get("swing", 0.0)

    def tpos(bar, st):
        sw = swing * step if st % 2 == 1 else 0.0
        return bar * bar_s + st * step + sw

    prog = cfg["prog"]
    clen = cfg["clen"]
    chords = [prog[(b // clen) % len(prog)] for b in range(nbars)]

    # ---- drums
    kick = drum_kick(rng)
    clap = drum_clap(rng)
    hats = [drum_hat(rng) for _ in range(3)]
    shak = [drum_shaker(rng) for _ in range(3)]
    swish = drum_swish(rng)
    pat = DRUMS[cfg["drums"]]
    hv = cfg.get("hat_vel", 1.0)
    for b in range(nbars):
        phrase_end = b % 4 == 3
        for st in pat["kick"]:
            mix.add(kick, tpos(b, st), 0.85 if st == 0 else 0.7)
        for st in pat["clap"]:
            mix.add(clap, tpos(b, st), 0.30 * rng.uniform(0.9, 1.0), 0.05, rev=0.25)
        if phrase_end and cfg["drums"] not in ("soft", "half"):
            for st in (13, 14, 15):
                mix.add(clap, tpos(b, st), 0.14 + 0.04 * (st - 13), -0.1, rev=0.2)
        for st in pat["hat"]:
            acc = 1.0 if st % 4 == 2 else 0.6
            mix.add(hats[rng.integers(3)], tpos(b, st), 0.13 * acc * hv * rng.uniform(0.85, 1.1), 0.35)
        for st in pat["shaker"]:
            acc = 1.0 if st % 2 == 0 else 0.6
            mix.add(shak[rng.integers(3)], tpos(b, st), 0.10 * acc * hv * rng.uniform(0.8, 1.1), -0.4)
        if cfg.get("crash") and b % 8 == 0:
            mix.add(swish, tpos(b, 0), 0.10, 0.2, rev=0.4)

    # ---- bass
    bpat = BASS_PATTERNS[cfg["bass"]]
    broot = root - 24
    while broot < 36:
        broot += 12
    for b in range(nbars):
        c = chords[b]
        for st, which, ln in bpat:
            d = {"r": c, "f": c + 4, "o": c + 7, "t": c + 2}[which]
            m = deg_to_midi(broot, d)
            if which == "f" and m - deg_to_midi(broot, c) > 7:
                m -= 12
            sig = inst_bass(mtof(m), ln * step * 0.85, 1.0)
            mix.add(sig, tpos(b, st), 0.46)

    # ---- comp
    croot = root
    for comp in cfg["comp"]:
        for b in range(nbars):
            c = chords[b]
            tones = [deg_to_midi(croot, c + o) for o in (0, 2, 4)]
            if comp == "broken":
                order = [0, 1, 2, 1, 0, 2, 1, 2]
                for i, o in enumerate(order):
                    m = tones[o] + (12 if (i == 5) else 0)
                    mix.add(inst_marimba(mtof(m), 1.0, tau=0.22), tpos(b, i * 2), 0.17,
                            (-0.5 + o * 0.5), rev=0.3)
            elif comp == "stab":
                for st in (2, 6, 10, 14):
                    for k, m in enumerate(tones):
                        mix.add(inst_pluck(mtof(m), 1.0, tau=0.10, length=0.5), tpos(b, st),
                                0.075, -0.3 + 0.3 * k, rev=0.25)
            elif comp == "oompah":
                for st in (4, 12):
                    for k, m in enumerate(tones):
                        mix.add(inst_pluck(mtof(m), 1.0, tau=0.14, length=0.6), tpos(b, st),
                                0.09, -0.3 + 0.3 * k, rev=0.2)
            elif comp == "pad":
                if b % clen == 0:
                    fr = [mtof(m - 12) for m in tones] + [mtof(tones[0])]
                    sig = inst_pad(fr, bar_s * clen - 0.05)
                    mix.add(sig, tpos(b, 0), 0.20, 0.0, rev=0.5)
            elif comp in ("bellarp", "bellarp16"):
                seq = [0, 1, 2, 3, 2, 1, 2, 3] if comp == "bellarp" else \
                      [0, 1, 2, 3, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 4, 2]
                stp = 2 if comp == "bellarp" else 1
                for i, s in enumerate(seq):
                    m = deg_to_midi(croot + 12, c + 2 * s)
                    pan = 0.6 * math.sin(i * 0.7)
                    mix.add(inst_bell(mtof(m), 1.0, tau=0.5), tpos(b, i * stp),
                            0.07 if stp == 2 else 0.05, pan, rev=0.45)
            elif comp == "arp16":
                seq = [0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 2, 3, 4, 3, 2, 1]
                for i, s in enumerate(seq):
                    m = deg_to_midi(croot, c + 2 * s)
                    sig = inst_synth(mtof(m), step * 0.6, 1.0, cutoff=1800) if name == "space" \
                        else inst_pluck(mtof(m), 1.0, tau=0.09, length=0.4)
                    pan = 0.5 * math.sin(TWO_PI * i / 16)
                    mix.add(sig, tpos(b, i), 0.075, pan, rev=0.25, dly=0.15)

    # ---- lead
    mel = make_melody(rng, nbars, chords)
    lroot = root + cfg.get("lead_up", 12) - (12 if root >= 66 else 0)
    for (b, st, ln, d) in mel:
        f = mtof(deg_to_midi(lroot, d))
        gate = ln * step * 0.88
        vel = 1.0 if st % 4 == 0 else 0.85
        for kind, g in cfg["lead"]:
            for cents, pan in ((6, -0.3), (-6, 0.3)):
                sig = render_lead(kind, f * 2 ** (cents / 1200), gate, vel)
                mix.add(sig, tpos(b, st), g * 0.5, pan, rev=0.25, dly=cfg.get("dly", 0.12))

    # ---- master (all periodic)
    dry = mix.folded("dry")
    rev = mix.folded("rev")
    dly = mix.folded("dly")
    rlen, rtau = cfg["rev"]
    out = dry.copy()
    for ch in range(2):
        ir = make_ir(rng, rlen, rtau)
        out[ch] += cfg["wet"] * 2.2 * circ_conv(rev[ch], ir)
        d_s = int(round(beat * (0.75 if ch == 0 else 1.0) * SR))
        out[ch] += 0.55 * circ_delay(dly[ch], d_s, 0.33)
    for ch in range(2):
        out[ch] = fft_filter(out[ch], lo=35, hi=11000, order=1, circular=True)
    # gentle soft-knee limiting then normalize
    pk = np.max(np.abs(out))
    out = np.tanh(1.6 * out / pk) / np.tanh(1.6)
    return normalize(out), nbars, loop_s


# --------------------------------------------------------------------------
# SFX
# --------------------------------------------------------------------------
class Sfx:
    def __init__(self, dur, extra=0.0):
        self.dur = dur
        self.buf = np.zeros(nsamp(dur + extra + 1.0))

    def add(self, sig, t, g=1.0):
        p = nsamp(t) if t > 0 else 0
        e = min(len(self.buf), p + len(sig))
        self.buf[p:e] += g * sig[: e - p]

    def finish(self, rng, wet=0.0, rlen=0.4, rtau=0.12, lp=9000, fout=0.02):
        y = self.buf
        if wet > 0:
            y = y + wet * lin_conv(y, make_ir(rng, rlen, rtau, lp=7000, predelay=0.006))
        y = fft_filter(y, lo=60, hi=lp, order=1)
        y = y[: nsamp(self.dur)]
        return normalize(fades(y, 0.002, min(fout, self.dur * 0.25)))


def sweep_sine(n, f0, f1, tau):
    t = tarr(n)
    fc = f1 + (f0 - f1) * np.exp(-t / tau)
    return np.sin(TWO_PI * np.cumsum(fc) / SR)


def shimmer(rng, n, fmin, fmax, count, tau):
    y = np.zeros(n)
    t = tarr(n)
    for _ in range(count):
        f = rng.uniform(fmin, fmax)
        st = rng.uniform(0, 0.5) * n / SR
        e = np.where(t >= st, np.exp(-(t - st) / tau) * np.minimum(1, (t - st + 1e-9) / 0.004), 0)
        y += np.sin(TWO_PI * f * t + rng.uniform(0, 6)) * e
    return y


def make_sfx():
    rng = np.random.default_rng(7)
    out = {}
    C = lambda m: mtof(m)

    # tap: very short soft pluck
    s = Sfx(0.06)
    n = nsamp(0.06)
    y = sweep_sine(n, 1150, 880, 0.01) * env_exp(n, 0.001, 0.014)
    t = tarr(n)
    y += 0.2 * np.sin(TWO_PI * 1760 * t) * np.exp(-t / 0.006)
    s.add(y, 0)
    out["tap"] = s.finish(rng, lp=7000, fout=0.01)

    # button: tiny pop
    s = Sfx(0.05)
    n = nsamp(0.05)
    y = sweep_sine(n, 1500, 520, 0.008) * env_exp(n, 0.0008, 0.010)
    y += 0.12 * fft_filter(noise(n, rng), lo=3000, hi=8000) * env_exp(n, 0.0002, 0.0015)
    s.add(y, 0)
    out["button"] = s.finish(rng, lp=8000, fout=0.008)

    # countdown: soft beep
    s = Sfx(0.12)
    n = nsamp(0.12)
    t = tarr(n)
    ph = TWO_PI * 880 * t
    y = (np.sin(ph) + 0.25 * wave_from(ph, tri_partials(880, 6000))) * env_gate(n, 0.004, 0.07, 0.025)
    s.add(y, 0)
    out["countdown"] = s.finish(rng, fout=0.015)

    # go: higher beep with octave body
    s = Sfx(0.25)
    n = nsamp(0.25)
    t = tarr(n)
    e = env_gate(n, 0.004, 0.15, 0.04)
    y = (np.sin(TWO_PI * C(88) * t) + 0.35 * np.sin(TWO_PI * C(76) * t)
         + 0.15 * np.sin(TWO_PI * C(95) * t)) * e
    s.add(y, 0)
    out["go"] = s.finish(rng, wet=0.15, fout=0.03)

    # combo: rising blip
    s = Sfx(0.2)
    n = nsamp(0.2)
    t = tarr(n)
    fc = 650 * (1400 / 650) ** np.minimum(1, t / 0.11)
    ph = TWO_PI * np.cumsum(fc) / SR
    y = (np.sin(ph) + 0.3 * wave_from(ph, tri_partials(1000, 7000)[1:])) * env_gate(n, 0.004, 0.12, 0.03)
    s.add(y, 0)
    out["combo"] = s.finish(rng, wet=0.1, fout=0.03)

    # coin: classic sparkly two-note
    s = Sfx(0.18)
    s.add(inst_pluck(C(83), 1.0, tau=0.05, length=0.06, limit=9000) * env_gate(nsamp(0.06), 0.001, 0.05, 0.006), 0)
    s.add(inst_pluck(C(88), 1.0, tau=0.07, length=0.14, limit=9000), 0.055)
    s.add(inst_bell(C(100), 0.25, tau=0.06, length=0.13), 0.06)
    out["coin"] = s.finish(rng, wet=0.1, lp=11000, fout=0.03)

    # gate_ok: bright two-note ding up
    s = Sfx(0.2)
    s.add(inst_bell(C(84), 1.0, tau=0.12, length=0.2), 0)
    s.add(inst_marimba(C(84), 0.5, tau=0.08, length=0.2), 0)
    s.add(inst_bell(C(91), 1.0, tau=0.12, length=0.15), 0.065)
    s.add(inst_marimba(C(91), 0.5, tau=0.08, length=0.15), 0.065)
    out["gate_ok"] = s.finish(rng, wet=0.12, lp=11000, fout=0.04)

    # gate_fail: gentle descending bwomp
    s = Sfx(0.45)
    n1 = nsamp(0.14)
    t1 = tarr(n1)
    ph = TWO_PI * np.cumsum(392 - 30 * t1 / 0.14) / SR
    y1 = wave_from(ph, tri_partials(392, 2500)) * env_gate(n1, 0.006, 0.08, 0.025)
    s.add(y1, 0, 0.8)
    n2 = nsamp(0.32)
    t2 = tarr(n2)
    fc = (300 * (140 / 300) ** np.minimum(1, t2 / 0.28)) * (1 + 0.025 * np.sin(TWO_PI * 7 * t2))
    ph = TWO_PI * np.cumsum(fc) / SR
    y2 = (wave_from(ph, tri_partials(300, 2200)) + 0.3 * np.sin(ph)) * env_gate(n2, 0.012, 0.18, 0.06)
    s.add(y2, 0.125)
    out["gate_fail"] = s.finish(rng, wet=0.1, lp=2400, fout=0.05)

    # star: twinkle
    s = Sfx(0.35)
    s.add(inst_bell(C(100), 1.0, tau=0.12, length=0.35), 0)
    s.add(inst_bell(C(107), 0.8, tau=0.12, length=0.3), 0.06)
    n = nsamp(0.35)
    t = tarr(n)
    tw = np.sin(TWO_PI * C(112) * t) * (0.5 + 0.5 * np.sin(TWO_PI * 22 * t)) * env_exp(n, 0.03, 0.08)
    s.add(0.25 * tw, 0.04)
    out["star"] = s.finish(rng, wet=0.18, lp=13000, fout=0.06)

    # diamond: crystal arpeggio shimmer
    s = Sfx(0.5)
    for i, m in enumerate([91, 95, 98, 103, 107]):
        s.add(inst_bell(C(m), 1.0 - 0.1 * i, tau=0.22, length=0.5), i * 0.038)
    s.add(0.08 * shimmer(rng, nsamp(0.5), 6000, 10000, 10, 0.06), 0.05)
    out["diamond"] = s.finish(rng, wet=0.22, lp=13000, fout=0.08)

    # life_refill: warm rising chime
    s = Sfx(0.6)
    for i, m in enumerate([72, 76, 79, 84]):
        s.add(inst_marimba(C(m), 0.8, tau=0.3), i * 0.08)
        s.add(inst_bell(C(m + 12), 0.4, tau=0.3), i * 0.08)
    pad = inst_pad([C(60), C(64), C(67)], 0.35)
    s.add(0.6 * pad, 0)
    out["life_refill"] = s.finish(rng, wet=0.2, fout=0.12)

    # level_complete: happy fanfare
    s = Sfx(1.6)
    seq = [(0.00, 79, 0.10), (0.11, 84, 0.10), (0.22, 88, 0.10), (0.33, 91, 0.18),
           (0.55, 88, 0.10), (0.66, 91, 0.10), (0.80, 96, 0.55)]
    for t0, m, g in seq:
        for cents in (5, -5):
            s.add(inst_chip(C(m) * 2 ** (cents / 1200), g, 0.5), t0)
        s.add(inst_bell(C(m), 0.35, tau=0.4), t0)
    for m in (72, 76, 79, 84):
        s.add(inst_marimba(C(m), 0.35, tau=0.5, length=1.0), 0.80)
    s.add(0.45 * inst_pad([C(60), C(64), C(67), C(72)], 0.5), 0.80)
    s.add(inst_bass(C(48), 0.3), 0.0, 0.5)
    s.add(inst_bass(C(48), 0.6), 0.80, 0.6)
    s.add(0.3 * shimmer(rng, nsamp(0.8), 5000, 9000, 8, 0.12), 0.85)
    out["level_complete"] = s.finish(rng, wet=0.25, rlen=0.9, rtau=0.25, fout=0.25)

    # area_complete: bigger fanfare with chords
    s = Sfx(2.5)
    chords = [(0.0, [60, 64, 67]), (0.4, [65, 69, 72]), (0.8, [67, 71, 74]), (1.2, [72, 76, 79])]
    mel = [(0.00, 84), (0.133, 88), (0.266, 91), (0.40, 93), (0.533, 96), (0.666, 93),
           (0.80, 95), (0.933, 98), (1.066, 95)]
    for t0, m in mel:
        for cents in (6, -6):
            s.add(inst_chip(C(m) * 2 ** (cents / 1200), 0.11, 0.45), t0)
        s.add(inst_bell(C(m), 0.3, tau=0.3), t0)
    for cents in (6, -6):
        s.add(inst_synth(C(96) * 2 ** (cents / 1200), 0.9, 0.5), 1.2)
    s.add(inst_bell(C(96), 0.6, tau=0.8), 1.2)
    for i, (t0, ch) in enumerate(chords):
        last = i == len(chords) - 1
        s.add(0.55 * inst_pad([C(m) for m in ch], 1.0 if last else 0.36), t0)
        for m in ch:
            s.add(inst_marimba(C(m), 0.35, tau=0.4 if last else 0.2), t0)
        s.add(inst_bass(C(ch[0] - 24), 0.9 if last else 0.34), t0, 0.6)
        s.add(drum_kick(rng), t0, 0.5)
    s.add(0.25 * drum_swish(rng, 1.3), 1.2)
    for i, m in enumerate([96, 100, 103, 108, 103, 108, 112]):
        s.add(inst_bell(C(m), 0.35, tau=0.3), 1.35 + i * 0.07)
    out["area_complete"] = s.finish(rng, wet=0.28, rlen=1.2, rtau=0.3, fout=0.4)

    # unlock: magical sparkle sweep
    s = Sfx(0.9)
    penta = [0, 2, 4, 7, 9]
    notes = [84 + 12 * (i // 5) + penta[i % 5] for i in range(12)]
    for i, m in enumerate(notes):
        t0 = 0.5 * (i / 12) ** 0.85
        s.add(inst_bell(C(m), 0.5 + 0.04 * i, tau=0.18, length=0.5), t0)
    n = nsamp(0.7)
    t = tarr(n)
    fcv = 2000 * (10000 / 2000) ** (t / 0.7)
    sw = svf_band(noise(n, rng), fcv, 1.5) * np.sin(np.pi * t / 0.7) ** 2
    s.add(0.05 * sw, 0)
    for m in (96, 100, 103):
        s.add(inst_bell(C(m), 0.5, tau=0.3), 0.52)
    s.add(0.3 * shimmer(rng, nsamp(0.4), 6000, 11000, 8, 0.07), 0.5)
    out["unlock"] = s.finish(rng, wet=0.25, rlen=0.6, rtau=0.18, lp=13000, fout=0.12)

    # purchase: cute register ding + coin shimmer
    s = Sfx(0.6)
    n = nsamp(0.04)
    s.add(0.5 * fft_filter(noise(n, rng), lo=2000, hi=6000) * env_exp(n, 0.0005, 0.006), 0)
    s.add(inst_marimba(C(72), 0.5, tau=0.08, length=0.2), 0)
    s.add(inst_bell(C(93), 1.0, tau=0.2, length=0.6), 0.03)
    s.add(inst_bell(C(100), 1.0, tau=0.25, length=0.55), 0.10)
    s.add(inst_pluck(C(95), 0.4, tau=0.05, length=0.08), 0.18)
    s.add(inst_pluck(C(100), 0.4, tau=0.08, length=0.2), 0.23)
    s.add(0.3 * shimmer(rng, nsamp(0.4), 5000, 9500, 12, 0.06), 0.15)
    out["purchase"] = s.finish(rng, wet=0.18, lp=12000, fout=0.1)

    # achievement: triumphant short jingle
    s = Sfx(1.2)
    seq = [(0.0, 79, 0.08), (0.1, 79, 0.08), (0.2, 79, 0.08), (0.32, 84, 0.7)]
    for t0, m, g in seq:
        for cents in (6, -6):
            s.add(inst_synth(C(m) * 2 ** (cents / 1200), g, 0.45, cutoff=3000), t0)
        s.add(inst_bell(C(m + 12), 0.25, tau=0.35), t0)
    s.add(0.5 * inst_pad([C(60), C(64), C(67), C(72)], 0.55), 0.32)
    for m in (76, 79, 84, 88):
        s.add(inst_marimba(C(m), 0.3, tau=0.4), 0.32)
    s.add(inst_bass(C(43), 0.25), 0, 0.5)
    s.add(inst_bass(C(48), 0.6), 0.32, 0.6)
    s.add(drum_kick(rng), 0.32, 0.45)
    for i, m in enumerate([96, 100, 103, 108]):
        s.add(inst_bell(C(m), 0.3, tau=0.25), 0.45 + i * 0.06)
    out["achievement"] = s.finish(rng, wet=0.25, rlen=0.8, rtau=0.22, fout=0.2)

    # whoosh: soft filtered noise sweep
    s = Sfx(0.35)
    n = nsamp(0.35)
    t = tarr(n)
    fcv = 350 * (2600 / 350) ** (t / 0.35)
    amp = np.sin(np.pi * np.minimum(1, t / 0.33)) ** 2
    y = svf_band(noise(n, rng), fcv, 0.9) * amp
    y += 0.5 * fft_filter(noise(n, rng), hi=900) * amp
    s.add(y, 0)
    out["whoosh"] = s.finish(rng, lp=6000, fout=0.03)

    # shield: bubble pop + shield ping
    s = Sfx(0.3)
    n = nsamp(0.07)
    t = tarr(n)
    fc = 380 * (1250 / 380) ** np.minimum(1, t / 0.05)
    y = np.sin(TWO_PI * np.cumsum(fc) / SR) * env_exp(n, 0.002, 0.025)
    s.add(y, 0, 0.9)
    n = nsamp(0.26)
    t = tarr(n)
    trem = 1 - 0.3 * (0.5 + 0.5 * np.sin(TWO_PI * 28 * t))
    s.add(inst_bell(C(91), 0.8, tau=0.1, length=0.26) * trem, 0.04)
    s.add(inst_bell(C(98), 0.35, tau=0.08, length=0.26) * trem, 0.05)
    out["shield"] = s.finish(rng, wet=0.15, lp=11000, fout=0.04)

    return out


# --------------------------------------------------------------------------
# encoding / verification
# --------------------------------------------------------------------------
def pick_codec():
    r = subprocess.run(["ffmpeg", "-hide_banner", "-encoders"], capture_output=True, text=True)
    if "libvorbis" in r.stdout:
        return ["-c:a", "libvorbis"]
    return ["-c:a", "vorbis", "-strict", "-2"]


def encode(wav, ogg, q, codec):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav] + codec +
                   ["-q:a", str(q), "-map_metadata", "-1", ogg], check=True)


def ffprobe_duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=nw=1:nk=1", path], capture_output=True, text=True)
    return float(r.stdout.strip())


def main():
    wav_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(tempfile.gettempdir(), "yr_audio_wav")
    os.makedirs(wav_dir, exist_ok=True)
    os.makedirs(SFX_DIR, exist_ok=True)
    os.makedirs(MUSIC_DIR, exist_ok=True)
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found")
    codec = pick_codec()
    rows = []

    for name, y in make_sfx().items():
        wav = os.path.join(wav_dir, f"sfx_{name}.wav")
        ogg = os.path.join(SFX_DIR, f"{name}.ogg")
        write_wav(wav, y)
        encode(wav, ogg, 4, codec)
        rows.append(("sfx", name, wav, ogg, None))

    for name, cfg in TRACKS.items():
        y, nbars, loop_s = render_music(name, cfg)
        # seam check: jump across the loop point vs. typical sample-to-sample step
        seam = np.max(np.abs(y[:, 0] - y[:, -1]))
        typ = np.percentile(np.abs(np.diff(y, axis=1)), 99.9)
        wav = os.path.join(wav_dir, f"music_{name}.wav")
        ogg = os.path.join(MUSIC_DIR, f"{name}.ogg")
        write_wav(wav, y)
        q = 3
        encode(wav, ogg, q, codec)
        while os.path.getsize(ogg) > 700 * 1024 and q > 0:
            q -= 1
            encode(wav, ogg, q, codec)
        rows.append(("music", name, wav, ogg,
                     f"{cfg['bpm']}bpm {nbars}bars q{q} seam={seam:.4f}/{typ:.4f}"))

    print(f"{'kind':5} {'name':15} {'dur(s)':>7} {'KB':>6} {'peak dBFS':>9} {'rms dBFS':>8}  notes")
    bad = False
    for kind, name, wav, ogg, note in rows:
        a = read_wav(wav)
        peak = np.max(np.abs(a))
        rms = np.sqrt(np.mean(a ** 2))
        pk_db = 20 * np.log10(max(peak, 1e-9))
        rms_db = 20 * np.log10(max(rms, 1e-9))
        if rms_db < -40 or peak >= 0.999:
            bad = True
        dur = ffprobe_duration(ogg)
        kb = os.path.getsize(ogg) / 1024
        print(f"{kind:5} {name:15} {dur:7.3f} {kb:6.1f} {pk_db:9.2f} {rms_db:8.2f}  {note or ''}")
    if bad:
        sys.exit("verification failed: silent or clipping file")


if __name__ == "__main__":
    main()
