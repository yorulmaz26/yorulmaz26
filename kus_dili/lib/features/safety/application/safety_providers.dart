import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../chat/application/chat_providers.dart';
import '../data/firestore_safety_repository.dart';
import '../domain/safety_repository.dart';

final safetyRepositoryProvider = Provider<SafetyRepository>(
  (ref) => FirestoreSafetyRepository(ref.watch(firestoreProvider)),
);

/// Engellediğim kişiler.
final blockedProvider = StreamProvider<Set<String>>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(const {});
  return ref.watch(safetyRepositoryProvider).watchBlocked(uid);
});

final isBlockedProvider = Provider.family<bool, String>(
  (ref, other) => ref.watch(blockedProvider).value?.contains(other) ?? false,
);
