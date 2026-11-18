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
/// * Çoklu cihaz: her kullanıcının en fazla 5 cihazı vardır, her cihazın kendi
///   Signal kimliği ve oturumları olur. Birebir mesajda içerik rastgele bir
///   anahtarla (AES-256-GCM) bir kez şifrelenir; bu anahtar alıcının her
///   cihazına ve gönderenin diğer cihazlarına ayrı Signal oturumlarıyla
///   iletilir. Grupta her cihazın kendi gönderici anahtarı (Sender Key) olur.
/// * Bir cihaz, kendisi bağlanmadan önce gönderilmiş mesajları okuyamaz.
/// * Çözülen ve gönderilen içerik [PayloadCache]'te cihazda şifreli tutulur.
/// * Tüm Signal durum değişiklikleri sıraya alınır (aynı anda iki çözme
///   oturum durumunu bozmasın diye).
final class SignalSealer implements MessageSealer {
  SignalSealer({
    required this.myUid,
    required PersistentSignalStore store,
    required KeyDirectory directory,
    required PayloadCache cache,
    required Future<int> Function() ensureKeys,
    GroupDirectory? groups,
    int Function()? epochClock,
    Duration deviceListTtl = const Duration(seconds: 60),
    DateTime Function()? clock,
  }) : _store = store,
       _dir = directory,
       _cache = cache,
       _ensureKeys = ensureKeys,
       _groups = groups,
       _ttl = deviceListTtl,
       _clock = clock ?? DateTime.now,
       _epochClock =
           epochClock ?? (() => DateTime.now().microsecondsSinceEpoch);

  static const int version = 1;

  final String myUid;
  final PersistentSignalStore _store;
  final KeyDirectory _dir;
  final PayloadCache _cache;

  /// Anahtarları hazırlar ve bu cihazın kimliğini döndürür.
  final Future<int> Function() _ensureKeys;
  final GroupDirectory? _groups;
  final Duration _ttl;
  final DateTime Function() _clock;

  /// Grup anahtarı dönemi (epoch) için tekil artan sayı.
  final int Function() _epochClock;

  static final _aes = AesGcm.with256bits();

  static bool isGroup(String chatId) => chatId.startsWith('g_');

  static String addressKey(String uid, int device) => '$uid:$device';

  Future<void> _queue = Future.value();
  final Map<String, Future<MessagePayload>> _inFlight = {};

  /// Bu uygulama çalıştığı sürece kimliği sunucuyla karşılaştırılmış
  /// cihazlar (`uid:cihaz`).
  final Set<String> _identityChecked = {};

  /// Kişilerin cihaz listeleri (kısa süreli önbellek).
  final Map<String, (DateTime, List<int>)> _devices = {};

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

  /// [uid]'in etkin cihazları. Yeni bir cihaz görülürse kişi için "güvenlik
  /// numarası değişti" işareti konur.
  Future<List<int>> _devicesOf(String uid) async {
    final now = _clock();
    final c = _devices[uid];
    if (c != null && now.difference(c.$1) < _ttl) return c.$2;
    final list = await _dir.devices(uid);
    await _store.updateKnownDevices(uid, list);
    _devices[uid] = (now, list);
    return list;
  }

  SessionCipher _cipher(SignalProtocolAddress a) =>
      SessionCipher(_store, _store, _store, _store, a);

