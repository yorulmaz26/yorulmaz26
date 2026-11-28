# Test raporu — sürüm 3.0 (13) · 28 Kasım 2026

Ortam: Linux bulut konteyneri. **Gerçek cihaz ve Android emülatörü yok** (donanım hızlandırma/KVM yok). Arayüz ve oynanış testleri, oyunun gerçek kodunu Robolectric (gerçek Android grafik motoru, SDK 34) üzerinde çalıştırarak yapıldı.

| Test | Sonuç | Durum |
|---|---|---|
| Can sistemi birim testleri (18 test: 20 dk dolum, 0→4 can 20/40/60/80 dk, en fazla can, sıfır can, harcamada sayaç sıfırlanmaması, uygulama kapalı/zorla durdurulmuş, sürekli aç-kapa, saat ileri/geri/tarih/saat dilimi, internetli/internetsiz yeniden başlatma, saat ileri + yeniden başlatma hilesinin internetle geri alınması, kayıt elle değiştirme, 2.2 sürümünden güncelleme, reklam/para ile can, atomik kayıt, "19:42" biçimi) | 18/18 geçti | TEST EDİLDİ |
| Çeviri denetimi (her derlemede): 5 çeviri dili × 173 anahtar, yer tutucular | Eksik 0 | TEST EDİLDİ |
| Android lint | 0 hata, 39 uyarı (çoğu tipografi/eski API; engelleyici değil) | TEST EDİLDİ |
| Arayüz görüntüleri 6 dilde (ana ekran, ayarlar, can penceresi, bölüm girişi, bölümler, market, sonsuz mod, oyun sonu) | Metin taşması yok; uzun kelimeler otomatik küçülüyor | TEST EDİLDİ |
| Reklam yerleşimi 4 ekran tipinde (20:9, 16:9, 3 düğmeli gezinme, bölünmüş ekran) | Banner düğmelerle çakışmıyor | TEST EDİLDİ (sahte reklamla) |
| 500 bölüm geçilebilirlik simülasyonu (6 parkur × 500) | 3000/3000 geçildi | TEST EDİLDİ (2.2'de; 3.0'da fizik değişmedi) |
| Otomatik pilotla gerçek oynanış (10 harita, fragman/ekran görüntüsü kayıtları) | Kayıtlar yanmadan tamamlandı | TEST EDİLDİ |
| 2 oyunculu maç (iki gerçek oyun örneği, mesajlar doğrudan iletilerek) | Bölünmüş ekran çalışıyor | TEST EDİLDİ (gerçek Wi-Fi yok) |
| Gerçek cihazda kurulum/çalıştırma, gerçek reklamlar, gerçek Wi-Fi çok oyunculu, performans (FPS, RAM, pil, ısınma), düşük donanım | Yapılamadı | MANUEL KONTROL GEREKLİ |
| Gerçek reklam tıklaması / gerçek satın alma | Yapılmadı (yasak + satın alma yok) | UYGULANAMAZ |

## Bilinen uyarılar
- Lint "LockedOrientationActivity": oyun bilinçli olarak dikeydir; Android 16 büyük ekran kuralından `appCategory="game"` ile muaftır.
- Fragman/ekran görüntüleri oyunun kendi çizim koduyla üretildi; fiziksel cihaz ekran görüntüsü değildir.
