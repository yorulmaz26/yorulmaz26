import 'dart:io';
import 'dart:typed_data';

import 'package:gal/gal.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

/// Fotoğraf ve ses dosyalarını uygulamanın özel klasöründe saklar.
///
/// Veritabanında yalnızca `media/<uuid>.jpg` gibi GÖRELİ yollar tutulur;
/// iOS'ta uygulama kapsayıcısının mutlak yolu güncellemelerde değişebilir.
class MediaService {
  MediaService({Directory? baseDir}) : _base = baseDir;

  static const folder = 'media';
  Directory? _base;
  final _picker = ImagePicker();
  final _uuid = const Uuid();

  Future<Directory> baseDir() async {
    _base ??= await getApplicationDocumentsDirectory();
    return _base!;
  }

  Future<Directory> mediaDir() async {
    final d = Directory(p.join((await baseDir()).path, folder));
    if (!await d.exists()) await d.create(recursive: true);
    return d;
  }

  /// Senkron çözümleme için başlangıçta [init] çağrılır.
  String? _basePath;
  Future<void> init() async => _basePath = (await baseDir()).path;

  File? resolve(String? relative) {
    if (relative == null || relative.isEmpty || _basePath == null) return null;
    return File(p.join(_basePath!, relative));
  }

  Future<File?> resolveAsync(String? relative) async {
    if (relative == null || relative.isEmpty) return null;
    return File(p.join((await baseDir()).path, relative));
  }

  /// Galeriden ya da kameradan fotoğraf seçer; büyük görselleri 2048 px'e
  /// küçültüp %85 kaliteyle sıkıştırır.
  Future<String?> pickImage({bool camera = false}) async {
    final x = await _picker.pickImage(
      source: camera ? ImageSource.camera : ImageSource.gallery,
      maxWidth: 2048,
      maxHeight: 2048,
      imageQuality: 85,
    );
    if (x == null) return null;
    return importFile(File(x.path), ext: '.jpg');
  }

  Future<List<String>> pickMultipleImages() async {
    final xs = await _picker.pickMultiImage(maxWidth: 2048, maxHeight: 2048, imageQuality: 85, limit: 20);
    final out = <String>[];
    for (final x in xs) {
      out.add(await importFile(File(x.path), ext: '.jpg'));
    }
    return out;
  }

  Future<String> importFile(File source, {String? ext}) async {
    final dir = await mediaDir();
    final name = '${_uuid.v4()}${ext ?? p.extension(source.path)}';
    await source.copy(p.join(dir.path, name));
    return p.join(folder, name);
  }

  Future<String> saveBytes(Uint8List bytes, String ext) async {
    final dir = await mediaDir();
    final name = '${_uuid.v4()}$ext';
    await File(p.join(dir.path, name)).writeAsBytes(bytes, flush: true);
    return p.join(folder, name);
  }

  /// Ses kaydı için yeni bir dosya yolu: (göreli, mutlak)
  Future<(String, String)> newAudioPath() async {
    final dir = await mediaDir();
    final name = '${_uuid.v4()}.m4a';
    return (p.join(folder, name), p.join(dir.path, name));
  }

  Future<void> delete(String? relative) async {
    final f = await resolveAsync(relative);
    if (f != null && await f.exists()) await f.delete();
  }

  /// PNG'yi cihaz galerisine kaydeder (Hayal Panom, rozet kartları).
  Future<void> saveToGallery(Uint8List png, {String name = 'hayallerim_cepte'}) async {
    if (!await Gal.hasPermission(toAlbum: true)) {
      final ok = await Gal.requestPermission(toAlbum: true);
      if (!ok) throw const MediaPermissionException();
    }
    await Gal.putImageBytes(png, album: 'Hayallerim Cepte', name: '${name}_${DateTime.now().millisecondsSinceEpoch}');
  }

  Future<File> writeTemp(Uint8List bytes, String fileName) async {
    final dir = await getTemporaryDirectory();
    final f = File(p.join(dir.path, fileName));
    await f.writeAsBytes(bytes, flush: true);
    return f;
  }
}

class MediaPermissionException implements Exception {
  const MediaPermissionException();
  @override
  String toString() => 'Galeriye kaydetme izni verilmedi.';
}
