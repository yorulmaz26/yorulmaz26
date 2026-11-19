using System.Collections.Generic;
using WorldWonders.Core.Content;

namespace WorldWonders.Core.Services
{
    public sealed class ProfileSnapshot
    {
        public LevelState Level;
        public long TotalXp;
        public List<string> Badges;
        public int LevelsCompleted;
        public int TotalStars;
        public int PiecesPlaced;
        public int WondersCompleted;
        public int ArtifactsCollected;
        public int ChestsOpened;
        public int DailyMissionsCompleted;
        public int DailyAllCompletedDays;
        public int StreakCycles;
        public int LevelsWon;
        public int LevelsLost;
        public string Frame;
    }

    /// <summary>Tamamen çevrimdışı kişisel profil.</summary>
    public sealed class ProfileService
    {
        private readonly GameContext _ctx;
        private readonly StarService _stars;
        private readonly JigsawService _jigsaw;
        private readonly DiscoveryManager _disc;

        public ProfileService(GameContext ctx, StarService stars, JigsawService jigsaw, DiscoveryManager disc) { _ctx = ctx; _stars = stars; _jigsaw = jigsaw; _disc = disc; }

        public ProfileSnapshot Snapshot()
        {
            var s = _ctx.Save;
            var p = new ProfileSnapshot
            {
                Level = _ctx.Xp.Current,
                TotalXp = s.TotalXp,
                Badges = new List<string>(s.Badges),
                LevelsCompleted = s.LevelStars.Count,
                TotalStars = _stars.TotalStars,
                PiecesPlaced = _jigsaw.TotalPlaced,
                DailyMissionsCompleted = s.DailyMissionsCompletedTotal,
                DailyAllCompletedDays = s.DailyAllCompletedDays,
                StreakCycles = s.StreakCycles,
                LevelsWon = s.LevelsWon,
                LevelsLost = s.LevelsLost,
            };
            string frame; s.Equipped.TryGetValue(CosmeticSlot.ProfileFrame, out frame); p.Frame = frame;
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                p.WondersCompleted += s.WonderCompleted[w];
                p.ArtifactsCollected += _disc.ArtifactsCollected(w);
                p.ChestsOpened += _disc.ChestsOpened(w);
            }
            return p;
        }
    }

    public enum RoomExhibitKind { WonderModel, BadgeCase, ToolCase, WorldMap, StudyDesk, MasteryCorner, Decoration }

    public sealed class RoomExhibit
    {
        public RoomExhibitKind Kind;
        public string Id;
        public string Title;
        public string Detail;
        public float Progress; // 0..1, maketler için
        public bool Visible;
    }

    /// <summary>Kişisel Kâşif Odası: müzenin içinde, büyütme sistemi yok; sergiler kayıttan türetilir.</summary>
    public sealed class PersonalRoomService
    {
        private readonly GameContext _ctx;
        private readonly LevelProgressService _levels;
        private readonly ConstructionService _construction;
        private readonly ToolUpgradeService _tools;
        private readonly ProfileService _profile;

        public PersonalRoomService(GameContext ctx, LevelProgressService levels, ConstructionService c, ToolUpgradeService tools, ProfileService profile)
        { _ctx = ctx; _levels = levels; _construction = c; _tools = tools; _profile = profile; }

        public bool IsUnlocked { get { return _levels.IsColosseumLevelCompleted(GameConfig.PersonalRoomUnlockColosseumLevel); } }

        public List<RoomExhibit> Exhibits()
        {
            var list = new List<RoomExhibit>();
            var prof = _profile.Snapshot();
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var info = WonderCatalog.Get(w);
                float pr = _construction.Progress(w);
                list.Add(new RoomExhibit { Kind = RoomExhibitKind.WonderModel, Id = "model_" + info.Id, Title = info.Name + " Maketi", Detail = (int)(pr * 12) + "/12 inşa aşaması", Progress = pr, Visible = true });
            }
            list.Add(new RoomExhibit { Kind = RoomExhibitKind.BadgeCase, Id = "badges", Title = "Rozet Vitrini", Detail = prof.Badges.Count + " rozet", Visible = true });
            list.Add(new RoomExhibit
            {
                Kind = RoomExhibitKind.ToolCase, Id = "tools", Title = "Keşif Ekipmanı",
                Detail = "Büyüteç Sv." + _tools.Level(ToolType.Magnifier) + " · Fırça Sv." + _tools.Level(ToolType.Brush) + " · Mala Sv." + _tools.Level(ToolType.Trowel),
                Visible = true
            });
            list.Add(new RoomExhibit { Kind = RoomExhibitKind.WorldMap, Id = "worldmap", Title = "Dünya Haritası", Detail = prof.WondersCompleted + "/7 harika tamamlandı", Visible = true });
            list.Add(new RoomExhibit { Kind = RoomExhibitKind.StudyDesk, Id = "desk", Title = "Çalışma Masası", Detail = prof.LevelsCompleted + " bölüm · " + prof.TotalStars + " yıldız · " + prof.ArtifactsCollected + " eser", Visible = true });
            list.Add(new RoomExhibit { Kind = RoomExhibitKind.MasteryCorner, Id = "mastery", Title = "Ustalık Köşesi", Detail = prof.Level.Level >= GameConfig.MaxExplorerLevel ? "Ustalık " + prof.Level.Mastery : "Seviye 100'de açılır", Visible = true });
            foreach (var id in _ctx.Save.OwnedCosmetics)
            {
                var c = CosmeticCatalog.Resolve(id);
                if (c != null && c.Slot == CosmeticSlot.MuseumDecoration)
                    list.Add(new RoomExhibit { Kind = RoomExhibitKind.Decoration, Id = id, Title = c.Name, Visible = _ctx.Save.PlacedRoomDecorations.Contains(id) });
            }
            return list;
        }

        public bool ToggleDecoration(string id)
        {
            if (!_ctx.Save.OwnedCosmetics.Contains(id)) return false;
            if (!_ctx.Save.PlacedRoomDecorations.Remove(id)) _ctx.Save.PlacedRoomDecorations.Add(id);
            _ctx.Events.MarkDirty();
            return true;
        }
    }
}
