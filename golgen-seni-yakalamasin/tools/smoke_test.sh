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

# Her adb çağrısı zaman aşımıyla: emülatörde nadiren takılan komutlar testi kilitlemesin
adbt() { timeout 25 adb "$@"; }

ok()   { echo "GEÇTİ  | $1" | tee -a "$RESULTS"; PASS=$((PASS+1)); }
bad()  { echo "KALDI  | $1" | tee -a "$RESULTS"; FAIL=$((FAIL+1)); }
log()  { timeout 20 adb logcat -d -s GolgenUI:I 2>/dev/null; }
shot() { timeout 20 adb exec-out screencap -p > "$OUT/ekranlar/$1.png" || echo "ekran görüntüsü zaman aşımı: $1"; echo "ekran görüntüsü: $1"; }

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
  adbt shell input tap "$x" "$y"
  sleep 0.9
}

clear_log() { adb logcat -d >> "$OUT/logcat.txt" 2>/dev/null; adb logcat -c; }

prefs() { timeout 20 adb shell run-as $PKG cat shared_prefs/golgen_kayit.xml 2>/dev/null; }
pref_int() { prefs | grep "name=\"$1\"" | sed -E 's/.*value="(-?[0-9]+)".*/\1/'; }

adb wait-for-device
# Canlı logcat (takılma olursa son olaylar görülebilsin)
( adb logcat -v time -s GolgenUI:I GolgenFPS:I GolgenAds:V AndroidRuntime:E ActivityManager:W > "$OUT/canli_logcat.txt" 2>/dev/null & )
step() { echo "ADIM: $*" | tee -a "$OUT/adimlar.txt"; }
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
# Android'in ilk tam ekran açılışındaki "Viewing full screen" sistem uyarısı dokunuşları yutar; testte önceden onaylanır.
adb shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1 || true
adb shell wm size | tee "$OUT/cihaz.txt"
adb shell getprop ro.build.version.release | sed 's/^/Android /' | tee -a "$OUT/cihaz.txt"

# 1) Kurulum ve temiz başlangıç
adb uninstall $PKG >/dev/null 2>&1 || true
if adb install -r "$APK" >/dev/null; then ok "APK kurulumu"; else bad "APK kurulumu"; fi
clear_log
adb shell am start -W -n $ACT --ez reklamsiz true --es dil tr >/dev/null
sleep 1.6; shot 00_logo_a
sleep 3.4; shot 00_logo_b
if log | grep -q "ekran=LogoScreen"; then ok "Magic Lumos logo animasyonu gösterildi"; else bad "Logo animasyonu gösterilmedi"; fi
wait_screen SplashScreen 10; sleep 0.8; shot 01_acilis
if log | grep -q "ekran=SplashScreen"; then ok "Uygulama açılışı → SplashScreen gösterildi"; else bad "Açılış ekranı gösterilmedi"; fi
# İlk açılışta öğretici gelmeli
expect_screen TutorialScreen "İlk açılış öğreticisi" 18
sleep 0.5
# Öğretici: sürükleyerek 1. adımı geç
for i in 1 2 3 4; do adb shell input swipe 300 1700 800 1500 250; adb shell input swipe 800 1500 300 1800 250; done
sleep 0.8; shot 02_ogretici_golge
tap_target TutorialScreen tutorial.skip
expect_screen MenuScreen "Öğretici → Ana menü"
sleep 1.0
if log | grep -q "hedef ekran=MenuScreen ad=gunluk-odul"; then
  shot 03a_gunluk_odul
  tap_target MenuScreen gunluk-odul
  sleep 0.6
  if [ -n "$(pref_int lastLoginClaimDay)" ] && [ "$(pref_int loginStreak)" = "1" ] && [ "$(pref_int diamonds)" -ge 10 ]; then ok "Günlük giriş ödülü alındı ve kaydedildi"; else bad "Günlük giriş ödülü: seri=$(pref_int loginStreak) elmas=$(pref_int diamonds)"; fi
else
  bad "Günlük giriş ödülü penceresi açılmadı"
fi
sleep 0.6; shot 03_ana_menu

# 2) Sonsuz mod
tap_target MenuScreen menu.play
expect_screen PlayScreen "OYNA butonu"
sleep 0.8; shot 04_oyun_hazir
# Daireler çizerek oyna (gölgeden kaç)
for i in $(seq 1 3); do
  adbt shell input swipe 300 1500 800 1500 220
  adbt shell input swipe 800 1500 800 1900 220
  adbt shell input swipe 800 1900 300 1900 220
  adbt shell input swipe 300 1900 300 1500 220
