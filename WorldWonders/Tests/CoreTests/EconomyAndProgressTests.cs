using System;
using System.IO;
using System.Linq;
using NUnit.Framework;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Save;
using WorldWonders.Core.Services;

namespace WorldWonders.Tests
{
    public static class TestUtil
    {
        public static DateTime T0 = new DateTime(2026, 11, 19, 9, 0, 0, DateTimeKind.Utc);
        public static GameSession NewSession(out ManualClock clock)
        {
            clock = new ManualClock(T0);
            return new GameSession(SaveData.CreateNew(T0), clock);
        }

        /// <summary>Bir harikanın tüm bölümlerini verilen yıldızla tamamlar (can harcamadan).</summary>
        public static void ClearWonder(GameSession s, int w, int stars = 3, bool placePieces = true)
        {
            for (int l = 1; l <= GameConfig.LevelsPerWonder; l++)
            {
                int g = GameConfig.GlobalLevel(w, l);
                Assert.AreEqual(StartLevelError.None, s.Levels.StartLevel(g), "start " + g);
                s.Levels.CompleteLevel(g, stars);
            }
            if (placePieces) for (int p = 0; p < 12; p++) Assert.IsTrue(s.Jigsaw.TryPlace(w, p, p));
        }
    }

    public class ConfigTests
    {
        [Test]
        public void DifficultyDistributionPerWonder()
        {
            var counts = new int[4];
            for (int l = 1; l <= 60; l++) counts[(int)GameConfig.DifficultyForLocalLevel(l)]++;
            Assert.AreEqual(new[] { 24, 24, 9, 3 }, counts);
        }

        [Test]
        public void OneTimeXpTotalIs19005()
        {
            long xp = 0;
            for (int w = 0; w < 7; w++)
                for (int l = 1; l <= 60; l++) xp += GameConfig.LevelXp(GameConfig.DifficultyForLocalLevel(l));
            xp += 84 * GameConfig.XpJigsawPlace + 42 * GameConfig.XpArtifact + 42 * GameConfig.XpChest + 7 * GameConfig.XpWonderComplete;
            Assert.AreEqual(19005, xp);
            Assert.AreEqual(GameConfig.OneTimeXpTotal, xp);
        }

        [Test]
        public void XpToLevel100Is29205() { Assert.AreEqual(29205, XPService.XpToMaxLevel); }

        [Test]
        public void ChestRewardsPerWonderTotal2300GoldAnd5Crystals()
        {
            int gold = 0, crystals = 0;
            for (int a = 2; a <= 12; a += 2) { var r = GameConfig.ChestReward(a); gold += r.Gold; crystals += r.Crystals; }
            Assert.AreEqual(2300, gold); Assert.AreEqual(5, crystals);
        }

        [Test]
        public void ToolDistributionIs28Each()
        {
            var c = new int[3];
            for (int w = 0; w < 7; w++) for (int a = 1; a <= 12; a++) c[(int)GameConfig.ToolForArea(a)]++;
            Assert.AreEqual(new[] { 28, 28, 28 }, c);
        }

        [Test]
        public void CatalogCounts()
        {
            Assert.AreEqual(42, WonderCatalog.AllArtifacts().Count());
            Assert.AreEqual(42, WonderCatalog.AllArtifacts().Select(a => a.Id).Distinct().Count());
            var fixedC = CosmeticCatalog.AllFixed.ToList();
            Assert.AreEqual(7, fixedC.Count(c => c.Source == CosmeticSource.WonderLevel && c.Slot == CosmeticSlot.Outfit));
            Assert.AreEqual(21, fixedC.Count(c => c.Slot == CosmeticSlot.Accessory && (c.Source == CosmeticSource.WonderLevel || c.Source == CosmeticSource.WonderStars)));
            Assert.AreEqual(2, fixedC.Count(c => c.Slot == CosmeticSlot.Body));
            foreach (var w in WonderCatalog.All) { Assert.AreEqual(12, w.AreaNames.Length); Assert.AreEqual(12, w.ConstructionStages.Length); }
        }

