import '../database/app_database.dart';
import '../utils/dates.dart';

/// Hayal ilerlemesi, bahçe aşamaları, seri ve seviye hesapları.
abstract final class ProgressLogic {
  /// Tamamlanan adımlara göre ilerleme (0..1). Ağırlıklı modda her adımın
  /// `weight` değeri dikkate alınır. Hayal tamamlandıysa 1.
  static double dreamProgress(List<DreamMilestone> steps, {required bool weighted, bool completed = false}) {
    if (completed) return 1;
    if (steps.isEmpty) return 0;
    if (!weighted) {
      final done = steps.where((s) => s.isCompleted).length;
      return done / steps.length;
    }
    var total = 0;
    var done = 0;
    for (final s in steps) {
      final w = s.weight < 1 ? 1 : s.weight;
      total += w;
      if (s.isCompleted) done += w;
    }
    return total == 0 ? 0 : done / total;
  }

  static const stageNames = [
    'Tohum',
    'Filiz',
    'Fidan',
    'Genç ağaç',
    'Büyük ağaç',
    'Çiçek açan ağaç',
    'Meyve veren ağaç',
  ];

  /// İlerlemeye göre bahçe aşaması (0: Tohum … 6: Meyve veren ağaç).
  /// Meyve aşamasına yalnızca gerçekleşen hayaller ulaşır.
  static int gardenStage(double progress, {required bool completed}) {
    if (completed) return 6;
    if (progress <= 0) return 0;
    if (progress < 0.2) return 1;
    if (progress < 0.4) return 2;
    if (progress < 0.6) return 3;
    if (progress < 0.8) return 4;
    return 5;
  }

  /// Bir alışkanlığın güncel serisi (bugün henüz işaretlenmediyse dünden
  /// geriye sayılır). Planlanmamış günler seriyi bozmaz.
  static int habitStreak(Set<DateTime> doneDays, int weekdaysMask, {DateTime? today}) {
    final t = Dates.dayOf(today ?? DateTime.now());
    var d = doneDays.contains(t) ? t : t.subtract(const Duration(days: 1));
    var streak = 0;
    for (var guard = 0; guard < 3660; guard++) {
      if (!Dates.maskHas(weekdaysMask, d)) {
        d = d.subtract(const Duration(days: 1));
        continue;
      }
      if (doneDays.contains(d)) {
        streak++;
        d = d.subtract(const Duration(days: 1));
      } else {
        break;
      }
    }
    return streak;
  }

  static int bestStreak(Set<DateTime> doneDays, int weekdaysMask) {
    if (doneDays.isEmpty) return 0;
    final sorted = doneDays.toList()..sort();
    var best = 0;
    var cur = 0;
    DateTime? prev;
    for (final d in sorted) {
      if (prev == null) {
        cur = 1;
      } else {
        // Arada planlanmış ama yapılmamış gün var mı?
        var gap = false;
        var x = prev.add(const Duration(days: 1));
        while (x.isBefore(d)) {
          if (Dates.maskHas(weekdaysMask, x)) {
            gap = true;
            break;
          }
          x = x.add(const Duration(days: 1));
        }
        cur = gap ? 1 : cur + 1;
      }
      if (cur > best) best = cur;
      prev = d;
    }
    return best;
  }

  /// Genel aktivite serisi: arka arkaya en az bir kayıt olan günler.
  static int activityStreak(Set<DateTime> activeDays, {DateTime? today}) {
    final t = Dates.dayOf(today ?? DateTime.now());
    var d = activeDays.contains(t) ? t : t.subtract(const Duration(days: 1));
    var s = 0;
    while (activeDays.contains(d)) {
      s++;
      d = d.subtract(const Duration(days: 1));
    }
    return s;
  }

  /// Seviye: her seviye bir öncekinden 50 puan daha fazla ister.
  /// 1→2: 100, 2→3: 150, 3→4: 200 …
  static ({int level, int intoLevel, int needed}) level(int points) {
    var level = 1;
    var remaining = points < 0 ? 0 : points;
    var need = 100;
    while (remaining >= need) {
      remaining -= need;
      level++;
      need += 50;
    }
    return (level: level, intoLevel: remaining, needed: need);
  }

  static String levelTitle(int level) {
    if (level >= 20) return 'Hayal Efsanesi';
    if (level >= 15) return 'Hayal Ustası';
    if (level >= 10) return 'Yol Gösterici';
    if (level >= 6) return 'Kararlı Gezgin';
    if (level >= 3) return 'Hayal Kâşifi';
    return 'Hayalperest';
  }
}
