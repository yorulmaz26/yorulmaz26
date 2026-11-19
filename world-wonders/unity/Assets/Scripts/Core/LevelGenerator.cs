using System;
using System.Collections.Generic;
using System.Linq;

namespace WorldWonders.Core
{
    public enum GoalType { Color, Score, Block, Layer, Relic, Lock }

    public sealed class Goal
    {
        public GoalType Type; public int Color = -1; public int N; public int Left;
        public Goal Clone() => new Goal { Type = Type, Color = Color, N = N, Left = Left };
    }

    public sealed class BlockCell
    {
        public string Kind; public int Hp; public int Max;
        public BlockCell Clone() => new BlockCell { Kind = Kind, Hp = Hp, Max = Max };
    }

    public readonly struct Pos : IEquatable<Pos>
    {
        public readonly int R, C;
        public Pos(int r, int c) { R = r; C = c; }
        public int Key => R * GameData.Cols + C;
        public static Pos FromKey(int k) => new Pos(k / GameData.Cols, k % GameData.Cols);
        public bool Equals(Pos o) => R == o.R && C == o.C;
        public override bool Equals(object o) => o is Pos p && Equals(p);
        public override int GetHashCode() => Key;
        public override string ToString() => $"({R},{C})";
    }

    /// <summary>Bir bölümün değişmez tanımı. Prototipteki makeLevel() çıktısıyla birebir aynıdır.</summary>
    public sealed class LevelDef
    {
        public int G, W, Li, Colors, Moves, Seed, RelicTotal;
        public string Primary, Introduces;
        public bool Sig, SigIntro, Hard;
        public List<Goal> Goals = new List<Goal>();
        public BlockCell[,] Blocks = new BlockCell[GameData.Rows, GameData.Cols];
        public int[,] Layers = new int[GameData.Rows, GameData.Cols];
        public List<Pos> Relics = new List<Pos>(), Locks = new List<Pos>(), Lotus = new List<Pos>();
        public WonderDef Wonder => GameData.Wonders[W];
    }

    /// <summary>
    /// Tohumlu prosedürel bölüm üretimi. Rastgele sayı çağrılarının SIRASI prototiple aynı
    /// olmalıdır; bu yüzden kısa devre koşulları (&&) bilinçli olarak korunmuştur.
    /// </summary>
    public static class LevelGenerator
    {
        const int R = GameData.Rows, C = GameData.Cols;

