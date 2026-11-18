import 'dart:async';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:record/record.dart';

import '../providers.dart';
import '../theme/app_colors.dart';
import 'common.dart';

/// Ses kaydı sayfası. Kayıt bitince (göreliYol, süreMs) döner.
Future<(String, int)?> recordAudio(BuildContext context) =>
    showModalBottomSheet<(String, int)>(context: context, isDismissible: false, builder: (_) => const _Recorder());

class _Recorder extends ConsumerStatefulWidget {
  const _Recorder();
  @override
  ConsumerState<_Recorder> createState() => _RecorderState();
}

class _RecorderState extends ConsumerState<_Recorder> {
  final _rec = AudioRecorder();
  Timer? _timer;
  int _ms = 0;
  bool _recording = false;
  String? _rel;
  String? _error;

  static const _maxMs = 10 * 60 * 1000; // en fazla 10 dakika

  @override
  void dispose() {
    _timer?.cancel();
    _rec.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    if (!await _rec.hasPermission()) {
      setState(() => _error = 'Mikrofon izni verilmedi. Ayarlardan izin verebilirsin.');
      return;
    }
    final (rel, abs) = await ref.read(mediaProvider).newAudioPath();
    _rel = rel;
    await _rec.start(const RecordConfig(encoder: AudioEncoder.aacLc, bitRate: 96000), path: abs);
    setState(() {
      _recording = true;
      _ms = 0;
    });
    _timer = Timer.periodic(const Duration(milliseconds: 200), (_) {
      setState(() => _ms += 200);
      if (_ms >= _maxMs) _stop();
    });
  }

  Future<void> _stop() async {
    _timer?.cancel();
    await _rec.stop();
    if (!mounted) return;
    Navigator.pop(context, (_rel!, _ms));
  }

  Future<void> _cancel() async {
    _timer?.cancel();
    if (_recording) {
      await _rec.cancel();
      await ref.read(mediaProvider).delete(_rel);
    }
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final s = (_ms ~/ 1000);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(_recording ? 'Kaydediliyor…' : 'Ses kaydı', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Text('${(s ~/ 60).toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}',
              style: const TextStyle(fontSize: 40, fontWeight: FontWeight.w300, fontFeatures: [FontFeature.tabularFigures()])),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: const TextStyle(color: AppColors.danger))),
          const SizedBox(height: 16),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            TextButton(onPressed: _cancel, child: const Text('Vazgeç')),
            const SizedBox(width: 24),
            Semantics(
              button: true,
              label: _recording ? 'Kaydı bitir' : 'Kayda başla',
              child: GestureDetector(
                onTap: _recording ? _stop : _start,
                child: Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(color: _recording ? AppColors.danger : Theme.of(context).colorScheme.primary, shape: BoxShape.circle),
                  child: Icon(_recording ? Icons.stop_rounded : Icons.mic_rounded, color: Colors.white, size: 36),
                ),
              ),
            ),
          ]),
        ]),
      ),
    );
  }
}

/// Basit ses oynatıcı satırı
class AudioTile extends ConsumerStatefulWidget {
  const AudioTile({super.key, required this.path, this.durationMs, this.onDelete});
  final String path;
  final int? durationMs;
  final VoidCallback? onDelete;
  @override
  ConsumerState<AudioTile> createState() => _AudioTileState();
}

class _AudioTileState extends ConsumerState<AudioTile> {
  final _player = AudioPlayer();
  bool _playing = false;
  Duration _pos = Duration.zero;
  final _subs = <StreamSubscription<dynamic>>[];

  @override
  void initState() {
    super.initState();
    _subs.add(_player.onPositionChanged.listen((p) => setState(() => _pos = p)));
    _subs.add(_player.onPlayerComplete.listen((_) => setState(() {
          _playing = false;
          _pos = Duration.zero;
        })));
  }

  @override
  void dispose() {
    for (final s in _subs) {
      s.cancel();
    }
    _player.dispose();
    super.dispose();
  }

  Future<void> _toggle() async {
    if (_playing) {
      await _player.pause();
      setState(() => _playing = false);
      return;
    }
    final f = ref.read(mediaProvider).resolve(widget.path);
    if (f == null || !await f.exists()) {
      if (mounted) toast(context, 'Ses dosyası bulunamadı');
      return;
    }
    await _player.play(DeviceFileSource(f.path));
    setState(() => _playing = true);
  }

  @override
  Widget build(BuildContext context) {
    final total = widget.durationMs ?? 0;
    final v = total == 0 ? 0.0 : (_pos.inMilliseconds / total).clamp(0.0, 1.0);
    final secs = total ~/ 1000;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Row(children: [
          IconButton(tooltip: _playing ? 'Duraklat' : 'Oynat', icon: Icon(_playing ? Icons.pause_circle : Icons.play_circle, size: 32), onPressed: _toggle),
          Expanded(child: LinearProgressIndicator(value: v, minHeight: 4, borderRadius: BorderRadius.circular(4))),
          const SizedBox(width: 8),
          Text('${secs ~/ 60}:${(secs % 60).toString().padLeft(2, '0')}'),
          if (widget.onDelete != null) IconButton(tooltip: 'Sil', icon: const Icon(Icons.delete_outline), onPressed: widget.onDelete),
        ]),
      ),
    );
  }
}
