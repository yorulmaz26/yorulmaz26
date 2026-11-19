using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>Kâşif özelleştirme. Kozmetikler yalnız görünümü değiştirir, oynanış avantajı sağlamaz.</summary>
    public sealed class CustomizationScreen : UIScreen
    {
        private static readonly CosmeticSlot[] Slots =
        {
            CosmeticSlot.Body, CosmeticSlot.Hair, CosmeticSlot.SkinTone, CosmeticSlot.Outfit, CosmeticSlot.Shoes, CosmeticSlot.Hat,
            CosmeticSlot.Bag, CosmeticSlot.Accessory, CosmeticSlot.OutfitColor, CosmeticSlot.BagColor, CosmeticSlot.ProfileFrame
        };

        private CosmeticSlot _slot = CosmeticSlot.Outfit;
        private CharacterPreviewPanel _preview;
        private VisualElement _tabs, _list;

        protected override void Build(VisualElement root)
        {
            var page = UIKit.Column().Grow().Bg(Palette.Cream);
            page.Add(UIKit.Spacer(UIKit.HudHeight));
            var header = UIKit.Row().Pad(UIKit.Gutter, 12);
            header.Add(UIKit.IconButton(Icon.Back, Nav.Back, Palette.White, Palette.Text, 96));
            var t = UIKit.Title("Kâşifini Özelleştir"); t.style.marginLeft = 24;
            header.Add(t);
            page.Add(header);

            var top = UIKit.Column().Pad(UIKit.Gutter, 0);
            _preview = new CharacterPreviewPanel(560);
            top.Add(_preview);
            page.Add(top);

            var tabScroll = new ScrollView(ScrollViewMode.Horizontal);
            tabScroll.horizontalScrollerVisibility = ScrollerVisibility.Hidden;
            tabScroll.verticalScrollerVisibility = ScrollerVisibility.Hidden;
            tabScroll.style.flexShrink = 0; tabScroll.style.marginTop = 18;
            _tabs = UIKit.Row();
            _tabs.style.paddingLeft = UIKit.Gutter; _tabs.style.paddingRight = UIKit.Gutter;
            tabScroll.Add(_tabs);
            page.Add(tabScroll);

            var scroll = UIKit.Scroll();
            scroll.contentContainer.style.paddingLeft = UIKit.Gutter; scroll.contentContainer.style.paddingRight = UIKit.Gutter; scroll.contentContainer.style.paddingBottom = 80;
            _list = UIKit.Column();
            scroll.Add(_list);
            page.Add(scroll);
            root.Add(page);
        }

        public override void OnShow()
        {
            _preview.Show();
            RebuildTabs();
            RebuildList();
        }

        public override void OnHide() { _preview.Hide(); }

        private static string SlotName(CosmeticSlot s)
        {
            switch (s)
            {
                case CosmeticSlot.Body: return "Model";
                case CosmeticSlot.Hair: return "Saç";
                case CosmeticSlot.SkinTone: return "Ten";
                case CosmeticSlot.Outfit: return "Kıyafet";
                case CosmeticSlot.Shoes: return "Ayakkabı";
                case CosmeticSlot.Hat: return "Şapka";
                case CosmeticSlot.Bag: return "Çanta";
                case CosmeticSlot.Accessory: return "Aksesuar";
                case CosmeticSlot.OutfitColor: return "Kıyafet Rengi";
                case CosmeticSlot.BagColor: return "Çanta Rengi";
                case CosmeticSlot.ProfileFrame: return "Çerçeve";
                default: return s.ToString();
            }
        }

        private void RebuildTabs()
        {
            _tabs.Clear();
            foreach (var s in Slots)
            {
                var slot = s;
                bool sel = s == _slot;
                var b = UIKit.BaseButton(SlotName(s), () => { _slot = slot; RebuildTabs(); RebuildList(); }, sel ? Palette.Turquoise : Palette.White, sel ? Palette.White : Palette.Text);
                b.style.height = 90; b.style.fontSize = UIKit.FontSmall; b.style.marginRight = 12;
                if (!sel) b.Border(2, Palette.CardBorder);
                _tabs.Add(b);
            }
        }

        private void RebuildList()
        {
            _list.Clear();
            var cust = Session.Customization;
            string equipped = cust.Equipped(_slot);
            var items = new List<CosmeticInfo>();
            var seen = new HashSet<string>();
            foreach (var c in CosmeticCatalog.AllFixed) if (c.Slot == _slot) { items.Add(c); seen.Add(c.Id); }
            foreach (var c in cust.OwnedInSlot(_slot)) if (seen.Add(c.Id)) items.Add(c);
            // Sahip olunanlar önce.
            items.Sort((a, b) => (cust.IsOwned(b.Id) ? 1 : 0).CompareTo(cust.IsOwned(a.Id) ? 1 : 0));

            bool optional = _slot != CosmeticSlot.Body && _slot != CosmeticSlot.SkinTone && _slot != CosmeticSlot.Hair && _slot != CosmeticSlot.Outfit;
            if (optional && equipped != null)
            {
                var off = UIKit.Secondary("Çıkar (" + SlotName(_slot) + " yok)", () => { cust.Unequip(_slot); _preview.Refresh(); RebuildList(); });
                off.style.marginTop = 16;
                _list.Add(off);
            }

            var grid = UIKit.Row(); grid.style.flexWrap = Wrap.Wrap; grid.style.justifyContent = Justify.SpaceBetween; grid.style.marginTop = 16;
            foreach (var c in items) grid.Add(Item(c, cust.IsOwned(c.Id), c.Id == equipped));
            _list.Add(grid);
            if (items.Count == 0)
                _list.Add(UIKit.Small(_slot == CosmeticSlot.OutfitColor || _slot == CosmeticSlot.BagColor ? "Kıyafet ve çanta renkleri ustalık seviyelerinde açılır (seviye 100'den sonra)." : "Bu yuvada henüz eşya yok."));
            if (_slot == CosmeticSlot.ProfileFrame || _slot == CosmeticSlot.OutfitColor || _slot == CosmeticSlot.BagColor)
                _list.Add(UIKit.Small("Yeni çerçeveler ve renkler kâşif seviyesi, ustalık ve 1.260 yıldız kilometre taşıyla açılır."));
            _list.Add(UIKit.Spacer(14));
            _list.Add(UIKit.Small("Kozmetikler yalnız görünümü değiştirir; Match-3 oynanışında avantaj sağlamaz."));
        }

        private VisualElement Item(CosmeticInfo c, bool owned, bool equipped)
        {
            var card = UIKit.Column().Pad(18).Radius(30).Center();
            card.style.width = Length.Percent(31.5f); card.style.marginBottom = 18; card.style.minHeight = 260;
            card.Bg(equipped ? Palette.Lighten(Palette.Turquoise, 0.7f) : Palette.White).Border(equipped ? 5 : 2, equipped ? Palette.Turquoise : Palette.CardBorder);
            var swatch = new VisualElement().Size(110, 110).Radius(55).Center();
            swatch.Bg(c.ColorHex != null ? Palette.Hex(c.ColorHex) : Palette.Lighten(Palette.Stone, 0.4f));
            swatch.Border(3, Palette.WithAlpha(Palette.Text, 0.1f));
            if (!owned) swatch.Add(UIKit.IconImage(Icon.Lock, Palette.White, 54));
            else if (equipped) swatch.Add(UIKit.IconImage(Icon.Check, Palette.White, 54));
            else swatch.Add(UIKit.IconImage(SlotIcon(c.Slot), Palette.WithAlpha(Palette.White, 0.9f), 54));
            card.Add(swatch);
            var name = UIKit.Text(c.Name, UIKit.FontTiny, Palette.Text, true);
            name.style.unityTextAlign = TextAnchor.MiddleCenter; name.style.marginTop = 10;
            card.Add(name);
            if (!owned)
            {
                var how = UIKit.Text(UnlockText(c), UIKit.FontTiny - 4, Palette.TextSoft);
                how.style.unityTextAlign = TextAnchor.MiddleCenter;
                card.Add(how);
            }
            var id = c.Id;
            card.RegisterCallback<ClickEvent>(_ =>
            {
                if (owned)
                {
                    if (Session.Customization.Equip(id)) { _preview.Refresh(ExplorerAnim.Celebrate); RebuildList(); }
                }
                else if (c.Source == CosmeticSource.Store) Nav.Store();
                else UI.Info(c.Name, "Kilidi açmak için: " + UnlockText(c));
            });
            UIKit.PressFeedback(card);
            return card;
        }

        private static Icon SlotIcon(CosmeticSlot s)
        {
            switch (s)
            {
                case CosmeticSlot.Body: return Icon.User;
                case CosmeticSlot.Bag: return Icon.Shop;
                case CosmeticSlot.ProfileFrame: return Icon.Trophy;
                case CosmeticSlot.Accessory: return Icon.Star;
                default: return Icon.Shirt;
            }
        }

        public static string UnlockText(CosmeticInfo c)
        {
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                string n = WonderCatalog.Get(w).Name;
                if (c.Id == CosmeticCatalog.SpecialOutfitId(w)) return n + " " + GameConfig.SpecialOutfitLocalLevel + ". bölüm";
                if (c.Id == CosmeticCatalog.AccessoryId(w, 1)) return n + " " + GameConfig.AccessoryLocalLevelA + ". bölüm";
                if (c.Id == CosmeticCatalog.AccessoryId(w, 2)) return n + " " + GameConfig.AccessoryLocalLevelB + ". bölüm";
                if (c.Id == CosmeticCatalog.AccessoryId(w, 3)) return n + " " + GameConfig.StarMilestoneAccessory + " yıldız";
            }
            switch (c.Source)
            {
                case CosmeticSource.Store: return "Mağaza: " + c.GoldPrice + " altın";
                case CosmeticSource.Milestone: return "1.260 yıldızın tamamı";
                case CosmeticSource.ExplorerLevel: return "Kâşif seviyesi ödülü";
                case CosmeticSource.Mastery: return c.Id == CosmeticCatalog.MasteryOutfit ? "Ustalık 10" : "Ustalık ödülü";
                default: return "Ücretsiz";
            }
        }
    }
}
