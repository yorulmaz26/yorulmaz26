import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/database/app_database.dart';
import '../../core/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/widgets/capture.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Hayal Panom — Pinterest tarzı görsel pano.
class BoardScreen extends ConsumerStatefulWidget {
  const BoardScreen({super.key});
  @override
  ConsumerState<BoardScreen> createState() => _BoardScreenState();
}

class _BoardScreenState extends ConsumerState<BoardScreen> {
  final _boardKey = GlobalKey();
  String? _category;
  bool _reorder = false;

  Future<void> _export() async {
    final png = await capturePng(_boardKey, pixelRatio: 2);
    if (png == null) return;
    try {
      await ref.read(mediaProvider).saveToGallery(png, name: 'hayal_panom');
      if (mounted) toast(context, 'Panon galeriye kaydedildi 📌');
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  Future<void> _add() async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(leading: const Icon(Icons.photo_library_outlined), title: const Text('Fotoğraf ekle'), onTap: () => Navigator.pop(c, 'photo')),
          ListTile(leading: const Icon(Icons.format_quote), title: const Text('Motivasyon yazısı ekle'), onTap: () => Navigator.pop(c, 'quote')),
          ListTile(leading: const Icon(Icons.grid_view), title: const Text('Kolaj hazırla'), onTap: () => Navigator.pop(c, 'collage')),
        ]),
      ),
    );
    if (!mounted || choice == null) return;
    final cat = _category ?? 'Genel';
    switch (choice) {
      case 'photo':
        final paths = await ref.read(mediaProvider).pickMultipleImages();
        if (paths.isEmpty) return;
        final c = await _askCategory(cat);
        await ref.read(boardRepoProvider).addImages(paths, c ?? cat);
      case 'quote':
        final t = await promptText(context, title: 'Motivasyon yazısı', hint: 'Örn. Her gün bir adım!', maxLength: 140, maxLines: 3);
        if (t == null) return;
        await ref.read(boardRepoProvider).addQuote(t, cat, AppColors.accents[DateTime.now().second % AppColors.accents.length].toARGB32());
      case 'collage':
        final items = (ref.read(boardProvider).value ?? const <DreamMediaItem>[]).where((m) => m.kind == 'image').toList();
        if (items.length < 2) {
          toast(context, 'Kolaj için panonda en az 2 fotoğraf olmalı');
          return;
        }
        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _CollageScreen(items: items)));
    }
  }

  Future<String?> _askCategory(String initial) => promptText(context, title: 'Pano kategorisi', initial: initial, maxLength: 30);

  Future<void> _options(DreamMediaItem m) async {
    final v = await showModalBottomSheet<String>(
      context: context,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(leading: const Icon(Icons.edit), title: Text(m.kind == 'quote' ? 'Yazıyı düzenle' : 'Açıklama ekle'), onTap: () => Navigator.pop(c, 'caption')),
          ListTile(leading: const Icon(Icons.label_outline), title: const Text('Kategoriyi değiştir'), onTap: () => Navigator.pop(c, 'category')),
          ListTile(leading: const Icon(Icons.delete_outline, color: AppColors.danger), title: const Text('Panodan kaldır'), onTap: () => Navigator.pop(c, 'remove')),
        ]),
      ),
    );
    if (!mounted || v == null) return;
    final repo = ref.read(boardRepoProvider);
    switch (v) {
      case 'caption':
        final t = await promptText(context, title: 'Metin', initial: m.caption, maxLength: 140, maxLines: 3);
        if (t != null) await repo.update(m.copyWith(caption: t));
      case 'category':
        final t = await _askCategory(m.boardCategory);
        if (t != null) await repo.update(m.copyWith(boardCategory: t));
      case 'remove':
        await repo.remove(m);
    }
  }

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(boardProvider).value ?? const <DreamMediaItem>[];
    final cats = {for (final m in all) m.boardCategory}.toList()..sort();
    final items = _category == null ? all : all.where((m) => m.boardCategory == _category).toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hayal Panom'),
        actions: [
          if (all.isNotEmpty) ...[
            IconButton(
              tooltip: _reorder ? 'Bitti' : 'Sırala',
              icon: Icon(_reorder ? Icons.check : Icons.swap_vert),
              onPressed: () => setState(() => _reorder = !_reorder),
            ),
            IconButton(tooltip: 'Panoyu galeriye kaydet', icon: const Icon(Icons.download), onPressed: _export),
          ],
        ],
      ),
      floatingActionButton: FloatingActionButton(onPressed: _add, tooltip: 'Panoya ekle', child: const Icon(Icons.add)),
      body: Column(children: [
        if (cats.length > 1)
          SizedBox(
            height: 44,
            child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: Gap.lg), children: [
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(label: const Text('Tümü'), selected: _category == null, onSelected: (_) => setState(() => _category = null)),
              ),
              for (final c in cats)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(label: Text(c), selected: _category == c, onSelected: (_) => setState(() => _category = c)),
                ),
            ]),
          ),
        Expanded(
          child: items.isEmpty
              ? EmptyState(
                  icon: Icons.dashboard_customize_outlined,
                  title: 'Panon boş',
                  message: 'Hayallerini hatırlatan fotoğrafları ve sözleri ekle. Her şey yalnızca bu cihazda saklanır.',
                  actionLabel: 'Ekle',
                  onAction: _add,
                )
              : _reorder
                  ? ReorderableListView.builder(
                      padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
                      itemCount: items.length,
                      onReorderItem: (o, n) {
                        final list = [...items];
                        list.insert(n, list.removeAt(o));
                        ref.read(boardRepoProvider).reorder(list);
                      },
                      itemBuilder: (_, i) => ListTile(
                        key: ValueKey(items[i].id),
                        leading: SizedBox(
                          width: 56,
                          height: 56,
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: items[i].kind == 'quote'
                                ? Container(color: Color(items[i].color ?? AppColors.purple.toARGB32()), child: const Icon(Icons.format_quote, color: Colors.white))
                                : LocalImage(items[i].path, cacheWidth: 112),
                          ),
                        ),
                        title: Text(items[i].caption.isEmpty ? items[i].boardCategory : items[i].caption, maxLines: 1, overflow: TextOverflow.ellipsis),
                        trailing: const Icon(Icons.drag_handle),
                      ),
                    )
                  : SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(Gap.lg, Gap.sm, Gap.lg, 100),
                      child: RepaintBoundary(
                        key: _boardKey,
                        child: Container(
                          color: Theme.of(context).scaffoldBackgroundColor,
                          child: LayoutBuilder(builder: (context, c) {
                            final cols = c.maxWidth > 700 ? 4 : (c.maxWidth > 480 ? 3 : 2);
                            final columns = List.generate(cols, (_) => <(int, DreamMediaItem)>[]);
                            for (var i = 0; i < items.length; i++) {
                              columns[i % cols].add((i, items[i]));
                            }
                            return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              for (var ci = 0; ci < cols; ci++) ...[
                                if (ci > 0) Gap.w8,
                                Expanded(
                                  child: Column(children: [
                                    for (final (i, m) in columns[ci])
                                      Padding(
                                        padding: const EdgeInsets.only(bottom: 8),
                                        child: GestureDetector(onLongPress: () => _options(m), onTap: () => _options(m), child: _Tile(m, i)),
                                      ),
                                  ]),
                                ),
                              ],
                            ]);
                          }),
                        ),
                      ),
                    ),
        ),
      ]),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile(this.m, this.index);
  final DreamMediaItem m;
  final int index;

  @override
  Widget build(BuildContext context) {
    if (m.kind == 'quote') {
      final c = Color(m.color ?? AppColors.purple.toARGB32());
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          gradient: LinearGradient(colors: [c, c.withValues(alpha: 0.7)], begin: Alignment.topLeft, end: Alignment.bottomRight),
          borderRadius: BorderRadius.circular(Radii.md),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Icon(Icons.format_quote_rounded, color: Colors.white70),
          Text(m.caption, style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700, height: 1.3)),
        ]),
      );
    }
    final ratios = [0.8, 1.0, 1.3, 0.9];
    return ClipRRect(
      borderRadius: BorderRadius.circular(Radii.md),
      child: Stack(children: [
        AspectRatio(aspectRatio: ratios[index % ratios.length], child: LocalImage(m.path, cacheWidth: 420)),
        if (m.caption.isNotEmpty)
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.all(8),
              color: Colors.black45,
              child: Text(m.caption, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white, fontSize: 12)),
            ),
          ),
      ]),
    );
  }
}

