import 'package:material_ui/material_ui.dart';

/// Kullanım koşulları (mağaza kuralları: kullanıcı içeriği olan uygulamalar
/// kötüye kullanıma karşı açık kurallar sunmalı). Metin
/// docs/KULLANIM_KOSULLARI.md ile aynı tutulmalı.
class TermsScreen extends StatelessWidget {
  const TermsScreen({super.key});

  static const sections = <(String, String)>[
    (
      'Kabul',
      'Kuş Dili\'ni kullanarak bu koşulları kabul etmiş olursun. Kabul '
          'etmiyorsan uygulamayı kullanma.',
    ),
    (
      'Yasak içerik ve davranış',
      'Taciz, tehdit, nefret söylemi, cinsel istismar, çocukların istismarı, '
          'şiddete çağrı, dolandırıcılık, istenmeyen toplu mesaj (spam), '
          'başkasının kimliğine bürünme ve yasa dışı her türlü içerik '
          'kesinlikle yasaktır. Bu kurallara uyulmamasına hiçbir tolerans '
          'gösterilmez.',
    ),
    (
      'Engelleme ve şikâyet',
      'Rahatsız olduğun kişiyi sohbet menüsünden engelleyebilir ve '
          'şikâyet edebilirsin. Mesajlar uçtan uca şifreli olduğu için '
          'ekibimiz onları göremez; şikâyet ederken izin verirsen son '
          'mesajlar telefonunda çözülüp yalnızca moderasyon ekibine gönderilir. '
          'Şikâyetler 24 saat içinde incelenir; kuralları çiğneyen hesaplar '
          'kapatılır.',
    ),
    (
      'Hesabın',
      'Hesabın telefon numarana bağlıdır. Kullanıcı adın benzersizdir ve '
          'değiştirilemez. Hesabını istediğin zaman Profil ekranından '
          'kalıcı olarak silebilirsin.',
    ),
    (
      'Gizlilik',
      'Hangi verilerin işlendiğini Gizlilik Politikası\'nda açıklıyoruz. '
          'Mesajlarının içeriğini sunucularımızda okuyamayız.',
    ),
    (
      'Sorumluluk',
      'Uygulama "olduğu gibi" sunulur. Gönderdiğin içerikten sen '
          'sorumlusun. Koşullar değişirse uygulama içinde duyurulur.',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Kullanım koşulları')),
      body: ListView(
        key: const Key('terms'),
        padding: const EdgeInsets.all(16),
        children: [
          for (final (title, body) in sections) ...[
            Text(title, style: text.titleMedium),
            const SizedBox(height: 4),
            Text(body, style: text.bodyMedium),
            const SizedBox(height: 16),
          ],
        ],
      ),
    );
  }
}
