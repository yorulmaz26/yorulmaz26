import 'package:drift/drift.dart' show Value;
import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/database/app_database.dart';
import '../../core/settings/settings_controller.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/utils/money.dart';
import '../../core/widgets/common.dart';
import '../../data/providers.dart';

const txKindLabels = {'deposit': 'Birikim ekle', 'withdraw': 'Birikim çek', 'income': 'Gelir', 'expense': 'Gider'};

class TransactionTile extends ConsumerWidget {
  const TransactionTile(this.t, {super.key});
  final SavingsTransaction t;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final positive = t.kind == 'deposit' || t.kind == 'income';
    final color = positive ? AppColors.success : AppColors.danger;
    final orig = t.originalCurrency != null && t.originalAmountMinor != null
        ? ' (${Money.format(t.originalAmountMinor!, currency: t.originalCurrency!)} · kur ${t.manualRate})'
        : '';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: CircleAvatar(
        backgroundColor: color.withValues(alpha: 0.12),
        child: Icon(positive ? Icons.arrow_downward_rounded : Icons.arrow_upward_rounded, color: color),
      ),
      title: Text(t.note.isEmpty ? txKindLabels[t.kind]! : t.note),
      subtitle: Text('${Dates.short(t.date)}$orig', style: const TextStyle(fontSize: 12)),
      trailing: Text('${positive ? '+' : '-'}${Money.format(t.amountMinor, currency: t.currency)}',
          style: TextStyle(color: color, fontWeight: FontWeight.w700)),
      onLongPress: () async {
        if (await confirm(context, title: 'İşlem silinsin mi?', ok: 'Sil', destructive: true)) {
          await ref.read(savingsRepoProvider).deleteTransaction(t);
        }
      },
    );
  }
}

/// Kumbara oluşturma / düzenleme
Future<void> showSavingsGoalSheet(BuildContext context, {SavingsGoal? existing}) =>
    showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => _GoalSheet(existing: existing));

class _GoalSheet extends ConsumerStatefulWidget {
  const _GoalSheet({this.existing});
  final SavingsGoal? existing;
  @override
  ConsumerState<_GoalSheet> createState() => _GoalSheetState();
}

class _GoalSheetState extends ConsumerState<_GoalSheet> {
  final _form = GlobalKey<FormState>();
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _target = TextEditingController(text: widget.existing == null ? '' : Money.toInput(widget.existing!.targetMinor));
  late final _monthly =
      TextEditingController(text: widget.existing?.monthlyTargetMinor == null ? '' : Money.toInput(widget.existing!.monthlyTargetMinor!));
  late String _currency = widget.existing?.currency ?? ref.read(settingsProvider).defaultCurrency;
  late DateTime? _date = widget.existing?.targetDate;
  late int? _dream = widget.existing?.dreamId;
  bool _reminder = false;

