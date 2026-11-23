# Reklam rehberi — 5 Saniyelik Dünya

Durum tarihi: 21 Kasım 2026. Oyun Google AdMob (Google Mobile Ads 25.5.0) ile reklam gösterir. Paket şu anda Google'ın **deneme** kimlikleriyle derlenir: yalnızca "Test Ad" etiketli reklam çıkar, gelir oluşmaz. Gelir için aşağıdaki adımlar sizde.

## 1. Oyunda reklam nerede çıkar

Amaç: oyuncu reklamı kendisi istesin; istemediği reklamı çok seyrek görsün.

**Ödüllü reklamlar (her zaman isteğe bağlı, düğmede ödül yazar):**

| Yer | Düğme | Ödül | Sınır |
|---|---|---|---|
| Canlar bitince | Reklam izle: +2 can | Kaldığın kontrol noktasından 2 canla devam | Deneme başına 1 |
| Bölüm sonu | Reklam izle: +N altın | O bölümde toplanan altın kadar ek altın | Bölüm başına 1 |
| Mağaza | Reklam izle: +3 elmas | 3 elmas | Günde 5 |
| Günlük ödül | Reklam izle: ödülü tekrar al | O günün ödülü bir kez daha (ödül düğmenin üstünde yazar) | Günde 1 |
| Sonsuz Dünya | Reklam izle: devam et | Koşuya kaldığın yerden devam | Koşu başına 1 |

Reklam yüklenmemişse (çevrimdışı, reklam yok) düğmeler hiç görünmez; reklam yarıda kapatılırsa ödül verilmez.

**Geçiş reklamı (oyuncunun istemeden gördüğü tek tam ekran reklam):**

* Yalnızca bir bölüm **tamamlanınca**, kutlama bittikten sonra ve sonuç ekranı açılmadan önce.
* En erken 10 bölüm tamamlandıktan sonra; en çok 3 bölümde bir; iki reklam arasında en az 3 dakika; oturumun ilk 2 dakikasında yok; oyuncu ödüllü reklam izlediyse 3 dakika boyunca yok.
* Oyun sırasında, açılışta, çıkışta, başarısız denemeden sonra, bir düğmeye basılınca ve Sonsuz Dünya'da **asla** gösterilmez.

**Afiş (banner) reklam:**

* Yalnızca içeriği kaydırılan beş menü ekranının alt kenarında: Haritalar, Mağaza, Görevler, Başarılar, Koleksiyon. Afiş görünürken ekranın içeriği afişin üstünde biter ve arada küçük bir boşluk kalır. İçeriği afişle birlikte sığmayan bir ekranda afiş kendiliğinden kapanır.
* Afiş bir ekranın ortasında birden belirmez: yalnızca ekran açılırken, o anda yüklüyse gösterilir (oyuncu listeye dokunurken altına reklam gelmesin). Bu yüzden oturumun ilk afişli ekranında çoğu zaman görünmez, sonrakilerde görünür.
* **Oyun sırasında hiç gösterilmez.** Oyun ekranın her yerine dokunularak oynandığı için oyun içi afişe yanlışlıkla basılır; bu hem oyuncuyu rahatsız eder hem de AdMob'un "yanlışlıkla tıklama" kuralını çiğner. Ana menü, bölüm seçimi, ayarlar gibi altı düğmelerle dolu ekranlarda da yoktur.
* Bu ekranlarda oyuncu az vakit geçirdiği için afişten beklenen gelir düşüktür; asıl gelir ödüllü reklamlardan gelir.
* Kapatmak için `ads.properties` içinde `banners=off` yazın.

Açılış reklamı yoktur. Sınırlar `core/.../ads/Ads.kt` içindeki sabitlerdir (`AdDirector`); değiştirmek tek satırdır. Geçiş reklamlarını tümüyle kapatmak için `ads.properties` içinde `interstitials=off` yazmanız yeterlidir; ödüllü reklamlar etkilenmez.

Bölüm sonucu (yıldızlar, altın, açılan bölüm) reklamdan önce kaydedilir; reklam sırasında uygulama kapansa da ilerleme kaybolmaz.

## 2. Sizin yapmanız gerekenler

### 2.1 AdMob hesabı ve kimlikler

1. admob.google.com üzerinden hesap açın (18 yaş, kendi adınız ve posta adresiniz kimlikteki gibi). AdMob bir ödeme (AdSense) hesabına bağlanır.
2. Uygulamalar > Uygulama ekle > Android > "henüz mağazada değil" > ad: 5 Saniyelik Dünya.
3. Reklam birimleri: bir **Ödüllü**, bir **Geçiş** ve bir **Banner** birimi oluşturun.
4. Proje kökündeki `ads.properties` dosyasına yazın:

