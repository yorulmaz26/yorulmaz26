import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../features/auth/application/auth_providers.dart';
import '../features/notifications/application/push_providers.dart';
import '../features/privacy/application/privacy_providers.dart';
import '../features/privacy/presentation/unlock_gate.dart';
import 'router.dart';
import 'theme/app_colors.dart';
import 'theme/app_theme.dart';

class KusDiliApp extends ConsumerStatefulWidget {
  const KusDiliApp({super.key});

  @override
  ConsumerState<KusDiliApp> createState() => _KusDiliAppState();
}

class _KusDiliAppState extends ConsumerState<KusDiliApp> {
  late final AppLifecycleListener _lifecycle;
  StreamSubscription<String>? _opened;

  /// Uygulama değiştiricide (son uygulamalar) içerik görünmesin.
  bool _obscured = false;

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(
      onInactive: () => setState(() => _obscured = true),
      onResume: () => setState(() => _obscured = false),
      // Arka plana geçince açılmış tüm kilitler yeniden kapanır.
      onHide: () => ref.read(unlockProvider.notifier).lockAll(),
    );
    _listenNotificationTaps();
  }

  /// Bildirime dokunulunca ilgili sohbeti aç (kilitliyse kilit ekranı gelir).
  Future<void> _listenNotificationTaps() async {
    final messaging = ref.read(pushMessagingProvider);
    void open(String chatId) {
      if (ref.read(sessionStatusProvider) != SessionStatus.ready) return;
      ref.read(routerProvider).push(Routes.chat(chatId));
    }

    try {
      _opened = messaging.openedChatIds.listen(open);
      final initial = await messaging.initialChatId();
      if (initial != null) {
        // Oturum hazır olana kadar bekle.
        var done = false;
        ref.listenManual(sessionStatusProvider, (_, s) {
          if (!done && s == SessionStatus.ready) {
            done = true;
            open(initial);
          }
        }, fireImmediately: true);
      }
    } catch (_) {
      // Firebase yapılandırılmamışsa bildirim yok.
    }
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    _opened?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(screenSecureProvider, (_, secure) {
      ref.read(secureScreenProvider).setSecure(secure);
    });
    return MaterialApp.router(
      title: 'Kuş Dili',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      routerConfig: ref.watch(routerProvider),
      builder: (context, child) {
        final ready = ref.watch(sessionStatusProvider) == SessionStatus.ready;
        final prefs = ref.watch(privacyPrefsProvider).value;
        final appLocked =
            ready &&
            (prefs?.appLock ?? false) &&
            !ref.watch(unlockProvider).app;
        final anySecret =
            (prefs?.appLock ?? false) ||
            (prefs?.lockedChats.isNotEmpty ?? false) ||
            (prefs?.hiddenChats.isNotEmpty ?? false);
        return Stack(
          children: [
            child ?? const SizedBox.shrink(),
            if (appLocked)
              // Kendi Navigator'ı: metin alanı için Overlay gerekir.
              Positioned.fill(
                child: Navigator(
                  pages: [
                    MaterialPage<void>(
                      child: UnlockGate(
                        title: 'Kuş Dili kilitli',
                        onUnlocked: ref.read(unlockProvider.notifier).unlockApp,
                      ),
                    ),
                  ],
                  onDidRemovePage: (_) {},
                ),
              ),
            if (_obscured && anySecret)
              const Positioned.fill(
                child: ColoredBox(
                  color: AppColors.night,
                  child: Center(
                    child: Icon(
                      Icons.flutter_dash,
                      size: 72,
                      color: AppColors.gold,
                    ),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
