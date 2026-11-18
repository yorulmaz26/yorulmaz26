import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../chat/application/chat_providers.dart';
import '../../chat/domain/chat.dart';
import '../domain/report.dart';
import 'safety_providers.dart';

/// Şikâyete eklenebilecek son mesajlar: silinmemiş, [from] kişisinden (grup
/// şikâyetinde benden olmayan) mesajlar; en yenisi sonda.
List<ChatMessage> reportableMessages(
  List<ChatMessage> all, {
  required String me,
  String? from,
}) {
  final out = [
    for (final m in all)
      if (!m.deleted &&
          !m.pending &&
          m.senderId != me &&
          (from == null || m.senderId == from))
        m,
  ];
  return out.length <= Report.maxMessages
      ? out
      : out.sublist(out.length - Report.maxMessages);
}

final safetyActionsProvider = Provider<SafetyActions>(SafetyActions.new);

final class SafetyActions {
  SafetyActions(this._ref);
  final Ref _ref;

  String get _me {
    final uid = _ref.read(myUidProvider);
    if (uid == null) throw StateError('Oturum yok.');
    return uid;
  }

  Future<void> block(String other) =>
      _ref.read(safetyRepositoryProvider).block(uid: _me, other: other);

  Future<void> unblock(String other) =>
      _ref.read(safetyRepositoryProvider).unblock(uid: _me, other: other);

  /// Şikâyet gönderir. [messages] yalnızca kullanıcı onay verdiyse dolu
  /// gelir; burada cihazda çözülüp açık metin olarak eklenir.
  Future<void> report({
    required String chatId,
    required ReportReason reason,
    String? reported,
    String note = '',
    List<ChatMessage> messages = const [],
    bool alsoBlock = false,
  }) async {
    final me = _me;
    final sealer = _ref.read(messageSealerProvider);
    final included = <ReportedMessage>[];
    for (final m in messages) {
      try {
        final p = await sealer.open(chatId, m.senderId, m.encVersion, m.body);
        included.add(
          ReportedMessage(
            id: m.id,
            senderId: m.senderId,
            text: p.text,
            sentAt: m.sentAt,
          ),
        );
      } catch (_) {
        // Bu cihazda açılamayan mesaj eklenmez.
      }
    }
    await _ref
        .read(safetyRepositoryProvider)
        .report(
          Report(
            reporter: me,
            reported: reported,
            chatId: chatId,
            reason: reason,
            note: note,
            messages: included,
          ),
        );
    if (alsoBlock && reported != null) await block(reported);
  }
}
