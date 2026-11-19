using System;
using System.Collections.Generic;

namespace WorldWonders.Core.Match3
{
    public struct Pos : IEquatable<Pos>
    {
        public int X, Y;
        public Pos(int x, int y) { X = x; Y = y; }
        public bool Equals(Pos o) { return X == o.X && Y == o.Y; }
        public override bool Equals(object o) { return o is Pos && Equals((Pos)o); }
        public override int GetHashCode() { return X * 397 ^ Y; }
        public override string ToString() { return "(" + X + "," + Y + ")"; }
        public bool IsAdjacent(Pos o) { return Math.Abs(X - o.X) + Math.Abs(Y - o.Y) == 1; }
    }

    public sealed class Tile
    {
        public int Id;
        public TileColor Color;
        public SpecialType Special;
        public Tile Clone() { return new Tile { Id = Id, Color = Color, Special = Special }; }
    }

    public struct Cell
    {
        public bool Playable;
        public int Sand;   // taşın altındaki kum katmanı
        public int Crate;  // sandık canı; >0 ise hücrede taş yoktur
        public bool Chain; // taş zincirli: yer değiştiremez, düşmez
    }

    // ---- Görünüm katmanı için hamle kayıtları ----
    public struct TileCleared { public int TileId; public Pos At; public TileColor Color; public SpecialType Special; }
    public struct TileCreated { public int TileId; public Pos At; public TileColor Color; public SpecialType Special; }
    public struct TileMoved { public int TileId; public Pos From; public Pos To; }
    public struct TileSpawned { public int TileId; public Pos At; public TileColor Color; public SpecialType Special; public int DropRows; }
    public enum ObstacleKind { Sand, Crate, Chain }
    public struct ObstacleHit { public Pos At; public ObstacleKind Kind; public int Remaining; }
    public struct SpecialFired { public Pos At; public SpecialType Special; public TileColor Color; }

    public sealed class CascadeStep
    {
        public readonly List<TileCleared> Cleared = new List<TileCleared>();
        public readonly List<TileCreated> Created = new List<TileCreated>();
        public readonly List<SpecialFired> Fired = new List<SpecialFired>();
        public readonly List<ObstacleHit> Obstacles = new List<ObstacleHit>();
        public readonly List<TileMoved> Moves = new List<TileMoved>();
        public readonly List<TileSpawned> Spawns = new List<TileSpawned>();
        public int ScoreGained;
    }

    public sealed class MoveResult
    {
        public bool Valid;
        public Pos A, B;
        public readonly List<CascadeStep> Steps = new List<CascadeStep>();
        public bool Shuffled;
        public List<TileMoved> ShuffleMoves = new List<TileMoved>();
        public static readonly MoveResult Invalid = new MoveResult { Valid = false };
    }

    /// <summary>
    /// Saf C# Match-3 motoru (Unity'den bağımsız, test edilebilir).
    /// y = 0 en üst satırdır; taşlar y'nin arttığı yöne düşer.
    /// </summary>
    public sealed class Match3Board
    {
        public readonly int Width, Height, ColorCount;
        public readonly Cell[,] Cells;
        public readonly Tile[,] Tiles;
        private readonly Rng _rng;
        private int _nextId = 1;

        // İstatistik/hedef sayaçları (Match3Manager okur)
        public readonly int[] ClearedByColor = new int[6];
        public int SandCleared, CratesBroken, ChainsReleased, Score;
        public int SpecialsCreated;

        public Match3Board(int width, int height, int colors, ulong seed)
        {
            Width = width; Height = height; ColorCount = colors;
            Cells = new Cell[width, height];
            Tiles = new Tile[width, height];
            _rng = new Rng(seed);
        }

        public static Match3Board FromDefinition(LevelDefinition def, ulong? seedOverride = null)
        {
            var b = new Match3Board(def.Width, def.Height, def.Colors, seedOverride ?? def.Seed);
            for (int y = 0; y < def.Height; y++)
                for (int x = 0; x < def.Width; x++)
                {
                    char c = def.At(x, y);
                    var cell = new Cell { Playable = c != 'x' };
                    if (c == 's') cell.Sand = 1;
                    if (c == 'S') cell.Sand = 2;
                    if (c == 'c') cell.Crate = 1;
                    if (c == 'C') cell.Crate = 2;
                    if (c == 'l') cell.Chain = true;
                    b.Cells[x, y] = cell;
                }
            b.FillInitial();
            return b;
        }

