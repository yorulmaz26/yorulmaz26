#!/usr/bin/env python3
"""Çeviri dosyası denetimi: python3 tools/i18n/validate.py [kod ...]
Her dil dosyasında tr.json'daki tüm anahtarların bulunduğunu, yer tutucuların ({0}, {1,plural,...}) aynı olduğunu,
köşeli parantez içermediğini ve JSON'un geçerli olduğunu kontrol eder. Uzunluk uyarısı da verir."""
import json, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIR = os.path.join(ROOT, "android", "assets", "localization")
PH = re.compile(r"\{(\d+)[,}]")
def load(code):
    with open(os.path.join(DIR, code + ".json"), encoding="utf-8") as f:
        return {k: v for k, v in json.load(f).items() if not k.startswith("_")}
def braces_ok(s):
    d = 0
    for c in s:
        if c == "{": d += 1
        elif c == "}":
            d -= 1
            if d < 0: return False
    return d == 0
def main(codes):
    tr = load("tr")
    bad = 0
    for code in codes:
        try:
            m = load(code)
        except Exception as e:
            print(f"{code}: OKUNAMADI: {e}"); bad += 1; continue
        missing = [k for k in tr if not str(m.get(k, "")).strip()]
        extra = [k for k in m if k not in tr]
        ph = [k for k in tr if k in m and set(PH.findall(m[k])) != set(PH.findall(tr[k]))]
        br = [k for k in m if "[" in m[k] or not braces_ok(m[k])]
        nl = [k for k in tr if k in m and tr[k].count("\n") != m[k].count("\n")]
        long_ = [k for k in tr if k in m and len(m[k]) > max(12, len(tr[k]) * 1.6)]
        print(f"{code}: {len(m)} anahtar | eksik {len(missing)} | fazla {len(extra)} | yer tutucu {len(ph)} | parantez/köşeli {len(br)} | satır sonu farkı {len(nl)} | çok uzun (uyarı) {len(long_)}")
        for name, lst in (("eksik", missing), ("fazla", extra), ("yer tutucu", ph), ("parantez", br), ("satır sonu", nl)):
            if lst: print(f"  {name}: {lst[:25]}")
        if long_: print(f"  uzun (uyarı, kısaltmayı düşün): {long_[:25]}")
        bad += len(missing) + len(ph) + len(br)
    return bad
if __name__ == "__main__":
    codes = sys.argv[1:] or ["en", "de", "it", "fr", "es"]
    sys.exit(1 if main(codes) else 0)
