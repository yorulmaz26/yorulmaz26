import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';

import '../../../app/theme/app_colors.dart';
import '../../auth/application/auth_providers.dart';
import '../../auth/domain/phone_number.dart';
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
        ],
      ),
    );
  }
}
