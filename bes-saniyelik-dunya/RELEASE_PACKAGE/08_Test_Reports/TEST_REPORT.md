# Test raporu — sürüm 1.1.0 (28 Kasım 2026)

## Otomatik testler: 99/99 geçti (`./gradlew :core:test`)
| Test sınıfı | Test | Kapsam |
|---|---|---|
| LivesTest | 16 | 20 dk yenilenme (0→1→2→3), sayaç durması, sayacın yeni harcamada sıfırlanmaması, oyun kapalıyken süre, saati ileri/geri alma, saat dilimi değişimi, cihazın yeniden başlatılması, zorla kapatma ve 30 kez kapat-aç, eksi/sınır üstü can, elle değiştirilmiş kayıt (imza), eski sürüm kaydının taşınması, güncelleme sonrası koruma, reklamla +1 can ve günlük sınır, elmasla doldurma, ilk bölümlerin ücretsizliği ve iade kuralları, süre biçimi |
| LocalizationTest | 9 | 6 dil dosyası, eksik/fazla anahtar (0), yer tutucular, koddaki her anahtarın varlığı, içerik metinleri (kural, harita, alan, karakter, başarı, görev), 500 bölüm adı (Türkçe adlar kayıtlı veriyle birebir aynı), bölüm ipuçlarının çevrilebilmesi, dil algılama, tekil/çoğul ve ondalık ayırıcı, yazı tipinde olmayan karakter (0), arayüz kodunda sabit Türkçe metin (0) |
| AdsTest | 12 | reklam sıklığı, ödüllerin bir kez verilmesi, günlük sınırlar, eski kayıt |
| SimulationTest / LogicFixesTest / LevelsTest / ProgressTest / AuditTest | 19 / 16 / 11 / 11 / 5 | oyun mantığı, 500 bölümün çözülebilirliği, ilerleme, kayıt |

## Masaüstü uçtan uca denemeler (gerçek oyun kodu, masaüstü arka ucu)
| Deneme | Sonuç |
|---|---|
| 6 dilde mağaza senaryosu: 3 bölüm oynatma, 12 menü ekranı, bölüm sonu, Sonsuz Dünya | 6/6 dil hatasız; metin taşması göze çarpmadı (düğmeler sığmayan yazıyı %60'a kadar küçültür) |
| Video kaydı (TR, EN): 7 bölüm baştan sona + 5 ekran | 7/7 bölüm 3 yıldızla tamamlandı |
| Performans ve bellek: 12 bölüm art arda (`performance_desktop.txt`) | ort. kare 16,7 ms (60 fps), p95 ≤ 20,6 ms; bellek 4 → 8 MB'ta sabit, aynı bölümler tekrar oynanınca artış yok (sızıntı bulunmadı); ilk kare 1,2 sn |
| Tablet çözünürlükleri 1920×1200 ve 2560×1600 | arayüz bozulmadan ölçeklendi |

## Paket denetimi (`release_check_output.txt`): 274/274
Görsel ölçüleri ve saydamlık, 8 videonun süre/kodek/fps değerleri, ZIP boyutları, çeviri ve mağaza metni sınırları, pakette şifre/anahtar olmaması.

## Android paketi
`aapt2`: com.besaniyelik.dunya, sürüm 1.1.0 (kod 2), minSdk 24, targetSdk 36; Türkçe cihazda ad "5 Saniyelik Dünya", diğerlerinde "Stopwatch Shuffle"; 4 ABI; `zipalign -c -P 16` geçti.

## Yapılamayanlar
- Gerçek telefon/emülatör testi: bu ortamda donanım hızlandırma (KVM) ve cihaz yok. Batarya, ısınma, düşük donanımlı cihaz ve gerçek reklam davranışı ölçülemedi.
- Masaüstü kare süreleri yazılımsal OpenGL ve dikey eşitlemeyle ölçüldü; telefon performansını göstermez, yalnızca bellek ve kare hesap süresinin kararlılığını gösterir.
