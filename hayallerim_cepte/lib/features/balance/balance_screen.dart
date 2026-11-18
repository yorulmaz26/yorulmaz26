import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/database/app_database.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/utils/dates.dart';
import '../../core/widgets/common.dart';
import '../../data/misc_repositories.dart';
import '../../data/providers.dart';

const _areaColors = [
  Color(0xFF2EAD6B),
  Color(0xFFD6AD60),
  Color(0xFF101827),
  Color(0xFFF97316),
  Color(0xFFEF6C8F),
  Color(0xFF3B82F6),
  Color(0xFF8B5CF6),
  Color(0xFFEC4899),
];

/// Yaşam Dengem — 8 alanı 1-10 arası puanla, radar grafikte gör.
class BalanceScreen extends ConsumerStatefulWidget {
  const BalanceScreen({super.key});
  @override
  ConsumerState<BalanceScreen> createState() => _BalanceScreenState();
}

class _BalanceScreenState extends ConsumerState<BalanceScreen> {
  int _area = 0;

  @override
  Widget build(BuildContext context) {
    final entries = ref.watch(balanceProvider).value ?? const <LifeBalanceEntry>[];
    final last = entries.isEmpty ? null : entries.last;
    final prev = entries.length >= 2 ? entries[entries.length - 2] : null;
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Yaşam Dengem')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => showModalBottomSheet<void>(context: context, isScrollControlled: true, builder: (_) => _Rate(initial: last)),
        icon: const Icon(Icons.add_chart),
        label: const Text('Değerlendir'),
      ),
      body: last == null
          ? EmptyState(
              icon: Icons.donut_large,
              title: 'Hayatının dengesini gör',
              message: 'Sağlıktan kariyere 8 alanı 1-10 arasında puanla; zaman içindeki değişimini takip et.',
              actionLabel: 'İlk değerlendirmem',
              onAction: () => showModalBottomSheet<void>(context: context, isScrollControlled: true, builder: (_) => const _Rate()),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(Gap.lg, 0, Gap.lg, 100),
              children: [
                SoftCard(
                  child: Column(children: [
                    Text('Son değerlendirme: ${Dates.long(last.createdAt)}', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12)),
                    Gap.h8,
                    SizedBox(
                      height: 290,
                      child: Semantics(
                        label: [
                          for (var i = 0; i < 8; i++) '${BalanceRepository.areas[i]} ${BalanceRepository.values(last)[i]}',
                        ].join(', '),
                        child: RadarChart(RadarChartData(
                          dataSets: [
                            if (prev != null)
                              RadarDataSet(
                                dataEntries: [for (final v in BalanceRepository.values(prev)) RadarEntry(value: v.toDouble())],
                                fillColor: Colors.grey.withValues(alpha: 0.12),
                                borderColor: Colors.grey,
                                borderWidth: 1,
                                entryRadius: 0,
                              ),
                            RadarDataSet(
                              dataEntries: [for (final v in BalanceRepository.values(last)) RadarEntry(value: v.toDouble())],
                              fillColor: AppColors.purple.withValues(alpha: 0.25),
                              borderColor: AppColors.purple,
                              borderWidth: 2,
                              entryRadius: 3,
                            ),
                            // Ölçeği 0-10'a sabitlemek için görünmez veri setleri
                            RadarDataSet(
                              dataEntries: List.filled(8, const RadarEntry(value: 10)),
                              fillColor: Colors.transparent,
                              borderColor: Colors.transparent,
                              entryRadius: 0,
                            ),
                            RadarDataSet(
                              dataEntries: List.filled(8, const RadarEntry(value: 0)),
                              fillColor: Colors.transparent,
                              borderColor: Colors.transparent,
                              entryRadius: 0,
                            ),
                          ],
                          radarShape: RadarShape.polygon,
                          tickCount: 5,
                          ticksTextStyle: const TextStyle(fontSize: 0, color: Colors.transparent),
                          tickBorderData: BorderSide(color: cs.outlineVariant),
                          gridBorderData: BorderSide(color: cs.outlineVariant),
                          radarBorderData: BorderSide(color: cs.outlineVariant),
                          titleTextStyle: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
                          getTitle: (i, _) => RadarChartTitle(text: '${BalanceRepository.areas[i]}\n${BalanceRepository.values(last)[i]}'),
                        )),
                      ),
                    ),
                    if (prev != null)
                      Text('Gri çizgi: bir önceki değerlendirme', style: TextStyle(fontSize: 11, color: cs.onSurfaceVariant)),
                  ]),
                ),
                Gap.h12,
                _Insights(last: last, prev: prev),
                if (entries.length >= 2) ...[
                  const SectionHeader('Zaman içindeki değişim'),
                  SizedBox(
                    height: 40,
                    child: ListView(scrollDirection: Axis.horizontal, children: [
                      for (var i = 0; i < 8; i++)
                        Padding(
                          padding: const EdgeInsets.only(right: 6),
                          child: ChoiceChip(label: Text(BalanceRepository.areas[i]), selected: _area == i, onSelected: (_) => setState(() => _area = i)),
                        ),
                    ]),
                  ),
                  Gap.h8,
                  SoftCard(
                    child: SizedBox(
                      height: 180,
                      child: LineChart(LineChartData(
                        minY: 0,
                        maxY: 10,
                        gridData: const FlGridData(show: true, drawVerticalLine: false, horizontalInterval: 2),
                        borderData: FlBorderData(show: false),
                        titlesData: FlTitlesData(
                          rightTitles: const AxisTitles(),
                          topTitles: const AxisTitles(),
                          leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: true, interval: 2, reservedSize: 24)),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              interval: 1,
                              getTitlesWidget: (v, _) {
                                final i = v.toInt();
                                if (i < 0 || i >= entries.length || v != i.toDouble()) return const SizedBox();
                                return Text(Dates.dayMonth(entries[i].createdAt), style: const TextStyle(fontSize: 10));
                              },
                            ),
                          ),
                        ),
                        lineBarsData: [
                          LineChartBarData(
                            spots: [for (var i = 0; i < entries.length; i++) FlSpot(i.toDouble(), BalanceRepository.values(entries[i])[_area].toDouble())],
                            color: _areaColors[_area],
                            barWidth: 3,
                            isCurved: true,
                            preventCurveOverShooting: true,
                          ),
                        ],
                      )),
                    ),
                  ),
                ],
                const SectionHeader('Geçmiş değerlendirmeler'),
                for (final e in entries.reversed)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(Dates.long(e.createdAt)),
                    subtitle: Text(e.note.isEmpty ? 'Ortalama ${(BalanceRepository.values(e).reduce((a, b) => a + b) / 8).toStringAsFixed(1)}' : e.note),
                    trailing: IconButton(
                      tooltip: 'Sil',
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () async {
                        if (await confirm(context, title: 'Değerlendirme silinsin mi?', ok: 'Sil', destructive: true)) {
                          await ref.read(balanceRepoProvider).delete(e);
                        }
                      },
                    ),
                  ),
              ],
            ),
    );
  }
}

