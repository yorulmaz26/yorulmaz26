using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Tek bir ekranın kökü. Ekranlar UIFlowController yığınında tutulur.</summary>
    public abstract class UIScreen
    {
        public VisualElement Root { get; private set; }
        protected GameManager Game { get { return GameManager.I; } }
        protected GameSession Session { get { return GameManager.I.Session; } }
        protected UIFlowController UI { get { return GameManager.I.UI; } }

        /// <summary>Üst bilgi çubuğu (altın, kristal, can, seviye) gösterilsin mi?</summary>
        public virtual bool ShowHud { get { return true; } }

        internal void Create()
        {
            Root = new VisualElement();
            Root.style.flexGrow = 1;
            Root.style.position = Position.Absolute;
            Root.style.left = 0; Root.style.right = 0; Root.style.top = 0; Root.style.bottom = 0;
            Root.pickingMode = PickingMode.Ignore;
            Build(Root);
        }

        protected abstract void Build(VisualElement root);
        /// <summary>Ekran (yeniden) görünür olduğunda: verileri tazele, 3D görünümü ayarla.</summary>
        public virtual void OnShow() { }
        public virtual void OnHide() { }
        public virtual void Tick(float dt) { }
        /// <summary>Geri tuşu: true dönerse ekran olayı kendisi ele aldı.</summary>
        public virtual bool OnBack() { return false; }
    }

    /// <summary>
    /// UI Toolkit tabanlı ekran akışı: ekran yığını, açılır pencereler, bildirimler, HUD ve güvenli alan.
    /// UI'a yapılan dokunuşlar 3D dünyaya geçmez (IsOverUI).
    /// </summary>
    public sealed class UIFlowController : MonoBehaviour
    {
        private UIDocument _doc;
        private VisualElement _root, _safe, _screenLayer, _hudLayer, _popupLayer, _toastLayer;
        private readonly List<UIScreen> _stack = new List<UIScreen>();
        private readonly List<VisualElement> _popups = new List<VisualElement>();
        private HudBar _hud;
        private Rect _lastSafe;

        public UIScreen Current { get { return _stack.Count > 0 ? _stack[_stack.Count - 1] : null; } }
        public VisualElement PopupLayer { get { return _popupLayer; } }
        public bool HasPopup { get { return _popups.Count > 0; } }

        public void Init()
        {
            var go = new GameObject("UI");
            go.transform.SetParent(transform, false);
            go.SetActive(false);
            var ps = ScriptableObject.CreateInstance<PanelSettings>();
            ps.name = "WorldWondersPanel";
            ps.scaleMode = PanelScaleMode.ScaleWithScreenSize;
            ps.referenceResolution = new Vector2Int(1080, 1920);
            ps.screenMatchMode = PanelScreenMatchMode.MatchWidthOrHeight;
            ps.match = 0.35f;
            ps.clearColor = false;
            var theme = Resources.Load<ThemeStyleSheet>("UI/WWTheme");
            if (theme != null) ps.themeStyleSheet = theme;
            _doc = go.AddComponent<UIDocument>();
            _doc.panelSettings = ps;
            go.SetActive(true);

            _root = _doc.rootVisualElement;
            _root.pickingMode = PickingMode.Ignore;
            _root.style.color = Palette.Text;
            var font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            if (font != null) _root.style.unityFont = font;

            _safe = new VisualElement().Fill().Ignore();
            _root.Add(_safe);
            _screenLayer = new VisualElement().Fill().Ignore();
            _hudLayer = new VisualElement().Ignore();
            _hudLayer.Absolute(0, 0, 0, null);
            _popupLayer = new VisualElement().Fill().Ignore();
            _toastLayer = new VisualElement().Ignore();
            _toastLayer.Absolute(0, null, 0, 160);
            _toastLayer.style.alignItems = Align.Center;
            _safe.Add(_screenLayer); _safe.Add(_hudLayer); _safe.Add(_popupLayer); _safe.Add(_toastLayer);

            _hud = new HudBar();
            _hudLayer.Add(_hud);
            _root.RegisterCallback<GeometryChangedEvent>(_ => ApplySafeArea(true));
        }

        private void ApplySafeArea(bool force)
        {
            var sa = Screen.safeArea;
            if (!force && sa == _lastSafe) return;
            _lastSafe = sa;
            float w = _root.layout.width;
            if (float.IsNaN(w) || w <= 0 || Screen.width <= 0) return;
            float k = w / Screen.width;
            _safe.style.left = sa.xMin * k;
            _safe.style.right = (Screen.width - sa.xMax) * k;
            _safe.style.top = (Screen.height - sa.yMax) * k;
            _safe.style.bottom = sa.yMin * k;
        }

        // ------------------------------------------------------------- ekran yığını
        public void Push(UIScreen s)
        {
            if (Current != null) { Current.OnHide(); Current.Root.RemoveFromHierarchy(); }
            s.Create();
            _stack.Add(s);
            Attach(s);
        }

        /// <summary>Üstteki ekranı yenisiyle değiştirir.</summary>
        public void Replace(UIScreen s)
        {
            if (Current != null) { Current.OnHide(); Current.Root.RemoveFromHierarchy(); _stack.RemoveAt(_stack.Count - 1); }
            s.Create();
            _stack.Add(s);
            Attach(s);
        }

        /// <summary>Yığını temizleyip tek ekranla başlar.</summary>
        public void ResetTo(UIScreen s)
        {
            ClearStack();
            Push(s);
        }

        public void ClearStack()
        {
            CloseAllPopups();
            foreach (var x in _stack) { x.OnHide(); x.Root.RemoveFromHierarchy(); }
            _stack.Clear();
        }

        public bool Back()
        {
            if (_popups.Count > 0) { ClosePopup(); return true; }
            var cur = Current;
            if (cur == null) return false;
            if (cur.OnBack()) return true;
            if (_stack.Count <= 1) return false;
            cur.OnHide(); cur.Root.RemoveFromHierarchy();
            _stack.RemoveAt(_stack.Count - 1);
            Attach(Current);
            return true;
        }

        /// <summary>Belirli türde bir ekran yığındaysa ona kadar geri döner.</summary>
        public bool PopTo<T>() where T : UIScreen
        {
            int idx = _stack.FindLastIndex(x => x is T);
            if (idx < 0) return false;
            CloseAllPopups();
            while (_stack.Count - 1 > idx)
            {
                var top = _stack[_stack.Count - 1];
                top.OnHide(); top.Root.RemoveFromHierarchy();
                _stack.RemoveAt(_stack.Count - 1);
            }
            Attach(Current);
            return true;
        }

        private void Attach(UIScreen s)
        {
            _screenLayer.Add(s.Root);
            _hudLayer.style.display = s.ShowHud ? DisplayStyle.Flex : DisplayStyle.None;
            _hud.Refresh();
            s.OnShow();
        }

        // ------------------------------------------------------------- açılır pencereler
        public VisualElement ShowPopup(VisualElement content, bool dismissOnBackdrop = true, Action onDismiss = null)
        {
            var backdrop = new VisualElement().Fill();
            backdrop.Bg(new Color(0.15f, 0.22f, 0.29f, 0.35f));
            backdrop.style.justifyContent = Justify.Center; backdrop.style.alignItems = Align.Center;
            backdrop.pickingMode = PickingMode.Position;
            var holder = UIKit.Column();
            holder.style.width = Length.Percent(88);
            holder.Add(content);
            content.RegisterCallback<PointerDownEvent>(e => e.StopPropagation());
            backdrop.Add(holder);
            if (dismissOnBackdrop)
                backdrop.RegisterCallback<PointerDownEvent>(e => { if (e.target == backdrop) { ClosePopup(backdrop); if (onDismiss != null) onDismiss(); } });
            _popupLayer.Add(backdrop);
            _popups.Add(backdrop);
            holder.style.scale = new Scale(new Vector3(0.9f, 0.9f, 1f));
            holder.style.opacity = 0f;
            Tween.Run(0.22f, k => { holder.style.scale = new Scale(new Vector3(0.9f + 0.1f * k, 0.9f + 0.1f * k, 1f)); holder.style.opacity = Mathf.Clamp01(k); }, null, Ease.OutBack);
            return backdrop;
        }

        public void ClosePopup(VisualElement popup = null)
        {
            if (_popups.Count == 0) return;
            if (popup == null) popup = _popups[_popups.Count - 1];
            _popups.Remove(popup);
            popup.RemoveFromHierarchy();
        }

        public void CloseAllPopups()
        {
            foreach (var p in _popups) p.RemoveFromHierarchy();
            _popups.Clear();
        }

        public void Confirm(string title, string message, string okText, Action onOk, string cancelText = "Vazgeç", Action onCancel = null, Color? okColor = null)
        {
            var card = UIKit.Card();
            card.Add(UIKit.H2(title));
            card.Add(UIKit.Spacer(16));
            card.Add(UIKit.Body(message));
            card.Add(UIKit.Spacer(28));
            VisualElement popup = null;
            card.Add(UIKit.Primary(okText, () => { ClosePopup(popup); if (onOk != null) onOk(); }, okColor));
            if (cancelText != null) card.Add(UIKit.Secondary(cancelText, () => { ClosePopup(popup); if (onCancel != null) onCancel(); }));
            popup = ShowPopup(card, cancelText != null, onCancel);
        }

        public void Info(string title, string message, string okText = "Tamam")
        {
            Confirm(title, message, okText, null, null);
        }

        // ------------------------------------------------------------- bildirimler
        public void Toast(string text, Color accent)
        {
            var t = UIKit.Row();
            t.Bg(Palette.White).Radius(40).Pad(32, 18).Border(3, accent).Shadow();
            t.style.marginTop = 12; t.style.maxWidth = Length.Percent(90);
            t.Add(new VisualElement().Size(22, 22).Bg(accent).Radius(11));
            var l = UIKit.Text(text, UIKit.FontSmall, Palette.Text, true); l.style.marginLeft = 16;
            t.Add(l);
            t.pickingMode = PickingMode.Ignore;
            _toastLayer.Add(t);
            t.style.opacity = 0;
            Tween.Run(0.25f, k => t.style.opacity = k);
            Tween.Run(0.4f, k => t.style.opacity = 1 - k, () => t.RemoveFromHierarchy(), Ease.Linear, 2.8f);
            while (_toastLayer.childCount > 4) _toastLayer.RemoveAt(0);
        }

        // ------------------------------------------------------------- dünya etkileşimi koruması
        public bool IsOverUI(Vector2 screenPos)
        {
            if (_root == null || _root.panel == null) return false;
            var panelPos = RuntimePanelUtils.ScreenToPanel(_root.panel, new Vector2(screenPos.x, Screen.height - screenPos.y));
            var picked = _root.panel.Pick(panelPos);
            return picked != null && picked.pickingMode == PickingMode.Position;
        }

        public void RefreshHud() { _hud.Refresh(); }

        public void Tick(float dt)
        {
            ApplySafeArea(false);
            _hud.Tick(dt);
            if (Current != null) Current.Tick(dt);
        }
    }

    /// <summary>Üst çubuk: seviye/XP, can + dolum süresi, altın, kristal.</summary>
    public sealed class HudBar : VisualElement
    {
        private readonly Label _level, _lifeTimer;
        private readonly ProgressBar _xp;
        private readonly VisualElement _gold, _crystal, _lives;
        private float _t;

        public HudBar()
        {
            style.flexDirection = FlexDirection.Row; style.alignItems = Align.Center;
            style.height = UIKit.HudHeight;
            style.paddingLeft = 28; style.paddingRight = 28; style.paddingTop = 20;
            pickingMode = PickingMode.Ignore;

            var lvl = UIKit.Row();
            lvl.Bg(Palette.White).Radius(40).Pad(10, 6).Border(2, Palette.CardBorder);
            var badge = new VisualElement().Size(76, 76).Bg(Palette.Turquoise).Radius(38).Center();
            _level = UIKit.Text("1", UIKit.FontBody, Palette.White, true);
            badge.Add(_level);
            lvl.Add(badge);
            _xp = new ProgressBar(Palette.Turquoise, 22);
            _xp.style.width = 130; _xp.style.flexGrow = 0; _xp.style.marginLeft = 10; _xp.style.marginRight = 10;
            lvl.Add(_xp);
            lvl.pickingMode = PickingMode.Position;
            lvl.RegisterCallback<ClickEvent>(_ => Nav.Profile());
            Add(lvl);

            Add(UIKit.Flex());
            _lives = UIKit.Chip(Icon.Heart, "5", Palette.Coral);
            _lifeTimer = UIKit.Text("", UIKit.FontTiny, Palette.TextSoft, true);
            _lives.Add(_lifeTimer);
            _gold = UIKit.Chip(Icon.Coin, "0", Palette.Gold);
            _crystal = UIKit.Chip(Icon.Crystal, "0", Palette.Turquoise);
            _gold.style.marginLeft = 10; _crystal.style.marginLeft = 10;
            _gold.pickingMode = PickingMode.Position;
            _gold.RegisterCallback<ClickEvent>(_ => Nav.Store());
            Add(_lives); Add(_gold); Add(_crystal);
        }

        public void Tick(float dt)
        {
            _t += dt;
            if (_t < 0.5f) return;
            _t = 0;
            Refresh();
        }

        public void Refresh()
        {
            var gm = GameManager.I;
            if (gm == null || gm.Session == null) return;
            var s = gm.Session;
            var lv = s.Xp.Current;
            _level.text = lv.Level.ToString();
            _xp.Set(lv.Level >= GameConfig.MaxExplorerLevel ? (lv.XpForNextMastery > 0 ? lv.XpIntoMastery / (float)lv.XpForNextMastery : 1f) : lv.XpIntoLevel / (float)lv.XpForNext);
            int lives = s.Lives.Lives;
            UIKit.SetChip(_lives, lives.ToString());
            if (lives < GameConfig.MaxLives)
            {
                var t = s.Lives.TimeToNextLife();
                _lifeTimer.text = string.Format("{0:00}:{1:00}", (int)t.TotalMinutes, t.Seconds);
            }
            else _lifeTimer.text = "Dolu";
            UIKit.SetChip(_gold, s.Economy.Gold.ToString("N0"));
            UIKit.SetChip(_crystal, s.Economy.Crystals.ToString());
        }
    }
}