        [Test]
        public void TitlesAtThresholds()
        {
            Assert.AreEqual("Meraklı Gezgin", Names.Title(1));
            Assert.AreEqual("Genç Kâşif", Names.Title(10));
            Assert.AreEqual("Tarih Araştırmacısı", Names.Title(25));
            Assert.AreEqual("Deneyimli Kâşif", Names.Title(40));
            Assert.AreEqual("Usta Arkeolog", Names.Title(60));
            Assert.AreEqual("Dünya Gezgini", Names.Title(80));
            Assert.AreEqual("Dünya Harikaları Ustası", Names.Title(100));
        }
    }

    public class LevelRewardTests
    {
        [Test]
        public void FirstClearAndReplayPayOnlyDifference()
        {
            var s = TestUtil.NewSession(out _);
            int g0 = s.Economy.Gold;
            s.Levels.StartLevel(1);
            var r1 = s.Levels.CompleteLevel(1, 1); // kolay
            Assert.AreEqual(50, r1.GoldGained);
            Assert.AreEqual(20, r1.XpGained);
            s.Levels.StartLevel(1);
            var r2 = s.Levels.CompleteLevel(1, 3);
            Assert.AreEqual(35, r2.GoldGained);
            Assert.AreEqual(0, r2.XpGained, "Tekrar oynanan bölüm XP vermez");
            s.Levels.StartLevel(1);
            var r3 = s.Levels.CompleteLevel(1, 2);
            Assert.AreEqual(0, r3.GoldGained);
            Assert.AreEqual(3, s.Stars.StarsOf(1));
            Assert.AreEqual(g0 + 85, s.Economy.Gold - LevelUpGold(s));
        }

        private static int LevelUpGold(GameSession s)
        {
            int g = 0; for (int l = 2; l <= s.Xp.Current.Level; l++) g += XPService.LevelReward(l).Gold; return g;
        }

        [Test]
        public void VeryHardFirstClearThreeStars()
        {
            var s = TestUtil.NewSession(out _);
            for (int l = 1; l < 20; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            s.Levels.StartLevel(20);
            var r = s.Levels.CompleteLevel(20, 3);
            Assert.AreEqual(Difficulty.VeryHard, GameConfig.DifficultyForLocalLevel(20));
            Assert.AreEqual(185, r.GoldGained);
            Assert.AreEqual(60, r.XpGained);
        }

        [Test]
        public void LevelsUnlockSequentially()
        {
            var s = TestUtil.NewSession(out _);
            Assert.IsTrue(s.Levels.IsUnlocked(1));
            Assert.IsFalse(s.Levels.IsUnlocked(2));
            Assert.AreEqual(StartLevelError.Locked, s.Levels.StartLevel(2));
            s.Levels.StartLevel(1); s.Levels.CompleteLevel(1, 1);
            Assert.IsTrue(s.Levels.IsUnlocked(2));
            Assert.Throws<InvalidOperationException>(() => s.Levels.CompleteLevel(5, 3));
        }

        [Test]
        public void StarMilestonesGrantedOnce()
        {
            var s = TestUtil.NewSession(out _);
            TestUtil.ClearWonder(s, 0, 3, placePieces: false);
            Assert.AreEqual(180, s.Stars.WonderStars(0));
            Assert.IsTrue(s.Save.Badges.Contains(BadgeCatalog.Stars(0)));
            Assert.IsTrue(s.Save.OwnedCosmetics.Contains(CosmeticCatalog.AccessoryId(0, 3)));
            Assert.IsTrue(s.Save.Transactions.Contains("starms:0:45"));
            int crystals = s.Economy.Crystals;
            s.Levels.StartLevel(1); s.Levels.CompleteLevel(1, 3);
            Assert.AreEqual(crystals, s.Economy.Crystals, "Kilometre taşı ikinci kez verilmez");
        }
    }

    public class LifeTests
    {
        [Test]
        public void FailAndAbandonCostLifeWinDoesNot()
        {
            var s = TestUtil.NewSession(out var clock);
            Assert.AreEqual(5, s.Lives.Lives);
            s.Levels.StartLevel(1); s.Levels.FailLevel(1);
            Assert.AreEqual(4, s.Lives.Lives);
            s.Levels.StartLevel(1); s.Levels.AbandonActiveLevel();
            Assert.AreEqual(3, s.Lives.Lives);
            s.Levels.StartLevel(1); s.Levels.CompleteLevel(1, 2);
            Assert.AreEqual(3, s.Lives.Lives);
        }

