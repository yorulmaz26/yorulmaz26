import 'phone_number.dart';

/// Oturum açmış kullanıcı (yalnızca kimlik bilgisi; profil ayrı tutulur).
final class AuthUser {
  const AuthUser({required this.uid, this.phoneNumber});

  final String uid;
  final String? phoneNumber;
}

sealed class PhoneVerificationResult {
  const PhoneVerificationResult();
}

/// SMS gönderildi; kullanıcı kodu girmeli.
final class CodeSent extends PhoneVerificationResult {
  const CodeSent({required this.verificationId, this.resendToken});

  final String verificationId;
  final int? resendToken;
}

/// Android SMS'i otomatik okudu ve oturum açıldı.
final class AutoVerified extends PhoneVerificationResult {
  const AutoVerified();
}

/// Kimlik doğrulama sözleşmesi. Hatalar [AuthFailure] olarak fırlatılır.
abstract interface class AuthRepository {
  Stream<AuthUser?> authStateChanges();

  AuthUser? get currentUser;

  Future<PhoneVerificationResult> startPhoneVerification(
    PhoneNumber phone, {
    int? resendToken,
  });

  Future<void> confirmSmsCode({
    required String verificationId,
    required String smsCode,
  });

  Future<void> signOut();
}
