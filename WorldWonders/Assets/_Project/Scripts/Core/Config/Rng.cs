using System;

namespace WorldWonders.Core
{
    /// <summary>Deterministik, platformdan bağımsız RNG (xorshift64*). Seviye üretimi ve testler için.</summary>
    public sealed class Rng
    {
        private ulong _state;

        public Rng(ulong seed) { _state = seed == 0 ? 0x9E3779B97F4A7C15UL : seed; }

        public ulong State { get { return _state; } set { _state = value == 0 ? 0x9E3779B97F4A7C15UL : value; } }

        public ulong NextULong()
        {
            ulong x = _state;
            x ^= x >> 12; x ^= x << 25; x ^= x >> 27;
            _state = x;
            return x * 0x2545F4914F6CDD1DUL;
        }

        /// <summary>[0, maxExclusive)</summary>
        public int Next(int maxExclusive)
        {
            if (maxExclusive <= 0) throw new ArgumentOutOfRangeException(nameof(maxExclusive));
            return (int)(NextULong() % (ulong)maxExclusive);
        }

        public int Range(int minInclusive, int maxExclusive) { return minInclusive + Next(maxExclusive - minInclusive); }

        public double NextDouble() { return (NextULong() >> 11) * (1.0 / 9007199254740992.0); }

        public bool Chance(double p) { return NextDouble() < p; }

        public void Shuffle<T>(T[] arr)
        {
            for (int i = arr.Length - 1; i > 0; i--) { int j = Next(i + 1); T t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
        }

        public void Shuffle<T>(System.Collections.Generic.IList<T> arr)
        {
            for (int i = arr.Count - 1; i > 0; i--) { int j = Next(i + 1); T t = arr[i]; arr[i] = arr[j]; arr[j] = t; }
        }

        public static ulong Hash(string s)
        {
            // FNV-1a 64
            ulong h = 14695981039346656037UL;
            foreach (char c in s) { h ^= c; h *= 1099511628211UL; }
            return h;
        }
    }

    public interface IClock
    {
        DateTime UtcNow { get; }
        /// <summary>Yerel saat farkı (günlük 04:00 sıfırlaması için).</summary>
        TimeSpan LocalOffset { get; }
    }

    public sealed class SystemClock : IClock
    {
        public DateTime UtcNow { get { return DateTime.UtcNow; } }
        public TimeSpan LocalOffset { get { return TimeZoneInfo.Local.GetUtcOffset(DateTime.UtcNow); } }
    }

    /// <summary>Testler için elle ilerletilen saat.</summary>
    public sealed class ManualClock : IClock
    {
        public DateTime UtcNow { get; set; }
        public TimeSpan LocalOffset { get; set; }
        public ManualClock(DateTime utc) { UtcNow = DateTime.SpecifyKind(utc, DateTimeKind.Utc); LocalOffset = TimeSpan.FromHours(3); }
        public void Advance(TimeSpan t) { UtcNow = UtcNow + t; }
    }
}
