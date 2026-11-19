# Kayıp Adalar referans panolarından tekil, saydam sprite'lar keser.
#
# Panolar koyu lacivert zeminli; her satırdaki nesneler sütun boşluklarından ayrılır, kutu içinde
# arka plan (koyu-mavimsi pikseller) ve alt yazılar atılır, kenarlar yumuşatılır.
# Kullanım: python3 -I tools/cut_sprites.py <tum_materyaller.png> <arayuz.png> <adalar_klasoru> <çıktı_assets>
import sys, os, json
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

MAT, UI, ISL, OUT = sys.argv[1:5]


def bgmask(A):
    r, g, b = A[..., 0].astype(int), A[..., 1].astype(int), A[..., 2].astype(int)
    mx = A[..., :3].max(axis=2)
    return (mx < 105) & (b >= r - 8) & (b >= g - 30)


def split_row(A, x0, y0, x1, y1, min_gap=4, min_w=14):
    """Satırı arka plan sütun boşluklarından nesne kutularına böler."""
    fg = ~bgmask(A[y0:y1, x0:x1])
    fg = ndi.binary_opening(fg, iterations=1)
    cols = fg.sum(axis=0) > 2
    boxes, start, gap = [], None, 0
    for i, c in enumerate(list(cols) + [False] * (min_gap + 1)):
        if c:
            if start is None: start = i
            gap = 0
        elif start is not None:
            gap += 1
            if gap >= min_gap:
                end = i - gap + 1
                if end - start >= min_w: boxes.append((x0 + start, y0, x0 + end, y1))
                start, gap = None, 0
    # Beklenenden az kutu varsa: en geniş kutuyu orta bölgedeki en seyrek sütundan böl.
    return boxes


def split_to(A, box, n):
    x0, y0, x1, y1 = box
    boxes = split_row(A, x0, y0, x1, y1)
    fg = ndi.binary_opening(~bgmask(A[y0:y1]), iterations=1)
    guard = 0
    while len(boxes) < n and guard < 20:
        guard += 1
        i = max(range(len(boxes)), key=lambda k: boxes[k][2] - boxes[k][0])
        bx0, _, bx1, _ = boxes[i]
        w = bx1 - bx0
        lo, hi = bx0 + int(w * 0.25), bx0 + int(w * 0.75)
        dens = fg[:, lo:hi].sum(axis=0)
        cut_x = lo + int(np.argmin(dens))
        boxes[i:i + 1] = [(bx0, y0, cut_x, y1), (cut_x, y0, bx1, y1)]
    return boxes


def cut(A, box, pad=2, keep_ratio=0.12):
    x0, y0, x1, y1 = box
    x0 = max(0, x0 - pad); y0 = max(0, y0 - pad); x1 = min(A.shape[1], x1 + pad); y1 = min(A.shape[0], y1 + pad)
    C = A[y0:y1, x0:x1, :3]
    fg = ~bgmask(C)
    fg = ndi.binary_opening(fg, iterations=1)
    lab, n = ndi.label(fg, structure=np.ones((3, 3)))
    if n == 0: return None
    sizes = ndi.sum(fg, lab, range(1, n + 1))
    big = int(np.argmax(sizes)) + 1
    sl = ndi.find_objects(lab)
    by0, by1 = sl[big - 1][0].start, sl[big - 1][0].stop
    keep = np.zeros_like(fg)
    for i in range(1, n + 1):
        ys = sl[i - 1][0]
        # Yazılar genelde nesnenin altında kalır: dikeyde nesneyle örtüşmeyen küçük parçalar atılır.
        overlap = min(ys.stop, by1) - max(ys.start, by0)
        if i == big or (sizes[i - 1] >= keep_ratio * sizes[big - 1] and overlap > 0.5 * (ys.stop - ys.start)) or \
                (sizes[i - 1] >= 25 and ys.stop <= by1 - 4 and overlap > 0):
            keep |= lab == i
    keep = ndi.binary_fill_holes(keep)
    alpha = Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
    rgba = Image.fromarray(C.astype(np.uint8)).convert('RGBA')
    rgba.putalpha(alpha)
    bb = rgba.getbbox()
    return rgba.crop(bb) if bb else None


