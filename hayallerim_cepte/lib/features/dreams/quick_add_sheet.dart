import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_spacing.dart';
import '../../core/utils/app_icons.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Ana sayfadaki "Hızlı hayal ekle" — başlık + kategori ile saniyeler içinde.
Future<void> showQuickAddDream(BuildContext context) => showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => const _QuickAdd(),
    );

class _QuickAdd extends ConsumerStatefulWidget {
  const _QuickAdd();
  @override
  ConsumerState<_QuickAdd> createState() => _QuickAddState();
}

class _QuickAddState extends ConsumerState<_QuickAdd> {
  final _title = TextEditingController();
  int? _cat;

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final t = _title.text.trim();
    final cats = ref.read(categoriesProvider).value ?? const [];
    if (t.isEmpty || cats.isEmpty) return;
    final id = await ref.read(dreamRepoProvider).createDream(title: t, categoryId: _cat ?? cats.last.id);
    if (!mounted) return;
    Navigator.pop(context);
    toast(context, 'Hayalin bahçene tohum olarak ekildi 🌱');
    context.push('/dream/$id');
  }

  @override
  Widget build(BuildContext context) {
    final cats = ref.watch(categoriesProvider).value ?? const [];
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Hızlı hayal ekle', style: Theme.of(context).textTheme.titleLarge),
          Gap.h16,
          TextField(
            controller: _title,
            autofocus: true,
            maxLength: 120,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(hintText: 'Örn. Japonya\'ya gitmek'),
            onSubmitted: (_) => _save(),
          ),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                for (final c in cats)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      avatar: Icon(AppIcons.of(c.iconKey), size: 18, color: Color(c.color)),
                      label: Text(c.name),
                      selected: _cat == c.id,
                      onSelected: (_) => setState(() => _cat = c.id),
                    ),
                  ),
              ],
            ),
          ),
          Gap.h16,
          Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () {
                  Navigator.pop(context);
                  context.push('/dream/new${_cat == null ? '' : '?category=$_cat'}');
                },
                child: const Text('Ayrıntılı'),
              ),
            ),
            Gap.w12,
            Expanded(child: FilledButton(onPressed: _save, child: const Text('Ekle'))),
          ]),
        ],
      ),
    );
  }
}
