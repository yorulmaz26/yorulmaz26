import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../crypto/application/crypto_providers.dart';
import '../../privacy/application/privacy_providers.dart';
import '../application/chat_providers.dart';
import '../domain/chat.dart';

/// Sohbet listesi (ana ekran). Gizlilik gereği mesaj önizlemesi
/// gösterilmez; sunucuda da tutulmaz.
class ChatListScreen extends ConsumerWidget {
  const ChatListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chats = ref.watch(chatsProvider);
    // Anahtarlar yayımlansın ki başkaları bize şifreli yazabilsin.
    ref.watch(e2eeBootstrapProvider);
    final me = ref.watch(myUidProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kuş Dili'),
        actions: [
          IconButton(
            tooltip: 'Çevirici',
            icon: const Icon(Icons.translate),
            onPressed: () => context.push(Routes.translator),
          ),
          IconButton(
            key: const Key('openProfile'),
            tooltip: 'Profil',
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => context.push(Routes.profile),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        key: const Key('newChat'),
        tooltip: 'Yeni sohbet',
        backgroundColor: AppColors.turquoise,
        foregroundColor: AppColors.night,
        onPressed: () => context.push(Routes.newChat),
        child: const Icon(Icons.edit_outlined),
      ),
      body: chats.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => const Center(child: Text('Sohbetler yüklenemedi.')),
        data: (list) {
          final hidden = ref.watch(privacyPrefsProvider).value?.hiddenChats;
          final visible = [
            for (final c in list)
              if (!(hidden?.contains(c.id) ?? false)) c,
          ];
          if (visible.isEmpty || me == null) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: Text(
                  'Henüz sohbetin yok.\nKullanıcı adıyla birini bulup '
                  'kuş dilinde yazışmaya başla.',
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }
          return ListView.separated(
            itemCount: visible.length,
            separatorBuilder: (_, _) => const Divider(height: 1, indent: 72),
            itemBuilder: (context, i) => ChatTile(chat: visible[i], me: me),
          );
        },
      ),
    );
  }
}

class ChatTile extends ConsumerWidget {
  const ChatTile({super.key, required this.chat, required this.me});

  final Chat chat;
  final String me;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final other = ref.watch(profileByUidProvider(chat.otherMember(me))).value;
    final unread = chat.isUnreadFor(me);
    final t = chat.lastActivityAt?.toLocal();
    final now = DateTime.now();
    final time = t == null
        ? ''
        : (t.year == now.year && t.month == now.month && t.day == now.day)
        ? '${t.hour.toString().padLeft(2, '0')}:'
              '${t.minute.toString().padLeft(2, '0')}'
        : '${t.day.toString().padLeft(2, '0')}.'
              '${t.month.toString().padLeft(2, '0')}';
    final name = other?.displayName ?? '…';
    final locked =
        ref.watch(privacyPrefsProvider).value?.isLocked(chat.id) ?? false;
    return ListTile(
      key: ValueKey('chat-${chat.id}'),
      leading: CircleAvatar(
        backgroundColor: AppColors.violet,
        child: Text(name.characters.first.toUpperCase()),
      ),
      title: Text(
        name,
        style: unread ? const TextStyle(fontWeight: FontWeight.w700) : null,
      ),
      subtitle: Row(
        children: [
          if (locked) ...[
            const Icon(Icons.lock, size: 13, color: AppColors.gold),
            const SizedBox(width: 4),
          ],
          Text(other == null ? '' : '@${other.username}'),
        ],
      ),
      trailing: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Text(time, style: Theme.of(context).textTheme.labelSmall),
          if (unread) ...[
            const SizedBox(height: 6),
            const CircleAvatar(
              key: Key('unreadDot'),
              radius: 5,
              backgroundColor: AppColors.turquoise,
            ),
          ],
        ],
      ),
      onTap: () => context.push(Routes.chat(chat.id)),
    );
  }
}
