import 'dart:io';

import 'package:flutter/services.dart';

/// Ekran görüntüsü / kaydı engelleme. Android'de FLAG_SECURE; iOS bunu
/// sistem düzeyinde desteklemez (uygulama değiştiricide örtü kullanılır).
abstract interface class SecureScreen {
  Future<void> setSecure(bool secure);
}

final class PlatformSecureScreen implements SecureScreen {
  static const _channel = MethodChannel('kusdili/secure_screen');

  @override
  Future<void> setSecure(bool secure) async {
    if (!Platform.isAndroid) return;
    try {
      await _channel.invokeMethod<void>('setSecure', secure);
    } on PlatformException {
      // Eski sürüm / test ortamı: yok say.
    } on MissingPluginException {
      // Test ortamı.
    }
  }
}
