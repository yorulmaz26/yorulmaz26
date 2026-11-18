import 'dart:async';
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../chat/application/chat_providers.dart';
import '../data/firestore_call_repository.dart';
import '../data/turn_credentials.dart';
import '../data/webrtc_call_engine.dart';
import '../domain/call.dart';
import '../domain/call_engine.dart';

final callRepositoryProvider = Provider<CallRepository>(
  (ref) => FirestoreCallRepository(ref.watch(firestoreProvider)),
);

final callEngineFactoryProvider = Provider<CallEngine Function()>(
  (ref) => WebRtcCallEngine.new,
);

final iceServerSourceProvider = Provider<IceServerSource>(
  (ref) => CloudFunctionIceServers(),
);

/// Cevapsız sayılmadan önce çalma süresi.
final ringTimeoutProvider = Provider<Duration>(
  (ref) => const Duration(seconds: 45),
);

/// Bana gelen, çalan arama.
final incomingCallProvider = StreamProvider<CallInfo?>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(null);
  return ref.watch(callRepositoryProvider).watchIncoming(uid);
});

enum CallPhase { idle, outgoing, connecting, connected, ended }

final class CallSession {
  const CallSession({
    this.phase = CallPhase.idle,
    this.callId,
    this.peerUid,
    this.video = false,
    this.muted = false,
    this.cameraOn = true,
    this.speaker = false,
    this.connectedAt,
    this.endReason,
    this.engine,
  });

  final CallPhase phase;
  final String? callId;
  final String? peerUid;
  final bool video;
  final bool muted;
  final bool cameraOn;
  final bool speaker;
  final DateTime? connectedAt;
  final String? endReason;
  final CallEngine? engine;

  bool get active =>
      phase == CallPhase.outgoing ||
      phase == CallPhase.connecting ||
      phase == CallPhase.connected;

  CallSession copyWith({
    CallPhase? phase,
    bool? muted,
    bool? cameraOn,
    bool? speaker,
    DateTime? connectedAt,
    String? endReason,
  }) => CallSession(
    phase: phase ?? this.phase,
    callId: callId,
    peerUid: peerUid,
    video: video,
    muted: muted ?? this.muted,
    cameraOn: cameraOn ?? this.cameraOn,
    speaker: speaker ?? this.speaker,
    connectedAt: connectedAt ?? this.connectedAt,
    endReason: endReason ?? this.endReason,
    engine: engine,
  );
}

final class CallUnavailableException implements Exception {
  const CallUnavailableException(this.message);
  final String message;
}

/// Tek seferde tek arama. Kurulum bilgileri (SDP/ICE) Signal ile birebir
/// şifrelenip `calls/{id}/signals` altından iletilir.
class CallController extends Notifier<CallSession> {
  final List<StreamSubscription<Object?>> _subs = [];
  final Set<String> _processed = {};
  Future<void> _queue = Future.value();
  Timer? _ringTimer;
  int _seq = 0;
  bool _ending = false;

  @override
  CallSession build() {
    ref.onDispose(_teardown);
    return const CallSession();
  }

  String get _me => ref.read(myUidProvider) ?? (throw StateError('Oturum yok'));

  Future<void> startCall({
    required String chatId,
    required String peer,
    required bool video,
  }) async {
    if (state.active) return;
    final sealer = ref.read(signalSealerProvider);
    if (sealer == null) {
      throw const CallUnavailableException('Şifreleme hazır değil.');
    }
    final repo = ref.read(callRepositoryProvider);
    final engine = ref.read(callEngineFactoryProvider)();
    final callId = repo.newCallId();
    _reset();
    state = CallSession(
      phase: CallPhase.outgoing,
      callId: callId,
      peerUid: peer,
      video: video,
      speaker: video,
      engine: engine,
    );
    try {
      await engine.start(
        video: video,
        iceServers: await ref.read(iceServerSourceProvider).iceServers(),
      );
      await repo.create(
        callId: callId,
        chatId: chatId,
        caller: _me,
        callee: peer,
        video: video,
      );
      _wire(callId, peer, engine);
      await _send(callId, peer, {
        't': 'offer',
        'd': await engine.createOffer(),
      });
      _ringTimer = Timer(ref.read(ringTimeoutProvider), () {
        if (state.phase == CallPhase.outgoing && state.callId == callId) {
          unawaited(_finish(CallStatus.missed, 'Cevap yok'));
        }
      });
    } catch (e) {
      await _finish(CallStatus.ended, 'Arama başlatılamadı');
    }
  }

  Future<void> accept(CallInfo call) async {
    if (state.active) return;
    final engine = ref.read(callEngineFactoryProvider)();
    _reset();
    state = CallSession(
      phase: CallPhase.connecting,
      callId: call.id,
      peerUid: call.caller,
      video: call.video,
      speaker: call.video,
      engine: engine,
    );
    try {
      await engine.start(
        video: call.video,
        iceServers: await ref.read(iceServerSourceProvider).iceServers(),
      );
      await ref
          .read(callRepositoryProvider)
          .setStatus(call.id, CallStatus.accepted, by: _me);
      _wire(call.id, call.caller, engine);
    } catch (_) {
      await _finish(CallStatus.ended, 'Arama bağlanamadı');
    }
  }

