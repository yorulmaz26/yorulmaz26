using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Ana ekran: 3D dünya haritası dioraması üzerinde bölge etiketleri, "Devam Et" kartı ve alt menü.
    /// Bölgeye (3D ya da etikete) dokunmak doğrudan destinasyon haritasını açar.
    /// </summary>
    public sealed class WorldMapScreen : UIScreen
    {
        private VisualElement _labels, _cardHost, _menuHost;
        private readonly VisualElement[] _tags = new VisualElement[GameConfig.WonderCount];
        private WorldMapView _view;

        protected override void Build(VisualElement root)
        {
            _labels = new VisualElement().Fill().Ignore();
            root.Add(_labels);
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                _tags[w] = new VisualElement();
                _tags[w].Absolute(0, 0);
                _tags[w].style.translate = new Translate(Length.Percent(-50), Length.Percent(-100), 0);
                _labels.Add(_tags[w]);
            }

            var note = UIKit.Pill("Geçici 3D görseller", Palette.WithAlpha(Palette.Text, 0.55f));
            note.style.fontSize = UIKit.FontTiny;
            note.Absolute(UIKit.Gutter, UIKit.HudHeight + 6f);
            note.pickingMode = PickingMode.Ignore;
            root.Add(note);

            var bottom = UIKit.Column();
            bottom.Absolute(0, null, 0, 0);
            bottom.pickingMode = PickingMode.Ignore;
            bottom.style.paddingBottom = 24;
            _cardHost = UIKit.Column();
            _cardHost.style.paddingLeft = UIKit.Gutter; _cardHost.style.paddingRight = UIKit.Gutter;
            _cardHost.pickingMode = PickingMode.Ignore;
            bottom.Add(_cardHost);
            _menuHost = new VisualElement();
            _menuHost.pickingMode = PickingMode.Ignore;
            bottom.Add(_menuHost);
            root.Add(bottom);
        }

        public override void OnShow()
        {
            _view = Game.ShowView<WorldMapView>();
            _view.AfterCamera -= PositionTags;
            _view.AfterCamera += PositionTags;
            BuildTags();
            BuildCard();
            BuildMenu();
        }

        public override void OnHide()
        {
            if (_view != null) _view.AfterCamera -= PositionTags;
        }

        // ------------------------------------------------------------- bölge etiketleri
        private void BuildTags()
        {
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var tag = _tags[w];
                tag.Clear();
                var info = WonderCatalog.Get(w);
                var st = Session.WorldMap.StateOf(w);
                var box = UIKit.Column();
                box.style.alignItems = Align.Center;
                box.Bg(Palette.WithAlpha(Palette.White, 0.94f)).Radius(30).Pad(22, 10).Border(3, st == RegionState.Completed ? Palette.Gold : st == RegionState.InProgress ? Palette.Turquoise : Palette.Locked);
                var row = UIKit.Row();
                if (st == RegionState.Locked) row.Add(UIKit.IconImage(Icon.Lock, Palette.TextSoft, 34));
                if (st == RegionState.Completed) row.Add(UIKit.IconImage(Icon.Check, Palette.GoldDeep, 34));
                var name = UIKit.Text(info.Name, UIKit.FontSmall, st == RegionState.Locked ? Palette.TextSoft : Palette.Text, true);
                name.style.marginLeft = 6;
                row.Add(name);
                box.Add(row);
                if (st != RegionState.Locked)
                {
                    int placed = Session.Jigsaw.PlacedCount(w);
                    int done = Session.Levels.CompletedCountInWonder(w);
                    var bar = new ProgressBar(st == RegionState.Completed ? Palette.Gold : Palette.Turquoise, 16);
                    bar.style.width = 170; bar.style.flexGrow = 0; bar.style.marginTop = 6;
                    bar.Set(placed / (float)GameConfig.PiecesPerWonder);
                    box.Add(bar);
                    box.Add(UIKit.Text(done + "/60 bölüm · " + placed + "/12 parça", UIKit.FontTiny, Palette.TextSoft));
                }
                else box.Add(UIKit.Text(info.Country, UIKit.FontTiny, Palette.TextSoft));
                int wonder = w;
                box.pickingMode = PickingMode.Position;
                box.RegisterCallback<ClickEvent>(_ => Nav.Destination(wonder));
                tag.Add(box);
            }
            PositionTags();
        }

        private void PositionTags()
        {
            if (_view == null || Root.panel == null || Game.Cam == null) return;
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var sp = Game.Cam.WorldToScreenPoint(_view.LabelAnchor(w));
                var tag = _tags[w];
                if (sp.z <= 0f) { tag.style.display = DisplayStyle.None; continue; }
                var panelPos = RuntimePanelUtils.ScreenToPanel(Root.panel, new Vector2(sp.x, Screen.height - sp.y));
                var local = _labels.WorldToLocal(panelPos);
                bool visible = local.y > UIKit.HudHeight + 40f && local.y < _labels.layout.height - 360f;
                tag.style.display = visible ? DisplayStyle.Flex : DisplayStyle.None;
                tag.style.left = local.x;
                tag.style.top = local.y;
            }
        }

        // ------------------------------------------------------------- devam kartı
        private void BuildCard()
        {
            _cardHost.Clear();
            var s = Session;
            int g = s.Levels.CurrentLevel;
            int w = GameConfig.WonderOfLevel(g);
            int local = GameConfig.LocalLevel(g);
            var info = WonderCatalog.Get(w);
            bool allDone = s.Save.WonderCompleted[GameConfig.WonderCount - 1] == 1;
            int unplaced = s.Jigsaw.UnplacedPieces(w).Count;

            var card = UIKit.Card();
            card.style.marginBottom = 14;
            var top = UIKit.Row();
            top.Add(UIKit.IconImage(Icon.Map, Palette.Turquoise, 64));
            var col = UIKit.Column(); col.style.marginLeft = 18; col.style.flexGrow = 1; col.style.flexShrink = 1;
            col.Add(UIKit.H2(info.Name));
            string sub;
            if (allDone) sub = "Bütün harikalar tamamlandı! Yıldızları tamamlamak için bölümleri yeniden oynayabilirsin.";
            else if (s.Levels.IsCompleted(g)) sub = info.Country + " · 60 bölüm tamamlandı. Sonraki harikayı açmak için parçaları yerleştir.";
            else sub = info.Country + " · Bölüm " + local + " / 60 (" + Names.Difficulty(GameConfig.DifficultyForLocalLevel(local)) + ")";
            col.Add(UIKit.Small(sub));
            top.Add(col);
            card.Add(top);

            var bar = new ProgressBar(Palette.Turquoise, 22);
            bar.style.marginTop = 16; bar.style.flexGrow = 0;
            bar.Set(s.Levels.CompletedCountInWonder(w) / (float)GameConfig.LevelsPerWonder);
            card.Add(bar);

            var buttons = UIKit.Row();
            buttons.style.marginTop = 10;
            var go = UIKit.Primary("Devam Et", () => Nav.Destination(w));
            go.style.flexGrow = 1;
            buttons.Add(go);
            if (unplaced > 0)
            {
                var jig = UIKit.Gold("Parça Yerleştir (" + unplaced + ")", () => Nav.Jigsaw(w, -1));
                jig.style.flexGrow = 1; jig.style.marginLeft = 16;
                buttons.Add(jig);
            }
            card.Add(buttons);
            _cardHost.Add(card);
        }

        // ------------------------------------------------------------- alt menü
        private void BuildMenu()
        {
            _menuHost.Clear();
            var scroll = new ScrollView(ScrollViewMode.Horizontal);
            scroll.horizontalScrollerVisibility = ScrollerVisibility.Hidden;
            scroll.verticalScrollerVisibility = ScrollerVisibility.Hidden;
            scroll.contentContainer.style.flexDirection = FlexDirection.Row;
            scroll.contentContainer.style.paddingLeft = UIKit.Gutter - 8; scroll.contentContainer.style.paddingRight = UIKit.Gutter - 8;
            scroll.style.height = 186;

            scroll.Add(UIKit.MenuTile(Icon.Museum, "Müze", Nav.Museum, Palette.GoldDeep));
            bool dailyOpen = Session.Daily.IsUnlocked;
            var daily = UIKit.MenuTile(Icon.Calendar, "Günlük", Nav.Daily, Palette.Green, !dailyOpen);
            if (dailyOpen)
            {
                int remaining = 0;
                foreach (var m in Session.Daily.Missions) if (!m.Completed) remaining++;
                if (remaining > 0)
                {
                    var dot = UIKit.Text(remaining.ToString(), UIKit.FontTiny, Palette.White, true).Bg(Palette.Coral).Radius(22).Pad(12, 2);
                    dot.Absolute(null, 6, 10, null);
                    daily.Add(dot);
                }
            }
            scroll.Add(daily);
            scroll.Add(UIKit.MenuTile(Icon.User, "Profil", Nav.Profile, Palette.Turquoise));
            scroll.Add(UIKit.MenuTile(Icon.Shirt, "Kâşif", Nav.Customize, Palette.Lilac));
            scroll.Add(UIKit.MenuTile(Icon.Shop, "Mağaza", Nav.Store, Palette.Gold));
            scroll.Add(UIKit.MenuTile(Icon.Magnifier, "Araçlar", Nav.Tools, Palette.TurquoiseDeep));
            scroll.Add(UIKit.MenuTile(Icon.Gear, "Ayarlar", Nav.Settings, Palette.TextSoft));
            _menuHost.Add(scroll);
        }

        public override bool OnBack() { return true; } // kök ekran: geri tuşu uygulamadan çıkarmaz
    }
}