        public Rng Random { get { return _rng; } }

        public bool InBounds(Pos p) { return p.X >= 0 && p.Y >= 0 && p.X < Width && p.Y < Height; }
        public bool HoldsTile(Pos p) { return InBounds(p) && Cells[p.X, p.Y].Playable && Cells[p.X, p.Y].Crate == 0; }
        public Tile TileAt(Pos p) { return InBounds(p) ? Tiles[p.X, p.Y] : null; }
        private bool IsFixed(int x, int y) { return Cells[x, y].Chain; }
        public bool CanSwapCell(Pos p) { var t = TileAt(p); return t != null && !Cells[p.X, p.Y].Chain; }

        private Tile NewTile(TileColor c, SpecialType s = SpecialType.None) { return new Tile { Id = _nextId++, Color = c, Special = s }; }
        private TileColor RandomColor() { return (TileColor)_rng.Next(ColorCount); }

        // ------------------------------------------------------------------ Kurulum
        public void FillInitial()
        {
            for (int y = 0; y < Height; y++)
                for (int x = 0; x < Width; x++)
                {
                    if (!HoldsTile(new Pos(x, y))) { Tiles[x, y] = null; continue; }
                    TileColor c;
                    int guard = 0;
                    do { c = RandomColor(); } while (++guard < 50 && WouldMatchAt(x, y, c));
                    Tiles[x, y] = NewTile(c);
                }
            if (!HasPossibleMove()) ShuffleBoard(null);
        }

        private bool WouldMatchAt(int x, int y, TileColor c)
        {
            if (x >= 2 && ColorAt(x - 1, y) == c && ColorAt(x - 2, y) == c) return true;
            if (y >= 2 && ColorAt(x, y - 1) == c && ColorAt(x, y - 2) == c) return true;
            return false;
        }

        private TileColor ColorAt(int x, int y)
        {
            if (x < 0 || y < 0 || x >= Width || y >= Height) return TileColor.None;
            var t = Tiles[x, y];
            if (t == null || t.Special == SpecialType.TimeOrb) return TileColor.None;
            return t.Color;
        }

        // ------------------------------------------------------------------ Eşleşme bulma
        public sealed class MatchGroup
        {
            public TileColor Color;
            public readonly HashSet<Pos> Cells = new HashSet<Pos>();
            public int MaxH, MaxV;
            public bool HasH, HasV;
            public SpecialType SpecialToCreate
            {
                get
                {
                    if (MaxH >= 5 || MaxV >= 5) return SpecialType.TimeOrb;
                    if (HasH && HasV) return SpecialType.Bomb;
                    if (MaxH == 4) return SpecialType.ArrowRow;
                    if (MaxV == 4) return SpecialType.ArrowColumn;
                    return SpecialType.None;
                }
            }
        }

