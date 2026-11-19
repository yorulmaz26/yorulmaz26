using System.Linq;
using NUnit.Framework;
using WorldWonders.Core;
using WorldWonders.Core.Match3;
using WorldWonders.Core.Minigames;

namespace WorldWonders.Tests
{
    public class Match3Tests
    {
        private static Match3Board Board7(string[] rows, string[] layout = null)
        {
            var def = new LevelDefinition
            {
                Id = 1, Width = 7, Height = 7, Colors = 6, Moves = 20, Seed = 42,
                Layout = layout ?? Enumerable.Repeat(".......", 7).ToArray(),
                Goals = { new GoalDefinition { Type = GoalType.Score, Count = 1 } }, Star2MovesLeft = 1, Star3MovesLeft = 2
            };
            var b = Match3Board.FromDefinition(def);
            b.SetTilesForTest(rows);
            return b;
        }

        [Test]
        public void InitialBoardHasNoMatchesAndHasMove()
        {
            for (ulong seed = 1; seed < 60; seed++)
            {
                var def = LevelGenerator.Generate(0, (int)(seed % 60) + 1);
                var b = Match3Board.FromDefinition(def, seed);
                Assert.AreEqual(0, b.FindMatches().Count, "seed " + seed);
                Assert.IsTrue(b.HasPossibleMove(), "seed " + seed);
            }
        }

        [Test]
        public void InvalidSwapIsReverted()
        {
            var b = Board7(new[]
            {
                "0123450",
                "1234501",
                "2345012",
                "3450123",
                "4501234",
                "5012345",
                "0123450",
            });
            string before = b.Dump();
            var r = b.TrySwap(new Pos(0, 0), new Pos(1, 0));
            Assert.IsFalse(r.Valid);
            Assert.AreEqual(before, b.Dump());
        }

        [Test]
        public void SimpleMatchClearsAndRefills()
        {
            var b = Board7(new[]
            {
                "0012345",
                "2340123",
                "3451234",
                "4512340",
                "5123401",
                "1234012",
                "2340123",
            });
            // (2,1)=4 ile (2,0)=1 değiştir → satır 0: 0 0 4? Hayır: (2,1)'i 0 yapalım
            b.SetTilesForTest(new[]
            {
                "0013345",
                "2304123",
                "3451234",
                "4512340",
                "5123401",
                "1234012",
                "2340123",
            });
            var r = b.TrySwap(new Pos(2, 0), new Pos(2, 1));
            Assert.IsTrue(r.Valid);
            Assert.GreaterOrEqual(r.Steps[0].Cleared.Count, 3);
            Assert.GreaterOrEqual(b.ClearedByColor[0], 3);
            for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++) Assert.IsNotNull(b.Tiles[x, y], "boş hücre kalmamalı");
            Assert.AreEqual(0, b.FindMatches().Count, "zincirleme çözülmüş olmalı");
        }

        [Test]
        public void FourInRowCreatesArrow_FiveCreatesOrb_TLCreatesBomb()
        {
            var b = Board7(new[]
            {
                "0012345",
                "2304123",
                "1451234",
                "4512340",
                "5123401",
                "1234512",
                "2345123",
            });
            // satır 0: 0 0 1 2 ... (2,1)=0 → (2,0) ile değiştir: 0 0 0 + (3,0)? 4'lü için (3,0)=0 olsun
            b.SetTilesForTest(new[]
            {
                "0010345",
                "2304123",
                "1451234",
                "4512340",
                "5123401",
                "1234512",
                "2345123",
            });
            var groups = b.PreviewSwap(new Pos(2, 0), new Pos(2, 1));
            Assert.AreEqual(1, groups.Count(g => g.Color == TileColor.GoldCoin));
            Assert.AreEqual(SpecialType.ArrowRow, groups.First(g => g.Color == TileColor.GoldCoin).SpecialToCreate);

            b.SetTilesForTest(new[]
            {
                "0010045",
                "2304123",
                "1451234",
                "4512340",
                "5123401",
                "1234512",
                "2345123",
            });
            groups = b.PreviewSwap(new Pos(2, 0), new Pos(2, 1));
            Assert.AreEqual(SpecialType.TimeOrb, groups.First(g => g.Color == TileColor.GoldCoin).SpecialToCreate);

            // L şekli: sütun 0'da 0,0 ve satır 2'de 0 0 → (0,2) köşe
            b.SetTilesForTest(new[]
            {
                "0123451",
                "0234512",
                "1004523",
                "0345134",
                "4512345",
                "5123451",
                "1234512",
            });
            groups = b.PreviewSwap(new Pos(0, 2), new Pos(0, 3));
            var gg = groups.First(g => g.Color == TileColor.GoldCoin);
            Assert.AreEqual(SpecialType.Bomb, gg.SpecialToCreate);
            var r = b.TrySwap(new Pos(0, 2), new Pos(0, 3));
            Assert.IsTrue(r.Valid);
            Assert.IsTrue(r.Steps[0].Created.Any(c => c.Special == SpecialType.Bomb));
        }

