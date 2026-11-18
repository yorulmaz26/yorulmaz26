# Hayallerim Cepte — Mimari

> Hayal Et, Planla, Gerçekleştir.
> Android ve iOS için **%100 çevrimdışı** kişisel yaşam planlayıcısı (Flutter).

## 1. İlkeler

| İlke | Uygulama |
|---|---|
| Tamamen çevrimdışı | Ağ isteği yapan hiçbir paket yok. Android yayın manifestinde `INTERNET` izni yok. Firebase, reklam, analitik, hesap yok. |
| Veri cihazda kalır | SQLite (Drift) + uygulamanın özel klasörü. Android otomatik bulut yedeği kapalı (`allowBackup=false`, `data_extraction_rules.xml`). |
| Güvenilir para hesabı | Tutarlar **tam sayı kuruş** (`int`) olarak saklanır; ayrıştırma ve manuel kur `Decimal` ile yapılır. |
| Hassas veriye gerçek koruma | Arayüz kilidinin ötesinde alan düzeyinde AES-256-GCM şifreleme; anahtar Keychain/Keystore'da. |
| Sahte veri/akış yok | Her ekran veritabanına bağlı; tüm butonlar çalışır. |

## 2. Katmanlar

```
┌──────────────────────────────────────────────────────────────┐
│ features/*  (ekranlar, ConsumerWidget, bottom sheet'ler)      │  Sunum
├──────────────────────────────────────────────────────────────┤
│ data/providers.dart  (StreamProvider / Provider birleşimleri) │  Durum (Riverpod 3)
├──────────────────────────────────────────────────────────────┤
│ data/*_repository.dart  (iş kuralları + Drift sorguları)      │  Veri
├──────────────────────────────────────────────────────────────┤
│ core/logic  (saf, test edilebilir: ilerleme, seri, planlayıcı)│  Alan mantığı
│ core/services (şifreleme, medya, bildirim, kilit, yedekleme)  │  Altyapı
│ core/database (Drift tabloları, göçler)                       │
└──────────────────────────────────────────────────────────────┘
```

* **Riverpod 3**: Çekirdek servisler `main.dart` içinde oluşturulup `overrideWithValue` ile verilir; testlerde bellek içi sürümler kullanılır.
* **Reaktif arayüz**: Depolar Drift `watch()` akışları döndürür; veri değişince ilgili ekranlar kendiliğinden güncellenir.
* **GoRouter 18**: `StatefulShellRoute.indexedStack` ile 5 sekmeli alt menü; her sekme kendi gezinme yığınını korur.

## 3. Klasör yapısı

```
hayallerim_cepte/
├─ lib/
│  ├─ main.dart                 Başlatma: DB, anahtar deposu, ayarlar, kilit, bildirim senkronu
│  ├─ app.dart                  MaterialApp.router, tema, yazı ölçeği, kilit katmanı, yaşam döngüsü
│  ├─ core/
│  │  ├─ database/              tables.dart (21 tablo), app_database.dart (+ .g.dart üretilen)
│  │  ├─ logic/                 progress_logic, smart_planner, achievements_catalog
│  │  ├─ services/              crypto, media, notification, reminder_logic, lock, backup
│  │  ├─ settings/              settings_controller (AppSettings tablosu)
│  │  ├─ router/                app_router (rotalar), shell (alt menü + rozet kutlaması)
│  │  ├─ theme/                 app_colors, app_spacing, app_theme (Material 3 açık/koyu)
│  │  ├─ utils/                 money, dates, quotes, app_icons
│  │  └─ widgets/               common, logo, audio, capture
│  ├─ data/                     Depolar (repository) + Riverpod sağlayıcıları
│  └─ features/                 splash, onboarding, home, dreams, board, goals, habits,
│                               savings, journal, capsule, garden, achievements, focus,
│                               balance, planner, stats, profile, settings, security, backup
├─ test/                        Birim, veritabanı entegrasyonu ve widget testleri
├─ integration_test/            Gerçek cihazda uçtan uca akış
├─ tool/                        İkon üretimi, tasarım ekran görüntüleri
└─ docs/                        Bu belge, yayın kontrol listesi
```

## 4. Ekran haritası

