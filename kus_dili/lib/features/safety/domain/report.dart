/// Şikâyet nedeni. Kodlar Firestore kurallarıyla AYNI olmalı.
enum ReportReason {
  spam('spam', 'İstenmeyen mesaj (spam)'),
  harassment('harassment', 'Taciz veya zorbalık'),
  inappropriate('inappropriate', 'Uygunsuz içerik'),
  impersonation('impersonation', 'Başkasının kimliğine bürünme'),
  other('other', 'Diğer');

  const ReportReason(this.code, this.label);
  final String code;
  final String label;
}

final class ReportFormatException implements Exception {
  const ReportFormatException(this.message);
  final String message;
  @override
  String toString() => 'ReportFormatException: $message';
}

/// Şikâyete kullanıcının ONAYIYLA eklenen, cihazda çözülmüş bir mesaj.
///
/// Mesajlar uçtan uca şifreli olduğu için sunucu içerikleri okuyamaz;
/// şikâyet eden kişi isterse son mesajları açık hâliyle ekler (WhatsApp'taki
/// gibi). Eklenmezse yalnızca kim, hangi sohbet ve neden bilgisi gider.
final class ReportedMessage {
  const ReportedMessage({
    required this.id,
    required this.senderId,
    required this.text,
    this.sentAt,
  });

  final String id;
  final String senderId;
  final String text;
  final DateTime? sentAt;

  Map<String, Object?> toJson() => {
    'id': id,
    'senderId': senderId,
    'text': text.length > Report.maxTextLength
        ? text.substring(0, Report.maxTextLength)
        : text,
    if (sentAt != null) 'sentAt': sentAt!.millisecondsSinceEpoch,
  };
}

final class Report {
  Report({
    required this.reporter,
    required this.chatId,
    required this.reason,
    this.reported,
    String note = '',
    this.messages = const [],
  }) : note = note.trim() {
    if (reported == reporter) {
      throw const ReportFormatException('Kendini şikâyet edemezsin.');
    }
    if (this.note.length > maxNoteLength) {
      throw const ReportFormatException(
        'Açıklama en fazla $maxNoteLength karakter olabilir.',
      );
    }
    if (messages.length > maxMessages) {
      throw const ReportFormatException(
        'En fazla $maxMessages mesaj eklenebilir.',
      );
    }
  }

  static const int maxMessages = 5;
  static const int maxNoteLength = 500;
  static const int maxTextLength = 4000;

  final String reporter;

  /// Şikâyet edilen kişi; grubun kendisi şikâyet ediliyorsa `null`.
  final String? reported;
  final String chatId;
  final ReportReason reason;
  final String note;
  final List<ReportedMessage> messages;

  Map<String, Object?> toJson() => {
    'reporter': reporter,
    if (reported != null) 'reported': reported,
    'chatId': chatId,
    'reason': reason.code,
    if (note.isNotEmpty) 'note': note,
    'messages': [for (final m in messages) m.toJson()],
  };
}
