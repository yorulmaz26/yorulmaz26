import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';

import '../../../app/theme/app_colors.dart';
import '../../auth/application/auth_providers.dart';
import '../../auth/domain/phone_number.dart';
import '../../account/application/account_providers.dart';
import '../../notifications/application/push_providers.dart';
import '../application/profile_providers.dart';

/// Kullanıcı profili.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(currentProfileProvider).value;
    final user = ref.watch(authStateProvider).value;
    final phone = PhoneNumber.tryParse(user?.phoneNumber ?? '');
    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: AppColors.violet,
                child: Text(
                  (profile?.displayName.characters.firstOrNull ?? '?')
                      .toUpperCase(),
                ),
              ),
              title: Text(profile?.displayName ?? ''),
              subtitle: Text(
                '@${profile?.username ?? ''}'
                '${phone == null ? '' : '  ·  ${phone.display}'}',
              ),
            ),
          ),
          const SizedBox(height: 8),
          const Text(
            'Telefon numaran yalnızca sende görünür; diğer kullanıcılar seni '
            'kullanıcı adınla bulur.',
          ),
          const SizedBox(height: 16),
          ListTile(
            key: const Key('openPrivacy'),
            leading: const Icon(Icons.shield_outlined),
            title: const Text('Gizlilik ve güvenlik'),
            subtitle: const Text('PIN, uygulama kilidi, gizli sohbetler'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(Routes.privacy),
          ),
          ListTile(
            key: const Key('openNotifications'),
            leading: const Icon(Icons.notifications_outlined),
            title: const Text('Bildirimler'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(Routes.notifications),
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            key: const Key('signOut'),
            icon: const Icon(Icons.logout),
            label: const Text('Çıkış yap'),
            onPressed: () => signOutWithPushCleanup(ref),
          ),
          const SizedBox(height: 32),
          TextButton.icon(
            key: const Key('deleteAccount'),
            icon: Icon(
              Icons.delete_forever_outlined,
              color: Theme.of(context).colorScheme.error,
            ),
            label: Text(
              'Hesabı kalıcı olarak sil',
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            onPressed: profile == null
                ? null
                : () => _confirmDelete(context, ref, profile.username),
          ),
        ],
      ),
    );
  }

  Future<void> _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    String username,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => _DeleteDialog(username: username),
    );
    if (ok != true || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      await deleteAccountEverywhere(ref);
    } catch (_) {
      messenger.showSnackBar(
        const SnackBar(
          content: Text(
            'Hesap silinemedi. Bağlantını kontrol edip tekrar dene.',
          ),
        ),
      );
    }
  }
}

class _DeleteDialog extends StatefulWidget {
  const _DeleteDialog({required this.username});
  final String username;

  @override
  State<_DeleteDialog> createState() => _DeleteDialogState();
}

class _DeleteDialogState extends State<_DeleteDialog> {
  final _c = TextEditingController();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final match = _c.text.trim().replaceFirst('@', '') == widget.username;
    return AlertDialog(
      title: const Text('Hesabı sil'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Profilin, kullanıcı adın, şifreleme anahtarların ve bu '
            'cihazdaki mesaj kopyaların kalıcı olarak silinir; gruplardan '
            'çıkarılırsın. Bu işlem geri alınamaz.',
          ),
          const SizedBox(height: 12),
          Text('Onaylamak için kullanıcı adını yaz: @${widget.username}'),
          TextField(
            key: const Key('deleteConfirmField'),
            controller: _c,
            autocorrect: false,
            onChanged: (_) => setState(() {}),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context, false),
          child: const Text('Vazgeç'),
        ),
        FilledButton(
          key: const Key('deleteConfirm'),
          style: FilledButton.styleFrom(
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
          onPressed: match ? () => Navigator.pop(context, true) : null,
          child: const Text('Kalıcı olarak sil'),
        ),
      ],
    );
  }
}
