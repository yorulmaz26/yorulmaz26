using System;
using System.Collections.Generic;

namespace WorldWonders.Core.Minigames
{
    /// <summary>Normalleştirilmiş [0,1]x[0,1] alanda nokta.</summary>
    public struct Vec2
    {
        public float X, Y;
        public Vec2(float x, float y) { X = x; Y = y; }
        public static float DistSq(Vec2 a, Vec2 b) { float dx = a.X - b.X, dy = a.Y - b.Y; return dx * dx + dy * dy; }
    }

    public interface IDiscoveryMinigame
    {
        ToolType Tool { get; }
        bool IsComplete { get; }
        /// <summary>Günlük görev raporu için değer (bulunan sembol / temizlik yüzdesi / buluntu).</summary>
        int ResultValue { get; }
        float Progress01 { get; }
    }

    /// <summary>Büyüteç: mercek gizli sembollerin üzerinde kısa süre tutulunca sembol bulunur.</summary>
    public sealed class MagnifierGame : IDiscoveryMinigame
    {
        public const float BaseLensRadius = 0.11f;
        public const float DwellSeconds = 0.35f;

        public sealed class Symbol { public Vec2 Pos; public float Dwell; public bool Found; public int Glyph; }

        public readonly List<Symbol> Symbols = new List<Symbol>();
        public readonly float LensRadius;
        public ToolType Tool { get { return ToolType.Magnifier; } }

        public MagnifierGame(int symbolCount, float toolMultiplier, ulong seed)
        {
            LensRadius = BaseLensRadius * toolMultiplier;
            var rng = new Rng(seed);
            int guard = 0;
            while (Symbols.Count < symbolCount && guard++ < 1000)
            {
                var p = new Vec2(0.08f + (float)rng.NextDouble() * 0.84f, 0.12f + (float)rng.NextDouble() * 0.76f);
                bool far = true;
                foreach (var s in Symbols) if (Vec2.DistSq(s.Pos, p) < 0.16f * 0.16f) { far = false; break; }
                if (far) Symbols.Add(new Symbol { Pos = p, Glyph = rng.Next(8) });
            }
        }

        /// <summary>Mercek konumuyla ilerletir; yeni bulunan sembolleri döndürür.</summary>
        public List<Symbol> Update(Vec2 lens, float dt)
        {
            var found = new List<Symbol>();
            float r2 = LensRadius * LensRadius;
            foreach (var s in Symbols)
            {
                if (s.Found) continue;
                if (Vec2.DistSq(s.Pos, lens) <= r2)
                {
                    s.Dwell += dt;
                    if (s.Dwell >= DwellSeconds) { s.Found = true; found.Add(s); }
                }
                else s.Dwell = Math.Max(0, s.Dwell - dt * 0.5f);
            }
            return found;
        }

        public int FoundCount { get { int c = 0; foreach (var s in Symbols) if (s.Found) c++; return c; } }
        public bool IsComplete { get { return FoundCount >= Symbols.Count; } }
        public int ResultValue { get { return FoundCount; } }
        public float Progress01 { get { return Symbols.Count == 0 ? 1 : FoundCount / (float)Symbols.Count; } }
    }

    /// <summary>Fırça: toz/kum ızgarası; fırça darbesi daire içindeki tozu azaltır.</summary>
    public sealed class BrushGame : IDiscoveryMinigame
    {
        public const int GridSize = 28;
        public const float BaseRadius = 0.075f;
        public readonly float[,] Dust = new float[GridSize, GridSize];
        public readonly float Radius;
        public readonly int TargetPercent;
        private readonly float _initialTotal;
        public ToolType Tool { get { return ToolType.Brush; } }

        public BrushGame(int targetPercent, float toolMultiplier, ulong seed)
        {
            TargetPercent = targetPercent;
            Radius = BaseRadius * toolMultiplier;
            var rng = new Rng(seed);
            float total = 0;
            for (int y = 0; y < GridSize; y++)
                for (int x = 0; x < GridSize; x++)
                {
                    float v = 0.6f + (float)rng.NextDouble() * 0.6f; // 0.6–1.2 kalınlık
                    Dust[x, y] = v; total += v;
                }
            _initialTotal = total;
        }

        /// <summary>İki nokta arasındaki fırça darbesi. Her darbe yoğunluğu strength kadar azaltır.</summary>
        public void Stroke(Vec2 from, Vec2 to, float strength = 0.35f)
        {
            float len = (float)Math.Sqrt(Vec2.DistSq(from, to));
            int steps = Math.Max(1, (int)(len / (Radius * 0.5f)));
            for (int i = 0; i <= steps; i++)
            {
                float t = i / (float)steps;
                var p = new Vec2(from.X + (to.X - from.X) * t, from.Y + (to.Y - from.Y) * t);
                Dab(p, strength / (steps + 1) * 2f);
            }
        }

