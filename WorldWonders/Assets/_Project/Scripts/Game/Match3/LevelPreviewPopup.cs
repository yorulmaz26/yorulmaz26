using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;

namespace WorldWonders.Game
{
    /// <summary>Bölüm düğümüne dokununca hemen açılan ön izleme kartı.</summary>
    public static class LevelPreviewPopup
    {
        public static void Show(int globalLevel)
        {
            var gm = GameManager.I;
            var s = gm.Session;
            var ui = gm.UI;
            int w = GameConfig.WonderOfLevel(globalLevel);
            int local = GameConfig.LocalLevel(globalLevel);
            int area = GameConfig.AreaOfLocalLevel(local);
            var info = WonderCatalog.Get(w);
            var def = gm.Levels.Get(globalLevel);
            bool unlocked = s.Levels.IsUnlocked(globalLevel);
            int best = s.Stars.StarsOf(globalLevel);

            var card = UIKit.Card();
            var head = UIKit.Row();
            var tc = UIKit.Column(); tc.style.flexGrow = 1; tc.style.flexShrink = 1;
            tc.Add(UIKit.Title("Bölüm " + local));
            tc.Add(UIKit.Small(info.Name + " · " + area + ". alan: " + info.AreaNames[area - 1]));
            head.Add(tc);
            VisualElement popup = null;
            head.Add(UIKit.IconButton(Icon.Close, () => ui.ClosePopup(popup), Palette.White, Palette.Text, 90));
            card.Add(head);
            card.Add(UIKit.Spacer(12));

            var tags = UIKit.Row();
            tags.Add(UIKit.Pill(Names.Difficulty(def.Difficulty), GoalUI.DifficultyColor(def.Difficulty)));
            var sz = UIKit.Pill(def.Width + "×" + def.Height + " · " + def.ShapeName, Palette.Lighten(Palette.Stone, 0.3f), Palette.Text);
            sz.style.marginLeft = 10;
            tags.Add(sz);
            card.Add(tags);
            card.Add(UIKit.Spacer(18));

            if (!unlocked)
            {
                var lr = UIKit.Row();
                lr.Add(UIKit.IconImage(Icon.Lock, Palette.TextSoft, 60));
                var lt = UIKit.Body(s.Levels.IsWonderUnlocked(w) ? "Bu bölüm kilitli. Önce önceki bölümü tamamla." : "Bu harika henüz kilitli.");
                lt.style.marginLeft = 14; lt.style.flexShrink = 1;
                lr.Add(lt);
                card.Add(lr);
                card.Add(UIKit.Spacer(16));
            }

            // Hedefler + hamle
            var gRow = UIKit.Row();
            gRow.Bg(Palette.Cream).Radius(28).Pad(16, 14);
            gRow.style.justifyContent = Justify.Center; gRow.style.flexWrap = Wrap.Wrap;
            foreach (var g in def.Goals) gRow.Add(GoalUI.Item(g, -1, 60));
            var mv = UIKit.Column(); mv.style.alignItems = Align.Center; mv.style.marginLeft = 20;
            mv.Add(UIKit.Text(def.Moves.ToString(), UIKit.FontTitle, Palette.TurquoiseDeep, true));
            mv.Add(UIKit.Text("hamle", UIKit.FontTiny, Palette.TextSoft));
            gRow.Add(mv);
            card.Add(gRow);
            card.Add(UIKit.Spacer(16));

            // En iyi yıldız + ödül
            var sr = UIKit.Row();
            sr.Add(UIKit.Stars(best, 3, 54));
            var bl = UIKit.Small(best > 0 ? "En iyi sonucun" : "Henüz oynanmadı"); bl.style.marginLeft = 12;
            sr.Add(bl);
            card.Add(sr);
            card.Add(UIKit.Spacer(10));

            string reward;
            if (best == 0)
                reward = "İlk tamamlama: " + GameConfig.FirstClearGold(def.Difficulty) + " altın + " + GameConfig.LevelXp(def.Difficulty) + " XP · 2 yıldız +15, 3 yıldız +35 altın";
            else if (best < 3)
                reward = "Kalan ödül: en fazla " + (GameConfig.TotalLevelGold(def.Difficulty, 3) - GameConfig.TotalLevelGold(def.Difficulty, best)) + " altın (yalnız yeni yıldız farkı; tekrar XP yok)";
            else
                reward = "Bu bölümün bütün ödüllerini aldın.";
            var rw = UIKit.Row();
            rw.Add(UIKit.IconImage(Icon.Coin, Palette.Gold, 48));
            var rl = UIKit.Small(reward); rl.style.marginLeft = 10; rl.style.flexShrink = 1;
            rw.Add(rl);
            card.Add(rw);

            if (GameConfig.JigsawPieceForLocalLevel(local) >= 0 && best == 0)
            {
                var jr = UIKit.Row(); jr.style.marginTop = 10;
                jr.Add(UIKit.IconImage(Icon.Puzzle, Palette.GoldDeep, 48));
                var jl = UIKit.Small("İlk tamamlamada Jigsaw parçası " + (GameConfig.JigsawPieceForLocalLevel(local) + 1) + "/12 kazanılır."); jl.style.marginLeft = 10; jl.style.flexShrink = 1;
                jr.Add(jl);
                card.Add(jr);
            }

            card.Add(UIKit.Spacer(16));
            var lifeRow = UIKit.Row();
            lifeRow.Add(UIKit.Chip(Icon.Heart, s.Lives.Lives + "/" + GameConfig.MaxLives, Palette.Coral));
            if (s.Lives.Lives < GameConfig.MaxLives)
            {
                var t = s.Lives.TimeToNextLife();
                var tl = UIKit.Small(string.Format("Sonraki can {0:00}:{1:00}", (int)t.TotalMinutes, t.Seconds)); tl.style.marginLeft = 12;
                lifeRow.Add(tl);
            }
            card.Add(lifeRow);
            card.Add(UIKit.Spacer(12));

            var play = UIKit.Primary(s.Lives.Lives > 0 ? "Oyna" : "Can yok", () => { ui.ClosePopup(popup); Nav.PlayLevel(globalLevel); });
            if (!unlocked) UIKit.SetEnabled(play, false);
            card.Add(play);

            popup = ui.ShowPopup(card);
        }
    }
}
