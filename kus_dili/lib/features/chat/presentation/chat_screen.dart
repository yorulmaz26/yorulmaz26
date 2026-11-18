import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../crypto/application/crypto_providers.dart';
import '../application/chat_controller.dart';
import '../application/chat_providers.dart';
import '../domain/chat.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';
import 'chat_settings_sheet.dart';
import 'widgets/message_bubble.dart';

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key, required this.chatId});

  final String chatId;

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final _input = TextEditingController();
  DateTime? _lastMarked;

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  void _maybeMarkRead(List<ChatMessage> messages, Chat? chat, String me) {
    if (chat == null || messages.isEmpty) return;
    final last = messages.lastWhere(
      (m) => m.senderId != me && m.sentAt != null,
      orElse: () => messages.first,
    );
    if (last.senderId == me || last.sentAt == null) return;
    final read = chat.readAt[me];
    if (read != null && !read.isBefore(last.sentAt!)) return;
    if (_lastMarked == last.sentAt) return;
    _lastMarked = last.sentAt;
    ref.read(chatControllerProvider(widget.chatId).notifier).markRead();
  }

  void _actions(ChatMessage m, MessagePayload p, bool mine) {
    final c = ref.read(chatControllerProvider(widget.chatId).notifier);
    showModalBottomSheet<void>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.reply),
              title: const Text('Yanıtla'),
              onTap: () {
                Navigator.pop(ctx);
                c.replyTo(m.id, p);
              },
            ),
            ListTile(
              leading: const Icon(Icons.copy),
              title: const Text('Türkçesini kopyala'),
              onTap: () {
                Navigator.pop(ctx);
                Clipboard.setData(ClipboardData(text: p.text));
              },
            ),
            if (p.mode == DisplayMode.kusDili)
              ListTile(
                leading: const Icon(Icons.flutter_dash),
                title: const Text('Kuş dilini kopyala'),
                onTap: () {
                  Navigator.pop(ctx);
                  Clipboard.setData(
                    ClipboardData(text: KusDiliCodec(p.variant).encode(p.text)),
                  );
                },
              ),
            if (mine) ...[
              ListTile(
                key: const Key('editMessage'),
                leading: const Icon(Icons.edit_outlined),
                title: const Text('Düzenle'),
                onTap: () {
                  Navigator.pop(ctx);
                  c.startEdit(m.id, p);
                  _input.text = p.text;
                },
              ),
              ListTile(
                key: const Key('deleteMessage'),
                leading: const Icon(Icons.delete_outline),
                title: const Text('Herkesten sil'),
                onTap: () {
                  Navigator.pop(ctx);
                  c.delete(m);
                },
              ),
            ],
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final me = ref.watch(myUidProvider);
    final chat = ref.watch(chatProvider(widget.chatId)).value;
    final messages = ref.watch(messagesProvider(widget.chatId));
    final settings =
        ref.watch(chatSettingsProvider(widget.chatId)).value ??
        const ChatSettings();
    final composer = ref.watch(chatControllerProvider(widget.chatId));
    final controller = ref.read(chatControllerProvider(widget.chatId).notifier);
    final otherUid = (chat != null && me != null) ? chat.otherMember(me) : null;
    final other = otherUid == null
        ? null
        : ref.watch(profileByUidProvider(otherUid)).value;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(other?.displayName ?? ''),
            if (other != null)
              Text(
                '@${other.username}',
                style: Theme.of(context).textTheme.labelSmall,
              ),
          ],
        ),
        actions: [
          IconButton(
            key: const Key('chatSettings'),
            tooltip: 'Sohbet dili',
            icon: const Icon(Icons.tune),
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              builder: (_) => ChatSettingsSheet(
                initial: settings,
                onSave: controller.saveSettings,
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          _E2eeBanner(chatId: widget.chatId, otherUid: otherUid),
          Expanded(
            child: messages.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) =>
                  const Center(child: Text('Mesajlar yüklenemedi.')),
              data: (list) {
                if (me != null) {
                  WidgetsBinding.instance.addPostFrameCallback(
                    (_) => _maybeMarkRead(list, chat, me),
                  );
                }
                return ListView.builder(
                  reverse: true,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: list.length,
                  itemBuilder: (context, i) {
                    final m = list[list.length - 1 - i];
                    final mine = m.senderId == me;
                    return MessageBubble(
                      key: ValueKey(m.id),
                      chatId: widget.chatId,
                      message: m,
                      isMine: mine,
                      viewMode: mine ? ViewMode.asSent : settings.viewMode,
                      chat: chat,
                      onLongPress: (p) => _actions(m, p, mine),
                    );
                  },
                );
              },
            ),
          ),
          _Composer(
            input: _input,
            state: composer,
            settings: settings,
            onCancel: () {
              if (composer.editing != null) _input.clear();
              controller.cancel();
            },
            onSend: () async {
              final text = _input.text;
              final ok = await controller.submit(text);
              if (ok && _input.text == text) _input.clear();
            },
          ),
        ],
      ),
    );
  }
}

