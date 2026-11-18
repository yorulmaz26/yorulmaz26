import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'database/app_database.dart';
import 'services/crypto_service.dart';
import 'services/media_service.dart';
import 'services/notification_service.dart';

/// Çekirdek servisler `main.dart` içinde başlatılıp `overrideWithValue` ile
/// enjekte edilir; testlerde bellek içi sürümler verilir.
final databaseProvider = Provider<AppDatabase>((ref) => throw UnimplementedError('databaseProvider override edilmedi'));
final cryptoProvider = Provider<CryptoService>((ref) => throw UnimplementedError());
final mediaProvider = Provider<MediaService>((ref) => throw UnimplementedError());
final notificationsProvider = Provider<NotificationService>((ref) => NotificationService());
final prefsProvider = Provider<SharedPreferences>((ref) => throw UnimplementedError());
final keyStoreProvider = Provider<KeyStore>((ref) => SecureKeyStore());
