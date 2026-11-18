import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/providers.dart';
import '../../core/router/app_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/widgets/logo.dart';

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1600));

  @override
  void initState() {
    super.initState();
    _c.forward().whenComplete(() async {
      await Future<void>.delayed(const Duration(milliseconds: 250));
      if (!mounted) return;
      final done = onboardingDone(ref.read(prefsProvider));
      context.go(done ? '/home' : '/onboarding');
    });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.night,
      body: Container(
        decoration: const BoxDecoration(gradient: AppColors.nightGradient),
        child: Center(
          child: AnimatedBuilder(
            animation: _c,
            builder: (context, _) {
              final textT = Curves.easeOut.transform(((_c.value - 0.5) / 0.5).clamp(0, 1));
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  AppLogo(size: 132, progress: _c.value),
                  const SizedBox(height: 28),
                  Opacity(
                    opacity: textT,
                    child: Transform.translate(
                      offset: Offset(0, 12 * (1 - textT)),
                      child: Column(children: [
                        const Text('Hayallerim Cepte',
                            style: TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w800, letterSpacing: -0.5)),
                        const SizedBox(height: 8),
                        Text('Hayal Et, Planla, Gerçekleştir.',
                            style: TextStyle(color: Colors.white.withValues(alpha: 0.75), fontSize: 15, letterSpacing: 0.2)),
                      ]),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}
