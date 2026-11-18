import 'dart:typed_data';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/core/services/backup_service.dart';
import 'package:hayallerim_cepte/core/services/crypto_service.dart';
import 'package:hayallerim_cepte/core/utils/dates.dart';
import 'package:hayallerim_cepte/data/savings_repository.dart';
import 'package:path/path.dart' as p;

import 'helpers.dart';

void main() {
  late TestEnv env;
  setUp(() async => env = await TestEnv.create());
  tearDown(() => env.dispose());

  test('varsayılan kategoriler ve profil oluşturulur', () async {
    final cats = await env.db.select(env.db.dreamCategories).get();
    expect(cats.length, 11);
    expect(cats.map((c) => c.name), contains('Aile ve ilişkiler'));
    expect(await env.db.select(env.db.userProfiles).get(), hasLength(1));
  });

  test('hayal → ilk adım, bahçe tohumu, rozet; adımlar ağacı büyütür', () async {
    final id = await env.dreams.createDream(
      title: "Japonya'ya gitmek",
      categoryId: await env.categoryId(),
      steps: ['Bütçe', 'Araştırma', 'Birikim', 'Hazırlık', 'Gerçekleştir'],
    );
    final steps = await (env.db.select(env.db.dreamMilestones)..where((m) => m.dreamId.equals(id))).get();
    expect(steps, hasLength(6));
    expect(steps.first.isCompleted, true);

    var tree = await (env.db.select(env.db.gardenTrees)..where((t) => t.dreamId.equals(id))).getSingle();
    expect(tree.plantType, 'palm'); // Seyahat → palmiye
    expect(tree.stage, 1); // 1/6 ≈ %17 → Filiz

    for (final s in steps.skip(1).take(3)) {
      await env.dreams.toggleMilestone(s);
    }
    tree = await (env.db.select(env.db.gardenTrees)..where((t) => t.dreamId.equals(id))).getSingle();
    expect(tree.stage, 4); // 4/6 ≈ %67 → Büyük ağaç

    final dream = await env.dreams.getDream(id);
    await env.dreams.setCompleted(dream, true);
    tree = await (env.db.select(env.db.gardenTrees)..where((t) => t.dreamId.equals(id))).getSingle();
    expect(tree.stage, 6);

    final codes = (await env.db.select(env.db.achievements).get()).map((a) => a.code).toSet();
    expect(codes, containsAll(['first_dream', 'first_step', 'dream_hunter']));
  });

  test('arşiv ve kalıcı silme: bağlı hedef korunur, adımlar silinir', () async {
    final id = await env.dreams.createDream(title: 'Ev', categoryId: await env.categoryId('Ev ve yaşam'));
    final goalId = await env.goals.createGoal(title: 'Peşinat', period: 'yearly', dreamId: id);
    await env.dreams.archive(id);
    expect((await env.dreams.getDream(id)).archivedAt, isNotNull);
    await env.dreams.deletePermanently(id);
    expect(await env.db.select(env.db.dreamMilestones).get(), isEmpty);
    expect(await env.db.select(env.db.gardenTrees).get(), isEmpty);
    final g = await (env.db.select(env.db.goals)..where((x) => x.id.equals(goalId))).getSingle();
    expect(g.dreamId, isNull);
  });

  test('gizli hayalin açıklaması şifreli saklanır', () async {
    final id = await env.dreams.createDream(
      title: 'Gizli',
      categoryId: await env.categoryId(),
      description: 'çok özel açıklama',
      hidden: true,
    );
    final raw = await env.dreams.getDream(id);
    expect(raw.description, startsWith(CryptoService.prefix));
    expect((await env.dreams.readable(raw)).description, 'çok özel açıklama');
  });

  test('tekrarlanan görev tamamlanınca ertelenir ve geçmişe yazılır', () async {
    final id = await env.goals.createTask(title: 'Su iç', dueDate: Dates.today(), repeatRule: 'daily');
    var t = await (env.db.select(env.db.tasks)..where((x) => x.id.equals(id))).getSingle();
    await env.goals.toggleTask(t);
    t = await (env.db.select(env.db.tasks)..where((x) => x.id.equals(id))).getSingle();
    expect(t.isCompleted, false);
    expect(t.dueDate, Dates.today().add(const Duration(days: 1)));
    final log = await env.db.select(env.db.activityLog).get();
    expect(log.where((l) => l.type == 'task'), hasLength(1));
  });

  test('görev işareti geri alınınca puan geri alınır', () async {
    final id = await env.goals.createTask(title: 'Tek seferlik');
    var t = await (env.db.select(env.db.tasks)..where((x) => x.id.equals(id))).getSingle();
    await env.goals.toggleTask(t);
    t = await (env.db.select(env.db.tasks)..where((x) => x.id.equals(id))).getSingle();
    await env.goals.toggleTask(t);
    final log = await env.db.select(env.db.activityLog).get();
    expect(log.where((l) => l.type == 'task'), isEmpty);
  });

  test('alışkanlık: gelecek gün işaretlenemez, aynı gün iki kez kaydedilmez', () async {
    final id = await env.habits.createHabit(title: 'Kitap', iconKey: 'book', color: 0xFF000000);
    final h = await (env.db.select(env.db.habits)..where((x) => x.id.equals(id))).getSingle();
    await env.habits.toggleDay(h, DateTime.now().add(const Duration(days: 2)));
    expect(await env.db.select(env.db.habitCompletions).get(), isEmpty);
    await env.habits.toggleDay(h, DateTime.now());
    expect(await env.db.select(env.db.habitCompletions).get(), hasLength(1));
    await env.habits.toggleDay(h, DateTime.now());
    expect(await env.db.select(env.db.habitCompletions).get(), isEmpty);
  });

  test('kumbara bakiyesi, manuel kur ve tamamlanma', () async {
    final id = await env.savings.createGoal(title: 'Araba', targetMinor: 100000000);
    await env.savings.addTransaction(goalId: id, kind: 'deposit', amountMinor: 30000000, currency: 'TRY');
    await env.savings.addTransaction(goalId: id, kind: 'withdraw', amountMinor: 5000000, currency: 'TRY');
    await env.savings.addTransaction(kind: 'income', amountMinor: 999999, currency: 'TRY'); // bakiyeyi etkilemez
    var g = await (env.db.select(env.db.savingsGoals)..where((x) => x.id.equals(id))).getSingle();
    var txs = await (env.db.select(env.db.savingsTransactions)..where((t) => t.savingsGoalId.equals(id))).get();
    var s = SavingsSummary(g, txs);
    expect(s.balance, 25000000);
    expect(s.ratio, 0.25);
    expect(s.remaining, 75000000);
    expect(g.isCompleted, false);
    await env.savings.addTransaction(goalId: id, kind: 'deposit', amountMinor: 75000000, currency: 'TRY');
    g = await (env.db.select(env.db.savingsGoals)..where((x) => x.id.equals(id))).getSingle();
    txs = await (env.db.select(env.db.savingsTransactions)..where((t) => t.savingsGoalId.equals(id))).get();
    s = SavingsSummary(g, txs);
    expect(s.remaining, 0);
    expect(g.isCompleted, true);
    expect(() => env.savings.addTransaction(goalId: id, kind: 'deposit', amountMinor: 0, currency: 'TRY'), throwsArgumentError);
  });

  test('zaman kapsülü: tarih gelmeden açılmaz, içerik şifreli', () async {
    await env.journal.createCapsule(
      title: 'Gelecek',
      letter: 'Merhaba gelecekteki ben',
      dreams: '',
      goals: '',
      openAt: DateTime.now().add(const Duration(days: 30)),
    );
    final c = (await env.db.select(env.db.timeCapsules).get()).single;
    expect(c.letter, startsWith(CryptoService.prefix));
    expect(await env.journal.openCapsule(c), isNull);
    final opened = await env.journal.openCapsule(c.copyWith(openAt: DateTime.now().subtract(const Duration(minutes: 1))));
    expect(opened!.letter, 'Merhaba gelecekteki ben');
  });

  group('Yedekleme', () {
    test('şifreli yedek → başka cihaza üzerine yazma (farklı anahtar)', () async {
      final catId = await env.categoryId();
      final d = await env.dreams.createDream(title: 'Hayal A', categoryId: catId, hidden: true, notes: 'gizli not');
      final img = await env.media.saveBytes(Uint8List.fromList([1, 2, 3, 4]), '.jpg');
      await env.dreams.addDreamImages(d, [img]);
      await env.journal.saveEntry(date: DateTime.now(), title: 'Gün', body: 'özel metin', mood: 4, tags: ['a'], important: false, private: true);
      await env.db.setSetting('theme_mode', 'dark');

      final bytes = await BackupService(env.db, env.crypto, env.media).createBackup('parola123');
      expect(String.fromCharCodes(bytes.sublist(0, 8)), 'HCBACKUP');

      final other = await TestEnv.create(); // farklı veri anahtarı
      addTearDown(other.dispose);
      final svc = BackupService(other.db, other.crypto, other.media);
      await expectLater(svc.open(bytes, 'yanlış-parola'), throwsA(isA<BackupException>()));
      final contents = await svc.open(bytes, 'parola123');
      expect(contents.count('dreams'), 1);
      await svc.restore(contents, RestoreMode.overwrite);

      final dreams = await other.db.select(other.db.dreams).get();
      expect(dreams.single.title, 'Hayal A');
      expect((await other.dreams.readable(dreams.single)).notes, 'gizli not');
      final entry = (await other.db.select(other.db.journalEntries).get()).single;
      expect(await other.journal.readableBody(entry), 'özel metin');
      expect(await other.db.getSetting('theme_mode'), 'dark');
      final media = (await other.db.select(other.db.dreamMedia).get()).single;
      expect(await File(p.join(other.dir.path, media.path!)).readAsBytes(), [1, 2, 3, 4]);
      expect(await other.db.integrityProblems(), isEmpty);
    });

    test('birleştirme: yerel veriler korunur, kimlikler yeniden eşlenir', () async {
      final a = await env.dreams.createDream(title: 'Yedekteki', categoryId: await env.categoryId());
      await env.goals.createTask(title: 'Yedekteki görev', dreamId: a);
      final bytes = await BackupService(env.db, env.crypto, env.media).createBackup('parola123');

      final other = await TestEnv.create();
      addTearDown(other.dispose);
      await other.dreams.createDream(title: 'Yerel', categoryId: await other.categoryId());
      await other.dreams.createDream(title: 'Yerel 2', categoryId: await other.categoryId());
      final svc = BackupService(other.db, other.crypto, other.media);
      await svc.restore(await svc.open(bytes, 'parola123'), RestoreMode.merge);

      final titles = (await other.db.select(other.db.dreams).get()).map((d) => d.title).toSet();
      expect(titles, {'Yerel', 'Yerel 2', 'Yedekteki'});
      final imported = (await other.db.select(other.db.dreams).get()).firstWhere((d) => d.title == 'Yedekteki');
      final task = (await other.db.select(other.db.tasks).get()).single;
      expect(task.dreamId, imported.id);
      expect(await other.db.select(other.db.dreamCategories).get(), hasLength(11)); // kategoriler çoğalmaz
      expect(await other.db.integrityProblems(), isEmpty);
    });

    test('kısa parola reddedilir', () async {
      expect(() => BackupService(env.db, env.crypto, env.media).createBackup('123'), throwsA(isA<BackupException>()));
    });
  });
}
