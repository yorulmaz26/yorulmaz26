# Kuş Dili — Gizlilik Politikası (TASLAK)

> ⚠️ Bu metin bir taslaktır; yayımlamadan önce bir hukukçuya (KVKK ve
> mağaza kuralları açısından) inceletilmelidir. Köşeli parantezli yerleri
> doldurun.

**Veri sorumlusu:** [Ad / Unvan], [Adres], [E-posta]
**Son güncelleme:** [Tarih]

## 1. Kısaca

Kuş Dili'nde mesajlarınız **uçtan uca şifrelidir**: içerik telefonunuzda
şifrelenir ve yalnızca konuştuğunuz kişinin (ve sizin) bağlı cihazlarınızda
çözülür. Biz (ve sunucularımızı
barındıran Google Firebase) bu içerikleri **okuyamayız**. Kuş dili
dönüşümü yalnızca telefonunuzda yapılır.

## 2. İşlediğimiz veriler

| Veri | Neden | Nerede |
|---|---|---|
| Telefon numarası | Hesap doğrulama (SMS) | Firebase Authentication; profilde **görünmez** |
| Kullanıcı adı, görünen ad | Arkadaşlarınızın sizi bulması | Firestore (diğer kullanıcılar görebilir) |
| Açık şifreleme anahtarları, bağlı cihazların adı ("iPhone", "Android telefon") ve son etkinlik zamanı | Uçtan uca şifreleme, Bağlı cihazlar ekranı | Firestore (gizli anahtarlar yalnızca telefonunuzda) |
| Şifreli mesaj içerikleri | Mesajın iletilmesi | Firestore — okunamaz |
| Üst veri: kiminle, ne zaman yazıştığınız; grup üyelikleri | Hizmetin çalışması | Firestore |
| Engellediğiniz kişiler | Engelleme | Firestore (yalnızca siz görürsünüz) |
| Şikâyetler: kim, kimi, hangi sohbet, neden, açıklama; **onay verirseniz** son en fazla 5 mesajın açık metni | Kötüye kullanımla mücadele | Firestore (yalnızca moderasyon ekibi) |
| Bildirim belirteci, sessize alınan sohbetler | Bildirim göndermek | Firestore |
| Öğrenme ilerlemesi, gizli/kilitli sohbet listesi, PIN özeti | Uygulama özellikleri | **Yalnızca telefonunuzda** |

Fotoğraf, ses, konum, rehber, reklam kimliği **toplanmaz**; uygulama kamera
ve mikrofon izni istemez. Reklam ve üçüncü taraf analiz
araçları **kullanılmaz**.

## 3. Saklama süresi

- Süreli mesajlar seçtiğiniz süre sonunda silinir (sunucudan silme genellikle
  24 saat içinde tamamlanır).
- Tek görüntülük mesajın içeriği açıldığında silinir.
- Diğer şifreli içerikler, siz veya karşı taraf silene ya da hesap silinene
  kadar saklanır. Hesabınızı uygulama içinden (Profil → Hesabı kalıcı olarak
  sil) anında silebilirsiniz: profil, kullanıcı adı, anahtarlar, engel listesi
  ve bildirim bilgileri silinir, gruplardan çıkarılırsınız.
- Şikâyetler inceleme ve yasal yükümlülükler için [süre] saklanır. Karşı tarafın cihazındaki
  kopyalar onların kontrolündedir.

## 4. Aktarım

Veriler Google LLC'nin Firebase hizmetinde [bölge: ör. Avrupa] tutulur.
Bu, KVKK md. 9 kapsamında yurt dışına aktarım sayılabilir; [dayanağı
belirtin: açık rıza / standart sözleşme].

## 5. Haklarınız (KVKK md. 11)

Verilerinize erişme, düzeltme, silme, işlemeye itiraz etme vb. haklarınız
için [e-posta] adresine yazabilirsiniz.

## 6. Güvenlik ve sınırlar

Uçtan uca şifreleme Signal Protokolü ile yapılır. Telefonunuz ele
geçirilirse, ekran görüntüsü alınırsa veya telefonunuzda zararlı yazılım
varsa şifreleme bu durumlara karşı koruma sağlamaz. Kuş dili bir şifreleme
yöntemi değildir; yalnızca görünüm biçimidir.

## 7. Çocuklar

Uygulama [13/16] yaşın altındaki kullanıcılara yönelik değildir.

## 8. İletişim

[E-posta]
