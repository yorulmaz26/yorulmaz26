import 'exceptions.dart';
import 'turkish_text.dart';

/// Bir kuş dili varyantı: her sesli harften sonra eklenecek heceyi tanımlar.
///
/// Kural bir **şablon** ile ifade edilir; `{v}` yer tutucusu o anki sesli
/// harfin kendisiyle değiştirilir:
///
/// | Varyant | Şablon  | Örnek          |
/// |---------|---------|----------------|
/// | G'li    | `g{v}`  | a → a**ga**    |
/// | F'li    | `f{v}`  | a → a**fa**    |
/// | C'li    | `c{v}`  | a → a**ca**    |
/// | Özel    | ör. `z{v}`, `p{v}r` | a → a**za**, a**par** |
///
/// Ayrıca sesli harf bazında farklı şablon tanımlanabilir
/// ([vowelOverrides]), ör. `{'a': 'v{v}'}`.
///
/// Neden ters dönüşüm her zaman tekildir: kodlanmış metin, her kümenin
/// kendi kodunun art arda eklenmesidir. Sessiz harfler kendileridir; sesli
/// harf kodu o sesli harfle başlar. Çözücü soldan sağa okurken her kümede
/// tek bir seçenek vardır (sesliyse arkasındaki beklenen heceyi atla), bu
/// yüzden geçerli bir kuş dili metninin yalnızca bir Türkçe karşılığı
/// vardır. Şablonun en az bir sessiz harf içermesi zorunluluğu, kuş dili
/// metninin normal Türkçeden ayırt edilebilmesini (tespit) sağlar.
final class KusDiliVariant {
  const KusDiliVariant._({
    required this.id,
    required this.name,
    required this.template,
    this.vowelOverrides = const {},
  });

  /// Kullanıcı tanımlı varyant. Kural geçersizse [KusDiliRuleException].
  factory KusDiliVariant.custom({
    required String id,
    required String name,
    required String template,
    Map<String, String> vowelOverrides = const {},
  }) {
    final cleanId = id.trim();
    if (!RegExp(r'^[a-z0-9_-]{1,32}$').hasMatch(cleanId)) {
      throw const KusDiliRuleException(
        'Kimlik 1-32 karakter olmalı ve yalnızca a-z, 0-9, _ ve - içermeli.',
      );
    }
    if (presets.any((p) => p.id == cleanId)) {
      throw KusDiliRuleException('"$cleanId" hazır bir varyantın kimliği.');
    }
    final cleanName = name.trim();
    if (cleanName.isEmpty || cleanName.length > 40) {
      throw const KusDiliRuleException('Ad 1-40 karakter olmalı.');
    }
    final t = TurkishText.lower(template.trim());
    validateTemplate(t);
    final overrides = <String, String>{};
    vowelOverrides.forEach((vowel, tpl) {
      final v = TurkishText.lower(vowel.trim());
      if (!TurkishText.baseVowels.contains(v)) {
        throw KusDiliRuleException('"$vowel" bir Türkçe sesli harf değil.');
      }
      final lt = TurkishText.lower(tpl.trim());
      validateTemplate(lt);
      overrides[v] = lt;
    });
    return KusDiliVariant._(
      id: cleanId,
      name: cleanName,
      template: t,
      vowelOverrides: Map.unmodifiable(overrides),
    );
  }

  /// Kaydedilmiş JSON'dan varyant üretir. Hazır varyantlar kimlikten tanınır.
  factory KusDiliVariant.fromJson(Map<String, Object?> json) {
    final id = json['id'];
    if (id is! String) {
      throw const KusDiliRuleException('Varyant JSON\'unda "id" yok.');
    }
    final preset = KusDiliVariant.preset(id);
    if (preset != null) return preset;
    final rawOverrides = json['vowelOverrides'];
    return KusDiliVariant.custom(
      id: id,
      name: json['name'] as String? ?? id,
      template: json['template'] as String? ?? '',
      vowelOverrides: rawOverrides is Map
          ? rawOverrides.map((k, v) => MapEntry(k.toString(), v.toString()))
          : const {},
    );
  }

