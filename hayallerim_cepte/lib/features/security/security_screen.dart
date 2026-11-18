import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import 'lock_controller.dart';
import 'lock_screen.dart';

final _hasPinProvider = FutureProvider.autoDispose<bool>((ref) => ref.watch(lockServiceProvider).hasPin());
final _bioProvider = FutureProvider.autoDispose<bool>((ref) => ref.watch(lockServiceProvider).biometricsAvailable());

class SecurityScreen extends ConsumerWidget {
  const SecurityScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(settingsProvider);
    final c = ref.read(settingsProvider.notifier);
    final hasPin = ref.watch(_hasPinProvider).value ?? false;
    final bio = ref.watch(_bioProvider).value ?? false;
    return Scaffold(
      appBar: AppBar(title: const Text('Güvenlik ve gizlilik')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 40),
        children: [
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              ListTile(
                leading: const Icon(Icons.pin_outlined),
                title: Text(hasPin ? 'PIN\'i değiştir' : 'PIN oluştur'),
                subtitle: const Text('4–8 haneli uygulama PIN\'i'),
                onTap: () async {
                  if (hasPin && !await _verify(context)) return;
                  if (!context.mounted) return;
                  final pin = await _newPin(context);
                  if (pin == null) return;
                  await ref.read(lockServiceProvider).setPin(pin);
                  ref.invalidate(_hasPinProvider);
                  if (context.mounted) toast(context, 'PIN kaydedildi');
                },
              ),
              SwitchListTile(
                secondary: const Icon(Icons.lock_outline),
                title: const Text('Uygulama kilidi'),
                subtitle: const Text('Açılışta ve arka plandan dönüşte PIN iste'),
                value: s.lockEnabled && hasPin,
                onChanged: !hasPin ? null : (v) => c.update((x) => x.copyWith(lockEnabled: v)),
              ),
              SwitchListTile(
                secondary: const Icon(Icons.fingerprint),
                title: const Text('Biyometrik doğrulama'),
                subtitle: Text(bio ? 'Parmak izi / Yüz tanıma' : 'Bu cihazda kullanılamıyor'),
                value: s.biometricEnabled && bio && hasPin,
                onChanged: !(bio && hasPin)
                    ? null
                    : (v) async {
                        if (v && !await ref.read(lockServiceProvider).authenticateBiometric('Biyometrik kilidi etkinleştir')) return;
                        await c.update((x) => x.copyWith(biometricEnabled: v));
                      },
              ),
              ListTile(
                enabled: s.lockEnabled,
                leading: const Icon(Icons.timer_outlined),
                title: const Text('Otomatik kilit'),
                trailing: DropdownButton<int>(
                  value: const [0, 30, 60, 300].contains(s.lockTimeoutSeconds) ? s.lockTimeoutSeconds : 30,
                  underline: const SizedBox(),
                  items: const [
                    DropdownMenuItem(value: 0, child: Text('Hemen')),
                    DropdownMenuItem(value: 30, child: Text('30 sn')),
                    DropdownMenuItem(value: 60, child: Text('1 dk')),
                    DropdownMenuItem(value: 300, child: Text('5 dk')),
                  ],
                  onChanged: s.lockEnabled ? (v) => c.update((x) => x.copyWith(lockTimeoutSeconds: v)) : null,
                ),
              ),
              if (hasPin)
                ListTile(
                  leading: const Icon(Icons.lock_reset),
                  title: const Text('PIN\'i kaldır'),
                  subtitle: const Text('Uygulama kilidi ve biyometrik de kapanır'),
                  onTap: () async {
                    if (!await _verify(context)) return;
                    await ref.read(lockServiceProvider).removePin();
                    await c.update((x) => x.copyWith(lockEnabled: false, biometricEnabled: false));
                    ref.invalidate(_hasPinProvider);
                  },
                ),
              if (s.lockEnabled && hasPin)
                ListTile(
                  leading: const Icon(Icons.lock),
                  title: const Text('Şimdi kilitle'),
                  onTap: () => ref.read(lockProvider.notifier).lockNow(),
                ),
            ]),
          ),
          const SectionHeader('Verilerin nasıl korunuyor?'),
          const SoftCard(
            child: Text(
              '• PIN düz metin olarak saklanmaz; tuzlanmış PBKDF2 özeti iOS Keychain / Android Keystore\'da tutulur. Art arda hatalı denemelerde bekleme süresi uygulanır.\n'
              '• Gizli hayallerin açıklama ve notları, özel günlüklerin metni ve zaman kapsülü içerikleri AES-256-GCM ile şifrelenerek veritabanına yazılır. Şifreleme anahtarı yalnızca cihazın güvenli deposundadır.\n'
              '• Gizli içerikler, kilit açılmadan listelerde ve panoda gösterilmez; uygulama arka plana alındığında gizli içerik oturumu kapanır.\n'
              '• Yedek dosyaları, senin belirlediğin parolayla (PBKDF2 + AES-256-GCM) şifrelenir.\n'
              '• Bilinen sınır: fotoğraf ve ses dosyaları uygulamanın özel klasöründe şifresiz durur (işletim sistemi korumasıyla). Cihaz saati değiştirilerek zaman kapsülleri erken açılabilir.',
              style: TextStyle(height: 1.45),
            ),
          ),
        ],
      ),
    );
  }

  Future<bool> _verify(BuildContext context) async {
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (c) => Padding(
        padding: const EdgeInsets.only(bottom: 24),
        child: PinPad(title: 'Mevcut PIN', autoBiometric: false, onSuccess: () => Navigator.pop(c, true)),
      ),
    );
    return ok == true;
  }

  Future<String?> _newPin(BuildContext context) async {
    final a = TextEditingController();
    final b = TextEditingController();
    String? error;
    final r = await showDialog<String>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, setState) => AlertDialog(
          title: const Text('Yeni PIN'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: a, obscureText: true, keyboardType: TextInputType.number, maxLength: 8, decoration: const InputDecoration(labelText: 'PIN (4–8 hane)')),
            TextField(controller: b, obscureText: true, keyboardType: TextInputType.number, maxLength: 8, decoration: InputDecoration(labelText: 'PIN tekrar', errorText: error)),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c), child: const Text('Vazgeç')),
            FilledButton(
              onPressed: () {
                if (!RegExp(r'^\d{4,8}$').hasMatch(a.text)) return setState(() => error = 'PIN 4–8 rakam olmalı');
                if (a.text != b.text) return setState(() => error = 'PIN\'ler eşleşmiyor');
                Navigator.pop(c, a.text);
              },
              child: const Text('Kaydet'),
            ),
          ],
        ),
      ),
    );
    a.dispose();
    b.dispose();
    return r;
  }
}
