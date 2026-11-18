import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import '../domain/attachment.dart';
import '../domain/media_source.dart';

/// Sesli mesaj: AAC (m4a), tek kanal, 64 kbps. Kayıt geçici dosyaya yazılır,
/// okunduktan hemen sonra silinir; yalnızca şifreli hâli yüklenir.
final class RecordVoiceRecorder implements VoiceRecorder {
  RecordVoiceRecorder([AudioRecorder? recorder])
    : _rec = recorder ?? AudioRecorder();

  static const minDuration = Duration(milliseconds: 700);
  static const maxDuration = Duration(minutes: 5);

  final AudioRecorder _rec;
  final _watch = Stopwatch();
  String? _path;

  @override
  Future<bool> hasPermission() => _rec.hasPermission();

  @override
  Future<void> start() async {
    final dir = await getTemporaryDirectory();
    _path =
        '${dir.path}/kus_voice_${DateTime.now().microsecondsSinceEpoch}.m4a';
    await _rec.start(
      const RecordConfig(bitRate: 64000, sampleRate: 44100, numChannels: 1),
      path: _path!,
    );
    _watch
      ..reset()
      ..start();
  }

  @override
  Future<PickedMedia?> stop() async {
    _watch.stop();
    final path = await _rec.stop() ?? _path;
    _path = null;
    if (path == null) return null;
    final file = File(path);
    try {
      if (_watch.elapsed < minDuration || !await file.exists()) return null;
      return PickedMedia(
        kind: AttachmentKind.audio,
        bytes: await file.readAsBytes(),
        mime: 'audio/mp4',
        name: 'sesli-mesaj.m4a',
        durationMs: _watch.elapsedMilliseconds.clamp(
          0,
          maxDuration.inMilliseconds,
        ),
      );
    } finally {
      if (await file.exists()) await file.delete();
    }
  }

  @override
  Future<void> cancel() async {
    _watch.stop();
    await _rec.cancel();
    final p = _path;
    _path = null;
    if (p != null && await File(p).exists()) await File(p).delete();
  }
}
