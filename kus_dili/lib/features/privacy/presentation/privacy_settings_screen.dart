import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../application/privacy_providers.dart';
import '../domain/pin_vault.dart';

/// Gizlilik ayarları: PIN, biyometri, uygulama kilidi, gizli sohbetler.
class PrivacySettingsScreen extends ConsumerWidget {
  const PrivacySettingsScreen({super.key});

  Future<void> _setPin(BuildContext context, WidgetRef ref) async {
    final pin = await showDialog<String>(
      context: context,
      builder: (_) => const _NewPinDialog(),
    );
    if (pin == null) return;
    await ref.read(pinVaultProvider)?.setPin(pin);
    ref.invalidate(hasPinProvider);
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('PIN kaydedildi.')));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final hasPin = ref.watch(hasPinProvider).value ?? false;
    final prefs = ref.watch(privacyPrefsProvider).value;
    final bioAvailable = ref.watch(biometricAvailableProvider).value ?? false;
    final c = ref.read(privacyPrefsProvider.notifier);
    return Scaffold(
      appBar: AppBar(title: const Text('Gizlilik')),
      body: ListView(
        children: [
          ListTile(
            key: const Key('setPin'),
            leading: const Icon(Icons.pin_outlined),
            title: Text(hasPin ? 'PIN\'i değiştir' : 'PIN oluştur'),
            subtitle: const Text(
              'Kilitli ve gizli sohbetler ile uygulama kilidi için gerekir.',
            ),
            onTap: () => _setPin(context, ref),
          ),
          if (hasPin)
            ListTile(
              key: const Key('removePin'),
              leading: const Icon(Icons.no_encryption_outlined),
              title: const Text('PIN\'i kaldır'),
              subtitle: const Text('Tüm kilitler ve gizli sohbetler açılır.'),
              onTap: () async {
                await ref.read(pinVaultProvider)?.clear();
                await c.clearLocks();
                ref.invalidate(hasPinProvider);
              },
            ),
          SwitchListTile(
            key: const Key('appLockSwitch'),
            secondary: const Icon(Icons.phonelink_lock),
            title: const Text('Uygulama kilidi'),
            subtitle: const Text('Açılışta ve arka plandan dönüşte PIN sor.'),
            value: prefs?.appLock ?? false,
            onChanged: hasPin ? c.setAppLock : null,
          ),
          SwitchListTile(
            key: const Key('biometricSwitch'),
            secondary: const Icon(Icons.fingerprint),
            title: const Text('Parmak izi / yüz tanıma'),
            subtitle: Text(
              bioAvailable
                  ? 'PIN yerine biyometri ile aç.'
                  : 'Bu cihazda kullanılabilir biyometri yok.',
            ),
            value: (prefs?.biometrics ?? false) && bioAvailable,
            onChanged: hasPin && bioAvailable ? c.setBiometrics : null,
          ),
          SwitchListTile(
            key: const Key('blockScreenshotsSwitch'),
            secondary: const Icon(Icons.screenshot_monitor_outlined),
            title: const Text('Ekran görüntüsünü engelle'),
            subtitle: const Text(
              'Android: tüm uygulamada ekran görüntüsü ve kaydı engellenir. '
              'Kilitli/gizli sohbetlerde ve tek görüntülük mesajlarda her '
              'zaman engellenir. iOS bunu desteklemez.',
            ),
            value: prefs?.blockScreenshots ?? false,
            onChanged: c.setBlockScreenshots,
          ),
          const Divider(),
          ListTile(
            key: const Key('openHiddenChats'),
            leading: const Icon(Icons.visibility_off_outlined),
            title: const Text('Gizli sohbetler'),
            subtitle: Text('${prefs?.hiddenChats.length ?? 0} sohbet'),
            enabled: hasPin,
            onTap: () => context.push(Routes.hiddenChats),
          ),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Hangi sohbetlerin gizli veya kilitli olduğu yalnızca bu cihazda '
              'saklanır; sunucuya gönderilmez. Sohbet listesinde mesaj '
              'önizlemesi gösterilmez ve sunucuda tutulmaz.',
            ),
          ),
        ],
      ),
    );
  }
}

class _NewPinDialog extends StatefulWidget {
  const _NewPinDialog();

  @override
  State<_NewPinDialog> createState() => _NewPinDialogState();
}

class _NewPinDialogState extends State<_NewPinDialog> {
  final _a = TextEditingController();
  final _b = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _a.dispose();
    _b.dispose();
    super.dispose();
  }

  Widget _field(TextEditingController c, String label, Key key) => TextField(
    key: key,
    controller: c,
    obscureText: true,
    keyboardType: TextInputType.number,
    maxLength: 8,
    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
    decoration: InputDecoration(labelText: label, counterText: ''),
  );

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Yeni PIN'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _field(_a, 'PIN (4-8 rakam)', const Key('newPin')),
          _field(_b, 'PIN tekrar', const Key('newPinAgain')),
          if (_error != null)
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Vazgeç'),
        ),
        FilledButton(
          key: const Key('savePin'),
          onPressed: () {
            final err =
                PinVault.validate(_a.text) ??
                (_a.text != _b.text ? 'PIN\'ler eşleşmiyor.' : null);
            if (err != null) {
              setState(() => _error = err);
              return;
            }
            Navigator.pop(context, _a.text);
          },
          child: const Text('Kaydet'),
        ),
      ],
    );
  }
}
