using System;
using NUnit.Framework;
using UnityEngine;
using WorldWonders.Core;
using WorldWonders.Core.Save;
using WorldWonders.Core.Services;
using WorldWonders.Game;

namespace WorldWonders.Tests
{
    /// <summary>Unity Test Runner (EditMode): Resources'tan bölüm yükleme ve kayıt dosyası döngüsü.
    /// Saf oyun kuralı testlerinin tamamı Tests/CoreTests (dotnet test) altındadır.</summary>
    public class UnityIntegrationTests
    {
        [Test]
        public void LevelRepositoryLoadsAll420()
        {
            var repo = new LevelRepository();
            for (int g = 1; g <= GameConfig.TotalLevels; g++)
            {
                var d = repo.Get(g);
                Assert.AreEqual(g, d.Id);
                d.Validate();
            }
        }

        [Test]
        public void SaveRoundTripInPersistentPath()
        {
            var dir = System.IO.Path.Combine(Application.temporaryCachePath, "ww_test_" + Guid.NewGuid().ToString("N"));
            var store = new SaveStore(dir);
            var s = new GameSession(SaveData.CreateNew(DateTime.UtcNow), new DeviceClock());
            s.Levels.StartLevel(1); s.Levels.CompleteLevel(1, 3);
            store.Write(s.Save);
            var back = store.Load(DateTime.UtcNow);
            Assert.AreEqual(LoadSource.Main, back.Source);
            Assert.AreEqual(3, back.Data.LevelStars[1]);
            System.IO.Directory.Delete(dir, true);
        }
    }
}