        [Test]
        public void LivesRegenerateAndCap()
        {
            var s = TestUtil.NewSession(out var clock);
            for (int i = 0; i < 5; i++) { s.Levels.StartLevel(1); s.Levels.FailLevel(1); }
            Assert.AreEqual(0, s.Lives.Lives);
            Assert.AreEqual(StartLevelError.NoLives, s.Levels.StartLevel(1));
            clock.Advance(TimeSpan.FromMinutes(29));
            Assert.AreEqual(0, s.Lives.Lives);
            clock.Advance(TimeSpan.FromMinutes(1));
            Assert.AreEqual(1, s.Lives.Lives);
            clock.Advance(TimeSpan.FromMinutes(75));
            Assert.AreEqual(3, s.Lives.Lives);
            Assert.AreEqual(TimeSpan.FromMinutes(15), s.Lives.TimeToNextLife());
            clock.Advance(TimeSpan.FromHours(10));
            Assert.AreEqual(5, s.Lives.Lives);
        }

        [Test]
        public void ClockRewindDoesNotGrantLives()
        {
            var s = TestUtil.NewSession(out var clock);
            s.Levels.StartLevel(1); s.Levels.FailLevel(1);
            clock.Advance(TimeSpan.FromMinutes(10));
            Assert.AreEqual(4, s.Lives.Lives);
            clock.Advance(TimeSpan.FromHours(-5)); // saat geri alındı
            Assert.AreEqual(4, s.Lives.Lives);
            clock.Advance(TimeSpan.FromMinutes(29));
            Assert.AreEqual(4, s.Lives.Lives, "Geri alınan saatten sonra üretim yeniden başlar; kazanç olmaz");
            clock.Advance(TimeSpan.FromMinutes(1));
            Assert.AreEqual(5, s.Lives.Lives);
        }

        [Test]
        public void ConfigurableRegen()
        {
            var s = TestUtil.NewSession(out var clock);
            s.Lives.SetRegenMinutes(10);
            s.Levels.StartLevel(1); s.Levels.FailLevel(1);
            clock.Advance(TimeSpan.FromMinutes(10));
            Assert.AreEqual(5, s.Lives.Lives);
        }

        [Test]
        public void AppKilledDuringLevelCountsAsAbandon()
        {
            var s = TestUtil.NewSession(out var clock);
            s.Levels.StartLevel(1);
            var text = SaveStore.Serialize(s.Save);
            var s2 = new GameSession(SaveStore.Deserialize(text), clock);
            Assert.IsTrue(s2.OnBoot());
            Assert.AreEqual(4, s2.Lives.Lives);
            Assert.IsFalse(s2.OnBoot());
        }
    }

    public class JigsawAndWonderTests
    {
        [Test]
        public void PiecesEarnedAtMultiplesOfFive_PlacementManual()
        {
            var s = TestUtil.NewSession(out _);
            for (int l = 1; l <= 5; l++) { s.Levels.StartLevel(l); var r = s.Levels.CompleteLevel(l, 1); if (l == 5) Assert.AreEqual(0, r.PieceEarned); }
            Assert.IsTrue(s.Jigsaw.IsEarned(0, 0));
            Assert.IsFalse(s.Jigsaw.IsPlaced(0, 0), "İnşa otomatik ilerlemez");
            Assert.IsFalse(s.Construction.IsGroupBuilt(0, 0));
            Assert.IsFalse(s.Jigsaw.TryPlace(0, 0, 3), "Yanlış yuva reddedilir");
            long xp = s.Save.TotalXp;
            Assert.IsTrue(s.Jigsaw.TryPlace(0, 0, 0));
            Assert.AreEqual(xp + 25, s.Save.TotalXp);
            Assert.IsTrue(s.Construction.IsGroupBuilt(0, 0));
            Assert.IsFalse(s.Jigsaw.TryPlace(0, 0, 0), "İkinci kez yerleştirilemez");
            Assert.IsFalse(s.Jigsaw.TryPlace(0, 1, 1), "Kazanılmamış parça yerleştirilemez");
        }

