import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../garden/plant_painter.dart';

class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cats = ref.watch(categoriesProvider).value ?? const [];
    return Scaffold(
      appBar: AppBar(title: const Text('Kategoriler')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => const _NewCategory()),
        icon: const Icon(Icons.add),
        label: const Text('Kategori ekle'),
      ),
      body: ListView.builder(
        padding: const EdgeInsets.only(bottom: 100),
        itemCount: cats.length,
        itemBuilder: (_, i) {
          final c = cats[i];
          return ListTile(
            leading: CircleAvatar(backgroundColor: Color(c.color).withValues(alpha: 0.15), child: Icon(AppIcons.of(c.iconKey), color: Color(c.color))),
            title: Text(c.name),
            subtitle: Text('Bahçede: ${kPlantNames[c.plantType] ?? c.plantType}${c.isBuiltIn ? ' · Varsayılan' : ''}'),
            trailing: c.isBuiltIn
                ? null
                : IconButton(
                    tooltip: 'Sil',
                    icon: const Icon(Icons.delete_outline, color: AppColors.danger),
                    onPressed: () async {
                      final ok = await confirm(context,
                          title: '"${c.name}" silinsin mi?', message: 'Bu kategorideki hayaller "Diğer" kategorisine taşınır.', ok: 'Sil', destructive: true);
                      if (ok) await ref.read(dreamRepoProvider).deleteCategory(c);
                    },
                  ),
          );
        },
      ),
    );
  }
}

class _NewCategory extends ConsumerStatefulWidget {
  const _NewCategory();
  @override
  ConsumerState<_NewCategory> createState() => _NewCategoryState();
}

class _NewCategoryState extends ConsumerState<_NewCategory> {
  final _name = TextEditingController();
  String _icon = 'star';
  int _color = 0xFF6D5DFB;
  String _plant = 'oak';

  static const _colors = [0xFF6D5DFB, 0xFF20B8A6, 0xFFD6AD60, 0xFFEF6C8F, 0xFF3B82F6, 0xFFF97316, 0xFF2EAD6B, 0xFF8B5CF6, 0xFF64748B];

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Text('Yeni kategori', style: Theme.of(context).textTheme.titleLarge),
          Gap.h12,
          TextField(controller: _name, autofocus: true, maxLength: 40, decoration: const InputDecoration(labelText: 'Kategori adı')),
          const Text('İkon'),
          Gap.h8,
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in AppIcons.map.entries)
              ChoiceChip(label: Icon(e.value, size: 18), selected: _icon == e.key, onSelected: (_) => setState(() => _icon = e.key)),
          ]),
          Gap.h12,
          const Text('Renk'),
          Gap.h8,
          Wrap(spacing: 10, children: [
            for (final c in _colors)
              GestureDetector(
                onTap: () => setState(() => _color = c),
                child: CircleAvatar(
                  radius: 16,
                  backgroundColor: Color(c),
                  child: _color == c ? const Icon(Icons.check, size: 16, color: Colors.white) : null,
                ),
              ),
          ]),
          Gap.h12,
          const Text('Bahçedeki bitki türü'),
          Gap.h8,
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final p in kPlantTypes) ChoiceChip(label: Text(kPlantNames[p]!), selected: _plant == p, onSelected: (_) => setState(() => _plant = p)),
          ]),
          Gap.h16,
          FilledButton(
            onPressed: () async {
              if (_name.text.trim().isEmpty) return;
              await ref.read(dreamRepoProvider).addCategory(_name.text, _icon, _color, _plant);
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('Kaydet'),
          ),
        ]),
      ),
    );
  }
}
