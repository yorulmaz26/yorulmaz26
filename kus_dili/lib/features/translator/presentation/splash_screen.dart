import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    // Aşama 3'te: oturum durumuna göre /auth veya /chats'e yönlenecek.
    Future<void>.delayed(const Duration(milliseconds: 1200), () {
      if (mounted) context.go(Routes.translator);
    });
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.flutter_dash, size: 72, color: AppColors.gold),
            const SizedBox(height: 16),
            Text(
              'Kuş Dili',
              style: text.displaySmall?.copyWith(
                color: AppColors.snow,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Sadece anlayan anlasın.',
              style: text.titleMedium?.copyWith(color: AppColors.turquoise),
            ),
          ],
        ),
      ),
    );
  }
}
