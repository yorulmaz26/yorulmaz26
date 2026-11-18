import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/providers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/audio.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../security/lock_controller.dart';
import '../security/lock_screen.dart';
import 'journal_screen.dart';

final _entryProvider = StreamProvider.family<JournalEntry?, int>((ref, id) => ref.watch(journalRepoProvider).watchEntry(id));
final _entryMediaProvider = StreamProvider.family<List<JournalMediaItem>, int>((ref, id) => ref.watch(journalRepoProvider).watchEntryMedia(id));

class JournalEditorScreen extends ConsumerStatefulWidget {
  const JournalEditorScreen({super.key, this.id});
  final int? id;
  @override
  ConsumerState<JournalEditorScreen> createState() => _JournalEditorScreenState();
}

class _JournalEditorScreenState extends ConsumerState<JournalEditorScreen> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  final _tag = TextEditingController();
  DateTime _date = DateTime.now();
  int _mood = 4;
  final List<String> _tags = [];
  bool _important = false;
  bool _private = false;
  final List<(String, String, int?)> _newMedia = [];
  bool _loaded = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    if (widget.id == null) _loaded = true;
  }

  Future<void> _load(JournalEntry e) async {
    _loaded = true;
    _title.text = e.title;
    _body.text = await ref.read(journalRepoProvider).readableBody(e);
    _date = e.date;
    _mood = e.mood;
    _tags
      ..clear()
      ..addAll(e.tags.split(',').where((t) => t.isNotEmpty));
    _important = e.isImportant;
    _private = e.isPrivate;
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    _tag.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_body.text.trim().isEmpty && _title.text.trim().isEmpty && _newMedia.isEmpty) {
      toast(context, 'Boş günlük kaydedilemez');
      return;
    }
    setState(() => _saving = true);
    await ref.read(journalRepoProvider).saveEntry(
          id: widget.id,
          date: _date,
          title: _title.text,
          body: _body.text,
          mood: _mood,
          tags: _tags,
          important: _important,
          private: _private,
          newMedia: _newMedia,
        );
    _newMedia.clear();
    if (mounted) context.pop();
  }

  Future<void> _discardNewMedia() async {
    for (final m in _newMedia) {
      await ref.read(mediaProvider).delete(m.$2);
    }
  }

  @override
  Widget build(BuildContext context) {
    JournalEntry? entry;
    if (widget.id != null) {
      entry = ref.watch(_entryProvider(widget.id!)).value;
      if (entry != null && entry.isPrivate && !ref.watch(lockProvider).privateUnlocked) {
        return Scaffold(
          appBar: AppBar(),
          body: EmptyState(
            icon: Icons.lock_outline,
            title: 'Özel günlük',
            actionLabel: 'Kilidi aç',
            onAction: () => requestPrivateAccess(context, ref),
          ),
        );
      }
      if (entry != null && !_loaded) _load(entry);
    }
    final media = widget.id == null ? const <JournalMediaItem>[] : (ref.watch(_entryMediaProvider(widget.id!)).value ?? const []);
    return PopScope(
      onPopInvokedWithResult: (didPop, _) {
        if (didPop && _newMedia.isNotEmpty) _discardNewMedia();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.id == null ? 'Yeni günlük' : 'Günlük'),
          actions: [
            if (entry != null)
              IconButton(
                tooltip: 'Sil',
                icon: const Icon(Icons.delete_outline),
                onPressed: () async {
                  if (await confirm(context, title: 'Günlük silinsin mi?', ok: 'Sil', destructive: true)) {
                    await ref.read(journalRepoProvider).deleteEntry(entry!);
                    if (context.mounted) context.pop();
                  }
                },
              ),
            TextButton(onPressed: _saving ? null : _save, child: const Text('Kaydet')),
          ],
        ),
        body: !_loaded
            ? const Center(child: CircularProgressIndicator())
            : ListView(
                padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 60),
                children: [
                  ActionChip(
                    avatar: const Icon(Icons.event, size: 18),
                    label: Text(Dates.longWithDay(_date)),
                    onPressed: () async {
                      final d = await pickDate(context, initial: _date, last: DateTime.now());
                      if (d != null) setState(() => _date = d);
                    },
                  ),
                  Gap.h12,
                  const Text('Bugün nasıl hissediyorsun?', style: TextStyle(fontWeight: FontWeight.w600)),
                  Gap.h8,
                  Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
                    for (var i = 1; i <= 5; i++)
                      Semantics(
                        button: true,
                        selected: _mood == i,
                        label: moodLabels[i - 1],
                        child: GestureDetector(
                          onTap: () => setState(() => _mood = i),
                          child: AnimatedScale(
                            scale: _mood == i ? 1.3 : 1,
                            duration: const Duration(milliseconds: 200),
                            child: Opacity(opacity: _mood == i ? 1 : 0.45, child: Text(moods[i - 1], style: const TextStyle(fontSize: 34))),
                          ),
                        ),
                      ),
                  ]),
                  Gap.h16,
                  TextField(
                    controller: _title,
                    maxLength: 120,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(hintText: 'Başlık'),
                  ),
                  TextField(
                    controller: _body,
                    minLines: 8,
                    maxLines: null,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(hintText: 'Bugün neler oldu? Neye şükrediyorsun? Hayallerine bir adım attın mı?'),
                  ),
                  Gap.h12,
                  Wrap(spacing: 6, runSpacing: 6, children: [
                    for (final t in _tags) InputChip(label: Text('#$t'), onDeleted: () => setState(() => _tags.remove(t))),
                  ]),
                  TextField(
                    controller: _tag,
                    decoration: InputDecoration(
                      hintText: 'Etiket ekle (ör. seyahat)',
                      prefixIcon: const Icon(Icons.tag),
                      suffixIcon: IconButton(icon: const Icon(Icons.add), onPressed: () => _addTag(_tag.text)),
                    ),
                    onSubmitted: _addTag,
                  ),
                  Gap.h12,
                  Row(children: [
                    OutlinedButton.icon(
                      onPressed: () async {
                        final cam = await pickSourceSheet(context);
                        if (cam == null) return;
                        final p = await ref.read(mediaProvider).pickImage(camera: cam);
                        if (p != null) setState(() => _newMedia.add(('image', p, null)));
                      },
                      icon: const Icon(Icons.photo_camera_outlined),
                      label: const Text('Fotoğraf'),
                    ),
                    Gap.w8,
                    OutlinedButton.icon(
                      onPressed: () async {
                        final r = await recordAudio(context);
                        if (r != null) setState(() => _newMedia.add(('audio', r.$1, r.$2)));
                      },
                      icon: const Icon(Icons.mic_none),
                      label: const Text('Ses kaydı'),
                    ),
                  ]),
                  Gap.h12,
                  _MediaList(
                    saved: media,
                    pending: _newMedia,
                    onRemovePending: (m) async {
                      await ref.read(mediaProvider).delete(m.$2);
                      setState(() => _newMedia.remove(m));
                    },
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    secondary: const Icon(Icons.star_outline, color: Colors.amber),
                    title: const Text('Önemli an'),
                    value: _important,
                    onChanged: (v) => setState(() => _important = v),
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    secondary: const Icon(Icons.lock_outline, color: AppColors.purple),
                    title: const Text('Özel günlük'),
                    subtitle: const Text('Metin şifrelenir, görüntülemek için PIN gerekir'),
                    value: _private,
                    onChanged: (v) async {
                      if (v && !await ref.read(lockServiceProvider).hasPin()) {
                        if (context.mounted) toast(context, 'Önce Profil › Güvenlik bölümünden PIN oluştur');
                        return;
                      }
                      setState(() => _private = v);
                    },
                  ),
                ],
              ),
      ),
    );
  }

  void _addTag(String v) {
    final t = v.trim().replaceAll(',', ' ').replaceAll('#', '');
    if (t.isEmpty || _tags.contains(t)) return;
    setState(() => _tags.add(t));
    _tag.clear();
  }
}