class _E2eeBanner extends ConsumerWidget {
  const _E2eeBanner({required this.chatId, required this.otherUid});

  final String chatId;
  final String? otherUid;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final uid = otherUid;
    final changed =
        uid != null && (ref.watch(identityChangedProvider(uid)).value ?? false);
    final color = changed ? AppColors.gold : AppColors.turquoise;
    return InkWell(
      key: const Key('e2eeBanner'),
      onTap: uid == null
          ? null
          : () => context.push(Routes.safetyNumber(chatId, uid)),
      child: Container(
        width: double.infinity,
        color: color.withValues(alpha: 0.12),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          children: [
            Icon(
              changed ? Icons.warning_amber : Icons.lock,
              size: 14,
              color: color,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                changed
                    ? 'Karşı tarafın güvenlik numarası değişti. Doğrulamak için dokun.'
                    : 'Mesajlar uçtan uca şifreli. Güvenlik numarası için dokun.',
                style: Theme.of(context).textTheme.labelSmall
                    ?.copyWith(color: color),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatefulWidget {
  const _Composer({
    required this.input,
    required this.state,
    required this.settings,
    required this.onSend,
    required this.onCancel,
  });

  final TextEditingController input;
  final ComposerState state;
  final ChatSettings settings;
  final VoidCallback onSend;
  final VoidCallback onCancel;

  @override
  State<_Composer> createState() => _ComposerState();
}

class _ComposerState extends State<_Composer> {
  @override
  void initState() {
    super.initState();
    widget.input.addListener(_onText);
  }

  @override
  void dispose() {
    widget.input.removeListener(_onText);
    super.dispose();
  }

  void _onText() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final s = widget.state;
    final text = Theme.of(context).textTheme;
    final editing = s.editing;
    final kus = editing != null
        ? editing.payload.mode == DisplayMode.kusDili
        : widget.settings.sendMode == DisplayMode.kusDili;
    final variant = editing?.payload.variant ?? widget.settings.variant;
    final typed = widget.input.text;

    return SafeArea(
      top: false,
      child: Container(
        color: AppColors.nightRaised,
        padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (s.replyTo != null || editing != null)
              Row(
                children: [
                  Icon(
                    editing != null ? Icons.edit_outlined : Icons.reply,
                    size: 16,
                    color: AppColors.turquoise,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      editing != null
                          ? 'Mesajı düzenliyorsun'
                          : s.replyTo!.quote,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: text.bodySmall,
                    ),
                  ),
                  IconButton(
                    key: const Key('cancelCompose'),
                    icon: const Icon(Icons.close, size: 18),
                    onPressed: widget.onCancel,
                  ),
                ],
              ),
            if (kus && typed.trim().isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 0, 8, 4),
                child: Text(
                  KusDiliCodec(variant).encode(typed),
                  key: const Key('livePreview'),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: text.bodySmall?.copyWith(color: AppColors.gold),
                ),
              ),
            if (s.error != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(8, 0, 8, 4),
                child: Text(
                  s.error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Padding(
                  padding: const EdgeInsets.only(bottom: 12, left: 4),
                  child: Icon(
                    kus ? Icons.flutter_dash : Icons.chat_bubble_outline,
                    color: kus ? AppColors.gold : AppColors.snow,
                    size: 22,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    key: const Key('messageInput'),
                    controller: widget.input,
                    minLines: 1,
                    maxLines: 5,
                    maxLength: MessagePayload.maxTextLength,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: InputDecoration(
                      counterText: '',
                      hintText: kus
                          ? 'Türkçe yaz, kuş diline çevrilsin'
                          : 'Mesaj',
                      isDense: true,
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                IconButton.filled(
                  key: const Key('send'),
                  onPressed: s.busy || typed.trim().isEmpty
                      ? null
                      : widget.onSend,
                  icon: Icon(editing != null ? Icons.check : Icons.send),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