        public List<MatchGroup> FindMatches()
        {
            var hRun = new int[Width, Height];
            var vRun = new int[Width, Height];
            for (int y = 0; y < Height; y++)
            {
                int x = 0;
                while (x < Width)
                {
                    var c = ColorAt(x, y);
                    int e = x + 1;
                    if (c != TileColor.None) while (e < Width && ColorAt(e, y) == c) e++;
                    if (c != TileColor.None && e - x >= 3) for (int k = x; k < e; k++) hRun[k, y] = e - x;
                    x = e;
                }
            }
            for (int x = 0; x < Width; x++)
            {
                int y = 0;
                while (y < Height)
                {
                    var c = ColorAt(x, y);
                    int e = y + 1;
                    if (c != TileColor.None) while (e < Height && ColorAt(x, e) == c) e++;
                    if (c != TileColor.None && e - y >= 3) for (int k = y; k < e; k++) vRun[x, k] = e - y;
                    y = e;
                }
            }
            var groups = new List<MatchGroup>();
            var visited = new bool[Width, Height];
            var stack = new Stack<Pos>();
            for (int y = 0; y < Height; y++)
                for (int x = 0; x < Width; x++)
                {
                    if (visited[x, y] || (hRun[x, y] == 0 && vRun[x, y] == 0)) continue;
                    var g = new MatchGroup { Color = ColorAt(x, y) };
                    stack.Push(new Pos(x, y)); visited[x, y] = true;
                    while (stack.Count > 0)
                    {
                        var p = stack.Pop();
                        g.Cells.Add(p);
                        if (hRun[p.X, p.Y] > 0) { g.HasH = true; g.MaxH = Math.Max(g.MaxH, hRun[p.X, p.Y]); }
                        if (vRun[p.X, p.Y] > 0) { g.HasV = true; g.MaxV = Math.Max(g.MaxV, vRun[p.X, p.Y]); }
                        // Komşular: yalnız aynı run üzerinden bağlan (aynı renk + run içinde).
                        TryLink(p, 1, 0, hRun[p.X, p.Y] > 0, hRun, g.Color, visited, stack);
                        TryLink(p, -1, 0, hRun[p.X, p.Y] > 0, hRun, g.Color, visited, stack);
                        TryLink(p, 0, 1, vRun[p.X, p.Y] > 0, vRun, g.Color, visited, stack);
                        TryLink(p, 0, -1, vRun[p.X, p.Y] > 0, vRun, g.Color, visited, stack);
                    }
                    groups.Add(g);
                }
            return groups;
        }

        private void TryLink(Pos p, int dx, int dy, bool inRun, int[,] run, TileColor color, bool[,] visited, Stack<Pos> stack)
        {
            if (!inRun) return;
            int nx = p.X + dx, ny = p.Y + dy;
            if (nx < 0 || ny < 0 || nx >= Width || ny >= Height || visited[nx, ny]) return;
            if (run[nx, ny] == 0 || ColorAt(nx, ny) != color) return;
            visited[nx, ny] = true;
            stack.Push(new Pos(nx, ny));
        }

        // ------------------------------------------------------------------ Hamle
        public bool IsSwappable(Pos a, Pos b)
        {
            return a.IsAdjacent(b) && CanSwapCell(a) && CanSwapCell(b);
        }

        /// <summary>Hamle geçerli olur mu? (Durumu değiştirmez.)</summary>
        public bool WouldSwapBeValid(Pos a, Pos b)
        {
            if (!IsSwappable(a, b)) return false;
            var ta = TileAt(a); var tb = TileAt(b);
            if (ta.Special != SpecialType.None && tb.Special != SpecialType.None) return true;
            if (ta.Special == SpecialType.TimeOrb || tb.Special == SpecialType.TimeOrb) return true;
            SwapRaw(a, b);
            bool ok = HasMatchAround(a) || HasMatchAround(b);
            SwapRaw(a, b);
            return ok;
        }

        private bool HasMatchAround(Pos p)
        {
            var c = ColorAt(p.X, p.Y);
            if (c == TileColor.None) return false;
            int h = 1, v = 1;
            for (int x = p.X - 1; x >= 0 && ColorAt(x, p.Y) == c; x--) h++;
            for (int x = p.X + 1; x < Width && ColorAt(x, p.Y) == c; x++) h++;
            for (int y = p.Y - 1; y >= 0 && ColorAt(p.X, y) == c; y--) v++;
            for (int y = p.Y + 1; y < Height && ColorAt(p.X, y) == c; y++) v++;
            return h >= 3 || v >= 3;
        }

        private void SwapRaw(Pos a, Pos b)
        {
            var t = Tiles[a.X, a.Y]; Tiles[a.X, a.Y] = Tiles[b.X, b.Y]; Tiles[b.X, b.Y] = t;
        }

