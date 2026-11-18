import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';
import 'package:material_ui/material_ui.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:video_player/video_player.dart';

import '../../../app/theme/app_colors.dart';
import '../application/media_providers.dart';
import '../domain/attachment.dart';

/// Mesaj balonundaki ek. İçerik indirilip cihazda çözülür.
class AttachmentView extends ConsumerWidget {
  const AttachmentView({super.key, required this.attachment});

  final Attachment attachment;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return switch (attachment.kind) {
      AttachmentKind.image => _ImageAttachment(attachment),
      AttachmentKind.audio => VoiceNotePlayer(attachment: attachment),
      AttachmentKind.video => _FileLikeTile(
        attachment: attachment,
        icon: Icons.play_circle_outline,
        label: 'Video',
        onOpen: (bytes) => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => _VideoScreen(attachment: attachment, bytes: bytes),
          ),
        ),
      ),
      AttachmentKind.file => _FileLikeTile(
        attachment: attachment,
        icon: Icons.description_outlined,
        label: attachment.name ?? 'Belge',
        onOpen: (bytes) => _shareFile(attachment, bytes),
      ),
    };
  }
}

const _failText = 'Dosya açılamadı. Silinmiş ya da değiştirilmiş olabilir.';

class _ImageAttachment extends ConsumerWidget {
  const _ImageAttachment(this.a);

  final Attachment a;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bytes = ref.watch(attachmentBytesProvider(a));
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 280, minWidth: 160),
        child: bytes.when(
          loading: () => const SizedBox(
            width: 200,
            height: 160,
            child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
          ),
          error: (_, _) => const Padding(
            padding: EdgeInsets.all(12),
            child: Text(_failText),
          ),
          data: (b) => GestureDetector(
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => _ImageScreen(bytes: b)),
            ),
            child: Image.memory(
              b,
              key: const Key('attImage'),
              fit: BoxFit.cover,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) => const Padding(
                padding: EdgeInsets.all(12),
                child: Text('Görüntü gösterilemiyor.'),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ImageScreen extends StatelessWidget {
  const _ImageScreen({required this.bytes});
  final Uint8List bytes;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    appBar: AppBar(backgroundColor: Colors.black),
    body: Center(
      child: InteractiveViewer(maxScale: 5, child: Image.memory(bytes)),
    ),
  );
}

/// Video ve belge: dokununca indirip çöz.
class _FileLikeTile extends ConsumerStatefulWidget {
  const _FileLikeTile({
    required this.attachment,
    required this.icon,
    required this.label,
    required this.onOpen,
  });

  final Attachment attachment;
  final IconData icon;
  final String label;
  final FutureOr<void> Function(Uint8List bytes) onOpen;

  @override
  ConsumerState<_FileLikeTile> createState() => _FileLikeTileState();
}

class _FileLikeTileState extends ConsumerState<_FileLikeTile> {
  bool _busy = false;
  String? _error;

