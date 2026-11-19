using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;
using WorldWonders.Core.Content;
using WorldWonders.Core.Services;

namespace WorldWonders.Game
{
    /// <summary>Günlük keşif görevleri (3 araç) ve yedi günlük keşif serisi.</summary>
    public sealed class DailyMissionsScreen : UIScreen
    {
        private ScrollView _content;
        private Label _reset;

        protected override void Build(VisualElement root)
        {
            UIKit.Page(root, "Günlük Keşif", Nav.Back, out _content);
        }

        public override void OnShow() { Rebuild(); }

        private void Rebuild()
        {
            _content.Clear();
            var daily = Session.Daily;
            var missions = daily.Missions;

            var head = UIKit.Row();
            head.Add(UIKit.IconImage(Icon.Calendar, Palette.Turquoise, 60));
            _reset = UIKit.Small("");
            _reset.style.marginLeft = 14;
            head.Add(_reset);
            head.style.marginBottom = 20;
            _content.Add(head);
            UpdateReset();

            int done = 0;
            foreach (var m in missions)
            {
                if (m.Completed) done++;
                var tpl = DailyMissionManager.Template(m.Tool, m.TemplateIndex);
                var card = UIKit.Card();
                var row = UIKit.Row();
                var iconBg = new VisualElement().Size(120, 120).Radius(60).Center().Bg(m.Completed ? Palette.Lighten(Palette.Green, 0.4f) : Palette.Lighten(Palette.Turquoise, 0.6f));
                iconBg.Add(UIKit.IconImage(m.Completed ? Icon.Check : ToolIcon(m.Tool), m.Completed ? Palette.Green : Palette.TurquoiseDeep, 70));
                row.Add(iconBg);
                var col = UIKit.Column(); col.style.marginLeft = 24; col.style.flexGrow = 1; col.style.flexShrink = 1;
                col.Add(UIKit.Text(Names.Tool(m.Tool) + " · " + tpl.Title, UIKit.FontBody, Palette.Text, true));
                col.Add(UIKit.Small(tpl.Description));
                var rewardRow = UIKit.Row(); rewardRow.style.marginTop = 8;
                rewardRow.Add(UIKit.IconImage(Icon.Coin, Palette.Gold, 40));
                rewardRow.Add(UIKit.Text(" " + GameConfig.DailyMissionGold(m.Tool) + " altın · " + GameConfig.XpDailyMission + " XP", UIKit.FontSmall, Palette.Text, true));
                col.Add(rewardRow);
                var bar = new ProgressBar(m.Completed ? Palette.Green : Palette.Turquoise, 26, m.Progress + "/" + m.Target);
                bar.Set(m.Target > 0 ? m.Progress / (float)m.Target : 0f, m.Progress + "/" + m.Target);
                bar.style.marginTop = 10;
                col.Add(bar);
                row.Add(col);
                card.Add(row);
                if (m.Completed) card.Add(UIKit.Pill("Tamamlandı", Palette.Green));
                else
                {
                    var tool = m.Tool;
                    var play = UIKit.Primary("Oyna", () => Nav.DailyMinigame(tool));
                    card.Add(play);
                }
                _content.Add(card);
            }

            var bonus = UIKit.Card(Palette.Lighten(Palette.Gold, 0.75f));
            var br = UIKit.Row();
            br.Add(UIKit.IconImage(Icon.Chest, Palette.GoldDeep, 80));
            var bc = UIKit.Column(); bc.style.marginLeft = 20; bc.style.flexShrink = 1;
            bc.Add(UIKit.Text("Üç görev bonusu", UIKit.FontBody, Palette.Text, true));
            bc.Add(UIKit.Small(Session.Save.DailyBonusClaimed ? "Bugünkü bonus alındı. Yarın 04.00'te yeni görevler!"
                : done + "/3 görev · Hepsini bitir: +" + GameConfig.DailyAllBonusGold + " altın, +" + GameConfig.XpDailyBonus + " XP. Günde toplam 400 altın."));
            br.Add(bc);
            bonus.Add(br);
            _content.Add(bonus);

            _content.Add(UIKit.Spacer(16));
            _content.Add(UIKit.H2("Yedi Günlük Keşif Serisi"));
            _content.Add(UIKit.Small("Üç görevi tamamladığın her gün seri bir aşama ilerler. Oynamadığın gün ilerleme sıfırlanmaz."));
            _content.Add(UIKit.Spacer(16));

            var streak = Session.Streak;
            int nextDay = streak.NextDay;
            bool advancedToday = Session.Save.StreakLastAdvancedDayKey == Session.Daily.CurrentDayKey;
            var track = UIKit.Row();
            track.style.flexWrap = Wrap.Wrap;
            track.style.justifyContent = Justify.SpaceBetween;
            for (int d = 1; d <= 7; d++)
            {
                bool got = d < nextDay;
                bool current = d == nextDay;
                var r = GameConfig.StreakDayReward(d);
                var cell = UIKit.Column().Radius(28).Pad(10).Center();
                cell.style.width = Length.Percent(23.5f); cell.style.marginBottom = 14; cell.style.height = 210;
                cell.Bg(got ? Palette.Lighten(Palette.Green, 0.45f) : current ? Palette.White : Palette.Lighten(Palette.Stone, 0.6f));
                cell.Border(current ? 5 : 2, current ? Palette.Turquoise : Palette.CardBorder);
                cell.Add(UIKit.Text(d + ". gün", UIKit.FontSmall, Palette.Text, true));
                cell.Add(UIKit.IconImage(got ? Icon.Check : d == 7 ? Icon.Trophy : Icon.Coin, got ? Palette.Green : Palette.Gold, 56));
                var lbl = UIKit.Text(r.Gold + " altın" + (r.Crystals > 0 ? "\n+" + r.Crystals + " kristal" : "") + (r.Boosters.Count > 0 ? "\n+" + BoosterText(r) : "") + (d == 7 ? "\n+100 XP" : ""),
                    UIKit.FontTiny - 4, Palette.Text);
                lbl.style.unityTextAlign = TextAnchor.MiddleCenter;
                cell.Add(lbl);
                track.Add(cell);
            }
            _content.Add(track);
            _content.Add(UIKit.Small(advancedToday ? "Bugün seri ilerledi. Sıradaki ödül: " + nextDay + ". gün." : "Sıradaki ödül: " + nextDay + ". gün."));
            _content.Add(UIKit.Spacer(14));

            var cycles = UIKit.Card();
            cycles.Add(UIKit.Text("Tamamlanan döngü: " + streak.CompletedCycles, UIKit.FontBody, Palette.Text, true));
            var badges = UIKit.Row(); badges.style.marginTop = 12; badges.style.flexWrap = Wrap.Wrap;
            foreach (var ms in GameConfig.StreakCycleBadgeMilestones)
            {
                bool has = Session.Save.Badges.Contains(BadgeCatalog.StreakCycles(ms));
                var b = UIKit.Column().Center().Pad(12).Radius(24);
                b.Bg(has ? Palette.Lighten(Palette.Gold, 0.6f) : Palette.Lighten(Palette.Stone, 0.6f));
                b.style.marginRight = 12; b.style.width = 170;
                b.Add(UIKit.IconImage(has ? Icon.Trophy : Icon.Lock, has ? Palette.GoldDeep : Palette.Locked, 56));
                b.Add(UIKit.Text(ms + " döngü", UIKit.FontTiny, Palette.Text, true));
                badges.Add(b);
            }
            cycles.Add(badges);
            _content.Add(cycles);
        }

        private static string BoosterText(RewardBundle r)
        {
            foreach (var kv in r.Boosters) return Names.Booster(kv.Key);
            return "";
        }

        private static Icon ToolIcon(ToolType t)
        {
            return t == ToolType.Magnifier ? Icon.Magnifier : t == ToolType.Brush ? Icon.Brush : Icon.Trowel;
        }

        private float _t;
        public override void Tick(float dt)
        {
            _t += dt;
            if (_t < 1f) return;
            _t = 0;
            string before = Session.Save.DailyDayKey;
            Session.Daily.Refresh();
            if (before != Session.Save.DailyDayKey) { Rebuild(); return; }
            UpdateReset();
        }

        private void UpdateReset()
        {
            if (_reset == null) return;
            var t = Session.Daily.TimeToReset();
            _reset.text = string.Format("Yeni görevler {0:00}:{1:00}:{2:00} sonra (yerel 04.00)", (int)t.TotalHours, t.Minutes, t.Seconds);
        }
    }
}
