using System;
using System.Collections.Generic;
using System.Linq;

namespace WorldWonders.Core
{
    public enum GemKind { Gem, Disk, Relic }

    public sealed class Gem
    {
        public int Id; public GemKind Kind; public int Color = -1;
        /// <summary>'\0' yok, 'h' satır, 'v' sütun, 'b' Tunç Bomba</summary>
        public char Sp;
        public bool Lock; public string Shape;
        internal bool Fired;
        public bool IsSpecial => Kind == GemKind.Disk || (Kind == GemKind.Gem && Sp != '\0');
    }

    public enum BoardEventType { Swap, InvalidSwap, Match, SpecialFired, Cleared, BlockHit, BlockBroken, SpecialCreated, Fall, Spawn, RelicCollected, Reshuffle, Sig, Score, LotusBonus, Win, Lose }

    /// <summary>Görünüm katmanının (Unity) canlandıracağı olaylar. Çekirdek animasyon bilmez.</summary>
    public readonly struct BoardEvent
    {
        public readonly BoardEventType Type; public readonly Pos A, B; public readonly int Value, Depth; public readonly int GemId;
        public BoardEvent(BoardEventType t, Pos a = default, Pos b = default, int value = 0, int depth = 0, int gemId = 0) { Type = t; A = a; B = b; Value = value; Depth = depth; GemId = gemId; }
        public override string ToString() => $"{Type} {A} {B} v={Value} d={Depth}";
    }

    /// <summary>
    /// World Wonders Match-3 çekirdeği. Saf C#, UnityEngine bağımlılığı yok.
    /// Web prototipindeki motorla aynı kuralları ve aynı rastgele sayı sırasını kullanır
    /// (bkz. Tests/CoreTests: 420 bölüm + hamle hamle karşılaştırma).
    /// </summary>
    public sealed class Board
    {
        const int R = GameData.Rows, C = GameData.Cols;
        public readonly LevelDef L; public readonly WonderDef W;
        public readonly Gem[,] Grid = new Gem[R, C];
        public readonly BlockCell[,] Blocks = new BlockCell[R, C];
        public readonly int[,] Layers = new int[R, C];
        public readonly bool[,] Lotus = new bool[R, C];
        public readonly List<Goal> Goals;
        public int Moves, Score, Combo, Turn, Raids, RelicSpawned, RelicCollected, LayerCap;
        public bool Over, Won;
        public readonly List<BoardEvent> Events = new List<BoardEvent>();
        readonly Rng rnd; int gid = 1;

        public Board(LevelDef level)
        {
            L = level; W = level.Wonder; rnd = new Rng(level.Seed);
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) { Blocks[r, c] = level.Blocks[r, c]?.Clone(); Layers[r, c] = level.Layers[r, c]; }
            Goals = level.Goals.Select(g => { var x = g.Clone(); x.Left = x.N; return x; }).ToList();
            Moves = level.Moves;
            foreach (var p in level.Lotus) Lotus[p.R, p.C] = true;
            LayerCap = CountLayers();
            FillInitial();
        }

        // ------------------------------------------------------------ yardımcılar
        static bool InB(int r, int c) => r >= 0 && r < R && c >= 0 && c < C;
        int RandColor() => (int)Math.Floor(rnd.Next() * L.Colors);
        Gem NewGem(int color, GemKind kind = GemKind.Gem, char sp = '\0') => new Gem { Id = gid++, Kind = kind, Color = color, Sp = sp };
        bool SameCol(int r, int c, int col) { var g = Grid[r, c]; return g != null && g.Kind == GemKind.Gem && g.Color == col; }
        int CountLayers() { int n = 0; foreach (var v in Layers) if (v > 0) n++; return n; }
        void AddScore(int n) { Score += n; Events.Add(new BoardEvent(BoardEventType.Score, value: n)); }
        void GoalTick(GoalType t, int n, int color = -1)
        {
            foreach (var gl in Goals) if (gl.Type == t && (t != GoalType.Color || gl.Color == color) && gl.Left > 0) gl.Left = Math.Max(0, gl.Left - n);
        }
        void SyncLayerGoal() { int n = CountLayers(); foreach (var gl in Goals) if (gl.Type == GoalType.Layer) gl.Left = n; }
        public bool GoalsDone() => Goals.All(gl => gl.Type == GoalType.Score ? Score >= gl.N : gl.Left <= 0);
        bool IsFixed(int r, int c) => Blocks[r, c] != null || (Grid[r, c] != null && Grid[r, c].Lock);

