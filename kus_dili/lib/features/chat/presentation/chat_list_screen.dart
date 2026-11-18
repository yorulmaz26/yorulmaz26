import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../account/application/account_providers.dart';
import '../../crypto/application/crypto_providers.dart';
import '../../crypto/domain/key_directory.dart';
import '../../notifications/application/push_providers.dart';
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
    final boot = ref.watch(e2eeBootstrapProvider);
    ref.listen(deviceRemovedProvider, (_, next) {
      if (next.value == true) wipeUnlinkedDevice(ref);
    });
    final newDevice = ref.watch(newOwnDeviceProvider).value ?? false;
    final tooMany = boot.error is TooManyDevicesException;
    ref.watch(pushRegistrationProvider);
    final me = ref.watch(myUidProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kuş Dili'),
        actions: [
          IconButton(
            key: const Key('openLearn'),
            tooltip: 'Kuş dilini öğren',
            icon: const Icon(Icons.school_outlined),
            onPressed: () => context.push(Routes.learn),
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
      body: Column(
        children: [
          if (tooMany)
            _Banner(
              key: const Key('tooManyDevices'),
              text: const TooManyDevicesException().message,
              action: 'Cihazlar',
              onTap: () => context.push(Routes.devices),
            ),
          if (newDevice)
            _Banner(
              key: const Key('newDeviceBanner'),
              text: 'Hesabına yeni bir cihaz bağlandı. Sen değilsen hemen kaldır.',
              action: 'Kontrol et',
              onTap: () => context.push(Routes.devices),
            ),
          Expanded(
            child: chats.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) =>
                  const Center(child: Text('Sohbetler yüklenemedi.')),
              data: (list) {
                final hidden = ref
                    .watch(privacyPrefsProvider)
                    .value
                    ?.hiddenChats;
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
                  separatorBuilder: (_, _) =>
                      const Divider(height: 1, indent: 72),
                  itemBuilder: (context, i) =>
                      ChatTile(chat: visible[i], me: me),
                );
              },
            ),
          ),
        ],
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
    final other = chat.isGroup
        ? null
        : ref.watch(profileByUidProvider(chat.otherMember(me))).value;
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
    final name = chat.isGroup
        ? (chat.name ?? 'Grup')
        : (other?.displayName ?? '…');
    final locked =
        ref.watch(privacyPrefsProvider).value?.isLocked(chat.id) ?? false;
    return ListTile(
      key: ValueKey('chat-${chat.id}'),
      leading: chat.isGroup
          ? const CircleAvatar(
              backgroundColor: AppColors.turquoise,
              child: Icon(Icons.groups, color: AppColors.night),
            )
          : CircleAvatar(
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
          Text(
            chat.isGroup
                ? '${chat.members.length} üye'
                : (other == null ? '' : '@${other.username}'),
          ),
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

class _Banner extends StatelessWidget {
  const _Banner({
    super.key,
    required this.text,
    required this.action,
    required this.onTap,
  });

  final String text;
  final String action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    color: AppColors.gold.withValues(alpha: 0.15),
    padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
    child: Row(
      children: [
        const Icon(Icons.devices_outlined, color: AppColors.gold, size: 20),
        const SizedBox(width: 8),
        Expanded(child: Text(text)),
        TextButton(onPressed: onTap, child: Text(action)),
      ],
    ),
  );
}
