using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Destinasyon haritası üst katmanı: harika bilgisi, ilerleme, alan adı, Jigsaw/müze/görev kısayolları ve "Oyna".</summary>
    public sealed class DestinationScreen : UIScreen
    {
        public readonly int Wonder;
        private Label _title, _area;
        private VisualElement _levelsChip, _starsChip, _piecesChip;
        private Button _play;
        private VisualElement _jigsawTile;
        private Label _jigsawBadge;
        private float _t;

        public DestinationScreen(int wonder) { Wonder = wonder; }

        protected override void Build(VisualElement root)
        {
            var info = WonderCatalog.Get(Wonder);

            // Üst: geri, başlık, ilerleme
            var top = UIKit.Column().Ignore();
            top.Absolute(0, UIKit.HudHeight, 0, null);
            top.Pad(UIKit.Gutter, 6);
            var head = UIKit.Row();
            head.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 100));
            var titleCard = UIKit.Column();
            titleCard.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(36).Pad(30, 14).Border(2, Palette.CardBorder);
            titleCard.style.marginLeft = 12; titleCard.style.flexGrow = 1;
            _title = UIKit.H2(info.Name);
            titleCard.Add(_title);
            _area = UIKit.Small(info.Country + " · " + info.DestinationTheme);
            titleCard.Add(_area);
            head.Add(titleCard);
            top.Add(head);

            var stats = UIKit.Row().Ignore();
            stats.style.marginTop = 14;
            _levelsChip = UIKit.Chip(Icon.Play, "0/60", Palette.Turquoise);
            _starsChip = UIKit.Chip(Icon.Star, "0/180", Palette.Gold);
            _piecesChip = UIKit.Chip(Icon.Puzzle, "0/12", Palette.Lilac);
            _starsChip.style.marginLeft = 10; _piecesChip.style.marginLeft = 10;
            stats.Add(_levelsChip); stats.Add(_starsChip); stats.Add(_piecesChip);
            top.Add(stats);
            root.Add(top);

            // Alt: kısayollar ve oyna
            var bottom = UIKit.Column().Ignore();
            bottom.Absolute(0, null, 0, 0);
            bottom.Pad(UIKit.Gutter, 36);
            var tiles = UIKit.Row().Ignore();
            tiles.style.justifyContent = Justify.Center;
            _jigsawTile = UIKit.MenuTile(Icon.Puzzle, "Jigsaw & Anıt", () => Nav.Jigsaw(Wonder, -1), Palette.Gold);
            _jigsawBadge = UIKit.Text("", UIKit.FontTiny, Palette.White, true);
            _jigsawBadge.Bg(Palette.Coral).Radius(22).Pad(14, 4);
            _jigsawBadge.Absolute(null, -8, -8, null);
            _jigsawTile.Add(_jigsawBadge);
            tiles.Add(_jigsawTile);
            tiles.Add(UIKit.MenuTile(Icon.Museum, "Müze", Nav.Museum, Palette.Turquoise));
            tiles.Add(UIKit.MenuTile(Icon.Calendar, "Görevler", Nav.Daily, Palette.Green, !Session.Daily.IsUnlocked));
            tiles.Add(UIKit.MenuTile(Icon.Shirt, "Kâşif", Nav.Customize, Palette.Lilac));
            bottom.Add(tiles);
            bottom.Add(UIKit.Spacer(12));
            _play = UIKit.Gold("Oyna", OnPlay);
            _play.style.height = 130; _play.style.fontSize = UIKit.FontH2;
            bottom.Add(_play);
            root.Add(bottom);
        }

        private int CurrentLocal()
        {
            var d = Session.Destinations;
            for (int l = 1; l <= GameConfig.LevelsPerWonder; l++)
                if (d.NodeStateOf(Wonder, l) == NodeState.Current) return l;
            return Session.Levels.CompletedCountInWonder(Wonder) >= GameConfig.LevelsPerWonder ? GameConfig.LevelsPerWonder : 1;
        }

        private void OnPlay()
        {
            if (Session.Levels.CompletedCountInWonder(Wonder) >= GameConfig.LevelsPerWonder && Session.Jigsaw.PlacedCount(Wonder) < GameConfig.PiecesPerWonder)
            {
                Nav.Jigsaw(Wonder, -1);
                return;
            }
            int local = CurrentLocal();
            var view = Game.GetView<DestinationMapView>();
            view.FocusLevel(local);
            Nav.LevelPreview(GameConfig.GlobalLevel(Wonder, local));
        }

        public override void OnShow()
        {
            var view = Game.ShowView<DestinationMapView>();
            view.ShowWonder(Wonder);
            Refresh();
        }

        private void Refresh()
        {
            var s = Session;
            int done = s.Levels.CompletedCountInWonder(Wonder);
            UIKit.SetChip(_levelsChip, done + "/" + GameConfig.LevelsPerWonder);
            UIKit.SetChip(_starsChip, s.Stars.WonderStars(Wonder) + "/" + GameConfig.MaxStarsPerWonder);
            UIKit.SetChip(_piecesChip, s.Jigsaw.PlacedCount(Wonder) + "/" + GameConfig.PiecesPerWonder);
            int unplaced = s.Jigsaw.UnplacedPieces(Wonder).Count;
            _jigsawBadge.text = unplaced.ToString();
            _jigsawBadge.style.display = unplaced > 0 ? DisplayStyle.Flex : DisplayStyle.None;
            if (done >= GameConfig.LevelsPerWonder)
                _play.text = s.Jigsaw.PlacedCount(Wonder) < GameConfig.PiecesPerWonder ? "Parçaları yerleştir" : "Tekrar oyna";
            else
                _play.text = "Oyna · Bölüm " + CurrentLocal();
        }

        public override void Tick(float dt)
        {
            _t += dt;
            if (_t < 0.3f) return;
            _t = 0f;
            var view = Game.GetView<DestinationMapView>();
            if (view.Wonder != Wonder) return;
            int area = view.AreaAtPivot();
            var info = WonderCatalog.Get(Wonder);
            string lockTxt = Session.Destinations.IsAreaUnlocked(Wonder, area) ? "" : " (kilitli)";
            _area.text = "Alan " + area + " · " + info.AreaNames[area - 1] + lockTxt;
        }
    }
}