  Future<void> _open() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final b = await ref
          .read(attachmentServiceProvider)
          .download(widget.attachment);
      if (mounted) await widget.onOpen(b);
    } catch (_) {
      if (mounted) setState(() => _error = _failText);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.attachment;
    final details = [
      Attachment.formatSize(a.size),
      if (a.durationMs != null) Attachment.formatDuration(a.durationMs!),
    ].join(' · ');
    return InkWell(
      key: Key('att-${a.kind.code}'),
      onTap: _busy ? null : _open,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _busy
                ? const SizedBox.square(
                    dimension: 32,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(widget.icon, size: 32, color: AppColors.gold),
            const SizedBox(width: 10),
            Flexible(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  Text(details, style: Theme.of(context).textTheme.labelSmall),
                  if (_error != null)
                    Text(
                      _error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Çözülmüş içeriği geçici dosyaya yazar; [use] bitince dosyayı siler.
Future<T> _withTempFile<T>(
  Attachment a,
  Uint8List bytes,
  Future<T> Function(File f) use,
) async {
  final dir = await getTemporaryDirectory();
  final safe = (a.name ?? 'dosya').replaceAll(RegExp(r'[^\w.\-]'), '_');
  final f = File(
    '${dir.path}/kus_${DateTime.now().microsecondsSinceEpoch}_$safe',
  );
  await f.writeAsBytes(bytes, flush: true);
  try {
    return await use(f);
  } finally {
    if (await f.exists()) await f.delete();
  }
}

Future<void> _shareFile(Attachment a, Uint8List bytes) => _withTempFile(
  a,
  bytes,
  (f) => SharePlus.instance.share(
    ShareParams(
      files: [XFile(f.path, mimeType: a.mime, name: a.name)],
    ),
  ),
);

class _VideoScreen extends StatefulWidget {
  const _VideoScreen({required this.attachment, required this.bytes});

  final Attachment attachment;
  final Uint8List bytes;

  @override
  State<_VideoScreen> createState() => _VideoScreenState();
}

class _VideoScreenState extends State<_VideoScreen> {
  VideoPlayerController? _c;
  File? _file;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    final dir = await getTemporaryDirectory();
    final f = File(
      '${dir.path}/kus_video_${DateTime.now().microsecondsSinceEpoch}.mp4',
    );
    await f.writeAsBytes(widget.bytes, flush: true);
    _file = f;
    final c = VideoPlayerController.file(f);
    await c.initialize();
    if (!mounted) {
      await c.dispose();
      return;
    }
    setState(() => _c = c);
    await c.play();
  }

  @override
  void dispose() {
    _c?.dispose();
    // Çözülmüş video diskte kalmasın.
    final f = _file;
    if (f != null) unawaited(f.delete().then((_) {}, onError: (_) {}));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = _c;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(backgroundColor: Colors.black),
      body: Center(
        child: c == null
            ? const CircularProgressIndicator()
            : GestureDetector(
                onTap: () =>
                    setState(() => c.value.isPlaying ? c.pause() : c.play()),
                child: AspectRatio(
                  aspectRatio: c.value.aspectRatio,
                  child: VideoPlayer(c),
                ),
              ),
      ),
    );
  }
}

/// Sesli mesaj oynatıcı. Oynatıcı ilk dokunuşta oluşturulur.
class VoiceNotePlayer extends ConsumerStatefulWidget {
  const VoiceNotePlayer({super.key, required this.attachment});

  final Attachment attachment;

  @override
  ConsumerState<VoiceNotePlayer> createState() => _VoiceNotePlayerState();
}

class _VoiceNotePlayerState extends ConsumerState<VoiceNotePlayer> {
  AudioPlayer? _player;
  StreamSubscription<PlayerState>? _sub;
  StreamSubscription<Duration>? _pos;
  bool _playing = false;
  bool _loading = false;
  Duration _position = Duration.zero;
  String? _error;
  File? _file;

  @override
  void dispose() {
    _sub?.cancel();
    _pos?.cancel();
    _player?.dispose();
    // Çözülmüş ses diskte kalmasın.
    final f = _file;
    if (f != null) unawaited(f.delete().then((_) {}, onError: (_) {}));
    super.dispose();
  }

  Future<void> _toggle() async {
    final p = _player;
    if (p != null) {
      _playing ? await p.pause() : await p.play();
      return;
    }
    setState(() => _loading = true);
    try {
      final bytes = await ref
          .read(attachmentServiceProvider)
          .download(widget.attachment);
      final dir = await getTemporaryDirectory();
      final f = File(
        '${dir.path}/kus_voice_${DateTime.now().microsecondsSinceEpoch}.m4a',
      );
      await f.writeAsBytes(bytes, flush: true);
      _file = f;
      final player = AudioPlayer();
      await player.setFilePath(f.path);
      _sub = player.playerStateStream.listen((s) {
        if (!mounted) return;
        final done = s.processingState == ProcessingState.completed;
        if (done) {
          unawaited(player.pause());
          unawaited(player.seek(Duration.zero));
        }
        setState(() => _playing = s.playing && !done);
      });
      _pos = player.positionStream.listen((d) {
        if (mounted) setState(() => _position = d);
      });
      _player = player;
      await player.play();
    } catch (_) {
      if (mounted) setState(() => _error = _failText);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final total = widget.attachment.durationMs ?? 0;
    final progress = total == 0
        ? 0.0
        : (_position.inMilliseconds / total).clamp(0.0, 1.0);
    return SizedBox(
      width: 220,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              IconButton(
                key: const Key('voicePlay'),
                onPressed: _loading ? null : _toggle,
                icon: _loading
                    ? const SizedBox.square(
                        dimension: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        _playing ? Icons.pause_circle : Icons.play_circle,
                        size: 32,
                        color: AppColors.gold,
                      ),
              ),
              Expanded(child: LinearProgressIndicator(value: progress)),
              const SizedBox(width: 8),
              Text(
                Attachment.formatDuration(
                  _playing || _position > Duration.zero
                      ? _position.inMilliseconds
                      : total,
                ),
                key: const Key('voiceDuration'),
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ],
          ),
          if (_error != null)
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
        ],
      ),
    );
  }
}
