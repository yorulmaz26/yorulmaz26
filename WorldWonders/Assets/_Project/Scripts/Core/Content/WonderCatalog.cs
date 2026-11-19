using System.Collections.Generic;

namespace WorldWonders.Core.Content
{
    public sealed class ArtifactInfo
    {
        public string Id;
        public int Wonder;
        public int Index;   // 0..5
        public int Area;    // 1,3,5,7,9,11
        public string Name;
        public string History;
    }

    public sealed class WonderInfo
    {
        public int Index;
        public string Id;
        public string Name;
        public string Country;
        public string DestinationTheme;
        public string[] AreaNames;      // 12
        public string[] ConstructionStages; // 12
        public ArtifactInfo[] Artifacts; // 6
        /// <summary>Harita ve tema rengi (hex).</summary>
        public string ThemeColor;
    }

    /// <summary>Yedi harikanın sabit içeriği. Tarihî metinler kısa ve doğrulanabilir bilgilerden oluşur.</summary>
    public static class WonderCatalog
    {
        public static readonly WonderInfo[] All = Build();

        public static WonderInfo Get(int index) { return All[index]; }

        public static IEnumerable<ArtifactInfo> AllArtifacts()
        {
            foreach (var w in All) foreach (var a in w.Artifacts) yield return a;
        }

        public static ArtifactInfo ArtifactForArea(int wonder, int area)
        {
            if (area % 2 == 0) return null;
            return All[wonder].Artifacts[(area - 1) / 2];
        }

        private static WonderInfo W(int i, string id, string name, string country, string theme, string color, string[] areas, string[] stages, string[][] arts)
        {
            var w = new WonderInfo { Index = i, Id = id, Name = name, Country = country, DestinationTheme = theme, ThemeColor = color, AreaNames = areas, ConstructionStages = stages };
            w.Artifacts = new ArtifactInfo[6];
            for (int k = 0; k < 6; k++)
                w.Artifacts[k] = new ArtifactInfo { Id = id + "_art" + (k + 1), Wonder = i, Index = k, Area = k * 2 + 1, Name = arts[k][0], History = arts[k][1] };
            return w;
        }

