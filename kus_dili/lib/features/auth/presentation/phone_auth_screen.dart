import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../application/phone_auth_controller.dart';

/// Telefon numarasıyla kayıt / giriş (aynı akış).
class PhoneAuthScreen extends ConsumerStatefulWidget {
  const PhoneAuthScreen({super.key});

  @override
  ConsumerState<PhoneAuthScreen> createState() => _PhoneAuthScreenState();
}

class _PhoneAuthScreenState extends ConsumerState<PhoneAuthScreen> {
  final _phone = TextEditingController();
  final _code = TextEditingController();
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    // Kodu yeniden gönderme geri sayımını güncel tutar.
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(phoneAuthControllerProvider);
    final c = ref.read(phoneAuthControllerProvider.notifier);
    final text = Theme.of(context).textTheme;
    final codeStep = s.step == PhoneAuthStep.enterCode;

    return Scaffold(
      appBar: AppBar(
        title: Text(codeStep ? 'Doğrulama kodu' : 'Giriş yap'),
        leading: codeStep
            ? IconButton(
                tooltip: 'Numarayı değiştir',
                icon: const Icon(Icons.arrow_back),
                onPressed: s.busy
                    ? null
                    : () {
                        _code.clear();
                        c.editPhone();
                      },
              )
            : null,
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (!codeStep) ...[
              Text(
                'Telefon numaranı gir',
                style: text.headlineSmall?.copyWith(color: AppColors.snow),
              ),
              const SizedBox(height: 8),
              const Text(
                'Numarana tek kullanımlık bir SMS kodu göndereceğiz. '
                'Numaran profilinde gösterilmez.',
              ),
              const SizedBox(height: 24),
              TextField(
                key: const Key('phone'),
                controller: _phone,
                keyboardType: TextInputType.phone,
                autofillHints: const [AutofillHints.telephoneNumber],
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9+\s()\-]')),
                ],
                enabled: !s.busy,
                decoration: const InputDecoration(
                  labelText: 'Telefon',
                  hintText: '0532 123 45 67',
                  prefixIcon: Icon(Icons.phone_iphone),
                ),
                onSubmitted: c.submitPhone,
              ),
            ] else ...[
              Text(
                'Kodu gir',
                style: text.headlineSmall?.copyWith(color: AppColors.snow),
              ),
              const SizedBox(height: 8),
              Text('${s.phone?.display} numarasına gönderilen 6 haneli kod.'),
              const SizedBox(height: 24),
              TextField(
                key: const Key('code'),
                controller: _code,
                keyboardType: TextInputType.number,
                autofillHints: const [AutofillHints.oneTimeCode],
                maxLength: 6,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                enabled: !s.busy,
                style: text.headlineSmall?.copyWith(letterSpacing: 8),
                decoration: const InputDecoration(
                  counterText: '',
                  hintText: '••••••',
                ),
                onChanged: (v) {
                  if (v.length == 6) c.submitCode(v);
                },
              ),
            ],
            if (s.error != null) ...[
              const SizedBox(height: 12),
              Text(
                s.error!,
                key: const Key('authError'),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              key: const Key('submit'),
              onPressed: s.busy
                  ? null
                  : () => codeStep
                        ? c.submitCode(_code.text)
                        : c.submitPhone(_phone.text),
              child: s.busy
                  ? const SizedBox.square(
                      dimension: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(codeStep ? 'Doğrula' : 'Kod gönder'),
            ),
            if (codeStep) _ResendButton(state: s, onResend: c.resend),
          ],
        ),
      ),
    );
  }
}

class _ResendButton extends ConsumerWidget {
  const _ResendButton({required this.state, required this.onResend});

  final PhoneAuthState state;
  final VoidCallback onResend;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final now = ref.read(clockProvider)();
    final at = state.resendAvailableAt;
    final left = at == null ? 0 : at.difference(now).inSeconds + 1;
    final waiting = at != null && now.isBefore(at);
    return TextButton(
      onPressed: state.busy || waiting ? null : onResend,
      child: Text(
        waiting ? 'Kodu tekrar gönder ($left sn)' : 'Kodu tekrar gönder',
      ),
    );
  }
}
