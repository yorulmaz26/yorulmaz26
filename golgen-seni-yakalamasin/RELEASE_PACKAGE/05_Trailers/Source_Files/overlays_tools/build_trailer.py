#!/usr/bin/env python3
"""Tanıtım videosu kurgusu (ffmpeg). Gerçek oyun sahnelerinden 30 sn'lik video üretir.

Kullanım: build_trailer.py --lang tr --clips <klip_klasörü> --texts <metin.json> --out <çıktı.mp4>
          [--endcard play|social1|social2] [--horizontal]
Klipler: v_<dil>_01_kanca.mp4, _02_mekanik, _03_deneyim, _04a.._04e, _05_basari, _06_menu (60 FPS, 1080x1920).
Metinler: {"k1":..,"k2":..,"k3":..,"k4":..,"k5":..,"end":..} ; yazı katmanları Chromium ile PNG'ye çizilir.
"""
import argparse, json, os, subprocess, sys, tempfile, urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
F = 0.25  # geçiş süresi (sn)
# (klip son eki, klip içindeki başlangıç sn, süre sn, metin anahtarı)
PLAN = [
    ("01_kanca", 0.0, 3.25, "k1"),
    ("02_mekanik", 0.0, 5.25, "k2"),
    ("03_deneyim", 0.0, 7.25, "k3"),
    ("04", 0.0, 7.0, "k4"),        # 5 hızlı kesme (a..e), her biri 1.4 sn
    ("05_basari", 0.0, 5.25, "k5"),
    ("06_menu", 0.0, 3.25, "end"),
]

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(" ".join(cmd)); print(r.stderr[-3000:]); sys.exit(1)

