#!/usr/bin/env python3
"""Ham CI çekimlerinden mağaza ve sosyal medya görsellerini üretir.
Kullanım: compose_store.py <ham_goruntuler_klasoru>  (ss_<dil>_0N.png, tab7_*, tab10_*)
"""
import json, os, subprocess, sys, urllib.parse
from PIL import Image

RAW = sys.argv[1]
T = '/tmp/claude-0/media'
R = '/home/user/yorulmaz26/golgen-seni-yakalamasin/RELEASE_PACKAGE'
aso = json.load(open('/tmp/claude-0/aso_all.json'))
LANGS = ['TR', 'EN', 'DE', 'IT', 'FR', 'ES']
jobs = []

def q(d): return urllib.parse.urlencode(d)

# 1) Telefon ekran görüntüleri: 8 x 6 dil, 1080x1920, başlıklı
for L in LANGS:
    caps = aso[L]['screenshotCaptions']
    for i in range(8):
        raw = f"{RAW}/ss_{L.lower()}_{i+1:02d}.png"
        h = caps[i]['headline']
        # ilk kelimeden sonraki kısmı vurgula (turkuaz) — tek kelimeyse hepsi beyaz
        parts = h.split(' ', 1)
        hh = parts[0] + (' *' + parts[1] + '*' if len(parts) > 1 else '')
        out = f"{R}/04_PlayStore/Phone_Screenshots/{L}/{L.lower()}_{i+1:02d}.png"
        jobs.append({"html": f"{T}/store/shot.html?" + q({'h': hh, 's': caps[i]['subline'], 'img': raw}), "out": out, "w": 1080, "h": 1920})

# 2) Kapak görselleri (1024x500, 24 bit, şeffaflık yok)
for L, lang in [('TR', 'tr'), ('EN', 'en')]:
    jobs.append({"html": f"{T}/store/feature.html?" + q({'lang': lang, 'tag': aso[L]['featureGraphicTagline'], 'img': f"{RAW}/ss_{lang}_01.png"}),
                 "out": f"{R}/04_PlayStore/Feature_Graphics/feature_graphic_{lang}.png", "w": 1024, "h": 500})

# 3) Sosyal medya paketi (TR/EN x 8 biçim)
SOC = [('instagram_post_1080x1080', 1080, 1080, ''), ('instagram_portrait_1080x1350', 1080, 1350, ''),
       ('instagram_story_1080x1920', 1080, 1920, 'cta'), ('reels_cover_1080x1920', 1080, 1920, ''),
       ('tiktok_cover_1080x1920', 1080, 1920, ''), ('youtube_thumbnail_1280x720', 1280, 720, ''),
       ('facebook_1200x630', 1200, 630, ''), ('launch_poster_1080x1920', 1080, 1920, 'soon')]
TXT = {'tr': {'cta': 'Hemen Oyna!', 'soon': 'Çok yakında Google Play\'de'}, 'en': {'cta': 'Play Now!', 'soon': 'Coming soon to Google Play'}}
for L, lang, folder in [('TR', 'tr', 'Turkish'), ('EN', 'en', 'English')]:
    for k, (name, w, h, extra) in enumerate(SOC):
        shot = f"{RAW}/ss_{lang}_{[1, 2, 3, 1, 6, 1, 3, 1][k]:02d}.png"
        bg = f"{RAW}/ss_{lang}_{[6, 1, 3, 6, 1, 3, 6, 3][k]:02d}.png"
        jobs.append({"html": f"{T}/store/social.html?" + q({'lang': lang, 'tag': aso[L]['slogan'], 'cta': TXT[lang][extra] if extra else '', 'img': bg, 'shot': shot}),
                     "out": f"{R}/07_Social_Media/{folder}/{lang}_{name}.png", "w": w, "h": h})

for j in jobs: os.makedirs(os.path.dirname(j['out']), exist_ok=True)
jf = '/tmp/claude-0/media/store/jobs_all.json'
json.dump(jobs, open(jf, 'w'))
subprocess.run(['node', f'{T}/tools/render.js', jf], check=True, stdout=subprocess.DEVNULL)

# Şeffaflık kaldır (Play: 24 bit PNG / şeffaf olmayan)
for j in jobs:
    im = Image.open(j['out'])
    if im.mode != 'RGB': im.convert('RGB').save(j['out'], optimize=True)

# 4) Tablet ekran görüntüleri: ham, değiştirilmeden (gerçek tablet ekran ölçüsünde)
import shutil
for inch, folder in [('7', 'Tablet_7_Inch'), ('10', 'Tablet_10_Inch')]:
    for f in sorted(os.listdir(RAW)):
        if f.startswith(f'tab{inch}_'):
            Image.open(f"{RAW}/{f}").convert('RGB').save(f"{R}/04_PlayStore/{folder}/{f}", optimize=True)
print(len(jobs), 'görsel üretildi')
