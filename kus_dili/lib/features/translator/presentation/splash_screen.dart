import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../auth/application/auth_providers.dart';
import '../../profile/application/profile_providers.dart';

/// Açılış ekranı. Oturum/profil yüklenirken gösterilir; yönlendirme
/// GoRouter `redirect` ile otomatik yapılır.
class SplashScreen extends ConsumerWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final isError = ref.watch(sessionStatusProvider) == SessionStatus.error;
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.flutter_dash, size: 72, color: AppColors.gold),
              const SizedBox(height: 16),
              Text(
                'Kuş Dili',
                style: text.displaySmall?.copyWith(
                  color: AppColors.snow,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Sadece anlayan anlasın.',
                style: text.titleMedium?.copyWith(color: AppColors.turquoise),
              ),
              const SizedBox(height: 32),
              if (isError) ...[
                const Text(
                  'Hesap bilgileri alınamadı.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () {
                    ref.invalidate(authStateProvider);
                    ref.invalidate(currentProfileProvider);
                  },
                  child: const Text('Tekrar dene'),
                ),
              ] else
                const SizedBox.square(
                  dimension: 24,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              const SizedBox(height: 16),
              TextButton(
                onPressed: () => context.push(Routes.learn),
                child: const Text('Kuş dilini öğren (çevrimdışı)'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
