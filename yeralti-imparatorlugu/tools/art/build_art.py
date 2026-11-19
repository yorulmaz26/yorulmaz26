"""Tüm çizimleri üretir, tek atlasa paketler ve libGDX .atlas dosyasını yazar.

Kullanım: python3 build_art.py  →  ../../assets/art/game.png, game.atlas
"""
import json, os, subprocess, sys
from PIL import Image
import artlib
import sprites_world

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "assets", "art")
SIZE = 2048
PAD = 4

def pack(sprites):
    order = sorted(range(len(sprites)), key=lambda i: -sprites[i][2])
    x = y = PAD
    row_h = 0
    pos = {}
    for i in order:
        _, w, h, *_ = sprites[i]
        if x + w + PAD > SIZE:
            x = PAD; y += row_h + PAD * 2; row_h = 0
        pos[i] = (x, y)
        x += w + PAD * 2
        row_h = max(row_h, h)
    if y + row_h > SIZE:
        sys.exit(f"Atlas doldu (y={y + row_h})")
    return pos


def main(modules):
    for m in modules:
        m.build()
    sprites = artlib.SPRITES
    names = [s[0] for s in sprites]
    dup = {n for n in names if names.count(n) > 1}
    if dup:
        sys.exit(f"Yinelenen ad: {dup}")
    pos = pack(sprites)
    parts = []
    for i, (name, w, h, defs, body, kind, split) in enumerate(sprites):
        x, y = pos[i]
        d = defs.replace("ID_", name + "_")
        b = body.replace("ID_", name + "_")
        parts.append(f'<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;left:{x}px;top:{y}px" width="{w}" height="{h}" viewBox="0 0 {w} {h}" overflow="hidden"><defs>{d}</defs>{b}</svg>')
    html = f'<!doctype html><html><head><style>html,body{{margin:0;background:transparent}}</style></head><body><div style="position:relative;width:{SIZE}px;height:{SIZE}px">{"".join(parts)}</div></body></html>'
    tmp = os.path.join(HERE, "build")
    os.makedirs(tmp, exist_ok=True)
    page = os.path.join(tmp, "atlas.html")
    open(page, "w").write(html)
    raw = os.path.join(tmp, "atlas_raw.png")
    subprocess.check_call(["node", os.path.join(HERE, "render.js"), page, raw, str(SIZE), str(SIZE)])
    img = Image.open(raw).convert("RGBA")
    # Dikişsiz karolar için kenar piksellerini dolgu alanına uzat (filtreleme taşmasını önler).
    for i, (name, w, h, defs, body, kind, split) in enumerate(sprites):
        if kind != "tile":
            continue
        x, y = pos[i]
        for p in range(1, PAD + 1):
            img.paste(img.crop((x, y, x + w, y + 1)), (x, y - p))
            img.paste(img.crop((x, y + h - 1, x + w, y + h)), (x, y + h - 1 + p))
        for p in range(1, PAD + 1):
            img.paste(img.crop((x, y - PAD, x + 1, y + h + PAD)), (x - p, y - PAD))
            img.paste(img.crop((x + w - 1, y - PAD, x + w, y + h + PAD)), (x + w - 1 + p, y - PAD))
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "game.png"), optimize=True)
    lines = ["game.png", f"size: {SIZE},{SIZE}", "format: RGBA8888", "filter: MipMapLinearLinear,Linear", "repeat: none"]
    for i, (name, w, h, defs, body, kind, split) in enumerate(sprites):
        x, y = pos[i]
        lines.append(name)
        lines.append(f"  bounds: {x}, {y}, {w}, {h}")
        if split:
            lines.append(f"  split: {split[0]}, {split[1]}, {split[2]}, {split[3]}")
    open(os.path.join(OUT, "game.atlas"), "w").write("\n".join(lines) + "\n")
    print(f"{len(sprites)} çizim paketlendi → {OUT}")


if __name__ == "__main__":
    import importlib
    mods = [sprites_world]
    for extra in ["sprites_beings", "sprites_items", "sprites_ui", "sprites_surface"]:
        try:
            mods.append(importlib.import_module(extra))
        except ModuleNotFoundError:
            pass
    main(mods)
