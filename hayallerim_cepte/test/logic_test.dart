import 'package:decimal/decimal.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/core/database/app_database.dart';
import 'package:hayallerim_cepte/core/logic/progress_logic.dart';
import 'package:hayallerim_cepte/core/logic/smart_planner.dart';
import 'package:hayallerim_cepte/core/services/reminder_logic.dart';
import 'package:hayallerim_cepte/core/utils/dates.dart';
import 'package:hayallerim_cepte/core/utils/money.dart';
import 'package:hayallerim_cepte/data/goal_repository.dart';

DreamMilestone step(bool done, {int weight = 1}) => DreamMilestone(
      id: 0,
      dreamId: 1,
      title: 'x',
      description: '',
      isCompleted: done,
      weight: weight,
      note: '',
      sortOrder: 0,
    );

Reminder rem(String repeat, DateTime at, {int? mask}) => Reminder(
      id: 1,
      kind: 'task',
      title: 't',
      body: '',
      at: at,
      repeat: repeat,
      weekdaysMask: mask,
      enabled: true,
      createdAt: at,
    );

void main() {
  group('Money', () {
    test('Türkçe ve İngilizce biçimleri kuruşa çevirir', () {
      expect(Money.parseMinor('1.250.000,50'), 125000050);
      expect(Money.parseMinor('1250000.5'), 125000050);
      expect(Money.parseMinor('1 000 000'), 100000000);
      expect(Money.parseMinor('250.000'), 25000000);
      expect(Money.parseMinor('0,1'), 10);
      expect(Money.parseMinor('12,345'), null); // 3 ondalık hane geçersiz
      expect(Money.parseMinor('abc'), null);
      expect(Money.parseMinor(''), null);
    });

    test('kayan nokta hatası yok: 0,10 + 0,20 = 0,30', () {
      final a = Money.parseMinor('0,10')!;
      final b = Money.parseMinor('0,20')!;
      expect(a + b, Money.parseMinor('0,30'));
    });

    test('biçimlendirme', () {
      expect(Money.format(125000050), '1.250.000,50 ₺');
      expect(Money.format(100000000, showCents: false), '1.000.000 ₺');
      expect(Money.format(-5000, currency: 'USD'), r'-50 $');
    });

    test('manuel kurla çevirme Decimal ile yapılır', () {
      // 100 USD × 32,4567 = 3245,67 TL
      expect(Money.convert(10000, Decimal.parse('32.4567')), 324567);
      expect(Money.parseRate('32,45'), Decimal.parse('32.45'));
      expect(Money.parseRate('-1'), null);
    });

    test('Araba örneği: 1.000.000 hedef, 250.000 birikim → %25, 750.000 kalan', () {
      const target = 100000000;
      const saved = 25000000;
      expect(Money.ratio(saved, target), 0.25);
      expect(target - saved, 75000000);
      expect(Money.monthsToGoal(remainingMinor: 75000000, monthlyMinor: 5000000), 15);
      expect(Money.requiredMonthly(remainingMinor: 75000000, months: 12), 6250000);
    });
  });

  group('SmartPlanner', () {
    test('"6 ayda 30 kitap okumak istiyorum"', () {
      final p = SmartPlanner.parse('6 ayda 30 kitap okumak istiyorum')!;
      expect(p.days, 180);
      expect(p.amount, 3000);
      expect(p.unit, 'kitap');
      expect(p.isMoney, false);
      final r = SmartPlanner.compute(p);
      expect(r.fmt(r.perMonth), '5');
      expect(r.range(r.perWeek), '1–2');
      expect(r.summary, contains('Aylık hedef: 5 kitap'));
      expect(r.summary, contains('Haftalık yaklaşık hedef: 1–2 kitap'));
    });

    test('para hedefi', () {
      final p = SmartPlanner.parse('1 yılda 120000 TL biriktirmek istiyorum')!;
      expect(p.isMoney, true);
      expect(p.amount, 12000000);
      expect(p.days, 365);
      final r = SmartPlanner.compute(p);
      expect(r.months, 12);
    });

    test('"50 bin TL" çarpanı', () {
      expect(SmartPlanner.parse('10 ayda 50 bin tl')!.amount, 5000000);
    });

    test('anlaşılmayan cümle', () {
      expect(SmartPlanner.parse('daha çok kitap okumak istiyorum'), null);
      expect(SmartPlanner.parse('6 ayda kitap okumak'), null);
    });
  });

  group('ProgressLogic', () {
    test('eşit ve ağırlıklı ilerleme', () {
      final steps = [step(true), step(false), step(false), step(true)];
      expect(ProgressLogic.dreamProgress(steps, weighted: false), 0.5);
      final w = [step(true, weight: 3), step(false, weight: 1)];
      expect(ProgressLogic.dreamProgress(w, weighted: true), 0.75);
      expect(ProgressLogic.dreamProgress(const [], weighted: false), 0);
      expect(ProgressLogic.dreamProgress(const [], weighted: false, completed: true), 1);
    });

    test('bahçe aşamaları', () {
      expect(ProgressLogic.gardenStage(0, completed: false), 0);
      expect(ProgressLogic.gardenStage(0.1, completed: false), 1);
      expect(ProgressLogic.gardenStage(0.5, completed: false), 3);
      expect(ProgressLogic.gardenStage(1, completed: false), 5);
      expect(ProgressLogic.gardenStage(0.2, completed: true), 6);
    });

    test('alışkanlık serisi planlanmamış günleri atlar', () {
      final today = DateTime(2026, 11, 18); // Çarşamba
      final days = {DateTime(2026, 11, 18), DateTime(2026, 11, 16), DateTime(2026, 11, 13)};
      // Pzt, Çar, Cum (mask: bit0, bit2, bit4)
      const mask = 1 | 4 | 16;
      expect(ProgressLogic.habitStreak(days, mask, today: today), 3);
      expect(ProgressLogic.habitStreak(days, 127, today: today), 1);
      expect(ProgressLogic.bestStreak(days, mask), 3);
    });

    test('seri bugün işaretlenmediyse dünden sayar', () {
      final today = DateTime(2026, 11, 18);
      final days = {DateTime(2026, 11, 17), DateTime(2026, 11, 16)};
      expect(ProgressLogic.habitStreak(days, 127, today: today), 2);
    });

    test('seviye', () {
      expect(ProgressLogic.level(0).level, 1);
      expect(ProgressLogic.level(99).level, 1);
      expect(ProgressLogic.level(100).level, 2);
      expect(ProgressLogic.level(250).level, 3);
      expect(ProgressLogic.level(260).intoLevel, 10);
    });
  });

  group('ReminderLogic', () {
    final now = DateTime(2026, 11, 18, 12, 0);
    test('tek seferlik: geçmişteyse planlanmaz', () {
      expect(ReminderLogic.nextOccurrence(rem('none', DateTime(2026, 11, 18, 11)), now), null);
      expect(ReminderLogic.nextOccurrence(rem('none', DateTime(2026, 11, 19, 9)), now), DateTime(2026, 11, 19, 9));
    });
    test('günlük', () {
      expect(ReminderLogic.nextOccurrence(rem('daily', DateTime(2026, 1, 1, 9)), now), DateTime(2026, 11, 19, 9));
      expect(ReminderLogic.nextOccurrence(rem('daily', DateTime(2026, 1, 1, 20)), now), DateTime(2026, 11, 18, 20));
    });
    test('haftalık maske', () {
      // Yalnızca Cuma (bit4)
      expect(ReminderLogic.nextOccurrence(rem('weekly', DateTime(2026, 1, 1, 9), mask: 16), now), DateTime(2026, 11, 20, 9));
    });
    test('aylık 31 çeken günü atlamaz', () {
      expect(ReminderLogic.nextOccurrence(rem('monthly', DateTime(2026, 1, 31, 9)), now), DateTime(2026, 12, 31, 9));
    });
    test('yıllık', () {
      expect(ReminderLogic.nextOccurrence(rem('yearly', DateTime(1990, 3, 5, 9)), now), DateTime(2027, 3, 5, 9));
    });
    test('iOS sınırı: en yakın 60 planlanır', () {
      final many = [for (var i = 1; i <= 100; i++) rem('none', now.add(Duration(hours: i)))];
      final plan = ReminderLogic.plan(many, now);
      expect(plan.length, ReminderLogic.maxPending);
      expect(plan.first.$2, now.add(const Duration(hours: 1)));
    });
  });

  group('Görev tekrarı ve tarihler', () {
    test('sonraki tarih', () {
      expect(GoalRepository.nextDue(DateTime(2026, 1, 31), 'monthly'), DateTime(2026, 2, 28));
      expect(GoalRepository.nextDue(DateTime(2026, 1, 1), 'weekly'), DateTime(2026, 1, 8));
    });
    test('hafta Pazartesi başlar', () {
      expect(Dates.startOfWeek(DateTime(2026, 11, 22)), DateTime(2026, 11, 16));
    });
  });
}
