using System;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    /// <summary>
    /// Aydınlık, premium mobil UI bileşen fabrikası (UI Toolkit, kodla).
    /// Referans çözünürlük 1080x1920; ölçüler bu ölçeğe göredir.
    /// </summary>
    public static class UIKit
    {
        public const float HudHeight = 170f;
        public const float Gutter = 40f;
        public const int FontTitle = 58, FontH2 = 44, FontBody = 34, FontSmall = 28, FontTiny = 24;

        // ------------------------------------------------------------- stil yardımcıları
        public static T Pad<T>(this T e, float all) where T : VisualElement
        {
            e.style.paddingLeft = all; e.style.paddingRight = all; e.style.paddingTop = all; e.style.paddingBottom = all; return e;
        }
        public static T Pad<T>(this T e, float h, float v) where T : VisualElement
        {
            e.style.paddingLeft = h; e.style.paddingRight = h; e.style.paddingTop = v; e.style.paddingBottom = v; return e;
        }
        public static T Margin<T>(this T e, float all) where T : VisualElement
        {
            e.style.marginLeft = all; e.style.marginRight = all; e.style.marginTop = all; e.style.marginBottom = all; return e;
        }
        public static T MarginV<T>(this T e, float top, float bottom) where T : VisualElement { e.style.marginTop = top; e.style.marginBottom = bottom; return e; }
        public static T Radius<T>(this T e, float r) where T : VisualElement
        {
            e.style.borderTopLeftRadius = r; e.style.borderTopRightRadius = r; e.style.borderBottomLeftRadius = r; e.style.borderBottomRightRadius = r; return e;
        }
        public static T Border<T>(this T e, float w, Color c) where T : VisualElement
        {
            e.style.borderLeftWidth = w; e.style.borderRightWidth = w; e.style.borderTopWidth = w; e.style.borderBottomWidth = w;
            e.style.borderLeftColor = c; e.style.borderRightColor = c; e.style.borderTopColor = c; e.style.borderBottomColor = c; return e;
        }
        public static T Bg<T>(this T e, Color c) where T : VisualElement { e.style.backgroundColor = c; return e; }
        public static T Size<T>(this T e, float w, float h) where T : VisualElement { e.style.width = w; e.style.height = h; return e; }
        public static T Grow<T>(this T e, float g = 1) where T : VisualElement { e.style.flexGrow = g; return e; }
        public static T NoShrink<T>(this T e) where T : VisualElement { e.style.flexShrink = 0; return e; }
        public static T Ignore<T>(this T e) where T : VisualElement { e.pickingMode = PickingMode.Ignore; return e; }
        public static T Absolute<T>(this T e, float? left = null, float? top = null, float? right = null, float? bottom = null) where T : VisualElement
        {
            e.style.position = Position.Absolute;
            if (left.HasValue) e.style.left = left.Value; if (top.HasValue) e.style.top = top.Value;
            if (right.HasValue) e.style.right = right.Value; if (bottom.HasValue) e.style.bottom = bottom.Value;
            return e;
        }
        public static T Fill<T>(this T e) where T : VisualElement { return e.Absolute(0, 0, 0, 0); }
        public static T Center<T>(this T e) where T : VisualElement { e.style.alignItems = Align.Center; e.style.justifyContent = Justify.Center; return e; }
        public static T Shadow<T>(this T e) where T : VisualElement
        {
            // UI Toolkit'te gölge yok: alt kenarda yumuşak koyu çizgi ile derinlik.
            e.style.borderBottomWidth = 6; e.style.borderBottomColor = new Color(0.15f, 0.22f, 0.29f, 0.12f); return e;
        }

        // ------------------------------------------------------------- yerleşim
        public static VisualElement Row(float gap = 0)
        {
            var e = new VisualElement();
            e.style.flexDirection = FlexDirection.Row; e.style.alignItems = Align.Center;
            if (gap > 0) e.AddToClassList("ww-gap");
            return e;
        }
        public static VisualElement Column() { var e = new VisualElement(); e.style.flexDirection = FlexDirection.Column; return e; }
        public static VisualElement Spacer(float h) { var e = new VisualElement(); e.style.height = h; e.style.width = h; e.style.flexShrink = 0; e.pickingMode = PickingMode.Ignore; return e; }
        public static VisualElement Flex() { var e = new VisualElement(); e.style.flexGrow = 1; e.pickingMode = PickingMode.Ignore; return e; }

        public static ScrollView Scroll()
        {
            var s = new ScrollView(ScrollViewMode.Vertical);
            s.style.flexGrow = 1;
            s.horizontalScrollerVisibility = ScrollerVisibility.Hidden;
            s.verticalScrollerVisibility = ScrollerVisibility.Hidden;
            return s;
        }

        /// <summary>Tam ekran sayfa iskeleti: krem arka plan, HUD altında başlık ve kaydırılabilir içerik.</summary>
        public static VisualElement Page(VisualElement root, string title, Action onBack, out ScrollView content, bool opaque = true, bool hudSpace = true)
        {
            var page = Column();
            page.style.flexGrow = 1;
            if (opaque) page.Bg(Palette.Cream);
            else page.Ignore();
            if (hudSpace) page.Add(Spacer(HudHeight));
            var header = Row();
            header.Pad(Gutter, 18);
            if (onBack != null) header.Add(IconButton(Icon.Back, onBack, Palette.White, Palette.Text, 96));
            var t = Title(title); t.style.marginLeft = 24; t.style.flexGrow = 1;
            header.Add(t);
            page.Add(header);
            content = Scroll();
            content.contentContainer.style.paddingLeft = Gutter; content.contentContainer.style.paddingRight = Gutter; content.contentContainer.style.paddingBottom = 80;
            page.Add(content);
            root.Add(page);
            return page;
        }

        // ------------------------------------------------------------- metin
        public static Label Text(string text, int size, Color color, bool bold = false)
        {
            var l = new Label(text);
            l.style.fontSize = size; l.style.color = color;
            l.style.unityFontStyleAndWeight = bold ? FontStyle.Bold : FontStyle.Normal;
            l.style.whiteSpace = WhiteSpace.Normal;
            l.style.marginLeft = 0; l.style.marginRight = 0; l.style.paddingLeft = 0; l.style.paddingRight = 0;
            l.pickingMode = PickingMode.Ignore;
            return l;
        }
        public static Label Title(string t) { return Text(t, FontTitle, Palette.Text, true); }
        public static Label H2(string t) { return Text(t, FontH2, Palette.Text, true); }
        public static Label Body(string t) { return Text(t, FontBody, Palette.Text); }
        public static Label Small(string t) { return Text(t, FontSmall, Palette.TextSoft); }

        // ------------------------------------------------------------- kartlar
        public static VisualElement Card(Color? bg = null)
        {
            var c = Column();
            c.Bg(bg ?? Palette.White).Radius(36).Pad(32).Border(2, Palette.CardBorder).Shadow();
            c.style.marginBottom = 24;
            return c;
        }

        public static Label Pill(string text, Color bg, Color? fg = null)
        {
            var l = Text(text, FontSmall, fg ?? Palette.White, true);
            l.Bg(bg).Radius(28).Pad(22, 8);
            l.style.alignSelf = Align.FlexStart;
            l.style.unityTextAlign = TextAnchor.MiddleCenter;
            return l;
        }

        // ------------------------------------------------------------- butonlar
        public static Button Primary(string text, Action onClick, Color? color = null)
        {
            var b = BaseButton(text, onClick, color ?? Palette.Turquoise, Palette.White);
            b.style.height = 120;
            return b;
        }

        public static Button Gold(string text, Action onClick) { return Primary(text, onClick, Palette.Gold); }

        public static Button Secondary(string text, Action onClick)
        {
            var b = BaseButton(text, onClick, Palette.White, Palette.Text);
            b.Border(3, Palette.CardBorder);
            b.style.height = 110;
            return b;
        }

        public static Button BaseButton(string text, Action onClick, Color bg, Color fg)
        {
            var b = new Button(() => { Haptics.Light(); if (onClick != null) onClick(); }) { text = text };
            b.style.fontSize = FontBody; b.style.color = fg; b.style.unityFontStyleAndWeight = FontStyle.Bold;
            b.Bg(bg).Radius(44).Border(0, Color.clear);
            b.style.borderBottomWidth = 8; b.style.borderBottomColor = Palette.Darken(bg, 0.25f);
            b.style.paddingLeft = 36; b.style.paddingRight = 36;
            b.style.marginLeft = 0; b.style.marginRight = 0; b.style.marginTop = 10; b.style.marginBottom = 10;
            b.style.unityTextAlign = TextAnchor.MiddleCenter;
            PressFeedback(b);
            return b;
        }

        public static Button IconButton(Icon icon, Action onClick, Color bg, Color fg, float size = 110, string badge = null)
        {
            var b = new Button(() => { Haptics.Light(); if (onClick != null) onClick(); });
            b.text = "";
            b.Size(size, size).Bg(bg).Radius(size * 0.5f).Border(0, Color.clear);
            b.style.borderBottomWidth = 6; b.style.borderBottomColor = Palette.Darken(bg, 0.2f);
            b.style.marginLeft = 8; b.style.marginRight = 8; b.style.paddingLeft = 0; b.style.paddingRight = 0;
            b.Center();
            b.Add(IconImage(icon, fg, size * 0.52f));
            if (badge != null)
            {
                var dot = Text(badge, FontTiny, Palette.White, true).Bg(Palette.Coral).Radius(20).Pad(10, 2);
                dot.Absolute(null, -6, -6, null);
                b.Add(dot);
            }
            PressFeedback(b);
            return b;
        }

        /// <summary>İkon + etiket içeren menü düğmesi (dünya haritası alt çubuğu vb.).</summary>
        public static Button MenuTile(Icon icon, string label, Action onClick, Color tint, bool locked = false)
        {
            var b = new Button(() => { Haptics.Light(); if (onClick != null) onClick(); });
            b.text = "";
            b.style.flexDirection = FlexDirection.Column; b.Center();
            b.Bg(Palette.White).Radius(36).Border(2, Palette.CardBorder).Shadow();
            b.style.width = 180; b.style.height = 170; b.style.marginLeft = 8; b.style.marginRight = 8; b.style.paddingLeft = 0; b.style.paddingRight = 0;
            var ic = IconImage(locked ? Icon.Lock : icon, locked ? Palette.Locked : tint, 70);
            b.Add(ic);
            var l = Text(label, FontTiny, locked ? Palette.TextSoft : Palette.Text, true);
            l.style.unityTextAlign = TextAnchor.MiddleCenter; l.style.marginTop = 6;
            b.Add(l);
            PressFeedback(b);
            return b;
        }

        public static Image IconImage(Icon icon, Color tint, float size)
        {
            var img = new Image { image = IconFactory.Get(icon), scaleMode = ScaleMode.ScaleToFit };
            img.tintColor = tint;
            img.style.width = size; img.style.height = size; img.style.flexShrink = 0;
            img.pickingMode = PickingMode.Ignore;
            return img;
        }

        public static void PressFeedback(VisualElement e)
        {
            e.RegisterCallback<PointerDownEvent>(_ => e.style.scale = new Scale(new Vector3(0.95f, 0.95f, 1f)), TrickleDown.TrickleDown);
            e.RegisterCallback<PointerUpEvent>(_ => e.style.scale = new Scale(Vector3.one), TrickleDown.TrickleDown);
            e.RegisterCallback<PointerLeaveEvent>(_ => e.style.scale = new Scale(Vector3.one));
        }

        public static void SetEnabled(Button b, bool enabled)
        {
            b.SetEnabled(enabled);
            b.style.opacity = enabled ? 1f : 0.45f;
        }

        // ------------------------------------------------------------- göstergeler
        public static VisualElement Stars(int earned, int max = 3, float size = 56, Color? on = null)
        {
            var r = Row();
            for (int i = 0; i < max; i++)
                r.Add(IconImage(Icon.Star, i < earned ? (on ?? Palette.Gold) : Palette.Locked, size));
            r.pickingMode = PickingMode.Ignore;
            return r;
        }

        public static VisualElement Chip(Icon icon, string value, Color iconTint)
        {
            var r = Row();
            r.Bg(Palette.White).Radius(40).Pad(14, 6).Border(2, Palette.CardBorder);
            r.Add(IconImage(icon, iconTint, 52));
            var l = Text(value, FontSmall, Palette.Text, true); l.style.marginLeft = 10; l.style.marginRight = 8;
            r.Add(l);
            r.userData = l;
            return r;
        }

        public static void SetChip(VisualElement chip, string value) { var l = chip.userData as Label; if (l != null) l.text = value; }
    }

    /// <summary>Doldurulabilir ilerleme çubuğu.</summary>
    public sealed class ProgressBar : VisualElement
    {
        private readonly VisualElement _fill;
        private readonly Label _label;

        public ProgressBar(Color fill, float height = 28, string label = null)
        {
            style.height = height; style.flexGrow = 1;
            this.Bg(Palette.Lighten(Palette.Stone, 0.55f)).Radius(height * 0.5f);
            style.overflow = Overflow.Hidden;
            _fill = new VisualElement();
            _fill.Bg(fill).Radius(height * 0.5f);
            _fill.style.height = Length.Percent(100); _fill.style.width = Length.Percent(0);
            Add(_fill);
            if (label != null)
            {
                _label = UIKit.Text(label, UIKit.FontTiny, Palette.Text, true);
                _label.Fill(); _label.style.unityTextAlign = TextAnchor.MiddleCenter;
                Add(_label);
            }
            pickingMode = PickingMode.Ignore;
        }

        public void Set(float t, string label = null)
        {
            _fill.style.width = Length.Percent(Mathf.Clamp01(t) * 100f);
            if (_label != null && label != null) _label.text = label;
        }
    }

    /// <summary>
    /// Dokunsal geri bildirim (ayarlarda kapatılabilir). Unity'nin yerleşik API'si yalnız uzun titreşim sunar;
    /// bu yüzden buton dokunuşları titreştirmez (Light boş), yalnız önemli anlar (Strong) titreştirir.
    /// Kısa iOS/Android haptikleri için yerel eklenti gerekir (yayın hazırlığı listesinde).
    /// </summary>
    public static class Haptics
    {
        public static void Light() { }

        public static void Strong()
        {
#if UNITY_ANDROID || UNITY_IOS
            var gm = GameManager.I;
            if (gm != null && gm.Session != null && gm.Session.Save.HapticsOn) Handheld.Vibrate();
#endif
        }
    }
}
