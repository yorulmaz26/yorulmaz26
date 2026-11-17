# Play Console — Veri Güvenliği Beyanı (taslak)

> Hukuki inceleme ile birlikte doldurun. Uygulama reklam SDK'sı ve analitik SDK'sı içermez. Çökme raporları için Firebase Crashlytics kullanılır (yalnızca yayın sürümü).

| Veri türü | Toplanıyor | Paylaşılıyor | Amaç | Zorunlu mu | Not |
|---|---|---|---|---|---|
| Ad, soyad | Evet | Evet — randevu alınan salonla | Hesap yönetimi, uygulama işlevi | Evet | Değerlendirmede "Ad S." olarak kamuya açık |
| Telefon numarası | Evet | Evet — randevu alınan salonla | Hesap (SMS doğrulama), uygulama işlevi | Evet | Kamuya açık değil |
| Konum (yaklaşık/kesin) | Evet (salon sahibi: salon konumu; müşteri: "Yakınımdakiler" araması, isteğe bağlı) | Salon konumu kamuya açık | Uygulama işlevi | Hayır | Müşterinin konumu yalnızca arama isteğinde sunucuya gönderilir, saklanmaz |
| Kilitlenme günlükleri / tanılama | Evet (Crashlytics, yalnızca yayın sürümü) | Hayır | Uygulama kararlılığı | Hayır | Kişisel veri içermez; kullanıcı kimliği eklenmez |
| Fotoğraflar | Evet (salon logo/galeri) | Evet — kamuya açık salon görseli | Uygulama işlevi | Logo zorunlu (salon sahibi) | |
| Uygulama içi etkinlik (randevular, değerlendirmeler) | Evet | Evet — ilgili salonla; değerlendirmeler kamuya açık | Uygulama işlevi | Evet | |
| Cihaz kimliği (FCM token, kurulum kimliği) | Evet | Hayır | Bildirimler | Hayır | Çıkışta/hesap silmede silinir |
| Kullanıcı içeriği (yorum, açıklamalar, salon yanıtı) | Evet | Evet — kamuya açık | Uygulama işlevi | Hayır | |
| Başka kişinin adı (başkası adına / elden randevu) | Evet | Evet — ilgili salonla | Uygulama işlevi | Hayır | Randevu alan kişi tarafından girilir |

- **Aktarım şifreleme:** Evet (HTTPS/TLS; Firebase).
- **Silme talebi:** Uygulama içinden (Hesap → Hesabımı Sil) + web/destek kanalı.
- **Ödeme bilgisi:** Toplanmaz (ödeme salonda yapılır).
- **Üçüncü taraf işleyici:** Google Firebase / Google Cloud (altyapı).