done
shot 05_oyun_ortasi
count_pause() { log | grep -c "hedef ekran=PlayScreen ad=play.pause.resume"; }
PAUSED=0
for attempt in 1 2 3; do
  if [ "$(current_screen)" != "PlayScreen" ]; then
    echo "Not: oyun hareket sırasında bitti; yeniden başlatılıyor"
    wait_screen GameOverScreen 5; sleep 1.5; tap_target GameOverScreen gameover.play_again; wait_screen PlayScreen 5; sleep 0.5
  fi
  BEFORE=$(count_pause)
  tap_target PlayScreen ikon-1
  sleep 0.6
  if [ "$(count_pause)" -gt "$BEFORE" ] && [ "$(current_screen)" = "PlayScreen" ]; then PAUSED=1; break; fi
done
shot 06_duraklatma
if [ "$PAUSED" = "1" ]; then ok "Duraklatma menüsü açıldı"; else bad "Duraklatma menüsü"; fi
# Duraklatılmışken bekle: oyun sonu gelmemeli (gölge zamanı ilerlememeli)
sleep 6
if [ "$(current_screen)" = "PlayScreen" ]; then ok "Duraklatmada oyun ilerlemiyor (6 sn)"; else bad "Duraklatmada oyun bitti"; fi
tap_target PlayScreen play.pause.resume
# Başlamadan önce duraklatıldıysa oyun yine "dokun ve başla" bekler: tek dokunuşla başlat, sonra hareketsiz kal
adbt shell input tap 540 1500
# Hareketsiz kal → gölge yakalamalı
expect_screen GameOverScreen "Hareketsiz kalınca gölge yakalar / oyun sonu" 25
sleep 1.5; shot 07_oyun_sonu
GP=$(pref_int gamesPlayed)
if [ "${GP:-0}" -ge 1 ]; then ok "Kayıt: oynanan oyun sayısı kaydedildi ($GP)"; else bad "Kayıt: gamesPlayed=$GP"; fi
BS=$(pref_int bestScore)
if [ "${BS:-0}" -gt 0 ]; then ok "Kayıt: rekor kaydedildi ($BS)"; else bad "Kayıt: bestScore=$BS"; fi
tap_target GameOverScreen gameover.play_again
expect_screen PlayScreen "Tekrar oyna"
clear_log
adbt shell input keyevent KEYCODE_BACK; sleep 0.8
if log | grep -q "hedef ekran=PlayScreen ad=play.pause.main_menu"; then ok "Geri tuşu oyunu duraklatır"; else bad "Geri tuşu duraklatma"; fi
# Oyun "dokun ve başla" ekranındayken duraklatıldı: devam edince kendiliğinden başlamamalı.
# Başlasaydı hareketsiz oyuncuyu gölge ~5 sn içinde yakalar ve oyun sonu gelirdi.
# (clear_log sonrası ekran değişmediği için current_screen boş döner; olaylar doğrudan günlükten okunur)
tap_target PlayScreen play.pause.resume
sleep 8
N0=$(log | grep -c "hedef ekran=PlayScreen ad=play.pause.main_menu")
adbt shell input keyevent KEYCODE_BACK; sleep 0.8
N1=$(log | grep -c "hedef ekran=PlayScreen ad=play.pause.main_menu")
if ! log | grep -q 'ekran=GameOverScreen$' && [ "$N1" -gt "$N0" ]; then ok "Hazır ekranında duraklat → devam: oyun dokunuş bekliyor (8 sn)"
else bad "Hazır ekranında devam: oyun sonu=$(log | grep -c 'ekran=GameOverScreen$') duraklatma menüsü $N0→$N1"; fi
tap_target PlayScreen play.pause.main_menu
expect_screen MenuScreen "Duraklatma → Ana menü"

# 3) Karakterler (debug ekstrasıyla 500 elmas ver, satın al, seç)
adbt shell am start -n $ACT --ei elmas 500 >/dev/null; sleep 1
tap_target MenuScreen menu.character
expect_screen SkinsScreen "Karakter butonu"
sleep 0.6
tap_target SkinsScreen kart-2
sleep 0.4; shot 08_karakterler
D0=$(pref_int diamonds)
tap_target SkinsScreen karakter-satin-al
sleep 0.8
D1=$(pref_int diamonds); SEL=$(pref_int selectedSkin); UNL=$(pref_int unlockedSkins)
if [ "$SEL" = "2" ] && [ $(( ${UNL:-0} & 4 )) -ne 0 ]; then ok "Karakter satın alındı ve seçildi (elmas $D0 → $D1)"; else bad "Satın alma: seçili=$SEL açık=$UNL elmas $D0→$D1"; fi
shot 09_karakter_alindi
adbt shell input keyevent KEYCODE_BACK
expect_screen MenuScreen "Geri tuşu → Ana menü"

