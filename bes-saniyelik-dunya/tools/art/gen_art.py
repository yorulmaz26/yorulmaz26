"""Bütün grafik varlıkları üretir: python3 tools/art/gen_art.py <assets_dir> <android_res_dir>"""
import os, sys, time
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from common import Packer, Canvas, hexc, shade
import ui_art, game_art, theme_art

assets = sys.argv[1]
res = sys.argv[2]
font = os.path.join(assets, 'fonts', 'Nunito-ExtraBold.ttf')
ui_art.FONT = font
out = os.path.join(assets, 'atlas')
os.makedirs(out, exist_ok=True)
t0 = time.time()

# ---------------- UI
p = Packer('ui', 2048, filt='Linear,Linear')
for name, col in ui_art.BUTTON_COLORS.items():
    img, split = ui_art.button(col)
    p.add('btn_' + name, img, split)
for name, col, border in [('panel', '#FFFFFF', '#E3ECF7'), ('panel_blue', '#EAF6FF', '#CBE6FA'), ('panel_yellow', '#FFF8DD', '#FFE8A3'), ('panel_pink', '#FFF0F5', '#FFD3E2')]:
    img, split = ui_art.panel(color=col, border=border)
    p.add(name, img, split)
for name, w, h, r, col, oc, ow in [('bar_bg', 64, 40, 18, '#E3ECF7', None, 0), ('bar_fill', 64, 40, 18, '#73D65C', None, 0), ('bar_fill_y', 64, 40, 18, '#FFC93C', None, 0),
                                  ('bar_fill_b', 64, 40, 18, '#6FB7F5', None, 0), ('chip', 80, 64, 30, '#FFFFFF', '#E3ECF7', 3), ('chip_dark', 80, 64, 30, (37, 56, 88, 120), None, 0),
                                  ('card', 96, 96, 28, '#F6FAFF', '#E3ECF7', 3), ('card_sel', 96, 96, 28, '#FFF6D6', '#FFC93C', 5), ('white', 16, 16, 2, '#FFFFFF', None, 0),
                                  ('toggle_on', 120, 64, 30, '#73D65C', None, 0), ('toggle_off', 120, 64, 30, '#C9D3E0', None, 0), ('tab', 96, 80, 28, '#FFFFFF', '#E3ECF7', 3),
                                  ('tab_sel', 96, 80, 28, '#FFD54F', '#F0B429', 3), ('banner', 128, 96, 40, (255, 255, 255, 235), '#FFFFFF', 4)]:
    img, split = ui_art.flat_rrect(w, h, r, col, oc, ow)
    p.add(name, img, split)
for g in ['play', 'pause', 'home', 'retry', 'next', 'back', 'close', 'check', 'plus', 'settings', 'music', 'music_off', 'sound', 'sound_off', 'vibrate', 'lock', 'info', 'tap', 'hold', 'run']:
    p.add('g_' + g, ui_art.glyph(g))
p.add('g_lock_gray', ui_art.glyph('lock', 96, '#9AA9C0'))
for b in ['coin', 'gem', 'star', 'star_empty', 'star_gray', 'heart', 'heart_empty', 'trophy', 'gift', 'calendar', 'scroll', 'puzzle', 'person', 'bag', 'map', 'infinity', 'crown', 'bolt',
          'm_normal', 'm_gravity', 'm_fast', 'm_ice', 'm_ghost', 'm_bouncy', 'm_small', 'm_control', 'm_slow', 'm_shift',
          'p_shield', 'p_magnet', 'p_double', 'p_invincible', 'p_life', 'p_coinx2', 'p_finder', 'p_guard']:
    p.add('b_' + b, ui_art.badge(b))
p.add('soft', ui_art.soft_circle(64))
img, split = game_art.sign_board()
p.add('sign', img, split)
p.add('logo', ui_art.logo(font))
print('ui sayfa:', p.write(out))

# ---------------- oyun
g = Packer('game', 2048, filt='MipMapLinearLinear,Linear')
g.add('ch_body', game_art.body()); g.add('ch_shine', game_art.body_shine()); g.add('ch_belly', game_art.belly())
for k in ['dots', 'stripes', 'stars', 'panel', 'swirl']: g.add('pat_' + k, game_art.pattern(k))
g.add('pat_rainbow', game_art.rainbow_pattern())
g.add('ch_eye', game_art.eye_white()); g.add('ch_pupil', game_art.pupil()); g.add('ch_eye_happy', game_art.eye_happy()); g.add('ch_eye_x', game_art.eye_x())
for m in ['smile', 'open', 'o', 'sad', 'grin']: g.add('ch_mouth_' + m, game_art.mouth(m))
g.add('ch_cheek', game_art.cheek()); g.add('ch_foot', game_art.foot())
for a in ['leaf', 'sprout', 'cap', 'bow', 'wizard', 'crown', 'cat_ears', 'bunny_ears', 'antenna', 'helmet', 'flower', 'headband', 'mushroom', 'bee_antenna', 'top_hat', 'halo', 'headphones']:
    g.add('acc_' + a, game_art.accessory(a))
