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
- [x] Otomatik testler (39 birim/entegrasyon testi + masaüstü arayüz oynatma turu)
- [x] Debug APK, release APK, release AAB

## Kullanıcı onayı / erişimi gerektiren
- [ ] Google Play yükleme anahtarı: `keystore.properties` (örnek: `keystore.properties.example`) oluşturulup `./gradlew :android:bundleRelease` yeniden çalıştırılmalı. Şu anki release çıktıları **hata ayıklama anahtarıyla** imzalıdır (kurulum ve deneme için uygundur, Play'e yüklenemez).
- [ ] Play Console mağaza kaydı (ekran görüntüleri `docs/screenshots/`, simge `docs/play_icon_512.png`).
- [ ] Gerçek Android cihazda test (bu ortamda cihaz/emülatör yok).

## İyileştirme fikirleri
- [ ] 12 bölümde (bkz. `docs/level_report.tsv`, "dar") zamanlama penceresi ±2 tick hedefinin altında kalıyor; bölümler çözülebilir ama dardır.
- [ ] İsteğe bağlı bulut kaydı / liderlik tablosu (ilk sürümde çevrimdışı kalması istendi).
