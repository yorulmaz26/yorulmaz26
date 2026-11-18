import 'package:kus_dili/features/learn/domain/progress.dart';

/// YALNIZCA TESTLER.
class FakeSpeaker implements Speaker {
  final List<String> spoken = [];

  @override
  Future<void> speak(String text) async => spoken.add(text);

  @override
  Future<void> stop() async {}
}

class InMemoryProgressStore implements ProgressStore {
  LearnProgress value = const LearnProgress();

  @override
  Future<LearnProgress> load() async => value;

  @override
  Future<void> save(LearnProgress p) async => value = p;
}
