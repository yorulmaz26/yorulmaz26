"""Bölüm şeridi çizici: bir bölümün tamamını (zemin, engeller, toplanabilirler, kural aşamaları) ve analiz
aracının bulduğu oynanış yolunu (her zıplamanın zamanlama payıyla) tek bir PNG'de gösterir. İnceleme içindir.

Kullanım:
  ./gradlew :core:analyzeLevels -Pdump=<dump klasörü>          # yörünge + ölçüm JSON'ları
  python3 tools/level_strip.py assets <dump klasörü> <çıktı klasörü> [1-500]
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFont

PX = 24            # piksel / karo
ROW_UNITS = 60     # bir satırdaki karo
Y0, Y1 = -1.6, 10.6
ROW_H = int((Y1 - Y0) * PX)
HEAD = 22
PAGE_ROWS = 4
CEIL = 9.0
SPIKE_VIS, SPIKE_HIT, SAW_HIT, RUN_SPEED = 0.92, 0.78, 0.86, 5.2
RULE_TR = {'NORMAL': 'Normal', 'REVERSE_GRAVITY': 'Ters Yer Çekimi', 'FAST': 'Hızlı', 'ICE': 'Buz', 'GHOST': 'Hayalet', 'BOUNCY': 'Zıplayan',
           'SMALL': 'Küçük', 'REVERSE_CONTROL': 'Ters Kontrol', 'SLOW': 'Yavaş', 'SHIFTING': 'Kayan Platform'}
RULE_COL = {'NORMAL': (236, 246, 236), 'REVERSE_GRAVITY': (226, 232, 255), 'FAST': (255, 238, 220), 'ICE': (222, 244, 255), 'GHOST': (240, 228, 255),
            'BOUNCY': (255, 232, 244), 'SMALL': (232, 255, 246), 'REVERSE_CONTROL': (255, 228, 228), 'SLOW': (246, 246, 222), 'SHIFTING': (255, 246, 214)}


def font(size):
    for p in ('assets/fonts/Nunito-ExtraBold.ttf', os.path.join(os.path.dirname(__file__), '..', 'assets', 'fonts', 'Nunito-ExtraBold.ttf')):
        if os.path.exists(p): return ImageFont.truetype(p, size)
    return ImageFont.load_default()


F11, F13, F16 = font(11), font(13), font(16)


def blend(cols):
    n = len(cols)
    return tuple(sum(c[i] for c in cols) // n for i in range(3))


def draw_level(L, D, out_path):
    length = L['goalX'] + 6
    rows = int(math.ceil((length + 4) / ROW_UNITS))
    W = ROW_UNITS * PX
    H = 46 + rows * (ROW_H + HEAD)
    im = Image.new('RGB', (W, H), (255, 255, 255))
    d = ImageDraw.Draw(im, 'RGBA')
    phases = L['phases']
    kinds = {'TUTORIAL': 'Eğitim', 'NORMAL': 'Normal', 'CHALLENGE': 'Meydan Okuma', 'FINAL': 'Final', 'GRAND_FINAL': 'Büyük Final'}
    head = 'Bölüm %d · %s · %s · zorluk %d' % (L['id'], L['name'], kinds.get(L['kind'], L['kind']), L['difficulty'])
    if D:
        ws = [j['w'] for j in D['jumps']] or [49]
        hs = [j['h'] for j in D['jumps']] or [49]
        head += ' · ölçülen %.1f · tamamlama usta/orta/acemi %%%d/%%%d/%%%d · %d zıplama · en dar pay %d tick (tutma %d)' % (
            D['score'], D['p'][0] * 100, D['p'][1] * 100, D['p'][2] * 100, len(D['jumps']), min(ws), min(hs))
    d.text((8, 4), head, font=F16, fill=(20, 30, 60))
    d.text((8, 26), 'Çizgi: oynanış yolu · üçgen: zıplama (rakam = zamanlama payı, tick; kırmızı < 7, turuncu < 11, yeşil ≥ 11) · alttaki kırmızı çentikler: '
                    'orta oyuncunun öldüğü yerler', font=F11, fill=(90, 90, 110))

    def sx(x, row): return (x - (row * ROW_UNITS - 2)) * PX
    def sy(y, row): return 46 + row * (ROW_H + HEAD) + HEAD + (Y1 - min(max(y, Y0), Y1)) * PX

    for row in range(rows):
        rx0 = row * ROW_UNITS - 2; rx1 = rx0 + ROW_UNITS
        top = 46 + row * (ROW_H + HEAD)
        # aşama bantları
        for i, ph in enumerate(phases):
            a = ph['x'] if i > 0 else -99
            b = phases[i + 1]['x'] if i + 1 < len(phases) else 1e9
            if b < rx0 or a > rx1: continue
            col = blend([RULE_COL.get(r, (240, 240, 240)) for r in ph['rules']])
            d.rectangle((max(0, sx(a, row)), top + HEAD, min(W, sx(b, row)), top + HEAD + ROW_H), fill=col + (255,))
            if rx0 <= ph['x'] <= rx1 or (a <= rx0 < b):
                x = max(2, sx(ph['x'], row))
                d.line((x, top + HEAD, x, top + HEAD + ROW_H), fill=(120, 120, 160, 255), width=1)
                d.text((x + 3, top + 4), ' + '.join(RULE_TR.get(r, r) for r in ph['rules']), font=F13, fill=(40, 40, 110))
        d.text((W - 86, top + 6), 'x %d–%d' % (rx0, rx1), font=F11, fill=(130, 130, 130))
        # zemin çizgileri
        for yy in (0, CEIL):
            d.line((0, sy(yy, row), W, sy(yy, row)), fill=(0, 0, 0, 28), width=1)

        def rect(x, y, w, h, fill, outline=None):
            if x + w < rx0 or x > rx1: return
            d.rectangle((sx(x, row), sy(y + h, row), sx(x + w, row), sy(y, row)), fill=fill, outline=outline)

        for s in L['solids']:
            t = s.get('type', 'GROUND')
            x, y, w, h = s['x'], s['y'], s['w'], s['h']
            ax, ay = s.get('ax', 0), s.get('ay', 0)
            if t in ('GROUND', 'CEILING'): rect(x, y, w, h, (150, 112, 78, 255), (96, 70, 46, 255))
            elif t == 'BLOCK': rect(x, y, w, h, (112, 116, 140, 255), (60, 62, 84, 255))
            elif t == 'LOW': rect(x, y, w, h, (86, 96, 110, 255), (40, 46, 56, 255))
            elif t == 'GHOST': rect(x, y, w, h, (176, 120, 236, 150), (110, 60, 190, 255))
            elif t == 'SHIFT':
                rect(x, y - abs(ay), w, h + 2 * abs(ay), (255, 200, 60, 50))
                rect(x, y, w, h, (240, 176, 40, 255), (150, 100, 10, 255))
            elif t == 'MOVE':
                rect(x - abs(ax), y - abs(ay), w + 2 * abs(ax), h + 2 * abs(ay), (70, 150, 240, 45))
                rect(x, y, w, h, (70, 150, 240, 255), (30, 80, 170, 255))
        for hz in L.get('hazards', []):
            x, y, w = hz['x'], hz['y'], hz['w']
            if hz['type'] == 'SPIKE':
                if x + w < rx0 or x > rx1: continue
                dr = hz.get('dir', 1)
                n = max(2, int(w / 0.5 + 0.5)); cw = w / n
                for i in range(n):
                    bx = x + i * cw
                    d.polygon([(sx(bx, row), sy(y, row)), (sx(bx + cw, row), sy(y, row)), (sx(bx + cw / 2, row), sy(y + dr * SPIKE_VIS, row))], fill=(226, 44, 52, 255), outline=(110, 10, 20, 255))
                yb, yt = sorted((y, y + dr * SPIKE_HIT))
                d.rectangle((sx(x + 0.08, row), sy(yt, row), sx(x + w - 0.08, row), sy(yb, row)), outline=(110, 10, 20, 160))
            else:
                r = w
                ax, ay = hz.get('ax', 0), hz.get('ay', 0)
                cx, cy = x, y
                if hz.get('period', 0) > 0:
                    if cx + r + abs(ax) >= rx0 and cx - r - abs(ax) <= rx1:
                        d.line((sx(cx - ax, row), sy(cy - ay, row), sx(cx + ax, row), sy(cy + ay, row)), fill=(226, 44, 52, 120), width=2)
                    s0 = math.sin(hz.get('phase', 0))
                    cx, cy = x + ax * s0, y + ay * s0  # oyuncu hizadayken (kilitli testere) ya da t = 0 anındaki konum
                if cx + r < rx0 or cx - r > rx1: continue
                d.ellipse((sx(cx - r, row), sy(cy + r, row), sx(cx + r, row), sy(cy - r, row)), fill=(226, 44, 52, 235), outline=(110, 10, 20, 255))
                hr = r * SAW_HIT
                d.ellipse((sx(cx - hr, row), sy(cy + hr, row), sx(cx + hr, row), sy(cy - hr, row)), outline=(255, 255, 255, 200))
        for p in L.get('pads', []):
            dr = p.get('dir', 1)
            if p['type'] == 'SPRING':
                y0, y1 = sorted((p['y'], p['y'] + dr * 0.9)); rect(p['x'], y0, p['w'], y1 - y0, (236, 72, 153, 255), (120, 20, 80, 255))
            else:
                y0, y1 = sorted((p['y'], p['y'] + dr * 0.4)); rect(p['x'], y0, p['w'], y1 - y0, (30, 200, 200, 255), (10, 110, 120, 255))
        for cp in L.get('checkpoints', []):
            if rx0 <= cp <= rx1:
                d.line((sx(cp, row), sy(0, row), sx(cp, row), sy(2.1, row)), fill=(30, 160, 60, 255), width=3)
                d.polygon([(sx(cp, row), sy(2.1, row)), (sx(cp + 0.9, row), sy(1.8, row)), (sx(cp, row), sy(1.5, row))], fill=(60, 200, 90, 255))
        g = L['goalX']
        if rx0 <= g <= rx1 + 2:
            d.rectangle((sx(g - 1.3, row), sy(3.9, row), sx(g + 1.3, row), sy(0, row)), fill=(255, 255, 255, 200), outline=(40, 40, 160, 255), width=3)
            d.text((sx(g - 1.1, row), sy(3.2, row)), 'KAPI', font=F13, fill=(40, 40, 160))
        for c in L.get('coins', []):
            if rx0 <= c['x'] <= rx1: d.ellipse((sx(c['x'], row) - 4, sy(c['y'], row) - 4, sx(c['x'], row) + 4, sy(c['y'], row) + 4), fill=(250, 196, 30, 255), outline=(170, 110, 0, 255))
        for c in L.get('stars', []):
            if rx0 <= c['x'] <= rx1:
                cx, cy = sx(c['x'], row), sy(c['y'], row)
                pts = [(cx + (11 if k % 2 == 0 else 5) * math.sin(k * math.pi / 5), cy - (11 if k % 2 == 0 else 5) * math.cos(k * math.pi / 5)) for k in range(10)]
                d.polygon(pts, fill=(255, 214, 40, 255), outline=(150, 100, 0, 255))
        c = L.get('piece')
        if c and rx0 <= c['x'] <= rx1: d.rectangle((sx(c['x'], row) - 8, sy(c['y'], row) - 8, sx(c['x'], row) + 8, sy(c['y'], row) + 8), fill=(150, 100, 230, 255), outline=(70, 30, 140, 255))
        for c in L.get('powerups', []):
            if rx0 <= c['x'] <= rx1:
                d.ellipse((sx(c['x'], row) - 9, sy(c['y'], row) - 9, sx(c['x'], row) + 9, sy(c['y'], row) + 9), fill=(60, 220, 200, 230), outline=(0, 110, 110, 255))
                d.text((sx(c['x'], row) - 4, sy(c['y'], row) - 7), 'G', font=F11, fill=(0, 60, 70))
        for ft in L.get('features', []):
            if rx0 <= ft['x'] <= rx1: d.text((sx(ft['x'], row), top + HEAD + ROW_H - 14), ft['p'].lower(), font=F11, fill=(70, 70, 70))
        if D:
            pts = [(sx(x, row), sy(y, row)) for x, y in D['traj'] if rx0 - 1 <= x <= rx1 + 1]
            if len(pts) > 1: d.line(pts, fill=(20, 60, 200, 255), width=2)
            for j in D['jumps']:
                if not (rx0 <= j['x'] <= rx1): continue
                w = min(j['w'], j['h'])
                col = (220, 30, 40) if w < 7 else (240, 140, 20) if w < 11 else (30, 150, 60)
                x, y = sx(j['x'], row), sy(j['y'], row)
                d.polygon([(x - 6, y + 9), (x + 6, y + 9), (x, y - 3)], fill=col + (255,), outline=(0, 0, 0, 255))
                d.text((x - 6, y - 20), str(j['w']) if j['h'] >= j['w'] else '%d/%d' % (j['w'], j['h']), font=F13, fill=col)
            for x in D.get('deaths', []):
                if rx0 <= x <= rx1: d.line((sx(x, row), top + HEAD + ROW_H - 6, sx(x, row), top + HEAD + ROW_H), fill=(220, 0, 0, 200), width=2)
    # Uzun bölümler okunaklı kalsın diye en çok PAGE_ROWS satırlık sayfalara bölünür: lv_XXX.png, lv_XXX_s2.png ...
    pages = []
    per = PAGE_ROWS * (ROW_H + HEAD)
    n = int(math.ceil(rows / PAGE_ROWS))
    for k in range(n):
        y0 = 46 + k * per
        y1 = min(H, y0 + per)
        page = Image.new('RGB', (W, 46 + (y1 - y0)), (255, 255, 255))
        page.paste(im.crop((0, 0, W, 46)), (0, 0))
        page.paste(im.crop((0, y0, W, y1)), (0, 46))
        if n > 1: ImageDraw.Draw(page).text((W - 150, 26), 'sayfa %d / %d' % (k + 1, n), font=F13, fill=(160, 40, 40))
        path = out_path if k == 0 else out_path.replace('.png', '_s%d.png' % (k + 1))
        page.save(path, optimize=True)
        pages.append(path)
    return pages


def main():
    assets, dump, out = sys.argv[1], sys.argv[2], sys.argv[3]
    lo, hi = (int(v) for v in (sys.argv[4] if len(sys.argv) > 4 else '1-500').split('-'))
    os.makedirs(out, exist_ok=True)
    n = 0
    for m in range(1, 11):
        f = os.path.join(assets, 'levels', 'map_%02d.json' % m)
        if not os.path.exists(f): continue
        for L in json.load(open(f))['levels']:
            if not (lo <= L['id'] <= hi): continue
            dp = os.path.join(dump, 'lv_%03d.json' % L['id'])
            D = json.load(open(dp)) if os.path.exists(dp) else None
            draw_level(L, D, os.path.join(out, 'lv_%03d.png' % L['id']))
            n += 1
    print('%d bölüm şeridi -> %s' % (n, out))


if __name__ == '__main__':
    main()
