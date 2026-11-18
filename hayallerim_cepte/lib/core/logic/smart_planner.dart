import '../utils/money.dart';

/// Akıllı Planlayıcı — tamamen yerel, kural tabanlı hedef ayrıştırıcı.
///
/// Yapay zekâ DEĞİLDİR; Türkçe cümledeki süre ve miktar kalıplarını düzenli
/// ifadelerle yakalar ve aritmetik bir plan önerir. Örnek:
///   "6 ayda 30 kitap okumak istiyorum" → süre 6 ay, miktar 30 kitap.
class PlanInput {
  PlanInput({required this.amount, required this.unit, required this.days, required this.isMoney, this.action = ''});

  /// Para hedeflerinde kuruş; diğerlerinde adet × 100 (kesir desteği için).
  final int amount;
  final String unit;
  final int days;
  final bool isMoney;
  final String action;

  PlanInput copyWith({int? amount, String? unit, int? days, bool? isMoney}) => PlanInput(
        amount: amount ?? this.amount,
        unit: unit ?? this.unit,
        days: days ?? this.days,
        isMoney: isMoney ?? this.isMoney,
        action: action,
      );
}

class PlanResult {
  PlanResult({
    required this.input,
    required this.perDay,
    required this.perWeek,
    required this.perMonth,
    required this.months,
    required this.weeks,
    required this.summary,
  });

  final PlanInput input;
  final double perDay;
  final double perWeek;
  final double perMonth;
  final int months;
  final int weeks;
  final String summary;

  String fmt(double v) {
    if (input.isMoney) return Money.format((v).ceil(), withSymbol: true);
    final units = v / 100;
    if (units >= 10 || units == units.roundToDouble()) return units.toStringAsFixed(0);
    return units.toStringAsFixed(1).replaceAll('.', ',');
  }

  /// "1–2 kitap" gibi aralık (adet hedeflerinde).
  String range(double v) {
    if (input.isMoney) return fmt(v);
    final u = v / 100;
    final lo = u.floor();
    final hi = u.ceil();
    if (lo == hi) return '$lo';
    if (lo == 0) return 'en az 1';
    return '$lo–$hi';
  }
}

abstract final class SmartPlanner {
  static const _unitDays = {
    'gün': 1,
    'gun': 1,
    'hafta': 7,
    'ay': 30,
    'yıl': 365,
    'yil': 365,
    'sene': 365,
  };

  static final _durationRe = RegExp(
    r'(\d+)\s*(gün|gun|hafta|ay|yıl|yil|sene)\w*',
    caseSensitive: false,
  );

  static final _moneyRe = RegExp(
    r'(\d[\d.,]*)\s*(bin|milyon)?\s*(tl|₺|lira|dolar|\$|euro|€|usd|eur)',
    caseSensitive: false,
  );

  static final _countRe = RegExp(r'(\d+)\s+([a-zçğıöşü]+)', caseSensitive: false);

  /// Cümleyi ayrıştırır; anlaşılamazsa null döner.
  static PlanInput? parse(String text) {
    final s = text.toLowerCase().trim();
    if (s.isEmpty) return null;
    final dm = _durationRe.firstMatch(s);
    if (dm == null) return null;
    final n = int.tryParse(dm.group(1)!) ?? 0;
    final unitKey = dm.group(2)!.toLowerCase();
    final days = n * (_unitDays[unitKey] ?? 0);
    if (days <= 0) return null;
    final rest = s.replaceRange(dm.start, dm.end, ' ');

    final mm = _moneyRe.firstMatch(rest);
    if (mm != null) {
      var minor = Money.parseMinor(mm.group(1)!);
      if (minor == null) return null;
      final mult = mm.group(2);
      if (mult == 'bin') minor *= 1000;
      if (mult == 'milyon') minor *= 1000000;
      if (minor <= 0) return null;
      return PlanInput(amount: minor, unit: 'TL', days: days, isMoney: true, action: 'biriktirmek');
    }

    final cm = _countRe.firstMatch(rest);
    if (cm == null) return null;
    final count = int.tryParse(cm.group(1)!) ?? 0;
    if (count <= 0) return null;
    final unit = cm.group(2)!;
    final after = rest.substring(cm.end).trim().split(RegExp(r'\s+'));
    final action = after.isNotEmpty ? after.first : '';
    return PlanInput(amount: count * 100, unit: unit, days: days, isMoney: false, action: action);
  }

  static PlanResult compute(PlanInput i) {
    final days = i.days < 1 ? 1 : i.days;
    final perDay = i.amount / days;
    final weeks = (days / 7).ceil();
    final months = (days / 30).round() < 1 ? 1 : (days / 30).round();
    final perWeek = i.amount / (days / 7);
    final perMonth = i.amount / (days / 30);
    final r = PlanResult(
      input: i,
      perDay: perDay,
      perWeek: perWeek,
      perMonth: perMonth,
      months: months,
      weeks: weeks,
      summary: '',
    );
    final what = i.isMoney ? '' : ' ${i.unit}';
    final summary = StringBuffer();
    if (days >= 60) summary.writeln('Aylık hedef: ${r.fmt(perMonth)}$what');
    if (days >= 14) summary.writeln('Haftalık yaklaşık hedef: ${r.range(perWeek)}$what');
    summary.write('Günlük ortalama: ${r.fmt(perDay)}$what');
    return PlanResult(
      input: i,
      perDay: perDay,
      perWeek: perWeek,
      perMonth: perMonth,
      months: months,
      weeks: weeks,
      summary: summary.toString(),
    );
  }

  static const examples = [
    '6 ayda 30 kitap okumak istiyorum',
    '1 yılda 120000 TL biriktirmek istiyorum',
    '3 ayda 200 km koşmak istiyorum',
    '8 haftada 40 ders bitirmek istiyorum',
  ];
}
