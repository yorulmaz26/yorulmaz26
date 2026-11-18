#!/usr/bin/env bash
# Emülatörde uçtan uca test: kurulum, ekran geçişleri, menü butonları, oynanış,
# gölge yakalaması, duraklatma, kayıt sistemi kalıcılığı, çökme kontrolü ve ekran görüntüleri.
# Kullanım: tools/smoke_test.sh <debug.apk> <çıktı_klasörü>
set -u
APK="$1"; OUT="$2"
PKG=com.golgen.oyun
ACT=$PKG/.MainActivity
mkdir -p "$OUT/ekranlar"
PASS=0; FAIL=0
RESULTS="$OUT/test_sonuclari.txt"
: > "$RESULTS"; : > "$OUT/logcat.txt"

ok()   { echo "GEÇTİ  | $1" | tee -a "$RESULTS"; PASS=$((PASS+1)); }
bad()  { echo "KALDI  | $1" | tee -a "$RESULTS"; FAIL=$((FAIL+1)); }
log()  { adb logcat -d -s GolgenUI:I 2>/dev/null; }
shot() { adb exec-out screencap -p > "$OUT/ekranlar/$1.png"; echo "ekran görüntüsü: $1"; }

current_screen() { log | grep -o 'ekran=[A-Za-z]*$' | tail -1 | cut -d= -f2; }

wait_screen() { # $1 ekran adı, $2 saniye
  local i
  for i in $(seq 1 $(( ${2:-8} * 4 ))); do
    if [ "$(current_screen)" = "$1" ]; then return 0; fi
    sleep 0.25
  done
  return 1
}

expect_screen() { # $1 ekran, $2 açıklama
  if wait_screen "$1" "${3:-8}"; then ok "$2 → $1"; else bad "$2 → beklenen $1, bulunan $(current_screen)"; fi
}

tap_target() { # $1 ekran, $2 hedef adı
  local line x y
  line=$(log | grep "hedef ekran=$1 ad=$2 x=" | tail -1)
  if [ -z "$line" ]; then bad "hedef bulunamadı: $1/$2"; return 1; fi
  x=$(echo "$line" | sed -E 's/.* x=([0-9]+).*/\1/')
  y=$(echo "$line" | sed -E 's/.* y=([0-9]+).*/\1/')
  adb shell input tap "$x" "$y"
  sleep 0.9
}

clear_log() { adb logcat -d >> "$OUT/logcat.txt" 2>/dev/null; adb logcat -c; }

prefs() { adb shell run-as $PKG cat shared_prefs/golgen_kayit.xml 2>/dev/null; }
pref_int() { prefs | grep "name=\"$1\"" | sed -E 's/.*value="(-?[0-9]+)".*/\1/'; }

adb wait-for-device
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
# Android'in ilk tam ekran açılışındaki "Viewing full screen" sistem uyarısı dokunuşları yutar; testte önceden onaylanır.
adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell wm size | tee "$OUT/cihaz.txt"
adb shell getprop ro.build.version.release | sed 's/^/Android /' | tee -a "$OUT/cihaz.txt"

# 1) Kurulum ve temiz başlangıç
adb uninstall $PKG >/dev/null 2>&1 || true
if adb install -r "$APK" >/dev/null; then ok "APK kurulumu"; else bad "APK kurulumu"; fi
clear_log
adb shell am start -W -n $ACT >/dev/null
sleep 1.0; shot 01_acilis
expect_screen SplashScreen "Uygulama açılışı"
# İlk açılışta öğretici gelmeli
expect_screen TutorialScreen "İlk açılış öğreticisi" 8
sleep 0.5
# Öğretici: sürükleyerek 1. adımı geç
for i in 1 2 3 4; do adb shell input swipe 300 1700 800 1500 250; adb shell input swipe 800 1500 300 1800 250; done
sleep 0.8; shot 02_ogretici_golge
tap_target TutorialScreen Atla
expect_screen MenuScreen "Öğretici → Ana menü"
sleep 1.2; shot 03_ana_menu

# 2) Sonsuz mod
tap_target MenuScreen OYNA
expect_screen PlayScreen "OYNA butonu"
sleep 0.8; shot 04_oyun_hazir
# Daireler çizerek oyna (gölgeden kaç)
for i in $(seq 1 3); do
  adb shell input swipe 300 1500 800 1500 220
  adb shell input swipe 800 1500 800 1900 220
  adb shell input swipe 800 1900 300 1900 220
  adb shell input swipe 300 1900 300 1500 220
done
shot 05_oyun_ortasi
if [ "$(current_screen)" != "PlayScreen" ]; then
  echo "Not: oyun hareket sırasında bitti (engel/gölge); yeniden başlatılıyor"
  wait_screen GameOverScreen 5; sleep 1; tap_target GameOverScreen "TEKRAR OYNA"; wait_screen PlayScreen 5
