import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../application/privacy_providers.dart';
import '../domain/pin_vault.dart';

/// PIN veya biyometri ile kilit açma ekranı.
class UnlockGate extends ConsumerStatefulWidget {
  const UnlockGate({
    super.key,
    required this.title,
    required this.onUnlocked,
    this.autoBiometric = true,
  });

  final String title;
  final VoidCallback onUnlocked;
  final bool autoBiometric;

  @override
  ConsumerState<UnlockGate> createState() => _UnlockGateState();
}

class _UnlockGateState extends ConsumerState<UnlockGate> {
  final _pin = TextEditingController();
  String? _error;
  bool _busy = false;
  bool _triedBio = false;

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  Future<void> _bio() async {
    final ok = await ref
        .read(biometricAuthProvider)
        .authenticate('${widget.title} için kimliğini doğrula');
    if (ok && mounted) widget.onUnlocked();
  }

  Future<void> _submit() async {
    final vault = ref.read(pinVaultProvider);
    if (vault == null || _busy) return;
    setState(() => _busy = true);
    final r = await vault.verify(_pin.text);
    if (!mounted) return;
    _pin.clear();
    setState(() {
      _busy = false;
      _error = switch (r) {
        PinOk() => null,
        PinWrong(:final attemptsLeft) =>
          'PIN hatalı. $attemptsLeft deneme hakkın kaldı.',
        PinLockedOut(:final until) =>
          'Çok fazla hatalı deneme. ${_fmt(until.difference(DateTime.now()))} '
              'sonra tekrar dene.',
      };
    });
    if (r is PinOk) widget.onUnlocked();
  }

  static String _fmt(Duration d) =>
      d.inMinutes >= 1 ? '${d.inMinutes + 1} dk' : '${d.inSeconds + 1} sn';

  @override
  Widget build(BuildContext context) {
    final prefs = ref.watch(privacyPrefsProvider).value;
    final bioOn =
        (prefs?.biometrics ?? false) &&
        (ref.watch(biometricAvailableProvider).value ?? false);
    if (bioOn && widget.autoBiometric && !_triedBio) {
      _triedBio = true;
      scheduleMicrotask(_bio);
    }
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.lock_outline, size: 56, color: AppColors.gold),
                const SizedBox(height: 16),
                Text(widget.title, style: text.titleLarge),
                const SizedBox(height: 24),
                SizedBox(
                  width: 220,
                  child: TextField(
                    key: const Key('unlockPin'),
                    controller: _pin,
                    autofocus: !bioOn,
                    obscureText: true,
                    keyboardType: TextInputType.number,
                    textAlign: TextAlign.center,
                    maxLength: 8,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    style: text.headlineSmall?.copyWith(letterSpacing: 8),
                    decoration: const InputDecoration(
                      counterText: '',
                      hintText: 'PIN',
                    ),
                    onSubmitted: (_) => _submit(),
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    _error!,
                    key: const Key('unlockError'),
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                FilledButton(
                  key: const Key('unlockSubmit'),
                  onPressed: _busy ? null : _submit,
                  child: const Text('Kilidi aç'),
                ),
                if (bioOn)
                  TextButton.icon(
                    key: const Key('unlockBiometric'),
                    icon: const Icon(Icons.fingerprint),
                    label: const Text('Parmak izi / yüz ile aç'),
                    onPressed: _bio,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
