import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

class _Page {
  const _Page(this.title, this.text, this.icon, this.gradient);
  final String title;
  final String text;
  final IconData icon;
  final Gradient gradient;
}

const _pages = [
  _Page('Hayallerini Keşfet', 'Hayallerini tek bir yerde biriktir.', Icons.auto_awesome, AppColors.brandGradient),
  _Page('Hedeflerini Planla', 'Büyük hayallerini küçük adımlarla gerçekleştir.', Icons.route_rounded,
      LinearGradient(colors: [AppColors.turquoise, Color(0xFF3B82F6)], begin: Alignment.topLeft, end: Alignment.bottomRight)),
  _Page('Başarılarını Kutla', 'Her adımda büyüyen kendi hayal dünyanı oluştur.', Icons.park_rounded, AppColors.goldGradient),
];

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _ctrl = PageController();
  int _index = 0;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _next() {
    if (_index < _pages.length - 1) {
      _ctrl.nextPage(duration: const Duration(milliseconds: 380), curve: Curves.easeOutCubic);
    } else {
      context.go('/setup');
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(onPressed: () => context.go('/setup'), child: const Text('Atla')),
            ),
            Expanded(
              child: PageView.builder(
                controller: _ctrl,
                itemCount: _pages.length,
                onPageChanged: (i) => setState(() => _index = i),
                itemBuilder: (context, i) {
                  final p = _pages[i];
                  return LayoutBuilder(builder: (context, c) {
                    final art = (c.maxHeight * 0.42).clamp(140.0, 280.0);
                    return SingleChildScrollView(
                      padding: const EdgeInsets.symmetric(horizontal: Gap.xl),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(minHeight: c.maxHeight),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            TweenAnimationBuilder<double>(
                              key: ValueKey(i),
                              tween: Tween(begin: 0.85, end: 1),
                              duration: const Duration(milliseconds: 600),
                              curve: Curves.easeOutBack,
                              builder: (_, v, child) => Transform.scale(scale: v, child: child),
                              child: Container(
                                width: art,
                                height: art,
                                decoration: BoxDecoration(
                                  gradient: p.gradient,
                                  borderRadius: BorderRadius.circular(art * 0.3),
                                  boxShadow: [BoxShadow(color: AppColors.purple.withValues(alpha: 0.25), blurRadius: 40, offset: const Offset(0, 16))],
                                ),
                                child: Icon(p.icon, size: art * 0.42, color: Colors.white),
                              ),
                            ),
                            const SizedBox(height: 40),
                            Text(p.title, textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineMedium),
                            Gap.h12,
                            Text(p.text, textAlign: TextAlign.center, style: TextStyle(fontSize: 17, color: cs.onSurfaceVariant, height: 1.4)),
                          ],
                        ),
                      ),
                    );
                  });
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(Gap.xl),
              child: Row(
                children: [
                  Row(
                    children: List.generate(
                      _pages.length,
                      (i) => AnimatedContainer(
                        duration: const Duration(milliseconds: 250),
                        margin: const EdgeInsets.only(right: 6),
                        width: i == _index ? 24 : 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: i == _index ? cs.primary : cs.primary.withValues(alpha: 0.25),
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
                  ),
                  const Spacer(),
                  FilledButton(
                    onPressed: _next,
                    child: Text(_index == _pages.length - 1 ? 'Başlayalım' : 'İleri'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
