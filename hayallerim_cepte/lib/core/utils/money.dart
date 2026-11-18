import 'package:decimal/decimal.dart';

/// Para tutarları veritabanında **kuruş (minor unit) cinsinden tam sayı**
/// olarak saklanır. Ayrıştırma ve kur çarpımı [Decimal] ile yapılır; böylece
/// kayan nokta hataları (0.1 + 0.2) oluşmaz.
abstract final class Money {
  static const currencies = <String, String>{
    'TRY': '₺',
    'USD': r'$',
    'EUR': '€',
    'GBP': '£',
    'JPY': '¥',
    'CHF': 'CHF',
  };

  static String symbol(String code) => currencies[code] ?? code;

  /// "1.250.000,50" / "1250000.5" / "1 250 000" gibi kullanıcı girdilerini
  /// kuruşa çevirir. Geçersiz girdide null döner.
  static int? parseMinor(String input) {
    var s = input.trim().replaceAll(RegExp(r'[\s₺$€£¥]'), '');
    s = s.replaceAll(RegExp(r'[A-Za-z]'), '');
    if (s.isEmpty) return null;
    final neg = s.startsWith('-');
    if (neg) s = s.substring(1);
    final lastComma = s.lastIndexOf(',');
    final lastDot = s.lastIndexOf('.');
    String intPart;
    String frac = '';
    if (lastComma >= 0 && lastDot >= 0) {
      // Hangisi sonda ise ondalık ayırıcıdır.
      final decSep = lastComma > lastDot ? ',' : '.';
      final idx = s.lastIndexOf(decSep);
      intPart = s.substring(0, idx).replaceAll(RegExp(r'[.,]'), '');
      frac = s.substring(idx + 1);
    } else if (lastComma >= 0) {
      // Türkçe: virgül ondalıktır (birden fazla virgül → binlik ayırıcı).
      if (','.allMatches(s).length > 1) {
        intPart = s.replaceAll(',', '');
      } else {
        intPart = s.substring(0, lastComma);
        frac = s.substring(lastComma + 1);
      }
    } else if (lastDot >= 0) {
      // Nokta: tek nokta ve arkasında 1-2 hane varsa ondalık, yoksa binlik.
      final after = s.length - lastDot - 1;
      if ('.'.allMatches(s).length == 1 && after > 0 && after <= 2) {
        intPart = s.substring(0, lastDot);
        frac = s.substring(lastDot + 1);
      } else {
        intPart = s.replaceAll('.', '');
      }
    } else {
      intPart = s;
    }
    if (intPart.isEmpty) intPart = '0';
    if (!RegExp(r'^\d+$').hasMatch(intPart) || (frac.isNotEmpty && !RegExp(r'^\d+$').hasMatch(frac))) {
      return null;
    }
    if (frac.length > 2) return null;
    final value = Decimal.parse('$intPart.${frac.isEmpty ? '0' : frac}');
    final minor = (value * Decimal.fromInt(100)).toBigInt();
    if (minor > BigInt.from(900000000000000000)) return null; // taşma koruması
    final r = minor.toInt();
    return neg ? -r : r;
  }

  /// 125000050 → "1.250.000,50 ₺"
  static String format(int minor, {String currency = 'TRY', bool withSymbol = true, bool showCents = true}) {
    final neg = minor < 0;
    final abs = minor.abs();
    final intPart = abs ~/ 100;
    final cents = abs % 100;
    final digits = intPart.toString();
    final buf = StringBuffer();
    for (var i = 0; i < digits.length; i++) {
      if (i > 0 && (digits.length - i) % 3 == 0) buf.write('.');
      buf.write(digits[i]);
    }
    var out = buf.toString();
    if (showCents && cents != 0) out += ',${cents.toString().padLeft(2, '0')}';
    if (neg) out = '-$out';
    return withSymbol ? '$out ${symbol(currency)}' : out;
  }

  /// Kullanıcı girişi için kısa metin (ör. düzenleme alanına geri yazma).
  static String toInput(int minor) => format(minor, withSymbol: false);

  /// Manuel kur ile çevirme: amountMinor * rate (yarım yukarı yuvarlama).
  static int convert(int amountMinor, Decimal rate) {
    final v = Decimal.fromInt(amountMinor) * rate;
    return v.round().toBigInt().toInt();
  }

  static Decimal? parseRate(String input) {
    final s = input.trim().replaceAll(',', '.');
    final d = Decimal.tryParse(s);
    if (d == null || d <= Decimal.zero) return null;
    return d;
  }

  /// İlerleme oranı 0..1 (tam sayılarla).
  static double ratio(int current, int target) {
    if (target <= 0) return 0;
    if (current <= 0) return 0;
    if (current >= target) return 1;
    return current / target;
  }

  /// Aylık birikim hızına göre hedefe kalan ay sayısı (yukarı yuvarlanır).
  static int? monthsToGoal({required int remainingMinor, required int monthlyMinor}) {
    if (remainingMinor <= 0) return 0;
    if (monthlyMinor <= 0) return null;
    return (remainingMinor + monthlyMinor - 1) ~/ monthlyMinor;
  }

  /// Hedef tarihe kadar her ay ne kadar biriktirilmeli? (yukarı yuvarlanır)
  static int requiredMonthly({required int remainingMinor, required int months}) {
    if (remainingMinor <= 0) return 0;
    if (months <= 0) return remainingMinor;
    return (remainingMinor + months - 1) ~/ months;
  }
}
