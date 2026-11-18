import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';

/// Sohbete özel dil ayarları: gönderim biçimi, kuş dili türü (özel kural
/// dahil) ve gelen mesajların görünümü.
class ChatSettingsSheet extends StatefulWidget {
  const ChatSettingsSheet({
    super.key,
    required this.initial,
    required this.onSave,
  });

  final ChatSettings initial;
  final Future<void> Function(ChatSettings) onSave;

  @override
  State<ChatSettingsSheet> createState() => _ChatSettingsSheetState();
}

class _ChatSettingsSheetState extends State<ChatSettingsSheet> {
  late ChatSettings _s = widget.initial;
  late final _template = TextEditingController(
    text: widget.initial.variant.isPreset
        ? 'z{v}'
        : widget.initial.variant.template,
  );
  late bool _custom = !widget.initial.variant.isPreset;
  String? _templateError;
  bool _saving = false;

  @override
  void dispose() {
    _template.dispose();
    super.dispose();
  }

  KusDiliVariant? _customVariant() {
    try {
      final v = KusDiliVariant.custom(
        id: 'ozel',
        name: 'Özel kuş dili',
        template: _template.text,
      );
      _templateError = null;
      return v;
    } on KusDiliRuleException catch (e) {
      _templateError = e.message;
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final variant = _custom ? _customVariant() : _s.variant;
    final preview = variant == null
        ? '—'
        : KusDiliCodec(variant).encode('Seni seviyorum');

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          8,
          20,
          20 + MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Sohbet dili', style: text.titleLarge),
              const SizedBox(height: 16),
              Text('Gönderdiğim mesajlar', style: text.titleSmall),
              const SizedBox(height: 8),
              SegmentedButton<DisplayMode>(
                segments: const [
                  ButtonSegment(
                    value: DisplayMode.plain,
                    label: Text('Normal'),
                    icon: Icon(Icons.chat_bubble_outline),
                  ),
                  ButtonSegment(
                    value: DisplayMode.kusDili,
                    label: Text('Kuş dili'),
                    icon: Icon(Icons.flutter_dash),
                  ),
                ],
                selected: {_s.sendMode},
                onSelectionChanged: (v) =>
                    setState(() => _s = _s.copyWith(sendMode: v.first)),
              ),
              if (_s.sendMode == DisplayMode.kusDili) ...[
                const SizedBox(height: 16),
                Text('Kuş dili türü', style: text.titleSmall),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final p in KusDiliVariant.presets)
                      ChoiceChip(
                        label: Text(p.name.split(' ').first),
                        selected: !_custom && _s.variant == p,
                        onSelected: (_) => setState(() {
                          _custom = false;
                          _s = _s.copyWith(variant: p);
                        }),
                      ),
                    ChoiceChip(
                      key: const Key('customVariant'),
                      label: const Text('Özel'),
                      selected: _custom,
                      onSelected: (_) => setState(() => _custom = true),
                    ),
                  ],
                ),
                if (_custom) ...[
                  const SizedBox(height: 8),
                  TextField(
                    key: const Key('template'),
                    controller: _template,
                    decoration: InputDecoration(
                      labelText: 'Kural şablonu',
                      helperText: '{v} = sesli harf. Örnek: z{v} → a → aza',
                      errorText: _templateError,
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                ],
                const SizedBox(height: 8),
                Text(
                  'Önizleme: $preview',
                  style: text.bodyMedium?.copyWith(color: AppColors.gold),
                ),
              ],
              const SizedBox(height: 20),
              Text('Gelen kuş dili mesajları', style: text.titleSmall),
              RadioGroup<ViewMode>(
                groupValue: _s.viewMode,
                onChanged: (v) => setState(() => _s = _s.copyWith(viewMode: v)),
                child: const Column(
                  children: [
                    RadioListTile(
                      value: ViewMode.asSent,
                      title: Text('Kuş dilinde göster, dokununca çöz'),
                    ),
                    RadioListTile(
                      value: ViewMode.both,
                      title: Text('Kuş dili ve altında Türkçesi'),
                    ),
                    RadioListTile(
                      value: ViewMode.original,
                      title: Text('Her zaman Türkçe göster'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  key: const Key('saveSettings'),
                  onPressed: _saving || (_custom && variant == null)
                      ? null
                      : () async {
                          setState(() => _saving = true);
                          final s = _custom
                              ? _s.copyWith(variant: variant)
                              : _s;
                          await widget.onSave(s);
                          if (context.mounted) Navigator.of(context).pop();
                        },
                  child: const Text('Kaydet'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
