#!/usr/bin/env bash
# Emülatörde APK'yı kurar, oyunu açar, 25 sn bekler; süreç ayakta mı ve FATAL hata var mı bakar.
set -euo pipefail
APK="$1"
TAG="${2:-debug}"
PKG=com.yuruyensehir.gocebe
mkdir -p smoke
adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install -r "$APK"
adb logcat -c
adb shell am start -n "$PKG/com.yuruyensehir.android.AndroidLauncher"
sleep 25
adb exec-out screencap -p > smoke/$TAG-acilis.png || true
# Geri tuşu: çıkış onayı penceresi görünmeli, oyun kapanmamalı
adb shell input keyevent 4
sleep 3
adb exec-out screencap -p > smoke/$TAG-geri-tusu.png || true
adb logcat -d > smoke/$TAG-logcat.txt || true
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "HATA: oyun süreci çalışmıyor"; grep -E "FATAL|AndroidRuntime" -A 20 smoke/$TAG-logcat.txt | head -80; exit 1
fi
if grep -q "FATAL EXCEPTION" smoke/$TAG-logcat.txt; then
  echo "HATA: FATAL EXCEPTION"; grep -A 30 "FATAL EXCEPTION" smoke/$TAG-logcat.txt | head -80; exit 1
fi
# Ekran döndürme / yapılandırma değişikliği: oyun çökmemeli
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
sleep 4
adb shell settings put system user_rotation 0 || true
sleep 4
adb logcat -d > smoke/$TAG-logcat.txt || true
if grep -q "FATAL EXCEPTION" smoke/$TAG-logcat.txt; then
  echo "HATA: döndürmeden sonra FATAL EXCEPTION"; grep -A 30 "FATAL EXCEPTION" smoke/$TAG-logcat.txt | head -80; exit 1
fi
echo "Açılış testi başarılı ($TAG): oyun çalışıyor."
