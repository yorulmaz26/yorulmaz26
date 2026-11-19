using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Kişisel Kâşif Odası (müzenin içinde, büyütme yok): maketler, rozet vitrini, ekipman, dünya haritası,
    /// çalışma masası, ustalık köşesi ve dekorasyonlar. Sergiler kayıttan türetilir.
    /// </summary>
    public sealed class PersonalRoomScreen : UIScreen
    {
        private MuseumView _view;
        private VisualElement _list, _detail;
        private Label _detailTitle, _detailText;

        protected override void Build(VisualElement root)
        {
            var col = UIKit.Column().Ignore();
            col.style.flexGrow = 1;
            root.Add(col);
            col.Add(UIKit.Spacer(UIKit.HudHeight));
            var header = UIKit.Row().Ignore();
            header.Pad(UIKit.Gutter, 12);
            header.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 96));
            var title = UIKit.H2("Kâşif Odası");
            title.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(36).Pad(28, 12).Border(2, Palette.CardBorder);
            title.style.marginLeft = 16;
            header.Add(title);
            col.Add(header);

            col.Add(UIKit.Flex());

            _detail = UIKit.Card();
            _detail.style.marginLeft = UIKit.Gutter; _detail.style.marginRight = UIKit.Gutter;
            _detailTitle = UIKit.H2("");
            _detailText = UIKit.Body("");
            _detail.Add(_detailTitle);
            _detail.Add(UIKit.Spacer(8));
            _detail.Add(_detailText);
            _detail.style.display = DisplayStyle.None;
            col.Add(_detail);

            var panel = UIKit.Column();
            panel.Bg(Palette.WithAlpha(Palette.Cream, 0.96f)).Radius(40).Border(2, Palette.CardBorder);
            panel.style.height = 560; panel.style.flexShrink = 0;
            panel.style.marginLeft = 20; panel.style.marginRight = 20; panel.style.marginBottom = 30;
            panel.style.paddingTop = 20;
            var scroll = UIKit.Scroll();
            scroll.contentContainer.style.paddingLeft = 24; scroll.contentContainer.style.paddingRight = 24; scroll.contentContainer.style.paddingBottom = 30;
            _list = scroll.contentContainer;
            panel.Add(scroll);
            col.Add(panel);
        }

        public override void OnShow()
        {
            _view = Game.ShowView<MuseumView>();
            _view.EnterPersonalRoom();
            _view.ExhibitTapped = ShowDetail;
            Rebuild();
        }

        public override void OnHide()
        {
            if (_view != null) _view.ExhibitTapped = null;
        }

        private void Rebuild()
        {
            _list.Clear();
            var exhibits = Session.Room.Exhibits();
            _list.Add(UIKit.H2("Sergiler"));
            _list.Add(UIKit.Spacer(10));
            var decorations = new List<RoomExhibit>();
            foreach (var ex in exhibits)
            {
                if (ex.Kind == RoomExhibitKind.Decoration) { decorations.Add(ex); continue; }
                var captured = ex;
                var row = UIKit.Row();
                row.Bg(Palette.White).Radius(28).Pad(24, 14).Border(2, Palette.CardBorder);
                row.style.marginBottom = 12;
                row.Add(UIKit.IconImage(IconFor(ex.Kind), Palette.Turquoise, 60));
                var texts = UIKit.Column().Ignore();
                texts.style.marginLeft = 18; texts.style.flexGrow = 1; texts.style.flexShrink = 1;
                texts.Add(UIKit.Text(ex.Title, UIKit.FontSmall, Palette.Text, true));
                texts.Add(UIKit.Text(ex.Detail ?? "", UIKit.FontTiny, Palette.TextSoft));
                row.Add(texts);
                if (ex.Kind == RoomExhibitKind.WonderModel)
                {
                    var pb = new ProgressBar(Palette.Gold, 20);
                    pb.style.width = 120; pb.style.flexGrow = 0;
                    pb.Set(ex.Progress);
                    row.Add(pb);
                }
                row.pickingMode = PickingMode.Position;
                row.RegisterCallback<ClickEvent>(_ => { _view.FocusExhibit(captured.Id); ShowDetail(captured); });
                _list.Add(row);
            }

            _list.Add(UIKit.Spacer(16));
            _list.Add(UIKit.H2("Dekorasyonlar"));
            _list.Add(UIKit.Spacer(8));
            if (decorations.Count == 0)
                _list.Add(UIKit.Small("Henüz dekorasyonun yok. Müze dekorasyonları ustalık ödülü olarak açılır (seviye 100 sonrası)."));
            foreach (var d in decorations)
            {
                var captured = d;
                var row = UIKit.Row();
                row.Bg(Palette.White).Radius(28).Pad(24, 8).Border(2, Palette.CardBorder);
                row.style.marginBottom = 10;
                var info = CosmeticCatalog.Resolve(d.Id);
                var swatch = new VisualElement().Size(48, 48).Radius(24).Bg(info != null && info.ColorHex != null ? Palette.Hex(info.ColorHex) : Palette.Lilac);
                row.Add(swatch);
                var l = UIKit.Text(d.Title, UIKit.FontSmall, Palette.Text, true);
                l.style.marginLeft = 16; l.style.flexGrow = 1;
                row.Add(l);
                var btn = d.Visible
                    ? UIKit.BaseButton("Kaldır", null, Palette.White, Palette.Text)
                    : UIKit.BaseButton("Yerleştir", null, Palette.Turquoise, Palette.White);
                btn.clicked += () =>
                {
                    Session.Room.ToggleDecoration(captured.Id);
                    _view.RefreshRoom();
                    Rebuild();
                };
                btn.style.height = 90; btn.style.fontSize = UIKit.FontSmall;
                row.Add(btn);
                _list.Add(row);
            }
        }

        private void ShowDetail(RoomExhibit ex)
        {
            _detail.style.display = DisplayStyle.Flex;
            _detailTitle.text = ex.Title;
            string text = string.IsNullOrEmpty(ex.Detail) ? "" : ex.Detail;
            if (ex.Kind == RoomExhibitKind.BadgeCase)
            {
                var names = new List<string>();
                foreach (var b in Session.Save.Badges) names.Add(BadgeCatalog.Resolve(b).Name);
                text += names.Count > 0 ? "\n" + string.Join(" · ", names) : "\nİlk rozetini galerileri tamamlayarak, yıldız toplayarak veya keşif serisiyle kazanabilirsin.";
            }
            else if (ex.Kind == RoomExhibitKind.StudyDesk)
            {
                var p = Session.Profile.Snapshot();
                text += "\nJigsaw: " + p.PiecesPlaced + "/84 · Sandık: " + p.ChestsOpened + "/42 · Günlük görev: " + p.DailyMissionsCompleted;
            }
            _detailText.text = text;
        }

        private static Icon IconFor(RoomExhibitKind k)
        {
            switch (k)
            {
                case RoomExhibitKind.WonderModel: return Icon.Museum;
                case RoomExhibitKind.BadgeCase: return Icon.Trophy;
                case RoomExhibitKind.ToolCase: return Icon.Magnifier;
                case RoomExhibitKind.WorldMap: return Icon.Map;
                case RoomExhibitKind.StudyDesk: return Icon.Calendar;
                case RoomExhibitKind.MasteryCorner: return Icon.Star;
                default: return Icon.Gear;
            }
        }
    }
}
