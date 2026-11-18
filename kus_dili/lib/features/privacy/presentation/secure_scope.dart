import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../application/privacy_providers.dart';

/// [enabled] iken içindeki ekran açıkken ekran görüntüsü engellenir.
class SecureScope extends ConsumerStatefulWidget {
  const SecureScope({super.key, required this.enabled, required this.child});

  final bool enabled;
  final Widget child;

  @override
  ConsumerState<SecureScope> createState() => _SecureScopeState();
}

class _SecureScopeState extends ConsumerState<SecureScope> {
  bool _active = false;
  late final SecureContentCounter _counter;

  @override
  void initState() {
    super.initState();
    _counter = ref.read(secureContentProvider.notifier);
    _sync();
  }

  @override
  void didUpdateWidget(SecureScope old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    if (widget.enabled == _active) return;
    _active = widget.enabled;
    // Derleme sırasında sağlayıcı değiştirilemez; bir sonraki mikro görevde.
    Future.microtask(_active ? _counter.enter : _counter.leave);
  }

  @override
  void dispose() {
    if (_active) Future.microtask(_counter.leave);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
