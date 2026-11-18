import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import 'app/app.dart';
import 'core/firebase/firebase_bootstrap.dart';
import 'core/firebase/firebase_providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final firebase = await initFirebase();
  runApp(
    ProviderScope(
      overrides: [firebaseStatusProvider.overrideWithValue(firebase)],
      child: const KusDiliApp(),
    ),
  );
}
