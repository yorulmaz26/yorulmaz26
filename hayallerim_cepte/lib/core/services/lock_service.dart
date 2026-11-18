import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:local_auth/local_auth.dart';

import 'crypto_service.dart';

/// Uygulama PIN'i ve biyometrik doğrulama.
///
/// PIN düz metin olarak saklanmaz: PBKDF2-HMAC-SHA256 (rastgele tuzla) özeti
/// güvenli depoda (Keychain/Keystore) tutulur. Art arda 5 hatalı denemeden
/// sonra artan bekleme süresi uygulanır.
class LockService {
  LockService(this._store);

  final KeyStore _store;
  final _auth = LocalAuthentication();

  static const _pinKey = 'hc_pin_hash_v1';
  static const _failKey = 'hc_pin_failures';
  static const _iterations = 60000;

  Future<bool> hasPin() async => (await _store.read(_pinKey)) != null;

  Future<void> setPin(String pin) async {
    if (!RegExp(r'^\d{4,8}$').hasMatch(pin)) throw ArgumentError('PIN 4-8 haneli olmalı');
    final salt = CryptoService.randomBytes(16);
    final key = await CryptoService.deriveKey(pin, salt, iterations: _iterations);
    final hash = base64Encode(await key.extractBytes());
    await _store.write(_pinKey, '${base64Encode(salt)}:$hash');
    await _store.delete(_failKey);
  }

  Future<void> removePin() async {
    await _store.delete(_pinKey);
    await _store.delete(_failKey);
  }

  /// Bekleme süresi varsa kalan süre.
  Future<Duration?> lockoutRemaining() async {
    final raw = await _store.read(_failKey);
    if (raw == null) return null;
    final parts = raw.split(':');
    final count = int.tryParse(parts[0]) ?? 0;
    final last = DateTime.fromMillisecondsSinceEpoch(int.tryParse(parts.length > 1 ? parts[1] : '0') ?? 0);
    if (count < 5) return null;
    final wait = Duration(seconds: 30 * (1 << ((count - 5).clamp(0, 6))));
    final until = last.add(wait);
    final now = DateTime.now();
    return until.isAfter(now) ? until.difference(now) : null;
  }

  Future<bool> verifyPin(String pin) async {
    if (await lockoutRemaining() != null) return false;
    final stored = await _store.read(_pinKey);
    if (stored == null) return false;
    final parts = stored.split(':');
    final salt = base64Decode(parts[0]);
    final key = await CryptoService.deriveKey(pin, salt, iterations: _iterations);
    final hash = base64Encode(await key.extractBytes());
    final ok = _constantTimeEquals(hash, parts[1]);
    if (ok) {
      await _store.delete(_failKey);
    } else {
      final raw = await _store.read(_failKey);
      final count = (int.tryParse(raw?.split(':').first ?? '0') ?? 0) + 1;
      await _store.write(_failKey, '$count:${DateTime.now().millisecondsSinceEpoch}');
    }
    return ok;
  }

  static bool _constantTimeEquals(String a, String b) {
    if (a.length != b.length) return false;
    var r = 0;
    for (var i = 0; i < a.length; i++) {
      r |= a.codeUnitAt(i) ^ b.codeUnitAt(i);
    }
    return r == 0;
  }

  Future<bool> biometricsAvailable() async {
    try {
      return await _auth.isDeviceSupported() && (await _auth.getAvailableBiometrics()).isNotEmpty;
    } on PlatformException {
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> authenticateBiometric(String reason) async {
    try {
      return await _auth.authenticate(localizedReason: reason, biometricOnly: true);
    } on PlatformException {
      return false;
    } catch (_) {
      return false;
    }
  }
}
