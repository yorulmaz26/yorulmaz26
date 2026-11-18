import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/services/media_service.dart';
import 'gamification_repository.dart';

/// Odaklanma (Pomodoro) oturumları.
class FocusRepository {
  FocusRepository(this.db, this.game);
  final AppDatabase db;
  final GamificationRepository game;

  Stream<List<FocusSession>> watchSessions({DateTime? since}) => (db.select(db.focusSessions)
        ..where((f) => since == null ? const Constant(true) : f.startedAt.isBiggerOrEqualValue(since))
        ..orderBy([(f) => OrderingTerm.desc(f.startedAt)]))
      .watch();

  Future<void> record({
    required DateTime startedAt,
    required DateTime endedAt,
    required int plannedSeconds,
    required int actualSeconds,
    required String kind,
    required bool completed,
    int? goalId,
    int? dreamId,
  }) async {
    if (actualSeconds < 60) return; // 1 dakikadan kısa oturumlar kaydedilmez
    final id = await db.into(db.focusSessions).insert(FocusSessionsCompanion.insert(
          startedAt: startedAt,
          endedAt: endedAt,
          plannedSeconds: plannedSeconds,
          actualSeconds: actualSeconds,
          kind: Value(kind),
          completed: Value(completed),
          goalId: Value(goalId),
          dreamId: Value(dreamId),
        ));
    if (kind == 'work' && completed) {
      await game.award('focus', id, 'Odak oturumu (${actualSeconds ~/ 60} dk)', Points.focus);
    }
  }
}

/// Yaşam Dengem değerlendirmeleri.
class BalanceRepository {
  BalanceRepository(this.db, this.game);
  final AppDatabase db;
  final GamificationRepository game;

  static const areas = ['Sağlık', 'Finans', 'Kariyer', 'Eğitim', 'Aile', 'Sosyal yaşam', 'Kişisel gelişim', 'Hobiler'];

  static List<int> values(LifeBalanceEntry e) =>
      [e.health, e.finance, e.career, e.education, e.family, e.social, e.growth, e.hobbies];

  Stream<List<LifeBalanceEntry>> watchEntries() =>
      (db.select(db.lifeBalanceEntries)..orderBy([(e) => OrderingTerm.asc(e.createdAt)])).watch();

  Future<void> add(List<int> v, String note) async {
    if (v.length != 8 || v.any((x) => x < 1 || x > 10)) throw ArgumentError('Puanlar 1-10 arasında olmalı');
    final id = await db.into(db.lifeBalanceEntries).insert(LifeBalanceEntriesCompanion.insert(
          health: v[0],
          finance: v[1],
          career: v[2],
          education: v[3],
          family: v[4],
          social: v[5],
          growth: v[6],
          hobbies: v[7],
          note: Value(note),
        ));
    await game.award('balance', id, 'Yaşam dengesi değerlendirmesi', Points.balance);
  }

  Future<void> delete(LifeBalanceEntry e) => (db.delete(db.lifeBalanceEntries)..where((x) => x.id.equals(e.id))).go();
}

/// Profil.
class ProfileRepository {
  ProfileRepository(this.db, this.media);
  final AppDatabase db;
  final MediaService media;

  Stream<UserProfile> watch() => (db.select(db.userProfiles)..where((p) => p.id.equals(1))).watchSingleOrNull().map(
        (p) => p ?? UserProfile(id: 1, name: '', avatarPath: null, focusCategories: '', createdAt: DateTime.now()),
      );

  Future<void> save({String? name, String? avatarPath, bool clearAvatar = false, String? focusCategories}) async {
    final old = await (db.select(db.userProfiles)..where((p) => p.id.equals(1))).getSingleOrNull();
    if (old == null) await db.into(db.userProfiles).insert(UserProfilesCompanion.insert());
    if ((clearAvatar || avatarPath != null) && old?.avatarPath != null && old!.avatarPath != avatarPath) {
      await media.delete(old.avatarPath);
    }
    await (db.update(db.userProfiles)..where((p) => p.id.equals(1))).write(UserProfilesCompanion(
      name: name == null ? const Value.absent() : Value(name.trim()),
      avatarPath: clearAvatar ? const Value(null) : (avatarPath == null ? const Value.absent() : Value(avatarPath)),
      focusCategories: focusCategories == null ? const Value.absent() : Value(focusCategories),
    ));
  }
}

/// Hayal Panom (görsel pano).
class BoardRepository {
  BoardRepository(this.db, this.media);
  final AppDatabase db;
  final MediaService media;

  /// Gizli hayallere ait görseller panoda gösterilmez.
  Stream<List<DreamMediaItem>> watchBoard() {
    final q = db.select(db.dreamMedia).join([
      leftOuterJoin(db.dreams, db.dreams.id.equalsExp(db.dreamMedia.dreamId)),
    ])
      ..where(db.dreamMedia.showOnBoard.equals(true) &
          (db.dreams.id.isNull() | (db.dreams.isHidden.equals(false) & db.dreams.archivedAt.isNull())))
      ..orderBy([OrderingTerm.asc(db.dreamMedia.sortOrder), OrderingTerm.desc(db.dreamMedia.id)]);
    return q.watch().map((rows) => rows.map((r) => r.readTable(db.dreamMedia)).toList());
  }

  Future<void> addImages(List<String> paths, String category, {int? dreamId}) async {
    final base = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    await db.batch((b) {
      for (var i = 0; i < paths.length; i++) {
        b.insert(
          db.dreamMedia,
          DreamMediaCompanion.insert(
            dreamId: Value(dreamId),
            path: Value(paths[i]),
            boardCategory: Value(category),
            sortOrder: Value(base + i),
          ),
        );
      }
    });
  }

  Future<void> addQuote(String text, String category, int color) => db.into(db.dreamMedia).insert(DreamMediaCompanion.insert(
        kind: const Value('quote'),
        caption: Value(text.trim()),
        boardCategory: Value(category),
        color: Value(color),
        sortOrder: Value(DateTime.now().millisecondsSinceEpoch ~/ 1000),
      ));

  Future<void> update(DreamMediaItem m) => db.update(db.dreamMedia).replace(m);

  Future<void> reorder(List<DreamMediaItem> ordered) => db.batch((b) {
        for (var i = 0; i < ordered.length; i++) {
          b.update(db.dreamMedia, DreamMediaCompanion(sortOrder: Value(i)), where: (t) => t.id.equals(ordered[i].id));
        }
      });

  /// Panodan kaldırır. Bir hayale bağlı görselse hayal galerisinde kalır.
  Future<void> remove(DreamMediaItem m) async {
    if (m.dreamId != null) {
      await (db.update(db.dreamMedia)..where((x) => x.id.equals(m.id))).write(const DreamMediaCompanion(showOnBoard: Value(false)));
    } else {
      await (db.delete(db.dreamMedia)..where((x) => x.id.equals(m.id))).go();
      await media.delete(m.path);
    }
  }
}