def save(img, rel, scale=2):
    if img is None:
        print('BOŞ', rel); return
    if scale != 1:
        img = img.resize((img.width * scale, img.height * scale), Image.LANCZOS)
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True)
    manifest[rel] = [img.width, img.height]


manifest = {}
A = np.asarray(Image.open(MAT).convert('RGB'))
U = np.asarray(Image.open(UI).convert('RGB'))

ROWS = [
    # (pano, kutu, adlar, klasör)
    (A, (300, 28, 840, 97), ['grass', 'forest', 'rocky', 'mountain', 'snow', 'desert'], 'islands'),
    (A, (300, 110, 840, 190), ['volcano', 'crystal', 'swamp', 'ice', 'gold', 'mystic'], 'islands'),
    (A, (12, 249, 748, 347), ['castle', 'town_hall', 'research', 'trade', 'port', 'shipyard'], 'buildings'),
    (A, (762, 245, 1525, 331), ['farm', 'mill', 'lumber', 'quarry', 'mine', 'crystal_mine', 'ranch'], 'buildings'),
    (A, (12, 404, 748, 476), ['barracks', 'archer_tower', 'cannon_tower', 'defense_tower', 'wall', 'war_academy', 'siege_workshop'], 'buildings'),
    (A, (762, 398, 1525, 470), ['house_small', 'house_big', 'warehouse', 'hospital', 'worker_camp', 'temple', 'school', 'culture', 'garden'], 'buildings'),
    (A, (14, 538, 410, 587), ['worker', 'farmer', 'woodcutter', 'miner', 'carrier'], 'units'),
    (A, (14, 600, 410, 650), ['infantry', 'swordsman', 'spearman', 'shieldman', 'archer', 'cavalry'], 'units'),
    (A, (14, 664, 410, 714), ['mage', 'priest', 'engineer', 'commander', 'king'], 'units'),
    (A, (420, 538, 780, 610), ['ship_explore', 'ship_trade', 'ship_war', 'ship_siege'], 'ships'),
    (A, (790, 535, 1160, 588), ['gold', 'wood', 'stone', 'iron', 'crystal'], 'resources'),
    (A, (790, 612, 1160, 662), ['food', 'water', 'energy', 'gem', 'potion', 'map'], 'resources'),
    (A, (1178, 540, 1525, 612), ['r_build', 'r_research', 'r_upgrade', 'r_train', 'r_explore'], 'icons'),
    (A, (1178, 624, 1525, 692), ['r_trade', 'r_diplomacy', 'r_alliance', 'r_war', 'r_quest'], 'icons'),
    (A, (862, 770, 1098, 801), ['star', 'alert', 'check', 'cross', 'minus'], 'icons'),
    (A, (862, 804, 1098, 832), ['crown', 'helmet', 'shield', 'swords', 'target'], 'icons'),
    (A, (862, 834, 1098, 864), ['people', 'mail', 'trophy', 'scroll', 'lock_small'], 'icons'),
    (A, (862, 866, 1098, 896), ['lock', 'key', 'gear', 'refresh', 'trash'], 'icons'),
    (A, (440, 930, 770, 975), ['tree_oak', 'tree_pine', 'tree_autumn', 'tree_sakura', 'palm', 'tree_round'], 'nature'),
    (A, (440, 978, 770, 1015), ['bush', 'flowers', 'rocks', 'stump', 'crystals', 'stone_rune', 'stone_rune2'], 'nature'),
    (A, (780, 928, 1115, 975), ['banner_blue', 'banner_red', 'banner_red2', 'lamp', 'fountain', 'statue', 'crates'], 'decor'),
    (A, (780, 978, 1115, 1015), ['tent_red', 'well', 'barrel', 'bench', 'tent', 'chest', 'cart'], 'decor'),
    (A, (12, 928, 425, 968), ['t0', 't1', 't2', 't3', 't4', 't5', 't6', 't7', 't8'], 'terrain'),
    (A, (12, 972, 425, 1012), ['t9', 't10', 't11', 't12', 't13', 't14', 't15', 't16', 't17'], 'terrain'),
    (A, (1128, 928, 1532, 1012), ['sun', 'cloud', 'rain', 'storm', 'snowfall', 'fog', 'dusk', 'moon'], 'weather'),
    (A, (1130, 778, 1328, 818), ['fx_build0', 'fx_build1', 'fx_build2'], 'effects'),
    (A, (1335, 778, 1530, 818), ['fx_up0', 'fx_up1', 'fx_up2', 'fx_up3'], 'effects'),
    (A, (1130, 822, 1328, 858), ['fx_merge0', 'fx_merge1', 'fx_merge2'], 'effects'),
    (A, (1335, 820, 1530, 860), ['fx_boom0', 'fx_boom1', 'fx_boom2', 'fx_boom3'], 'effects'),
    (A, (1130, 860, 1328, 900), ['fx_portal0', 'fx_portal1', 'fx_portal2'], 'effects'),
    (A, (1190, 30, 1530, 115), ['m_lighthouse', 'm_arch', 'm_ruins', 'm_crystals'], 'decor'),
    (A, (1190, 120, 1530, 205), ['m_wreck', 'm_head', 'm_palm', 'm_statue', 'm_rocks', 'm_chest'], 'decor'),
    (U, (1108, 618, 1368, 668), ['ui_wood', 'ui_stone', 'ui_gold', 'ui_food', 'ui_people', 'ui_star'], 'resources'),
    (U, (8, 600, 1085, 690), ['b_main', 'b_house', 'b_lumber', 'b_quarry', 'b_farm', 'b_depot', 'b_barracks', 'b_archery', 'b_shipyard', 'b_explore', 'b_tower', 'b_wall'], 'buildings'),
    (U, (1405, 370, 1665, 500), ['u_worker', 'u_woodcutter', 'u_stonecutter', 'u_farmer'], 'units'),
    (U, (1405, 450, 1665, 520), ['u_infantry', 'u_archer', 'u_cavalry', 'u_scout'], 'units'),
]

