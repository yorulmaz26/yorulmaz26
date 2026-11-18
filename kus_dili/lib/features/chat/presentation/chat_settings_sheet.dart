import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../notifications/application/push_providers.dart';
import '../../privacy/application/privacy_providers.dart';
import '../application/chat_providers.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';

/// Sohbete özel dil ayarları: gönderim biçimi, kuş dili türü (özel kural
/// dahil) ve gelen mesajların görünümü.
class ChatSettingsSheet extends ConsumerStatefulWidget {
  const ChatSettingsSheet({
    super.key,
    required this.chatId,
    required this.initial,
    required this.onSave,
  });

  final String chatId;
  final ChatSettings initial;
  final Future<void> Function(ChatSettings) onSave;

  @override
  ConsumerState<ChatSettingsSheet> createState() => _ChatSettingsSheetState();
}

class _ChatSettingsSheetState extends ConsumerState<ChatSettingsSheet> {
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
              Text('Gizlilik', style: text.titleSmall),
              const SizedBox(height: 4),
              DropdownButtonFormField<int>(
                key: const Key('disappearPicker'),
                initialValue: _s.disappearSeconds,
                decoration: const InputDecoration(
                  labelText: 'Süreli mesajlar (gönderdiklerim)',
                  prefixIcon: Icon(Icons.timer_outlined),
                ),
                items: [
                  for (final e in ChatSettings.disappearOptions.entries)
                    DropdownMenuItem(value: e.key, child: Text(e.value)),
                ],
                onChanged: (v) =>
                    setState(() => _s = _s.copyWith(disappearSeconds: v ?? 0)),
              ),
              _PrivacyToggles(chatId: widget.chatId),
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

/// Kilit ve gizleme anında uygulanır; yalnızca bu cihazda saklanır.
class _PrivacyToggles extends ConsumerWidget {
  const _PrivacyToggles({required this.chatId});

  final String chatId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final hasPin = ref.watch(hasPinProvider).value ?? false;
    final prefs = ref.watch(privacyPrefsProvider).value;
    final c = ref.read(privacyPrefsProvider.notifier);
    final push = ref.watch(pushPrefsProvider).value;
    final uid = ref.watch(myUidProvider);
    return Column(
      children: [
        SwitchListTile(
          key: const Key('muteChatSwitch'),
          contentPadding: EdgeInsets.zero,
          secondary: const Icon(Icons.notifications_off_outlined),
          title: const Text('Bildirimleri sessize al'),
          value: push?.isMuted(chatId) ?? false,
          onChanged: uid == null
              ? null
              : (v) =>
                    ref.read(pushRepositoryProvider).setMuted(uid, chatId, v),
        ),
        SwitchListTile(
          key: const Key('lockChatSwitch'),
          contentPadding: EdgeInsets.zero,
          secondary: const Icon(Icons.lock_outline),
          title: const Text('Sohbeti kilitle'),
          subtitle: const Text('Açmak için PIN / biyometri gerekir.'),
          value: prefs?.isLocked(chatId) ?? false,
          onChanged: hasPin ? (v) => c.setLocked(chatId, v) : null,
        ),
        SwitchListTile(
          key: const Key('hideChatSwitch'),
          contentPadding: EdgeInsets.zero,
          secondary: const Icon(Icons.visibility_off_outlined),
          title: const Text('Ana listeden gizle'),
          subtitle: const Text(
            'Profil → Gizlilik → Gizli sohbetler bölümünde görünür.',
          ),
          value: prefs?.isHidden(chatId) ?? false,
          onChanged: hasPin ? (v) => c.setHidden(chatId, v) : null,
        ),
        if (!hasPin)
          const Padding(
            padding: EdgeInsets.only(bottom: 8),
            child: Text(
              'Kilit ve gizleme için önce Profil → Gizlilik bölümünden PIN '
              'oluştur.',
            ),
          ),
      ],
    );
  }
}
