# İlerleme

| Aşama | Durum | Not |
|---|---|---|
| 1 — Temel altyapı | ✅ | Gradle (AGP 8.11, Kotlin 2.1.21, libGDX 1.13.1, Box2D, FreeType), Android/desktop başlatıcıları, karakter kontrolleri (coyote time, zıplama tamponu, değişken zıplama), kamera |
| 2 — Dünya kuralları | ✅ | 10 mekanik, konuma bağlı 5 saniyelik aşamalar, uyumluluk matrisi + öncelik, uyarı ve geçiş efektleri |
| 3 — İlk oynanabilir içerik | ✅ | Ana menü, eğitim bölümleri 1–10 (ipuçları, etkileşimli "dokun" anı), Yeşil Vadi grafikleri, sonsuz mod, kayıt sistemi |
| 4 — Bölüm üretimi | ✅ | 10 harita × 10 alan × 5 bölüm = 500 bölüm, 100 farklı alan, 90 meydan okuma, 9 harita finali + büyük final; çözücü ile doğrulandı |
| 5 — Süreklilik sistemleri | ✅ | XP/seviye, yıldız, altın/elmas, 20 karakter, 9 iz efekti, günlük/haftalık görevler, 50 başarı, koleksiyon, 7 günlük giriş ödülü, mağaza |
| 6 — Görsel ve ses kalitesi | ✅ | 20 ekran, 10 açık renkli tema, parçalı karakter animasyonları, parçacıklar, 16 efekt + 12 müzik (sentez) |
| 7 — Optimizasyon ve teslim | ✅ | Görünür alan kırpma, nesne havuzu, tek atlas sayfaları, az efekt modu; APK + AAB; dokümantasyon ve test raporu |

Ayrıntılı sonuçlar: `TEST_REPORT.md`. Mimari: `ARCHITECTURE.md`. Kalan işler: `TASKS.md`.

### 20 Kasım — mantık ve görüntü denetimi

* Çarpışma kutusu karakter görseline uyduruldu (dikenlere görsel olarak sürtüp ölmeme hatası), kalıplar ve 500 bölüm yeniden üretildi; düşüşte zemine gömülme giderildi.
* Sonuç pencereleri sabit boyutlu.
* 102 bölüm (her alandan bir) oyun ekranında baştan sona oynatıldı: 102/102; bulunan görüntü hataları düzeltildi (süslerdeki çerçeve çizgileri, üst üste binen bildirimler, 2. harita zemini, eksik font simgeleri, profil metni).
* Testler: 41/41.

### 21 Kasım — bölüm mantığı denetimi ve zorluk kalibrasyonu

