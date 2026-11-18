import 'package:drift/drift.dart';

import '../core/database/app_database.dart';
import '../core/logic/achievements_catalog.dart';
import '../core/logic/progress_logic.dart';
import '../core/services/crypto_service.dart';
import '../core/services/media_service.dart';
import 'gamification_repository.dart';
import 'reminder_repository.dart';

class DreamWithCategory {
  DreamWithCategory(this.dream, this.category);
  final Dream dream;
  final DreamCategory category;
}

/// Hayaller, yolculuk adımları, medya ve Hayal Bahçem ağaçları.
class DreamRepository {
  DreamRepository(this.db, this.crypto, this.media, this.game, this.reminders);

  final AppDatabase db;
  final CryptoService crypto;
  final MediaService media;
  final GamificationRepository game;
  final ReminderRepository reminders;

  // ------------------------------ Okuma ----------------------------------
  Stream<List<DreamWithCategory>> watchDreams({bool archived = false}) {
    final q = db.select(db.dreams).join([
      innerJoin(db.dreamCategories, db.dreamCategories.id.equalsExp(db.dreams.categoryId)),
    ])
      ..where(archived ? db.dreams.archivedAt.isNotNull() : db.dreams.archivedAt.isNull())
      ..orderBy([OrderingTerm.desc(db.dreams.createdAt)]);
    return q.watch().map((rows) => rows
        .map((r) => DreamWithCategory(r.readTable(db.dreams), r.readTable(db.dreamCategories)))
        .toList());
  }

  Stream<List<DreamMilestone>> watchAllMilestones() => db.select(db.dreamMilestones).watch();

  Stream<List<DreamMilestone>> watchMilestones(int dreamId) => (db.select(db.dreamMilestones)
        ..where((m) => m.dreamId.equals(dreamId))
        ..orderBy([(m) => OrderingTerm.asc(m.sortOrder), (m) => OrderingTerm.asc(m.id)]))
      .watch();

  Future<Dream> getDream(int id) => (db.select(db.dreams)..where((d) => d.id.equals(id))).getSingle();

  Stream<Dream?> watchDream(int id) =>
      (db.select(db.dreams)..where((d) => d.id.equals(id))).watchSingleOrNull();

  Stream<List<DreamCategory>> watchCategories() =>
      (db.select(db.dreamCategories)..orderBy([(c) => OrderingTerm.asc(c.sortOrder)])).watch();

  Stream<List<DreamMediaItem>> watchDreamMedia(int dreamId) => (db.select(db.dreamMedia)
        ..where((m) => m.dreamId.equals(dreamId) & m.kind.equals('image'))
        ..orderBy([(m) => OrderingTerm.asc(m.sortOrder)]))
      .watch();

  /// Gizli hayallerin açıklama/notları şifre çözülmüş olarak döner.
  Future<Dream> readable(Dream d) async {
    if (!d.isHidden) return d;
    return d.copyWith(
      description: await crypto.decryptOrPlaceholder(d.description),
      notes: await crypto.decryptOrPlaceholder(d.notes),
    );
  }

  // ------------------------------ Yazma ----------------------------------
  Future<int> createDream({
    required String title,
    required int categoryId,
    String description = '',
    String? coverPath,
    DateTime? targetDate,
    int priority = 1,
    String motivation = '',
    String notes = '',
    int? budgetMinor,
    String currency = 'TRY',
    bool hidden = false,
    bool weighted = false,
    List<String> steps = const [],
  }) async {
    final id = await db.transaction(() async {
      final id = await db.into(db.dreams).insert(DreamsCompanion.insert(
            title: title.trim(),
            categoryId: categoryId,
            description: Value(hidden ? await crypto.encrypt(description) : description),
            notes: Value(hidden ? await crypto.encrypt(notes) : notes),
            coverPath: Value(coverPath),
            targetDate: Value(targetDate),
            priority: Value(priority),
            motivation: Value(motivation),
            budgetMinor: Value(budgetMinor),
            currency: Value(currency),
            isHidden: Value(hidden),
            weightedProgress: Value(weighted),
          ));
      final all = ['Hayalimi oluşturdum', ...steps.where((s) => s.trim().isNotEmpty)];
      for (var i = 0; i < all.length; i++) {
        await db.into(db.dreamMilestones).insert(DreamMilestonesCompanion.insert(
              dreamId: id,
              title: all[i],
              sortOrder: Value(i),
              isCompleted: Value(i == 0),
              completedAt: Value(i == 0 ? DateTime.now() : null),
            ));
      }
      await _ensureTree(id, categoryId);
      return id;
    });
    await _syncTargetReminder(id, title, targetDate);
    await syncTree(id);
    await game.award('dream_created', id, 'Yeni hayal: $title', 5);
    return id;
  }

