import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/providers.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Hayal oluşturma / düzenleme
class DreamFormScreen extends ConsumerStatefulWidget {
  const DreamFormScreen({super.key, this.editId, this.categoryId});
  final int? editId;
  final int? categoryId;

  @override
  ConsumerState<DreamFormScreen> createState() => _DreamFormScreenState();
}

class _DreamFormScreenState extends ConsumerState<DreamFormScreen> {
  final _form = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _desc = TextEditingController();
  final _motivation = TextEditingController();
  final _notes = TextEditingController();
  final _budget = TextEditingController();
  final _stepCtrl = TextEditingController();
  final List<String> _steps = [];
  late int? _category = widget.categoryId;
  DateTime? _target;
  int _priority = 1;
  String? _cover;
  bool _hidden = false;
  bool _weighted = false;
  late String _currency = ref.read(settingsProvider).defaultCurrency;
  Dream? _editing;
  bool _loading = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    if (widget.editId != null) _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final repo = ref.read(dreamRepoProvider);
    final d = await repo.readable(await repo.getDream(widget.editId!));
    _editing = d;
    _title.text = d.title;
    _desc.text = d.description;
    _motivation.text = d.motivation;
    _notes.text = d.notes;
    _budget.text = d.budgetMinor == null ? '' : Money.toInput(d.budgetMinor!);
    _category = d.categoryId;
    _target = d.targetDate;
    _priority = d.priority;
    _cover = d.coverPath;
    _hidden = d.isHidden;
    _weighted = d.weightedProgress;
    _currency = d.currency;
    if (mounted) setState(() => _loading = false);
  }

  @override
  void dispose() {
    for (final c in [_title, _desc, _motivation, _notes, _budget, _stepCtrl]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickCover() async {
    final cam = await pickSourceSheet(context);
    if (cam == null) return;
    final p = await ref.read(mediaProvider).pickImage(camera: cam);
    if (p != null) setState(() => _cover = p);
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    final cats = ref.read(categoriesProvider).value ?? const [];
    final catId = _category ?? (cats.isNotEmpty ? cats.last.id : null);
    if (catId == null) return;
    final budget = _budget.text.trim().isEmpty ? null : Money.parseMinor(_budget.text);
    setState(() => _saving = true);
    final repo = ref.read(dreamRepoProvider);
    if (_editing == null) {
      final id = await repo.createDream(
        title: _title.text,
        categoryId: catId,
        description: _desc.text,
        coverPath: _cover,
        targetDate: _target,
        priority: _priority,
        motivation: _motivation.text,
        notes: _notes.text,
        budgetMinor: budget,
        currency: _currency,
        hidden: _hidden,
        weighted: _weighted,
        steps: _steps,
      );
      if (mounted) context.pushReplacement('/dream/$id');
    } else {
      if (_editing!.coverPath != null && _editing!.coverPath != _cover) {
        await ref.read(mediaProvider).delete(_editing!.coverPath);
      }
      await repo.updateDream(_editing!.copyWith(
        title: _title.text.trim(),
        description: _desc.text,
        motivation: _motivation.text,
        notes: _notes.text,
        categoryId: catId,
        targetDate: Value(_target),
        priority: _priority,
        coverPath: Value(_cover),
        budgetMinor: Value(budget),
        currency: _currency,
        isHidden: _hidden,
        weightedProgress: _weighted,
      ));
      if (mounted) context.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final cats = ref.watch(categoriesProvider).value ?? const [];
    final cs = Theme.of(context).colorScheme;
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    return Scaffold(
      appBar: AppBar(title: Text(_editing == null ? 'Yeni hayal' : 'Hayali düzenle')),
      body: Form(
        key: _form,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 120),
          children: [
            GestureDetector(
              onTap: _pickCover,
              child: ClipRRect(
                borderRadius: Radii.card,
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: _cover != null
                      ? Stack(fit: StackFit.expand, children: [
                          LocalImage(_cover, cacheWidth: 800),
                          Positioned(
                            right: 8,
                            top: 8,
                            child: IconButton.filledTonal(
                              tooltip: 'Kapağı kaldır',
                              onPressed: () => setState(() => _cover = null),
                              icon: const Icon(Icons.close),
                            ),
                          ),
                        ])
                      : Container(
                          color: cs.primary.withValues(alpha: 0.08),
                          child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                            Icon(Icons.add_photo_alternate_outlined, size: 40, color: cs.primary),
                            Gap.h8,
                            Text('Kapak fotoğrafı ekle', style: TextStyle(color: cs.primary, fontWeight: FontWeight.w600)),
                          ]),
                        ),
                ),
              ),
            ),
            Gap.h16,
            TextFormField(
              controller: _title,
              maxLength: 120,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(labelText: 'Hayalin *', hintText: 'Örn. Japonya\'ya gitmek'),
              validator: (v) => (v == null || v.trim().isEmpty) ? 'Hayalinin bir adı olmalı' : null,
            ),
            TextFormField(
              controller: _desc,
              maxLines: 3,
              maxLength: 1000,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(labelText: 'Açıklama'),
            ),
            const SectionHeader('Kategori'),
            Wrap(spacing: 8, runSpacing: 8, children: [
              for (final c in cats)
                ChoiceChip(
                  avatar: Icon(AppIcons.of(c.iconKey), size: 18, color: Color(c.color)),
                  label: Text(c.name),
                  selected: _category == c.id,
                  onSelected: (_) => setState(() => _category = c.id),
                ),
              ActionChip(avatar: const Icon(Icons.add, size: 18), label: const Text('Yeni kategori'), onPressed: () => context.push('/categories')),
            ]),
            const SectionHeader('Plan'),
            SoftCard(
              padding: EdgeInsets.zero,
              child: Column(children: [
                ListTile(
                  leading: const Icon(Icons.event),
                  title: const Text('Hedef tarihi'),
                  subtitle: Text(_target == null ? 'Seçilmedi' : Dates.long(_target!)),
                  trailing: _target == null ? const Icon(Icons.chevron_right) : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _target = null)),
                  onTap: () async {
                    final d = await pickDate(context, initial: _target ?? DateTime.now().add(const Duration(days: 180)), first: DateTime(2000));
                    if (d != null) setState(() => _target = d);
                  },
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                  child: Row(children: [
                    const Text('Öncelik'),
                    const Spacer(),
                    ChoiceChips<int>(values: const [0, 1, 2], labels: PriorityChip.labels, selected: _priority, onSelected: (v) => setState(() => _priority = v)),
                  ]),
                ),
              ]),
            ),
            Gap.h12,
            Row(children: [
              Expanded(
                child: TextFormField(
                  controller: _budget,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Bütçe (isteğe bağlı)'),
                  validator: (v) => (v == null || v.trim().isEmpty || Money.parseMinor(v) != null) ? null : 'Geçerli bir tutar girin',
                ),
              ),
              Gap.w8,
              DropdownButton<String>(
                value: _currency,
                items: [for (final c in Money.currencies.keys) DropdownMenuItem(value: c, child: Text(c))],
                onChanged: (v) => setState(() => _currency = v!),
              ),
            ]),
            if (_editing == null) ...[
              const SectionHeader('Yolculuk adımları'),
              Text('İlk adım "Hayalimi oluşturdum" otomatik eklenir.', style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
              Gap.h8,
              for (var i = 0; i < _steps.length; i++)
                ListTile(
                  dense: true,
                  leading: CircleAvatar(radius: 12, child: Text('${i + 2}', style: const TextStyle(fontSize: 11))),
                  title: Text(_steps[i]),
                  trailing: IconButton(icon: const Icon(Icons.close), onPressed: () => setState(() => _steps.removeAt(i))),
                ),
              TextField(
                controller: _stepCtrl,
                decoration: InputDecoration(
                  hintText: 'Örn. Seyahat bütçemi belirle',
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.add_circle),
                    onPressed: () {
                      if (_stepCtrl.text.trim().isEmpty) return;
                      setState(() => _steps.add(_stepCtrl.text.trim()));
                      _stepCtrl.clear();
                    },
                  ),
                ),
                onSubmitted: (v) {
                  if (v.trim().isEmpty) return;
                  setState(() => _steps.add(v.trim()));
                  _stepCtrl.clear();
                },
              ),
            ],
            const SectionHeader('Motivasyon ve notlar'),
            TextFormField(
              controller: _motivation,
              maxLength: 200,
              decoration: const InputDecoration(labelText: 'Motivasyon cümlen', hintText: 'Neden bu hayali istiyorsun?'),
            ),
            TextFormField(controller: _notes, maxLines: 4, decoration: const InputDecoration(labelText: 'Kişisel notlar')),
            Gap.h8,
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Ağırlıklı ilerleme'),
              subtitle: const Text('Adımlara farklı ağırlık verebilirsin'),
              value: _weighted,
              onChanged: (v) => setState(() => _weighted = v),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Gizli hayal'),
              subtitle: const Text('Listelerde görünmez; açıklama ve notlar şifrelenir'),
              value: _hidden,
              onChanged: (v) => setState(() => _hidden = v),
            ),
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.all(Gap.lg),
        child: FilledButton(onPressed: _saving ? null : _save, child: Text(_editing == null ? 'Hayalimi oluştur' : 'Kaydet')),
      ),
    );
  }
}
