using System;
using System.Collections.Generic;
using System.Globalization;
using WorldWonders.Core.Content;
using WorldWonders.Core.Save;

namespace WorldWonders.Core.Services
{
    public sealed class MissionTemplate
    {
        public ToolType Tool;
        public int Index;
        public string Title;
        public string Description;
        public int Target;
    }

    /// <summary>
    /// Günlük keşif görevleri: her gün üç görev (büyüteç, fırça, mala), yerel 04.00'te yenilenir.
    /// Saat geri alınırsa eski gün yeniden açılmaz; tamamlanan gün anahtarları işlem kimlikleriyle korunur.
    /// Günlük görevler kalıcı eser koleksiyonuna eser eklemez.
    /// </summary>
    public sealed class DailyMissionManager
    {
        private readonly GameContext _ctx;
        private readonly LevelProgressService _levels;
        private readonly ExplorationStreakService _streak;

        public DailyMissionManager(GameContext ctx, LevelProgressService levels, ExplorationStreakService streak) { _ctx = ctx; _levels = levels; _streak = streak; }

        public static readonly MissionTemplate[] Templates =
        {
            new MissionTemplate { Tool = ToolType.Magnifier, Index = 0, Title = "Gizli Mühürler", Description = "Büyüteçle 5 gizli mührü bul.", Target = 5 },
            new MissionTemplate { Tool = ToolType.Magnifier, Index = 1, Title = "Yıldız Haritası", Description = "Büyüteçle 7 yıldız sembolünü bul.", Target = 7 },
            new MissionTemplate { Tool = ToolType.Magnifier, Index = 2, Title = "Kayıp Semboller", Description = "Büyüteçle 9 kayıp sembolü bul.", Target = 9 },
            new MissionTemplate { Tool = ToolType.Brush, Index = 0, Title = "Mozaik Temizliği", Description = "Fırçayla mozaiğin %80'ini temizle.", Target = 80 },
            new MissionTemplate { Tool = ToolType.Brush, Index = 1, Title = "Kum Altındaki Yazıt", Description = "Fırçayla yazıtın %85'ini ortaya çıkar.", Target = 85 },
            new MissionTemplate { Tool = ToolType.Brush, Index = 2, Title = "Tozlu Kabartma", Description = "Fırçayla kabartmanın %90'ını temizle.", Target = 90 },
            new MissionTemplate { Tool = ToolType.Trowel, Index = 0, Title = "Katmanlı Toprak", Description = "Malayla 6 buluntu çıkar.", Target = 6 },
            new MissionTemplate { Tool = ToolType.Trowel, Index = 1, Title = "Çanak Çömlek", Description = "Malayla 8 seramik parçası çıkar.", Target = 8 },
            new MissionTemplate { Tool = ToolType.Trowel, Index = 2, Title = "Gömülü Sikkeler", Description = "Malayla 10 sikke çıkar.", Target = 10 },
        };

        public static MissionTemplate Template(ToolType t, int index) { return Templates[(int)t * 3 + index]; }

        public bool IsUnlocked { get { return _levels.IsColosseumLevelCompleted(GameConfig.DailyUnlockColosseumLevel); } }

        /// <summary>Yerel saatle 04.00 sınırlı gün anahtarı (yyyy-MM-dd).</summary>
        public static string DayKey(DateTime utcNow, TimeSpan localOffset)
        {
            var local = utcNow + localOffset - TimeSpan.FromHours(GameConfig.DailyResetHourLocal);
            return local.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        }

        public string CurrentDayKey { get { return DayKey(_ctx.Clock.UtcNow, _ctx.Clock.LocalOffset); } }

        public TimeSpan TimeToReset()
        {
            var local = _ctx.Clock.UtcNow + _ctx.Clock.LocalOffset;
            var next = local.Date.AddHours(GameConfig.DailyResetHourLocal);
            if (next <= local) next = next.AddDays(1);
            return next - local;
        }