        // ------------------------------------------------------------ başlangıç
        void FillInitial()
        {
            int tries = 0;
            do
            {
                for (int r = 0; r < R; r++) for (int c = 0; c < C; c++)
                {
                    Grid[r, c] = null; if (Blocks[r, c] != null) continue;
                    int col, guard = 0;
                    do { col = RandColor(); guard++; }
                    while (guard < 20 && ((c >= 2 && SameCol(r, c - 1, col) && SameCol(r, c - 2, col)) || (r >= 2 && SameCol(r - 1, c, col) && SameCol(r - 2, c, col))));
                    Grid[r, c] = NewGem(col);
                }
                RelicSpawned = 0;
                foreach (var p in L.Relics) { Grid[p.R, p.C] = new Gem { Id = gid++, Kind = GemKind.Relic, Shape = W.RelicShape }; RelicSpawned++; }
                foreach (var p in L.Locks) { var gm = Grid[p.R, p.C]; if (gm != null && gm.Kind == GemKind.Gem) gm.Lock = true; }
            } while ((FindGroups().Count > 0 || FindMove() == null) && ++tries < 50);
        }

        // ------------------------------------------------------------ eşleşme bulma
        public sealed class Run { public char Dir; public int Color; public List<Pos> Cells = new List<Pos>(); }
        public sealed class Group
        {
            public List<Run> Runs = new List<Run>(); public int Color;
            public List<Pos> Cells = new List<Pos>(); public HashSet<int> Keys = new HashSet<int>();
            public void AddCell(Pos p) { if (Keys.Add(p.Key)) Cells.Add(p); }
        }

        public List<Group> FindGroups()
        {
            var runs = new List<Run>();
            int M(int r, int c) { var g = Grid[r, c]; return g != null && g.Kind == GemKind.Gem ? g.Color : -1; }
            for (int r = 0; r < R; r++) { int c = 0; while (c < C) { int col = M(r, c); if (col < 0) { c++; continue; } int n = 1; while (c + n < C && M(r, c + n) == col) n++; if (n >= 3) { var run = new Run { Dir = 'h', Color = col }; for (int i = 0; i < n; i++) run.Cells.Add(new Pos(r, c + i)); runs.Add(run); } c += n; } }
            for (int c = 0; c < C; c++) { int r = 0; while (r < R) { int col = M(r, c); if (col < 0) { r++; continue; } int n = 1; while (r + n < R && M(r + n, c) == col) n++; if (n >= 3) { var run = new Run { Dir = 'v', Color = col }; for (int i = 0; i < n; i++) run.Cells.Add(new Pos(r + i, c)); runs.Add(run); } r += n; } }
            var parent = Enumerable.Range(0, runs.Count).ToArray();
            int Find(int i) => parent[i] == i ? i : (parent[i] = Find(parent[i]));
            var owner = new Dictionary<int, int>();
            for (int i = 0; i < runs.Count; i++) foreach (var p in runs[i].Cells) { if (owner.TryGetValue(p.Key, out var o)) parent[Find(i)] = Find(o); else owner[p.Key] = i; }
            var byRoot = new Dictionary<int, Group>(); var order = new List<Group>();
            for (int i = 0; i < runs.Count; i++)
            {
                int root = Find(i);
                if (!byRoot.TryGetValue(root, out var gp)) { gp = new Group { Color = runs[i].Color }; byRoot[root] = gp; order.Add(gp); }
                gp.Runs.Add(runs[i]); foreach (var p in runs[i].Cells) gp.AddCell(p);
            }
            return order;
        }

