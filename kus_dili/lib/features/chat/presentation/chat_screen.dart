import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../calls/application/call_controller.dart';
import '../../crypto/application/crypto_providers.dart';
import '../../privacy/application/privacy_providers.dart';
import '../../privacy/presentation/secure_scope.dart';
import '../../privacy/presentation/unlock_gate.dart';
import '../application/chat_controller.dart';
import '../application/chat_providers.dart';
import '../domain/chat.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';
import '../domain/message_view.dart';
import '../../media/application/media_providers.dart';
import '../../media/domain/attachment.dart';
import '../../media/presentation/attachment_view.dart';
import '../../media/presentation/voice_record_button.dart';
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
  Timer? _expiry;

  @override
  void dispose() {
    _expiry?.cancel();
    _input.dispose();
    super.dispose();
  }

  /// Süresi dolan mesajlar ekrandan anında kalksın.
  List<ChatMessage> _alive(List<ChatMessage> list) {
    final now = DateTime.now();
    final alive = [
      for (final m in list)
        if (!m.isExpired(now)) m,
    ];
    DateTime? next;
    for (final m in alive) {
      final e = m.expireAt;
      if (e != null && (next == null || e.isBefore(next))) next = e;
    }
    _expiry?.cancel();
    if (next != null) {
      _expiry = Timer(
        next.difference(now) + const Duration(milliseconds: 50),
        () {
          if (mounted) setState(() {});
        },
      );
    }
    return alive;
  }

  Future<void> _openViewOnce(
    ChatMessage m,
    MessagePayload p,
    ViewMode mode,
  ) async {
    final view = MessageView.of(p, viewMode: mode);
    await showDialog<void>(
      context: context,
      builder: (ctx) => SecureScope(
        enabled: true,
        child: AlertDialog(
          key: const Key('viewOnceDialog'),
          title: const Text('Tek görüntülük mesaj'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (p.attachment != null)
                AttachmentView(attachment: p.attachment!),
              if (p.hasText)
                Text(view.primary, style: Theme.of(ctx).textTheme.titleMedium),
              if (view.isEncodedNow) ...[
                const SizedBox(height: 8),
                Text(view.original, style: Theme.of(ctx).textTheme.bodySmall),
              ],
              const SizedBox(height: 12),
              const Text('Kapattığında bu mesaj silinecek.'),
            ],
          ),
          actions: [
            TextButton(
              key: const Key('closeViewOnce'),
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Kapat'),
            ),
          ],
        ),
      ),
    );
    await ref
        .read(chatControllerProvider(widget.chatId).notifier)
        .consumeViewOnce(m);
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
                  c.delete(m, payload: p);
                },
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _call(String peer, {required bool video}) async {
    final router = GoRouter.of(context);
    unawaited(router.push(Routes.call));
    try {
      await ref
          .read(callControllerProvider.notifier)
          .startCall(chatId: widget.chatId, peer: peer, video: video);
    } on CallUnavailableException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _pickAttachment() async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final (key, icon, label) in [
              ('gallery', Icons.photo_outlined, 'Galeriden fotoğraf'),
              ('camera', Icons.photo_camera_outlined, 'Fotoğraf çek'),
              ('video', Icons.videocam_outlined, 'Video'),
              ('file', Icons.description_outlined, 'Belge'),
            ])
              ListTile(
                key: Key('pick-$key'),
                leading: Icon(icon),
                title: Text(label),
                onTap: () => Navigator.pop(ctx, key),
              ),
          ],
        ),
      ),
    );
    if (choice == null || !mounted) return;
    final source = ref.read(mediaSourceProvider);
    try {
      final media = switch (choice) {
        'gallery' => await source.pickImage(camera: false),
        'camera' => await source.pickImage(camera: true),
        'video' => await source.pickVideo(camera: false),
        _ => await source.pickFile(),
      };
      if (media == null || !mounted) return;
      final caption = _input.text;
      final ok = await ref
          .read(chatControllerProvider(widget.chatId).notifier)
          .sendMedia(media, caption: caption);
      if (ok && _input.text == caption) _input.clear();
    } on AttachmentTooLargeException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final prefs = ref.watch(privacyPrefsProvider).value;
    final locked = prefs?.isLocked(widget.chatId) ?? false;
    if (locked && !ref.watch(unlockProvider).chats.contains(widget.chatId)) {
      return UnlockGate(
        key: const Key('chatLock'),
        title: 'Bu sohbet kilitli',
        onUnlocked: () =>
            ref.read(unlockProvider.notifier).unlockChat(widget.chatId),
      );
    }
    final me = ref.watch(myUidProvider);
    final chat = ref.watch(chatProvider(widget.chatId)).value;
    final messages = ref.watch(messagesProvider(widget.chatId));
    final settings =
        ref.watch(chatSettingsProvider(widget.chatId)).value ??
        const ChatSettings();
    final composer = ref.watch(chatControllerProvider(widget.chatId));
    final controller = ref.read(chatControllerProvider(widget.chatId).notifier);
    final isGroup = chat?.isGroup ?? widget.chatId.startsWith('g_');
    final otherUid = (chat != null && me != null && !isGroup)
        ? chat.otherMember(me)
        : null;
    final other = otherUid == null
        ? null
        : ref.watch(profileByUidProvider(otherUid)).value;

    final secret =
        (prefs?.isLocked(widget.chatId) ?? false) ||
        (prefs?.isHidden(widget.chatId) ?? false);
    return SecureScope(
      enabled: secret,
      child: Scaffold(
        appBar: AppBar(
          title: InkWell(
            key: const Key('chatTitle'),
            onTap: isGroup
                ? () => context.push(Routes.groupInfo(widget.chatId))
                : null,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(isGroup ? (chat?.name ?? '') : (other?.displayName ?? '')),
                if (isGroup && chat != null)
                  Text(
                    '${chat.members.length} üye · bilgi için dokun',
                    style: Theme.of(context).textTheme.labelSmall,
                  )
                else if (other != null)
                  Text(
                    '@${other.username}',
                    style: Theme.of(context).textTheme.labelSmall,
                  ),
              ],
            ),
          ),
          actions: [
            if (!isGroup && otherUid != null) ...[
              IconButton(
                key: const Key('voiceCall'),
                tooltip: 'Sesli arama',
                icon: const Icon(Icons.call_outlined),
                onPressed: () => _call(otherUid, video: false),
              ),
              IconButton(
                key: const Key('videoCall'),
                tooltip: 'Görüntülü arama',
                icon: const Icon(Icons.videocam_outlined),
                onPressed: () => _call(otherUid, video: true),
              ),
            ],
            IconButton(
              key: const Key('chatSettings'),
              tooltip: 'Sohbet dili',
              icon: const Icon(Icons.tune),
              onPressed: () => showModalBottomSheet<void>(
                context: context,
                isScrollControlled: true,
                builder: (_) => ChatSettingsSheet(
                  chatId: widget.chatId,
                  initial: settings,
                  onSave: controller.saveSettings,
                ),
              ),
            ),
          ],
        ),
        body: Column(
          children: [
            _E2eeBanner(
              chatId: widget.chatId,
              otherUid: otherUid,
              isGroup: isGroup,
            ),
            if (isGroup &&
                chat != null &&
                me != null &&
                !chat.members.contains(me))
              const Padding(
                padding: EdgeInsets.all(8),
                child: Text('Artık bu grubun üyesi değilsin.'),
              ),
            Expanded(
              child: messages.when(
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (e, _) =>
                    const Center(child: Text('Mesajlar yüklenemedi.')),
                data: (all) {
                  final list = _alive(all);
                  if (me != null) {
                    WidgetsBinding.instance.addPostFrameCallback(
                      (_) => _maybeMarkRead(list, chat, me),
                    );
                  }
                  // Limit doluysa daha eski mesajlar olabilir.
                  final more =
                      all.length >=
                      ref.watch(messageLimitProvider(widget.chatId));
                  return ListView.builder(
                    reverse: true,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    itemCount: list.length + (more ? 1 : 0),
                    itemBuilder: (context, i) {
                      if (i == list.length) {
                        return Center(
                          child: TextButton.icon(
                            key: const Key('loadOlder'),
                            icon: const Icon(Icons.history),
                            label: const Text('Daha eski mesajlar'),
                            onPressed: () => ref
                                .read(
                                  messageLimitProvider(widget.chatId).notifier,
                                )
                                .loadMore(),
                          ),
                        );
                      }
                      final m = list[list.length - 1 - i];
                      final mine = m.senderId == me;
                      return MessageBubble(
                        key: ValueKey(m.id),
                        showSender: isGroup && !mine,
                        chatId: widget.chatId,
                        message: m,
                        isMine: mine,
                        viewMode: mine ? ViewMode.asSent : settings.viewMode,
                        chat: chat,
                        onLongPress: (p) => _actions(m, p, mine),
                        onOpenViewOnce: (p) =>
                            _openViewOnce(m, p, settings.viewMode),
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
              onToggleViewOnce: controller.toggleViewOnce,
              onAttach: _pickAttachment,
              onVoice: (media) async {
                await controller.sendMedia(media);
              },
              onSend: () async {
                final text = _input.text;
                final ok = await controller.submit(text);
                if (ok && _input.text == text) _input.clear();
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _E2eeBanner extends ConsumerWidget {
  const _E2eeBanner({
    required this.chatId,
    required this.otherUid,
    required this.isGroup,
  });

  final String chatId;
  final String? otherUid;
  final bool isGroup;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final uid = otherUid;
    final changed =
        uid != null && (ref.watch(identityChangedProvider(uid)).value ?? false);
    final color = changed ? AppColors.gold : AppColors.turquoise;
    return InkWell(
      key: const Key('e2eeBanner'),
      onTap: isGroup
          ? () => context.push(Routes.groupInfo(chatId))
          : uid == null
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
                    : isGroup
                    ? 'Grup mesajları uçtan uca şifreli. Üyeler için dokun.'
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
    required this.onToggleViewOnce,
    required this.onAttach,
    required this.onVoice,
  });

  final VoidCallback onAttach;
  final Future<void> Function(PickedMedia) onVoice;
  final TextEditingController input;
  final ComposerState state;
  final ChatSettings settings;
  final VoidCallback onSend;
  final VoidCallback onCancel;
  final VoidCallback onToggleViewOnce;

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
            if (s.busy)
              const Padding(
                padding: EdgeInsets.fromLTRB(8, 0, 8, 4),
                child: LinearProgressIndicator(key: Key('sending')),
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
                if (editing == null)
                  IconButton(
                    key: const Key('attach'),
                    tooltip: 'Fotoğraf, video, belge',
                    icon: const Icon(Icons.attach_file),
                    onPressed: s.busy ? null : widget.onAttach,
                  ),
                if (editing == null)
                  IconButton(
                    key: const Key('viewOnceToggle'),
                    tooltip: 'Tek görüntülük',
                    icon: Icon(
                      Icons.looks_one_outlined,
                      color: s.viewOnce ? AppColors.turquoise : AppColors.snow,
                    ),
                    onPressed: widget.onToggleViewOnce,
                  ),
                if (widget.settings.disappearSeconds > 0)
                  const Padding(
                    padding: EdgeInsets.only(bottom: 12),
                    child: Icon(
                      Icons.timer_outlined,
                      key: Key('disappearOn'),
                      size: 18,
                      color: AppColors.gold,
                    ),
                  ),
                const SizedBox(width: 4),
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
                if (editing == null && typed.trim().isEmpty)
                  VoiceRecordButton(
                    enabled: !s.busy,
                    onRecorded: widget.onVoice,
                  )
                else
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
