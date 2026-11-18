import 'dart:math';

import 'package:kus_dili_engine/kus_dili_engine.dart';

import 'curriculum.dart';

sealed class Exercise {
  const Exercise();

  /// Doğru cevap (gösterim ve telaffuz için).
  String get answer;

  /// Seslendirilecek metin.
  String get speakText;
}

/// Türkçe kelime → doğru kuş dili seçeneği.
final class ChooseKus extends Exercise {
  const ChooseKus({
    required this.turkish,
    required this.options,
    required this.correct,
  });
  final String turkish;
  final List<String> options;
  final int correct;
  @override
  String get answer => options[correct];
  @override
  String get speakText => answer;
}

/// Kuş dili → doğru Türkçe seçeneği.
final class ChooseTurkish extends Exercise {
  const ChooseTurkish({
    required this.kus,
    required this.options,
    required this.correct,
  });
  final String kus;
  final List<String> options;
  final int correct;
  @override
  String get answer => options[correct];
  @override
  String get speakText => kus;
}

/// Türkçeyi kuş dilinde yaz.
final class TypeKus extends Exercise {
  const TypeKus({
    required this.turkish,
    required this.expected,
    required this.variant,
  });
  final String turkish;
  final String expected;
  final KusDiliVariant variant;
  @override
  String get answer => expected;
  @override
  String get speakText => expected;
}

/// Karışık kuş dili kelimelerinden cümle kur.
final class BuildSentence extends Exercise {
  const BuildSentence({
    required this.turkish,
    required this.tokens,
    required this.expected,
  });
  final String turkish;

  /// Karıştırılmış kelimeler.
  final List<String> tokens;
  final List<String> expected;
  @override
  String get answer => expected.join(' ');
  @override
  String get speakText => answer;
}

/// Alıştırma üretici. Yanlış seçenekler öğretici hatalardan üretilir:
/// başka varyant (F'li/C'li), hece eksik, yanlış sesli tekrarı.
final class ExerciseGenerator {
  ExerciseGenerator({Random? random, this.variant = KusDiliVariant.g})
    : _rnd = random ?? Random();

  final Random _rnd;
  final KusDiliVariant variant;

  KusDiliCodec get _codec => KusDiliCodec(variant);

  String encode(String tr) => _codec.encode(tr);

  /// Öğretici yanlış kuş dili biçimleri.
  Set<String> distractorsFor(String tr) {
    final right = encode(tr);
    final out = <String>{};
    for (final v in KusDiliVariant.presets) {
      if (v != variant) out.add(KusDiliCodec(v).encode(tr));
    }
    // Hece eksik: ilk sesliden sonraki hece atlanmış.
    final firstVowel = RegExp('[aeıioöuüAEIİOÖUÜ]').firstMatch(tr);
    if (firstVowel != null) {
      final i = firstVowel.end;
      out.add(
        encode(tr.substring(0, i)).replaceRange(i, null, '') +
            encode(tr.substring(i)),
      );
    }
    // Yanlış sesli tekrarı: "aga" yerine "age".
    out.add(
      right.replaceFirstMapped(
        RegExp('([aeıioöuü])g([aeıioöuü])'),
        (m) => '${m[1]}g${m[1] == 'a' ? 'e' : 'a'}',
      ),
    );
    out
      ..remove(right)
      ..remove(tr);
    return out;
  }

  List<String> _options(
    String right,
    Iterable<String> wrong, {
    required int count,
  }) {
    final w = wrong.where((x) => x != right).toSet().toList()..shuffle(_rnd);
    final opts = [right, ...w.take(count - 1)]..shuffle(_rnd);
    return opts;
  }

  ChooseKus chooseKus(String tr) {
    final right = encode(tr);
    final opts = _options(right, distractorsFor(tr), count: 4);
    return ChooseKus(turkish: tr, options: opts, correct: opts.indexOf(right));
  }

  ChooseTurkish chooseTurkish(String tr, List<String> pool) {
    final wrong = pool.where((w) => w != tr).toList()..shuffle(_rnd);
    final opts = _options(tr, wrong, count: 4);
    return ChooseTurkish(
      kus: encode(tr),
      options: opts,
      correct: opts.indexOf(tr),
    );
  }

  TypeKus typeKus(String tr) =>
      TypeKus(turkish: tr, expected: encode(tr), variant: variant);

  BuildSentence buildSentence(String tr) {
    final expected = encode(tr).split(' ');
    final tokens = [...expected];
    if (tokens.length > 1) {
      do {
        tokens.shuffle(_rnd);
      } while (_same(tokens, expected));
    }
    return BuildSentence(turkish: tr, tokens: tokens, expected: expected);
  }

  static bool _same(List<String> a, List<String> b) {
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  /// Bir ders için karışık alıştırma dizisi.
  List<Exercise> forLesson(Lesson l, {int count = 8}) {
    final words = [...l.words]..shuffle(_rnd);
    final sentences = [...l.sentences]..shuffle(_rnd);
    final out = <Exercise>[];
    var w = 0;
    var s = 0;
    for (var i = 0; i < count; i++) {
      final word = words[w++ % words.length];
      out.add(switch (i % 4) {
        0 => chooseKus(word),
        1 => chooseTurkish(word, l.words),
        2 => typeKus(word),
        _ =>
          sentences.isEmpty
              ? typeKus(word)
              : buildSentence(sentences[s++ % sentences.length]),
      });
    }
    return out;
  }

  /// Tüm derslerden mini test.
  List<Exercise> quiz({int count = 10}) {
    final all = [for (final l in Curriculum.lessons) ...l.words];
    final sentences = [for (final l in Curriculum.lessons) ...l.sentences];
    all.shuffle(_rnd);
    sentences.shuffle(_rnd);
    return [
      for (var i = 0; i < count; i++)
        switch (i % 5) {
          0 || 3 => chooseKus(all[i]),
          1 => chooseTurkish(all[i], all),
          2 => typeKus(all[i]),
          _ => buildSentence(sentences[i % sentences.length]),
        },
    ];
  }
}

/// Yazılan cevabı kontrol eder. Büyük/küçük harf ve fazla boşluk önemsizdir;
/// kuş dili kuralına uyan her yazım kabul edilir (ör. büyük harfli hece).
final class AnswerChecker {
  const AnswerChecker._();

  static String _norm(String s) =>
      TurkishText.lower(s.trim())
          .replaceAll(RegExp(r'\s+'), ' ')
          .replaceAll(RegExp(r'[.,!?]+$'), '');

  static bool typed(TypeKus e, String input) {
    if (_norm(input) == _norm(e.expected)) return true;
    final r = KusDiliCodec(e.variant).decode(input.trim());
    return r.isValid && r.vowelCount > 0 && _norm(r.text) == _norm(e.turkish);
  }

  /// Yanlışsa ilk hatalı yer için ipucu.
  static String? hint(TypeKus e, String input) {
    final r = KusDiliCodec(e.variant).decode(input.trim());
    if (r.issues.isEmpty) {
      return r.vowelCount == 0
          ? 'Hiç sesli harf yok. Her sesliden sonra hece eklemeyi unutma.'
          : 'Kurala uygun ama yanlış kelime: "${r.text}".';
    }
    final i = r.issues.first;
    return '"${i.text}" sesli harfinden sonra '
        '"${e.variant.infixFor(i.text, shout: false)}" gelmeli.';
  }

  static bool built(BuildSentence e, List<String> picked) =>
      picked.length == e.expected.length &&
      Iterable<int>.generate(picked.length)
          .every((i) => picked[i] == e.expected[i]);
}
