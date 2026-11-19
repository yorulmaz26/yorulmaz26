using System;
using System.Collections.Generic;
using WorldWonders.Core.Content;

namespace WorldWonders.Core.Services
{
    public enum StartLevelError { None, Locked, NoLives, AlreadyActive }

    public sealed class LevelCompletionReport
    {
        public int GlobalLevel;
        public int Stars;
        public int PreviousBestStars;
        public bool FirstClear;
        public int GoldGained;
        public int XpGained;
        public int PieceEarned = -1;
        public bool NewAreaUnlocked;
        public List<string> Notes = new List<string>();
    }

    /// <summary>Yıldız hesapları ve harika başına yıldız kilometre taşları.</summary>
    public sealed class StarService
    {
        private readonly GameContext _ctx;
        public StarService(GameContext ctx) { _ctx = ctx; }

        /// <summary>Kalan hamleye göre 1–3 yıldız. Eşikler bölüm verisindedir.</summary>
        public static int StarsFor(int movesLeft, int star2MovesLeft, int star3MovesLeft)
        {
            if (movesLeft >= star3MovesLeft) return 3;
            if (movesLeft >= star2MovesLeft) return 2;
            return 1;
        }

        public int StarsOf(int globalLevel) { int s; return _ctx.Save.LevelStars.TryGetValue(globalLevel, out s) ? s : 0; }

        public int WonderStars(int wonder)
        {
            int t = 0;
            for (int l = 1; l <= GameConfig.LevelsPerWonder; l++) t += StarsOf(GameConfig.GlobalLevel(wonder, l));
            return t;
        }

        public int TotalStars
        {
            get { int t = 0; foreach (var kv in _ctx.Save.LevelStars) t += kv.Value; return t; }
        }

        public static RewardBundle MilestoneReward(int wonder, int milestone)
        {
            switch (milestone)
            {
                case GameConfig.StarMilestone1: return RewardBundle.Of(gold: 250);
                case GameConfig.StarMilestone2: return RewardBundle.Of().WithBooster(BoosterType.AncientBomb, 1);
                case GameConfig.StarMilestone3: return RewardBundle.Of(gold: 500);
                case GameConfig.StarMilestoneAccessory: return RewardBundle.Of().WithCosmetic(CosmeticCatalog.AccessoryId(wonder, 3));
                case GameConfig.StarMilestone4: return RewardBundle.Of(crystals: 10).WithBadge(BadgeCatalog.Stars(wonder));
                default: return null;
            }
        }

        public static readonly int[] Milestones = { GameConfig.StarMilestone1, GameConfig.StarMilestone2, GameConfig.StarMilestone3, GameConfig.StarMilestoneAccessory, GameConfig.StarMilestone4 };

        internal void CheckMilestones(int wonder)
        {
            int stars = WonderStars(wonder);
            foreach (var m in Milestones)
                if (stars >= m) _ctx.Rewards.Grant("starms:" + wonder + ":" + m, MilestoneReward(wonder, m));
            if (TotalStars >= GameConfig.MaxStarsTotal)
                _ctx.Rewards.Grant("starms:all", RewardBundle.Of().WithCosmetic(CosmeticCatalog.AllStarsFrame));
        }
    }

    public sealed class LevelProgressService
    {
        private readonly GameContext _ctx;
        private readonly LifeService _lives;
        private readonly StarService _stars;
        private readonly JigsawService _jigsaw;

        public LevelProgressService(GameContext ctx, LifeService lives, StarService stars, JigsawService jigsaw)
        {
            _ctx = ctx; _lives = lives; _stars = stars; _jigsaw = jigsaw;
        }

        public bool IsCompleted(int globalLevel) { return _ctx.Save.LevelStars.ContainsKey(globalLevel); }

        public bool IsWonderUnlocked(int wonder)
        {
            if (wonder <= 0) return true;
            if (wonder >= GameConfig.WonderCount) return false;
            return _ctx.Save.WonderCompleted[wonder - 1] == 1;
        }

