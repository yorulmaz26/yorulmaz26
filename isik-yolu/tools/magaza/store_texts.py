#!/usr/bin/env python3
"""Mağaza metinlerini (store_texts.<dil>.json) dil klasörlerine Play Console'a yapıştırılacak dosyalar olarak yazar
ve Google Play sınırlarını denetler.

  python3 tools/magaza/store_texts.py RELEASE_PACKAGE/06_Store_Descriptions
"""
import json
import os
import sys

LANGS = ["tr", "en", "de", "it", "fr", "es"]
LIMITS = {"title": 30, "title_alt_aso": 30, "short_description": 80, "long_description": 4000}
BANNED_IN_TITLE = ["best", "#1", "top", "free", "new", "sale", "en iyi", "ücretsiz", "yeni", "beste", "gratis", "kostenlos",
                   "neu", "meilleur", "gratuit", "nouveau", "migliore", "nuovo", "mejor", "nuevo"]
HEAD = {"tr": "Mağaza metinleri", "en": "Store listing", "de": "Store-Eintrag", "it": "Scheda dello store", "fr": "Fiche Play Store",
        "es": "Ficha de Play Store"}


def check(d, lang):
    errs = []
    for k, n in LIMITS.items():
        if len(d[k]) > n: errs.append(f"{lang}: {k} {len(d[k])} > {n}")
    for k in ("title", "title_alt_aso", "short_description"):
        low = d[k].lower()
        for b in BANNED_IN_TITLE:
            if b in low.split() or (b.startswith("#") and b in low): errs.append(f"{lang}: {k} yasaklı ifade içeriyor: {b}")
        if any(ord(ch) > 0x2600 for ch in d[k]): errs.append(f"{lang}: {k} emoji/simge içeriyor")
    for i, (a, b) in enumerate(d["screenshot_captions"]):
        if len(a) > 26 or len(b) > 44: errs.append(f"{lang}: ekran başlığı {i + 1} uzun ({len(a)}/{len(b)})")
    if d["title"] not in ("Işık Yolu – Prismway", "Prismway – Işık Yolu"): errs.append(f"{lang}: başlık marka biçiminde değil")
    return errs


def write(base, d, lang):
    od = os.path.join(base, lang.upper())
    os.makedirs(od, exist_ok=True)
    files = {"title.txt": d["title"], "short_description.txt": d["short_description"], "full_description.txt": d["long_description"],
             "release_notes.txt": d["release_notes"], "video_title.txt": d["video_title"], "youtube_description.txt": d["youtube_description"]}
    for name, text in files.items():
        open(os.path.join(od, name), "w", encoding="utf-8").write(text.rstrip() + "\n")
    L = [f"# {HEAD[lang]} — {d['title']}", "", f"Dil / language: `{lang}`", ""]
    L += ["| Alan | Karakter | Sınır |", "|---|---|---|"]
    for k, n in LIMITS.items():
        L.append(f"| {k} | {len(d[k])} | {n} |")
    L += ["", "## Title", d["title"], "", "## Title (ASO alternative)", d["title_alt_aso"], "", "## Short description", d["short_description"],
          "", "## Slogan", d["slogan"], "", "## Full description", "", d["long_description"], "", "## Features"]
    L += [f"- {f}" for f in d["features"]]
    L += ["", "## Gameplay", d["gameplay"], "", "## Target audience", d["target_audience"], "", "## Keywords",
          ", ".join(d["keywords"]), "", "## Category", d["category"], "", "## Tags", ", ".join(d["tags"]), "",
          "## Release notes (first release)", d["release_notes"], "", "## Promo video title", d["video_title"], "",
          "## YouTube description", d["youtube_description"], "", "## Social media posts"]
    L += [f"{i + 1}. {p}" for i, p in enumerate(d["social_posts"])]
    L += ["", "## Screenshot captions and alt texts", "", "| # | Caption | Subline | Alt text |", "|---|---|---|---|"]
    for i, ((a, b), alt) in enumerate(zip(d["screenshot_captions"], d["screenshot_alt_texts"])):
        L.append(f"| {i + 1} | {a} | {b} | {alt} |")
    L += ["", "Feature graphic alt text: " + d["feature_graphic_alt_text"], "", "## Trailer captions"]
    L += [f"- {k}: {v}" for k, v in d["trailer_captions"].items()]
    open(os.path.join(od, "store_listing.md"), "w", encoding="utf-8").write("\n".join(L) + "\n")


def main():
    base = sys.argv[1]
    errs = []
    for lang in LANGS:
        d = json.load(open(os.path.join(base, f"store_texts.{lang}.json"), encoding="utf-8"))
        errs += check(d, lang)
        write(base, d, lang)
    print("\n".join(errs) if errs else "tüm sınırlar uygun")
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
