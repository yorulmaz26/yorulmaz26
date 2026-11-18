import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../chat/application/chat_providers.dart';
import '../../chat/presentation/chat_list_screen.dart';
import '../application/privacy_providers.dart';
import 'unlock_gate.dart';

/// Ana listeden gizlenen sohbetler; PIN/biyometri ile açılır.
class HiddenChatsScreen extends ConsumerWidget {
  const HiddenChatsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unlocked = ref.watch(unlockProvider).hiddenRevealed;
    if (!unlocked) {
      return UnlockGate(
        title: 'Gizli sohbetler',
        onUnlocked: ref.read(unlockProvider.notifier).revealHidden,
      );
    }
    final prefs = ref.watch(privacyPrefsProvider).value;
    final me = ref.watch(myUidProvider);
    final chats = (ref.watch(chatsProvider).value ?? const [])
        .where((c) => prefs?.isHidden(c.id) ?? false)
        .toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Gizli sohbetler')),
      body: chats.isEmpty || me == null
          ? const Center(child: Text('Gizli sohbet yok.'))
          : ListView(
              children: [for (final c in chats) ChatTile(chat: c, me: me)],
            ),
    );
  }
}
