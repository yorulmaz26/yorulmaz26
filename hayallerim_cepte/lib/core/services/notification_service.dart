import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import '../database/app_database.dart';
import 'reminder_logic.dart';

/// Tamamen yerel bildirim sistemi (sunucu / push yok).
///
/// Platform notları:
/// * Android 13+: POST_NOTIFICATIONS çalışma zamanı izni istenir.
/// * Kesin alarm izni (SCHEDULE_EXACT_ALARM) istenmez; bildirimler
///   `inexactAllowWhileIdle` ile planlanır. Doze modunda birkaç dakika
///   gecikebilir — Play politikalarına en uygun seçenek budur.
/// * iOS: en fazla 64 bekleyen bildirim; en yakın 60 tanesi planlanır.
class NotificationService {
  NotificationService();

  late final _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;

  static const focusNotificationId = 2000000001;
  static const _channelReminders = 'reminders';
  static const _channelFocus = 'focus';

  bool get isSupported => !kIsWeb && (Platform.isAndroid || Platform.isIOS);

  Future<void> init() async {
    if (!isSupported || _ready) return;
    tzdata.initializeTimeZones();
    try {
      final info = await FlutterTimezone.getLocalTimezone();
      tz.setLocalLocation(tz.getLocation(info.identifier));
    } catch (_) {
      tz.setLocalLocation(tz.getLocation('Europe/Istanbul'));
    }
    const settings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(
        requestAlertPermission: false,
        requestBadgePermission: false,
        requestSoundPermission: false,
      ),
    );
    await _plugin.initialize(settings: settings);
    _ready = true;
  }

  /// Kullanıcı bildirimleri açtığında çağrılır. İzin verildiyse true.
  Future<bool> requestPermission() async {
    if (!isSupported) return false;
    await init();
    if (Platform.isAndroid) {
      final a = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      return await a?.requestNotificationsPermission() ?? false;
    }
    final i = _plugin.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>();
    return await i?.requestPermissions(alert: true, badge: true, sound: true) ?? false;
  }

  NotificationDetails _details(String channel) => NotificationDetails(
        android: AndroidNotificationDetails(
          channel,
          channel == _channelFocus ? 'Odaklanma' : 'Hatırlatıcılar',
          channelDescription: channel == _channelFocus
              ? 'Pomodoro oturumu bittiğinde bildirir'
              : 'Hedef, görev, alışkanlık ve motivasyon hatırlatıcıları',
          importance: Importance.high,
          priority: Priority.high,
        ),
        iOS: const DarwinNotificationDetails(),
      );

  DateTimeComponents? _components(String repeat) => switch (repeat) {
        'daily' => DateTimeComponents.time,
        'weekly' => DateTimeComponents.dayOfWeekAndTime,
        'monthly' => DateTimeComponents.dayOfMonthAndTime,
        'yearly' => DateTimeComponents.dateAndTime,
        _ => null,
      };

  /// Veritabanındaki tüm hatırlatıcıları yeniden planlar (açılışta ve her
  /// hatırlatıcı değişiminde). Odak bildirimi korunur.
  Future<void> resync(List<Reminder> reminders) async {
    if (!isSupported) return;
    await init();
    final pending = await _plugin.pendingNotificationRequests();
    for (final p in pending) {
      if (p.id != focusNotificationId) await _plugin.cancel(id: p.id);
    }
    final now = DateTime.now();
    final planned = ReminderLogic.plan(reminders, now);
    for (final (r, next) in planned) {
      if (r.repeat == 'weekly' && r.weekdaysMask != null) {
        // Her seçili gün için ayrı haftalık bildirim (id = r.id*10 + gün)
        for (var wd = 1; wd <= 7; wd++) {
          if ((r.weekdaysMask! >> (wd - 1)) & 1 != 1) continue;
          var d = DateTime(now.year, now.month, now.day, r.at.hour, r.at.minute);
          while (d.weekday != wd || !d.isAfter(now)) {
            d = d.add(const Duration(days: 1));
          }
          await _schedule(r.id * 10 + wd, r.title, r.body, d, DateTimeComponents.dayOfWeekAndTime);
        }
      } else {
        await _schedule(r.id * 10, r.title, r.body, next, _components(r.repeat));
      }
    }
  }

  Future<void> _schedule(int id, String title, String body, DateTime when, DateTimeComponents? comp) async {
    try {
      await _plugin.zonedSchedule(
        id: id,
        title: title,
        body: body,
        scheduledDate: tz.TZDateTime.from(when, tz.local),
        notificationDetails: _details(_channelReminders),
        androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
        matchDateTimeComponents: comp,
      );
    } catch (e) {
      debugPrint('Bildirim planlanamadı: $e');
    }
  }

  Future<void> scheduleFocusEnd(DateTime when, String title, String body) async {
    if (!isSupported) return;
    await init();
    await _plugin.cancel(id: focusNotificationId);
    try {
      await _plugin.zonedSchedule(
        id: focusNotificationId,
        title: title,
        body: body,
        scheduledDate: tz.TZDateTime.from(when, tz.local),
        notificationDetails: _details(_channelFocus),
        androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
      );
    } catch (e) {
      debugPrint('Odak bildirimi planlanamadı: $e');
    }
  }

  Future<void> cancelFocus() async {
    if (!isSupported) return;
    await init();
    await _plugin.cancel(id: focusNotificationId);
  }

  Future<void> showNow(String title, String body) async {
    if (!isSupported) return;
    await init();
    await _plugin.show(
      id: DateTime.now().millisecondsSinceEpoch ~/ 1000 % 1000000,
      title: title,
      body: body,
      notificationDetails: _details(_channelReminders),
    );
  }
}
