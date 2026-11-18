import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/theme/app_colors.dart';
import '../../chat/domain/chat.dart';
import '../application/safety_actions.dart';
import '../domain/report.dart';

/// Şikâyet formu. Başarıyla gönderilirse `true` döner.
Future<bool> showReportSheet(
  BuildContext context, {
  required String chatId,
  String? reported,
  required List<ChatMessage> messages,
  String? title,
}) async {
  final ok = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    builder: (_) => ReportSheet(
      chatId: chatId,
      reported: reported,
      messages: messages,
      title: title,
    ),
  );
  if (ok == true && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Şikâyetin alındı. Teşekkürler.')),
    );
  }
  return ok == true;
}

class ReportSheet extends ConsumerStatefulWidget {
  const ReportSheet({
    super.key,
    required this.chatId,
    required this.reported,
    required this.messages,
    this.title,
  });

  final String chatId;
  final String? reported;
  final List<ChatMessage> messages;
  final String? title;

  @override
  ConsumerState<ReportSheet> createState() => _ReportSheetState();
}

class _ReportSheetState extends ConsumerState<ReportSheet> {
  final _note = TextEditingController();
  ReportReason? _reason;
  bool _include = true;
  bool _block = true;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final reason = _reason;
    if (reason == null) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(safetyActionsProvider)
          .report(
            chatId: widget.chatId,
            reason: reason,
            reported: widget.reported,
            note: _note.text,
            messages: _include ? widget.messages : const [],
            alsoBlock: _block && widget.reported != null,
          );
      if (mounted) Navigator.pop(context, true);
    } on ReportFormatException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Şikâyet gönderilemedi. Bağlantını kontrol et.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final n = widget.messages.length;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Column(
            key: const Key('reportSheet'),
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title ?? 'Şikâyet et', style: text.titleLarge),
              const SizedBox(height: 8),
              for (final r in ReportReason.values)
                ListTile(
                  key: Key('reason-${r.code}'),
                  contentPadding: EdgeInsets.zero,
                  title: Text(r.label),
                  trailing: Icon(
                    _reason == r
                        ? Icons.radio_button_checked
                        : Icons.radio_button_unchecked,
                    color: _reason == r ? AppColors.violet : null,
                  ),
                  onTap: () => setState(() => _reason = r),
                ),
              TextField(
                key: const Key('reportNote'),
                controller: _note,
                maxLength: Report.maxNoteLength,
                maxLines: 3,
                minLines: 1,
                decoration: const InputDecoration(
                  labelText: 'Açıklama (isteğe bağlı)',
                ),
              ),
              if (n > 0)
                SwitchListTile(
                  key: const Key('includeMessages'),
                  contentPadding: EdgeInsets.zero,
                  value: _include,
                  onChanged: (v) => setState(() => _include = v),
                  title: Text('Son $n mesajı ekle'),
                  subtitle: const Text(
                    'Mesajlar uçtan uca şifreli olduğu için ekibimiz onları '
                    'göremez. Eklersen bu mesajlar telefonunda çözülüp yalnızca '
                    'moderasyon ekibine gönderilir. Diğer mesajların gizli kalır.',
                  ),
                ),
              if (widget.reported != null)
                SwitchListTile(
                  key: const Key('alsoBlock'),
                  contentPadding: EdgeInsets.zero,
                  value: _block,
                  onChanged: (v) => setState(() => _block = v),
                  title: const Text('Ayrıca engelle'),
                ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Text(
                    _error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: _busy ? null : () => Navigator.pop(context),
                    child: const Text('Vazgeç'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    key: const Key('submitReport'),
                    onPressed: _busy || _reason == null ? null : _submit,
                    child: const Text('Gönder'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
