import 'attachment.dart';

/// Galeri / kamera / dosya seçici (platform kodu data/ altında).
abstract interface class MediaSource {
  Future<PickedMedia?> pickImage({required bool camera});
  Future<PickedMedia?> pickVideo({required bool camera});
  Future<PickedMedia?> pickFile();
}

/// Sesli mesaj kaydı.
abstract interface class VoiceRecorder {
  Future<bool> hasPermission();
  Future<void> start();

  /// Kaydı bitirir; çok kısa ya da başarısızsa `null`.
  Future<PickedMedia?> stop();
  Future<void> cancel();
}