# 4) Görevler: günlük, görev modu, başarılar
tap_target MenuScreen menu.tasks
expect_screen MissionsScreen "Görevler butonu"
sleep 0.6; shot 10_gunluk_gorevler
tap_target MissionsScreen missions.tab.maps; sleep 0.4; shot 11_haritalar
tap_target MissionsScreen missions.tab.achievements; sleep 0.4; shot 12_basarilar
tap_target MissionsScreen missions.tab.maps
tap_target MissionsScreen bolum-0
expect_screen PlayScreen "Bölüm 1 başlatma (harita düğümü)"
sleep 0.5
if [ "$(pref_int enLives)" = "3" ]; then ok "Bölüm başlatınca 1 can harcandı (4 → 3)"; else bad "Can harcama: enLives=$(pref_int enLives)"; fi
sleep 0.5; shot 13_gorev_oyun
adbt shell input keyevent KEYCODE_BACK; sleep 0.5
tap_target PlayScreen play.pause.main_menu
expect_screen MenuScreen "Görev → Ana menü"

# 5) Rekorlar
tap_target MenuScreen menu.records
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
tap_target SettingsScreen settings.reset; sleep 0.4; shot 16_sifirlama_onay
tap_target SettingsScreen common.cancel
tap_target SettingsScreen settings.tutorial
expect_screen TutorialScreen "Ayarlar → Öğretici"
sleep 0.6; shot 17_ogretici
tap_target TutorialScreen tutorial.close
expect_screen SettingsScreen "Öğretici kapat → açıldığı Ayarlar ekranına döner"
adbt shell input keyevent KEYCODE_BACK
expect_screen MenuScreen "Ayarlar → geri → Ana menü"

step harita-goruntuleri
# 6b) Farklı haritalardan oynanış görüntüleri (debug ekstrasıyla bölüm aç)
touch_count() { log | grep -c "dokunma a=0"; }
for lv in 2 17 27 37 57 72 77 92 99 149 249 349 449 499; do
  step "bolum $((lv+1))"
  adbt shell am start -n $ACT --es ekran gorev:$lv >/dev/null
  if wait_screen PlayScreen 10; then
    sleep 0.8
    T0=$(touch_count)
    for i in 1 2 3; do
      adbt shell input swipe 300 1600 800 1450 260
      adbt shell input swipe 800 1450 650 1900 260
      adbt shell input swipe 650 1900 300 1600 260
    done
    shot "19_harita_bolum_$((lv+1))"
    T1=$(touch_count)
    if [ "$T1" -ge $((T0 + 6)) ]; then ok "Bölüm $((lv+1)) oynanabilir (girdi işlendi, donma yok)"; else bad "Bölüm $((lv+1)) yanıt vermiyor (dokunma $T0 → $T1)"; fi
  else
    bad "Bölüm $((lv+1)) açılamadı"
  fi
done
adbt shell am start -n $ACT --es ekran menu >/dev/null
if wait_screen MenuScreen 8; then ok "Harita bölümlerinden ana menüye dönüş"; else bad "Harita bölümlerinden menüye dönüş"; fi

# 6c) Çoklu dil: dil seçici, 6 dilde tüm ekranlar, metin taşması ve eksik çeviri taraması
step diller
pref_str() { prefs | grep "name=\"$1\"" | sed -E 's/.*>([^<]*)<.*/\1/'; }
adbt shell am start -n $ACT --es ekran ayarlar >/dev/null
wait_screen SettingsScreen 8; sleep 0.8
tap_target SettingsScreen ayar-dil; sleep 0.6
tap_target SettingsScreen dil-de; sleep 0.8
if log | grep -q "dil=de" && [ "$(pref_str lang)" = "de" ]; then ok "Ayarlar → Dil: Deutsch seçildi, yeniden başlatmadan uygulandı ve kaydedildi"; else bad "Dil seçici: log=$(log | grep -c 'dil=de') kayıt=$(pref_str lang)"; fi
shot 24_ayarlar_de
for L in tr en de it fr es; do
  for E in menu gorevler gunluk basarilar karakter rekorlar ayarlar ogretici gorev:0; do
    adbt shell am start -n $ACT --es dil $L --es ekran $E >/dev/null; sleep 1.5
    case "$E" in menu|gorevler|gorev:0) shot "25_dil_${L}_${E%%:*}";; esac
  done
