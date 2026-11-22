# MASTER PROMPT v6.3 — Güncellenmiş Akıllı Tahmin Mimarisi

> Bu dosya kullanıcının verdiği v6.3 talimatının çalışma kopyasıdır. Kullanıcı ayrıca şu ek talimatı vermiştir:
> **"Gol radar oranını yüzde 90 yap"** — Canlı Gol Radar'daki MODEL A ve MODEL B eşikleri %99'dan **%90**'a indirilmiştir.
> Bölüm 9 ve 10'daki "%99" ifadeleri bu talimatla güncellenmiştir; diğer bütün radar şartları aynen korunur
> ve seçim modları radar eşiğini değiştiremez.
>
> Not: MASTER PROMPT v6.2 metni ve v6.2 kodu bu depoda bulunmadı. v6.2'ye ait olduğu belirtilen unsurlar
> (21 organizasyon, MODEL A/B, marketler, geçmiş zaman veri deposu, kalibrasyon, dayanıklılık, Kalite Kapısı,
> bağımsız denetim, Başarı Merkezi, Canlı Gol Radar, 65 aşama) v6.3 metnindeki atıflardan yeniden kurulmuştur.
> Ayrıntılar: `VARSAYIMLAR.md`.

## ANA TALİMAT

MASTER PROMPT v6.2'nin tüm gereksinimlerini eksiksiz koru. 21 futbol organizasyonu, iki ana tahmin modeli (MODEL A ve MODEL B), bütün veriyle desteklenen futbol marketleri, geçmiş zaman veri deposu, tahmin kalibrasyonu, tahmin dayanıklılığı, Tahmin Kalite Kapısı, bağımsız denetim, Tahmin Başarı Merkezi, Canlı Gol Radar ve 65 aşamalı geliştirme planı geçerliliğini koruyacaktır.

Bu sürümde Akıllı Tahmin Motoru'nun seçim mimarisini aşağıdaki kurallarla güncelle. Yeni seçim modları üçüncü bir ana tahmin modeli değildir; MODEL A ve MODEL B'nin çıktılarını kullanan karar ve sıralama katmanlarıdır.

## 1. ÜÇ SEÇİM MODU

Akıllı Tahmin Motoru üç mod sunacaktır: 1) En Güvenilir Tahminler 2) En Avantajlı Oranlar 3) Dengeli Seçimler. Varsayılan mod: DENGELİ SEÇİMLER. Kullanıcı modlar arasında geçiş yapabilecektir. Mod değiştirildiğinde temel MODEL A/B olasılıkları değiştirilmez; yalnızca seçim uygunluğu, sıralaması ve gerekçeleri yeniden değerlendirilir.

### 1.1. EN GÜVENİLİR TAHMİNLER
Amaç, veri ve kalibrasyon bakımından yeterli olan seçenekler arasından gerçekleşme olasılığı en yüksek marketleri bulmaktır.
- Oran yüksekliği seçim için zorunlu değildir.
- MODEL A ve MODEL B olasılıkları ayrı gösterilir.
- Geçmiş kalibrasyon ve örneklem yeterliliği kontrol edilir.
- Tahmin dayanıklılığı değerlendirilir.
- Model uyuşmazlığı ve belirsizlik dikkate alınır.
- Gerekirse güvenilirlik alt sınırları kullanılır.
- Aynı maçtaki ilişkili marketler açıkça belirtilir.
- Düşük oranlı seçimler yüksek isabet potansiyeli taşısa bile pozitif getiri garantisi olarak sunulmaz.
Sıralamada yalnızca ham olasılığı değil, kalibrasyon ve belirsizlik kontrollerinden geçen olasılığı esas al.

### 1.2. EN AVANTAJLI ORANLAR
Amaç, modelin tahmin ettiği gerçekleşme olasılığı ile sunulan gerçek oran arasındaki matematiksel farkı değerlendirmektir.
Temel formül: EV = (p × decimal_odds) − 1 — p, uygun biçimde kalibre edilmiş olasılıktır.
- Yalnızca doğrulanmış ve güncel oranları kullan.
- Oranların kaynağını ve zamanını sakla.
- İddaa dışındaki oranları İddaa olarak gösterme.
- MODEL A ve MODEL B'nin beklenen değerlerini ayrı hesapla.
- Piyasa marjını ve marjdan arındırılmış referans olasılıklarını değerlendir.
- Tahmin belirsizliğini beklenen değer hesabına yansıt.
- Gerekirse pozitif beklenen değerin ihtiyatlı alt sınırını hesapla.
- Model uyuşmazlığında otomatik ortalama alma.
- Avantajın yalnızca model tahmini doğruysa geçerli olduğunu açıkça belirt.
- Pozitif EV tek başına seçim onayı için yeterli değildir.
Güvenilir bir avantaj belirlenemiyorsa seçim üretme.

