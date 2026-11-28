# Google Play Data Safety Formu — TASLAK

**Uygulama:** Gölgen Seni Yakalamasın / Doppelglow (geçici) — `com.golgen.oyun` — v1.1.0
**Geliştirici:** Magic Lumos Software

> **ÖNEMLİ — UYARI:** Google'ın resmi AdMob veri beyanı sayfası
> <https://developers.google.com/admob/android/privacy/play-data-disclosure>
> bu paketin hazırlandığı derleme ortamından **ERİŞİLEMEDİ**. Bu nedenle aşağıdaki **Google Mobile Ads SDK satırlarının tamamı "MANUEL KONTROL GEREKLİ"** olarak işaretlenmiştir. Play Console'a girmeden önce, kullanılan SDK sürümüne göre bu sayfadaki güncel tablo ile **tek tek doğrulanmalıdır**. Buradaki satırlar beklenen (tipik) değerlerdir, kesin değildir.

> Uygulamanın kendi kodu hiçbir veri toplamaz veya göndermez. Aşağıdaki tüm "toplanır" satırları yalnızca dahil edilen Google Mobile Ads SDK (AdMob) ve User Messaging Platform (UMP) kaynaklıdır. Play kuralları gereği, üçüncü taraf SDK'ların topladığı veriler de beyan edilmelidir.

## 1. Data collection and security (Genel sorular)

| Field (EN) | Önerilen cevap | Not |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Yalnızca reklam SDK'sı nedeniyle |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Google SDK'ları HTTPS/TLS kullanır — MANUEL KONTROL GEREKLİ |
| Do you provide a way for users to request that their data is deleted? | **Hayır / açıklama** — aşağıya bakın | Bkz. Bölüm 4 |
| Privacy policy URL | `[GİZLİLİK POLİTİKASI URL'si]` | Herkese açık URL zorunlu |

## 2. Data types — Google Mobile Ads SDK (beklenen satırlar)

Tüm satırlar: **MANUEL KONTROL GEREKLİ** (resmi sayfa ile doğrulayın).

| Data type category | Data type | Collected | Shared | Processed ephemerally | Required or optional | Purposes | Durum |
|---|---|---|---|---|---|---|---|
| Location | **Approximate location** (IP adresinden) | Yes | Yes (Google'a)* | No | Required** | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | MANUEL KONTROL GEREKLİ |
| App activity | **App interactions** | Yes | Yes* | No | Required** | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | MANUEL KONTROL GEREKLİ |
| App info and performance | **Crash logs** | Yes | Yes* | No | Required** | Analytics; Fraud prevention, security, and compliance | MANUEL KONTROL GEREKLİ |
| App info and performance | **Diagnostics** | Yes | Yes* | No | Required** | Analytics; Advertising or marketing; Fraud prevention, security, and compliance | MANUEL KONTROL GEREKLİ |
| Device or other IDs | **Device or other IDs** (Advertising ID, App Set ID) | Yes | Yes* | No | Required** | Advertising or marketing; Analytics; Fraud prevention, security, and compliance | MANUEL KONTROL GEREKLİ |

\* **Shared** yorumu: Google'ın Play tanımına göre, veriyi uygulama adına işleyen bir "hizmet sağlayıcıya" aktarmak "paylaşım" sayılmayabilir. AdMob'un bu bağlamda nasıl sınıflandırılması gerektiğini resmi sayfadan kontrol edin. Emin olunamıyorsa "Yes" seçmek daha temkinlidir.
\** Kullanıcı reklam SDK'sının veri toplamasını uygulama içinde kapatamaz (EEA/UK'de UMP onayı kişiselleştirmeyi etkiler, ancak toplama tamamen durmayabilir). MANUEL KONTROL GEREKLİ.

## 3. Toplanmayan veri türleri (beyan: Not collected)

| Category | Cevap |
|---|---|
| Personal info (name, email, user IDs, address, phone, etc.) | Not collected |
| Financial info | Not collected (uygulama içi satın alma yok) |
| Health and fitness | Not collected |
| Messages | Not collected (sohbet yok) |
| Photos and videos | Not collected |
| Audio files | Not collected |
| Files and docs | Not collected |
| Calendar | Not collected |
| Contacts | Not collected |
| Location — Precise location | Not collected (konum izni yok) |
| App activity — Other user-generated content, In-app search history, Installed apps, Other actions | Not collected — MANUEL KONTROL GEREKLİ |
| Web browsing | Not collected |

**Yerel ilerleme verisi (SharedPreferences):** Cihazdan çıkmadığı ve geliştiriciye iletilmediği için Play tanımında "toplanan veri" sayılmaz. Android Otomatik Yedekleme (`allowBackup=true`) kullanıcının cihaz ayarlarına bağlı olarak Google tarafından yapılır; uygulama bu veriyi geliştiriciye göndermez.

## 4. Data deletion (Veri silme)

- Geliştirici (Magic Lumos Software) hiçbir sunucuda kullanıcı verisi tutmaz; hesap sistemi yoktur. Bu nedenle "Account deletion" gerekliliği uygulanmaz (hesap oluşturulamaz).
- Yerel veriler: Ayarlar > Uygulamalar > Gölgen Seni Yakalamasın > Depolama > "Verileri temizle" veya uygulamayı kaldırma ile silinir.
- Reklam SDK'sı verileri Google tarafından işlenir; kullanıcı reklam kimliğini silebilir/sıfırlayabilir (Ayarlar > Gizlilik > Reklamlar) ve <https://myadcenter.google.com> üzerinden tercihlerini yönetebilir.
- "Do you provide a way for users to request that their data is deleted?" sorusu için: geliştiricide silinecek veri olmadığını ve yukarıdaki yöntemleri gizlilik politikasında açıklayın; cevabı Play Console yardım metnine göre seçin — MANUEL KONTROL GEREKLİ.

## 5. Diğer Play Console beyanları (ilgili)

| Field | Cevap |
|---|---|
| Advertising ID declaration — Does your app use advertising ID? | **Yes** — Purpose: Advertising or marketing (+ Analytics, Fraud prevention — MANUEL KONTROL GEREKLİ) |
| Contains ads | **Yes** |
| Target audience | 13+ (bkz. `content_rating_and_audience.md`) |
| Independent security review | No (isteğe bağlı) |

## 6. Kontrol listesi

- [ ] Resmi AdMob veri beyanı sayfası açılıp tüm SDK satırları doğrulandı
- [ ] Kullanılan `play-services-ads` ve UMP sürümleri kontrol edildi
- [ ] Gizlilik politikası herkese açık URL'de yayımlandı ve forma girildi
- [ ] Form içeriği gizlilik politikası ile tutarlı

---
*Bu taslak hukuki danışmanlık değildir; göndermeden önce hukuki inceleme önerilir.*
