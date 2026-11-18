import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/application/chat_providers.dart';
import '../application/call_controller.dart';

/// Sürmekte olan arama.
class CallScreen extends ConsumerStatefulWidget {
  const CallScreen({super.key});

  @override
  ConsumerState<CallScreen> createState() => _CallScreenState();
}

class _CallScreenState extends ConsumerState<CallScreen> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  String _status(CallSession s) {
    switch (s.phase) {
      case CallPhase.outgoing:
        return 'Aranıyor…';
      case CallPhase.connecting:
        return 'Bağlanıyor…';
      case CallPhase.connected:
        final d = DateTime.now().difference(s.connectedAt ?? DateTime.now());
        return '${d.inMinutes}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
      case CallPhase.ended:
        return s.endReason ?? 'Arama bitti';
      case CallPhase.idle:
        return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = ref.watch(callControllerProvider);
    final c = ref.read(callControllerProvider.notifier);
    ref.listen(callControllerProvider, (prev, next) {
      if (next.phase == CallPhase.ended && prev?.phase != CallPhase.ended) {
        final router = GoRouter.of(context);
        Future<void>.delayed(const Duration(milliseconds: 1500), () {
          if (!mounted) return;
          c.dismiss();
          if (router.canPop()) router.pop();
        });
      }
    });
    final peer = s.peerUid == null
        ? null
        : ref.watch(profileByUidProvider(s.peerUid!)).value;
    final engine = s.engine;
    final showVideo = s.video && engine != null && s.active;
    final text = Theme.of(context).textTheme;
    return PopScope(
      canPop: !s.active,
      child: Scaffold(
        backgroundColor: AppColors.night,
        body: Stack(
          children: [
            if (showVideo) Positioned.fill(child: engine.remoteView()),
            SafeArea(
              child: Column(
                children: [
                  const SizedBox(height: 32),
                  if (!showVideo || s.phase != CallPhase.connected) ...[
                    CircleAvatar(
                      radius: 44,
                      backgroundColor: AppColors.violet,
                      child: Text(
                        (peer?.displayName.characters.firstOrNull ?? '?')
                            .toUpperCase(),
                        style: text.headlineMedium,
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                  Text(peer?.displayName ?? '', style: text.headlineSmall),
                  const SizedBox(height: 6),
                  Text(
                    _status(s),
                    key: const Key('callStatus'),
                    style: text.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  const Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.lock, size: 13, color: AppColors.turquoise),
                      SizedBox(width: 4),
                      Text(
                        'Uçtan uca şifreli',
                        style: TextStyle(color: AppColors.turquoise),
                      ),
                    ],
                  ),
                  const Spacer(),
                  if (showVideo && s.cameraOn)
                    Align(
                      alignment: Alignment.centerRight,
                      child: Container(
                        margin: const EdgeInsets.all(16),
                        width: 110,
                        height: 160,
                        clipBehavior: Clip.antiAlias,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppColors.gold),
                        ),
                        child: engine.localView(),
                      ),
                    ),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 32),
                    child: Wrap(
                      spacing: 16,
                      alignment: WrapAlignment.center,
                      children: [
                        _RoundButton(
                          key: const Key('callMute'),
                          icon: s.muted ? Icons.mic_off : Icons.mic,
                          active: s.muted,
                          onTap: s.active ? c.toggleMute : null,
                        ),
                        if (s.video) ...[
                          _RoundButton(
                            key: const Key('callCamera'),
                            icon: s.cameraOn
                                ? Icons.videocam
                                : Icons.videocam_off,
                            active: !s.cameraOn,
                            onTap: s.active ? c.toggleCamera : null,
                          ),
                          _RoundButton(
                            key: const Key('callSwitchCamera'),
                            icon: Icons.cameraswitch,
                            onTap: s.active ? c.switchCamera : null,
                          ),
                        ],
                        _RoundButton(
                          key: const Key('callSpeaker'),
                          icon: s.speaker ? Icons.volume_up : Icons.hearing,
                          active: s.speaker,
                          onTap: s.active ? c.toggleSpeaker : null,
                        ),
                        _RoundButton(
                          key: const Key('callHangUp'),
                          icon: Icons.call_end,
                          color: Colors.redAccent,
                          onTap: s.active ? c.hangUp : null,
                        ),
                      ],
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

class _RoundButton extends StatelessWidget {
  const _RoundButton({
    super.key,
    required this.icon,
    required this.onTap,
    this.active = false,
    this.color,
  });

  final IconData icon;
  final VoidCallback? onTap;
  final bool active;
  final Color? color;

  @override
  Widget build(BuildContext context) => Material(
    color: color ?? (active ? AppColors.snow : AppColors.nightRaised),
    shape: const CircleBorder(),
    child: InkWell(
      customBorder: const CircleBorder(),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Icon(
          icon,
          color: active && color == null ? AppColors.night : AppColors.snow,
        ),
      ),
    ),
  );
}
