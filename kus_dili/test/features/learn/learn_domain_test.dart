import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/learn/domain/curriculum.dart';
import 'package:kus_dili/features/learn/domain/exercise.dart';
import 'package:kus_dili/features/learn/domain/progress.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';

void main() {
  final gen = ExerciseGenerator(random: Random(7));

  group('İçerik', () {
    test('her dersin kuş dili karşılığı motorla tersine çevrilebilir', () {
      const codec = KusDiliCodec(KusDiliVariant.g);
      for (final l in Curriculum.lessons) {
        expect(l.words.length, greaterThanOrEqualTo(4), reason: l.id);
        for (final t in [...l.words, ...l.sentences]) {
          final k = codec.encode(t);
          expect(codec.decode(k).text, t, reason: t);
        }
      }
      expect(Curriculum.byId('yok').id, 'temel');
    });
  });

  group('Alıştırma üretici', () {
    test('Türkçe → kuş dili: 4 farklı seçenek, biri doğru', () {
      for (final w in ['selam', 'merhaba', 'anne', 'kuş', 'güneş']) {
        final e = gen.chooseKus(w);
        expect(e.options.toSet().length, e.options.length, reason: w);
        expect(e.options.length, inInclusiveRange(3, 4));
        expect(e.answer, gen.encode(w));
        expect(e.options.where((o) => o == gen.encode(w)).length, 1);
      }
    });

    test('Yanlış seçenekler öğretici hatalar', () {
      final d = gen.distractorsFor('selam');
      expect(d, contains('Sefelafam'.toLowerCase()));
      expect(d, contains('secelacam'));
      expect(d, contains('selagam'), reason: 'ilk hece eksik');
      expect(d, isNot(contains('segelagam')));
    });

    test('Kuş dili → Türkçe', () {
      final e = gen.chooseTurkish('anne', Curriculum.byId('aile').words);
      expect(e.kus, 'agannege');
      expect(e.answer, 'anne');
      expect(e.options.toSet().length, 4);
    });

    test('Cümle kurma: kelimeler karışık ama aynı küme', () {
      final e = gen.buildSentence('Seni çok özledim');
      expect(e.expected, ['Segenigi', 'çogok', 'ögözlegedigim']);
      expect(e.tokens.toSet(), e.expected.toSet());
      expect(e.tokens, isNot(e.expected));
      expect(AnswerChecker.built(e, e.expected), isTrue);
      expect(AnswerChecker.built(e, e.tokens), isFalse);
    });

    test('Ders ve test dizileri', () {
      final l = gen.forLesson(Curriculum.byId('duygu'));
      expect(l, hasLength(8));
      expect(l.whereType<ChooseKus>(), isNotEmpty);
      expect(l.whereType<ChooseTurkish>(), isNotEmpty);
      expect(l.whereType<TypeKus>(), isNotEmpty);
      expect(l.whereType<BuildSentence>(), isNotEmpty);
      expect(gen.quiz(), hasLength(10));
    });

    test('F\'li varyantla pratik', () {
      final f = ExerciseGenerator(random: Random(1), variant: KusDiliVariant.f);
      expect(f.typeKus('selam').expected, 'sefelafam');
    });
  });

  group('Cevap denetimi', () {
    final e = gen.typeKus('merhaba');
    test('doğru, büyük harf, boşluk ve noktalama farkı kabul', () {
      expect(AnswerChecker.typed(e, 'megerhagabaga'), isTrue);
      expect(AnswerChecker.typed(e, '  MEGERHAGABAGA! '), isTrue);
      expect(AnswerChecker.typed(e, 'MegerHagabaga'), isTrue);
    });
    test('yanlışlar reddedilir ve ipucu verilir', () {
      expect(AnswerChecker.typed(e, 'merhaba'), isFalse);
      expect(AnswerChecker.hint(e, 'merhaba'), contains('"ge"'));
      expect(AnswerChecker.typed(e, 'megerhagabag'), isFalse);
      expect(AnswerChecker.typed(e, 'segelagam'), isFalse);
      expect(AnswerChecker.hint(e, 'segelagam'), contains('selam'));
      expect(AnswerChecker.hint(e, 'xyz'), contains('sesli harf yok'));
    });
  });

  group('İlerleme', () {
    final d1 = DateTime(2026, 11, 18, 20);
    test('XP, seviye, doğruluk, ders tamamlama', () {
      var p = const LearnProgress();
      p = p.record(correct: 7, total: 8, now: d1, lessonId: 'selam');
      expect(p.xp, 70);
      expect(p.level, 1);
      expect(p.completedLessons, {'selam'});
      p = p.record(correct: 5, total: 8, now: d1, lessonId: 'aile');
      expect(p.completedLessons, {'selam'}, reason: '%80 altı geçmez');
      expect(p.xp, 120);
      expect(p.level, 2);
      expect(p.levelProgress, closeTo(0.2, 1e-9));
      expect(p.accuracyPercent, 75);
    });

    test('günlük seri', () {
      var p = const LearnProgress().record(correct: 1, total: 1, now: d1);
      expect(p.streak, 1);
      p = p.record(correct: 1, total: 1, now: d1.add(const Duration(hours: 2)));
      expect(p.streak, 1, reason: 'aynı gün');
      p = p.record(correct: 1, total: 1, now: d1.add(const Duration(days: 1)));
      expect(p.streak, 2);
      expect(p.streakOn(d1.add(const Duration(days: 2))), 2);
      expect(p.streakOn(d1.add(const Duration(days: 3))), 0);
      p = p.record(correct: 1, total: 1, now: d1.add(const Duration(days: 5)));
      expect(p.streak, 1, reason: 'seri koptu');
    });

    test('en iyi test puanı ve JSON', () {
      var p = const LearnProgress().record(
        correct: 7,
        total: 10,
        now: d1,
        quiz: true,
      );
      p = p.record(correct: 5, total: 10, now: d1, quiz: true);
      expect(p.bestQuiz, 70);
      final back = LearnProgress.fromJson(p.toJson());
      expect(back.bestQuiz, 70);
      expect(back.xp, p.xp);
      expect(back.lastActiveDay, '2026-11-18');
    });
  });
}