```properties
admobAppId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
rewardedUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
interstitialUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
bannerUnitId=ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN
audience=child
```

5. `./gradlew :android:bundleRelease` ile yeniden derleyin. Deneme kimlikleri kaldıysa derleme "UYARI: ... yalnızca DENEME reklamı gösterir" yazar.

### 2.2 Hedef kitle kararı (`audience`)

Play Console'daki "Hedef kitle" beyanınızla aynı olmalı.

| Değer | Ne zaman | Sonuç |
|---|---|---|
| `child` (varsayılan) | Kitlede 13 yaş altı var ya da emin değilsiniz | Reklamlar çocuklara uygun (G), kişiselleştirilmez, reklam kimliği izni pakette yok, onay formu gösterilmez. Gelir daha düşüktür |
| `general` | Yalnızca 13 yaş ve üstü | Kişiselleştirilmiş reklam (onay gereken ülkelerde onayla), içerik sınırı PG, reklam kimliği izni pakette |

Çizgi film tarzı bir oyunu yalnızca 13+ diye beyan etmek, Google'ın "çocuklara hitap ediyor" değerlendirmesiyle reddedilme riski taşır. Emin değilseniz `child` kalsın.

Kitlede çocuk varsa Google Play, reklam kitaplığının "Aileler için kendi kendini onaylamış" sürümler listesinde olmasını ister. Eldeki kayıtlara göre Google AdMob 19.0.0 ve sonrası listede; kullanılan 25.5.0 bunun üstünde. Yüklemeden önce Play Console yardımındaki güncel listeye bakın. Aileler Politikası ayrıca çocuklara gösterilen reklamların 5 saniye içinde kapatılabilmesini ister; bunu reklamı sunan Google sağlar, ama bu gerekçeyle ret alırsanız `interstitials=off` ile geçiş reklamlarını kapatabilirsiniz.

### 2.3 Kendi reklamınıza tıklamayın

Gerçek kimliklerle derlenmiş paketi kendi telefonunuzda denemeden önce telefonu AdMob > Ayarlar > Test cihazları bölümüne ekleyin (ya da kimliğini `ads.properties` içindeki `testDeviceIds` satırına yazın). Kendi canlı reklamınıza tıklamak, yakınlarınıza tıklatmak ya da oyunculardan tıklamalarını istemek "geçersiz trafik" sayılır; hesap askıya alınabilir ve kazanç geri alınabilir.

### 2.4 app-ads.txt (yeni uygulamalar için zorunlu)

