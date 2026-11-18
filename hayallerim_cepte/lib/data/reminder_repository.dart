import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/services/notification_service.dart';

/// Tüm hatırlatıcılar `reminders` tablosunda tutulur; bildirimler bu tablodan
/// türetilir. Böylece yedekten geri yüklemede bildirimler de geri gelir.
class ReminderRepository {
  ReminderRepository(this.db, this.notifications);

  final AppDatabase db;
  final NotificationService notifications;

  Stream<List<Reminder>> watchSpecialDays() => (db.select(db.reminders)
        ..where((r) => r.kind.equals('special'))
        ..orderBy([(r) => OrderingTerm.asc(r.at)]))
      .watch();

  Future<Reminder?> find(String kind, int refId) => (db.select(db.reminders)
        ..where((r) => r.kind.equals(kind) & r.refId.equals(refId))
        ..limit(1))
      .getSingleOrNull();

  /// (kind, refId) için tek bir hatırlatıcı tutar; varsa günceller.
  Future<void> upsertFor({
    required String kind,
    int? refId,
    required String title,
    String body = '',
    required DateTime at,
    String repeat = 'none',
    int? weekdaysMask,
    bool resyncNow = true,
  }) async {
    final existing = refId == null
        ? await (db.select(db.reminders)
              ..where((r) => r.kind.equals(kind) & r.refId.isNull())
              ..limit(1))
            .getSingleOrNull()
        : await find(kind, refId);
    final comp = RemindersCompanion(
      kind: Value(kind),
      refId: Value(refId),
      title: Value(title),
      body: Value(body),
      at: Value(at),
      repeat: Value(repeat),
      weekdaysMask: Value(weekdaysMask),
      enabled: const Value(true),
    );
    if (existing == null) {
      await db.into(db.reminders).insert(comp);
    } else {
      await (db.update(db.reminders)..where((r) => r.id.equals(existing.id))).write(comp);
    }
    if (resyncNow) await resync();
  }

  Future<void> removeFor(String kind, int refId, {bool resyncNow = true}) async {
    await (db.delete(db.reminders)..where((r) => r.kind.equals(kind) & r.refId.equals(refId))).go();
    if (resyncNow) await resync();
  }

  Future<void> removeKind(String kind) async {
    await (db.delete(db.reminders)..where((r) => r.kind.equals(kind))).go();
    await resync();
  }

  Future<int> addSpecialDay(String title, DateTime at) async {
    final id = await db.into(db.reminders).insert(RemindersCompanion.insert(
          kind: 'special',
          title: title,
          body: const Value('Bugün senin için önemli bir gün ✨'),
          at: at,
          repeat: const Value('yearly'),
        ));
    await resync();
    return id;
  }

  Future<void> deleteById(int id) async {
    await (db.delete(db.reminders)..where((r) => r.id.equals(id))).go();
    await resync();
  }

  /// Bildirimler kapalıysa her şey iptal edilir.
  Future<void> resync() async {
    final enabled = (await db.getSetting('notifications_enabled')) == 'true';
    if (!enabled) {
      await notifications.resync(const []);
      return;
    }
    final all = await db.select(db.reminders).get();
    await notifications.resync(all);
  }
}