done
TAS=$(timeout 20 adb logcat -d -s GolgenTasma:W 2>/dev/null | grep "olcek=" | sed -E 's/.*GolgenTasma[^:]*: //' | sort -u)
echo "$TAS" > "$OUT/metin_tasmalari.txt"
if [ -z "$TAS" ]; then ok "6 dilde 9 ekran: metin taşması yok"; else bad "Metin taşması ($(echo "$TAS" | wc -l)): $(echo "$TAS" | head -3 | tr '\n' ';')"; fi
EKS=$(timeout 20 adb logcat -d -s GolgenL10n:W 2>/dev/null | grep "eksik=" | tail -1)
if [ -z "$EKS" ]; then ok "Eksik çeviri anahtarı yok (çalışma anı)"; else bad "Eksik çeviri: $EKS"; fi
# Can kalmadı penceresi
adbt shell am start -n $ACT --es dil tr --ei canlar 0 --es ekran gorevler >/dev/null; sleep 1.5
tap_target MissionsScreen bolum-0; sleep 1.0
if log | grep -q "hedef ekran=LivesDialog ad=can-kapat"; then ok "Can yokken bölüme dokununca 'Canın kalmadı' penceresi açıldı"; else bad "Can kalmadı penceresi açılmadı"; fi
shot 26_can_kalmadi
tap_target LivesDialog can-kapat; sleep 0.6
adbt shell am start -n $ACT --es dil tr --ei canlar 4 --es ekran menu >/dev/null; sleep 1.2

# 7) Kalıcılık: uygulamayı öldür ve yeniden aç → öğretici tekrar gelmemeli, veriler korunmalı
adb shell am force-stop $PKG
clear_log
adb shell am start -W -n $ACT >/dev/null
expect_screen MenuScreen "Yeniden açılış (öğretici atlanır, kayıt yüklenir)" 20
if [ "$(pref_int selectedSkin)" = "2" ]; then ok "Kalıcılık: seçili karakter korundu"; else bad "Kalıcılık: selectedSkin=$(pref_int selectedSkin)"; fi
if [ "$(pref_str lang)" = "tr" ]; then ok "Kalıcılık: dil tercihi korundu"; else bad "Kalıcılık: lang=$(pref_str lang)"; fi
sleep 1; shot 18_yeniden_acilis

# 7b) Reklamlar (ağ gerektirir: yükleme adımları BİLGİ olarak raporlanır, çökme/donma ise hata sayılır)
step reklamlar
soft() { echo "BİLGİ  | $1" | tee -a "$RESULTS"; }
adlog() { timeout 20 adb logcat -d -s GolgenAds:V 2>/dev/null; }
adbt shell am start -n $ACT --ez reklamsiz false >/dev/null
for i in $(seq 1 30); do adlog | grep -q "SDK hazır" && break; sleep 1; done
if adlog | grep -q "SDK hazır"; then ok "Reklam SDK'sı başlatıldı (AdMob test kimlikleri)"; else bad "Reklam SDK'sı başlatılamadı"; fi
for i in $(seq 1 25); do adlog | grep -q "banner yüklendi" && break; sleep 1; done
if adlog | grep -q "banner yüklendi"; then soft "Banner yüklendi (yalnızca menülerde)"; else soft "Banner yüklenemedi: $(adlog | grep banner | tail -1)"; fi
sleep 1; shot 20_reklam_banner_menu
tap_target MenuScreen menu.play
if wait_screen PlayScreen 8; then sleep 1; shot 21_reklam_oyunda_banner_yok; ok "Oyun ekranına geçildi (banner gizli olmalı)"; fi
adbt shell input keyevent KEYCODE_BACK; sleep 0.6; tap_target PlayScreen play.pause.main_menu; wait_screen MenuScreen 8
for i in $(seq 1 20); do adlog | grep -q "geçiş reklamı hazır" && break; sleep 1; done
adbt shell am start -n $ACT --es reklam gecis >/dev/null; sleep 4
if adlog | grep -q "geçiş reklamı gösterildi"; then soft "Geçiş (görsel) reklamı gösterildi"; shot 22_reklam_gecis; else soft "Geçiş reklamı gösterilemedi: $(adlog | grep -i 'geçiş' | tail -1)"; fi
adbt shell input keyevent KEYCODE_BACK; sleep 2
for i in $(seq 1 20); do adlog | grep -q "ödüllü video hazır" && break; sleep 1; done
adbt shell am start -n $ACT --es reklam video >/dev/null; sleep 5
if adlog | grep -q "ödüllü video gösterildi"; then soft "Ödüllü video reklamı gösterildi"; shot 23_reklam_video; else soft "Ödüllü video gösterilemedi: $(adlog | grep -i 'video' | tail -1)"; fi
adbt shell input keyevent KEYCODE_BACK; sleep 2
adb logcat -d >> "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then bad "Reklamlar sırasında çökme"; else ok "Reklamlar sırasında çökme yok"; fi

# 8) Çökme / ANR kontrolü
adb logcat -d >> "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" || grep -q "ANR in $PKG" "$OUT/logcat.txt"; then
  bad "Çökme/ANR bulundu"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
else ok "Çökme veya ANR yok"; fi
grep GolgenFPS "$OUT/logcat.txt" | tail -5 | tee "$OUT/fps.txt" || true

echo "TOPLAM: $PASS geçti, $FAIL kaldı" | tee -a "$RESULTS"
[ "$FAIL" -eq 0 ]