        [Test]
        public void ArrowClearsRow()
        {
            // (3,3) satır oku (renk 1 → 'B'); dikey eşleşmeye dahil et
            var b = Board7(new[]
            {
                "0123450",
                "2345012",
                "4501214",
                "3452B23",
                "5012345",
                "1234501",
                "3450123",
            });
            // sütun 4: (4,1)=0? değiştirelim: (4,2)=1,(4,3)=B(1),(4,4)=3 → (4,4) yerine 1 getir: (5,4)=4... basit yol: çekiç ile tetikle
            int before = b.ClearedByColor.Sum();
            var r = b.ApplyHammer(new Pos(4, 3));
            Assert.IsTrue(r.Valid);
            Assert.IsTrue(r.Steps[0].Fired.Any(f => f.Special == SpecialType.ArrowRow));
            Assert.GreaterOrEqual(r.Steps[0].Cleared.Count, 7, "bütün satır temizlenmeli");
        }

        [Test]
        public void OrbSwapClearsColor()
        {
            var b = Board7(new[]
            {
                "Z123450",
                "2345012",
                "4501234",
                "3450123",
                "5012345",
                "1234501",
                "3450123",
            });
            int ones = 0;
            for (int y = 0; y < 7; y++) for (int x = 0; x < 7; x++) if (b.Tiles[x, y].Color == TileColor.Scroll && b.Tiles[x, y].Special == SpecialType.None) ones++;
            var r = b.TrySwap(new Pos(0, 0), new Pos(1, 0));
            Assert.IsTrue(r.Valid);
            Assert.GreaterOrEqual(r.Steps[0].Cleared.Count(c => c.Color == TileColor.Scroll), ones);
        }

        [Test]
        public void SpecialComboIsValidMove()
        {
            var b = Board7(new[]
            {
                "AM23450",
                "2345012",
                "4501234",
                "3450123",
                "5012345",
                "1234501",
                "3450123",
            });
            var r = b.TrySwap(new Pos(0, 0), new Pos(1, 0));
            Assert.IsTrue(r.Valid);
            Assert.GreaterOrEqual(r.Steps[0].Fired.Count(f => f.Special != SpecialType.None), 2, "iki özel taş birlikte patlamalı");
        }

        [Test]
        public void IrregularLayoutHolesNeverGetTiles()
        {
            var layout = new[] { "x.....x", ".......", "...x...", ".......", ".......", ".......", "x.....x" };
            var def = new LevelDefinition { Id = 2, Width = 7, Height = 7, Colors = 5, Moves = 20, Seed = 7, Layout = layout, Goals = { new GoalDefinition { Type = GoalType.Score, Count = 999999 } }, Star2MovesLeft = 1, Star3MovesLeft = 2 };
            var g = new Match3Game(def);
            var rng = new Rng(3);
            for (int i = 0; i < 15 && g.State == Match3State.Playing; i++)
            {
                Assert.IsTrue(LevelBot.PickMove(g, rng, out var a, out var bb));
                Assert.IsTrue(g.Swap(a, bb).Valid);
                foreach (var p in new[] { new Pos(0, 0), new Pos(6, 0), new Pos(3, 2), new Pos(0, 6), new Pos(6, 6) })
                    Assert.IsNull(g.Board.TileAt(p));
                Assert.IsTrue(g.Board.HasPossibleMove());
            }
        }

        [Test]
        public void ObstaclesAndGoals()
        {
            var layout = new[] { ".......", ".......", ".......", "..c....", "sssssss", "..l....", "......." };
            var def = new LevelDefinition
            {
                Id = 3, Width = 7, Height = 7, Colors = 4, Moves = 60, Seed = 11, Layout = layout,
                Goals = { new GoalDefinition { Type = GoalType.ClearSand, Count = 7 }, new GoalDefinition { Type = GoalType.BreakCrates, Count = 1 }, new GoalDefinition { Type = GoalType.ReleaseChains, Count = 1 } },
                Star2MovesLeft = 5, Star3MovesLeft = 15
            };
            var g = new Match3Game(def);
            Assert.AreEqual(7, g.Board.TotalSand());
            Assert.IsNull(g.Board.TileAt(new Pos(2, 3)), "sandık hücresinde taş yok");
            Assert.IsFalse(g.Board.CanSwapCell(new Pos(2, 5)), "zincirli taş değiştirilemez");
            g.UseBooster(BoosterType.Hammer, new Pos(2, 3));
            Assert.AreEqual(1, g.Board.CratesBroken);
            g.UseBooster(BoosterType.Hammer, new Pos(2, 5));
            Assert.AreEqual(1, g.Board.ChainsReleased);
            g.UseBooster(BoosterType.AncientArrow, new Pos(3, 4));
            Assert.AreEqual(7, g.Board.SandCleared);
            Assert.AreEqual(Match3State.Won, g.State);
            Assert.AreEqual(60, g.MovesLeft, "güçlendirici hamle harcamaz");
            Assert.AreEqual(3, g.Stars);
        }

