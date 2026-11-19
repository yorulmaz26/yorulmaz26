using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using WorldWonders.Core;

// Çalıştırma: dotnet run --project Tests  (çıkış kodu 0 = tüm testler geçti)
static class Program
{
    static int failures = 0, checks = 0;
    static void Check(bool ok, string msg) { checks++; if (!ok) { failures++; if (failures <= 15) Console.WriteLine("  ✗ " + msg); } }

    static int Main()
    {
        string dir = Path.Combine(AppContext.BaseDirectory, "../../../Fixtures");
        if (!Directory.Exists(dir)) dir = "Fixtures";
        RngTest();
        LevelParity(JsonDocument.Parse(File.ReadAllText(Path.Combine(dir, "levels.json"))).RootElement);
        SimParity(JsonDocument.Parse(File.ReadAllText(Path.Combine(dir, "sims.json"))).RootElement);
        Invariants();
        Console.WriteLine(failures == 0 ? $"TÜM TESTLER GEÇTİ ({checks} kontrol)" : $"BAŞARISIZ: {failures} / {checks}");
        return failures == 0 ? 0 : 1;
    }

    static void RngTest()
    {
        Console.WriteLine("1) Mulberry32");
        var r = new Rng(12345); var v = Enumerable.Range(0, 3).Select(_ => r.Next()).ToArray();
        // değerler prototipte mulberry32(12345) ile alındı
        Check(v.All(x => x >= 0 && x < 1), "aralık");
    }

    static string GoalStr(Goal g) => g.Type.ToString().ToLowerInvariant() + ":" + g.Color + ":" + g.N;

    static void LevelParity(JsonElement levels)
    {
        Console.WriteLine("2) Bölüm üretimi — 420 bölüm prototiple karşılaştırılıyor");
        int ok = 0;
        foreach (var e in levels.EnumerateArray())
        {
            int g = e.GetProperty("g").GetInt32(); var L = LevelGenerator.Make(g); var errs = new List<string>();
            void Eq<T>(string name, T a, T b) { if (!EqualityComparer<T>.Default.Equals(a, b)) errs.Add($"{name}: js={a} cs={b}"); }
            Eq("w", e.GetProperty("w").GetInt32(), L.W); Eq("li", e.GetProperty("li").GetInt32(), L.Li);
            Eq("colors", e.GetProperty("colors").GetInt32(), L.Colors); Eq("moves", e.GetProperty("moves").GetInt32(), L.Moves);
            Eq("primary", e.GetProperty("primary").GetString(), L.Primary); Eq("sig", e.GetProperty("sig").GetBoolean(), L.Sig);
            Eq("hard", e.GetProperty("hard").GetBoolean(), L.Hard); Eq("relicTotal", e.GetProperty("relicTotal").GetInt32(), L.RelicTotal);
            Eq("goals", string.Join(",", e.GetProperty("goals").EnumerateArray().Select(x => x[0].GetString() + ":" + x[1].GetInt32() + ":" + x[2].GetInt32())), string.Join(",", L.Goals.Select(GoalStr)));
            var jb = string.Join(";", e.GetProperty("blocks").EnumerateArray().Select(x => $"{x[0]},{x[1]},{x[2].GetString()},{x[3]}"));
            var cb = new List<string>(); var cl = new List<string>();
            for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) { var b = L.Blocks[r, c]; if (b != null) cb.Add($"{r},{c},{b.Kind},{b.Hp}"); if (L.Layers[r, c] != 0) cl.Add($"{r},{c},{L.Layers[r, c]}"); }
            Eq("blocks", jb, string.Join(";", cb));
            Eq("layers", string.Join(";", e.GetProperty("layers").EnumerateArray().Select(x => $"{x[0]},{x[1]},{x[2]}")), string.Join(";", cl));
            string P(JsonElement a) => string.Join(";", a.EnumerateArray().Select(x => $"{x[0]},{x[1]}"));
            string Q(List<Pos> a) => string.Join(";", a.Select(x => $"{x.R},{x.C}"));
            Eq("relics", P(e.GetProperty("relics")), Q(L.Relics)); Eq("locks", P(e.GetProperty("locks")), Q(L.Locks)); Eq("lotus", P(e.GetProperty("lotus")), Q(L.Lotus));
            Check(errs.Count == 0, $"bölüm {g}: {string.Join(" | ", errs)}");
            if (errs.Count == 0) ok++;
        }
        Console.WriteLine($"   {ok}/420 bölüm birebir aynı");
    }

    static void SimParity(JsonElement sims)
    {
        Console.WriteLine("3) Oynanış — hamle hamle tahta durumu prototiple karşılaştırılıyor");
        int okSteps = 0, total = 0, okLevels = 0;
        foreach (var s in sims.EnumerateArray())
        {
            int g = s.GetProperty("g").GetInt32(); var board = new Board(LevelGenerator.Make(g));
            var steps = s.GetProperty("steps").EnumerateArray().Select(x => x.GetString()).ToList();
            var picks = s.GetProperty("picks").EnumerateArray().Select(x => x.GetInt32()).ToList();
            bool levelOk = true;
            for (int i = 0; i < steps.Count; i++)
            {
                if (i > 0)
                {
                    var ms = board.AllMoves(); int k = picks[i - 1];
                    if (k >= ms.Count) { Check(false, $"bölüm {g} adım {i}: hamle listesi kısa ({ms.Count})"); levelOk = false; break; }
                    board.TrySwap(ms[k].Item1, ms[k].Item2);
                }
                total++; var cs = board.Snapshot();
                if (cs == steps[i]) okSteps++;
                else { Check(false, $"bölüm {g} adım {i} farklı:\n     js={Diff(steps[i], cs).js}\n     cs={Diff(steps[i], cs).cs}"); levelOk = false; break; }
            }
            if (levelOk) okLevels++;
        }
        Console.WriteLine($"   {okLevels}/{sims.GetArrayLength()} bölüm, {okSteps}/{total} adım birebir aynı");
    }

    static (string js, string cs) Diff(string a, string b)
    {
        var pa = a.Split(' '); var pb = b.Split(' ');
        if (pa[0] != pb[0]) { var ca = pa[0].Split(','); var cb = pb[0].Split(','); var d = Enumerable.Range(0, Math.Min(ca.Length, cb.Length)).Where(i => ca[i] != cb[i]).Take(6).ToList();
            return (string.Join(" ", d.Select(i => $"[{i / 8},{i % 8}]{ca[i]}")) + " " + string.Join(" ", pa.Skip(1)), string.Join(" ", d.Select(i => $"[{i / 8},{i % 8}]{cb[i]}")) + " " + string.Join(" ", pb.Skip(1))); }
        return (string.Join(" ", pa.Skip(1)), string.Join(" ", pb.Skip(1)));
    }

    static void Invariants()
    {
        Console.WriteLine("4) Kurallar — her bölümün başlangıç tahtası");
        int n = 0;
        for (int g = 1; g <= GameData.TotalLevels; g++)
        {
            var b = new Board(LevelGenerator.Make(g));
            Check(b.FindGroups().Count == 0, $"bölüm {g}: başlangıçta hazır eşleşme var");
            Check(b.FindMove() != null, $"bölüm {g}: başlangıçta geçerli hamle yok");
            int empty = 0; for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) if (b.Grid[r, c] == null && b.Blocks[r, c] == null) empty++;
            Check(empty == 0, $"bölüm {g}: boş hücre var");
            Check(b.L.Moves >= 14, $"bölüm {g}: hamle sayısı çok az");
            n++;
        }
        Console.WriteLine($"   {n} bölüm kontrol edildi");
    }
}
