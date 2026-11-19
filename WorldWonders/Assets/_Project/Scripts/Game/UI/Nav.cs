using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Uygulama içi gezinme. Bütün ekran geçişleri buradan yapılır (sahte buton yok: her hedef gerçek bir ekrandır).</summary>
    public static class Nav
    {
        private static GameManager G { get { return GameManager.I; } }
        private static GameSession S { get { return GameManager.I.Session; } }

        private static bool IsTop<T>() where T : UIScreen { return G.UI.Current is T; }

        public static void WorldMap() { G.UI.ResetTo(new WorldMapScreen()); }

        public static void Destination(int wonder)
        {
            if (!S.Levels.IsWonderUnlocked(wonder))
            {
                G.UI.Info(WonderCatalog.Get(wonder).Name, "Bu harika, önceki harikanın 60 bölümü tamamlanıp 12 Jigsaw parçası yerleştirildiğinde açılır.");
                return;
            }
            var cur = G.UI.Current as DestinationScreen;
            if (cur != null && cur.Wonder == wonder) return;
            if (!G.UI.PopTo<WorldMapScreen>()) G.UI.ResetTo(new WorldMapScreen());
            G.UI.Push(new DestinationScreen(wonder));
        }

        public static void LevelPreview(int globalLevel) { LevelPreviewPopup.Show(globalLevel); }

        public static void PlayLevel(int globalLevel)
        {
            var err = S.Levels.CanStart(globalLevel);
            if (err == StartLevelError.Locked) { G.UI.Info("Bölüm kilitli", "Önce önceki bölümü tamamla."); return; }
            if (err == StartLevelError.NoLives) { NoLives(); return; }
            G.UI.CloseAllPopups();
            G.UI.Push(new Match3Screen(globalLevel));
        }

        public static void NoLives()
        {
            var t = S.Lives.TimeToNextLife();
            G.UI.Confirm("Canın kalmadı",
                string.Format("Bir sonraki can {0:00}:{1:00} sonra dolacak. Bu sırada müzeyi gezebilir, keşif görevlerini yapabilir ya da kâşifini özelleştirebilirsin.", (int)t.TotalMinutes, t.Seconds),
                "Müzeye git", Museum, "Tamam");
        }

        public static void Jigsaw(int wonder, int highlightPiece = -1)
        {
            if (IsTop<JigsawScreen>()) return;
            G.UI.Push(new JigsawScreen(wonder, highlightPiece));
        }

        public static void Museum() { if (!IsTop<MuseumScreen>()) G.UI.Push(new MuseumScreen()); }

        public static void PersonalRoom()
        {
            if (!S.Room.IsUnlocked) { G.UI.Info("Kâşif Odası", "Kişisel Kâşif Odası, Kolezyum 10. bölüm tamamlanınca açılır."); return; }
            if (!IsTop<PersonalRoomScreen>()) G.UI.Push(new PersonalRoomScreen());
        }

        public static void Discovery(int wonder, int area)
        {
            var st = S.Discoveries.StateOf(wonder, area);
            if (st == DiscoveryState.Locked) { G.UI.Info("Keşif kilitli", "Bu keşif noktası, alanı açıldığında erişilebilir olur."); return; }
            if (st == DiscoveryState.Done) { G.UI.Info("Keşfedildi", "Bu noktayı zaten keşfettin."); return; }
            G.UI.Push(MinigameScreen.ForDiscovery(wonder, area));
        }

        public static void Daily()
        {
            if (!S.Daily.IsUnlocked) { G.UI.Info("Günlük Keşif Görevleri", "Günlük görevler Kolezyum 5. bölüm tamamlanınca açılır."); return; }
            if (!IsTop<DailyMissionsScreen>()) G.UI.Push(new DailyMissionsScreen());
        }

        public static void DailyMinigame(ToolType tool) { G.UI.Push(MinigameScreen.ForDaily(tool)); }
        public static void Profile() { if (!IsTop<ProfileScreen>()) G.UI.Push(new ProfileScreen()); }
        public static void Customize() { if (!IsTop<CustomizationScreen>()) G.UI.Push(new CustomizationScreen()); }
        public static void Store() { if (!IsTop<StoreScreen>()) G.UI.Push(new StoreScreen()); }
        public static void Tools() { if (!IsTop<ToolsScreen>()) G.UI.Push(new ToolsScreen()); }
        public static void Settings() { if (!IsTop<SettingsScreen>()) G.UI.Push(new SettingsScreen()); }
        public static void ArtifactInspect(ArtifactInfo a) { G.UI.Push(new ArtifactInspectScreen(a)); }
        public static void Back() { G.UI.Back(); }
    }
}
