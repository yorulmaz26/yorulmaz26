using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>
    /// Dünya Müzesi ekranı: 3D müzeyi gösterir, atrium/galeri/Kâşif Odası arasında hızlı geçiş sağlar.
    /// Can gerektirmez; canı olmayan oyuncu da müzeyi gezebilir.
    /// </summary>
    public sealed class MuseumScreen : UIScreen
    {
        private MuseumView _view;
        private Label _total;
        private readonly Button[] _galleryChips = new Button[GameConfig.WonderCount];
        private Button _roomChip;

        protected override void Build(VisualElement root)
        {
            var col = UIKit.Column().Ignore();
            col.style.flexGrow = 1;
            root.Add(col);
            col.Add(UIKit.Spacer(UIKit.HudHeight));

            var header = UIKit.Row();
            header.Pad(UIKit.Gutter, 12);
            header.pickingMode = PickingMode.Ignore;
            header.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 96));
            var titleBox = UIKit.Column();
            titleBox.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(36).Pad(28, 10).Border(2, Palette.CardBorder);
            titleBox.style.marginLeft = 16;
            titleBox.Add(UIKit.H2("Dünya Müzesi"));
            _total = UIKit.Small("");
            titleBox.Add(_total);
            header.Add(titleBox);
            col.Add(header);

            var hint = UIKit.Small("Vitrine dokun: kâşifin yürür ve eseri incelersin. Sürükle: kaydır, iki parmak: yakınlaş.");
            hint.Bg(Palette.WithAlpha(Palette.White, 0.85f)).Radius(24).Pad(20, 8);
            hint.style.marginLeft = UIKit.Gutter; hint.style.marginRight = UIKit.Gutter; hint.style.marginTop = 4;
            col.Add(hint);

            col.Add(UIKit.Flex());

            var bar = new ScrollView(ScrollViewMode.Horizontal);
            bar.horizontalScrollerVisibility = ScrollerVisibility.Hidden;
            bar.verticalScrollerVisibility = ScrollerVisibility.Hidden;
            bar.style.flexShrink = 0;
            bar.style.height = 150;
            bar.contentContainer.style.flexDirection = FlexDirection.Row;
            bar.contentContainer.style.alignItems = Align.Center;
            bar.contentContainer.style.paddingLeft = UIKit.Gutter; bar.contentContainer.style.paddingRight = UIKit.Gutter;
            bar.style.marginBottom = 40;
            col.Add(bar);

            bar.Add(Chip("Atrium", Palette.Gold, () => _view.EnterAtrium()));
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                int captured = w;
                _galleryChips[w] = Chip(WonderCatalog.Get(w).Name, Palette.Hex(WonderCatalog.Get(w).ThemeColor), () => _view.EnterGallery(captured));
                bar.Add(_galleryChips[w]);
            }
            _roomChip = Chip("Kâşif Odası", Palette.Turquoise, OpenRoom);
            bar.Add(_roomChip);
        }

        private static Button Chip(string text, Color accent, System.Action onClick)
        {
            var b = UIKit.BaseButton(text, onClick, Palette.White, Palette.Text);
            b.style.height = 104; b.style.fontSize = UIKit.FontSmall;
            b.Border(3, accent);
            b.style.borderBottomWidth = 8; b.style.borderBottomColor = accent;
            b.style.marginRight = 14; b.style.flexShrink = 0;
            return b;
        }

        private void OpenRoom()
        {
            if (!Session.Room.IsUnlocked)
            {
                _view.EnterPersonalRoom();
                UI.Info("Kâşif Odası kilitli", "Kişisel Kâşif Odası, Kolezyum 10. bölüm tamamlanınca açılır.");
                return;
            }
            Nav.PersonalRoom();
        }

        public override void OnShow()
        {
            _view = Game.ShowView<MuseumView>();
            _view.ExhibitTapped = null;
            _total.text = "Toplanan eser: " + Session.Museum.TotalCollected + "/42";
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                int n = Session.Discoveries.ArtifactsCollected(w);
                _galleryChips[w].text = WonderCatalog.Get(w).Name + "  " + n + "/6" + (n == 6 ? " · tamam" : "");
            }
            _roomChip.text = Session.Room.IsUnlocked ? "Kâşif Odası" : "Kâşif Odası (kilitli)";
        }
    }
}
