using System.Collections.Generic;

namespace WorldWonders.Core.Content
{
    public enum CosmeticSource { Free = 0, Store = 1, WonderLevel = 2, WonderStars = 3, ExplorerLevel = 4, Mastery = 5, Milestone = 6 }

    public sealed class CosmeticInfo
    {
        public string Id;
        public string Name;
        public CosmeticSlot Slot;
        public CosmeticSource Source;
        public int GoldPrice;     // yalnız Store
        public string ColorHex;   // renk kozmetikleri ve geçici görseller için
    }

    public sealed class BadgeInfo
    {
        public string Id;
        public string Name;
        public string Description;
    }

    /// <summary>
    /// Kozmetik kataloğu. Kozmetikler oynanış avantajı sağlamaz; yalnız görünüm değiştirir.
    /// Ustalık kozmetikleri sınırsız olduğu için kimlikten üretilir (Resolve).
    /// </summary>
    public static class CosmeticCatalog
    {
        private static readonly Dictionary<string, CosmeticInfo> Fixed = BuildFixed();

        public static IEnumerable<CosmeticInfo> AllFixed { get { return Fixed.Values; } }

        public static string SpecialOutfitId(int wonder) { return "outfit_" + WonderCatalog.Get(wonder).Id; }
        public static string AccessoryId(int wonder, int n) { return "acc_" + WonderCatalog.Get(wonder).Id + "_" + n; }
        public const string AllStarsFrame = "frame_all_stars";
        public const string MasteryOutfit = "outfit_mastery";

        public static CosmeticInfo Resolve(string id)
        {
            CosmeticInfo c;
            if (Fixed.TryGetValue(id, out c)) return c;
            if (id.StartsWith("mastery_"))
            {
                // mastery_<slot>_<n>
                var parts = id.Split('_');
                int n; int.TryParse(parts[parts.Length - 1], out n);
                string hue = HueHex(n);
                switch (parts[1])
                {
                    case "outfitcolor": return new CosmeticInfo { Id = id, Name = "Ustalık Kıyafet Rengi " + n, Slot = CosmeticSlot.OutfitColor, Source = CosmeticSource.Mastery, ColorHex = hue };
                    case "bagcolor": return new CosmeticInfo { Id = id, Name = "Ustalık Çanta Rengi " + n, Slot = CosmeticSlot.BagColor, Source = CosmeticSource.Mastery, ColorHex = hue };
                    case "frame": return new CosmeticInfo { Id = id, Name = "Ustalık Çerçevesi " + n, Slot = CosmeticSlot.ProfileFrame, Source = CosmeticSource.Mastery, ColorHex = hue };
                    case "decor": return new CosmeticInfo { Id = id, Name = "Müze Dekorasyonu " + n, Slot = CosmeticSlot.MuseumDecoration, Source = CosmeticSource.Mastery, ColorHex = hue };
                }
            }
            if (id.StartsWith("lvl_"))
            {
                int n; int.TryParse(id.Substring(id.LastIndexOf('_') + 1), out n);
                if (id.StartsWith("lvl_frame_")) return new CosmeticInfo { Id = id, Name = "Seviye " + n + " Çerçevesi", Slot = CosmeticSlot.ProfileFrame, Source = CosmeticSource.ExplorerLevel, ColorHex = HueHex(n) };
                return new CosmeticInfo { Id = id, Name = "Seviye " + n + " Şapkası", Slot = CosmeticSlot.Hat, Source = CosmeticSource.ExplorerLevel, ColorHex = HueHex(n) };
            }
            return null;
        }

        private static string HueHex(int n)
        {
            // Aydınlık paletten türetilmiş, deterministik açık tonlar.
            string[] p = { "#55C5D3", "#D5A447", "#8FCB79", "#E89A7A", "#9C8FD8", "#F2C14E", "#6FB7A8", "#D97AA6", "#7FA7E0", "#C9B37E" };
            return p[((n % p.Length) + p.Length) % p.Length];
        }

        private static void Add(Dictionary<string, CosmeticInfo> d, string id, string name, CosmeticSlot slot, CosmeticSource src, int price = 0, string color = null)
        {
            d[id] = new CosmeticInfo { Id = id, Name = name, Slot = slot, Source = src, GoldPrice = price, ColorHex = color };
        }