g.add('acc_scarf_front', game_art.scarf_front()); g.add('acc_glasses_front', game_art.glasses_front()); g.add('acc_headphone_cups', game_art.headphone_cups()); g.add('acc_helmet_glass', game_art.helmet_glass())
for i in range(6): g.add('coin_%d' % i, game_art.coin(i))
g.add('star_item', game_art.star_item()); g.add('piece_item', game_art.piece_item()); g.add('bubble', game_art.bubble())
g.add('spike', game_art.spike()); g.add('saw', game_art.saw()); g.add('spring_0', game_art.spring(False)); g.add('spring_1', game_art.spring(True))
g.add('boost', game_art.boost()); g.add('flag_0', game_art.flag(False)); g.add('flag_1', game_art.flag(True)); g.add('goal', game_art.goal_gate()); g.add('chaser', game_art.chaser())
g.add('tile_ghost', game_art.ghost_tile()); g.add('tile_shift', game_art.shift_tile()); g.add('tile_move', game_art.move_tile())
for k in ['dust', 'sparkle', 'star', 'heart', 'bubble', 'note', 'confetti', 'crystal', 'ring', 'leaf', 'snow', 'line', 'dot']:
    g.add('pt_' + k, game_art.particle(k))
g.add('pt_glow', ui_art.soft_circle(64)); g.add('arrow', game_art.arrow())
for b in ['p_shield', 'p_magnet', 'p_double', 'p_invincible', 'p_life', 'p_coinx2', 'p_finder', 'p_guard', 'heart']:
    g.add('b_' + b, ui_art.badge(b, 96))
print('game sayfa:', g.write(out))

# ---------------- temalar
for mid, th in theme_art.THEMES.items():
    tp = Packer('theme_%02d' % mid, 2048, filt='MipMapLinearLinear,Linear')
    tp.add('top', theme_art.tile_top(th, mid * 3), extrude=2)
    tp.add('fill', theme_art.tile_fill(th, mid * 3 + 1), extrude=2)
    tp.add('ceil', theme_art.tile_top(th, mid * 3 + 2, ceiling=True), extrude=2)
    for k, v in theme_art.block_parts(th).items(): tp.add('block_' + k, v, extrude=1)
    tp.add('low', theme_art.low_tile(th), extrude=2)
    for i, d in enumerate(th['decor']): tp.add('decor_%d' % i, theme_art.decor(d, th, mid * 10 + i))
    tp.add('sky', theme_art.sky_strip(th))
    tp.write(out)
    for layer in ('far', 'mid'):
        theme_art.background(th, layer, mid).save(os.path.join(out, 'bg_%02d_%s.png' % (mid, layer)), optimize=True)
    print('tema', mid, th['name'])

# ---------------- uygulama simgesi
def launcher(sz, round_=False):
    c = Canvas(sz, sz)
    lay = c.layer()
    if round_: c.circle(sz / 2, sz / 2, sz / 2 - 1, fill='#FFFFFF', img=lay)
    else: c.rrect((0, 0, sz, sz), sz * 0.22, fill='#FFFFFF', img=lay)
    c.gradient_fill(lay, '#8FD3FF', '#58D6D0')
    k = sz / 192
    c.ellipse((10 * k, 130 * k, 182 * k, 230 * k), fill='#73D65C')
    lay2 = c.layer(); c.rrect((46 * k, 40 * k, 146 * k, 140 * k), 36 * k, fill='#FFFFFF', img=lay2)
    c.gradient_fill(lay2, '#FFE27A', '#FFB938')
    c.rrect((46 * k, 40 * k, 146 * k, 140 * k), 36 * k, outline='#E08A00', width=5 * k)
    for x in (78, 114):
        c.ellipse(((x - 11) * k, 70 * k, (x + 11) * k, 98 * k), fill='#FFFFFF', outline='#253858', width=3 * k)
        c.circle((x + 2) * k, 86 * k, 6 * k, fill='#253858')
    c.arc((80 * k, 96 * k, 112 * k, 120 * k), 20, 160, '#253858', 4 * k)
    from PIL import ImageDraw, ImageFont
    f = ImageFont.truetype(font, int(54 * k * 4))
    d = ImageDraw.Draw(c.img)
    d.text((150 * k * 4, 44 * k * 4), "5", font=f, fill=(255, 255, 255, 255), anchor='mm', stroke_width=int(7 * k * 4), stroke_fill=hexc('#FF6F91'))
    return c.done()

for dens, sz in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    dd = os.path.join(res, 'mipmap-' + dens); os.makedirs(dd, exist_ok=True)
    launcher(sz).save(os.path.join(dd, 'ic_launcher.png'))
    launcher(sz, True).save(os.path.join(dd, 'ic_launcher_round.png'))
launcher(512).save(os.path.join(assets, '..', 'docs', 'play_icon_512.png'))
print('bitti %.1f sn' % (time.time() - t0))
