import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/services/crypto_service.dart';
import '../core/services/media_service.dart';
import '../core/utils/dates.dart';
import 'gamification_repository.dart';
import 'reminder_repository.dart';

/// Hayal Günlüğüm ve Gelecekteki Ben (zaman kapsülü).
///
/// * Özel günlüklerin metni veri anahtarıyla şifrelenir.
/// * Kapsül mektubu, hayal listesi ve hedefler HER ZAMAN şifrelenir ve
///   açılış tarihinden önce arayüzde çözülmez. Not: Cihaz saati kullanıcı
///   tarafından değiştirilebilir; bu kilit bir sürpriz mekanizmasıdır,
///   kurcalamaya karşı dayanıklı (tamper-proof) bir güvenlik önlemi değildir.
class JournalRepository {
  JournalRepository(this.db, this.crypto, this.media, this.game, this.reminders);

  final AppDatabase db;
  final CryptoService crypto;
  final MediaService media;
  final GamificationRepository game;
  final ReminderRepository reminders;

  Stream<List<JournalEntry>> watchEntries() =>
      (db.select(db.journalEntries)..orderBy([(e) => OrderingTerm.desc(e.date), (e) => OrderingTerm.desc(e.id)])).watch();

  Stream<JournalEntry?> watchEntry(int id) =>
      (db.select(db.journalEntries)..where((e) => e.id.equals(id))).watchSingleOrNull();

  Stream<List<JournalMediaItem>> watchEntryMedia(int entryId) =>
      (db.select(db.journalMedia)..where((m) => m.entryId.equals(entryId))).watch();

  Future<String> readableBody(JournalEntry e) => crypto.decryptOrPlaceholder(e.body);

  Future<int> saveEntry({
    int? id,
    required DateTime date,
    required String title,
    required String body,
    required int mood,
    required List<String> tags,
    required bool important,
    required bool private,
    List<(String kind, String path, int? durationMs)> newMedia = const [],
  }) async {
    final storedBody = private ? await crypto.encrypt(body) : body;
    final comp = JournalEntriesCompanion(
      date: Value(Dates.dayOf(date)),
      title: Value(title.trim()),
      body: Value(storedBody),
      mood: Value(mood),
      tags: Value(tags.map((t) => t.trim()).where((t) => t.isNotEmpty).join(',')),
      isImportant: Value(important),
      isPrivate: Value(private),
      updatedAt: Value(DateTime.now()),
    );
    late int entryId;
    await db.transaction(() async {
      if (id == null) {
        entryId = await db.into(db.journalEntries).insert(comp);
      } else {
        entryId = id;
        await (db.update(db.journalEntries)..where((e) => e.id.equals(id))).write(comp);
      }
      for (final m in newMedia) {
        await db.into(db.journalMedia).insert(JournalMediaCompanion.insert(
              entryId: Value(entryId),
              kind: m.$1,
              path: m.$2,
              durationMs: Value(m.$3),
            ));
      }
    });
    if (id == null) await game.award('journal', entryId, 'Günlük yazıldı', Points.journal);
    return entryId;
  }

  Future<void> deleteEntry(JournalEntry e) async {
    final ms = await (db.select(db.journalMedia)..where((m) => m.entryId.equals(e.id))).get();
    await (db.delete(db.journalEntries)..where((x) => x.id.equals(e.id))).go();
    for (final m in ms) {
      await media.delete(m.path);
    }
  }

  Future<void> deleteMedia(JournalMediaItem m) async {
    await (db.delete(db.journalMedia)..where((x) => x.id.equals(m.id))).go();
    await media.delete(m.path);
  }

  /// Arama: başlık ve etiketlerde SQL ile; şifresiz metinlerde gövdede.
  /// Özel (şifreli) günlüklerin gövdesi yalnızca kilit açıkken aranır.
  Future<List<JournalEntry>> search(String query, {required bool includePrivateBodies}) async {
    final q = query.toLowerCase().trim();
    final all = await (db.select(db.journalEntries)..orderBy([(e) => OrderingTerm.desc(e.date)])).get();
    if (q.isEmpty) return all;
    final out = <JournalEntry>[];
    for (final e in all) {
      if (e.title.toLowerCase().contains(q) || e.tags.toLowerCase().contains(q)) {
        out.add(e);
        continue;
      }
      if (!e.isPrivate) {
        if (e.body.toLowerCase().contains(q)) out.add(e);
      } else if (includePrivateBodies) {
        final b = await crypto.decryptOrPlaceholder(e.body);
        if (b.toLowerCase().contains(q)) out.add(e);
      }
    }
    return out;
  }

  // ---------------------------- Zaman kapsülü ----------------------------
  Stream<List<TimeCapsule>> watchCapsules() =>
      (db.select(db.timeCapsules)..orderBy([(c) => OrderingTerm.asc(c.openAt)])).watch();

  Stream<List<JournalMediaItem>> watchCapsuleMedia(int capsuleId) =>
      (db.select(db.journalMedia)..where((m) => m.capsuleId.equals(capsuleId))).watch();

  static bool isOpenable(TimeCapsule c, [DateTime? now]) => !(now ?? DateTime.now()).isBefore(c.openAt);

  Future<int> createCapsule({
    required String title,
    required String letter,
    required String dreams,
    required String goals,
    required DateTime openAt,
    List<(String kind, String path, int? durationMs)> mediaItems = const [],
  }) async {
    final id = await db.transaction(() async {
      final id = await db.into(db.timeCapsules).insert(TimeCapsulesCompanion.insert(
            title: title.trim(),
            letter: Value(await crypto.encrypt(letter)),
            dreamsSnapshot: Value(await crypto.encrypt(dreams)),
            goalsText: Value(await crypto.encrypt(goals)),
            openAt: openAt,
          ));
      for (final m in mediaItems) {
        await db.into(db.journalMedia).insert(JournalMediaCompanion.insert(
              capsuleId: Value(id),
              kind: m.$1,
              path: m.$2,
              durationMs: Value(m.$3),
            ));
      }
      return id;
    });
    await reminders.upsertFor(
      kind: 'capsule',
      refId: id,
      title: 'Zaman kapsülün açılmaya hazır! ⏳',
      body: '"$title" — geçmişteki sen sana bir mesaj bıraktı.',
      at: openAt,
    );
    await game.evaluate();
    return id;
  }

  /// Tarih gelmeden içerik çözülmez.
  Future<({String letter, String dreams, String goals})?> openCapsule(TimeCapsule c) async {
    if (!isOpenable(c)) return null;
    if (c.openedAt == null) {
      await (db.update(db.timeCapsules)..where((x) => x.id.equals(c.id)))
          .write(TimeCapsulesCompanion(openedAt: Value(DateTime.now())));
    }
    return (
      letter: await crypto.decryptOrPlaceholder(c.letter),
      dreams: await crypto.decryptOrPlaceholder(c.dreamsSnapshot),
      goals: await crypto.decryptOrPlaceholder(c.goalsText),
    );
  }

  Future<void> deleteCapsule(TimeCapsule c) async {
    final ms = await (db.select(db.journalMedia)..where((m) => m.capsuleId.equals(c.id))).get();
    await (db.delete(db.timeCapsules)..where((x) => x.id.equals(c.id))).go();
    for (final m in ms) {
      await media.delete(m.path);
    }
    await reminders.removeFor('capsule', c.id);
  }
}