### 1.3. DENGELİ SEÇİMLER — VARSAYILAN
Amaç, yüksek gerçekleşme olasılığı ile olumlu beklenen değeri birlikte değerlendirmektir. Seçim için:
1. Veri güncel ve yeterli olmalı. 2. Market sonuçlandırma kuralları doğrulanmalı. 3. MODEL A ve MODEL B geçerli olmalı. 4. Kalibrasyon yeterli olmalı. 5. Belirsizlik kabul edilebilir düzeyde olmalı. 6. Tahmin dayanıklılık testi uygun olmalı. 7. Model uyuşmazlığı kontrol edilmeli. 8. Gerçek ve güncel oran bulunmalı. 9. Beklenen değer avantajı belirsizlik altında da makul biçimde desteklenmeli. 10. Bağımsız seçim denetimi başarılı olmalı.
Olasılık, beklenen değer ve belirsizlik eşikleri kronolojik doğrulama verileriyle belirlenecektir. Rastgele %70 olasılık veya %10 EV gibi sabit eşikler dayatılmayacaktır.
Uygun seçim yoksa "Güvenilir ve avantajlı seçim bulunamadı." mesajını göster.

## 2. MODEL A VE MODEL B KURALLARI
MODEL A yalnızca futbol verileri kullanır. MODEL B futbol verileri, uygun out-of-fold MODEL A çıktıları ve tahmin anında erişilebilir gerçek oran bilgilerini kullanabilir. Seçim modları MODEL A'nın oran izolasyonunu ihlal edemez. İki modelin tahminleri aynı veri veya ortak hata kaynaklarından etkilenebileceği için birbirinden bağımsız doğrulama olarak kabul edilmez. MODEL A/B olasılıklarının birleşimi ancak kronolojik olarak eğitilmiş, kalibre edilmiş ve doğrulanmış bir karar yöntemiyle yapılabilir.

## 3. ÜÇ MODUN KARŞILAŞTIRILMASI
Aynı maç ve marketler üzerinde üç modun sonuçlarını ayrı değerlendir. Her mod için: uygun aday sayısı, önerilen seçim sayısı, seçim yapılmayan maç sayısı, isabet oranı, Brier Score, Log Loss, kalibrasyon, ortalama oran, ortalama tahmini olasılık, ortalama beklenen değer, teorik birim bahis ROI, güven aralıkları, lig bazlı başarı, market bazlı başarı, son 7/30/90 gün performansı raporla. Farklı modların başarılarını karşılaştırırken aynı zaman aralığı, aynı maç havuzu ve seçim kapsamındaki farklılıklar dikkate alınacaktır. Yalnızca seçilmiş tahminleri göstererek başarıyı olduğundan yüksek sunma.

## 4. MARKET TARAMA VE SEÇİM SÜRECİ
Her maç için mevcut ve veriyle desteklenen marketleri tara. İşlem sırası:
1. Fikstürü ve veri durumunu doğrula. 2. Mevcut marketleri al. 3. MODEL A tahminlerini üret. 4. MODEL B tahminlerini üret. 5. Olasılık tutarlılığını kontrol et. 6. Kalibrasyon ve belirsizliği değerlendir. 7. Tahmin dayanıklılığını kontrol et. 8. Tahmin geçerliliğini kontrol et. 9. Market bağımlılığını değerlendir. 10. Güncel oran ve EV hesaplarını üret. 11. Tahmin Kalite Kapısı'nı uygula. 12. Üç seçim modu için uygun adayları ayrı belirle. 13. Bağımsız denetimi uygula. 14. Sonuçları gerekçeleriyle kaydet. 15. Kullanıcının seçtiği moda göre sırala ve göster.

## 5. AKILLI TAHMİNLER ARAYÜZÜ
Sayfanın üst bölümünde üç seçim modu göster: En Güvenilir, En Avantajlı, Dengeli. İlk açılışta Dengeli seçili olsun.
Her seçim kartında: maç, lig, başlangıç saati, market, seçenek, gerçek oran, MODEL A olasılığı, MODEL B olasılığı, beklenen değer, belirsizlik seviyesi, tahmin dayanıklılığı, veri güncelliği, tahmin geçerliliği, geçmiş başarı, seçim gerekçesi, bağımsız denetim sonucu göster.
Kullanıcı "Neden seçildi?" alanını açarak kararın hangi koşulları karşıladığını inceleyebilsin. "Neden elendi?" alanında reddedilen adayların gerekçeleri görüntülenebilsin.

