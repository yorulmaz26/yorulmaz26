/// Türkçeye özgü metin yardımcıları.
///
/// Dart'ın `toUpperCase` / `toLowerCase` metotları Türkçe "i/İ" ve "ı/I"
/// eşleşmelerini bilmez ("I".toLowerCase() == "i", "İ".toLowerCase() ==
/// "i̇"). Bu sınıf Türkçe kurallarla büyük/küçük harf dönüşümü yapar ve metni
/// "kümelere" (temel karakter + birleşen işaretler / emoji ekleri) böler.
abstract final class TurkishText {
  static final RegExp _letter = RegExp(r'\p{L}', unicode: true);

  /// Kuş dilinde hece eklenen sesli harfler (küçük harf, düzeltme işaretli
  /// biçimler dahil).
  static const Set<String> vowels = {
    'a', 'e', 'ı', 'i', 'o', 'ö', 'u', 'ü', //
    'â', 'î', 'û', 'ô', 'ê',
  };

  /// Kurallarda (özel varyant) kullanılabilen temel sesliler.
  static const Set<String> baseVowels = {
    'a',
    'e',
    'ı',
    'i',
    'o',
    'ö',
    'u',
    'ü',
  };

  /// Ayrık (decomposed) yazılmış yaygın Türkçe karakterlerin birleşik
  /// karşılıkları. Yalnızca karşılaştırma (katlama) için kullanılır; çıktı
  /// metni asla normalize edilmez.
  static const Map<String, String> _compose = {
    'ö': 'ö',
    'Ö': 'Ö',
    'ü': 'ü',
    'Ü': 'Ü',
    'ç': 'ç',
    'Ç': 'Ç',
    'ş': 'ş',
    'Ş': 'Ş',
    'ğ': 'ğ',
    'Ğ': 'Ğ',
    'İ': 'İ',
    'â': 'â',
    'Â': 'Â',
    'î': 'î',
    'Î': 'Î',
    'û': 'û',
    'Û': 'Û',
    'ô': 'ô',
    'Ô': 'Ô',
    'ê': 'ê',
    'Ê': 'Ê',
  };

  static const Map<String, String> _circumflexFold = {
    'â': 'a',
    'î': 'i',
    'û': 'u',
    'ô': 'o',
    'ê': 'e',
  };

  /// Türkçe kurallarla küçük harfe çevirir (I→ı, İ→i).
  static String lower(String s) {
    final b = StringBuffer();
    for (final r in s.runes) {
      switch (r) {
        case 0x49: // I
          b.write('ı');
        case 0x130: // İ
          b.write('i');
        default:
          b.write(String.fromCharCode(r).toLowerCase());
      }
    }
    return b.toString();
  }

  /// Türkçe kurallarla büyük harfe çevirir (i→İ, ı→I).
  static String upper(String s) {
    final b = StringBuffer();
    for (final r in s.runes) {
      switch (r) {
        case 0x69: // i
          b.write('İ');
        case 0x131: // ı
          b.write('I');
        default:
          final c = String.fromCharCode(r);
          final u = c.toUpperCase();
          // 'ß' gibi tek karakterin birden çok karaktere açılmasını engelle.
          b.write(u.runes.length == 1 ? u : c);
      }
    }
    return b.toString();
  }

  /// Kümenin harf olup olmadığı (temel karaktere bakılır).
  static bool isLetter(String cluster) =>
      cluster.isNotEmpty &&
      _letter.hasMatch(String.fromCharCode(cluster.runes.first));

  /// Küme büyük harf mi? (Harf olmayanlar için `false`.)
  static bool isUpper(String cluster) {
    if (!isLetter(cluster)) return false;
    final base = String.fromCharCode(cluster.runes.first);
    return lower(base) != base;
  }

  /// Küme küçük harf mi? (Harf olmayanlar için `false`.)
  static bool isLower(String cluster) {
    if (!isLetter(cluster)) return false;
    final base = String.fromCharCode(cluster.runes.first);
    return upper(base) != base;
  }

  /// Karşılaştırma anahtarı: Türkçe küçük harf + ayrık karakterleri birleştir
  /// + düzeltme işaretini kaldır. "Â", "â", "â" ve "a" aynı anahtarı
  /// verir.
  static String fold(String cluster) {
    final composed = _compose[cluster] ?? cluster;
    final l = lower(composed);
    return _circumflexFold[l] ?? l;
  }

  /// Küme sesli harf mi? Temel karakter (ve varsa birleşen işaretleri)
  /// katlanarak kontrol edilir; böylece ayrık yazılmış "ö" da sesli
  /// sayılır.
  static bool isVowel(String cluster) {
    if (cluster.isEmpty) return false;
    final composed = _compose[cluster] ?? cluster;
    final base = lower(String.fromCharCode(composed.runes.first));
    return vowels.contains(base);
  }

  static bool _isExtender(int r) =>
      (r >= 0x0300 && r <= 0x036F) || // birleşen aksan işaretleri
      (r >= 0x1AB0 && r <= 0x1AFF) ||
      (r >= 0x1DC0 && r <= 0x1DFF) ||
      (r >= 0x20D0 && r <= 0x20FF) || // keycap vb.
      (r >= 0xFE20 && r <= 0xFE2F) ||
      r == 0xFE0E ||
      r == 0xFE0F || // varyasyon seçicileri (emoji/metin)
      (r >= 0x1F3FB && r <= 0x1F3FF) || // ten rengi
      (r >= 0xE0020 && r <= 0xE007F); // bayrak etiketleri

  /// Metni kümelere böler: temel karakter + ardından gelen birleşen işaretler,
  /// varyasyon seçicileri, ten rengi ve ZWJ (U+200D) ile bağlanan emoji
  /// parçaları tek küme olur. Böylece emojiler ve aksanlı harfler asla
  /// ortasından bölünmez. Kümeler birleştirildiğinde orijinal metin birebir
  /// elde edilir.
  static List<String> clusters(String s) {
    final out = <String>[];
    final runes = s.runes.toList(growable: false);
    var i = 0;
    while (i < runes.length) {
      final b = StringBuffer()..writeCharCode(runes[i]);
      var prevWasRegionalIndicator = _isRegionalIndicator(runes[i]);
      i++;
      while (i < runes.length) {
        final r = runes[i];
        if (_isExtender(r)) {
          b.writeCharCode(r);
          i++;
        } else if (r == 0x200D && i + 1 < runes.length) {
          b
            ..writeCharCode(r)
            ..writeCharCode(runes[i + 1]);
          i += 2;
        } else if (prevWasRegionalIndicator && _isRegionalIndicator(r)) {
          // Bayrak emojisi = iki bölgesel gösterge harfi.
          b.writeCharCode(r);
          prevWasRegionalIndicator = false;
          i++;
        } else {
          break;
        }
      }
      out.add(b.toString());
    }
    return out;
  }

  static bool _isRegionalIndicator(int r) => r >= 0x1F1E6 && r <= 0x1F1FF;
}
