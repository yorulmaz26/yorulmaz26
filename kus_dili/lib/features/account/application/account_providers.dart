import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/application/auth_providers.dart';
import '../../crypto/application/crypto_providers.dart';
import '../../notifications/application/push_providers.dart';
import '../data/cloud_account_service.dart';
import '../domain/account_service.dart';

final accountServiceProvider = Provider<AccountService>(
  (ref) => CloudAccountService(),
);

/// Hesabı sunucudan siler, sonra bu cihazdaki tüm anahtarları ve şifreli
/// önbelleği temizleyip çıkış yapar. Sunucu silmesi başarısız olursa
/// cihazda hiçbir şey silinmez (hata fırlatılır).
Future<void> deleteAccountEverywhere(WidgetRef ref) async {
  await ref.read(accountServiceProvider).deleteAccount();
  try {
    await ref.read(payloadCacheProvider)?.clearAll();
  } catch (_) {}
  try {
    await ref.read(pushMessagingProvider).deleteToken();
  } catch (_) {}
  await ref.read(secureKvProvider).clearAll();
  await ref.read(authRepositoryProvider).signOut();
}

/// Bu cihaz hesaptan kaldırıldı: anahtarlar ve şifreli önbellek silinir,
/// bildirim anahtarı kaldırılır ve çıkış yapılır. Yeniden giriş yapılırsa
/// cihaz yeni bir cihaz olarak eklenir.
Future<void> wipeUnlinkedDevice(WidgetRef ref) async {
  ref
      .read(signOutNoticeProvider.notifier)
      .set(
        'Bu cihazın bağlantısı hesabının başka bir cihazından kaldırıldı. '
        'Bu cihazdaki mesajlar ve anahtarlar silindi.',
      );
  try {
    await ref.read(payloadCacheProvider)?.clearAll();
  } catch (_) {}
  await ref.read(secureKvProvider).clearAll();
  await signOutWithPushCleanup(ref);
}

/// Çıkış nedenini giriş ekranında bir kez göstermek için.
class SignOutNotice extends Notifier<String?> {
  @override
  String? build() => null;

  void set(String? v) => state = v;
}

final signOutNoticeProvider = NotifierProvider<SignOutNotice, String?>(
  SignOutNotice.new,
);
