import 'dart:async';

import 'package:kus_dili/features/auth/domain/auth_failure.dart';
import 'package:kus_dili/features/auth/domain/auth_repository.dart';
import 'package:kus_dili/features/auth/domain/phone_number.dart';

/// YALNIZCA TESTLER İÇİN bellek içi kimlik doğrulama. Gerçek SMS göndermez.
class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({AuthUser? signedIn}) : _user = signedIn;

  static const validCode = '123456';

  AuthUser? _user;
  final _changes = StreamController<AuthUser?>.broadcast();

  /// Ayarlanırsa bir sonraki SMS isteği bu hatayla başarısız olur.
  AuthFailure? nextSendFailure;
  bool autoVerify = false;
  final List<(PhoneNumber, int?)> sendCalls = [];
  PhoneNumber? _pending;

  @override
  Stream<AuthUser?> authStateChanges() async* {
    yield _user;
    yield* _changes.stream;
  }

  @override
  AuthUser? get currentUser => _user;

  void _signIn(PhoneNumber phone) {
    _user = AuthUser(
      uid: 'uid-${phone.e164.substring(1)}',
      phoneNumber: phone.e164,
    );
    _changes.add(_user);
  }

  @override
  Future<PhoneVerificationResult> startPhoneVerification(
    PhoneNumber phone, {
    int? resendToken,
  }) async {
    sendCalls.add((phone, resendToken));
    final f = nextSendFailure;
    if (f != null) {
      nextSendFailure = null;
      throw f;
    }
    if (autoVerify) {
      _signIn(phone);
      return const AutoVerified();
    }
    _pending = phone;
    return CodeSent(verificationId: 'vid-${sendCalls.length}', resendToken: 42);
  }

  @override
  Future<void> confirmSmsCode({
    required String verificationId,
    required String smsCode,
  }) async {
    if (smsCode != validCode) {
      throw const AuthFailure(AuthFailureCode.invalidCode);
    }
    _signIn(_pending!);
  }

  @override
  Future<void> signOut() async {
    _user = null;
    _changes.add(null);
  }
}
