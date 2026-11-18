import 'package:flutter/material.dart';

/// Rozet kontrolü için anlık istatistikler.
class GameStats {
  const GameStats({
    this.dreams = 0,
    this.completedDreams = 0,
    this.completedMilestones = 0,
    this.completedTasks = 0,
    this.bestHabitStreak = 0,
    this.activityStreak = 0,
    this.depositCount = 0,
    this.completedSavings = 0,
    this.journalCount = 0,
    this.focusMinutes = 0,
    this.capsules = 0,
    this.balanceEntries = 0,
    this.badges = 0,
  });

  final int dreams;
  final int completedDreams;
  final int completedMilestones;
  final int completedTasks;
  final int bestHabitStreak;
  final int activityStreak;
  final int depositCount;
  final int completedSavings;
  final int journalCount;
  final int focusMinutes;
  final int capsules;
  final int balanceEntries;
  final int badges;
}

class AchievementDef {
  const AchievementDef({
    required this.code,
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
    required this.points,
    required this.check,
  });

  final String code;
  final String title;
  final String description;
  final IconData icon;
  final Color color;
  final int points;
  final bool Function(GameStats s) check;
}

/// Puan tablosu
abstract final class Points {
  static const task = 10;
  static const milestone = 25;
  static const dream = 100;
  static const habit = 5;
  static const deposit = 5;
  static const focus = 10;
  static const journal = 5;
  static const goal = 30;
  static const balance = 10;
}

final List<AchievementDef> kAchievements = [
  AchievementDef(
    code: 'first_dream',
    title: 'İlk Hayalim',
    description: 'İlk hayalini oluşturdun.',
    icon: Icons.auto_awesome,
    color: const Color(0xFF6D5DFB),
    points: 20,
    check: (s) => s.dreams >= 1,
  ),
  AchievementDef(
    code: 'first_step',
    title: 'İlk Adım',
    description: 'Bir hayal adımını ya da görevi tamamladın.',
    icon: Icons.directions_walk,
    color: const Color(0xFF20B8A6),
    points: 20,
    check: (s) => s.completedMilestones + s.completedTasks >= 1,
  ),
  AchievementDef(
    code: 'streak_7',
    title: '7 Günlük Seri',
    description: 'Bir alışkanlığı 7 gün üst üste sürdürdün.',
    icon: Icons.local_fire_department,
    color: const Color(0xFFF97316),
    points: 50,
    check: (s) => s.bestHabitStreak >= 7,
  ),
  AchievementDef(
    code: 'streak_21',
    title: '21 Gün Kuralı',
    description: '21 günlük alışkanlık serisi.',
    icon: Icons.whatshot,
    color: const Color(0xFFEF4444),
    points: 80,
    check: (s) => s.bestHabitStreak >= 21,
  ),
  AchievementDef(
    code: 'discipline_30',
    title: '30 Günlük Disiplin',
    description: '30 gün boyunca bir alışkanlığı bırakmadın.',
    icon: Icons.military_tech,
    color: const Color(0xFFD6AD60),
    points: 120,
    check: (s) => s.bestHabitStreak >= 30,
  ),
  AchievementDef(
    code: 'streak_100',
    title: '100 Gün Efsanesi',
    description: '100 günlük alışkanlık serisi.',
    icon: Icons.diamond,
    color: const Color(0xFF3B82F6),
    points: 300,
    check: (s) => s.bestHabitStreak >= 100,
  ),
  AchievementDef(
    code: 'saver',
    title: 'Birikim Ustası',
    description: '10 birikim yaptın ya da bir kumbarayı doldurdun.',
    icon: Icons.savings,
    color: const Color(0xFFD6AD60),
    points: 80,
    check: (s) => s.depositCount >= 10 || s.completedSavings >= 1,
  ),
  AchievementDef(
    code: 'dream_hunter',
    title: 'Hayal Avcısı',
    description: 'İlk hayalini gerçekleştirdin!',
    icon: Icons.emoji_events,
    color: const Color(0xFFEAB308),
    points: 150,
    check: (s) => s.completedDreams >= 1,
  ),
  AchievementDef(
    code: 'dream_collector',
    title: 'Başarı Koleksiyoncusu',
    description: '5 hayal gerçekleştirdin.',
    icon: Icons.workspace_premium,
    color: const Color(0xFF8B5CF6),
    points: 300,
    check: (s) => s.completedDreams >= 5,
  ),
  AchievementDef(
    code: 'task_50',
    title: 'Görev Makinesi',
    description: '50 görev tamamladın.',
    icon: Icons.task_alt,
    color: const Color(0xFF2EAD6B),
    points: 100,
    check: (s) => s.completedTasks >= 50,
  ),
  AchievementDef(
    code: 'writer',
    title: 'Hayal Yazarı',
    description: '10 günlük yazdın.',
    icon: Icons.menu_book,
    color: const Color(0xFFEC4899),
    points: 60,
    check: (s) => s.journalCount >= 10,
  ),
  AchievementDef(
    code: 'focus_10h',
    title: 'Derin Odak',
    description: 'Toplam 10 saat odaklandın.',
    icon: Icons.center_focus_strong,
    color: const Color(0xFF101827),
    points: 100,
    check: (s) => s.focusMinutes >= 600,
  ),
  AchievementDef(
    code: 'time_traveler',
    title: 'Zaman Yolcusu',
    description: 'Geleceğe ilk mektubunu bıraktın.',
    icon: Icons.hourglass_bottom,
    color: const Color(0xFF0EA5E9),
    points: 30,
    check: (s) => s.capsules >= 1,
  ),
  AchievementDef(
    code: 'balanced',
    title: 'Dengede Yaşam',
    description: 'Yaşam dengeni 3 kez değerlendirdin.',
    icon: Icons.balance,
    color: const Color(0xFF14B8A6),
    points: 40,
    check: (s) => s.balanceEntries >= 3,
  ),
  AchievementDef(
    code: 'daily_7',
    title: 'Her Gün Bir Adım',
    description: 'Uygulamada 7 gün üst üste ilerleme kaydettin.',
    icon: Icons.calendar_month,
    color: const Color(0xFF6366F1),
    points: 50,
    check: (s) => s.activityStreak >= 7,
  ),
];

AchievementDef? achievementByCode(String code) {
  for (final a in kAchievements) {
    if (a.code == code) return a;
  }
  return null;
}
