import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hayallerim_cepte/main.dart' as app;
import 'package:integration_test/integration_test.dart';

/// Gerçek cihaz/emülatör üzerinde uçtan uca akış:
///   `flutter test integration_test/app_flow_test.dart -d CIHAZ_ID`
/// Not: Uygulama verisi temiz olmalıdır (ilk kurulum akışını test eder).
/// Uçak modunda çalıştırarak çevrimdışı çalışmayı da doğrulayın.
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('ilk kurulum → hayal ekle → adım tamamla → bahçe', (t) async {
    app.main();
    await t.pumpAndSettle(const Duration(seconds: 3));

    if (find.text('Atla').evaluate().isNotEmpty) {
      await t.tap(find.text('Atla'));
      await t.pumpAndSettle();
      await t.enterText(find.byType(TextField).first, 'Test');
      // Motivasyon bildirimini kapat (izin penceresi açılmasın)
      await t.tap(find.byType(Switch).first);
      await t.pumpAndSettle();
      await t.tap(find.text('Hayal yolculuğuna başla'));
      await t.pumpAndSettle(const Duration(seconds: 2));
    }

    expect(find.text('Hayal ekle'), findsOneWidget);
    await t.tap(find.text('Hayal ekle'));
    await t.pumpAndSettle();
    await t.enterText(find.byType(TextField).last, 'Entegrasyon hayali');
    await t.tap(find.widgetWithText(FilledButton, 'Ekle'));
    await t.pumpAndSettle(const Duration(seconds: 2));
    expect(find.text('Hayal yolculuğum'), findsOneWidget);

    // Yeni adım ekle ve tamamla
    await t.tap(find.text('Adım ekle'));
    await t.pumpAndSettle();
    await t.enterText(find.byType(TextField).last, 'İlk somut adım');
    await t.tap(find.text('Kaydet'));
    await t.pumpAndSettle();
    await t.tap(find.bySemanticsLabel('Adım 2'));
    await t.pumpAndSettle(const Duration(seconds: 2));
    expect(find.textContaining('2/2 adım'), findsOneWidget);

    // Bahçede görünür
    await t.pageBack();
    await t.pumpAndSettle();
    await t.tap(find.text('Bahçem'));
    await t.pump(const Duration(seconds: 2));
    expect(find.text('Entegrasyon hayali'), findsWidgets);
  });
}
