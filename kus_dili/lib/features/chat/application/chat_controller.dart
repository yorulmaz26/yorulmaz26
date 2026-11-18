import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../crypto/domain/signal_sealer.dart';
import '../../media/application/media_providers.dart';
import '../../media/domain/attachment.dart';
import '../domain/chat.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';
import 'chat_providers.dart';

final class ComposerState {
  const ComposerState({
    this.replyTo,
    this.editing,
    this.busy = false,
    this.error,
    this.viewOnce = false,
  });

  /// Yanıtlanan mesaj (kimlik + alıntı).
  final ({String id, String quote})? replyTo;

  /// Düzenlenen mesaj ve özgün paketi.
  final ({String id, MessagePayload payload})? editing;
  final bool busy;
  final String? error;

  /// Bir sonraki mesaj tek görüntülük olsun.
  final bool viewOnce;
}

/// Bir sohbetin mesaj yazma alanı ve mesaj işlemleri.
class ChatController extends Notifier<ComposerState> {
  ChatController(this.chatId);

  final String chatId;

  @override
  ComposerState build() => const ComposerState();

  ChatSettings get _settings =>
      ref.read(chatSettingsProvider(chatId)).value ?? const ChatSettings();

  void replyTo(String messageId, MessagePayload payload) =>
      state = ComposerState(
        replyTo: (
          id: messageId,
          quote: MessagePayload.quoteOf(payload.summary),
        ),
      );

  void startEdit(String messageId, MessagePayload payload) =>
      state = ComposerState(editing: (id: messageId, payload: payload));

  void cancel() => state = const ComposerState();

  void toggleViewOnce() =>
      state = ComposerState(replyTo: state.replyTo, viewOnce: !state.viewOnce);

  /// Gönderir veya düzenlemeyi kaydeder. Başarılıysa `true`.
  /// Fotoğraf/video/ses/belge gönderir: dosya cihazda şifrelenip yüklenir,
  /// anahtarı şifreli mesaj paketinde gider. [caption] kuş diline çevrilir.
  Future<bool> sendMedia(PickedMedia media, {String caption = ''}) async {
    final uid = ref.read(myUidProvider);
    if (uid == null || state.busy) return false;
    final reply = state.replyTo;
    final viewOnce = state.viewOnce;
    state = ComposerState(replyTo: reply, viewOnce: viewOnce, busy: true);
    try {
      final att = await ref
          .read(attachmentServiceProvider)
          .upload(chatId: chatId, uploader: uid, media: media);
      state = ComposerState(replyTo: reply, viewOnce: viewOnce);
      return await submit(caption, attachment: att);
    } on AttachmentTooLargeException catch (e) {
      if (ref.mounted) {
        state = ComposerState(
          replyTo: reply,
          viewOnce: viewOnce,
          error: e.message,
        );
      }
    } catch (_) {
      if (ref.mounted) {
        state = ComposerState(
          replyTo: reply,
          viewOnce: viewOnce,
          error: 'Dosya yüklenemedi. Bağlantını kontrol et.',
        );
      }
    }
    return false;
  }

  Future<bool> submit(String text, {Attachment? attachment}) async {
    final uid = ref.read(myUidProvider);
    if (uid == null || state.busy) return false;
    if (text.trim().isEmpty && attachment == null) return false;
    final repo = ref.read(chatRepositoryProvider);
    final sealer = ref.read(messageSealerProvider);
    final editing = state.editing;
    final reply = state.replyTo;
    final viewOnce = editing == null && state.viewOnce;
    final disappear = _settings.disappearSeconds;
    final MessagePayload payload;
    try {
      payload = editing != null
          ? MessagePayload(
              text: text,
              mode: editing.payload.mode,
              variant: editing.payload.variant,
              replyToId: editing.payload.replyToId,
              replyQuote: editing.payload.replyQuote,
              attachment: editing.payload.attachment,
            )
          : MessagePayload(
              text: text,
              mode: _settings.sendMode,
              variant: _settings.variant,
              replyToId: reply?.id,
              replyQuote: reply?.quote,
              expiresInSeconds: disappear > 0 ? disappear : null,
              viewOnce: viewOnce,
              attachment: attachment,
            );
    } on PayloadFormatException catch (e) {
      state = ComposerState(replyTo: reply, editing: editing, error: e.message);
      return false;
    }
    state = const ComposerState(busy: true);
    try {
      final body = await sealer.seal(chatId, payload);
      if (editing != null) {
        await repo.editMessage(
          chatId: chatId,
          messageId: editing.id,
          body: body,
        );
      } else {
        // Önce kimlik üretilir: ağ hatasında aynı mesaj tekrar yazılsa bile
        // çiftlenmez. Firestore yazmayı çevrimdışıyken kuyruğa alır.
        final id = repo.newMessageId(chatId);
        await repo.sendMessage(
          chatId: chatId,
          messageId: id,
          senderId: uid,
          encVersion: sealer.encVersion,
          body: body,
          expireAt: disappear > 0
              ? DateTime.now().add(Duration(seconds: disappear))
              : null,
          viewOnce: viewOnce,
        );
      }
      if (ref.mounted) state = const ComposerState();
      return true;
    } on RecipientHasNoKeysException {
      if (ref.mounted) {
        state = ComposerState(
          replyTo: reply,
          editing: editing,
          error:
              'Karşı taraf henüz şifreli mesaj alamıyor; uygulamayı '
              'güncelleyip bir kez açması gerekiyor.',
        );
      }
      return false;
    } catch (_) {
      if (ref.mounted) {
        state = ComposerState(
          replyTo: reply,
          editing: editing,
          error: 'Mesaj gönderilemedi. Bağlantınızı kontrol edin.',
        );
      }
      return false;
    }
  }

  /// Herkesten sil: mesaj içeriği ve (varsa) şifreli ek dosya kaldırılır.
  Future<void> delete(ChatMessage m, {MessagePayload? payload}) async {
    try {
      await ref
          .read(chatRepositoryProvider)
          .deleteMessage(chatId: chatId, messageId: m.id);
      final att = payload?.attachment;
      if (att != null) {
        try {
          await ref.read(attachmentServiceProvider).delete(att);
        } catch (_) {
          // Dosya zaten silinmiş olabilir; mesaj silindiği için sorun değil.
        }
      }
    } catch (_) {
      if (ref.mounted) {
        state = const ComposerState(error: 'Mesaj silinemedi.');
      }
    }
  }

  /// Tek görüntülük mesaj okundu: sunucudan ve bu cihazdan silinir.
  Future<void> consumeViewOnce(ChatMessage m) async {
    try {
      await ref
          .read(chatRepositoryProvider)
          .consumeViewOnce(chatId: chatId, messageId: m.id);
      await ref.read(messageSealerProvider).forget(chatId, m.body);
    } catch (_) {
      if (ref.mounted) state = const ComposerState(error: 'Mesaj silinemedi.');
    }
  }

  Future<void> markRead() async {
    final uid = ref.read(myUidProvider);
    if (uid == null) return;
    try {
      await ref.read(chatRepositoryProvider).markRead(chatId: chatId, uid: uid);
    } catch (_) {
      // Okundu bilgisi kritik değil; sessizce yeniden denenecek.
    }
  }

  Future<void> saveSettings(ChatSettings s) async {
    final uid = ref.read(myUidProvider);
    if (uid == null) return;
    await ref
        .read(chatSettingsRepositoryProvider)
        .save(uid: uid, chatId: chatId, settings: s);
  }
}

final chatControllerProvider =
    NotifierProvider.family<ChatController, ComposerState, String>(
      ChatController.new,
    );