Bir geliştirici web siteniz olmalı. Sitenin kökünde `app-ads.txt` dosyası ve içinde şu satır bulunmalı (yayıncı kimliğiniz AdMob > Ayarlar'da):

```
google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
```

Aynı alan adını Play Console mağaza sayfasındaki "Web sitesi" alanına yazın. Bu doğrulanmadan reklam gösterimi sınırlı kalır. Gizlilik politikası sayfası da aynı sitede durabilir.

### 2.5 Onay mesajları (AdMob > Gizlilik ve mesajlaşma)

Oyun Avrupa'da da yayımlanacaksa "Avrupa düzenlemeleri" mesajını, ABD'de yayımlanacaksa "ABD eyalet düzenlemeleri" mesajını oluşturup yayımlayın. Mesaj oluşturulmazsa uygulama engellenmez ama o ülkelerde onay alınmadan reklam istenmiş olur. Uygulama tarafı hazır: her açılışta onay durumu sorulur, gerekirse form gösterilir, Ayarlar ekranında "Reklam Tercihleri" düğmesi çıkar. `audience=child` iken form gösterilmez.

Türkiye'deki oyunculara Google bir onay formu göstermez. KVKK açısından açık rıza gerekip gerekmediği hukuki bir sorudur; bir danışmana sorun.

### 2.6 Yayından sonra

* Uygulama Play'de yayına girince AdMob'da uygulamayı mağaza kaydına bağlayın; inceleme birkaç gün sürer. O zamana kadar gerçek reklamlar az gelir ya da hiç gelmez.
* Ödeme: adres doğrulaması (posta ile PIN), kimlik doğrulaması, ödeme yöntemi (Türkiye'de banka havalesi). Eşik tutarlarını AdMob > Ödemeler ekranından okuyun.
* İsteğe bağlı: AdMob'da geçiş reklamı birimine "3 dakikada 1 gösterim" sıklık sınırı koyun (koddaki kuralın yedeği). Uygulama düzeyinde sınır koymayın; ödüllü reklamları da kısar.

### 2.7 Play Console'da değişenler

| Form | Yeni yanıt |
|---|---|
| Reklamlar | Evet, reklam içeriyor |
| Veri güvenliği | Toplanıyor ve paylaşılıyor: yaklaşık konum (IP'den), uygulama etkileşimleri, tanılama, cihaz ya da diğer kimlikler; amaç: reklam, analiz, dolandırıcılık önleme. Kaynak: Google Mobile Ads SDK'nın veri bildirimi sayfası; formu doldururken o sayfayı esas alın |
| Reklam kimliği | `audience=child`: Hayır. `audience=general`: Evet, reklam amacıyla |
| Gizlilik politikası | `docs/play_store/gizlilik_politikasi.md` (reklam bölümüyle güncellendi) |

## 3. Teknik notlar

* Bütün reklam kodu `android/.../AndroidAds.kt` içindedir; oyun yalnızca `AdService` arayüzünü bilir.
* Google, Temmuz 2026'da yeni nesil reklam kitaplığını (ads-mobile-sdk) duyurdu. Kullanılan 25.x sürümü 30 Haziran 2027'de "kullanımdan kalkıyor" (reklam sürer, teknik destek biter), 30 Haziran 2028'de sonlanıyor (reklam gelmeyebilir, yayın engellenir). O tarihten önce geçiş yapılmalı; değişiklik tek dosyadır.
* En düşük Android sürümü 5.0'dan 7.0'a (API 24) çıktı: reklam kitaplığı bunu istiyor.
* Kotlin 2.1'den 2.2'ye yükseltildi (reklam kitaplığı gerektiriyor).
* Masaüstünde deneme: `./gradlew :desktop:run -Dads=fake -DtourAds=1 -Dshots=<klasör>` sahte reklamla bütün akışları oynatır.

## 4. Doğrulananlar ve doğrulanamayanlar

* Doğrulandı: reklam kuralları (12 birim testi), masaüstünde sahte reklamla bütün akışlar (5 ödüllü yerleşim, bölüm sonu geçiş reklamı, afişin menüde açılıp oyun ekranında kapanması), Android paketinin gerçek kitaplıkla derlenmesi (R8 küçültmesi dahil), paketteki izinler, 16 KB hizası.
* **Bağımsız kod incelemesi yapıldı:** ilk deneme kullanım sınırına takılmıştı; ikinci denemede iki inceleyici ve bir doğrulayıcı reklam kodunun tamamını inceledi. Bulunan 19 sorunun hepsi düzeltildi. Başlıcaları: reklam açılırken ekrandaki öbür düğmelere basılabiliyordu (artık ekran kilitleniyor); mağazadaki elmas kartı her izlemeden sonra kayboluyordu; Sonsuz Dünya'da "reklam izle" sorusu açıkken uygulama kapanırsa koşunun sonucu kayboluyordu; afiş bir kez yüklenemezse bir daha denenmiyordu; afiş geç yüklenince ekran içeriği altında kalabilirdi; afiş geri tuşunu oyundan çalabilirdi; `ads.properties` içine yanlış yazılmış bir kimlik uygulamayı açılışta çökertirdi (artık derleme sırasında Türkçe hata verir); cihaz saatini geri alarak günlük reklam sınırı sıfırlanabiliyordu.
* **Bilinen sınır:** ödüllü reklam açıkken oyuncu ana ekrana çıkıp oyuna uygulama simgesinden dönerse reklam kapanır ve ödül verilmez (son uygulamalar listesinden dönerse sorun olmaz). Düzeltmesi uygulamanın açılış ayarını değiştirmeyi gerektiriyor; bu ayar gerçek cihazda denenmeden değiştirilmedi.
* **Doğrulanamadı: gerçek bir telefonda reklamın yüklenmesi, gösterilmesi, kapanması ve ödülün verilmesi.** Bu ortamda cihaz yok. İlk denemeyi deneme kimlikleriyle, dahili test kanalında yapın: her ödüllü düğme, reklamı yarıda kapatma, reklam sırasında ana ekrana çıkıp dönme, uçak modu; afişin beş menü ekranında içeriğin üstüne binmediği, çentikli ekranda ortalandığı ve afiş görünürken geri tuşunun çalıştığı.
* Politika bilgileri Google'ın yardım sayfalarının arama sonuçlarındaki alıntılarına dayanır; sayfaların kendisi bu ortamdan açılamadı. Reklam kitaplığının arayüzü ise indirilen kitaplıktan ve Google'ın resmi örnek kodundan doğrudan okundu.
* Gelir tahmini verilemez: Google bir rakam yayımlamıyor; ülkeye, mevsime ve oyuncu sayısına göre değişir. İlk haftalarda gelir düşük ve dalgalı olur.
