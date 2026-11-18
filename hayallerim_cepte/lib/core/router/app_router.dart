import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../features/achievements/achievements_screen.dart';
import '../../features/backup/backup_screen.dart';
import '../../features/balance/balance_screen.dart';
import '../../features/board/board_screen.dart';
import '../../features/capsule/capsule_screens.dart';
import '../../features/dreams/dream_detail_screen.dart';
import '../../features/dreams/dream_form_screen.dart';
import '../../features/dreams/dreams_screen.dart';
import '../../features/dreams/archive_screen.dart';
import '../../features/dreams/categories_screen.dart';
import '../../features/focus/focus_screen.dart';
import '../../features/garden/garden_screen.dart';
import '../../features/goals/goal_detail_screen.dart';
import '../../features/goals/goals_screen.dart';
import '../../features/habits/habits_screen.dart';
import '../../features/home/home_screen.dart';
import '../../features/journal/journal_editor_screen.dart';
import '../../features/journal/journal_screen.dart';
import '../../features/onboarding/onboarding_screen.dart';
import '../../features/onboarding/setup_screen.dart';
import '../../features/planner/planner_screen.dart';
import '../../features/profile/profile_screen.dart';
import '../../features/savings/savings_detail_screen.dart';
import '../../features/savings/savings_screen.dart';
import '../../features/settings/settings_screen.dart';
import '../../features/settings/special_days_screen.dart';
import '../../features/security/security_screen.dart';
import '../../features/splash/splash_screen.dart';
import '../../features/stats/stats_screen.dart';
import '../providers.dart';
import 'shell.dart';

const kOnboardingDoneKey = 'onboarding_done';

bool onboardingDone(SharedPreferences p) => p.getBool(kOnboardingDoneKey) ?? false;

final rootNavigatorKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  final prefs = ref.watch(prefsProvider);
  return GoRouter(
    navigatorKey: rootNavigatorKey,
    initialLocation: '/splash',
    redirect: (context, state) {
      final loc = state.matchedLocation;
      final allowed = loc == '/splash' || loc == '/onboarding' || loc == '/setup';
      if (!onboardingDone(prefs) && !allowed) return '/onboarding';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/onboarding', builder: (_, _) => const OnboardingScreen()),
      GoRoute(path: '/setup', builder: (_, _) => const SetupScreen()),
      StatefulShellRoute.indexedStack(
        builder: (context, state, shell) => AppShell(shell: shell),
        branches: [
          StatefulShellBranch(routes: [GoRoute(path: '/home', builder: (_, _) => const HomeScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/dreams', builder: (_, _) => const DreamsScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/goals', builder: (_, _) => const GoalsScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/garden', builder: (_, _) => const GardenScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/profile', builder: (_, _) => const ProfileScreen())]),
        ],
      ),
      GoRoute(path: '/dream/new', builder: (_, s) => DreamFormScreen(categoryId: int.tryParse(s.uri.queryParameters['category'] ?? ''))),
      GoRoute(path: '/dream/:id', builder: (_, s) => DreamDetailScreen(id: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/dream/:id/edit', builder: (_, s) => DreamFormScreen(editId: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/archive', builder: (_, _) => const ArchiveScreen()),
      GoRoute(path: '/categories', builder: (_, _) => const CategoriesScreen()),
      GoRoute(path: '/board', builder: (_, _) => const BoardScreen()),
      GoRoute(path: '/goal/:id', builder: (_, s) => GoalDetailScreen(id: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/habits', builder: (_, _) => const HabitsScreen()),
      GoRoute(path: '/savings', builder: (_, _) => const SavingsScreen()),
      GoRoute(path: '/savings/:id', builder: (_, s) => SavingsDetailScreen(id: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/journal', builder: (_, _) => const JournalScreen()),
      GoRoute(path: '/journal/new', builder: (_, _) => const JournalEditorScreen()),
      GoRoute(path: '/journal/:id', builder: (_, s) => JournalEditorScreen(id: int.parse(s.pathParameters['id']!))),
      GoRoute(path: '/capsules', builder: (_, _) => const CapsulesScreen()),
      GoRoute(path: '/capsules/new', builder: (_, _) => const CapsuleFormScreen()),
      GoRoute(path: '/achievements', builder: (_, _) => const AchievementsScreen()),
      GoRoute(path: '/focus', builder: (_, _) => const FocusScreen()),
      GoRoute(path: '/balance', builder: (_, _) => const BalanceScreen()),
      GoRoute(path: '/planner', builder: (_, _) => const PlannerScreen()),
      GoRoute(path: '/stats', builder: (_, _) => const StatsScreen()),
      GoRoute(path: '/settings', builder: (_, _) => const SettingsScreen()),
      GoRoute(path: '/special-days', builder: (_, _) => const SpecialDaysScreen()),
      GoRoute(path: '/security', builder: (_, _) => const SecurityScreen()),
      GoRoute(path: '/backup', builder: (_, _) => const BackupScreen()),
    ],
  );
});
