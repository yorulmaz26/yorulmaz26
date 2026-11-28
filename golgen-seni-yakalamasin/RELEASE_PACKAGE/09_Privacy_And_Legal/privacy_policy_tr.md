# Gizlilik Politikası — Gölgen Seni Yakalamasın

> **Yürürlük tarihi:** [YÜRÜRLÜK TARİHİ]
> **Uygulama:** Gölgen Seni Yakalamasın (geçici İngilizce marka adı: "Doppelglow" — marka tescil araştırması devam ediyor)
> **Paket adı:** com.golgen.oyun — **Sürüm:** 1.1.0
> **Geliştirici:** Magic Lumos Software
> **İletişim:** [İLETİŞİM E-POSTASI]

> **Yayın notu (geliştirici için, yayımlarken bu kutuyu kaldırın):** Google Play, reklam SDK'sı içeren uygulamalar için bu politikanın **herkese açık, giriş gerektirmeyen, PDF olmayan bir web adresinde (URL)** yayımlanmasını ve bu adresin Play Console'a ("Uygulama içeriği > Gizlilik politikası") ve mağaza kaydına girilmesini şart koşar. Bu metin hukuki danışmanlık değildir; yayımlamadan önce bir hukuk uzmanına inceletilmesi önerilir.

## 1. Özet

Gölgen Seni Yakalamasın, tek parmakla oynanan, çevrimdışı çalışabilen bir arcade oyunudur. Oyunda **hesap, giriş, sohbet, kullanıcı tarafından oluşturulan içerik, konum izni veya uygulama içi satın alma yoktur.** Oyunun kendi kodu hiçbir kişisel veriyi toplamaz, sunucularımıza göndermez ve satmaz. Oyun ücretsizdir ve reklamlarla desteklenir; reklamlar Google'ın reklam hizmetleri (Google Mobile Ads SDK / AdMob) tarafından gösterilir ve bu hizmetler aşağıda açıklanan verileri işleyebilir.

## 2. Toplamadığımız veriler

Magic Lumos Software, oyun aracılığıyla şunları **toplamaz**:

- Ad, e-posta, telefon numarası veya herhangi bir hesap bilgisi
- Hassas (GPS) konum bilgisi — oyun konum izni istemez
- Kişiler, fotoğraflar, dosyalar, mikrofon veya kamera verileri
- Mesajlar, sohbet içerikleri veya kullanıcı içerikleri
- Ödeme veya finansal bilgiler

Oyunun kendi kodu internet üzerinden hiçbir veri göndermez. İnternet bağlantısı yalnızca reklam SDK'sı tarafından kullanılır.

## 3. Cihazda yerel olarak saklanan veriler

Oyun ilerlemeniz (ör. en yüksek skor, ayarlar, ses/titreşim tercihleri, günlük ödüllü reklam sayacı) **yalnızca cihazınızda**, Android'in SharedPreferences mekanizmasıyla saklanır. Bu veriler bize gönderilmez.

Uygulamada Android Otomatik Yedekleme (`allowBackup=true`) açıktır. Bu nedenle, **cihazınızın yedekleme ayarlarına bağlı olarak** bu yerel oyun verileri, Android'in kendi yedekleme hizmeti tarafından Google hesabınıza yedeklenebilir. Bu yedekleme Google tarafından, sizin cihaz ayarlarınız doğrultusunda yapılır; Magic Lumos Software bu yedeklere erişemez. Yedeklemeyi Android ayarlarından (Ayarlar > Sistem/Google > Yedekleme) kapatabilirsiniz.

## 4. Reklamlar ve Google Mobile Ads SDK

Oyun, reklam göstermek için **Google Mobile Ads SDK (AdMob)** kullanır:

- **Banner reklamlar:** yalnızca menü ekranlarında,
- **Geçiş (interstitial) reklamları:** yalnızca oyun turları arasında, sıklık sınırlamasıyla,
- **Ödüllü video reklamlar:** tamamen isteğe bağlı; yalnızca siz seçerseniz (ör. +1 can) gösterilir.

Oynanış sırasında reklam gösterilmez.

Google Mobile Ads SDK, reklamları göstermek, reklam performansını ölçmek, analiz yapmak ve dolandırıcılığı/kötüye kullanımı önlemek amacıyla aşağıdaki verileri **toplayabilir ve Google'a iletebilir**:

- **Cihaz veya diğer tanımlayıcılar:** Android reklam kimliği (Advertising ID), uygulama grubu kimliği (App Set ID) ve benzeri tanımlayıcılar
- **IP adresi:** yaklaşık konumun (ülke/şehir düzeyinde) tahmin edilmesi için kullanılabilir
- **Uygulama etkileşimleri:** reklam gösterimleri, reklama tıklamalar, uygulama açılışları gibi
- **Teşhis ve performans verileri:** çökme kayıtları, SDK performans ve hata bilgileri
- Cihaz modeli, işletim sistemi sürümü, dil, ağ türü gibi teknik bilgiler

Bu verilerin Google tarafından nasıl kullanıldığı hakkında ayrıntılı bilgi için:

- Google'ın, iş ortaklarının site ve uygulamalarını kullandığınızda verileri nasıl kullandığı: <https://policies.google.com/technologies/partner-sites>
- Google Gizlilik Politikası: <https://policies.google.com/privacy>

