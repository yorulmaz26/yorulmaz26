import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import 'notification_helpers.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(settingsProvider);
    final c = ref.read(settingsProvider.notifier);
    return Scaffold(
      appBar: AppBar(title: const Text('Ayarlar')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 40),
        children: [
          const SectionHeader('Görünüm'),
          SoftCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SegmentedButton<ThemeMode>(
                segments: const [
                  ButtonSegment(value: ThemeMode.system, label: Text('Sistem')),
                  ButtonSegment(value: ThemeMode.light, label: Text('Açık')),
                  ButtonSegment(value: ThemeMode.dark, label: Text('Koyu')),
                ],
                selected: {s.themeMode},
                onSelectionChanged: (v) => c.update((x) => x.copyWith(themeMode: v.first)),
              ),
              Gap.h16,
              const Text('Vurgu rengi', style: TextStyle(fontWeight: FontWeight.w600)),
              Gap.h8,
              Wrap(spacing: 12, runSpacing: 8, children: [
                for (var i = 0; i < AppColors.accents.length; i++)
                  Semantics(
                    button: true,
                    selected: s.accentIndex == i,
                    label: AppColors.accentNames[i],
                    child: GestureDetector(
                      onTap: () => c.update((x) => x.copyWith(accentIndex: i)),
                      child: CircleAvatar(
                        radius: 18,
                        backgroundColor: AppColors.accents[i],
                        child: s.accentIndex == i ? const Icon(Icons.check, color: Colors.white, size: 18) : null,
                      ),
                    ),
                  ),
              ]),
              Gap.h16,
              Text('Yazı boyutu: %${(s.textScale * 100).round()}', style: const TextStyle(fontWeight: FontWeight.w600)),
              Slider(
                value: s.textScale,
                min: 0.85,
                max: 1.4,
                divisions: 11,
                label: '%${(s.textScale * 100).round()}',
                onChanged: (v) => c.update((x) => x.copyWith(textScale: v)),
              ),
              Text('Sistem yazı boyutu ayarın da ayrıca uygulanır.', style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
            ]),
          ),
          const SectionHeader('Bildirimler'),
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              SwitchListTile(
                title: const Text('Bildirimlere izin ver'),
                subtitle: const Text('Tümü yerel bildirimdir; internet gerekmez'),
                value: s.notificationsEnabled,
                onChanged: (v) async {
                  if (v) {
                    final ok = await enableNotifications(ref);
                    if (!ok && context.mounted) {
                      toast(context, 'Bildirim izni verilmedi. Telefon ayarlarından Hayallerim Cepte için bildirimleri açabilirsin.');
                    }
                  } else {
                    await disableNotifications(ref);
                  }
                },
              ),
              SwitchListTile(
                title: const Text('Günlük motivasyon'),
                value: s.motivationEnabled,
                onChanged: !s.notificationsEnabled
                    ? null
                    : (v) async {
                        await c.update((x) => x.copyWith(motivationEnabled: v));
                        await syncMotivationReminder(ref);
                      },
              ),
              ListTile(
                enabled: s.notificationsEnabled && s.motivationEnabled,
                title: const Text('Motivasyon saati'),
                trailing: Text(Dates.minutesToHm(s.motivationMinutes), style: const TextStyle(fontWeight: FontWeight.w700)),
                onTap: () async {
                  final t = await pickTime(context, initial: TimeOfDay(hour: s.motivationMinutes ~/ 60, minute: s.motivationMinutes % 60));
                  if (t == null) return;
                  await c.update((x) => x.copyWith(motivationMinutes: t.hour * 60 + t.minute));
                  await syncMotivationReminder(ref);
                },
              ),
              ListTile(
                title: const Text('Önemli günler'),
                subtitle: const Text('Doğum günleri, yıl dönümleri…'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.push('/special-days'),
              ),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              'Görev, alışkanlık, hedef, kumbara ve zaman kapsülü hatırlatıcıları ilgili ekranlardan açılır. '
              'Android pil tasarrufu modunda bildirimler birkaç dakika gecikebilir.',
              style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
            ),
          ),
          const SectionHeader('Genel'),
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              ListTile(
                title: const Text('Varsayılan para birimi'),
                trailing: DropdownButton<String>(
                  value: s.defaultCurrency,
                  underline: const SizedBox(),
                  items: [for (final k in Money.currencies.keys) DropdownMenuItem(value: k, child: Text('$k ${Money.symbol(k)}'))],
                  onChanged: (v) => c.update((x) => x.copyWith(defaultCurrency: v)),
                ),
              ),
              ListTile(
                title: const Text('Ana sayfa kartları'),
                subtitle: Text('${s.homeOrder.length - s.hiddenCards.length}/${s.homeOrder.length} görünür'),
                trailing: TextButton(
                  onPressed: () => c.update((x) => x.copyWith(homeOrder: HomeCard.values, hiddenCards: {})),
                  child: const Text('Sıfırla'),
                ),
              ),
            ]),
          ),
          const SectionHeader('Gizlilik'),
          const SoftCard(
            child: Text(
              'Hayallerim Cepte tamamen çevrimdışı çalışır. Hesap, sunucu, bulut, reklam veya analitik yoktur. '
              'Verilerin yalnızca bu cihazda saklanır ve üçüncü taraflarla paylaşılmaz. Yedek dosyaları senin seçtiğin parolayla şifrelenir.',
              style: TextStyle(height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}
