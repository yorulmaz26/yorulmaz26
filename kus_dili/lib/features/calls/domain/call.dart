/// Arama durumu (calls/{id}.state). Sunucu yalnızca bunu ve kimin kimi
/// aradığını görür; görüntü/ses ve kurulum bilgileri şifrelidir.
enum CallStatus {
  ringing,
  accepted,
  declined,
  busy,
  missed,
  ended;

  static CallStatus parse(Object? s) =>
      values.firstWhere((v) => v.name == s, orElse: () => CallStatus.ended);

  bool get isTerminal =>
      this == declined || this == busy || this == missed || this == ended;
}

final class CallInfo {
  const CallInfo({
    required this.id,
    required this.chatId,
    required this.caller,
    required this.callee,
    required this.video,
    required this.status,
    this.createdAt,
  });

  final String id;
  final String chatId;
  final String caller;
  final String callee;
  final bool video;
  final CallStatus status;
  final DateTime? createdAt;

  String peerOf(String me) => me == caller ? callee : caller;
}

/// Şifreli kurulum mesajı (calls/{id}/signals/{id}).
final class CallSignal {
  const CallSignal({
    required this.id,
    required this.from,
    required this.to,
    required this.seq,
    required this.body,
  });

  final String id;
  final String from;
  final String to;
  final int seq;
  final String body;
}

abstract interface class CallRepository {
  String newCallId();

  Future<void> create({
    required String callId,
    required String chatId,
    required String caller,
    required String callee,
    required bool video,
  });

  Stream<CallInfo?> watch(String callId);

  /// Bana gelen, hâlâ çalan en yeni arama.
  Stream<CallInfo?> watchIncoming(String uid);

  Future<void> setStatus(
    String callId,
    CallStatus status, {
    required String by,
  });

  Future<void> addSignal({
    required String callId,
    required String from,
    required String to,
    required int seq,
    required String body,
  });

  Stream<List<CallSignal>> watchSignals(String callId, {required String to});
}
