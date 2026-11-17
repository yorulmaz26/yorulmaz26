import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import 'app/app.dart';

void main() {
  // Aşama 3'te burada Firebase.initializeApp(...) çağrılacak.
  runApp(const ProviderScope(child: KusDiliApp()));
}
