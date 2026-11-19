# Yol haritası ve durum

Durum dürüstçe işaretlenmiştir: ✅ çalışıyor ve test edildi · 🟡 kısmen · ⬜ yapılmadı.

## Aşama 1 — Teknik temel ✅
Gradle çok modüllü proje, Kotlin, libGDX 1.13.1, dikey ekran, ekran yönetimi, izometrik harita, Android + masaüstü başlatıcı.

## Aşama 2 — İlk oynanabilir sürüm ✅
Temel robot, komut kartları, dokun/sürükle-bırak düzenleme (ekle, sil, taşı, kopyala, sırala), deterministik simülasyon, başlat/duraklat/tek adım/geri adım/sıfırla, hız 1×/2×/4×, hatalı kart vurgusu, kazanma/kaybetme, yıldızlar, kayıt.

## Aşama 3 — İlk 25 bölüm ✅
5 alan × 5 bölüm, tek robot kuralı. Kutu alma/bırakma/itme, çukur doldurma, anahtar, basınç plakası, ters kapı, zamanlı düğme, bekleme, enerji hücresi, şarj istasyonu, enerji soketi. Etkileşimli öğretici balonları, alan hikâyeleri, YENİ kart rozetleri. Bütün bölümler otomatik doğrulanır.

## Aşama 4 — Çoklu robot 🟡 (bölüm 26–45 ✅, 46–50 patron alanı Aşama 6'ya bağlı)
- ✅ Bölüm 26'da ikinci robot; eş zamanlı zincirler, robot sekmeleri, haritada robota dokunarak seçim, zinciri diğer robota kopyalama.
- ✅ Mesaj gönder / Mesaj bekle (29 ve 43. bölümlerde kart sınırı sinyal kullanmayı zorunlu kılar).
- ✅ Hareketli bantlar (bant üstünde dönmek/beklemek taşınır; programlar bitse de bant çalışır), lazerler (kanalla kapanır, kutu ışını keser), VE-kilitleri (bütün plakalar aynı anda), senkron varış hedefi.
- ✅ Çok robotlu çözücü (Dijkstra, toplam kart maliyeti); 45 bölümün tamamında par = optimum doğrulanır.
- ✅ Alan 10 (46–50) Aşama 6'da tamamlandı.

## Aşama 5 — Kalıcı fabrika 🟡 (çekirdek ✅)
- ✅ 15 bina kataloğu; 7'si kurulabilir ve gerçek etkili: Komuta Merkezi (seviye sınırı, bina açma), Enerji Santrali, Metal İşleme (enerji harcar), Kaynak Deposu (kapasite), Mikroçip Laboratuvarı (metal harcar), Araştırma Merkezi (TP + kademe), Ticaret Merkezi (metal/enerji → altın).
- ✅ Gerçek zamanlı ve çevrimdışı üretim (dakikalık zincir hesabı, depo ve 8 saat sınırı); süre ölçümü can sistemiyle aynı saat korumasını kullanır.
- ✅ Araştırma ağacı: 4 dalda 13 etkili düğüm (altın/XP bonusu bölüm ödüllerine uygulanır, üretim, depo, çevrimdışı süre, ticaret kuru). Askerî dal savaş sistemine kadar kilitli.
- ✅ İzometrik fabrika görünümü (binalar seviyeyle yükselir), üretim raporu, 10 test.
- ⬜ Diğer 8 bina (robot üretimi, garaj, akademi, savunma, keşif, tamir, müze, yapay zekâ lab.) bağlı oldukları sistemlerle gelecek; arayüzde kilitli ve nedeni yazılı.
- ⬜ Robot geliştirme ve programlanabilir üretim hatları (fabrika otomasyonu) — Aşama 6 robot sınıflarıyla birlikte.

## Aşama 6 — Strateji ve savaş 🟡 (çekirdek ✅)
- ✅ Savaş kartları: Saldır, Kalkan aç. Robotlar düşmanlardan önce hareket eder (önce vuran avantajlı), deterministik.
- ✅ 4 düşman davranışı: Devriye, Nöbetçi Taret (menzilli; duvar/kutu/robot atışı keser), Takipçi (en yakın robot, sabit eşitlik kuralı), İsyan Lideri patronu (2 aşama: dönen ön saldırı → kovalama). Lazerler düşmanları da etkisiz bırakır.
- ✅ Robot sınıflarının bulmaca değerleri: Temel, Savaş (2 hasar), Kalkan (5 can, saldıramaz, 3 adım kalkan). Kalıcı geliştirmeler bulmaca kurallarını değiştirmez.
- ✅ Alan 10 (46–50): ilk düşman, taret koridoru, takipçiyi lazere çekme, kalkan+savaş takımı, patron. 50 bölümün hepsi çözücüyle doğrulanır.
- ⬜ Kalan 4 düşman davranışı (tuzak kuran, kaynak koruyan, komut bozan, takım saldırısı), diğer 12 robot sınıfının yetenekleri, kombinasyonlar, Askerî araştırma dalı.

