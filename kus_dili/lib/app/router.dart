import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/translator/presentation/splash_screen.dart';
import '../features/translator/presentation/translator_screen.dart';

/// Rota adları tek yerde tutulur. Sonraki aşamalarda eklenecekler:
/// /auth, /profile/setup, /chats, /chats/:id, /settings/privacy, /learn ...
abstract final class Routes {
  static const splash = '/';
  static const translator = '/translator';
}

final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    initialLocation: Routes.splash,
    routes: [
      GoRoute(
        path: Routes.splash,
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: Routes.translator,
        builder: (context, state) => const TranslatorScreen(),
      ),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});
