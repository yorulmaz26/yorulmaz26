import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../app/router.dart';
import '../../../app/theme/app_colors.dart';
import '../../profile/application/profile_providers.dart';
import '../../profile/domain/user_profile.dart';
import '../../profile/domain/username.dart';
import '../application/chat_providers.dart';

/// Kullanıcı adıyla arkadaş bulma ve sohbet başlatma.
class NewChatScreen extends ConsumerStatefulWidget {
  const NewChatScreen({super.key});

  @override
  ConsumerState<NewChatScreen> createState() => _NewChatScreenState();
}

class _NewChatScreenState extends ConsumerState<NewChatScreen> {
  final _query = TextEditingController();
  UserProfile? _found;
  String? _message;
  bool _busy = false;

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final name = Username.normalize(_query.text);
    final invalid = Username.validate(name);
    if (invalid != null && name.length < Username.minLength) {
      setState(() {
        _found = null;
        _message = invalid;
      });
      return;
    }
    setState(() {
      _busy = true;
      _message = null;
      _found = null;
    });
    try {
      final p = await ref.read(profileRepositoryProvider).findByUsername(name);
      if (!mounted) return;
      final me = ref.read(myUidProvider);
      setState(() {
        _found = p != null && p.uid != me ? p : null;
        _message = p == null
            ? '@$name bulunamadı.'
            : (p.uid == me ? 'Bu senin kullanıcı adın.' : null);
      });
    } catch (_) {
      if (mounted) setState(() => _message = 'Arama yapılamadı.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _start(UserProfile other) async {
    final me = ref.read(myUidProvider);
    if (me == null) return;
    setState(() => _busy = true);
    try {
      final id = await ref
          .read(chatRepositoryProvider)
          .ensureDirectChat(me: me, other: other.uid);
      if (mounted) context.pushReplacement(Routes.chat(id));
    } catch (_) {
      if (mounted) {
        setState(() {
          _busy = false;
          _message = 'Sohbet başlatılamadı.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final found = _found;
    return Scaffold(
      appBar: AppBar(title: const Text('Yeni sohbet')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            key: const Key('searchUsername'),
            controller: _query,
            autocorrect: false,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              labelText: 'Kullanıcı adı',
              prefixText: '@',
              suffixIcon: IconButton(
                key: const Key('doSearch'),
                icon: const Icon(Icons.search),
                onPressed: _busy ? null : _search,
              ),
            ),
            onSubmitted: (_) => _search(),
          ),
          const SizedBox(height: 16),
          if (_busy) const LinearProgressIndicator(),
          if (_message != null) Text(_message!),
          if (found != null)
            Card(
              child: ListTile(
                key: const Key('searchResult'),
                leading: CircleAvatar(
                  backgroundColor: AppColors.violet,
                  child: Text(found.displayName.characters.first.toUpperCase()),
                ),
                title: Text(found.displayName),
                subtitle: Text('@${found.username}'),
                trailing: const Icon(Icons.chat_bubble_outline),
                onTap: _busy ? null : () => _start(found),
              ),
            ),
        ],
      ),
    );
  }
}
