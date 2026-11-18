import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../chat/application/chat_providers.dart';
import '../application/safety_providers.dart';
import 'block_dialogs.dart';

class BlockedUsersScreen extends ConsumerWidget {
  const BlockedUsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final blocked = ref.watch(blockedProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Engellenen kişiler')),
      body: blocked.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, _) => const Center(child: Text('Liste yüklenemedi.')),
        data: (set) {
          if (set.isEmpty) {
            return const Center(child: Text('Kimseyi engellemedin.'));
          }
          final list = set.toList()..sort();
          return ListView(
            children: [
              for (final uid in list)
                Consumer(
                  builder: (context, ref, _) {
                    final p = ref.watch(profileByUidProvider(uid)).value;
                    return ListTile(
                      key: Key('blocked-$uid'),
                      leading: const Icon(Icons.block),
                      title: Text(p?.displayName ?? 'Kullanıcı'),
                      subtitle: p == null ? null : Text('@${p.username}'),
                      trailing: TextButton(
                        key: Key('unblock-$uid'),
                        onPressed: () => unblockWithFeedback(context, ref, uid),
                        child: const Text('Engeli kaldır'),
                      ),
                    );
                  },
                ),
            ],
          );
        },
      ),
    );
  }
}