        /// <summary>
        /// İki taşı değiştirir ve bütün zincirleme eşleşmeleri çözer. Geçersiz hamle durumu değiştirmez (geri alınır).
        /// </summary>
        public MoveResult TrySwap(Pos a, Pos b)
        {
            if (!WouldSwapBeValid(a, b)) return MoveResult.Invalid;
            var res = new MoveResult { Valid = true, A = a, B = b };
            SwapRaw(a, b);
            var ta = TileAt(a); var tb = TileAt(b); // ta artık a'da (eskiden b'deki taş)
            var step = new CascadeStep();
            var toClear = new List<Pos>();

            bool sa = ta.Special != SpecialType.None, sb = tb.Special != SpecialType.None;
            if (sa && sb)
            {
                FireCombo(b, a, ta, tb, step, toClear);
                ResolveFrom(step, toClear, false, res, null);
            }
            else if (ta.Special == SpecialType.TimeOrb || tb.Special == SpecialType.TimeOrb)
            {
                Pos orbPos = ta.Special == SpecialType.TimeOrb ? a : b;
                Pos otherPos = orbPos.Equals(a) ? b : a;
                var other = TileAt(otherPos);
                step.Fired.Add(new SpecialFired { At = orbPos, Special = SpecialType.TimeOrb, Color = other.Color });
                TileAt(orbPos).Special = SpecialType.None; // tükendi
                RemoveTileRecord(orbPos, step);
                foreach (var p in AllOfColor(other.Color)) toClear.Add(p);
                ResolveFrom(step, toClear, false, res, null);
            }
            else
            {
                ResolveFrom(step, toClear, true, res, new[] { a, b });
            }
            AfterMoveShuffle(res);
            return res;
        }

        // ------------------------------------------------------------------ Güçlendiriciler (hamle harcamaz)
        public MoveResult ApplyHammer(Pos p)
        {
            if (!InBounds(p) || !Cells[p.X, p.Y].Playable) return MoveResult.Invalid;
            if (TileAt(p) == null && Cells[p.X, p.Y].Crate == 0) return MoveResult.Invalid;
            var res = new MoveResult { Valid = true, A = p, B = p };
            var step = new CascadeStep();
            ResolveFrom(step, new List<Pos> { p }, false, res, null);
            AfterMoveShuffle(res);
            return res;
        }

        public MoveResult ApplyPlacedSpecial(Pos p, SpecialType special)
        {
            var t = TileAt(p);
            if (t == null || Cells[p.X, p.Y].Chain) return MoveResult.Invalid;
            var res = new MoveResult { Valid = true, A = p, B = p };
            t.Special = special;
            var step = new CascadeStep();
            ResolveFrom(step, new List<Pos> { p }, false, res, null);
            AfterMoveShuffle(res);
            return res;
        }

        public MoveResult ApplyShuffleBooster()
        {
            var res = new MoveResult { Valid = true };
            ShuffleBoard(res.ShuffleMoves);
            res.Shuffled = true;
            return res;
        }

        private void AfterMoveShuffle(MoveResult res)
        {
            if (!HasPossibleMove())
            {
                ShuffleBoard(res.ShuffleMoves);
                res.Shuffled = true;
            }
        }

