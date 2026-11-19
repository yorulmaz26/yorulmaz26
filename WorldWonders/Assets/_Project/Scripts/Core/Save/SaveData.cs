using System;
using System.Collections.Generic;

namespace WorldWonders.Core.Save
{
    /// <summary>
    /// Tek, sürümlü kayıt modeli. Bütün oyuncu ilerlemesi burada tutulur.
    /// Yeni alan eklerken CurrentVersion artırılır ve SaveMigrations'a adım eklenir.
    /// </summary>
    [Serializable]
    public sealed class SaveData
    {
        public const int CurrentVersion = 1;

        public int Version = CurrentVersion;
        public long SaveCounter;
        public DateTime CreatedUtc;
        public DateTime LastSavedUtc;

        // Ekonomi
        public int Gold = GameConfig.StartingGold;
        public int Crystals;
        public Dictionary<BoosterType, int> Boosters = new Dictionary<BoosterType, int>();

        // Canlar
        public int Lives = GameConfig.MaxLives;
        /// <summary>Bir sonraki canın sayılmaya başladığı UTC anı (can doluysa anlamsız).</summary>
        public DateTime LifeRegenAnchorUtc;
        /// <summary>Görülen en yüksek UTC saati; saat geri alınırsa can üretimi durur.</summary>
        public DateTime HighestSeenUtc;
        public int LifeRegenMinutes = GameConfig.DefaultLifeRegenMinutes;
        /// <summary>Başlatılıp henüz sonuçlanmamış bölüm (uygulama kapanırsa terk sayılır). 0 = yok.</summary>
        public int ActiveLevel;

        // Bölümler: global bölüm → en iyi yıldız (1..3). Yoksa tamamlanmamış.
        public Dictionary<int, int> LevelStars = new Dictionary<int, int>();

        // Jigsaw: harika → kazanılan parça bit maskesi / yerleştirilen parça bit maskesi
        public int[] PiecesEarned = new int[GameConfig.WonderCount];
        public int[] PiecesPlaced = new int[GameConfig.WonderCount];
        public int[] WonderCompleted = new int[GameConfig.WonderCount]; // 0/1

        // Keşif: harika → tamamlanan keşif alanları bit maskesi (bit area-1)
        public int[] DiscoveriesDone = new int[GameConfig.WonderCount];

        // Araçlar
        public int[] ToolLevels = { 1, 1, 1 };

        // XP
        public long TotalXp;

        // Kozmetik ve rozetler
        public List<string> OwnedCosmetics = new List<string>();
        public Dictionary<CosmeticSlot, string> Equipped = new Dictionary<CosmeticSlot, string>();
        public List<string> Badges = new List<string>();
        public List<string> PlacedRoomDecorations = new List<string>();

        // Günlük görevler
        public string DailyDayKey = "";
        public string HighestDayKey = "";
        public List<DailyMissionState> DailyMissions = new List<DailyMissionState>();
        public bool DailyBonusClaimed;
        public int DailyMissionsCompletedTotal;
        public int DailyAllCompletedDays;

        // Seri
        public int StreakStage;      // 0..6: bir sonraki ödülün günü = StreakStage + 1
        public int StreakCycles;
        public string StreakLastAdvancedDayKey = "";

        // Ayarlar
        public bool MusicOn = true;
        public bool SfxOn = true;
        public bool HapticsOn = true;
        public bool TutorialDone;

        /// <summary>İşlem kimlikleri: her ödül yalnız bir kez verilir.</summary>
        public HashSet<string> Transactions = new HashSet<string>();

        // İstatistik
        public int LevelsWon;
        public int LevelsLost;
        public int TotalMatchesMade;

        public static SaveData CreateNew(DateTime utcNow)
        {
            var s = new SaveData { CreatedUtc = utcNow, LastSavedUtc = utcNow, LifeRegenAnchorUtc = utcNow, HighestSeenUtc = utcNow };
            foreach (var id in Content.CosmeticCatalog.AllFixed)
                if (id.Source == Content.CosmeticSource.Free) s.OwnedCosmetics.Add(id.Id);
            foreach (var id in Content.CosmeticCatalog.DefaultLoadout)
            {
                var info = Content.CosmeticCatalog.Resolve(id);
                s.Equipped[info.Slot] = id;
            }
            s.Boosters[BoosterType.AncientArrow] = 1;
            s.Boosters[BoosterType.Hammer] = 1;
            return s;
        }

        public void Normalize()
        {
            if (Boosters == null) Boosters = new Dictionary<BoosterType, int>();
            if (LevelStars == null) LevelStars = new Dictionary<int, int>();
            if (PiecesEarned == null || PiecesEarned.Length != GameConfig.WonderCount) PiecesEarned = Resize(PiecesEarned);
            if (PiecesPlaced == null || PiecesPlaced.Length != GameConfig.WonderCount) PiecesPlaced = Resize(PiecesPlaced);
            if (WonderCompleted == null || WonderCompleted.Length != GameConfig.WonderCount) WonderCompleted = Resize(WonderCompleted);
            if (DiscoveriesDone == null || DiscoveriesDone.Length != GameConfig.WonderCount) DiscoveriesDone = Resize(DiscoveriesDone);
            if (ToolLevels == null || ToolLevels.Length != 3) ToolLevels = new[] { 1, 1, 1 };
            if (OwnedCosmetics == null) OwnedCosmetics = new List<string>();
            if (Equipped == null) Equipped = new Dictionary<CosmeticSlot, string>();
            if (Badges == null) Badges = new List<string>();
            if (PlacedRoomDecorations == null) PlacedRoomDecorations = new List<string>();
            if (DailyMissions == null) DailyMissions = new List<DailyMissionState>();
            if (Transactions == null) Transactions = new HashSet<string>();
            if (DailyDayKey == null) DailyDayKey = "";
            if (HighestDayKey == null) HighestDayKey = "";
            if (StreakLastAdvancedDayKey == null) StreakLastAdvancedDayKey = "";
            if (Lives < 0) Lives = 0;
            if (LifeRegenMinutes <= 0) LifeRegenMinutes = GameConfig.DefaultLifeRegenMinutes;
        }

        private static int[] Resize(int[] a)
        {
            var r = new int[GameConfig.WonderCount];
            if (a != null) Array.Copy(a, r, Math.Min(a.Length, r.Length));
            return r;
        }
    }

    [Serializable]
    public sealed class DailyMissionState
    {
        public ToolType Tool;
        public int TemplateIndex;  // 0..2
        public int Target;
        public int Progress;
        public bool Completed;
    }
}
