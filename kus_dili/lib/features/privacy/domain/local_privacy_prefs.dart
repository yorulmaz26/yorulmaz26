import 'dart:convert';

import '../../crypto/domain/secure_kv.dart';

/// Yalnızca bu cihazda tutulan gizlilik tercihleri. Hangi sohbetlerin gizli
/// ya da kilitli olduğu sunucuya YAZILMAZ (sunucu bunu bilmemeli).
final class PrivacyPrefs {
  const PrivacyPrefs({
    this.hiddenChats = const {},
    this.lockedChats = const {},
    this.appLock = false,
    this.biometrics = false,
  });

  factory PrivacyPrefs.fromJson(Map<String, Object?> j) => PrivacyPrefs(
    hiddenChats: {...?(j['hidden'] as List?)?.cast<String>()},
    lockedChats: {...?(j['locked'] as List?)?.cast<String>()},
    appLock: j['appLock'] == true,
    biometrics: j['bio'] == true,
  );

  final Set<String> hiddenChats;
  final Set<String> lockedChats;

  /// Uygulama açılışında / arka plandan dönüşte kilit.
  final bool appLock;

  /// PIN yerine biyometri kullanılabilsin.
  final bool biometrics;

  bool isHidden(String chatId) => hiddenChats.contains(chatId);
  bool isLocked(String chatId) => lockedChats.contains(chatId);

  PrivacyPrefs copyWith({
    Set<String>? hiddenChats,
    Set<String>? lockedChats,
    bool? appLock,
    bool? biometrics,
  }) => PrivacyPrefs(
    hiddenChats: hiddenChats ?? this.hiddenChats,
    lockedChats: lockedChats ?? this.lockedChats,
    appLock: appLock ?? this.appLock,
    biometrics: biometrics ?? this.biometrics,
  );

  Map<String, Object?> toJson() => {
    'hidden': hiddenChats.toList()..sort(),
    'locked': lockedChats.toList()..sort(),
    'appLock': appLock,
    'bio': biometrics,
  };
}

final class PrivacyPrefsStore {
  PrivacyPrefsStore(this._kv, this._owner);

  final SecureKv _kv;
  final String _owner;

  String get _key => 'privacy.$_owner';

  Future<PrivacyPrefs> load() async {
    final raw = await _kv.read(_key);
    if (raw == null) return const PrivacyPrefs();
    try {
      return PrivacyPrefs.fromJson(jsonDecode(raw) as Map<String, Object?>);
    } catch (_) {
      return const PrivacyPrefs();
    }
  }

  Future<void> save(PrivacyPrefs p) => _kv.write(_key, jsonEncode(p.toJson()));
}