report = []
for (S, box, names, folder) in ROWS:
    boxes = split_to(S, box, len(names))
    report.append(f'{folder} {box}: {len(boxes)} kutu / {len(names)} ad')
    for i, b in enumerate(boxes[:len(names)]):
        save(cut(S, b), f'{folder}/{names[i]}.png')

# Logo
logo = cut(A, (0, 0, 292, 218), keep_ratio=0.02)
save(logo, 'ui/logo.png', scale=2)
# Danışman portreleri (arayüz panosu, çerçeveli kartlar → kırpılıp doğrudan kullanılır)
for n, x0, x1 in [('king', 1125, 1188), ('explorer', 1194, 1256), ('scholar', 1261, 1323), ('commander', 1329, 1391)]:
    card = Image.fromarray(U[353:490, x0:x1]).convert('RGBA')
    save(card, f'portraits/{n}.png', scale=2)

# Açık zeminden ayrılmış, sağlam ada sprite'ları (01_Adalar)
for f, n in [('bataklik_ada', 'big_swamp'), ('col_ada', 'big_desert'), ('dag_ada', 'big_mountain'), ('kar_ada', 'big_snow'),
             ('kristal_ada', 'big_crystal'), ('orman_ada', 'big_forest')]:
    p = os.path.join(ISL, f + '.png')
    if os.path.exists(p):
        save(Image.open(p).convert('RGBA'), f'islands/{n}.png', scale=1)

# Yüksek çözünürlüklü, dama deseninden temizlenmiş bina ve ada sprite'ları (tools/clean_checker.py çıktısı)
HI = os.environ.get('HI_DIR')
if HI:
    for f, n in [('kraliyet_kalesi', 'buildings/hi_castle'), ('askeri_kisla', 'buildings/hi_barracks'), ('ev', 'buildings/hi_house'),
                 ('kereste_atolyesi', 'buildings/hi_lumber'), ('tas_ocagi', 'buildings/hi_quarry'), ('tarla', 'buildings/hi_farm'),
                 ('altin_madeni', 'buildings/hi_mine'), ('liman', 'buildings/hi_port'), ('cimen_ada', 'islands/home')]:
        p = os.path.join(HI, f + '.png')
        if os.path.exists(p): save(Image.open(p).convert('RGBA'), n + '.png', scale=1)

json.dump(manifest, open(os.path.join(OUT, 'sprites.json'), 'w'), indent=1, sort_keys=True)
print('\n'.join(report))
print(len(manifest), 'sprite')
