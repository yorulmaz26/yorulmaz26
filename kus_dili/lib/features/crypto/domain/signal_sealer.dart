import 'dart:convert';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:libsignal_protocol_dart/libsignal_protocol_dart.dart';

import '../../chat/domain/message_payload.dart';
import '../../chat/domain/message_sealer.dart';
import '../data/persistent_signal_store.dart';
import 'group_directory.dart';
import 'key_directory.dart';
import 'payload_cache.dart';

/// Bu cihazda açılamayan mesaj (ör. uygulama yeniden kurulduktan sonra eski
/// mesajlar ya da bozuk şifreli metin).
final class MessageUnavailableException implements Exception {
  const MessageUnavailableException(this.reason);
  final String reason;
  @override
  String toString() => 'MessageUnavailableException: $reason';
}

/// Karşı tarafın şifreleme anahtarı yok (uygulamanın eski sürümünü
/// kullanıyor ya da hiç açmamış).
final class RecipientHasNoKeysException implements Exception {
  const RecipientHasNoKeysException();
}

/// Signal Protokolü (X3DH + Double Ratchet) ile uçtan uca şifreleme.
///
/// * Paket gönderen cihazda şifrelenir; Firestore'a yalnızca şifreli metin
///   yazılır (`encVersion` 1). Sunucu açık metni göremez.
/// * Her kullanıcının şimdilik tek cihazı vardır (deviceId 1).
/// * Çözülen ve gönderilen içerik [PayloadCache]'te cihazda şifreli tutulur.
/// * Tüm Signal durum değişiklikleri sıraya alınır (aynı anda iki çözme
///   oturum durumunu bozmasın diye).
final class SignalSealer implements MessageSealer {
  SignalSealer({
    required this.myUid,
    required PersistentSignalStore store,
    required KeyDirectory directory,
    required PayloadCache cache,
    required Future<void> Function() ensureKeys,
    GroupDirectory? groups,
    int Function()? epochClock,
  }) : _store = store,
       _dir = directory,
       _cache = cache,
       _ensureKeys = ensureKeys,
       _groups = groups,
       _epochClock =
           epochClock ?? (() => DateTime.now().microsecondsSinceEpoch);

  static const int version = 1;
  static const int deviceId = 1;

  final String myUid;
  final PersistentSignalStore _store;
  final KeyDirectory _dir;
  final PayloadCache _cache;
  final Future<void> Function() _ensureKeys;
  final GroupDirectory? _groups;

  /// Grup anahtarı dönemi (epoch) için tekil artan sayı.
  final int Function() _epochClock;

  static bool isGroup(String chatId) => chatId.startsWith('g_');

  Future<void> _queue = Future.value();
  final Map<String, Future<MessagePayload>> _inFlight = {};

  /// Bu uygulama çalıştığı sürece kimliği sunucuyla karşılaştırılmış kişiler.
  final Set<String> _identityChecked = {};

  @override
  int get encVersion => version;

  Future<T> _serial<T>(Future<T> Function() op) {
    final r = _queue.then((_) => op());
    _queue = r.then((_) {}, onError: (_) {});
    return r;
  }

  static Future<String> bodyHash(String body) async {
    final h = await Sha256().hash(utf8.encode(body));
    return base64UrlEncode(h.bytes);
  }

  /// İki kişilik sohbette karşı taraf.
  String _peerOf(String chatId) {
    final parts = chatId.split('_');
    if (parts.length != 3 || parts[0] != 'd' || !parts.contains(myUid)) {
      throw ArgumentError('Desteklenmeyen sohbet: $chatId');
    }
    return parts[1] == myUid ? parts[2] : parts[1];
  }

  SessionCipher _cipher(SignalProtocolAddress a) =>
      SessionCipher(_store, _store, _store, _store, a);

  Future<void> _ensureSession(String peer) async {
    final addr = SignalProtocolAddress(peer, deviceId);
    final hasSession = await _store.containsSession(addr);
    if (hasSession && _identityChecked.contains(peer)) return;

    final published = await _dir.fetchIdentity(peer);
    if (published == null) {
      if (hasSession) return; // geçici olarak okunamadı; eski oturumla devam
      throw const RecipientHasNoKeysException();
    }
    _identityChecked.add(peer);
    if (hasSession) {
      final current = (await _store.loadSession(addr)).sessionState
          .getRemoteIdentityKey();
      final same =
          current != null &&
          base64Encode(current.serialize()) ==
              base64Encode(published.identityKey);
      if (same) return;
      // Karşı taraf uygulamayı yeniden kurmuş: oturumu yeniden kur.
      await _store.deleteSession(addr);
    }
    final claimed = await _dir.claimBundle(peer);
    if (claimed == null) throw const RecipientHasNoKeysException();
    final id = claimed.identity;
    final bundle = PreKeyBundle(
      id.registrationId,
      deviceId,
      claimed.preKeyId,
      claimed.preKey == null ? null : Curve.decodePoint(claimed.preKey!, 0),
      id.signedPreKeyId,
      Curve.decodePoint(id.signedPreKey, 0),
      id.signedPreKeySignature,
      IdentityKey.fromBytes(id.identityKey, 0),
    );
    await SessionBuilder(
      _store,
      _store,
      _store,
      _store,
      addr,
    ).processPreKeyBundle(bundle);
  }

