"""Bütün grafik varlıkları üretir: python3 tools/art/gen_art.py <assets_dir> <android_res_dir>"""
import os, sys, time
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from common import Packer, Canvas, hexc, shade
import ui_art, game_art, theme_art, user_art

assets = sys.argv[1]
res = sys.argv[2]
font = os.path.join(assets, 'fonts', 'Nunito-ExtraBold.ttf')
ui_art.FONT = font
out = os.path.join(assets, 'atlas')
os.makedirs(out, exist_ok=True)
t0 = time.time()
n_user = user_art.init(os.path.join(assets, '..', 'art_input'))
print('kullanıcı görseli:', n_user)

# ---------------- kullanıcı görselleri: atlas bölge adı -> işlenmiş görsel
BTN = {'yellow': 'yellow', 'orange': 'orange', 'pink': 'pink', 'turq': 'turquoise', 'green': 'green', 'blue': 'blue',
       'purple': 'purple', 'white': 'white', 'gray': 'grey', 'red': 'red'}
ICON = {'coin': 'coin', 'gem': 'gem', 'star': 'star', 'star_empty': 'star_empty', 'heart': 'heart', 'heart_empty': 'heart_empty',
        'trophy': 'trophy', 'gift': 'gift', 'calendar': 'calendar', 'scroll': 'missions', 'puzzle': 'collection', 'person': 'profile',
        'bag': 'shop', 'map': 'map', 'infinity': 'endless', 'crown': 'crown', 'bolt': 'lightning'}
MECH = {'m_normal': 'normal', 'm_gravity': 'gravity', 'm_fast': 'fast', 'm_ice': 'ice', 'm_ghost': 'ghost', 'm_bouncy': 'bouncy',
        'm_small': 'small', 'm_control': 'control', 'm_slow': 'slow', 'm_shift': 'shift'}
POWER = {'p_shield': 'shield', 'p_magnet': 'magnet', 'p_double': 'double_jump', 'p_invincible': 'invincible', 'p_life': 'extra_life',
         'p_coinx2': 'coin_x2', 'p_finder': 'star_finder', 'p_guard': 'checkpoint_guard'}
OBJ = {'spike': ('spike.png', 256), 'saw': ('saw.png', 384), 'spring_0': ('spring_idle.png', 256), 'spring_1': ('spring_pressed.png', 256),
       'boost': ('boost.png', 512), 'flag_0': ('checkpoint_off.png', 384), 'flag_1': ('checkpoint_on.png', 384), 'goal': ('goal.png', 640),
       'chaser': ('chaos_wall.png', 384), 'tile_ghost': ('platform_ghost.png', 256), 'tile_shift': ('platform_shift.png', 256),
       'tile_move': ('platform_move.png', 256), 'star_item': ('star.png', 256), 'piece_item': ('piece.png', 256)}
# Not: kullanıcının altın sayfası ve güçlendirme baloncuğu yarı saydam/soluk geldi; mevcut çizimler kullanılır.
COINS = None


def override(atlas, name, img, split):
    if atlas == 'ui' and name.startswith('btn_') and name[4:] in BTN:
        return user_art.button_patch('button_%s.png' % BTN[name[4:]])
    if atlas == 'ui' and name == 'panel':
        return user_art.panel_patch()
    if name.startswith('g_') and user_art.has('glyph_%s.png' % name[2:]):
        return user_art.ui_plain('glyph_%s.png' % name[2:], 96), split
    if name.startswith('b_'):
        k = name[2:]
        f = ('icon_%s.png' % ICON[k]) if k in ICON else ('mech_%s.png' % MECH[k]) if k in MECH else ('power_%s.png' % POWER[k]) if k in POWER else None
        if f and user_art.has(f):
            return user_art.ui_plain(f, 160 if atlas == 'ui' else 128), split
    if atlas == 'ui' and name == 'logo' and user_art.has('game_logo.png'):
        im = user_art.obj('game_logo.png', 1000)
        return im, None
    if atlas == 'game' and name in OBJ:
        f, sz = OBJ[name]
        im = user_art.obj(f, sz)
        if im is not None: return im, None
    if atlas == 'game' and name.startswith('coin_') and COINS:
        return COINS[int(name[5:])], None
    return None


