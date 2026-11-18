/// Ders içerikleri. Kelimeler ve cümleler Türkçe; kuş dili karşılıkları
/// motor tarafından üretilir (elle yazılmaz, hata olmaz).
final class Lesson {
  const Lesson({
    required this.id,
    required this.title,
    required this.description,
    required this.words,
    required this.sentences,
  });

  final String id;
  final String title;
  final String description;
  final List<String> words;
  final List<String> sentences;
}

abstract final class Curriculum {
  static const lessons = <Lesson>[
    Lesson(
      id: 'temel',
      title: 'Kural: sesliden sonra hece',
      description: 'Her sesli harften sonra "g" + aynı sesli gelir: a → aga.',
      words: ['a', 'e', 'ı', 'i', 'o', 'ö', 'u', 'ü', 'su', 'el', 'ev', 'at'],
      sentences: ['Ben', 'Sen', 'O ev'],
    ),
    Lesson(
      id: 'selam',
      title: 'Selamlaşma',
      description: 'Günlük selamlar ve vedalar.',
      words: [
        'selam',
        'merhaba',
        'günaydın',
        'hoşça kal',
        'görüşürüz',
        'teşekkürler',
        'lütfen',
        'evet',
        'hayır',
        'tamam',
      ],
      sentences: [
        'Merhaba nasılsın',
        'İyiyim teşekkürler',
        'Görüşürüz arkadaşım',
        'Günaydın canım',
      ],
    ),
    Lesson(
      id: 'aile',
      title: 'Aile ve arkadaşlar',
      description: 'Sevdiklerin kuş dilinde.',
      words: [
        'anne',
        'baba',
        'kardeş',
        'abla',
        'ağabey',
        'dede',
        'nine',
        'arkadaş',
        'sevgili',
        'aile',
      ],
      sentences: [
        'Annem seni seviyor',
        'Kardeşim okulda',
        'Arkadaşım geliyor',
        'Ailemle yemek yiyorum',
      ],
    ),
    Lesson(
      id: 'duygu',
      title: 'Duygular',
      description: 'Hislerini sadece anlayan anlasın.',
      words: [
        'sevgi',
        'mutlu',
        'üzgün',
        'kızgın',
        'heyecan',
        'özlem',
        'korku',
        'umut',
        'gülmek',
        'ağlamak',
      ],
      sentences: [
        'Seni seviyorum',
        'Seni çok özledim',
        'Bugün çok mutluyum',
        'Biraz üzgünüm',
      ],
    ),
    Lesson(
      id: 'gunluk',
      title: 'Günlük hayat',
      description: 'Okul, iş, yemek ve buluşmalar.',
      words: [
        'okul',
        'öğretmen',
        'ödev',
        'yemek',
        'su',
        'çay',
        'kahve',
        'otobüs',
        'akşam',
        'sabah',
      ],
      sentences: [
        'Akşam buluşalım mı',
        'Ödevimi bitirdim',
        'Çay içelim mi',
        'Otobüs geç kaldı',
      ],
    ),
    Lesson(
      id: 'doga',
      title: 'Doğa ve kuşlar',
      description: 'Kuşların dilinde kuşlar.',
      words: [
        'kuş',
        'güneş',
        'ay',
        'yıldız',
        'deniz',
        'orman',
        'çiçek',
        'ağaç',
        'bulut',
        'yağmur',
      ],
      sentences: [
        'Kuşlar uçuyor',
        'Deniz çok güzel',
        'Yağmur yağıyor',
        'Güneş doğdu',
      ],
    ),
  ];

  static Lesson byId(String id) =>
      lessons.firstWhere((l) => l.id == id, orElse: () => lessons.first);
}
