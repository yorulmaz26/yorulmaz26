import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:kus_dili_engine/kus_dili_engine.dart';

enum TranslateDirection { toKusDili, toTurkish }

final class TranslatorState {
  const TranslatorState({
    this.input = '',
    this.direction = TranslateDirection.toKusDili,
    this.variant = KusDiliVariant.g,
  });

  final String input;
  final TranslateDirection direction;
  final KusDiliVariant variant;

  TranslatorState copyWith({
    String? input,
    TranslateDirection? direction,
    KusDiliVariant? variant,
  }) => TranslatorState(
    input: input ?? this.input,
    direction: direction ?? this.direction,
    variant: variant ?? this.variant,
  );
}

/// Çevirici ekranının durumu. Dönüşüm tamamen cihaz üzerinde yapılır.
class TranslatorController extends Notifier<TranslatorState> {
  @override
  TranslatorState build() => const TranslatorState();

  void setInput(String value) => state = state.copyWith(input: value);

  void setVariant(KusDiliVariant v) => state = state.copyWith(variant: v);

  /// Yönü değiştirir ve mevcut çıktıyı yeni girdi yapar.
  void swap(String currentOutput) => state = state.copyWith(
    input: currentOutput,
    direction: state.direction == TranslateDirection.toKusDili
        ? TranslateDirection.toTurkish
        : TranslateDirection.toKusDili,
  );
}

final translatorProvider =
    NotifierProvider<TranslatorController, TranslatorState>(
      TranslatorController.new,
    );

final class TranslationOutput {
  const TranslationOutput(this.text, {this.decode});

  final String text;

  /// Yalnızca Kuş Dili → Türkçe yönünde dolu.
  final DecodeResult? decode;
}

/// Girdi her değiştiğinde anlık hesaplanan çıktı.
final translationOutputProvider = Provider<TranslationOutput>((ref) {
  final s = ref.watch(translatorProvider);
  final codec = KusDiliCodec(s.variant);
  return switch (s.direction) {
    TranslateDirection.toKusDili => TranslationOutput(codec.encode(s.input)),
    TranslateDirection.toTurkish => () {
      final r = codec.decode(s.input);
      return TranslationOutput(r.text, decode: r);
    }(),
  };
});
