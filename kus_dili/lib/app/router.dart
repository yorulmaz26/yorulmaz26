import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../features/auth/application/auth_providers.dart';
import '../features/auth/presentation/phone_auth_screen.dart';
import '../features/crypto/presentation/safety_number_screen.dart';
import '../features/home/presentation/firebase_setup_screen.dart';
import '../features/learn/presentation/learn_screens.dart';
import '../features/legal/terms_screen.dart';
import '../features/chat/presentation/chat_list_screen.dart';
import '../features/chat/presentation/chat_screen.dart';
import '../features/chat/presentation/group_info_screen.dart';
import '../features/chat/presentation/new_chat_screen.dart';
import '../features/chat/presentation/new_group_screen.dart';
import '../features/notifications/presentation/notification_settings_screen.dart';
import '../features/privacy/presentation/hidden_chats_screen.dart';
import '../features/privacy/presentation/privacy_settings_screen.dart';
import '../features/profile/presentation/profile_screen.dart';
import '../features/profile/presentation/profile_setup_screen.dart';
import '../features/safety/presentation/blocked_users_screen.dart';
import '../features/translator/presentation/splash_screen.dart';
import '../features/translator/presentation/translator_screen.dart';

abstract final class Routes {
  static const splash = '/';
  static const translator = '/translator';
  static const auth = '/auth';
  static const profileSetup = '/profile/setup';
  static const home = '/home';
  static const firebaseSetup = '/setup';
  static const profile = '/profile';
  static const newChat = '/chats/new';
  static const hiddenChats = '/chats/hidden';
  static const newGroup = '/groups/new';
  static String groupInfo(String chatId) => '/chats/$chatId/info';
  static const privacy = '/settings/privacy';
  static const notifications = '/settings/notifications';
  static const blocked = '/settings/blocked';
  static const terms = '/terms';
  static const learn = '/learn';
  static const learnQuiz = '/learn/quiz';
  static const learnPronounce = '/learn/pronounce';
  static String learnLesson(String id) => '/learn/lesson/$id';
  static String chat(String id) => '/chats/$id';
  static String safetyNumber(String chatId, String uid) =>
      '/chats/$chatId/safety/$uid';
}

/// Oturum durumuna göre yönlendirme. Saf fonksiyon; birim testi vardır.
///
/// Çevirici her durumda açıktır (öğrenme modülü mesajlaşmadan bağımsızdır).
String? redirectFor(SessionStatus status, String location) {
  // Çevirici, öğrenme modülü ve kullanım koşulları hesapsız da açılır.
  if (location == Routes.translator ||
      location == Routes.terms ||
      location.startsWith(Routes.learn)) {
    return null;
  }
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
        builder: (context, state) => const ChatListScreen(),
      ),
      GoRoute(
        path: Routes.profile,
        builder: (context, state) => const ProfileScreen(),
      ),
      GoRoute(
        path: Routes.newChat,
        builder: (context, state) => const NewChatScreen(),
      ),
      GoRoute(
        path: Routes.newGroup,
        builder: (context, state) => const NewGroupScreen(),
      ),
      GoRoute(
        path: Routes.hiddenChats,
        builder: (context, state) => const HiddenChatsScreen(),
      ),
      GoRoute(
        path: Routes.learn,
        builder: (context, state) => const LearnHomeScreen(),
        routes: [
          GoRoute(
            path: 'quiz',
            builder: (context, state) => const QuizScreen(),
          ),
          GoRoute(
            path: 'pronounce',
            builder: (context, state) => const PronounceScreen(),
          ),
          GoRoute(
            path: 'lesson/:id',
            builder: (context, state) =>
                LessonScreen(lessonId: state.pathParameters['id']!),
          ),
        ],
      ),
      GoRoute(
        path: Routes.notifications,
        builder: (context, state) => const NotificationSettingsScreen(),
      ),
      GoRoute(
        path: Routes.blocked,
        builder: (context, state) => const BlockedUsersScreen(),
      ),
      GoRoute(
        path: Routes.terms,
        builder: (context, state) => const TermsScreen(),
      ),
      GoRoute(
        path: Routes.privacy,
        builder: (context, state) => const PrivacySettingsScreen(),
      ),
      GoRoute(
        path: '/chats/:id',
        builder: (context, state) =>
            ChatScreen(chatId: state.pathParameters['id']!),
        routes: [
          GoRoute(
            path: 'info',
            builder: (context, state) =>
                GroupInfoScreen(chatId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'safety/:uid',
            builder: (context, state) =>
                SafetyNumberScreen(otherUid: state.pathParameters['uid']!),
          ),
        ],
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
