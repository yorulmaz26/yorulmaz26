import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../application/learn_providers.dart';
import '../domain/curriculum.dart';
import '../domain/exercise.dart';
import 'exercise_runner.dart';

/// Kuş dili öğrenme ana sayfası. Hesap ve internet gerektirmez.
class LearnHomeScreen extends ConsumerWidget {
  const LearnHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = ref.watch(progressProvider).value;
    final now = ref.watch(learnClockProvider)();
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Kuş dilini öğren')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Text(
                        'Seviye ${p?.level ?? 1}',
                        key: const Key('learnLevel'),
                        style: text.titleLarge,
                      ),
                      const Spacer(),
                      const Icon(
                        Icons.local_fire_department,
                        color: AppColors.gold,
                      ),
                      Text(
                        ' ${p?.streakOn(now) ?? 0} gün',
                        key: const Key('learnStreak'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  LinearProgressIndicator(value: p?.levelProgress ?? 0),
                  const SizedBox(height: 8),
                  Text(
                    '${p?.xp ?? 0} XP · doğruluk %${p?.accuracyPercent ?? 0} · '
                    'en iyi test ${p?.bestQuiz ?? 0}',
                    key: const Key('learnStats'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: _Tile(
                  key: const Key('openTranslator'),
                  icon: Icons.translate,
                  title: 'Çevirici',
                  onTap: () => context.push(Routes.translator),
                ),
              ),
              Expanded(
                child: _Tile(
                  key: const Key('openQuiz'),
                  icon: Icons.quiz_outlined,
                  title: 'Mini test',
                  onTap: () => context.push(Routes.learnQuiz),
                ),
              ),
              Expanded(
                child: _Tile(
                  key: const Key('openPronounce'),
                  icon: Icons.record_voice_over_outlined,
                  title: 'Telaffuz',
                  onTap: () => context.push(Routes.learnPronounce),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text('Dersler', style: text.titleMedium),
          for (final l in Curriculum.lessons)
            Card(
              child: ListTile(
                key: Key('lesson-${l.id}'),
                leading: Icon(
                  (p?.completedLessons.contains(l.id) ?? false)
                      ? Icons.check_circle
                      : Icons.flutter_dash,
                  color: (p?.completedLessons.contains(l.id) ?? false)
                      ? AppColors.turquoise
                      : AppColors.gold,
                ),
                title: Text(l.title),
                subtitle: Text(l.description),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => context.push(Routes.learnLesson(l.id)),
              ),
            ),
        ],
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Column(
          children: [
            Icon(icon, color: AppColors.gold),
            const SizedBox(height: 6),
            Text(title, textAlign: TextAlign.center),
          ],
        ),
      ),
    ),
  );
}

class LessonScreen extends ConsumerStatefulWidget {
  const LessonScreen({super.key, required this.lessonId});

  final String lessonId;

  @override
  ConsumerState<LessonScreen> createState() => _LessonScreenState();
}

class _LessonScreenState extends ConsumerState<LessonScreen> {
  late final Lesson _lesson = Curriculum.byId(widget.lessonId);
  late final List<Exercise> _exercises = ExerciseGenerator(
    random: ref.read(learnRandomProvider),
  ).forLesson(_lesson);

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(_lesson.title)),
    body: ExerciseRunner(
      exercises: _exercises,
      onFinished: (c, t) => ref
          .read(progressProvider.notifier)
          .record(correct: c, total: t, lessonId: _lesson.id),
    ),
  );
}

class QuizScreen extends ConsumerStatefulWidget {
  const QuizScreen({super.key});

  @override
  ConsumerState<QuizScreen> createState() => _QuizScreenState();
}

class _QuizScreenState extends ConsumerState<QuizScreen> {
  late final List<Exercise> _exercises = ExerciseGenerator(
    random: ref.read(learnRandomProvider),
  ).quiz();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Mini test')),
    body: ExerciseRunner(
      exercises: _exercises,
      onFinished: (c, t) => ref
          .read(progressProvider.notifier)
          .record(correct: c, total: t, quiz: true),
    ),
  );
}

/// Telaffuz: yazdığın metni kuş diline çevirip sesli okur.
class PronounceScreen extends ConsumerStatefulWidget {
  const PronounceScreen({super.key});

  @override
  ConsumerState<PronounceScreen> createState() => _PronounceScreenState();
}

class _PronounceScreenState extends ConsumerState<PronounceScreen> {
  final _c = TextEditingController(text: 'Seni seviyorum');
  KusDiliVariant _variant = KusDiliVariant.g;

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final kus = KusDiliCodec(_variant).encode(_c.text);
    final speaker = ref.read(speakerProvider);
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Telaffuz')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SegmentedButton<KusDiliVariant>(
            segments: [
              for (final v in KusDiliVariant.presets)
                ButtonSegment(value: v, label: Text(v.name.split(' ').first)),
            ],
            selected: {_variant},
            onSelectionChanged: (s) => setState(() => _variant = s.first),
          ),
          const SizedBox(height: 12),
          TextField(
            key: const Key('pronounceInput'),
            controller: _c,
            decoration: const InputDecoration(labelText: 'Türkçe'),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 16),
          Text(
            kus,
            key: const Key('pronounceKus'),
            style: text.headlineSmall?.copyWith(color: AppColors.gold),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              FilledButton.icon(
                key: const Key('speakKus'),
                icon: const Icon(Icons.volume_up),
                label: const Text('Kuş dilinde dinle'),
                onPressed: () => speaker.speak(kus),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                key: const Key('speakSlow'),
                onPressed: () => speaker.speak(kus.replaceAll(' ', ' , ')),
                child: const Text('Yavaş'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          const Text(
            'İpucu: her sesli harften sonra gelen heceyi vurgulamadan, akıcı '
            'söyle. "Segenigi" → se-ge-ni-gi.',
          ),
        ],
      ),
    );
  }
}
