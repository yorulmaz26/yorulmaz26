using System;
using WorldWonders.Core.Content;

namespace WorldWonders.Core.Services
{
    public sealed class EconomyService
    {
        private readonly GameContext _ctx;
        public EconomyService(GameContext ctx) { _ctx = ctx; }

        public int Gold { get { return _ctx.Save.Gold; } }
        public int Crystals { get { return _ctx.Save.Crystals; } }

        public int BoosterCount(BoosterType b) { int c; _ctx.Save.Boosters.TryGetValue(b, out c); return c; }

        public bool TrySpendGold(int amount)
        {
            if (amount < 0 || _ctx.Save.Gold < amount) return false;
            _ctx.Save.Gold -= amount;
            _ctx.Events.Currency(); _ctx.Events.MarkDirty();
            return true;
        }

        public bool TryConsumeBooster(BoosterType b)
        {
            int c = BoosterCount(b);
            if (c <= 0) return false;
            _ctx.Save.Boosters[b] = c - 1;
            _ctx.Events.Currency(); _ctx.Events.MarkDirty();
            return true;
        }

        /// <summary>Mağaza: güçlendirici altınla alınır. Jigsaw parçası satılmaz.</summary>
        public bool BuyBooster(BoosterType b)
        {
            if (!TrySpendGold(GameConfig.BoosterPrice(b))) return false;
            _ctx.Save.Boosters[b] = BoosterCount(b) + 1;
            _ctx.Events.Currency(); _ctx.Events.MarkDirty();
            return true;
        }

        public bool BuyCosmetic(string id)
        {
            var info = CosmeticCatalog.Resolve(id);
            if (info == null || info.Source != CosmeticSource.Store) return false;
            if (_ctx.Save.OwnedCosmetics.Contains(id)) return false;
            if (!TrySpendGold(info.GoldPrice)) return false;
            _ctx.Save.OwnedCosmetics.Add(id);
            _ctx.Events.Cosmetic(id); _ctx.Events.MarkDirty();
            return true;
        }
    }

    /// <summary>
    /// Çevrimdışı can sistemi. Saat geri alınırsa üretim çapası şimdiye çekilir (kazanç olmaz).
    /// Not: Çevrimdışı ortamda ileri alınan saat kesin olarak tespit edilemez; yalnız makul koruma sağlanır.
    /// </summary>
    public sealed class LifeService
    {
        private readonly GameContext _ctx;
        public LifeService(GameContext ctx) { _ctx = ctx; }

        public int Lives { get { Tick(); return _ctx.Save.Lives; } }
        public int MaxLives { get { return GameConfig.MaxLives; } }
        public TimeSpan RegenInterval { get { return TimeSpan.FromMinutes(_ctx.Save.LifeRegenMinutes); } }

        public void SetRegenMinutes(int minutes)
        {
            Tick();
            _ctx.Save.LifeRegenMinutes = Math.Max(1, minutes);
        }

        public void Tick()
        {
            var s = _ctx.Save;
            var now = _ctx.Clock.UtcNow;
            if (now > s.HighestSeenUtc) s.HighestSeenUtc = now;
            if (now < s.LifeRegenAnchorUtc) s.LifeRegenAnchorUtc = now; // saat geri alındı
            if (s.Lives >= GameConfig.MaxLives) { s.Lives = Math.Min(s.Lives, GameConfig.MaxLives); s.LifeRegenAnchorUtc = now; return; }
            var interval = RegenInterval;
            long n = (now - s.LifeRegenAnchorUtc).Ticks / interval.Ticks;
            if (n <= 0) return;
            int gained = (int)Math.Min(n, GameConfig.MaxLives - s.Lives);
            s.Lives += gained;
            s.LifeRegenAnchorUtc = s.Lives >= GameConfig.MaxLives ? now : s.LifeRegenAnchorUtc + TimeSpan.FromTicks(interval.Ticks * gained);
            _ctx.Events.Lives(); _ctx.Events.MarkDirty();
        }

        public TimeSpan TimeToNextLife()
        {
            Tick();
            if (_ctx.Save.Lives >= GameConfig.MaxLives) return TimeSpan.Zero;
            var t = _ctx.Save.LifeRegenAnchorUtc + RegenInterval - _ctx.Clock.UtcNow;
            return t < TimeSpan.Zero ? TimeSpan.Zero : t;
        }

        public bool HasLife { get { return Lives > 0; } }

        internal void LoseLife()
        {
            Tick();
            var s = _ctx.Save;
            if (s.Lives <= 0) return;
            if (s.Lives >= GameConfig.MaxLives) s.LifeRegenAnchorUtc = _ctx.Clock.UtcNow;
            s.Lives--;
            _ctx.Events.Lives(); _ctx.Events.MarkDirty();
        }
    }
}
