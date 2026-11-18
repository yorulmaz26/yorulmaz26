import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/providers.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/quotes.dart';
import '../../data/providers.dart';

/// Bildirimleri açar (izin ister). İzin verilmezse false döner.
Future<bool> enableNotifications(WidgetRef ref) async {
  final granted = await ref.read(notificationsProvider).requestPermission();
  await ref.read(settingsProvider.notifier).update((s) => s.copyWith(notificationsEnabled: granted));
  await syncMotivationReminder(ref);
  await ref.read(reminderRepoProvider).resync();
  return granted;
}

Future<void> disableNotifications(WidgetRef ref) async {
  await ref.read(settingsProvider.notifier).update((s) => s.copyWith(notificationsEnabled: false));
  await ref.read(reminderRepoProvider).resync();
}

/// Günlük motivasyon bildirimi: her gün seçilen saatte günün sözü.
Future<void> syncMotivationReminder(WidgetRef ref) async {
  final s = ref.read(settingsProvider);
  final repo = ref.read(reminderRepoProvider);
  if (!s.motivationEnabled) {
    await repo.removeKind('motivation');
    return;
  }
  await repo.upsertFor(
    kind: 'motivation',
    title: 'Günün sözü ✨',
    body: Quotes.ofDay(),
    at: Dates.today().add(Duration(minutes: s.motivationMinutes)),
    repeat: 'daily',
  );
}
