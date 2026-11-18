import 'package:fake_cloud_firestore/fake_cloud_firestore.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/core/firebase/firebase_bootstrap.dart';
import 'package:kus_dili/features/learn/domain/progress.dart';
import 'package:material_ui/material_ui.dart';

import '../helpers/fake_auth_repository.dart';
import '../helpers/fake_learn.dart';
import '../helpers/test_app.dart';

void main() {
  testWidgets('Öğrenme modülü hesapsız ve Firebase olmadan açılır', (t) async {
    tallScreen(t);
    final progress = InMemoryProgressStore()
      ..value = const LearnProgress(xp: 130, correct: 9, answered: 12);
    final speaker = FakeSpeaker();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(),
        db: FakeFirebaseFirestore(),
        firebase: FirebaseStatus.unavailable(Exception('yapılandırılmadı')),
        progress: progress,
        speaker: speaker,
      ),
    );
    await settle(t);
    await t.tap(find.text('Kuş dilini öğren (çevrimdışı)'));
    await settle(t);
    expect(find.text('Seviye 2'), findsOneWidget);
    expect(find.textContaining('130 XP · doğruluk %75'), findsOneWidget);

    // Telaffuz.
    await t.tap(find.byKey(const Key('openPronounce')));
    await settle(t);
    expect(find.text('Segenigi segevigiyogorugum'), findsOneWidget);
    await t.tap(find.byKey(const Key('speakKus')));
    expect(speaker.spoken.last, 'Segenigi segevigiyogorugum');
    await t.pageBack();
    await settle(t);

    // Ders açılır; bir soruyu cevaplayıp ilerleme kaydedilir.
    await t.tap(find.byKey(const Key('lesson-temel')));
    await settle(t);
    expect(find.byKey(const Key('check')), findsOneWidget);
    expect(find.byKey(const Key('prompt')), findsOneWidget);

    await t.pumpWidget(const SizedBox());
  });

  testWidgets('Ders bitince ilerleme kaydedilir ve ana sayfada görünür', (
    t,
  ) async {
    tallScreen(t);
    final progress = InMemoryProgressStore();
    await t.pumpWidget(
      testApp(
        auth: FakeAuthRepository(),
        db: FakeFirebaseFirestore(),
        firebase: FirebaseStatus.unavailable(Exception('yok')),
        progress: progress,
      ),
    );
    await settle(t);
    await t.tap(find.text('Kuş dilini öğren (çevrimdışı)'));
    await settle(t);
    await t.tap(find.byKey(const Key('openQuiz')));
    await settle(t);
    // 10 soruyu cevapsız geç (yanlış sayılır): seçmeli için ilk seçenek,
    // yazma için boş, cümle için boş.
    for (var i = 0; i < 10; i++) {
      final opt = find.byKey(const Key('opt-0'));
      if (opt.evaluate().isNotEmpty) await t.tap(opt);
      await t.pump();
      await t.tap(find.byKey(const Key('check')));
      await t.pump();
      await t.tap(find.byKey(const Key('next')));
      await settle(t, 3);
    }
    expect(find.byKey(const Key('learnScore')), findsOneWidget);
    expect(progress.value.answered, 10);
    expect(progress.value.lastActiveDay, isNotNull);
    await t.tap(find.byKey(const Key('learnDone')));
    await settle(t);
    expect(find.byKey(const Key('learnStats')), findsOneWidget);
    expect(find.text(' 1 gün'), findsOneWidget);
    await t.pumpWidget(const SizedBox());
  });
}
