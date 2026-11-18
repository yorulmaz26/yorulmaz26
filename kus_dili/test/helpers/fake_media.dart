import 'dart:convert';
import 'dart:typed_data';

import 'package:kus_dili/features/media/domain/attachment.dart';
import 'package:kus_dili/features/media/domain/attachment_storage.dart';
import 'package:kus_dili/features/media/domain/media_source.dart';

/// 1x1 şeffaf PNG.
final tinyPng = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
);

/// YALNIZCA TESTLER: Storage'ın bellek içi karşılığı.
class InMemoryAttachmentStorage implements AttachmentStorage {
  final Map<String, Uint8List> blobs = {};
  final Map<String, String> uploaders = {};

  @override
  Future<void> put(String path, Uint8List c, {required String uploader}) async {
    if (blobs.containsKey(path)) throw StateError('üzerine yazılamaz');
    blobs[path] = Uint8List.fromList(c);
    uploaders[path] = uploader;
  }

  @override
  Future<Uint8List> get(String path, {required int maxBytes}) async {
    final b = blobs[path];
    if (b == null) throw StateError('yok');
    if (b.length > maxBytes) throw StateError('çok büyük');
    return b;
  }

  @override
  Future<void> delete(String path) async => blobs.remove(path);
}

/// YALNIZCA TESTLER: seçici sahtesi.
class FakeMediaSource implements MediaSource {
  PickedMedia? next;

  @override
  Future<PickedMedia?> pickImage({required bool camera}) async =>
      next ??
      PickedMedia(
        kind: AttachmentKind.image,
        bytes: Uint8List.fromList(tinyPng),
        mime: 'image/png',
        name: 'kus.png',
      );

  @override
  Future<PickedMedia?> pickVideo({required bool camera}) async => next;

  @override
  Future<PickedMedia?> pickFile() async => next;
}

/// YALNIZCA TESTLER: ses kaydı sahtesi.
class FakeVoiceRecorder implements VoiceRecorder {
  bool permission = true;
  bool recording = false;

  @override
  Future<bool> hasPermission() async => permission;

  @override
  Future<void> start() async => recording = true;

  @override
  Future<PickedMedia?> stop() async {
    recording = false;
    return PickedMedia(
      kind: AttachmentKind.audio,
      bytes: Uint8List.fromList(utf8.encode('SES-VERISI-' * 50)),
      mime: 'audio/mp4',
      name: 'sesli-mesaj.m4a',
      durationMs: 3200,
    );
  }

  @override
  Future<void> cancel() async => recording = false;
}