Uygulamanın talep ettiği ilgili izinler: `INTERNET`, `ACCESS_NETWORK_STATE` (reklam yüklemek için), `com.google.android.gms.permission.AD_ID` (reklam kimliği), `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS` (Android Privacy Sandbox reklam API'leri), `WAKE_LOCK` ve `FOREGROUND_SERVICE` (reklam SDK'sının içindeki WorkManager kütüphanesi tarafından arka plan görevleri için eklenir). Oyunun kendisi ayrıca `VIBRATE` izni ile titreşim geri bildirimi kullanır.

## 5. Onay (rıza) yönetimi — Google UMP

Avrupa Ekonomik Alanı (AEA), Birleşik Krallık ve İsviçre'deki kullanıcılar için oyun, **Google User Messaging Platform (UMP)** aracılığıyla bir onay formu gösterir. Bu form üzerinden kişiselleştirilmiş reklamlara ve ilgili veri işlemeye onay verebilir, reddedebilir veya tercihlerinizi yönetebilirsiniz. Onay vermezseniz Google, yürürlükteki kurallara uygun olarak kişiselleştirilmemiş veya sınırlı reklamlar gösterebilir. Tercihlerinizi daha sonra oyun içindeki gizlilik seçenekleri üzerinden (mevcutsa) veya uygulama verilerini temizleyerek yeniden belirleyebilirsiniz.

## 6. Reklam kimliğinizi sıfırlama veya silme

Android cihazınızda reklam kimliğinizi kontrol edebilirsiniz:

- **Android 12 ve üzeri:** Ayarlar > Gizlilik > Reklamlar (veya Ayarlar > Google > Reklamlar) > "Reklam kimliğini sil". Silindiğinde uygulamalara tanımlayıcı yerine sıfırlardan oluşan bir değer verilir.
- **Eski sürümler:** Ayarlar > Google > Reklamlar > "Reklam kimliğini sıfırla" ve/veya "İlgi alanına dayalı reklamları devre dışı bırak".

Menü adları cihaz üreticisine göre farklılık gösterebilir. Ayrıca reklam kişiselleştirme tercihlerinizi <https://myadcenter.google.com> adresinden yönetebilirsiniz.

## 7. Çocukların gizliliği

Oyun **13 yaş altındaki çocuklara yönelik değildir**; önerilen hedef kitle 13 yaş ve üzeridir. 13 yaş altındaki çocuklardan bilerek kişisel veri toplamıyoruz. Bir çocuğun oyunu kullanarak reklam SDK'sı üzerinden veri paylaştığını düşünüyorsanız lütfen [İLETİŞİM E-POSTASI] adresinden bizimle iletişime geçin.

## 8. Veri saklama süresi

- **Yerel oyun verileri:** uygulamayı kaldırana veya uygulama verilerini temizleyene kadar cihazınızda kalır (varsa Android yedeğiniz, Google'ın yedekleme kurallarına tabidir).
- **Reklam SDK'sı verileri:** Google tarafından, Google'ın kendi saklama politikalarına göre saklanır. Magic Lumos Software bu verileri saklamaz.

## 9. Verilerin silinmesi

Sunucularımızda size ait veri bulunmadığından, yerel verilerinizi şu yollarla silebilirsiniz:

- Ayarlar > Uygulamalar > Gölgen Seni Yakalamasın > Depolama > "Verileri temizle", veya
- Uygulamayı kaldırmak.

Google'ın işlediği reklam verileri için reklam kimliğinizi silebilir/sıfırlayabilir (bkz. Bölüm 6) ve Google'ın gizlilik araçlarını kullanabilirsiniz.

## 10. Güvenlik

Oyunun kendisi veri iletmez. Reklam SDK'sının Google'a gönderdiği veriler, Google'ın beyanına göre aktarım sırasında şifrelenir (HTTPS/TLS). Cihazdaki yerel veriler Android'in uygulama korumalı alanı (sandbox) içinde saklanır. Hiçbir sistem %100 güvenli değildir; ancak makul önlemleri almaya özen gösteriyoruz.

## 11. Haklarınız — KVKK ve GDPR

**6698 sayılı Kişisel Verilerin Korunması Kanunu (KVKK)** kapsamında ve AEA/Birleşik Krallık kullanıcıları için **Genel Veri Koruma Tüzüğü (GDPR)** kapsamında şu haklara sahipsiniz:

- Kişisel verilerinizin işlenip işlenmediğini öğrenme ve bilgi talep etme,
- Verilerinize erişme, düzeltilmesini veya silinmesini isteme,
- İşlemenin kısıtlanmasını isteme ve işlemeye itiraz etme,
- Onayınızı istediğiniz zaman geri çekme (UMP formu veya cihaz ayarları üzerinden),
- Veri taşınabilirliği (GDPR),
- Yetkili denetim makamına şikâyette bulunma (Türkiye'de Kişisel Verileri Koruma Kurumu; AEA'da ilgili veri koruma otoritesi).

Kişiselleştirilmiş reklamlar için hukuki dayanak, ilgili bölgelerde UMP üzerinden alınan açık rızanızdır. Reklam SDK'sı tarafından toplanan verilere ilişkin talepler için Google'ın gizlilik araçlarını da kullanabilirsiniz. Magic Lumos Software'e yönelik talepler için: [İLETİŞİM E-POSTASI].

## 12. Değişiklikler

Bu politikayı zaman zaman güncelleyebiliriz. Güncel sürüm her zaman aynı web adresinde yayımlanır ve sayfanın başındaki yürürlük tarihi güncellenir. Önemli değişikliklerde makul ölçüde bilgilendirme yapılır.

## 13. İletişim

Magic Lumos Software
E-posta: [İLETİŞİM E-POSTASI]

---
*Yayımlamadan önce hukuki inceleme yapılması önerilir.*
