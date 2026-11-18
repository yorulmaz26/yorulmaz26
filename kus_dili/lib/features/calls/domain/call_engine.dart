import 'package:material_ui/material_ui.dart';

final class IceServer {
  const IceServer(this.urls, {this.username, this.credential});

  final List<String> urls;
  final String? username;
  final String? credential;

  Map<String, Object?> toMap() => {
    'urls': urls,
    if (username != null) 'username': username,
    if (credential != null) 'credential': credential,
  };
}

enum EngineConnection { connecting, connected, failed, closed }

/// WebRTC sarmalayıcısı (platform kodu data/ altında; testlerde sahtesi).
/// SDP ve ICE adayları JSON metin olarak taşınır; bu metinler Signal ile
/// şifrelenerek karşı tarafa gider.
abstract interface class CallEngine {
  Future<void> start({
    required bool video,
    required List<IceServer> iceServers,
  });
  Future<String> createOffer();

  /// Teklifi uygular ve yanıtı (answer) döner.
  Future<String> acceptOffer(String offer);
  Future<void> setAnswer(String answer);

  /// Uzak açıklama gelmeden gelen adaylar sıraya alınır.
  Future<void> addIceCandidate(String candidate);

  Stream<String> get localIceCandidates;
  Stream<EngineConnection> get connection;

  Future<void> setMuted(bool muted);
  Future<void> setCameraEnabled(bool enabled);
  Future<void> switchCamera();
  Future<void> setSpeaker(bool on);

  Widget localView();
  Widget remoteView();

  Future<void> dispose();
}