  Future<void> updateDream(Dream d) async {
    final toSave = d.isHidden
        ? d.copyWith(description: await crypto.encrypt(d.description), notes: await crypto.encrypt(d.notes))
        : d.copyWith(
            description: await crypto.decryptOrPlaceholder(d.description),
            notes: await crypto.decryptOrPlaceholder(d.notes),
          );
    await db.update(db.dreams).replace(toSave.copyWith(updatedAt: DateTime.now()));
    await _syncTargetReminder(d.id, d.title, d.targetDate, completed: d.isCompleted);
    await syncTree(d.id);
  }

  Future<void> setCompleted(Dream d, bool done) async {
    await (db.update(db.dreams)..where((x) => x.id.equals(d.id))).write(DreamsCompanion(
      isCompleted: Value(done),
      completedAt: Value(done ? DateTime.now() : null),
      updatedAt: Value(DateTime.now()),
    ));
    if (done) {
      await game.award('dream', d.id, 'Hayal gerçekleşti: ${d.title}', Points.dream);
      await reminders.removeFor('dream', d.id);
    } else {
      await game.revoke('dream', d.id);
      await _syncTargetReminder(d.id, d.title, d.targetDate);
    }
    await syncTree(d.id);
  }

  Future<void> archive(int id) async {
    await (db.update(db.dreams)..where((d) => d.id.equals(id)))
        .write(DreamsCompanion(archivedAt: Value(DateTime.now())));
    await reminders.removeFor('dream', id);
  }

  Future<void> restore(int id) async {
    await (db.update(db.dreams)..where((d) => d.id.equals(id))).write(const DreamsCompanion(archivedAt: Value(null)));
    final d = await (db.select(db.dreams)..where((x) => x.id.equals(id))).getSingle();
    await _syncTargetReminder(id, d.title, d.targetDate, completed: d.isCompleted);
  }

  /// Kalıcı silme: adımlar, medya ve ağaç silinir; hedef/görev/kumbara
  /// kayıtları korunur (bağlantıları kaldırılır). Dosyalar da temizlenir.
  Future<void> deletePermanently(int id) async {
    final d = await (db.select(db.dreams)..where((x) => x.id.equals(id))).getSingleOrNull();
    if (d == null) return;
    final mediaRows = await (db.select(db.dreamMedia)..where((m) => m.dreamId.equals(id))).get();
    final steps = await (db.select(db.dreamMilestones)..where((m) => m.dreamId.equals(id))).get();
    await db.transaction(() async {
      for (final s in steps) {
        await (db.delete(db.reminders)..where((r) => r.kind.equals('milestone') & r.refId.equals(s.id))).go();
      }
      await (db.delete(db.reminders)..where((r) => r.kind.equals('dream') & r.refId.equals(id))).go();
      await (db.delete(db.dreams)..where((x) => x.id.equals(id))).go();
    });
    await media.delete(d.coverPath);
    for (final m in mediaRows) {
      await media.delete(m.path);
    }
    for (final s in steps) {
      await media.delete(s.photoPath);
    }
    await reminders.resync();
  }

