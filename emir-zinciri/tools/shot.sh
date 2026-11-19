#!/usr/bin/env bash
# Masaüstü sürümünü sanal ekranda çalıştırıp ekran görüntüsü alır.
# Kullanım: tools/shot.sh cikti.png [başlatıcı argümanları...]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(realpath -m "$1")"; shift
cd "$ROOT/assets"
timeout 90 xvfb-run -a -s "-screen 0 1600x1600x24" "$ROOT/lwjgl3/build/install/lwjgl3/bin/lwjgl3" --shot "$OUT" --exit "$@" > /tmp/ez-shot.log 2>&1 || true
grep -E "Exception|Error|at com\.emir" /tmp/ez-shot.log | grep -v ALSA | head -20 || true
[ -f "$OUT" ] && echo "kaydedildi: $OUT"
