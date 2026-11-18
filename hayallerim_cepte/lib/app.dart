import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/router/app_router.dart';
import 'core/settings/settings_controller.dart';
import 'core/theme/app_colors.dart';
import 'core/theme/app_theme.dart';
import 'features/security/lock_controller.dart';
import 'features/security/lock_screen.dart';

class HayallerimCepteApp extends ConsumerStatefulWidget {
  const HayallerimCepteApp({super.key});

  @override
  ConsumerState<HayallerimCepteApp> createState() => _HayallerimCepteAppState();
}

class _HayallerimCepteAppState extends ConsumerState<HayallerimCepteApp> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final lock = ref.read(lockProvider.notifier);
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden) lock.onPaused();
    if (state == AppLifecycleState.resumed) lock.onResumed();
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsProvider);
    final router = ref.watch(routerProvider);
    final accent = AppColors.accents[settings.accentIndex.clamp(0, AppColors.accents.length - 1)];
    return MaterialApp.router(
      title: 'Hayallerim Cepte',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(accent),
      darkTheme: AppTheme.dark(accent),
      themeMode: settings.themeMode,
      routerConfig: router,
      locale: const Locale('tr', 'TR'),
      supportedLocales: const [Locale('tr', 'TR')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      builder: (context, child) {
        // Erişilebilirlik: sistem yazı boyutuna uygulama içi ölçek eklenir
        // (aşırı büyümede yerleşim bozulmasın diye üst sınır 2.0).
        final mq = MediaQuery.of(context);
        final scaler = TextScaler.linear((mq.textScaler.scale(1) * settings.textScale).clamp(0.8, 2.0));
        return MediaQuery(
          data: mq.copyWith(textScaler: scaler),
          child: Consumer(
            builder: (context, ref, _) {
              final locked = ref.watch(lockProvider.select((s) => s.locked));
              return Stack(children: [
                child ?? const SizedBox(),
                if (locked) const Positioned.fill(child: LockScreen()),
              ]);
            },
          ),
        );
      },
    );
  }
}