  @override
  void dispose() {
    _title.dispose();
    _target.dispose();
    _monthly.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    final target = Money.parseMinor(_target.text)!;
    final monthly = _monthly.text.trim().isEmpty ? null : Money.parseMinor(_monthly.text);
    final repo = ref.read(savingsRepoProvider);
    if (widget.existing == null) {
      await repo.createGoal(
        title: _title.text,
        targetMinor: target,
        currency: _currency,
        monthlyTargetMinor: monthly,
        targetDate: _date,
        dreamId: _dream,
        monthlyReminder: _reminder,
      );
    } else {
      await repo.updateGoal(widget.existing!.copyWith(
        title: _title.text.trim(),
        targetMinor: target,
        currency: _currency,
        monthlyTargetMinor: Value(monthly),
        targetDate: Value(_date),
        dreamId: Value(_dream),
      ));
    }
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final dreams = (ref.watch(dreamViewsProvider).value ?? const []).where((d) => !d.dream.isHidden).toList();
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: Form(
        key: _form,
        child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
            Text(widget.existing == null ? 'Yeni kumbara' : 'Kumbarayı düzenle', style: Theme.of(context).textTheme.titleLarge),
            Gap.h12,
            TextFormField(
              controller: _title,
              maxLength: 120,
              decoration: const InputDecoration(labelText: 'Kumbara adı', hintText: 'Örn. Araba almak'),
              validator: (v) => v == null || v.trim().isEmpty ? 'Ad gerekli' : null,
            ),
            Row(children: [
              Expanded(
                child: TextFormField(
                  controller: _target,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Hedef tutar'),
                  validator: (v) {
                    final m = Money.parseMinor(v ?? '');
                    return m == null || m <= 0 ? 'Geçerli bir tutar girin' : null;
                  },
                ),
              ),
              Gap.w8,
              DropdownButton<String>(
                value: _currency,
                items: [for (final c in Money.currencies.keys) DropdownMenuItem(value: c, child: Text(c))],
                onChanged: (v) => setState(() => _currency = v!),
              ),
            ]),
            Gap.h12,
            TextFormField(
              controller: _monthly,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: 'Aylık birikim hedefi (isteğe bağlı)'),
              validator: (v) => v == null || v.trim().isEmpty || Money.parseMinor(v) != null ? null : 'Geçerli bir tutar girin',
            ),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event),
              title: const Text('Hedef tarihi'),
              subtitle: Text(_date == null ? 'Seçilmedi' : Dates.long(_date!)),
              trailing: _date == null ? null : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _date = null)),
              onTap: () async {
                final d = await pickDate(context, initial: _date ?? DateTime.now().add(const Duration(days: 365)), first: Dates.today());
                if (d != null) setState(() => _date = d);
              },
            ),
            DropdownButtonFormField<int?>(
              initialValue: dreams.any((d) => d.dream.id == _dream) ? _dream : null,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Bağlı hayal'),
              items: [
                const DropdownMenuItem(value: null, child: Text('Yok')),
                for (final d in dreams) DropdownMenuItem(value: d.dream.id, child: Text(d.dream.title, overflow: TextOverflow.ellipsis)),
              ],
              onChanged: (v) => setState(() => _dream = v),
            ),
            if (widget.existing == null)
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Aylık birikim hatırlatıcısı'),
                subtitle: const Text('Her ayın 1\'inde 10:00'),
                value: _reminder,
                onChanged: (v) => setState(() => _reminder = v),
              ),
            Gap.h12,
            FilledButton(onPressed: _save, child: const Text('Kaydet')),
          ]),
        ),
      ),
    );
  }
}

/// İşlem ekleme: birikim ekle/çek (kumbara) ya da gelir/gider (genel).
/// Farklı para birimi seçilirse manuel kur istenir.
Future<void> showTransactionSheet(BuildContext context, {SavingsGoal? goal, String kind = 'deposit'}) =>
    showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => _TxSheet(goal: goal, kind: goal == null ? 'income' : kind));

class _TxSheet extends ConsumerStatefulWidget {
  const _TxSheet({this.goal, required this.kind});
  final SavingsGoal? goal;
  final String kind;
  @override
  ConsumerState<_TxSheet> createState() => _TxSheetState();
}

class _TxSheetState extends ConsumerState<_TxSheet> {
  final _form = GlobalKey<FormState>();
  final _amount = TextEditingController();
  final _rate = TextEditingController();
  final _note = TextEditingController();
  late String _kind = widget.kind;
  late final String _baseCurrency = widget.goal?.currency ?? ref.read(settingsProvider).defaultCurrency;
  late String _currency = _baseCurrency;
  DateTime _date = DateTime.now();

