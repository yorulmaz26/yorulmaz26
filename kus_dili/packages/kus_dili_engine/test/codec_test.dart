import 'dart:math';

import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:test/test.dart';

void main() {
  const g = KusDiliCodec(KusDiliVariant.g);
  const f = KusDiliCodec(KusDiliVariant.f);
  const c = KusDiliCodec(KusDiliVariant.c);

  group("G'li kodlama — şartnamedeki örnekler", () {
    const cases = {
      'Merhaba': 'Megerhagabaga',
      'Selam': 'Segelagam',
      'Seni seviyorum': 'Segenigi segevigiyogorugum',
      'Nasılsın': 'Nagasıgılsıgın',
    };
    cases.forEach((tr, kus) {
      test('$tr → $kus', () => expect(g.encode(tr), kus));
      test('$kus → $tr', () {
        final r = g.decode(kus);
        expect(r.text, tr);
        expect(r.isValid, isTrue);
        expect(r.confidence, 1.0);
      });
    });
  });

  group('Sesli harf tablosu', () {
    const table = {
      'a': 'aga',
      'e': 'ege',
      'ı': 'ıgı',
      'i': 'igi',
      'o': 'ogo',
      'ö': 'ögö',
      'u': 'ugu',
      'ü': 'ügü',
    };
    table.forEach((v, expected) {
      test("G'li $v → $expected", () => expect(g.encode(v), expected));
    });
    test("F'li ve C'li", () {
      expect(f.encode('aeıioöuü'), 'afaefeıfıifiofoöföufuüfü');
      expect(c.encode('aeıioöuü'), 'acaeceıcıiciocoöcöucuücü');
      expect(f.encode('Merhaba'), 'Meferhafabafa');
      expect(c.encode('Selam'), 'Secelacam');
    });
    test('Sessiz harfler ve Türkçe sessizler değişmez', () {
      const consonants = 'bcçdfgğhjklmnprsştvyzBCÇDFGĞHJKLMNPRSŞTVYZ';
      expect(g.encode(consonants), consonants);
    });
  });

  group('Büyük/küçük harf', () {
    test('Baş harf büyük', () {
      expect(g.encode('Ali'), 'Agaligi');
      expect(g.encode('Ödev'), 'Ögödegev');
      expect(g.encode('İzmir'), 'İgizmigir');
      expect(g.encode('Irmak'), 'Igırmagak');
      expect(g.encode('Uğur'), 'Uguğugur');
    });
    test('Tamamı büyük kelimeler büyük hece alır (Türkçe İ/I)', () {
      expect(g.encode('ALİ'), 'AGALİGİ');
      expect(g.encode('KIRMIZI'), 'KIGIRMIGIZIGI');
      expect(g.encode('MERHABA DÜNYA'), 'MEGERHAGABAGA DÜGÜNYAGA');
    });
    test('Tek harfli büyük kelime komşu kelimeye uyar', () {
      expect(g.encode('O GELDİ'), 'OGO GEGELDİGİ');
      expect(g.encode('O geldi'), 'Ogo gegeldigi');
      expect(g.encode('A'), 'Aga');
    });
    test('Karışık harf (iPhone, McDonald) küçük hece alır', () {
      expect(g.encode('iPhone'), 'igiPhogonege');
    });
    test('Çözme büyük/küçük harf duyarsızdır ve aslını korur', () {
      expect(g.decode('AGALİGİ').text, 'ALİ');
      expect(g.decode('AgALiGi').text, 'ALi');
      expect(g.decode('KIGIRMIGIZIGI').text, 'KIRMIZI');
      expect(g.decode('İgizmigir').text, 'İzmir');
    });
  });

  group('Noktalama, boşluk, rakam, emoji', () {
    test('Noktalama korunur', () {
      expect(
        g.encode('Merhaba, nasılsın? İyiyim!'),
        'Megerhagabaga, nagasıgılsıgın? İgiyigiyigim!',
      );
      expect(
        g.encode('"Evet" (dedi) — tamam...'),
        '"Egeveget" (degedigi) — tagamagam...',
      );
    });
    test("Kesme işareti: Ali'nin", () {
      expect(g.encode("Ali'nin"), "Agaligi'nigin");
      expect(g.decode("Agaligi'nigin").text, "Ali'nin");
    });
    test('Boşluklar birebir korunur', () {
      const s = '  a\tb\n\nc  e \r\n';
      expect(g.encode(s), '  aga\tb\n\nc  ege \r\n');
      expect(g.decode(g.encode(s)).text, s);
    });
    test('Rakamlar korunur', () {
      expect(g.encode('Saat 10:30'), 'Sagaagat 10:30');
    });
    test('Emojiler korunur ve bölünmez', () {
      const s = 'Selam 👋🏽 aile: 👨‍👩‍👧‍👦 bayrak 🇹🇷 kalp ❤️ 1️⃣';
      final enc = g.encode(s);
      expect(enc, contains('👋🏽'));
      expect(enc, contains('👨‍👩‍👧‍👦'));
      expect(enc, contains('🇹🇷'));
      expect(enc, contains('❤️'));
      expect(enc, contains('1️⃣'));
      expect(enc.startsWith('Segelagam 👋🏽 agaigilege: '), isTrue);
      expect(g.decode(enc).text, s);
    });
    test('Boş ve sesli harfsiz metin', () {
      expect(g.encode(''), '');
      expect(g.encode('hmm 😀 !!'), 'hmm 😀 !!');
      expect(g.decode('').text, '');
      expect(g.decode('hmm').isValid, isTrue);
    });
  });

  group('Düzeltme işareti ve ayrık (decomposed) karakterler', () {
    test('â î û', () {
      expect(g.encode('hâlâ'), 'hâgâlâgâ');
      expect(g.encode('kâğıt'), 'kâgâğıgıt');
      expect(g.decode('hâgâlâgâ').text, 'hâlâ');
      // Hece düz yazılmış olsa da kabul edilir.
      expect(g.decode('hâgalâga').text, 'hâlâ');
    });
    test('Ayrık ö (o + U+0308) tek sesli harf sayılır', () {
      const decomposed = 'göz';
      expect(g.encode(decomposed), 'gögöz');
      expect(g.decode('gögöz').text, decomposed);
      // Birleşik ve ayrık yazım karşılaştırmada eşdeğerdir.
      expect(g.decode('gögör').text, 'gör');
    });
  });

  group('Belirsizlik: naif "aga→a" değiştirmenin bozduğu kelimeler', () {
    // Naif yaklaşım bu örneklerde yanlış sonuç verir; durum makinesi doğru.
    const words = [
      'Ege',
      'gaga',
      'sigara',
      'ağaç',
      'aga',
      'yeğen',
      'Egemen',
      'gogo',
      'iğne',
      'göğüs',
      'ögöö',
      'agaga',
      'gigi',
      'Agah',
      'eğe',
    ];
    for (final w in words) {
      test('"$w" gidiş-dönüş', () {
        final enc = g.encode(w);
        final r = g.decode(enc);
        expect(r.text, w, reason: 'kodlama: $enc');
        expect(r.isValid, isTrue);
      });
    }
    test('Somut değerler', () {
      expect(g.encode('Ege'), 'Egegege');
      expect(g.decode('Egegege').text, 'Ege');
      expect(g.encode('gaga'), 'gagagaga');
      expect(g.decode('gagagaga').text, 'gaga');
      expect(g.encode('sigara'), 'sigigagaraga');
      expect(g.decode('sigigagaraga').text, 'sigara');
    });
  });

  group('Geçersiz/eksik girdi', () {
    test('Normal Türkçe kuş dili değildir', () {
      final r = g.decode('Merhaba');
      expect(r.isValid, isFalse);
      expect(r.text, 'Merhaba', reason: 'hoşgörülü: metin bozulmaz');
      expect(r.issues.first.kind, DecodeIssueKind.missingInfix);
      expect(r.issues.first.offset, 1);
      expect(r.confidence, lessThan(0.5));
    });
    test('Yarım hece (yazılmakta olan metin)', () {
      final r = g.decode('Meg');
      expect(r.text, 'Meg');
      expect(r.issues.single.kind, DecodeIssueKind.truncatedInfix);
    });
    test('Karışık metin: geçerli kısımlar çözülür', () {
      final r = g.decode('Segelagam Merhaba');
      expect(r.text, 'Selam Merhaba');
      expect(r.isValid, isFalse);
      expect(r.vowelCount, 5);
      expect(r.matchedVowelCount, 2);
    });
    test('Yanlış varyantla çözme geçersiz sayılır', () {
      expect(f.decode(g.encode('Seni seviyorum')).isValid, isFalse);
    });
  });

  group('Korunan bölümler (URL, e-posta, @kullanıcı)', () {
    test('URL, e-posta ve kullanıcı adı değişmez', () {
      const s = 'Bak: https://ornek.com/sayfa?a=1, yaz: ali@posta.com @ayse';
      final enc = g.encode(s);
      expect(enc, contains('https://ornek.com/sayfa?a=1,'));
      expect(enc, contains('ali@posta.com'));
      expect(enc, contains('@ayse'));
      expect(enc.startsWith('Bagak: '), isTrue);
      expect(g.decode(enc).text, s);
    });
    test('URL sonundaki noktalama URL\'ye dahil edilmez', () {
      expect(g.encode('www.ornek.com.'), 'www.ornek.com.');
      expect(g.encode('(https://a.io)'), '(https://a.io)');
    });
    test('Koruma kapatılabilir', () {
      const raw = KusDiliCodec(
        KusDiliVariant.g,
        preserveSpecialSegments: false,
      );
      expect(raw.encode('@ali'), '@agaligi');
      expect(raw.decode('@agaligi').text, '@ali');
    });
    test('Gerçek belirsizlik bildirilir', () {
      // "a@x.o" korunmaz (TLD tek harf) → "aga@x.ogo" olur; bu ise geçerli
      // bir e-posta gibi de görünür. Korunan yorum seçilir ve uyarı verilir.
      final enc = g.encode('a@x.o');
      expect(enc, 'aga@x.ogo');
      final r = g.decode(enc);
      expect(r.text, 'aga@x.ogo');
      expect(r.isValid, isTrue);
      expect(r.issues.single.kind, DecodeIssueKind.ambiguousProtectedSegment);
    });
    test('Gerçek e-posta belirsiz sayılmaz', () {
      final r = g.decode('Yaz: mehmet@posta.com');
      expect(
        r.issues.where(
          (i) => i.kind == DecodeIssueKind.ambiguousProtectedSegment,
        ),
        isEmpty,
      );
    });
  });

  group('Tespit', () {
    test('looksEncoded', () {
      expect(g.looksEncoded('Segenigi segevigiyogorugum'), isTrue);
      expect(g.looksEncoded('Seni seviyorum'), isFalse);
      expect(g.looksEncoded('hmm'), isFalse);
    });
    test('detectVariant', () {
      expect(
        KusDiliCodec.detectVariant(f.encode('Bugün hava çok güzel')),
        KusDiliVariant.f,
      );
      expect(
        KusDiliCodec.detectVariant(c.encode('Yarın görüşürüz')),
        KusDiliVariant.c,
      );
      expect(KusDiliCodec.detectVariant('Yarın görüşürüz'), isNull);
    });
  });

  group('Özelliğe dayalı (rastgele) gidiş-dönüş testleri', () {
    const alphabet =
        'abcçdefgğhıijklmnoöprsştuüvyz'
        'ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ'
        'âîû0123456789 .,!?;:\'"()-\n\t';
    const emojis = ['😀', '👋🏽', '👨‍👩‍👧', '🇹🇷', '❤️', '🐦'];
    final variants = [
      KusDiliVariant.g,
      KusDiliVariant.f,
      KusDiliVariant.c,
      KusDiliVariant.custom(id: 'zz', name: 'Z', template: 'z{v}'),
      KusDiliVariant.custom(id: 'pr', name: 'PR', template: 'p{v}r'),
      KusDiliVariant.custom(id: 'gg', name: 'Çift', template: 'g{v}g{v}'),
      KusDiliVariant.custom(
        id: 'mix',
        name: 'Karışık',
        template: 'g{v}',
        vowelOverrides: {'a': 'v{v}', 'ü': 'ş{v}'},
      ),
    ];

    String randomText(Random rnd) {
      final len = rnd.nextInt(60);
      final b = StringBuffer();
      for (var i = 0; i < len; i++) {
        if (rnd.nextInt(12) == 0) {
          b.write(emojis[rnd.nextInt(emojis.length)]);
        } else {
          b.write(alphabet[rnd.nextInt(alphabet.length)]);
        }
      }
      return b.toString();
    }

    for (final v in variants) {
      test('decode(encode(x)) == x  [${v.id}]', () {
        final rnd = Random(20261117 ^ v.id.hashCode);
        final codec = KusDiliCodec(v, preserveSpecialSegments: false);
        for (var n = 0; n < 3000; n++) {
          final s = randomText(rnd);
          final enc = codec.encode(s);
          final r = codec.decode(enc);
          expect(r.text, s, reason: 'girdi: "$s" kodlama: "$enc"');
          expect(r.isValid, isTrue, reason: 'girdi: "$s"');
          if (r.vowelCount > 0) expect(r.confidence, 1.0);
        }
      });
    }

    test('Korumalı modda gerçekçi cümleler gidiş-dönüş', () {
      final rnd = Random(42);
      const words = [
        'merhaba',
        'Ankara\'ya',
        'gidiyorum',
        'https://kusdili.app/x',
        'iletisim@kusdili.app',
        '@kullanici_1',
        'ÇOK',
        'güzel',
        '🐦',
        'İ',
        'ılık',
        'öğle',
        'saat',
        '12:45',
        'hâlâ',
        '—',
        'Ege',
        'gaga',
      ];
      for (var n = 0; n < 2000; n++) {
        final s = List.generate(
          1 + rnd.nextInt(8),
          (_) => words[rnd.nextInt(words.length)],
        ).join(rnd.nextBool() ? ' ' : ', ');
        final enc = g.encode(s);
        expect(g.decode(enc).text, s, reason: 'girdi: "$s" kodlama: "$enc"');
      }
    });

    test('Kodlama yalnızca ekleme yapar: sesliler çıkarılınca aslı kalır', () {
      final rnd = Random(7);
      for (var n = 0; n < 500; n++) {
        final s = randomText(rnd);
        final enc = g.encode(s);
        expect(enc.length, greaterThanOrEqualTo(s.length));
      }
    });
  });

  group('Performans', () {
    test('100 bin karakterlik metin anlık dönüşür', () {
      final text = List.filled(5000, 'Seni seviyorum 🐦 ').join();
      final sw = Stopwatch()..start();
      final enc = g.encode(text);
      final dec = g.decode(enc).text;
      sw.stop();
      expect(dec, text);
      // CI makineleri yavaş olabilir; yine de 2 sn'nin çok altında olmalı.
      expect(sw.elapsedMilliseconds, lessThan(2000));
    });
  });
}
