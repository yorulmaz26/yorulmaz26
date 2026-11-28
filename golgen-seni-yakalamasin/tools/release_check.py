#!/usr/bin/env python3
"""RELEASE_PACKAGE otomatik kalite kontrolü: görsel ölçüleri, videolar, metin sınırları, çeviri anahtarları, dosyalar."""
import json, os, subprocess, sys, zipfile
from PIL import Image
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'RELEASE_PACKAGE')
A = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets', 'localization')
res = []
def chk(name, ok, info=''): res.append(('GEÇTİ' if ok else 'KALDI', name, info))
def img(p, size, alpha_ok=True, maxkb=None):
    if not os.path.exists(p): chk(p, False, 'yok'); return
    im = Image.open(p); im.load()
    ok = im.size == size and (alpha_ok or im.mode == 'RGB') and (maxkb is None or os.path.getsize(p) <= maxkb * 1024)
    chk(os.path.relpath(p, R), ok, f'{im.size[0]}x{im.size[1]} {im.mode} {os.path.getsize(p)//1024} KB')
img(f'{R}/04_PlayStore/App_Icon/app_icon_512.png', (512, 512), True, 1024)
for l in ['tr', 'en']: img(f'{R}/04_PlayStore/Feature_Graphics/feature_graphic_{l}.png', (1024, 500), False)
for L in ['TR', 'EN', 'DE', 'IT', 'FR', 'ES']:
    for i in range(1, 9): img(f'{R}/04_PlayStore/Phone_Screenshots/{L}/{L.lower()}_{i:02d}.png', (1080, 1920), False)
for d, s in [('Tablet_7_Inch', (1200, 1920)), ('Tablet_10_Inch', (1600, 2560))]:
    fs = sorted(os.listdir(f'{R}/04_PlayStore/{d}')); chk(f'{d} adet', len(fs) >= 4, str(len(fs)))
    for f in fs: img(f'{R}/04_PlayStore/{d}/{f}', s, False)
SOC = {'instagram_post_1080x1080': (1080, 1080), 'instagram_portrait_1080x1350': (1080, 1350), 'instagram_story_1080x1920': (1080, 1920), 'reels_cover_1080x1920': (1080, 1920), 'tiktok_cover_1080x1920': (1080, 1920), 'youtube_thumbnail_1280x720': (1280, 720), 'facebook_1200x630': (1200, 630), 'launch_poster_1080x1920': (1080, 1920)}
for lang, folder in [('tr', 'Turkish'), ('en', 'English')]:
    for k, s in SOC.items(): img(f'{R}/07_Social_Media/{folder}/{lang}_{k}.png', s, False)
for root, _, files in os.walk(f'{R}/03_Logos'):
    for f in files:
        im = Image.open(os.path.join(root, f)); chk(os.path.relpath(os.path.join(root, f), R), im.size[0] >= 1024, f'{im.size} {im.mode}')
def video(p, w, h):
    out = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=codec_name,width,height,r_frame_rate:format=duration', '-of', 'json', p], capture_output=True, text=True).stdout
    j = json.loads(out); v = [s for s in j['streams'] if s.get('width')][0]; a = [s for s in j['streams'] if not s.get('width')]
    d = float(j['format']['duration'])
    ok = v['codec_name'] == 'h264' and (v['width'], v['height']) == (w, h) and a and a[0]['codec_name'] == 'aac' and 29.9 <= d <= 30.1 and v['r_frame_rate'] in ('60/1', '30/1')
    chk(os.path.relpath(p, R), ok, f"{v['codec_name']} {v['width']}x{v['height']} {v['r_frame_rate']} {a[0]['codec_name'] if a else '-'} {d:.2f}s")
for p, w, h in [('Turkish/trailer_tr_vertical.mp4', 1080, 1920), ('English/trailer_en_vertical.mp4', 1080, 1920), ('Horizontal/trailer_tr_horizontal.mp4', 1920, 1080), ('Horizontal/trailer_en_horizontal.mp4', 1920, 1080)] + [('Social_Media/' + f, 1080, 1920) for f in sorted(os.listdir(f'{R}/05_Trailers/Social_Media')) if f.endswith('.mp4')]:
    video(f'{R}/05_Trailers/{p}', w, h)
for l in ['tr', 'en']: chk(f'altyazı {l}', os.path.getsize(f'{R}/05_Trailers/Subtitles/trailer_{l}.srt') > 50)
LIM = {'title': 30, 'shortDescription': 80, 'longDescription': 4000}
for L in ['TR', 'EN', 'DE', 'IT', 'FR', 'ES']:
    x = json.load(open(f'{R}/06_Store_Descriptions/{L}/store_listing.json'))
    for k, m in LIM.items(): chk(f'{L} {k} <= {m}', len(x[k]) <= m, str(len(x[k])))
keys = {l: set(json.load(open(f'{A}/{l}.json'))) for l in ['tr', 'en', 'de', 'it', 'fr', 'es']}
miss = sum(len(keys['tr'] ^ keys[l]) for l in keys); chk('eksik/fazla çeviri anahtarı', miss == 0, f'{len(keys["tr"])} anahtar x 6 dil, fark={miss}')
for f in ['01_Game_Build/APK/Doppelglow_GolgenSeniYakalamasin_1.1.0.apk', '01_Game_Build/AAB/Doppelglow_GolgenSeniYakalamasin_1.1.0.aab', '02_Game_Name_Research/Final_Name.md', '09_Privacy_And_Legal/privacy_policy_tr.md', '09_Privacy_And_Legal/privacy_policy_en.md', '09_Privacy_And_Legal/data_safety_form_draft.md', '10_Release_Checklist/RELEASE_CHECKLIST.md']:
    chk('dosya ' + f, os.path.exists(f'{R}/{f}'))
for z in sorted(os.listdir(f'{R}/ZIP')):
    p = f'{R}/ZIP/{z}'; ok = zipfile.ZipFile(p).testzip() is None and os.path.getsize(p) <= 30 * 1024 * 1024
    chk('ZIP ' + z, ok, f'{os.path.getsize(p)/1048576:.1f} MB')
apk = zipfile.ZipFile(f'{R}/01_Game_Build/APK/Doppelglow_GolgenSeniYakalamasin_1.1.0.apk').namelist()
chk('APK native kütüphane yok (16 KB kuralı etkilemez)', not any(n.endswith('.so') for n in apk))
chk('APK 6 dil dosyası', all(f'assets/localization/{l}.json' in apk for l in keys))
fails = [r for r in res if r[0] != 'GEÇTİ']
out = ['# Yayın paketi otomatik kontrolü', '', f'Toplam {len(res)} kontrol: {len(res)-len(fails)} geçti, {len(fails)} kaldı.', '', '| Sonuç | Kontrol | Ayrıntı |', '|---|---|---|'] + [f'| {a} | {b} | {c} |' for a, b, c in res]
open(f'{R}/08_Test_Reports/release_package_check.md', 'w').write('\n'.join(out) + '\n')
print(out[2]); [print(f) for f in fails]
