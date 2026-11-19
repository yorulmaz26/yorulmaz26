using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Match3;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Hedeflerin UI gösterimi (Match-3 ekranı ve bölüm ön izlemesi ortak kullanır).</summary>
    public static class GoalUI
    {
        public static string Name(GoalDefinition g)
        {
            switch (g.Type)
            {
                case GoalType.Collect: return Names.Tile(g.Color);
                case GoalType.ClearSand: return "Kum";
                case GoalType.BreakCrates: return "Sandık";
                case GoalType.ReleaseChains: return "Zincir";
                default: return "Puan";
            }
        }

        public static VisualElement Swatch(GoalDefinition g, float size)
        {
            switch (g.Type)
            {
                case GoalType.Collect:
                    return new VisualElement().Size(size, size).Bg(Palette.TileColors[(int)g.Color]).Radius(size * 0.5f).Border(4, Palette.White);
                case GoalType.ClearSand:
                    return new VisualElement().Size(size, size).Bg(Palette.Sand).Radius(12).Border(4, Palette.White);
                case GoalType.BreakCrates:
                    return new VisualElement().Size(size, size).Bg(Palette.Hex("#B07A4A")).Radius(8).Border(4, Palette.Hex("#7A4B2A"));
                case GoalType.ReleaseChains:
                    return UIKit.IconImage(Icon.Lock, Palette.Hex("#8C96A0"), size);
                default:
                    return UIKit.IconImage(Icon.Star, Palette.Gold, size);
            }
        }

        /// <summary>Tek hedef öğesi. current &lt; 0 ise yalnız hedef sayısı gösterilir.</summary>
        public static VisualElement Item(GoalDefinition g, int current, float size = 64)
        {
            var col = UIKit.Column();
            col.style.alignItems = Align.Center;
            col.style.marginLeft = 14; col.style.marginRight = 14;
            var holder = new VisualElement().Size(size, size).Center();
            holder.Add(Swatch(g, size));
            if (current >= 0 && current >= g.Count)
            {
                var ok = new VisualElement().Size(size * 0.55f, size * 0.55f).Bg(Palette.Green).Radius(size * 0.3f).Center();
                ok.Absolute(null, null, -8, -8);
                ok.Add(UIKit.IconImage(Icon.Check, Palette.White, size * 0.4f));
                holder.Add(ok);
            }
            col.Add(holder);
            string txt = current < 0 ? g.Count.ToString() : Mathf.Min(current, g.Count) + "/" + g.Count;
            var l = UIKit.Text(txt, UIKit.FontSmall, Palette.Text, true);
            l.style.unityTextAlign = TextAnchor.MiddleCenter; l.style.marginTop = 4;
            col.Add(l);
            var n = UIKit.Text(Name(g), UIKit.FontTiny, Palette.TextSoft);
            n.style.unityTextAlign = TextAnchor.MiddleCenter;
            col.Add(n);
            return col;
        }

        public static Color DifficultyColor(Difficulty d)
        {
            switch (d)
            {
                case Difficulty.Easy: return Palette.Green;
                case Difficulty.Medium: return Palette.Turquoise;
                case Difficulty.Hard: return Palette.Gold;
                default: return Palette.Coral;
            }
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
    }

    /// <summary>
    /// Match-3 bölüm ekranı: bölümü başlatır (can kuralları Core'da), 3D tahtayı gösterir,
    /// hamle/hedef/güçlendirici arayüzünü ve kazanma/kaybetme sonuçlarını yönetir.
    /// </summary>
    public sealed class Match3Screen : UIScreen
    {
        private readonly int _global;
        private readonly int _wonder, _local;
        private LevelDefinition _def;
        private Match3Game _game;
        private Match3BoardView _view;
        private bool _started, _finished, _resolving;
        private BoosterType? _targeting;

        private Label _moves, _targetHint;
        private VisualElement _goalsRow, _boosterRow;

        public override bool ShowHud { get { return false; } }

        public Match3Screen(int globalLevel)
        {
            _global = globalLevel;
            _wonder = GameConfig.WonderOfLevel(globalLevel);
            _local = GameConfig.LocalLevel(globalLevel);
        }

        protected override void Build(VisualElement root)
        {
            _def = Game.Levels.Get(_global);

            // ---- Üst çubuk
            var top = UIKit.Column().Ignore();
            top.Absolute(0, 0, 0, null);
            top.Pad(UIKit.Gutter, 24);
            var bar = UIKit.Row();
            bar.Add(UIKit.IconButton(Icon.Gear, OpenPause, Palette.White, Palette.Text, 100));
            var info = UIKit.Column(); info.style.marginLeft = 16;
            info.Add(UIKit.H2("Bölüm " + _local));
            var sub = UIKit.Row();
            sub.Add(UIKit.Pill(Names.Difficulty(_def.Difficulty), GoalUI.DifficultyColor(_def.Difficulty)));
            var wn = UIKit.Small(WonderCatalog.Get(_wonder).Name); wn.style.marginLeft = 12;
            sub.Add(wn);
            info.Add(sub);
            bar.Add(info);
            bar.Add(UIKit.Flex());
            var movesBadge = UIKit.Column().Size(170, 170).Bg(Palette.Turquoise).Radius(85).Center().Border(6, Palette.White);
            _moves = UIKit.Text(_def.Moves.ToString(), 72, Palette.White, true);
            _moves.style.unityTextAlign = TextAnchor.MiddleCenter;
            movesBadge.Add(_moves);
            var ml = UIKit.Text("Hamle", UIKit.FontTiny, Palette.White, true); ml.style.unityTextAlign = TextAnchor.MiddleCenter;
            movesBadge.Add(ml);
            bar.Add(movesBadge);
            top.Add(bar);

            var goalsCard = UIKit.Row();
            goalsCard.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(36).Pad(20, 14).Border(2, Palette.CardBorder);
            goalsCard.style.marginTop = 16; goalsCard.style.justifyContent = Justify.Center;
            var gt = UIKit.Text("Hedefler", UIKit.FontSmall, Palette.TextSoft, true); gt.style.marginRight = 12;
            goalsCard.Add(gt);
            _goalsRow = UIKit.Row();
            goalsCard.Add(_goalsRow);
            top.Add(goalsCard);
            root.Add(top);

            // ---- Alt güçlendirici çubuğu
            var bottom = UIKit.Column().Ignore();
            bottom.Absolute(0, null, 0, 0);
            bottom.style.alignItems = Align.Center;
            bottom.style.paddingBottom = 40;
            _targetHint = UIKit.Text("", UIKit.FontSmall, Palette.Text, true);
            _targetHint.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(30).Pad(28, 10);
            _targetHint.style.display = DisplayStyle.None; _targetHint.style.marginBottom = 14;
            bottom.Add(_targetHint);
            _boosterRow = UIKit.Row();
            _boosterRow.Bg(Palette.WithAlpha(Palette.White, 0.92f)).Radius(60).Pad(24, 14).Border(2, Palette.CardBorder);
            bottom.Add(_boosterRow);
            root.Add(bottom);
        }

        public override void OnShow()
        {
            if (!_started)
            {
                _started = true;
                var err = Session.Levels.StartLevel(_global);
                if (err != StartLevelError.None)
                {
                    _finished = true;
                    Tween.Delay(0.01f, () =>
                    {
                        UI.Back();
                        if (err == StartLevelError.NoLives) Nav.NoLives();
                        else UI.Info("Bölüm başlatılamadı", "Bu bölüm henüz kilitli.");
                    });
                    return;
                }
                ulong seed = (ulong)DateTime.UtcNow.Ticks ^ ((ulong)_global * 0x9E3779B97F4A7C15UL);
                _game = new Match3Game(_def, seed);
            }
            if (_game == null) return;
            _view = Game.ShowView<Match3BoardView>();
            if (_view.Game3 != _game)
            {
                _view.Setup(_game);
                _view.SwapRequested -= OnSwap; _view.CellTapped -= OnCellTapped;
                _view.SwapRequested += OnSwap; _view.CellTapped += OnCellTapped;
            }
            RefreshUI();
        }

        public override void OnHide()
        {
            if (_view != null) { _view.SwapRequested -= OnSwap; _view.CellTapped -= OnCellTapped; _view.SetTargeting(false); }
        }

        public override bool OnBack()
        {
            if (_finished) return false;
            if (UI.HasPopup) { UI.ClosePopup(); return true; }
            ConfirmQuit();
            return true;
        }

        // ------------------------------------------------------------- arayüz
        private void RefreshUI()
        {
            if (_game == null) return;
            _moves.text = _game.MovesLeft.ToString();
            _moves.style.color = _game.MovesLeft <= 5 ? Palette.Hex("#FFF2C2") : Palette.White;
            _goalsRow.Clear();
            foreach (var g in _game.Goals) _goalsRow.Add(GoalUI.Item(g.Def, g.Current, 60));
            RefreshBoosters();
        }

        private void RefreshBoosters()
        {
            _boosterRow.Clear();
            foreach (BoosterType b in Enum.GetValues(typeof(BoosterType)))
            {
                var bt = b;
                int count = Session.Economy.BoosterCount(b);
                bool active = _targeting.HasValue && _targeting.Value == b;
                var col = UIKit.Column(); col.style.alignItems = Align.Center; col.style.marginLeft = 10; col.style.marginRight = 10;
                var btn = UIKit.IconButton(GoalUI.BoosterIcon(b), () => OnBooster(bt), active ? Palette.Gold : (count > 0 ? Palette.Turquoise : Palette.Locked), Palette.White, 124, count > 0 ? count.ToString() : "+");
                col.Add(btn);
                var l = UIKit.Text(Names.Booster(b), UIKit.FontTiny, Palette.Text, true); l.style.unityTextAlign = TextAnchor.MiddleCenter;
                col.Add(l);
                _boosterRow.Add(col);
            }
            if (_targeting.HasValue)
            {
                _targetHint.text = Names.Booster(_targeting.Value) + ": hedef taşa dokun (iptal için düğmeye tekrar dokun)";
                _targetHint.style.display = DisplayStyle.Flex;
            }
            else _targetHint.style.display = DisplayStyle.None;
        }

        private void OnBooster(BoosterType b)
        {
            if (_finished || _resolving || _game == null || _game.State != Match3State.Playing || (_view != null && _view.IsBusy)) return;
            if (_targeting.HasValue && _targeting.Value == b) { CancelTargeting(); return; }
            if (Session.Economy.BoosterCount(b) <= 0)
            {
                int price = GameConfig.BoosterPrice(b);
                UI.Confirm(Names.Booster(b), price + " altın karşılığında 1 adet " + Names.Booster(b) + " satın almak ister misin? (Altının: " + Session.Economy.Gold + ")", "Satın Al", () =>
                {
                    if (!Session.Economy.BuyBooster(b)) UI.Info("Yetersiz altın", "Bölümleri tamamlayarak, sandıkları açarak ve günlük görevlerle altın kazanabilirsin.");
                    else UI.Toast(Names.Booster(b) + " satın alındı.", Palette.Gold);
                    RefreshBoosters();
                });
                return;
            }
            if (b == BoosterType.Shuffle)
            {
                CancelTargeting();
                var r = _game.UseBooster(BoosterType.Shuffle, default(Pos));
                if (r.Valid) { Session.Economy.TryConsumeBooster(b); Resolve(r, false); }
                return;
            }
            _targeting = b;
            _view.SetTargeting(true);
            RefreshBoosters();
        }

        private void CancelTargeting()
        {
            _targeting = null;
            if (_view != null) _view.SetTargeting(false);
            RefreshBoosters();
        }

        private void OnCellTapped(Pos p)
        {
            if (!_targeting.HasValue || _resolving) return;
            var b = _targeting.Value;
            var r = _game.UseBooster(b, p);
            if (!r.Valid) { UI.Toast("Bu hücreye uygulanamaz.", Palette.Coral); _view.HideTargetMarker(); return; }
            Session.Economy.TryConsumeBooster(b);
            _targeting = null;
            _view.SetTargeting(false);
            Resolve(r, false);
        }

        private void OnSwap(Pos a, Pos b)
        {
            if (_finished || _resolving || _game.State != Match3State.Playing) return;
            var r = _game.Swap(a, b);
            if (!r.Valid) { _view.AnimateInvalid(a, b, null); return; }
            Resolve(r, true);
        }

        private void Resolve(MoveResult r, bool isSwap)
        {
            _resolving = true;
            RefreshBoosters();
            _view.Animate(r, isSwap, () =>
            {
                _resolving = false;
                RefreshUI();
                if (_game.State == Match3State.Won) OnWin();
                else if (_game.State == Match3State.Lost) OnLose();
            });
        }

        // ------------------------------------------------------------- duraklat / çık
        private void OpenPause()
        {
            if (_finished) return;
            var card = UIKit.Card();
            card.Add(UIKit.H2("Duraklatıldı"));
            card.Add(UIKit.Spacer(12));
            card.Add(UIKit.Body("Bölüm " + _local + " · " + WonderCatalog.Get(_wonder).Name));
            card.Add(UIKit.Small("Kalan hamle: " + (_game != null ? _game.MovesLeft : 0) + " · Can: " + Session.Lives.Lives));
            card.Add(UIKit.Spacer(24));
            VisualElement popup = null;
            card.Add(UIKit.Primary("Devam Et", () => UI.ClosePopup(popup)));
            card.Add(UIKit.Secondary("Bölümden Çık", () => { UI.ClosePopup(popup); ConfirmQuit(); }));
            popup = UI.ShowPopup(card);
        }

        private void ConfirmQuit()
        {
            UI.Confirm("Bölümden çıkılsın mı?", "Başlanmış bölümü terk etmek 1 can kaybettirir.", "Çık (−1 can)", () =>
            {
                _finished = true;
                Session.Levels.AbandonActiveLevel();
                Game.SaveNow();
                UI.Back();
            }, "Oynamaya Devam Et", null, Palette.Coral);
        }

        // ------------------------------------------------------------- sonuçlar
        private void OnWin()
        {
            if (_finished) return;
            _finished = true;
            int stars = _game.Stars;
            var rep = Session.Levels.CompleteLevel(_global, stars);
            Game.SaveNow();
            Haptics.Strong();
            UI.RefreshHud();

            var card = UIKit.Card();
            card.style.alignItems = Align.Center;
            var t = UIKit.Title("Bölüm Tamamlandı!"); t.style.unityTextAlign = TextAnchor.MiddleCenter;
            card.Add(t);
            card.Add(UIKit.Small("Bölüm " + _local + " · " + WonderCatalog.Get(_wonder).Name));
            card.Add(UIKit.Spacer(20));

            var starRow = UIKit.Row();
            var starEls = new List<VisualElement>();
            for (int i = 0; i < 3; i++)
            {
                var s = UIKit.IconImage(Icon.Star, i < stars ? Palette.Gold : Palette.Locked, i == 1 ? 150 : 120);
                s.style.marginLeft = 8; s.style.marginRight = 8;
                if (i < stars) { s.style.scale = new Scale(Vector3.zero); starEls.Add(s); }
                starRow.Add(s);
            }
            card.Add(starRow);
            for (int i = 0; i < starEls.Count; i++)
            {
                var s = starEls[i];
                Tween.Run(0.35f, k => s.style.scale = new Scale(new Vector3(k, k, 1)), null, Ease.OutBack, 0.25f + i * 0.3f);
            }
            if (rep.PreviousBestStars > 0 && stars <= rep.PreviousBestStars)
                card.Add(UIKit.Small("En iyi sonucun: " + rep.PreviousBestStars + " yıldız"));

            card.Add(UIKit.Spacer(16));
            var rewards = UIKit.Row(); rewards.style.justifyContent = Justify.Center;
            rewards.Add(UIKit.Chip(Icon.Coin, "+" + rep.GoldGained, Palette.Gold));
            var xp = UIKit.Chip(Icon.Star, rep.XpGained > 0 ? "+" + rep.XpGained + " XP" : "XP yok", Palette.Turquoise);
            xp.style.marginLeft = 12;
            rewards.Add(xp);
            card.Add(rewards);
            if (!rep.FirstClear)
                card.Add(UIKit.Small(rep.GoldGained > 0 ? "Tekrar oynama: yalnız yeni yıldız farkı verildi." : "Tekrar oynama: yeni ödül yok (en iyi sonuç geçilmedi)."));
            if (rep.NewAreaUnlocked) { card.Add(UIKit.Spacer(8)); card.Add(UIKit.Pill("Yeni alan açıldı!", Palette.Green)); }

            VisualElement popup = null;
            if (rep.PieceEarned >= 0)
            {
                card.Add(UIKit.Spacer(16));
                var pr = UIKit.Row();
                pr.Bg(Palette.Lighten(Palette.Gold, 0.7f)).Radius(28).Pad(20, 14);
                pr.Add(UIKit.IconImage(Icon.Puzzle, Palette.GoldDeep, 70));
                var pl = UIKit.Body("Jigsaw parçası " + (rep.PieceEarned + 1) + "/12 kazandın! Anıtı inşa etmek için yerleştir.");
                pl.style.marginLeft = 14; pl.style.flexShrink = 1;
                pr.Add(pl);
                card.Add(pr);
                int piece = rep.PieceEarned;
                card.Add(UIKit.Gold("Parçayı Yerleştir", () => { UI.CloseAllPopups(); UI.Back(); Nav.Jigsaw(_wonder, piece); }));
            }
            card.Add(UIKit.Primary("Devam", () => { UI.CloseAllPopups(); UI.Back(); }));
            card.Add(UIKit.Secondary("Tekrar Oyna", () => { UI.CloseAllPopups(); UI.Back(); Nav.PlayLevel(_global); }));
            popup = UI.ShowPopup(card, false);
        }

        private void OnLose()
        {
            if (_finished) return;
            _finished = true;
            Session.Levels.FailLevel(_global);
            Game.SaveNow();
            UI.RefreshHud();

            var card = UIKit.Card();
            card.style.alignItems = Align.Center;
            card.Add(UIKit.Title("Hamlen Bitti"));
            card.Add(UIKit.Spacer(12));
            var goals = UIKit.Row(); goals.style.justifyContent = Justify.Center;
            foreach (var g in _game.Goals) goals.Add(GoalUI.Item(g.Def, g.Current, 56));
            card.Add(goals);
            card.Add(UIKit.Spacer(12));
            int lives = Session.Lives.Lives;
            var lr = UIKit.Row(); lr.style.justifyContent = Justify.Center;
            lr.Add(UIKit.Chip(Icon.Heart, lives + " can kaldı", Palette.Coral));
            card.Add(lr);
            if (lives < GameConfig.MaxLives)
            {
                var t = Session.Lives.TimeToNextLife();
                card.Add(UIKit.Small(string.Format("Sonraki can: {0:00}:{1:00}", (int)t.TotalMinutes, t.Seconds)));
            }
            card.Add(UIKit.Spacer(16));
            var retry = UIKit.Primary("Tekrar Dene", () => { UI.CloseAllPopups(); UI.Back(); Nav.PlayLevel(_global); });
            card.Add(retry);
            if (lives <= 0) UIKit.SetEnabled(retry, false);
            card.Add(UIKit.Secondary("Haritaya Dön", () => { UI.CloseAllPopups(); UI.Back(); }));
            UI.ShowPopup(card, false);
        }
    }
}
