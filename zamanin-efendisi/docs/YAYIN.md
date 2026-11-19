# Google Play Yayın Hazırlığı

## Derleme

```bash
cd zamanin-efendisi
cp keystore.properties.example keystore.properties   # doldurun; depoya eklemeyin
keytool -genkeypair -v -keystore zaman-yayin.jks -alias zaman -keyalg RSA -keysize 4096 -validity 10000
./gradlew :logic:test :android:bundleRelease   # android/build/outputs/bundle/release/android-release.aab
```

`keystore.properties` yoksa release derlemesi imzasız üretilir (Play'e yüklenemez). Play App Signing kullanılması önerilir.

## Teknik kontrol listesi

| Madde | Durum |
|---|---|
| targetSdk / compileSdk 35, minSdk 23 | Tamam |
| 64-bit yerel kütüphaneler (arm64-v8a, x86_64) + armeabi-v7a, x86 | Tamam (libGDX ve FreeType) |
| R8 küçültme ve libGDX/kotlinx.serialization ProGuard kuralları | Tamam (release derlemesi geçiyor) |
| Uyarlanabilir simge (adaptive icon) ve eski sürümler için katmanlı simge | Tamam |
| Dikey mod, tam ekran, çentik (display cutout) güvenli alanları | Tamam |
| Çevrimdışı çalışma, internet izni yok | Tamam (yalnızca VIBRATE izni) |
| Android otomatik yedekleme ile kayıt koruma (`allowBackup`) | Tamam |
| Gerçek cihaz/emülatör testi | **Yapılmadı**: bu ortamda KVM ve cihaz yok. Masaüstü (LWJGL3, Mesa yazılım GL) üzerinde 1, 10, 30, 35, 100, 500. bölümler, sonsuz mod, mücadeleler, kale ve müze ekranlarıyla duman testi yapıldı |
| Mağaza ekran görüntüleri (en az 2, 9:16) | Masaüstü başlatıcısıyla alınabilir: `-Dzaman.start=game:7:4 -Dzaman.w=1080 -Dzaman.h=1920` |

## Veri güvenliği formu (taslak)

- Uygulama hiçbir kişisel veri toplamaz veya paylaşmaz; ağ bağlantısı kullanmaz.
- Oyun ilerlemesi yalnızca cihazın uygulamaya özel depolamasında tutulur (Android yedeklemesine dahil olabilir).
- Reklam, analiz ya da üçüncü taraf SDK'sı yoktur. Uygulama içi satın alma yoktur (market yalnızca oyun içi parayla çalışır).

## İçerik derecelendirmesi notları

Şiddet: hafif, çizgi film tarzı (soyut figürler, kan yok). Kumar, sohbet, kullanıcı içeriği, konum yok.

## Mağaza metinleri

**Kısa açıklama (80 karakter):** Geçmişi değiştir, bugünü yönet, geleceği fethet: üç çağlık zaman stratejisi.

**Uzun açıklama:**

Zamanın Efendisi: Üç Çağ, aynı haritanın Geçmiş, Şimdi ve Gelecek hâlleri arasında geçiş yaptığın özgün bir strateji
bulmaca oyunudur. Geçmiş'te kurduğun ahşap köprü Şimdi'de taş köprü, Gelecek'te ışık geçidi olur; diktiğin fidan bir
gün kadim bir ormana dönüşür ve düşman ordularının yolunu keser. Her hamlenin sonucu iki çağ boyunca yankılanır.

• 100 alan, 500 bölüm; her 5 bölümde bir alan patronu, her 50 bölümde dünya finali
• Gerçek nedensellik: Geçmiş'teki her değişiklik ileri çağlarda kurallarla hesaplanır, paradokslar ve kelebek etkileri
• Zaman enerjisi, zaman dondurma, kristaller, mühürler, savunma kuleleri ve 9 farklı birlik
• 6 farklı düşman stratejisi: saldırgan, savunmacı, yağmacı, zaman hırsızı, paradoks ajanı, teknoloji uzmanı
• Zaman Kalesi: 15 bina, araştırma ağacı, çevrimdışı üretim ve kalenin görsel olarak büyümesi
• Sonsuz Zaman Modu, Günlük Bulmaca, Haftalık Kriz, görevler, seferler, 150 eserlik koleksiyon ve müze
• Tamamen çevrimdışı, reklamsız; para harcamadan sonuna kadar oynanır
• Türkçe, aydınlık ve renkli izometrik grafikler
