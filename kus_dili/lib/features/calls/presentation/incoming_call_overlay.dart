import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/application/chat_providers.dart';
import '../domain/call.dart';

/// Uygulama açıkken gelen arama ekranı.
class IncomingCallOverlay extends ConsumerWidget {
  const IncomingCallOverlay({
    super.key,
    required this.call,
    required this.onAccept,
    required this.onDecline,
  });

  final CallInfo call;
  final VoidCallback onAccept;
  final VoidCallback onDecline;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final caller = ref.watch(profileByUidProvider(call.caller)).value;
    final text = Theme.of(context).textTheme;
    return Material(
      color: AppColors.night,
      child: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 48),
            CircleAvatar(
              radius: 48,
              backgroundColor: AppColors.violet,
              child: Text(
                (caller?.displayName.characters.firstOrNull ?? '?')
                    .toUpperCase(),
                style: text.headlineMedium,
              ),
            ),
            const SizedBox(height: 16),
            Text(caller?.displayName ?? '', style: text.headlineSmall),
            const SizedBox(height: 8),
            Text(
              call.video ? 'Görüntülü arama' : 'Sesli arama',
              key: const Key('incomingKind'),
              style: text.titleMedium,
            ),
            const Spacer(),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                FloatingActionButton(
                  key: const Key('declineCall'),
                  heroTag: 'decline',
                  backgroundColor: Colors.redAccent,
                  onPressed: onDecline,
                  child: const Icon(Icons.call_end),
                ),
                FloatingActionButton(
                  key: const Key('acceptCall'),
                  heroTag: 'accept',
                  backgroundColor: AppColors.turquoise,
                  onPressed: onAccept,
                  child: Icon(call.video ? Icons.videocam : Icons.call),
                ),
              ],
            ),
            const SizedBox(height: 56),
          ],
        ),
      ),
    );
  }
}
