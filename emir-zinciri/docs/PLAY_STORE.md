# Google Play yayın hazırlığı

## Derleme
1. Anahtar oluşturun (bir kez, güvenli yerde saklayın):
   `keytool -genkeypair -v -keystore emir-zinciri/release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias emirzinciri`
2. `keystore.properties.example` → `keystore.properties` olarak kopyalayıp şifreleri yazın (bu dosya ve `.jks` depoya eklenmez).
3. `./gradlew :logic:test :core:test :android:bundleRelease` → `android/build/outputs/bundle/release/android-release.aab`
4. Play Console'da "Play App Signing" etkin bırakın; AAB'yi dahili test kanalına yükleyin.

Yayın öncesi kontrol listesi:
- [ ] En az iki gerçek cihazda (düşük ve orta segment) bölüm 1–50, fabrika, sonsuz mod denendi.
- [ ] Uygulama arka plana alınıp geri açıldığında kayıt ve canlar doğru.
- [ ] Ses/müzik ayarları kalıcı.
- [ ] R8 küçültmesi açılacaksa (`isMinifyEnabled = true`) cihazda tekrar test edildi.
- [ ] Mağaza görselleri (512×512 ikon, 1024×500 öne çıkan görsel, en az 2 dikey ekran görüntüsü — `docs/screens/`).

## Mağaza metinleri (Türkçe)

**Uygulama adı:** Emir Zinciri: Robot Komutan

**Kısa açıklama (80):** Komut kartlarını diz, robotlarını programla, fabrikanı büyüt! Çevrimdışı strateji.

**Tam açıklama:**
Komutları Sen Ver, Geleceği Robotların İnşa Etsin!

Merkezi yapay zekâ kontrolden çıktı. Eski komuta teknolojisini kullanabilen son Robot Komutan sensin. Komut kartlarını bir zincire diz, Başlat'a bas ve robotlarının planını adım adım uygulamasını izle. Yanlış giderse zincirin korunur: yalnızca hatalı kartı düzelt.

• 100 alanda 500 bölüm: hareket, kutu taşıma, kapılar, enerji, bantlar, lazerler, düşmanlar ve patronlar
• İlk 25 bölüm tek robotla; 26. bölümden itibaren aynı anda çalışan çoklu robot zincirleri
• Robotlar birbirini etkiler: biri kapıyı tutar, diğeri geçer; mesajlarla senkronize olurlar
• Uçan, görünmez, manyetik, savaş ve kalkan robotları
• Kalıcı Robot Fabrikası: binalar, üretim zinciri, araştırma ağacı ve ticaret
• Sonsuz Fabrika, Günlük Komut Bulmacası, günlük/haftalık görevler ve 100 başarım
• Tamamen çevrimdışı, hesap gerekmez, zorunlu reklam yok, uygulama içi satın alma yok

**Kategori:** Bulmaca (ikincil: Strateji) · **İçerik derecelendirmesi:** Herkes (şiddet yok; robotlar "etkisiz hâle" getirilir)

## Veri güvenliği formu
- Veri toplanmıyor ve paylaşılmıyor. Ağ izni (INTERNET) istenmiyor.
- Kayıt yalnızca cihazın uygulamaya özel dahili depolamasında tutulur (Android yedeklemesi açık: `allowBackup=true`).

## Gizlilik politikası (yayınlanacak metin)
Emir Zinciri: Robot Komutan kişisel veri toplamaz, işlemez veya üçüncü taraflarla paylaşmaz. Oyun ilerlemesi yalnızca cihazınızda saklanır ve internet bağlantısı kullanılmaz. Uygulamayı kaldırdığınızda bu veriler silinir.
