import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/app.dart';
import 'package:hayallerim_cepte/core/providers.dart';
import 'package:hayallerim_cepte/core/router/app_router.dart';
import 'package:hayallerim_cepte/core/services/crypto_service.dart';
import 'package:hayallerim_cepte/core/settings/settings_controller.dart';
import 'package:hayallerim_cepte/features/garden/plant_painter.dart';
import 'package:hayallerim_cepte/features/security/lock_controller.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'helpers.dart';

Future<(TestEnv, Widget)> buildApp({bool onboarded = false, bool locked = false, KeyStore? keys}) async {
  SharedPreferences.setMockInitialValues({if (onboarded) kOnboardingDoneKey: true});
  final prefs = await SharedPreferences.getInstance();
  final store = keys ?? MemoryKeyStore();
  final env = await TestEnv.create(keys: store);
  final app = ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(env.db),
      cryptoProvider.overrideWithValue(env.crypto),
      mediaProvider.overrideWithValue(env.media),
      prefsProvider.overrideWithValue(prefs),
      keyStoreProvider.overrideWithValue(store),
      initialSettingsProvider.overrideWithValue(AppSettingsState(lockEnabled: locked)),
      initialLockedProvider.overrideWithValue(locked),
    ],
    child: const HayallerimCepteApp(),
  );
  return (env, app);
}

Future<void> settle(WidgetTester t) async {
  for (var i = 0; i < 20; i++) {
    await t.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
    await t.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUpAll(() => initializeDateFormatting('tr_TR'));

  testWidgets('İlk açılış: splash → tanıtım → kurulum → ana sayfa', (t) async {
    final (env, app) = (await t.runAsync(buildApp))!;
    await t.pumpWidget(app);
    expect(find.text('Hayallerim Cepte'), findsOneWidget);
    await t.pump(const Duration(seconds: 2));
    await settle(t);
    expect(find.text('Hayallerini Keşfet'), findsOneWidget);

    await t.tap(find.text('İleri'));
    await settle(t);
    expect(find.text('Hedeflerini Planla'), findsOneWidget);
    await t.tap(find.text('İleri'));
    await settle(t);
    expect(find.text('Başarılarını Kutla'), findsOneWidget);
    await t.tap(find.text('Başlayalım'));
    await settle(t);

    expect(find.text('Seni tanıyalım'), findsOneWidget);
    await t.enterText(find.byType(TextField).first, 'Deniz');
    await t.tap(find.text('Hayal yolculuğuna başla'));
    await settle(t);

    expect(find.textContaining('Deniz'), findsWidgets);
    expect(find.text('Hayal ekle'), findsOneWidget);
    expect(find.text('Günün sözü'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
    await t.pump(const Duration(seconds: 1));
    await t.runAsync(env.dispose);
  });

  testWidgets('Hızlı hayal ekleme veritabanına yazar ve detay açılır', (t) async {
    final (env, app) = (await t.runAsync(() => buildApp(onboarded: true)))!;
    await t.pumpWidget(app);
    await t.pump(const Duration(seconds: 2));
    await settle(t);
    await t.tap(find.text('Hayal ekle'));
    await settle(t);
    await t.enterText(find.byType(TextField).last, 'Kendi evim');
    await t.tap(find.widgetWithText(FilledButton, 'Ekle'));
    await settle(t);
    await settle(t);
    final dreams = await t.runAsync(() => env.db.select(env.db.dreams).get());
    expect(dreams!.single.title, 'Kendi evim');
    expect(find.text('Hayal yolculuğum'), findsOneWidget);
    expect(find.text('Hayalimi oluşturdum'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
    await t.pump(const Duration(seconds: 1));
    await t.runAsync(env.dispose);
  });

  testWidgets('Uygulama kilidi PIN ile açılır', (t) async {
    final store = MemoryKeyStore();
    final (env, app) = (await t.runAsync(() => buildApp(onboarded: true, locked: true, keys: store)))!;
    await t.runAsync(() => LockServiceHelper.setPin(store, '2468'));
    await t.pumpWidget(app);
    await t.pump(const Duration(seconds: 2));
    await settle(t);
    expect(find.text('PIN kodunu gir'), findsOneWidget);
    for (final d in ['2', '4', '6', '8']) {
      await t.tap(find.text(d).last);
      await t.pump();
    }
    await settle(t);
    expect(find.text('PIN kodunu gir'), findsNothing);
    expect(find.text('Hayal ekle'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
    await t.pump(const Duration(seconds: 1));
    await t.runAsync(env.dispose);
  });

  testWidgets('Bitki tüm aşama ve türlerde hatasız çizilir', (t) async {
    await t.pumpWidget(MaterialApp(
      home: Scaffold(
        body: Wrap(children: [
          for (final type in kPlantTypes)
            for (var s = 0; s <= 6; s++) Plant(stage: s, type: type, size: 60),
        ]),
      ),
    ));
    expect(find.byType(Plant), findsNWidgets(kPlantTypes.length * 7));
    expect(t.takeException(), isNull);
  });
}
