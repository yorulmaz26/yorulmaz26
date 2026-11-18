import 'dart:async';

import 'package:firebase_auth/firebase_auth.dart';

import '../domain/auth_failure.dart';
import '../domain/auth_repository.dart';
import '../domain/phone_number.dart';

/// Firebase Authentication ile telefon numarası doğrulaması.
final class FirebaseAuthRepository implements AuthRepository {
  FirebaseAuthRepository(this._auth) {
    // SMS ve reCAPTCHA metinleri Türkçe gönderilsin.
    unawaited(_auth.setLanguageCode('tr'));
  }

  final FirebaseAuth _auth;

  static AuthUser? _map(User? u) =>
      u == null ? null : AuthUser(uid: u.uid, phoneNumber: u.phoneNumber);

  @override
  Stream<AuthUser?> authStateChanges() => _auth.authStateChanges().map(_map);

  @override
  AuthUser? get currentUser => _map(_auth.currentUser);

  @override
  Future<PhoneVerificationResult> startPhoneVerification(
    PhoneNumber phone, {
    int? resendToken,
  }) async {
    final completer = Completer<PhoneVerificationResult>();
    void fail(Object e) {
      if (completer.isCompleted) return;
      completer.completeError(
        e is FirebaseAuthException
            ? AuthFailure.fromFirebaseCode(e.code, e.message)
            : AuthFailure(AuthFailureCode.unknown, e.toString()),
      );
    }

    try {
      await _auth.verifyPhoneNumber(
        phoneNumber: phone.e164,
        forceResendingToken: resendToken,
        timeout: const Duration(seconds: 60),
        verificationCompleted: (credential) async {
          // Yalnızca Android: SMS otomatik okundu.
          try {
            await _auth.signInWithCredential(credential);
            if (!completer.isCompleted) {
              completer.complete(const AutoVerified());
            }
          } catch (e) {
            fail(e);
          }
        },
        verificationFailed: fail,
        codeSent: (verificationId, token) {
          if (!completer.isCompleted) {
            completer.complete(
              CodeSent(verificationId: verificationId, resendToken: token),
            );
          }
        },
        codeAutoRetrievalTimeout: (_) {},
      );
    } catch (e) {
      fail(e);
    }
    return completer.future;
  }

  @override
  Future<void> confirmSmsCode({
    required String verificationId,
    required String smsCode,
  }) async {
    try {
      await _auth.signInWithCredential(
        PhoneAuthProvider.credential(
          verificationId: verificationId,
          smsCode: smsCode,
        ),
      );
    } on FirebaseAuthException catch (e) {
      throw AuthFailure.fromFirebaseCode(e.code, e.message);
    }
  }

  @override
  Future<void> signOut() => _auth.signOut();
}
