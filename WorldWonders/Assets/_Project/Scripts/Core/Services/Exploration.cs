using System;
using System.Collections.Generic;
using WorldWonders.Core.Content;

namespace WorldWonders.Core.Services
{
    public sealed class DiscoveryPoint
    {
        public int Wonder;
        public int Area;
        public DiscoveryKind Kind;
        public ToolType Tool;
        public ArtifactInfo Artifact; // yalnız Artifact türünde
        public string Id { get { return WonderCatalog.Get(Wonder).Id + "_disc" + Area; } }
    }

    public enum DiscoveryState { Locked, Available, Done }

    /// <summary>84 kalıcı keşif noktası: tek alanlarda eser, çift alanlarda sandık.</summary>
    public sealed class DiscoveryManager
    {
        private readonly GameContext _ctx;
        private readonly DestinationMapService _dest;

        public DiscoveryManager(GameContext ctx, DestinationMapService dest) { _ctx = ctx; _dest = dest; }

        public static DiscoveryPoint Point(int w, int area)
        {
            var kind = GameConfig.DiscoveryKindForArea(area);
            return new DiscoveryPoint
            {
                Wonder = w, Area = area, Kind = kind, Tool = GameConfig.ToolForArea(area),
                Artifact = kind == DiscoveryKind.Artifact ? WonderCatalog.ArtifactForArea(w, area) : null
            };
        }

        public IEnumerable<DiscoveryPoint> PointsOf(int w)
        {
            for (int a = 1; a <= GameConfig.AreasPerWonder; a++) yield return Point(w, a);
        }

        public bool IsDone(int w, int area) { return (_ctx.Save.DiscoveriesDone[w] & (1 << (area - 1))) != 0; }

        public DiscoveryState StateOf(int w, int area)
        {
            if (IsDone(w, area)) return DiscoveryState.Done;
            return _dest.IsAreaUnlocked(w, area) ? DiscoveryState.Available : DiscoveryState.Locked;
        }

        /// <summary>Keşif mini oyunu başarıyla bittiğinde çağrılır. Her keşif bir kez tamamlanır.</summary>
        public RewardBundle Complete(int w, int area)
        {
            if (StateOf(w, area) != DiscoveryState.Available) return null;
            _ctx.Save.DiscoveriesDone[w] |= 1 << (area - 1);
            var p = Point(w, area);
            RewardBundle reward;
            if (p.Kind == DiscoveryKind.Artifact)
            {
                reward = RewardBundle.Of(xp: GameConfig.XpArtifact);
                _ctx.Rewards.Grant("artifact:" + w + ":" + area, reward);
                if (ArtifactsCollected(w) == GameConfig.ArtifactsPerWonder)
                    _ctx.Rewards.Grant("gallery:" + w, RewardBundle.Of().WithBadge(BadgeCatalog.Gallery(w)));
            }
            else
            {
                reward = GameConfig.ChestReward(area).WithXp(GameConfig.XpChest);
                _ctx.Rewards.Grant("chest:" + w + ":" + area, reward);
            }
            _ctx.Events.Discovery(w, area);
            _ctx.Events.MarkDirty();
            return reward;
        }

        public int ArtifactsCollected(int w)
        {
            int c = 0;
            for (int a = 1; a <= GameConfig.AreasPerWonder; a += 2) if (IsDone(w, a)) c++;
            return c;
        }

        public int ChestsOpened(int w)
        {
            int c = 0;
            for (int a = 2; a <= GameConfig.AreasPerWonder; a += 2) if (IsDone(w, a)) c++;
            return c;
        }

        public bool IsArtifactCollected(ArtifactInfo a) { return IsDone(a.Wonder, a.Area); }
    }

    public sealed class MuseumService
    {
        private readonly DiscoveryManager _disc;
        public MuseumService(DiscoveryManager disc) { _disc = disc; }

        public int GalleryCount { get { return GameConfig.WonderCount; } }
        public ArtifactInfo[] Showcases(int gallery) { return WonderCatalog.Get(gallery).Artifacts; }
        public bool IsCollected(ArtifactInfo a) { return _disc.IsArtifactCollected(a); }
        public bool IsGalleryComplete(int gallery) { return _disc.ArtifactsCollected(gallery) == GameConfig.ArtifactsPerWonder; }
        public int TotalCollected
        {
            get { int t = 0; for (int w = 0; w < GameConfig.WonderCount; w++) t += _disc.ArtifactsCollected(w); return t; }
        }
    }

    public enum ToolUpgradeError { None, MaxLevel, NotUnlockedYet, NotEnoughGold }

    /// <summary>Araç seviyeleri bütün harikalarda geçerlidir ve zorunlu değildir.</summary>
    public sealed class ToolUpgradeService
    {
        private readonly GameContext _ctx;
        private readonly LevelProgressService _levels;
        private readonly EconomyService _eco;
        public ToolUpgradeService(GameContext ctx, LevelProgressService levels, EconomyService eco) { _ctx = ctx; _levels = levels; _eco = eco; }

        public int Level(ToolType t) { return _ctx.Save.ToolLevels[(int)t]; }
        public float Multiplier(ToolType t) { return GameConfig.ToolEffectMultiplier(Level(t)); }

        public ToolUpgradeError CanUpgrade(ToolType t)
        {
            int next = Level(t) + 1;
            if (next > GameConfig.ToolMaxLevel) return ToolUpgradeError.MaxLevel;
            if (!_levels.IsColosseumLevelCompleted(GameConfig.ToolUpgradeUnlockLevel(next))) return ToolUpgradeError.NotUnlockedYet;
            if (_eco.Gold < GameConfig.ToolUpgradeCost(next)) return ToolUpgradeError.NotEnoughGold;
            return ToolUpgradeError.None;
        }

        public ToolUpgradeError Upgrade(ToolType t)
        {
            var e = CanUpgrade(t);
            if (e != ToolUpgradeError.None) return e;
            int next = Level(t) + 1;
            if (!_eco.TrySpendGold(GameConfig.ToolUpgradeCost(next))) return ToolUpgradeError.NotEnoughGold;
            _ctx.Save.ToolLevels[(int)t] = next;
            _ctx.Events.MarkDirty();
            return ToolUpgradeError.None;
        }
    }

    public sealed class ExplorerCustomizationService
    {
        private readonly GameContext _ctx;
        public ExplorerCustomizationService(GameContext ctx) { _ctx = ctx; }

        public bool IsOwned(string id) { return _ctx.Save.OwnedCosmetics.Contains(id); }

        public string Equipped(CosmeticSlot slot) { string id; return _ctx.Save.Equipped.TryGetValue(slot, out id) ? id : null; }

        public IEnumerable<CosmeticInfo> OwnedInSlot(CosmeticSlot slot)
        {
            foreach (var id in _ctx.Save.OwnedCosmetics)
            {
                var c = CosmeticCatalog.Resolve(id);
                if (c != null && c.Slot == slot) yield return c;
            }
        }

        public bool Equip(string id)
        {
            if (!IsOwned(id)) return false;
            var c = CosmeticCatalog.Resolve(id);
            if (c == null) return false;
            _ctx.Save.Equipped[c.Slot] = id;
            _ctx.Events.MarkDirty();
            return true;
        }

        public void Unequip(CosmeticSlot slot)
        {
            if (slot == CosmeticSlot.Body || slot == CosmeticSlot.SkinTone || slot == CosmeticSlot.Hair || slot == CosmeticSlot.Outfit) return; // zorunlu yuvalar
            _ctx.Save.Equipped.Remove(slot);
            _ctx.Events.MarkDirty();
        }
    }
}
