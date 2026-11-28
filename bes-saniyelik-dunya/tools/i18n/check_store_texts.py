#!/usr/bin/env python3
"""Mağaza metinlerinin karakter sınırlarını denetler: ad ≤ 30, kısa açıklama ≤ 80, tam açıklama ≤ 4000, notlar ≤ 500."""
import glob, os, re, sys
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "RELEASE_PACKAGE", "06_Store_Descriptions")
LIMITS = [("ad", 30), ("kısa", 80), ("tam", 4000), ("notlar", 500)]
bad = False
for f in sorted(glob.glob(os.path.join(ROOT, "*", "store_listing_*.md"))):
    blocks = re.findall(r"```\n(.*?)\n```", open(f, encoding="utf-8").read(), re.S)
    vals = blocks[:4]
    res = []
    for (name, lim), v in zip(LIMITS, vals):
        n = len(v); ok = n <= lim; bad |= not ok
        res.append(f"{name} {n}/{lim}{'' if ok else ' AŞIYOR'}")
    print(os.path.basename(f), " | ".join(res))
sys.exit(1 if bad else 0)