class _Insights extends StatelessWidget {
  const _Insights({required this.last, this.prev});
  final LifeBalanceEntry last;
  final LifeBalanceEntry? prev;

  @override
  Widget build(BuildContext context) {
    final v = BalanceRepository.values(last);
    final avg = v.reduce((a, b) => a + b) / 8;
    var low = 0;
    var high = 0;
    for (var i = 1; i < 8; i++) {
      if (v[i] < v[low]) low = i;
      if (v[i] > v[high]) high = i;
    }
    String? change;
    if (prev != null) {
      final p = BalanceRepository.values(prev!);
      var best = 0;
      for (var i = 1; i < 8; i++) {
        if (v[i] - p[i] > v[best] - p[best]) best = i;
      }
      final d = v[best] - p[best];
      if (d > 0) change = 'En çok gelişen alan: ${BalanceRepository.areas[best]} (+$d)';
    }
    return SoftCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Ortalama denge puanın: ${avg.toStringAsFixed(1)} / 10', style: const TextStyle(fontWeight: FontWeight.w700)),
        Gap.h8,
        Text('💪 Güçlü alanın: ${BalanceRepository.areas[high]} (${v[high]})'),
        Text('🌱 Gelişime açık alan: ${BalanceRepository.areas[low]} (${v[low]}) — bu alana yönelik küçük bir hedef koymayı düşünebilirsin.'),
        if (change != null) Text('📈 $change'),
      ]),
    );
  }
}

class _Rate extends ConsumerStatefulWidget {
  const _Rate({this.initial});
  final LifeBalanceEntry? initial;
  @override
  ConsumerState<_Rate> createState() => _RateState();
}

class _RateState extends ConsumerState<_Rate> {
  late final List<int> _v = widget.initial == null ? List.filled(8, 5) : [...BalanceRepository.values(widget.initial!)];
  final _note = TextEditingController();

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(left: Gap.lg, right: Gap.lg, bottom: MediaQuery.viewInsetsOf(context).bottom + Gap.lg),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
          Text('Bugün yaşam alanlarını nasıl değerlendiriyorsun?', style: Theme.of(context).textTheme.titleMedium),
          for (var i = 0; i < 8; i++)
            Row(children: [
              SizedBox(width: 110, child: Text(BalanceRepository.areas[i])),
              Expanded(
                child: Slider(
                  value: _v[i].toDouble(),
                  min: 1,
                  max: 10,
                  divisions: 9,
                  activeColor: _areaColors[i],
                  label: '${_v[i]}',
                  semanticFormatterCallback: (x) => '${BalanceRepository.areas[i]} ${x.round()}',
                  onChanged: (x) => setState(() => _v[i] = x.round()),
                ),
              ),
              SizedBox(width: 24, child: Text('${_v[i]}', style: const TextStyle(fontWeight: FontWeight.w700))),
            ]),
          TextField(controller: _note, maxLength: 200, decoration: const InputDecoration(labelText: 'Not (isteğe bağlı)')),
          FilledButton(
            onPressed: () async {
              await ref.read(balanceRepoProvider).add(_v, _note.text.trim());
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('Kaydet'),
          ),
        ]),
      ),
    );
  }
}
