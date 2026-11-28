#!/usr/bin/env python3
"""Tanıtım videosu için özgün müzik (kodla sentezlenir; telif/lisans sorunu yoktur). 30 sn, 128 BPM, neşeli chiptune.
Kullanım: python3 music.py çıktı.wav [saniye]"""
import sys, wave, numpy as np
SR = 44100
def note(n): return 440.0 * 2 ** ((n - 69) / 12)
def env(n, a=0.005, d=0.15, s=0.6, r=0.05, length=None):
    length = length or n
    e = np.ones(n); ia = int(a * SR); idd = int(d * SR); ir = int(r * SR)
    if ia: e[:ia] = np.linspace(0, 1, ia)
    if idd: e[ia:ia + idd] = np.linspace(1, s, min(idd, max(0, n - ia)))[: max(0, min(idd, n - ia))]
    e[ia + idd:] = s
    if ir and n > ir: e[-ir:] *= np.linspace(1, 0, ir)
    return e
def square(f, n, duty=0.5): t = np.arange(n) / SR; return np.where((t * f) % 1 < duty, 1.0, -1.0)
def tri(f, n): t = np.arange(n) / SR; return 2 * np.abs(2 * ((t * f) % 1) - 1) - 1
def make(seconds=30.0):
    bpm = 128; beat = 60 / bpm; total = int(seconds * SR)
    out = np.zeros(total + SR)
    prog = [(60, [60, 64, 67]), (55, [55, 59, 62]), (57, [57, 60, 64]), (53, [53, 57, 60])]  # C G Am F
    melody = [72, 74, 76, 79, 76, 74, 72, 67, 71, 74, 79, 77, 76, 72, 74, 76]
    nbeats = int(seconds / beat) + 1
    for b in range(nbeats):
        t0 = int(b * beat * SR)
        root, chord = prog[(b // 4) % 4]
        # bas (üçgen)
        n = int(beat * SR * 0.9); out[t0:t0 + n] += 0.22 * tri(note(root - 24), n) * env(n, d=0.1, s=0.7)
        # arpej (kare, kısa)
        for k in range(4):
            tt = t0 + int(k * beat / 4 * SR); m = int(beat / 4 * SR * 0.8)
            out[tt:tt + m] += 0.07 * square(note(chord[k % 3] + 12), m, 0.25) * env(m, d=0.05, s=0.3)
        # melodi (her 2 vuruşta bir nota, ilk 4 vuruş sessiz giriş)
        if b >= 4 and b % 2 == 0:
            mn = melody[(b // 2) % len(melody)]; m = int(beat * 2 * SR * 0.85)
            out[t0:t0 + m] += 0.10 * square(note(mn), m, 0.5) * env(m, d=0.2, s=0.5)
        # davul: kick her vuruş, hi-hat ara vuruş, clap 2 ve 4
        m = int(0.12 * SR); tk = np.arange(m) / SR
        out[t0:t0 + m] += 0.5 * np.sin(2 * np.pi * (120 - 400 * tk) * tk) * np.exp(-tk * 30)
        th = t0 + int(beat / 2 * SR); mh = int(0.04 * SR)
        out[th:th + mh] += 0.05 * np.random.default_rng(b).uniform(-1, 1, mh) * np.exp(-np.arange(mh) / SR * 80)
        if b % 2 == 1:
            mc = int(0.08 * SR); out[t0:t0 + mc] += 0.12 * np.random.default_rng(1000 + b).uniform(-1, 1, mc) * np.exp(-np.arange(mc) / SR * 40)
    out = out[:total]
    fade = int(1.5 * SR); out[-fade:] *= np.linspace(1, 0, fade); out[:int(0.05 * SR)] *= np.linspace(0, 1, int(0.05 * SR))
    out = out / np.max(np.abs(out)) * 0.85
    return out
if __name__ == "__main__":
    sec = float(sys.argv[2]) if len(sys.argv) > 2 else 30.0
    x = make(sec); st = np.stack([x, x], 1)
    with wave.open(sys.argv[1], "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((st * 32767).astype("<i2").tobytes())
    print("tamam")