def render_overlays(texts, lang, endcard, horizontal, tmp):
    """Yazı katmanlarını ve kapanış kartını şeffaf PNG olarak üretir."""
    jobs, out = [], {}
    W, H = (1920, 1080) if horizontal else (1080, 1920)
    for k in ["k1", "k2", "k3", "k4", "k5"]:
        p = os.path.join(tmp, f"ov_{k}.png")
        jobs.append({"html": os.path.join(HERE, "overlay.html") + "?" + urllib.parse.urlencode({"t": texts[k], "mode": "caption", "hz": int(horizontal)}),
                     "out": p, "w": W, "h": H, "transparent": True})
        out[k] = p
    if horizontal:
        p = os.path.join(tmp, "ov_deco.png")
        jobs.append({"html": os.path.join(HERE, "overlay.html") + "?" + urllib.parse.urlencode({"mode": "deco", "lang": lang, "hz": 1}),
                     "out": p, "w": W, "h": H, "transparent": True})
        out["deco"] = p
    p = os.path.join(tmp, "ov_end.png")
    endtext = texts["end"] if endcard == "play" else texts[endcard]
    jobs.append({"html": os.path.join(HERE, "overlay.html") + "?" + urllib.parse.urlencode({"t": endtext, "mode": "end", "lang": lang, "hz": int(horizontal)}),
                 "out": p, "w": W, "h": H, "transparent": True})
    out["end"] = p
    jf = os.path.join(tmp, "jobs.json")
    json.dump(jobs, open(jf, "w"))
    run(["node", os.path.join(HERE, "render.js"), jf])
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True)
    ap.add_argument("--clips", required=True)
    ap.add_argument("--texts", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--endcard", default="play")
    ap.add_argument("--horizontal", action="store_true")
    ap.add_argument("--music", default="/tmp/claude-0/media/audio/muzik.wav")
    ap.add_argument("--sfx", default="/tmp/claude-0/media/audio")
    a = ap.parse_args()
    texts = json.load(open(a.texts))
    tmp = tempfile.mkdtemp(prefix="fragman_")
    ov = render_overlays(texts, a.lang, a.endcard, a.horizontal, tmp)
    W, H = (1920, 1080) if a.horizontal else (1080, 1920)
    inputs, filters, labels = [], [], []
    idx = 0
    def clip(name):
        return os.path.join(a.clips, f"v_{a.lang}_{name}.mp4")
    # --- segmentler ---
    for si, (suffix, start, dur, tkey) in enumerate(PLAN):
        seg_dur = dur
        if suffix == "04":
            parts = []
            for j, letter in enumerate("abcde"):
                inputs += ["-ss", "0", "-t", "1.4", "-i", clip("04" + letter)]
                filters.append(f"[{idx}:v]fps=60,scale=1080:1920,setsar=1,setpts=PTS-STARTPTS[c{si}{letter}]")
                parts.append(f"[c{si}{letter}]")
                idx += 1
            filters.append(f"{''.join(parts)}concat=n=5:v=1:a=0[raw{si}]")
        else:
            inputs += ["-ss", str(start), "-t", str(dur), "-i", clip(suffix)]
            filters.append(f"[{idx}:v]fps=60,scale=1080:1920,setsar=1,setpts=PTS-STARTPTS[raw{si}]")
            idx += 1
        src = f"raw{si}"
        if suffix == "06_menu":
            # kapanış: menü görüntüsü bulanık ve koyu arka plan olur
            filters.append(f"[{src}]boxblur=18:2,eq=brightness=-0.18:saturation=1.1[blur{si}]")
            src = f"blur{si}"
        if a.horizontal:
            # yatay: arka planda bulanık büyütülmüş görüntü, ortada dikey oyun (siyah kenar yok)
            filters.append(f"[{src}]split[hs{si}a][hs{si}b]")
            filters.append(f"[hs{si}a]scale=1920:-2,crop=1920:1080:0:(ih-1080)/2,boxblur=20:2,eq=brightness=-0.04:saturation=1.35[hb{si}]")
            filters.append(f"[hs{si}b]scale=-2:1080[hf{si}]")
            filters.append(f"[hb{si}][hf{si}]overlay=(W-w)/2:0[geo{si}]")
            src = f"geo{si}"
            if tkey != "end":
                inputs.extend(["-loop", "1", "-t", str(seg_dur), "-i", ov["deco"]])
                filters.append(f"[{src}][{idx}:v]overlay=0:0:format=auto[deco{si}]")
                idx += 1
                src = f"deco{si}"
        # yazı katmanı (segment boyunca, yumuşak giriş/çıkış)
        inputs += ["-loop", "1", "-t", str(seg_dur), "-i", ov[tkey]]
        fade_in = 0.25 if tkey != "end" else 0.35
        filters.append(f"[{idx}:v]format=rgba,fade=t=in:st=0.1:d={fade_in}:alpha=1,fade=t=out:st={seg_dur - 0.35}:d=0.3:alpha=1[t{si}]")
        idx += 1
        filters.append(f"[{src}][t{si}]overlay=0:0:format=auto,format=yuv420p,trim=duration={seg_dur},setpts=PTS-STARTPTS,fps=60,settb=1/60[s{si}]")
        labels.append((f"s{si}", seg_dur))
    # --- geçişler (xfade) ---
    cur, cur_dur = labels[0]
    for i, (lab, d) in enumerate(labels[1:], 1):
        off = cur_dur - F
        filters.append(f"[{cur}][{lab}]xfade=transition=fade:duration={F}:offset={off:.3f}[x{i}]")
        cur, cur_dur = f"x{i}", cur_dur + d - F
    total = cur_dur
    filters.append(f"[{cur}]trim=duration=30,setpts=PTS-STARTPTS,format=yuv420p[vout]")
    # --- ses: oyunun kendi müziği (20 sn döngü) + başarı efekti ---
    inputs += ["-stream_loop", "1", "-i", a.music]
    mi = idx; idx += 1
    inputs += ["-i", os.path.join(a.sfx, "sfx_success.wav")]
    si_ = idx; idx += 1
    win_at = sum(p[2] for p in PLAN[:4]) - 4 * F + 2.2  # 5. segmentte kazanma anı (çekim: once=2.2)
    filters.append(f"[{mi}:a]atrim=0:30,afade=t=in:st=0:d=0.6,afade=t=out:st=28.3:d=1.7,volume=0.9[mus]")
    filters.append(f"[{si_}:a]adelay={int(win_at * 1000)}|{int(win_at * 1000)},volume=0.8[win]")
    filters.append(f"[mus][win]amix=inputs=2:duration=first:normalize=0,aresample=48000[aout]")
    cmd = ["ffmpeg", "-y", "-loglevel", "error"] + inputs + ["-filter_complex", ";".join(filters),
           "-map", "[vout]", "-map", "[aout]", "-t", "30", "-r", "60",
           "-c:v", "libx264", "-profile:v", "high", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p",
           "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-movflags", "+faststart", a.out]
    run(cmd)
    print(f"ok {a.out} (kurgu toplamı {total:.2f} sn, 30 sn'ye kırpıldı)")

if __name__ == "__main__":
    main()
