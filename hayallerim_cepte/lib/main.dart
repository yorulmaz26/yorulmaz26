import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/database/app_database.dart';
import 'core/providers.dart';
import 'core/services/crypto_service.dart';
import 'core/services/lock_service.dart';
import 'core/services/media_service.dart';
import 'core/services/notification_service.dart';
import 'core/settings/settings_controller.dart';
import 'data/reminder_repository.dart';
import 'features/security/lock_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('tr_TR');

  final prefs = await SharedPreferences.getInstance();
  final db = AppDatabase();
  final keyStore = SecureKeyStore();
  final crypto = CryptoService(keyStore);
  final media = MediaService();
  await media.init();
  final notifications = NotificationService();

  var settings = AppSettingsState.fromMap(await db.allSettings());
  final lock = LockService(keyStore);
  final hasPin = await lock.hasPin();
  if (settings.lockEnabled && !hasPin) {
    // Kilit açık ama PIN yok (ör. başka cihazdan geri yükleme) → kilidi kapat.
    settings = settings.copyWith(lockEnabled: false, biometricEnabled: false);
    for (final e in settings.toMap().entries) {
      await db.setSetting(e.key, e.value);
    }
  }

  // Bildirimleri arka planda yeniden planla (açılışı geciktirmez).
  ReminderRepository(db, notifications).resync().ignore();

  runApp(ProviderScope(
    overrides: [
      databaseProvider.overrideWithValue(db),
      cryptoProvider.overrideWithValue(crypto),
      mediaProvider.overrideWithValue(media),
      notificationsProvider.overrideWithValue(notifications),
      prefsProvider.overrideWithValue(prefs),
      keyStoreProvider.overrideWithValue(keyStore),
      initialSettingsProvider.overrideWithValue(settings),
      initialLockedProvider.overrideWithValue(settings.lockEnabled && hasPin),
    ],
    child: const HayallerimCepteApp(),
  ));
}