## Aşama 7 — Süreklilik 🟡 (çekirdek ✅)
- ✅ Günlük (3) ve haftalık (3) görevler: tarihe göre belirlenir (aynı gün herkes için aynı), çevrimdışı, ödül elle toplanır.
- ✅ 100 başarım (17 ölçüt, kademeli), altın ödüllü.
- ✅ Doğrulanmış bölüm üretici: rastgele harita → çözücüyle doğrulama → çok kolay/çözümsüz olanı reddetme; kat arttıkça kutu, çukur, anahtar, plaka, enerji ve devriye eklenir. Aynı tohum = aynı bölüm.
- ✅ Sonsuz Fabrika (kat kat koşu, rekor, can harcamaz) ve Günlük Komut Bulmacası (günlük ödül, en az kart rekoru).
- ✅ Market: altınla 10 robot görünümü (yalnızca görsel). Gerçek para yok; Google Play Billing eklenmedi.
- ✅ Harita editörü (Kendi Fabrikanı Tasarla): 17 fırça, yön çevirme, boyut 4–10, çözücüyle "çözülebilir mi?", test etme, yerel kayıt/açma/silme. Harita biçimi bölüm JSON'uyla aynı (ileride çevrimiçi paylaşıma hazır).
- ✅ Robot seferleri: Keşif Merkezi seviyesi kadar yuva, 3 rota (30 dk / 2 sa / 8 sa), enerji maliyeti, güvenilir süre ölçümü, başlangıçta belirlenen ödül.
- ✅ Koleksiyon (5 set × 20 = 100 parça, 3 nadirlik) ve Koleksiyon Müzesi binası (parça başına saatlik altın, set ödülleri).
- ✅ Alternatif bölüm kuralları (Zor mod: kart sınırı = par, çarpma yok) ve prestij (500 bölüm sonrası "Yeni Komuta Çağı": ilerleme korunur, zor mod ödülleri artar).
- ⬜ Fabrika savunması, komut ustalığı seviyeleri, rastgele fabrika olayları, editör haritalarını çevrimiçi paylaşma.
- Not: görev tarihi cihaz saatinden okunur; tarihi değiştirmek görevleri yeniler (ödüller küçük tutuldu). Canlar ve üretim bundan etkilenmez.

## Aşama 8 — İçerik genişletme ✅
- ✅ 100 alan × 5 bölüm = **500 bölüm**. 1–50 elle tasarlandı; 51–500 kampanya üreticisiyle (`logic/gen/Campaign.kt`) üretildi.
- ✅ Her alanın mekanik profili (alan adından/mekaniğinden): kutu, çukur, anahtar, plaka, zamanlı düğme, enerji, bant, lazer, devriye, taret, takipçi, döngü, uçan, görünmez, manyetik robot.
- ✅ Kalite kuralları: her bölüm çözücüyle çözülür; çözüm alanın mekaniğini gerçekten kullanmak zorunda; çok kolay olanlar reddedilir; döngü alanlarında çözüm Tekrar bloklarıyla sıkıştırılır ve kart sınırı döngüyü gerektirir; her 25. bölüm patron savaşı (18 farklı patron adı, büyüyen can).
- ✅ Robot sayısı kuralı: 1–25 tek robot, sonra alan aralığına göre üst sınır (üretimde en fazla 3 robot kullanılır; üretilemeyen çok robotlu bölümde sayı azaltılır — kural bir üst sınırdır).
- ✅ Yeni sınıflar: Uçan (boşluktan düşmez, plakaya basmaz, kutu taşıyamaz), Görünmez (düşmanlar hedef almaz), Manyetik (3 hücreye kadar uzaktaki kutuyu çeker).
- ✅ Doğrulama: 500 bölümün hepsi için çözüm/3 yıldız/determinizm/boş zincir testi; optimumluk testi 1–50'de tam, sonrasında örneklem.
- Sınır: 51–500 üretilmiş bulmacalardır; elle tasarlanan 1–50 kadar "anlatılı" değildir. Bazı alan adlarındaki mekanikler (ayna, yankı, virüs, ışınlanma, klonlama, birleşim…) motorda henüz yoktur; o alanlar mevcut mekaniklerin birleşimiyle üretildi.

## Aşama 9 — Yayın hazırlığı 🟡
- ✅ Sentezlenmiş müzik (menü ve oyun döngüsü, OGG) ve ses efektleri; ikisi ayrı kapatılabilir.
- ✅ Çizim performansı: izometrik çizicide kare başına nesne üretimi renk havuzuyla kaldırıldı.
- ✅ İmzalı yayın yapılandırması (`keystore.properties`, depoya eklenmez), `bundleRelease` ile AAB, sürüm 1.0.0.
- ✅ Play mağaza metinleri, veri güvenliği ve gizlilik metni: [`PLAY_STORE.md`](PLAY_STORE.md).
- ⬜ Gerçek cihaz testleri (bu ortamda cihaz/emülatör yok), R8 küçültmesinin cihazda doğrulanması, mağaza görselleri, yerelleştirme (yalnızca Türkçe).

## Bilinen eksikler
- Müzik yok (yalnızca ses efektleri); ayarlarda müzik seçeneği bu yüzden gösterilmiyor.
- Gerçek Android cihazda/emülatörde oynanış testi bu ortamda yapılmadı; APK derleniyor, oyun masaüstünde sanal ekranda çalıştırılıp ekran görüntüleriyle kontrol edildi.
