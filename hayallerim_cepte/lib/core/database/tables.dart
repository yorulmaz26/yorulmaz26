import 'package:drift/drift.dart';

// ---------------------------------------------------------------------------
// Hayallerim Cepte veri modeli.
//
// Silme stratejisi:
//  * Hayaller önce ARŞİVLENİR (archivedAt). Arşivden geri alınabilir.
//  * Kalıcı silmede: kilometre taşları, medya ve bahçe ağacı CASCADE silinir;
//    hedefler, görevler, kumbaralar ve odak oturumları SET NULL ile korunur.
//  * Kategoriler silinirken bağlı hayaller "Diğer" kategorisine taşınır
//    (uygulama katmanı); FK RESTRICT ile yanlışlıkla kayıp engellenir.
// Şifreli metin alanları "enc:v1:" önekiyle saklanır (bkz. CryptoService).
// Medya yolları uygulama medya klasörüne GÖRELİ saklanır (iOS kapsayıcı yolu
// güncellemelerde değişebildiği için).
// ---------------------------------------------------------------------------

@DataClassName('UserProfile')
class UserProfiles extends Table {
  IntColumn get id => integer().withDefault(const Constant(1))();
  TextColumn get name => text().withDefault(const Constant(''))();
  TextColumn get avatarPath => text().nullable()();
  TextColumn get focusCategories => text().withDefault(const Constant(''))(); // virgülle ayrılmış kategori id'leri
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('AppSetting')
class AppSettings extends Table {
  TextColumn get key => text()();
  TextColumn get value => text()();

  @override
  Set<Column> get primaryKey => {key};
}

@DataClassName('DreamCategory')
class DreamCategories extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get name => text().withLength(min: 1, max: 40)();
  TextColumn get iconKey => text().withDefault(const Constant('star'))();
  IntColumn get color => integer()();
  TextColumn get plantType => text().withDefault(const Constant('oak'))();
  BoolColumn get isBuiltIn => boolean().withDefault(const Constant(false))();
  IntColumn get sortOrder => integer().withDefault(const Constant(0))();
}

