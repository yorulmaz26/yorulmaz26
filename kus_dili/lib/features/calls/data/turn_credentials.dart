import 'package:cloud_functions/cloud_functions.dart';

import '../domain/call_engine.dart';

/// Varsayılan: yalnızca STUN (aynı ağdaki veya basit NAT arkasındaki
/// cihazlar). Mobil ağlarda güvenilir arama için TURN gerekir; kısa ömürlü
/// TURN kimlik bilgileri `turnCredentials` Cloud Function'ından alınır.
const defaultStun = IceServer(['stun:stun.l.google.com:19302']);

abstract interface class IceServerSource {
  Future<List<IceServer>> iceServers();
}

final class CloudFunctionIceServers implements IceServerSource {
  CloudFunctionIceServers([FirebaseFunctions? f])
    : _f = f ?? FirebaseFunctions.instanceFor(region: 'europe-west1');

  final FirebaseFunctions _f;

  @override
  Future<List<IceServer>> iceServers() async {
    try {
      final r = await _f
          .httpsCallable('turnCredentials')
          .call<Map<String, dynamic>>();
      final d = r.data;
      final urls = (d['urls'] as List?)?.cast<String>() ?? const [];
      if (urls.isEmpty) return const [defaultStun];
      return [
        defaultStun,
        IceServer(
          urls,
          username: d['username'] as String?,
          credential: d['credential'] as String?,
        ),
      ];
    } catch (_) {
      return const [defaultStun];
    }
  }
}