        public static LevelDef Make(int g)
        {
            int w = Math.Min(GameData.Wonders.Length - 1, (g - 1) / GameData.LevelsPerWonder);
            int li = (g - 1) % GameData.LevelsPerWonder + 1;
            var W = GameData.Wonders[w];
            var rnd = new Rng(g * 7919 + 13);
            double diff = Math.Clamp((li - 1) / (double)(GameData.LevelsPerWonder - 1), 0, 1) * .6 + w * .02;
            var L = new LevelDef { G = g, W = w, Li = li, Colors = g <= 8 ? 5 : 6, Seed = g * 31 + 7 };
            L.Moves = JsMath.Round(25 - diff * 5 + rnd.Next() * 4);

            List<int> MirrorPick(Rng rr, int n, int r0, int r1, Func<int, int, bool> avoid)
            {
                var outp = new List<int>(); int guard = 0;
                while (outp.Count < n && guard++ < 400)
                {
                    int r = r0 + (int)Math.Floor(rr.Next() * (r1 - r0 + 1)), c = (int)Math.Floor(rr.Next() * (C / 2));
                    int k1 = r * C + c, k2 = r * C + (C - 1 - c);
                    if (outp.Contains(k1) || (avoid != null && avoid(r, c))) continue;
                    outp.Add(k1); outp.Add(k2);
                }
                return outp.Take(n + (n % 2)).ToList();
            }

            var mechs = new List<string> { "color", "block" };
            if (W.Layer != null) mechs.Add("layer");
            if (W.RelicShape != null) mechs.Add("relic");
            string primary; int intro = w == 0 ? li - 2 : li - 1;
            if (g == 1) primary = "tut1"; else if (g == 2) primary = "tut2";
            else if (li == 1) primary = "color";
            else if (intro >= 1 && intro < mechs.Count) { primary = mechs[intro]; L.Introduces = primary; }
            else primary = mechs[(int)Math.Floor(rnd.Next() * mechs.Count)];

            void ColorGoal(int k, double baseN)
            {
                var cs = Enumerable.Range(0, L.Colors).ToList(); rnd.Shuffle(cs);
                foreach (var ci in cs.Take(k))
                    L.Goals.Add(new Goal { Type = GoalType.Color, Color = ci, N = JsMath.Round(baseN + diff * 14 + rnd.Next() * 4) });
            }
            int CountBlocks() { int n = 0; foreach (var b in L.Blocks) if (b != null) n++; return n; }
            int CountLayers() { int n = 0; foreach (var v in L.Layers) if (v > 0) n++; return n; }
            int PlaceBlocks(int n)
            {
                var ks = MirrorPick(rnd, n, 2, 6, null); string hard = W.Blocks.Length > 1 ? W.Blocks[1] : null;
                foreach (var k in ks)
                {
                    int r = k / C, c = k % C; string kind = W.Blocks[0];
                    if (hard != null && rnd.Next() < .15 + diff * .45) kind = hard;
                    int hp = GameData.BlockHp[kind];
                    if (hp >= 3 && rnd.Next() > diff * .8) hp = 2;
                    L.Blocks[r, c] = new BlockCell { Kind = kind, Hp = hp, Max = hp };
                }
                for (int r = 0; r < R; r++) for (int c = 0; c < C / 2; c++)
                    L.Blocks[r, C - 1 - c] = L.Blocks[r, c]?.Clone();
                return CountBlocks();
            }
            int PlaceLayers(int n)
            {
                var ks = MirrorPick(rnd, n, 1, 7, (r, c) => L.Blocks[r, c] != null); int cnt = 0;
                foreach (var k in ks)
                {
                    int r = k / C, c = k % C; if (L.Blocks[r, c] != null) continue;
                    L.Layers[r, c] = rnd.Next() < diff * .45 ? 2 : 1; cnt++;
                }
                return cnt;
            }

            switch (primary)
            {
                case "tut1": L.Moves = 20; L.Goals.Add(new Goal { Type = GoalType.Color, Color = 0, N = 12 }); L.Goals.Add(new Goal { Type = GoalType.Color, Color = 3, N = 12 }); break;
                case "tut2": L.Moves = 20; L.Goals.Add(new Goal { Type = GoalType.Score, N = 3000 }); break;
                case "color": ColorGoal(rnd.Next() < .5 ? 1 : 2, 14); if (li > 6 && rnd.Next() < .6) PlaceBlocks(4 + (int)Math.Floor(diff * 6)); break;
                case "block": { int n = PlaceBlocks(6 + (int)Math.Floor(diff * 8)); L.Goals.Add(new Goal { Type = GoalType.Block, N = n }); L.Moves += 4; if (rnd.Next() < .35) ColorGoal(1, 12); break; }
                case "layer": { if (rnd.Next() < .3) PlaceBlocks(4); int n = PlaceLayers(8 + (int)Math.Floor(diff * 12)); L.Goals.Add(new Goal { Type = GoalType.Layer, N = n }); L.Moves += 6; break; }
                case "relic":
                {
                    if (rnd.Next() < .35) PlaceBlocks(4 + (int)Math.Floor(diff * 4));
                    L.RelicTotal = 2 + (int)Math.Floor(diff * 2); L.Goals.Add(new Goal { Type = GoalType.Relic, N = L.RelicTotal }); L.Moves += 9;
                    var cols = new List<int> { 1, 2, 3, 4, 5, 6 }; rnd.Shuffle(cols);
                    foreach (var c in cols.Take(Math.Min(3, L.RelicTotal))) if (L.Blocks[0, c] == null) L.Relics.Add(new Pos(0, c));
                    break;
                }
            }
            L.Primary = primary;

            // imza mekaniği (ayrı tohum)
            var r2 = new Rng(g * 131 + 7); var SG = W.Sig;
            L.Sig = li == GameData.SigIntroLevel || (li > GameData.SigIntroLevel && r2.Next() < .45);
            L.SigIntro = li == GameData.SigIntroLevel;
            void EnsureGoal(GoalType t, int n) { var gl = L.Goals.FirstOrDefault(x => x.Type == t); if (gl != null) gl.N = n; else L.Goals.Add(new Goal { Type = t, N = n }); }
            if (L.Sig)
            {
                bool Free(int r, int c) => L.Blocks[r, c] != null || L.Relics.Any(p => p.R == r && p.C == c);
                switch (SG.Id)
                {
                    case SigId.Shield:
                        foreach (var k in MirrorPick(rnd, 2 + 2 * (int)Math.Floor(r2.Next() * 2), 2, 6, Free)) L.Blocks[k / C, k % C] = new BlockCell { Kind = "shield", Hp = 1, Max = 1 };
                        EnsureGoal(GoalType.Block, CountBlocks()); L.Moves += 7; break;
                    case SigId.Sandstorm: case SigId.Wind:
                        if (CountLayers() == 0) PlaceLayers(7);
                        EnsureGoal(GoalType.Layer, CountLayers()); L.Moves += SG.Id == SigId.Sandstorm ? 8 : 4; break;
                    case SigId.Raid:
                        if (CountBlocks() == 0) PlaceBlocks(4);
                        EnsureGoal(GoalType.Block, CountBlocks()); L.Moves += 3; break;
                    case SigId.Lock:
                        L.Locks = MirrorPick(rnd, 4 + (int)Math.Floor(diff * 4), 2, 6, Free).Select(Pos.FromKey).ToList();
                        L.Goals.Add(new Goal { Type = GoalType.Lock, N = L.Locks.Count }); L.Moves += 6; break;
                    case SigId.Lotus:
                        L.Lotus = MirrorPick(rnd, 6, 1, 7, (r, c) => L.Blocks[r, c] != null).Select(Pos.FromKey).ToList(); break;
                }
            }

            // testere dişi zorluk
            int pos = (g - 1) % GameData.LevelsPerPiece;
            L.Hard = g > GameData.LevelsPerPiece && pos == GameData.LevelsPerPiece - 1;
            if (g > GameData.LevelsPerPiece && pos == 0) L.Moves += 3;
            if (L.Hard) { L.Moves -= 2; foreach (var gl in L.Goals) if (gl.Type == GoalType.Color || gl.Type == GoalType.Score) gl.N = JsMath.Round(gl.N * 1.15); }
            if (g <= 10) L.Moves += 6 - g / 3;
            else if (L.Li <= 3) L.Moves += 2;
            L.Moves = Math.Max(14, L.Moves);
            foreach (var gl in L.Goals) gl.Left = gl.N;
            return L;
        }
    }
}
