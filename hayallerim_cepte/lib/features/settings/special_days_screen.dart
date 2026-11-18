import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Önemli günler — her yıl aynı gün yerel bildirim.
class SpecialDaysScreen extends ConsumerWidget {
  const SpecialDaysScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(specialDaysProvider).value ?? const [];
    final notifOn = ref.watch(settingsProvider).notificationsEnabled;
    return Scaffold(
      appBar: AppBar(title: const Text('Önemli günler')),
      floatingActionButton: FloatingActionButton.extended(
        icon: const Icon(Icons.add),
        label: const Text('Gün ekle'),
        onPressed: () async {
          final title = await promptText(context, title: 'Önemli gün', hint: 'Örn. Annemin doğum günü', maxLength: 80);
          if (title == null || !context.mounted) return;
          final d = await pickDate(context, first: DateTime(1900));
          if (d == null || !context.mounted) return;
          final t = await pickTime(context, initial: const TimeOfDay(hour: 9, minute: 0));
          await ref.read(reminderRepoProvider).addSpecialDay(title, DateTime(d.year, d.month, d.day, t?.hour ?? 9, t?.minute ?? 0));
        },
      ),
      body: Column(children: [
        if (!notifOn)
          const MaterialBanner(
            content: Text('Bildirimler kapalı. Ayarlar ekranından açabilirsin.'),
            actions: [SizedBox()],
          ),
        Expanded(
          child: list.isEmpty
              ? const EmptyState(icon: Icons.cake_outlined, title: 'Önemli günlerin', message: 'Doğum günleri ve yıl dönümleri için her yıl hatırlatma al.')
              : ListView(children: [
                  for (final r in list)
                    ListTile(
                      leading: const Icon(Icons.cake_outlined),
                      title: Text(r.title),
                      subtitle: Text('Her yıl ${Dates.dayMonth(r.at)} · ${Dates.time(r.at)}'),
                      trailing: IconButton(
                        tooltip: 'Sil',
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => ref.read(reminderRepoProvider).deleteById(r.id),
                      ),
                    ),
                ]),
        ),
      ]),
    );
  }
}
