import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/application/chat_providers.dart';
import '../application/crypto_providers.dart';
import '../domain/safety_number.dart';

typedef _Safety = ({String number, int myDevices, int otherDevices});

final _safetyNumberProvider = FutureProvider.family<_Safety?, String>((
  ref,
  otherUid,
) async {
  final me = ref.watch(myUidProvider);
  if (me == null) return null;
  await ref.watch(keyManagerProvider)?.ensureKeys();
  final dir = ref.watch(keyDirectoryProvider);
  final mine = await dir.listDevices(me);
  final theirs = await dir.listDevices(otherUid);
  if (mine.isEmpty || theirs.isEmpty) return null;
  return (
    number: safetyNumber(
      myUid: me,
      myIdentityKeys: [for (final d in mine) d.identity.identityKey],
      otherUid: otherUid,
      otherIdentityKeys: [for (final d in theirs) d.identity.identityKey],
    ),
    myDevices: mine.length,
    otherDevices: theirs.length,
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
                : Column(
                    children: [
                      SelectableText(
                        n.number,
                        key: const Key('safetyNumber'),
                        textAlign: TextAlign.center,
                        style: text.headlineSmall?.copyWith(
                          fontFeatures: const [FontFeature.tabularFigures()],
                          letterSpacing: 2,
                          height: 1.6,
                          color: AppColors.snow,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'Senin ${n.myDevices} cihazın, '
                        '${other?.displayName ?? 'karşı tarafın'} '
                        '${n.otherDevices} cihazı hesaba katıldı. Biriniz cihaz '
                        'ekler ya da kaldırırsa numara değişir.',
                        key: const Key('deviceCounts'),
                        textAlign: TextAlign.center,
                        style: text.bodySmall,
                      ),
                    ],
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
