"""Fragmanları birleştirir: oyunun sentezlenmiş müziği + olay efektleri, dikey/yatay/sosyal sürümler, altyazılar.
Kullanım: python3 make_trailers.py <media_dir> <store_dir> <out_dir>"""
import json, os, subprocess, sys, wave
import numpy as np

media, store, out = sys.argv[1:4]
A = os.path.join(media, 'trailer', 'audio')
RATE = 44100

def load(name):
    with wave.open(os.path.join(A, name)) as w:
        d = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        sr = w.getframerate()
    x = np.arange(0, len(d) * RATE / sr) * sr / RATE
    return np.interp(x, np.arange(len(d)), d).astype(np.float32)

def mix(events, dur=30.0):
    n = int(dur * RATE)
    music = load('music_theme9.wav')
    m = np.tile(music, int(np.ceil(n / len(music))) + 1)[:n] * 0.85
    fade = np.ones(n, np.float32)
    fi = int(0.6 * RATE); fo = int(1.6 * RATE)
    fade[:fi] = np.linspace(0, 1, fi); fade[-fo:] = np.linspace(1, 0, fo)
    sfx = np.zeros(n, np.float32)
    vol = {'flap': 0.32, 'score': 0.55, 'coin': 0.6, 'record': 0.85, 'swipe': 0.5, 'click': 0.5, 'start': 0.8}
    cache = {}
    last = {}
    for e in events:
        s = e['s']
        if s == 'flap' and e['t'] - last.get(s, -1) < 0.09: continue
        last[s] = e['t']
        if s not in cache: cache[s] = load(s + '.wav')
        clip = cache[s] * vol.get(s, 0.5)
        i = int(e['t'] * RATE)
        j = min(n, i + len(clip)); sfx[i:j] += clip[:j - i]
    y = m * fade + sfx
    y = np.tanh(y * 1.1) * 0.89
    st = np.stack([y, y], axis=1)
    return (st * 32767).astype(np.int16)

def save_wav(path, data):
    with wave.open(path, 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(RATE); w.writeframes(data.tobytes())

def run(cmd):
    subprocess.run(cmd, check=True)

def srt_time(t):
    ms = int(round(t * 1000)); h, ms = divmod(ms, 3600000); m, ms = divmod(ms, 60000); s, ms = divmod(ms, 1000)
    return f'{h:02}:{m:02}:{s:02},{ms:03}'

beats = [0, 3, 8, 15, 22, 27, 30]
for lang in ['tr', 'en']:
    d = os.path.join(media, 'trailer', lang)
    j = json.load(open(os.path.join(store, lang + '.json'), encoding='utf-8'))
    events = json.load(open(os.path.join(d, 'events.json')))
    wav = os.path.join(d, 'mix.wav'); save_wav(wav, mix(events))
    variants = {'play': 'closing_play.mp4', 'social_pre': 'closing_social_pre.mp4', 'social_post': 'closing_social_post.mp4'}
    names = {'tr': {'play': 'trailer_tr_vertical.mp4', 'social_pre': 'trailer_tr_social_maceraya_basla.mp4', 'social_post': 'trailer_tr_social_hemen_oyna.mp4'},
             'en': {'play': 'trailer_en_vertical.mp4', 'social_pre': 'trailer_en_social_start_your_adventure.mp4', 'social_post': 'trailer_en_social_play_now.mp4'}}[lang]
    for v, closing in variants.items():
        lst = os.path.join(d, f'concat_{v}.txt')
        open(lst, 'w').write(f"file '{os.path.join(d, 'main_27s.mp4')}'\nfile '{os.path.join(d, closing)}'\n")
        silent = os.path.join(d, f'silent_{v}.mp4')
        run(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy', silent])
        target = os.path.join(out, 'Vertical' if v == 'play' else 'Social_Media', names[v])
        os.makedirs(os.path.dirname(target), exist_ok=True)
        run(['ffmpeg', '-loglevel', 'error', '-y', '-i', silent, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'copy',
             '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', target])
        if v == 'play':
            # Yatay 1920x1080: ortada esnetilmemiş dikey görüntü, yanlar aynı görüntünün bulanık hâli (siyah kenar yok)
            h = os.path.join(out, 'Horizontal', f'trailer_{lang}_horizontal.mp4'); os.makedirs(os.path.dirname(h), exist_ok=True)
            run(['ffmpeg', '-loglevel', 'error', '-y', '-i', target, '-filter_complex',
                 '[0:v]scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,boxblur=28:3,eq=brightness=-0.12:saturation=1.1[bg];'
                 '[0:v]scale=-2:1080[fg];[bg][fg]overlay=(W-w)/2:0,format=yuv420p[v]',
                 '-map', '[v]', '-map', '0:a', '-c:v', 'libx264', '-preset', 'medium', '-crf', '19', '-r', '30',
                 '-c:a', 'copy', '-movflags', '+faststart', h])
    # Altyazı (SRT): videodaki yazılar ve kapanış
    caps = j['trailerCaptions']
    sub = os.path.join(out, 'Subtitles', f'trailer_{lang}.srt'); os.makedirs(os.path.dirname(sub), exist_ok=True)
    lines = []; k = 1
    for i in range(6):
        text = caps[i] if i < 5 else ('Gezegen Kaçışı – Planet Gap · Magic Lumos Software' if lang == 'tr' else 'Planet Gap – Gezegen Kaçışı · Magic Lumos Software')
        if not text.strip(): continue
        lines.append(f'{k}\n{srt_time(beats[i] + 0.1)} --> {srt_time(beats[i + 1] - 0.1)}\n{text}\n'); k += 1
    open(sub, 'w', encoding='utf-8').write('\n'.join(lines))
print('ok')
