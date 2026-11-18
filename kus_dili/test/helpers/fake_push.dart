import 'dart:async';

import 'package:kus_dili/features/notifications/domain/push.dart';

/// YALNIZCA TESTLER: FCM sahtesi.
class FakePushMessaging implements PushMessaging {
  FakePushMessaging({this.permission = true, this.currentToken = 'tok-1'});

  bool permission;
  String? currentToken;
  String? initial;
  bool deleted = false;
  final opened = StreamController<String>.broadcast();
  final refresh = StreamController<String>.broadcast();

  @override
  Future<bool> requestPermission() async => permission;

  @override
  Future<String?> token() async => currentToken;

  @override
  Stream<String> get tokenRefresh => refresh.stream;

  @override
  Future<String?> initialChatId() async => initial;

  @override
  Stream<String> get openedChatIds => opened.stream;

  @override
  Future<void> deleteToken() async {
    deleted = true;
    currentToken = null;
  }
}
