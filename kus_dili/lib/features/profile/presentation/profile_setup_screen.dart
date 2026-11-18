import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../notifications/application/push_providers.dart';
import '../application/profile_providers.dart';
import '../domain/username.dart';

class ProfileSetupScreen extends ConsumerStatefulWidget {
  const ProfileSetupScreen({super.key});

  @override
  ConsumerState<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends ConsumerState<ProfileSetupScreen> {
  final _username = TextEditingController();
  final _displayName = TextEditingController();
  String? _usernameHint;

  @override
  void dispose() {
    _username.dispose();
    _displayName.dispose();
    super.dispose();
  }

  void _submit() => ref
      .read(profileSetupControllerProvider.notifier)
      .submit(username: _username.text, displayName: _displayName.text);

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(profileSetupControllerProvider);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profil oluştur'),
        actions: [
          TextButton(
            onPressed: () => signOutWithPushCleanup(ref),
            child: const Text('Çıkış'),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              'Arkadaşların seni nasıl bulsun?',
              style: text.headlineSmall?.copyWith(color: AppColors.snow),
            ),
            const SizedBox(height: 8),
            const Text(
              'Kullanıcı adın herkese açıktır ve sonradan değiştirilemez.',
            ),
            const SizedBox(height: 24),
            TextField(
              key: const Key('displayName'),
              controller: _displayName,
              enabled: !s.busy,
              maxLength: DisplayName.maxLength,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(
                labelText: 'Adın',
                prefixIcon: Icon(Icons.person_outline),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              key: const Key('username'),
              controller: _username,
              enabled: !s.busy,
              maxLength: Username.maxLength + 1,
              autocorrect: false,
              decoration: InputDecoration(
                labelText: 'Kullanıcı adı',
                prefixText: '@',
                errorText: _usernameHint,
              ),
              onChanged: (v) {
                final n = Username.normalize(v);
                setState(() {
                  _usernameHint = n.isEmpty ? null : Username.validate(n);
                });
              },
              onSubmitted: (_) => _submit(),
            ),
            if (s.error != null) ...[
              const SizedBox(height: 8),
              Text(
                s.error!,
                key: const Key('profileError'),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              key: const Key('saveProfile'),
              onPressed: s.busy ? null : _submit,
              child: s.busy
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Devam et'),
            ),
            const SizedBox(height: 8),
            TextButton(
              key: const Key('openTerms'),
              onPressed: () => context.push(Routes.terms),
              child: const Text(
                'Devam ederek Kullanım Koşulları\'nı kabul edersin. '
                'Taciz, spam ve yasa dışı içeriğe izin verilmez.',
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