        private static WonderInfo[] Build()
        {
            return new[]
            {
                W(0, "colosseum", "Kolezyum", "İtalya", "Roma kalıntıları", "#D9A066",
                    new[] { "Forum Girişi", "Titus Kemeri", "Via Sacra", "Palatin Yamacı", "Ludus Magnus", "Meta Sudans", "Konstantin Kemeri", "Caelius Tepesi", "Esquilin Bahçeleri", "Domus Aurea", "Hypogeum Kapısı", "Arena Meydanı" },
                    new[] { "Temel halkası", "Zemin kat kemerleri", "Hypogeum dehlizleri", "Arena zemini", "Birinci kat kemerleri", "İkinci kat kemerleri", "Oturma basamakları", "İmparator locası", "Üçüncü kat", "Attika duvarı", "Velarium direkleri", "Harabe cephe ve ışıklar" },
                    new[]
                    {
                        new[] { "Gladyatör Miğferi", "Murmillo gibi gladyatör tipleri, yüzü tamamen kapatan geniş kenarlı tunç miğferler takardı." },
                        new[] { "Titus Sestertiusu", "MS 80'de açılan amfitiyatro, İmparator Titus döneminde basılan sikkelerde tasvir edildi." },
                        new[] { "Tessera Bileti", "Seyirciler, oturacakları bölümü gösteren tessera adlı fişlerle numaralı girişlerden içeri alınırdı." },
                        new[] { "Velarium Halkası", "Seyircileri güneşten koruyan dev tente (velarium), denizciler tarafından direk ve halatlarla gerilirdi." },
                        new[] { "Sütun Başlığı", "Dış cephede alttan üste Toskana, İon ve Korint düzenlerinde yarım sütunlar sıralanır." },
                        new[] { "Hypogeum Makarası", "Arenanın altındaki hypogeum'da hayvanlar ve dekorlar ahşap asansörlerle arenaya çıkarılırdı." }
                    }),
                W(1, "petra", "Petra", "Ürdün", "Petra kanyonları", "#D97A5B",
                    new[] { "Bab es-Siq", "Siq Geçidi", "Hazine Meydanı", "Cepheler Sokağı", "Tiyatro", "Kral Mezarları", "Sütunlu Cadde", "Büyük Tapınak", "Kasr el-Bint", "Aslanlı Triklinyum", "Manastır Yolu", "Ed-Deyr" },
                    new[] { "Kaya yüzeyi", "Alt cephe hatları", "Basamaklar", "Giriş portali", "Alt sütunlar", "Alt alınlık", "Orta friz", "Üst kat kaidesi", "Tholos", "Yan köşkler", "Urne ve tepelik", "Kanyon ışıkları" },
                    new[]
                    {
                        new[] { "Nebati Kandili", "Nebatiler ışık için pişmiş topraktan yağ kandilleri üretirdi." },
                        new[] { "İnce Boyalı Kâse", "Nebati seramikleri, yumurta kabuğu inceliğindeki duvarları ve bitki motifli boyamalarıyla tanınır." },
                        new[] { "IV. Aretas Sikkesi", "Nebati krallığının parlak döneminde, IV. Aretas (MÖ 9 – MS 40) adına sikkeler basıldı." },
                        new[] { "Su Kanalı Parçası", "Nebatiler, Siq boyunca kayaya oyulmuş kanallar ve pişmiş toprak borularla kente su taşıdı." },
                        new[] { "Dushara Betili", "Nebati tanrısı Dushara çoğunlukla figürsüz, dikdörtgen bir taş blokla temsil edilirdi." },
                        new[] { "Hazine Urnesi", "El Hazne'nin tepesindeki taş urnede hazine saklandığı efsanesi yüzünden açılmış kurşun izleri vardır." }
                    }),
                W(2, "tajmahal", "Tac Mahal", "Hindistan", "Tac Mahal bahçeleri", "#E9E1D3",
                    new[] { "Yamuna Kıyısı", "Büyük Kapı", "Batı Bahçesi", "Doğu Bahçesi", "Su Kanalları", "Lotus Havuzu", "Cami", "Cevap Binası", "Kaide Terası", "Minareler", "Mezar Odası", "Ay Bahçesi" },
                    new[] { "Kaide platformu", "Kare gövde", "Pishtak kemerleri", "Kenar nişleri", "Mermer kaplama", "Kubbe kasnağı", "Ana kubbe", "Çatri köşkleri", "Alem", "Kuzey minareleri", "Güney minareleri", "Bahçe ve yansıma havuzu" },
                    new[]
                    {
                        new[] { "Pietra Dura Panosu", "Beyaz mermere değerli ve yarı değerli taşlar kakılarak çiçek motifleri oluşturuldu." },
                        new[] { "Hat Levhası", "Kapılardaki yazıların harfleri yukarı doğru büyütüldü; böylece aşağıdan bakınca eşit boyda görünür." },
                        new[] { "Babür Mühür Yüzüğü", "Babür sarayında fermanlar ve belgeler mühür yüzükleriyle onaylanırdı." },
                        new[] { "Mermer Jali", "Mezar odasını çevreleyen delikli mermer paravanlar ışığı süzerek içeri alır." },
                        new[] { "Çarbağ Planı", "Bahçe, kanallarla dört eşit parçaya bölünen Babür çarbağ düzenine göre tasarlandı." },
                        new[] { "Kubbe Alemi", "Ana kubbenin tepesindeki alem başlangıçta altındı; 19. yüzyılda yaldızlı tunç bir kopyayla değiştirildi." }
                    }),
                W(3, "greatwall", "Çin Seddi", "Çin", "Çin Seddi dağları", "#8E9C78",
                    new[] { "Badaling Eteği", "Juyongguan Geçidi", "Mutianyu Sırtı", "Jinshanling", "Simatai", "Gubeikou", "Huanghuacheng", "Jiankou", "Shanhaiguan", "Laolongtou", "Jiayuguan", "Gözcü Doruğu" },
                    new[] { "Sıkıştırılmış toprak çekirdek", "Taş temel", "Alt duvar sırası", "Üst duvar sırası", "Mazgallar", "Devriye yolu", "Basamaklar", "Birinci gözetleme kulesi", "İkinci gözetleme kulesi", "İşaret kulesi", "Kale kapısı", "Dağ sırtı ve sis" },
                    new[]
                    {
                        new[] { "Damgalı Ming Tuğlası", "Ming döneminde birçok sur bölümü fırınlanmış tuğlayla örüldü; bazı tuğlalarda üretim yerini gösteren damgalar bulunur." },
                        new[] { "İşaret Kulesi Fenerleri", "Gözetleme kuleleri, düşman yaklaşımını duman ve ateş sinyalleriyle kuleden kuleye iletirdi." },
                        new[] { "Tunç Arbalet Tetiği", "Han dönemi sınır birlikleri hassas tunç tetik mekanizmalı arbaletler kullanırdı." },
                        new[] { "Sınır Geçiş Belgesi", "Sur kapılarından geçen yolcu ve tüccarlar resmî geçiş belgesi göstermek zorundaydı." },
                        new[] { "Toprak Sıkıştırma Kalıbı", "Erken dönem surlar, ahşap kalıplar içinde katman katman sıkıştırılan topraktan yapıldı." },
                        new[] { "Jiayuguan Kapı Tokmağı", "Jiayuguan Kalesi, Ming Seddi'nin batı ucunda İpek Yolu'nu denetleyen geçitti." }
                    }),
                W(4, "machupicchu", "Machu Picchu", "Peru", "Machu Picchu terasları", "#7FB77E",
                    new[] { "Urubamba Vadisi", "İnka Patikası", "Güneş Kapısı", "Bekçi Kulübesi", "Tarım Terasları", "Kuru Hendek", "Taş Ocağı", "Güneş Tapınağı", "Üç Pencere Tapınağı", "İntihuatana Tepesi", "Kutsal Kaya", "Huayna Picchu" },
                    new[] { "Dağ sırtı", "Alt teraslar", "Üst teraslar", "Merdivenler", "Su kanalları", "Taş evler", "Güneş Tapınağı", "Üç Pencere Tapınağı", "Ana Meydan", "İntihuatana", "Saz çatılar", "Bulutlar ve zirve" },
                    new[]
                    {
                        new[] { "Quipu", "İnkalar yazı yerine, düğümlü renkli iplerden oluşan quipu ile sayısal kayıt tuttu." },
                        new[] { "İntihuatana Maketi", "İntihuatana, güneşin hareketleriyle ilişkilendirilen ve kayadan oyulmuş bir taştır." },
                        new[] { "Tumi Bıçağı", "Yarım ay ağızlı tumi, And kültürlerinde törenlerde kullanılan bir bıçaktır." },
                        new[] { "Aryballo Testisi", "Sivri dipli büyük aryballo testileri, iple sırtta taşınacak şekilde yapılırdı." },
                        new[] { "Chakitaqlla", "Ayakla bastırılan bu tarım aleti, teraslarda toprağı işlemek için kullanılırdı." },
                        new[] { "Gümüş Lama Figürini", "İnkalar değerli metallerden küçük lama figürinlerini adak olarak bırakırdı." }
                    }),
                W(5, "chichenitza", "Chichén Itzá", "Meksika", "Maya ormanı", "#5FA36B",
                    new[] { "Orman Girişi", "Kutsal Cenote Yolu", "Venüs Platformu", "Kafatası Duvarı", "Büyük Top Sahası", "Jaguar Tapınağı", "Savaşçılar Tapınağı", "Bin Sütun", "Pazar Yeri", "El Caracol", "Rahibeler Binası", "El Castillo Meydanı" },
                    new[] { "Birinci teras", "İkinci teras", "Üçüncü teras", "Dördüncü teras", "Beşinci teras", "Altıncı teras", "Yedinci teras", "Sekizinci teras", "Dokuzuncu teras", "Dört merdiven", "Yılan başları", "Tepe tapınağı" },
                    new[]
                    {
                        new[] { "Chac Mool Heykelciği", "Yarı uzanmış, başını yana çevirmiş ve karnında tabak tutan figürler Chichén Itzá'da bulunur." },
                        new[] { "Kukulkan Yılan Başı", "El Castillo merdiveninin dibindeki yılan başları, ekinokslarda gölgeyle oluşan yılan gövdesine bağlanır." },
                        new[] { "Takvim Glifi", "El Castillo'nun dört merdiveni 91'er basamaklıdır; tepe platformuyla toplam 365'e ulaşır." },
                        new[] { "Top Oyunu Halkası", "Büyük Top Sahası'nın duvarlarında yüksekte asılı taş halkalar vardır." },
                        new[] { "Yeşim Kolye", "Kutsal Cenote'den çıkarılan adaklar arasında yeşim, altın ve seramik eserler bulunur." },
                        new[] { "Kopal Tütsü Kabı", "Mayalar ağaç reçinesi kopalı törenlerde tütsü olarak yakardı." }
                    }),
                W(6, "christredeemer", "Kurtarıcı İsa", "Brezilya", "Rio tepeleri", "#7EC8E3",
                    new[] { "Cosme Velho", "Dişli Tren Durağı", "Tijuca Ormanı", "Paineiras", "Şelale Yolu", "Seyir Terası", "Guanabara Manzarası", "Copacabana Bakışı", "Şeker Somunu Bakışı", "Kaide Merdivenleri", "Şapel", "Corcovado Zirvesi" },
                    new[] { "Zirve kayası", "Kaide temeli", "Kaide ve şapel", "Betonarme iskelet", "Gövde alt kısmı", "Gövde üst kısmı", "Sol kol", "Sağ kol", "Eller", "Baş", "Sabuntaşı mozaik", "Işıklandırma" },
                    new[]
                    {
                        new[] { "Sabuntaşı Mozaik Karosu", "Heykelin yüzeyi, binlerce küçük üçgen sabuntaşı karoyla kaplandı." },
                        new[] { "Alçı El Modeli", "Fransız heykeltıraş Paul Landowski heykelin tasarımında yer aldı; yüzü Gheorghe Leonida yonttu." },
                        new[] { "Dişli Tren Bileti", "1884'te açılan Corcovado dişli treni yolcuları ormanın içinden zirveye taşır." },
                        new[] { "Mühendis Çizimi", "Mühendis Heitor da Silva Costa'nın yönettiği yapım tamamlandı ve heykel 1931'de açıldı." },
                        new[] { "Paratoner Parçası", "Zirvedeki heykele sık sık yıldırım düşer; bu yüzden paratoner sistemiyle korunur." },
                        new[] { "Betonarme Numune", "Yaklaşık 30 metrelik heykel, 8 metrelik kaidesinin üzerinde betonarme bir iskeletle yükselir." }
                    }),
            };
        }
    }

