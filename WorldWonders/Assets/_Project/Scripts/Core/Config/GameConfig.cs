using System;

namespace WorldWonders.Core
{
    /// <summary>
    /// Kesinleştirilmiş tasarım değerleri. Buradaki sayılar tasarım belgesinden gelir;
    /// değiştirilmeleri oyun dengesini değiştirir. Testler bu değerleri doğrular.
    /// </summary>
    public static class GameConfig
    {
        public const int WonderCount = 7;
        public const int LevelsPerWonder = 60;
        public const int TotalLevels = WonderCount * LevelsPerWonder; // 420
        public const int PiecesPerWonder = 12;
        public const int AreasPerWonder = 12;
        public const int LevelsPerArea = 5;
        public const int ArtifactsPerWonder = 6;
        public const int ChestsPerWonder = 6;
        public const int MaxStarsPerLevel = 3;
        public const int MaxStarsPerWonder = LevelsPerWonder * MaxStarsPerLevel; // 180
        public const int MaxStarsTotal = TotalLevels * MaxStarsPerLevel; // 1260

        // ---- Can sistemi ----
        public const int MaxLives = 5;
        public const int DefaultLifeRegenMinutes = 30;

        // ---- Bölüm ödülleri ----
        public static int FirstClearGold(Difficulty d)
        {
            switch (d)
            {
                case Difficulty.Easy: return 50;
                case Difficulty.Medium: return 75;
                case Difficulty.Hard: return 100;
                default: return 150;
            }
        }

        public static int StarBonusGold(int stars)
        {
            if (stars >= 3) return 35;
            if (stars == 2) return 15;
            return 0;
        }

        /// <summary>Belirli bir yıldız sonucunun toplam altın değeri (0 yıldız = bölüm geçilmedi).</summary>
        public static int TotalLevelGold(Difficulty d, int stars)
        {
            if (stars <= 0) return 0;
            return FirstClearGold(d) + StarBonusGold(stars);
        }

        public static int LevelXp(Difficulty d)
        {
            switch (d)
            {
                case Difficulty.Easy: return 20;
                case Difficulty.Medium: return 30;
                case Difficulty.Hard: return 45;
                default: return 60;
            }
        }

        // ---- XP ----
        public const int XpJigsawPlace = 25;
        public const int XpArtifact = 40;
        public const int XpChest = 15;
        public const int XpWonderComplete = 300;
        public const int XpDailyMission = 10;
        public const int XpDailyBonus = 20;
        public const int XpStreakCycle = 100;
        public const int OneTimeXpTotal = 19005;

        public const int MaxExplorerLevel = 100;
        public static int XpForNextLevel(int level) { return 50 + 5 * (level - 1); }
        public static int XpForMasteryLevel(int masteryLevel) { return 300 + 20 * (masteryLevel - 1); }

        // ---- Zorluk dağılımı ----
        // Her 20 bölümlük blok: 8 kolay, 8 orta, 3 zor, 1 çok zor → harika başına 24/24/9/3.
        private const string DifficultyPattern = "EEMEMEMHEMEMEHMEMMHV";

        public static Difficulty DifficultyForLocalLevel(int localLevel)
        {
            if (localLevel < 1 || localLevel > LevelsPerWonder) throw new ArgumentOutOfRangeException(nameof(localLevel));
            char c = DifficultyPattern[(localLevel - 1) % DifficultyPattern.Length];
            switch (c)
            {
                case 'E': return Difficulty.Easy;
                case 'M': return Difficulty.Medium;
                case 'H': return Difficulty.Hard;
                default: return Difficulty.VeryHard;
            }
        }

        // ---- Seviye adresleme ----
        /// <summary>Global bölüm (1..420) → harika indeksi (0..6).</summary>
        public static int WonderOfLevel(int globalLevel) { return (globalLevel - 1) / LevelsPerWonder; }
        public static int LocalLevel(int globalLevel) { return (globalLevel - 1) % LevelsPerWonder + 1; }
        public static int GlobalLevel(int wonder, int localLevel) { return wonder * LevelsPerWonder + localLevel; }
        /// <summary>Yerel bölüm → alan (1..12).</summary>
        public static int AreaOfLocalLevel(int localLevel) { return (localLevel - 1) / LevelsPerArea + 1; }
        /// <summary>Yerel 5,10,...,60 → parça indeksi 0..11, değilse -1.</summary>
        public static int JigsawPieceForLocalLevel(int localLevel) { return localLevel % 5 == 0 ? localLevel / 5 - 1 : -1; }