        [Test]
        public void LoseWhenOutOfMoves()
        {
            var def = new LevelDefinition { Id = 4, Width = 7, Height = 7, Colors = 6, Moves = 2, Seed = 5, Layout = Enumerable.Repeat(".......", 7).ToArray(), Goals = { new GoalDefinition { Type = GoalType.Score, Count = 10_000_000 } }, Star2MovesLeft = 1, Star3MovesLeft = 1 };
            var g = new Match3Game(def);
            var rng = new Rng(1);
            while (g.State == Match3State.Playing) { LevelBot.PickMove(g, rng, out var a, out var b); g.Swap(a, b); }
            Assert.AreEqual(Match3State.Lost, g.State);
            Assert.AreEqual(0, g.Stars);
        }

        [Test]
        public void ShuffleKeepsTilesAndLeavesMove()
        {
            var b = Board7(new[]
            {
                "0123450",
                "2345012",
                "4501234",
                "3450123",
                "5012345",
                "1234501",
                "3450123",
            });
            var ids = Enumerable.Range(0, 49).Select(i => b.Tiles[i % 7, i / 7].Id).OrderBy(x => x).ToArray();
            b.ShuffleBoard(null);
            var ids2 = Enumerable.Range(0, 49).Select(i => b.Tiles[i % 7, i / 7].Id).OrderBy(x => x).ToArray();
            CollectionAssert.AreEqual(ids, ids2);
            Assert.AreEqual(0, b.FindMatches().Count);
            Assert.IsTrue(b.HasPossibleMove());
        }
    }

    public class LevelDataTests
    {
        [Test]
        public void GeneratedLevelsAreValidAndVaried()
        {
            var layouts = new System.Collections.Generic.HashSet<string>();
            for (int w = 0; w < 7; w++)
                for (int l = 1; l <= 60; l++)
                {
                    var d = LevelGenerator.Generate(w, l);
                    d.Validate();
                    Assert.AreEqual(GameConfig.DifficultyForLocalLevel(l), d.Difficulty);
                    layouts.Add(string.Join("|", d.Layout) + d.Colors + string.Join(",", d.Goals.Select(g => g.Type + ":" + g.Color + ":" + g.Count)));
                }
            Assert.AreEqual(420, layouts.Count, "420 bölümün hepsi farklı olmalı");
        }

        [Test]
        public void BalancerProducesWinnableLevels()
        {
            foreach (var id in new[] { 1, 20, 77, 250, 420 })
            {
                var d = LevelGenerator.Generate(GameConfig.WonderOfLevel(id), GameConfig.LocalLevel(id));
                LevelBalancer.Balance(d, 6);
                d.Validate();
                Assert.Greater(d.BotWinRate, 0f, "bölüm " + id + " bot tarafından en az bir kez kazanılmalı");
            }
        }
    }

    public class MinigameTests
    {
        [Test]
        public void MagnifierFindsSymbolsWithDwell()
        {
            var g = new MagnifierGame(5, 1f, 9);
            Assert.AreEqual(5, g.Symbols.Count);
            foreach (var s in g.Symbols.ToList())
            {
                g.Update(s.Pos, 0.2f);
                Assert.IsFalse(s.Found);
                g.Update(s.Pos, 0.2f);
                Assert.IsTrue(s.Found);
            }
            Assert.IsTrue(g.IsComplete);
            Assert.Greater(new MagnifierGame(5, 1.4f, 9).LensRadius, g.LensRadius);
        }

        [Test]
        public void BrushCleansAndUpgradeHelps()
        {
            int Strokes(float mult)
            {
                var b = new BrushGame(80, mult, 3);
                int n = 0;
                while (!b.IsComplete && n < 2000)
                {
                    for (float y = 0.02f; y < 1f; y += 0.06f) { b.Stroke(new Vec2(0, y), new Vec2(1, y)); n++; if (b.IsComplete) break; }
                }
                return n;
            }
            int s1 = Strokes(1f), s3 = Strokes(1.4f);
            Assert.Less(s1, 2000);
            Assert.Less(s3, s1);
        }

        [Test]
        public void TrowelRevealsFinds()
        {
            var t = new TrowelGame(6, 1f, 4);
            int taps = 0;
            for (int y = 0; y < TrowelGame.Rows; y++) for (int x = 0; x < TrowelGame.Cols; x++) while (t.Plots[x, y].Layers > 0) { t.Dig(x, y); taps++; }
            Assert.IsTrue(t.IsComplete);
            Assert.AreEqual(6, t.ResultValue);
            var t3 = new TrowelGame(6, 1.4f, 4);
            int taps3 = 0;
            for (int y = 0; y < TrowelGame.Rows; y++) for (int x = 0; x < TrowelGame.Cols; x++) while (t3.Plots[x, y].Layers > 0) { t3.Dig(x, y); taps3++; }
            Assert.Less(taps3, taps);
        }
    }
}
