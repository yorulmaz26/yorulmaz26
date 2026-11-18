import 'dart:typed_data';

import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/device_media_source.dart';
import '../data/firebase_attachment_storage.dart';
import '../data/record_voice_recorder.dart';
import '../domain/attachment.dart';
import '../domain/attachment_service.dart';
import '../domain/attachment_storage.dart';
import '../domain/media_source.dart';

final attachmentStorageProvider = Provider<AttachmentStorage>(
  (ref) => FirebaseAttachmentStorage(FirebaseStorage.instance),
);

final attachmentServiceProvider = Provider<AttachmentService>(
  (ref) => AttachmentService(ref.watch(attachmentStorageProvider)),
);

final mediaSourceProvider = Provider<MediaSource>((ref) => DeviceMediaSource());

final voiceRecorderProvider = Provider<VoiceRecorder>(
  (ref) => RecordVoiceRecorder(),
);

/// İndirilip çözülmüş ek. Ekrandan çıkınca bellekten atılır.
final attachmentBytesProvider = FutureProvider.autoDispose
    .family<Uint8List, Attachment>(
      (ref, a) => ref.watch(attachmentServiceProvider).download(a),
    );
