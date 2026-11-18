import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../application/safety_actions.dart';
import '../domain/safety_repository.dart';

/// Onay alıp engeller. Engellendiyse `true`.
Future<bool> confirmBlock(
  BuildContext context,
  WidgetRef ref, {
  required String uid,
  required String name,
}) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text('$name engellensin mi?'),
      content: const Text(
        'Engellediğin kişi sana birebir mesaj gönderemez ve yeni sohbet '
        'başlatamaz. Grup mesajları sana gizlenir. Engellendiğine dair '
        'bildirim almaz.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: const Text('Vazgeç'),
        ),
        FilledButton(
          key: const Key('confirmBlock'),
          onPressed: () => Navigator.pop(ctx, true),
          child: const Text('Engelle'),
        ),
      ],
    ),
  );
  if (ok != true) return false;
  try {
    await ref.read(safetyActionsProvider).block(uid);
    return true;
  } on TooManyBlockedException catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(e.message)));
    }
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Engellenemedi.')));
    }
  }
  return false;
}

Future<void> unblockWithFeedback(
  BuildContext context,
  WidgetRef ref,
  String uid,
) async {
  try {
    await ref.read(safetyActionsProvider).unblock(uid);
  } catch (_) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Engel kaldırılamadı.')));
    }
  }
}