        public bool IsUnlocked(int globalLevel)
        {
            if (globalLevel < 1 || globalLevel > GameConfig.TotalLevels) return false;
            int w = GameConfig.WonderOfLevel(globalLevel);
            if (!IsWonderUnlocked(w)) return false;
            int local = GameConfig.LocalLevel(globalLevel);
            return local == 1 || IsCompleted(globalLevel - 1);
        }

        /// <summary>Oynanabilir en ileri bölüm (aktif bölüm göstergesi için).</summary>
        public int CurrentLevel
        {
            get
            {
                for (int l = 1; l <= GameConfig.TotalLevels; l++)
                    if (IsUnlocked(l) && !IsCompleted(l)) return l;
                // Hepsi tamamlandı ya da bir sonraki harika Jigsaw bekliyor.
                for (int l = GameConfig.TotalLevels; l >= 1; l--) if (IsUnlocked(l)) return l;
                return 1;
            }
        }

        public int CompletedCountInWonder(int w)
        {
            int c = 0;
            for (int l = 1; l <= GameConfig.LevelsPerWonder; l++) if (IsCompleted(GameConfig.GlobalLevel(w, l))) c++;
            return c;
        }

        public bool IsColosseumLevelCompleted(int local) { return IsCompleted(GameConfig.GlobalLevel(0, local)); }

        public StartLevelError CanStart(int globalLevel)
        {
            if (!IsUnlocked(globalLevel)) return StartLevelError.Locked;
            if (!_lives.HasLife) return StartLevelError.NoLives;
            return StartLevelError.None;
        }

        /// <summary>Bölümü başlatır; bu andan itibaren terk etmek bir can kaybettirir.</summary>
        public StartLevelError StartLevel(int globalLevel)
        {
            if (_ctx.Save.ActiveLevel != 0) AbandonActiveLevel();
            var e = CanStart(globalLevel);
            if (e != StartLevelError.None) return e;
            _ctx.Save.ActiveLevel = globalLevel;
            _ctx.Events.MarkDirty();
            return StartLevelError.None;
        }

        /// <summary>Başlanmış bölüm terk edildi (menüye dönüş ya da uygulama kapandıktan sonra açılış).</summary>
        public bool AbandonActiveLevel()
        {
            if (_ctx.Save.ActiveLevel == 0) return false;
            _ctx.Save.ActiveLevel = 0;
            _lives.LoseLife();
            return true;
        }

        public void FailLevel(int globalLevel)
        {
            if (_ctx.Save.ActiveLevel != globalLevel) return;
            _ctx.Save.ActiveLevel = 0;
            _ctx.Save.LevelsLost++;
            _lives.LoseLife();
        }

