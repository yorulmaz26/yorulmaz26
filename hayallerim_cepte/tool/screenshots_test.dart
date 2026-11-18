// Örnek verilerle ekran görüntüleri üretir (tasarım kontrolü için):
//   flutter test tool/screenshots_test.dart
// Çıktı: tool/screenshots/*.png (depoya eklenmez)
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hayallerim_cepte/core/router/app_router.dart';
import 'package:hayallerim_cepte/core/utils/dates.dart';
import 'package:intl/date_symbol_data_local.dart';

import '../test/helpers.dart';
import '../test/widget_test.dart' show buildApp, settle;

Future<void> _loadFonts() async {
  const dir = '/opt/flutter/bin/cache/artifacts/material_fonts';
  final roboto = FontLoader('Roboto');
  for (final f in ['Roboto-Regular', 'Roboto-Medium', 'Roboto-Bold', 'Roboto-Black', 'Roboto-Light', 'Roboto-Italic']) {
    roboto.addFont(Future.value(ByteData.sublistView(File('$dir/$f.ttf').readAsBytesSync())));
  }
  await roboto.load();
  final icons = FontLoader('MaterialIcons')..addFont(Future.value(ByteData.sublistView(File('$dir/MaterialIcons-Regular.otf').readAsBytesSync())));
  await icons.load();
}

Future<void> _seed(TestEnv e) async {
  final travel = await e.categoryId('Seyahat');
  final car = await e.categoryId('Araba');
  final edu = await e.categoryId('Eğitim');
  final j = await e.dreams.createDream(
    title: "Japonya'ya gitmek",
    categoryId: travel,
    motivation: 'Kiraz çiçeklerini kendi gözlerimle görmek istiyorum.',
    targetDate: DateTime.now().add(const Duration(days: 240)),
    priority: 2,
    steps: ['Seyahat bütçemi belirledim', 'Gideceğim yerleri araştırdım', 'Birikim yapmaya başladım', 'Hazırlıklarımı tamamladım', 'Hayalimi gerçekleştirdim'],
  );
  final steps = await (e.db.select(e.db.dreamMilestones)..where((m) => m.dreamId.equals(j))).get();
  await e.dreams.toggleMilestone(steps[1]);
  await e.dreams.toggleMilestone(steps[2]);
  final a = await e.dreams.createDream(title: 'Araba almak', categoryId: car, budgetMinor: 100000000, steps: ['Ehliyet', 'Model seç', 'Peşinat']);
  final s2 = await (e.db.select(e.db.dreamMilestones)..where((m) => m.dreamId.equals(a))).get();
  for (final s in s2.skip(1)) {
    await e.dreams.toggleMilestone(s);
  }
  final b = await e.dreams.createDream(title: 'Yüksek lisans', categoryId: edu, steps: ['Dil sınavı']);
  await e.dreams.setCompleted(await e.dreams.getDream(b), true);
  await e.dreams.createDream(title: 'Maraton koşmak', categoryId: await e.categoryId('Sağlık'));
  final sg = await e.savings.createGoal(title: 'Araba almak', targetMinor: 100000000, monthlyTargetMinor: 5000000, dreamId: a);
  await e.savings.addTransaction(goalId: sg, kind: 'deposit', amountMinor: 25000000, currency: 'TRY', date: DateTime.now().subtract(const Duration(days: 60)));
  await e.goals.createTask(title: '30 dakika kitap oku', dueDate: Dates.today(), dueMinutes: 21 * 60, repeatRule: 'daily', reminder: true);
  await e.goals.createTask(title: 'Uçak biletlerini karşılaştır', dueDate: Dates.today(), dreamId: j, priority: 2);
  await e.goals.createGoal(title: 'Bu ay 5 kitap', period: 'monthly');
  final h = await e.habits.createHabit(title: 'Su içmek', iconKey: 'water', color: 0xFF3B82F6, challengeDays: 21);
  final habit = await (e.db.select(e.db.habits)..where((x) => x.id.equals(h))).getSingle();
  for (var i = 0; i < 5; i++) {
    await e.habits.toggleDay(habit, DateTime.now().subtract(Duration(days: i)));
  }
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('tr_TR');
    await _loadFonts();
  });

  testWidgets('ekran görüntüleri', (t) async {
    t.view.physicalSize = const Size(1170, 2532);
    t.view.devicePixelRatio = 3;
    final (env, app) = (await t.runAsync(() => buildApp(onboarded: true)))!;
    await t.runAsync(() => _seed(env));
    final key = GlobalKey();
    await t.pumpWidget(RepaintBoundary(key: key, child: app));
    await t.pump(const Duration(seconds: 2));
    await settle(t);
    final out = Directory('tool/screenshots')..createSync(recursive: true);

    Future<void> shot(String name) async {
      await settle(t);
      await t.runAsync(() async {
        final b = key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
        final img = await b.toImage(pixelRatio: 1);
        final data = await img.toByteData(format: ui.ImageByteFormat.png);
        await File('${out.path}/$name.png').writeAsBytes(data!.buffer.asUint8List());
      });
    }

    final router = GoRouter.of(rootNavigatorKey.currentContext!);
    await shot('01_home');
    for (final (name, route) in [
      ('02_dreams', '/dreams'),
      ('03_goals', '/goals'),
      ('04_garden', '/garden'),
      ('05_profile', '/profile'),
    ]) {
      router.go(route);
      await shot(name);
    }
    router.go('/home');
    await settle(t);
    for (final (name, route) in [
      ('06_dream_detail', '/dream/1'),
      ('07_savings', '/savings/1'),
      ('08_habits', '/habits'),
      ('09_achievements', '/achievements'),
      ('10_planner', '/planner'),
      ('11_focus', '/focus'),
    ]) {
      router.push(route);
      await shot(name);
      router.pop();
      await settle(t);
    }
    await t.pumpWidget(const SizedBox());
    await t.pump(const Duration(seconds: 1));
    await t.runAsync(env.dispose);
    t.view.reset();
  });
}