  Future<void> _ensureSession(SignalProtocolAddress addr) async {
    final uid = addr.getName();
    final dev = addr.getDeviceId();
    final k = addressKey(uid, dev);
    final hasSession = await _store.containsSession(addr);
    if (hasSession && _identityChecked.contains(k)) return;

    final published = await _dir.fetchIdentity(uid, dev);
    if (published == null) {
      if (hasSession) return; // geçici olarak okunamadı; eski oturumla devam
      throw const RecipientHasNoKeysException();
    }
    _identityChecked.add(k);
    if (hasSession) {
      final current = (await _store.loadSession(addr)).sessionState
          .getRemoteIdentityKey();
      final same =
          current != null &&
          base64Encode(current.serialize()) ==
              base64Encode(published.identityKey);
      if (same) return;
      // Cihaz anahtarını yenilemiş: oturumu yeniden kur (uyarı işaretlenir).
      await _store.deleteSession(addr);
    }
    final claimed = await _dir.claimBundle(uid, dev);
    if (claimed == null) throw const RecipientHasNoKeysException();
    final id = claimed.identity;
    final bundle = PreKeyBundle(
      id.registrationId,
      dev,
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
    final myDev = await _ensureKeys();
    final json = payload.encode();
    final bytes = Uint8List.fromList(utf8.encode(json));
    final body = isGroup(chatId)
        ? await _sealGroup(chatId, bytes, myDev)
        : await _serial(() => _sealDirect(chatId, bytes, myDev));
    // Kendi mesajımızı bu cihazda Signal ile geri çözemeyiz; cihazda saklarız.
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
        final myDev = await _ensureKeys();
        final json = await _serial(() async {
          // Sıra beklerken başka bir çağrı çözmüş olabilir.
          final again = await _cache.get(chatId, hash);
          if (again != null) return again;
          final clear = isGroup(chatId)
              ? await _openGroup(chatId, senderId, body, myDev)
              : await _openDirect(chatId, senderId, body, myDev);
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

  static Map<String, Object?> _envelope(String body) {
    try {
      final env = jsonDecode(body);
      if (env is Map<String, Object?>) return env;
    } catch (_) {
      // aşağıda
    }
    throw const MessageUnavailableException('Şifreli mesaj biçimi bozuk.');
  }

  /// Gönderen cihaz henüz bilinmiyorsa kişinin cihaz listesi tazelenir; yeni
  /// cihazsa "güvenlik numarası değişti" uyarısı konur.
  Future<void> _noteSenderDevice(String sender, int sd) async {
    if ((await _devicesOf(sender)).contains(sd)) return;
    _devices.remove(sender);
    await _devicesOf(sender);
  }

  /// Gönderen bu cihazsa ve önbellekte yoksa açılamaz.
  void _rejectOwnDevice(String sender, int sd, int myDev) {
    if (sender == myUid && sd == myDev) {
      throw const MessageUnavailableException(
        'Bu mesaj bu cihazdan, yeniden kurulumdan önce gönderildi.',
      );
    }
  }

  // ---- Birebir (çoklu cihaz) ----------------------------------------------

  static List<int> _aad(String chatId, String sender, int sd) =>
      utf8.encode('kusdili-md|$chatId|$sender|$sd');

  /// Sıra (`_serial`) içinde çağrılmalı.
  Future<String> _sealDirect(String chatId, Uint8List bytes, int myDev) async {
    final peer = _peerOf(chatId);
    final peerDevices = await _devicesOf(peer);
    final myOthers = [
      for (final d in await _devicesOf(myUid))
        if (d != myDev) d,
    ];
    final secret = await _aes.newSecretKey();
    final keyBytes = Uint8List.fromList(await secret.extractBytes());
    final box = await _aes.encrypt(
      bytes,
      secretKey: secret,
      aad: _aad(chatId, myUid, myDev),
    );
    final wraps = <String, Object>{};
    var reachedPeer = false;
    for (final (uid, dev) in [
      for (final d in peerDevices) (peer, d),
      for (final d in myOthers) (myUid, d),
    ]) {
      final addr = SignalProtocolAddress(uid, dev);
      try {
        await _ensureSession(addr);
        wraps[addressKey(uid, dev)] = await _pairwiseEncrypt(addr, keyBytes);
        if (uid == peer) reachedPeer = true;
      } on RecipientHasNoKeysException {
        // Anahtarı olmayan cihaz bu mesajı okuyamaz.
      }
    }
    if (!reachedPeer) throw const RecipientHasNoKeysException();
    return jsonEncode({
      't': 'md',
      'sd': myDev,
      'n': base64Encode(box.nonce),
      'c': base64Encode(box.cipherText),
      'm': base64Encode(box.mac.bytes),
      'k': wraps,
    });
  }

  /// Sıra (`_serial`) içinde çağrılmalı.
  Future<String> _openDirect(
    String chatId,
    String sender,
    String body,
    int myDev,
  ) async {
    final env = _envelope(body);
    final sd = env['sd'];
    final k = env['k'];
    final n = env['n'];
    final c = env['c'];
    final m = env['m'];
    if (env['t'] != 'md' ||
        sd is! int ||
        k is! Map ||
        n is! String ||
        c is! String ||
        m is! String) {
      throw const MessageUnavailableException('Şifreli mesaj biçimi bozuk.');
    }
    _rejectOwnDevice(sender, sd, myDev);
    await _noteSenderDevice(sender, sd);
    final wrap = k[addressKey(myUid, myDev)];
    if (wrap == null) {
      throw const MessageUnavailableException(
        'Bu mesaj, bu cihaz bağlanmadan önce gönderildi.',
      );
    }
    final key = await _pairwiseDecrypt(SignalProtocolAddress(sender, sd), wrap);
    try {
      final clear = await _aes.decrypt(
        SecretBox(
          base64Decode(c),
          nonce: base64Decode(n),
          mac: Mac(base64Decode(m)),
        ),
        secretKey: SecretKey(key),
        aad: _aad(chatId, sender, sd),
      );
      return utf8.decode(clear);
    } catch (e) {
      throw MessageUnavailableException('Çözülemedi: $e');
    }
  }

  // ---- Birebir Signal oturumu -------------------------------------------

  /// Sıra (`_serial`) içinde çağrılmalı.
  Future<Map<String, Object>> _pairwiseEncrypt(
    SignalProtocolAddress addr,
    Uint8List bytes,
  ) async {
    final ct = await _cipher(addr).encrypt(bytes);
    return {'t': ct.getType(), 'c': base64Encode(ct.serialize())};
  }

  /// Sıra (`_serial`) içinde çağrılmalı. [env] eşleme ya da JSON metni.
  Future<Uint8List> _pairwiseDecrypt(
    SignalProtocolAddress addr,
    Object? env,
  ) async {
    final Object? e = env is String ? _envelope(env) : env;
    if (e is! Map) {
      throw const MessageUnavailableException('Şifreli metin yok.');
    }
    final type = e['t'];
    final c = e['c'];
    if (c is! String) {
      throw const MessageUnavailableException('Şifreli metin yok.');
    }
    final bytes = base64Decode(c);
    final cipher = _cipher(addr);
    try {
      if (type == CiphertextMessage.prekeyType) {
        return await cipher.decrypt(PreKeySignalMessage(bytes));
      } else if (type == CiphertextMessage.whisperType) {
        return await cipher.decryptFromSignal(
          SignalMessage.fromSerialized(bytes),
        );
      }
      throw const MessageUnavailableException('Bilinmeyen mesaj türü.');
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

  SenderKeyName _skName(String chatId, int epoch, String sender, int dev) =>
      SenderKeyName('$chatId#$epoch', SignalProtocolAddress(sender, dev));

  /// Grubun cihaz kümesi (üyelerin tüm cihazları ve benim diğer cihazlarım),
  /// anahtarımı son paylaştığım kümeden farklıysa yeni dönem açar: yeni
  /// gönderici anahtarı üretir ve her cihaza birebir şifreli dağıtır.
  /// Böylece çıkarılan üye yeni mesajları, yeni üye eski mesajları okuyamaz.
  Future<int> _ensureMyGroupKey(String chatId, int myDev) async {
    final members = [...await _g.members(chatId)]..sort();
    if (!members.contains(myUid)) {
      throw const MessageUnavailableException('Bu grubun üyesi değilsin.');
    }
    final lists = await Future.wait(members.map(_devicesOf));
    final targets = <(String, int)>[
      for (var i = 0; i < members.length; i++)
        for (final d in [...lists[i]]..sort())
          if (!(members[i] == myUid && d == myDev)) (members[i], d),
    ];
    final all = [for (final (u, d) in targets) addressKey(u, d)].join(',');
    final epochStr = await _store.readMeta('grp.$chatId.epoch');
    final sharedTo = await _store.readMeta('grp.$chatId.sharedTo');
    if (epochStr != null && sharedTo == all) return int.parse(epochStr);

    final epoch = _epochClock();
    final name = _skName(chatId, epoch, myUid, myDev);
    final skdm = await GroupSessionBuilder(_store).create(name);
    final shares = <String, String>{};
    final reached = <String>[];
    for (final (uid, dev) in targets) {
      final addr = SignalProtocolAddress(uid, dev);
      try {
        await _ensureSession(addr);
        final k = addressKey(uid, dev);
        shares[k] = jsonEncode(await _pairwiseEncrypt(addr, skdm.serialize()));
        reached.add(k);
      } on RecipientHasNoKeysException {
        // Anahtarı olmayan cihaz bu dönemi okuyamaz; bir sonraki gönderimde
        // yeniden denenir (sharedTo farklı kalacağı için).
      }
    }
    await _g.publishSenderKey(
      chatId: chatId,
      sender: myUid,
      device: myDev,
      epoch: epoch,
      shares: shares,
    );
    await _store.writeMeta('grp.$chatId.epoch', '$epoch');
    await _store.writeMeta('grp.$chatId.sharedTo', reached.join(','));
    return epoch;
  }

  Future<String> _sealGroup(String chatId, Uint8List bytes, int myDev) =>
      _serial(() async {
        final epoch = await _ensureMyGroupKey(chatId, myDev);
        final ct = await GroupCipher(
          _store,
          _skName(chatId, epoch, myUid, myDev),
        ).encrypt(bytes);
        return jsonEncode({
          't': 'sk',
          'e': epoch,
          'sd': myDev,
          'c': base64Encode(ct),
        });
      });

  /// Sıra (`_serial`) içinde çağrılır.
  Future<String> _openGroup(
    String chatId,
    String sender,
    String body,
    int myDev,
  ) async {
    final env = _envelope(body);
    final epoch = env['e'];
    final sd = env['sd'];
    final c = env['c'];
    if (env['t'] != 'sk' || epoch is! int || sd is! int || c is! String) {
      throw const MessageUnavailableException('Grup mesajı biçimi bozuk.');
    }
    _rejectOwnDevice(sender, sd, myDev);
    await _noteSenderDevice(sender, sd);
    final name = _skName(chatId, epoch, sender, sd);
    final gotKey = 'grp.$chatId.got.$sender.$sd.$epoch';
    if (await _store.readMeta(gotKey) == null) {
      final share = await _g.fetchShare(
        chatId: chatId,
        sender: sender,
        device: sd,
        epoch: epoch,
        me: addressKey(myUid, myDev),
      );
      if (share == null) {
        throw const MessageUnavailableException(
          'Bu mesajın grup anahtarı bu cihaza paylaşılmamış.',
        );
      }
      final skdm = await _pairwiseDecrypt(
        SignalProtocolAddress(sender, sd),
        share,
      );
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
