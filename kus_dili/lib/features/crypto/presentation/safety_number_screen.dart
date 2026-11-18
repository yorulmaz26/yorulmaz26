import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/application/chat_providers.dart';
import '../application/crypto_providers.dart';
import '../domain/safety_number.dart';

final _safetyNumberProvider = FutureProvider.family<String?, String>((
  ref,
  otherUid,
) async {
  final me = ref.watch(myUidProvider);
  final store = ref.watch(signalStoreProvider);
  if (me == null || store == null) return null;
  await ref.watch(keyManagerProvider)?.ensureKeys();
  final other = await ref.watch(keyDirectoryProvider).fetchIdentity(otherUid);
  if (other == null) return null;
  final mine = await store.getIdentityKeyPair();
  return safetyNumber(
    myUid: me,
    myIdentityKey: mine.getPublicKey().serialize(),
    otherUid: otherUid,
    otherIdentityKey: other.identityKey,
  );
});

/// Güvenlik numarası: iki taraf yan yana karşılaştırır; aynıysa araya giren
/// (ortadaki adam) yoktur.
class SafetyNumberScreen extends ConsumerWidget {
  const SafetyNumberScreen({super.key, required this.otherUid});

  final String otherUid;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final number = ref.watch(_safetyNumberProvider(otherUid));
    final other = ref.watch(profileByUidProvider(otherUid)).value;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Güvenlik numarası')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text(
            '${other?.displayName ?? 'Karşı taraf'} ile aranızdaki mesajlar '
            'uçtan uca şifreli. Aşağıdaki numarayı onun ekranındakiyle '
            'karşılaştırın (yüz yüze veya güvendiğiniz başka bir kanaldan). '
            'Aynıysa konuşmanıza kimse aracılık etmiyor.',
          ),
          const SizedBox(height: 24),
          number.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => const Text('Güvenlik numarası hesaplanamadı.'),
            data: (n) => n == null
                ? const Text('Karşı tarafın şifreleme anahtarı henüz yok.')
                : SelectableText(
                    n,
                    key: const Key('safetyNumber'),
                    textAlign: TextAlign.center,
                    style: text.headlineSmall?.copyWith(
                      fontFeatures: const [FontFeature.tabularFigures()],
                      letterSpacing: 2,
                      height: 1.6,
                      color: AppColors.snow,
                    ),
                  ),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            key: const Key('markVerified'),
            icon: const Icon(Icons.verified_user_outlined),
            label: const Text('Karşılaştırdım, aynı'),
            onPressed: () async {
              await ref
                  .read(signalStoreProvider)
                  ?.acknowledgeIdentity(otherUid);
              ref.invalidate(identityChangedProvider(otherUid));
              if (context.mounted) Navigator.of(context).pop();
            },
          ),
        ],
      ),
    );
  }
}