  static const KusDiliVariant g = KusDiliVariant._(
    id: 'g',
    name: "G'li kuş dili",
    template: 'g{v}',
  );
  static const KusDiliVariant f = KusDiliVariant._(
    id: 'f',
    name: "F'li kuş dili",
    template: 'f{v}',
  );
  static const KusDiliVariant c = KusDiliVariant._(
    id: 'c',
    name: "C'li kuş dili",
    template: 'c{v}',
  );

  static const List<KusDiliVariant> presets = [g, f, c];

  static KusDiliVariant? preset(String id) {
    for (final p in presets) {
      if (p.id == id) return p;
    }
    return null;
  }

  /// Benzersiz kimlik (hazırlar için 'g', 'f', 'c').
  final String id;

  /// Kullanıcıya gösterilen ad.
  final String name;

  /// Varsayılan şablon (küçük harf), ör. `g{v}`.
  final String template;

  /// Sesli harfe özel şablonlar (anahtar: temel sesli, küçük harf).
  final Map<String, String> vowelOverrides;

  bool get isPreset => preset(id) != null;

  static const int maxLiteralLetters = 6;
  static const int maxPlaceholders = 2;
  static final RegExp _templateShape = RegExp(r'^(?:[a-zçğıöşüqwx]|\{v\})+$');

  /// Şablonu doğrular; geçersizse [KusDiliRuleException] fırlatır.
  /// Şablon küçük harfe çevrilmiş olarak verilmelidir.
  static void validateTemplate(String template) {
    if (!_templateShape.hasMatch(template)) {
      throw KusDiliRuleException(
        '"$template" geçersiz. Yalnızca Türkçe harfler ve {v} yer tutucusu '
        'kullanılabilir.',
      );
    }
    final placeholders = '{v}'.allMatches(template).length;
    final literals = template.replaceAll('{v}', '');
    if (placeholders > maxPlaceholders) {
      throw const KusDiliRuleException(
        'Şablonda en fazla $maxPlaceholders adet {v} olabilir.',
      );
    }
    if (literals.isEmpty || literals.length > maxLiteralLetters) {
      throw const KusDiliRuleException(
        'Şablonda 1-$maxLiteralLetters harf olmalı.',
      );
    }
    final hasConsonant = literals
        .split('')
        .any((ch) => !TurkishText.vowels.contains(ch));
    if (!hasConsonant) {
      throw const KusDiliRuleException(
        'Şablon en az bir sessiz harf içermeli; aksi hâlde kuş dili normal '
        'Türkçeden ayırt edilemez.',
      );
    }
  }

  /// Verilen sesli kümeden sonra eklenecek hece.
  ///
  /// [shout] doğruysa (tamamı büyük harf yazılmış kelime) hece büyük harfle
  /// üretilir: "ALİ" → "AGALİGİ"; aksi hâlde küçük harfle: "Ali" → "Agaligi".
  String infixFor(String vowelCluster, {required bool shout}) {
    final tpl = vowelOverrides[TurkishText.fold(vowelCluster)] ?? template;
    final echo = shout
        ? TurkishText.upper(vowelCluster)
        : TurkishText.lower(vowelCluster);
    final b = StringBuffer();
    var i = 0;
    while (i < tpl.length) {
      if (tpl.startsWith('{v}', i)) {
        b.write(echo);
        i += 3;
      } else {
        final ch = tpl[i];
        b.write(shout ? TurkishText.upper(ch) : ch);
        i++;
      }
    }
    return b.toString();
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'name': name,
    'template': template,
    if (vowelOverrides.isNotEmpty) 'vowelOverrides': vowelOverrides,
  };

  @override
  bool operator ==(Object other) =>
      other is KusDiliVariant &&
      other.id == id &&
      other.template == template &&
      _mapEquals(other.vowelOverrides, vowelOverrides);

  @override
  int get hashCode => Object.hash(
    id,
    template,
    Object.hashAllUnordered(
      vowelOverrides.entries.map((e) => Object.hash(e.key, e.value)),
    ),
  );

  @override
  String toString() => 'KusDiliVariant($id, $template)';

  static bool _mapEquals(Map<String, String> a, Map<String, String> b) {
    if (a.length != b.length) return false;
    for (final e in a.entries) {
      if (b[e.key] != e.value) return false;
    }
    return true;
  }
}
