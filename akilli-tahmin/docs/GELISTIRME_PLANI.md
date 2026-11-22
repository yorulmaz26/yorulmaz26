# Akıllı Tahmin — Geliştirme Planı v6.3 (66 Aşama)

Sürüm: 6.3 · Durum tarihi: 2026-11-21
Dayanak: `docs/MASTER_PROMPT_v6.3.md` (özellikle Bölüm 11 ve 12), `docs/API.md`
Kod: `akilli_tahmin/` · Testler: `tests/`

---

## 0. Yeniden kurulum notu (önce okuyun)

> **Kullanıcı kararı (2026-11-22):** v6.2 metni mevcut değildir. Aşağıdaki yeniden kurulmuş Aşama 1–65 ve
> Aşama 66 bundan sonra **esas plandır**; Ek A'daki uzlaştırma tablosu kullanılmayacaktır. Radar eşiği %90
> onaylandı. Oran kaynağı olarak İddaa seçildi (erişim yöntemi: `VARSAYIMLAR.md` §6).

> **Aşama 1–65 yeniden kurulmuştur.** MASTER PROMPT v6.2 metni ve orijinal 65 aşamalı geliştirme listesi bu depoda
> bulunamadı. Aşağıdaki Aşama 1–65, v6.3 metninin v6.2'ye yaptığı atıflardan yeniden kurulmuştur:
> 21 futbol organizasyonu; yalnızca iki ana tahmin modeli (MODEL A = yalnızca futbol verisi, katı oran izolasyonu;
> MODEL B = futbol verisi + kronolojik out-of-fold MODEL A çıktıları + tahmin anında erişilebilir oranlar);
> veriyle desteklenen bütün futbol marketleri ve sonuçlandırma kuralları; geçmiş zaman (point-in-time) veri deposu;
> tahmin kalibrasyonu; tahmin dayanıklılığı; Tahmin Kalite Kapısı; bağımsız denetim; Tahmin Başarı Merkezi;
> Canlı Gol Radar. **Aşama 66** v6.3 Bölüm 11'den doğrudan alınmıştır ve yeniden kurulum değildir.
>
> Kullanıcı orijinal v6.2 listesini sağlarsa bu plan onunla **uzlaştırılmalıdır**:
> 1. Uzlaştırma tamamlanana kadar bu plandaki numaralar değiştirilmez; kod, test ve commit referansları bu numaralara verilir.
> 2. Orijinal liste geldiğinde Ek A'daki eşleme tablosu doldurulur (v6.2 no → bu plandaki no). Orijinal numaralandırma
>    benimsenirse içerik eşleme tablosuyla taşınır; **Aşama 66 her durumda 66 kalır.**
> 3. Orijinal listede olup burada bulunmayan içerik, en yakın aşamaya "v6.2 uzlaştırması:" alt maddesiyle eklenir;
>    burada olup orijinalde bulunmayan içerik "yeniden kurulum eki" olarak işaretlenir. Toplam aşama sayısı 66 kalır.
> 4. Durum bilgileri eşleme sırasında birlikte taşınır.
>
> Varsayımların ayrıntısı `docs/VARSAYIMLAR.md` dosyasında tutulur (MASTER_PROMPT_v6.3.md'nin atıf yaptığı dosya).

---

## 1. Değişmez ilkeler (v6.3 Bölüm 12)

- Sistemde **yalnızca iki ana tahmin modeli** vardır: MODEL A ve MODEL B.
- **Üç seçim modu** vardır; bunlar üçüncü bir tahmin modeli değildir, MODEL A/B çıktılarını kullanan karar ve sıralama katmanlarıdır.
- **Varsayılan seçim modu Dengeli Seçimler'dir** (`balanced`).
- Mod değiştirildiğinde MODEL A/B olasılıkları değişmez; yalnızca uygunluk, sıralama ve gerekçe yeniden değerlendirilir.
- **Tahmin başarısı** (isabet, Brier, log loss, kalibrasyon) ile **matematiksel avantaj** (EV, teorik ROI) ayrı değerlendirilir ve ayrı gösterilir.
- Yüksek isabet oranı veya pozitif beklenen değer **hiçbir zaman garanti kazanç olarak sunulmaz.**
- Güvenilir seçim bulunmayan maçlarda **zorla tahmin üretilmez.**
- Olasılık, EV, belirsizlik ve uyuşmazlık eşikleri **kronolojik doğrulama** ile belirlenir; %70 olasılık veya %10 EV gibi sabit eşikler dayatılmaz.
- MODEL A oran verisi kullanmaz; hiçbir seçim modu bu izolasyonu ihlal edemez. MODEL A ve MODEL B birbirinin bağımsız doğrulaması sayılmaz.
- **Canlı Gol Radar** seçim modlarından bağımsızdır. Eşik kullanıcı talebiyle ("Gol radar oranını yüzde 90 yap") MODEL A ve MODEL B için **%99'dan %90'a** indirilmiştir; diğer bütün radar şartları aynen korunur ve hiçbir seçim modu bunları gevşetemez.
- Sentetik veri her yerde açıkça etiketlenir ve gerçek performans iddiası için kullanılmaz.

## 2. Durum tanımları ve genel kabul ilkeleri

| Durum | Anlamı |
|---|---|
| **Tamamlandı (temel)** | Modül temel işlevleriyle mevcut; sentetik veya tarihsel CSV verisiyle çalışıyor ve birim testleriyle kapsanıyor. Üretim için hazır **değildir** (gerçek veri akışı, izleme, dağıtım yok). |
| **Kısmen** | Çıktıların bir bölümü mevcut; eksik kalanlar aşama içinde belirtilmiştir. |
| **Planlandı** | Henüz başlanmadı. |

Durumlar `akilli_tahmin/` modül haritasına göre atanmıştır. Bir aşama "Tamamlandı (temel)" olarak işaretlenmeden önce kabul kriterlerine karşılık gelen testlerin `pytest` ile geçtiği doğrulanmalıdır. Gerçek veri sağlayıcı entegrasyonu, 21 organizasyonun üretim veri akışları, mobil uygulamalar, push bildirim teslim altyapısı, izleme/operasyon ve üretim dağıtımı henüz yapılmamıştır (Planlandı).

**Genel kabul ilkeleri** (her aşamaya uygulanır):

1. Bütün doğrulama **kronolojiktir** (walk-forward). Zaman serisinde rastgele K-katlı bölme kullanılmaz; eğitim/ayar penceresi uygulama penceresinden önce biter.
2. Kabul metrikleri istatistikseldir: **Brier Score, log loss, ECE / güvenilirlik eğrisi, güven aralıkları** (oranlar için Wilson veya Clopper–Pearson, ROI ve metrik farkları için maç gününe göre blok bootstrap).
3. "İsabet oranı ≥ %X" veya "ROI ≥ %Y" biçiminde **hedef kabul kriteri yoktur**; isabet ve ROI yalnızca örneklem sayısı ve güven aralığıyla raporlanır.
4. Karşılaştırmalar **aynı zaman aralığı ve aynı maç havuzu** üzerinde yapılır; seçim kapsamı farkları açıkça belirtilir.
5. Eşikler `selection_mode_configs` kayıtlarından gelir ve kaynağı (`chronological_validation`), doğrulama penceresi ve metrikleriyle saklanır. Yeterli veri yoksa durum `not_established` olur ve o mod seçim üretmez. `docs/API.md`'deki eşik değerleri yalnızca örnektir.
6. Sentetik veriyle elde edilen sonuçlar yalnızca işlevsel doğrulama içindir; performans iddiası olarak raporlanmaz.
7. Sayısal toleranslar (ör. olasılık toplamı için 1e-9) test toleransıdır, seçim eşiği değildir.

## 3. Seçim modları (v6.3)

| Kod | Ad | Kısa ad | Oran zorunlu mu? | Sıralama ölçütü |
|---|---|---|---|---|
| `balanced` | Dengeli Seçimler (**varsayılan**) | Dengeli | Evet | Belirsizlik altında ihtiyatlı Kelly oranı |
| `highest_probability` | En Güvenilir Tahminler | En Güvenilir | Hayır | Kalibrasyon ve belirsizlik kontrollerinden geçen olasılık (alt sınır), ham olasılık değil |
| `best_value` | En Avantajlı Oranlar | En Avantajlı | Evet (doğrulanmış ve güncel) | İhtiyatlı EV alt sınırı; EV = (p × decimal_odds) − 1, p kalibre olasılık |

Uygun seçim yoksa mesajlar: Dengeli → "Güvenilir ve avantajlı seçim bulunamadı." · En Avantajlı → "Güvenilir bir avantaj belirlenemedi; seçim üretilmedi." · En Güvenilir → "Kalibrasyon ve belirsizlik kontrollerinden geçen güvenilir seçim bulunamadı."

---

## 4. Özet tablo

| No | Aşama | Faz | Durum |
|---|---|---|---|
| 1 | Proje iskeleti, yapılandırma ve sürüm yönetimi | Temel ve Veri | Tamamlandı (temel) |
| 2 | Sorumlu kullanım ilkeleri, uyarı metinleri ve sentetik veri etiketi | Temel ve Veri | Kısmen |
| 3 | 21 futbol organizasyonu kaydı | Temel ve Veri | Tamamlandı (temel) |
| 4 | Market kataloğu ve sonuçlandırma kuralları | Temel ve Veri | Tamamlandı (temel) |
| 5 | Veritabanı şeması ve değiştirilemez kayıt | Temel ve Veri | Tamamlandı (temel) |
| 6 | Geçmiş zaman (point-in-time) veri deposu | Temel ve Veri | Tamamlandı (temel) |
| 7 | Tarihsel veri içe aktarıcı (CSV) | Temel ve Veri | Tamamlandı (temel) |
| 8 | Sentetik demo ligler ve oranlar | Temel ve Veri | Tamamlandı (temel) |
| 9 | Gerçek veri sağlayıcı entegrasyonu ve kimlik eşleştirme | Temel ve Veri | Kısmen |
| 10 | 21 organizasyon için üretim veri akışları | Temel ve Veri | Kısmen |
| 11 | Oran anlık görüntüleri: kaynak, zaman ve İddaa ayrımı | Temel ve Veri | Kısmen |
| 12 | Veri güncelliği ve yeterliliği kontrolleri | Temel ve Veri | Tamamlandı (temel) |
| 13 | Özellik çıkarımı ve kronolojik eğitim pencereleri | Modeller | Kısmen |
| 14 | MODEL A: zaman azalımlı Dixon–Coles Poisson | Modeller | Tamamlandı (temel) |
| 15 | MODEL A skor matrisinden market olasılıkları | Modeller | Tamamlandı (temel) |
| 16 | MODEL A oran izolasyonu | Modeller | Tamamlandı (temel) |
| 17 | Kronolojik out-of-fold MODEL A çıktıları | Modeller | Tamamlandı (temel) |
| 18 | Oran marjı arındırma ve oran doğrulama | Modeller | Tamamlandı (temel) |
| 19 | MODEL B: yığılmış lojistik model | Modeller | Tamamlandı (temel) |
| 20 | MODEL B zaman sızıntısı korumaları | Modeller | Tamamlandı (temel) |
| 21 | Model sürümleme, yeniden eğitim ve tahmin kaydı | Modeller | Kısmen |
| 22 | Gelişmiş futbol özellikleri (kadro, sakatlık, xG, dinlenme) | Modeller | Planlandı |
| 23 | Kronolojik kalibrasyon | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 24 | Olasılık ve kalibrasyon metrikleri | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 25 | Nadir olay kalibrasyonu | Kalibrasyon ve Güvenilirlik | Kısmen |
| 26 | Tahmin belirsizliği | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 27 | Tahmin dayanıklılığı | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 28 | Olasılık tutarlılığı kontrolleri | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 29 | Tahmin geçerliliği | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 30 | Market bağımlılığı ve ilişkili seçimler | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 31 | MODEL A–MODEL B uyuşmazlık analizi | Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) |
| 32 | Beklenen değer ve ihtiyatlı EV alt sınırı | Seçim ve Kalite | Tamamlandı (temel) |
| 33 | Tahmin Kalite Kapısı | Seçim ve Kalite | Tamamlandı (temel) |
| 34 | Akıllı Seçim Motoru (15 adımlı süreç) | Seçim ve Kalite | Tamamlandı (temel) |
| 35 | Kronolojik eşik belirleme | Seçim ve Kalite | Tamamlandı (temel) |
| 36 | Bağımsız seçim denetimi | Seçim ve Kalite | Tamamlandı (temel) |
| 37 | Karar gerekçeleri ve ret nedenleri | Seçim ve Kalite | Tamamlandı (temel) |
| 38 | "Seçim yok" politikası | Seçim ve Kalite | Tamamlandı (temel) |
| 39 | MODEL A/B birleşim karar yöntemi | Seçim ve Kalite | Kısmen |
| 40 | Kapanış oranı (CLV) ve oran hareketi analizi | Seçim ve Kalite | Planlandı |
| 41 | REST API çekirdeği | Arayüz ve API | Tamamlandı (temel) |
| 42 | Kullanıcı ayarları ve varsayılan mod tercihi | Arayüz ve API | Tamamlandı (temel) |
| 43 | Akıllı Tahminler arayüzü | Arayüz ve API | Kısmen |
| 44 | Maç detayı ve tüm market tarama görünümü | Arayüz ve API | Kısmen |
| 45 | Lig, organizasyon ve fikstür görünümleri | Arayüz ve API | Kısmen |
| 46 | Türkçe yerelleştirme, erişilebilirlik ve duyarlı tasarım | Arayüz ve API | Kısmen |
| 47 | Kimlik doğrulama, yetkilendirme ve hız sınırlama | Arayüz ve API | Planlandı |
| 48 | Mobil uygulamalar (Android / iOS) | Arayüz ve API | Planlandı |
| 49 | Sonuçlandırma motoru | Başarı Merkezi ve Doğrulama | Tamamlandı (temel) |
| 50 | Walk-forward kronolojik geriye dönük test | Başarı Merkezi ve Doğrulama | Tamamlandı (temel) |
| 51 | Tahmin Başarı Merkezi | Başarı Merkezi ve Doğrulama | Tamamlandı (temel) |
| 52 | Kırılımlar: lig, market, dönem ve güven aralıkları | Başarı Merkezi ve Doğrulama | Tamamlandı (temel) |
| 53 | Taban çizgisi ve seçim yanlılığı koruması | Başarı Merkezi ve Doğrulama | Tamamlandı (temel) |
| 54 | Sezonlar arası dayanıklılık ve model kayması izleme | Başarı Merkezi ve Doğrulama | Kısmen |
| 55 | Model ve piyasa karşılaştırma raporları | Başarı Merkezi ve Doğrulama | Kısmen |
| 56 | Canlı maç veri akışı | Canlı | Kısmen |
| 57 | Canlı oran akışı | Canlı | Kısmen |
| 58 | Canlı MODEL A / MODEL B kalan süre gol olasılıkları | Canlı | Tamamlandı (temel) |
| 59 | Canlı Gol Radar karar kuralları (%90) | Canlı | Tamamlandı (temel) |
| 60 | Alarm kaydı, mükerrer alarm engeli, bildirim izni ve radar başarı takibi | Canlı | Kısmen |
| 61 | Bildirim teslim altyapısı | Canlı | Planlandı |
| 62 | Test paketi ve sürekli entegrasyon | Operasyon | Kısmen |
| 63 | İzleme, günlükleme ve operasyonel uyarılar | Operasyon | Planlandı |
| 64 | Güvenlik, gizlilik ve yasal uyum | Operasyon | Planlandı |
| 65 | Üretim dağıtımı, yedekleme ve felaket kurtarma | Operasyon | Planlandı |
| 66 | Üç Seçim Modu Entegrasyonu ve Bağımsız Performans Doğrulaması | v6.3 Entegrasyonu | Kısmen |

