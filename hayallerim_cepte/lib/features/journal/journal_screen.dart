import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';
import '../goals/goals_screen.dart' show MonthCalendar;
import '../security/lock_controller.dart';
import '../security/lock_screen.dart';

const moods = ['😞', '😕', '😐', '🙂', '😄'];
const moodLabels = ['Çok kötü', 'Kötü', 'Normal', 'İyi', 'Harika'];

class JournalScreen extends ConsumerStatefulWidget {
  const JournalScreen({super.key});
  @override
  ConsumerState<JournalScreen> createState() => _JournalScreenState();
}

class _JournalScreenState extends ConsumerState<JournalScreen> {
  bool _calendar = false;
  DateTime _day = Dates.today();
  String _query = '';
  List<JournalEntry>? _results;
  bool _importantOnly = false;

  Future<void> _search(String q) async {
    _query = q;
    if (q.trim().isEmpty) {
      setState(() => _results = null);
      return;
    }
    final r = await ref.read(journalRepoProvider).search(q, includePrivateBodies: ref.read(lockProvider).privateUnlocked);
    if (mounted && _query == q) setState(() => _results = r);
  }

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(journalProvider).value ?? const <JournalEntry>[];
    var list = _results ?? all;
    if (_importantOnly) list = list.where((e) => e.isImportant).toList();
    if (_calendar) list = list.where((e) => Dates.sameDay(e.date, _day)).toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Hayal Günlüğüm'),
        actions: [
          IconButton(
            tooltip: _calendar ? 'Liste' : 'Takvim',
            icon: Icon(_calendar ? Icons.view_list : Icons.calendar_month),
            onPressed: () => setState(() => _calendar = !_calendar),
          ),
          IconButton(
            tooltip: 'Önemli anlar',
            icon: Icon(_importantOnly ? Icons.star : Icons.star_border),
            onPressed: () => setState(() => _importantOnly = !_importantOnly),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/journal/new'),
        icon: const Icon(Icons.edit),
        label: const Text('Yaz'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
        children: [
          TextField(
            decoration: const InputDecoration(hintText: 'Günlüklerde ara (başlık, etiket, metin)', prefixIcon: Icon(Icons.search), isDense: true),
            onChanged: _search,
          ),
          if (_calendar) ...[
            Gap.h12,
            SoftCard(
              padding: const EdgeInsets.all(Gap.sm),
              child: MonthCalendar(
                selected: _day,
                markers: {for (final e in all) Dates.dayOf(e.date): 2},
                onSelect: (d) => setState(() => _day = d),
              ),
            ),
          ],
          Gap.h12,
          if (list.isEmpty)
            EmptyState(
              icon: Icons.menu_book_outlined,
              title: all.isEmpty ? 'İlk sayfanı yaz' : 'Kayıt bulunamadı',
              message: all.isEmpty ? 'Duygularını, önemli anlarını ve hayallerini kaydet.' : null,
            ),
          for (final e in list) Padding(padding: const EdgeInsets.only(bottom: 10), child: _EntryCard(e)),
        ],
      ),
    );
  }
}

class _EntryCard extends ConsumerWidget {
  const _EntryCard(this.e);
  final JournalEntry e;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unlocked = ref.watch(lockProvider).privateUnlocked;
    final locked = e.isPrivate && !unlocked;
    final preview = locked ? '🔒 Özel günlük — görmek için kilidi aç' : (e.isPrivate ? '🔓 Özel günlük' : e.body);
    return SoftCard(
      onTap: () async {
        if (e.isPrivate && !await requestPrivateAccess(context, ref)) return;
        if (context.mounted) context.push('/journal/${e.id}');
      },
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Column(children: [
          Text(moods[(e.mood - 1).clamp(0, 4)], style: const TextStyle(fontSize: 28)),
          Text('${e.date.day}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
          Text(Dates.weekdayShort(e.date), style: const TextStyle(fontSize: 11)),
        ]),
        Gap.w16,
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(
                child: Text(e.title.isEmpty ? Dates.long(e.date) : e.title,
                    maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
              ),
              if (e.isImportant) const Icon(Icons.star, size: 18, color: Colors.amber),
            ]),
            Gap.h4,
            Text(preview, maxLines: 3, overflow: TextOverflow.ellipsis, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            if (e.tags.isNotEmpty) ...[
              Gap.h8,
              Wrap(spacing: 6, children: [
                for (final t in e.tags.split(',')) Text('#$t', style: TextStyle(fontSize: 12, color: Theme.of(context).colorScheme.primary)),
              ]),
            ],
          ]),
        ),
      ]),
    );
  }
}
