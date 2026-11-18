import 'dart:convert';

import 'package:kus_dili_engine/kus_dili_engine.dart';

/// Gönderenin seçtiği gösterim biçimi. Alıcı cihaz metni bu bilgiye göre
/// kuş diline çevirip gösterir. Sunucuda dönüşüm YAPILMAZ.
enum DisplayMode {
  /// Normal mesaj.
  plain('p'),

  /// Kuş dilinde gösterilecek mesaj.
  kusDili('k');

  const DisplayMode(this.code);
  final String code;

  static DisplayMode fromCode(Object? c) =>
      values.firstWhere((m) => m.code == c, orElse: () => DisplayMode.plain);
}

final class PayloadFormatException implements Exception {
  const PayloadFormatException(this.message);
  final String message;
  @override
  String toString() => 'PayloadFormatException: $message';
}

/// Mesajın şifrelenecek (Aşama 7) içeriği. Sunucuya yazılan tek şey
/// bunun mühürlenmiş (sealed) hâlidir; içindeki hiçbir alan Firestore'da
/// ayrı bir alan olarak tutulmaz.
final class MessagePayload {
  MessagePayload({
    required this.text,
    this.mode = DisplayMode.plain,
    this.variant = KusDiliVariant.g,
    this.replyToId,
    this.replyQuote,
    this.expiresInSeconds,
    this.viewOnce = false,
  }) {
    if (text.trim().isEmpty) {
      throw const PayloadFormatException('Boş mesaj gönderilemez.');
    }
    if (text.length > maxTextLength) {
      throw const PayloadFormatException(
        'Mesaj en fazla $maxTextLength karakter olabilir.',
      );
    }
  }

  static const int version = 1;
  static const int maxTextLength = 4000;
  static const int maxQuoteLength = 120;

  /// Orijinal Türkçe metin (her zaman orijinal saklanır).
  final String text;

  final DisplayMode mode;
  final KusDiliVariant variant;
  final String? replyToId;

  /// Yanıtlanan mesajın kısa alıntısı (o da şifreli paketin içinde).
  final String? replyQuote;

  /// Süreli mesaj: gönderimden kaç saniye sonra silinecek.
  final int? expiresInSeconds;

  /// Tek görüntülük: alıcı bir kez açtıktan sonra silinir.
  final bool viewOnce;

  Map<String, Object?> toJson() => {
    'v': version,
    't': 'text',
    'text': text,
    'm': mode.code,
    if (mode == DisplayMode.kusDili) 'var': variant.toJson(),
    if (replyToId != null) 'r': replyToId,
    if (replyQuote != null) 'rq': replyQuote,
    if (expiresInSeconds != null) 'exp': expiresInSeconds,
    if (viewOnce) 'vo': true,
  };

  String encode() => jsonEncode(toJson());

  static MessagePayload decode(String s) {
    final Object? json;
    try {
      json = jsonDecode(s);
    } on FormatException {
      throw const PayloadFormatException('Mesaj okunamadı.');
    }
    if (json is! Map<String, Object?>) {
      throw const PayloadFormatException('Mesaj biçimi geçersiz.');
    }
    final v = json['v'];
    if (v is! int || v > version) {
      throw const PayloadFormatException(
        'Bu mesaj daha yeni bir uygulama sürümüyle gönderilmiş.',
      );
    }
    final text = json['text'];
    if (text is! String) {
      throw const PayloadFormatException('Mesaj metni yok.');
    }
    final mode = DisplayMode.fromCode(json['m']);
    var variant = KusDiliVariant.g;
    final rawVariant = json['var'];
    if (mode == DisplayMode.kusDili && rawVariant is Map<String, Object?>) {
      try {
        variant = KusDiliVariant.fromJson(rawVariant);
      } on KusDiliRuleException {
        variant = KusDiliVariant.g; // bozuk özel kural: varsayılana düş
      }
    }
    final r = json['r'];
    final rq = json['rq'];
    final exp = json['exp'];
    if (json['t'] != null && json['t'] != 'text') {
      // Eski sürümlerden kalan ekli mesajlar artık desteklenmiyor.
      throw const PayloadFormatException('Bu mesaj türü desteklenmiyor.');
    }
    return MessagePayload(
      text: text,
      mode: mode,
      variant: variant,
      replyToId: r is String ? r : null,
      replyQuote: rq is String ? rq : null,
      expiresInSeconds: exp is int ? exp : null,
      viewOnce: json['vo'] == true,
    );
  }

  /// Listede / alıntıda gösterilecek kısa özet.
  String get summary => text;

  /// Yanıt alıntısı için kısaltılmış metin.
  static String quoteOf(String s) {
    final oneLine = s.replaceAll(RegExp(r'\s+'), ' ').trim();
    return oneLine.length <= maxQuoteLength
        ? oneLine
        : '${oneLine.substring(0, maxQuoteLength - 1)}…';
  }
}
