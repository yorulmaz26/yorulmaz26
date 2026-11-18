import 'package:kus_dili_engine/kus_dili_engine.dart';

import 'message_payload.dart';

/// Gelen kuş dili mesajlarının bende nasıl görüneceği.
enum ViewMode {
  /// Gönderenin seçtiği biçimde (kuş dili); dokununca çözülür.
  asSent('asSent'),

  /// Her zaman orijinal Türkçe.
  original('original'),

  /// Kuş dili ve altında Türkçesi.
  both('both');

  const ViewMode(this.code);
  final String code;

  static ViewMode fromCode(Object? c) =>
      values.firstWhere((m) => m.code == c, orElse: () => ViewMode.asSent);
}

/// Kullanıcının bir sohbet için tercihleri (her üyenin kendine ait).
final class ChatSettings {
  const ChatSettings({
    this.sendMode = DisplayMode.kusDili,
    this.variant = KusDiliVariant.g,
    this.viewMode = ViewMode.asSent,
    this.disappearSeconds = 0,
  });

  /// Süreli mesaj seçenekleri (saniye). 0 = kapalı.
  static const disappearOptions = <int, String>{
    0: 'Kapalı',
    30: '30 saniye',
    300: '5 dakika',
    3600: '1 saat',
    86400: '1 gün',
    604800: '1 hafta',
  };

  factory ChatSettings.fromJson(Map<String, Object?> j) {
    var variant = KusDiliVariant.g;
    final raw = j['variant'];
    if (raw is Map<String, Object?>) {
      try {
        variant = KusDiliVariant.fromJson(raw);
      } on KusDiliRuleException {
        // geçersiz kayıt: varsayılan
      }
    }
    return ChatSettings(
      sendMode: j['sendMode'] == null
          ? const ChatSettings().sendMode
          : DisplayMode.fromCode(j['sendMode']),
      variant: variant,
      viewMode: ViewMode.fromCode(j['viewMode']),
      disappearSeconds: disappearOptions.containsKey(j['disappear'])
          ? j['disappear']! as int
          : 0,
    );
  }

  /// Benim gönderdiğim mesajların biçimi (varsayılan: otomatik kuş dili).
  final DisplayMode sendMode;
  final KusDiliVariant variant;
  final ViewMode viewMode;

  /// Gönderdiğim mesajlar bu süre sonra silinir (0 = kapalı).
  final int disappearSeconds;

  ChatSettings copyWith({
    DisplayMode? sendMode,
    KusDiliVariant? variant,
    ViewMode? viewMode,
    int? disappearSeconds,
  }) => ChatSettings(
    sendMode: sendMode ?? this.sendMode,
    variant: variant ?? this.variant,
    viewMode: viewMode ?? this.viewMode,
    disappearSeconds: disappearSeconds ?? this.disappearSeconds,
  );

  Map<String, Object?> toJson() => {
    'sendMode': sendMode.code,
    'variant': variant.toJson(),
    'viewMode': viewMode.code,
    'disappear': disappearSeconds,
  };
}

abstract interface class ChatSettingsRepository {
  Stream<ChatSettings> watch({required String uid, required String chatId});

  Future<void> save({
    required String uid,
    required String chatId,
    required ChatSettings settings,
  });
}
