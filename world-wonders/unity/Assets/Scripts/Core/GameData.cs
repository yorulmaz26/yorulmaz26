using System.Collections.Generic;

namespace WorldWonders.Core
{
    public enum SigId { Shield, Sandstorm, Lotus, Raid, Lock, Sunlight, Wind }

    public sealed class SigDef
    {
        public SigId Id; public string Name; public int Every; public string Desc;
    }

    public sealed class WonderDef
    {
        public string Id; public string Name; public string Country;
        public string[] Blocks; public string Layer; public string RelicShape; public string RelicName;
        public SigDef Sig;
    }

    /// <summary>Kesinleşmiş tasarım sabitleri ve içerik verisi (prototiple aynı).</summary>
    public static class GameData
    {
        public const int Rows = 8, Cols = 8;
        public const int PiecesPerWonder = 12;      // karar
        public const int LevelsPerPiece = 5;        // karar
        public const int LevelsPerWonder = PiecesPerWonder * LevelsPerPiece;
        public const int MaxLives = 3;              // karar
        public const int LifeRegenSeconds = 20 * 60;// karar
        public const int SigIntroLevel = 21;

        public static readonly string[] StoneNames =
            { "Altın Sikke", "Antik Parşömen", "Antik Amfora", "Defne Çelengi", "Horus’un Gözü", "Yakut Taç" };

        public static readonly Dictionary<string, int> BlockHp = new Dictionary<string, int>
        {
            ["marble"] = 1, ["brick"] = 2, ["rock"] = 1, ["rockHard"] = 2, ["tajMarble"] = 1, ["inlay"] = 2,
            ["wood"] = 1, ["stonewall"] = 2, ["granite"] = 1, ["seal"] = 3, ["mountain"] = 1, ["granite2"] = 2, ["shield"] = 1,
        };

        public static readonly WonderDef[] Wonders =
        {
            new WonderDef { Id="colosseum", Name="Kolezyum", Country="İtalya", Blocks=new[]{"marble","brick"},
                Sig=new SigDef{ Id=SigId.Shield, Name="Gladyatör Kalkanı" } },
            new WonderDef { Id="petra", Name="Petra", Country="Ürdün", Blocks=new[]{"rock","rockHard"}, Layer="sand", RelicShape="urn", RelicName="Nebati Vazosu",
                Sig=new SigDef{ Id=SigId.Sandstorm, Name="Kum Fırtınası", Every=4 } },
            new WonderDef { Id="taj", Name="Tac Mahal", Country="Hindistan", Blocks=new[]{"tajMarble","inlay"}, Layer="garden",
                Sig=new SigDef{ Id=SigId.Lotus, Name="Lotus Havuzu" } },
            new WonderDef { Id="wall", Name="Çin Seddi", Country="Çin", Blocks=new[]{"wood","stonewall"},
                Sig=new SigDef{ Id=SigId.Raid, Name="Akıncı Saldırısı", Every=4 } },
            new WonderDef { Id="machu", Name="Machu Picchu", Country="Peru", Blocks=new[]{"granite"}, Layer="vine", RelicShape="idol", RelicName="İnka Heykelciği",
                Sig=new SigDef{ Id=SigId.Lock, Name="Sarmaşık Kilidi" } },
            new WonderDef { Id="chichen", Name="Chichen Itza", Country="Meksika", Blocks=new[]{"seal"}, Layer="glyph", RelicShape="mask", RelicName="Yeşim Maske",
                Sig=new SigDef{ Id=SigId.Sunlight, Name="Kukulkan Işığı", Every=5 } },
            new WonderDef { Id="christ", Name="Kurtarıcı İsa", Country="Brezilya", Blocks=new[]{"mountain","granite2"}, Layer="cloud", RelicShape="statue", RelicName="Heykel Parçası",
                Sig=new SigDef{ Id=SigId.Wind, Name="Rüzgâr", Every=3 } },
        };

        public static int TotalLevels => LevelsPerWonder * Wonders.Length;
    }
}