**Faz özeti**

| Faz | Aşamalar | Tamamlandı (temel) | Kısmen | Planlandı |
|---|---|---|---|---|
| 1. Temel ve Veri | 1–12 | 8 | 2 | 2 |
| 2. Modeller | 13–22 | 7 | 2 | 1 |
| 3. Kalibrasyon ve Güvenilirlik | 23–31 | 8 | 1 | 0 |
| 4. Seçim ve Kalite | 32–40 | 7 | 1 | 1 |
| 5. Arayüz ve API | 41–48 | 3 | 3 | 2 |
| 6. Başarı Merkezi ve Doğrulama | 49–55 | 5 | 2 | 0 |
| 7. Canlı | 56–61 | 2 | 3 | 1 |
| 8. Operasyon | 62–65 | 0 | 1 | 3 |
| 9. v6.3 Entegrasyonu | 66 | 0 | 1 | 0 |
| **Toplam** | **66** | **40** | **16** | **10** |

**v6.3 entegrasyonu içeren aşamalar:** 5 (veritabanı), 16, 20, 32, 33 (Tahmin Kalite Kapısı), 34 (Akıllı Seçim Motoru), 35, 36, 37, 38, 39, 41 (API), 42, 43 (arayüz), 48, 49, 50, 51 (Başarı Merkezi), 52, 53, 54, 59 (radar bağımsızlığı), 62 (testler) ve 66.

---

## 5. Fazlar ve aşamalar

### Faz 1 — Temel ve Veri (Aşama 1–12)

#### AŞAMA 1 — Proje İskeleti, Yapılandırma ve Sürüm Yönetimi

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `config.py`, `akilli_tahmin/` paket yapısı |

**Amaç:** Tek yapılandırma kaynağı, sürüm bilgisi ve tekrarlanabilir çalıştırma ile sistemin iskeletini kurmak.

