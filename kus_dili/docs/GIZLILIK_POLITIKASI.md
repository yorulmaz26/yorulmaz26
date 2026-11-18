# Kuş Dili — Gizlilik Politikası (TASLAK)

> ⚠️ Bu metin bir taslaktır; yayımlamadan önce bir hukukçuya (KVKK ve
> mağaza kuralları açısından) inceletilmelidir. Köşeli parantezli yerleri
> doldurun.

**Veri sorumlusu:** [Ad / Unvan], [Adres], [E-posta]
**Son güncelleme:** [Tarih]

## 1. Kısaca

Kuş Dili'nde mesajlarınız, fotoğraflarınız, sesli mesajlarınız ve
aramalarınız **uçtan uca şifrelidir**: içerik telefonunuzda şifrelenir ve
yalnızca konuştuğunuz kişinin telefonunda çözülür. Biz (ve sunucularımızı
barındıran Google Firebase) bu içerikleri **okuyamayız**. Kuş dili
dönüşümü yalnızca telefonunuzda yapılır.

## 2. İşlediğimiz veriler

| Veri | Neden | Nerede |
|---|---|---|
| Telefon numarası | Hesap doğrulama (SMS) | Firebase Authentication; profilde **görünmez** |
| Kullanıcı adı, görünen ad | Arkadaşlarınızın sizi bulması | Firestore (diğer kullanıcılar görebilir) |
| Açık şifreleme anahtarları | Uçtan uca şifreleme | Firestore (gizli anahtarlar yalnızca telefonunuzda) |
| Şifreli mesaj/dosya içerikleri | Mesajın iletilmesi | Firestore / Storage — okunamaz |
| Üst veri: kiminle, ne zaman yazıştığınız; grup üyelikleri; arama kayıtları (kim, kimi, ne zaman, görüntülü mü) | Hizmetin çalışması | Firestore |
| Bildirim belirteci, sessize alınan sohbetler | Bildirim göndermek | Firestore |
| Öğrenme ilerlemesi, gizli/kilitli sohbet listesi, PIN özeti | Uygulama özellikleri | **Yalnızca telefonunuzda** |

Konum, rehber, reklam kimliği **toplanmaz**. Reklam ve üçüncü taraf analiz
araçları **kullanılmaz**.

## 3. Saklama süresi

- Süreli mesajlar seçtiğiniz süre sonunda silinir (sunucudan silme genellikle
  24 saat içinde tamamlanır).
- Tek görüntülük mesajın içeriği açıldığında silinir.
- Diğer şifreli içerikler, siz veya karşı taraf silene ya da hesap silinene
  kadar saklanır. Hesabınızı uygulama içinden (Profil → Hesabı kalıcı olarak
  sil) anında silebilirsiniz: profil, kullanıcı adı, anahtarlar ve bildirim
  bilgileri silinir, gruplardan çıkarılırsınız. Karşı tarafın cihazındaki
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
