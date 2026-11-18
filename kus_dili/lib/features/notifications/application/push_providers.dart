import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../auth/application/auth_providers.dart';
import '../../auth/domain/auth_repository.dart';
import '../../chat/application/chat_providers.dart';
import '../data/firebase_push_messaging.dart';
import '../data/firestore_push_repository.dart';
import '../domain/push.dart';

final pushMessagingProvider = Provider<PushMessaging>(
  (ref) => FirebasePushMessaging(),
);

final pushRepositoryProvider = Provider<PushRepository>(
  (ref) => FirestorePushRepository(ref.watch(firestoreProvider)),
);

final pushPrefsProvider = StreamProvider<PushPrefs>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(const PushPrefs());
  return ref.watch(pushRepositoryProvider).watch(uid);
});

/// Oturum açıkken bu cihazın bildirim anahtarını kaydeder ve yeniler.
/// İzin verilmezse sessizce vazgeçer (uygulama bildirimsiz çalışır).
final pushRegistrationProvider = FutureProvider<bool>((ref) async {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return false;
  final messaging = ref.watch(pushMessagingProvider);
  final repo = ref.watch(pushRepositoryProvider);
  try {
    if (!await messaging.requestPermission()) return false;
    final token = await messaging.token();
    if (token == null) return false;
    await repo.addToken(uid, token);
    final sub = messaging.tokenRefresh.listen((t) => repo.addToken(uid, t));
    ref.onDispose(sub.cancel);
    return true;
  } catch (_) {
    return false;
  }
});

/// Çıkış: önce bu cihazın bildirim anahtarı silinir ki çıkış yapılan
/// telefona bu hesabın bildirimleri gitmeye devam etmesin.
Future<void> signOutEverywhere({
  required String? uid,
  required PushMessaging messaging,
  required PushRepository repo,
  required AuthRepository auth,
}) async {
  try {
    final token = await messaging.token();
    if (uid != null && token != null) await repo.removeToken(uid, token);
    await messaging.deleteToken();
  } catch (_) {
    // Bildirim temizliği başarısız olsa da çıkış yapılmalı.
  }
  await auth.signOut();
}

Future<void> signOutWithPushCleanup(WidgetRef ref) => signOutEverywhere(
  uid: ref.read(myUidProvider),
  messaging: ref.read(pushMessagingProvider),
  repo: ref.read(pushRepositoryProvider),
  auth: ref.read(authRepositoryProvider),
);
