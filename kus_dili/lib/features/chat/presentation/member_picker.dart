import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../profile/application/profile_providers.dart';
import '../../profile/domain/user_profile.dart';
import '../../profile/domain/username.dart';

/// Kullanıcı adıyla kişi arayıp seçme alanı (grup oluşturma / üye ekleme).
class MemberSearchField extends ConsumerStatefulWidget {
  const MemberSearchField({
    super.key,
    required this.onFound,
    required this.excluded,
  });

  final void Function(UserProfile) onFound;

  /// Zaten seçili / üye olanlar.
  final Set<String> excluded;

  @override
  ConsumerState<MemberSearchField> createState() => _MemberSearchFieldState();
}

class _MemberSearchFieldState extends ConsumerState<MemberSearchField> {
  final _q = TextEditingController();
  String? _msg;
  bool _busy = false;

  @override
  void dispose() {
    _q.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    final name = Username.normalize(_q.text);
    if (name.length < Username.minLength) {
      setState(() => _msg = Username.validate(name));
      return;
    }
    setState(() {
      _busy = true;
      _msg = null;
    });
    try {
      final p = await ref.read(profileRepositoryProvider).findByUsername(name);
      if (!mounted) return;
      if (p == null) {
        setState(() => _msg = '@$name bulunamadı.');
      } else if (widget.excluded.contains(p.uid)) {
        setState(() => _msg = '@$name zaten listede.');
      } else {
        _q.clear();
        widget.onFound(p);
      }
    } catch (_) {
      if (mounted) setState(() => _msg = 'Arama yapılamadı.');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          key: const Key('memberSearch'),
          controller: _q,
          autocorrect: false,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            labelText: 'Kullanıcı adıyla ekle',
            prefixText: '@',
            suffixIcon: IconButton(
              key: const Key('memberSearchGo'),
              icon: const Icon(Icons.person_add_alt),
              onPressed: _busy ? null : _search,
            ),
          ),
          onSubmitted: (_) => _search(),
        ),
        if (_busy) const LinearProgressIndicator(),
        if (_msg != null)
          Padding(padding: const EdgeInsets.only(top: 6), child: Text(_msg!)),
      ],
    );
  }
}
