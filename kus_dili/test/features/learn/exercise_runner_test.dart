import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/learn/application/learn_providers.dart';
import 'package:kus_dili/features/learn/domain/exercise.dart';
import 'package:kus_dili/features/learn/presentation/exercise_runner.dart';
import 'package:material_ui/material_ui.dart';

import '../../helpers/fake_learn.dart';

void main() {
  final gen = ExerciseGenerator(random: Random(11));

  Future<(FakeSpeaker, List<(int, int)>)> pump(
    WidgetTester t,
    List<Exercise> ex,
  ) async {
    final speaker = FakeSpeaker();
    final done = <(int, int)>[];
    t.view.physicalSize = const Size(1080, 2340);
    t.view.devicePixelRatio = 2.25;
    addTearDown(t.view.reset);
    await t.pumpWidget(
      ProviderScope(
        overrides: [speakerProvider.overrideWithValue(speaker)],
        child: MaterialApp(
          home: Scaffold(
            body: ExerciseRunner(
              exercises: ex,
              onFinished: (c, total) async => done.add((c, total)),
            ),
          ),
        ),
      ),
    );
    return (speaker, done);
  }

  testWidgets('Dört alıştırma türü doğru cevaplanır', (t) async {
    final a = gen.chooseKus('selam');
    final b = gen.chooseTurkish('anne', ['anne', 'baba', 'dede', 'nine']);
    final c = gen.typeKus('merhaba');
    final d = gen.buildSentence('Seni çok özledim');
    final (speaker, done) = await pump(t, [a, b, c, d]);

    expect(find.text('selam'), findsOneWidget);
    await t.tap(find.byKey(Key('opt-${a.correct}')));
    await t.pump();
    await t.tap(find.byKey(const Key('check')));
    await t.pump();
    expect(find.byKey(const Key('feedbackOk')), findsOneWidget);
    await t.tap(find.byKey(const Key('speakAnswer')));
    expect(speaker.spoken.last, 'segelagam');
    await t.tap(find.byKey(const Key('next')));
    await t.pump();

    expect(find.text('agannege'), findsOneWidget);
    await t.tap(find.byKey(const Key('speakPrompt')));
    expect(speaker.spoken.last, 'agannege');
    await t.tap(find.byKey(Key('opt-${b.correct}')));
    await t.pump();
    await t.tap(find.byKey(const Key('check')));
    await t.pump();
    expect(find.byKey(const Key('feedbackOk')), findsOneWidget);
    await t.tap(find.byKey(const Key('next')));
    await t.pump();

    await t.enterText(find.byKey(const Key('typeAnswer')), 'MEGERHAGABAGA');
    await t.tap(find.byKey(const Key('check')));
    await t.pump();
    expect(find.byKey(const Key('feedbackOk')), findsOneWidget);
    await t.tap(find.byKey(const Key('next')));
    await t.pump();

    for (final word in d.expected) {
      final i = d.tokens.indexOf(word);
      await t.tap(find.byKey(Key('tok-$i')));
      await t.pump();
    }
    await t.tap(find.byKey(const Key('check')));
    await t.pump();
    expect(find.byKey(const Key('feedbackOk')), findsOneWidget);
    await t.tap(find.byKey(const Key('next')));
    await t.pumpAndSettle();

    expect(find.text('4 / 4 doğru'), findsOneWidget);
    expect(find.text('+40 XP'), findsOneWidget);
    expect(done, [(4, 4)]);
  });

  testWidgets('Yanlış cevapta doğrusu ve ipucu gösterilir', (t) async {
    final c = gen.typeKus('merhaba');
    final (_, done) = await pump(t, [c]);
    await t.enterText(find.byKey(const Key('typeAnswer')), 'merhaba');
    await t.tap(find.byKey(const Key('check')));
    await t.pump();
    expect(find.byKey(const Key('feedbackWrong')), findsOneWidget);
    expect(find.text('Doğrusu: megerhagabaga'), findsOneWidget);
    expect(find.textContaining('"ge" gelmeli'), findsOneWidget);
    await t.tap(find.byKey(const Key('next')));
    await t.pumpAndSettle();
    expect(find.text('0 / 1 doğru'), findsOneWidget);
    expect(done, [(0, 1)]);
  });
}