**Çıktılar:**
- `akilli_tahmin/` paket yapısı (Ek B'deki modül haritası).
- `config.py`: sürüm `6.3.0`, tahmin modelleri (`A`, `B`), varsayılan seçim modu `balanced`, veri/oran tazelik süreleri, radar ayarları.
- Sabit rastgele tohum (seed) ile tekrarlanabilir çalıştırma.

**Kabul kriterleri:**
- `GET /api/v1/system/status` → `version: "6.3.0"`, `prediction_models: ["MODEL_A","MODEL_B"]`, `selection_modes: 3`, `default_mode: "balanced"`.
- Aynı veri, aynı veri kesim zamanı ve aynı tohumla iki çalıştırma aynı olasılıkları üretir (test).
- Yapılandırmada sabit seçim eşiği (olasılık / EV) bulunmaz; seçim eşikleri yalnızca `selection_mode_configs` kayıtlarından okunur (test).

#### AŞAMA 2 — Sorumlu Kullanım İlkeleri, Uyarı Metinleri ve Sentetik Veri Etiketi

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Kısmen | `api/`, `web/static`, `data/synthetic.py` |

**Amaç:** Kullanıcıya hiçbir koşulda garanti kazanç vaadi sunulmamasını ve sentetik verinin gerçek veri gibi gösterilmemesini sağlamak.

**Çıktılar:**
- Bütün öneri yanıtlarında `disclaimer` (olasılık tahmini olduğu, kazanç garantisi olmadığı, 18 yaş sınırı).
- API ve arayüzde `is_synthetic_data` bayrağı ve görünür "Sentetik veri" etiketi.
- Kart uyarıları: "Avantaj yalnızca model tahmini doğruysa geçerlidir.", "Pozitif EV kazanç garantisi değildir."
- Yasaklı ifade listesi ("garanti", "kesin kazanç", "banko" vb.) için metin taraması.
- Eksik (Aşama 47 ve 64 ile): hukuki metin incelemesi, yaş doğrulama, sorumlu oyun bilgilendirmesi.

**Kabul kriterleri:**
- Her öneri ucunun yanıtında boş olmayan `disclaimer` bulunur (sözleşme testi).
- Sentetik veriyle üretilen her yanıtta `is_synthetic_data: true`; arayüzde etiket görünür (arayüz testi).
- Yasaklı ifade taraması API yanıt şablonlarında ve statik dosyalarda sıfır eşleşme verir (test).
- Sentetik oran kaynağı hiçbir zaman `is_iddaa: true` olamaz (şema kısıtı + test).

#### AŞAMA 3 — 21 Futbol Organizasyonu Kaydı

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `leagues.py` |

**Amaç:** Desteklenen 21 futbol organizasyonunu kararlı kimlik, ad, ülke ve tür (lig / kupa / kıtasal) bilgisiyle tek kaynaktan tanımlamak.

**Çıktılar:**
- 21 kayıtlık organizasyon listesi (ör. `tr-super-lig`, `en-premier-league`, `uefa-champions-league`), tarihsel CSV dosya kodu eşlemesi (varsa).
- `GET /api/v1/leagues`.
- Not: Liste v6.3 atıflarından yeniden kurulmuştur; orijinal v6.2 listesiyle uzlaştırılacaktır (bkz. Bölüm 0).

**Kabul kriterleri:**
- Kayıtta tam 21 benzersiz kimlik vardır; `GET /api/v1/leagues` 21 öğe döner (test).
- Her organizasyonun adı, ülkesi ve türü (`league` | `cup` | `continental`) boş değildir (test).
- Bilinmeyen organizasyon koduyla yapılan istek anlamlı bir 4xx hatası döner; sessizce boş sonuç üretilmez (test).

#### AŞAMA 4 — Market Kataloğu ve Sonuçlandırma Kuralları

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `domain/markets.py` |

**Amaç:** Veriyle desteklenen bütün futbol marketlerini ve her seçeneğin sonuçlandırma kuralını tek bir tanımda toplamak.

**Çıktılar:**
- Market aileleri: Maç Sonucu (1X2), Çifte Şans, Beraberlikte İade, Alt/Üst (0,5–5,5), Karşılıklı Gol, Takım Alt/Üst, İlk Yarı Sonucu, İlk Yarı Alt/Üst, İlk Yarı/Maç Sonucu, Toplam Gol Aralığı, Skor, Handikaplı Maç Sonucu (±1), Tek/Çift.
- Her seçenek için skor → `won` / `lost` / `void` kuralı; kapsam **normal süredir** (uzatma devreleri ve penaltılar hariç).
- Ertelenen / iptal edilen maçlar için iade kuralı; devre skoru gerektiren marketler için `needs_halftime` bayrağı.
- Veri desteği kuralı: gerekli veri alanı yoksa market taranmaz (ör. korner/kart verisi olmadan bu marketler eklenmez). Asya handikabı gibi yarım sonuçlu marketler ileride eklenecektir.

**Kabul kriterleri:**
- Kapsayıcı (exhaustive) marketlerde 0–10 × 0–10 her skor için tam olarak bir seçenek `won` olur (özellik testi).
- Beraberlikte İade'de beraberlik `void` verir; ertelenen / iptal edilen maçta bütün seçenekler `void` olur (test).
- Devre skoru olmayan maçta `needs_halftime` marketleri aday üretmez (test).
- Market kimlikleri API ile aynıdır (`1X2`, `OU_2.5`, `BTTS` …) (sözleşme testi).

#### AŞAMA 5 — Veritabanı Şeması ve Değiştirilemez Kayıt

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `db/schema.sql`, `db/database.py` |

**Amaç:** Tahmin, oran, seçim, denetim ve sonuç kayıtlarını denetlenebilir, zaman damgalı ve geriye dönük değiştirilemez biçimde saklamak.

**Çıktılar:**
- Temel tablolar: `leagues`, `teams`, `fixtures`, `data_revisions`, `odds_sources`, `odds_snapshots`, `market_settlement_rules`, `prediction_runs`, `model_versions`, `calibration_maps`, `predictions`, `selection_audits`, `user_settings`, `live_states`, `radar_calibration`, `radar_evaluations`, `radar_alerts`, `notification_permissions`.
- Ek-yalnız (append-only) tetikleyiciler: tahmin, oran, karar ve denetim kayıtlarında UPDATE/DELETE engellenir.
- Şema kısıtları: MODEL A için `uses_odds = 0`; `is_iddaa` yalnızca `source_type = 'iddaa'` ise; `data_cutoff_at ≤ selection_timestamp`.
- **v6.3 entegrasyonu:**
  - Tablolar: `selection_modes`, `selection_mode_configs`, `selection_mode_candidates`, `selection_mode_decisions`, `selection_mode_rejections`, `selection_mode_performance`, `selection_mode_calibration`, `selection_mode_comparisons`.
  - Her seçim kaydında: `fixture_id`, `market_id`, `outcome_id`, `model_a_prediction_id`, `model_b_prediction_id`, `selection_mode`, `selection_timestamp`, `odds_snapshot_id`, `data_cutoff_at`, `estimated_probability`, `estimated_ev`, `uncertainty_metrics`, `audit_status`, `decision_reason`, `settlement_status`.
  - `selection_mode` yalnızca `highest_probability` | `best_value` | `balanced` olabilir.
  - `settlement_status` yalnızca `pending → won | lost | void` geçişine izin verilerek güncellenir; diğer alanlar değiştirilemez.

**Kabul kriterleri:**
- Şema boş veritabanında tekrar tekrar (idempotent) kurulur (test).
- Karar, aday, denetim, tahmin ve oran kayıtlarında UPDATE/DELETE hata verir (test).
- v6.3 entegrasyonu: Kaydedilmiş bir kararın `selection_mode` değerini değiştirme girişimi reddedilir ("Geçmiş seçimler geriye dönük değiştirilmemeli" testi).
- v6.3 entegrasyonu: Üç kod dışındaki `selection_mode` değeri eklenemez; `settlement_status` `won`'dan `lost`'a çevrilemez (test).
- v6.3 entegrasyonu: Her kararda 15 alan doludur; `odds_snapshot_id` ve `estimated_ev` yalnızca oransız En Güvenilir seçimlerinde boş olabilir (test).

#### AŞAMA 6 — Geçmiş Zaman (Point-in-Time) Veri Deposu

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `data/point_in_time.py` |

**Amaç:** Her tahminin yalnızca tahmin anında (`data_cutoff_at`) bilinen verilerle üretilmesini garanti etmek.

**Çıktılar:**
- `as_of(t)` sorgu arayüzü: bilinme zamanı (`known_at` / `recorded_at`) ≤ t olan sonuçlar ve yakalanma zamanı ≤ t olan oranlar.
- Olay zamanı ile bilinme zamanının ayrı saklanması; düzeltilen verilerin sürümlü (`data_revisions`) tutulması.

**Kabul kriterleri:**
- Rastgele t değerleri için `as_of(t)` bilinme zamanı t'den sonra olan hiçbir kaydı döndürmez (özellik testi).
- Sonradan düzeltilen bir sonuç, düzeltmeden önceki t için eski haliyle döner (test).
- Aynı `data_cutoff_at` ile yeniden üretilen tahmin, depoya yeni veri eklendikten sonra da aynı kalır (test).

#### AŞAMA 7 — Tarihsel Veri İçe Aktarıcı (CSV)

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `data/football_data_csv.py` |

**Amaç:** Geçmiş maç sonuçlarını ve varsa tarihsel oranları kronolojik doğrulama için içe aktarmak.

**Çıktılar:**
- CSV ayrıştırıcı: tarih, takımlar, maç sonu ve devre skorları, oran sütunları; takım adı normalleştirme ve tekilleştirme.
- Oranlar `historical_csv` kaynak türüyle; yakalanma zamanı kesin değilse `timestamp_precision = 'approximate'`.
- İçe aktarma raporu: okunan / kabul edilen / reddedilen satırlar ve ret nedenleri.

**Kabul kriterleri:**
- Aynı dosyanın iki kez içe aktarılması yinelenen kayıt üretmez (test).
- Bozuk satırlar nedeniyle birlikte reddedilir ve sayıları raporlanır (test).
- İçe aktarılan oranlar İddaa olarak etiketlenmez (test).
- Zamanı yaklaşık olan oranlarla elde edilen sonuçlar raporlarda ayrıca işaretlenir (test).

#### AŞAMA 8 — Sentetik Demo Ligler ve Oranlar

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `data/synthetic.py` |

**Amaç:** Gerçek veri bağlanmadan uçtan uca akışı denemek için açıkça etiketli sentetik veri üretmek.

**Çıktılar:**
- Bilinen gizli takım güçleriyle sentetik ligler, fikstürler, sonuçlar ve marjlı oranlar; kurgusal takım adları.
- Kaynak `synthetic-demo`, etiket "Sentetik demo bahisçisi"; tohumla tekrarlanabilir üretim.

**Kabul kriterleri:**
- Her sentetik kayıt sentetik kaynak türü taşır; bu verilerle üretilen API yanıtlarında `is_synthetic_data: true` (test).
- Sentetik kayıtlar gerçek veri performans raporlarına karışmaz (filtre testi).
- Bilinen parametrelerle üretilen veride MODEL A takım güç sıralamasını geri kazanır: tahmini ve gerçek güçler arasındaki Spearman korelasyonunun %95 güven aralığı sıfırı içermez (test).

#### AŞAMA 9 — Gerçek Veri Sağlayıcı Entegrasyonu ve Kimlik Eşleştirme

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Planlandı | `data/` (yeni sağlayıcı adaptörleri) |

**Amaç:** Lisanslı bir sağlayıcıdan fikstür, sonuç, devre skoru ve kadro verilerini almak; takım, lig ve sezon kimliklerini eşleştirmek.

**Çıktılar:**
- Sağlayıcı adaptör arayüzü (fikstür, sonuç, kadro); yeniden deneme ve istek sınırı yönetimi.
- Kimlik eşleştirme tablosu (sağlayıcı kimliği ↔ iç kimlik) ve çakışma raporu.
- Lisans ve kullanım koşullarının kaydı.
- Point-in-time depoya `known_at` ile yazım (geriye tarihleme yok).

**Kabul kriterleri:**
- Kaydedilmiş örnek yanıtlarla adaptör sözleşme testleri geçer.
- Eşleşmeyen takım/maç oranı raporlanır; eşleşmeyen kayıtlar tahminde kullanılmaz (test).
- Bütün kayıtların `known_at` değeri alım zamanından önce olamaz (test).
- Simüle edilmiş sağlayıcı kesintisinde veri durumu `stale` olur ve seçim üretilmez (entegrasyon testi).

#### AŞAMA 10 — 21 Organizasyon İçin Üretim Veri Akışları

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Planlandı | `data/`, zamanlayıcı |

**Amaç:** 21 organizasyonun fikstür, sonuç ve geçmiş sezon verilerini düzenli ve izlenebilir biçimde güncel tutmak.

**Çıktılar:**
- Zamanlanmış güncelleme işleri (maç öncesi yenileme pencereleri, maç sonrası sonuç alımı).
- Her organizasyon için geçmiş sezon doldurma (backfill); küme düşme / yükselme ve sezon geçişlerinin işlenmesi.
- Organizasyon bazlı kapsam raporu (sezon sayısı, maç sayısı, eksik alanlar).

**Kabul kriterleri:**
- Kapsam raporu 21 organizasyonun tamamını listeler; geçmişi yetersiz organizasyonlar işaretlenir ve bu organizasyonlarda seçim üretilmez (test).
- İşler idempotenttir; yeniden çalıştırma yinelenen kayıt üretmez (test).
- Sonuç oluşumu → kayıt gecikmesi ölçülür ve raporlanır; tazelik sınırları `config.py`'den gelir.

#### AŞAMA 11 — Oran Anlık Görüntüleri: Kaynak, Zaman ve İddaa Ayrımı

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Kısmen | `db/`, `odds/` |

**Amaç:** Her oranı kaynağı, yakalanma zamanı ve bahisçisiyle saklamak; İddaa dışı oranları asla İddaa olarak göstermemek.

**Çıktılar:**
- Mevcut: `odds_sources` / `odds_snapshots` (kaynak, etiket, `is_iddaa`, `captured_at`, market, seçenek, ondalık oran) ve oran doğrulama.
- Eksik: gerçek oran sağlayıcı(lar)ı, İddaa resmi kaynak doğrulaması, oran yaşı hizmet düzeyi hedefleri.

**Kabul kriterleri:**
- `is_iddaa: true` yalnızca doğrulanmış İddaa kaynağında görülür (şema kısıtı + test).
- `captured_at` değeri seçim zamanından sonra olan oran hiçbir hesapta kullanılmaz (test).
- Yapılandırmadaki azami yaşı aşan oran `stale` sayılır ve EV hesabına girmez (test).
- Oran değişikliği yeni anlık görüntü olarak eklenir; mevcut görüntü değiştirilemez (test).

#### AŞAMA 12 — Veri Güncelliği ve Yeterliliği Kontrolleri

| Faz | Durum | Modül |
|---|---|---|
| Temel ve Veri | Tamamlandı (temel) | `quality/`, `data/point_in_time.py` |

**Amaç:** Tahmin öncesinde verinin güncel ve yeterli olduğunu ölçülebilir ölçütlerle doğrulamak.

**Çıktılar:**
- Güncellik durumu (`fresh` / `stale`), `last_result_at`, `odds_age_seconds`.
- Yeterlilik ölçütleri (takım başına asgari maç, lig geçmişi); asgari değerler geçmiş uzunluğu ile kalibrasyon kalitesi arasındaki kronolojik ilişkiden belirlenir.
- Ret nedeni kodu (`data_fresh`) ve açıklaması.

**Kabul kriterleri:**
- API `data_freshness` alanları her öneride doludur (sözleşme testi).
- Yetersiz veya eski veride aday reddedilir ve ret nedeni "Neden elendi?" verisinde görünür (test).
- Yeterlilik asgari değerleri kaynağıyla birlikte saklanır; kod içinde gerekçesiz sabit olarak bulunmaz (test).

### Faz 2 — Modeller (Aşama 13–22)

#### AŞAMA 13 — Özellik Çıkarımı ve Kronolojik Eğitim Pencereleri

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Kısmen | `models/`, `data/point_in_time.py`, `backtest/` |

**Amaç:** Modellerin kullandığı futbol özelliklerini point-in-time üretmek ve eğitim/doğrulama/test pencerelerini kronolojik tanımlamak.

**Çıktılar:**
- Mevcut: skor tabanlı girdiler, kronolojik pencere bölücü (genişleyen / kayan pencere).
- Eksik: özellik kayıt defteri (her özelliğin kaynağı ve `uses_odds` bayrağı), form / dinlenme günü gibi ek özellikler.

**Kabul kriterleri:**
- Her katmanda `max(eğitim.kickoff) < min(test.kickoff)` (test).
- Özellik kayıt defterinde her özellik `uses_odds` ile işaretlidir; MODEL A girdi kümesinde `uses_odds = true` özellik sayısı sıfırdır (test).
- Maç m için hesaplanan özellikler yalnızca `known_at ≤ data_cutoff_at(m)` verisini kullanır (test).

#### AŞAMA 14 — MODEL A: Zaman Azalımlı Dixon–Coles Poisson

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/model_a.py` |

**Amaç:** Yalnızca futbol verileriyle (skorlar, iç saha, zaman) takımların hücum/savunma güçlerini ve skor dağılımını tahmin etmek.

**Çıktılar:**
- Dixon–Coles modeli: düşük skor düzeltmesi (ρ), iç saha avantajı, üstel zaman azalımı (ξ).
- ξ ve düzenlileştirme yalnızca doğrulama penceresinde seçilir.
- Skor matrisi çıktısı; tahmin kaydında model sürümü ve `data_cutoff_at`.

**Kabul kriterleri:**
- Skor matrisi elemanları ≥ 0 ve toplamı 1'e 1e-9 toleransla eşittir (test).
- ξ seçimi test penceresine erişmeden yapılır (test).
- Gerçek tarihsel veride kronolojik test döneminde MODEL A'nın 1X2 log loss ve Brier değeri, naif taban çizgisinden (lig bazlı geçmiş 1/X/2 frekansları) düşüktür ve farkın blok bootstrap %95 güven aralığı sıfırı içermez. (Sentetik veride yalnızca işlevsel doğrulama yapılır.)
- Aynı veri ve kesim zamanıyla sonuç deterministiktir (test).

#### AŞAMA 15 — MODEL A Skor Matrisinden Market Olasılıkları

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/model_a.py`, `domain/markets.py` |

**Amaç:** Katalogdaki her market için olasılıkları skor matrisinden ve sonuçlandırma kurallarıyla tutarlı biçimde türetmek.

**Çıktılar:**
- Olasılıklar, sonuçlandırmada kullanılan aynı kural fonksiyonlarının skor matrisine uygulanmasıyla hesaplanır (tek doğruluk kaynağı).
- İlk yarı marketleri için devre skor dağılımı; desteklenmeyen marketler `unsupported` olarak işaretlenir.

**Kabul kriterleri:**
- Kapsayıcı marketlerde seçenek olasılıkları toplamı 1'dir; `void` içeren marketlerde (Beraberlikte İade) iade olasılığı ayrıca raporlanır (test).
- Tutarlılık: P(Üst 2,5) = Σ P(toplam gol ≥ 3); P(1X) = P(1) + P(X) (test).
- P(Üst k,5) k arttıkça azalır (test).
- Model desteği olmayan market aday üretmez (test).

#### AŞAMA 16 — MODEL A Oran İzolasyonu

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/isolation.py` |

**Amaç:** MODEL A'nın eğitim ve tahmin sürecine hiçbir oran veya orandan türetilmiş bilginin girmemesini garanti etmek.

**Çıktılar:**
- Girdi beyaz listesi ve oran sütunu tespitinde hata veren izolasyon koruyucusu.
- MODEL A için oran tablolarına erişimi olmayan veri erişim katmanı.
- **v6.3 entegrasyonu:**
  - Seçim modları (özellikle En Avantajlı ve Dengeli) MODEL A'ya oran geçiremez; EV, MODEL A olasılığı üretildikten sonra seçim katmanında hesaplanır.

**Kabul kriterleri:**
- Oran sütunu içeren girdiyle MODEL A çağrısı izolasyon hatası verir (test).
- Veritabanındaki bütün oranlar değiştirildiğinde MODEL A olasılıkları birebir aynı kalır ("MODEL A oran verisi kullanmamalı" testi).
- v6.3 entegrasyonu: Üç modda çalıştırma aynı MODEL A tahmin kimliklerini ve değerlerini üretir (test).

#### AŞAMA 17 — Kronolojik Out-of-Fold MODEL A Çıktıları

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/model_b.py`, `models/isolation.py` |

**Amaç:** MODEL B'ye girdi olacak MODEL A olasılıklarını, ilgili maçı görmemiş modellerden kronolojik olarak üretmek.

**Çıktılar:**
- Her maç için yalnızca o maçın kesim zamanından önceki verilerle eğitilmiş MODEL A'dan üretilen out-of-fold (OOF) olasılıklar; katman (fold) kimliğiyle saklanır.

**Kabul kriterleri:**
- Her OOF değeri için MODEL A eğitim verisinin son maçı, ilgili maçın başlangıcından öncedir (test).
- MODEL B eğitiminde örneklem içi (in-sample) MODEL A çıktısı kullanılmaz (katman izleme testi).

#### AŞAMA 18 — Oran Marjı Arındırma ve Oran Doğrulama

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `odds/` |

**Amaç:** Gerçek oranları doğrulamak ve marjdan arındırılmış referans olasılıklarını hesaplamak.

**Çıktılar:**
- Marj hesabı; marj arındırma yöntemleri (orantılı ve alternatifler); yöntem seçimi kronolojik log loss ile.
- Doğrulama kuralları: oran > 1, market eksiksizliği, makul marj aralığı, tazelik.

**Kabul kriterleri:**
- Marjsız olasılıkların toplamı 1'e 1e-9 toleransla eşittir (test).
- Elle hesaplanmış örnek vektörler birebir üretilir (test).
- Geçersiz oran (≤ 1, eksik seçenek, makul olmayan marj, eski) `invalid` işaretlenir ve hiçbir hesapta kullanılmaz ("En Avantajlı modu geçersiz oran kullanmamalı" testinin altyapısı).

#### AŞAMA 19 — MODEL B: Yığılmış Lojistik Model

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/model_b.py` |

**Amaç:** Futbol verilerini, OOF MODEL A çıktılarını ve tahmin anında erişilebilir marjsız oranları birleştirerek olasılık üretmek.

**Çıktılar:**
- Market ailesi bazında yığılmış lojistik model; girdiler: OOF MODEL A logitleri, marjsız oran logitleri, futbol özellikleri.
- Düzenlileştirme kronolojik doğrulamayla seçilir; katsayılar ve sürüm saklanır.
- Tahmin anında geçerli oran yoksa MODEL B üretilmez (geçersiz sayılır).

**Kabul kriterleri:**
- Gerçek tarihsel veride MODEL B ile marjsız piyasa olasılıkları arasındaki log loss farkı blok bootstrap %95 güven aralığıyla raporlanır; MODEL B piyasadan istatistiksel olarak anlamlı biçimde kötüyse o market ailesinde MODEL B geçersiz sayılır ve seçimlerde kullanılmaz (test + rapor).
- Tahmin anında oran yoksa MODEL B kaydı oluşmaz ve MODEL B gerektiren adaylar reddedilir (test).
- Katsayılar ve model sürümü her tahmin kaydından izlenebilir (test).

#### AŞAMA 20 — MODEL B Zaman Sızıntısı Korumaları

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Tamamlandı (temel) | `models/isolation.py` |

**Amaç:** MODEL B'nin yalnızca tahmin anında erişilebilen veri ve oranları kullandığını garanti etmek.

**Çıktılar:**
- Koruyucu kural: `odds.captured_at ≤ data_cutoff_at ≤ kickoff`; kapanış oranı daha önceki bir tahminde kullanılamaz.
- Eğitim etiketleri yalnızca kesim zamanından önce biten maçlardan gelir.
- **v6.3 entegrasyonu:**
  - Seçim kaydındaki `odds_snapshot_id`, MODEL B'nin kullandığı görüntüyle aynı ya da ondan yenidir ve her ikisi de seçim zamanından öncedir; bağımsız denetim bunu yeniden kontrol eder.

**Kabul kriterleri:**
- Gelecekteki bir oran görüntüsü eklendiğinde koruyucu bunu dışlar veya hata verir ("MODEL B zaman sızıntısı içermemeli" testi).
- Kesim zamanından sonraki veriler değiştirildiğinde t anındaki tahminler değişmez (test).
- v6.3 entegrasyonu: Kayıtlı bütün kararlar için denetimin `odds_timing` kontrolü geçer (test).

#### AŞAMA 21 — Model Sürümleme, Yeniden Eğitim ve Tahmin Kaydı

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Kısmen | `db/`, `models/` |

**Amaç:** Her tahminin hangi model sürümü, parametre ve veri kesim zamanıyla üretildiğini izlenebilir kılmak.

**Çıktılar:**
- Mevcut: `model_versions` (model, parametre özeti, eğitim penceresi, `uses_odds`), ek-yalnız `predictions`.
- Eksik: yeniden eğitim takvimi; sürüm yükseltme kuralı (yeni sürüm yalnızca aynı kronolojik test kümesinde kötüleşme göstermezse devreye alınır).

**Kabul kriterleri:**
- Her tahminde `model_version` ve `data_cutoff_at` doludur (test).
- Yeni sürüm, aynı kronolojik test kümesinde log loss farkının güven aralığı kötüleşme gösteriyorsa devreye alınmaz (sahte sürümlerle test).
- Yeniden eğitimden sonra eski tahmin kayıtları değişmez (test).

#### AŞAMA 22 — Gelişmiş Futbol Özellikleri

| Faz | Durum | Modül |
|---|---|---|
| Modeller | Planlandı | `models/`, `data/` |

**Amaç:** Kadro, sakatlık/ceza, dinlenme süresi ve xG gibi futbol verilerini sağlayıcı desteklediğinde modellere eklemek.

**Çıktılar:**
- Point-in-time özellik adaptörleri (kadro yalnızca açıklandıktan sonra).
- Her özellik için kronolojik ablasyon çalışması.

**Kabul kriterleri:**
- Yeni özellik yalnızca kronolojik ablasyonda log loss iyileşmesinin %95 güven aralığı sıfırı içermiyorsa devreye alınır.
- Eklenen bütün özellikler `uses_odds = false` (MODEL A'ya uygun) (test).
- Kadro özellikleri yalnızca `known_at ≤ data_cutoff_at` verisini kullanır (test).

### Faz 3 — Kalibrasyon ve Güvenilirlik (Aşama 23–31)

#### AŞAMA 23 — Kronolojik Kalibrasyon

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `calibration/` |

**Amaç:** Ham MODEL A ve MODEL B olasılıklarını yalnızca geçmiş doğrulama verisiyle kalibre etmek.

**Çıktılar:**
- Model × market ailesi bazında kalibratörler (izotonik / Platt / beta; seçim kronolojik); uygulama penceresinden önce biten pencerede eğitilir ve penceresiyle saklanır (`calibration_maps`).
- Ham ve kalibre olasılıkların birlikte saklanması; örneklem yeterliliği kuralı.

**Kabul kriterleri:**
- Kalibratör eğitim penceresi tahmin zamanından önce biter (test).
- Kronolojik test döneminde kalibrasyon ECE değerini anlamlı biçimde kötüleştirmez (fark güven aralığıyla raporlanır); kötüleştiriyorsa belgelenmiş geri dönüş uygulanır.
- Kalibrasyon örneklemi yetersizse durum `insufficient` olur ve aday üç modda da reddedilir ("Kalibrasyon yetersizse seçim reddedilmeli" testi).

#### AŞAMA 24 — Olasılık ve Kalibrasyon Metrikleri

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `calibration/metrics.py` |

**Amaç:** Tahmin kalitesini standart ve karşılaştırılabilir metriklerle ölçmek.

**Çıktılar:**
- Brier (ikili ve çok sınıflı), log loss (kırpma kuralı belgelenmiş), ECE, güven aralıklı güvenilirlik kutuları.
- İsabet oranı için Wilson aralığı, ROI için bootstrap aralığı.

**Kabul kriterleri:**
- Metrik fonksiyonları referans uygulamalarla örnek vektörlerde 1e-12 toleransla aynı sonucu verir (test).
- Wilson aralığının simülasyonla ölçülen kapsama oranı nominal %95 düzeyiyle tutarlıdır (simülasyon testi, tolerans testte belgelenir).
- n = 0 olduğunda metrikler `null` döner (API sözleşmesiyle uyumlu) (test).

#### AŞAMA 25 — Nadir Olay Kalibrasyonu

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Kısmen | `calibration/`, `radar/` |

**Amaç:** Yüksek olasılık bölgesinde (radar için MODEL A ve MODEL B > %90) ve az gözlemli marketlerde kalibrasyonun yeterli olup olmadığını ölçmek.

**Çıktılar:**
- Kuyruk bölgesi güvenilirlik analizi; Clopper–Pearson kesin binom güven aralıkları.
- Yeterlilik kuralı: güven aralığı genişliği kronolojik olarak belirlenmiş azami genişliği aşmamalı ve ortalama tahmin aralık içinde kalmalı.
- `radar_calibration` kayıtları (model bazında, `sufficient` / `insufficient`).
- Eksik: gerçek canlı veriyle doğrulama (Aşama 56–57'ye bağlı).

**Kabul kriterleri:**
- Radar bölgesi için n, gözlenen frekans ve %95 Clopper–Pearson aralığı MODEL A ve MODEL B için ayrı raporlanır (test).
- Durum `insufficient` ise radar alarm üretmez (radar koşul testi).
- Kalibrasyon durumu yalnızca değerlendirme zamanından önceki verilerle hesaplanır (test).

#### AŞAMA 26 — Tahmin Belirsizliği

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `calibration/` (Laplace çekilişleri) |

**Amaç:** Her olasılık için parametre belirsizliğinden kaynaklanan aralığı hesaplamak.

**Çıktılar:**
- Tahmin edilen parametreler çevresinde Laplace yaklaşımı ve sonsal çekilişler → olasılık alt/üst sınırları, `interval_width`.
- Belirsizlik düzeyi (`low` / `medium` / `high`); düzey sınırları kronolojik doğrulamayla belirlenir.

**Kabul kriterleri:**
- `lower ≤ nokta tahmini ≤ upper` her aday için sağlanır (test).
- Sentetik veride örneklem büyüdükçe ortalama aralık genişliği küçülür (test).
- Aynı tohumla sonuçlar tekrarlanabilir; düzey sınırları kaynağıyla saklanır (test).

#### AŞAMA 27 — Tahmin Dayanıklılığı

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `quality/` (dayanıklılık pertürbasyonları) |

**Amaç:** Tahminin makul model ve veri varsayımı değişikliklerine karşı kararlı olup olmadığını ölçmek.

**Çıktılar:**
- Senaryolar: zaman ağırlığı yavaş/hızlı, son maçın çıkarılması, iç saha avantajı değişimi, bir önceki oran görüntüsü (MODEL B).
- `max_shift`, `score`, durum `robust` / `fragile`; senaryo listesi API'de.

**Kabul kriterleri:**
- Her adayda senaryolar ve `max_shift` kaydedilir (test).
- `max_shift` kronolojik olarak belirlenen sınırı aşarsa aday `fragile` olur ve üç modda da reddedilir (test).
- Senaryolar aynı veri kesim zamanını kullanır (sızıntı testi).

#### AŞAMA 28 — Olasılık Tutarlılığı Kontrolleri

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `quality/` |

**Amaç:** Aynı maçtaki market olasılıklarının birbiriyle matematiksel olarak tutarlı olduğunu doğrulamak.

**Çıktılar:**
- Kontroller: toplamlar, iç içe marketler (P(1X) ≥ P(1)), toplam gol monotonluğu, karşılıklı gol ile skor matrisi uyumu; kalibrasyon sonrası tutarsızlıkta yeniden normalleştirme veya ret.

**Kabul kriterleri:**
- Bilerek tutarsız verilen girdide aday ret kodu ile reddedilir (test).
- Üretilen bütün aday kümeleri kontrolleri 1e-6 toleransla geçer (test).

#### AŞAMA 29 — Tahmin Geçerliliği

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `quality/` (tahmin geçerliliği) |

**Amaç:** Tahminin hangi koşullarda ve ne zamana kadar geçerli olduğunu belirlemek.

**Çıktılar:**
- `valid_until` (en geç maç başlangıcı); geçersizleşme nedenleri: yeni veri, sınırı aşan oran hareketi, kadro açıklanması, erteleme.
- Durum `valid` / `expired`.

**Kabul kriterleri:**
- Maç başladıktan sonra maç öncesi adaylar `expired` olur ve yeni maç öncesi seçim üretilmez (test).
- Ertelenen maçta bütün adaylar `expired`, sonuçlar `void` olur (test).
- Süresi dolmuş seçim API'de ve arayüzde etkin olarak gösterilmez (test).

#### AŞAMA 30 — Market Bağımlılığı ve İlişkili Seçimler

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `quality/` (market bağımlılığı) |

**Amaç:** Aynı maçtaki ilişkili marketleri belirlemek ve kullanıcıya açıkça göstermek.

**Çıktılar:**
- Skor matrisinden ortak olasılıklarla seçenekler arası korelasyon ve kapsama (implied subset) ilişkisi.
- Kartta `related_selections`; ilişkili seçimlerin bağımsızmış gibi sunulmasını engelleyen kural.

**Kabul kriterleri:**
- Örnek skor matrisinde hesaplanan korelasyon analitik değerle aynıdır (test).
- Belgelenmiş korelasyon sınırını aşan ilişkili seçimler kartta listelenir (test).
- İlişkili seçeneklerin olasılıkları çarpılarak birleşik olasılık hesaplanmaz (test).

#### AŞAMA 31 — MODEL A–MODEL B Uyuşmazlık Analizi

| Faz | Durum | Modül |
|---|---|---|
| Kalibrasyon ve Güvenilirlik | Tamamlandı (temel) | `quality/`, `selection/` |

**Amaç:** İki model arasındaki farkı ölçmek ve karar kurallarına otomatik ortalama almadan yansıtmak.

**Çıktılar:**
- Uyuşmazlık ölçüsü (`disagreement`); `max_disagreement` sınırı kronolojik.
- İki modelin olasılıkları her yerde ayrı gösterilir.

**Kabul kriterleri:**
- Uyuşmazlık sınırı aşıldığında aday ilgili mod kuralına göre reddedilir; olasılıklar ortalanarak karar verilmez (test).
- İki modelin uyumu denetimde "bağımsız doğrulama" olarak sayılmaz (test: denetim kontrol listesinde böyle bir kontrol yoktur).

### Faz 4 — Seçim ve Kalite (Aşama 32–40)

#### AŞAMA 32 — Beklenen Değer ve İhtiyatlı EV Alt Sınırı

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `odds/` (EV) |

**Amaç:** EV = (p × decimal_odds) − 1 hesabını kalibre olasılık ve belirsizlikle birlikte yapmak.

**Çıktılar:**
- Nokta EV ve olasılık alt sınırından ihtiyatlı EV alt sınırı.
- Oran yalnızca doğrulanmış ve güncel görüntüden alınır.
- **v6.3 entegrasyonu:**
  - MODEL A ve MODEL B için EV ayrı hesaplanır ve saklanır (`expected_value.model_a`, `expected_value.model_b`).
  - En Avantajlı ve Dengeli modlarında avantaj belirsizlik altında da (alt sınır) desteklenmelidir; pozitif nokta EV tek başına yeterli değildir.
  - Her EV gösteriminde "Avantaj yalnızca model tahmini doğruysa geçerlidir." uyarısı.

**Kabul kriterleri:**
- Birim testleri: p = 0,5 ve oran 2,00 → EV = 0 (test).
- EV alt sınırı ≤ nokta EV (test).
- EV ham değil kalibre olasılıkla hesaplanır (test).
- Geçersiz veya eski oranla EV hesaplanmaz; değer `null` olur (test).

#### AŞAMA 33 — Tahmin Kalite Kapısı

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `quality/` (Tahmin Kalite Kapısı) |

**Amaç:** Bir adayın herhangi bir seçim moduna veya radara girebilmesi için gereken ortak asgari kalite koşullarını tek noktada uygulamak.

**Çıktılar:**
- Kapı kontrolleri: veri güncel ve yeterli, sonuçlandırma kuralı doğrulanmış, MODEL A ve MODEL B geçerli, kalibrasyon yeterli, belirsizlik kabul edilebilir, dayanıklılık uygun, tahmin geçerli, olasılık tutarlı.
- Kontrol listesiyle birlikte `passed` / `failed` sonucu.
- **v6.3 entegrasyonu:**
  - Kapı, mod-bağımsız ortak çekirdek + mod-özel ek koşullar olarak çalışır: En Güvenilir modunda oran zorunlu değildir; En Avantajlı ve Dengeli modlarında doğrulanmış ve güncel oran zorunludur.
  - Hiçbir mod çekirdek koşulları gevşetemez veya atlayamaz.
  - Kapı sonucu her aday ve mod için `selection_mode_candidates` / `selection_mode_rejections` tablolarına yazılır.
  - Radarın kalite kapısı kontrolü seçim modundan bağımsızdır.

**Kabul kriterleri:**
- Her çekirdek kontrolün tek başına başarısız olması adayın üç modda da reddedilmesine yol açar (parametrik test).
- v6.3 entegrasyonu: Oransız aday En Güvenilir modunda kapıdan geçebilir; En Avantajlı ve Dengeli modlarında geçemez (test).
- v6.3 entegrasyonu: Çekirdek kapı sonucu kullanıcının seçtiği moddan bağımsız olarak aynıdır (test).

#### AŞAMA 34 — Akıllı Seçim Motoru (15 Adımlı Süreç)

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `selection/` (15 adımlı motor) |

**Amaç:** Her maç için veriyle desteklenen bütün marketleri tarayıp v6.3 Bölüm 4'teki 15 adımı sırasıyla uygulamak.

**Çıktılar:**
- Adımlar: 1) fikstür ve veri durumu 2) mevcut marketler 3) MODEL A 4) MODEL B 5) olasılık tutarlılığı 6) kalibrasyon ve belirsizlik 7) dayanıklılık 8) tahmin geçerliliği 9) market bağımlılığı 10) güncel oran ve EV 11) Tahmin Kalite Kapısı 12) üç mod için uygun adaylar 13) bağımsız denetim 14) gerekçelerle kayıt 15) kullanıcının moduna göre sıralama ve gösterim.
- Her çalıştırma `run_id` ile adım adım günlüklenir.
- **v6.3 entegrasyonu:**
  - En Güvenilir (`highest_probability`): kalibrasyon ve belirsizlik kontrollerinden geçen olasılık alt sınırına göre sıralama; oran zorunlu değil; ilişkili marketler açıkça belirtilir.
  - En Avantajlı (`best_value`): doğrulanmış ve güncel oran zorunlu; EV alt sınırı ile sıralama; güvenilir avantaj yoksa seçim üretilmez.
  - Dengeli (`balanced`, varsayılan): v6.3 Bölüm 1.3'teki 10 koşulun tamamı; belirsizlik altında ihtiyatlı Kelly oranıyla sıralama.
  - Adım 12'de üç mod aynı aday havuzundan ayrı ayrı değerlendirilir; adım 15 yalnızca gösterimi kullanıcının moduna göre sıralar.
  - Mod değişimi MODEL A/B olasılıklarını yeniden hesaplatmaz.

**Kabul kriterleri:**
- Her çalıştırmanın günlüğünde 15 adım doğru sırayla yer alır (test).
- v6.3 entegrasyonu: Mod değişiminde MODEL A/B tahmin kimlikleri ve değerleri aynı kalır ("Mod değişimi MODEL A/B tahminlerini değiştirmemeli" testi).
- v6.3 entegrasyonu: Ham olasılığı en yüksek ama belirsizliği yüksek ya da kalibrasyonu yetersiz aday En Güvenilir modunda ilk sırada yer almaz ("kör seçim yapmamalı" testi).
- v6.3 entegrasyonu: Dengeli mod EV alt sınırı eşiği geçmeyen (pozitif EV desteği olmayan) adayı seçmez (test).
- v6.3 entegrasyonu: En Avantajlı mod geçersiz, eski veya doğrulanmamış oranı kullanmaz (test).
- v6.3 entegrasyonu: Mod parametresi ve kayıtlı tercih yoksa çalıştırma Dengeli modla sonuç döner (test).

#### AŞAMA 35 — Kronolojik Eşik Belirleme

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `selection/` (kronolojik eşik ayarı) |

**Amaç:** Olasılık, EV, belirsizlik ve uyuşmazlık eşiklerini yalnızca kronolojik doğrulama verisiyle belirlemek.

**Çıktılar:**
- Uygulama penceresinden önce biten doğrulama penceresinde eşik ayarı; amaç fonksiyonu ve asgari seçim sayısı belgelenmiş.
- `selection_mode_configs` kaydı: değerler, kaynak (`chronological_validation`), doğrulama penceresi, doğrulama metrikleri, durum (`established` / `not_established`).
- **v6.3 entegrasyonu:**
  - Her mod için ayrı eşik seti ve ayrı doğrulama kaydı.
  - `not_established` durumundaki mod seçim üretmez; boşluğu doldurmak için başka modun eşiği kullanılmaz.

**Kabul kriterleri:**
- Ayar penceresinin sonu uygulama penceresinin başından öncedir (şema kısıtı + test).
- Eşikler yalnızca `selection_mode_configs` kayıtlarından okunur; kayıt yoksa mod `not_established` olur (test).
- Yeniden ayar yeni kayıt oluşturur; önceki kararlar kendi `config_id` değerini korur (test).

#### AŞAMA 36 — Bağımsız Seçim Denetimi

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `selection/` (bağımsız denetim) |

**Amaç:** Seçim motorundan ayrı bir denetçinin her seçimi ham kayıtlardan yeniden hesaplayarak doğrulaması.

**Çıktılar:**
- Denetim kontrolleri: oran zamanı seçimden önce, oran geçerliliği, veri kesim zamanı, MODEL A izolasyonu, eşiklerin kayıtlı yapılandırmayla uyumu, EV'nin yeniden hesaplanması, kapı kontrolleri.
- `selection_audits` (ek-yalnız), durum `passed` / `failed` / `pending`.
- **v6.3 entegrasyonu:**
  - Denetim her mod kararı için ayrı yapılır ve `audit_status` olarak kaydedilir.
  - Denetimden kalan seçim hiçbir modda gösterilmez.
  - Denetçi, kararın mod etiketinin ve eşiklerinin sonradan değiştirilmediğini doğrular.

**Kabul kriterleri:**
- Denetçi kodu motorun karar fonksiyonlarını içe aktarmaz (içe aktarma grafiği testi).
- Kurcalanmış kayıt (ör. oran veya mod değişmiş) `failed` sonuç verir (test).
- Denetim sonuçları saklanır ve `decision-explanation` ucundan okunabilir ("Bağımsız denetim sonuçları saklanmalı" testi).
- `failed` durumundaki seçim `/recommendations` yanıtında yer almaz (test).

#### AŞAMA 37 — Karar Gerekçeleri ve Ret Nedenleri

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `selection/`, `db/` |

**Amaç:** Her seçim ve ret kararını sağlanan ve sağlanamayan koşullarla birlikte açıklanabilir kılmak.

**Çıktılar:**
- Kontrol listesi: `code`, `label`, `passed`, `detail`, `value`, `threshold`; Türkçe `reason_summary`; ret nedeni sayımları.
- **v6.3 entegrasyonu:**
  - "Neden seçildi?" ve "Neden elendi?" verileri mod bazında üretilir ve saklanır.
  - Uçlar: `/recommendations/{id}/decision-explanation`, `/recommendations/{id}/rejection-reasons`, `/recommendations/rejections`.

**Kabul kriterleri:**
- Her reddedilen adayın en az bir başarısız kontrolü vardır (test).
- Her seçilen adayın bütün kontrolleri başarılıdır (test).
- Gerekçe metnindeki sayılar kayıtlı değerlerle aynıdır ve Türkçe ondalık virgülle biçimlenir (test).

#### AŞAMA 38 — "Seçim Yok" Politikası

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Tamamlandı (temel) | `selection/`, `api/` |

**Amaç:** Güvenilir seçim bulunmadığında zorla tahmin üretmemek.

**Çıktılar:**
- Boş `items` ve mod-özel `message`; `fixtures_without_selection` sayımı.
- **v6.3 entegrasyonu:**
  - Dengeli: "Güvenilir ve avantajlı seçim bulunamadı."
  - En Avantajlı: "Güvenilir bir avantaj belirlenemedi; seçim üretilmedi."
  - En Güvenilir: "Kalibrasyon ve belirsizlik kontrollerinden geçen güvenilir seçim bulunamadı."

**Kabul kriterleri:**
- Bütün adayların elendiği senaryoda her mod `items: []` ve kendi mesajını birebir döner (test).
- Liste boş kaldığında eşikleri gevşeten bir geri dönüş yolu yoktur; ardışık çalıştırmalarda eşik değerleri değişmez (test).

#### AŞAMA 39 — MODEL A/B Birleşim Karar Yöntemi

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Kısmen | `selection/` |

**Amaç:** İki modelin olasılıklarını yalnızca kronolojik olarak eğitilmiş, kalibre edilmiş ve doğrulanmış bir yöntemle birleştirmek.

**Çıktılar:**
- Mevcut: `conservative_min` (iki modelin ihtiyatlı alt değeri; ortalama yok); yöntem `probability_method` alanında kaydedilir.
- Eksik: kronolojik eğitilmiş birleştirici (doğrulama penceresinde yığma), kalibrasyonu ve doğrulaması.
- **v6.3 entegrasyonu:**
  - Seçim modları birleşim yöntemini değiştiremez; uyuşmazlıkta otomatik ortalama alınmaz.
  - İki model birbirinin bağımsız doğrulaması olarak kabul edilmez.

**Kabul kriterleri:**
- `probability_method` yalnızca onaylı yöntemlerden biri olabilir; basit ortalama reddedilir (test).
- Eğitilmiş birleştirici, kronolojik test döneminde MODEL B'ye göre log loss farkının %95 güven aralığı iyileşme gösterirse ve ECE kötüleşmezse devreye alınır (rapor + test).

#### AŞAMA 40 — Kapanış Oranı (CLV) ve Oran Hareketi Analizi

| Faz | Durum | Modül |
|---|---|---|
| Seçim ve Kalite | Planlandı | `odds/`, `performance/` |

**Amaç:** Seçim anındaki oranı kapanış oranıyla karşılaştırarak avantaj tahminini piyasaya karşı izlemek.

**Çıktılar:**
- Kapanış oranı yakalama; seçim başına CLV; mod bazında CLV raporu.
- Sınırı aşan oran hareketinde geçerlilik yeniden değerlendirmesi (Aşama 29 ile).

**Kabul kriterleri:**
- CLV yalnızca maç başlangıcından önce yakalanmış görüntülerle hesaplanır; kapanış görüntüsü deterministik biçimde belirlenir (test).
- CLV mod bazında güven aralığıyla raporlanır; kazanç iddiası içermez.
- Gerçek oran akışına bağlıdır (Aşama 11).

### Faz 5 — Arayüz ve API (Aşama 41–48)

#### AŞAMA 41 — REST API Çekirdeği

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Tamamlandı (temel) | `api/` (FastAPI) |

**Amaç:** `docs/API.md` sözleşmesini `/api/v1` altında sunmak.

**Çıktılar:**
- Uçlar: `/leagues`, `/system/status`, `/recommendations`, `/settings`, `/radar/*`; ISO-8601 UTC zamanlar; `X-User-Id` yoksa `local`; OpenAPI belgesi.
- **v6.3 entegrasyonu:**
  - `GET /api/v1/recommendations/modes`
  - `GET /api/v1/recommendations?mode=balanced|highest_probability|best_value`
  - `GET /api/v1/recommendations/modes/comparison`, `GET /api/v1/recommendations/modes/performance`
  - `GET /api/v1/recommendations/{id}/decision-explanation`, `GET /api/v1/recommendations/{id}/rejection-reasons`, `GET /api/v1/recommendations/rejections`
  - `GET /api/v1/performance/selection-modes`
  - Geçersiz mod → `422`.

**Kabul kriterleri:**
- Her uç `docs/API.md`'deki alan adları ve türleriyle uyumludur (sözleşme testleri).
- v6.3 entegrasyonu: `mode` verilmezse kullanıcının kayıtlı tercihi, o da yoksa `balanced` kullanılır (test).
- v6.3 entegrasyonu: Geçersiz `mode` değeri `422` döner (test).
- Zamanlar `Z` ile biter; olasılıklar [0, 1] aralığındadır (test).

#### AŞAMA 42 — Kullanıcı Ayarları ve Varsayılan Mod Tercihi

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Tamamlandı (temel) | `api/`, `db/` (`user_settings`) |

**Amaç:** Kullanıcının varsayılan seçim modu tercihini saklamak.

**Çıktılar:**
- `GET /api/v1/settings`, `PUT /api/v1/settings`.
- **v6.3 entegrasyonu:**
  - `default_mode` yalnızca üç koddan biri olabilir; ilk kullanımda `balanced`.
  - Tercih yalnızca gösterilen modu belirler; kayıtlı geçmiş seçimleri değiştirmez.

**Kabul kriterleri:**
- Yeni kullanıcı için `GET /settings` → `default_mode: "balanced"` (test).
- `PUT {"default_mode": "best_value"}` sonrasında `mode` parametresiz `/recommendations` En Avantajlı sonuç döner (test).
- Geçersiz mod `422` döner ve ayar değişmez (test).
- Varsayılan mod değişikliği `selection_mode_decisions` kayıtlarını değiştirmez (satır özeti karşılaştırma testi).

#### AŞAMA 43 — Akıllı Tahminler Arayüzü

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Kısmen | `web/static`, `scripts/ui_smoke.js` |

**Amaç:** Seçimleri gerekçeleri, belirsizlikleri ve uyarılarıyla birlikte anlaşılır biçimde göstermek.

> Durum notu: arayüz tamamlandı; arayüz testleri şu an Playwright ile çalışan duman testi
> (`scripts/ui_smoke.js`, CI'da gerçek sunucuya karşı) düzeyindedir. Ayrıntılı DOM/etkileşim testleri
> (aşağıdaki "arayüz testi" kriterleri için ayrı test paketi) henüz yazılmadı.

**Çıktılar:**
- Akıllı Tahminler sayfası, seçim kartları, uyarı metinleri ve sentetik veri etiketi.
- **v6.3 entegrasyonu:**
  - Sayfanın üstünde üç mod: En Güvenilir, En Avantajlı, Dengeli; ilk açılışta Dengeli seçili (kayıtlı tercih varsa o).
  - Kart alanları: maç, lig, başlangıç saati, market, seçenek, gerçek oran (kaynak etiketiyle), MODEL A olasılığı, MODEL B olasılığı, beklenen değer, belirsizlik seviyesi, tahmin dayanıklılığı, veri güncelliği, tahmin geçerliliği, geçmiş başarı, seçim gerekçesi, bağımsız denetim sonucu.
  - "Neden seçildi?" ve "Neden elendi?" açılır alanları.
  - Mod değişiminde yalnızca liste, sıra ve gerekçe değişir; olasılıklar aynı kalır.
  - Uygun seçim yoksa mod-özel mesaj.

**Kabul kriterleri:**
- v6.3 entegrasyonu: İlk yüklemede Dengeli sekmesi etkindir (arayüz testi).
- v6.3 entegrasyonu: Mod değiştirildiğinde API `mode` parametresiyle çağrılır; iki modda da görünen bir adayın MODEL A/B değerleri aynıdır (arayüz testi).
- v6.3 entegrasyonu: Her kart 16 alanın tamamını gösterir; En Güvenilir modunda oran yoksa "Oran yok" açıkça yazılır (test).
- İddaa dışı kaynaklı oran hiçbir zaman "İddaa" olarak etiketlenmez (test).

#### AŞAMA 44 — Maç Detayı ve Tüm Market Tarama Görünümü

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Kısmen | `web/static`, `api/` |

**Amaç:** Bir maçtaki veriyle desteklenen bütün marketleri, yalnızca seçilenleri değil, MODEL A/B olasılıkları ve kapı durumlarıyla göstermek.

**Çıktılar:**
- Mevcut: `/recommendations/rejections?fixture_id=` ile maç bazında elenen adaylar.
- Eksik: maç detay ucu (`docs/API.md`'ye eklenecek) ve görünümü.

**Kabul kriterleri:**
- Gösterilen market sayısı, o maç için taranan veriyle desteklenen market sayısına eşittir (test).
- Elenen adaylar nedenleriyle birlikte görünür; varsayılan görünüm yalnızca seçilenlere filtrelenmez (test).

#### AŞAMA 45 — Lig, Organizasyon ve Fikstür Görünümleri

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Kısmen | `web/static`, `api/` |

**Amaç:** 21 organizasyonu ve fikstürleri veri kapsamı ve güncellik bilgisiyle listelemek.

**Çıktılar:**
- Mevcut: `GET /api/v1/leagues`, `league` ve `date` sorgu parametreleri.
- Eksik: organizasyon sayfası, fikstür listesi ve kapsam göstergeleri.

**Kabul kriterleri:**
- Lig filtresi yalnızca o organizasyonun öğelerini döner (test).
- Her organizasyonda veri kapsamı durumu (yeterli / yetersiz) gösterilir (test).

#### AŞAMA 46 — Türkçe Yerelleştirme, Erişilebilirlik ve Duyarlı Tasarım

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Kısmen | `web/static` |

**Amaç:** Arayüzün Türkçe sayı/tarih biçimleri, erişilebilirlik ve mobil tarayıcı uyumuyla kullanılabilir olması.

**Çıktılar:**
- Ondalık virgül ve yüzde biçimi; API UTC, gösterim Europe/Istanbul yerel saati.
- Erişilebilirlik etiketleri, klavye gezinmesi, renk kontrastı; dar ekran düzeni.

**Kabul kriterleri:**
- Biçim testleri: 0.562 → "%56,2"; UTC zaman yerel saate doğru çevrilir (test).
- Otomatik erişilebilirlik taramasında kritik ihlal yoktur.
- 360 px genişlikte yatay kaydırma oluşmaz (arayüz testi).

#### AŞAMA 47 — Kimlik Doğrulama, Yetkilendirme ve Hız Sınırlama

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Planlandı | `api/` |

**Amaç:** `X-User-Id` yer tutucusunu gerçek kimlik doğrulamayla değiştirmek ve kullanıcı verilerini korumak.

**Çıktılar:**
- Belirteç tabanlı kimlik doğrulama; kullanıcı bazında ayarlar ve bildirim izni; istek hızı sınırı; 18 yaş onayı.

**Kabul kriterleri:**
- Kimliği doğrulanmamış istek kullanıcı uçlarında `401` döner; bir kullanıcı başka kullanıcının ayarını okuyamaz/değiştiremez (test).
- Yapılandırılmış sınırın üstündeki istekler `429` döner (test).
- Yaş onayı verilmeden öneri gösterilmez (test).

#### AŞAMA 48 — Mobil Uygulamalar (Android / iOS)

| Faz | Durum | Modül |
|---|---|---|
| Arayüz ve API | Planlandı | (yeni istemciler) |

**Amaç:** Android ve iOS istemcilerinin aynı `/api/v1` sözleşmesini kullanması.

**Çıktılar:**
- Mobil istemciler; radar bildirimleri için Aşama 61 ile entegrasyon.
- **v6.3 entegrasyonu:**
  - Mobilde de üç mod, ilk açılışta Dengeli, aynı kart alanları, "Neden seçildi?" / "Neden elendi?".

**Kabul kriterleri:**
- Mobil istemciler web ile aynı sözleşme testlerini geçer; gösterilen değerler API ile aynıdır.
- v6.3 entegrasyonu: İlk açılışta Dengeli modu seçilidir (test).

### Faz 6 — Başarı Merkezi ve Doğrulama (Aşama 49–55)

#### AŞAMA 49 — Sonuçlandırma Motoru

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Tamamlandı (temel) | `performance/` (sonuçlandırma), `domain/markets.py` |

**Amaç:** Her seçimi ve adayı market kataloğundaki kurallarla sonuçlandırmak.

**Çıktılar:**
- Katalogla aynı kuralları kullanan sonuçlandırma; birim kâr/zarar (kazandı: oran − 1, kaybetti: −1, iade: 0).
- **v6.3 entegrasyonu:**
  - `settlement_status` her mod kararı için ayrı kaydedilir; aynı aday iki modda seçildiyse iki ayrı kayıt oluşur.

**Kabul kriterleri:**
- Bilinen skorlarla market bazında tablo testleri geçer.
- Sonuçlandırma idempotenttir (test).
- Sonuçlandırılmış kayıt farklı sonuçla yeniden sonuçlandırılamaz; düzeltme yalnızca açık düzeltme kaydıyla yapılır (test).
- v6.3 entegrasyonu: Bir modun sonuçlandırması diğer modun kaydını etkilemez (test).

#### AŞAMA 50 — Walk-Forward Kronolojik Geriye Dönük Test

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Tamamlandı (temel) | `backtest/` |

**Amaç:** Modelleri, kalibrasyonu, eşikleri ve seçimi zaman içinde ileri kayan pencerelerle, geleceği görmeden test etmek.

**Çıktılar:**
- Her adımda geçmişle eğitim/kalibrasyon/eşik ayarı, sonraki pencere için tahmin ve seçim.
- Rapor: Brier, log loss, ECE, isabet ve ROI güven aralıkları.
- **v6.3 entegrasyonu:**
  - Üç mod her adımda aynı aday havuzu üzerinde çalışır; kararları ayrı kaydedilir.
  - Eşik ayar penceresi uygulama penceresinden önce biter.

**Kabul kriterleri:**
- Veri deposuna maç sonucunu gelecekten taşıyan sahte bir sütun eklendiğinde backtest metrikleri değişmez (sızıntı testi).
- Aynı tohumla backtest tekrarlanabilir (test).
- v6.3 entegrasyonu: Rapor her mod ve taban çizgisi için aynı pencere ve havuzla metrik üretir (test).

#### AŞAMA 51 — Tahmin Başarı Merkezi

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Tamamlandı (temel) | `performance/`, `api/`, `web/static` |

**Amaç:** Tahmin başarısını ve matematiksel avantajı ayrı ayrı ve şeffaf metriklerle göstermek.

**Çıktılar:**
- MODEL A, MODEL B ve bütün adaylar için genel metrikler; Başarı Merkezi sayfası.
- **v6.3 entegrasyonu:**
  - "Seçim Modları" karşılaştırma sekmesi: üç modun isabet oranı, kalibrasyonu, seçim sıklığı, ortalama oranı, beklenen değeri, gerçekleşmiş teorik getirisi, sezonlar arası dayanıklılığı.
  - Modların başarısı birbirinden bağımsız kaydedilir; geçmiş seçimler sonradan başka bir modun sonucuymuş gibi değiştirilemez.
  - Uç: `GET /api/v1/performance/selection-modes`.

**Kabul kriterleri:**
- v6.3 entegrasyonu: Sekme üç modu ve taban çizgisini gösterir; değerler API ile aynıdır (test).
- Tahmin başarısı (Brier, log loss, isabet) ve değer (EV, ROI) ayrı bölümlerde gösterilir (arayüz testi).
- Sentetik veride görünür uyarı bandı vardır; "kazanç garantisi" ifadesi bulunmaz (test).

#### AŞAMA 52 — Kırılımlar: Lig, Market, Dönem ve Güven Aralıkları

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Tamamlandı (temel) | `performance/` |

**Amaç:** Başarıyı lig, market ailesi, sezon ve 7/30/90 günlük pencerelerde güven aralıklarıyla raporlamak.

**Çıktılar:**
- `by_league`, `by_market`, `by_season`, `windows` (7d / 30d / 90d); küçük örneklemde "yetersiz örnek" işareti.
- **v6.3 entegrasyonu:**
  - Bütün kırılımlar mod bazında (`/recommendations/modes/performance?mode=`).

**Kabul kriterleri:**
- Lig bazlı n toplamı genel n'ye eşittir (test).
- Pencere sınırları belgelenmiş kurala göre hesaplanır (sınır testi).
- Her oran güven aralığıyla döner; küçük örneklem işaretlenir (test).

#### AŞAMA 53 — Taban Çizgisi ve Seçim Yanlılığı Koruması

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Tamamlandı (temel) | `performance/` (aynı havuzda mod karşılaştırması) |

**Amaç:** Yalnızca seçilmiş tahminleri göstererek başarının olduğundan yüksek sunulmasını önlemek.

**Çıktılar:**
- Taban çizgisi: "Tüm adaylar (MODEL B, seçim yok)"; `selection_rate`, `n_fixtures_without_selection`.
- **v6.3 entegrasyonu:**
  - `/recommendations/modes/comparison` üç modu aynı zaman aralığı ve aynı maç havuzunda karşılaştırır; kapsam farkı notu; `selection_mode_comparisons` kaydı.

**Kabul kriterleri:**
- v6.3 entegrasyonu: Karşılaştırmadaki maç kimlikleri üç mod ve taban çizgisi için aynıdır (test).
- v6.3 entegrasyonu: Aynı karşılaştırmada `n_candidates` modlar arasında eşittir; `n_selections` farkları gösterilir (test).
- Notlarda seçim kapsamı farkı belirtilir; isabet oranı hiçbir yerde n ve güven aralığı olmadan gösterilmez (test).

#### AŞAMA 54 — Sezonlar Arası Dayanıklılık ve Model Kayması İzleme

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Kısmen | `performance/`, `calibration/` |

**Amaç:** Modellerin ve modların performansının sezonlar ve zaman içinde kararlı olup olmadığını izlemek.

**Çıktılar:**
- Mevcut: `season_stability` (`roi_std`, `hit_rate_std`, `consistent`).
- Eksik: kayan pencerede kalibrasyon kayması (ECE) izleme; anlamlı kaymada yeniden kalibrasyon / eğitim tetikleme (Aşama 63 ile).
- **v6.3 entegrasyonu:**
  - `season_stability` mod bazında hesaplanır ve Seçim Modları sekmesinde gösterilir.

**Kabul kriterleri:**
- `consistent` kuralı belgelenmiştir ve sentetik rejim değişiminde test edilir (test).
- Sentetik veride bilerek eklenen kalibrasyon bozulması belgelenmiş pencere sayısı içinde tespit edilir (test).

#### AŞAMA 55 — Model ve Piyasa Karşılaştırma Raporları

| Faz | Durum | Modül |
|---|---|---|
| Başarı Merkezi ve Doğrulama | Kısmen | `performance/`, `backtest/` |

**Amaç:** MODEL A, MODEL B ve marjsız piyasa olasılıklarını aynı maçlarda dürüstçe karşılaştırmak.

**Çıktılar:**
- Lig ve market ailesi bazında log loss / Brier farkları ve blok bootstrap güven aralıkları.
- Eksik: gerçek veriyle düzenli rapor üretimi.

**Kabul kriterleri:**
- Üç kaynak aynı maç kümesinde karşılaştırılır (test).
- Farklar güven aralığıyla raporlanır; MODEL B piyasadan iyi değilse rapor bunu açıkça belirtir (seçici raporlama yok).

### Faz 7 — Canlı (Aşama 56–61)

#### AŞAMA 56 — Canlı Maç Veri Akışı

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Kısmen | `radar/`, `data/` |

**Amaç:** Canlı skor, dakika ve olay (gol, kırmızı kart) verisini düşük gecikmeyle almak.

**Durum (2026-11-22):** API-Football ücretsiz planı (günde 100 istek) bağlandı: `data/api_football.py`
(bütçe denetimli istemci, `api_requests` kaydı) ve `radar/window_radar.py` (Son 15 Dakika Radarı). Tek
`/fixtures?live=<ligler>` isteği o anda oynanan bütün maçları getirir; maçlar takım adı benzerliği, lig ve
başlama saatiyle yerel maçlara bağlanır (`apif_fixture_links`). Sürekli akış yoktur: istek yalnızca pencere
başlarında ve olasılık kapısından geçen maçlar için gönderilir. Kalan: kesinti/gecikme izleme, sürekli akış.

**Çıktılar:**
- Canlı sağlayıcı adaptörü; olay zamanı ve alınma zamanı; veri yaşı ölçümü.
- Kalan normal süre hesabı (uzatma devreleri hariç; uzatma dakikaları belgelenmiş kurala göre).

**Kabul kriterleri:**
- Her canlı güncellemede kaynak zamanı ve alınma zamanı bulunur; yaş hesaplanır (test).
- Yaş `live_data_max_age_seconds` değerini aşarsa radar koşulu başarısız olur (test).
- Simüle edilmiş kesintide alarm üretilmez (test).

#### AŞAMA 57 — Canlı Oran Akışı

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Kısmen | `odds/`, `radar/` |

**Amaç:** Radar için gerekli canlı oranları kaynak ve zaman bilgisiyle almak.

**Durum (2026-11-22):** API-Football `/odds/live` (ücretsiz planda erişilebilir; tek istekte bütün canlı maçlar)
Son 15 Dakika Radarı'nda kullanılır: "Over/Under (1st Half)" (49) ve "Match Goals" (25) marketlerinin
(gol + 0,5) çizgisi; güncelleme zamanı ≤ 60 sn ve askıda değilse geçerli. Oran isteği yalnızca MODEL A eşiğe
ulaştığında gönderilir. İddaa oranı değildir ve İddaa olarak gösterilmez.

**Çıktılar:**
- Canlı oran adaptörü; askıya alınan market yönetimi.

**Kabul kriterleri:**
- Oran yaşı `live_odds_max_age_seconds` değerini aşarsa radar koşulu başarısız olur (test).
- Askıdaki markette alarm üretilmez (test).
- İddaa dışı canlı oran İddaa olarak etiketlenmez (test).

#### AŞAMA 58 — Canlı MODEL A / MODEL B Kalan Süre Gol Olasılıkları

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Tamamlandı (temel) | `radar/` |

**Amaç:** "Kalan normal sürede en az bir gol" olasılığını MODEL A (yalnızca futbol verisi + canlı durum) ve MODEL B (ek olarak canlı oran) ile ayrı hesaplamak.

**Çıktılar:**
- Maç öncesi MODEL A gol beklentilerinden, kalan süre ve oyun durumuna (skor, kırmızı kart) göre ölçeklenen canlı Poisson yoğunluğu.
- MODEL B: MODEL A canlı çıktısı + marjsız canlı oran; MODEL A oran izolasyonu canlıda da geçerlidir.

**Kabul kriterleri:**
- Canlı oranlar değiştiğinde MODEL A canlı olasılığı değişmez (test).
- Aynı oyun durumunda kalan süre azaldıkça olasılık artmaz (monotonluk testi).
- MODEL B yalnızca `captured_at ≤ değerlendirme zamanı` olan oranları kullanır (test).

#### AŞAMA 59 — Canlı Gol Radar Karar Kuralları (%90)

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Tamamlandı (temel) | `radar/`, `config.py` |

**Amaç:** Radar alarmını yalnızca bütün zorunlu şartlar aynı anda sağlandığında üretmek.

**Çıktılar:**
- Zorunlu şartlar (v6.3, kullanıcı talebiyle güncel eşik):
  - MODEL A > %90 (önceki v6.2 değeri: %99)
  - MODEL B > %90 (önceki v6.2 değeri: %99)
  - Normal sürede en az 15 dakika kalması
  - Güncel canlı veriler (Aşama 56)
  - Gerekli güncel oranlar (Aşama 57)
  - Yeterli nadir olay kalibrasyonu (Aşama 25)
  - Tahmin Kalite Kapısı (Aşama 33)
  - Bildirim izni (Aşama 60)
  - Mükerrer alarm engeli (Aşama 60)
- `GET /api/v1/radar/config`, `GET /api/v1/radar/live` (koşul bazında `passed` ve `detail`, `blocked_reason`).
- Not: %90 bir ürün kararıdır (kullanıcı talimatı: "Gol radar oranını yüzde 90 yap"); kronolojik olarak belirlenen bir seçim eşiği değildir. Eşiğin düşmesiyle alarm sıklığı artacağından nadir olay kalibrasyonu ve radar başarı takibi (Aşama 25 ve 60) zorunlu kalır.
- **v6.3 entegrasyonu:**
  - Radar seçim modlarından bağımsızdır; radar ayarları `selection_mode_configs` kayıtlarından okunmaz.
  - Dengeli dahil hiçbir mod radar eşiklerini gevşetemez veya bir şartı atlayamaz.

**Kabul kriterleri:**
- `GET /radar/config` → `model_a_min_probability = 0.90`, `model_b_min_probability = 0.90`, `min_remaining_minutes = 15` ("Canlı Gol Radar'ın mevcut eşikleri (%90) korunmalı" testi).
- Kesin büyüktür karşılaştırması: MODEL A = 0,90 tam değerinde alarm yok; MODEL A ve MODEL B 0,90'ın hemen üstünde ve diğer şartlar sağlanınca alarm var (sınır testleri).
- MODEL A > %90 ama MODEL B ≤ %90 ise (veya tersi) alarm yok (test).
- Kalan normal süre 15 dakikanın altındaysa alarm yok; tam 15 dakikada diğer şartlar sağlanıyorsa alarm var (sınır testi).
- Diğer şartların her biri tek başına başarısız olduğunda alarm yok ve `blocked_reason` dolu (parametrik test).
- v6.3 entegrasyonu: Kullanıcının seçim modu veya `default_mode` ayarı değiştirildiğinde radar yapılandırması ve kararları aynı kalır (test).
- Yapılandırmada eski %99 değeri bulunmaz (gerileme testi).

#### AŞAMA 60 — Alarm Kaydı, Mükerrer Alarm Engeli, Bildirim İzni ve Radar Başarı Takibi

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Kısmen | `radar/`, `db/`, `api/` |

**Amaç:** Her alarmı değiştirilemez kaydetmek, aynı olay için tekrar alarmı engellemek, izinsiz bildirim göndermemek ve radar isabetini ölçmek.

**Çıktılar:**
- Mevcut: `radar_evaluations`, `radar_alerts`, `notification_permissions`; mükerrer alarm anahtarı (kullanıcı, maç, hedef, dönem); `POST /radar/notifications/permission`, `GET /radar/alerts`.
- Eksik: radar başarı raporu (alarm sonrası kalan normal sürede gol olup olmadığının sonuçlandırılması ve kalibrasyon raporu).

**Kabul kriterleri:**
- Aynı maç ve hedef için ikinci değerlendirme ikinci alarm üretmez (test).
- Bildirim izni yoksa alarm üretilmez ve bildirim gönderilmez; `blocked_reason` izin eksikliğini belirtir (test).
- Radar başarı raporu MODEL A ve MODEL B için ayrı: alarm sayısı, gözlenen gol frekansı ve %95 Clopper–Pearson aralığı, ortalama tahmin; ortalama tahmin aralığın dışındaysa kalibrasyon uyarısı (test).

#### AŞAMA 61 — Bildirim Teslim Altyapısı

| Faz | Durum | Modül |
|---|---|---|
| Canlı | Planlandı | (yeni bildirim servisi) |

**Amaç:** Radar alarmlarını izin veren kullanıcılara güvenilir ve zamanında iletmek.

**Çıktılar:**
- Mobil push ve web push teslimi; teslim alındı bilgisi; yinelemeye karşı korumalı yeniden deneme.

**Kabul kriterleri:**
- Yeniden denemelerde her alarm en fazla bir kez teslim edilir (tekil kimlik testi).
- Gönderim anında şartlar artık sağlanmıyorsa (ör. kalan normal süre 15 dakikanın altına düştüyse) gecikmiş bildirim gönderilmez (test).
- İzin geri alındığında teslim derhal durur (test).

### Faz 8 — Operasyon (Aşama 62–65)

#### AŞAMA 62 — Test Paketi ve Sürekli Entegrasyon

| Faz | Durum | Modül |
|---|---|---|
| Operasyon | Kısmen | `tests/` |

**Amaç:** Bütün kuralları otomatik testlerle güvenceye almak ve her değişiklikte çalıştırmak.

**Çıktılar:**
- Mevcut: birim, özellik, sözleşme ve entegrasyon testleri (ağ erişimi gerektirmez; sentetik veri ve kayıtlı örnekler).
- Eksik: CI hattı, kapsam raporu, tarayıcıda uçtan uca arayüz testleri.
- **v6.3 entegrasyonu:** v6.3 Bölüm 10'daki 12 testin her biri adlandırılmış bir testle eşlenir:
  1. Varsayılan mod Dengeli olmalı
  2. Mod değişimi MODEL A/B tahminlerini değiştirmemeli
  3. En Güvenilir modu yalnızca en yüksek ham olasılığa göre kör seçim yapmamalı
  4. En Avantajlı modu geçersiz oran kullanmamalı
  5. Dengeli mod pozitif EV olmadan seçim üretmemeli
  6. Kalibrasyon yetersizse seçim reddedilmeli
  7. MODEL A oran verisi kullanmamalı
  8. MODEL B zaman sızıntısı içermemeli
  9. Bağımsız denetim sonuçları saklanmalı
  10. Üç modun performans kayıtları ayrı tutulmalı
  11. Geçmiş seçimler geriye dönük değiştirilmemeli
  12. Canlı Gol Radar'ın mevcut eşikleri (%90) korunmalı

**Kabul kriterleri:**
- v6.3 entegrasyonu: 12 testin tamamı mevcuttur ve geçer; test adları ile madde numaraları arasındaki eşleme test dosyasında belgelidir.
- Test paketi ağ erişimi olmadan çalışır (test).
- CI her değişiklikte paketi çalıştırır; başarısız test birleştirmeyi engeller (Planlandı).

#### AŞAMA 63 — İzleme, Günlükleme ve Operasyonel Uyarılar

| Faz | Durum | Modül |
|---|---|---|
| Operasyon | Planlandı | (yeni operasyon katmanı) |

**Amaç:** Veri akışlarının, model çalıştırmalarının, API'nin ve radarın sağlığını izlemek.

**Çıktılar:**
- `run_id` içeren yapılandırılmış günlükler; metrikler (veri yaşı, iş başarısı, API gecikmesi, radar değerlendirme gecikmesi); operasyon uyarıları ve panolar.

**Kabul kriterleri:**
- Veri akışı tazelik sınırından uzun süre durduğunda yapılandırılmış süre içinde operasyon uyarısı üretilir (simülasyon testi).
- Her çalıştırmanın başlangıç, bitiş ve durumu kaydedilir; başarısız çalıştırma öneri yayımlamaz (test).

#### AŞAMA 64 — Güvenlik, Gizlilik ve Yasal Uyum

| Faz | Durum | Modül |
|---|---|---|
| Operasyon | Planlandı | (tüm sistem) |

**Amaç:** Kullanıcı verilerini korumak ve ilgili mevzuata (KVKK, yaş sınırı, şans oyunlarıyla ilgili iletişim kuralları) uyum sağlamak.

**Çıktılar:**
- KVKK aydınlatma metni, veri envanteri ve veri azaltma; gizli anahtar yönetimi; bağımlılık güvenlik taraması.
- Uyarı ve arayüz metinlerinin hukuki incelemesi; 18 yaş politikası; sorumlu oyun bilgilendirmesi.

**Kabul kriterleri:**
- Güvenlik taramasında açık yüksek/kritik bulgu yoktur.
- Kişisel veri envanteri belgelenmiş, silme talebi süreci test edilmiştir.
- Üretime geçmeden önce hukuki inceleme onayı kayıt altındadır.

#### AŞAMA 65 — Üretim Dağıtımı, Yedekleme ve Felaket Kurtarma

| Faz | Durum | Modül |
|---|---|---|
| Operasyon | Planlandı | `db/`, dağıtım yapılandırması |

**Amaç:** Sistemi üretim ortamında güvenilir biçimde çalıştırmak ve veriyi korumak.

**Çıktılar:**
- Konteynerleştirme, ortam yapılandırmaları, sürümlü yayın ve geri alma.
- Gerekirse SQLite'tan üretim veritabanına geçiş; ek-yalnız garantilerinin korunması.
- Yedekleme ve geri yükleme tatbikatları.

**Kabul kriterleri:**
- Geri yükleme tatbikatında yedek açılır ve bütünlük kontrolleri (ek-yalnız tetikleyiciler, satır sayıları, özetler) geçer.
- Veritabanı geçişinden sonra Aşama 5 testleri üretim veritabanında da geçer.
- Önceki sürüme geri alma test edilmiştir.

### Faz 9 — v6.3 Entegrasyonu (Aşama 66)

#### AŞAMA 66 — Üç Seçim Modu Entegrasyonu ve Bağımsız Performans Doğrulaması

| Faz | Durum | Modül |
|---|---|---|
| v6.3 Entegrasyonu | Kısmen | `selection/`, `performance/`, `quality/`, `db/`, `api/`, `web/static`, `backtest/`, `tests/` |

**Amaç:** v6.3 üç modlu seçim mimarisini uçtan uca tamamlamak ve üç modun performansını birbirinden bağımsız, kronolojik ve aynı maç havuzu üzerinde doğrulamak.

**Çıktılar (v6.3 Bölüm 11):**
1. **Üç mod tamamlanacak:** En Güvenilir Tahminler (`highest_probability`), En Avantajlı Oranlar (`best_value`), Dengeli Seçimler (`balanced`).
2. **Varsayılan mod Dengeli:** API, arayüz ve ayarlarda ilk değer `balanced`.
3. **Kullanıcı mod değiştirebilecek:** arayüzde mod sekmeleri, API'de `mode` parametresi, ayarlarda kalıcı varsayılan tercih.
4. **Mod bazlı başarı kayıtları:** `selection_mode_decisions`, `selection_mode_performance`, `selection_mode_calibration` kayıtları her mod için ayrı ve değiştirilemez.
5. **Kronolojik karşılaştırma:** üç mod aynı zaman aralığı ve aynı maç havuzunda; 7/30/90 gün ve sezon pencereleri; taban çizgisi; kapsam farkı notları (`selection_mode_comparisons`).
6. **Bağımsız denetim:** her mod kararı için seçim motorundan ayrı denetim ve saklanan `audit_status`.
7. **Arayüz ve API testleri:** mod sekmeleri, kart alanları, "Neden seçildi?" / "Neden elendi?", bütün v6.3 uçları için sözleşme testleri.

Mevcut durum: 1–7'nin temel uygulaması sentetik veriyle çalışıyor. Eksik: gerçek tarihsel veri üzerinde bağımsız performans doğrulama raporu, tarayıcıda uçtan uca arayüz testleri, CI.

**Kabul kriterleri:**
- **Üç mod:** Her mod için `selection_mode_configs` kaydı vardır; eşikler kronolojik doğrulamadan gelir (`source = chronological_validation`) veya durum `not_established`'tır ve o mod seçim üretmez (test).
- **Varsayılan:** Parametresiz `/recommendations`, yeni kullanıcı `GET /settings` ve ilk arayüz yüklemesi Dengeli döner (test).
- **Mod değişimi:** Mod değişimi MODEL A/B tahmin kimliklerini ve olasılıklarını değiştirmez; yalnızca uygunluk, sıralama ve gerekçe değişir (test).
- **Mod bazlı kayıtlar:** Üç modun performans kayıtları ayrı tutulur; bir modun kaydını başka moda aktarma veya geçmiş seçimi değiştirme girişimi veritabanında reddedilir (test).
- **Kronolojik karşılaştırma:** Karşılaştırmada üç mod ve taban çizgisi aynı maç kimliklerini ve aynı aday sayısını kullanır; eşik ayar penceresi uygulama penceresinden önce biter (test).
- **Metrikler:** Her mod için uygun aday sayısı, önerilen seçim sayısı, seçim yapılmayan maç sayısı, isabet oranı, Brier Score, log loss, kalibrasyon (ECE ve güvenilirlik kutuları), ortalama oran, ortalama tahmini olasılık, ortalama EV, teorik birim bahis ROI, güven aralıkları, lig ve market bazlı başarı ve son 7/30/90 gün performansı raporlanır (sözleşme testi).
- **Bağımsız performans doğrulaması:** Performans raporu seçim motoru kodu çalıştırılmadan yalnızca veritabanı kayıtları ve sonuçlandırmalardan yeniden üretilebilir; iki bağımsız üretim aynı sayıları verir (test).
- **Gerçek veri raporu:** Gerçek tarihsel veride (Aşama 7) walk-forward çalıştırmayla her mod için Brier, log loss, ECE ve ROI blok bootstrap %95 güven aralıklarıyla raporlanır. Kabul, belirli bir isabet veya ROI hedefine değil raporun eksiksiz, tekrarlanabilir ve sızıntısız olmasına dayanır; güven aralığı negatif getiriyi dışlamayan mod için rapor bunu açıkça yazar.
- **Bağımsız denetim:** Her karar için denetim sonucu saklanır; denetimden kalan seçim hiçbir modda gösterilmez (test).
- **Arayüz ve API:** v6.3 Bölüm 8'deki bütün uçlar sözleşme testlerini geçer; geçersiz mod `422`; arayüzde üç sekme, ilk açılışta Dengeli, 16 kart alanı, "Neden seçildi?" ve "Neden elendi?" çalışır (test).
- **Radar ayrımı:** Hiçbir mod Canlı Gol Radar eşiklerini (%90) veya diğer şartlarını değiştirmez (test).
- **İletişim:** Hiçbir yanıt veya ekranda garanti kazanç ifadesi yoktur; En Avantajlı ve Dengeli kartlarında "Avantaj yalnızca model tahmini doğruysa geçerlidir." uyarısı bulunur (test).
- v6.3 Bölüm 10'daki 12 testin tamamı geçer (Aşama 62).

---

## 6. Sonraki adımlar

> Durum (2026-11-22): ücretsiz gerçek veri kaynağı football-data.co.uk bağlandı (16/21 organizasyon). Bu kaynak
> İddaa oranı ve canlı maç verisi içermez; oranları yaklaşık zamanlıdır. Bu yüzden gerçek veride şu an yalnızca
> En Güvenilir modu güncel seçim üretebilir; En Avantajlı ve Dengeli için kesin zamanlı güncel oran gerekir.

Öncelik sırasıyla üzerinde çalışılacak 5 somut aşama:

1. **AŞAMA 66 — gerçek tarihsel veriyle bağımsız performans doğrulaması.** Tarihsel CSV içe aktarıcıyla (Aşama 7) en az bir organizasyonun birden çok sezonunu yükle; üç modu walk-forward backtest (Aşama 50) ile aynı havuzda çalıştır; raporu yalnızca veritabanından yeniden üreten bağımsız betiği ekle. Yaklaşık zamanlı tarihsel oranlarla elde edilen sonuçları raporda ayrıca işaretle.
2. **AŞAMA 62 — v6.3 test eşlemesi ve CI.** Bölüm 10'daki 12 testin adlarıyla eşlemesini test dosyasında belgele; ağ erişimi olmadan çalışan paketi CI'ye bağla; tarayıcıda uçtan uca arayüz testlerini (Dengeli varsayılanı, mod geçişi, kart alanları) ekle.
3. **AŞAMA 25 — radarın %90 bölgesi için nadir olay kalibrasyonu.** Eşik %90'a indiği için `radar_calibration` yeterlilik kuralını kronolojik olarak belirle ve Clopper–Pearson raporunu MODEL A ve MODEL B için ayrı üret; ardından Aşama 60'taki radar başarı raporunu tamamla.
4. **AŞAMA 9 — gerçek veri sağlayıcı entegrasyonu.** Ücretsiz kaynak (football-data.co.uk) bağlandı: 21 organizasyonun 16'sı için sonuçlar, yaklaşan maçlar ve maç öncesi oranlar (`data/football_data_feed.py`, `real-data` / `update-data` komutları). Kalan: TFF 1. Lig, Türkiye Kupası ve UEFA organizasyonları için kaynak; canlı maç verisi (radar) için kaynak; İddaa oranları için lisanslı/izinli erişim.
5. **AŞAMA 11 — gerçek oran anlık görüntüleri ve İddaa ayrımı.** Doğrulanmış oran kaynağı/kaynakları bağla, oran yaşı hizmet düzeyi hedeflerini tanımla; En Avantajlı ve Dengeli modlarının gerçek veriyle çalışması ve Aşama 40 (CLV) buna bağlıdır.

---

## Ek A — v6.2 uzlaştırma tablosu (orijinal liste gelince doldurulacak)

| v6.2 No | v6.2 başlığı | Bu plandaki No | Fark / not |
|---|---|---|---|
| — | — | — | Orijinal v6.2 listesi bekleniyor. |

## Ek B — Modül → aşama haritası

| Modül | İlgili aşamalar |
|---|---|
| `config.py` | 1, 59 |
| `leagues.py` | 3 |
| `domain/markets.py` | 4, 15, 49 |
| `db/schema.sql`, `db/database.py` | 5, 11, 21, 42, 60, 65, 66 |
| `data/synthetic.py` | 2, 8 |
| `data/api_football.py` | 56, 57 |
| `data/football_data_csv.py` | 7, 66 |
| `data/point_in_time.py` | 6, 12, 13 |
| `models/model_a.py` | 14, 15 |
| `models/model_b.py` | 17, 19 |
| `models/isolation.py` | 16, 17, 20 |
| `calibration/` | 23, 24, 25, 26, 54 |
| `quality/` | 12, 27, 28, 29, 30, 31, 33 |
| `odds/` | 11, 18, 32, 40 |
| `selection/` | 31, 34, 35, 36, 37, 38, 39, 66 |
| `performance/` | 49, 51, 52, 53, 54, 55, 66 |
| `radar/` | 25, 56, 57, 58, 59, 60 |
| `api/` | 41, 42, 44, 45, 66 |
| `web/static` | 43, 44, 45, 46, 51, 66 |
| `backtest/` | 13, 50, 55, 66 |
| `tests/` | 62, 66 |
