using WorldWonders.Core.Save;

namespace WorldWonders.Core.Services
{
    /// <summary>
    /// Bütün oyun servislerini tek kayıt modeli üzerinde birbirine bağlar.
    /// Unity tarafındaki GameManager bunun bir örneğini tutar.
    /// </summary>
    public sealed class GameSession
    {
        public readonly GameContext Context;
        public readonly RewardService Rewards;
        public readonly XPService Xp;
        public readonly EconomyService Economy;
        public readonly LifeService Lives;
        public readonly StarService Stars;
        public readonly JigsawService Jigsaw;
        public readonly LevelProgressService Levels;
        public readonly ConstructionService Construction;
        public readonly WorldMapService WorldMap;
        public readonly DestinationMapService Destinations;
        public readonly DiscoveryManager Discoveries;
        public readonly MuseumService Museum;
        public readonly ToolUpgradeService Tools;
        public readonly ExplorerCustomizationService Customization;
        public readonly ExplorationStreakService Streak;
        public readonly DailyMissionManager Daily;
        public readonly ProfileService Profile;
        public readonly PersonalRoomService Room;

        public SaveData Save { get { return Context.Save; } }
        public GameEvents Events { get { return Context.Events; } }

        public GameSession(SaveData save, IClock clock)
        {
            save.Normalize();
            Context = new GameContext(save, clock);
            Rewards = new RewardService(Context); Context.Rewards = Rewards;
            Xp = new XPService(Context); Context.Xp = Xp;
            Economy = new EconomyService(Context);
            Lives = new LifeService(Context);
            Stars = new StarService(Context);
            Jigsaw = new JigsawService(Context);
            Levels = new LevelProgressService(Context, Lives, Stars, Jigsaw);
            Jigsaw.Levels = Levels;
            Construction = new ConstructionService(Jigsaw);
            WorldMap = new WorldMapService(Context, Levels, Construction);
            Destinations = new DestinationMapService(Levels, Stars);
            Discoveries = new DiscoveryManager(Context, Destinations);
            Museum = new MuseumService(Discoveries);
            Tools = new ToolUpgradeService(Context, Levels, Economy);
            Customization = new ExplorerCustomizationService(Context);
            Streak = new ExplorationStreakService(Context);
            Daily = new DailyMissionManager(Context, Levels, Streak);
            Profile = new ProfileService(Context, Stars, Jigsaw, Discoveries);
            Room = new PersonalRoomService(Context, Levels, Construction, Tools, Profile);
        }

        /// <summary>Açılışta çağrılır: önceki oturumda terk edilen bölüm için can düşer, canlar ve günlük görevler güncellenir.</summary>
        public bool OnBoot()
        {
            bool abandoned = Levels.AbandonActiveLevel();
            Lives.Tick();
            Daily.Refresh();
            return abandoned;
        }

        /// <summary>Kayıt yazılmadan hemen önce çağrılır.</summary>
        public void BeforeSave()
        {
            Save.SaveCounter++;
            Save.LastSavedUtc = Context.Clock.UtcNow;
        }
    }
}
