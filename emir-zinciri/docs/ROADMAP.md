# Yol haritası ve durum

Durum dürüstçe işaretlenmiştir: ✅ çalışıyor ve test edildi · 🟡 kısmen · ⬜ yapılmadı.

## Aşama 1 — Teknik temel ✅
Gradle çok modüllü proje, Kotlin, libGDX 1.13.1, dikey ekran, ekran yönetimi, izometrik harita, Android + masaüstü başlatıcı.

## Aşama 2 — İlk oynanabilir sürüm ✅
Temel robot, komut kartları, dokun/sürükle-bırak düzenleme (ekle, sil, taşı, kopyala, sırala), deterministik simülasyon, başlat/duraklat/tek adım/geri adım/sıfırla, hız 1×/2×/4×, hatalı kart vurgusu, kazanma/kaybetme, yıldızlar, kayıt.

## Aşama 3 — İlk 25 bölüm ✅
5 alan × 5 bölüm, tek robot kuralı. Kutu alma/bırakma/itme, çukur doldurma, anahtar, basınç plakası, ters kapı, zamanlı düğme, bekleme, enerji hücresi, şarj istasyonu, enerji soketi. Etkileşimli öğretici balonları, alan hikâyeleri, YENİ kart rozetleri. Bütün bölümler otomatik doğrulanır.

## Aşama 4 — Çoklu robot ⬜ (sıradaki)
Bölüm 26–50, ikinci robot, eş zamanlı zincirler (motor ve arayüz zaten çoklu robotu destekliyor; mesaj gönder/bekle, robotlar arası enerji aktarımı ve çakışma kuralları testli), çok robotlu çözücü, hareketli bantlar, lazerler, senkron varış.

## Aşama 5 — Kalıcı fabrika ⬜
Kaynaklar (altın, metal, mikroçip) bölümlerden zaten birikiyor ve kaydediliyor; fabrika ekranı, binalar, araştırma ve robot geliştirme henüz yok.

## Aşama 6–9 ⬜
Robot sınıfları, savaş, düşman yapay zekâsı, patronlar; günlük/haftalık görevler, sonsuz mod, market, harita editörü, koleksiyon, başarımlar; 500 bölüme genişleme; müzik ve yayın hazırlığı.

## Bilinen eksikler
- Müzik yok (yalnızca ses efektleri); ayarlarda müzik seçeneği bu yüzden gösterilmiyor.
- Gerçek Android cihazda/emülatörde oynanış testi bu ortamda yapılmadı; APK derleniyor, oyun masaüstünde sanal ekranda çalıştırılıp ekran görüntüleriyle kontrol edildi.
