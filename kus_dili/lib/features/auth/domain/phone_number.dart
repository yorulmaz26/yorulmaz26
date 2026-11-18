/// Doğrulanmış, E.164 biçiminde telefon numarası (ör. +905321234567).
final class PhoneNumber {
  const PhoneNumber._(this.e164);

  /// E.164 biçimi: '+' ve 8-15 rakam.
  final String e164;

  static final RegExp _separators = RegExp(r'[\s\-(). ]');
  static final RegExp _digits = RegExp(r'^\d+$');

  /// Kullanıcı girdisini ayrıştırır; geçersizse `null`.
  ///
  /// Türkiye için kabul edilen biçimler (yalnızca 5 ile başlayan mobil
  /// numaralar SMS alabilir):
  /// `0532 123 45 67`, `532 123 45 67`, `90 532 123 45 67`,
  /// `+90 532 123 45 67`, `0090 532 123 45 67`.
  /// Diğer ülkeler `+` veya `00` ile uluslararası biçimde yazılmalıdır.
  static PhoneNumber? tryParse(String input) {
    var s = input.trim().replaceAll(_separators, '');
    if (s.isEmpty) return null;
    var international = false;
    if (s.startsWith('+')) {
      international = true;
      s = s.substring(1);
    } else if (s.startsWith('00')) {
      international = true;
      s = s.substring(2);
    }
    if (!_digits.hasMatch(s)) return null;

    if (!international) {
      if (s.length == 11 && s.startsWith('05')) {
        s = '9$s';
      } else if (s.length == 10 && s.startsWith('5')) {
        s = '90$s';
      } else if (!(s.length == 12 && s.startsWith('905'))) {
        return null;
      }
    }
    if (s.startsWith('0')) return null; // ülke kodu 0 ile başlamaz
    if (s.length < 8 || s.length > 15) return null;
    if (s.startsWith('90')) {
      final national = s.substring(2);
      if (national.length != 10 || !national.startsWith('5')) return null;
    }
    return PhoneNumber._('+$s');
  }

  bool get isTurkish => e164.startsWith('+90');

  /// Gösterim: "+90 532 123 45 67" (Türkiye), diğerleri E.164.
  String get display {
    if (!isTurkish) return e164;
    final n = e164.substring(3);
    return '+90 ${n.substring(0, 3)} ${n.substring(3, 6)} '
        '${n.substring(6, 8)} ${n.substring(8)}';
  }

  @override
  bool operator ==(Object other) => other is PhoneNumber && other.e164 == e164;

  @override
  int get hashCode => e164.hashCode;

  @override
  String toString() => e164;
}
