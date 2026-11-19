# Mimari

## Katmanlar

```
lwjgl3 / android  →  core (libGDX: ekranlar, çizim, PlaySession)  →  logic (saf Kotlin)
```

`logic` hiçbir grafik kütüphanesine bağlı değildir; simülasyon, bölüm doğrulama, kayıt ve can kuralları burada test edilir.

## Simülasyon motoru (`logic/sim/Simulation.kt`)

Sabit mantıksal adımlarla ilerler; animasyon hızı sonucu değiştirmez (`PlaySessionTest.animationSpeedDoesNotChangeResult`).

Bir adımın sırası:

1. Zamanlı düğme sayaçları bir azalır.
2. Robotlar **kimlik sırasıyla** sıradaki kartı alır. Akış kartları (Tekrar, Önü boşsa/kapalıysa, Blok sonu) adım harcamaz; yalnızca akış kartından oluşan sonsuz döngüler 1000 akış adımında güvenle kesilir.
3. Enerji açık bölümlerde maliyet kontrol edilir (yetmezse robot durur).
4. Dönüşler uygulanır, hareket talepleri toplanır.
5. Hareket çakışmaları: statik engeller (duvar, panel, kapalı kapı, kutu) → aynı hücreye yönelenlerin hepsi durur → yer değiştirmeye izin yok → duran robotun hücresine giriş engellenir (sabit noktaya kadar; ardışık robotlar birlikte ilerleyebilir).
6. Hareket/itme/çekme uygulanır; boşluğa giren robot kaybolur, çukura itilen kutu çukuru doldurur.
7. Eylemler (al, bırak, bas, topla, aktar, mesaj gönder) hareket sonrası duruma göre robot sırasıyla.
8. Mesajlar teslim edilir (bir sonraki adımda alınır); plaka, anahtar, zamanlayıcı ve soketlerden kanal durumu, oradan kapılar hesaplanır. Üzerinde robot/kutu olan kapı kapanmaz.
9. Kazanma (bütün hedefler) / kaybetme (robot kaybı, enerji, program bitti, kilitlenme, süre) kontrolü.

Her adım `SimEvent` listesi üretir; çizici, sesler, açılır yazılar ve hata ayıklama satırı bunları kullanır. Her adımdan önce dünyanın kopyası saklanır: **geri adım** bu kopyalardan döner.

## Komut zinciri

Mobilde tek satırlı düzenleme için bloklar düz listede tutulur: `REPEAT:3 FORWARD TURN_LEFT END`. `Program.compile` eşleşmeyi doğrular ve hatalı kartın indeksini döndürür.

## Bölüm biçimi (`assets/levels/area_NNN.json`)

Her alan dosyası 5 bölüm içerir. Harita karakterleri:

| Karakter | Anlam | Karakter | Anlam |
|---|---|---|---|
| `.` | zemin | `#` | duvar |
| ` ` | boşluk (düşülür) | `o` | çukur (kutuyla dolar) |
| `E` | çıkış | `x` | kutu hedefi |
| `B` / `X` | kutu / hedefte kutu | `c` | enerji hücresi |
| `p` | basınç plakası | `s` | anahtar paneli |
| `t` | zamanlı düğme | `D` / `d` | kapı / ters kapı |
| `Q` | şarj istasyonu | `K` | enerji soketi |

Diğer alanlar: `robots`, `commands` (açık kartlar), `maxCommands` (kart sınırı), `par` (2. yıldız), `third` (3. yıldız kuralı), `objectives`, `energy`, `channels` (`"x,y": kanal`), `values` (hücre miktarı / zamanlayıcı süresi / soket ihtiyacı), `tutorial`, `story`, `solution` (referans çözüm).

İstenen veri alanlarıyla eşleme: levelId=`id`, areaId=`area`, mapWidth/Height=`map` boyutu, tiles/obstacles/interactiveObjects=`map`+`boxes`+`cells`+`channels`, availableCommands=`commands`, commandLimit=`maxCommands`, energyLimit=robot `energy`/`maxEnergy`, objectives/winConditions=`objectives`, starConditions=`par`+`third`, rewards=`Progression.firstClearReward`, tutorialStep=`tutorial`, difficulty=`difficulty`.

## Bölüm doğrulama

`LevelValidationTest` her bölüm için: kimlik/alan tutarlılığı, robot sayısı kuralı (1–25 tek robot), robotların zeminde başlaması, referans çözümün yalnızca açık kartlarla **kazanıp 3 yıldız alması**, determinizm, boş zincirle kazanılamaması ve döngüsüz tek robot bölümlerinde `par` değerinin **BFS çözücünün bulduğu kesin optimum** olması.

Tasarım akışı: harita taslağı → `levelTool` çözer → `par` ve referans çözüm yazılır → testler doğrular. Çözücü tasarım sırasında iki çözümsüz bölümü ve iki istenmeyen kestirmeyi yakaladı.

## Kayıt

`SaveData` (kotlinx.serialization, `version` alanı). `SaveManager`: atomik yazma (geçici dosya + `ATOMIC_MOVE`, `fsync`), her yazmadan önce sağlam dosyanın yedeği, bozuk ana dosyada yedekten kurtarma, ikisi de bozuksa bozuk dosyayı saklayıp yeni profil, sürüm yükseltme zinciri, bilinmeyen alanları yok sayma. Uygulama arka plana alınınca (`pause`) kaydedilir.

## Can sistemi ve saat koruması

Bkz. `logic/progress/Lives.kt`. 4 can, eksik başına 15 dk, sırayla dolar, doluyken sayaç durur.

- Aynı cihaz açılışı içinde geçen süre **yalnızca monotonik saatten** (Android `SystemClock.elapsedRealtime`) ölçülür: saati ileri almak can doldurmaz, geri almak can kaybettirmez.
- Açılış değişmişse (`Settings.Global.BOOT_COUNT`) duvar saati farkı kullanılır; negatif fark 0 sayılır.
- **Sınır:** tamamen çevrimdışı oyunda "saati ileri al + cihazı yeniden başlat" tespit edilemez; uygulama verisini düzenlemek de engellenemez. Meşru oyuncunun canı hiçbir senaryoda kalıcı kilitlenmez (süre yalnızca ileriye birikir).
- Can yalnızca **başarısız sonuç gösterildiğinde** düşer. Planlama, düzenleme, öğretici ve sonuç gelmeden sıfırlama ücretsizdir. Alan 1 (öğretici) ve tamamlanmış bölümlerin tekrarı can harcamaz.