  @override
  Future<String> seal(String chatId, MessagePayload payload) async {
    await _ensureKeys();
    final json = payload.encode();
    final bytes = Uint8List.fromList(utf8.encode(json));
    final body = isGroup(chatId)
        ? await _sealGroup(chatId, bytes)
        : await _serial(() async {
            final peer = _peerOf(chatId);
            await _ensureSession(peer);
            return _pairwiseEncrypt(peer, bytes);
          });
    // Kendi mesajımızı Signal ile geri çözemeyiz; cihazda saklarız.
    await _cache.put(chatId, await bodyHash(body), json);
    return body;
  }

  @override
  Future<MessagePayload> open(
    String chatId,
    String senderId,
    int encVersion,
    String body,
  ) async {
    if (encVersion != version) throw UnsupportedEncryptionException(encVersion);
    final hash = await bodyHash(body);
    final key = '$chatId/$hash';
    return _inFlight[key] ??= () async {
      try {
        final cached = await _cache.get(chatId, hash);
        if (cached != null) return MessagePayload.decode(cached);
        if (senderId == myUid) {
          throw const MessageUnavailableException(
            'Bu mesaj başka bir cihazdan veya yeniden kurulumdan önce gönderildi.',
          );
        }
        await _ensureKeys();
        final json = await _serial(() async {
          // Sıra beklerken başka bir çağrı çözmüş olabilir.
          final again = await _cache.get(chatId, hash);
          if (again != null) return again;
          final clear = isGroup(chatId)
              ? await _openGroup(chatId, senderId, body)
              : utf8.decode(await _pairwiseDecrypt(senderId, body));
          await _cache.put(chatId, hash, clear);
          return clear;
        });
        return MessagePayload.decode(json);
      } finally {
        _inFlight.remove(key);
      }
    }();
  }

  /// Tek görüntülük mesaj açıldıktan sonra cihazdaki kopyayı siler. Signal
  /// mesajı tekrar çözemeyeceği için içerik bu cihazdan da tamamen gider.
  @override
  Future<void> forget(String chatId, String body) async =>
      _cache.remove(chatId, await bodyHash(body));

  // ---- Birebir (pairwise) -------------------------------------------------

  /// Sıra (`_serial`) içinde çağrılmalı.
  Future<String> _pairwiseEncrypt(String peer, Uint8List bytes) async {
    final ct = await _cipher(SignalProtocolAddress(peer, deviceId))
        .encrypt(bytes);
    return jsonEncode({'t': ct.getType(), 'c': base64Encode(ct.serialize())});
  }

  /// Sıra (`_serial`) içinde çağrılmalı.
  Future<Uint8List> _pairwiseDecrypt(String sender, String body) async {
    final Map<String, Object?> env;
    try {
      env = jsonDecode(body) as Map<String, Object?>;
    } catch (_) {
      throw const MessageUnavailableException('Şifreli mesaj biçimi bozuk.');
    }
    final type = env['t'];
    final c = env['c'];
    if (c is! String) {
      throw const MessageUnavailableException('Şifreli metin yok.');
    }
    final bytes = base64Decode(c);
    final cipher = _cipher(SignalProtocolAddress(sender, deviceId));
    try {
      final Uint8List clear;
      if (type == CiphertextMessage.prekeyType) {
        clear = await cipher.decrypt(PreKeySignalMessage(bytes));
      } else if (type == CiphertextMessage.whisperType) {
        clear = await cipher.decryptFromSignal(
          SignalMessage.fromSerialized(bytes),
        );
      } else {
        throw const MessageUnavailableException('Bilinmeyen mesaj türü.');
      }
      return clear;
    } on MessageUnavailableException {
      rethrow;
    } catch (e) {
      throw MessageUnavailableException('Çözülemedi: $e');
    }
  }

  // ---- Grup (Sender Keys) ----------------------------------------------------

  GroupDirectory get _g {
    final g = _groups;
    if (g == null) throw StateError('Grup dizini yapılandırılmadı.');
    return g;
  }

  SenderKeyName _skName(String chatId, int epoch, String sender) =>
      SenderKeyName('$chatId#$epoch', SignalProtocolAddress(sender, deviceId));

