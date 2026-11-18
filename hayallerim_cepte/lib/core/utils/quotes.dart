/// Günlük motivasyon sözleri (yerel, çevrimdışı).
abstract final class Quotes {
  static const all = <String>[
    'Büyük hayaller küçük adımlarla gerçekleşir.',
    'Bugün attığın küçük adım, yarının büyük farkıdır.',
    'Hayal etmek başlangıçtır; planlamak yoldur; yapmak varıştır.',
    'Mükemmel zamanı bekleme; zamanı mükemmel yap.',
    'Disiplin, ne istediğini hatırlamaktır.',
    'Her gün biraz daha iyi olmak yeterlidir.',
    'Yavaş ilerlemek, hiç ilerlememekten iyidir.',
    'Hayallerin, sen onlara inandığın kadar büyüktür.',
    'Bir hedef, tarihi olan bir hayaldir.',
    'Vazgeçmek istediğinde neden başladığını hatırla.',
    'Bugün kendin için bir şey yap; yarınki sen teşekkür edecek.',
    'Başarı, her gün tekrarlanan küçük çabaların toplamıdır.',
    'Engeller, gözünü hedeften ayırdığında görünür.',
    'Kendine verdiğin sözleri tutmak, en güçlü özgüvendir.',
    'Bir tohum bile sabırla ağaç olur.',
    'İlerleme, mükemmellikten daha değerlidir.',
    'Yol uzunsa adımlarını küçült, ama yürümeyi bırakma.',
    'Bugünün emeği, yarının hikâyesidir.',
    'Seni hayallerinden ayıran şey, atacağın bir sonraki adımdır.',
    'Odaklandığın şey büyür.',
    'Her başarı, denemeye karar verdiğin an başlar.',
    'Planı olmayan hayal yalnızca bir dilektir.',
    'Kendi bahçeni sula; çiçekler zamanla açar.',
    'Zor günler, güçlü insanların yapıldığı günlerdir.',
    'Bir adım at; yol kendini gösterecek.',
    'Hayatını değiştirmek için her gün bir şeyi değiştir.',
    'Şükretmek, sahip olduklarını yeterli kılar.',
    'Bugün, geri kalan hayatının ilk günü.',
    'Cesaret, korkuya rağmen ilerlemektir.',
    'Sen yapabilirsin. Sadece başla.',
  ];

  static String ofDay([DateTime? d]) {
    final now = d ?? DateTime.now();
    final dayOfYear = now.difference(DateTime(now.year)).inDays;
    return all[(dayOfYear + now.year) % all.length];
  }
}
