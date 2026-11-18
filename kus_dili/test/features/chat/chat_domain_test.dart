import 'package:flutter_test/flutter_test.dart';
import 'package:kus_dili/features/chat/domain/chat.dart';
import 'package:kus_dili/features/chat/domain/chat_settings.dart';
import 'package:kus_dili/features/chat/domain/message_payload.dart';
import 'package:kus_dili/features/chat/domain/message_sealer.dart';
import 'package:kus_dili/features/chat/domain/message_view.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';

void main() {
  group('MessagePayload', () {
    test('JSON gidiş-dönüş (özel varyant ve yanıt dahil)', () {
      final v = KusDiliVariant.custom(id: 'z', name: 'Z', template: 'z{v}');
      final p = MessagePayload(
        text: 'Seni seviyorum 🐦',
        mode: DisplayMode.kusDili,
        variant: v,
        replyToId: 'm0',
        replyQuote: 'Selam',
      );
      final back = MessagePayload.decode(p.encode());
      expect(back.text, p.text);
      expect(back.mode, DisplayMode.kusDili);
      expect(back.variant, v);
      expect(back.replyToId, 'm0');
      expect(back.replyQuote, 'Selam');
    });

    test('Normal mesajda varyant taşınmaz', () {
      final p = MessagePayload(text: 'Merhaba');
      expect(p.toJson().containsKey('var'), isFalse);
      expect(MessagePayload.decode(p.encode()).mode, DisplayMode.plain);
    });

    test('Boş, çok uzun, bozuk ve gelecekteki sürüm reddedilir', () {
      expect(
        () => MessagePayload(text: '  '),
        throwsA(isA<PayloadFormatException>()),
      );
      expect(
        () => MessagePayload(text: 'a' * 4001),
        throwsA(isA<PayloadFormatException>()),
      );
      for (final bad in ['{', '[]', '{"v":1}', '{"v":99,"text":"x"}']) {
        expect(
          () => MessagePayload.decode(bad),
          throwsA(isA<PayloadFormatException>()),
          reason: bad,
        );
      }
    });

    test('Bozuk özel kural varsayılan G\'liye düşer', () {
      final p = MessagePayload.decode(
        '{"v":1,"text":"Selam","m":"k","var":{"id":"x","template":"{v}"}}',
      );
      expect(p.variant, KusDiliVariant.g);
    });

    test('Alıntı kısaltılır', () {
      expect(MessagePayload.quoteOf('a\n\n b'), 'a b');
      expect(MessagePayload.quoteOf('x' * 300).length, 120);
    });
  });

  test('Geliştirme mühürleyicisi yalnızca encVersion 0 açar', () async {
    const s = PlaintextDevSealer();
    final body = await s.seal('c', MessagePayload(text: 'Selam'));
    expect((await s.open('c', 'u', 0, body)).text, 'Selam');
    expect(
      () => s.open('c', 'u', 1, body),
      throwsA(isA<UnsupportedEncryptionException>()),
    );
  });

  group('MessageView (alıcı ne görür)', () {
    final kus = MessagePayload(
      text: 'Seni seviyorum',
      mode: DisplayMode.kusDili,
    );
    test('Kuş dili gönderildi → kuş dili görünür, dokununca Türkçe', () {
      final v = MessageView.of(kus, viewMode: ViewMode.asSent);
      expect(v.primary, 'Segenigi segevigiyogorugum');
      expect(v.original, 'Seni seviyorum');
      expect(v.isEncodedNow, isTrue);
      final r = MessageView.of(kus, viewMode: ViewMode.asSent, revealed: true);
      expect(r.primary, 'Seni seviyorum');
      expect(r.isEncodedNow, isFalse);
    });
    test('"Her zaman Türkçe" ve "ikisi birden"', () {
      expect(
        MessageView.of(kus, viewMode: ViewMode.original).primary,
        'Seni seviyorum',
      );
      final both = MessageView.of(kus, viewMode: ViewMode.both);
      expect(both.primary, 'Segenigi segevigiyogorugum');
      expect(both.secondary, 'Seni seviyorum');
    });
    test('Gönderenin varyantı kullanılır', () {
      final f = MessagePayload(
        text: 'Selam',
        mode: DisplayMode.kusDili,
        variant: KusDiliVariant.f,
      );
      expect(MessageView.of(f, viewMode: ViewMode.asSent).primary, 'Sefelafam');
    });
    test('Normal mesaj olduğu gibi', () {
      final v = MessageView.of(
        MessagePayload(text: 'Selam'),
        viewMode: ViewMode.asSent,
      );
      expect(v.primary, 'Selam');
      expect(v.isKusDili, isFalse);
    });
  });

  group('Chat', () {
    test('directChatId sıralı ve simetrik', () {
      expect(directChatId('b', 'a'), 'd_a_b');
      expect(directChatId('a', 'b'), 'd_a_b');
      expect(() => directChatId('a', 'a'), throwsArgumentError);
    });

    final t0 = DateTime(2026, 11, 18, 10);
    test('Okunmamış ve okundu bilgisi', () {
      final chat = Chat(
        id: 'd_a_b',
        members: const ['a', 'b'],
        lastActivityAt: t0,
        lastSenderId: 'a',
        readAt: {'a': t0, 'b': t0.subtract(const Duration(minutes: 1))},
      );
      expect(chat.isUnreadFor('b'), isTrue);
      expect(chat.isUnreadFor('a'), isFalse);
      expect(chat.otherMember('a'), 'b');
      final m = ChatMessage(
        id: 'm',
        senderId: 'a',
        encVersion: 0,
        body: 'x',
        sentAt: t0,
      );
      expect(m.isReadBy('b', chat), isFalse);
      final read = Chat(
        id: 'd_a_b',
        members: const ['a', 'b'],
        readAt: {'b': t0.add(const Duration(seconds: 5))},
      );
      expect(m.isReadBy('b', read), isTrue);
    });
  });

  test('ChatSettings JSON', () {
    final s = ChatSettings(
      sendMode: DisplayMode.plain,
      variant: KusDiliVariant.c,
      viewMode: ViewMode.both,
    );
    final back = ChatSettings.fromJson(s.toJson());
    expect(back.sendMode, DisplayMode.plain);
    expect(back.variant, KusDiliVariant.c);
    expect(back.viewMode, ViewMode.both);
    expect(ChatSettings.fromJson({}).sendMode, DisplayMode.kusDili);
  });
}