        private static Dictionary<string, CosmeticInfo> BuildFixed()
        {
            var d = new Dictionary<string, CosmeticInfo>();
            Add(d, "body_a", "Kâşif Ada", CosmeticSlot.Body, CosmeticSource.Free);
            Add(d, "body_b", "Kâşif Deniz", CosmeticSlot.Body, CosmeticSource.Free);
            string[] hair = { "#3B2A20", "#7A4B2A", "#C68B4E", "#E3C07A", "#1E1E24", "#A3A3A8" };
            for (int i = 0; i < hair.Length; i++) Add(d, "hair_" + (i + 1), "Saç " + (i + 1), CosmeticSlot.Hair, CosmeticSource.Free, 0, hair[i]);
            string[] skin = { "#F6D7C3", "#EBC0A0", "#D4A07A", "#B07850", "#8A5A3C", "#5E3B28" };
            for (int i = 0; i < skin.Length; i++) Add(d, "skin_" + (i + 1), "Ten " + (i + 1), CosmeticSlot.SkinTone, CosmeticSource.Free, 0, skin[i]);
            Add(d, "outfit_safari", "Safari Gömleği", CosmeticSlot.Outfit, CosmeticSource.Free, 0, "#C9B37E");
            Add(d, "outfit_field", "Arazi Yeleği", CosmeticSlot.Outfit, CosmeticSource.Free, 0, "#8FCB79");
            Add(d, "outfit_linen", "Keten Takım", CosmeticSlot.Outfit, CosmeticSource.Free, 0, "#F8F5ED");
            Add(d, "shoes_boots", "Arazi Botu", CosmeticSlot.Shoes, CosmeticSource.Free, 0, "#7A4B2A");
            Add(d, "shoes_sneakers", "Yürüyüş Ayakkabısı", CosmeticSlot.Shoes, CosmeticSource.Free, 0, "#55C5D3");
            Add(d, "hat_none", "Şapkasız", CosmeticSlot.Hat, CosmeticSource.Free);
            Add(d, "hat_explorer", "Kâşif Şapkası", CosmeticSlot.Hat, CosmeticSource.Free, 0, "#C9B37E");
            Add(d, "hat_cap", "Kep", CosmeticSlot.Hat, CosmeticSource.Free, 0, "#55C5D3");
            Add(d, "bag_satchel", "Deri Çanta", CosmeticSlot.Bag, CosmeticSource.Free, 0, "#7A4B2A");
            Add(d, "bag_backpack", "Sırt Çantası", CosmeticSlot.Bag, CosmeticSource.Free, 0, "#55C5D3");
            Add(d, "acc_none", "Aksesuarsız", CosmeticSlot.Accessory, CosmeticSource.Free);

            // Mağaza (altınla) — yalnız görünüm.
            Add(d, "hat_straw", "Hasır Şapka", CosmeticSlot.Hat, CosmeticSource.Store, 600, "#E3C07A");
            Add(d, "bag_canvas", "Kanvas Çanta", CosmeticSlot.Bag, CosmeticSource.Store, 500, "#8FCB79");
            Add(d, "shoes_sandals", "Sandalet", CosmeticSlot.Shoes, CosmeticSource.Store, 400, "#D5A447");
            Add(d, "acc_scarf", "Turkuaz Fular", CosmeticSlot.Accessory, CosmeticSource.Store, 700, "#55C5D3");

            string[] outfitNames = { "Roma Togası", "Nebati Pelerini", "Babür Kaftanı", "Ming Zırh Yeleği", "İnka Pançosu", "Maya Tüylü Pelerin", "Carioca Keten Takım" };
            string[] accNames =
            {
                "Defne Tacı", "Kartal Broş", "Gladyatör Kalkanı",
                "Kervan Feneri", "Nebati Bilezik", "Kumtaşı Kolye",
                "Lotus Broş", "Mermer Kolye", "Tavuskuşu Tüyü",
                "Ejder Madalyon", "Yeşim Bilezik", "Kâğıt Fener",
                "Kondor Tüyü", "Gümüş Tupu İğnesi", "Dokuma Kuşak",
                "Yeşim Küpe", "Kukulkan Madalyon", "Kakao Kesesi",
                "Sabuntaşı Kolye", "Rio Gün Gözlüğü", "Bossa Şapka Bandı"
            };
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                Add(d, SpecialOutfitId(w), outfitNames[w], CosmeticSlot.Outfit, CosmeticSource.WonderLevel, 0, WonderCatalog.Get(w).ThemeColor);
                for (int n = 1; n <= 3; n++)
                    Add(d, AccessoryId(w, n), accNames[w * 3 + n - 1], CosmeticSlot.Accessory, n == 3 ? CosmeticSource.WonderStars : CosmeticSource.WonderLevel, 0, WonderCatalog.Get(w).ThemeColor);
            }
            Add(d, AllStarsFrame, "1.260 Yıldız Çerçevesi", CosmeticSlot.ProfileFrame, CosmeticSource.Milestone, 0, "#D5A447");
            Add(d, MasteryOutfit, "Ustalık Kıyafeti", CosmeticSlot.Outfit, CosmeticSource.Mastery, 0, "#D5A447");
            return d;
        }

        public static readonly string[] DefaultLoadout = { "body_a", "hair_1", "skin_2", "outfit_safari", "shoes_boots", "hat_explorer", "bag_satchel", "acc_none" };
    }

    public static class BadgeCatalog
    {
        public static string Gallery(int wonder) { return "badge_gallery_" + WonderCatalog.Get(wonder).Id; }
        public static string Stars(int wonder) { return "badge_stars_" + WonderCatalog.Get(wonder).Id; }
        public static string WonderComplete(int wonder) { return "badge_complete_" + WonderCatalog.Get(wonder).Id; }
        public static string StreakCycles(int cycles) { return "badge_streak_" + cycles; }
        public static string Mastery(int level) { return "badge_mastery_" + level; }

        public static BadgeInfo Resolve(string id)
        {
            for (int w = 0; w < GameConfig.WonderCount; w++)
            {
                var name = WonderCatalog.Get(w).Name;
                if (id == Gallery(w)) return new BadgeInfo { Id = id, Name = name + " Galerisi", Description = name + " galerisindeki altı eserin hepsini topla." };
                if (id == Stars(w)) return new BadgeInfo { Id = id, Name = name + " Yıldızı", Description = name + " bölümlerinde 180 yıldızın tamamını topla." };
                if (id == WonderComplete(w)) return new BadgeInfo { Id = id, Name = name + " İnşacısı", Description = name + " anıtını tamamen inşa et." };
            }
            if (id.StartsWith("badge_streak_")) { var n = id.Substring(13); return new BadgeInfo { Id = id, Name = n + " Keşif Serisi", Description = "Yedi günlük keşif serisini " + n + " kez tamamla." }; }
            if (id.StartsWith("badge_mastery_")) { var n = id.Substring(14); return new BadgeInfo { Id = id, Name = "Ustalık " + n, Description = "Ustalık seviyesi " + n + "'e ulaş." }; }
            return new BadgeInfo { Id = id, Name = id, Description = "" };
        }
    }
}