        // ------------------------------------------------------------------ Çözümleme
        private void ResolveFrom(CascadeStep step, List<Pos> initialClear, bool needMatchesFirst, MoveResult res, Pos[] swapCells)
        {
            int depth = 0;
            bool first = true;
            while (true)
            {
                var matches = FindMatches();
                bool hasWork = initialClear.Count > 0 || matches.Count > 0;
                if (first && needMatchesFirst && matches.Count == 0) { break; }
                if (!hasWork) break;
                depth++;
                var clear = new HashSet<Pos>(initialClear);
                var matchAdj = new HashSet<Pos>();
                var created = new List<KeyValuePair<Pos, Tile>>();
                foreach (var g in matches)
                {
                    var sp = g.SpecialToCreate;
                    Pos? createAt = null;
                    if (sp != SpecialType.None)
                    {
                        if (first && swapCells != null)
                            foreach (var sc in swapCells) if (g.Cells.Contains(sc)) { createAt = sc; break; }
                        if (createAt == null) createAt = PickCenter(g);
                    }
                    foreach (var p in g.Cells)
                    {
                        clear.Add(p);
                        matchAdj.Add(new Pos(p.X + 1, p.Y)); matchAdj.Add(new Pos(p.X - 1, p.Y));
                        matchAdj.Add(new Pos(p.X, p.Y + 1)); matchAdj.Add(new Pos(p.X, p.Y - 1));
                    }
                    if (createAt.HasValue)
                    {
                        var nt = NewTile(sp == SpecialType.TimeOrb ? g.Color : g.Color, sp);
                        created.Add(new KeyValuePair<Pos, Tile>(createAt.Value, nt));
                    }
                }
                // Eşleşmelere komşu sandıklar hasar alır (eşleşen hücreler hariç).
                foreach (var p in matchAdj)
                    if (InBounds(p) && Cells[p.X, p.Y].Playable && Cells[p.X, p.Y].Crate > 0 && !clear.Contains(p))
                        HitCrate(p, step);

                ClearPositions(clear, step);
                foreach (var kv in created)
                {
                    // Oluşma hücresi patlama sırasında boşaltıldıysa yeni özel taş oraya konur.
                    if (Tiles[kv.Key.X, kv.Key.Y] != null && !Cells[kv.Key.X, kv.Key.Y].Chain)
                        RemoveTileRecord(kv.Key, step);
                    if (Tiles[kv.Key.X, kv.Key.Y] == null && HoldsTile(kv.Key))
                    {
                        Tiles[kv.Key.X, kv.Key.Y] = kv.Value;
                        SpecialsCreated++;
                        step.Created.Add(new TileCreated { TileId = kv.Value.Id, At = kv.Key, Color = kv.Value.Color, Special = kv.Value.Special });
                    }
                }
                step.ScoreGained += step.Cleared.Count * 60 * depth;
                Score += step.Cleared.Count * 60 * depth;
                ApplyGravity(step);
                res.Steps.Add(step);
                step = new CascadeStep();
                initialClear = new List<Pos>();
                first = false;
                if (depth > 100) break; // güvenlik
            }
        }

        private Pos PickCenter(MatchGroup g)
        {
            long sx = 0, sy = 0;
            foreach (var p in g.Cells) { sx += p.X; sy += p.Y; }
            float cx = sx / (float)g.Cells.Count, cy = sy / (float)g.Cells.Count;
            Pos best = default(Pos); float bd = float.MaxValue;
            foreach (var p in g.Cells)
            {
                float d = (p.X - cx) * (p.X - cx) + (p.Y - cy) * (p.Y - cy);
                if (d < bd || (Math.Abs(d - bd) < 1e-4 && (p.Y > best.Y || (p.Y == best.Y && p.X < best.X)))) { bd = d; best = p; }
            }
            return best;
        }

        /// <summary>Bir küme hücreyi temizler; özel taşları zincirleme patlatır.</summary>
        private void ClearPositions(HashSet<Pos> initial, CascadeStep step)
        {
            var queue = new Queue<Pos>(initial);
            var done = new HashSet<Pos>();
            while (queue.Count > 0)
            {
                var p = queue.Dequeue();
                if (!InBounds(p) || !done.Add(p)) continue;
                ref Cell cell = ref Cells[p.X, p.Y];
                if (!cell.Playable) continue;
                if (cell.Crate > 0) { HitCrate(p, step); continue; }
                var t = Tiles[p.X, p.Y];
                if (t == null) continue;
                if (cell.Chain)
                {
                    cell.Chain = false;
                    ChainsReleased++;
                    step.Obstacles.Add(new ObstacleHit { At = p, Kind = ObstacleKind.Chain, Remaining = 0 });
                    continue;
                }
                if (t.Special != SpecialType.None)
                {
                    var sp = t.Special;
                    step.Fired.Add(new SpecialFired { At = p, Special = sp, Color = t.Color });
                    t.Special = SpecialType.None;
                    foreach (var q in BlastArea(p, sp, t.Color)) if (!done.Contains(q)) queue.Enqueue(q);
                }
                RemoveTileRecord(p, step);
            }
        }

        private void RemoveTileRecord(Pos p, CascadeStep step)
        {
            var t = Tiles[p.X, p.Y];
            if (t == null) return;
            step.Cleared.Add(new TileCleared { TileId = t.Id, At = p, Color = t.Color, Special = t.Special });
            if (t.Color != TileColor.None) ClearedByColor[(int)t.Color]++;
            Tiles[p.X, p.Y] = null;
            ref Cell cell = ref Cells[p.X, p.Y];
            if (cell.Sand > 0)
            {
                cell.Sand--;
                SandCleared++;
                step.Obstacles.Add(new ObstacleHit { At = p, Kind = ObstacleKind.Sand, Remaining = cell.Sand });
            }
        }

