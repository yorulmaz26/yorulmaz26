import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

import 'tables.dart';

export 'tables.dart';

part 'app_database.g.dart';

const kDatabaseName = 'hayallerim_cepte';

/// Varsayılan hayal kategorileri: (ad, ikon, renk, bitki türü)
const kDefaultCategories = <(String, String, int, String)>[
  ('Ev ve yaşam', 'home', 0xFF6D5DFB, 'oak'),
  ('Araba', 'car', 0xFF3B82F6, 'pine'),
  ('Seyahat', 'flight', 0xFF20B8A6, 'palm'),
  ('Eğitim', 'school', 0xFFF97316, 'maple'),
  ('Kariyer', 'work', 0xFF101827, 'pine'),
  ('Finans', 'savings', 0xFFD6AD60, 'lemon'),
  ('Sağlık', 'health', 0xFF2EAD6B, 'apple'),
  ('Aile ve ilişkiler', 'family', 0xFFEF6C8F, 'sakura'),
  ('Kişisel gelişim', 'growth', 0xFF8B5CF6, 'sunflower'),
  ('Hobiler', 'palette', 0xFFEC4899, 'sakura'),
  ('Diğer', 'star', 0xFF64748B, 'oak'),
];

@DriftDatabase(tables: [
  UserProfiles,
  AppSettings,
  DreamCategories,
  Dreams,
  DreamMilestones,
  DreamMedia,
  Goals,
  Tasks,
  Habits,
  HabitCompletions,
  SavingsGoals,
  SavingsTransactions,
  JournalEntries,
  JournalMedia,
  TimeCapsules,
  GardenTrees,
  Achievements,
  FocusSessions,
  LifeBalanceEntries,
  Reminders,
  ActivityLog,
])
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? executor]) : super(executor ?? _open());

  static QueryExecutor _open() => driftDatabase(name: kDatabaseName);

  /// Şema sürümü. Her şema değişikliğinde artırılır ve [migration] içinde
  /// `from < N` adımı eklenir. Yedek dosyaları da bu sürümü taşır.
  @override
  int get schemaVersion => 1;

  /// Yedekleme/geri yüklemede tabloların bağımlılık sırası (ebeveyn önce).
  static const tableOrder = <String>[
    'user_profiles',
    'app_settings',
    'dream_categories',
    'dreams',
    'dream_milestones',
    'dream_media',
    'goals',
    'tasks',
    'habits',
    'habit_completions',
    'savings_goals',
    'savings_transactions',
    'journal_entries',
    'time_capsules',
    'journal_media',
    'garden_trees',
    'achievements',
    'focus_sessions',
    'life_balance_entries',
    'reminders',
    'activity_log',
  ];

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) async {
          await m.createAll();
          await seedDefaults();
        },
        onUpgrade: (m, from, to) async {
          // Gelecek sürümler için şablon:
          // if (from < 2) { await m.addColumn(dreams, dreams.yeniAlan); }
        },
        beforeOpen: (details) async {
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );

  Future<void> seedDefaults() async {
    await into(userProfiles).insert(
      UserProfilesCompanion.insert(),
      mode: InsertMode.insertOrIgnore,
    );
    final existing = await select(dreamCategories).get();
    if (existing.isNotEmpty) return;
    await batch((b) {
      for (var i = 0; i < kDefaultCategories.length; i++) {
        final c = kDefaultCategories[i];
        b.insert(
          dreamCategories,
          DreamCategoriesCompanion.insert(
            name: c.$1,
            iconKey: Value(c.$2),
            color: c.$3,
            plantType: Value(c.$4),
            isBuiltIn: const Value(true),
            sortOrder: Value(i),
          ),
        );
      }
    });
  }

  /// "Diğer" kategorisinin id'si (silinen kategorilerin hayalleri buraya taşınır).
  Future<int> fallbackCategoryId() async {
    final row = await (select(dreamCategories)
          ..where((c) => c.name.equals('Diğer') & c.isBuiltIn.equals(true))
          ..limit(1))
        .getSingleOrNull();
    if (row != null) return row.id;
    return into(dreamCategories).insert(
      DreamCategoriesCompanion.insert(
        name: 'Diğer',
        color: 0xFF64748B,
        isBuiltIn: const Value(true),
        sortOrder: const Value(99),
      ),
    );
  }

  // ----------------------------- Ayarlar ---------------------------------
  Future<String?> getSetting(String key) async {
    final row = await (select(appSettings)..where((s) => s.key.equals(key))).getSingleOrNull();
    return row?.value;
  }

  Future<void> setSetting(String key, String value) =>
      into(appSettings).insertOnConflictUpdate(AppSettingsCompanion.insert(key: key, value: value));

  Future<Map<String, String>> allSettings() async {
    final rows = await select(appSettings).get();
    return {for (final r in rows) r.key: r.value};
  }

  // ------------------------- Bütünlük kontrolü ----------------------------
  /// SQLite bütünlük ve yabancı anahtar kontrolü. Sorun yoksa boş liste döner.
  Future<List<String>> integrityProblems() async {
    final problems = <String>[];
    final ic = await customSelect('PRAGMA integrity_check').get();
    for (final r in ic) {
      final v = r.data.values.first?.toString() ?? '';
      if (v.toLowerCase() != 'ok') problems.add(v);
    }
    final fk = await customSelect('PRAGMA foreign_key_check').get();
    for (final r in fk) {
      problems.add('Yabancı anahtar hatası: ${r.data}');
    }
    return problems;
  }
}