* Dört mercekli denetim + kendi ölçümlerim: 17 mantık hatası düzeltildi (ters yer çekimiyle biten 99 bölüm, yükselen platformda zıplama, hareketli testere, kontrol noktası döngüsü, sonsuz modda ekstra can, çift sayım, hayalet kural değişimleri, Kaos Duvarı ...).
* Dikenler/testereler kırmızı, konturlu ve iri; diğer nesneler büyütüldü.
* `LevelAnalyzer` ile her bölüm insan gibi oynatılıp ölçülüyor; 500 bölüm testere dişi hedef eğrisine göre yeniden üretildi.
* İkinci tur kod incelemesi (simülasyon + üretici/ölçüm): 12 bulgu + oyun ekranında 2 hata düzeltildi. Öne çıkanlar: "Değişen Platformlar"/"Hayalet Dünya" duyurulup nesnesi olmayan aşamalar (172 → 0), kalkan kurtarmasının Kaos Duvarı'nın içine bırakması, kurtarmanın önceki platforma dönmesi, dar hayalet parçaları, ölçüm modelindeki tutma payı ve çift zıplama hataları.
* Üreticiye "yoğunluk" ayarı eklendi (üst zorluk engel sayısıyla artar, pay 6–7 tick'in altına inmez); hedef eğrisi ölçülebilir düzeye çekildi (harita basamağı 0,62). Sonuç: **500/500 bölüm hedef bandında**, en büyük sapma 0,9 puan.
* Testler: 61/61.

### 21 Kasım — Google Play'e hazırlık

* Paket ve güncel Play kuralları denetlendi. Yüklemeyi engelleyecek iki şey bulundu: hedef API 35 (artık 36 gerekli) ve hata ayıklama imzası.
* Hedef API 36'ya çıkarıldı (SDK Platform 36 kuruldu); Android 16 için geri tuşu ve çentik payı düzenlendi; uygulama içi gizlilik politikası eklendi.
* Mağaza için hazırlananlar: gizlilik politikası metni (TR/EN), mağaza metinleri (TR/EN), 8 adet 1920×1080 ekran görüntüsü, 32-bit simge, adım adım yayın rehberi (`docs/play_store/`).
* Geliştiricide kalanlar: yükleme anahtarı, hesap, formlar, gerçek cihaz testi, 12 kişilik 14 günlük kapalı test.

### 21 Kasım — reklam

* Google AdMob eklendi: isteğe bağlı ödüllü reklamlar (canlar bitince +2 can, bölüm sonu ek altın, mağazada elmas, günlük ödül tekrarı, Sonsuz Dünya'da diriliş) ve yalnızca bölüm tamamlanınca, seyrek gösterilen geçiş reklamı. Açılış reklamı yok (afiş sonradan, yalnızca liste menülerine eklendi; aşağıya bakın).
* Güncel politikalar ve kitaplık kullanımı araştırıldı (3 araştırma + doğrulama); geçiş reklamının yeri buna göre "düğmeye basınca" yerine "sonuç ekranından önce" yapıldı; varsayılan kitle modu çocuklara uygun.
* Kotlin 2.2.21'e, en düşük Android sürümü 7.0'a yükseltildi. Paket deneme reklam kimlikleriyle derleniyor; gerçek kimlikler `ads.properties` dosyasına yazılacak.
* Gizlilik politikası, mağaza metinleri ve yayın rehberi reklamlı duruma göre güncellendi; `docs/play_store/REKLAM_REHBERI.md` eklendi.

### 21 Kasım — afiş reklam ve reklam kodunun bağımsız incelemesi

* Afiş (banner) eklendi: yalnızca içeriği kaydırılan beş menü ekranının altında (Haritalar, Mağaza, Görevler, Başarılar, Koleksiyon). Oyun sırasında afiş yok: oyun ekranın her yerine dokunularak oynandığı için yanlışlıkla tıklanırdı. `ads.properties` içinde `banners=off` ile kapatılır.
* Reklam kodu iki bağımsız inceleyici ve bir doğrulayıcıyla incelendi (ilk deneme kullanım sınırına takılmıştı); bulunan 19 sorun düzeltildi: reklam açılırken ekran kilidi, mağaza elmas kartının kaybolması, Sonsuz Dünya sonucunun diriliş sorusu sırasında kaybolması, afişin yeniden yüklenmemesi ve içeriğin üstüne binmesi, geri tuşu odağı, yanlış yazılmış reklam kimliğinin derlemede yakalanması, saat geri alınarak günlük sınırın sıfırlanması ve diğerleri.
* Değiştirilmeyen bilinen sınır: ödüllü reklam açıkken uygulama simgesinden dönülürse reklam ödülsüz kapanır (`launchMode="singleTask"`; gerçek cihazda denenmeden değiştirilmedi).
* Afişin ve reklamların gerçek cihazdaki davranışı hâlâ denenmedi.

### 28 Kasım — küresel yayın hazırlığı (sürüm 1.1.0)

* Kalıcı can sistemi: en fazla 3 can, 20 dakikada 1 yenilenir (oyun kapalıyken de), saat oynamalarına dayanıklı (monotonik saat + açılış sayacı), "Canların bitti" penceresi, reklamla +1 can, elmasla doldurma. Bölüm içi canların adı "kalp" oldu.
* 6 dil (tr, en, de, it, fr, es): telefon dilinden otomatik seçim, ayarlardan anında değiştirme, 708 metin, eksik çeviri 0.
* İngilizce ad "Stopwatch Shuffle" (33 adaylı araştırma), iki dilli logo ve marka paketi, Magic Lumos Software.
* Mağaza görselleri (48 telefon, 16 tablet, 2 kapak), 16 sosyal medya görseli, 8 tanıtım videosu, 6 dilde mağaza metinleri.
* Testler 99/99. Ayrıntı: `RELEASE_PACKAGE/11_Final_Report/FINAL_RELEASE_REPORT.md`.
