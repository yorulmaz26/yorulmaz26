import 'package:file_picker/file_picker.dart';
import 'package:image_picker/image_picker.dart';

import '../domain/attachment.dart';
import '../domain/media_source.dart';

final class DeviceMediaSource implements MediaSource {
  DeviceMediaSource([ImagePicker? picker]) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;

  @override
  Future<PickedMedia?> pickImage({required bool camera}) async {
    // Yeniden kodlama (boyut/kalite) fotoğrafı küçültür; konum gibi EXIF
    // bilgilerini de büyük ölçüde atar (platforma göre değişir).
    final x = await _picker.pickImage(
      source: camera ? ImageSource.camera : ImageSource.gallery,
      maxWidth: 2048,
      maxHeight: 2048,
      imageQuality: 82,
      requestFullMetadata: false,
    );
    if (x == null) return null;
    return PickedMedia(
      kind: AttachmentKind.image,
      bytes: await x.readAsBytes(),
      mime: x.mimeType ?? 'image/jpeg',
      name: x.name,
    );
  }

  @override
  Future<PickedMedia?> pickVideo({required bool camera}) async {
    final x = await _picker.pickVideo(
      source: camera ? ImageSource.camera : ImageSource.gallery,
      maxDuration: const Duration(minutes: 3),
    );
    if (x == null) return null;
    final len = await x.length();
    if (len > AttachmentKind.video.maxBytes) {
      throw const AttachmentTooLargeException(AttachmentKind.video);
    }
    return PickedMedia(
      kind: AttachmentKind.video,
      bytes: await x.readAsBytes(),
      mime: x.mimeType ?? 'video/mp4',
      name: x.name,
    );
  }

  @override
  Future<PickedMedia?> pickFile() async {
    final f = await FilePicker.pickFile();
    if (f == null) return null;
    final len = await f.length() ?? 0;
    if (len > AttachmentKind.file.maxBytes) {
      throw const AttachmentTooLargeException(AttachmentKind.file);
    }
    return PickedMedia(
      kind: AttachmentKind.file,
      bytes: await f.readAsBytes(),
      mime: f.xFile.mimeType ?? 'application/octet-stream',
      name: f.name,
    );
  }
}
