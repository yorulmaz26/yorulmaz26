# World Wonders — Teknik Geliştirme Planı ve Durum

Durum etiketleri: **Tamam** (çalışıyor ve otomatik testle doğrulandı) · **Kod tamam** (yazıldı, Unity olmadan derleme denetiminden geçti, Unity Editor'de çalıştırılarak doğrulanmadı) · **Geçici** (işlevsel ama geçici görsel) · **Yapılmadı**.

> Önemli sınır: Bu çalışma ortamında Unity Editor yok. Çekirdek (oyun kuralları) .NET üzerinde derlenip testlerle doğrulandı. Unity katmanı (`Scripts/Game`) UnityEngine 2021.3 referans derlemelerine karşı derleme denetiminden geçirildi; Unity 6 Editor'de açılıp oynanarak doğrulanması, cihaz testi ve performans ölçümü henüz yapılmadı.

| # | Aşama | Çekirdek (kurallar) | Unity katmanı | Not |
|---|---|---|---|---|
| 1 | Unity altyapısı ve kayıt sistemi | Tamam | Kod tamam | Atomik kayıt + .bak + SHA-256, işlem kimlikleri, yedek al/geri yükle, açılışta terk edilen bölüm cezası |
| 2 | Match-3 motoru | Tamam | Kod tamam | 6 taş, 7–9 boyut, düzensiz tahtalar, Ok/Bomba/Küre + kombinasyonlar, güvenli karıştırma, kum/sandık/zincir |
| 3 | Kolezyum'un 60 bölümü | Tamam | — | Aslında 420 bölümün tamamı üretildi ve dengelendi |
| 4 | Jigsaw ve 3D Kolezyum inşası | Tamam | Kod tamam · Geçici | Sürükle-bırak yerleştirme; 7 anıt × 12 inşa grubu prosedürel |
| 5 | Kolezyum destinasyon haritası | Tamam | Kod tamam · Geçici | 7 temanın hepsi |
| 6 | Keşif araçları ve keşif noktaları | Tamam | Kod tamam | 84 nokta, 3 mini oyun, araç yükseltme |
| 7 | Dünya Müzesi | Tamam | Kod tamam · Geçici | Atrium + 7 galeri × 6 vitrin, 3D inceleme |
| 8 | Kâşif karakteri ve özelleştirme | Tamam | Kod tamam · Geçici | NavMesh (çalışma anı), 6 animasyon durumu, 2 model |
| 9 | Günlük görevler ve 7 günlük seri | Tamam | Kod tamam | 04.00 yenileme, saat geri alma koruması |
| 10 | XP ve ustalık | Tamam | Kod tamam | 19.005 tek seferlik XP ve 29.205 XP eğrisi testle doğrulandı |
| 11 | Kişisel profil | Tamam | Kod tamam | Çevrimdışı |
| 12 | Kişisel Kâşif Odası | Tamam | Kod tamam · Geçici | Müzenin içinde, büyütme yok |
| 13 | Diğer altı harika | Tamam | Kod tamam · Geçici | İçerik (alanlar, eserler, kozmetikler) ve 360 bölüm dahil |
| 14 | Optimizasyon, test, yayın hazırlığı | Kısmen | Yapılmadı | Aşağıdaki listeye bakın |

## Kalan işler (dürüst liste)

1. **Unity Editor doğrulaması:** projeyi Unity 6000.0 LTS'te açmak, derleme hatalarını (Unity 6'ya özgü API farkları) gidermek, her ekranı oynayarak doğrulamak.
2. **Final görseller:** bütün 3D modeller, ikonlar ve mini oyun görselleri prosedürel geçici varlıktır (`PlaceholderAsset`, oyunda "Geçici 3D görseller" etiketi). Stilize final modeller, dokular, VFX ve animasyonlu karakter riggi gerekir. Final varlıklar **Addressables** ile yüklenecek (paket manifestte; şu an yüklenecek final varlık olmadığı için kullanılmıyor).
3. **Ses ve müzik:** ayar anahtarları var, ses varlıkları ve ses yöneticisi yok.
4. **Kısa dokunsal geri bildirim:** Unity yerleşik API'si yalnız uzun titreşim veriyor; iOS/Android kısa haptik için yerel eklenti.
5. **Cihaz performansı:** düşük seviye Android cihazlarda profil çıkarma, nesne birleştirme/GPU instancing ölçümü, termal test.
6. **İnsan oyun testi ile denge:** bölümler açgözlü botla dengelendi (`Docs/LEVEL_REPORT.md`); gerçek oyuncu verisiyle ayar gerekir.
7. **Mağaza yayını:** uygulama ikonları, mağaza görselleri, gizlilik metni, imzalama (Android keystore, iOS sertifikaları), yaş derecelendirmesi.
8. **Yerelleştirme:** metinler şu an Türkçe ve kodda; çok dil için tablo yapısına taşınmalı.
