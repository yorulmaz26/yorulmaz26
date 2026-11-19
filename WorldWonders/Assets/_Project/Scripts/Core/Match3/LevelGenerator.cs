using System;
using System.Collections.Generic;
using System.Linq;

namespace WorldWonders.Core.Match3
{
    /// <summary>
    /// 420 bölümün veri dosyalarını üreten deterministik üretici (yalnız geliştirme aracında çalışır;
    /// oyun, üretilmiş JSON dosyalarını okur). Her bölüm farklı şekil, boyut, engel ve hedef kombinasyonu alır;
    /// hamle sayısı ve yıldız eşikleri LevelBot simülasyonlarıyla ayarlanır.
    /// </summary>
    public static class LevelGenerator
    {
        public static readonly string[] ShapeNames =
        {
            "Tam", "Kesik Köşe", "Elmas", "Merkez Avlu", "Haç", "Kum Saati", "İkiz Kule", "Kemer", "Simetrik Harabe", "Basamaklı Piramit", "Çerçeve", "Zikzak"
        };

        public static LevelDefinition Generate(int wonder, int local)
        {
            int id = GameConfig.GlobalLevel(wonder, local);
            var diff = GameConfig.DifficultyForLocalLevel(local);
            var rng = new Rng(Rng.Hash("ww-level-" + id));
            float t = (id - 1) / (float)(GameConfig.TotalLevels - 1); // 0..1 genel ilerleme

            // Boyut: başta 7-8, ilerledikçe 9x9 da gelir.
            int[] sizes = t < 0.05f ? new[] { 7, 7, 8 } : t < 0.3f ? new[] { 7, 8, 8, 9 } : new[] { 7, 8, 9, 9 };
            int w = sizes[rng.Next(sizes.Length)];
            int h = sizes[rng.Next(sizes.Length)];
            if (local == 1 && wonder == 0) { w = 7; h = 7; }

            // Şekil ailesi: ardışık bölümlerde tekrar etmesin.
            int shape = (int)((Rng.Hash("shape" + id) + (ulong)local * 7) % (ulong)ShapeNames.Length);
            if (wonder == 0 && local <= 3) shape = local == 1 ? 0 : 1;
            var mask = BuildMask(shape, w, h, rng);

            var def = new LevelDefinition
            {
                Id = id, Wonder = wonder, Local = local, Difficulty = diff, Width = w, Height = h,
                Seed = Rng.Hash("ww-board-" + id), ShapeName = ShapeNames[shape]
            };
            def.Colors = (wonder == 0 && local <= 12) || diff == Difficulty.Easy && t < 0.25f ? 5 : 6;

            // Engeller
            var grid = new char[w, h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid[x, y] = mask[x, y] ? '.' : 'x';
            int playable = 0; foreach (var b in mask) if (b) playable++;

            int dIdx = (int)diff;
            bool sandOn = !(wonder == 0 && local < 3);
            bool crateOn = !(wonder == 0 && local < 11);
            bool chainOn = !(wonder == 0 && local < 21);
            int variant = rng.Next(6);
            // Bölüm "teması": hangi engeller bu bölümde var
            bool useSand = sandOn && (variant == 0 || variant == 1 || variant == 4 || dIdx >= 2);
            bool useCrate = crateOn && (variant == 1 || variant == 2 || variant == 5 || dIdx >= 3);
            bool useChain = chainOn && (variant == 3 || variant == 5 || (dIdx >= 2 && variant == 4));

            float density = 0.10f + 0.06f * dIdx + 0.10f * t;
            if (useSand) PlacePattern(grid, w, h, rng, (int)(playable * (density + 0.08f)), dIdx >= 1 && t > 0.15f ? 'S' : 's', 's', true);
            if (useCrate) PlacePattern(grid, w, h, rng, Math.Max(2, (int)(playable * density * 0.55f)), t > 0.35f && dIdx >= 1 ? 'C' : 'c', 'c', false);
            if (useChain) PlacePattern(grid, w, h, rng, Math.Max(2, (int)(playable * density * 0.5f)), 'l', 'l', false);

            def.Layout = new string[h];
            for (int y = 0; y < h; y++)
            {
                var row = new char[w];
                for (int x = 0; x < w; x++) row[x] = grid[x, y];
                def.Layout[y] = new string(row);
            }

            // Hedefler
            int sandLayers = 0, crates = 0, chains = 0;
            foreach (var c in grid) { if (c == 's') sandLayers++; if (c == 'S') sandLayers += 2; if (c == 'c' || c == 'C') crates++; if (c == 'l') chains++; }
            if (sandLayers > 0) def.Goals.Add(new GoalDefinition { Type = GoalType.ClearSand, Count = sandLayers });
            if (crates > 0) def.Goals.Add(new GoalDefinition { Type = GoalType.BreakCrates, Count = crates });
            if (chains > 0) def.Goals.Add(new GoalDefinition { Type = GoalType.ReleaseChains, Count = chains });

            int collectBase = 14 + dIdx * 5 + (int)(t * 14);
            if (def.Goals.Count == 0 || (def.Goals.Count < 3 && rng.Chance(0.55)))
            {
                if (rng.Chance(0.12) && def.Goals.Count == 0)
                {
                    def.Goals.Add(new GoalDefinition { Type = GoalType.Score, Count = 2500 + dIdx * 1500 + (int)(t * 4000) });
                }
                else
                {
                    int colorsWanted = def.Goals.Count == 0 ? 1 + rng.Next(dIdx >= 2 ? 3 : 2) : 1;
                    var picks = Enumerable.Range(0, def.Colors).ToArray();
                    rng.Shuffle(picks);
                    for (int i = 0; i < colorsWanted && def.Goals.Count < 3; i++)
                        def.Goals.Add(new GoalDefinition { Type = GoalType.Collect, Color = (TileColor)picks[i], Count = Math.Max(8, collectBase - i * 3) });
                }
            }
            def.Moves = 30; // geçici; LevelBalancer ayarlar
            def.Star2MovesLeft = 3; def.Star3MovesLeft = 7;
            return def;
        }

        // ---------------------------------------------------------------- Şekiller
        private static bool[,] BuildMask(int shape, int w, int h, Rng rng)
        {
            var m = new bool[w, h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) m[x, y] = true;
            int cx = (w - 1) / 2, cy = (h - 1) / 2;
            switch (shape)
            {
                case 0: break;
                case 1: // kesik köşe
                    Off(m, 0, 0); Off(m, w - 1, 0); Off(m, 0, h - 1); Off(m, w - 1, h - 1);
                    break;
                case 2: // elmas
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                        {
                            int dx = Math.Min(x, w - 1 - x), dy = Math.Min(y, h - 1 - y);
                            if (dx + dy < 2) m[x, y] = false;
                        }
                    break;
                case 3: // merkez avlu
                    Off(m, cx, cy); if (w % 2 == 0) Off(m, cx + 1, cy); if (h % 2 == 0) Off(m, cx, cy + 1); if (w % 2 == 0 && h % 2 == 0) Off(m, cx + 1, cy + 1);
                    break;
                case 4: // haç
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) { Off(m, x, y); Off(m, w - 1 - x, y); Off(m, x, h - 1 - y); Off(m, w - 1 - x, h - 1 - y); }
                    break;
                case 5: // kum saati
                    for (int y = 0; y < h; y++)
                    {
                        int dy = Math.Abs(y - cy);
                        int cut = Math.Max(0, 2 - dy);
                        for (int k = 0; k < cut; k++) { Off(m, k, y); Off(m, w - 1 - k, y); }
                    }
                    break;
                case 6: // ikiz kule: ortada üstten boşluk
                    for (int y = 0; y < h / 2 - 1; y++) { Off(m, cx, y); if (w % 2 == 0) Off(m, cx + 1, y); }
                    break;
                case 7: // kemer: altta ortada kapı
                    for (int y = h - 2; y < h; y++) { Off(m, cx, y); if (w % 2 == 0) Off(m, cx + 1, y); }
                    Off(m, 0, 0); Off(m, w - 1, 0);
                    break;
                case 8: // simetrik harabe: rastgele ama aynalı boşluklar
                    int holes = 2 + rng.Next(3);
                    for (int i = 0; i < holes; i++)
                    {
                        int x = rng.Next(w / 2), y = 1 + rng.Next(h - 2);
                        Off(m, x, y); Off(m, w - 1 - x, y);
                    }
                    break;
                case 9: // basamaklı piramit: üst satırlar dar
                    for (int y = 0; y < 3; y++) for (int k = 0; k < 3 - y; k++) { Off(m, k, y); Off(m, w - 1 - k, y); }
                    break;
                case 10: // çerçeve: içte halka boşlukları
                    Off(m, 2, 2); Off(m, w - 3, 2); Off(m, 2, h - 3); Off(m, w - 3, h - 3);
                    break;
                case 11: // zikzak kenarlar
                    for (int y = 0; y < h; y += 2) { Off(m, 0, y); Off(m, w - 1, h - 1 - y); }
                    break;
            }
            return m;
        }