Packer.OVERRIDE = override

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
for nm, col in [('yellow', '#FFC93C'), ('orange', '#FF9F43'), ('pink', '#FF7EB6'), ('turq', '#38C9C2'), ('gray', '#AFBBCC'), ('green', '#5CC96B')]:
    p.add('node_' + nm, ui_art.level_node(col))
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
from PIL import ImageOps
bgdir = os.path.join(assets, 'bg'); os.makedirs(bgdir, exist_ok=True)
for mid, th in theme_art.THEMES.items():
    tp = Packer('theme_%02d' % mid, 2048, filt='MipMapLinearLinear,Linear')
    top = user_art.ground_top(mid)
    fill = user_art.ground_fill(mid)
    tp.add('top', top if top else theme_art.tile_top(th, mid * 3), extrude=2)
    tp.add('fill', fill if fill else theme_art.tile_fill(th, mid * 3 + 1), extrude=2)
    ceil = ImageOps.flip(top) if top else theme_art.tile_top(th, mid * 3 + 2, ceiling=True)
    tp.add('ceil', ceil, extrude=2)
    parts = user_art.platform_parts(mid)
    if parts:
        for k, v in zip(('l', 'm', 'r'), parts): tp.add('block_' + k, v, extrude=1)
    else:
        for k, v in theme_art.block_parts(th).items(): tp.add('block_' + k, v, extrude=1)
    tp.add('low', ceil if top else theme_art.low_tile(th), extrude=2)
    props = user_art.decor_props(mid)
    if props:
        for i in range(6): tp.add('decor_%d' % i, props[i % len(props)])
    else:
        for i, d in enumerate(th['decor']): tp.add('decor_%d' % i, theme_art.decor(d, th, mid * 10 + i))
    tp.add('sky', theme_art.sky_strip(th))
    tp.write(out)
    sk = user_art.sky(mid)
    skp = os.path.join(bgdir, '%02d_sky.jpg' % mid)
    if sk: sk.save(skp, quality=92, optimize=True)
    elif os.path.exists(skp): os.remove(skp)
    for layer in ('far', 'mid'):
        r = user_art.parallax(mid, layer)
        if r:
            r[0].save(os.path.join(bgdir, '%02d_%s.jpg' % (mid, layer)), quality=90, optimize=True)
            r[1].save(os.path.join(bgdir, '%02d_%s_a.png' % (mid, layer)), optimize=True)
        else:
            img = theme_art.background(th, layer, mid).resize((2048, 1024), Image.LANCZOS)
            img.convert('RGB').save(os.path.join(bgdir, '%02d_%s.jpg' % (mid, layer)), quality=90)
            img.split()[3].save(os.path.join(bgdir, '%02d_%s_a.png' % (mid, layer)), optimize=True)
    print('tema', mid, th['name'], 'kullanıcı' if top else 'programatik')

# ---------------- karakter kareleri
import progress_skins
# her görünüm ayrı küçük atlasta: oyunda yalnızca seçili karakter yüklenir
for f in os.listdir(out):
    if f.startswith('heroes') or f.startswith('hero_') or f.startswith('hero_thumbs'): os.remove(os.path.join(out, f))
nh = 0
thumbs = Packer('hero_thumbs', 1024, filt='Linear,Linear')
for sid in progress_skins.SKINS:
    fr = user_art.hero_frames(sid)
    if not fr: continue
    nh += 1
    # karakterde mipmap yok: üç-doğrusal filtre küçültmede yarı çözünürlüklü kopyayı karıştırıp bulanıklaştırıyordu
    hp = Packer('hero_' + sid, 2048, filt='Linear,Linear')
    for i, f in enumerate(fr): hp.add('f%d' % i, f)
    hp.write(out)
    idle = fr[15]
    thumbs.add('t_' + sid, idle.resize((max(1, int(idle.width * 120 / idle.height)), 120), Image.LANCZOS))
    thumbs.add('h_' + sid, fr[13].resize((max(1, int(fr[13].width * 120 / fr[13].height)), 120), Image.LANCZOS))
if nh: thumbs.write(out)
print('karakter görünümü:', nh)

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

USER_ICON = user_art.load('play_icon.png') if user_art.has('play_icon.png') else None


def round_mask(im):
    from PIL import ImageDraw
    m = Image.new('L', (im.width * 4, im.height * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, m.width - 1, m.height - 1), fill=255)
    im = im.copy(); im.putalpha(m.resize(im.size, Image.LANCZOS)); return im


def rounded(im, r=0.22):
    from PIL import ImageDraw
    m = Image.new('L', (im.width * 4, im.height * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, m.width - 1, m.height - 1), radius=int(m.width * r), fill=255)
    im = im.copy(); im.putalpha(m.resize(im.size, Image.LANCZOS)); return im


for dens, sz in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
    dd = os.path.join(res, 'mipmap-' + dens); os.makedirs(dd, exist_ok=True)
    if USER_ICON:
        base = USER_ICON.resize((sz, sz), Image.LANCZOS)
        rounded(base).save(os.path.join(dd, 'ic_launcher.png'))
        round_mask(base).save(os.path.join(dd, 'ic_launcher_round.png'))
    else:
        launcher(sz).save(os.path.join(dd, 'ic_launcher.png'))
        launcher(sz, True).save(os.path.join(dd, 'ic_launcher_round.png'))
docs = os.path.join(assets, '..', 'docs')
if USER_ICON: USER_ICON.resize((512, 512), Image.LANCZOS).convert('RGB').save(os.path.join(docs, 'play_icon_512.png'))
else: launcher(512).save(os.path.join(docs, 'play_icon_512.png'))
if user_art.has('feature_graphic.png'): user_art.load('feature_graphic.png').convert('RGB').resize((1024, 500)).save(os.path.join(docs, 'play_feature_graphic.png'))
print('bitti %.1f sn' % (time.time() - t0))
