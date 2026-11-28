#!/usr/bin/env bash
# Prismway / Işık Yolu — yayın paketinin medya ve derleme çıktılarını baştan üretir.
#   tools/magaza/build_release_package.sh <Roboto klasörü> [kayıt klasörü]
# Gerekenler: Android SDK (local.properties), JDK 17, Python 3 + Pillow + NumPy, ffmpeg (libx264, libvorbis).
# Çıktılar RELEASE_PACKAGE/ altına yazılır. İmza: ~/.gradle/gradle.properties içinde uploadStoreFile/… verilirse
# AAB senin yükleme anahtarınla imzalanır; verilmezse debug anahtarıyla (yalnızca test).
set -euo pipefail
cd "$(dirname "$0")/../.."
FONTS=${1:?Roboto yazı tipi klasörü gerekli (Roboto-Black/Bold/Medium.ttf)}
REC=${2:-$(mktemp -d)/kayit}
LANGS=(tr en de it fr es)
OUT=RELEASE_PACKAGE

echo "== 1/5 testler"
./gradlew -q :engine:test :app:testDebugUnitTest

echo "== 2/5 gerçek oyun kayıtları (6 dil, telefon + tablet)"
for l in "${LANGS[@]}"; do
  ./gradlew -q :app:testDebugUnitTest --tests '*PromoCaptureTest*' --tests '*TabletCaptureTest*' -PpromoDir="$REC/$l" -PpromoLang="$l"
done

echo "== 3/5 mağaza görselleri, fragmanlar, sosyal medya"
python3 tools/magaza/release_media.py --rec "$REC" --fonts "$FONTS" --sounds app/src/main/res/raw \
  --texts $OUT/06_Store_Descriptions --out $OUT --langs "$(IFS=,; echo "${LANGS[*]}")"
python3 tools/magaza/package_media.py --rec "$REC" --out $OUT
python3 tools/magaza/logos.py --fonts "$FONTS" --out $OUT/03_Logos
python3 tools/magaza/store_texts.py $OUT/06_Store_Descriptions

echo "== 4/5 APK ve AAB"
./gradlew -q :app:assembleRelease :app:bundleRelease
V=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
mkdir -p $OUT/01_Game_Build/APK $OUT/01_Game_Build/AAB
cp app/build/outputs/apk/release/app-release.apk "$OUT/01_Game_Build/APK/Prismway-IsikYolu-$V.apk"
cp app/build/outputs/bundle/release/app-release.aab "$OUT/01_Game_Build/AAB/Prismway-IsikYolu-$V.aab"

echo "== 5/5 ZIP arşivleri (her biri ≤ 30 MB)"
python3 tools/magaza/package_media.py --zip-only --out $OUT
echo "tamam: $OUT"
