import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/profile/domain/username.dart';

void main() {
  test('Normalize', () {
    expect(Username.normalize('  @Ayse_K '), 'ayse_k');
    expect(Username.normalize('İREM'), 'irem');
    expect(Username.normalize('IRMAK'), 'irmak');
  });

  test('Geçerli kullanıcı adları', () {
    for (final u in [
      'ali',
      'ayse.k',
      'mehmet_42',
      'a12',
      'abcdefghijklmnopqrst',
    ]) {
      expect(Username.validate(u), isNull, reason: u);
    }
  });

  test('Geçersiz kullanıcı adları', () {
    for (final u in [
      'al',
      'abcdefghijklmnopqrstu',
      '1ali',
      '_ali',
      '.ali',
      'ali..veli',
      'ali.',
      'ali-veli',
      'ali veli',
      'ayşe',
      'ılgaz',
      'admin',
      'destek',
      'Ali',
    ]) {
      expect(Username.validate(u), isNotNull, reason: u);
    }
    expect(Username.validate('ayşe'), contains('Türkçe karakter'));
  });

  test('Görünen ad', () {
    expect(DisplayName.validate('Ayşe'), isNull);
    expect(DisplayName.validate('   '), isNotNull);
    expect(DisplayName.validate('a' * 41), isNotNull);
  });
}