        [Test]
        public void NextWonderNeedsAllLevelsAndPieces()
        {
            var s = TestUtil.NewSession(out _);
            TestUtil.ClearWonder(s, 0, 1, placePieces: false);
            Assert.IsFalse(s.Levels.IsUnlocked(61));
            Assert.AreEqual(RegionState.InProgress, s.WorldMap.StateOf(0));
            for (int p = 0; p < 11; p++) s.Jigsaw.TryPlace(0, p, p);
            Assert.IsFalse(s.Levels.IsUnlocked(61));
            s.Jigsaw.TryPlace(0, 11, 11);
            Assert.IsTrue(s.Levels.IsUnlocked(61));
            Assert.AreEqual(RegionState.Completed, s.WorldMap.StateOf(0));
            Assert.AreEqual(RegionState.InProgress, s.WorldMap.StateOf(1));
            Assert.IsTrue(s.Save.Badges.Contains(BadgeCatalog.WonderComplete(0)));
        }

        [Test]
        public void CosmeticsUnlockAtLevels15_30_45()
        {
            var s = TestUtil.NewSession(out _);
            TestUtil.ClearWonder(s, 0, 1, placePieces: false);
            Assert.IsTrue(s.Customization.IsOwned(CosmeticCatalog.AccessoryId(0, 1)));
            Assert.IsTrue(s.Customization.IsOwned(CosmeticCatalog.AccessoryId(0, 2)));
            Assert.IsTrue(s.Customization.IsOwned(CosmeticCatalog.SpecialOutfitId(0)));
            Assert.IsFalse(s.Customization.IsOwned(CosmeticCatalog.AccessoryId(0, 3)), "60 yıldız < 150");
            Assert.IsTrue(s.Customization.Equip(CosmeticCatalog.SpecialOutfitId(0)));
            Assert.IsFalse(s.Customization.Equip(CosmeticCatalog.SpecialOutfitId(1)));
        }

        [Test]
        public void FullGameGivesExactOneTimeXpAndAllStarsFrame()
        {
            var s = TestUtil.NewSession(out _);
            for (int w = 0; w < 7; w++)
            {
                TestUtil.ClearWonder(s, w, 3);
                for (int a = 1; a <= 12; a++) Assert.IsNotNull(s.Discoveries.Complete(w, a));
            }
            Assert.AreEqual(19005, s.Save.TotalXp);
            Assert.AreEqual(1260, s.Stars.TotalStars);
            Assert.IsTrue(s.Customization.IsOwned(CosmeticCatalog.AllStarsFrame));
            Assert.AreEqual(84, s.Jigsaw.TotalPlaced);
            Assert.AreEqual(42, s.Museum.TotalCollected);
            for (int w = 0; w < 7; w++) Assert.IsTrue(s.Museum.IsGalleryComplete(w));
            Assert.AreEqual(7, s.Profile.Snapshot().WondersCompleted);
            Assert.AreEqual(42, s.Profile.Snapshot().ChestsOpened);
            Assert.AreEqual(XPService.Compute(19005).Level, s.Xp.Current.Level);
        }
    }

