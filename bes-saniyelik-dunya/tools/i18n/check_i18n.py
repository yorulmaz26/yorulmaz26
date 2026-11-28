#!/usr/bin/env python3
"""Dil dosyası denetleyicisi: eksik/fazla anahtar, yer tutucu, çoğul, liste ve cinsiyet biçimi denetimi.

Kullanım: python3 tools/i18n/check_i18n.py [dil ...]   (varsayılan: tr dışındaki bütün diller)
Kaynak dil tr.json'dur; her dil dosyası aynı anahtarları içermelidir. Hata varsa çıkış kodu 1.
"""
import json, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "i18n")
GENDERED = {"de": "mfn", "it": "mf", "fr": "mf", "es": "mf"}
PH = re.compile(r"\{[a-z]+\}")

def load(lang):
    with open(os.path.join(ROOT, lang + ".json"), encoding="utf-8") as f:
        return json.load(f)

def check(lang, src, en):
    d = load(lang); errs = []; warns = []
    for k in sorted(set(src) - set(d)): errs.append(f"eksik anahtar: {k}")
    for k in sorted(set(d) - set(src)): errs.append(f"fazla anahtar: {k}")
    for k, v in d.items():
        if k not in src: continue
        if not isinstance(v, str): errs.append(f"{k}: metin değil"); continue
        if src[k] and not v.strip(): errs.append(f"{k}: boş")
        if sorted(set(PH.findall(v))) != sorted(set(PH.findall(src[k]))) and not k.endswith(".one"):
            errs.append(f"{k}: yer tutucular farklı {sorted(set(PH.findall(v)))} != {sorted(set(PH.findall(src[k])))}")
        if k.endswith(".one") and not set(PH.findall(v)) <= set(PH.findall(src[k])):
            errs.append(f"{k}: bilinmeyen yer tutucu")
        if v.count("\n") != src[k].count("\n") and k.startswith(("game.intro", "level.boosts", "result.star_rules", "endless.no_records", "settings.about")):
            warns.append(f"{k}: satır sayısı farklı")
        if lang not in ("tr", "en") and en.get(k) == v and len(v) > 3 and not k.startswith(("app.", "lives.count", "level.kind_id", "levelname.pattern", "unit.", "result.xp", "skin.pati")):
            warns.append(f"{k}: İngilizceyle aynı (çevrilmemiş olabilir): {v!r}")
    for k in [k for k in src if k.endswith((".nouns", ".adjectives"))] + ["player.names"]:
        if k not in d: continue
        items = d[k].split("|")
        if len(items) != len(src[k].split("|")): errs.append(f"{k}: {len(items)} öğe, {len(src[k].split('|'))} olmalı")
        g = GENDERED.get(lang)
        if k.endswith(".nouns"):
            for it in items:
                if g:
                    if ":" not in it or it.split(":")[1] not in g: errs.append(f"{k}: '{it}' cinsiyet işareti eksik/yanlış (:{'/:'.join(g)})")
                elif ":" in it: errs.append(f"{k}: '{it}' bu dilde cinsiyet işareti olmamalı")
        if k.endswith(".adjectives") and g:
            for it in items:
                n = len(it.split("/"))
                if n not in (1, len(g)): errs.append(f"{k}: '{it}' {len(g)} biçim (ya da tek biçim) olmalı")
    return errs, warns

def main():
    src = load("tr"); en = load("en")
    langs = sys.argv[1:] or sorted(f[:-5] for f in os.listdir(ROOT) if f.endswith(".json") and f != "tr.json")
    bad = False
    for lang in langs:
        errs, warns = check(lang, src, en)
        print(f"[{lang}] {len(load(lang))} metin, {len(errs)} hata, {len(warns)} uyarı")
        for e in errs: print("  HATA  ", e)
        for w in warns[:40]: print("  uyarı ", w)
        bad |= bool(errs)
    sys.exit(1 if bad else 0)

if __name__ == "__main__":
    main()