        /// <summary>Başarılı bölüm: can kaybettirmez; ödülleri farkla verir.</summary>
        public LevelCompletionReport CompleteLevel(int globalLevel, int stars)
        {
            if (stars < 1 || stars > 3) throw new ArgumentOutOfRangeException(nameof(stars));
            if (!IsUnlocked(globalLevel)) throw new InvalidOperationException("Kilitli bölüm tamamlanamaz: " + globalLevel);
            if (_ctx.Save.ActiveLevel == globalLevel) _ctx.Save.ActiveLevel = 0;

            int w = GameConfig.WonderOfLevel(globalLevel);
            int local = GameConfig.LocalLevel(globalLevel);
            var diff = GameConfig.DifficultyForLocalLevel(local);
            int prev = _stars.StarsOf(globalLevel);
            var rep = new LevelCompletionReport { GlobalLevel = globalLevel, Stars = stars, PreviousBestStars = prev, FirstClear = prev == 0 };
            _ctx.Save.LevelsWon++;

            if (stars > prev)
            {
                _ctx.Save.LevelStars[globalLevel] = stars;
                // Yalnız önceki en iyi sonuçla aradaki fark verilir. Her yıldız seviyesi ayrı işlem kimliğiyle korunur.
                for (int s = prev + 1; s <= stars; s++)
                {
                    int delta = GameConfig.TotalLevelGold(diff, s) - GameConfig.TotalLevelGold(diff, s - 1);
                    if (_ctx.Rewards.Grant("level:" + globalLevel + ":star" + s, RewardBundle.Of(gold: delta))) rep.GoldGained += delta;
                }
            }

            if (rep.FirstClear)
            {
                int xp = GameConfig.LevelXp(diff);
                if (_ctx.Rewards.Grant("level:" + globalLevel + ":xp", RewardBundle.Of(xp: xp))) rep.XpGained = xp;

                int piece = GameConfig.JigsawPieceForLocalLevel(local);
                if (piece >= 0 && _jigsaw.Earn(w, piece)) rep.PieceEarned = piece;

                if (local % GameConfig.LevelsPerArea == 0 && local < GameConfig.LevelsPerWonder)
                {
                    rep.NewAreaUnlocked = true;
                    _ctx.Events.Area(w, GameConfig.AreaOfLocalLevel(local) + 1);
                }

                if (local == GameConfig.AccessoryLocalLevelA) _ctx.Rewards.Grant("cos:" + w + ":acc1", RewardBundle.Of().WithCosmetic(CosmeticCatalog.AccessoryId(w, 1)));
                if (local == GameConfig.AccessoryLocalLevelB) _ctx.Rewards.Grant("cos:" + w + ":acc2", RewardBundle.Of().WithCosmetic(CosmeticCatalog.AccessoryId(w, 2)));
                if (local == GameConfig.SpecialOutfitLocalLevel) _ctx.Rewards.Grant("cos:" + w + ":outfit", RewardBundle.Of().WithCosmetic(CosmeticCatalog.SpecialOutfitId(w)));

                if (w == 0)
                {
                    if (local == GameConfig.DailyUnlockColosseumLevel) _ctx.Events.Feature(Features.DailyMissions);
                    if (local == GameConfig.PersonalRoomUnlockColosseumLevel) _ctx.Events.Feature(Features.PersonalRoom);
                    if (local == GameConfig.ToolUpgradeUnlockLevel(2)) _ctx.Events.Feature(Features.ToolUpgradeL2);
                    if (local == GameConfig.ToolUpgradeUnlockLevel(3)) _ctx.Events.Feature(Features.ToolUpgradeL3);
                }
            }

            _stars.CheckMilestones(w);
            _jigsaw.CheckWonderCompletion(w);
            _ctx.Events.MarkDirty();
            return rep;
        }
    }

    /// <summary>Jigsaw parçaları: bölümle kazanılır, oyuncu doğru yuvaya sürükleyerek yerleştirir. Satın alınamaz.</summary>
    public sealed class JigsawService
    {
        private readonly GameContext _ctx;
        public LevelProgressService Levels; // döngüsel bağımlılık: GameSession bağlar

        public JigsawService(GameContext ctx) { _ctx = ctx; }

        public bool IsEarned(int w, int piece) { return (_ctx.Save.PiecesEarned[w] & (1 << piece)) != 0; }
        public bool IsPlaced(int w, int piece) { return (_ctx.Save.PiecesPlaced[w] & (1 << piece)) != 0; }

        public int PlacedCount(int w) { return BitCount(_ctx.Save.PiecesPlaced[w]); }
        public int EarnedCount(int w) { return BitCount(_ctx.Save.PiecesEarned[w]); }
        public int TotalPlaced { get { int t = 0; for (int w = 0; w < GameConfig.WonderCount; w++) t += PlacedCount(w); return t; } }

        public List<int> UnplacedPieces(int w)
        {
            var r = new List<int>();
            for (int i = 0; i < GameConfig.PiecesPerWonder; i++) if (IsEarned(w, i) && !IsPlaced(w, i)) r.Add(i);
            return r;
        }

        internal bool Earn(int w, int piece)
        {
            if (IsEarned(w, piece)) return false;
            _ctx.Save.PiecesEarned[w] |= 1 << piece;
            _ctx.Events.Earned(w, piece);
            _ctx.Events.MarkDirty();
            return true;
        }