  @override
  void dispose() {
    _amount.dispose();
    _rate.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    final amount = Money.parseMinor(_amount.text)!;
    var converted = amount;
    Decimal? rate;
    if (_currency != _baseCurrency) {
      rate = Money.parseRate(_rate.text)!;
      converted = Money.convert(amount, rate);
    }
    try {
      await ref.read(savingsRepoProvider).addTransaction(
            goalId: widget.goal?.id,
            kind: _kind,
            amountMinor: converted,
            currency: _baseCurrency,
            originalCurrency: _currency != _baseCurrency ? _currency : null,
            originalAmountMinor: _currency != _baseCurrency ? amount : null,
            rate: rate,
            note: _note.text.trim(),
            date: _date,
            goalTitle: widget.goal?.title,
          );
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) toast(context, 'Kaydedilemedi: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final kinds = widget.goal == null ? const ['income', 'expense'] : const ['deposit', 'withdraw'];
    final converted = () {
      final a = Money.parseMinor(_amount.text);
      final r = Money.parseRate(_rate.text);
      if (a == null || r == null) return null;
      return Money.convert(a, r);
    }();
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: Form(
        key: _form,
        child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
            Text(widget.goal?.title ?? 'Gelir / gider kaydı', style: Theme.of(context).textTheme.titleLarge),
            Gap.h12,
            ChoiceChips<String>(values: kinds, labels: [for (final k in kinds) txKindLabels[k]!], selected: _kind, onSelected: (v) => setState(() => _kind = v)),
            Gap.h12,
            Row(children: [
              Expanded(
                child: TextFormField(
                  controller: _amount,
                  autofocus: true,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Tutar'),
                  onChanged: (_) => setState(() {}),
                  validator: (v) {
                    final m = Money.parseMinor(v ?? '');
                    return m == null || m <= 0 ? 'Geçerli bir tutar girin' : null;
                  },
                ),
              ),
              Gap.w8,
              DropdownButton<String>(
                value: _currency,
                items: [for (final c in Money.currencies.keys) DropdownMenuItem(value: c, child: Text(c))],
                onChanged: (v) => setState(() => _currency = v!),
              ),
            ]),
            if (_currency != _baseCurrency) ...[
              Gap.h12,
              TextFormField(
                controller: _rate,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: '1 $_currency = ? $_baseCurrency (manuel kur)',
                  helperText: converted == null ? 'Çevrimdışı uygulama: kuru kendin gir' : '= ${Money.format(converted, currency: _baseCurrency)}',
                ),
                onChanged: (_) => setState(() {}),
                validator: (v) => Money.parseRate(v ?? '') == null ? 'Geçerli bir kur girin' : null,
              ),
            ],
            Gap.h12,
            TextField(controller: _note, maxLength: 120, decoration: const InputDecoration(labelText: 'Not (isteğe bağlı)')),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event),
              title: Text(Dates.long(_date)),
              onTap: () async {
                final d = await pickDate(context, initial: _date, last: DateTime.now());
                if (d != null) setState(() => _date = d);
              },
            ),
            FilledButton(onPressed: _save, child: const Text('Kaydet')),
          ]),
        ),
      ),
    );
  }
}

/// Tasarruf hesaplayıcısı: hedef tutar + süre → aylık/haftalık/günlük;
/// ya da aylık tutar → hedefe kaç ayda ulaşılır.
Future<void> showSavingsCalculator(BuildContext context) =>
    showModalBottomSheet(context: context, isScrollControlled: true, builder: (_) => const _Calculator());

class _Calculator extends StatefulWidget {
  const _Calculator();
  @override
  State<_Calculator> createState() => _CalculatorState();
}

class _CalculatorState extends State<_Calculator> {
  final _target = TextEditingController();
  final _current = TextEditingController();
  final _months = TextEditingController(text: '12');
  final _monthly = TextEditingController();

  @override
  void dispose() {
    for (final c in [_target, _current, _months, _monthly]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final target = Money.parseMinor(_target.text) ?? 0;
    final current = Money.parseMinor(_current.text) ?? 0;
    final remaining = (target - current).clamp(0, target);
    final months = int.tryParse(_months.text) ?? 0;
    final monthly = Money.parseMinor(_monthly.text) ?? 0;
    final need = Money.requiredMonthly(remainingMinor: remaining, months: months);
    final reach = Money.monthsToGoal(remainingMinor: remaining, monthlyMinor: monthly);
    final weekly = months <= 0 ? 0 : (remaining * 7 + months * 30 - 1) ~/ (months * 30);
    final daily = months <= 0 ? 0 : (remaining + months * 30 - 1) ~/ (months * 30);
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Text('Tasarruf hesaplayıcısı', style: Theme.of(context).textTheme.titleLarge),
          Gap.h12,
          TextField(
            controller: _target,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Hedef tutar (₺)'),
            onChanged: (_) => setState(() {}),
          ),
          Gap.h8,
          TextField(
            controller: _current,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Mevcut birikim (₺)'),
            onChanged: (_) => setState(() {}),
          ),
          Gap.h8,
          TextField(
            controller: _months,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Kaç ayda?'),
            onChanged: (_) => setState(() {}),
          ),
          Gap.h12,
          SoftCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Kalan: ${Money.format(remaining)}'),
              Gap.h4,
              Text('Aylık: ${Money.format(need)}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
              Text('Haftalık ≈ ${Money.format(weekly)} · Günlük ≈ ${Money.format(daily)}'),
            ]),
          ),
          const SectionHeader('Ya da: aylık ne kadar ayırabilirim?'),
          TextField(
            controller: _monthly,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Aylık birikim (₺)'),
            onChanged: (_) => setState(() {}),
          ),
          Gap.h8,
          Text(reach == null ? 'Aylık tutar girin' : 'Hedefe yaklaşık $reach ayda ulaşırsın (${Dates.monthYear(Dates.addMonths(DateTime.now(), reach))}).',
              style: const TextStyle(fontWeight: FontWeight.w600)),
        ]),
      ),
    );
  }
}
