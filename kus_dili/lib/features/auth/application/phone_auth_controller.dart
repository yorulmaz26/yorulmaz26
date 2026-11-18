import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../domain/auth_failure.dart';
import '../domain/auth_repository.dart';
import '../domain/phone_number.dart';
import 'auth_providers.dart';

enum PhoneAuthStep { enterPhone, enterCode }

final class PhoneAuthState {
  const PhoneAuthState({
    this.step = PhoneAuthStep.enterPhone,
    this.phone,
    this.verificationId,
    this.resendToken,
    this.resendAvailableAt,
    this.busy = false,
    this.error,
  });

  final PhoneAuthStep step;
  final PhoneNumber? phone;
  final String? verificationId;
  final int? resendToken;
  final DateTime? resendAvailableAt;
  final bool busy;
  final String? error;

  PhoneAuthState copyWith({
    PhoneAuthStep? step,
    PhoneNumber? phone,
    String? verificationId,
    int? resendToken,
    DateTime? resendAvailableAt,
    bool? busy,
    String? error,
    bool clearError = false,
  }) => PhoneAuthState(
    step: step ?? this.step,
    phone: phone ?? this.phone,
    verificationId: verificationId ?? this.verificationId,
    resendToken: resendToken ?? this.resendToken,
    resendAvailableAt: resendAvailableAt ?? this.resendAvailableAt,
    busy: busy ?? this.busy,
    error: clearError ? null : (error ?? this.error),
  );
}

/// Test için saat enjekte edilebilir.
final clockProvider = Provider<DateTime Function()>((ref) => DateTime.now);

/// Telefon numarası → SMS kodu → oturum akışı.
class PhoneAuthController extends Notifier<PhoneAuthState> {
  static const resendCooldown = Duration(seconds: 60);
  static final RegExp _code = RegExp(r'^\d{6}$');

  @override
  PhoneAuthState build() {
    // Oturum açılınca/kapanınca akış baştan başlar (ör. çıkıştan sonra
    // eski "kodu gir" adımı görünmesin).
    ref.watch(authStateProvider.select((a) => a.value?.uid));
    return const PhoneAuthState();
  }

  AuthRepository get _repo => ref.read(authRepositoryProvider);
  DateTime _now() => ref.read(clockProvider)();

  Future<void> submitPhone(String raw) async {
    if (state.busy) return;
    final phone = PhoneNumber.tryParse(raw);
    if (phone == null) {
      state = state.copyWith(
        error: const AuthFailure(AuthFailureCode.invalidPhone).message,
      );
      return;
    }
    await _send(phone, resendToken: null);
  }

  Future<void> resend() async {
    final phone = state.phone;
    final at = state.resendAvailableAt;
    if (phone == null || state.busy) return;
    if (at != null && _now().isBefore(at)) return;
    await _send(phone, resendToken: state.resendToken);
  }

  Future<void> _send(PhoneNumber phone, {required int? resendToken}) async {
    state = state.copyWith(busy: true, phone: phone, clearError: true);
    try {
      final r = await _repo.startPhoneVerification(
        phone,
        resendToken: resendToken,
      );
      if (!ref.mounted) return;
      switch (r) {
        case CodeSent(:final verificationId, resendToken: final token):
          state = PhoneAuthState(
            step: PhoneAuthStep.enterCode,
            phone: phone,
            verificationId: verificationId,
            resendToken: token,
            resendAvailableAt: _now().add(resendCooldown),
          );
        case AutoVerified():
          // Oturum açıldı; yönlendirici profil/ana ekrana geçirir.
          state = state.copyWith(busy: false);
      }
    } on AuthFailure catch (e) {
      if (!ref.mounted) return;
      state = state.copyWith(busy: false, error: e.message);
    }
  }

  Future<void> submitCode(String raw) async {
    final id = state.verificationId;
    if (id == null || state.busy) return;
    final code = raw.replaceAll(RegExp(r'\s'), '');
    if (!_code.hasMatch(code)) {
      state = state.copyWith(error: 'Kod 6 haneli olmalı.');
      return;
    }
    state = state.copyWith(busy: true, clearError: true);
    try {
      await _repo.confirmSmsCode(verificationId: id, smsCode: code);
      if (!ref.mounted) return;
      state = state.copyWith(busy: false);
    } on AuthFailure catch (e) {
      if (!ref.mounted) return;
      state = state.copyWith(busy: false, error: e.message);
    }
  }

  /// "Numarayı değiştir".
  void editPhone() => state = PhoneAuthState(phone: state.phone);
}

final phoneAuthControllerProvider =
    NotifierProvider<PhoneAuthController, PhoneAuthState>(
      PhoneAuthController.new,
    );
