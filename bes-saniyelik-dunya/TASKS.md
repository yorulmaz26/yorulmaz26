# Görevler

## Tamamlanan
- [x] Proje iskeleti, Android/desktop başlatıcıları, Box2D entegrasyonu
- [x] Oyuncu kontrolü: otomatik koşu, dokun-zıpla, basılı tut-yüksek zıpla, coyote time, tampon, çift zıplama (güçlendirme)
- [x] 10 dünya mekaniği + kombinasyonlar + uyumluluk matrisi + öncelik + uyarı
- [x] 500 bölüm (veri odaklı JSON), çözülebilirlik doğrulaması, yıldız/altın/parça yerleşimi
- [x] Sonsuz Dünya (deterministik parça üretimi, artan hız, puan, rekorlar, günlük sonsuz görevleri)
- [x] XP/seviye, görevler, başarılar, koleksiyon, giriş ödülü, mağaza, karakterler, iz efektleri
- [x] 8 güçlendirme (kalkan, mıknatıs, çift zıplama, dokunulmazlık, ekstra can, altın çarpanı, yıldız algılayıcı, kontrol noktası koruması)
- [x] 20 ekran, programatik grafikler ve sesler
- [x] Güvenilir kayıt (CRC, atomik yazma, yedek, sürüm göçü)
- [x] Otomatik testler (61 birim/entegrasyon testi + masaüstü arayüz oynatma turu)
- [x] Debug APK, release APK, release AAB

## Kullanıcı onayı / erişimi gerektiren
- [ ] Google Play yükleme anahtarı: `keystore.properties` (örnek: `keystore.properties.example`) oluşturulup `./gradlew :android:bundleRelease` yeniden çalıştırılmalı. Şu anki release çıktıları **hata ayıklama anahtarıyla** imzalıdır (kurulum ve deneme için uygundur, Play'e yüklenemez).
- [ ] Play Console: geliştirici hesabı, gizlilik politikası sayfasının yayımlanması, uygulama içeriği formları ve mağaza sayfası. Adım adım: `docs/play_store/YAYIN_REHBERI.md`; metinler `docs/play_store/magaza_metinleri.md`; gizlilik metni `docs/play_store/gizlilik_politikasi.md`; 8 ekran görüntüsü `docs/play_store/ekran_goruntuleri/`; simge `docs/play_icon_512.png`; öne çıkan görsel `docs/play_feature_graphic.png`.
- [ ] Yeni kişisel hesapta kapalı test: en az 12 kullanıcı, 14 gün kesintisiz; ardından üretim erişimi başvurusu.
- [ ] Gerçek Android cihazda test (bu ortamda cihaz/emülatör yok): dahili test kanalında açılış, dokunma, geri tuşu (Android 16), çentikli ekranda üst köşe düğmeleri.

## İyileştirme fikirleri
- [ ] libGDX 1.14.1 veya sonrasına geçiş (Android 16 geri hareketinin yeni yolu; şu an Google'ın geçici saydığı `enableOnBackInvokedCallback="false"` kullanılıyor).
- [ ] Nisan 2027'den sonra mağaza sayfası güncellenirse katlanabilir cihaz ekran görüntüleri (1:1, 4:3, 16:10); Ağustos 2027'den sonraki güncellemeler için katlanır cihaz / pencere boyutlandırma denemesi.
- [ ] Yüksek Zıplama / Küçük / Ters Kontrol aşamalarının %9–17'sinde kurala özgü desen (yüksek duvar, dar geçit, asılı diken) yer kalmadığı için yok; kural yine de karakterin kendisinde görünür. (Hayalet ve Değişen Platformlar aşamalarında eksik yok.)
- [ ] 36 aşama (3 870'te) boş: yer çekimi dönüşü / boy değişimi sonrası çok kısa kalan Yavaş Zaman aşamaları.
- [ ] Zorluk ölçümü bir oyuncu modelidir; gerçek oyuncu verisiyle (ölüm konumları, bölüm başına deneme sayısı) doğrulanması önerilir.
- [ ] İsteğe bağlı bulut kaydı / liderlik tablosu (ilk sürümde çevrimdışı kalması istendi).