  /// Üyelik, anahtarımı son paylaştığım kümeden farklıysa yeni dönem açar:
  /// yeni gönderici anahtarı üretir ve her üyeye birebir şifreli dağıtır.
  /// Böylece çıkarılan üye yeni mesajları, yeni üye eski mesajları okuyamaz.
  Future<int> _ensureMyGroupKey(String chatId) async {
    final members = [...await _g.members(chatId)]..sort();
    if (!members.contains(myUid)) {
      throw const MessageUnavailableException('Bu grubun üyesi değilsin.');
    }
    final others = members.where((m) => m != myUid).toList();
    final epochStr = await _store.readMeta('grp.$chatId.epoch');
    final sharedTo = await _store.readMeta('grp.$chatId.sharedTo');
    if (epochStr != null && sharedTo == others.join(',')) {
      return int.parse(epochStr);
    }
    final epoch = _epochClock();
    final name = _skName(chatId, epoch, myUid);
    final skdm = await GroupSessionBuilder(_store).create(name);
    final shares = <String, String>{};
    final reached = <String>[];
    for (final m in others) {
      try {
        await _ensureSession(m);
        shares[m] = await _pairwiseEncrypt(m, skdm.serialize());
        reached.add(m);
      } on RecipientHasNoKeysException {
        // Anahtarı olmayan üye bu dönemi okuyamaz; bir sonraki gönderimde
        // yeniden denenir (sharedTo farklı kalacağı için).
      }
    }
    await _g.publishSenderKey(
      chatId: chatId,
      sender: myUid,
      epoch: epoch,
      shares: shares,
    );
    await _store.writeMeta('grp.$chatId.epoch', '$epoch');
    await _store.writeMeta('grp.$chatId.sharedTo', reached.join(','));
    return epoch;
  }

  Future<String> _sealGroup(String chatId, Uint8List bytes) =>
      _serial(() async {
        final epoch = await _ensureMyGroupKey(chatId);
        final ct = await GroupCipher(
          _store,
          _skName(chatId, epoch, myUid),
        ).encrypt(bytes);
        return jsonEncode({'t': 'sk', 'e': epoch, 'c': base64Encode(ct)});
      });

  /// Sıra (`_serial`) içinde çağrılır.
  Future<String> _openGroup(String chatId, String sender, String body) async {
    final Map<String, Object?> env;
    try {
      env = jsonDecode(body) as Map<String, Object?>;
    } catch (_) {
      throw const MessageUnavailableException('Şifreli mesaj biçimi bozuk.');
    }
    final epoch = env['e'];
    final c = env['c'];
    if (env['t'] != 'sk' || epoch is! int || c is! String) {
      throw const MessageUnavailableException('Grup mesajı biçimi bozuk.');
    }
    final name = _skName(chatId, epoch, sender);
    final gotKey = 'grp.$chatId.got.$sender.$epoch';
    if (await _store.readMeta(gotKey) == null) {
      final share = await _g.fetchShare(
        chatId: chatId,
        sender: sender,
        epoch: epoch,
        me: myUid,
      );
      if (share == null) {
        throw const MessageUnavailableException(
          'Bu mesajın grup anahtarı sana paylaşılmamış.',
        );
      }
      final skdm = await _pairwiseDecrypt(sender, share);
      try {
        await GroupSessionBuilder(_store).process(
          name,
          SenderKeyDistributionMessageWrapper.fromSerialized(skdm),
        );
      } catch (e) {
        throw MessageUnavailableException('Grup anahtarı işlenemedi: $e');
      }
      await _store.writeMeta(gotKey, '1');
    }
    try {
      final clear = await GroupCipher(_store, name).decrypt(base64Decode(c));
      return utf8.decode(clear);
    } catch (e) {
      throw MessageUnavailableException('Çözülemedi: $e');
    }
  }
}

/// Geçiş dönemi: yeni mesajlar Signal ile mühürlenir; geliştirme sürümünde
/// yazılmış eski şifresiz (encVersion 0) mesajlar yine okunabilir.
final class CompositeSealer implements MessageSealer {
  const CompositeSealer(
    this.primary, {
    this.legacy = const PlaintextDevSealer(),
  });

  final MessageSealer primary;
  final MessageSealer legacy;

  @override
  int get encVersion => primary.encVersion;

  @override
  Future<void> forget(String chatId, String body) =>
      primary.forget(chatId, body);

  @override
  Future<String> seal(String chatId, MessagePayload payload) =>
      primary.seal(chatId, payload);

  @override
  Future<MessagePayload> open(
    String chatId,
    String senderId,
    int encVersion,
    String body,
  ) => encVersion == legacy.encVersion
      ? legacy.open(chatId, senderId, encVersion, body)
      : primary.open(chatId, senderId, encVersion, body);
}