```
Splash ─► Tanıtım (3 sayfa) ─► İlk kurulum (isim, görsel, tema, motivasyon, kategoriler)
                                   │
   ┌───────────────┬───────────────┼────────────────┬────────────────┐
 Ana Sayfa      Hayallerim     Hedeflerim        Hayal Bahçem       Profil
   │               │               │                 │                │
   ├ Hızlı ekle    ├ Kart/Liste/   ├ Görevler        ├ Ağaç ayrıntısı ├ Başarılarım (rozet, geçmiş)
   ├ Kart düzenle  │  Pano/Zaman   │  (liste/takvim) │  → Hayal       ├ İstatistikler
   ├ Kısayollar ───┤  çizelgesi    ├ Hedefler        └ Başarılarım    ├ Hayal Panom (kolaj, dışa aktar)
   │  (tüm         ├ Hayal detayı  │  → Hedef detayı                  ├ Alışkanlıklar
   │   modüller)   │  ├ Yolculuk   ├ Gecikenler                       ├ Hayal Kumbarası → Kumbara detayı
   │               │  ├ Görseller  ├ Alışkanlıklar                    ├ Hayal Günlüğüm → Editör
   │               │  ├ Bağlı      └ Akıllı Planlayıcı                ├ Gelecekteki Ben → Yeni kapsül
   │               │  │ hedefler                                      ├ Odaklanma
   │               │  └ Kumbara                                       ├ Yaşam Dengem
   │               ├ Yeni/Düzenle                                     ├ Ayarlar → Önemli günler
   │               ├ Kategoriler                                      ├ Güvenlik (PIN, biyometrik)
   │               └ Arşiv                                            └ Yedekleme
```

Her modüle Ana Sayfa'daki "Hızlı erişim" kartından **tek dokunuşla**, Profil'den iki dokunuşla ulaşılır.

## 5. Veri modeli

| Model (Drift sınıfı) | Tablo | Önemli alanlar |
|---|---|---|
| UserProfile | user_profiles | tek satır (id=1), ad, avatar, odak kategorileri |
| AppSetting | app_settings | key/value (tema, vurgu, bildirim, kilit, ana sayfa düzeni…) |
| DreamCategory | dream_categories | ad, ikon anahtarı, renk, bitki türü, varsayılan mı |
| Dream | dreams | başlık, açıklama*, kapak, kategori, hedef tarih, öncelik, notlar*, motivasyon, bütçe (kuruş), para birimi, ağırlıklı ilerleme, tamamlandı, gizli, **archivedAt** |
| DreamMilestone | dream_milestones | yolculuk adımı: açıklama, hedef tarih, tamamlandı, ağırlık, fotoğraf, not, hatırlatıcı, sıra |
| DreamMediaItem | dream_media | hayal görseli ve/veya pano öğesi (`image`/`quote`), pano kategorisi, sıra |
| Goal | goals | dönem (günlük/haftalık/aylık/yıllık), hayal, öncelik, bitiş, tamamlandı |
| TaskItem | tasks | hedef, hayal, tarih+saat, öncelik, tekrar kuralı, hatırlatıcı |
| Habit / HabitCompletion | habits / habit_completions | gün maskesi, hatırlatma saati, meydan okuma (7/21/30/100); (habit, gün) benzersiz |
| SavingsGoal / SavingsTransaction | savings_goals / savings_transactions | hedef, aylık hedef (kuruş); işlem türü deposit/withdraw/income/expense, orijinal tutar + manuel kur |
| JournalEntry / JournalMediaItem | journal_entries / journal_media | ruh hâli 1-5, etiketler, önemli, özel (gövde*); medya hem günlüğe hem kapsüle bağlanabilir |
| TimeCapsule | time_capsules | mektup*, hayal listesi*, hedefler*, açılış tarihi |
| GardenTree | garden_trees | hayal (benzersiz), bitki türü, aşama 0–6, yuva |
| Achievement | achievements | rozet kodu (benzersiz), açılma zamanı |
| FocusSession | focus_sessions | başlangıç/bitiş, planlanan/gerçek süre, tür, hedef/hayal bağlantısı |
| LifeBalanceEntry | life_balance_entries | 8 alan (1–10), not |
| Reminder | reminders | tüm yerel bildirimlerin kaynağı: tür, ref, zaman, tekrar, gün maskesi |
| ActivityLogEntry | activity_log | başarı geçmişi + puan defteri |

`*` işaretli alanlar `enc:v1:` önekiyle şifreli saklanır (gizli hayal / özel günlük / her kapsül).

### İlişkiler ve silme davranışları

```
dream_categories 1─* dreams                      RESTRICT  (kategori silinirken hayaller "Diğer"e taşınır)
dreams 1─* dream_milestones                      CASCADE
dreams 1─* dream_media                           CASCADE
dreams 1─1 garden_trees                          CASCADE
dreams 1─* goals / tasks / habits / savings_goals / focus_sessions   SET NULL (kayıtlar korunur)
goals  1─* tasks                                 CASCADE
goals  1─* focus_sessions                        SET NULL
habits 1─* habit_completions                     CASCADE
savings_goals 1─* savings_transactions           CASCADE (genel gelir/gider: savings_goal_id NULL)
journal_entries 1─* journal_media                CASCADE
time_capsules   1─* journal_media                CASCADE
reminders.ref_id → (kind'a göre) task/habit/milestone/savings/capsule/dream/goal  (uygulama katmanı)
```

