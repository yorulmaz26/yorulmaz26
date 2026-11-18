import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../features/auth/application/auth_providers.dart';
import '../features/auth/presentation/phone_auth_screen.dart';
import '../features/home/presentation/firebase_setup_screen.dart';
import '../features/home/presentation/home_screen.dart';
import '../features/profile/presentation/profile_setup_screen.dart';
import '../features/translator/presentation/splash_screen.dart';
import '../features/translator/presentation/translator_screen.dart';

abstract final class Routes {
  static const splash = '/';
  static const translator = '/translator';
  static const auth = '/auth';
  static const profileSetup = '/profile/setup';
  static const home = '/home';
  static const firebaseSetup = '/setup';
}

/// Oturum durumuna göre yönlendirme. Saf fonksiyon; birim testi vardır.
///
/// Çevirici her durumda açıktır (öğrenme modülü mesajlaşmadan bağımsızdır).
String? redirectFor(SessionStatus status, String location) {
  if (location == Routes.translator) return null;
  String? onlyAt(String target) => location == target ? null : target;
  return switch (status) {
    SessionStatus.firebaseUnavailable => onlyAt(Routes.firebaseSetup),
    SessionStatus.loading || SessionStatus.error => onlyAt(Routes.splash),
    SessionStatus.signedOut => onlyAt(Routes.auth),
    SessionStatus.needsProfile => onlyAt(Routes.profileSetup),
    SessionStatus.ready =>
      const {
            Routes.splash,
            Routes.auth,
            Routes.profileSetup,
            Routes.firebaseSetup,
          }.contains(location)
          ? Routes.home
          : null,
  };
}

final routerProvider = Provider<GoRouter>((ref) {
  final status = ValueNotifier(ref.read(sessionStatusProvider));
  ref.listen(sessionStatusProvider, (_, next) => status.value = next);

  final router = GoRouter(
    initialLocation: Routes.splash,
    refreshListenable: status,
    redirect: (context, state) =>
        redirectFor(status.value, state.matchedLocation),
    routes: [
      GoRoute(
        path: Routes.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: Routes.translator,
        builder: (context, state) => const TranslatorScreen(),
      ),
      GoRoute(
        path: Routes.auth,
        builder: (context, state) => const PhoneAuthScreen(),
      ),
      GoRoute(
        path: Routes.profileSetup,
        builder: (context, state) => const ProfileSetupScreen(),
      ),
      GoRoute(
        path: Routes.home,
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: Routes.firebaseSetup,
        builder: (context, state) => const FirebaseSetupScreen(),
      ),
    ],
  );
  ref.onDispose(() {
    router.dispose();
    status.dispose();
  });
  return router;
});
