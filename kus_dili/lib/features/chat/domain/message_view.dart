import 'package:kus_dili_engine/kus_dili_engine.dart';

import 'chat_settings.dart';
import 'message_payload.dart';

/// Bir mesajın ekranda nasıl gösterileceği. Saf fonksiyonla hesaplanır
/// (cihaz üzerinde, çevrimdışı), bu yüzden kolayca test edilir.
final class MessageView {
  const MessageView({
    required this.primary,
    required this.original,
    required this.isKusDili,
    this.secondary,
  });

  factory MessageView.of(
    MessagePayload p, {
    required ViewMode viewMode,
    bool revealed = false,
  }) {
    if (p.mode != DisplayMode.kusDili) {
      return MessageView(primary: p.text, original: p.text, isKusDili: false);
    }
    final kus = KusDiliCodec(p.variant).encode(p.text);
    if (revealed || viewMode == ViewMode.original) {
      return MessageView(primary: p.text, original: p.text, isKusDili: true);
    }
    return MessageView(
      primary: kus,
      original: p.text,
      isKusDili: true,
      secondary: viewMode == ViewMode.both ? p.text : null,
    );
  }

  /// Balonda büyük gösterilen metin.
  final String primary;

  /// "Çöz" ile gösterilecek orijinal Türkçe.
  final String original;

  /// Altında küçük gösterilen (ör. "ikisi birden" modunda Türkçe).
  final String? secondary;

  final bool isKusDili;

  /// Şu an kuş dili mi görünüyor (çözülmemiş)?
  bool get isEncodedNow => isKusDili && primary != original;
}
