using System.Collections.Generic;

namespace WorldWonders.Core
{
    /// <summary>
    /// Mulberry32 — web prototipindeki mulberry32 ile bit düzeyinde aynı.
    /// Aynı tohum her platformda aynı bölümü üretir.
    /// </summary>
    public sealed class Rng
    {
        private int _a;
        public Rng(int seed) { _a = seed; }

        public double Next()
        {
            unchecked
            {
                _a = _a + 0x6D2B79F5;
                int a = _a;
                int t = (a ^ (int)((uint)a >> 15)) * (1 | a);
                t = (t + (t ^ (int)((uint)t >> 7)) * (61 | t)) ^ t;
                return (uint)(t ^ (int)((uint)t >> 14)) / 4294967296.0;
            }
        }

        /// <summary>JS: Math.floor(rnd() * n)</summary>
        public int NextInt(int n) => (int)System.Math.Floor(Next() * n);

        /// <summary>Fisher–Yates, prototipteki shuffleArr ile aynı sırayla.</summary>
        public void Shuffle<T>(IList<T> list)
        {
            for (int i = list.Count - 1; i > 0; i--)
            {
                int j = (int)System.Math.Floor(Next() * (i + 1));
                (list[i], list[j]) = (list[j], list[i]);
            }
        }
    }

    public static class JsMath
    {
        /// <summary>JavaScript Math.round (yarım yukarı yuvarlar; C#'ın bankacı yuvarlamasından farklı).</summary>
        public static int Round(double x) => (int)System.Math.Floor(x + 0.5);
    }
}
