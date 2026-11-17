# Güvenlik ve Gizlilik Özeti

## Yetki modeli (katmanlar)
| Katman | Görev |
|---|---|
| Firebase Auth (telefon OTP) | Kimlik. SMS kodu Firebase'de üretilir/doğrulanır; uygulama kod görmez. |
| App Check (Play Integrity / debug) | Yalnızca orijinal uygulamadan gelen çağrılar; callable'larda `enforceAppCheck: true` (emülatör hariç). |
| Cloud Functions | Tüm hassas yazımlar: randevu durumu, ücret, sahiplik, üyelik, puan, finans, takvim etkisi. Her callable: kimlik, sahiplik/üyelik denetimi, girdi doğrulama, gerekirse idempotency ve hız limiti, denetim kaydı. |
| Firestore kuralları | İstemci yazımı yalnızca: kendi ad/soyadı, cihaz token'ı, bildirim "okundu", kendi hizmetleri (alan/sınır doğrulamalı). Okumalar en az yetki ilkesiyle (`firestore.rules`). |
| Storage kuralları | İstemci yalnızca `uploads/{uid}/pending` altına ≤5 MB görsel yükler; kalıcı salon görsellerini yalnızca sunucu yazar. |

## Hız limitleri
| İşlem | Limit |
|---|---|
| SMS OTP | Firebase Auth'un yerleşik kötüye kullanım koruması + App Check; Console → Authentication → Settings → SMS bölge politikası: yalnızca TR önerilir. İstemci 60 sn yeniden gönderme bekletmesi. |
| Randevu oluşturma | 10 / saat / kullanıcı |
| Salon kaydı | 5 / saat / kullanıcı |
| Personel daveti | 30 / gün / salon |

## Veri tutarlılığı garantileri (testlerle doğrulanır)
- Aynı uzmana çakışan iki randevu oluşamaz (uzman×gün kilidi transaction'ı).
- Müşteri başına tek aktif randevu (müşteri kilidi transaction'ı).
- Tamamlama yalnızca bir kez finansa yazılır (`completion_{id}` create).
- Randevu başına tek değerlendirme (`reviews/{id}` create), ortalamalar atomik.
- Zamanlanmış işler (süre aşımı, hatırlatma, "Müşteri geldi mi?") bayrakla tek sefer.

## KVKK / veri minimizasyonu
- Müşteri telefonu yalnızca kendi profili ve ilgili randevu belgesinde (müşteri, atanmış uzman, salon sahibi okuyabilir); kamuya açık hiçbir koleksiyonda yok.
- Personel kişisel telefonu yalnızca davet belgesinde (sahip + davetli).
- Değerlendirmelerde yazar "Ad S."; müşteri kimliği ayrı, istemciye kapalı `reviewsPrivate` koleksiyonunda.
- Bildirim içerikleri kişisel veri içermez; kilit ekranında genel metin gösterilir.
- Yedekleme: `android:allowBackup=false`, veri çıkarma kuralları oturum/tercih verisini dışarıda tutar.
- Hesap silme: uygulama içinden (Hesap → Hesabımı Sil). Kişisel veriler silinir/anonimleştirilir; mali kayıtlar kişisel veri olmadan korunur.

## Bilinen açık noktalar (yayın öncesi karar gerektirir)
- Yorum moderasyonu (hakaret/spam bildirme ve kaldırma) için yönetici aracı yok.
- Salon sahibinin hesap silmesi ve salon devri destek süreciyle yapılır.
- Aynı kişinin farklı salonlarda aynı saate randevusu engellenmez (uzman kilidi salon bazlı).
- Yasal metinler TASLAK; hukuki inceleme ve veri sorumlusu bilgisi gerekli. Yurt dışı aktarım dayanağı netleştirilmeli.
