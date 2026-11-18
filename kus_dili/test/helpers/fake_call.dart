import 'dart:async';

import 'package:kus_dili/features/calls/data/turn_credentials.dart';
import 'package:kus_dili/features/calls/domain/call_engine.dart';
import 'package:material_ui/material_ui.dart';

/// YALNIZCA TESTLER: WebRTC sahtesi. Kayıt tutar, adayları elle yayar.
class FakeCallEngine implements CallEngine {
  FakeCallEngine(this.name);

  final String name;
  bool started = false;
  bool? video;
  bool disposed = false;
  bool muted = false;
  String? receivedOffer;
  String? receivedAnswer;
  final List<String> receivedIce = [];
  final ice = StreamController<String>.broadcast();
  final conn = StreamController<EngineConnection>.broadcast();

  String get offer =>
      '{"type":"offer","sdp":"v=0 FAKE-OFFER-$name a=fingerprint:sha-256 AA:BB"}';
  String get answer => '{"type":"answer","sdp":"v=0 FAKE-ANSWER-$name"}';

  @override
  Future<void> start({
    required bool video,
    required List<IceServer> iceServers,
  }) async {
    started = true;
    this.video = video;
  }

  @override
  Future<String> createOffer() async => offer;

  @override
  Future<String> acceptOffer(String offer) async {
    receivedOffer = offer;
    return answer;
  }

  @override
  Future<void> setAnswer(String answer) async => receivedAnswer = answer;

  @override
  Future<void> addIceCandidate(String candidate) async =>
      receivedIce.add(candidate);

  @override
  Stream<String> get localIceCandidates => ice.stream;

  @override
  Stream<EngineConnection> get connection => conn.stream;

  @override
  Future<void> setMuted(bool m) async => muted = m;

  @override
  Future<void> setCameraEnabled(bool enabled) async {}

  @override
  Future<void> switchCamera() async {}

  @override
  Future<void> setSpeaker(bool on) async {}

  @override
  Widget localView() => const SizedBox(key: Key('localVideo'));

  @override
  Widget remoteView() => const SizedBox(key: Key('remoteVideo'));

  @override
  Future<void> dispose() async => disposed = true;
}

class FakeIceServers implements IceServerSource {
  @override
  Future<List<IceServer>> iceServers() async => const [defaultStun];
}
