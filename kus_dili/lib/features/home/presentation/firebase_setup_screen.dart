import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../core/firebase/firebase_providers.dart';

/// Firebase yapılandırma dosyaları yoksa / başlatma başarısızsa gösterilir.
/// Uygulama çökmez; çevrimdışı çevirici kullanılabilir.
class FirebaseSetupScreen extends ConsumerWidget {
  const FirebaseSetupScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final error = ref.watch(firebaseStatusProvider).error;
    return Scaffold(
      appBar: AppBar(title: const Text('Bağlantı kurulamadı')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const Text(
            'Mesajlaşma sunucusuna bağlanılamadı. Geliştiriciyseniz '
            '`flutterfire configure` komutunu çalıştırıp uygulamayı yeniden '
            'derleyin (bkz. docs/FIREBASE_SETUP.md).',
          ),
          const SizedBox(height: 12),
          if (error != null)
            SelectableText(
              '$error',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: () => context.push(Routes.learn),
            child: const Text('Kuş dilini öğren (çevrimdışı)'),
          ),
        ],
      ),
    );
  }
}
