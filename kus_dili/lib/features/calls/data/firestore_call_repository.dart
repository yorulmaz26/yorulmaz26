import 'package:cloud_firestore/cloud_firestore.dart';

import '../domain/call.dart';

/// calls/{id}            { chatId, caller, callee, video, state, createdAt,
///                         answeredAt?, endedAt?, endedBy? }
/// calls/{id}/signals/{} { from, to, seq, body (Signal ile şifreli), createdAt }
final class FirestoreCallRepository implements CallRepository {
  FirestoreCallRepository(this._db);

  final FirebaseFirestore _db;

  CollectionReference<Map<String, dynamic>> get _calls =>
      _db.collection('calls');

  static CallInfo _info(DocumentSnapshot<Map<String, dynamic>> s) {
    final d = s.data()!;
    final c = d['createdAt'];
    return CallInfo(
      id: s.id,
      chatId: d['chatId'] as String,
      caller: d['caller'] as String,
      callee: d['callee'] as String,
      video: d['video'] == true,
      status: CallStatus.parse(d['state']),
      createdAt: c is Timestamp ? c.toDate() : null,
    );
  }

  @override
  String newCallId() => _calls.doc().id;

  @override
  Future<void> create({
    required String callId,
    required String chatId,
    required String caller,
    required String callee,
    required bool video,
  }) => _calls.doc(callId).set({
    'chatId': chatId,
    'caller': caller,
    'callee': callee,
    'video': video,
    'state': CallStatus.ringing.name,
    'createdAt': FieldValue.serverTimestamp(),
  });

  @override
  Stream<CallInfo?> watch(String callId) =>
      _calls.doc(callId).snapshots().map((s) => s.exists ? _info(s) : null);

  @override
  Stream<CallInfo?> watchIncoming(String uid) => _calls
      .where('callee', isEqualTo: uid)
      .where('state', isEqualTo: CallStatus.ringing.name)
      .snapshots()
      .map((q) {
        final now = DateTime.now();
        final calls =
            q.docs.map(_info).where((c) {
              final t = c.createdAt;
              // Çok eski "çalan" kayıtlar (ör. arayanın uygulaması çöktü) yok sayılır.
              return t == null ||
                  now.difference(t) < const Duration(seconds: 60);
            }).toList()..sort(
              (a, b) => (b.createdAt ?? now).compareTo(a.createdAt ?? now),
            );
        return calls.isEmpty ? null : calls.first;
      });

  @override
  Future<void> setStatus(
    String callId,
    CallStatus status, {
    required String by,
  }) {
    final data = <String, Object?>{'state': status.name};
    if (status == CallStatus.accepted) {
      data['answeredAt'] = FieldValue.serverTimestamp();
    } else if (status.isTerminal) {
      data['endedAt'] = FieldValue.serverTimestamp();
      data['endedBy'] = by;
    }
    return _calls.doc(callId).update(data);
  }

  @override
  Future<void> addSignal({
    required String callId,
    required String from,
    required String to,
    required int seq,
    required String body,
  }) => _calls.doc(callId).collection('signals').add({
    'from': from,
    'to': to,
    'seq': seq,
    'body': body,
    'createdAt': FieldValue.serverTimestamp(),
  });

  @override
  Stream<List<CallSignal>> watchSignals(String callId, {required String to}) =>
      _calls
          .doc(callId)
          .collection('signals')
          .where('to', isEqualTo: to)
          .snapshots()
          .map(
            (q) => [
              for (final d in q.docs)
                CallSignal(
                  id: d.id,
                  from: d['from'] as String,
                  to: d['to'] as String,
                  seq: (d['seq'] as num).toInt(),
                  body: d['body'] as String,
                ),
            ]..sort((a, b) => a.seq.compareTo(b.seq)),
          );
}