        private static void Off(bool[,] m, int x, int y)
        {
            if (x >= 0 && y >= 0 && x < m.GetLength(0) && y < m.GetLength(1)) m[x, y] = false;
        }

        /// <summary>Engelleri simetrik desenle yerleştirir. Sandık/zincir en üst satıra konmaz.</summary>
        private static void PlacePattern(char[,] g, int w, int h, Rng rng, int count, char strong, char weak, bool allowTop)
        {
            int placed = 0, guard = 0;
            int pattern = rng.Next(3);
            while (placed < count && guard++ < 500)
            {
                int x, y;
                switch (pattern)
                {
                    case 0: x = rng.Next(w); y = (allowTop ? 0 : 1) + rng.Next(h - (allowTop ? 0 : 1)); break;                 // dağınık
                    case 1: y = h - 1 - rng.Next(Math.Max(1, h / 2)); x = rng.Next(w); break;                                  // alt yarı
                    default: x = rng.Next(w); y = (h / 2) - 1 + rng.Next(3); break;                                          // orta bant
                }
                if (!allowTop && y == 0) continue;
                if (g[x, y] != '.') continue;
                g[x, y] = placed % 3 == 0 ? strong : weak; placed++;
                int mx = w - 1 - x;
                if (placed < count && mx != x && g[mx, y] == '.') { g[mx, y] = placed % 3 == 0 ? strong : weak; placed++; }
            }
        }
    }

