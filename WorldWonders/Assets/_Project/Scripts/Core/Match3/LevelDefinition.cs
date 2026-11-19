using System;
using System.Collections.Generic;

namespace WorldWonders.Core.Match3
{
    public enum GoalType { Collect = 0, ClearSand = 1, BreakCrates = 2, ReleaseChains = 3, Score = 4 }

    [Serializable]
    public sealed class GoalDefinition
    {
        public GoalType Type;
        public TileColor Color = TileColor.None; // yalnız Collect
        public int Count;
    }

    /// <summary>
    /// Bir Match-3 bölümünün verisi. 420 bölüm JSON veri dosyalarında saklanır (Resources/Levels/wonder_N.json).
    /// Layout satırları üstten alta: '.' oynanabilir, 'x' boşluk (düzensiz tahta),
    /// 's' 1 katman kum, 'S' 2 katman kum, 'c' 1 canlı sandık, 'C' 2 canlı sandık, 'l' zincirli taş.
    /// </summary>
    [Serializable]
    public sealed class LevelDefinition
    {
        public int Id;
        public int Wonder;
        public int Local;
        public Difficulty Difficulty;
        public int Width;
        public int Height;
        public string[] Layout;
        public int Colors = 6;
        public int Moves;
        public List<GoalDefinition> Goals = new List<GoalDefinition>();
        public int Star2MovesLeft;
        public int Star3MovesLeft;
        public ulong Seed;
        public string ShapeName;
        /// <summary>Denge botunun ölçtüğü kazanma oranı (bilgi amaçlı).</summary>
        public float BotWinRate;

        public char At(int x, int y) { return Layout[y][x]; }

        public void Validate()
        {
            if (Width < 7 || Width > 9 || Height < 7 || Height > 9) throw new InvalidOperationException("Tahta boyutu 7–9 olmalı: " + Id);
            if (Layout == null || Layout.Length != Height) throw new InvalidOperationException("Layout satır sayısı hatalı: " + Id);
            foreach (var row in Layout) if (row.Length != Width) throw new InvalidOperationException("Layout sütun sayısı hatalı: " + Id);
            if (Colors < 4 || Colors > 6) throw new InvalidOperationException("Renk sayısı 4–6 olmalı: " + Id);
            if (Moves <= 0) throw new InvalidOperationException("Hamle sayısı pozitif olmalı: " + Id);
            if (Goals == null || Goals.Count == 0) throw new InvalidOperationException("Hedef yok: " + Id);
            if (Star3MovesLeft < Star2MovesLeft) throw new InvalidOperationException("Yıldız eşikleri hatalı: " + Id);
        }
    }

    [Serializable]
    public sealed class LevelPack
    {
        public int Wonder;
        public List<LevelDefinition> Levels = new List<LevelDefinition>();
    }
}
