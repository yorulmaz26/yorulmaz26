import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/firebase/firebase_providers.dart';
import '../../auth/application/auth_providers.dart';
import '../../profile/application/profile_providers.dart';
import '../../profile/domain/user_profile.dart';
import '../data/firestore_chat_repository.dart';
import '../data/firestore_chat_settings_repository.dart';
import '../domain/chat.dart';
import '../domain/chat_settings.dart';
import '../domain/message_payload.dart';
import '../domain/message_sealer.dart';

final chatRepositoryProvider = Provider<ChatRepository>(
  (ref) => FirestoreChatRepository(ref.watch(firestoreProvider)),
);

final chatSettingsRepositoryProvider = Provider<ChatSettingsRepository>(
  (ref) => FirestoreChatSettingsRepository(ref.watch(firestoreProvider)),
);

/// Aşama 7'de uçtan uca şifreleyen uygulamayla değiştirilecek.
final messageSealerProvider = Provider<MessageSealer>(
  (ref) => const PlaintextDevSealer(),
);

final myUidProvider = Provider<String?>(
  (ref) => ref.watch(authStateProvider.select((a) => a.value?.uid)),
);

final chatsProvider = StreamProvider<List<Chat>>((ref) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(const []);
  return ref.watch(chatRepositoryProvider).watchChats(uid);
});

final chatProvider = StreamProvider.family<Chat?, String>(
  (ref, chatId) => ref.watch(chatRepositoryProvider).watchChat(chatId),
);

final messagesProvider = StreamProvider.family<List<ChatMessage>, String>(
  (ref, chatId) => ref.watch(chatRepositoryProvider).watchMessages(chatId),
);

final profileByUidProvider = StreamProvider.family<UserProfile?, String>(
  (ref, uid) => ref.watch(profileRepositoryProvider).watchProfile(uid),
);

final chatSettingsProvider = StreamProvider.family<ChatSettings, String>((
  ref,
  chatId,
) {
  final uid = ref.watch(myUidProvider);
  if (uid == null) return Stream.value(const ChatSettings());
  return ref
      .watch(chatSettingsRepositoryProvider)
      .watch(uid: uid, chatId: chatId);
});

/// Mühürlü mesajı açar. Anahtar gövdeyi de içerdiği için düzenlenen mesaj
/// yeniden açılır.
typedef SealedRef = ({String chatId, int encVersion, String body});

final openedPayloadProvider = FutureProvider.family<MessagePayload, SealedRef>(
  (ref, s) =>
      ref.watch(messageSealerProvider).open(s.chatId, s.encVersion, s.body),
);
