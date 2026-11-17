import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:test/test.dart';

void main() {
  test('Türkçe küçük/büyük harf', () {
    expect(TurkishText.lower('IŞIK İĞNE'), 'ışık iğne');
    expect(TurkishText.upper('ışık iğne'), 'IŞIK İĞNE');
    expect(TurkishText.upper('ß'), 'ß', reason: 'tek karakter kalır');
  });

  test('Sesli harf tanıma', () {
    for (final v in ['a', 'E', 'ı', 'I', 'i', 'İ', 'ö', 'Ü', 'â', 'ö']) {
      expect(TurkishText.isVowel(v), isTrue, reason: v);
    }
    for (final c in ['b', 'Ğ', 'ş', '1', ' ', '😀', '']) {
      expect(TurkishText.isVowel(c), isFalse, reason: c);
    }
  });

  test('Kümeler emojileri ve aksanları bölmez, birleşince aslını verir', () {
    const s = 'göz 👨‍👩‍👧‍👦🇹🇷👋🏽1️⃣ é';
    final parts = TurkishText.clusters(s);
    expect(parts.join(), s);
    expect(parts, contains('ö'));
    expect(parts, contains('👨‍👩‍👧‍👦'));
    expect(parts, contains('🇹🇷'));
    expect(parts, contains('👋🏽'));
    expect(parts, contains('1️⃣'));
  });

  test('Katlama', () {
    expect(TurkishText.fold('Â'), 'a');
    expect(TurkishText.fold('I'), 'ı');
    expect(TurkishText.fold('İ'), 'i');
    expect(TurkishText.fold('Ö'), 'ö');
  });
}
