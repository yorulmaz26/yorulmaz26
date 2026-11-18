import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../chat/application/chat_providers.dart';
import '../application/push_providers.dart';

/// Bildirim ayarları.
class NotificationSettingsScreen extends ConsumerWidget {
  const NotificationSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final prefs = ref.watch(pushPrefsProvider).value;
    final registered = ref.watch(pushRegistrationProvider).value ?? false;
    final uid = ref.watch(myUidProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Bildirimler')),
      body: ListView(
        children: [
          SwitchListTile(
            key: const Key('pushEnabled'),
            secondary: const Icon(Icons.notifications_outlined),
            title: const Text('Yeni mesaj bildirimleri'),
            value: prefs?.enabled ?? true,
            onChanged: uid == null
                ? null
                : (v) => ref.read(pushRepositoryProvider).setEnabled(uid, v),
          ),
          ListTile(
            leading: const Icon(Icons.notifications_off_outlined),
            title: const Text('Sessize alınan sohbetler'),
            subtitle: Text(
              '${prefs?.muted.length ?? 0} sohbet · sohbet ayarlarından '
              'değiştirilir',
            ),
          ),
          if (!registered)
            const ListTile(
              leading: Icon(Icons.info_outline),
              title: Text('Bu cihazda bildirim izni yok'),
              subtitle: Text(
                'Telefon ayarlarından Kuş Dili için bildirimlere izin ver.',
              ),
            ),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Mesajlar uçtan uca şifreli olduğu için sunucu içeriği bilmez. '
              'Bildirimlerde gönderen ve mesaj içeriği gösterilmez; yalnızca '
              '"Yeni bir mesajın var" yazar. Dokununca ilgili sohbet açılır.',
            ),
          ),
        ],
      ),
    );
  }
}
