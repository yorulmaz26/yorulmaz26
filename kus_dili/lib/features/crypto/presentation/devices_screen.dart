import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/application/chat_providers.dart';
import '../application/crypto_providers.dart';
import '../domain/key_directory.dart';

String _date(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.'
    '${d.year} ${d.hour.toString().padLeft(2, '0')}:'
    '${d.minute.toString().padLeft(2, '0')}';

/// Hesaba bağlı cihazlar: görüntüle, kaldır, yeni cihaz uyarısını onayla.
class DevicesScreen extends ConsumerWidget {
  const DevicesScreen({super.key});

  Future<void> _remove(
    BuildContext context,
    WidgetRef ref,
    DeviceInfo d,
  ) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${d.name} kaldırılsın mı?'),
        content: const Text(
          'Bu cihaz yeni mesajları alamaz; açıldığında oturumu kapanır ve '
          'üzerindeki mesajlar silinir.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Vazgeç'),
          ),
          FilledButton(
            key: const Key('confirmRemoveDevice'),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Kaldır'),
          ),
        ],
      ),
    );
    final uid = ref.read(myUidProvider);
    if (ok != true || uid == null) return;
    try {
      await ref.read(keyDirectoryProvider).removeDevice(uid, d.deviceId);
      ref
        ..invalidate(myDevicesProvider)
        ..invalidate(e2eeBootstrapProvider);
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Cihaz kaldırılamadı.')));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final devices = ref.watch(myDevicesProvider);
    final me = ref.watch(thisDeviceIdProvider).value;
    final alert = ref.watch(newOwnDeviceProvider).value ?? false;
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Bağlı cihazlar')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (alert)
            Card(
              key: const Key('newDeviceAlert'),
              color: AppColors.gold.withValues(alpha: 0.15),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Hesabına yeni bir cihaz bağlandı. Sen değilsen hemen '
                      'kaldır ve telefon numaranın güvenliğini kontrol et.',
                    ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        key: const Key('ackNewDevice'),
                        onPressed: () async {
                          final uid = ref.read(myUidProvider);
                          if (uid == null) return;
                          await ref
                              .read(signalStoreProvider)
                              ?.acknowledgeIdentity(uid);
                          ref
                            ..invalidate(newOwnDeviceProvider)
                            ..invalidate(identityChangedProvider(uid));
                        },
                        child: const Text('Hepsi benim'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          Text(
            'Hesabına en fazla $maxDevices cihaz bağlanabilir. Her cihazın '
            'kendi şifreleme anahtarı vardır. Yeni bağlanan cihaz yalnızca '
            'bağlandıktan sonraki mesajları okuyabilir; eski mesajlar ona '
            'aktarılmaz.',
            style: text.bodyMedium,
          ),
          const SizedBox(height: 16),
          devices.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, _) => const Text('Cihazlar yüklenemedi.'),
            data: (list) => Column(
              children: [
                for (final d in list)
                  Card(
                    child: ListTile(
                      key: Key('device-${d.deviceId}'),
                      leading: Icon(
                        d.name.contains('iPhone')
                            ? Icons.phone_iphone
                            : Icons.phone_android,
                      ),
                      title: Text(
                        d.deviceId == me ? '${d.name} (bu cihaz)' : d.name,
                      ),
                      subtitle: d.lastSeen == null
                          ? null
                          : Text('Son etkinlik: ${_date(d.lastSeen!)}'),
                      trailing: d.deviceId == me
                          ? null
                          : TextButton(
                              key: Key('removeDevice-${d.deviceId}'),
                              onPressed: () => _remove(context, ref, d),
                              child: const Text('Kaldır'),
                            ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
