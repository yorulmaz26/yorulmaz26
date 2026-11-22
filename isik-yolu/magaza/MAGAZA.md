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
| `simge_512.png` | 512×512 PNG | Uygulama simgesi |
| `one-cikan-gorsel_1024x500.png` | 1024×500 PNG (saydamlık yok) | Öne çıkan grafik |
| `ekran-goruntuleri/01…08_*.png` | 1080×1920 PNG, Türkçe başlıklı | Telefon ekran görüntüleri (en az 4 tane, en fazla 8) |
| `ekran-goruntuleri-yalin/01…08_*.png` | 1080×1920 PNG, başlıksız | Aynı görüntülerin yalın hâli: yedek ya da başka diller için |
| `video/tanitim-30sn.mp4` | 1920×1080, 30 FPS, H.264 + AAC, tam 30 sn | Tanıtım videosu (YouTube) |
| `video/tanitim-60sn.mp4` | 1920×1080, 30 FPS, H.264 + AAC, tam 60 sn | Tanıtım videosu (YouTube) |

- **Video yükleme:** Play Console video dosyası kabul etmez, YouTube bağlantısı ister. Videoyu YouTube'a "Herkese
  açık" ya da "Liste dışı" olarak yükle ve bağlantıyı "Tanıtım videosu" alanına yapıştır. Video, ekran görüntülerinin
  başında gösterilir. Önerilen: 30 sn'lik sürüm. 60 sn'lik sürüm YouTube kanalı ve sosyal medya için uygun.
- **Videonun içeriği:** Açılış kartıyla başlar ve gerçek oynanışı gösterir. Gösterilen mekanikler: aynalar, prizma,
  renk karışımı, bloklar, kapı/anahtar, mantık bölümü ve görünümler. Ardından bölüm seçimi ile günlük bulmaca gelir,
  kapanış kartıyla biter. Dokunuşlar beyaz halkayla gösterilir.
- **Ses:** oyunun kendi müziği ve ses efektleri. Lisans sorunu yok, hepsi oyun için üretildi.
- **Yazı tipi:** Roboto, Apache 2.0 lisanslı; oyunun arayüzündeki yazı tipiyle aynı.

## Mağaza metinleri (önerilen)

**Uygulama adı** (en fazla 30 karakter):
> Işık Yolu: Ayna Bulmacası

**Kısa açıklama** (en fazla 80 karakter):
> Aynaları döndür, ışığı hedefe ulaştır! 500 bölümlük mantık bulmacası.

**Tam açıklama:**
> Işık Yolu, ışığı aynalarla yönlendirdiğin sakin ama düşündüren bir bulmaca oyunu.
>
> Aynaları döndür, ışık hüzmesini hedeflere ulaştır. Prizmalarla beyaz ışığı renklerine ayır. Renkleri birleştirerek
> sarıyı, pembeyi, camgöbeğini yak. Taşları kaydırıp yol aç, anahtarları ışıkla yakıp kapıları aç.
>
> ★ 500 bölüm, 5 dünya: kolay başlangıçtan ustalık isteyen mantık bölümlerine
> ★ Mantık bölümleri: rastgele dokunarak geçilmez, önce düşünmek gerekir
> ★ Her bölümde en az hamleyle bitir, 3 yıldız topla
> ★ Her gün yeni bir günlük bulmaca ve seri ödülleri
> ★ Aynalar, tahtalar ve ışın efektleri için görünümler
> ★ Takıldığında yol gösteren akıllı ipuçları
> ★ İnternet olmadan oynanır
>
> Kısa molalar için de, uzun düşünme oturumları için de uygun. Işığı yönlendir, bulmacayı çöz!

**Kategori:** Oyun › Bulmaca · **Etiketler:** Bulmaca, Mantık, Beyin oyunları

**İçerik:** Reklam içerir: **Evet**. Bu, oyunda gösterilen reklamlar için zorunlu beyandır; mağaza görsellerinde
reklam gösterilmesi gerekmez. Uygulama içi satın alma yok: market yalnızca oyun içi parayla çalışır.

**Gizlilik politikası:** `docs/GIZLILIK.md` metnini bir web adresinde yayımla ve adresi buraya gir.

## Kontrol listesi (Play şartları)

- [x] Simge 512×512, 32 bit PNG.
- [x] Öne çıkan grafik 1024×500, saydamlıksız PNG.
- [x] En az 4 telefon ekran görüntüsü:
  - 1080×1920 (9:16; uzun kenar kısa kenarın 2 katını geçmiyor),
  - saydamlıksız PNG,
  - tanıtım alanlarında öne çıkabilmek için en az 1080 px.
- [x] Görüntüler ve video gerçek oynanışı gösterir; yanıltıcı içerik ya da reklam yoktur.
- [x] Video 1920×1080. Play önerisine uygun olarak yatay, kısa ve ilk saniyelerde oynanışa geçiyor.
- [ ] Videoyu YouTube'a yükle, bağlantıyı Play Console'a gir (senin hesabınla yapılmalı).
- [ ] Gizlilik politikası adresi, reklam beyanı, veri güvenliği formu ve hedef kitle (Play Console).
