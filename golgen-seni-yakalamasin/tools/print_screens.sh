#!/usr/bin/env bash
# Ekran görüntülerini küçültüp base64 olarak günlüğe yazar (artefakt indirilemeyen ortamlarda inceleme için).
DIR="$1"
for f in "$DIR"/*.png; do
  [ -f "$f" ] || continue
  n=$(basename "$f" .png)
  if command -v convert >/dev/null; then convert "$f" -resize 432x -quality 72 "/tmp/$n.jpg"; src="/tmp/$n.jpg"; else src="$f"; fi
  echo "=====EKRAN-BASLA $n====="
  base64 -w 0 "$src"; echo
  echo "=====EKRAN-BITIR $n====="
done
