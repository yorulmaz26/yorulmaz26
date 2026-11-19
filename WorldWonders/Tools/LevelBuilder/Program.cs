using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Match3;

// 420 bölümün veri dosyalarını üretir, bot simülasyonuyla dengeler ve ayrı tohumlarla doğrular.
// Kullanım: dotnet run -c Release -- <WorldWonders proje kökü>
class Program
{
    static int Main(string[] args)
    {
        string root = args.Length > 0 ? args[0] : "../..";
        string outDir = Path.Combine(root, "Assets/_Project/Resources/Levels");
        Directory.CreateDirectory(outDir);
        var sw = Stopwatch.StartNew();
        var all = new LevelDefinition[GameConfig.TotalLevels];
        Parallel.For(0, GameConfig.TotalLevels, i =>
        {
            int w = i / 60, l = i % 60 + 1;
            var d = LevelGenerator.Generate(w, l);
            LevelBalancer.Balance(d, 12);
            d.Validate();
            all[i] = d;
        });
        // Bağımsız doğrulama: farklı tohumlarla, verilen hamle sınırıyla kazanma oranı.
        var verify = new float[GameConfig.TotalLevels];
        Parallel.For(0, GameConfig.TotalLevels, i =>
        {
            var d = all[i]; int runs = 12;
            // Hiç kazanılamayan bölümde hamleyi kademeli artır (en az bir doğrulama zaferi şart).
            for (int attempt = 0; attempt < 8; attempt++)
            {
                int wins = 0;
                for (int r = 0; r < runs; r++) if (LevelBot.MovesToWin(d, Rng.Hash("verify" + d.Id + ":" + r), d.Moves) <= d.Moves) wins++;
                verify[i] = wins / (float)runs;
                if (wins > 0 || d.Moves >= 60) break;
                d.Moves = Math.Min(60, d.Moves + 3);
                d.Star2MovesLeft = Math.Max(1, (int)Math.Round(d.Moves * 0.12));
                d.Star3MovesLeft = Math.Max(d.Star2MovesLeft + 1, (int)Math.Round(d.Moves * 0.28));
            }
        });

        var settings = new JsonSerializerSettings { Formatting = Formatting.Indented, Converters = { new StringEnumConverter() } };
        for (int w = 0; w < GameConfig.WonderCount; w++)
        {
            var pack = new LevelPack { Wonder = w, Levels = all.Skip(w * 60).Take(60).ToList() };
            File.WriteAllText(Path.Combine(outDir, "wonder_" + w + ".json"), JsonConvert.SerializeObject(pack, settings));
        }

        var sb = new StringBuilder();
        sb.AppendLine("# Bölüm Denge Raporu");
        sb.AppendLine();
        sb.AppendLine("`Tools/LevelBuilder` tarafından üretildi. Bot açgözlü oynar (insandan zayıf); oranlar alt sınır olarak okunmalıdır.");
        sb.AppendLine("Doğrulama, dengelemede kullanılmayan tohumlarla, bölümün gerçek hamle sınırıyla yapılır.");
        sb.AppendLine();
        sb.AppendLine("| Harika | Kolay | Orta | Zor | Çok zor | Ort. hamle | 9x9 | Engelli bölüm |");
        sb.AppendLine("|---|---|---|---|---|---|---|---|");
        for (int w = 0; w < 7; w++)
        {
            var ls = all.Skip(w * 60).Take(60).ToList();
            var vs = verify.Skip(w * 60).Take(60).ToList();
            string Rate(Difficulty d) { var idx = Enumerable.Range(0, 60).Where(i => ls[i].Difficulty == d).ToList(); return "%" + (int)Math.Round(100 * idx.Average(i => vs[i])); }
            sb.AppendLine($"| {WonderCatalog.Get(w).Name} | {Rate(Difficulty.Easy)} | {Rate(Difficulty.Medium)} | {Rate(Difficulty.Hard)} | {Rate(Difficulty.VeryHard)} | {ls.Average(x => x.Moves):0.0} | {ls.Count(x => x.Width == 9 || x.Height == 9)} | {ls.Count(x => x.Goals.Any(g => g.Type != GoalType.Collect && g.Type != GoalType.Score))} |");
        }
        sb.AppendLine();
        var zero = Enumerable.Range(0, 420).Where(i => verify[i] == 0).Select(i => all[i].Id).ToList();
        sb.AppendLine("Bot tarafından doğrulamada hiç kazanılamayan bölümler: " + (zero.Count == 0 ? "yok" : string.Join(", ", zero)));
        sb.AppendLine();
        File.WriteAllText(Path.Combine(root, "Docs/LEVEL_REPORT.md"), sb.ToString());
        Console.WriteLine(sb.ToString());
        return 0;
    }
}