/// Kolaj: seçilen görsellerden ızgara kolaj + başlık; galeriye kaydet/paylaş.
class _CollageScreen extends ConsumerStatefulWidget {
  const _CollageScreen({required this.items});
  final List<DreamMediaItem> items;
  @override
  ConsumerState<_CollageScreen> createState() => _CollageScreenState();
}

class _CollageScreenState extends ConsumerState<_CollageScreen> {
  final _key = GlobalKey();
  final _selected = <int>[];
  final _title = TextEditingController(text: 'Hayallerim');

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _save({bool share = false}) async {
    final png = await capturePng(_key);
    if (png == null) return;
    try {
      if (share) {
        final f = await ref.read(mediaProvider).writeTemp(png, 'kolaj.png');
        await SharePlus.instance.share(ShareParams(files: [XFile(f.path)]));
      } else {
        await ref.read(mediaProvider).saveToGallery(png, name: 'kolaj');
        if (mounted) toast(context, 'Kolaj galeriye kaydedildi');
      }
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final picked = [for (final i in _selected) widget.items[i]];
    final cols = picked.length <= 2 ? picked.length.clamp(1, 2) : (picked.length <= 4 ? 2 : 3);
    return Scaffold(
      appBar: AppBar(title: const Text('Kolaj hazırla'), actions: [
        IconButton(tooltip: 'Paylaş', onPressed: picked.length < 2 ? null : () => _save(share: true), icon: const Icon(Icons.share)),
        IconButton(tooltip: 'Kaydet', onPressed: picked.length < 2 ? null : _save, icon: const Icon(Icons.download)),
      ]),
      body: ListView(padding: const EdgeInsets.all(Gap.lg), children: [
        TextField(controller: _title, maxLength: 40, decoration: const InputDecoration(labelText: 'Kolaj başlığı'), onChanged: (_) => setState(() {})),
        if (picked.length >= 2)
          RepaintBoundary(
            key: _key,
            child: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(gradient: AppColors.nightGradient, borderRadius: BorderRadius.circular(20)),
              child: Column(children: [
                Text(_title.text, style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                Gap.h8,
                GridView.count(
                  crossAxisCount: cols,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: 6,
                  crossAxisSpacing: 6,
                  children: [for (final m in picked) ClipRRect(borderRadius: BorderRadius.circular(10), child: LocalImage(m.path, cacheWidth: 500))],
                ),
                Gap.h8,
                const Text('Hayal Et, Planla, Gerçekleştir.', style: TextStyle(color: AppColors.gold, fontSize: 12)),
              ]),
            ),
          )
        else
          const Padding(padding: EdgeInsets.all(16), child: Text('Aşağıdan 2–9 fotoğraf seç.', textAlign: TextAlign.center)),
        const SectionHeader('Fotoğraflar'),
        GridView.count(
          crossAxisCount: 4,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 4,
          crossAxisSpacing: 4,
          children: [
            for (var i = 0; i < widget.items.length; i++)
              GestureDetector(
                onTap: () => setState(() {
                  if (_selected.contains(i)) {
                    _selected.remove(i);
                  } else if (_selected.length < 9) {
                    _selected.add(i);
                  }
                }),
                child: Stack(fit: StackFit.expand, children: [
                  ClipRRect(borderRadius: BorderRadius.circular(8), child: LocalImage(widget.items[i].path, cacheWidth: 160)),
                  if (_selected.contains(i))
                    Container(
                      decoration: BoxDecoration(color: Colors.black38, borderRadius: BorderRadius.circular(8)),
                      child: Center(
                        child: CircleAvatar(radius: 12, child: Text('${_selected.indexOf(i) + 1}', style: const TextStyle(fontSize: 12))),
                      ),
                    ),
                ]),
              ),
          ],
        ),
      ]),
    );
  }
}
