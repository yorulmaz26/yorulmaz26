using System.IO;
using System.Linq;
using Newtonsoft.Json;
using NUnit.Framework;
using WorldWonders.Core;
using WorldWonders.Core.Match3;

namespace WorldWonders.Tests
{
    /// <summary>Depodaki üretilmiş 420 bölüm veri dosyasını doğrular (oyunun gerçekte okuduğu dosyalar).</summary>
    public class LevelFilesTests
    {
        static string Dir => Path.Combine(TestContext.CurrentContext.TestDirectory, "../../../../../Assets/_Project/Resources/Levels");

        [Test]
        public void AllPacksPresentAndValid()
        {
            int total = 0;
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var pack = JsonConvert.DeserializeObject<LevelPack>(File.ReadAllText(Path.Combine(Dir, "wonder_" + w + ".json")));
                Assert.AreEqual(60, pack.Levels.Count);
                for (int i = 0; i < 60; i++)
                {
                    var d = pack.Levels[i];
                    d.Validate();
                    Assert.AreEqual(GameConfig.GlobalLevel(w, i + 1), d.Id);
                    Assert.AreEqual(GameConfig.DifficultyForLocalLevel(i + 1), d.Difficulty);
                    Assert.Greater(d.BotWinRate, 0f);
                    var g = new Match3Game(d);
                    Assert.IsTrue(g.Board.HasPossibleMove());
                    total++;
                }
                var diffs = pack.Levels.GroupBy(l => l.Difficulty).ToDictionary(x => x.Key, x => x.Count());
                Assert.AreEqual(24, diffs[Difficulty.Easy]); Assert.AreEqual(24, diffs[Difficulty.Medium]);
                Assert.AreEqual(9, diffs[Difficulty.Hard]); Assert.AreEqual(3, diffs[Difficulty.VeryHard]);
            }
            Assert.AreEqual(420, total);
        }
    }
}
