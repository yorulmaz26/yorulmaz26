import 'decode_result.dart';
import 'turkish_text.dart';
import 'variant.dart';

/// Türkçe ↔ Kuş Dili dönüştürücü. Durumsuzdur, eşzamanlıdır, ağ kullanmaz.
///
/// ```dart
/// const codec = KusDiliCodec(KusDiliVariant.g);
/// codec.encode('Merhaba');            // 'Megerhagabaga'
/// codec.decode('Megerhagabaga').text; // 'Merhaba'
/// ```
///
/// Korunan şeyler:
/// * Büyük/küçük harf: "Ali" → "Agaligi", "ALİ" → "AGALİGİ".
/// * Noktalama, boşluk (sekme, çoklu boşluk, satır sonu), rakamlar.
/// * Emojiler (ZWJ dizileri, ten rengi, bayraklar dahil) — asla bölünmez.
/// * [preserveSpecialSegments] açıkken URL'ler, e-postalar ve @kullanıcı
///   adları olduğu gibi kalır (bağlantılar bozulmasın diye).
final class KusDiliCodec {
  const KusDiliCodec(this.variant, {this.preserveSpecialSegments = true});

  final KusDiliVariant variant;
  final bool preserveSpecialSegments;

  // --- Korunan bölümler -----------------------------------------------------

  // Eşleşme yalnızca harf/rakamla bitişik OLMAYAN bir konumda başlayabilir.
  // Dönüşüm harfleri yalnızca harflerle değiştirdiği için bu sınırlar
  // kodlanmış metinde de aynı yerde kalır; çözücü aynı bölümleri bulur.
  static final RegExp _protected = RegExp(
    // URL
    r'(?<![\p{L}\p{N}])(?:https?://|www\.)[^\s<>"]+'
    // e-posta
    r'|(?<![\p{L}\p{N}._%+\-])[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+'
    r'(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}(?![\p{L}\p{N}])'
    // @kullanıcı
    r'|(?<![\p{L}\p{N}_@])@[\p{L}\p{N}_]+(?:\.[\p{L}\p{N}_]+)*',
    unicode: true,
  );

  static final RegExp _trailingPunct = RegExp(r'''[.,!?;:)\]}'"»…]+$''');

  // --- Kodlama (Türkçe → Kuş Dili) -----------------------------------------

  /// Türkçe metni kuş diline çevirir. Her girdi için tanımlıdır, hata
  /// fırlatmaz.
  String encode(String input) {
    if (input.isEmpty) return input;
    final pieces = _split(input, preserveSpecialSegments);
    final shout = _shoutFlags(pieces);
    final out = StringBuffer();
    for (var i = 0; i < pieces.length; i++) {
      final p = pieces[i];
      out.write(p.text);
      if (!p.isProtected && TurkishText.isVowel(p.text)) {
        out.write(variant.infixFor(p.text, shout: shout[i]));
      }
    }
    return out.toString();
  }

  // --- Çözme (Kuş Dili → Türkçe) -------------------------------------------

  /// Kuş dili metnini Türkçeye çevirir.
  ///
  /// Algoritma deterministiktir: soldan sağa her kümede yalnızca bir karar
  /// vardır. Sesli harf görülünce arkasında bu varyantın beklediği hece
  /// (büyük/küçük harf duyarsız) aranır; varsa atlanır. Yoksa sesli harf
  /// olduğu gibi bırakılır ve bir [DecodeIssue] kaydedilir.
  ///
  /// Bu sayede naif "aga → a" metin değiştirmenin hataları oluşmaz:
  /// "Egegege" → "Ege", "gagagaga" → "gaga", "sigigagaraga" → "sigara".
  DecodeResult decode(String input) {
    if (input.isEmpty) {
      return const DecodeResult(
        text: '',
        issues: [],
        vowelCount: 0,
        matchedVowelCount: 0,
      );
    }
    final pieces = _split(input, preserveSpecialSegments);
    final out = StringBuffer();
    final issues = <DecodeIssue>[];
    var vowels = 0;
    var matched = 0;

    var i = 0;
    while (i < pieces.length) {
      final p = pieces[i];
      out.write(p.text);
      if (p.isProtected) {
        final alt = _alternativeReading(p.text);
        if (alt != null) {
          issues.add(
            DecodeIssue(
              kind: DecodeIssueKind.ambiguousProtectedSegment,
              offset: p.offset,
              length: p.text.length,
              text: p.text,
            ),
          );
        }
        i++;
        continue;
      }
      if (!TurkishText.isVowel(p.text)) {
        i++;
        continue;
      }
      vowels++;
      final expected = TurkishText.clusters(
        variant.infixFor(p.text, shout: false),
      );
      var k = 0;
      while (k < expected.length) {
        final j = i + 1 + k;
        if (j >= pieces.length || pieces[j].isProtected) break;
        if (TurkishText.fold(pieces[j].text) != TurkishText.fold(expected[k])) {
          break;
        }
        k++;
      }
      if (k == expected.length) {
        matched++;
        i += 1 + k;
      } else {
        final reachedEnd = i + 1 + k >= pieces.length;
        issues.add(
          DecodeIssue(
            kind: reachedEnd && k > 0
                ? DecodeIssueKind.truncatedInfix
                : DecodeIssueKind.missingInfix,
            offset: p.offset,
            length: p.text.length,
            text: p.text,
          ),
        );
        i++;
      }
    }
    return DecodeResult(
      text: out.toString(),
      issues: List.unmodifiable(issues),
      vowelCount: vowels,
      matchedVowelCount: matched,
    );
  }

