import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../data/providers.dart';
import 'lock_controller.dart';

/// PIN klavyesi + biyometrik doğrulama. Hem uygulama kilidi hem de gizli
/// içerik doğrulaması için kullanılır.
class PinPad extends ConsumerStatefulWidget {
  const PinPad({super.key, required this.onSuccess, this.title = 'PIN kodunu gir', this.autoBiometric = true});
  final VoidCallback onSuccess;
  final String title;
  final bool autoBiometric;

  @override
  ConsumerState<PinPad> createState() => _PinPadState();
}

class _PinPadState extends ConsumerState<PinPad> {
  String _pin = '';
  String? _error;
  bool _busy = false;
  bool _bioAvailable = false;

  @override
  void initState() {
    super.initState();
    _initBio();
  }

  Future<void> _initBio() async {
    final s = ref.read(settingsProvider);
    if (!s.biometricEnabled) return;
    final ok = await ref.read(lockServiceProvider).biometricsAvailable();
    if (!mounted) return;
    setState(() => _bioAvailable = ok);
    if (ok && widget.autoBiometric) _bio();
  }

  Future<void> _bio() async {
    final ok = await ref.read(lockServiceProvider).authenticateBiometric('Hayallerim Cepte kilidini aç');
    if (ok && mounted) widget.onSuccess();
  }

  Future<void> _press(String d) async {
    if (_busy) return;
    HapticFeedback.selectionClick();
    if (_pin.length >= 8) return;
    setState(() {
      _pin += d;
      _error = null;
    });
    if (_pin.length >= 4) await _tryVerify(auto: true);
  }

  Future<void> _tryVerify({bool auto = false}) async {
    final svc = ref.read(lockServiceProvider);
    final wait = await svc.lockoutRemaining();
    if (wait != null) {
      setState(() {
        _error = 'Çok fazla hatalı deneme. ${wait.inSeconds} sn bekleyin.';
        _pin = '';
      });
      return;
    }
    setState(() => _busy = true);
    final ok = await svc.verifyPin(_pin);
    if (!mounted) return;
    setState(() => _busy = false);
    if (ok) {
      widget.onSuccess();
    } else if (!auto || _pin.length >= 8) {
      HapticFeedback.heavyImpact();
      setState(() {
        _error = 'PIN hatalı';
        _pin = '';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    Widget key(String label, {VoidCallback? onTap, IconData? icon, String? semantics}) => Semantics(
          button: true,
          label: semantics ?? label,
          child: InkResponse(
            onTap: onTap,
            radius: 40,
            child: SizedBox(
              width: 76,
              height: 76,
              child: Center(
                child: icon != null
                    ? Icon(icon, size: 28)
                    : Text(label, style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w600)),
              ),
            ),
          ),
        );
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(widget.title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 20),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: List.generate(
            _pin.length < 4 ? 4 : _pin.length,
            (i) => AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              margin: const EdgeInsets.symmetric(horizontal: 7),
              width: 14,
              height: 14,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: i < _pin.length ? cs.primary : Colors.transparent,
                border: Border.all(color: cs.primary, width: 2),
              ),
            ),
          ),
        ),
        SizedBox(height: 28, child: Center(child: Text(_error ?? '', style: const TextStyle(color: AppColors.danger)))),
        for (final row in const [
          ['1', '2', '3'],
          ['4', '5', '6'],
          ['7', '8', '9'],
        ])
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [for (final d in row) key(d, onTap: () => _press(d))]),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _bioAvailable ? key('', icon: Icons.fingerprint, onTap: _bio, semantics: 'Biyometrik doğrulama') : const SizedBox(width: 76),
            key('0', onTap: () => _press('0')),
            key('', icon: Icons.backspace_outlined, semantics: 'Sil', onTap: () {
              if (_pin.isNotEmpty) setState(() => _pin = _pin.substring(0, _pin.length - 1));
            }),
          ],
        ),
      ],
    );
  }
}

class LockScreen extends ConsumerWidget {
  const LockScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Column(
              children: [
                Container(
                  width: 72,
                  height: 72,
                  decoration: const BoxDecoration(gradient: AppColors.brandGradient, shape: BoxShape.circle),
                  child: const Icon(Icons.lock_rounded, color: Colors.white, size: 34),
                ),
                const SizedBox(height: 16),
                Text('Hayallerim Cepte', style: Theme.of(context).textTheme.headlineSmall),
                const SizedBox(height: 24),
                PinPad(onSuccess: () => ref.read(lockProvider.notifier).unlock()),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Gizli içeriği açmak için PIN/biyometrik ister. PIN yoksa kurmaya yönlendirir.
Future<bool> requestPrivateAccess(BuildContext context, WidgetRef ref) async {
  if (ref.read(lockProvider).privateUnlocked) return true;
  final hasPin = await ref.read(lockServiceProvider).hasPin();
  if (!context.mounted) return false;
  if (!hasPin) {
    await showDialog<void>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Önce PIN oluştur'),
        content: const Text('Gizli hayaller ve özel günlükler PIN ile korunur. Profil › Güvenlik bölümünden bir PIN oluşturabilirsin.'),
        actions: [TextButton(onPressed: () => Navigator.pop(c), child: const Text('Tamam'))],
      ),
    );
    return false;
  }
  final ok = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (c) => Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: PinPad(title: 'Gizli içeriği aç', onSuccess: () => Navigator.pop(c, true)),
    ),
  );
  if (ok == true) {
    ref.read(lockProvider.notifier).setPrivateUnlocked(true);
    return true;
  }
  return false;
}
