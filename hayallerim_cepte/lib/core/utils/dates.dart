import 'package:intl/intl.dart';

/// Tarih yardımcıları (Türkçe biçimler, gün başlangıcı, seri hesapları).
abstract final class Dates {
  static DateTime dayOf(DateTime d) => DateTime(d.year, d.month, d.day);
  static DateTime today() => dayOf(DateTime.now());

  static bool sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

  /// Pazartesi başlangıçlı hafta.
  static DateTime startOfWeek(DateTime d) => dayOf(d).subtract(Duration(days: d.weekday - 1));
  static DateTime startOfMonth(DateTime d) => DateTime(d.year, d.month);

  static DateTime addMonths(DateTime d, int months) {
    final y = d.year + ((d.month - 1 + months) ~/ 12);
    final m = (d.month - 1 + months) % 12 + 1;
    final lastDay = DateTime(y, m + 1, 0).day;
    return DateTime(y, m, d.day > lastDay ? lastDay : d.day, d.hour, d.minute);
  }

  static int daysBetween(DateTime from, DateTime to) => dayOf(to).difference(dayOf(from)).inHours ~/ 24;

  static String long(DateTime d) => DateFormat('d MMMM y', 'tr_TR').format(d);
  static String longWithDay(DateTime d) => DateFormat('d MMMM y, EEEE', 'tr_TR').format(d);
  static String short(DateTime d) => DateFormat('d MMM y', 'tr_TR').format(d);
  static String dayMonth(DateTime d) => DateFormat('d MMM', 'tr_TR').format(d);
  static String monthYear(DateTime d) => DateFormat('MMMM y', 'tr_TR').format(d);
  static String time(DateTime d) => DateFormat('HH:mm', 'tr_TR').format(d);
  static String weekdayShort(DateTime d) => DateFormat('E', 'tr_TR').format(d);
  static String dateTime(DateTime d) => DateFormat('d MMM y HH:mm', 'tr_TR').format(d);

  static String minutesToHm(int minutes) =>
      '${(minutes ~/ 60).toString().padLeft(2, '0')}:${(minutes % 60).toString().padLeft(2, '0')}';

  /// "3 gün kaldı", "Bugün", "2 gün gecikti"
  static String relative(DateTime target) {
    final diff = daysBetween(DateTime.now(), target);
    if (diff == 0) return 'Bugün';
    if (diff == 1) return 'Yarın';
    if (diff == -1) return 'Dün';
    if (diff > 0) {
      if (diff >= 60) return '${(diff / 30).round()} ay kaldı';
      return '$diff gün kaldı';
    }
    return '${-diff} gün gecikti';
  }

  static String greeting([DateTime? now]) {
    final h = (now ?? DateTime.now()).hour;
    if (h < 5) return 'İyi geceler';
    if (h < 12) return 'Günaydın';
    if (h < 18) return 'İyi günler';
    return 'İyi akşamlar';
  }

  /// Pazartesi=bit0 ... Pazar=bit6
  static bool maskHas(int mask, DateTime d) => (mask >> (d.weekday - 1)) & 1 == 1;

  static const weekdayLetters = ['Pt', 'Sa', 'Ça', 'Pe', 'Cu', 'Ct', 'Pz'];

  static String durationLabel(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) return '$h sa $m dk';
    return '$m dk';
  }
}
