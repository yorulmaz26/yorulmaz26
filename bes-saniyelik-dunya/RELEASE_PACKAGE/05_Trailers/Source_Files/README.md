# Tanıtım videoları — düzenlenebilir kaynaklar

Videolar tamamen gerçek oyun görüntüsünden, betikle kurgulanır; yeniden üretmek ya da değiştirmek için:

1. **Oyun kaydı** (her dil için; ~12 dk): oyun masaüstünde sabit 30 kare/sn ile oynatılır, kareler doğrudan ffmpeg'e yazılır.
   ```bash
   xvfb-run -a -s "-screen 0 2600x1700x24" ./gradlew :desktop:run -Drecord=<klasör> -Dwin=1920x1080 -Dlang=tr -DrecLevels=2,160,215,280,330,470,120
   rm -rf assets/save
   ```
   Çıktı: lv002.mp4, lv160.mp4 … lv120.mp4, maps.mp4, characters.mp4, levelmap.mp4, endless.mp4, menu.mp4 (H.264, CRF 12).
   Ham kayıtlar toplam ~600 MB olduğu için teslim paketine konmadı; komutla birebir yeniden üretilir.
2. **Kurgu**: `trailer_edl.json` (hangi kayıt, hangi saniye, hangi yazı). Yazılar: `video_captions.json` (tr, en).
3. **Video üretimi**:
   ```bash
   python3 tools/media/compose_video.py tools/media/trailer_edl.json <kayıt_klasörü> tr vertical trailer_tr_vertical.mp4
   python3 tools/media/compose_video.py tools/media/trailer_edl.json <kayıt_klasörü> tr horizontal trailer_tr_horizontal.mp4
   python3 tools/media/compose_video.py ... vertical trailer_tr_vertical_social.mp4 --cta      # "Maceraya Başla!"
   python3 tools/media/compose_video.py ... vertical trailer_tr_vertical_social_after_launch.mp4 --launch   # "Hemen Oyna!" (yayından sonra)
   ```
4. **Müzik**: `tools/media/music.py` ile kodla sentezlenen özgün parça (dış kaynak yok; ticari kullanım serbest).

Kurgu (30 sn): 0–3 sn ters yer çekiminde tavanda koşu (dikkat çekme) · 3–8 dokun/basılı tut ipucu (temel oynanış) · 8–15 kural değişimleri ve testereler · 15–22 haritalar, karakterler, Sonsuz Dünya · 22–27 bölüm sonu ve 3 yıldız · 27–30 iki dilli logo, slogan, Magic Lumos Software.
