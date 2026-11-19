using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Keşif araçları (Büyüteç, Fırça, Kazı Malası) ve isteğe bağlı yükseltmeleri.</summary>
    public sealed class ToolsScreen : UIScreen
    {
        private ScrollView _content;

        protected override void Build(VisualElement root)
        {
            UIKit.Page(root, "Keşif Araçları", Nav.Back, out _content);
        }

        public override void OnShow() { Rebuild(); }

        private void Rebuild()
        {
            _content.Clear();
            var intro = UIKit.Card(Palette.Lighten(Palette.Green, 0.7f));
            intro.Add(UIKit.Small("Yükseltmeler isteğe bağlıdır ve bütün harikalarda geçerlidir. Seviye 2: +%20 etki, Seviye 3: +%40 etki."));
            _content.Add(intro);

            foreach (ToolType t in System.Enum.GetValues(typeof(ToolType)))
            {
                var tool = t;
                int lvl = Session.Tools.Level(t);
                var card = UIKit.Card();
                var row = UIKit.Row();
                var ic = new VisualElement().Size(140, 140).Radius(70).Center().Bg(Palette.Lighten(Palette.Turquoise, 0.6f));
                ic.Add(UIKit.IconImage(t == ToolType.Magnifier ? Icon.Magnifier : t == ToolType.Brush ? Icon.Brush : Icon.Trowel, Palette.TurquoiseDeep, 80));
                row.Add(ic);
                var col = UIKit.Column(); col.style.marginLeft = 24; col.style.flexGrow = 1; col.style.flexShrink = 1;
                col.Add(UIKit.Text(Names.Tool(t), UIKit.FontH2, Palette.Text, true));
                col.Add(UIKit.Small(Purpose(t)));
                var lv = UIKit.Row(); lv.style.marginTop = 10;
                for (int i = 1; i <= GameConfig.ToolMaxLevel; i++)
                    lv.Add(new VisualElement().Size(60, 18).Radius(9).Bg(i <= lvl ? Palette.Gold : Palette.Lighten(Palette.Stone, 0.4f)).Margin(4));
                col.Add(lv);
                float mult = GameConfig.ToolEffectMultiplier(lvl);
                col.Add(UIKit.Small("Seviye " + lvl + " · Etki " + (mult > 1f ? "+%" + Mathf.RoundToInt((mult - 1f) * 100) : "temel") + " · " + Effect(t)));
                row.Add(col);
                card.Add(row);

                var err = Session.Tools.CanUpgrade(t);
                if (err == ToolUpgradeError.MaxLevel)
                {
                    card.Add(UIKit.Spacer(10));
                    card.Add(UIKit.Pill("En yüksek seviye", Palette.Green));
                }
                else
                {
                    int next = lvl + 1;
                    int cost = GameConfig.ToolUpgradeCost(next);
                    string req = "Kolezyum " + GameConfig.ToolUpgradeUnlockLevel(next) + ". bölüm tamamlanınca açılır";
                    if (err == ToolUpgradeError.NotUnlockedYet) { card.Add(UIKit.Spacer(8)); card.Add(UIKit.Small(req)); }
                    var b = UIKit.Gold("Seviye " + next + "'e yükselt · " + cost + " altın", () =>
                    {
                        var r = Session.Tools.Upgrade(tool);
                        if (r == ToolUpgradeError.None) { UI.Toast(Names.Tool(tool) + " seviye " + (lvl + 1) + "!", Palette.Gold); Game.SaveNow(); UI.RefreshHud(); Rebuild(); }
                        else if (r == ToolUpgradeError.NotEnoughGold) UI.Toast("Yeterli altın yok.", Palette.Coral);
                        else UI.Toast(req + ".", Palette.Coral);
                    });
                    UIKit.SetEnabled(b, err == ToolUpgradeError.None);
                    card.Add(b);
                }
                _content.Add(card);
            }
        }

        private static string Purpose(ToolType t)
        {
            switch (t)
            {
                case ToolType.Magnifier: return "Gizli sembolleri bulmak için.";
                case ToolType.Brush: return "Toz ve kumu temizlemek için.";
                default: return "Katmanlı toprak bölgelerini kazmak için.";
            }
        }

        private static string Effect(ToolType t)
        {
            switch (t)
            {
                case ToolType.Magnifier: return "daha geniş mercek";
                case ToolType.Brush: return "daha geniş fırça";
                default: return "komşu toprağı da kazar";
            }
        }
    }
}
