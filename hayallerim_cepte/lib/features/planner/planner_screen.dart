import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/logic/smart_planner.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

/// Akıllı Planlayıcı — yerel, kural tabanlı hedef planlama (yapay zekâ değildir).
class PlannerScreen extends ConsumerStatefulWidget {
  const PlannerScreen({super.key});
  @override
  ConsumerState<PlannerScreen> createState() => _PlannerScreenState();
}

class _PlannerScreenState extends ConsumerState<PlannerScreen> {
  final _text = TextEditingController();
  final _amount = TextEditingController();
  final _unit = TextEditingController();
  final _days = TextEditingController();
  final _title = TextEditingController();
  PlanInput? _input;
  bool _notUnderstood = false;
  bool _createDream = true;
  bool _monthlyGoals = true;
  bool _dailyTask = false;
  bool _savings = true;
  bool _busy = false;

  @override
  void dispose() {
    for (final c in [_text, _amount, _unit, _days, _title]) {
      c.dispose();
    }
    super.dispose();
  }

  void _analyze() {
    final p = SmartPlanner.parse(_text.text);
    setState(() {
      _notUnderstood = p == null;
      _input = p;
      if (p != null) {
        _amount.text = p.isMoney ? Money.toInput(p.amount) : '${p.amount ~/ 100}';
        _unit.text = p.unit;
        _days.text = '${p.days}';
        _title.text = _suggestTitle(p);
      }
    });
  }

  String _suggestTitle(PlanInput p) {
    final raw = _text.text.trim();
    final cleaned = raw.replaceAll(RegExp(r'\s*istiyorum\.?$', caseSensitive: false), '').trim();
    if (cleaned.isEmpty) return p.isMoney ? 'Birikim hedefi' : '${p.amount ~/ 100} ${p.unit}';
    return cleaned[0].toUpperCase() + cleaned.substring(1);
  }

  /// Kullanıcı düzenlemelerini plana uygular.
  PlanInput? get _edited {
    final i = _input;
    if (i == null) return null;
    final days = int.tryParse(_days.text);
    final amount = i.isMoney ? Money.parseMinor(_amount.text) : ((int.tryParse(_amount.text) ?? 0) * 100);
    if (days == null || days <= 0 || amount == null || amount <= 0) return null;
    return i.copyWith(amount: amount, days: days, unit: _unit.text.trim().isEmpty ? i.unit : _unit.text.trim());
  }

