#!/usr/bin/env bash
# Tanıtım videolarını üretir: gerçek oynanış kaydı (TrailerRunner) + müzik + altyazı + yatay sürüm + kapaklar.
# Kullanım: bash tools/trailer/make_trailers.sh [çıktı_klasörü]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:-$ROOT/RELEASE_PACKAGE/05_Trailers}"
RAW="$OUT/Source_Files/raw"
mkdir -p "$RAW" "$OUT"/{Turkish,English,Vertical,Horizontal,Social_Media,Subtitles,Source_Files}
MUSIC="$ROOT/android/assets/music/candy.ogg"
SFX="$ROOT/android/assets/sfx"
cd "$ROOT"
for lang in tr en; do
  for variant in store social; do
    f="$RAW/${lang}_${variant}_silent.mp4"
    if [ ! -s "$f" ]; then
      xvfb-run -a -s "-screen 0 1100x1960x24" ./gradlew :desktop:trailer -q -Plang=$lang -Pvariant=$variant -Ptrailer_out="$f" >/dev/null 2>&1
    fi
    # müzik (döngü, giriş/çıkış sönümü) + bölüm bitti ve marka efektleri; AAC 192k
    ffmpeg -v error -y -i "$f" -stream_loop -1 -i "$MUSIC" -i "$SFX/level_complete.ogg" -i "$SFX/achievement.ogg" -i "$SFX/go.ogg" \
      -filter_complex "[1:a]atrim=0:30,afade=t=in:d=0.4,afade=t=out:st=28.2:d=1.8,volume=0.85[m];[2:a]adelay=22100|22100,volume=0.9[a2];[3:a]adelay=27100|27100,volume=0.9[a3];[4:a]adelay=200|200,volume=0.7[a4];[m][a2][a3][a4]amix=inputs=4:normalize=0,atrim=0:30[a]" \
      -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -ar 48000 -movflags +faststart -t 30 "$RAW/${lang}_${variant}.mp4"
  done
  cp "$RAW/${lang}_store_silent.srt" "$OUT/Subtitles/trailer_${lang}.srt"
done
# dikey (Play / mağaza: indirme çağrısı yok) ve sosyal (çağrılı) sürümler
cp "$RAW/tr_store.mp4" "$OUT/Turkish/trailer_tr_vertical.mp4"
cp "$RAW/en_store.mp4" "$OUT/English/trailer_en_vertical.mp4"
cp "$RAW/tr_store.mp4" "$OUT/Vertical/trailer_tr_vertical.mp4"
cp "$RAW/en_store.mp4" "$OUT/Vertical/trailer_en_vertical.mp4"
cp "$RAW/tr_social.mp4" "$OUT/Social_Media/trailer_tr_social_vertical.mp4"
cp "$RAW/en_social.mp4" "$OUT/Social_Media/trailer_en_social_vertical.mp4"
# yatay 1920x1080: ortada dikey oynanış, yanlarda bulanık genişletilmiş arka plan (siyah bant yok)
for lang in tr en; do
  ffmpeg -v error -y -i "$RAW/${lang}_store.mp4" -filter_complex \
    "[0:v]scale=1920:-2,crop=1920:1080,boxblur=28:2,eq=brightness=0.04:saturation=1.2[bg];[0:v]scale=-2:1080[fg];[bg][fg]overlay=(W-w)/2:0,format=yuv420p" \
    -map 0:a -c:v libx264 -preset medium -crf 19 -c:a copy -movflags +faststart "$OUT/Horizontal/trailer_${lang}_horizontal.mp4"
done
# kapaklar
# kapaklar: tools/store/make_store_assets.py (07_Social_Media) ile üretilip buraya kopyalanır
cp "$ROOT/tools/trailer/captions_tr.json" "$ROOT/tools/trailer/captions_en.json" "$0" "$ROOT/tools/trailer/make_covers.py" "$ROOT/desktop/src/main/kotlin/com/yanlisrenk/desktop/TrailerRunner.kt" "$OUT/Source_Files/"
echo "tamam: $OUT"