@DataClassName('Dream')
@TableIndex(name: 'idx_dreams_category', columns: {#categoryId})
@TableIndex(name: 'idx_dreams_status', columns: {#archivedAt, #isCompleted})
class Dreams extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().withLength(min: 1, max: 120)();
  TextColumn get description => text().withDefault(const Constant(''))();
  TextColumn get coverPath => text().nullable()();
  IntColumn get categoryId =>
      integer().references(DreamCategories, #id, onDelete: KeyAction.restrict)();
  DateTimeColumn get targetDate => dateTime().nullable()();
  IntColumn get priority => integer().withDefault(const Constant(1))();
  TextColumn get notes => text().withDefault(const Constant(''))();
  TextColumn get motivation => text().withDefault(const Constant(''))();
  IntColumn get budgetMinor => integer().nullable()();
  TextColumn get currency => text().withDefault(const Constant('TRY'))();
  BoolColumn get weightedProgress => boolean().withDefault(const Constant(false))();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  DateTimeColumn get completedAt => dateTime().nullable()();
  BoolColumn get isHidden => boolean().withDefault(const Constant(false))();
  DateTimeColumn get archivedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('DreamMilestone')
@TableIndex(name: 'idx_milestones_dream', columns: {#dreamId, #sortOrder})
class DreamMilestones extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get dreamId => integer().references(Dreams, #id, onDelete: KeyAction.cascade)();
  TextColumn get title => text().withLength(min: 1, max: 160)();
  TextColumn get description => text().withDefault(const Constant(''))();
  DateTimeColumn get targetDate => dateTime().nullable()();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  DateTimeColumn get completedAt => dateTime().nullable()();
  IntColumn get weight => integer().withDefault(const Constant(1))();
  TextColumn get photoPath => text().nullable()();
  TextColumn get note => text().withDefault(const Constant(''))();
  DateTimeColumn get reminderAt => dateTime().nullable()();
  IntColumn get sortOrder => integer().withDefault(const Constant(0))();
}

/// Hayal görselleri ve Hayal Panom öğeleri. kind: 'image' | 'quote'
@DataClassName('DreamMediaItem')
@TableIndex(name: 'idx_media_dream', columns: {#dreamId})
@TableIndex(name: 'idx_media_board', columns: {#showOnBoard, #sortOrder})
class DreamMedia extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.cascade)();
  TextColumn get kind => text().withDefault(const Constant('image'))();
  TextColumn get path => text().nullable()();
  TextColumn get caption => text().withDefault(const Constant(''))();
  TextColumn get boardCategory => text().withDefault(const Constant('Genel'))();
  BoolColumn get showOnBoard => boolean().withDefault(const Constant(true))();
  IntColumn get color => integer().nullable()();
  IntColumn get sortOrder => integer().withDefault(const Constant(0))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

/// period: 'daily' | 'weekly' | 'monthly' | 'yearly'
@DataClassName('Goal')
@TableIndex(name: 'idx_goals_dream', columns: {#dreamId})
class Goals extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().withLength(min: 1, max: 160)();
  TextColumn get description => text().withDefault(const Constant(''))();
  TextColumn get period => text().withDefault(const Constant('monthly'))();
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.setNull)();
  IntColumn get priority => integer().withDefault(const Constant(1))();
  DateTimeColumn get startDate => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get dueDate => dateTime().nullable()();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  DateTimeColumn get completedAt => dateTime().nullable()();
  DateTimeColumn get archivedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

/// repeatRule: 'none' | 'daily' | 'weekly' | 'monthly'
@DataClassName('TaskItem')
@TableIndex(name: 'idx_tasks_due', columns: {#isCompleted, #dueDate})
@TableIndex(name: 'idx_tasks_goal', columns: {#goalId})
class Tasks extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get goalId =>
      integer().nullable().references(Goals, #id, onDelete: KeyAction.cascade)();
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.setNull)();
  TextColumn get title => text().withLength(min: 1, max: 160)();
  TextColumn get note => text().withDefault(const Constant(''))();
  DateTimeColumn get dueDate => dateTime().nullable()();
  IntColumn get dueMinutes => integer().nullable()(); // gün içi saat (dakika)
  IntColumn get priority => integer().withDefault(const Constant(1))();
  TextColumn get repeatRule => text().withDefault(const Constant('none'))();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  DateTimeColumn get completedAt => dateTime().nullable()();
  BoolColumn get reminder => boolean().withDefault(const Constant(false))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('Habit')
class Habits extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().withLength(min: 1, max: 80)();
  TextColumn get iconKey => text().withDefault(const Constant('check'))();
  IntColumn get color => integer()();
  IntColumn get weekdaysMask => integer().withDefault(const Constant(127))(); // Pzt=bit0
  IntColumn get reminderMinutes => integer().nullable()();
  IntColumn get challengeDays => integer().nullable()(); // 7, 21, 30, 100
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.setNull)();
  DateTimeColumn get startDate => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get archivedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('HabitCompletion')
@TableIndex(name: 'idx_habit_completion_day', columns: {#day})
class HabitCompletions extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get habitId => integer().references(Habits, #id, onDelete: KeyAction.cascade)();
  DateTimeColumn get day => dateTime()(); // yerel gece yarısı
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  @override
  List<Set<Column>> get uniqueKeys => [
        {habitId, day},
      ];
}

@DataClassName('SavingsGoal')
class SavingsGoals extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().withLength(min: 1, max: 120)();
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.setNull)();
  IntColumn get targetMinor => integer()();
  TextColumn get currency => text().withDefault(const Constant('TRY'))();
  IntColumn get monthlyTargetMinor => integer().nullable()();
  DateTimeColumn get targetDate => dateTime().nullable()();
  BoolColumn get isCompleted => boolean().withDefault(const Constant(false))();
  DateTimeColumn get archivedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

/// kind: 'deposit' | 'withdraw' (kumbara bakiyesini etkiler)
///       'income'  | 'expense'  (gelir/gider kaydı; bakiyeyi etkilemez)
/// amountMinor her zaman pozitif kuruş cinsindendir; yön `kind` ile belirlenir.
@DataClassName('SavingsTransaction')
@TableIndex(name: 'idx_tx_goal_date', columns: {#savingsGoalId, #date})
class SavingsTransactions extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get savingsGoalId =>
      integer().nullable().references(SavingsGoals, #id, onDelete: KeyAction.cascade)();
  TextColumn get kind => text()();
  IntColumn get amountMinor => integer()();
  TextColumn get currency => text().withDefault(const Constant('TRY'))();
  IntColumn get originalAmountMinor => integer().nullable()();
  TextColumn get originalCurrency => text().nullable()();
  TextColumn get manualRate => text().nullable()(); // Decimal metni
  TextColumn get note => text().withDefault(const Constant(''))();
  DateTimeColumn get date => dateTime()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('JournalEntry')
@TableIndex(name: 'idx_journal_date', columns: {#date})
class JournalEntries extends Table {
  IntColumn get id => integer().autoIncrement()();
  DateTimeColumn get date => dateTime()();
  TextColumn get title => text().withDefault(const Constant(''))();
  TextColumn get body => text().withDefault(const Constant(''))();
  IntColumn get mood => integer().withDefault(const Constant(3))(); // 1..5
  TextColumn get tags => text().withDefault(const Constant(''))();
  BoolColumn get isImportant => boolean().withDefault(const Constant(false))();
  BoolColumn get isPrivate => boolean().withDefault(const Constant(false))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get updatedAt => dateTime().withDefault(currentDateAndTime)();
}

/// kind: 'image' | 'audio'
@DataClassName('JournalMediaItem')
class JournalMedia extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get entryId =>
      integer().nullable().references(JournalEntries, #id, onDelete: KeyAction.cascade)();
  IntColumn get capsuleId =>
      integer().nullable().references(TimeCapsules, #id, onDelete: KeyAction.cascade)();
  TextColumn get kind => text()();
  TextColumn get path => text()();
  IntColumn get durationMs => integer().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('TimeCapsule')
class TimeCapsules extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get title => text().withLength(min: 1, max: 120)();
  TextColumn get letter => text().withDefault(const Constant(''))(); // her zaman şifreli
  TextColumn get dreamsSnapshot => text().withDefault(const Constant(''))(); // şifreli
  TextColumn get goalsText => text().withDefault(const Constant(''))(); // şifreli
  DateTimeColumn get openAt => dateTime()();
  DateTimeColumn get openedAt => dateTime().nullable()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

@DataClassName('GardenTree')
class GardenTrees extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get dreamId =>
      integer().unique().references(Dreams, #id, onDelete: KeyAction.cascade)();
  TextColumn get plantType => text()();
  IntColumn get stage => integer().withDefault(const Constant(0))(); // 0..6
  IntColumn get slot => integer()();
  DateTimeColumn get plantedAt => dateTime().withDefault(currentDateAndTime)();
  DateTimeColumn get lastGrowthAt => dateTime().nullable()();
}

@DataClassName('Achievement')
class Achievements extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get code => text().unique()();
  DateTimeColumn get unlockedAt => dateTime().withDefault(currentDateAndTime)();
}

/// kind: 'work' | 'break'
@DataClassName('FocusSession')
@TableIndex(name: 'idx_focus_started', columns: {#startedAt})
class FocusSessions extends Table {
  IntColumn get id => integer().autoIncrement()();
  DateTimeColumn get startedAt => dateTime()();
  DateTimeColumn get endedAt => dateTime()();
  IntColumn get plannedSeconds => integer()();
  IntColumn get actualSeconds => integer()();
  TextColumn get kind => text().withDefault(const Constant('work'))();
  BoolColumn get completed => boolean().withDefault(const Constant(false))();
  IntColumn get goalId =>
      integer().nullable().references(Goals, #id, onDelete: KeyAction.setNull)();
  IntColumn get dreamId =>
      integer().nullable().references(Dreams, #id, onDelete: KeyAction.setNull)();
}

@DataClassName('LifeBalanceEntry')
class LifeBalanceEntries extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get health => integer()();
  IntColumn get finance => integer()();
  IntColumn get career => integer()();
  IntColumn get education => integer()();
  IntColumn get family => integer()();
  IntColumn get social => integer()();
  IntColumn get growth => integer()();
  IntColumn get hobbies => integer()();
  TextColumn get note => text().withDefault(const Constant(''))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

/// Tüm yerel bildirimlerin kaynağı.
/// kind: 'task' | 'habit' | 'milestone' | 'savings' | 'capsule' | 'motivation'
///       | 'goal' | 'special'
/// repeat: 'none' | 'daily' | 'weekly' | 'monthly' | 'yearly'
@DataClassName('Reminder')
@TableIndex(name: 'idx_reminder_ref', columns: {#kind, #refId})
class Reminders extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get kind => text()();
  IntColumn get refId => integer().nullable()();
  TextColumn get title => text()();
  TextColumn get body => text().withDefault(const Constant(''))();
  DateTimeColumn get at => dateTime()(); // ilk tetik (tekrarda saat/gün şablonu)
  TextColumn get repeat => text().withDefault(const Constant('none'))();
  IntColumn get weekdaysMask => integer().nullable()(); // haftalık tekrarda günler
  BoolColumn get enabled => boolean().withDefault(const Constant(true))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

/// Başarı geçmişi ve puan defteri.
/// type: 'task' | 'milestone' | 'dream' | 'habit' | 'deposit' | 'focus'
///       | 'journal' | 'goal' | 'badge' | 'balance'
@DataClassName('ActivityLogEntry')
@TableIndex(name: 'idx_activity_date', columns: {#createdAt})
class ActivityLog extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get type => text()();
  IntColumn get refId => integer().nullable()();
  TextColumn get title => text()();
  IntColumn get points => integer().withDefault(const Constant(0))();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}
