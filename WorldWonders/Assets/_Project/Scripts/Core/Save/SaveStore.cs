using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json;

namespace WorldWonders.Core.Save
{
    public enum LoadSource { Main, Temp, Backup, Fresh }

    public sealed class LoadResult
    {
        public SaveData Data;
        public LoadSource Source;
        public string Warning;
    }

    /// <summary>
    /// Kayıtları cihazda atomik ve bozulmaya dayanıklı biçimde yazar.
    /// Dosya biçimi: ilk satır "WW1 &lt;sha256-hex&gt;", ardından JSON gövde.
    /// Yazma sırası: .tmp yaz → doğrula → mevcut sağlam ana dosyayı .bak'a kopyala → .tmp'yi ana dosyaya taşı.
    /// Okuma sırası: ana → .tmp → .bak; sağlama toplamı tutmayan dosya kullanılmaz.
    /// </summary>
    public sealed class SaveStore
    {
        private const string Magic = "WW1";
        private readonly string _dir;
        private readonly string _main, _tmp, _bak;

        public static readonly JsonSerializerSettings JsonSettings = new JsonSerializerSettings
        {
            Formatting = Formatting.None,
            NullValueHandling = NullValueHandling.Ignore,
            DateTimeZoneHandling = DateTimeZoneHandling.Utc,
            ObjectCreationHandling = ObjectCreationHandling.Replace
        };

        public SaveStore(string directory, string fileName = "worldwonders.save")
        {
            _dir = directory;
            Directory.CreateDirectory(_dir);
            _main = Path.Combine(_dir, fileName);
            _tmp = _main + ".tmp";
            _bak = _main + ".bak";
        }

        public string MainPath { get { return _main; } }
        public string BackupPath { get { return _bak; } }

        public static string Serialize(SaveData data)
        {
            string json = JsonConvert.SerializeObject(data, JsonSettings);
            return Magic + " " + Sha256Hex(json) + "\n" + json;
        }

        /// <summary>Bozuksa null döner.</summary>
        public static SaveData Deserialize(string text)
        {
            if (string.IsNullOrEmpty(text)) return null;
            int nl = text.IndexOf('\n');
            if (nl < 0) return null;
            string header = text.Substring(0, nl);
            string json = text.Substring(nl + 1);
            var parts = header.Split(' ');
            if (parts.Length != 2 || parts[0] != Magic) return null;
            if (!string.Equals(parts[1], Sha256Hex(json), StringComparison.OrdinalIgnoreCase)) return null;
            try
            {
                var data = JsonConvert.DeserializeObject<SaveData>(json, JsonSettings);
                if (data == null) return null;
                data.Normalize();
                SaveMigrations.Migrate(data);
                return data;
            }
            catch (JsonException) { return null; }
        }

        public void Write(SaveData data)
        {
            string text = Serialize(data);
            File.WriteAllText(_tmp, text, new UTF8Encoding(false));
            // Yazılanı geri okuyup doğrula (yarım yazmaya karşı).
            if (Deserialize(File.ReadAllText(_tmp, Encoding.UTF8)) == null)
                throw new IOException("Kayıt doğrulanamadı; önceki kayıt korunuyor.");
            if (File.Exists(_main) && Deserialize(SafeRead(_main)) != null)
                File.Copy(_main, _bak, true);
            if (File.Exists(_main)) File.Delete(_main);
            File.Move(_tmp, _main);
        }

        public LoadResult Load(DateTime utcNow)
        {
            var d = Deserialize(SafeRead(_main));
            if (d != null) return new LoadResult { Data = d, Source = LoadSource.Main };
            d = Deserialize(SafeRead(_tmp));
            if (d != null) return new LoadResult { Data = d, Source = LoadSource.Temp, Warning = "Ana kayıt okunamadı; yarım kalan son kayıt kullanıldı." };
            d = Deserialize(SafeRead(_bak));
            if (d != null) return new LoadResult { Data = d, Source = LoadSource.Backup, Warning = "Ana kayıt bozuktu; önceki sağlam kayıt geri yüklendi." };
            bool anyExisted = File.Exists(_main) || File.Exists(_bak);
            return new LoadResult { Data = SaveData.CreateNew(utcNow), Source = LoadSource.Fresh, Warning = anyExisted ? "Kayıt dosyaları okunamadı; yeni oyun başlatıldı." : null };
        }

        // ---- Kullanıcı yedeği (dışa/içe aktarma) ----
        public string ExportBackup(SaveData data, string path = null)
        {
            path = path ?? Path.Combine(_dir, "worldwonders_backup_" + DateTime.UtcNow.ToString("yyyyMMdd_HHmmss") + ".wwbak");
            File.WriteAllText(path, Serialize(data), new UTF8Encoding(false));
            return path;
        }

        /// <summary>Yedek dosyayı doğrular; geçersizse null.</summary>
        public SaveData ImportBackup(string path)
        {
            return Deserialize(SafeRead(path));
        }

        public string[] ListBackups()
        {
            return Directory.GetFiles(_dir, "*.wwbak");
        }

        private static string SafeRead(string path)
        {
            try { return File.Exists(path) ? File.ReadAllText(path, Encoding.UTF8) : null; }
            catch (IOException) { return null; }
            catch (UnauthorizedAccessException) { return null; }
        }

        public static string Sha256Hex(string s)
        {
            using (var sha = SHA256.Create())
            {
                var bytes = sha.ComputeHash(Encoding.UTF8.GetBytes(s));
                var sb = new StringBuilder(bytes.Length * 2);
                foreach (var b in bytes) sb.Append(b.ToString("x2"));
                return sb.ToString();
            }
        }
    }

    public static class SaveMigrations
    {
        public static void Migrate(SaveData d)
        {
            if (d.Version > SaveData.CurrentVersion)
                throw new JsonException("Kayıt bu sürümden yeni: " + d.Version);
            // v1 ilk sürüm. Gelecekteki adımlar: if (d.Version == 1) { ...; d.Version = 2; }
            d.Version = SaveData.CurrentVersion;
        }
    }
}
