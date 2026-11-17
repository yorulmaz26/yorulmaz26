import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/translator/application/translator_controller.dart';
import 'package:kus_dili/features/translator/presentation/translator_screen.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  Widget app() =>
      const ProviderScope(child: MaterialApp(home: TranslatorScreen()));

  SelectableText output(WidgetTester t) =>
      t.widget<SelectableText>(find.byKey(const Key('output')));

  testWidgets('Yazılan metin anında kuş diline çevrilir', (t) async {
    await t.pumpWidget(app());
    await t.enterText(find.byKey(const Key('input')), 'Seni seviyorum');
    await t.pump();
    expect(output(t).data, 'Segenigi segevigiyogorugum');
  });

  testWidgets('Yön değiştirilince çıktı Türkçeye geri çevrilir', (t) async {
    await t.pumpWidget(app());
    await t.enterText(find.byKey(const Key('input')), 'Merhaba');
    await t.pump();
    await t.tap(find.byKey(const Key('swap')));
    await t.pump();
    expect(output(t).data, 'Merhaba');
  });

  testWidgets("F'li varyant seçilebilir", (t) async {
    await t.pumpWidget(app());
    await t.tap(find.text("F'li"));
    await t.enterText(find.byKey(const Key('input')), 'Selam');
    await t.pump();
    expect(output(t).data, 'Sefelafam');
  });

  test('Sağlayıcı: Kuş dili → Türkçe geçersiz girdide uyarı üretir', () {
    final c = ProviderContainer();
    addTearDown(c.dispose);
    c.read(translatorProvider.notifier)
      ..setInput('Merhaba')
      ..swap('Merhaba');
    final out = c.read(translationOutputProvider);
    expect(out.decode?.isValid, isFalse);
    expect(c.read(translatorProvider).variant, KusDiliVariant.g);
  });
}
