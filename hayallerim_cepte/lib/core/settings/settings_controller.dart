import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../database/app_database.dart';
import '../providers.dart';

/// Ana sayfa kartları
enum HomeCard {
  stats('Özet istatistikler'),
  quote('Günün sözü'),
  today('Bugünkü görevler'),
  upcoming('Yaklaşan hedefler'),
  garden('Hayal Bahçem önizlemesi'),
  weekly('Haftalık ilerleme'),
  shortcuts('Hızlı erişim');

  const HomeCard(this.label);
  final String label;
}

@immutable
class AppSettingsState {
  const AppSettingsState({
    this.themeMode = ThemeMode.system,
    this.accentIndex = 0,
    this.notificationsEnabled = false,
    this.motivationEnabled = false,
    this.motivationMinutes = 9 * 60,
    this.lockEnabled = false,
    this.biometricEnabled = false,
    this.lockTimeoutSeconds = 30,
    this.homeOrder = HomeCard.values,
    this.hiddenCards = const {},
    this.focusWork = 25,
    this.focusBreak = 5,
    this.focusLongBreak = 15,
    this.defaultCurrency = 'TRY',
    this.textScale = 1.0,
  });

  final ThemeMode themeMode;
  final int accentIndex;
  final bool notificationsEnabled;
  final bool motivationEnabled;
  final int motivationMinutes;
  final bool lockEnabled;
  final bool biometricEnabled;
  final int lockTimeoutSeconds;
  final List<HomeCard> homeOrder;
  final Set<HomeCard> hiddenCards;
  final int focusWork;
  final int focusBreak;
  final int focusLongBreak;
  final String defaultCurrency;
  final double textScale;

  AppSettingsState copyWith({
    ThemeMode? themeMode,
    int? accentIndex,
    bool? notificationsEnabled,
    bool? motivationEnabled,
    int? motivationMinutes,
    bool? lockEnabled,
    bool? biometricEnabled,
    int? lockTimeoutSeconds,
    List<HomeCard>? homeOrder,
    Set<HomeCard>? hiddenCards,
    int? focusWork,
    int? focusBreak,
    int? focusLongBreak,
    String? defaultCurrency,
    double? textScale,
  }) =>
      AppSettingsState(
        themeMode: themeMode ?? this.themeMode,
        accentIndex: accentIndex ?? this.accentIndex,
        notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
        motivationEnabled: motivationEnabled ?? this.motivationEnabled,
        motivationMinutes: motivationMinutes ?? this.motivationMinutes,
        lockEnabled: lockEnabled ?? this.lockEnabled,
        biometricEnabled: biometricEnabled ?? this.biometricEnabled,
        lockTimeoutSeconds: lockTimeoutSeconds ?? this.lockTimeoutSeconds,
        homeOrder: homeOrder ?? this.homeOrder,
        hiddenCards: hiddenCards ?? this.hiddenCards,
        focusWork: focusWork ?? this.focusWork,
        focusBreak: focusBreak ?? this.focusBreak,
        focusLongBreak: focusLongBreak ?? this.focusLongBreak,
        defaultCurrency: defaultCurrency ?? this.defaultCurrency,
        textScale: textScale ?? this.textScale,
      );

  Map<String, String> toMap() => {
        'theme_mode': themeMode.name,
        'accent_index': '$accentIndex',
        'notifications_enabled': '$notificationsEnabled',
        'motivation_enabled': '$motivationEnabled',
        'motivation_minutes': '$motivationMinutes',
        'lock_enabled': '$lockEnabled',
        'biometric_enabled': '$biometricEnabled',
        'lock_timeout': '$lockTimeoutSeconds',
        'home_order': jsonEncode(homeOrder.map((c) => c.name).toList()),
        'home_hidden': jsonEncode(hiddenCards.map((c) => c.name).toList()),
        'focus_work': '$focusWork',
        'focus_break': '$focusBreak',
        'focus_long_break': '$focusLongBreak',
        'default_currency': defaultCurrency,
        'text_scale': '$textScale',
      };

  static AppSettingsState fromMap(Map<String, String> m) {
    const d = AppSettingsState();
    int i(String k, int def) => int.tryParse(m[k] ?? '') ?? def;
    bool b(String k, bool def) => m[k] == null ? def : m[k] == 'true';
    List<HomeCard> cards(String k, List<HomeCard> def) {
      try {
        final raw = m[k];
        if (raw == null) return def;
        final names = (jsonDecode(raw) as List).cast<String>();
        final list = <HomeCard>[
          for (final n in names)
            for (final c in HomeCard.values)
              if (c.name == n) c,
        ];
        // Yeni sürümde eklenen kartları sona ekle
        for (final c in def) {
          if (!list.contains(c)) list.add(c);
        }
        return list;
      } catch (_) {
        return def;
      }
    }

    return AppSettingsState(
      themeMode: ThemeMode.values.firstWhere((t) => t.name == m['theme_mode'], orElse: () => d.themeMode),
      accentIndex: i('accent_index', 0),
      notificationsEnabled: b('notifications_enabled', false),
      motivationEnabled: b('motivation_enabled', false),
      motivationMinutes: i('motivation_minutes', d.motivationMinutes),
      lockEnabled: b('lock_enabled', false),
      biometricEnabled: b('biometric_enabled', false),
      lockTimeoutSeconds: i('lock_timeout', 30),
      homeOrder: cards('home_order', HomeCard.values),
      hiddenCards: m['home_hidden'] == null ? const {} : cards('home_hidden', const []).toSet(),
      focusWork: i('focus_work', 25),
      focusBreak: i('focus_break', 5),
      focusLongBreak: i('focus_long_break', 15),
      defaultCurrency: m['default_currency'] ?? 'TRY',
      textScale: double.tryParse(m['text_scale'] ?? '') ?? 1.0,
    );
  }
}

/// Ayarlar veritabanındaki `app_settings` tablosunda tutulur (yedeklenir).
/// Başlangıç değeri `main.dart` içinde yüklenip override edilir.
final initialSettingsProvider = Provider<AppSettingsState>((ref) => const AppSettingsState());

final settingsProvider = NotifierProvider<SettingsController, AppSettingsState>(SettingsController.new);

class SettingsController extends Notifier<AppSettingsState> {
  @override
  AppSettingsState build() => ref.read(initialSettingsProvider);

  AppDatabase get _db => ref.read(databaseProvider);

  Future<void> update(AppSettingsState Function(AppSettingsState s) change) async {
    final next = change(state);
    state = next;
    final map = next.toMap();
    await _db.transaction(() async {
      for (final e in map.entries) {
        await _db.setSetting(e.key, e.value);
      }
    });
  }

  /// Geri yüklemeden sonra ayarları veritabanından yeniden okur.
  Future<void> reload() async => state = AppSettingsState.fromMap(await _db.allSettings());
}