    /// <summary>
    /// Açgözlü denge botu. İnsan oyuncudan zayıf oynar; bu yüzden bulduğu hamle sayısı muhafazakâr bir üst sınırdır.
    /// </summary>
    public static class LevelBot
    {
        /// <summary>Bölümü hamle sınırı olmadan oynar, hedefe kaç hamlede ulaştığını döndürür (ulaşamazsa maxMoves+1).</summary>
        public static int MovesToWin(LevelDefinition def, ulong seed, int maxMoves = 120)
        {
            var copy = Clone(def); copy.Moves = maxMoves;
            var game = new Match3Game(copy, seed);
            var rng = new Rng(seed ^ 0xABCDEFUL);
            while (game.State == Match3State.Playing)
            {
                Pos a, b;
                if (!PickMove(game, rng, out a, out b)) break;
                game.Swap(a, b);
            }
            return game.State == Match3State.Won ? game.MovesMade : maxMoves + 1;
        }

        public static bool PickMove(Match3Game game, Rng rng, out Pos bestA, out Pos bestB)
        {
            var board = game.Board;
            var swaps = board.AllValidSwaps();
            bestA = bestB = default(Pos);
            if (swaps.Count == 0) return false;
            double best = double.MinValue;
            var wantColor = new bool[6];
            bool wantSand = false, wantCrate = false, wantChain = false;
            foreach (var g in game.Goals)
            {
                if (g.Done) continue;
                if (g.Def.Type == GoalType.Collect) wantColor[(int)g.Def.Color] = true;
                if (g.Def.Type == GoalType.ClearSand) wantSand = true;
                if (g.Def.Type == GoalType.BreakCrates) wantCrate = true;
                if (g.Def.Type == GoalType.ReleaseChains) wantChain = true;
            }
            foreach (var s in swaps)
            {
                double score = rng.NextDouble() * 0.5;
                var ta = board.TileAt(s.Key); var tb = board.TileAt(s.Value);
                if (ta.Special != SpecialType.None && tb.Special != SpecialType.None) score += 30;
                else if (ta.Special == SpecialType.TimeOrb || tb.Special == SpecialType.TimeOrb) score += 22;
                foreach (var g in board.PreviewSwap(s.Key, s.Value))
                {
                    score += g.Cells.Count;
                    var sp = g.SpecialToCreate;
                    if (sp == SpecialType.TimeOrb) score += 14; else if (sp == SpecialType.Bomb) score += 8; else if (sp != SpecialType.None) score += 6;
                    if ((int)g.Color >= 0 && wantColor[(int)g.Color]) score += g.Cells.Count * 2.5;
                    foreach (var p in g.Cells)
                    {
                        var c = board.Cells[p.X, p.Y];
                        if (wantSand && c.Sand > 0) score += 3;
                        if (wantChain && c.Chain) score += 4;
                        var t = board.TileAt(p);
                        if (t != null && t.Special != SpecialType.None) score += 5;
                        if (wantCrate)
                        {
                            if (IsCrate(board, p.X + 1, p.Y)) score += 2.5; if (IsCrate(board, p.X - 1, p.Y)) score += 2.5;
                            if (IsCrate(board, p.X, p.Y + 1)) score += 2.5; if (IsCrate(board, p.X, p.Y - 1)) score += 2.5;
                        }
                    }
                    // Alt satırlardaki eşleşmeler daha çok zincirleme üretir.
                    foreach (var p in g.Cells) score += p.Y * 0.05;
                }
                if (score > best) { best = score; bestA = s.Key; bestB = s.Value; }
            }
            return true;
        }