        /// <summary>Gün değiştiyse yeni görevleri üretir. Saat geri alındıysa mevcut görevler korunur.</summary>
        public void Refresh()
        {
            if (!IsUnlocked) return;
            var s = _ctx.Save;
            string key = CurrentDayKey;
            if (string.CompareOrdinal(key, s.HighestDayKey) <= 0 && s.DailyMissions.Count == 3) return; // aynı gün ya da geri alınmış saat
            if (string.CompareOrdinal(key, s.HighestDayKey) < 0) return;
            s.HighestDayKey = key;
            s.DailyDayKey = key;
            s.DailyBonusClaimed = _ctx.Rewards.HasTransaction("dailybonus:" + key);
            s.DailyMissions.Clear();
            var rng = new Rng(Rng.Hash("daily:" + key));
            for (int t = 0; t < 3; t++)
            {
                var tpl = Template((ToolType)t, rng.Next(3));
                s.DailyMissions.Add(new DailyMissionState
                {
                    Tool = tpl.Tool, TemplateIndex = tpl.Index, Target = tpl.Target, Progress = 0,
                    Completed = _ctx.Rewards.HasTransaction("daily:" + key + ":" + (int)tpl.Tool)
                });
            }
            _ctx.Events.Daily(); _ctx.Events.MarkDirty();
        }

        public IList<DailyMissionState> Missions { get { Refresh(); return _ctx.Save.DailyMissions; } }

        public DailyMissionState MissionFor(ToolType t)
        {
            foreach (var m in Missions) if (m.Tool == t) return m;
            return null;
        }

        /// <summary>Görev mini oyunu bitince sonucu bildirir (bulunan sembol, temizlenen yüzde, çıkarılan buluntu).</summary>
        public bool ReportResult(ToolType tool, int value)
        {
            if (!IsUnlocked) return false;
            Refresh();
            var m = MissionFor(tool);
            if (m == null || m.Completed) return false;
            m.Progress = Math.Max(m.Progress, Math.Min(value, m.Target));
            if (m.Progress < m.Target) { _ctx.Events.MarkDirty(); return false; }
            string key = _ctx.Save.DailyDayKey;
            m.Completed = true;
            if (_ctx.Rewards.Grant("daily:" + key + ":" + (int)tool, RewardBundle.Of(gold: GameConfig.DailyMissionGold(tool), xp: GameConfig.XpDailyMission)))
                _ctx.Save.DailyMissionsCompletedTotal++;
            bool all = true;
            foreach (var x in _ctx.Save.DailyMissions) all &= x.Completed;
            if (all && _ctx.Rewards.Grant("dailybonus:" + key, RewardBundle.Of(gold: GameConfig.DailyAllBonusGold, xp: GameConfig.XpDailyBonus)))
            {
                _ctx.Save.DailyBonusClaimed = true;
                _ctx.Save.DailyAllCompletedDays++;
                _streak.Advance(key);
            }
            _ctx.Events.Daily(); _ctx.Events.MarkDirty();
            return true;
        }
    }

    /// <summary>Yedi günlük keşif serisi: kaçırılan gün ilerlemeyi sıfırlamaz.</summary>
    public sealed class ExplorationStreakService
    {
        private readonly GameContext _ctx;
        public ExplorationStreakService(GameContext ctx) { _ctx = ctx; }

        public int NextDay { get { return _ctx.Save.StreakStage + 1; } }
        public int CompletedCycles { get { return _ctx.Save.StreakCycles; } }

        internal void Advance(string dayKey)
        {
            var s = _ctx.Save;
            if (s.StreakLastAdvancedDayKey == dayKey) return;
            s.StreakLastAdvancedDayKey = dayKey;
            int day = s.StreakStage + 1;
            var reward = GameConfig.StreakDayReward(day);
            if (day == 7)
            {
                int cycle = s.StreakCycles + 1;
                reward = reward.WithXp(GameConfig.XpStreakCycle);
                foreach (var ms in GameConfig.StreakCycleBadgeMilestones)
                    if (cycle == ms) reward = reward.WithBadge(BadgeCatalog.StreakCycles(ms));
                _ctx.Rewards.Grant("streak:" + s.StreakCycles + ":" + day, reward);
                s.StreakCycles = cycle;
                s.StreakStage = 0;
            }
            else
            {
                _ctx.Rewards.Grant("streak:" + s.StreakCycles + ":" + day, reward);
                s.StreakStage = day;
            }
            _ctx.Events.MarkDirty();
        }
    }
}
