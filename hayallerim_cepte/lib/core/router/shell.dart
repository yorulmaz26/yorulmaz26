import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/providers.dart';
import '../logic/achievements_catalog.dart';

/// Alt menülü ana iskelet: Ana Sayfa, Hayallerim, Hedeflerim, Hayal Bahçem, Profil.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key, required this.shell});
  final StatefulNavigationShell shell;

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  StreamSubscription<AchievementDef>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = ref.read(gameRepoProvider).unlockedEvents.listen(_celebrate);
  }

  void _celebrate(AchievementDef a) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      duration: const Duration(seconds: 4),
      content: Row(children: [
        Icon(a.icon, color: a.color),
        const SizedBox(width: 12),
        Expanded(child: Text('Yeni rozet: ${a.title}  (+${a.points} puan)')),
      ]),
      action: SnackBarAction(label: 'Gör', onPressed: () => context.push('/achievements')),
    ));
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: widget.shell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: widget.shell.currentIndex,
        onDestinationSelected: (i) => widget.shell.goBranch(i, initialLocation: i == widget.shell.currentIndex),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home_rounded), label: 'Ana Sayfa'),
          NavigationDestination(icon: Icon(Icons.auto_awesome_outlined), selectedIcon: Icon(Icons.auto_awesome), label: 'Hayallerim'),
          NavigationDestination(icon: Icon(Icons.flag_outlined), selectedIcon: Icon(Icons.flag_rounded), label: 'Hedeflerim'),
          NavigationDestination(icon: Icon(Icons.park_outlined), selectedIcon: Icon(Icons.park_rounded), label: 'Bahçem'),
          NavigationDestination(icon: Icon(Icons.person_outline_rounded), selectedIcon: Icon(Icons.person_rounded), label: 'Profil'),
        ],
      ),
    );
  }
}