  // --------------------------- Yolculuk adımları -------------------------
  Future<void> addMilestone(int dreamId, String title, {DateTime? targetDate, int weight = 1}) async {
    final maxOrder = await (db.selectOnly(db.dreamMilestones)
          ..addColumns([db.dreamMilestones.sortOrder.max()])
          ..where(db.dreamMilestones.dreamId.equals(dreamId)))
        .map((r) => r.read(db.dreamMilestones.sortOrder.max()))
        .getSingle();
    await db.into(db.dreamMilestones).insert(DreamMilestonesCompanion.insert(
          dreamId: dreamId,
          title: title.trim(),
          targetDate: Value(targetDate),
          weight: Value(weight),
          sortOrder: Value((maxOrder ?? -1) + 1),
        ));
    await syncTree(dreamId);
  }

  Future<void> updateMilestone(DreamMilestone m) async {
    await db.update(db.dreamMilestones).replace(m);
    if (m.reminderAt != null && !m.isCompleted && m.reminderAt!.isAfter(DateTime.now())) {
      await reminders.upsertFor(
        kind: 'milestone',
        refId: m.id,
        title: 'Hayal adımı: ${m.title}',
        body: 'Bugün bu adım için küçük bir şey yapmaya ne dersin?',
        at: m.reminderAt!,
      );
    } else {
      await reminders.removeFor('milestone', m.id);
    }
    await syncTree(m.dreamId);
  }

  Future<void> toggleMilestone(DreamMilestone m) async {
    final done = !m.isCompleted;
    await (db.update(db.dreamMilestones)..where((x) => x.id.equals(m.id))).write(DreamMilestonesCompanion(
      isCompleted: Value(done),
      completedAt: Value(done ? DateTime.now() : null),
    ));
    if (done) {
      await game.award('milestone', m.id, 'Adım tamamlandı: ${m.title}', Points.milestone);
      await reminders.removeFor('milestone', m.id);
    } else {
      await game.revoke('milestone', m.id);
    }
    await syncTree(m.dreamId);
  }

  Future<void> deleteMilestone(DreamMilestone m) async {
    await (db.delete(db.dreamMilestones)..where((x) => x.id.equals(m.id))).go();
    await reminders.removeFor('milestone', m.id);
    await media.delete(m.photoPath);
    await syncTree(m.dreamId);
  }

  Future<void> reorderMilestones(List<DreamMilestone> ordered) async {
    await db.batch((b) {
      for (var i = 0; i < ordered.length; i++) {
        b.update(db.dreamMilestones, DreamMilestonesCompanion(sortOrder: Value(i)),
            where: (t) => t.id.equals(ordered[i].id));
      }
    });
  }

  // ------------------------------- Medya ---------------------------------
  Future<void> addDreamImages(int dreamId, List<String> paths, {bool onBoard = true}) async {
    if (paths.isEmpty) return;
    final dream = await (db.select(db.dreams)..where((d) => d.id.equals(dreamId))).getSingle();
    final cat = await (db.select(db.dreamCategories)..where((c) => c.id.equals(dream.categoryId))).getSingle();
    await db.batch((b) {
      for (final p in paths) {
        b.insert(
          db.dreamMedia,
          DreamMediaCompanion.insert(
            dreamId: Value(dreamId),
            path: Value(p),
            boardCategory: Value(cat.name),
            showOnBoard: Value(onBoard && !dream.isHidden),
            sortOrder: Value(DateTime.now().millisecondsSinceEpoch ~/ 1000),
          ),
        );
      }
    });
  }

  Future<void> deleteMedia(DreamMediaItem m) async {
    await (db.delete(db.dreamMedia)..where((x) => x.id.equals(m.id))).go();
    await media.delete(m.path);
  }

  // ---------------------------- Kategoriler ------------------------------
  Future<int> addCategory(String name, String iconKey, int color, String plant) async {
    final count = await db.select(db.dreamCategories).get();
    return db.into(db.dreamCategories).insert(DreamCategoriesCompanion.insert(
          name: name.trim(),
          iconKey: Value(iconKey),
          color: color,
          plantType: Value(plant),
          sortOrder: Value(count.length),
        ));
  }