    public static class Names
    {
        public static string Tile(TileColor c)
        {
            switch (c)
            {
                case TileColor.GoldCoin: return "Altın Para";
                case TileColor.Scroll: return "Parşömen";
                case TileColor.Amphora: return "Amfora";
                case TileColor.Scarab: return "Skarabe";
                case TileColor.Compass: return "Pusula";
                case TileColor.Crown: return "Taç";
                default: return "-";
            }
        }

        public static string Booster(BoosterType b)
        {
            switch (b)
            {
                case BoosterType.AncientArrow: return "Antik Ok";
                case BoosterType.AncientBomb: return "Antik Bomba";
                case BoosterType.Hammer: return "Çekiç";
                default: return "Karıştırma";
            }
        }

        public static string Tool(ToolType t)
        {
            switch (t)
            {
                case ToolType.Magnifier: return "Büyüteç";
                case ToolType.Brush: return "Fırça";
                default: return "Kazı Malası";
            }
        }

        public static string Difficulty(Core.Difficulty d)
        {
            switch (d)
            {
                case Core.Difficulty.Easy: return "Kolay";
                case Core.Difficulty.Medium: return "Orta";
                case Core.Difficulty.Hard: return "Zor";
                default: return "Çok Zor";
            }
        }

        public static string Title(int level)
        {
            if (level >= 100) return "Dünya Harikaları Ustası";
            if (level >= 80) return "Dünya Gezgini";
            if (level >= 60) return "Usta Arkeolog";
            if (level >= 40) return "Deneyimli Kâşif";
            if (level >= 25) return "Tarih Araştırmacısı";
            if (level >= 10) return "Genç Kâşif";
            return "Meraklı Gezgin";
        }
    }
}
