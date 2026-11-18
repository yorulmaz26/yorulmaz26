import 'dart:convert';
import 'dart:typed_data';

enum AttachmentKind {
  image('img', 15 * 1024 * 1024),
  video('vid', 50 * 1024 * 1024),
  audio('aud', 15 * 1024 * 1024),
  file('file', 50 * 1024 * 1024);

  const AttachmentKind(this.code, this.maxBytes);
  final String code;

  /// Şifrelenmeden önceki en büyük boyut.
  final int maxBytes;

  static AttachmentKind? fromCode(Object? c) {
    for (final k in values) {
      if (k.code == c) return k;
    }
    return null;
  }
}

/// Ek dosyanın şifreli mesaj paketinde taşınan bilgileri.
///
/// Dosya Storage'da AES-256-GCM ile şifreli durur; çözme [key] yalnızca bu
/// pakette (yani Signal ile şifreli) bulunur. [sha256] şifreli dosyanın
/// özetidir: indirilen dosya değiştirilmişse çözülmeden reddedilir.
final class Attachment {
  const Attachment({
    required this.kind,
    required this.path,
    required this.key,
    required this.sha256,
    required this.size,
    required this.mime,
    this.name,
    this.durationMs,
    this.width,
    this.height,
  });

  factory Attachment.fromJson(Map<String, Object?> j) {
    final kind = AttachmentKind.fromCode(j['k']);
    final path = j['p'];
    final key = j['key'];
    final sha = j['sha'];
    final size = j['s'];
    if (kind == null ||
        path is! String ||
        key is! String ||
        sha is! String ||
        size is! int) {
      throw const FormatException('Ek bilgisi bozuk.');
    }
    return Attachment(
      kind: kind,
      path: path,
      key: base64Decode(key),
      sha256: base64Decode(sha),
      size: size,
      mime: j['m'] as String? ?? 'application/octet-stream',
      name: j['n'] as String?,
      durationMs: j['d'] as int?,
      width: j['w'] as int?,
      height: j['h'] as int?,
    );
  }

  final AttachmentKind kind;

  /// Storage yolu: attachments/{chatId}/{id}
  final String path;
  final Uint8List key;
  final Uint8List sha256;

  /// Açık (şifresiz) boyut, bayt.
  final int size;
  final String mime;
  final String? name;
  final int? durationMs;
  final int? width;
  final int? height;

  Map<String, Object?> toJson() => {
    'k': kind.code,
    'p': path,
    'key': base64Encode(key),
    'sha': base64Encode(sha256),
    's': size,
    'm': mime,
    if (name != null) 'n': name,
    if (durationMs != null) 'd': durationMs,
    if (width != null) 'w': width,
    if (height != null) 'h': height,
  };

  @override
  bool operator ==(Object other) =>
      other is Attachment &&
      other.path == path &&
      base64Encode(other.sha256) == base64Encode(sha256);

  @override
  int get hashCode => Object.hash(path, base64Encode(sha256));

  /// "1,4 MB" gibi.
  static String formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1).replaceAll('.', ',')} MB';
  }

  /// "0:07", "1:23".
  static String formatDuration(int ms) {
    final s = (ms / 1000).round();
    return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
  }
}

/// Cihazdan seçilen / kaydedilen ham medya (henüz şifresiz, yalnızca bellekte).
final class PickedMedia {
  const PickedMedia({
    required this.kind,
    required this.bytes,
    required this.mime,
    this.name,
    this.durationMs,
  });

  final AttachmentKind kind;
  final Uint8List bytes;
  final String mime;
  final String? name;
  final int? durationMs;
}

final class AttachmentTooLargeException implements Exception {
  const AttachmentTooLargeException(this.kind);
  final AttachmentKind kind;

  String get message =>
      'Dosya çok büyük (en fazla ${Attachment.formatSize(kind.maxBytes)}).';
}

final class AttachmentIntegrityException implements Exception {
  const AttachmentIntegrityException();
}