  /// Metnin bu varyantta yazılmış kuş dili olma olasılığı (0-1).
  double confidence(String input) => decode(input).confidence;

  /// Metin bu varyantta kuş dili gibi görünüyor mu?
  ///
  /// En az [minVowels] sesli harf ve en az [threshold] oranında kurala uyum
  /// gerekir. Kısa metinlerde (ör. "gaga") yanlış pozitif olabileceği için
  /// mesajlaşmada tespit yerine mesajla birlikte taşınan varyant bilgisi
  /// kullanılır.
  bool looksEncoded(String input, {double threshold = 0.9, int minVowels = 2}) {
    final r = decode(input);
    return r.vowelCount >= minVowels && r.confidence >= threshold;
  }

  /// Verilen varyantlar arasından metne en iyi uyanı bulur.
  /// Hiçbiri [threshold] eşiğini geçmezse `null`.
  static KusDiliVariant? detectVariant(
    String input, {
    Iterable<KusDiliVariant> candidates = KusDiliVariant.presets,
    double threshold = 0.9,
    int minVowels = 2,
  }) {
    KusDiliVariant? best;
    var bestScore = -1.0;
    for (final v in candidates) {
      final r = KusDiliCodec(v).decode(input);
      if (r.vowelCount < minVowels) continue;
      if (r.confidence > bestScore) {
        bestScore = r.confidence;
        best = v;
      }
    }
    return bestScore >= threshold ? best : null;
  }

  // --- İç yardımcılar ------------------------------------------------------

  /// Korunan bir bölüm, korumasız okunduğunda da kusursuz bir kuş dili
  /// kodlaması mı (ve o kodlamanın aslı korunacak bir bölüm değil mi)?
  /// Örn. "aga@x.ogo" hem bir e-posta hem de "a@x.o" metninin kodlaması
  /// olabilir. Bu gerçek bir belirsizliktir; sonuç bildirilir.
  String? _alternativeReading(String segment) {
    final plain = KusDiliCodec(variant, preserveSpecialSegments: false);
    final r = plain.decode(segment);
    if (!r.isValid || r.vowelCount == 0) return null;
    // Aslı kodlanırken korunmuş olsaydı kodlama değişmezdi; o hâlde
    // alternatif ancak aslı korunmayan bir metinse mümkündür.
    if (encode(r.text) != segment) return null;
    return r.text;
  }

  static List<_Piece> _split(String input, bool preserve) {
    final pieces = <_Piece>[];
    void addPlain(int start, int end) {
      var offset = start;
      for (final c in TurkishText.clusters(input.substring(start, end))) {
        pieces.add(_Piece(c, offset, isProtected: false));
        offset += c.length;
      }
    }

    if (!preserve) {
      addPlain(0, input.length);
      return pieces;
    }
    var cursor = 0;
    for (final m in _protected.allMatches(input)) {
      var end = m.end;
      final matchText = m.group(0)!;
      if (matchText.startsWith('http') || matchText.startsWith('www.')) {
        final trail = _trailingPunct.firstMatch(matchText);
        if (trail != null) end -= trail.group(0)!.length;
      }
      if (end <= m.start) continue;
      if (m.start > cursor) addPlain(cursor, m.start);
      pieces.add(
        _Piece(input.substring(m.start, end), m.start, isProtected: true),
      );
      cursor = end;
    }
    if (cursor < input.length) addPlain(cursor, input.length);
    return pieces;
  }

  /// Her parça için "bağırma" (tamamı büyük harf kelime) bayrağı.
  ///
  /// * En az iki harfli ve tüm harfleri büyük olan kelime → büyük hece.
  /// * Tek harfli büyük kelime ("O", "A") en yakın çok harfli kelimeye uyar:
  ///   "O GELDİ" → "OGO GEGELDİGİ", "O geldi" → "Ogo gegeldigi".
  static List<bool> _shoutFlags(List<_Piece> pieces) {
    final flags = List<bool>.filled(pieces.length, false);
    // Kelime = ardışık, korunmayan harf kümeleri.
    final words = <(int start, int end, bool? shout)>[];
    var i = 0;
    while (i < pieces.length) {
      if (pieces[i].isProtected || !TurkishText.isLetter(pieces[i].text)) {
        i++;
        continue;
      }
      final start = i;
      while (i < pieces.length &&
          !pieces[i].isProtected &&
          TurkishText.isLetter(pieces[i].text)) {
        i++;
      }
      final letters = pieces.sublist(start, i).map((p) => p.text);
      final anyLower = letters.any(TurkishText.isLower);
      final anyUpper = letters.any(TurkishText.isUpper);
      final bool? shout;
      if (i - start >= 2) {
        shout = anyUpper && !anyLower;
      } else {
        shout = anyUpper ? null : false; // tek büyük harf: komşuya bak
      }
      words.add((start, i, shout));
    }
    for (var w = 0; w < words.length; w++) {
      var (start, end, shout) = words[w];
      if (shout == null) {
        bool? neighbour;
        for (var d = 1; d < words.length && neighbour == null; d++) {
          if (w - d >= 0 && words[w - d].$2 - words[w - d].$1 >= 2) {
            neighbour = words[w - d].$3;
          } else if (w + d < words.length &&
              words[w + d].$2 - words[w + d].$1 >= 2) {
            neighbour = words[w + d].$3;
          }
        }
        shout = neighbour ?? false;
      }
      for (var k = start; k < end; k++) {
        flags[k] = shout;
      }
    }
    return flags;
  }
}

final class _Piece {
  const _Piece(this.text, this.offset, {required this.isProtected});

  final String text;
  final int offset;
  final bool isProtected;
}