        public void Dab(Vec2 p, float strength)
        {
            float r = Radius * GridSize;
            int cx = (int)(p.X * GridSize), cy = (int)(p.Y * GridSize);
            int ri = (int)Math.Ceiling(r);
            for (int y = cy - ri; y <= cy + ri; y++)
                for (int x = cx - ri; x <= cx + ri; x++)
                {
                    if (x < 0 || y < 0 || x >= GridSize || y >= GridSize) continue;
                    float dx = x + 0.5f - p.X * GridSize, dy = y + 0.5f - p.Y * GridSize;
                    float d = (float)Math.Sqrt(dx * dx + dy * dy);
                    if (d > r) continue;
                    float falloff = 1f - d / (r + 0.0001f) * 0.5f;
                    Dust[x, y] = Math.Max(0, Dust[x, y] - strength * falloff);
                }
        }

        public int CleanPercent
        {
            get
            {
                float t = 0; foreach (var v in Dust) t += v;
                return (int)Math.Floor(100f * (1f - t / _initialTotal));
            }
        }

        public bool IsComplete { get { return CleanPercent >= TargetPercent; } }
        public int ResultValue { get { return CleanPercent; } }
        public float Progress01 { get { return Math.Min(1f, CleanPercent / (float)TargetPercent); } }
    }

    /// <summary>Kazı malası: katmanlı toprak hücreleri; her dokunuş katmanı kazır, buluntular ortaya çıkar.</summary>
    public sealed class TrowelGame : IDiscoveryMinigame
    {
        public const int Cols = 6, Rows = 7;
        public sealed class Plot { public float Layers; public int MaxLayers; public bool HasFind; public bool Revealed; }
        public readonly Plot[,] Plots = new Plot[Cols, Rows];
        public readonly float DigPower;
        public readonly int FindCount;
        public ToolType Tool { get { return ToolType.Trowel; } }

        public TrowelGame(int finds, float toolMultiplier, ulong seed)
        {
            DigPower = 1f * toolMultiplier;
            FindCount = Math.Min(finds, Cols * Rows);
            var rng = new Rng(seed);
            for (int y = 0; y < Rows; y++)
                for (int x = 0; x < Cols; x++)
                {
                    int layers = 1 + rng.Next(3);
                    Plots[x, y] = new Plot { Layers = layers, MaxLayers = layers };
                }
            var idx = new int[Cols * Rows];
            for (int i = 0; i < idx.Length; i++) idx[i] = i;
            rng.Shuffle(idx);
            for (int i = 0; i < FindCount; i++) Plots[idx[i] % Cols, idx[i] / Cols].HasFind = true;
        }

        /// <summary>Hücreyi kazır. Buluntu ortaya çıktıysa true.</summary>
        public bool Dig(int x, int y)
        {
            if (x < 0 || y < 0 || x >= Cols || y >= Rows) return false;
            var p = Plots[x, y];
            if (p.Layers <= 0) return false;
            // Ana hücre bir katman kazılır; yükseltilmiş mala (+%20/+%40) komşu hücreleri de o oranda kazır.
            bool revealed = Apply(x, y, 1f);
            float splash = DigPower - 1f;
            if (splash > 0f)
            {
                revealed |= Apply(x + 1, y, splash); revealed |= Apply(x - 1, y, splash);
                revealed |= Apply(x, y + 1, splash); revealed |= Apply(x, y - 1, splash);
            }
            return revealed;
        }

        private bool Apply(int x, int y, float amount)
        {
            if (x < 0 || y < 0 || x >= Cols || y >= Rows) return false;
            var p = Plots[x, y];
            if (p.Layers <= 0) return false;
            p.Layers = Math.Max(0, p.Layers - amount);
            if (p.Layers <= 0.0001f) { p.Layers = 0; if (p.HasFind && !p.Revealed) { p.Revealed = true; return true; } }
            return false;
        }

        public int Revealed { get { int c = 0; foreach (var p in Plots) if (p.Revealed) c++; return c; } }
        public bool IsComplete { get { return Revealed >= FindCount; } }
        public int ResultValue { get { return Revealed; } }
        public float Progress01 { get { return FindCount == 0 ? 1 : Revealed / (float)FindCount; } }
    }

    public static class MinigameFactory
    {
        /// <summary>Kalıcı keşif noktası için mini oyun (alan arttıkça biraz uzar).</summary>
        public static IDiscoveryMinigame ForDiscovery(ToolType tool, int wonder, int area, float multiplier)
        {
            ulong seed = Rng.Hash("disc" + wonder + ":" + area);
            switch (tool)
            {
                case ToolType.Magnifier: return new MagnifierGame(4 + area / 4 + wonder / 3, multiplier, seed);
                case ToolType.Brush: return new BrushGame(80, multiplier, seed);
                default: return new TrowelGame(4 + area / 4 + wonder / 3, multiplier, seed);
            }
        }

        public static IDiscoveryMinigame ForDaily(ToolType tool, int target, float multiplier, string dayKey)
        {
            ulong seed = Rng.Hash("daily" + dayKey + (int)tool);
            switch (tool)
            {
                case ToolType.Magnifier: return new MagnifierGame(target, multiplier, seed);
                case ToolType.Brush: return new BrushGame(target, multiplier, seed);
                default: return new TrowelGame(target, multiplier, seed);
            }
        }
    }
}
