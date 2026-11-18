import 'package:cloud_functions/cloud_functions.dart';

import '../domain/account_service.dart';

final class CloudAccountService implements AccountService {
  CloudAccountService([FirebaseFunctions? f])
    : _f = f ?? FirebaseFunctions.instanceFor(region: 'europe-west1');

  final FirebaseFunctions _f;

  @override
  Future<void> deleteAccount() async {
    await _f.httpsCallable('deleteAccount').call<Map<String, dynamic>>();
  }
}
