import '../database/app_database.dart';
import '../utils/dates.dart';

/// Bildirim planlaması için saf (test edilebilir) hesaplamalar.
abstract final class ReminderLogic {
  /// iOS bir uygulama için en fazla 64 bekleyen bildirime izin verir.
  /// Güvenlik payıyla en yakın 60 tanesi planlanır; kalanlar uygulama bir
  /// sonraki açıldığında yeniden planlanır.
  static const maxPending = 60;

  /// Tek seferlik hatırlatıcı için sonraki tetik zamanı; geçmişteyse null.
  /// Tekrarlı hatırlatıcılar için bir sonraki gerçekleşme zamanı.
  static DateTime? nextOccurrence(Reminder r, DateTime now) {
    final at = r.at;
    switch (r.repeat) {
      case 'none':
        return at.isAfter(now) ? at : null;
      case 'daily':
        var c = DateTime(now.year, now.month, now.day, at.hour, at.minute);
        if (!c.isAfter(now)) c = c.add(const Duration(days: 1));
        return c;
      case 'weekly':
        final mask = r.weekdaysMask ?? (1 << (at.weekday - 1));
        if (mask == 0) return null;
        for (var i = 0; i < 8; i++) {
          final d = Dates.dayOf(now).add(Duration(days: i));
          final c = DateTime(d.year, d.month, d.day, at.hour, at.minute);
          if (Dates.maskHas(mask, d) && c.isAfter(now)) return c;
        }
        return null;
      case 'monthly':
        var c = DateTime(now.year, now.month, at.day, at.hour, at.minute);
        var i = 0;
        while ((!c.isAfter(now) || c.day != at.day) && i < 24) {
          i++;
          c = DateTime(now.year, now.month + i, at.day, at.hour, at.minute);
        }
        return c;
      case 'yearly':
        var c = DateTime(now.year, at.month, at.day, at.hour, at.minute);
        if (!c.isAfter(now)) c = DateTime(now.year + 1, at.month, at.day, at.hour, at.minute);
        return c;
    }
    return null;
  }

  /// Planlanacak hatırlatıcıları en yakın zamana göre sıralayıp sınırlar.
  static List<(Reminder, DateTime)> plan(List<Reminder> all, DateTime now, {int limit = maxPending}) {
    final items = <(Reminder, DateTime)>[];
    for (final r in all) {
      if (!r.enabled) continue;
      final n = nextOccurrence(r, now);
      if (n != null) items.add((r, n));
    }
    items.sort((a, b) => a.$2.compareTo(b.$2));
    return items.take(limit).toList();
  }
}