**Yanlışlıkla kaybı önleme:** Hayaller önce *arşivlenir* (`archivedAt`), arşivden geri alınabilir. Kalıcı silmede adımlar, görseller ve ağaç silinir; hedef, görev, alışkanlık, kumbara ve odak kayıtları **korunur** (bağlantıları kaldırılır). Medya dosyaları da diskten temizlenir.

**İndeksler:** `dreams(category_id)`, `dreams(archived_at, is_completed)`, `dream_milestones(dream_id, sort_order)`, `dream_media(dream_id)`, `dream_media(show_on_board, sort_order)`, `goals(dream_id)`, `tasks(is_completed, due_date)`, `tasks(goal_id)`, `habit_completions(day)` + benzersiz `(habit_id, day)`, `savings_transactions(savings_goal_id, date)`, `journal_entries(date)`, `focus_sessions(started_at)`, `reminders(kind, ref_id)`, `activity_log(created_at)`.

**Göçler:** `AppDatabase.schemaVersion` = 1. Şema değiştiğinde sürüm artırılır ve `onUpgrade` içine `if (from < N)` adımı eklenir. `beforeOpen` her açılışta `PRAGMA foreign_keys = ON` uygular. Yedekler şema sürümünü taşır; eski yedekler yeni şemaya (eksik sütunlar varsayılanla) yüklenebilir, daha yeni şemalı yedek reddedilir.

**Medya yolları** veritabanında *göreli* (`media/<uuid>.jpg`) tutulur; iOS'ta uygulama kapsayıcısının mutlak yolu güncellemelerde değiştiği için zorunludur.

## 6. Tasarım sistemi

| Belirteç | Değer |
|---|---|
| Gece laciverti | `#101827` (koyu tema yüzeyleri, gece gradyanı, logo) |
| Premium mor | `#6D5DFB` (varsayılan vurgu) |
| Turkuaz | `#20B8A6` (ikincil) |
| Altın | `#D6AD60` (üçüncül, başarı/kumbara) |
| Açık arka plan / kart | `#F7F8FC` / `#FFFFFF` |
| Vurgu seçenekleri | Mor, Turkuaz, Altın, Gül, Gökyüzü, Gün Batımı |
| Boşluk ölçeği | 4 · 8 · 12 · 16 · 24 · 32 (`Gap`) |
| Köşe yarıçapı | 10 · 16 · 22 · 28 (`Radii`) |
| Bileşenler | `GradientCard`, `SoftCard`, `ProgressRing`, `LinearBar`, `StatTile`, `SectionHeader`, `EmptyState`, `PriorityChip`, `LocalImage`, `Avatar` |

