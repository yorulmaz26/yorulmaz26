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
- ⬜ Alan 10 (46–50, Robot İsyanı patronu) düşman yapay zekâsı gerektirir.

## Aşama 5 — Kalıcı fabrika 🟡 (çekirdek ✅)
- ✅ 15 bina kataloğu; 7'si kurulabilir ve gerçek etkili: Komuta Merkezi (seviye sınırı, bina açma), Enerji Santrali, Metal İşleme (enerji harcar), Kaynak Deposu (kapasite), Mikroçip Laboratuvarı (metal harcar), Araştırma Merkezi (TP + kademe), Ticaret Merkezi (metal/enerji → altın).
- ✅ Gerçek zamanlı ve çevrimdışı üretim (dakikalık zincir hesabı, depo ve 8 saat sınırı); süre ölçümü can sistemiyle aynı saat korumasını kullanır.
- ✅ Araştırma ağacı: 4 dalda 13 etkili düğüm (altın/XP bonusu bölüm ödüllerine uygulanır, üretim, depo, çevrimdışı süre, ticaret kuru). Askerî dal savaş sistemine kadar kilitli.
- ✅ İzometrik fabrika görünümü (binalar seviyeyle yükselir), üretim raporu, 10 test.
- ⬜ Diğer 8 bina (robot üretimi, garaj, akademi, savunma, keşif, tamir, müze, yapay zekâ lab.) bağlı oldukları sistemlerle gelecek; arayüzde kilitli ve nedeni yazılı.
- ⬜ Robot geliştirme ve programlanabilir üretim hatları (fabrika otomasyonu) — Aşama 6 robot sınıflarıyla birlikte.

## Aşama 6–9 ⬜
Robot sınıfları, savaş, düşman yapay zekâsı, patronlar; günlük/haftalık görevler, sonsuz mod, market, harita editörü, koleksiyon, başarımlar; 500 bölüme genişleme; müzik ve yayın hazırlığı.

## Bilinen eksikler
- Müzik yok (yalnızca ses efektleri); ayarlarda müzik seçeneği bu yüzden gösterilmiyor.
- Gerçek Android cihazda/emülatörde oynanış testi bu ortamda yapılmadı; APK derleniyor, oyun masaüstünde sanal ekranda çalıştırılıp ekran görüntüleriyle kontrol edildi.