    public class DiscoveryTests
    {
        [Test]
        public void AreasUnlockEveryFiveLevels()
        {
            var s = TestUtil.NewSession(out _);
            Assert.IsTrue(s.Destinations.IsAreaUnlocked(0, 1));
            Assert.IsFalse(s.Destinations.IsAreaUnlocked(0, 2));
            Assert.AreEqual(DiscoveryState.Available, s.Discoveries.StateOf(0, 1));
            Assert.AreEqual(DiscoveryState.Locked, s.Discoveries.StateOf(0, 2));
            for (int l = 1; l <= 5; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            Assert.IsTrue(s.Destinations.IsAreaUnlocked(0, 2));
            Assert.IsFalse(s.Destinations.IsAreaUnlocked(0, 3));
            Assert.IsFalse(s.Destinations.IsAreaUnlocked(1, 1));
        }

        [Test]
        public void ArtifactAndChestRewardsOnce()
        {
            var s = TestUtil.NewSession(out _);
            for (int l = 1; l <= 5; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            long xp = s.Save.TotalXp;
            var r = s.Discoveries.Complete(0, 1);
            Assert.IsNotNull(r);
            Assert.AreEqual(xp + 40, s.Save.TotalXp);
            Assert.IsTrue(s.Museum.IsCollected(WonderCatalog.Get(0).Artifacts[0]));
            Assert.IsNull(s.Discoveries.Complete(0, 1));
            int gold = s.Economy.Gold;
            var c = s.Discoveries.Complete(0, 2);
            Assert.AreEqual(150, c.Gold);
            Assert.AreEqual(gold + 150, s.Economy.Gold - 0 - LevelUps(s, xp));
        }

        private static int LevelUps(GameSession s, long xpBefore)
        {
            int g = 0; int before = XPService.Compute(xpBefore).Level;
            for (int l = before + 1; l <= s.Xp.Current.Level; l++) g += XPService.LevelReward(l).Gold;
            return g;
        }

        [Test]
        public void ToolUpgradesRequireUnlockAndGold()
        {
            var s = TestUtil.NewSession(out _);
            s.Save.Gold = 10000;
            Assert.AreEqual(ToolUpgradeError.NotUnlockedYet, s.Tools.Upgrade(ToolType.Brush));
            for (int l = 1; l <= 10; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            int gold = s.Economy.Gold;
            Assert.AreEqual(ToolUpgradeError.None, s.Tools.Upgrade(ToolType.Brush));
            Assert.AreEqual(gold - 500, s.Economy.Gold);
            Assert.AreEqual(1.2f, s.Tools.Multiplier(ToolType.Brush), 1e-5);
            Assert.AreEqual(ToolUpgradeError.NotUnlockedYet, s.Tools.Upgrade(ToolType.Brush));
            for (int l = 11; l <= 30; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            Assert.AreEqual(ToolUpgradeError.None, s.Tools.Upgrade(ToolType.Brush));
            Assert.AreEqual(1.4f, s.Tools.Multiplier(ToolType.Brush), 1e-5);
            Assert.AreEqual(ToolUpgradeError.MaxLevel, s.Tools.Upgrade(ToolType.Brush));
            s.Save.Gold = 100;
            Assert.AreEqual(ToolUpgradeError.NotEnoughGold, s.Tools.Upgrade(ToolType.Magnifier));
        }
    }

    public class DailyAndStreakTests
    {
        private static GameSession Unlocked(out ManualClock clock)
        {
            var s = TestUtil.NewSession(out clock);
            for (int l = 1; l <= 5; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            return s;
        }

        private static void CompleteAll(GameSession s)
        {
            foreach (var m in s.Daily.Missions.ToList()) s.Daily.ReportResult(m.Tool, m.Target);
        }

        [Test]
        public void LockedUntilColosseum5()
        {
            var s = TestUtil.NewSession(out _);
            Assert.IsFalse(s.Daily.IsUnlocked);
            Assert.AreEqual(0, s.Daily.Missions.Count);
        }

        [Test]
        public void ThreeMissionsOnePerTool_400GoldPerDay()
        {
            var s = Unlocked(out var clock);
            var ms = s.Daily.Missions;
            Assert.AreEqual(3, ms.Count);
            CollectionAssert.AreEquivalent(new[] { ToolType.Magnifier, ToolType.Brush, ToolType.Trowel }, ms.Select(m => m.Tool));
            int gold = s.Economy.Gold; long xp = s.Save.TotalXp;
            int streakDay1 = GameConfig.StreakDayReward(1).Gold;
            CompleteAll(s);
            int levelGold = 0; for (int l = XPService.Compute(xp).Level + 1; l <= s.Xp.Current.Level; l++) levelGold += XPService.LevelReward(l).Gold;
            Assert.AreEqual(gold + 400 + streakDay1 + levelGold, s.Economy.Gold);
            Assert.AreEqual(xp + 50, s.Save.TotalXp);
            Assert.AreEqual(1, s.Streak.NextDay - 1);
            // Aynı gün tekrar rapor: ödül yok
            gold = s.Economy.Gold;
            CompleteAll(s);
            Assert.AreEqual(gold, s.Economy.Gold);
        }

        [Test]
        public void ResetsAt0400Local()
        {
            var s = Unlocked(out var clock);
            // T0 = 09:00 UTC = 12:00 yerel (+3)
            string k1 = s.Daily.CurrentDayKey;
            clock.Advance(TimeSpan.FromHours(15)); // 03:00 yerel ertesi gün
            Assert.AreEqual(k1, s.Daily.CurrentDayKey);
            clock.Advance(TimeSpan.FromHours(1)); // 04:00
            Assert.AreNotEqual(k1, s.Daily.CurrentDayKey);
        }

        [Test]
        public void ClockRewindCannotFarmMissions()
        {
            var s = Unlocked(out var clock);
            CompleteAll(s);
            int gold = s.Economy.Gold;
            clock.Advance(TimeSpan.FromDays(1));
            s.Daily.Refresh();
            CompleteAll(s);
            Assert.Greater(s.Economy.Gold, gold);
            gold = s.Economy.Gold;
            clock.Advance(TimeSpan.FromDays(-1)); // geri al
            s.Daily.Refresh();
            CompleteAll(s);
            Assert.AreEqual(gold, s.Economy.Gold);
        }

        [Test]
        public void StreakDoesNotResetOnMissedDayAndCyclesGiveBadges()
        {
            var s = Unlocked(out var clock);
            for (int d = 0; d < 7; d++)
            {
                CompleteAll(s);
                clock.Advance(TimeSpan.FromDays(d == 2 ? 3 : 1)); // bir gün atlandı
            }
            Assert.AreEqual(1, s.Streak.CompletedCycles);
            Assert.AreEqual(1, s.Streak.NextDay);
            Assert.IsTrue(s.Save.Badges.Contains(BadgeCatalog.StreakCycles(1)));
            for (int d = 0; d < 7 * 4; d++) { CompleteAll(s); clock.Advance(TimeSpan.FromDays(1)); }
            Assert.AreEqual(5, s.Streak.CompletedCycles);
            Assert.IsTrue(s.Save.Badges.Contains(BadgeCatalog.StreakCycles(5)));
            Assert.IsFalse(s.Save.Badges.Contains(BadgeCatalog.StreakCycles(15)));
        }
    }

    public class XpAndMasteryTests
    {
        [Test]
        public void LevelCurve()
        {
            Assert.AreEqual(1, XPService.Compute(0).Level);
            Assert.AreEqual(1, XPService.Compute(49).Level);
            Assert.AreEqual(2, XPService.Compute(50).Level);
            Assert.AreEqual(3, XPService.Compute(105).Level);
            Assert.AreEqual(99, XPService.Compute(29204).Level);
            Assert.AreEqual(100, XPService.Compute(29205).Level);
            Assert.AreEqual(0, XPService.Compute(29205).Mastery);
            Assert.AreEqual(1, XPService.Compute(29205 + 300).Mastery);
            Assert.AreEqual(2, XPService.Compute(29205 + 300 + 320).Mastery);
            Assert.AreEqual(100, XPService.Compute(10_000_000).Level);
            Assert.Greater(XPService.Compute(10_000_000).Mastery, 100);
        }

        [Test]
        public void MasteryRewardsAreCosmeticOnly()
        {
            var s = TestUtil.NewSession(out _);
            s.Xp.AddXp(29205 + 300 * 20 + 20 * 200);
            Assert.AreEqual(100, s.Xp.Current.Level);
            Assert.GreaterOrEqual(s.Xp.Current.Mastery, 10);
            Assert.IsTrue(s.Customization.IsOwned(CosmeticCatalog.MasteryOutfit));
            Assert.IsTrue(s.Save.OwnedCosmetics.Any(c => c.StartsWith("mastery_outfitcolor_")));
            Assert.IsTrue(s.Save.OwnedCosmetics.Any(c => c.StartsWith("mastery_decor_")));
            // Güçlendirici vermez
            Assert.AreEqual(1, s.Economy.BoosterCount(BoosterType.AncientArrow));
            Assert.IsTrue(s.Customization.Equip(s.Save.OwnedCosmetics.First(c => c.StartsWith("mastery_frame_"))));
        }
    }

    public class SaveTests
    {
        private string _dir;
        [SetUp] public void Setup() { _dir = Path.Combine(Path.GetTempPath(), "wwtest_" + Guid.NewGuid().ToString("N")); }
        [TearDown] public void Down() { if (Directory.Exists(_dir)) Directory.Delete(_dir, true); }

        [Test]
        public void RoundTripPreservesEverything()
        {
            var s = TestUtil.NewSession(out var clock);
            TestUtil.ClearWonder(s, 0, 2);
            s.Discoveries.Complete(0, 1);
            var store = new SaveStore(_dir);
            store.Write(s.Save);
            var loaded = store.Load(clock.UtcNow);
            Assert.AreEqual(LoadSource.Main, loaded.Source);
            var s2 = new GameSession(loaded.Data, clock);
            Assert.AreEqual(s.Save.TotalXp, s2.Save.TotalXp);
            Assert.AreEqual(120, s2.Stars.TotalStars);
            Assert.AreEqual(12, s2.Jigsaw.PlacedCount(0));
            Assert.IsTrue(s2.Levels.IsUnlocked(61));
            Assert.IsTrue(s2.Discoveries.IsDone(0, 1));
            Assert.AreEqual(s.Save.Transactions.Count, s2.Save.Transactions.Count);
            // İşlem kimlikleri yüklemeden sonra da korur
            Assert.IsNull(s2.Discoveries.Complete(0, 1));
        }

        [Test]
        public void CorruptMainFallsBackToBackup()
        {
            var s = TestUtil.NewSession(out var clock);
            var store = new SaveStore(_dir);
            s.Save.Gold = 111; store.Write(s.Save);
            s.Save.Gold = 222; store.Write(s.Save);
            File.WriteAllText(store.MainPath, "WW1 deadbeef\n{\"Gold\":999999}");
            var r = store.Load(clock.UtcNow);
            Assert.AreEqual(LoadSource.Backup, r.Source);
            Assert.AreEqual(111, r.Data.Gold);
            Assert.IsNotNull(r.Warning);
        }

        [Test]
        public void TamperedPayloadRejected()
        {
            var s = TestUtil.NewSession(out _);
            var text = SaveStore.Serialize(s.Save);
            Assert.IsNotNull(SaveStore.Deserialize(text));
            Assert.IsNull(SaveStore.Deserialize(text.Replace("\"Gold\":500", "\"Gold\":99999")));
            Assert.IsNull(SaveStore.Deserialize("garbage"));
        }

        [Test]
        public void ExportImportBackup()
        {
            var s = TestUtil.NewSession(out var clock);
            var store = new SaveStore(_dir);
            s.Save.Gold = 4242;
            var path = store.ExportBackup(s.Save);
            var back = store.ImportBackup(path);
            Assert.AreEqual(4242, back.Gold);
            Assert.AreEqual(1, store.ListBackups().Length);
        }

        [Test]
        public void FreshWhenNothingExists()
        {
            var store = new SaveStore(_dir);
            var r = store.Load(TestUtil.T0);
            Assert.AreEqual(LoadSource.Fresh, r.Source);
            Assert.IsNull(r.Warning);
            Assert.AreEqual(5, r.Data.Lives);
        }

        [Test]
        public void RewardServiceIsIdempotent()
        {
            var s = TestUtil.NewSession(out _);
            int g = s.Economy.Gold;
            Assert.IsTrue(s.Rewards.Grant("x:1", RewardBundle.Of(gold: 10)));
            Assert.IsFalse(s.Rewards.Grant("x:1", RewardBundle.Of(gold: 10)));
            Assert.AreEqual(g + 10, s.Economy.Gold);
        }
    }

    public class StoreAndRoomTests
    {
        [Test]
        public void BuyBoostersAndCosmetics()
        {
            var s = TestUtil.NewSession(out _);
            s.Save.Gold = 1000;
            Assert.IsTrue(s.Economy.BuyBooster(BoosterType.Shuffle));
            Assert.AreEqual(750, s.Economy.Gold);
            Assert.IsTrue(s.Economy.BuyCosmetic("hat_straw"));
            Assert.IsFalse(s.Economy.BuyCosmetic("hat_straw"));
            Assert.IsFalse(s.Economy.BuyCosmetic(CosmeticCatalog.SpecialOutfitId(0)), "Harika kıyafeti satılmaz");
            Assert.IsFalse(s.Economy.BuyBooster(BoosterType.AncientBomb), "Yetersiz altın");
        }

        [Test]
        public void PersonalRoomUnlocksAtColosseum10AndReflectsProgress()
        {
            var s = TestUtil.NewSession(out _);
            Assert.IsFalse(s.Room.IsUnlocked);
            for (int l = 1; l <= 10; l++) { s.Levels.StartLevel(l); s.Levels.CompleteLevel(l, 1); }
            s.Jigsaw.TryPlace(0, 0, 0);
            Assert.IsTrue(s.Room.IsUnlocked);
            var ex = s.Room.Exhibits();
            Assert.AreEqual(7, ex.Count(e => e.Kind == RoomExhibitKind.WonderModel));
            Assert.AreEqual(1f / 12f, ex.First(e => e.Id == "model_colosseum").Progress, 1e-5);
            Assert.IsTrue(ex.Any(e => e.Kind == RoomExhibitKind.MasteryCorner));
        }
    }
}
