import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:hayallerim_cepte/core/database/app_database.dart';
import 'package:hayallerim_cepte/core/services/crypto_service.dart';
import 'package:hayallerim_cepte/core/services/lock_service.dart';
import 'package:hayallerim_cepte/core/services/media_service.dart';
import 'package:hayallerim_cepte/core/services/notification_service.dart';
import 'package:hayallerim_cepte/data/dream_repository.dart';
import 'package:hayallerim_cepte/data/gamification_repository.dart';
import 'package:hayallerim_cepte/data/goal_repository.dart';
import 'package:hayallerim_cepte/data/habit_repository.dart';
import 'package:hayallerim_cepte/data/journal_repository.dart';
import 'package:hayallerim_cepte/data/reminder_repository.dart';
import 'package:hayallerim_cepte/data/savings_repository.dart';

/// Bellek içi veritabanı ve geçici medya klasörüyle tam bir test ortamı.
class TestEnv {
  TestEnv._(this.db, this.crypto, this.media, this.dir);

  final AppDatabase db;
  final CryptoService crypto;
  final MediaService media;
  final Directory dir;

  late final notifications = NotificationService();
  late final reminders = ReminderRepository(db, notifications);
  late final game = GamificationRepository(db);
  late final dreams = DreamRepository(db, crypto, media, game, reminders);
  late final goals = GoalRepository(db, game, reminders);
  late final habits = HabitRepository(db, game, reminders);
  late final savings = SavingsRepository(db, game, reminders);
  late final journal = JournalRepository(db, crypto, media, game, reminders);

  static Future<TestEnv> create({KeyStore? keys}) async {
    driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;
    final dir = await Directory.systemTemp.createTemp('hc_test');
    final media = MediaService(baseDir: dir);
    await media.init();
    final db = AppDatabase(NativeDatabase.memory());
    return TestEnv._(db, CryptoService(keys ?? MemoryKeyStore()), media, dir);
  }

  Future<int> categoryId([String name = 'Seyahat']) async {
    final c = await (db.select(db.dreamCategories)..where((c) => c.name.equals(name))).getSingle();
    return c.id;
  }

  Future<void> dispose() async {
    game.dispose();
    await db.close();
    if (await dir.exists()) await dir.delete(recursive: true);
  }
}

class LockServiceHelper {
  static Future<void> setPin(KeyStore store, String pin) => LockService(store).setPin(pin);
}
