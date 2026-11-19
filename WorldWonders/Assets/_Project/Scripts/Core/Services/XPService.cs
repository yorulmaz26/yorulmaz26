using System;

namespace WorldWonders.Core.Services
{
    public struct LevelState
    {
        public int Level;            // 1..100
        public long XpIntoLevel;     // mevcut seviyede biriken XP
        public long XpForNext;       // 100'de 0
        public int Mastery;          // 0.. (yalnız 100'de)
        public long XpIntoMastery;
        public long XpForNextMastery;
        public string Title;
    }

    /// <summary>Kâşif seviyesi (1–100) ve sınırsız ustalık. Seviye/ustalık ödülleri işlem kimliğiyle verilir.</summary>
    public sealed class XPService
    {
        private readonly GameContext _ctx;
        public XPService(GameContext ctx) { _ctx = ctx; }

        public static readonly long XpToMaxLevel = ComputeXpToMax();

        private static long ComputeXpToMax()
        {
            long t = 0;
            for (int l = 1; l < GameConfig.MaxExplorerLevel; l++) t += GameConfig.XpForNextLevel(l);
            return t; // 29205
        }

        public static LevelState Compute(long totalXp)
        {
            var st = new LevelState();
            long rem = Math.Max(0, totalXp);
            int level = 1;
            while (level < GameConfig.MaxExplorerLevel && rem >= GameConfig.XpForNextLevel(level))
            {
                rem -= GameConfig.XpForNextLevel(level);
                level++;
            }
            st.Level = level;
            st.Title = Content.Names.Title(level);
            if (level < GameConfig.MaxExplorerLevel)
            {
                st.XpIntoLevel = rem;
                st.XpForNext = GameConfig.XpForNextLevel(level);
                return st;
            }
            int m = 0;
            while (rem >= GameConfig.XpForMasteryLevel(m + 1)) { rem -= GameConfig.XpForMasteryLevel(m + 1); m++; }
            st.Mastery = m;
            st.XpIntoMastery = rem;
            st.XpForNextMastery = GameConfig.XpForMasteryLevel(m + 1);
            return st;
        }

        public LevelState Current { get { return Compute(_ctx.Save.TotalXp); } }

        public void AddXp(int amount)
        {
            if (amount <= 0) return;
            var before = Compute(_ctx.Save.TotalXp);
            _ctx.Save.TotalXp += amount;
            var after = Compute(_ctx.Save.TotalXp);
            for (int l = before.Level + 1; l <= after.Level; l++)
            {
                _ctx.Rewards.Grant("xplevel:" + l, LevelReward(l));
                _ctx.Events.LevelUp(l);
                if (l == GameConfig.MaxExplorerLevel) _ctx.Events.Feature(Features.Mastery);
            }
            for (int m = before.Mastery + 1; m <= after.Mastery; m++)
            {
                _ctx.Rewards.Grant("mastery:" + m, MasteryService.RewardFor(m));
                _ctx.Events.MasteryUp(m);
            }
            _ctx.Events.MarkDirty();
        }

        /// <summary>Seviye ödülleri: küçük altın, her 5'te kristal, her 10'da şapka, unvan seviyelerinde çerçeve. XP içermez.</summary>
        public static RewardBundle LevelReward(int level)
        {
            var r = RewardBundle.Of(gold: 20 + 2 * level, crystals: level % 5 == 0 ? 2 : 0);
            if (level % 10 == 0) r = r.WithCosmetic("lvl_hat_" + level);
            if (level == 10 || level == 25 || level == 40 || level == 60 || level == 80 || level == 100) r = r.WithCosmetic("lvl_frame_" + level);
            return r;
        }
    }

    public static class MasteryService
    {
        /// <summary>Ustalık ödülü döngüsü; Match-3 gücü vermez, yalnız kozmetik/rozet ve küçük altın.</summary>
        public static RewardBundle RewardFor(int m)
        {
            var r = RewardBundle.Of(gold: 50);
            switch (m % 5)
            {
                case 1: r = r.WithCosmetic("mastery_outfitcolor_" + m); break;
                case 2: r = r.WithCosmetic("mastery_bagcolor_" + m); break;
                case 3: r = r.WithCosmetic("mastery_decor_" + m); break;
                case 4: r = r.WithBadge(Content.BadgeCatalog.Mastery(m)); break;
                default: r = r.WithCosmetic("mastery_frame_" + m); break;
            }
            if (m == 10) r = r.WithCosmetic(Content.CosmeticCatalog.MasteryOutfit);
            return r;
        }
    }
}