fi
tap_target PlayScreen ikon-1
sleep 0.6; shot 06_duraklatma
if log | grep -q "hedef ekran=PlayScreen ad=DEVAM ET"; then ok "Duraklatma menüsü açıldı"; else bad "Duraklatma menüsü"; fi
# Duraklatılmışken bekle: oyun sonu gelmemeli (gölge zamanı ilerlememeli)
sleep 6
if [ "$(current_screen)" = "PlayScreen" ]; then ok "Duraklatmada oyun ilerlemiyor (6 sn)"; else bad "Duraklatmada oyun bitti"; fi
tap_target PlayScreen "DEVAM ET"
# Hareketsiz kal → gölge yakalamalı
expect_screen GameOverScreen "Hareketsiz kalınca gölge yakalar / oyun sonu" 25
sleep 1.5; shot 07_oyun_sonu
GP=$(pref_int gamesPlayed)
if [ "${GP:-0}" -ge 1 ]; then ok "Kayıt: oynanan oyun sayısı kaydedildi ($GP)"; else bad "Kayıt: gamesPlayed=$GP"; fi
BS=$(pref_int bestScore)
if [ "${BS:-0}" -gt 0 ]; then ok "Kayıt: rekor kaydedildi ($BS)"; else bad "Kayıt: bestScore=$BS"; fi
tap_target GameOverScreen "TEKRAR OYNA"
expect_screen PlayScreen "Tekrar oyna"
clear_log
adb shell input keyevent KEYCODE_BACK; sleep 0.8
if log | grep -q "hedef ekran=PlayScreen ad=ANA MENÜ"; then ok "Geri tuşu oyunu duraklatır"; else bad "Geri tuşu duraklatma"; fi
tap_target PlayScreen "ANA MENÜ"
expect_screen MenuScreen "Duraklatma → Ana menü"

# 3) Karakterler (debug ekstrasıyla 500 elmas ver, satın al, seç)
adb shell am start -n $ACT --ei elmas 500 >/dev/null; sleep 1
tap_target MenuScreen Karakter
expect_screen SkinsScreen "Karakter butonu"
sleep 0.6
tap_target SkinsScreen kart-2
sleep 0.4; shot 08_karakterler
D0=$(pref_int diamonds)
tap_target SkinsScreen "SATIN AL  ·  120"
sleep 0.8
D1=$(pref_int diamonds); SEL=$(pref_int selectedSkin); UNL=$(pref_int unlockedSkins)
if [ "$SEL" = "2" ] && [ $(( ${UNL:-0} & 4 )) -ne 0 ]; then ok "Karakter satın alındı ve seçildi (elmas $D0 → $D1)"; else bad "Satın alma: seçili=$SEL açık=$UNL elmas $D0→$D1"; fi
shot 09_karakter_alindi
adb shell input keyevent KEYCODE_BACK
expect_screen MenuScreen "Geri tuşu → Ana menü"

# 4) Görevler: günlük, görev modu, başarılar
tap_target MenuScreen "Görevler"
expect_screen MissionsScreen "Görevler butonu"
sleep 0.6; shot 10_gunluk_gorevler
tap_target MissionsScreen "Görev Modu"; sleep 0.4; shot 11_gorev_modu
tap_target MissionsScreen "Başarılar"; sleep 0.4; shot 12_basarilar
tap_target MissionsScreen "Görev Modu"
tap_target MissionsScreen seviye-0
expect_screen PlayScreen "Görev 1 başlatma"
sleep 0.5; shot 13_gorev_oyun
adb shell input keyevent KEYCODE_BACK; sleep 0.5
tap_target PlayScreen "ANA MENÜ"
expect_screen MenuScreen "Görev → Ana menü"

# 5) Rekorlar
tap_target MenuScreen Rekorlar
expect_screen RecordsScreen "Rekorlar butonu"
sleep 0.6; shot 14_rekorlar
tap_target RecordsScreen ikon-3
expect_screen MenuScreen "Rekorlar geri butonu"

# 6) Ayarlar: ses kapat → kayıt kontrolü
tap_target MenuScreen ikon-2
expect_screen SettingsScreen "Ayarlar butonu"
tap_target SettingsScreen ayar-ses
tap_target SettingsScreen ayar-titresim
sleep 0.5; shot 15_ayarlar
if [ "$(pref_int sound)" = "0" ] && [ "$(pref_int vibration)" = "0" ]; then ok "Ayarlar: ses ve titreşim kapatıldı ve kaydedildi"; else bad "Ayarlar kaydı: ses=$(pref_int sound) titreşim=$(pref_int vibration)"; fi
tap_target SettingsScreen ayar-ses
tap_target SettingsScreen "İLERLEMEYİ SIFIRLA"; sleep 0.4; shot 16_sifirlama_onay
tap_target SettingsScreen "VAZGEÇ"
tap_target SettingsScreen "ÖĞRETİCİYİ OYNA"
expect_screen TutorialScreen "Ayarlar → Öğretici"
sleep 0.6; shot 17_ogretici
tap_target TutorialScreen Kapat
expect_screen MenuScreen "Öğretici kapat"

# 7) Kalıcılık: uygulamayı öldür ve yeniden aç → öğretici tekrar gelmemeli, veriler korunmalı
adb shell am force-stop $PKG
clear_log
adb shell am start -W -n $ACT >/dev/null
expect_screen MenuScreen "Yeniden açılış (öğretici atlanır, kayıt yüklenir)" 10
if [ "$(pref_int selectedSkin)" = "2" ]; then ok "Kalıcılık: seçili karakter korundu"; else bad "Kalıcılık: selectedSkin=$(pref_int selectedSkin)"; fi
sleep 1; shot 18_yeniden_acilis

# 8) Çökme / ANR kontrolü
adb logcat -d >> "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" || grep -q "ANR in $PKG" "$OUT/logcat.txt"; then
  bad "Çökme/ANR bulundu"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
else ok "Çökme veya ANR yok"; fi
grep GolgenFPS "$OUT/logcat.txt" | tail -5 | tee "$OUT/fps.txt" || true

echo "TOPLAM: $PASS geçti, $FAIL kaldı" | tee -a "$RESULTS"
[ "$FAIL" -eq 0 ]