        private void HitCrate(Pos p, CascadeStep step)
        {
            ref Cell cell = ref Cells[p.X, p.Y];
            if (cell.Crate <= 0) return;
            foreach (var o in step.Obstacles) if (o.Kind == ObstacleKind.Crate && o.At.Equals(p)) return; // adım başına bir hasar
            cell.Crate--;
            if (cell.Crate == 0) CratesBroken++;
            step.Obstacles.Add(new ObstacleHit { At = p, Kind = ObstacleKind.Crate, Remaining = cell.Crate });
        }

        private IEnumerable<Pos> BlastArea(Pos p, SpecialType sp, TileColor color)
        {
            switch (sp)
            {
                case SpecialType.ArrowRow:
                    for (int x = 0; x < Width; x++) yield return new Pos(x, p.Y);
                    break;
                case SpecialType.ArrowColumn:
                    for (int y = 0; y < Height; y++) yield return new Pos(p.X, y);
                    break;
                case SpecialType.Bomb:
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) yield return new Pos(p.X + dx, p.Y + dy);
                    break;
                case SpecialType.TimeOrb:
                    // Patlamayla tetiklenen küre: tahtadaki en yaygın rengi temizler.
                    foreach (var q in AllOfColor(MostCommonColor())) yield return q;
                    break;
            }
        }