  Future<void> decline(CallInfo call, {bool busy = false}) => ref
      .read(callRepositoryProvider)
      .setStatus(
        call.id,
        busy ? CallStatus.busy : CallStatus.declined,
        by: _me,
      );

  Future<void> hangUp() => _finish(
    state.phase == CallPhase.outgoing ? CallStatus.missed : CallStatus.ended,
    'Arama bitti',
  );

  Future<void> toggleMute() async {
    final m = !state.muted;
    await state.engine?.setMuted(m);
    state = state.copyWith(muted: m);
  }

  Future<void> toggleCamera() async {
    final on = !state.cameraOn;
    await state.engine?.setCameraEnabled(on);
    state = state.copyWith(cameraOn: on);
  }

  Future<void> toggleSpeaker() async {
    final on = !state.speaker;
    await state.engine?.setSpeaker(on);
    state = state.copyWith(speaker: on);
  }

  Future<void> switchCamera() async => state.engine?.switchCamera();

  /// Bitmiş aramayı ekrandan kaldır.
  void dismiss() {
    if (!state.active) state = const CallSession();
  }

  // ---------------------------------------------------------------------------

  void _reset() {
    _teardown();
    _processed.clear();
    _seq = 0;
    _ending = false;
  }

  void _wire(String callId, String peer, CallEngine engine) {
    final repo = ref.read(callRepositoryProvider);
    _subs
      ..add(
        repo.watchSignals(callId, to: _me).listen((signals) {
          for (final s in signals) {
            if (_processed.add(s.id)) {
              _queue = _queue.then((_) => _handle(callId, peer, engine, s));
            }
          }
        }),
      )
      ..add(
        engine.localIceCandidates.listen(
          (c) => unawaited(_send(callId, peer, {'t': 'ice', 'd': c})),
        ),
      )
      ..add(
        engine.connection.listen((c) {
          if (c == EngineConnection.connected &&
              state.phase != CallPhase.connected) {
            state = state.copyWith(
              phase: CallPhase.connected,
              connectedAt: DateTime.now(),
            );
          } else if (c == EngineConnection.failed) {
            unawaited(_finish(CallStatus.ended, 'Bağlantı koptu'));
          }
        }),
      )
      ..add(
        repo.watch(callId).listen((info) {
          if (info == null) return;
          if (info.status == CallStatus.accepted &&
              state.phase == CallPhase.outgoing) {
            _ringTimer?.cancel();
            state = state.copyWith(phase: CallPhase.connecting);
          }
          if (info.status.isTerminal && !_ending) {
            unawaited(
              _finish(null, switch (info.status) {
                CallStatus.declined => 'Arama reddedildi',
                CallStatus.busy => 'Meşgul',
                CallStatus.missed => 'Cevapsız arama',
                _ => 'Arama bitti',
              }),
            );
          }
        }),
      );
  }

  Future<void> _handle(
    String callId,
    String peer,
    CallEngine engine,
    CallSignal s,
  ) async {
    if (s.from != peer || state.callId != callId || !state.active) return;
    final Map<String, dynamic> m;
    try {
      final clear = await ref
          .read(signalSealerProvider)!
          .openDirect(s.from, s.body);
      m = jsonDecode(clear) as Map<String, dynamic>;
    } catch (_) {
      return; // çözülemeyen sinyal yok sayılır
    }
    final d = m['d'] as String?;
    if (d == null) return;
    switch (m['t']) {
      case 'offer':
        final answer = await engine.acceptOffer(d);
        await _send(callId, peer, {'t': 'answer', 'd': answer});
      case 'answer':
        await engine.setAnswer(d);
      case 'ice':
        await engine.addIceCandidate(d);
    }
  }

  Future<void> _send(
    String callId,
    String peer,
    Map<String, String> msg,
  ) async {
    final body = await ref
        .read(signalSealerProvider)!
        .sealDirect(peer, jsonEncode(msg));
    await ref
        .read(callRepositoryProvider)
        .addSignal(
          callId: callId,
          from: _me,
          to: peer,
          seq: _seq++,
          body: body,
        );
  }

  /// [status] null ise durumu karşı taraf zaten yazdı.
  Future<void> _finish(CallStatus? status, String reason) async {
    if (_ending) return;
    _ending = true;
    final callId = state.callId;
    if (status != null && callId != null) {
      try {
        await ref
            .read(callRepositoryProvider)
            .setStatus(callId, status, by: _me);
      } catch (_) {}
    }
    final engine = state.engine;
    _teardown();
    try {
      await engine?.dispose();
    } catch (_) {}
    if (ref.mounted) {
      state = state.copyWith(phase: CallPhase.ended, endReason: reason);
    }
  }

  void _teardown() {
    _ringTimer?.cancel();
    for (final s in _subs) {
      unawaited(s.cancel());
    }
    _subs.clear();
  }
}

final callControllerProvider = NotifierProvider<CallController, CallSession>(
  CallController.new,
);
