/// Ters dönüşümde (Kuş Dili → Türkçe) karşılaşılan bir sorun türü.
enum DecodeIssueKind {
  /// Sesli harften sonra beklenen hece yok (ör. "Merhaba" kuş dili değil).
  missingInfix,

  /// Metin, beklenen hece tamamlanmadan bitti (ör. yazılmakta olan "Meg").
  truncatedInfix,

  /// Korunan bölüm (URL/e-posta/@kullanıcı) aynı zamanda geçerli bir kuş dili
  /// kodlaması olarak da okunabiliyor. Korunan yorum tercih edildi.
  ambiguousProtectedSegment,
}

/// Ters dönüşümdeki tek bir sorun.
final class DecodeIssue {
  const DecodeIssue({
    required this.kind,
    required this.offset,
    required this.length,
    required this.text,
  });

  final DecodeIssueKind kind;

  /// Girdi metnindeki UTF-16 konumu.
  final int offset;

  /// Girdi metnindeki UTF-16 uzunluğu.
  final int length;

  /// İlgili girdi parçası.
  final String text;

  @override
  String toString() => 'DecodeIssue($kind @$offset "$text")';
}

/// Ters dönüşüm sonucu.
final class DecodeResult {
  const DecodeResult({
    required this.text,
    required this.issues,
    required this.vowelCount,
    required this.matchedVowelCount,
  });

  /// Çözülmüş Türkçe metin. Hatalı bölümler olduğu gibi bırakılır
  /// (hoşgörülü çözme), böylece kullanıcıya her zaman bir sonuç gösterilir.
  final String text;

  final List<DecodeIssue> issues;

  /// Çözülen (korunmayan) metindeki sesli harf sayısı.
  final int vowelCount;

  /// Arkasından beklenen hece gelen sesli harf sayısı.
  final int matchedVowelCount;

  /// Girdi bu varyantta kusursuz bir kuş dili metni mi?
  /// (Belirsizlik uyarıları geçerliliği bozmaz.)
  bool get isValid =>
      issues.every((i) => i.kind == DecodeIssueKind.ambiguousProtectedSegment);

  /// 0-1 arası: sesli harflerin ne kadarının kurala uyduğu.
  /// Hiç sesli yoksa 0 döner (kuş dili olduğuna dair kanıt yok).
  double get confidence => vowelCount == 0 ? 0 : matchedVowelCount / vowelCount;
}