## 6. BAŞARI MERKEZİ ENTEGRASYONU
Tahmin Başarı Merkezi'ne "Seçim Modları" karşılaştırma sekmesi ekle. Bu sekmede üç modun isabet oranlarını, kalibrasyonlarını, seçim sıklıklarını, ortalama oranlarını, beklenen değerlerini, gerçekleşmiş teorik getirilerini, sezonlar arası dayanıklılığını karşılaştır. Modların başarısı birbirinden bağımsız kaydedilecektir. Geçmiş seçimler sonradan farklı bir modun sonucuymuş gibi değiştirilmeyecektir.

## 7. VERİTABANI GÜNCELLEMELERİ
Tablolar: selection_modes, selection_mode_configs, selection_mode_candidates, selection_mode_decisions, selection_mode_rejections, selection_mode_performance, selection_mode_calibration, selection_mode_comparisons.
Her seçim için: fixture_id, market_id, outcome_id, model_a_prediction_id, model_b_prediction_id, selection_mode, selection_timestamp, odds_snapshot_id, data_cutoff_at, estimated_probability, estimated_ev, uncertainty_metrics, audit_status, decision_reason, settlement_status.

## 8. API GÜNCELLEMELERİ
GET /api/v1/recommendations/modes · GET /api/v1/recommendations?mode=balanced · GET /api/v1/recommendations?mode=highest_probability · GET /api/v1/recommendations?mode=best_value · GET /api/v1/recommendations/modes/comparison · GET /api/v1/recommendations/modes/performance · GET /api/v1/recommendations/{id}/decision-explanation · GET /api/v1/recommendations/{id}/rejection-reasons · GET /api/v1/performance/selection-modes. Kullanıcının varsayılan mod tercihi ayarlarda saklanabilir.

## 9. CANLI GOL RADAR İLE İLİŞKİ
Canlı Gol Radar'ın çalışma koşulları seçim modlarından bağımsızdır. Zorunlu şartlar (kullanıcı talebiyle güncel eşik):
- MODEL A > %90 (önceki: %99)
- MODEL B > %90 (önceki: %99)
- Normal sürede en az 15 dakika kalması
- Güncel canlı veriler
- Gerekli güncel oranlar
- Yeterli nadir olay kalibrasyonu
- Tahmin Kalite Kapısı
- Bildirim izni
- Mükerrer alarm engeli
Dengeli mod seçilmiş olsa bile Radar'ın eşikleri gevşetilmeyecektir.

## 10. TESTLER
Varsayılan mod Dengeli olmalı · Mod değişimi MODEL A/B tahminlerini değiştirmemeli · En Güvenilir modu yalnızca en yüksek ham olasılığa göre kör seçim yapmamalı · En Avantajlı modu geçersiz oran kullanmamalı · Dengeli mod pozitif EV olmadan seçim üretmemeli · Kalibrasyon yetersizse seçim reddedilmeli · MODEL A oran verisi kullanmamalı · MODEL B zaman sızıntısı içermemeli · Bağımsız denetim sonuçları saklanmalı · Üç modun performans kayıtları ayrı tutulmalı · Geçmiş seçimler geriye dönük değiştirilmemeli · Canlı Gol Radar'ın mevcut eşikleri (%90) korunmalı.

## 11. GELİŞTİRME PLANI GÜNCELLEMESİ
Mevcut 65 aşamayı koru. Akıllı Seçim Motoru, Tahmin Kalite Kapısı, arayüz, veritabanı ve Başarı Merkezi aşamalarının içine üç modlu seçim mimarisini entegre et. Ek: AŞAMA 66 — Üç Seçim Modu Entegrasyonu ve Bağımsız Performans Doğrulaması (üç mod tamamlanacak, varsayılan Dengeli, kullanıcı mod değiştirebilecek, mod bazlı başarı kayıtları, kronolojik karşılaştırma, bağımsız denetim, arayüz ve API testleri).

## 12. NİHAİ GELİŞTİRME TALİMATI
Sistemde yalnızca iki ana tahmin modeli bulunacak. Üç seçim modu bulunacak. Varsayılan seçim modu Dengeli Seçimler olacak. Tahmin başarısı ile matematiksel avantaj ayrı değerlendirilecek. Yüksek isabet oranı veya pozitif beklenen değer garanti edilmiş kazanç olarak sunulmayacak. Güvenilir seçim bulunmayan maçlarda zorla tahmin üretilmeyecek. Geliştirmeye güncellenmiş 66 aşamalı planla devam et.
