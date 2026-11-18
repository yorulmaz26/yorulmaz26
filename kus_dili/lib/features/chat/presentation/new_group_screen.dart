import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../profile/domain/user_profile.dart';
import '../application/chat_providers.dart';
import '../domain/chat.dart';
import 'member_picker.dart';

class NewGroupScreen extends ConsumerStatefulWidget {
  const NewGroupScreen({super.key});

  @override
  ConsumerState<NewGroupScreen> createState() => _NewGroupScreenState();
}

class _NewGroupScreenState extends ConsumerState<NewGroupScreen> {
  final _name = TextEditingController();
  final List<UserProfile> _members = [];
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _create() async {
    final me = ref.read(myUidProvider);
    final name = _name.text.trim();
    if (me == null) return;
    if (name.isEmpty || name.length > 50) {
      setState(() => _error = 'Grup adı 1-50 karakter olmalı.');
      return;
    }
    if (_members.isEmpty) {
      setState(() => _error = 'En az bir kişi ekle.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final id = await ref
          .read(chatRepositoryProvider)
          .createGroup(
            creator: me,
            name: name,
            members: [for (final m in _members) m.uid],
          );
      if (mounted) context.pushReplacement(Routes.chat(id));
    } catch (_) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = 'Grup oluşturulamadı.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final me = ref.watch(myUidProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Yeni grup')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            key: const Key('groupName'),
            controller: _name,
            maxLength: 50,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              labelText: 'Grup adı',
              prefixIcon: Icon(Icons.groups_outlined),
            ),
          ),
          const SizedBox(height: 8),
          MemberSearchField(
            excluded: {?me, for (final m in _members) m.uid},
            onFound: (p) => setState(() {
              if (_members.length < Chat.maxGroupMembers - 1) _members.add(p);
            }),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final m in _members)
                InputChip(
                  label: Text(m.displayName),
                  avatar: const Icon(Icons.person, size: 16),
                  onDeleted: () => setState(() => _members.remove(m)),
                ),
            ],
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('createGroup'),
            icon: const Icon(Icons.check),
            label: Text('Grubu oluştur (${_members.length + 1} üye)'),
            onPressed: _busy ? null : _create,
          ),
          const SizedBox(height: 12),
          const Text(
            'Grup mesajları uçtan uca şifrelidir. Yeni katılan üyeler '
            'katılmadan önceki mesajları okuyamaz; çıkarılan üyeler sonraki '
            'mesajları okuyamaz.',
            style: TextStyle(color: AppColors.turquoise),
          ),
        ],
      ),
    );
  }
}