        private TileColor MostCommonColor()
        {
            var counts = new int[6];
            for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) { var t = Tiles[x, y]; if (t != null && t.Special != SpecialType.TimeOrb) counts[(int)t.Color]++; }
            int best = 0; for (int i = 1; i < 6; i++) if (counts[i] > counts[best]) best = i;
            return (TileColor)best;
        }

        private List<Pos> AllOfColor(TileColor c)
        {
            var r = new List<Pos>();
            for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) { var t = Tiles[x, y]; if (t != null && t.Color == c && t.Special != SpecialType.TimeOrb) r.Add(new Pos(x, y)); }
            return r;
        }

        /// <summary>İki özel taş birlikte değiştirildiğinde kombinasyon.</summary>
        private void FireCombo(Pos target, Pos other, Tile tTarget, Tile tOther, CascadeStep step, List<Pos> toClear)
        {
            // target = b konumu (oyuncunun sürüklediği taşın vardığı yer)
            var s1 = tTarget.Special; var s2 = tOther.Special;
            bool orb1 = s1 == SpecialType.TimeOrb, orb2 = s2 == SpecialType.TimeOrb;
            bool arrow1 = s1 == SpecialType.ArrowRow || s1 == SpecialType.ArrowColumn;
            bool arrow2 = s2 == SpecialType.ArrowRow || s2 == SpecialType.ArrowColumn;
            step.Fired.Add(new SpecialFired { At = target, Special = s1, Color = tTarget.Color });
            step.Fired.Add(new SpecialFired { At = other, Special = s2, Color = tOther.Color });
            tTarget.Special = SpecialType.None; tOther.Special = SpecialType.None;
            RemoveTileRecord(target, step); RemoveTileRecord(other, step);

            if (orb1 && orb2)
            {
                for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) toClear.Add(new Pos(x, y));
                return;
            }
            if (orb1 || orb2)
            {
                var nonOrb = orb1 ? tOther : tTarget;
                var nonOrbSpecial = orb1 ? s2 : s1;
                var conv = nonOrbSpecial == SpecialType.Bomb ? SpecialType.Bomb : SpecialType.ArrowRow;
                foreach (var p in AllOfColor(nonOrb.Color))
                {
                    var t = TileAt(p);
                    if (Cells[p.X, p.Y].Chain) { toClear.Add(p); continue; }
                    t.Special = conv == SpecialType.Bomb ? SpecialType.Bomb : (_rng.Next(2) == 0 ? SpecialType.ArrowRow : SpecialType.ArrowColumn);
                    toClear.Add(p);
                }
                return;
            }
            if (arrow1 && arrow2)
            {
                for (int x = 0; x < Width; x++) toClear.Add(new Pos(x, target.Y));
                for (int y = 0; y < Height; y++) toClear.Add(new Pos(target.X, y));
                return;
            }
            if ((arrow1 && s2 == SpecialType.Bomb) || (arrow2 && s1 == SpecialType.Bomb))
            {
                for (int d = -1; d <= 1; d++)
                {
                    for (int x = 0; x < Width; x++) toClear.Add(new Pos(x, target.Y + d));
                    for (int y = 0; y < Height; y++) toClear.Add(new Pos(target.X + d, y));
                }
                return;
            }
            // Bomba + Bomba: 5x5
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) toClear.Add(new Pos(target.X + dx, target.Y + dy));
        }

        // ------------------------------------------------------------------ Yerçekimi
        private void ApplyGravity(CascadeStep step)
        {
            for (int x = 0; x < Width; x++)
            {
                // Taş tutabilen ve sabit olmayan hücreler (üstten alta).
                var slots = new List<int>();
                for (int y = 0; y < Height; y++)
                    if (HoldsTile(new Pos(x, y)) && !IsFixed(x, y)) slots.Add(y);
                if (slots.Count == 0) continue;
                var tiles = new List<KeyValuePair<int, Tile>>();
                foreach (var y in slots) if (Tiles[x, y] != null) tiles.Add(new KeyValuePair<int, Tile>(y, Tiles[x, y]));
                foreach (var y in slots) Tiles[x, y] = null;
                int si = slots.Count - 1;
                for (int ti = tiles.Count - 1; ti >= 0; ti--, si--)
                {
                    int ny = slots[si];
                    Tiles[x, ny] = tiles[ti].Value;
                    if (ny != tiles[ti].Key) step.Moves.Add(new TileMoved { TileId = tiles[ti].Value.Id, From = new Pos(x, tiles[ti].Key), To = new Pos(x, ny) });
                }
                int missing = si + 1;
                for (int k = si; k >= 0; k--)
                {
                    var t = NewTile(RandomColor());
                    Tiles[x, slots[k]] = t;
                    step.Spawns.Add(new TileSpawned { TileId = t.Id, At = new Pos(x, slots[k]), Color = t.Color, Special = SpecialType.None, DropRows = missing });
                }
            }
        }

        /// <summary>Takası geçici yapıp oluşacak eşleşme gruplarını döndürür (durumu değiştirmez). Denge botu ve ipucu için.</summary>
        public List<MatchGroup> PreviewSwap(Pos a, Pos b)
        {
            SwapRaw(a, b);
            var g = FindMatches();
            SwapRaw(a, b);
            return g;
        }

        // ------------------------------------------------------------------ Olası hamle & karıştırma
        public bool HasPossibleMove() { Pos a, b; return FindHint(out a, out b); }

        public bool FindHint(out Pos a, out Pos b)
        {
            for (int y = 0; y < Height; y++)
                for (int x = 0; x < Width; x++)
                {
                    var p = new Pos(x, y);
                    var r = new Pos(x + 1, y);
                    if (InBounds(r) && WouldSwapBeValid(p, r)) { a = p; b = r; return true; }
                    var d = new Pos(x, y + 1);
                    if (InBounds(d) && WouldSwapBeValid(p, d)) { a = p; b = d; return true; }
                }
            a = b = default(Pos);
            return false;
        }

        public List<KeyValuePair<Pos, Pos>> AllValidSwaps()
        {
            var list = new List<KeyValuePair<Pos, Pos>>();
            for (int y = 0; y < Height; y++)
                for (int x = 0; x < Width; x++)
                {
                    var p = new Pos(x, y);
                    var r = new Pos(x + 1, y);
                    if (InBounds(r) && WouldSwapBeValid(p, r)) list.Add(new KeyValuePair<Pos, Pos>(p, r));
                    var d = new Pos(x, y + 1);
                    if (InBounds(d) && WouldSwapBeValid(p, d)) list.Add(new KeyValuePair<Pos, Pos>(p, d));
                }
            return list;
        }

        /// <summary>Güvenli karıştırma: hazır eşleşme oluşturmaz ve en az bir hamle bırakır.</summary>
        public void ShuffleBoard(List<TileMoved> moves)
        {
            var positions = new List<Pos>();
            for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++)
                    if (Tiles[x, y] != null && !Cells[x, y].Chain) positions.Add(new Pos(x, y));
            if (positions.Count < 2) return;
            var original = new Dictionary<int, Pos>();
            foreach (var p in positions) original[Tiles[p.X, p.Y].Id] = p;
            var tiles = new List<Tile>();
            foreach (var p in positions) tiles.Add(Tiles[p.X, p.Y]);

            bool ok = false;
            for (int attempt = 0; attempt < 200 && !ok; attempt++)
            {
                _rng.Shuffle(tiles);
                for (int i = 0; i < positions.Count; i++) Tiles[positions[i].X, positions[i].Y] = tiles[i];
                ok = FindMatches().Count == 0 && HasPossibleMove();
            }
            if (!ok)
            {
                // Son çare: normal taşları eşleşmesiz yeniden renklendir, sonra bir hamle garanti et.
                for (int attempt = 0; attempt < 200 && !ok; attempt++)
                {
                    foreach (var p in positions)
                    {
                        var t = Tiles[p.X, p.Y];
                        if (t.Special != SpecialType.None) continue;
                        int guard = 0; TileColor c;
                        do { c = RandomColor(); } while (++guard < 30 && WouldMatchAt(p.X, p.Y, c));
                        t.Color = c;
                    }
                    ok = FindMatches().Count == 0 && HasPossibleMove();
                }
            }
            if (moves != null)
                foreach (var p in positions)
                {
                    var t = Tiles[p.X, p.Y];
                    Pos from; if (original.TryGetValue(t.Id, out from) && !from.Equals(p)) moves.Add(new TileMoved { TileId = t.Id, From = from, To = p });
                }
        }

        // ------------------------------------------------------------------ Yardımcılar
        public int TotalSand()
        {
            int s = 0; for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) s += Cells[x, y].Sand; return s;
        }

        public int TotalCrates()
        {
            int s = 0; for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) if (Cells[x, y].Crate > 0) s++; return s;
        }

        public int TotalChains()
        {
            int s = 0; for (int y = 0; y < Height; y++) for (int x = 0; x < Width; x++) if (Cells[x, y].Chain) s++; return s;
        }

        /// <summary>Test/hata ayıklama: tahtayı belirli renklerle kurar (satırlar üstten alta, '.' boş/oynanamaz).</summary>
        public void SetTilesForTest(string[] rows)
        {
            for (int y = 0; y < Height; y++)
                for (int x = 0; x < Width; x++)
                {
                    char ch = rows[y][x];
                    if (ch == '.' || !HoldsTile(new Pos(x, y))) { Tiles[x, y] = null; continue; }
                    SpecialType sp = SpecialType.None;
                    int ci;
                    if (char.IsDigit(ch)) ci = ch - '0';
                    else
                    {
                        // Büyük harf özel taş: R=ok satır(renk 0..), harf kodu: A-F renk 0-5 satır oku, G-L sütun oku, M-R bomba, Z küre
                        if (ch == 'Z') { Tiles[x, y] = NewTile(TileColor.GoldCoin, SpecialType.TimeOrb); continue; }
                        int k = ch - 'A';
                        ci = k % 6;
                        sp = k < 6 ? SpecialType.ArrowRow : k < 12 ? SpecialType.ArrowColumn : SpecialType.Bomb;
                    }
                    Tiles[x, y] = NewTile((TileColor)ci, sp);
                }
        }

        public string Dump()
        {
            var sb = new System.Text.StringBuilder();
            for (int y = 0; y < Height; y++)
            {
                for (int x = 0; x < Width; x++)
                {
                    var t = Tiles[x, y];
                    if (!Cells[x, y].Playable) sb.Append(' ');
                    else if (Cells[x, y].Crate > 0) sb.Append('#');
                    else if (t == null) sb.Append('.');
                    else if (t.Special == SpecialType.TimeOrb) sb.Append('Z');
                    else if (t.Special != SpecialType.None) sb.Append('*');
                    else sb.Append((int)t.Color);
                }
                sb.Append('\n');
            }
            return sb.ToString();
        }
    }
}
