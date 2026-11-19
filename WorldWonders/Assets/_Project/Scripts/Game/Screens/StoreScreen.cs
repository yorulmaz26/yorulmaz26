using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>Altınla güçlendirici ve kozmetik mağazası. Gerçek para, reklam ve Jigsaw satışı yoktur.</summary>
    public sealed class StoreScreen : UIScreen
    {
        private ScrollView _content;

        protected override void Build(VisualElement root)
        {
            UIKit.Page(root, "Mağaza", Nav.Back, out _content);
        }

        public override void OnShow() { Rebuild(); }

        private void Rebuild()
        {
            _content.Clear();
            var note = UIKit.Card(Palette.Lighten(Palette.Turquoise, 0.75f));
            note.Add(UIKit.Text("Jigsaw parçaları satılmaz. Zorunlu reklam yok.", UIKit.FontBody, Palette.Text, true));
            note.Add(UIKit.Small("Bütün ürünler oyunda kazanılan altınla alınır. Gerçek para ile satın alma yoktur."));
            _content.Add(note);

            _content.Add(UIKit.H2("Güçlendiriciler"));
            _content.Add(UIKit.Spacer(12));
            foreach (BoosterType b in System.Enum.GetValues(typeof(BoosterType)))
            {
                var booster = b;
                int price = GameConfig.BoosterPrice(b);
                var card = UIKit.Card();
                var row = UIKit.Row();
                var ic = new VisualElement().Size(120, 120).Radius(60).Center().Bg(Palette.Lighten(Palette.Coral, 0.6f));
                ic.Add(UIKit.IconImage(MinigameScreen.BoosterIcon(b), Palette.Coral, 66));
                row.Add(ic);
                var col = UIKit.Column(); col.style.marginLeft = 22; col.style.flexGrow = 1; col.style.flexShrink = 1;
                col.Add(UIKit.Text(Names.Booster(b), UIKit.FontBody, Palette.Text, true));
                col.Add(UIKit.Small(Describe(b)));
                col.Add(UIKit.Small("Sahip olunan: " + Session.Economy.BoosterCount(b)));
                row.Add(col);
                var buy = UIKit.Gold(price + " altın", () =>
                {
                    if (Session.Economy.BuyBooster(booster)) { UI.Toast(Names.Booster(booster) + " satın alındı.", Palette.Gold); Game.SaveNow(); UI.RefreshHud(); Rebuild(); }
                    else UI.Toast("Yeterli altın yok.", Palette.Coral);
                });
                buy.style.width = 240;
                UIKit.SetEnabled(buy, Session.Economy.Gold >= price);
                row.Add(buy);
                card.Add(row);
                _content.Add(card);
            }

            _content.Add(UIKit.Spacer(10));
            _content.Add(UIKit.H2("Kozmetikler"));
            _content.Add(UIKit.Small("Yalnız görünüm değiştirir; oynanışa etkisi yoktur."));
            _content.Add(UIKit.Spacer(12));
            foreach (var c in CosmeticCatalog.AllFixed)
            {
                if (c.Source != CosmeticSource.Store) continue;
                var id = c.Id;
                bool owned = Session.Customization.IsOwned(id);
                var card = UIKit.Card();
                var row = UIKit.Row();
                var sw = new VisualElement().Size(110, 110).Radius(55).Bg(c.ColorHex != null ? Palette.Hex(c.ColorHex) : Palette.Stone).Center();
                sw.Add(UIKit.IconImage(owned ? Icon.Check : Icon.Shirt, Palette.White, 56));
                row.Add(sw);
                var col = UIKit.Column(); col.style.marginLeft = 22; col.style.flexGrow = 1; col.style.flexShrink = 1;
                col.Add(UIKit.Text(c.Name, UIKit.FontBody, Palette.Text, true));
                col.Add(UIKit.Small(owned ? "Sahipsin · Özelleştirme ekranından giy" : c.GoldPrice + " altın"));
                row.Add(col);
                if (owned)
                {
                    var wear = UIKit.Secondary("Giy", () => { Session.Customization.Equip(id); UI.Toast(c.Name + " giyildi.", Palette.Turquoise); });
                    wear.style.width = 200;
                    row.Add(wear);
                }
                else
                {
                    var buy = UIKit.Gold(c.GoldPrice + " altın", () =>
                    {
                        if (Session.Economy.BuyCosmetic(id)) { UI.Toast(c.Name + " satın alındı.", Palette.Gold); Game.SaveNow(); UI.RefreshHud(); Rebuild(); }
                        else UI.Toast("Yeterli altın yok.", Palette.Coral);
                    });
                    buy.style.width = 240;
                    UIKit.SetEnabled(buy, Session.Economy.Gold >= c.GoldPrice);
                    row.Add(buy);
                }
                card.Add(row);
                _content.Add(card);
            }
        }

        private static string Describe(BoosterType b)
        {
            switch (b)
            {
                case BoosterType.AncientArrow: return "Seçtiğin taşın satırını temizler.";
                case BoosterType.AncientBomb: return "Seçtiğin taşın çevresini patlatır.";
                case BoosterType.Hammer: return "Tek bir taşı, sandığı ya da zinciri kırar.";
                default: return "Tahtayı güvenle karıştırır.";
            }
        }
    }
}
