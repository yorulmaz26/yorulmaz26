import 'package:flutter_tts/flutter_tts.dart';

import '../domain/progress.dart';

/// Türkçe sesle okuma. Kuş dili Türkçe ses kurallarına uyduğu için Türkçe
/// ses doğru telaffuz eder.
final class TtsSpeaker implements Speaker {
  TtsSpeaker([FlutterTts? tts]) : _tts = tts ?? FlutterTts();

  final FlutterTts _tts;
  bool _ready = false;

  Future<void> _init() async {
    if (_ready) return;
    await _tts.setLanguage('tr-TR');
    await _tts.setSpeechRate(0.45);
    _ready = true;
  }

  @override
  Future<void> speak(String text) async {
    try {
      await _init();
      await _tts.stop();
      await _tts.speak(text);
    } catch (_) {
      // Cihazda Türkçe ses yoksa sessizce geç.
    }
  }

  @override
  Future<void> stop() async {
    try {
      await _tts.stop();
    } catch (_) {}
  }
}
