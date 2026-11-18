import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_colors.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Arşivlenen hayaller: geri alma veya kalıcı silme.
class ArchiveScreen extends ConsumerWidget {
  const ArchiveScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(archivedDreamsProvider).value ?? const [];
    return Scaffold(
      appBar: AppBar(title: const Text('Arşiv')),
      body: list.isEmpty
          ? const EmptyState(icon: Icons.inventory_2_outlined, title: 'Arşiv boş', message: 'Arşivlediğin hayaller burada saklanır.')
          : ListView.builder(
              itemCount: list.length,
              itemBuilder: (_, i) {
                final d = list[i].dream;
                return ListTile(
                  leading: CircleAvatar(backgroundColor: Color(list[i].category.color), child: const Icon(Icons.inventory_2, color: Colors.white, size: 18)),
                  title: Text(d.title),
                  subtitle: Text('Arşivlendi: ${Dates.short(d.archivedAt ?? d.updatedAt)}'),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(
                      tooltip: 'Geri al',
                      icon: const Icon(Icons.unarchive_outlined),
                      onPressed: () async {
                        await ref.read(dreamRepoProvider).restore(d.id);
                        if (context.mounted) toast(context, 'Hayal geri alındı');
                      },
                    ),
                    IconButton(
                      tooltip: 'Kalıcı sil',
                      icon: const Icon(Icons.delete_forever_outlined, color: AppColors.danger),
                      onPressed: () async {
                        final ok = await confirm(
                          context,
                          title: 'Kalıcı olarak silinsin mi?',
                          message: 'Adımlar, görseller ve bahçedeki ağaç silinir. Bağlı hedefler, görevler ve kumbaralar korunur. Bu işlem geri alınamaz.',
                          ok: 'Kalıcı sil',
                          destructive: true,
                        );
                        if (ok) await ref.read(dreamRepoProvider).deletePermanently(d.id);
                      },
                    ),
                  ]),
                );
              },
            ),
    );
  }
}
