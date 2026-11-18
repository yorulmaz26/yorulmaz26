import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/progress.dart';

/// İlerleme cihazda saklanır (gizli veri değil; hesap gerektirmez).
final class PrefsProgressStore implements ProgressStore {
  static const _key = 'learn.progress.v1';

  @override
  Future<LearnProgress> load() async {
    final raw = (await SharedPreferences.getInstance()).getString(_key);
    if (raw == null) return const LearnProgress();
    try {
      return LearnProgress.fromJson(jsonDecode(raw) as Map<String, Object?>);
    } catch (_) {
      return const LearnProgress();
    }
  }

  @override
  Future<void> save(LearnProgress p) async =>
      (await SharedPreferences.getInstance()).setString(_key, p.encode());
}
