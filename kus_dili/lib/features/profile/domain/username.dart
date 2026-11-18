/// Kullanıcı adı kuralları. Firestore güvenlik kurallarıyla
/// (firebase/firestore.rules → validUsername) birebir aynı olmalıdır.
abstract final class Username {
  static const minLength = 3;
  static const maxLength = 20;

  static final RegExp _shape = RegExp(r'^[a-z][a-z0-9_.]{2,19}$');

  static const Set<String> reserved = {
    'admin',
    'administrator',
    'root',
    'system',
    'support',
    'destek',
    'kusdili',
    'kus_dili',
    'kus.dili',
    'moderator',
    'yonetici',
    'official',
    'resmi',
    'help',
    'yardim',
    'null',
    'undefined',
  };

  /// Girdiyi normalize eder: boşlukları atar, '@' önekini kaldırır,
  /// küçük harfe çevirir. Kullanıcı adları ASCII olduğundan 'I' ve 'İ'
  /// 'i' olur; 'ı' ise geçersiz karakter olarak kalır ve uyarı verilir.
  static String normalize(String input) {
    var s = input.trim();
    if (s.startsWith('@')) s = s.substring(1);
    return s.replaceAll('İ', 'i').toLowerCase();
  }

  /// Hata mesajı; geçerliyse `null`. [value] normalize edilmiş olmalıdır.
  static String? validate(String value) {
    if (value.length < minLength) {
      return 'Kullanıcı adı en az $minLength karakter olmalı.';
    }
    if (value.length > maxLength) {
      return 'Kullanıcı adı en fazla $maxLength karakter olabilir.';
    }
    if (RegExp(r'[çğıöşü]').hasMatch(value)) {
      return 'Türkçe karakter kullanılamaz (ç→c, ğ→g, ı→i, ö→o, ş→s, ü→u).';
    }
    if (!RegExp(r'^[a-z]').hasMatch(value)) {
      return 'Kullanıcı adı bir harfle başlamalı.';
    }
    if (!_shape.hasMatch(value)) {
      return 'Yalnızca a-z, 0-9, alt çizgi (_) ve nokta (.) kullanılabilir.';
    }
    if (value.contains('..')) return 'Art arda iki nokta kullanılamaz.';
    if (value.endsWith('.')) return 'Kullanıcı adı nokta ile bitemez.';
    if (reserved.contains(value)) return 'Bu kullanıcı adı ayrılmış.';
    return null;
  }
}

abstract final class DisplayName {
  static const maxLength = 40;

  static String? validate(String value) {
    final v = value.trim();
    if (v.isEmpty) return 'Adınızı girin.';
    if (v.length > maxLength) {
      return 'Ad en fazla $maxLength karakter olabilir.';
    }
    return null;
  }
}
