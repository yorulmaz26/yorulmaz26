/// Parmak izi / yüz tanıma. Platform kodu `data/` altında; testlerde sahtesi.
abstract interface class BiometricAuth {
  Future<bool> isAvailable();
  Future<bool> authenticate(String reason);
}
