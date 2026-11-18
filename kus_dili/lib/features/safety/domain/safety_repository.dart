import 'report.dart';

/// Engelleme ve şikâyet.
///
/// * Engel listesi `blocks/{uid}` belgesindedir; yalnızca sahibi okuyabilir.
///   Engellenen kişi listeyi göremez, yalnızca mesajı gitmez.
/// * Şikâyetler `reports/` koleksiyonuna yazılır; uygulamadan kimse
///   okuyamaz (yalnızca Firebase konsolundan moderasyon ekibi).
abstract interface class SafetyRepository {
  Stream<Set<String>> watchBlocked(String uid);

  Future<void> block({required String uid, required String other});

  Future<void> unblock({required String uid, required String other});

  Future<void> report(Report report);
}

/// Engel listesi en fazla bu kadar kişi (Firestore kuralıyla aynı).
const int maxBlocked = 1000;

final class TooManyBlockedException implements Exception {
  const TooManyBlockedException();
  String get message => 'En fazla $maxBlocked kişiyi engelleyebilirsin.';
}
