enum AuthFailureCode {
  invalidPhone,
  invalidCode,
  codeExpired,
  tooManyRequests,
  quotaExceeded,
  network,
  appNotAuthorized,
  unknown,
}

/// Kullanıcıya gösterilebilir kimlik doğrulama hatası.
final class AuthFailure implements Exception {
  const AuthFailure(this.code, [this.debugDetail]);

  /// Firebase Auth hata kodundan (ör. 'invalid-verification-code') üretir.
  factory AuthFailure.fromFirebaseCode(String code, [String? detail]) {
    final mapped = switch (code) {
      'invalid-phone-number' ||
      'missing-phone-number' => AuthFailureCode.invalidPhone,
      'invalid-verification-code' ||
      'missing-verification-code' => AuthFailureCode.invalidCode,
      'session-expired' ||
      'code-expired' ||
      'invalid-verification-id' => AuthFailureCode.codeExpired,
      'too-many-requests' => AuthFailureCode.tooManyRequests,
      'quota-exceeded' => AuthFailureCode.quotaExceeded,
      'network-request-failed' => AuthFailureCode.network,
      'app-not-authorized' ||
      'missing-client-identifier' ||
      'invalid-app-credential' ||
      'captcha-check-failed' ||
      'missing-app-credential' => AuthFailureCode.appNotAuthorized,
      _ => AuthFailureCode.unknown,
    };
    return AuthFailure(mapped, '$code${detail == null ? '' : ': $detail'}');
  }

  final AuthFailureCode code;

  /// Yalnızca günlük için; kullanıcıya gösterilmez.
  final String? debugDetail;

  String get message => switch (code) {
    AuthFailureCode.invalidPhone =>
      'Telefon numarası geçersiz. Örnek: 0532 123 45 67',
    AuthFailureCode.invalidCode => 'Doğrulama kodu hatalı.',
    AuthFailureCode.codeExpired =>
      'Kodun süresi doldu. Lütfen yeni kod isteyin.',
    AuthFailureCode.tooManyRequests =>
      'Çok fazla deneme yapıldı. Lütfen bir süre sonra tekrar deneyin.',
    AuthFailureCode.quotaExceeded =>
      'SMS gönderim sınırına ulaşıldı. Lütfen daha sonra tekrar deneyin.',
    AuthFailureCode.network =>
      'İnternet bağlantısı yok. Bağlantınızı kontrol edin.',
    AuthFailureCode.appNotAuthorized =>
      'Uygulama doğrulanamadı. Uygulamayı resmi mağazadan yüklediğinizden '
          'emin olun.',
    AuthFailureCode.unknown =>
      'Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.',
  };

  @override
  String toString() => 'AuthFailure($code, $debugDetail)';
}