        // ---- Yıldız kilometre taşları (harika başına) ----
        public const int StarMilestone1 = 45;  // 250 altın
        public const int StarMilestone2 = 90;  // Antik Bomba
        public const int StarMilestone3 = 135; // 500 altın
        public const int StarMilestoneAccessory = 150; // aksesuar
        public const int StarMilestone4 = 180; // rozet + 10 kristal

        // ---- Keşif ----
        public static DiscoveryKind DiscoveryKindForArea(int area) { return area % 2 == 1 ? DiscoveryKind.Artifact : DiscoveryKind.Chest; }

        private static readonly ToolType[] AreaTools =
        {
            ToolType.Magnifier, ToolType.Brush, ToolType.Brush, ToolType.Trowel, ToolType.Trowel, ToolType.Magnifier,
            ToolType.Brush, ToolType.Trowel, ToolType.Magnifier, ToolType.Brush, ToolType.Trowel, ToolType.Magnifier
        };
        public static ToolType ToolForArea(int area) { return AreaTools[area - 1]; }

        public static RewardBundle ChestReward(int area)
        {
            switch (area)
            {
                case 2: return RewardBundle.Of(gold: 150);
                case 4: return RewardBundle.Of(gold: 200).WithBooster(BoosterType.AncientArrow, 1);
                case 6: return RewardBundle.Of(gold: 300).WithBooster(BoosterType.Hammer, 1);
                case 8: return RewardBundle.Of(gold: 400).WithBooster(BoosterType.AncientBomb, 1);
                case 10: return RewardBundle.Of(gold: 500).WithBooster(BoosterType.Shuffle, 1);
                case 12: return RewardBundle.Of(gold: 750, crystals: 5).WithBooster(BoosterType.AncientBomb, 1);
                default: throw new ArgumentException("Sandık yalnızca çift numaralı alanlarda bulunur: " + area);
            }
        }

        // ---- Araç yükseltme ----
        public const int ToolMaxLevel = 3;
        public static int ToolUpgradeCost(int targetLevel) { return targetLevel == 2 ? 500 : targetLevel == 3 ? 1250 : 0; }
        /// <summary>Kolezyum'da tamamlanması gereken yerel bölüm.</summary>
        public static int ToolUpgradeUnlockLevel(int targetLevel) { return targetLevel == 2 ? 10 : targetLevel == 3 ? 30 : 0; }
        public static float ToolEffectMultiplier(int level) { return level >= 3 ? 1.4f : level == 2 ? 1.2f : 1f; }

        // ---- Günlük görevler ----
        public const int DailyUnlockColosseumLevel = 5;
        public const int DailyResetHourLocal = 4;
        public static int DailyMissionGold(ToolType t) { return t == ToolType.Trowel ? 100 : 75; }
        public const int DailyAllBonusGold = 150;

        // ---- 7 günlük seri ----
        public static RewardBundle StreakDayReward(int day)
        {
            switch (day)
            {
                case 1: return RewardBundle.Of(gold: 100);
                case 2: return RewardBundle.Of(gold: 150);
                case 3: return RewardBundle.Of(gold: 200).WithBooster(BoosterType.AncientArrow, 1);
                case 4: return RewardBundle.Of(gold: 250);
                case 5: return RewardBundle.Of(gold: 300).WithBooster(BoosterType.Hammer, 1);
                case 6: return RewardBundle.Of(gold: 350);
                case 7: return RewardBundle.Of(gold: 500, crystals: 5);
                default: throw new ArgumentOutOfRangeException(nameof(day));
            }
        }
        public static readonly int[] StreakCycleBadgeMilestones = { 1, 5, 15, 30 };

        // ---- Kilit açılışları ----
        public const int PersonalRoomUnlockColosseumLevel = 10;
        public const int SpecialOutfitLocalLevel = 30;
        public const int AccessoryLocalLevelA = 15;
        public const int AccessoryLocalLevelB = 45;

        // ---- Mağaza (altın) ----
        public static int BoosterPrice(BoosterType b)
        {
            switch (b)
            {
                case BoosterType.AncientArrow: return 300;
                case BoosterType.AncientBomb: return 450;
                case BoosterType.Hammer: return 350;
                default: return 250;
            }
        }

        public const int StartingGold = 500;
    }
}
