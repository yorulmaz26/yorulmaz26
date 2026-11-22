# Işık Yolu — Google Play mağaza malzemesi

Bu klasördeki her şey oyunun kendisinden üretildi. Görüntüler gerçek oyun ekranıdır. **Hiçbir görselde ya da
videoda reklam yoktur:** kayıt, reklam sağlayıcısı kapalıyken yapıldı.

Yeniden üretmek için:
```
./gradlew :app:testDebugUnitTest --tests '*PromoCaptureTest' -PpromoDir=/tmp/kayit
python3 tools/magaza/magaza.py --promo /tmp/kayit --fonts <Roboto klasörü> --sounds app/src/main/res/raw --out magaza
```

## Dosyalar ve Play Console'daki yerleri

| Dosya | Boyut | Play Console alanı |
|---|---|---|
| `simge_512.png` | 512×512, 32 bit PNG (RGBA) | Uygulama simgesi |
| `one-cikan-gorsel_1024x500.png` | 1024×500 PNG (saydamlık yok) | Öne çıkan grafik |
| `ekran-goruntuleri/01…08_*.png` | 1080×1920 PNG, Türkçe başlıklı | Telefon ekran görüntüleri (en az 4 tane, en fazla 8) |
| `ekran-goruntuleri-yalin/01…08_*.png` | 1080×1920 PNG, başlıksız | Aynı görüntülerin başlıksız hâli (kendi başlık tasarımın için) |
| `aciklama-kisa.txt`, `aciklama-tam.txt` | düz metin | Kısa ve tam açıklama: olduğu gibi kopyalanır |
| `video/tanitim-30sn.mp4` | 1920×1080, 30 FPS, H.264 + AAC, tam 30 sn | Tanıtım videosu (YouTube) |
| `video/tanitim-60sn.mp4` | 1920×1080, 30 FPS, H.264 + AAC, tam 60 sn | Tanıtım videosu (YouTube) |

- **Video yükleme:** Play Console video dosyası kabul etmez, YouTube bağlantısı ister.
  1. Videoyu YouTube'a "Herkese açık" ya da "Liste dışı" olarak yükle. "Gizli" olursa Play'de oynamaz.
  2. YouTube Studio'da bu videonun **para kazanmasını kapalı** tut. Açık olursa mağaza sayfasında videodan önce reklam
     çıkabilir.
  3. "Yerleştirmeye izin ver" açık olsun; yaş kısıtlaması koyma.
  4. Tek videonun bağlantısını (`https://www.youtube.com/watch?v=…`) Play Console'daki "Tanıtım videosu" alanına yapıştır.
     Oynatma listesi, kanal ya da Shorts bağlantısı olmasın. Videolar 16:9 olduğu için Shorts'a dönüşmez.
  5. Bağlantıyı gizli pencerede açıp reklamsız başladığını kontrol et.
  Önerilen: 30 sn'lik sürüm. 60 sn'lik sürüm YouTube kanalı ve sosyal medya için uygun.
- **Videoların içeriği:** iki video da açılış kartıyla başlar ve 2–3 sn içinde gerçek oynanışa geçer. Dokunuşlar beyaz
  halkayla gösterilir.
  - **30 sn:** aynalar, prizma, renk karışımı, yıldızlar ("Kusursuz!" penceresi bir kez), bloklar, anahtar/kapı,
    mantık bölümü, 5 dünya / bölüm seçimi, kapanış kartı.
  - **60 sn:** 30 sn'deki her şeye ek olarak dünya 5 usta bölümü, iki bloklu bölüm, prizma + süzgeç + taş bölümü,
    görünümler, günlük bulmaca ve ana menü ("İnternetsiz oyna").
- **Ses:** oyunun kendi müziği ve ses efektleri. Lisans sorunu yok, hepsi oyun için üretildi.
- **Yazı tipi:** Roboto, Apache 2.0 lisanslı; oyunun arayüzündeki yazı tipiyle aynı.

## Mağaza metinleri (önerilen)

**Uygulama adı** (en fazla 30 karakter):
> Işık Yolu: Ayna Bulmacası

**Kısa açıklama** (en fazla 80 karakter; `aciklama-kisa.txt`):
> Aynaları döndür, ışığı hedefe ulaştır! 500 bölümlük mantık bulmacası.

**Tam açıklama:** `aciklama-tam.txt` dosyasındaki metni olduğu gibi kopyala. Satır sonları yalnızca paragraf ve madde
aralarındadır.

**Kategori:** Oyun › Bulmaca · **Etiketler:** Bulmaca, Mantık, Beyin oyunları

**İçerik:** Reklam içerir: **Evet**. Bu, oyunda gösterilen reklamlar için zorunlu beyandır; mağaza görsellerinde
reklam gösterilmesi gerekmez. Uygulama içi satın alma yok: market yalnızca oyun içi parayla çalışır.

**Gizlilik politikası:** `docs/GIZLILIK.md` metnini bir web adresinde yayımla ve adresi buraya gir.

## Kontrol listesi (Play şartları)

- [x] Simge 512×512, 32 bit PNG (RGBA, opak kare; köşe maskesini Play uygular).
- [x] Öne çıkan grafik 1024×500, saydamlıksız PNG.
- [x] En az 4 telefon ekran görüntüsü:
  - 1080×1920 (9:16; uzun kenar kısa kenarın 2 katını geçmiyor),
  - saydamlıksız PNG,
  - tanıtım alanlarında öne çıkabilmek için en az 1080 px.
- [x] Görüntüler ve video gerçek oynanışı gösterir; yanıltıcı içerik ya da reklam yoktur.
- [x] Video 1920×1080. Play önerisine uygun olarak yatay, kısa ve ilk saniyelerde oynanışa geçiyor.
- [x] Görsellerde cihaz çerçevesi, "hemen indir" türü eylem çağrısı, fiyat ve sıralama iddiası yok.
- [ ] YouTube'da bu videonun para kazanması kapalı ve yerleştirme izni açık.
- [ ] Videoyu YouTube'a yükle, bağlantıyı Play Console'a gir (senin hesabınla yapılmalı).
- [ ] Gizlilik politikası adresi (`docs/GIZLILIK.md` yayımlanmış hâli).
- [ ] "Reklam içerir: Evet" beyanı.
- [ ] Reklam kimliği beyanı: Evet. Uygulama `AD_ID` iznini AdMob için kullanıyor.
- [ ] Veri güvenliği formu (AdMob: cihaz/reklam kimliği, uygulama etkileşimleri, tanılama, yaklaşık konum).
- [ ] İçerik derecelendirmesi (IARC anketi) ve hedef kitle beyanı.
