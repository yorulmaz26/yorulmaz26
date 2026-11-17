/// Geçersiz özel kuş dili kuralı tanımlandığında fırlatılır.
final class KusDiliRuleException implements Exception {
  const KusDiliRuleException(this.message);

  final String message;

  @override
  String toString() => 'KusDiliRuleException: $message';
}
