using System;
using System.IO;
using UnityEngine;
using UnityEngine.UIElements;
using WorldWonders.Core;

namespace WorldWonders.Game
{
    /// <summary>Ayarlar: ses/dokunsal, can bilgisi, yedekleme ve geri yükleme, kayıt bilgisi.</summary>
    public sealed class SettingsScreen : UIScreen
    {
        private ScrollView _content;

        protected override void Build(VisualElement root)
        {
            UIKit.Page(root, "Ayarlar", Nav.Back, out _content);
        }

        public override void OnShow() { Rebuild(); }

        private void Rebuild()
        {
            _content.Clear();
            var s = Session.Save;

            var sound = UIKit.Card();
            sound.Add(UIKit.H2("Ses ve titreşim"));
            sound.Add(UIKit.Spacer(8));
            sound.Add(Switch("Müzik", s.MusicOn, v => { s.MusicOn = v; Game.SaveNow(); }));
            sound.Add(Switch("Efekt sesleri", s.SfxOn, v => { s.SfxOn = v; Game.SaveNow(); }));
            sound.Add(Switch("Titreşim", s.HapticsOn, v => { s.HapticsOn = v; Game.SaveNow(); }));
            _content.Add(sound);

            var lives = UIKit.Card();
            lives.Add(UIKit.H2("Canlar"));
            lives.Add(UIKit.Spacer(8));
            lives.Add(UIKit.Body("En fazla " + GameConfig.MaxLives + " can. Her can " + (int)Session.Lives.RegenInterval.TotalMinutes + " dakikada dolar; bu süre cihaz saatiyle çevrimdışı hesaplanır."));
            lives.Add(UIKit.Small("Başarısız ya da yarıda bırakılan bölüm 1 can kaybettirir; kazanılan bölüm can harcamaz. Saat geri alındığında can kazanılmaz."));
            _content.Add(lives);

            var backup = UIKit.Card();
            backup.Add(UIKit.H2("Yedekleme"));
            backup.Add(UIKit.Spacer(8));
            backup.Add(UIKit.Small("İlerlemen yalnız bu cihazda tutulur. Kayıt atomik yazılır ve bozulmaya karşı önceki sağlam kopya korunur. Ayrıca elle yedek alabilirsin."));
            backup.Add(UIKit.Primary("Yedek Al", () =>
            {
                Game.SaveNow();
                try
                {
                    string path = Game.Saves.ExportBackup(Session);
                    UI.Toast("Yedek alındı: " + Path.GetFileName(path), Palette.Green);
                }
                catch (Exception e) { UI.Toast("Yedek alınamadı: " + e.Message, Palette.Coral); }
                Rebuild();
            }));
            string[] files;
            try { files = Game.Saves.ListBackups(); } catch (Exception) { files = new string[0]; }
            Array.Sort(files);
            Array.Reverse(files);
            if (files.Length == 0) backup.Add(UIKit.Small("Henüz yedek yok."));
            int shown = 0;
            foreach (var f in files)
            {
                if (shown++ >= 8) break;
                var path = f;
                var row = UIKit.Row(); row.style.marginTop = 12;
                var col = UIKit.Column(); col.style.flexGrow = 1; col.style.flexShrink = 1;
                col.Add(UIKit.Text(Path.GetFileName(f), UIKit.FontSmall, Palette.Text, true));
                DateTime t; try { t = File.GetLastWriteTime(f); } catch (Exception) { t = DateTime.MinValue; }
                col.Add(UIKit.Small(t.ToString("dd.MM.yyyy HH:mm")));
                row.Add(col);
                var restore = UIKit.Secondary("Geri Yükle", () => Restore(path));
                restore.style.width = 260;
                row.Add(restore);
                backup.Add(row);
            }
            _content.Add(backup);

            var info = UIKit.Card();
            info.Add(UIKit.H2("Kayıt bilgisi"));
            info.Add(UIKit.Spacer(8));
            info.Add(UIKit.Small("Kayıt sayısı: " + s.SaveCounter));
            info.Add(UIKit.Small("Son kayıt: " + s.LastSavedUtc.ToLocalTime().ToString("dd.MM.yyyy HH:mm:ss")));
            info.Add(UIKit.Small("Kayıt sürümü: " + s.Version));
            info.Add(UIKit.Small("Oyun tamamen çevrimdışı çalışır. Hesap gerekmez; veriler cihaz dışına gönderilmez."));
            _content.Add(info);

            var about = UIKit.Card();
            about.Add(UIKit.H2("Hakkında"));
            about.Add(UIKit.Spacer(8));
            about.Add(UIKit.Small("World Wonders · Sürüm " + Application.version));
            about.Add(UIKit.Small("Geçici görseller kullanılıyor: 3D modeller, ikonlar ve dokular prosedürel olarak üretilen geçici varlıklardır; final sanat varlıklarıyla değiştirilecektir."));
            _content.Add(about);
        }

        private void Restore(string path)
        {
            var data = Game.Saves.ImportBackup(path);
            if (data == null) { UI.Info("Yedek okunamadı", "Bu yedek dosyası bozuk ya da değiştirilmiş; geri yüklenemez."); return; }
            var p = new WorldWonders.Core.Services.GameSession(data, new DeviceClock()).Profile.Snapshot();
            UI.Confirm("Yedeği geri yükle?",
                "Seviye " + p.Level.Level + " · " + p.LevelsCompleted + " bölüm · " + p.TotalStars + " yıldız. Mevcut ilerlemenin yerine geçecek. Önce mevcut durumun yedeğini alman önerilir.",
                "Geri Yükle", () =>
                {
                    try { Game.Saves.ExportBackup(Session); } catch (Exception) { }
                    Game.ReplaceSave(data);
                    UI.Toast("Yedek geri yüklendi.", Palette.Green);
                }, "Vazgeç", null, Palette.Coral);
        }

        private static VisualElement Switch(string label, bool value, Action<bool> onChange)
        {
            var row = UIKit.Row(); row.style.marginTop = 10; row.style.marginBottom = 10;
            var l = UIKit.Body(label); l.style.flexGrow = 1;
            row.Add(l);
            var track = new VisualElement().Size(130, 70).Radius(35);
            var knob = new VisualElement().Size(56, 56).Radius(28).Bg(Palette.White).Ignore();
            knob.style.position = Position.Absolute; knob.style.top = 7;
            track.Add(knob);
            bool state = value;
            Action paint = () =>
            {
                track.Bg(state ? Palette.Turquoise : Palette.Locked);
                knob.style.left = state ? 67 : 7;
            };
            paint();
            track.RegisterCallback<ClickEvent>(_ => { state = !state; paint(); onChange(state); });
            row.Add(track);
            return row;
        }
    }
}
