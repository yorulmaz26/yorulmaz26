import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../application/media_providers.dart';
import '../domain/attachment.dart';

/// Dokun: kayıt başlar. Tekrar dokun: gönder. Çarpı: iptal.
class VoiceRecordButton extends ConsumerStatefulWidget {
  const VoiceRecordButton({
    super.key,
    required this.onRecorded,
    this.enabled = true,
  });

  final Future<void> Function(PickedMedia media) onRecorded;
  final bool enabled;

  @override
  ConsumerState<VoiceRecordButton> createState() => _VoiceRecordButtonState();
}

class _VoiceRecordButtonState extends ConsumerState<VoiceRecordButton> {
  bool _recording = false;
  Timer? _tick;
  final _watch = Stopwatch();

  @override
  void dispose() {
    _tick?.cancel();
    if (_recording) unawaited(ref.read(voiceRecorderProvider).cancel());
    super.dispose();
  }

  Future<void> _start() async {
    final rec = ref.read(voiceRecorderProvider);
    if (!await rec.hasPermission()) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Sesli mesaj için mikrofon izni gerekiyor.'),
          ),
        );
      }
      return;
    }
    await rec.start();
    _watch
      ..reset()
      ..start();
    _tick = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (mounted) setState(() {});
    });
    if (mounted) setState(() => _recording = true);
  }

  Future<void> _finish({required bool send}) async {
    _tick?.cancel();
    _watch.stop();
    final rec = ref.read(voiceRecorderProvider);
    setState(() => _recording = false);
    if (!send) {
      await rec.cancel();
      return;
    }
    final media = await rec.stop();
    if (media == null) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Kayıt çok kısa.')));
      }
      return;
    }
    await widget.onRecorded(media);
  }

  @override
  Widget build(BuildContext context) {
    if (!_recording) {
      return IconButton.filled(
        key: const Key('recordVoice'),
        tooltip: 'Sesli mesaj',
        onPressed: widget.enabled ? _start : null,
        icon: const Icon(Icons.mic),
      );
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          key: const Key('cancelVoice'),
          tooltip: 'İptal',
          onPressed: () => _finish(send: false),
          icon: const Icon(Icons.close),
        ),
        const Icon(
          Icons.fiber_manual_record,
          color: Colors.redAccent,
          size: 14,
        ),
        const SizedBox(width: 4),
        Text(
          Attachment.formatDuration(_watch.elapsedMilliseconds),
          key: const Key('voiceTimer'),
          style: const TextStyle(color: AppColors.snow),
        ),
        const SizedBox(width: 4),
        IconButton.filled(
          key: const Key('stopVoice'),
          tooltip: 'Gönder',
          onPressed: () => _finish(send: true),
          icon: const Icon(Icons.send),
        ),
      ],
    );
  }
}