        private static bool IsCrate(Match3Board b, int x, int y)
        {
            return x >= 0 && y >= 0 && x < b.Width && y < b.Height && b.Cells[x, y].Playable && b.Cells[x, y].Crate > 0;
        }

        public static LevelDefinition Clone(LevelDefinition d)
        {
            var c = new LevelDefinition
            {
                Id = d.Id, Wonder = d.Wonder, Local = d.Local, Difficulty = d.Difficulty, Width = d.Width, Height = d.Height,
                Layout = (string[])d.Layout.Clone(), Colors = d.Colors, Moves = d.Moves, Star2MovesLeft = d.Star2MovesLeft,
                Star3MovesLeft = d.Star3MovesLeft, Seed = d.Seed, ShapeName = d.ShapeName, BotWinRate = d.BotWinRate
            };
            foreach (var g in d.Goals) c.Goals.Add(new GoalDefinition { Type = g.Type, Color = g.Color, Count = g.Count });
            return c;
        }
    }

    public static class LevelBalancer
    {
        /// <summary>Hamle sayısını ve yıldız eşiklerini bot simülasyonlarıyla ayarlar.</summary>
        public static void Balance(LevelDefinition def, int runs = 10)
        {
            int[] needed = null;
            for (int iter = 0; iter < 5; iter++)
            {
                needed = new int[runs];
                for (int r = 0; r < runs; r++) needed[r] = LevelBot.MovesToWin(def, Rng.Hash("bal" + def.Id + ":" + iter + ":" + r));
                Array.Sort(needed);
                int median = needed[runs / 2];
                if (median >= 16 && median <= 36) break;
                // Toplama/puan hedeflerini ölçekle (engel hedefleri sabit kalır).
                float scale = median < 16 ? 1.3f : 0.75f;
                bool scalable = false;
                foreach (var g in def.Goals)
                    if (g.Type == GoalType.Collect || g.Type == GoalType.Score) { g.Count = Math.Max(6, (int)(g.Count * scale)); scalable = true; }
                if (!scalable)
                {
                    if (median > 36) { def.Colors = Math.Max(4, def.Colors - 1); }
                    else break;
                }
            }
            double pct; double mult; int add;
            switch (def.Difficulty)
            {
                case Difficulty.Easy: pct = 0.9; mult = 1.15; add = 3; break;
                case Difficulty.Medium: pct = 0.7; mult = 1.05; add = 2; break;
                case Difficulty.Hard: pct = 0.5; mult = 1.0; add = 0; break;
                default: pct = 0.35; mult = 0.97; add = 0; break;
            }
            int idx = Math.Min(runs - 1, (int)Math.Round(pct * (runs - 1)));
            int moves = (int)Math.Ceiling(needed[idx] * mult) + add;
            def.Moves = Math.Max(12, Math.Min(60, moves));
            def.Star2MovesLeft = Math.Max(1, (int)Math.Round(def.Moves * 0.12));
            def.Star3MovesLeft = Math.Max(def.Star2MovesLeft + 1, (int)Math.Round(def.Moves * 0.28));
            int wins = 0;
            foreach (var n in needed) if (n <= def.Moves) wins++;
            def.BotWinRate = wins / (float)runs;
        }
    }
}
