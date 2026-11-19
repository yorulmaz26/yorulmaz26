using System;
using System.Collections.Generic;
using System.Text;

namespace WorldWonders.Core
{
    /// <summary>Bir ödül paketi. Değişmez gibi kullanılır; With* yeni kopya döndürür.</summary>
    [Serializable]
    public sealed class RewardBundle
    {
        public int Gold;
        public int Crystals;
        public int Xp;
        public Dictionary<BoosterType, int> Boosters = new Dictionary<BoosterType, int>();
        public List<string> Badges = new List<string>();
        public List<string> Cosmetics = new List<string>();

        public static RewardBundle Of(int gold = 0, int crystals = 0, int xp = 0)
        {
            return new RewardBundle { Gold = gold, Crystals = crystals, Xp = xp };
        }

        public RewardBundle Clone()
        {
            return new RewardBundle
            {
                Gold = Gold, Crystals = Crystals, Xp = Xp,
                Boosters = new Dictionary<BoosterType, int>(Boosters),
                Badges = new List<string>(Badges),
                Cosmetics = new List<string>(Cosmetics)
            };
        }

        public RewardBundle WithBooster(BoosterType b, int count)
        {
            var c = Clone();
            int cur; c.Boosters.TryGetValue(b, out cur);
            c.Boosters[b] = cur + count;
            return c;
        }

        public RewardBundle WithBadge(string id) { var c = Clone(); c.Badges.Add(id); return c; }
        public RewardBundle WithCosmetic(string id) { var c = Clone(); c.Cosmetics.Add(id); return c; }
        public RewardBundle WithXp(int xp) { var c = Clone(); c.Xp += xp; return c; }

        public void Add(RewardBundle o)
        {
            if (o == null) return;
            Gold += o.Gold; Crystals += o.Crystals; Xp += o.Xp;
            foreach (var kv in o.Boosters) { int cur; Boosters.TryGetValue(kv.Key, out cur); Boosters[kv.Key] = cur + kv.Value; }
            Badges.AddRange(o.Badges);
            Cosmetics.AddRange(o.Cosmetics);
        }

        public bool IsEmpty
        {
            get { return Gold == 0 && Crystals == 0 && Xp == 0 && Boosters.Count == 0 && Badges.Count == 0 && Cosmetics.Count == 0; }
        }

        public override string ToString()
        {
            var sb = new StringBuilder();
            if (Gold != 0) sb.Append(Gold).Append(" altın ");
            if (Crystals != 0) sb.Append(Crystals).Append(" kristal ");
            if (Xp != 0) sb.Append(Xp).Append(" XP ");
            foreach (var kv in Boosters) sb.Append(kv.Value).Append("× ").Append(Content.Names.Booster(kv.Key)).Append(' ');
            foreach (var b in Badges) sb.Append("rozet:").Append(b).Append(' ');
            foreach (var c in Cosmetics) sb.Append("kozmetik:").Append(c).Append(' ');
            return sb.ToString().Trim();
        }
    }
}
