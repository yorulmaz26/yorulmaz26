#!/bin/bash
# Mağaza görselleri ve tanıtım videoları için GERÇEK oyun görüntüsü çekimi (CI emülatöründe).
# Debug APK'daki çekim modu (app/src/debug/.../debug/Capture.java) kullanılır: oyun döngüsü durdurulur,
# otomatik pilot oynar, kareler sabit 1/60 sn adımla cihaz çözünürlüğünde dosyaya yazılır.
#
# Kullanım: media_capture.sh <debug.apk> <çıktı_klasörü> <mod>
#   mod = telefon  : ekran 1080x1920 (9:16); 6 dilde 8 mağaza sahnesi (PNG) + TR/EN video sahneleri (JPEG kareler)
#   mod = tablet   : 7" (1200x1920, sw600dp) ve 10" (1600x2560, sw800dp) ekran ölçülerinde EN/TR 4'er sahne (PNG)
set -u
APK="$1"; OUT="$2"; MODE="${3:-telefon}"
PKG=com.golgen.oyun
ACT=$PKG/.MainActivity
REMOTE=/sdcard/Android/data/$PKG/files/cekim
mkdir -p "$OUT"
adbt() { timeout 60 adb "$@"; }

adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 2; done
adbt shell settings put global window_animation_scale 0
adbt shell settings put global transition_animation_scale 0
adbt shell settings put global animator_duration_scale 0
adbt shell settings put secure immersive_mode_confirmations confirmed
adbt install -r -g "$APK"

# Ekran ölçüsünü ayarla ve uygulamayı temiz başlat: reklamsız, örnek ilerleme (açık bölümler, taçlar, elmas)
ekran() {
  adbt shell am force-stop $PKG
  adbt shell wm size "$1"
  adbt shell wm density "$2"
  sleep 3
  { echo "--- $1 @ $2 dpi"; adbt shell wm size; adbt shell wm density; } | tee -a "$OUT/cihaz.txt"
  adbt shell am start -n $ACT --ez reklamsiz true --ei demo 1 --es ekran menu >/dev/null
  sleep 8
  adb logcat -c
}

# $1=spec ; bitene kadar bekler ve klasörü çeker
cek() {
  local spec="$1" ad
  ad=$(echo "$spec" | sed -E 's/.*ad=([^;]+).*/\1/')
  adbt shell rm -rf "$REMOTE/$ad"
  adbt shell am start -n $ACT --es cekim "\"$spec\"" >/dev/null
  local t=0
  until adbt shell "[ -f $REMOTE/$ad/bitti ] && echo ok" | grep -q ok; do
    sleep 2; t=$((t+2))
    if [ $t -gt 900 ]; then echo "ZAMAN AŞIMI: $spec"; adb logcat -d -s GolgenCekim:V AndroidRuntime:E | tail -20; return 1; fi
  done
  mkdir -p "$OUT/raw"
  adbt pull "$REMOTE/$ad" "$OUT/raw/" >/dev/null || timeout 600 adb pull "$REMOTE/$ad" "$OUT/raw/" >/dev/null
  adbt shell rm -rf "$REMOTE/$ad"
  echo "TAMAM $ad ($(ls "$OUT/raw/$ad" | grep -c -E 'jpg|png') kare, ${t}s)"
}

if [ "$MODE" = "telefon" ]; then
  ekran 1080x1920 420
  # ---- Mağaza ekran görüntüleri: 8 sahne x 6 dil (ilk 3'ü oynanış) ----
  for L in tr en de it fr es; do
    cek "ad=ss_${L}_01;dil=$L;gorev=83;tohum=11;asama=2;atla=2.5;kare=1;png=1"
    cek "ad=ss_${L}_02;dil=$L;gorev=0;tohum=3;atla=5.2;kare=1;png=1;parmak=1"
    cek "ad=ss_${L}_03;dil=$L;gorev=37;tohum=5;atla=24;kare=1;png=1"
    cek "ad=ss_${L}_04;dil=$L;ekran=gorevler;bekle=0.8;kare=1;png=1"
    cek "ad=ss_${L}_05;dil=$L;ekran=karakter;bekle=0.8;kare=1;png=1"
    cek "ad=ss_${L}_06;dil=$L;gorev=77;tohum=9;atla=21;kare=1;png=1"
    cek "ad=ss_${L}_07;dil=$L;gorev=1;tohum=4;bitir=1;sonra=2.6;kare=1;png=1"
    cek "ad=ss_${L}_08;dil=$L;ekran=menu;bekle=1.5;kare=1;png=1"
  done
  # ---- Video sahneleri (60 FPS kareler): Türkçe ve İngilizce ----
  for L in tr en; do
    cek "ad=v_${L}_01_kanca;dil=$L;gorev=83;tohum=11;asama=2;atla=1.0;kare=200"
    cek "ad=v_${L}_02_mekanik;dil=$L;gorev=0;tohum=3;atla=2.6;kare=320;parmak=1"
    cek "ad=v_${L}_03_deneyim;dil=$L;gorev=37;tohum=5;atla=18;kare=440"
    cek "ad=v_${L}_04a;dil=$L;gorev=8;tohum=2;atla=14;kare=96"
    cek "ad=v_${L}_04b;dil=$L;gorev=27;tohum=6;atla=16;kare=96"
    cek "ad=v_${L}_04c;dil=$L;gorev=17;tohum=8;atla=15;kare=96"
    cek "ad=v_${L}_04d;dil=$L;gorev=87;tohum=4;atla=15;kare=96"
    cek "ad=v_${L}_04e;dil=$L;ekran=karakter;bekle=0.5;kare=96"
    cek "ad=v_${L}_05_basari;dil=$L;gorev=4;tohum=7;bitir=1;once=2.2;kare=330"
    cek "ad=v_${L}_06_menu;dil=$L;ekran=menu;bekle=0.5;kare=200"
  done
else
  for T in "7:1200x1920:320" "10:1600x2560:320"; do
    IFS=: read -r INCH SIZE DPI <<< "$T"
    ekran "$SIZE" "$DPI"
    for L in en tr; do
      cek "ad=tab${INCH}_${L}_01;dil=$L;gorev=83;tohum=11;asama=2;atla=2.5;kare=1;png=1"
      cek "ad=tab${INCH}_${L}_02;dil=$L;gorev=37;tohum=5;atla=24;kare=1;png=1"
      cek "ad=tab${INCH}_${L}_03;dil=$L;ekran=gorevler;bekle=0.8;kare=1;png=1"
      cek "ad=tab${INCH}_${L}_04;dil=$L;ekran=menu;bekle=1.5;kare=1;png=1"
    done
  done
  adbt shell wm size reset; adbt shell wm density reset
fi
adb logcat -d -s GolgenCekim:V AndroidRuntime:E GolgenTasma:W > "$OUT/cekim_logcat.txt" 2>/dev/null || true
echo "Bitti: $(find "$OUT/raw" -type f | wc -l) dosya"