  /// Kategori silinirken hayalleri "Diğer" kategorisine taşınır.
  Future<void> deleteCategory(DreamCategory c) async {
    if (c.isBuiltIn) return;
    final fallback = await db.fallbackCategoryId();
    await db.transaction(() async {
      await (db.update(db.dreams)..where((d) => d.categoryId.equals(c.id)))
          .write(DreamsCompanion(categoryId: Value(fallback)));
      await (db.delete(db.dreamCategories)..where((x) => x.id.equals(c.id))).go();
    });
  }

  // ----------------------------- Bahçe -----------------------------------
  Future<void> _ensureTree(int dreamId, int categoryId) async {
    final existing = await (db.select(db.gardenTrees)..where((t) => t.dreamId.equals(dreamId))).getSingleOrNull();
    if (existing != null) return;
    final cat = await (db.select(db.dreamCategories)..where((c) => c.id.equals(categoryId))).getSingle();
    final maxSlot = await (db.selectOnly(db.gardenTrees)..addColumns([db.gardenTrees.slot.max()]))
        .map((r) => r.read(db.gardenTrees.slot.max()))
        .getSingle();
    await db.into(db.gardenTrees).insert(GardenTreesCompanion.insert(
          dreamId: dreamId,
          plantType: cat.plantType,
          slot: (maxSlot ?? -1) + 1,
        ));
  }

  /// İlerlemeye göre ağacın aşamasını günceller. Ağaç büyüdüyse true.
  Future<bool> syncTree(int dreamId) async {
    final d = await (db.select(db.dreams)..where((x) => x.id.equals(dreamId))).getSingleOrNull();
    if (d == null) return false;
    await _ensureTree(dreamId, d.categoryId);
    final steps = await (db.select(db.dreamMilestones)..where((m) => m.dreamId.equals(dreamId))).get();
    final p = ProgressLogic.dreamProgress(steps, weighted: d.weightedProgress, completed: d.isCompleted);
    final stage = ProgressLogic.gardenStage(p, completed: d.isCompleted);
    final tree = await (db.select(db.gardenTrees)..where((t) => t.dreamId.equals(dreamId))).getSingle();
    if (tree.stage == stage) return false;
    await (db.update(db.gardenTrees)..where((t) => t.id.equals(tree.id))).write(GardenTreesCompanion(
      stage: Value(stage),
      lastGrowthAt: Value(stage > tree.stage ? DateTime.now() : tree.lastGrowthAt),
    ));
    return stage > tree.stage;
  }

  Stream<List<(GardenTree, Dream, DreamCategory)>> watchGarden() {
    final q = db.select(db.gardenTrees).join([
      innerJoin(db.dreams, db.dreams.id.equalsExp(db.gardenTrees.dreamId)),
      innerJoin(db.dreamCategories, db.dreamCategories.id.equalsExp(db.dreams.categoryId)),
    ])
      ..where(db.dreams.archivedAt.isNull())
      ..orderBy([OrderingTerm.asc(db.gardenTrees.slot)]);
    return q.watch().map((rows) => rows
        .map((r) => (r.readTable(db.gardenTrees), r.readTable(db.dreams), r.readTable(db.dreamCategories)))
        .toList());
  }

  Future<void> _syncTargetReminder(int id, String title, DateTime? target, {bool completed = false}) async {
    if (target == null || completed) {
      await reminders.removeFor('dream', id);
      return;
    }
    // Hedef tarihten 3 gün önce saat 10:00'da hatırlat.
    final at = DateTime(target.year, target.month, target.day, 10).subtract(const Duration(days: 3));
    if (at.isBefore(DateTime.now())) {
      await reminders.removeFor('dream', id);
      return;
    }
    await reminders.upsertFor(
      kind: 'dream',
      refId: id,
      title: 'Hayaline 3 gün kaldı: $title',
      body: 'Hedef tarihin yaklaşıyor. Bir adım daha atmaya hazır mısın?',
      at: at,
    );
  }
}
