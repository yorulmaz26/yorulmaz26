import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../auth/application/auth_providers.dart';
import '../../auth/domain/phone_number.dart';
import '../../profile/application/profile_providers.dart';

/// Oturum açmış kullanıcının ana ekranı. Sohbet listesi Aşama 4'te
/// gerçek Firestore verisiyle buraya eklenecek.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(currentProfileProvider).value;
    final user = ref.watch(authStateProvider).value;
    final phone = PhoneNumber.tryParse(user?.phoneNumber ?? '');
    final text = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Kuş Dili'),
        actions: [
          IconButton(
            tooltip: 'Çevirici',
            icon: const Icon(Icons.translate),
            onPressed: () => context.push(Routes.translator),
          ),
        ],
      ),
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
          const SizedBox(height: 24),
          Text(
            'Mesajlaşma bir sonraki geliştirme aşamasında etkinleşecek.',
            style: text.bodyMedium?.copyWith(color: AppColors.snow),
          ),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            key: const Key('signOut'),
            icon: const Icon(Icons.logout),
            label: const Text('Çıkış yap'),
            onPressed: () => ref.read(authRepositoryProvider).signOut(),
          ),
        ],
      ),
    );
  }
}