  Future<void> _create(PlanResult r) async {
    setState(() => _busy = true);
    final i = r.input;
    final title = _title.text.trim().isEmpty ? 'Yeni hedef' : _title.text.trim();
    final end = Dates.today().add(Duration(days: i.days));
    int? dreamId;
    if (_createDream) {
      final cats = ref.read(categoriesProvider).value ?? const [];
      final cat = cats.where((c) => c.name == (i.isMoney ? 'Finans' : 'Kişisel gelişim')).firstOrNull ?? cats.last;
      dreamId = await ref.read(dreamRepoProvider).createDream(
            title: title,
            categoryId: cat.id,
            targetDate: end,
            budgetMinor: i.isMoney ? i.amount : null,
            description: 'Akıllı Planlayıcı ile oluşturuldu.\n${r.summary}',
            steps: [
              for (var m = 1; m <= r.months && m <= 24; m++)
                i.isMoney ? '$m. ay: ${r.fmt(r.perMonth * m)}' : '$m. ay: toplam ${r.fmt(r.perMonth * m)} ${i.unit}',
            ],
          );
    }
    final goals = ref.read(goalRepoProvider);
    if (_monthlyGoals) {
      final months = r.months.clamp(1, 24);
      for (var m = 0; m < months; m++) {
        final due = Dates.addMonths(Dates.today(), m + 1);
        await goals.createGoal(
          title: i.isMoney ? '$title — ${m + 1}. ay: ${r.fmt(r.perMonth)}' : '$title — ${m + 1}. ay: ${r.fmt(r.perMonth)} ${i.unit}',
          period: 'monthly',
          dreamId: dreamId,
          dueDate: due.isAfter(end) ? end : due,
        );
      }
    }
    if (_dailyTask) {
      await goals.createTask(
        title: i.isMoney ? 'Günlük birikim: ${r.fmt(r.perDay)}' : 'Günlük: ${r.fmt(r.perDay)} ${i.unit}',
        dreamId: dreamId,
        dueDate: Dates.today(),
        repeatRule: 'daily',
      );
    }
    if (i.isMoney && _savings) {
      await ref.read(savingsRepoProvider).createGoal(
            title: title,
            targetMinor: i.amount,
            monthlyTargetMinor: r.perMonth.ceil(),
            targetDate: end,
            dreamId: dreamId,
          );
    }
    if (!mounted) return;
    setState(() => _busy = false);
    toast(context, 'Planın oluşturuldu ✨');
    if (dreamId != null) {
      context.pushReplacement('/dream/$dreamId');
    } else {
      context.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final edited = _edited;
    final r = edited == null ? null : SmartPlanner.compute(edited);
    return Scaffold(
      appBar: AppBar(title: const Text('Akıllı Planlayıcı')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 60),
        children: [
          Text(
            'Hedefini bir cümleyle yaz; süre ve miktarı ayrıştırıp sana bir plan önereyim. '
            'Bu planlayıcı tamamen cihazında, kurallarla çalışır — internet ya da yapay zekâ kullanmaz.',
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
          Gap.h12,
          TextField(
            controller: _text,
            maxLines: 2,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(hintText: '6 ayda 30 kitap okumak istiyorum'),
            onSubmitted: (_) => _analyze(),
          ),
          Gap.h8,
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in SmartPlanner.examples)
              ActionChip(
                label: Text(e, style: const TextStyle(fontSize: 12)),
                onPressed: () {
                  _text.text = e;
                  _analyze();
                },
              ),
          ]),
          Gap.h12,
          FilledButton.icon(onPressed: _analyze, icon: const Icon(Icons.auto_fix_high), label: const Text('Plan öner')),
          if (_notUnderstood) ...[
            Gap.h12,
            const SoftCard(
              child: Text('Cümlede bir süre (ör. "6 ayda", "8 haftada", "1 yılda") ve bir miktar (ör. "30 kitap", "50000 TL") bulamadım. '
                  'Örneklerden birini deneyebilirsin.'),
            ),
          ],
          if (_input != null) ...[
            const SectionHeader('Planı düzenle'),
            TextField(controller: _title, decoration: const InputDecoration(labelText: 'Hedef adı'), onChanged: (_) => setState(() {})),
            Gap.h8,
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _amount,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: InputDecoration(labelText: _input!.isMoney ? 'Tutar (₺)' : 'Miktar'),
                  onChanged: (_) => setState(() {}),
                ),
              ),
              if (!_input!.isMoney) ...[
                Gap.w8,
                Expanded(child: TextField(controller: _unit, decoration: const InputDecoration(labelText: 'Birim'), onChanged: (_) => setState(() {}))),
              ],
              Gap.w8,
              Expanded(
                child: TextField(
                  controller: _days,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Süre (gün)'),
                  onChanged: (_) => setState(() {}),
                ),
              ),
            ]),
          ],
          if (r != null) ...[
            Gap.h16,
            GradientCard(
              gradient: const LinearGradient(colors: [Color(0xFFF97316), AppColors.gold]),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('Önerilen plan', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 17)),
                Gap.h8,
                Text(r.summary, style: const TextStyle(height: 1.5)),
                Gap.h8,
                Text('Bitiş: ${Dates.long(Dates.today().add(Duration(days: r.input.days)))}', style: const TextStyle(fontSize: 12)),
              ]),
            ),
            const SectionHeader('Neler oluşturulsun?'),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Hayal olarak ekle (aylık yolculuk adımlarıyla)'), value: _createDream, onChanged: (v) => setState(() => _createDream = v)),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: Text('Aylık hedefler (${r.months.clamp(1, 24)} adet)'), value: _monthlyGoals, onChanged: (v) => setState(() => _monthlyGoals = v)),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Günlük tekrarlanan görev'), value: _dailyTask, onChanged: (v) => setState(() => _dailyTask = v)),
            if (r.input.isMoney)
              SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Hayal kumbarası'), value: _savings, onChanged: (v) => setState(() => _savings = v)),
            Gap.h8,
            FilledButton(onPressed: _busy ? null : () => _create(r), child: const Text('Planı oluştur')),
          ],
        ],
      ),
    );
  }
}
