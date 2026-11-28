#!/usr/bin/env python3
"""Çeviri parçalarını (her biri {"tr": {...}, "en": {...}, ...}) dil dosyalarına birleştirir.

Kullanım: python3 tools/l10n_merge.py <parça_klasörü> [hedef_klasör]
Hedef varsayılan: app/src/main/assets/localization. Mevcut dosyalardaki anahtarlar korunur,
parçadakiler eklenir/güncellenir. Bir anahtar bir dilde eksikse veya yer tutucular ({0}, {n})
diller arasında farklıysa hata verir.
"""
import json, os, re, sys

LANGS = ["tr", "en", "de", "it", "fr", "es"]
PH = re.compile(r"\{(\d|n)\}")

def main():
    frag_dir = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "localization")
    tables = {}
    for l in LANGS:
        p = os.path.join(out_dir, l + ".json")
        tables[l] = json.load(open(p, encoding="utf-8")) if os.path.exists(p) else {}
    errors = []
    for name in sorted(os.listdir(frag_dir)):
        if not name.endswith(".json"):
            continue
        frag = json.load(open(os.path.join(frag_dir, name), encoding="utf-8"))
        keys = set(frag.get("tr", {}))
        for l in LANGS:
            if set(frag.get(l, {})) != keys:
                errors.append(f"{name}: '{l}' anahtar kümesi farklı: eksik={sorted(keys - set(frag.get(l, {})))[:5]} fazla={sorted(set(frag.get(l, {})) - keys)[:5]}")
        for k in keys:
            ph = sorted(set(PH.findall(frag["tr"][k])))
            for l in LANGS:
                v = frag.get(l, {}).get(k)
                if v is None:
                    continue
                if sorted(set(PH.findall(v))) != ph:
                    errors.append(f"{name}: {l}:{k} yer tutucular {sorted(set(PH.findall(v)))} != tr {ph}")
                tables[l][k] = v
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    os.makedirs(out_dir, exist_ok=True)
    for l in LANGS:
        with open(os.path.join(out_dir, l + ".json"), "w", encoding="utf-8") as f:
            json.dump(dict(sorted(tables[l].items())), f, ensure_ascii=False, indent=1)
            f.write("\n")
    print(f"{len(tables['tr'])} anahtar x {len(LANGS)} dil yazıldı -> {os.path.normpath(out_dir)}")

if __name__ == "__main__":
    main()