class _MediaList extends ConsumerWidget {
  const _MediaList({required this.saved, required this.pending, required this.onRemovePending});
  final List<JournalMediaItem> saved;
  final List<(String, String, int?)> pending;
  final ValueChanged<(String, String, int?)> onRemovePending;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final images = [
      for (final m in saved.where((m) => m.kind == 'image')) (m.path, () => ref.read(journalRepoProvider).deleteMedia(m)),
      for (final m in pending.where((m) => m.$1 == 'image')) (m.$2, () => onRemovePending(m)),
    ];
    final audios = [
      for (final m in saved.where((m) => m.kind == 'audio')) (m.path, m.durationMs, () => ref.read(journalRepoProvider).deleteMedia(m)),
      for (final m in pending.where((m) => m.$1 == 'audio')) (m.$2, m.$3, () => onRemovePending(m)),
    ];
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      if (images.isNotEmpty)
        SizedBox(
          height: 110,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: images.length,
            separatorBuilder: (_, _) => Gap.w8,
            itemBuilder: (_, i) => Stack(children: [
              ClipRRect(borderRadius: BorderRadius.circular(12), child: SizedBox(width: 110, height: 110, child: LocalImage(images[i].$1, cacheWidth: 220))),
              Positioned(
                right: 2,
                top: 2,
                child: IconButton.filledTonal(
                  tooltip: 'Kaldır',
                  visualDensity: VisualDensity.compact,
                  icon: const Icon(Icons.close, size: 16),
                  onPressed: () async {
                    if (await confirm(context, title: 'Fotoğraf kaldırılsın mı?', ok: 'Kaldır', destructive: true)) images[i].$2();
                  },
                ),
              ),
            ]),
          ),
        ),
      for (final a in audios) AudioTile(path: a.$1, durationMs: a.$2, onDelete: a.$3),
    ]);
  }
}
