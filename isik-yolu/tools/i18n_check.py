#!/usr/bin/env python3
"""Hızlı yerelleştirme denetimi (Gradle gerektirmez; asıl denetim I18nTest'tir).

  python3 tools/i18n_check.py [dil ...]

Her dil dosyasını Türkçe kaynakla karşılaştırır: eksik/fazla anahtar, yer tutucu ({0}…) farkı, çoğul biçimleri
(one/other), boş metin ve kısa arayüz metinlerinde İngilizceye göre aşırı uzunluk (taşma riski).
"""
import json
import os
import re
import sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "i18n")
PH = re.compile(r"\{(\d+)\}")


def load(code):
    return json.load(open(os.path.join(BASE, code + ".json"), encoding="utf-8"))


def ph(v):
    if isinstance(v, dict):
        s = set()
        for x in v.values(): s |= set(PH.findall(x))
        return s
    return set(PH.findall(v))


def texts(v):
    return list(v.values()) if isinstance(v, dict) else [v]


def main():
    langs = sys.argv[1:] or ["en", "de", "it", "fr", "es"]
    tr, en = load("tr"), load("en")
    bad = 0
    for code in langs:
        try:
            d = load(code)
        except FileNotFoundError:
            print(f"{code}: dosya yok"); bad += 1; continue
        missing = [k for k in tr if k not in d]
        extra = [k for k in d if k not in tr]
        probs = []
        for k, v in d.items():
            if k not in tr: continue
            if ph(v) != ph(tr[k]): probs.append(f"{k}: yer tutucu {sorted(ph(v))} ≠ {sorted(ph(tr[k]))}")
            if isinstance(tr[k], dict) != isinstance(v, dict): probs.append(f"{k}: çoğul/tekil biçim uyuşmuyor")
            if isinstance(v, dict) and not {"one", "other"} <= set(v): probs.append(f"{k}: one/other eksik")
            for t in texts(v):
                if not t.strip(): probs.append(f"{k}: boş")
            ref = texts(en.get(k, tr[k]))[0]
            mine = texts(v)[0]
            if len(ref) <= 18 and len(mine) > max(22, int(len(ref) * 1.7)):
                probs.append(f"{k}: kısa arayüz metni uzun ({len(mine)} > en {len(ref)}): {mine!r}")
        print(f"{code}: {len(d)} anahtar, eksik {len(missing)}, fazla {len(extra)}, sorun {len(probs)}")
        for x in (missing[:20] + [f"fazla: {e}" for e in extra[:10]] + probs[:60]): print("   ", x)
        bad += len(missing) + len(extra) + len([p for p in probs if "uzun" not in p])
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
