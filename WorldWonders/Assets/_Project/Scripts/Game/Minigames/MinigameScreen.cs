using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Minigames;
using WorldWonders.Core.Save;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>
    /// Keşif mini oyunları (Büyüteç, Fırça, Kazı Malası). Kalıcı keşif noktaları ve günlük görevler için ortak ekran.
    /// Bitirmeden çıkmak hiçbir şey kaybettirmez. Günlük görevler eser koleksiyonuna eser eklemez.
    /// </summary>
    public sealed class MinigameScreen : UIScreen
    {
        private const float AreaSize = 900f;

        private readonly bool _daily;
        private readonly int _wonder, _area;
        private readonly ToolType _tool;
        private IDiscoveryMinigame _game;
        private string _title, _goalText;

        private VisualElement _playArea;
        private ProgressBar _bar;
        private Label _status;
        private bool _finished;

        // Büyüteç
        private VisualElement _lens;
        private readonly List<Image> _symbolEls = new List<Image>();
        private bool _lensActive;
        private Vec2 _lensPos;
        // Fırça
        private Texture2D _dustTex;
        private Color32[] _dustPx;
        private bool _brushing;
        private Vec2 _lastBrush;
        // Mala
        private VisualElement[,] _plotEls;

        private int _pointer = -1;

        private MinigameScreen(bool daily, int wonder, int area, ToolType tool)
        {
            _daily = daily; _wonder = wonder; _area = area; _tool = tool;
        }

        public static MinigameScreen ForDiscovery(int wonder, int area)
        {
            return new MinigameScreen(false, wonder, area, GameConfig.ToolForArea(area));
        }

        public static MinigameScreen ForDaily(ToolType tool)
        {
            return new MinigameScreen(true, -1, -1, tool);
        }

        public override bool ShowHud { get { return false; } }

        protected override void Build(VisualElement root)
        {
            float mult = Session.Tools.Multiplier(_tool);
            int toolLevel = Session.Tools.Level(_tool);
            if (_daily)
            {
                var m = Session.Daily.MissionFor(_tool);
                int target = m != null ? m.Target : DailyMissionManager.Template(_tool, 0).Target;
                var tpl = m != null ? DailyMissionManager.Template(_tool, m.TemplateIndex) : DailyMissionManager.Template(_tool, 0);
                _game = MinigameFactory.ForDaily(_tool, target, mult, Session.Daily.CurrentDayKey);
                _title = "Günlük Görev: " + tpl.Title;
                _goalText = tpl.Description;
            }
            else
            {
                _game = MinigameFactory.ForDiscovery(_tool, _wonder, _area, mult);
                var p = DiscoveryManager.Point(_wonder, _area);
                var info = WonderCatalog.Get(_wonder);
                _title = info.AreaNames[_area - 1] + (p.Kind == DiscoveryKind.Artifact ? " · Tarihî Eser" : " · Keşif Sandığı");
                _goalText = GoalText();
            }

            var page = UIKit.Column().Grow().Bg(Palette.Cream);
            var header = UIKit.Row().Pad(UIKit.Gutter, 30);
            header.Add(UIKit.IconButton(Icon.Close, Nav.Back, Palette.White, Palette.Text, 96));
            var col = UIKit.Column(); col.style.marginLeft = 20; col.style.flexGrow = 1;
            col.Add(UIKit.H2(_title));
            col.Add(UIKit.Small(Names.Tool(_tool) + " Sv." + toolLevel + (toolLevel > 1 ? "  ·  +%" + Mathf.RoundToInt((mult - 1f) * 100) + " etki" : "")));
            header.Add(col);
            header.Add(UIKit.IconImage(ToolIcon(_tool), Palette.Turquoise, 90));
            page.Add(header);

            var goal = UIKit.Body(_goalText);
            goal.style.marginLeft = UIKit.Gutter; goal.style.marginRight = UIKit.Gutter; goal.style.marginBottom = 18;
            page.Add(goal);

            var areaWrap = UIKit.Row(); areaWrap.style.justifyContent = Justify.Center;
            _playArea = new VisualElement();
            _playArea.Size(AreaSize, AreaSize).Radius(40).Border(4, Palette.CardBorder);
            _playArea.style.overflow = Overflow.Hidden;
            areaWrap.Add(_playArea);
            page.Add(areaWrap);

            var barRow = UIKit.Row().Pad(UIKit.Gutter, 24);
            _bar = new ProgressBar(Palette.Gold, 40, "");
            barRow.Add(_bar);
            page.Add(barRow);
            _status = UIKit.Small("");
            _status.style.marginLeft = UIKit.Gutter; _status.style.marginRight = UIKit.Gutter;
            page.Add(_status);
            page.Add(UIKit.Flex());
            var note = UIKit.Small(_daily ? "Günlük görevler müze koleksiyonuna eser eklemez. Çıkarsan ilerleme kaybolmaz; yeniden deneyebilirsin."
                                          : "İstediğin zaman çıkabilirsin; keşif noktası kaybolmaz.");
            note.style.marginLeft = UIKit.Gutter; note.style.marginRight = UIKit.Gutter; note.style.marginBottom = 40;
            page.Add(note);
            root.Add(page);

            if (_game is MagnifierGame) BuildMagnifier((MagnifierGame)_game);
            else if (_game is BrushGame) BuildBrush((BrushGame)_game);
            else BuildTrowel((TrowelGame)_game);
            UpdateProgress();
        }

        private string GoalText()
        {
            switch (_tool)
            {
                case ToolType.Magnifier: return "Büyüteci sürükle ve gizli sembollerin üzerinde kısa süre beklet. Hepsini bul!";
                case ToolType.Brush: return "Parmağınla fırçalayarak tozu ve kumu temizle. %" + ((BrushGameTarget())) + " temizlik yeterli.";
                default: return "Toprak katmanlarına dokunarak kaz ve gömülü buluntuları çıkar.";
            }
        }

        private int BrushGameTarget() { return 80; }

        private static Icon ToolIcon(ToolType t)
        {
            return t == ToolType.Magnifier ? Icon.Magnifier : t == ToolType.Brush ? Icon.Brush : Icon.Trowel;
        }

        // ------------------------------------------------------------------ Büyüteç
        private void BuildMagnifier(MagnifierGame g)
        {
            _playArea.Bg(Palette.Lighten(Palette.Sand, 0.25f));
            // Dekoratif taş blok dokusu.
            for (int i = 0; i < 6; i++)
            {
                var stripe = new VisualElement().Ignore();
                stripe.Absolute(0, i * AreaSize / 6f, 0, null);
                stripe.style.height = 3; stripe.Bg(Palette.WithAlpha(Palette.Stone, 0.6f));
                _playArea.Add(stripe);
            }
            Icon[] glyphs = { Icon.Star, Icon.Crystal, Icon.Heart, Icon.Coin, Icon.Trophy, Icon.Puzzle, Icon.Map, Icon.Museum };
            foreach (var s in g.Symbols)
            {
                var el = UIKit.IconImage(glyphs[s.Glyph % glyphs.Length], Palette.Text, 90);
                el.Absolute(s.Pos.X * AreaSize - 45, s.Pos.Y * AreaSize - 45);
                el.style.opacity = 0.06f;
                _symbolEls.Add(el);
                _playArea.Add(el);
            }
            float d = g.LensRadius * 2f * AreaSize;
            _lens = new VisualElement().Ignore();
            _lens.Size(d, d).Radius(d / 2f).Border(10, Palette.GoldDeep).Bg(Palette.WithAlpha(Palette.Sky, 0.35f));
            _lens.style.position = Position.Absolute;
            _lensPos = new Vec2(0.5f, 0.5f);
            PlaceLens();
            _playArea.Add(_lens);

            _playArea.RegisterCallback<PointerDownEvent>(e => { _lensActive = true; Capture(e.pointerId); MoveLens(e.position); });
            _playArea.RegisterCallback<PointerMoveEvent>(e => { if (_lensActive && e.pointerId == _pointer) MoveLens(e.position); });
            _playArea.RegisterCallback<PointerUpEvent>(e => { _lensActive = false; Release(e.pointerId); });
        }

        private void MoveLens(Vector2 panelPos)
        {
            var l = _playArea.WorldToLocal(panelPos);
            _lensPos = new Vec2(Mathf.Clamp01(l.x / AreaSize), Mathf.Clamp01(l.y / AreaSize));
            PlaceLens();
        }

        private void PlaceLens()
        {
            float d = ((MagnifierGame)_game).LensRadius * 2f * AreaSize;
            _lens.style.left = _lensPos.X * AreaSize - d / 2f;
            _lens.style.top = _lensPos.Y * AreaSize - d / 2f;
        }

        // ------------------------------------------------------------------ Fırça
        private void BuildBrush(BrushGame g)
        {
            // Alttaki mozaik (temizlendikçe ortaya çıkar).
            var mosaic = UIKit.Column().Fill().Ignore();
            Color[] tones = { Palette.Turquoise, Palette.Gold, Palette.Cream, Palette.Terracotta, Palette.Green, Palette.Lilac };
            var rng = new Rng(Rng.Hash("mosaic" + _wonder + ":" + _area + ":" + _daily));
            const int n = 9;
            for (int y = 0; y < n; y++)
            {
                var row = UIKit.Row().Ignore(); row.style.flexGrow = 1;
                for (int x = 0; x < n; x++)
                {
                    // Simetrik desen: merkezden uzaklığa göre renk halkaları.
                    int ring = Mathf.Max(Mathf.Abs(x - n / 2), Mathf.Abs(y - n / 2));
                    var c = tones[(ring + (rng.Chance(0.15) ? 1 : 0)) % tones.Length];
                    var cell = new VisualElement().Ignore().Bg(c).Border(2, Palette.WithAlpha(Palette.White, 0.7f));
                    cell.style.flexGrow = 1;
                    row.Add(cell);
                }
                mosaic.Add(row);
            }
            _playArea.Add(mosaic);
            var relic = UIKit.IconImage(_daily ? Icon.Star : Icon.Museum, Palette.WithAlpha(Palette.White, 0.9f), 300);
            relic.Absolute(AreaSize / 2 - 150, AreaSize / 2 - 150);
            _playArea.Add(relic);

            int gs = BrushGame.GridSize;
            _dustTex = new Texture2D(gs, gs, TextureFormat.RGBA32, false) { filterMode = FilterMode.Bilinear, wrapMode = TextureWrapMode.Clamp };
            _dustPx = new Color32[gs * gs];
            var dust = new Image { image = _dustTex, scaleMode = ScaleMode.StretchToFill };
            dust.Fill().Ignore();
            _playArea.Add(dust);
            UpdateDustTexture();

            _playArea.RegisterCallback<PointerDownEvent>(e => { _brushing = true; Capture(e.pointerId); _lastBrush = ToNorm(e.position); g.Dab(_lastBrush, 0.25f); UpdateDustTexture(); });
            _playArea.RegisterCallback<PointerMoveEvent>(e =>
            {
                if (!_brushing || e.pointerId != _pointer) return;
                var p = ToNorm(e.position);
                g.Stroke(_lastBrush, p);
                _lastBrush = p;
                UpdateDustTexture();
            });
            _playArea.RegisterCallback<PointerUpEvent>(e => { _brushing = false; Release(e.pointerId); });
        }

        private void UpdateDustTexture()
        {
            var g = (BrushGame)_game;
            int gs = BrushGame.GridSize;
            Color32 baseC = Palette.Sand;
            for (int y = 0; y < gs; y++)
                for (int x = 0; x < gs; x++)
                {
                    float v = Mathf.Clamp01(g.Dust[x, y] / 0.6f);
                    byte a = (byte)(255 * v);
                    // Doku satırı 0 alttadır; oyun ızgarasında y=0 üsttedir.
                    _dustPx[(gs - 1 - y) * gs + x] = new Color32((byte)(baseC.r - (x * 7 + y * 3) % 18), (byte)(baseC.g - (x * 5 + y * 11) % 16), baseC.b, a);
                }
            _dustTex.SetPixels32(_dustPx);
            _dustTex.Apply(false);
            UpdateProgress();
        }

        private Vec2 ToNorm(Vector2 panelPos)
        {
            var l = _playArea.WorldToLocal(panelPos);
            return new Vec2(Mathf.Clamp01(l.x / AreaSize), Mathf.Clamp01(l.y / AreaSize));
        }

        // ------------------------------------------------------------------ Mala
        private void BuildTrowel(TrowelGame g)
        {
            _playArea.Bg(Palette.Lighten(Palette.Terracotta, 0.55f));
            _plotEls = new VisualElement[TrowelGame.Cols, TrowelGame.Rows];
            var grid = UIKit.Column().Fill().Pad(16);
            for (int y = 0; y < TrowelGame.Rows; y++)
            {
                var row = UIKit.Row(); row.style.flexGrow = 1;
                row.style.alignItems = Align.Stretch;
                for (int x = 0; x < TrowelGame.Cols; x++)
                {
                    int cx = x, cy = y;
                    var cell = new VisualElement().Radius(18).Center();
                    cell.style.flexGrow = 1; cell.style.marginLeft = 5; cell.style.marginRight = 5; cell.style.marginTop = 5; cell.style.marginBottom = 5;
                    cell.RegisterCallback<PointerDownEvent>(e => DigAt(cx, cy));
                    _plotEls[x, y] = cell;
                    row.Add(cell);
                }
                grid.Add(row);
            }
            _playArea.Add(grid);
            for (int y = 0; y < TrowelGame.Rows; y++) for (int x = 0; x < TrowelGame.Cols; x++) PaintPlot(x, y, false);
        }

        private void DigAt(int x, int y)
        {
            if (_finished) return;
            var g = (TrowelGame)_game;
            bool found = g.Dig(x, y);
            // Yükseltilmiş mala komşuları da kazar: hepsini yeniden boya.
            for (int yy = 0; yy < TrowelGame.Rows; yy++) for (int xx = 0; xx < TrowelGame.Cols; xx++) PaintPlot(xx, yy, false);
            var el = _plotEls[x, y];
            Tween.Run(0.18f, k => el.style.scale = new Scale(Vector3.one * (1f - 0.12f * Mathf.Sin(k * Mathf.PI))), null, Ease.Linear);
            if (found)
            {
                PaintPlot(x, y, true);
                UI.Toast("Buluntu çıkarıldı!", Palette.Gold);
            }
            UpdateProgress();
        }

        private void PaintPlot(int x, int y, bool pop)
        {
            var p = ((TrowelGame)_game).Plots[x, y];
            var el = _plotEls[x, y];
            if (p.Layers > 0)
            {
                // Katman ne kadar derinse o kadar koyu toprak (aydınlık palet içinde).
                float depth = p.Layers / 3f;
                el.Bg(Color.Lerp(Palette.Sand, Palette.Terracotta, depth)).Border(3, Palette.WithAlpha(Palette.Text, 0.12f));
                el.Clear();
                int whole = Mathf.CeilToInt(p.Layers - 0.001f);
                for (int i = 0; i < whole; i++)
                {
                    var dot = new VisualElement().Ignore().Size(16, 16).Radius(8).Bg(Palette.WithAlpha(Palette.White, 0.5f));
                    dot.style.marginTop = 2;
                    el.Add(dot);
                }
            }
            else
            {
                el.Clear();
                el.Bg(Palette.Lighten(Palette.Sand, 0.5f)).Border(3, Palette.CardBorder);
                if (p.HasFind && p.Revealed)
                {
                    var ic = UIKit.IconImage(_daily ? Icon.Coin : Icon.Crystal, Palette.Gold, 72);
                    el.Add(ic);
                    if (pop) Tween.Run(0.45f, k => ic.style.scale = new Scale(Vector3.one * Mathf.Lerp(0.2f, 1f, k)), null, Ease.OutBack);
                }
            }
        }

        // ------------------------------------------------------------------ ortak
        private void Capture(int pointerId)
        {
            _pointer = pointerId;
            _playArea.CapturePointer(pointerId);
        }

        private void Release(int pointerId)
        {
            if (_playArea.HasPointerCapture(pointerId)) _playArea.ReleasePointer(pointerId);
            _pointer = -1;
        }

        public override void Tick(float dt)
        {
            var mg = _game as MagnifierGame;
            if (mg != null && !_finished)
            {
                if (_lensActive)
                {
                    var found = mg.Update(_lensPos, dt);
                    foreach (var s in found)
                    {
                        int idx = mg.Symbols.IndexOf(s);
                        var el = _symbolEls[idx];
                        el.tintColor = Palette.GoldDeep;
                        Tween.Run(0.5f, k => { el.style.opacity = Mathf.Lerp(0.06f, 1f, k); el.style.scale = new Scale(Vector3.one * (1f + 0.4f * Mathf.Sin(k * Mathf.PI))); }, null, Ease.OutCubic);
                    }
                    if (found.Count > 0) UpdateProgress();
                }
                // Mercek altındaki semboller hafifçe belirir (ipucu).
                for (int i = 0; i < mg.Symbols.Count; i++)
                {
                    var s = mg.Symbols[i];
                    if (s.Found) continue;
                    _symbolEls[i].style.opacity = 0.06f + Mathf.Clamp01(s.Dwell / MagnifierGame.DwellSeconds) * 0.6f;
                }
            }
        }

        private void UpdateProgress()
        {
            if (_bar == null || _game == null) return;
            string label;
            if (_game is MagnifierGame) { var g = (MagnifierGame)_game; label = g.FoundCount + " / " + g.Symbols.Count + " sembol"; }
            else if (_game is BrushGame) { var g = (BrushGame)_game; label = "%" + g.CleanPercent + " temiz (hedef %" + g.TargetPercent + ")"; }
            else { var g = (TrowelGame)_game; label = g.Revealed + " / " + g.FindCount + " buluntu"; }
            _bar.Set(_game.Progress01, label);
            if (_game.IsComplete && !_finished) Finish();
        }

        private void Finish()
        {
            _finished = true;
            _lensActive = false; _brushing = false;
            Haptics.Strong();
            Tween.Delay(0.6f, ShowReward);
        }

        private void ShowReward()
        {
            var card = UIKit.Card();
            card.style.alignItems = Align.Center;
            if (_daily)
            {
                var before = Session.Save.DailyBonusClaimed;
                bool done = Session.Daily.ReportResult(_tool, _game.ResultValue);
                card.Add(UIKit.IconImage(Icon.Check, Palette.Green, 120));
                card.Add(UIKit.Title(done ? "Görev tamamlandı!" : "Görev ilerledi"));
                card.Add(UIKit.Spacer(12));
                if (done)
                {
                    card.Add(RewardRow(Icon.Coin, "+" + GameConfig.DailyMissionGold(_tool) + " altın", Palette.Gold));
                    card.Add(RewardRow(Icon.Star, "+" + GameConfig.XpDailyMission + " XP", Palette.Turquoise));
                    if (!before && Session.Save.DailyBonusClaimed)
                    {
                        card.Add(UIKit.Spacer(10));
                        card.Add(UIKit.Body("Üç görevin hepsi bitti! Günlük bonus: +" + GameConfig.DailyAllBonusGold + " altın, +" + GameConfig.XpDailyBonus + " XP ve keşif serisi bir gün ilerledi."));
                    }
                }
                else card.Add(UIKit.Body("Bu görev bugün zaten tamamlanmış ya da hedefe ulaşılmadı."));
            }
            else
            {
                var p = DiscoveryManager.Point(_wonder, _area);
                var reward = Session.Discoveries.Complete(_wonder, _area);
                if (p.Kind == DiscoveryKind.Artifact)
                {
                    card.Add(UIKit.IconImage(Icon.Museum, Palette.Gold, 120));
                    card.Add(UIKit.Title(p.Artifact.Name));
                    card.Add(UIKit.Spacer(8));
                    card.Add(UIKit.Pill("Müzeye eklendi", Palette.Turquoise));
                    card.Add(UIKit.Spacer(12));
                    card.Add(UIKit.Body(p.Artifact.History));
                    card.Add(UIKit.Spacer(12));
                    if (reward != null) card.Add(RewardRow(Icon.Star, "+" + reward.Xp + " XP", Palette.Turquoise));
                    if (Session.Museum.IsGalleryComplete(_wonder)) card.Add(UIKit.Body(WonderCatalog.Get(_wonder).Name + " galerisi tamamlandı! Galeri rozeti kazanıldı."));
                }
                else
                {
                    card.Add(UIKit.IconImage(Icon.Chest, Palette.Gold, 120));
                    card.Add(UIKit.Title("Keşif sandığı açıldı!"));
                    card.Add(UIKit.Spacer(12));
                    if (reward != null) AddBundleRows(card, reward);
                    else card.Add(UIKit.Body("Bu sandık zaten açılmış."));
                }
            }
            card.Add(UIKit.Spacer(18));
            VisualElement popup = null;
            var ok = UIKit.Primary("Devam", () => { UI.ClosePopup(popup); Game.SaveNow(); Nav.Back(); });
            ok.style.alignSelf = Align.Stretch;
            card.Add(ok);
            popup = UI.ShowPopup(card, false);
            UI.RefreshHud();
            Game.SaveNow();
        }

        private static void AddBundleRows(VisualElement card, RewardBundle b)
        {
            if (b.Gold > 0) card.Add(RewardRow(Icon.Coin, "+" + b.Gold + " altın", Palette.Gold));
            if (b.Crystals > 0) card.Add(RewardRow(Icon.Crystal, "+" + b.Crystals + " kristal", Palette.Turquoise));
            foreach (var kv in b.Boosters) card.Add(RewardRow(BoosterIcon(kv.Key), "+" + kv.Value + " " + Names.Booster(kv.Key), Palette.Coral));
            if (b.Xp > 0) card.Add(RewardRow(Icon.Star, "+" + b.Xp + " XP", Palette.Turquoise));
        }

        public static Icon BoosterIcon(BoosterType b)
        {
            switch (b)
            {
                case BoosterType.AncientArrow: return Icon.Arrow;
                case BoosterType.AncientBomb: return Icon.Bomb;
                case BoosterType.Hammer: return Icon.Hammer;
                default: return Icon.Shuffle;
            }
        }

        private static VisualElement RewardRow(Icon icon, string text, Color tint)
        {
            var r = UIKit.Row(); r.style.marginTop = 6;
            r.Add(UIKit.IconImage(icon, tint, 56));
            var l = UIKit.Text(text, UIKit.FontBody, Palette.Text, true); l.style.marginLeft = 14;
            r.Add(l);
            return r;
        }

        public override void OnHide()
        {
            _lensActive = false; _brushing = false;
        }

        public override bool OnBack()
        {
            // Bitmiş oyunda ödül penceresi açıkken geri tuşu ödül penceresini kapatıp çıkar.
            return false;
        }
    }
}
