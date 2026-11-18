import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../application/learn_providers.dart';
import '../domain/exercise.dart';

/// Bir alıştırma dizisini oynatır; bitince doğru sayısını bildirir.
class ExerciseRunner extends ConsumerStatefulWidget {
  const ExerciseRunner({
    super.key,
    required this.exercises,
    required this.onFinished,
  });

  final List<Exercise> exercises;
  final Future<void> Function(int correct, int total) onFinished;

  @override
  ConsumerState<ExerciseRunner> createState() => _ExerciseRunnerState();
}

class _ExerciseRunnerState extends ConsumerState<ExerciseRunner> {
  int _i = 0;
  int _correct = 0;
  bool? _result;
  String? _hint;
  int? _chosen;
  final _typed = TextEditingController();
  final List<int> _picked = [];
  bool _finished = false;

  Exercise get _e => widget.exercises[_i];

  @override
  void dispose() {
    _typed.dispose();
    super.dispose();
  }

  void _grade(bool ok, {String? hint}) {
    setState(() {
      _result = ok;
      _hint = ok ? null : hint;
      if (ok) _correct++;
    });
  }

  void _check() {
    switch (_e) {
      case final ChooseKus e:
        if (_chosen != null) _grade(_chosen == e.correct);
      case final ChooseTurkish e:
        if (_chosen != null) _grade(_chosen == e.correct);
      case final TypeKus e:
        final ok = AnswerChecker.typed(e, _typed.text);
        _grade(ok, hint: ok ? null : AnswerChecker.hint(e, _typed.text));
      case final BuildSentence e:
        _grade(AnswerChecker.built(e, [for (final i in _picked) e.tokens[i]]));
    }
  }

  Future<void> _next() async {
    if (_i + 1 >= widget.exercises.length) {
      setState(() => _finished = true);
      await widget.onFinished(_correct, widget.exercises.length);
      return;
    }
    setState(() {
      _i++;
      _result = null;
      _hint = null;
      _chosen = null;
      _typed.clear();
      _picked.clear();
    });
  }

  void _speak(String t) => ref.read(speakerProvider).speak(t);

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    if (_finished) {
      final total = widget.exercises.length;
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                _correct / total >= 0.8
                    ? Icons.emoji_events
                    : Icons.flutter_dash,
                size: 72,
                color: AppColors.gold,
              ),
              const SizedBox(height: 16),
              Text(
                '$_correct / $total doğru',
                key: const Key('learnScore'),
                style: text.headlineSmall,
              ),
              Text(
                '+${_correct * 10} XP',
                style: text.titleMedium?.copyWith(color: AppColors.turquoise),
              ),
              const SizedBox(height: 24),
              FilledButton(
                key: const Key('learnDone'),
                onPressed: () => Navigator.of(context).maybePop(),
                child: const Text('Bitir'),
              ),
            ],
          ),
        ),
      );
    }
    final answered = _result != null;
    return Column(
      children: [
        LinearProgressIndicator(
          value: (_i + (answered ? 1 : 0)) / widget.exercises.length,
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(20),
            children: [
              ..._prompt(text),
              const SizedBox(height: 20),
              ..._input(answered),
              if (answered) ...[
                const SizedBox(height: 20),
                _Feedback(
                  ok: _result!,
                  answer: _e.answer,
                  hint: _hint,
                  onSpeak: () => _speak(_e.speakText),
                ),
              ],
            ],
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: SizedBox(
              width: double.infinity,
              child: answered
                  ? FilledButton(
                      key: const Key('next'),
                      onPressed: _next,
                      child: const Text('Devam'),
                    )
                  : FilledButton(
                      key: const Key('check'),
                      onPressed: _check,
                      child: const Text('Kontrol et'),
                    ),
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _prompt(TextTheme text) {
    final kusStyle = text.headlineSmall?.copyWith(color: AppColors.gold);
    return switch (_e) {
      ChooseKus(:final turkish) => [
        Text('Kuş dilinde nasıl söylenir?', style: text.titleMedium),
        const SizedBox(height: 8),
        Text(turkish, key: const Key('prompt'), style: text.headlineSmall),
      ],
      ChooseTurkish(:final kus) => [
        Text('Bu ne demek?', style: text.titleMedium),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: Text(kus, key: const Key('prompt'), style: kusStyle),
            ),
            IconButton(
              key: const Key('speakPrompt'),
              icon: const Icon(Icons.volume_up),
              onPressed: () => _speak(kus),
            ),
          ],
        ),
      ],
      TypeKus(:final turkish) => [
        Text('Kuş dilinde yaz', style: text.titleMedium),
        const SizedBox(height: 8),
        Text(turkish, key: const Key('prompt'), style: text.headlineSmall),
      ],
      BuildSentence(:final turkish) => [
        Text('Kuş dilinde cümleyi kur', style: text.titleMedium),
        const SizedBox(height: 8),
        Text(turkish, key: const Key('prompt'), style: text.headlineSmall),
      ],
    };
  }

  List<Widget> _input(bool answered) {
    switch (_e) {
      case ChooseKus(:final options) || ChooseTurkish(:final options):
        return [
          for (var k = 0; k < options.length; k++)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: ChoiceChip(
                key: Key('opt-$k'),
                label: SizedBox(
                  width: double.infinity,
                  child: Text(options[k]),
                ),
                selected: _chosen == k,
                onSelected: answered
                    ? null
                    : (_) => setState(() => _chosen = k),
              ),
            ),
        ];
      case TypeKus():
        return [
          TextField(
            key: const Key('typeAnswer'),
            controller: _typed,
            enabled: !answered,
            autocorrect: false,
            decoration: const InputDecoration(hintText: 'Kuş dilinde yaz…'),
            onSubmitted: (_) => answered ? null : _check(),
          ),
        ];
      case BuildSentence(:final tokens):
        return [
          Container(
            key: const Key('builtArea'),
            constraints: const BoxConstraints(minHeight: 56),
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              border: Border.all(color: AppColors.nightOutline),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final i in _picked)
                  InputChip(
                    label: Text(tokens[i]),
                    onPressed: answered
                        ? null
                        : () => setState(() => _picked.remove(i)),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (var i = 0; i < tokens.length; i++)
                if (!_picked.contains(i))
                  ActionChip(
                    key: Key('tok-$i'),
                    label: Text(tokens[i]),
                    onPressed: answered
                        ? null
                        : () => setState(() => _picked.add(i)),
                  ),
            ],
          ),
        ];
    }
  }
}

class _Feedback extends StatelessWidget {
  const _Feedback({
    required this.ok,
    required this.answer,
    required this.hint,
    required this.onSpeak,
  });

  final bool ok;
  final String answer;
  final String? hint;
  final VoidCallback onSpeak;

  @override
  Widget build(BuildContext context) {
    final color = ok ? AppColors.turquoise : Colors.redAccent;
    return Container(
      key: Key(ok ? 'feedbackOk' : 'feedbackWrong'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color),
      ),
      child: Row(
        children: [
          Icon(ok ? Icons.check_circle : Icons.cancel, color: color),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  ok ? 'Doğru!' : 'Doğrusu: $answer',
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                if (hint != null) Text(hint!),
              ],
            ),
          ),
          IconButton(
            key: const Key('speakAnswer'),
            icon: const Icon(Icons.volume_up),
            onPressed: onSpeak,
          ),
        ],
      ),
    );
  }
}
