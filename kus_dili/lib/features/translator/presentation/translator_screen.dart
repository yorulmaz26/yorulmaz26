import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../application/translator_controller.dart';

/// Türkçe ↔ Kuş Dili çevirici. İnternet gerektirmez; mesajlaşmadan
/// bağımsız çalışır (öğrenme modülünün ilk parçası).
class TranslatorScreen extends ConsumerStatefulWidget {
  const TranslatorScreen({super.key});

  @override
  ConsumerState<TranslatorScreen> createState() => _TranslatorScreenState();
}

class _TranslatorScreenState extends ConsumerState<TranslatorScreen> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(translatorProvider);
    final output = ref.watch(translationOutputProvider);
    final notifier = ref.read(translatorProvider.notifier);
    final toKus = state.direction == TranslateDirection.toKusDili;

    return Scaffold(
      appBar: AppBar(title: const Text('Kuş Dili Çevirici')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            SegmentedButton<KusDiliVariant>(
              segments: [
                for (final v in KusDiliVariant.presets)
                  ButtonSegment(value: v, label: Text(v.name.split(' ').first)),
              ],
              selected: {state.variant},
              onSelectionChanged: (s) => notifier.setVariant(s.first),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: Text(
                    toKus ? 'Türkçe' : 'Kuş Dili',
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
                IconButton(
                  key: const Key('swap'),
                  tooltip: 'Yönü değiştir',
                  icon: const Icon(Icons.swap_vert),
                  onPressed: () {
                    notifier.swap(output.text);
                    _controller.text = output.text;
                  },
                ),
                Expanded(
                  child: Text(
                    toKus ? 'Kuş Dili' : 'Türkçe',
                    textAlign: TextAlign.end,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            TextField(
              key: const Key('input'),
              controller: _controller,
              minLines: 3,
              maxLines: 8,
              onChanged: notifier.setInput,
              decoration: InputDecoration(
                hintText: toKus
                    ? 'Türkçe yazın…'
                    : 'Kuş dilinde metin yapıştırın…',
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(
                          Icons.flutter_dash,
                          size: 18,
                          color: AppColors.gold,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            toKus ? 'Kuş dili' : 'Türkçe',
                            style: Theme.of(context).textTheme.labelLarge,
                          ),
                        ),
                        IconButton(
                          tooltip: 'Kopyala',
                          icon: const Icon(Icons.copy, size: 18),
                          onPressed: output.text.isEmpty
                              ? null
                              : () => Clipboard.setData(
                                  ClipboardData(text: output.text),
                                ),
                        ),
                      ],
                    ),
                    SelectableText(
                      output.text,
                      key: const Key('output'),
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                    if (output.decode case final r?
                        when state.input.isNotEmpty && !r.isValid)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Text(
                          'Uyarı: metnin %${(r.confidence * 100).round()} '
                          'kadarı ${state.variant.name} kuralına uyuyor; '
                          'uymayan kısımlar olduğu gibi bırakıldı.',
                          style: const TextStyle(color: AppColors.gold),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