        /// <summary>Parçayı bir yuvaya bırakır. Yalnız doğru yuva kabul edilir; yanlışsa parça geri döner.</summary>
        public bool TryPlace(int w, int piece, int slot)
        {
            if (!IsEarned(w, piece) || IsPlaced(w, piece)) return false;
            if (slot != piece) return false;
            _ctx.Save.PiecesPlaced[w] |= 1 << piece;
            _ctx.Events.Placed(w, piece);
            _ctx.Rewards.Grant("jigsaw:" + w + ":" + piece, RewardBundle.Of(xp: GameConfig.XpJigsawPlace));
            CheckWonderCompletion(w);
            _ctx.Events.MarkDirty();
            return true;
        }

        internal void CheckWonderCompletion(int w)
        {
            if (_ctx.Save.WonderCompleted[w] == 1) return;
            if (PlacedCount(w) < GameConfig.PiecesPerWonder) return;
            if (Levels.CompletedCountInWonder(w) < GameConfig.LevelsPerWonder) return;
            _ctx.Save.WonderCompleted[w] = 1;
            _ctx.Rewards.Grant("wonder:" + w, RewardBundle.Of(xp: GameConfig.XpWonderComplete).WithBadge(BadgeCatalog.WonderComplete(w)));
            _ctx.Events.WonderDone(w);
            if (w + 1 < GameConfig.WonderCount) _ctx.Events.WonderOpen(w + 1);
        }

        internal static int BitCount(int v) { int c = 0; while (v != 0) { v &= v - 1; c++; } return c; }
    }

    /// <summary>3D anıtın 12 inşa grubunun durumu (yerleştirilen parçalara göre).</summary>
    public sealed class ConstructionService
    {
        private readonly JigsawService _jigsaw;
        public ConstructionService(JigsawService jigsaw) { _jigsaw = jigsaw; }
        public bool IsGroupBuilt(int w, int group) { return _jigsaw.IsPlaced(w, group); }
        public float Progress(int w) { return _jigsaw.PlacedCount(w) / (float)GameConfig.PiecesPerWonder; }
    }

    public enum RegionState { Locked, InProgress, Completed }

    public sealed class WorldMapService
    {
        private readonly GameContext _ctx;
        private readonly LevelProgressService _levels;
        private readonly ConstructionService _construction;
        public WorldMapService(GameContext ctx, LevelProgressService levels, ConstructionService c) { _ctx = ctx; _levels = levels; _construction = c; }

        public RegionState StateOf(int w)
        {
            if (_ctx.Save.WonderCompleted[w] == 1) return RegionState.Completed;
            return _levels.IsWonderUnlocked(w) ? RegionState.InProgress : RegionState.Locked;
        }

        public float ConstructionProgress(int w) { return _construction.Progress(w); }
    }

    public enum NodeState { Locked, Current, Completed }

    public sealed class DestinationMapService
    {
        private readonly LevelProgressService _levels;
        private readonly StarService _stars;
        public DestinationMapService(LevelProgressService levels, StarService stars) { _levels = levels; _stars = stars; }

        public bool IsAreaUnlocked(int w, int area)
        {
            if (!_levels.IsWonderUnlocked(w)) return false;
            if (area <= 1) return true;
            return _levels.IsCompleted(GameConfig.GlobalLevel(w, (area - 1) * GameConfig.LevelsPerArea));
        }

        public int UnlockedAreaCount(int w)
        {
            int c = 0;
            for (int a = 1; a <= GameConfig.AreasPerWonder; a++) if (IsAreaUnlocked(w, a)) c++;
            return c;
        }

        public NodeState NodeStateOf(int w, int local)
        {
            int g = GameConfig.GlobalLevel(w, local);
            if (_levels.IsCompleted(g)) return NodeState.Completed;
            return _levels.IsUnlocked(g) ? NodeState.Current : NodeState.Locked;
        }

        public int NodeStars(int w, int local) { return _stars.StarsOf(GameConfig.GlobalLevel(w, local)); }
    }
}
