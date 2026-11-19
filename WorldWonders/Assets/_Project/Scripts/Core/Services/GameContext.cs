using System;
using WorldWonders.Core.Save;

namespace WorldWonders.Core.Services
{
    /// <summary>Servisler arası olay yolu. Unity katmanı bu olaylara abone olup UI/3D günceller.</summary>
    public sealed class GameEvents
    {
        public event Action<string, RewardBundle> RewardGranted;
        public event Action<int> ExplorerLevelUp;
        public event Action<int> MasteryLevelUp;
        public event Action<string> BadgeUnlocked;
        public event Action<string> CosmeticUnlocked;
        public event Action<int, int> PieceEarned;   // wonder, piece
        public event Action<int, int> PiecePlaced;   // wonder, piece
        public event Action<int> WonderCompleted;
        public event Action<int> WonderUnlocked;
        public event Action<int, int> AreaUnlocked;  // wonder, area
        public event Action<int, int> DiscoveryCompleted; // wonder, area
        public event Action LivesChanged;
        public event Action CurrencyChanged;
        public event Action DailyChanged;
        public event Action<string> FeatureUnlocked;
        public event Action Dirty;

        internal void Reward(string tx, RewardBundle b) { var h = RewardGranted; if (h != null) h(tx, b); }
        internal void LevelUp(int l) { var h = ExplorerLevelUp; if (h != null) h(l); }
        internal void MasteryUp(int m) { var h = MasteryLevelUp; if (h != null) h(m); }
        internal void Badge(string id) { var h = BadgeUnlocked; if (h != null) h(id); }
        internal void Cosmetic(string id) { var h = CosmeticUnlocked; if (h != null) h(id); }
        internal void Earned(int w, int p) { var h = PieceEarned; if (h != null) h(w, p); }
        internal void Placed(int w, int p) { var h = PiecePlaced; if (h != null) h(w, p); }
        internal void WonderDone(int w) { var h = WonderCompleted; if (h != null) h(w); }
        internal void WonderOpen(int w) { var h = WonderUnlocked; if (h != null) h(w); }
        internal void Area(int w, int a) { var h = AreaUnlocked; if (h != null) h(w, a); }
        internal void Discovery(int w, int a) { var h = DiscoveryCompleted; if (h != null) h(w, a); }
        internal void Lives() { var h = LivesChanged; if (h != null) h(); }
        internal void Currency() { var h = CurrencyChanged; if (h != null) h(); }
        internal void Daily() { var h = DailyChanged; if (h != null) h(); }
        internal void Feature(string f) { var h = FeatureUnlocked; if (h != null) h(f); }
        internal void MarkDirty() { var h = Dirty; if (h != null) h(); }
    }

    public static class Features
    {
        public const string DailyMissions = "daily_missions";
        public const string PersonalRoom = "personal_room";
        public const string ToolUpgradeL2 = "tool_upgrade_l2";
        public const string ToolUpgradeL3 = "tool_upgrade_l3";
        public const string Mastery = "mastery";
    }

    public sealed class GameContext
    {
        public SaveData Save;
        public readonly IClock Clock;
        public readonly GameEvents Events = new GameEvents();
        public RewardService Rewards;
        public XPService Xp;

        public GameContext(SaveData save, IClock clock) { Save = save; Clock = clock; }
    }

    /// <summary>
    /// Bütün ödüller buradan, benzersiz işlem kimliğiyle verilir. Aynı kimlik ikinci kez verilmez.
    /// </summary>
    public sealed class RewardService
    {
        private readonly GameContext _ctx;
        public RewardService(GameContext ctx) { _ctx = ctx; }

        public bool HasTransaction(string txId) { return _ctx.Save.Transactions.Contains(txId); }

        /// <summary>Ödülü bir kez verir. Daha önce verildiyse false.</summary>
        public bool Grant(string txId, RewardBundle bundle)
        {
            if (string.IsNullOrEmpty(txId)) throw new ArgumentException("İşlem kimliği zorunlu");
            if (!_ctx.Save.Transactions.Add(txId)) return false;
            Apply(bundle);
            _ctx.Events.Reward(txId, bundle);
            _ctx.Events.MarkDirty();
            return true;
        }

        private void Apply(RewardBundle b)
        {
            var s = _ctx.Save;
            if (b.Gold != 0 || b.Crystals != 0 || b.Boosters.Count > 0)
            {
                s.Gold += b.Gold;
                s.Crystals += b.Crystals;
                foreach (var kv in b.Boosters)
                {
                    int cur; s.Boosters.TryGetValue(kv.Key, out cur);
                    s.Boosters[kv.Key] = cur + kv.Value;
                }
                _ctx.Events.Currency();
            }
            foreach (var badge in b.Badges)
                if (!s.Badges.Contains(badge)) { s.Badges.Add(badge); _ctx.Events.Badge(badge); }
            foreach (var cos in b.Cosmetics)
                if (!s.OwnedCosmetics.Contains(cos)) { s.OwnedCosmetics.Add(cos); _ctx.Events.Cosmetic(cos); }
            if (b.Xp > 0) _ctx.Xp.AddXp(b.Xp);
        }
    }
}