* Material 3 (`ColorScheme.fromSeed`), açık/koyu/sistem tema.
* iOS'ta Cupertino sayfa geçişleri; Android'de `FadeForwards`.
* Erişilebilirlik: sistem yazı boyutu × uygulama içi ölçek (0.8–2.0 sınırlı), `Semantics` etiketleri (ilerleme halkaları, alışkanlık günleri, PIN tuşları, bahçe ağaçları), "hareketi azalt" ayarında bahçe animasyonu durur.
* Hayal Bahçem ve başarı ekranları renkli ve oyunlaştırılmış; diğer ekranlar sade/premium.
* Logo ve uygulama ikonu vektörel çizilir (`core/widgets/logo.dart`; ikon PNG'leri `tool/` ile üretilir).

## 7. Önemli alt sistemler

### Güvenlik
* **PIN**: 4–8 hane; PBKDF2-HMAC-SHA256 (60.000 tekrar, 16 bayt rastgele tuz) özeti güvenli depoda. 5 hatalı denemeden sonra 30 sn'den başlayıp katlanan bekleme. Sabit zamanlı karşılaştırma.
* **Biyometrik**: `local_auth` (yalnızca biyometri); PIN her zaman yedek yöntemdir.
* **Uygulama kilidi**: açılışta ve arka plandan seçilen süreden sonra dönüşte kilit katmanı.
* **Gizli içerik oturumu**: gizli hayaller/özel günlükler PIN ile açılır; uygulama 5 sn'den uzun arka planda kalınca oturum kapanır.
* **Alan şifreleme**: AES-256-GCM, her şifrelemede yeni nonce; veri anahtarı ilk açılışta üretilir, Keychain (`first_unlock_this_device`, iCloud'a senkronize olmaz) / Android Keystore'da tutulur.
* **Bilinen sınırlar (bilinçli):** fotoğraf/ses dosyaları uygulama sanal alanında şifresiz (işletim sistemi korumasıyla) durur; SQLite dosyasının tamamı şifreli değildir (yalnızca hassas alanlar). Tam veritabanı şifrelemesi (SQLCipher) yol haritasındadır.

### Yedekleme dosya biçimi (`.hcyedek`)
```
"HCBACKUP" | sürüm (1B) | PBKDF2 tekrar (4B, BE) | tuz (16B) | AES-256-GCM( nonce | ZIP | MAC )
ZIP: manifest.json  (şema sürümü, tarih, SHA-256: data.json + her medya dosyası, kayıt sayıları, veri anahtarı)
     data.json      (tüm tablolar, ham SQL satırları)
     media/…        (fotoğraflar ve ses kayıtları)
```
* Yedek öncesi `PRAGMA integrity_check` ve `foreign_key_check`.
* Geri yüklemede: parola/MAC doğrulaması → tüm SHA-256 özetleri → şema sürümü kontrolü → kullanıcıya özet → **Üzerine yaz** veya **Birleştir**.
* Tüm veritabanı değişikliği tek işlemde; hata olursa hiçbir şey değişmez. Medya önce geçici klasöre yazılır, işlem başarılı olunca yerine taşınır.
* Birleştirmede kimlikler yeniden numaralandırılır, yabancı anahtarlar eşlenir, aynı adlı kategoriler eşleştirilir, yerel profil/ayarlar korunur.
* Şifreli alanlar yedeğin veri anahtarıyla çözülüp yerel anahtarla yeniden şifrelenir (farklı cihaza geri yükleme çalışır).

### Bildirimler
* Tüm hatırlatıcılar `reminders` tablosundan türetilir; açılışta ve her değişiklikte yeniden planlanır (`ReminderRepository.resync`).
* iOS 64 bekleyen bildirim sınırı için en yakın 60 tanesi planlanır; tekrarlılar `matchDateTimeComponents` ile tek kayıt tutar.
* Android: `inexactAllowWhileIdle` — kesin alarm izni istenmez (Play politikası); Doze'da birkaç dakika gecikebilir. Yeniden başlatma alıcısı tanımlı.
* Odak zamanlayıcısı sayaçla değil **bitiş zamanıyla** hesaplanır ve bitişe bildirim planlanır → arka planda/kapatılmışken de doğru.

### Oyunlaştırma
* Puan defteri (`activity_log`): görev 10, adım 25, hedef 30, hayal 100, alışkanlık 5, birikim 5, odak 10, günlük 5, denge 10 + rozet puanları. Geri alınan işlemin puanı silinir.
* Seviye: her seviye öncekinden 50 puan fazla ister (100, 150, 200…).
* 15 rozet (`achievements_catalog.dart`), her işlemden sonra değerlendirilir; açılınca kutlama gösterilir.
* Bahçe: ilerleme %0 tohum → <%20 filiz → <%40 fidan → <%60 genç → <%80 büyük → ≥%80 çiçek açan → gerçekleşince meyve veren. Bitki türü kategoriye göre (8 tür), tamamen `CustomPainter` ile 2D.

### Akıllı Planlayıcı
Yapay zekâ değildir; Türkçe cümledeki süre (`6 ayda`, `8 haftada`, `1 yılda`) ve miktarı (`30 kitap`, `50 bin TL`) düzenli ifadelerle yakalar, aylık/haftalık/günlük hedefi hesaplar. Kullanıcı rakamları düzenleyip hayal + aylık hedefler + günlük görev + kumbara oluşturabilir.

## 8. Aşama durumu

| Aşama | Durum |
|---|---|
| 1 Mimari, klasörler, bağımlılıklar, tema | ✅ |
| 2 Splash, tanıtım, profil, navigasyon | ✅ |
| 3 Ana sayfa, hayal oluşturma/listeleme/detay | ✅ |
| 4 Hedefler, görevler, alışkanlıklar, takvim | ✅ |
| 5 Kumbara, günlük, zaman kapsülü | ✅ |
| 6 Bahçe, oyunlaştırma, başarılar | ✅ |
| 7 Odaklanma, yaşam dengesi, istatistikler | ✅ |
| 8 Güvenlik, bildirimler, yedekleme | ✅ |
| 9 Testler, performans, yayın hazırlığı | ✅ kod tarafı · ⏳ gerçek cihaz testleri ve mağaza varlıkları (bkz. RELEASE_CHECKLIST.md) |

## 9. Yol haritası / bilinen sınırlar
* Tam veritabanı şifrelemesi (SQLCipher) ve medya dosyalarının şifrelenmesi.
* Çok büyük yedeklerde (yüzlerce MB medya) bellek kullanımı: yedek şu an bellekte oluşturulur; akış tabanlı ZIP'e geçilebilir.
* Flutter 3.47+ ile gelen `material_ui` / `cupertino_ui` paketlerine geçiş (şu an `package:flutter/material.dart` kullanılıyor; uyarı `analysis_options.yaml`'da bilinçli olarak kapatıldı).
* Ana ekran widget'ları (Android App Widget / iOS WidgetKit).
* Çoklu dil (şu an yalnızca Türkçe).
