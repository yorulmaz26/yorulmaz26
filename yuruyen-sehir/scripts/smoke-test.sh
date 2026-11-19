#!/usr/bin/env bash
# Emülatörde APK'yı kurar, oyunu açar, 25 sn bekler; süreç ayakta mı ve FATAL hata var mı bakar.
set -euo pipefail
APK="$1"
PKG=com.yuruyensehir.gocebe
mkdir -p smoke
adb install -r "$APK"
adb logcat -c
adb shell am start -n "$PKG/com.yuruyensehir.android.AndroidLauncher"
sleep 25
adb exec-out screencap -p > smoke/acilis.png || true
# Geri tuşu: çıkış onayı penceresi görünmeli, oyun kapanmamalı
adb shell input keyevent 4
sleep 3
adb exec-out screencap -p > smoke/geri-tusu.png || true
adb logcat -d > smoke/logcat.txt || true
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "HATA: oyun süreci çalışmıyor"; grep -E "FATAL|AndroidRuntime" -A 20 smoke/logcat.txt | head -80; exit 1
fi
if grep -q "FATAL EXCEPTION" smoke/logcat.txt; then
  echo "HATA: FATAL EXCEPTION"; grep -A 30 "FATAL EXCEPTION" smoke/logcat.txt | head -80; exit 1
fi
echo "Açılış testi başarılı: oyun çalışıyor."
