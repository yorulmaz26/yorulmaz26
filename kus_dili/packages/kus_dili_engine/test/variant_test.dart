import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:test/test.dart';

void main() {
  group('Hazır varyantlar', () {
    test('Kimlikler ve şablonlar', () {
      expect(KusDiliVariant.presets.map((v) => v.id), ['g', 'f', 'c']);
      expect(KusDiliVariant.g.template, 'g{v}');
      expect(KusDiliVariant.preset('f'), same(KusDiliVariant.f));
      expect(KusDiliVariant.preset('yok'), isNull);
      expect(KusDiliVariant.g.isPreset, isTrue);
    });
  });

  group('Özel varyant doğrulaması', () {
    KusDiliVariant make(String tpl, {Map<String, String> o = const {}}) =>
        KusDiliVariant.custom(
          id: 'x',
          name: 'X',
          template: tpl,
          vowelOverrides: o,
        );

    test('Geçerli şablonlar', () {
      expect(make('z{v}').template, 'z{v}');
      expect(make('P{v}R').template, 'p{v}r', reason: 'küçük harfe çevrilir');
      expect(make('{v}ş').template, '{v}ş');
      expect(make('ğ{v}').template, 'ğ{v}');
    });

    test('Geçersiz şablonlar reddedilir', () {
      for (final bad in [
        '',
        '{v}',
        'a{v}',
        'aeı',
        'g{v}1',
        'g {v}',
        'g{x}',
        '{v}{v}{v}g',
        'gggggggg{v}',
        '😀{v}',
        'g{v',
        '-{v}',
      ]) {
        expect(
          () => make(bad),
          throwsA(isA<KusDiliRuleException>()),
          reason: '"$bad" reddedilmeliydi',
        );
      }
    });

    test('Kimlik, ad ve sesli harf kuralları doğrulanır', () {
      expect(
        () => KusDiliVariant.custom(id: 'g', name: 'G', template: 'z{v}'),
        throwsA(isA<KusDiliRuleException>()),
        reason: 'hazır kimlik kullanılamaz',
      );
      expect(
        () => KusDiliVariant.custom(
          id: 'Boşluk var',
          name: 'A',
          template: 'z{v}',
        ),
        throwsA(isA<KusDiliRuleException>()),
      );
      expect(
        () => KusDiliVariant.custom(id: 'a', name: '  ', template: 'z{v}'),
        throwsA(isA<KusDiliRuleException>()),
      );
      expect(
        () => make('z{v}', o: {'b': 'z{v}'}),
        throwsA(isA<KusDiliRuleException>()),
      );
      expect(
        () => make('z{v}', o: {'a': '{v}'}),
        throwsA(isA<KusDiliRuleException>()),
      );
    });

    test('Sesli harfe özel kural uygulanır', () {
      final v = make('g{v}', o: {'A': 'V{v}'});
      expect(v.vowelOverrides, {'a': 'v{v}'});
      final codec = KusDiliCodec(v);
      expect(codec.encode('Merhaba'), 'Megerhavabava');
      expect(codec.encode('hâlâ'), 'hâvâlâvâ');
      expect(codec.decode('Megerhavabava').text, 'Merhaba');
    });

    test('Çok heceli şablon', () {
      final codec = KusDiliCodec(make('p{v}r'));
      expect(codec.encode('Ali'), 'Aparlipir');
      expect(codec.decode('Aparlipir').text, 'Ali');
      expect(codec.encode('ALİ'), 'APARLİPİR');
    });
  });

  group('JSON', () {
    test('Hazır varyant kimlikten çözülür', () {
      expect(
        KusDiliVariant.fromJson(KusDiliVariant.c.toJson()),
        same(KusDiliVariant.c),
      );
    });
    test('Özel varyant gidiş-dönüş', () {
      final v = KusDiliVariant.custom(
        id: 'benim',
        name: 'Benim dilim',
        template: 'z{v}',
        vowelOverrides: {'ö': 'ş{v}'},
      );
      final back = KusDiliVariant.fromJson(v.toJson());
      expect(back, v);
      expect(back.name, 'Benim dilim');
      expect(back.hashCode, v.hashCode);
    });
    test('Bozuk JSON reddedilir', () {
      expect(
        () => KusDiliVariant.fromJson({}),
        throwsA(isA<KusDiliRuleException>()),
      );
      expect(
        () => KusDiliVariant.fromJson({'id': 'x', 'template': '{v}'}),
        throwsA(isA<KusDiliRuleException>()),
      );
    });
  });
}
