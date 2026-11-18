import 'dart:async';
import 'dart:convert';

import 'package:flutter_webrtc/flutter_webrtc.dart';
import 'package:material_ui/material_ui.dart';

import '../domain/call_engine.dart';

/// flutter_webrtc ile gerçek arama. Medya DTLS-SRTP ile şifrelidir; TURN
/// sunucusu üzerinden geçse bile sunucu içeriği çözemez.
final class WebRtcCallEngine implements CallEngine {
  RTCPeerConnection? _pc;
  MediaStream? _local;
  final _localRenderer = RTCVideoRenderer();
  final _remoteRenderer = RTCVideoRenderer();
  final _ice = StreamController<String>.broadcast();
  final _conn = StreamController<EngineConnection>.broadcast();
  final List<RTCIceCandidate> _pending = [];
  bool _remoteSet = false;
  bool _video = false;

  @override
  Future<void> start({
    required bool video,
    required List<IceServer> iceServers,
  }) async {
    _video = video;
    await _localRenderer.initialize();
    await _remoteRenderer.initialize();
    _local = await navigator.mediaDevices.getUserMedia({
      'audio': {'echoCancellation': true, 'noiseSuppression': true},
      'video': video
          ? {'facingMode': 'user', 'width': 1280, 'height': 720}
          : false,
    });
    _localRenderer.srcObject = _local;
    final pc = await createPeerConnection({
      'iceServers': [for (final s in iceServers) s.toMap()],
      'sdpSemantics': 'unified-plan',
    });
    _pc = pc;
    for (final t in _local!.getTracks()) {
      await pc.addTrack(t, _local!);
    }
    pc.onIceCandidate = (c) {
      if (c.candidate != null) _ice.add(jsonEncode(c.toMap()));
    };
    pc.onTrack = (e) {
      if (e.streams.isNotEmpty) _remoteRenderer.srcObject = e.streams.first;
    };
    pc.onConnectionState = (s) {
      _conn.add(switch (s) {
        RTCPeerConnectionState.RTCPeerConnectionStateConnected =>
          EngineConnection.connected,
        RTCPeerConnectionState.RTCPeerConnectionStateFailed =>
          EngineConnection.failed,
        RTCPeerConnectionState.RTCPeerConnectionStateClosed =>
          EngineConnection.closed,
        _ => EngineConnection.connecting,
      });
    };
    await Helper.setSpeakerphoneOn(video);
  }

  RTCPeerConnection get _p => _pc ?? (throw StateError('Arama başlamadı.'));

  static String _sdp(RTCSessionDescription d) =>
      jsonEncode({'sdp': d.sdp, 'type': d.type});

  static RTCSessionDescription _parse(String s) {
    final j = jsonDecode(s) as Map<String, dynamic>;
    return RTCSessionDescription(j['sdp'] as String?, j['type'] as String?);
  }

  Future<void> _flushPending() async {
    _remoteSet = true;
    for (final c in _pending) {
      await _p.addCandidate(c);
    }
    _pending.clear();
  }

  @override
  Future<String> createOffer() async {
    final o = await _p.createOffer({
      'offerToReceiveAudio': true,
      'offerToReceiveVideo': _video,
    });
    await _p.setLocalDescription(o);
    return _sdp(o);
  }

  @override
  Future<String> acceptOffer(String offer) async {
    await _p.setRemoteDescription(_parse(offer));
    await _flushPending();
    final a = await _p.createAnswer();
    await _p.setLocalDescription(a);
    return _sdp(a);
  }

  @override
  Future<void> setAnswer(String answer) async {
    await _p.setRemoteDescription(_parse(answer));
    await _flushPending();
  }

  @override
  Future<void> addIceCandidate(String candidate) async {
    final j = jsonDecode(candidate) as Map<String, dynamic>;
    final c = RTCIceCandidate(
      j['candidate'] as String?,
      j['sdpMid'] as String?,
      j['sdpMLineIndex'] as int?,
    );
    if (_remoteSet) {
      await _p.addCandidate(c);
    } else {
      _pending.add(c);
    }
  }

  @override
  Stream<String> get localIceCandidates => _ice.stream;

  @override
  Stream<EngineConnection> get connection => _conn.stream;

  @override
  Future<void> setMuted(bool muted) async {
    for (final t in _local?.getAudioTracks() ?? const <MediaStreamTrack>[]) {
      t.enabled = !muted;
    }
  }

  @override
  Future<void> setCameraEnabled(bool enabled) async {
    for (final t in _local?.getVideoTracks() ?? const <MediaStreamTrack>[]) {
      t.enabled = enabled;
    }
  }

  @override
  Future<void> switchCamera() async {
    final tracks = _local?.getVideoTracks() ?? const <MediaStreamTrack>[];
    if (tracks.isNotEmpty) await Helper.switchCamera(tracks.first);
  }

  @override
  Future<void> setSpeaker(bool on) => Helper.setSpeakerphoneOn(on);

  @override
  Widget localView() => RTCVideoView(
    _localRenderer,
    mirror: true,
    objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
  );

  @override
  Widget remoteView() => RTCVideoView(
    _remoteRenderer,
    objectFit: RTCVideoViewObjectFit.RTCVideoViewObjectFitCover,
  );

  @override
  Future<void> dispose() async {
    for (final t in _local?.getTracks() ?? const <MediaStreamTrack>[]) {
      await t.stop();
    }
    await _local?.dispose();
    await _pc?.close();
    _localRenderer.srcObject = null;
    _remoteRenderer.srcObject = null;
    await _localRenderer.dispose();
    await _remoteRenderer.dispose();
    await _ice.close();
    await _conn.close();
  }
}
