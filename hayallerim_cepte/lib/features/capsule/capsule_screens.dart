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
import '../../data/journal_repository.dart';
import '../../data/providers.dart';

final _capsuleMediaProvider =
    StreamProvider.family<List<JournalMediaItem>, int>((ref, id) => ref.watch(journalRepoProvider).watchCapsuleMedia(id));

/// Gelecekteki Ben — zaman kapsülleri
class CapsulesScreen extends ConsumerWidget {
  const CapsulesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(capsulesProvider).value ?? const <TimeCapsule>[];
    return Scaffold(
      appBar: AppBar(title: const Text('Gelecekteki Ben')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/capsules/new'),
        icon: const Icon(Icons.hourglass_top),
        label: const Text('Kapsül oluştur'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
        children: [
          GradientCard(
            gradient: const LinearGradient(colors: [Color(0xFF0EA5E9), AppColors.purple]),
            child: const Text(
              'Gelecekteki kendine bir mektup, fotoğraf ya da ses kaydı bırak. Kapsül, seçtiğin tarihe kadar kilitli kalır ve o gün sana bildirim gelir.',
              style: TextStyle(height: 1.4),
            ),
          ),
          Gap.h8,
          Text(
            'Not: Kilit cihaz saatine göre çalışır; cihaz saati değiştirilirse erken açılabilir. Bu bir sürpriz özelliğidir, güvenlik kilidi değildir.',
            style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
          Gap.h12,
          if (list.isEmpty) const EmptyState(icon: Icons.hourglass_empty, title: 'Henüz kapsülün yok'),
          for (final c in list) Padding(padding: const EdgeInsets.only(bottom: 10), child: _CapsuleCard(c)),
        ],
      ),
    );
  }
}

class _CapsuleCard extends ConsumerWidget {
  const _CapsuleCard(this.c);
  final TimeCapsule c;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final open = JournalRepository.isOpenable(c);
    final days = Dates.daysBetween(DateTime.now(), c.openAt);
    return SoftCard(
      onTap: () async {
        if (!open) {
          toast(context, 'Bu kapsül ${Dates.long(c.openAt)} tarihinde açılacak ⏳');
          return;
        }
        final content = await ref.read(journalRepoProvider).openCapsule(c);
        if (content == null || !context.mounted) return;
        await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => _OpenedCapsule(c: c, content: content)));
      },
      child: Row(children: [
        Container(
          width: 56,
          height: 56,
          decoration: BoxDecoration(
            gradient: open ? AppColors.goldGradient : AppColors.nightGradient,
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(open ? Icons.mark_email_unread_rounded : Icons.lock_clock, color: Colors.white),
        ),
        Gap.w16,
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.title, style: const TextStyle(fontWeight: FontWeight.w700)),
            Text(
              open
                  ? (c.openedAt == null ? 'Açılmaya hazır! Dokun ✨' : 'Açıldı: ${Dates.short(c.openedAt!)}')
                  : '${Dates.long(c.openAt)} · $days gün kaldı',
              style: TextStyle(fontSize: 12, color: open ? AppColors.gold : Theme.of(context).colorScheme.onSurfaceVariant),
            ),
            Text('Oluşturulma: ${Dates.short(c.createdAt)}', style: const TextStyle(fontSize: 11)),
          ]),
        ),
        IconButton(
          tooltip: 'Sil',
          icon: const Icon(Icons.delete_outline),
          onPressed: () async {
            if (await confirm(context, title: 'Kapsül silinsin mi?', message: 'İçerik kalıcı olarak silinir.', ok: 'Sil', destructive: true)) {
              await ref.read(journalRepoProvider).deleteCapsule(c);
            }
          },
        ),
      ]),
    );
  }
}

class _OpenedCapsule extends ConsumerWidget {
  const _OpenedCapsule({required this.c, required this.content});
  final TimeCapsule c;
  final ({String letter, String dreams, String goals}) content;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final media = ref.watch(_capsuleMediaProvider(c.id)).value ?? const [];
    return Scaffold(
      appBar: AppBar(title: Text(c.title)),
      body: ListView(
        padding: const EdgeInsets.all(Gap.lg),
        children: [
          Text('${Dates.long(c.createdAt)} tarihindeki senden', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          Gap.h16,
          if (content.letter.isNotEmpty) SoftCard(child: Text(content.letter, style: const TextStyle(fontSize: 16, height: 1.5))),
          if (content.dreams.isNotEmpty) ...[
            const SectionHeader('O günkü hayallerin'),
            SoftCard(child: Text(content.dreams, style: const TextStyle(height: 1.5))),
          ],
          if (content.goals.isNotEmpty) ...[
            const SectionHeader('Kişisel hedeflerin'),
            SoftCard(child: Text(content.goals, style: const TextStyle(height: 1.5))),
          ],
          if (media.isNotEmpty) const SectionHeader('Anılar'),
          for (final m in media)
            if (m.kind == 'image')
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: ClipRRect(borderRadius: BorderRadius.circular(16), child: LocalImage(m.path, fit: BoxFit.contain, cacheWidth: 1000)),
              )
            else
              AudioTile(path: m.path, durationMs: m.durationMs),
        ],
      ),
    );
  }
}

