using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Tamamen çevrimdışı kişisel profil: karakter, seviye, ustalık, rozetler ve istatistikler.</summary>
    public sealed class ProfileScreen : UIScreen
    {
        private ScrollView _content;
        private CharacterPreviewPanel _preview;

        protected override void Build(VisualElement root)
        {
            UIKit.Page(root, "Kâşif Profili", Nav.Back, out _content);
        }

        public override void OnShow()
        {
            _content.Clear();
            var snap = Session.Profile.Snapshot();
            var lv = snap.Level;

            _preview = new CharacterPreviewPanel(620);
            _content.Add(_preview);
            _preview.Show();

            var head = UIKit.Card();
            var hr = UIKit.Row();
            var badge = new VisualElement().Size(130, 130).Radius(65).Bg(Palette.Turquoise).Center();
            if (!string.IsNullOrEmpty(snap.Frame))
            {
                var fr = CosmeticCatalog.Resolve(snap.Frame);
                if (fr != null && fr.ColorHex != null) badge.Border(8, Palette.Hex(fr.ColorHex));
            }
            badge.Add(UIKit.Text(lv.Level.ToString(), UIKit.FontTitle, Palette.White, true));
            hr.Add(badge);
            var hc = UIKit.Column(); hc.style.marginLeft = 24; hc.style.flexGrow = 1; hc.style.flexShrink = 1;
            hc.Add(UIKit.H2(lv.Title));
            if (lv.Level >= GameConfig.MaxExplorerLevel)
            {
                hc.Add(UIKit.Small("Ustalık " + lv.Mastery + " · " + lv.XpIntoMastery + "/" + lv.XpForNextMastery + " XP"));
                var b = new ProgressBar(Palette.Gold, 28); b.Set(lv.XpForNextMastery > 0 ? lv.XpIntoMastery / (float)lv.XpForNextMastery : 0f); b.style.marginTop = 10;
                hc.Add(b);
            }
            else
            {
                hc.Add(UIKit.Small("Seviye " + lv.Level + " · " + lv.XpIntoLevel + "/" + lv.XpForNext + " XP"));
                var b = new ProgressBar(Palette.Turquoise, 28); b.Set(lv.XpIntoLevel / (float)lv.XpForNext); b.style.marginTop = 10;
                hc.Add(b);
            }
            hc.Add(UIKit.Small("Toplam XP: " + snap.TotalXp.ToString("N0")));
            hr.Add(hc);
            head.Add(hr);
            var btns = UIKit.Row(); btns.style.marginTop = 12;
            var c1 = UIKit.Primary("Kâşifi Özelleştir", Nav.Customize); c1.style.flexGrow = 1; c1.style.marginRight = 10;
            var c2 = UIKit.Secondary("Kâşif Odası", Nav.PersonalRoom); c2.style.flexGrow = 1;
            btns.Add(c1); btns.Add(c2);
            head.Add(btns);
            _content.Add(head);

            // İstatistikler
            _content.Add(UIKit.H2("İstatistikler"));
            _content.Add(UIKit.Spacer(10));
            var grid = UIKit.Row(); grid.style.flexWrap = Wrap.Wrap; grid.style.justifyContent = Justify.SpaceBetween;
            grid.Add(Stat(Icon.Play, "Bölümler", snap.LevelsCompleted + "/" + GameConfig.TotalLevels, Palette.Turquoise));
            grid.Add(Stat(Icon.Star, "Yıldızlar", snap.TotalStars + "/" + GameConfig.MaxStarsTotal, Palette.Gold));
            grid.Add(Stat(Icon.Puzzle, "Jigsaw", snap.PiecesPlaced + "/84", Palette.Gold));
            grid.Add(Stat(Icon.Trophy, "Harikalar", snap.WondersCompleted + "/7", Palette.GoldDeep));
            grid.Add(Stat(Icon.Museum, "Eserler", snap.ArtifactsCollected + "/42", Palette.Lilac));
            grid.Add(Stat(Icon.Chest, "Sandıklar", snap.ChestsOpened + "/42", Palette.Terracotta));
            grid.Add(Stat(Icon.Calendar, "Günlük görev", snap.DailyMissionsCompleted.ToString(), Palette.Green));
            grid.Add(Stat(Icon.Check, "Seri döngüsü", snap.StreakCycles.ToString(), Palette.Green));
            grid.Add(Stat(Icon.Heart, "Kazanılan / kaybedilen", snap.LevelsWon + " / " + snap.LevelsLost, Palette.Coral));
            grid.Add(Stat(Icon.Calendar, "Tam günler", snap.DailyAllCompletedDays.ToString(), Palette.Turquoise));
            _content.Add(grid);

            // Harika ilerlemesi
            _content.Add(UIKit.Spacer(10));
            _content.Add(UIKit.H2("Harikalar"));
            _content.Add(UIKit.Spacer(10));
            var wonders = UIKit.Card();
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var info = WonderCatalog.Get(w);
                bool unlocked = Session.Levels.IsWonderUnlocked(w);
                var row = UIKit.Row(); row.style.marginBottom = 16;
                row.Add(new VisualElement().Size(26, 26).Radius(13).Bg(unlocked ? Palette.Hex(info.ThemeColor) : Palette.Locked));
                var col = UIKit.Column(); col.style.marginLeft = 16; col.style.flexGrow = 1;
                int levels = Session.Levels.CompletedCountInWonder(w);
                col.Add(UIKit.Text(info.Name + (unlocked ? "" : " (kilitli)"), UIKit.FontSmall, Palette.Text, true));
                col.Add(UIKit.Small(levels + "/60 bölüm · " + Session.Stars.WonderStars(w) + "/180 yıldız · " + Session.Jigsaw.PlacedCount(w) + "/12 parça · " + Session.Discoveries.ArtifactsCollected(w) + "/6 eser"));
                var bar = new ProgressBar(Palette.Gold, 18); bar.Set(Session.Construction.Progress(w)); bar.style.marginTop = 6;
                col.Add(bar);
                row.Add(col);
                wonders.Add(row);
            }
            _content.Add(wonders);

            // Rozetler
            _content.Add(UIKit.H2("Rozetler (" + snap.Badges.Count + ")"));
            _content.Add(UIKit.Spacer(10));
            if (snap.Badges.Count == 0) _content.Add(UIKit.Small("Henüz rozet yok. Galerileri tamamla, 180 yıldız topla, harikaları inşa et ve keşif serilerini bitir."));
            var bgrid = UIKit.Row(); bgrid.style.flexWrap = Wrap.Wrap;
            foreach (var id in snap.Badges)
            {
                var info = BadgeCatalog.Resolve(id);
                var b = UIKit.Column().Center().Pad(14).Radius(28).Bg(Palette.White).Border(2, Palette.CardBorder);
                b.style.width = Length.Percent(31); b.style.marginRight = Length.Percent(2); b.style.marginBottom = 14;
                b.Add(UIKit.IconImage(Icon.Trophy, Palette.Gold, 64));
                var l = UIKit.Text(info.Name, UIKit.FontTiny, Palette.Text, true); l.style.unityTextAlign = TextAnchor.MiddleCenter;
                b.Add(l);
                var desc = info.Description;
                b.RegisterCallback<ClickEvent>(_ => UI.Info(info.Name, desc));
                bgrid.Add(b);
            }
            _content.Add(bgrid);
            _content.Add(UIKit.Spacer(16));
            _content.Add(UIKit.Small("Profil yalnız bu cihazda tutulur. Hesap, çevrimiçi liderlik tablosu veya arkadaş sistemi yoktur."));
        }

        private static VisualElement Stat(Icon icon, string label, string value, Color tint)
        {
            var c = UIKit.Card();
            c.style.width = Length.Percent(48.5f);
            c.style.flexDirection = FlexDirection.Row; c.style.alignItems = Align.Center;
            c.Add(UIKit.IconImage(icon, tint, 56));
            var col = UIKit.Column(); col.style.marginLeft = 14; col.style.flexShrink = 1;
            col.Add(UIKit.Text(value, UIKit.FontBody, Palette.Text, true));
            col.Add(UIKit.Text(label, UIKit.FontTiny, Palette.TextSoft));
            c.Add(col);
            return c;
        }

        public override void OnHide() { if (_preview != null) _preview.Hide(); }
    }
}