        bool MatchAt(int r, int c)
        {
            var g = Grid[r, c]; if (g == null || g.Kind != GemKind.Gem) return false; int col = g.Color;
            int h = 1, x = c - 1; while (x >= 0 && SameCol(r, x, col)) { h++; x--; } x = c + 1; while (x < C && SameCol(r, x, col)) { h++; x++; }
            if (h >= 3) return true;
            int v = 1, y = r - 1; while (y >= 0 && SameCol(y, c, col)) { v++; y--; } y = r + 1; while (y < R && SameCol(y, c, col)) { v++; y++; }
            return v >= 3;
        }

        bool IsValidMove(int r, int c, int r2, int c2)
        {
            var a = Grid[r, c]; var b = Grid[r2, c2]; if (a == null || b == null || a.Lock || b.Lock) return false;
            if ((a.Kind == GemKind.Disk && b.Kind != GemKind.Relic) || (b.Kind == GemKind.Disk && a.Kind != GemKind.Relic)) return true;
            if (a.IsSpecial && b.IsSpecial) return true;
            Grid[r, c] = b; Grid[r2, c2] = a; bool ok = MatchAt(r, c) || MatchAt(r2, c2); Grid[r, c] = a; Grid[r2, c2] = b;
            return ok;
        }

        /// <summary>Tüm geçerli hamleler (prototiple aynı tarama sırası).</summary>
        public List<(Pos, Pos)> AllMoves()
        {
            var list = new List<(Pos, Pos)>();
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++)
            {
                if (InB(r, c + 1) && IsValidMove(r, c, r, c + 1)) list.Add((new Pos(r, c), new Pos(r, c + 1)));
                if (InB(r + 1, c) && IsValidMove(r, c, r + 1, c)) list.Add((new Pos(r, c), new Pos(r + 1, c)));
            }
            return list;
        }
        public (Pos, Pos)? FindMove()
        {
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++)
            {
                if (InB(r, c + 1) && IsValidMove(r, c, r, c + 1)) return (new Pos(r, c), new Pos(r, c + 1));
                if (InB(r + 1, c) && IsValidMove(r, c, r + 1, c)) return (new Pos(r, c), new Pos(r + 1, c));
            }
            return null;
        }

        // ------------------------------------------------------------ hamle
        /// <summary>Takas dener. Geçersizse false döner ve hamle harcanmaz. Olaylar <see cref="Events"/> listesine eklenir.</summary>
        public bool TrySwap(Pos a, Pos b)
        {
            var A = Grid[a.R, a.C]; var B = Grid[b.R, b.C];
            if (A == null || B == null || Over || Math.Abs(a.R - b.R) + Math.Abs(a.C - b.C) != 1) return false;
            if (A.Lock || B.Lock) { Events.Add(new BoardEvent(BoardEventType.InvalidSwap, a, b)); return false; }
            Combo = 0;
            Grid[a.R, a.C] = B; Grid[b.R, b.C] = A;
            Events.Add(new BoardEvent(BoardEventType.Swap, a, b));
            bool comboable = (A.Kind == GemKind.Disk && B.Kind != GemKind.Relic) || (B.Kind == GemKind.Disk && A.Kind != GemKind.Relic) || (A.IsSpecial && B.IsSpecial);
            if (comboable) { Moves--; DoCombo(A, B, b); Settle(); Resolve(null); EndTurn(); return true; }
            if (FindGroups().Count == 0)
            {
                Grid[a.R, a.C] = A; Grid[b.R, b.C] = B;
                Events.Add(new BoardEvent(BoardEventType.InvalidSwap, a, b)); return false;
            }
            Moves--;
            Resolve(new[] { a, b });
            EndTurn();
            return true;
        }

        void Resolve(Pos[] swapCells)
        {
            bool first = true;
            for (int guard = 0; guard < 60; guard++)
            {
                var groups = FindGroups(); if (groups.Count == 0) break;
                ProcessGroups(groups, first ? swapCells : null);
                first = false; Settle(); Combo++;
            }
        }

        sealed class Create { public Pos P; public int Color; public bool Disk; public char Sp; }

        void ProcessGroups(List<Group> groups, Pos[] swapCells)
        {
            var create = new List<Create>(); var matchCells = new List<Pos>();
            foreach (var gp in groups)
            {
                matchCells.AddRange(gp.Cells);
                int maxRun = gp.Runs.Max(x => x.Cells.Count);
                bool hasH = gp.Runs.Any(x => x.Dir == 'h'), hasV = gp.Runs.Any(x => x.Dir == 'v');
                bool disk = false; char sp = '\0';
                if (maxRun >= 5) disk = true;
                else if (hasH && hasV) sp = 'b';
                else if (maxRun == 4) sp = gp.Runs.First(x => x.Cells.Count == 4).Dir;
                else continue;
                Pos? pos = null;
                if (swapCells != null) foreach (var s in swapCells) if (gp.Keys.Contains(s.Key)) { pos = s; break; }
                if (pos == null && sp == 'b')
                {
                    var cnt = new Dictionary<int, int>(); var ord = new List<int>();
                    foreach (var run in gp.Runs) foreach (var p in run.Cells) { if (cnt.ContainsKey(p.Key)) cnt[p.Key]++; else { cnt[p.Key] = 1; ord.Add(p.Key); } }
                    foreach (var k in ord) if (cnt[k] > 1) { pos = Pos.FromKey(k); break; }
                }
                if (pos == null)
                {
                    var longest = gp.Runs[0]; foreach (var run in gp.Runs) if (run.Cells.Count > longest.Cells.Count) longest = run;
                    pos = longest.Cells[longest.Cells.Count / 2];
                }
                create.Add(new Create { P = pos.Value, Color = gp.Color, Disk = disk, Sp = sp });
            }
            Events.Add(new BoardEvent(BoardEventType.Match, value: matchCells.Count, depth: Combo));
            AddScore(JsMath.Round(matchCells.Count * 60 * (1 + Combo * .5)));
            ClearCells(matchCells, true, create, new HashSet<int>());
        }

        void ClearCells(List<Pos> cells, bool adjacent, List<Create> create, HashSet<int> pre)
        {
            var depth = new Dictionary<int, int>(); var depthOrder = new List<int>();
            var blockHits = new Dictionary<int, int>(); var hitOrder = new List<int>();
            var queue = new Queue<(Gem g, int r, int c, int d)>();
            void Hit(int k, int d) { if (blockHits.TryGetValue(k, out var old)) blockHits[k] = Math.Min(old, d); else { blockHits[k] = d; hitOrder.Add(k); } }
            void Add(int r, int c, int d, bool fromEffect)
            {
                if (!InB(r, c)) return; int k = r * C + c;
                if (Blocks[r, c] != null) { if (fromEffect) Hit(k, d); return; }
                var g = Grid[r, c]; if (g == null || g.Kind == GemKind.Relic) return;
                if (depth.ContainsKey(k)) return; depth[k] = d; depthOrder.Add(k);
                if (g.IsSpecial && !pre.Contains(g.Id)) queue.Enqueue((g, r, c, d));
            }
            foreach (var p in cells) Add(p.R, p.C, 0, false);
            while (queue.Count > 0)
            {
                var (g, r, c, d) = queue.Dequeue(); if (g.Fired) continue; g.Fired = true;
                int nd = d + 1;
                Events.Add(new BoardEvent(BoardEventType.SpecialFired, new Pos(r, c), value: g.Kind == GemKind.Disk ? 'd' : g.Sp, depth: d, gemId: g.Id));
                if (g.Kind == GemKind.Disk)
                {
                    int col = MostCommonColor();
                    for (int y = 0; y < R; y++) for (int x = 0; x < C; x++) { var o = Grid[y, x]; if (o != null && o.Kind == GemKind.Gem && o.Color == col) Add(y, x, nd, true); }
                }
                else if (g.Sp == 'h') { for (int x = 0; x < C; x++) Add(r, x, nd, true); }
                else if (g.Sp == 'v') { for (int y = 0; y < R; y++) Add(y, c, nd, true); }
                else if (g.Sp == 'b') { for (int y = r - 1; y <= r + 1; y++) for (int x = c - 1; x <= c + 1; x++) Add(y, x, nd, true); }
                AddScore(200);
            }
            if (adjacent)
            {
                foreach (var p in cells) foreach (var (dr, dc) in new[] { (1, 0), (-1, 0), (0, 1), (0, -1) })
                {
                    int y = p.R + dr, x = p.C + dc;
                    if (InB(y, x) && Blocks[y, x] != null && (Blocks[y, x].Kind != "shield" || create.Count > 0)) { int k = y * C + x; if (!blockHits.ContainsKey(k)) { blockHits[k] = 0; hitOrder.Add(k); } }
                }
            }
            // mantıksal temizlik
            foreach (var k in depthOrder)
            {
                int r = k / C, c = k % C; var g = Grid[r, c]; if (g == null) continue;
                if (g.Kind == GemKind.Gem) GoalTick(GoalType.Color, 1, g.Color);
                if (g.Lock) GoalTick(GoalType.Lock, 1);
                Grid[r, c] = null;
                Events.Add(new BoardEvent(BoardEventType.Cleared, new Pos(r, c), depth: depth[k], gemId: g.Id));
                if (Lotus[r, c]) { Lotus[r, c] = false; Moves++; Events.Add(new BoardEvent(BoardEventType.LotusBonus, new Pos(r, c))); }
                if (Layers[r, c] > 0) Layers[r, c]--;
            }
            SyncLayerGoal();
            foreach (var k in hitOrder)
            {
                int r = k / C, c = k % C; var b = Blocks[r, c]; if (b == null) continue;
                b.Hp--; Events.Add(new BoardEvent(BoardEventType.BlockHit, new Pos(r, c), value: b.Hp));
                if (b.Hp <= 0) { Blocks[r, c] = null; GoalTick(GoalType.Block, 1); AddScore(150); Events.Add(new BoardEvent(BoardEventType.BlockBroken, new Pos(r, c))); }
            }
            foreach (var cr in create)
            {
                if (Blocks[cr.P.R, cr.P.C] != null) continue;
                var g = cr.Disk ? NewGem(-1, GemKind.Disk) : NewGem(cr.Color, GemKind.Gem, cr.Sp);
                Grid[cr.P.R, cr.P.C] = g;
                Events.Add(new BoardEvent(BoardEventType.SpecialCreated, cr.P, value: cr.Disk ? 'd' : cr.Sp, gemId: g.Id));
            }
        }

        int MostCommonColor()
        {
            var cnt = new int[6]; foreach (var g in Grid) if (g != null && g.Kind == GemKind.Gem) cnt[g.Color]++;
            int best = 0; for (int i = 1; i < 6; i++) if (cnt[i] > cnt[best]) best = i; return best;
        }

        void DoCombo(Gem A, Gem B, Pos at)
        {
            int r = at.R, c = at.C; var cells = new List<Pos>(); var pre = new HashSet<int> { A.Id, B.Id };
            Pos PosOf(Gem g) { for (int y = 0; y < R; y++) for (int x = 0; x < C; x++) if (Grid[y, x] == g) return new Pos(y, x); throw new InvalidOperationException(); }
            var disk = A.Kind == GemKind.Disk ? A : (B.Kind == GemKind.Disk ? B : null); var other = disk == A ? B : A;
            cells.Add(PosOf(A)); cells.Add(PosOf(B));
            if (disk != null && other.Kind == GemKind.Disk)
            {
                for (int y = 0; y < R; y++) for (int x = 0; x < C; x++) if (Grid[y, x] != null && Grid[y, x].Kind != GemKind.Relic) cells.Add(new Pos(y, x));
                AddScore(5000);
            }
            else if (disk != null && other.Sp == '\0')
            {
                for (int y = 0; y < R; y++) for (int x = 0; x < C; x++) { var g = Grid[y, x]; if (g != null && g.Kind == GemKind.Gem && g.Color == other.Color) cells.Add(new Pos(y, x)); }
                AddScore(1500);
            }
            else if (disk != null)
            {
                for (int y = 0; y < R; y++) for (int x = 0; x < C; x++)
                {
                    var g = Grid[y, x];
                    if (g != null && g.Kind == GemKind.Gem && g.Color == other.Color && g != other)
                    { g.Sp = other.Sp == 'b' ? 'b' : (rnd.Next() < .5 ? 'h' : 'v'); cells.Add(new Pos(y, x)); }
                }
            }
            else
            {
                var t = new string(new[] { A.Sp, B.Sp }.OrderBy(ch => ch).ToArray());
                if (t == "bb") { for (int y = r - 2; y <= r + 2; y++) for (int x = c - 2; x <= c + 2; x++) if (InB(y, x)) cells.Add(new Pos(y, x)); }
                else if (t.Contains('b')) { for (int d = -1; d <= 1; d++) { for (int x = 0; x < C; x++) if (InB(r + d, x)) cells.Add(new Pos(r + d, x)); for (int y = 0; y < R; y++) if (InB(y, c + d)) cells.Add(new Pos(y, c + d)); } }
                else { for (int x = 0; x < C; x++) cells.Add(new Pos(r, x)); for (int y = 0; y < R; y++) cells.Add(new Pos(y, c)); }
                AddScore(1200);
            }
            // efekt temizliği
            var uniq = new List<Pos>(); var seen = new HashSet<int>();
            foreach (var p in cells) if (seen.Add(p.Key)) uniq.Add(p);
            foreach (var p in uniq.Where(p => Blocks[p.R, p.C] != null).ToList())
            {
                var b = Blocks[p.R, p.C]; b.Hp--; Events.Add(new BoardEvent(BoardEventType.BlockHit, p, value: b.Hp));
                if (b.Hp <= 0) { Blocks[p.R, p.C] = null; GoalTick(GoalType.Block, 1); AddScore(150); Events.Add(new BoardEvent(BoardEventType.BlockBroken, p)); }
            }
            ClearCells(uniq.Where(p => Blocks[p.R, p.C] == null).ToList(), false, new List<Create>(), pre);
        }

        // ------------------------------------------------------------ düşme, eser, karıştırma
        void Settle()
        {
            for (int guard = 0; guard < 30; guard++)
            {
                Gravity();
                var got = new List<Pos>();
                for (int c = 0; c < C; c++) for (int r = R - 1; r >= 0; r--)
                {
                    if (Blocks[r, c] != null) continue;
                    var g = Grid[r, c]; if (g != null && g.Kind == GemKind.Relic && BottomOf(r, c)) got.Add(new Pos(r, c));
                    break;
                }
                if (got.Count == 0) break;
                foreach (var p in got) { Grid[p.R, p.C] = null; RelicCollected++; GoalTick(GoalType.Relic, 1); AddScore(1000); Events.Add(new BoardEvent(BoardEventType.RelicCollected, p)); }
            }
            if (FindMove() == null && !GoalsDone()) Reshuffle();
        }
        bool BottomOf(int r, int c) { for (int y = r + 1; y < R; y++) if (Blocks[y, c] == null) return false; return true; }
        bool FillableFromAbove(int r, int c) { for (int y = r; y >= 0; y--) if (IsFixed(y, c)) return false; return true; }
        int RelicsOnBoard() { int n = 0; foreach (var g in Grid) if (g != null && g.Kind == GemKind.Relic) n++; return n; }
        Gem SpawnGem()
        {
            bool rel = W.RelicShape != null && RelicSpawned < L.RelicTotal && RelicsOnBoard() < 3 && rnd.Next() < .25;
            if (rel) { RelicSpawned++; return new Gem { Id = gid++, Kind = GemKind.Relic, Shape = W.RelicShape }; }
            return NewGem(RandColor());
        }
        void Gravity()
        {
            for (int pass = 0; pass < 200; pass++)
            {
                var moved = new HashSet<int>(); int count = 0;
                for (int r = R - 1; r >= 0; r--) for (int c = 0; c < C; c++)
                {
                    if (Blocks[r, c] != null || Grid[r, c] != null) continue;
                    if (r == 0) { var g = SpawnGem(); Grid[0, c] = g; moved.Add(g.Id); count++; Events.Add(new BoardEvent(BoardEventType.Spawn, new Pos(0, c), gemId: g.Id)); continue; }
                    var above = Grid[r - 1, c];
                    if (above != null && !above.Lock && !moved.Contains(above.Id) && Blocks[r - 1, c] == null)
                    { Grid[r, c] = above; Grid[r - 1, c] = null; moved.Add(above.Id); count++; Events.Add(new BoardEvent(BoardEventType.Fall, new Pos(r - 1, c), new Pos(r, c), gemId: above.Id)); }
                }
                if (count == 0)
                {
                    for (int r = R - 1; r > 0; r--) for (int c = 0; c < C; c++)
                    {
                        if (Blocks[r, c] != null || Grid[r, c] != null || FillableFromAbove(r - 1, c)) continue;
                        foreach (int dc in new[] { -1, 1 })
                        {
                            int x = c + dc; if (!InB(r - 1, x)) continue; var g = Grid[r - 1, x];
                            if (g != null && !g.Lock && !moved.Contains(g.Id) && Blocks[r - 1, x] == null)
                            { Grid[r, c] = g; Grid[r - 1, x] = null; moved.Add(g.Id); count++; Events.Add(new BoardEvent(BoardEventType.Fall, new Pos(r - 1, x), new Pos(r, c), gemId: g.Id)); break; }
                        }
                    }
                }
                if (count == 0) break;
            }
        }
        void Reshuffle()
        {
            var spots = new List<Pos>(); var gems = new List<Gem>();
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) { var g = Grid[r, c]; if (g != null && g.Kind == GemKind.Gem && g.Sp == '\0' && !g.Lock) { spots.Add(new Pos(r, c)); gems.Add(g); } }
            for (int t = 0; t < 80; t++)
            {
                rnd.Shuffle(gems);
                for (int i = 0; i < spots.Count; i++) Grid[spots[i].R, spots[i].C] = gems[i];
                if (FindGroups().Count == 0 && FindMove() != null) break;
                if (t > 40) foreach (var g in gems) g.Color = RandColor();
            }
            Events.Add(new BoardEvent(BoardEventType.Reshuffle));
        }

        // ------------------------------------------------------------ tur sonu ve imza mekanikleri
        void EndTurn()
        {
            if (GoalsDone()) { Won = Over = true; Events.Add(new BoardEvent(BoardEventType.Win)); return; }
            if (L.Sig && Moves > 0 && !Over) { SigTick(); if (GoalsDone()) { Won = Over = true; Events.Add(new BoardEvent(BoardEventType.Win)); return; } }
            if (Moves <= 0) { Over = true; Events.Add(new BoardEvent(BoardEventType.Lose)); }
        }

        void SigTick()
        {
            var SG = W.Sig; Turn++;
            if (SG.Every == 0 || Turn % SG.Every != 0) return;
            switch (SG.Id)
            {
                case SigId.Sandstorm:
                {
                    var cand = new List<Pos>();
                    for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) if (Layers[r, c] > 0)
                        foreach (var (dr, dc) in new[] { (1, 0), (-1, 0), (0, 1), (0, -1) }) { int y = r + dr, x = c + dc; if (InB(y, x) && Blocks[y, x] == null && Layers[y, x] == 0) cand.Add(new Pos(y, x)); }
                    if (cand.Count == 0 || CountLayers() >= LayerCap) return;
                    var p = cand[(int)Math.Floor(rnd.Next() * cand.Count)]; Layers[p.R, p.C] = 1;
                    Events.Add(new BoardEvent(BoardEventType.Sig, p, value: (int)SG.Id)); break;
                }
                case SigId.Wind:
                {
                    if (CountLayers() == 0) return;
                    for (int r = 0; r < R; r++)
                    {
                        var cols = Enumerable.Range(0, C).Where(c => Blocks[r, c] == null).ToList(); var vals = cols.Select(c => Layers[r, c]).ToList();
                        for (int i = 0; i < cols.Count; i++) Layers[r, cols[i]] = vals[(i - 1 + vals.Count) % vals.Count];
                    }
                    Events.Add(new BoardEvent(BoardEventType.Sig, value: (int)SG.Id)); break;
                }
                case SigId.Raid:
                {
                    if (Raids >= 4) return;
                    var cand = new List<Pos>();
                    for (int r = 1; r <= 3; r++) for (int c = 0; c < C; c++) { var g = Grid[r, c]; if (g != null && g.Kind == GemKind.Gem && g.Sp == '\0' && !g.Lock) cand.Add(new Pos(r, c)); }
                    if (cand.Count == 0) return;
                    var p = cand[(int)Math.Floor(rnd.Next() * cand.Count)]; Grid[p.R, p.C] = null; Blocks[p.R, p.C] = new BlockCell { Kind = "wood", Hp = 1, Max = 1 }; Raids++;
                    foreach (var gl in Goals) if (gl.Type == GoalType.Block) gl.Left++;
                    Events.Add(new BoardEvent(BoardEventType.Sig, p, value: (int)SG.Id)); break;
                }
                case SigId.Sunlight:
                {
                    var cand = new List<(Gem, Pos)>();
                    for (int r = 0; r < R; r++) for (int c = 0; c < C; c++) { var g = Grid[r, c]; if (g != null && g.Kind == GemKind.Gem && g.Sp == '\0' && !g.Lock) cand.Add((g, new Pos(r, c))); }
                    if (cand.Count == 0) return;
                    var (gem, p) = cand[(int)Math.Floor(rnd.Next() * cand.Count)]; gem.Sp = rnd.Next() < .5 ? 'h' : 'v';
                    Events.Add(new BoardEvent(BoardEventType.Sig, p, value: (int)SG.Id)); break;
                }
                default: return;
            }
            SyncLayerGoal();
        }

        /// <summary>Test ve hata ayıklama için tahtanın metin özeti (prototipteki snap() ile aynı biçim).</summary>
        public string Snapshot()
        {
            var parts = new List<string>();
            for (int r = 0; r < R; r++) for (int c = 0; c < C; c++)
            {
                string cell; var b = Blocks[r, c]; var g = Grid[r, c];
                if (b != null) cell = "B" + b.Kind + b.Hp;
                else if (g == null) cell = ".";
                else if (g.Kind == GemKind.Disk) cell = "D";
                else if (g.Kind == GemKind.Relic) cell = "R";
                else cell = "g" + g.Color + (g.Sp == '\0' ? "" : g.Sp.ToString()) + (g.Lock ? "L" : "");
                parts.Add(cell + "|" + Layers[r, c] + (Lotus[r, c] ? "*" : ""));
            }
            string goals = string.Join(";", Goals.Select(gl => gl.Type.ToString().ToLowerInvariant() + ":" + gl.Color + ":" + gl.Left));
            return string.Join(",", parts) + $" M{Moves} S{Score} T{Turn} G{goals} O{(Over ? 1 : 0)}";
        }
    }
}