class CapsuleFormScreen extends ConsumerStatefulWidget {
  const CapsuleFormScreen({super.key});
  @override
  ConsumerState<CapsuleFormScreen> createState() => _CapsuleFormScreenState();
}

class _CapsuleFormScreenState extends ConsumerState<CapsuleFormScreen> {
  final _title = TextEditingController(text: 'Gelecekteki bana');
  final _letter = TextEditingController();
  final _goals = TextEditingController();
  late final TextEditingController _dreams;
  DateTime _openAt = DateTime(DateTime.now().year + 1, DateTime.now().month, DateTime.now().day, 9);
  final List<(String, String, int?)> _media = [];
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    final dreams = (ref.read(dreamViewsProvider).value ?? const []).where((d) => !d.dream.isHidden && !d.dream.isCompleted);
    _dreams = TextEditingController(text: dreams.map((d) => '• ${d.dream.title} (%${(d.progress * 100).round()})').join('\n'));
  }

  @override
  void dispose() {
    for (final c in [_title, _letter, _goals, _dreams]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_title.text.trim().isEmpty) return;
    if (!_openAt.isAfter(DateTime.now())) {
      toast(context, 'Açılış tarihi gelecekte olmalı');
      return;
    }
    setState(() => _saving = true);
    await ref.read(journalRepoProvider).createCapsule(
          title: _title.text,
          letter: _letter.text,
          dreams: _dreams.text,
          goals: _goals.text,
          openAt: _openAt,
          mediaItems: _media,
        );
    _media.clear();
    if (mounted) {
      toast(context, 'Kapsül mühürlendi 🔒 ${Dates.long(_openAt)} tarihinde açılacak');
      context.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) {
          for (final m in _media) {
            await ref.read(mediaProvider).delete(m.$2);
          }
        }
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Yeni zaman kapsülü')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 120),
          children: [
            TextField(controller: _title, maxLength: 120, decoration: const InputDecoration(labelText: 'Kapsül adı')),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.lock_clock),
              title: const Text('Açılış tarihi'),
              subtitle: Text(Dates.dateTime(_openAt)),
              onTap: () async {
                final d = await pickDate(context, initial: _openAt, first: Dates.today().add(const Duration(days: 1)));
                if (d == null || !context.mounted) return;
                final t = await pickTime(context, initial: TimeOfDay(hour: _openAt.hour, minute: _openAt.minute));
                setState(() => _openAt = DateTime(d.year, d.month, d.day, t?.hour ?? 9, t?.minute ?? 0));
              },
            ),
            Wrap(spacing: 8, children: [
              for (final (label, months) in const [('6 ay', 6), ('1 yıl', 12), ('3 yıl', 36), ('5 yıl', 60)])
                ActionChip(
                  label: Text(label),
                  onPressed: () => setState(() => _openAt = Dates.addMonths(DateTime.now(), months)),
                ),
            ]),
            Gap.h12,
            TextField(
              controller: _letter,
              minLines: 6,
              maxLines: null,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(labelText: 'Mektubun', hintText: 'Sevgili gelecekteki ben…', alignLabelWithHint: true),
            ),
            Gap.h12,
            TextField(
              controller: _dreams,
              minLines: 3,
              maxLines: null,
              decoration: const InputDecoration(labelText: 'Hayal listen', alignLabelWithHint: true),
            ),
            Gap.h12,
            TextField(
              controller: _goals,
              minLines: 3,
              maxLines: null,
              decoration: const InputDecoration(labelText: 'Kişisel hedeflerin', alignLabelWithHint: true),
            ),
            Gap.h12,
            Row(children: [
              OutlinedButton.icon(
                onPressed: () async {
                  final cam = await pickSourceSheet(context);
                  if (cam == null) return;
                  final p = await ref.read(mediaProvider).pickImage(camera: cam);
                  if (p != null) setState(() => _media.add(('image', p, null)));
                },
                icon: const Icon(Icons.photo_outlined),
                label: const Text('Fotoğraf'),
              ),
              Gap.w8,
              OutlinedButton.icon(
                onPressed: () async {
                  final r = await recordAudio(context);
                  if (r != null) setState(() => _media.add(('audio', r.$1, r.$2)));
                },
                icon: const Icon(Icons.mic_none),
                label: const Text('Ses kaydı'),
              ),
            ]),
            if (_media.isNotEmpty) ...[
              Gap.h8,
              Text('${_media.where((m) => m.$1 == 'image').length} fotoğraf, ${_media.where((m) => m.$1 == 'audio').length} ses kaydı eklendi'),
            ],
            Gap.h8,
            Text('İçerik şifrelenerek saklanır ve tarih gelene kadar uygulamada gösterilmez.',
                style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.onSurfaceVariant)),
          ],
        ),
        bottomNavigationBar: SafeArea(
          minimum: const EdgeInsets.all(Gap.lg),
          child: FilledButton.icon(onPressed: _saving ? null : _save, icon: const Icon(Icons.lock), label: const Text('Kapsülü mühürle')),
        ),
      ),
    );
  }
}
