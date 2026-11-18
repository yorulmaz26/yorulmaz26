import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/providers.dart';
import '../../core/router/app_router.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../settings/notification_helpers.dart';

/// İlk kurulum: isim, profil görseli, tema, motivasyon bildirimi ve
/// odak kategorileri. Hepsi isteğe bağlıdır; üyelik/internet yoktur.
class SetupScreen extends ConsumerStatefulWidget {
  const SetupScreen({super.key});

  @override
  ConsumerState<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends ConsumerState<SetupScreen> {
  final _name = TextEditingController();
  String? _avatar;
  ThemeMode _theme = ThemeMode.system;
  bool _motivation = true;
  int _minutes = 9 * 60;
  final Set<int> _cats = {};
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _pickAvatar() async {
    final cam = await pickSourceSheet(context);
    if (cam == null) return;
    final p = await ref.read(mediaProvider).pickImage(camera: cam);
    if (p != null) setState(() => _avatar = p);
  }

  Future<void> _finish() async {
    setState(() => _saving = true);
    await ref.read(profileRepoProvider).save(name: _name.text, avatarPath: _avatar, focusCategories: _cats.join(','));
    await ref.read(settingsProvider.notifier).update((s) => s.copyWith(
          themeMode: _theme,
          motivationEnabled: _motivation,
          motivationMinutes: _minutes,
        ));
    if (_motivation) {
      await enableNotifications(ref);
    }
    await ref.read(prefsProvider).setBool(kOnboardingDoneKey, true);
    if (mounted) context.go('/home');
  }

  @override
  Widget build(BuildContext context) {
    final cats = ref.watch(categoriesProvider).value ?? const [];
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Seni tanıyalım')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 120),
        children: [
          Text('Bu bilgiler yalnızca bu cihazda saklanır. Hepsi isteğe bağlıdır.', style: TextStyle(color: cs.onSurfaceVariant)),
          Gap.h24,
          Center(
            child: GestureDetector(
              onTap: _pickAvatar,
              child: Stack(children: [
                Avatar(path: _avatar, name: _name.text, size: 96),
                Positioned(
                  right: 0,
                  bottom: 0,
                  child: CircleAvatar(radius: 16, backgroundColor: cs.primary, child: const Icon(Icons.camera_alt, size: 16, color: Colors.white)),
                ),
              ]),
            ),
          ),
          Gap.h24,
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            maxLength: 40,
            decoration: const InputDecoration(labelText: 'Adın (isteğe bağlı)', prefixIcon: Icon(Icons.person_outline)),
            onChanged: (_) => setState(() {}),
          ),
          const SectionHeader('Tema'),
          SegmentedButton<ThemeMode>(
            segments: const [
              ButtonSegment(value: ThemeMode.system, label: Text('Sistem'), icon: Icon(Icons.brightness_auto)),
              ButtonSegment(value: ThemeMode.light, label: Text('Açık'), icon: Icon(Icons.light_mode)),
              ButtonSegment(value: ThemeMode.dark, label: Text('Koyu'), icon: Icon(Icons.dark_mode)),
            ],
            selected: {_theme},
            onSelectionChanged: (v) {
              setState(() => _theme = v.first);
              ref.read(settingsProvider.notifier).update((s) => s.copyWith(themeMode: v.first));
            },
          ),
          const SectionHeader('Günlük motivasyon'),
          SoftCard(
            padding: EdgeInsets.zero,
            child: Column(children: [
              SwitchListTile(
                value: _motivation,
                title: const Text('Her gün bir motivasyon sözü'),
                subtitle: const Text('Yerel bildirim — internet gerekmez'),
                onChanged: (v) => setState(() => _motivation = v),
              ),
              if (_motivation)
                ListTile(
                  leading: const Icon(Icons.schedule),
                  title: const Text('Bildirim saati'),
                  trailing: Text(Dates.minutesToHm(_minutes), style: const TextStyle(fontWeight: FontWeight.w700)),
                  onTap: () async {
                    final t = await pickTime(context, initial: TimeOfDay(hour: _minutes ~/ 60, minute: _minutes % 60));
                    if (t != null) setState(() => _minutes = t.hour * 60 + t.minute);
                  },
                ),
            ]),
          ),
          const SectionHeader('Odaklanmak istediğin alanlar'),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final c in cats)
                FilterChip(
                  avatar: Icon(AppIcons.of(c.iconKey), size: 18, color: Color(c.color)),
                  label: Text(c.name),
                  selected: _cats.contains(c.id),
                  onSelected: (v) => setState(() => v ? _cats.add(c.id) : _cats.remove(c.id)),
                ),
            ],
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.all(Gap.lg),
        child: FilledButton(
          onPressed: _saving ? null : _finish,
          child: _saving ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2)) : const Text('Hayal yolculuğuna başla'),
        ),
      ),
    );
  }
}
