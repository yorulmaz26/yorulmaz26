import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../application/chat_providers.dart';
import '../domain/chat.dart';
import 'member_picker.dart';

/// Grup bilgisi: üyeler, yönetici işlemleri, ayrılma.
class GroupInfoScreen extends ConsumerWidget {
  const GroupInfoScreen({super.key, required this.chatId});

  final String chatId;

  Future<void> _rename(BuildContext context, WidgetRef ref, Chat chat) async {
    final c = TextEditingController(text: chat.name);
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Grup adı'),
        content: TextField(
          key: const Key('renameField'),
          controller: c,
          maxLength: 50,
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            key: const Key('renameSave'),
            onPressed: () => Navigator.pop(ctx, c.text.trim()),
            child: const Text('Kaydet'),
          ),
        ],
      ),
    );
    if (name == null || name.isEmpty || name.length > 50) return;
    await ref
        .read(chatRepositoryProvider)
        .renameGroup(chatId: chatId, name: name);
  }

  Future<void> _leave(
    BuildContext context,
    WidgetRef ref,
    Chat chat,
    String me,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Gruptan ayrıl?'),
        content: const Text('Bu gruptaki yeni mesajları artık göremezsin.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            key: const Key('confirmLeave'),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Ayrıl'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    final others = chat.members.where((m) => m != me).toList();
    final lastAdmin = chat.admins.length == 1 && chat.isAdmin(me);
    await ref
        .read(chatRepositoryProvider)
        .leaveGroup(
          chatId: chatId,
          uid: me,
          promote: lastAdmin && others.isNotEmpty ? others.first : null,
        );
    if (context.mounted) context.go(Routes.home);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final chat = ref.watch(chatProvider(chatId)).value;
    final me = ref.watch(myUidProvider);
    if (chat == null || me == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final admin = chat.isAdmin(me);
    final repo = ref.read(chatRepositoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Grup bilgisi')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          ListTile(
            leading: const CircleAvatar(
              backgroundColor: AppColors.turquoise,
              child: Icon(Icons.groups, color: AppColors.night),
            ),
            title: Text(chat.name ?? '', key: const Key('groupTitle')),
            subtitle: Text('${chat.members.length} üye'),
            trailing: admin
                ? IconButton(
                    key: const Key('renameGroup'),
                    icon: const Icon(Icons.edit_outlined),
                    onPressed: () => _rename(context, ref, chat),
                  )
                : null,
          ),
          if (admin && chat.members.length < Chat.maxGroupMembers)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: MemberSearchField(
                excluded: chat.members.toSet(),
                onFound: (p) =>
                    repo.addGroupMembers(chatId: chatId, uids: [p.uid]),
              ),
            ),
          const Divider(),
          for (final uid in chat.members)
            _MemberTile(chat: chat, uid: uid, me: me, iAmAdmin: admin),
          const Divider(),
          ListTile(
            key: const Key('leaveGroup'),
            leading: Icon(
              Icons.logout,
              color: Theme.of(context).colorScheme.error,
            ),
            title: Text(
              'Gruptan ayrıl',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            onTap: () => _leave(context, ref, chat, me),
          ),
        ],
      ),
    );
  }
}

class _MemberTile extends ConsumerWidget {
  const _MemberTile({
    required this.chat,
    required this.uid,
    required this.me,
    required this.iAmAdmin,
  });

  final Chat chat;
  final String uid;
  final String me;
  final bool iAmAdmin;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = ref.watch(profileByUidProvider(uid)).value;
    final isAdmin = chat.isAdmin(uid);
    final repo = ref.read(chatRepositoryProvider);
    return ListTile(
      key: ValueKey('member-$uid'),
      leading: CircleAvatar(
        backgroundColor: AppColors.violet,
        child: Text(
          (p?.displayName.characters.firstOrNull ?? '?').toUpperCase(),
        ),
      ),
      title: Text(
        uid == me ? '${p?.displayName ?? ''} (sen)' : p?.displayName ?? '',
      ),
      subtitle: Text('@${p?.username ?? ''}'),
      onTap: uid == me
          ? null
          : () => context.push(Routes.safetyNumber(chat.id, uid)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (isAdmin)
            const Chip(
              label: Text('Yönetici'),
              visualDensity: VisualDensity.compact,
            ),
          if (iAmAdmin && uid != me)
            PopupMenuButton<String>(
              key: ValueKey('memberMenu-$uid'),
              onSelected: (v) {
                switch (v) {
                  case 'admin':
                    repo.setGroupAdmin(
                      chatId: chat.id,
                      uid: uid,
                      admin: !isAdmin,
                    );
                  case 'remove':
                    repo.removeGroupMember(chatId: chat.id, uid: uid);
                }
              },
              itemBuilder: (_) => [
                PopupMenuItem(
                  value: 'admin',
                  child: Text(isAdmin ? 'Yöneticilikten al' : 'Yönetici yap'),
                ),
                const PopupMenuItem(
                  value: 'remove',
                  child: Text('Gruptan çıkar'),
                ),
              ],
            ),
        ],
      ),
    );
  }
}
