import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../../app/theme/app_colors.dart';
import '../../application/chat_providers.dart';
import '../../domain/chat.dart';
import '../../domain/chat_settings.dart';
import '../../domain/message_payload.dart';
import '../../domain/message_view.dart';

/// Tek mesaj balonu. Kuş dili mesajları altın çerçeveli özel balonda,
/// kuş simgesiyle gösterilir; dokunulunca zarif bir geçişle çözülür.
class MessageBubble extends ConsumerStatefulWidget {
  const MessageBubble({
    super.key,
    required this.chatId,
    required this.message,
    required this.isMine,
    required this.viewMode,
    required this.chat,
    required this.onLongPress,
    required this.onOpenViewOnce,
    this.showSender = false,
  });

  /// Grupta başkasının mesajı: üstte gönderen adı.
  final bool showSender;

  final String chatId;
  final ChatMessage message;
  final bool isMine;
  final ViewMode viewMode;
  final Chat? chat;
  final void Function(MessagePayload payload) onLongPress;
  final void Function(MessagePayload payload) onOpenViewOnce;

  @override
  ConsumerState<MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends ConsumerState<MessageBubble> {
  bool _revealed = false;

  @override
  Widget build(BuildContext context) {
    final m = widget.message;
    final text = Theme.of(context).textTheme;
    final align = widget.isMine ? Alignment.centerRight : Alignment.centerLeft;

    if (m.deleted) {
      return _frame(
        align: align,
        color: AppColors.nightRaised,
        border: null,
        child: Text(
          m.viewOnce ? 'Tek görüntülük mesaj açıldı' : 'Bu mesaj silindi',
          style: text.bodyMedium?.copyWith(
            fontStyle: FontStyle.italic,
            color: AppColors.snow.withValues(alpha: 0.6),
          ),
        ),
      );
    }

    final opened = ref.watch(
      openedPayloadProvider((
        chatId: widget.chatId,
        senderId: m.senderId,
        encVersion: m.encVersion,
        body: m.body,
      )),
    );

    return opened.when(
      loading: () => _frame(
        align: align,
        color: AppColors.nightRaised,
        border: null,
        child: const SizedBox(width: 40, height: 16),
      ),
      error: (e, _) => _frame(
        align: align,
        color: AppColors.nightRaised,
        border: null,
        child: Text(
          'Bu mesaj bu cihazda açılamıyor.',
          style: text.bodyMedium?.copyWith(fontStyle: FontStyle.italic),
        ),
      ),
      data: (payload) {
        if (m.viewOnce) {
          return GestureDetector(
            key: ValueKey('msg-${m.id}'),
            onTap: widget.isMine ? null : () => widget.onOpenViewOnce(payload),
            child: _frame(
              align: align,
              color: widget.isMine ? AppColors.violet : AppColors.nightRaised,
              border: Border.all(color: AppColors.turquoise, width: 1.2),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.looks_one_outlined,
                    size: 18,
                    color: AppColors.turquoise,
                  ),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      widget.isMine
                          ? 'Tek görüntülük mesaj gönderildi'
                          : 'Tek görüntülük mesaj · açmak için dokun',
                      style: text.bodyMedium?.copyWith(color: AppColors.snow),
                    ),
                  ),
                ],
              ),
            ),
          );
        }
        final view = MessageView.of(
          payload,
          viewMode: widget.viewMode,
          revealed: _revealed,
        );
        final bg = widget.isMine ? AppColors.violet : AppColors.nightRaised;
        return GestureDetector(
          key: ValueKey('msg-${m.id}'),
          onTap: view.isKusDili
              ? () => setState(() => _revealed = !_revealed)
              : null,
          onLongPress: () => widget.onLongPress(payload),
          child: _frame(
            align: align,
            color: bg,
            border: view.isKusDili
                ? Border.all(color: AppColors.gold, width: 1.2)
                : null,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (widget.showSender)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 2),
                    child: Text(
                      ref
                              .watch(profileByUidProvider(m.senderId))
                              .value
                              ?.displayName ??
                          '…',
                      key: ValueKey('sender-${m.id}'),
                      style: text.labelMedium?.copyWith(
                        color: AppColors.turquoise,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                if (payload.replyQuote != null)
                  Container(
                    margin: const EdgeInsets.only(bottom: 6),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.night.withValues(alpha: 0.35),
                      border: const Border(
                        left: BorderSide(color: AppColors.turquoise, width: 3),
                      ),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      payload.replyQuote!,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: text.bodySmall,
                    ),
                  ),
                AnimatedSize(
                  duration: const Duration(milliseconds: 280),
                  curve: Curves.easeOutCubic,
                  alignment: Alignment.topLeft,
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 380),
                    switchInCurve: Curves.easeOutCubic,
                    switchOutCurve: Curves.easeInCubic,
                    transitionBuilder: (child, anim) => FadeTransition(
                      opacity: anim,
                      child: SlideTransition(
                        position: Tween(
                          begin: const Offset(0, 0.25),
                          end: Offset.zero,
                        ).animate(anim),
                        child: child,
                      ),
                    ),
                    child: Text(
                      view.primary,
                      key: ValueKey(view.primary),
                      style: text.bodyLarge?.copyWith(color: AppColors.snow),
                    ),
                  ),
                ),
                if (view.secondary != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    view.secondary!,
                    style: text.bodySmall?.copyWith(
                      color: AppColors.snow.withValues(alpha: 0.75),
                    ),
                  ),
                ],
                const SizedBox(height: 4),
                _Meta(
                  message: m,
                  isMine: widget.isMine,
                  chat: widget.chat,
                  isKusDili: view.isKusDili,
                  encodedNow: view.isEncodedNow,
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _frame({
    required Alignment align,
    required Color color,
    required BoxBorder? border,
    required Widget child,
  }) {
    return Align(
      alignment: align,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.78,
        ),
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 3),
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 6),
          decoration: BoxDecoration(
            color: color,
            border: border,
            borderRadius: BorderRadius.only(
              topLeft: const Radius.circular(18),
              topRight: const Radius.circular(18),
              bottomLeft: Radius.circular(widget.isMine ? 18 : 4),
              bottomRight: Radius.circular(widget.isMine ? 4 : 18),
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}

class _Meta extends StatelessWidget {
  const _Meta({
    required this.message,
    required this.isMine,
    required this.chat,
    required this.isKusDili,
    required this.encodedNow,
  });

  final ChatMessage message;
  final bool isMine;
  final Chat? chat;
  final bool isKusDili;
  final bool encodedNow;

  @override
  Widget build(BuildContext context) {
    final small = Theme.of(context).textTheme.labelSmall
        ?.copyWith(color: AppColors.snow.withValues(alpha: 0.7));
    final t = message.sentAt?.toLocal();
    final time = t == null
        ? ''
        : '${t.hour.toString().padLeft(2, '0')}:'
              '${t.minute.toString().padLeft(2, '0')}';
    Widget? status;
    if (isMine) {
      if (message.pending || message.sentAt == null) {
        status = const Icon(Icons.schedule, size: 13, key: Key('pending'));
      } else if (chat != null && message.isReadByAll(chat!)) {
        status = const Icon(
          Icons.done_all,
          size: 14,
          color: AppColors.turquoise,
          key: Key('read'),
        );
      } else {
        status = const Icon(Icons.done, size: 14, key: Key('sent'));
      }
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (isKusDili) ...[
          Icon(
            encodedNow ? Icons.flutter_dash : Icons.lock_open,
            size: 13,
            color: AppColors.gold,
          ),
          const SizedBox(width: 4),
        ],
        if (message.expireAt != null) ...[
          Icon(Icons.timer_outlined, size: 12, color: small?.color),
          const SizedBox(width: 3),
        ],
        if (message.editedAt != null) Text('düzenlendi · ', style: small),
        Text(time, style: small),
        if (status != null) ...[
          const SizedBox(width: 4),
          IconTheme(
            data: IconThemeData(color: AppColors.snow.withValues(alpha: 0.7)),
            child: status,
          ),
        ],
      ],
    );
  }
}
