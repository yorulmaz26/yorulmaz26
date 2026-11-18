import 'dart:convert';
import 'dart:math';

import 'package:cryptography/cryptography.dart';

import '../../crypto/domain/secure_kv.dart';

sealed class PinCheck {
  const PinCheck();
}

final class PinOk extends PinCheck {
  const PinOk();
}

final class PinWrong extends PinCheck {
  const PinWrong(this.attemptsLeft);

  /// Bekleme başlamadan önce kalan deneme.
  final int attemptsLeft;
}

final class PinLockedOut extends PinCheck {
  const PinLockedOut(this.until);
  final DateTime until;
}

/// Uygulama / sohbet kilidi PIN'i.
///
/// * PIN asla düz metin saklanmaz: PBKDF2-HMAC-SHA256 (210.000 tur, 16 bayt
///   rastgele tuz) özeti cihazın güvenli deposunda (Keystore/Keychain) durur.
/// * Karşılaştırma sabit sürelidir.
/// * 5 hatalı denemeden sonra bekleme: 30 sn, sonra her hatada iki katı
///   (en fazla 1 saat). Sayaç da güvenli depoda tutulur; uygulamayı kapatıp
///   açmak sıfırlamaz.
final class PinVault {
  PinVault(this._kv, this._owner, {DateTime Function()? clock, int? iterations})
    : _now = clock ?? DateTime.now,
      _iterations = iterations ?? 210000;

  final SecureKv _kv;
  final String _owner;
  final DateTime Function() _now;
  final int _iterations;

  static const freeAttempts = 5;
  static const baseLockout = Duration(seconds: 30);
  static const maxLockout = Duration(hours: 1);
  static final _pinShape = RegExp(r'^\d{4,8}$');

  String get _key => 'pin.$_owner';
  String get _stateKey => 'pin.state.$_owner';

  static String? validate(String pin) =>
      _pinShape.hasMatch(pin) ? null : 'PIN 4-8 rakamdan oluşmalı.';

  Future<bool> hasPin() async => await _kv.read(_key) != null;

  Future<List<int>> _derive(String pin, List<int> salt, int iterations) async {
    final algo = Pbkdf2(
      macAlgorithm: Hmac.sha256(),
      iterations: iterations,
      bits: 256,
    );
    final key = await algo.deriveKeyFromPassword(password: pin, nonce: salt);
    return key.extractBytes();
  }

  Future<void> setPin(String pin) async {
    final err = validate(pin);
    if (err != null) throw ArgumentError(err);
    final rnd = Random.secure();
    final salt = List<int>.generate(16, (_) => rnd.nextInt(256));
    final hash = await _derive(pin, salt, _iterations);
    await _kv.write(
      _key,
      jsonEncode({
        'v': 1,
        'it': _iterations,
        'salt': base64Encode(salt),
        'hash': base64Encode(hash),
      }),
    );
    await _kv.delete(_stateKey);
  }

  Future<void> clear() async {
    await _kv.delete(_key);
    await _kv.delete(_stateKey);
  }

  Future<({int fails, DateTime? until})> _state() async {
    final raw = await _kv.read(_stateKey);
    if (raw == null) return (fails: 0, until: null);
    final j = jsonDecode(raw) as Map<String, Object?>;
    final u = j['until'] as int?;
    return (
      fails: j['fails'] as int? ?? 0,
      until: u == null ? null : DateTime.fromMillisecondsSinceEpoch(u),
    );
  }

  Future<DateTime?> lockedUntil() async {
    final s = await _state();
    final u = s.until;
    return u != null && _now().isBefore(u) ? u : null;
  }

  Future<PinCheck> verify(String pin) async {
    final raw = await _kv.read(_key);
    if (raw == null) return const PinWrong(0);
    final s = await _state();
    if (s.until != null && _now().isBefore(s.until!)) {
      return PinLockedOut(s.until!);
    }
    final j = jsonDecode(raw) as Map<String, Object?>;
    final expected = base64Decode(j['hash']! as String);
    final got = await _derive(
      pin,
      base64Decode(j['salt']! as String),
      j['it']! as int,
    );
    var diff = expected.length ^ got.length;
    for (var i = 0; i < min(expected.length, got.length); i++) {
      diff |= expected[i] ^ got[i];
    }
    if (diff == 0) {
      await _kv.delete(_stateKey);
      return const PinOk();
    }
    final fails = s.fails + 1;
    DateTime? until;
    if (fails >= freeAttempts) {
      final factor = 1 << min(fails - freeAttempts, 16);
      final wait = baseLockout * factor;
      until = _now().add(wait > maxLockout ? maxLockout : wait);
    }
    await _kv.write(
      _stateKey,
      jsonEncode({'fails': fails, 'until': until?.millisecondsSinceEpoch}),
    );
    return until != null ? PinLockedOut(until) : PinWrong(freeAttempts - fails);
  }
}
