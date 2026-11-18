package com.yanlisrenk.logic.shop

enum class ShopCategory(val title: String, val icon: String) {
    CHARACTER("Karakterler", "character"),
    COSTUME("Kostümler", "costume"),
    COLOR_FX("Renk Efektleri", "palette"),
    GATE_FX("Kapı Efektleri", "sparkle"),
    TRAIL("Karakter İzleri", "trail"),
    ANIMATION("Animasyonlar", "magic"),
    BOOST("Güçlendirmeler", "bolt"),
    LIFE("Can Destekleri", "heart_full"),
    THEME("Tema Paketleri", "map"),
    COLLECTIBLE("Koleksiyon", "box"),
}

enum class Currency(val tr: String) { GOLD("Altın"), DIAMOND("Elmas") }

enum class Body { SQUARE, CIRCLE, TRIANGLE, DIAMOND, STAR, HEART, HEXAGON, CLOUD, EGG, GHOST, DROP, TALL }
enum class Eyes { ROUND, VISOR, SLEEPY, BIG, THREE, HAPPY }
enum class Feature {
    NONE, ANTENNA, PANDA, CAT, ALIEN, SPARKLE, RAINBOW, GOLD_TRIM, BEE, BEAR, BUNNY, PENGUIN, FROG, OCTO, CHICK,
    DRAGON, FROST, CANDY, CRATER, RAYS, SPIKES, CROWN, GHOST, DROP, CLOUD, PRISM
}
enum class Idle { BOUNCE, WOBBLE, SPIN, FLOAT, PULSE }

/** Görsel parametreleri olan market ürünü. [style] çizim varyantıdır. */
data class ShopItem(
    val id: String,
    val category: ShopCategory,
    val name: String,
    val description: String,
    val price: Int,
    val currency: Currency,
    /** Gereken oyuncu seviyesi (0 yok). */
    val requiredLevel: Int = 0,
    /** Satın alınamaz; başarı / ödülle açılır. */
    val rewardOnly: Boolean = false,
    val style: Int = 0,
    val body: Body = Body.SQUARE,
    val eyes: Eyes = Eyes.ROUND,
    val feature: Feature = Feature.NONE,
    val idle: Idle = Idle.BOUNCE,
    /** Tüketilebilir (güçlendirme/can) mi? */
    val consumable: Boolean = false,
    val free: Boolean = false,
)

object ShopCatalog {
    private fun ch(id: String, name: String, desc: String, price: Int, cur: Currency, body: Body, eyes: Eyes, f: Feature, idle: Idle, lvl: Int = 0, reward: Boolean = false, free: Boolean = false) =
        ShopItem("char_$id", ShopCategory.CHARACTER, name, desc, price, cur, lvl, reward, 0, body, eyes, f, idle, free = free)

    val characters = listOf(
        ch("klasik", "Klasik Küp", "Her şeyin başladığı küp.", 0, Currency.GOLD, Body.SQUARE, Eyes.ROUND, Feature.NONE, Idle.BOUNCE, free = true),
        ch("top", "Zıp Top", "Yerinde duramayan neşeli top.", 400, Currency.GOLD, Body.CIRCLE, Eyes.HAPPY, Feature.NONE, Idle.BOUNCE),
        ch("ucgen", "Üçgen Tayfa", "Sivri zekâlı üçgen.", 600, Currency.GOLD, Body.TRIANGLE, Eyes.ROUND, Feature.NONE, Idle.WOBBLE),
        ch("robot", "Sevimli Robot", "Antenli küçük yardımcı.", 900, Currency.GOLD, Body.SQUARE, Eyes.VISOR, Feature.ANTENNA, Idle.PULSE),
        ch("panda", "Mini Panda", "Bambu yerine renk sever.", 1200, Currency.GOLD, Body.CIRCLE, Eyes.ROUND, Feature.PANDA, Idle.WOBBLE),
        ch("kedi", "Renkli Kedi", "Miyav! Dokunuşlara bayılır.", 1200, Currency.GOLD, Body.CIRCLE, Eyes.HAPPY, Feature.CAT, Idle.BOUNCE),
        ch("uzayli", "Uzaylı", "Üç gözü var, hiçbir rengi kaçırmaz.", 1600, Currency.GOLD, Body.EGG, Eyes.THREE, Feature.ALIEN, Idle.FLOAT),
        ch("kristal", "Kristal Karakter", "Işığı kıran parlak kristal.", 25, Currency.DIAMOND, Body.DIAMOND, Eyes.BIG, Feature.SPARKLE, Idle.SPIN),
        ch("gokkusagi", "Gökkuşağı Küpü", "Üzerinde tüm renkler var.", 40, Currency.DIAMOND, Body.SQUARE, Eyes.HAPPY, Feature.RAINBOW, Idle.BOUNCE),
        ch("altin_robot", "Altın Robot", "Seviye 20 kahramanlarına özel.", 3000, Currency.GOLD, Body.SQUARE, Eyes.VISOR, Feature.GOLD_TRIM, Idle.PULSE, lvl = 20),
        ch("bulut", "Bulut Karakter", "Pamuk gibi yumuşak.", 1400, Currency.GOLD, Body.CLOUD, Eyes.SLEEPY, Feature.CLOUD, Idle.FLOAT),
        ch("yildiz", "Yıldız Kahraman", "Parlayan bir yıldız.", 30, Currency.DIAMOND, Body.STAR, Eyes.BIG, Feature.SPARKLE, Idle.SPIN),
        ch("kalp", "Kalpçik", "Sevgi dolu bir kalp.", 1000, Currency.GOLD, Body.HEART, Eyes.HAPPY, Feature.NONE, Idle.PULSE),
        ch("petek", "Petek Arı", "Vızz! Altıgen arı.", 1500, Currency.GOLD, Body.HEXAGON, Eyes.ROUND, Feature.BEE, Idle.WOBBLE),
        ch("ayicik", "Ayıcık", "Sarılmaya hazır.", 1300, Currency.GOLD, Body.CIRCLE, Eyes.ROUND, Feature.BEAR, Idle.BOUNCE),
        ch("tavsan", "Tavşan", "Uzun kulaklı zıpzıp.", 1300, Currency.GOLD, Body.CIRCLE, Eyes.HAPPY, Feature.BUNNY, Idle.BOUNCE),
        ch("penguen", "Penguen", "Buz Vadisi'nin yerlisi.", 1800, Currency.GOLD, Body.EGG, Eyes.ROUND, Feature.PENGUIN, Idle.WOBBLE),
        ch("kurbaga", "Kurbağa", "Vırak! Zıplamayı sever.", 1100, Currency.GOLD, Body.CIRCLE, Eyes.BIG, Feature.FROG, Idle.BOUNCE),
        ch("hayalet", "Hayaletçik", "Hiç korkutucu değil.", 2000, Currency.GOLD, Body.GHOST, Eyes.SLEEPY, Feature.GHOST, Idle.FLOAT),
        ch("damla", "Damla", "Su Altı Dünyası'ndan.", 1600, Currency.GOLD, Body.DROP, Eyes.HAPPY, Feature.DROP, Idle.PULSE),
        ch("ahtapot", "Ahtapotçuk", "Sekiz kolla dokunur.", 2200, Currency.GOLD, Body.CIRCLE, Eyes.BIG, Feature.OCTO, Idle.FLOAT),
        ch("civciv", "Civciv", "Cik cik!", 900, Currency.GOLD, Body.CIRCLE, Eyes.ROUND, Feature.CHICK, Idle.BOUNCE),
        ch("ejder", "Minik Ejder", "Ateş yerine renk püskürtür.", 60, Currency.DIAMOND, Body.SQUARE, Eyes.HAPPY, Feature.DRAGON, Idle.WOBBLE),
        ch("buz", "Buz Küpü", "Serin ve parlak.", 1700, Currency.GOLD, Body.SQUARE, Eyes.SLEEPY, Feature.FROST, Idle.SPIN),
        ch("seker", "Şeker Kalıbı", "Şeker Diyarı'nın tatlısı.", 1500, Currency.GOLD, Body.CIRCLE, Eyes.HAPPY, Feature.CANDY, Idle.SPIN),
        ch("ay", "Ay Dede", "Geceleri parlar.", 2400, Currency.GOLD, Body.CIRCLE, Eyes.SLEEPY, Feature.CRATER, Idle.FLOAT),
        ch("gunes", "Güneşçik", "Her yeri aydınlatır.", 2400, Currency.GOLD, Body.CIRCLE, Eyes.HAPPY, Feature.RAYS, Idle.SPIN),
        ch("kaktus", "Kaktüs", "Güneş Çölü'nün dikenli dostu.", 1900, Currency.GOLD, Body.TALL, Eyes.ROUND, Feature.SPIKES, Idle.WOBBLE),
        ch("prizma", "Prizma", "Prizma Sarayı'ndan geldi.", 80, Currency.DIAMOND, Body.TRIANGLE, Eyes.BIG, Feature.PRISM, Idle.SPIN),
        ch("kral", "Kral Küp", "Büyük Renk Evreni'nin hükümdarı. 500. bölümü bitirenlere.", 0, Currency.GOLD, Body.SQUARE, Eyes.HAPPY, Feature.CROWN, Idle.PULSE, reward = true),
    )

    private fun it(cat: ShopCategory, id: String, name: String, desc: String, price: Int, cur: Currency, style: Int, lvl: Int = 0, free: Boolean = false, reward: Boolean = false) =
        ShopItem(id, cat, name, desc, price, cur, lvl, reward, style, free = free)

    val costumes = listOf(
        it(ShopCategory.COSTUME, "cos_none", "Kostümsüz", "Doğal hâlin.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.COSTUME, "cos_party", "Parti Şapkası", "Her bölüm bir parti!", 500, Currency.GOLD, 1),
        it(ShopCategory.COSTUME, "cos_crown", "Altın Taç", "Gerçek bir renk kralı.", 30, Currency.DIAMOND, 2),
        it(ShopCategory.COSTUME, "cos_glasses", "Yuvarlak Gözlük", "Renkleri daha net gör.", 600, Currency.GOLD, 3),
        it(ShopCategory.COSTUME, "cos_sun", "Güneş Gözlüğü", "Havalı görünüm.", 800, Currency.GOLD, 4),
        it(ShopCategory.COSTUME, "cos_bow", "Papyon", "Şık ve zarif.", 500, Currency.GOLD, 5),
        it(ShopCategory.COSTUME, "cos_scarf", "Atkı", "Buz Vadisi için ideal.", 700, Currency.GOLD, 6),
        it(ShopCategory.COSTUME, "cos_helmet", "Uzay Kaskı", "Uzay İstasyonu'na hazır.", 1500, Currency.GOLD, 7, lvl = 10),
        it(ShopCategory.COSTUME, "cos_pirate", "Korsan Bandı", "Ahoy!", 900, Currency.GOLD, 8),
        it(ShopCategory.COSTUME, "cos_flower", "Çiçek Taç", "Renk Bahçesi'nden.", 650, Currency.GOLD, 9),
        it(ShopCategory.COSTUME, "cos_wizard", "Sihirbaz Şapkası", "Sihirli Kütüphane'nin bilgesi.", 40, Currency.DIAMOND, 10),
        it(ShopCategory.COSTUME, "cos_headphones", "Kulaklık", "Ritmi hisset.", 1100, Currency.GOLD, 11),
        it(ShopCategory.COSTUME, "cos_beanie", "Bere", "Sıcacık.", 550, Currency.GOLD, 12),
        it(ShopCategory.COSTUME, "cos_cape", "Kahraman Pelerini", "Uçuyormuş gibi!", 25, Currency.DIAMOND, 13),
        it(ShopCategory.COSTUME, "cos_unicorn", "Tek Boynuz", "Gökkuşağı enerjisi.", 50, Currency.DIAMOND, 14),
        it(ShopCategory.COSTUME, "cos_chef", "Şef Şapkası", "Renk Fabrikası şefi.", 1300, Currency.GOLD, 15, lvl = 15),
    )

    val colorFx = listOf(
        it(ShopCategory.COLOR_FX, "cfx_pop", "Klasik Pop", "Küçük bir patlama.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.COLOR_FX, "cfx_sparkle", "Parıltı", "Yıldız tozları saçar.", 600, Currency.GOLD, 1),
        it(ShopCategory.COLOR_FX, "cfx_ring", "Halka Dalga", "Renkli halka yayılır.", 800, Currency.GOLD, 2),
        it(ShopCategory.COLOR_FX, "cfx_confetti", "Konfeti", "Her dokunuş bir kutlama.", 1200, Currency.GOLD, 3),
        it(ShopCategory.COLOR_FX, "cfx_starburst", "Yıldız Patlaması", "Yıldızlar fışkırır.", 20, Currency.DIAMOND, 4),
        it(ShopCategory.COLOR_FX, "cfx_hearts", "Kalpler", "Sevgi dolu dokunuşlar.", 1000, Currency.GOLD, 5),
        it(ShopCategory.COLOR_FX, "cfx_gems", "Mücevher", "Elmas parçaları.", 30, Currency.DIAMOND, 6),
        it(ShopCategory.COLOR_FX, "cfx_rainbow", "Gökkuşağı", "Dört renk birden.", 2000, Currency.GOLD, 7, lvl = 12),
    )

    val gateFx = listOf(
        it(ShopCategory.GATE_FX, "gfx_classic", "Klasik", "Temiz bir geçiş.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.GATE_FX, "gfx_spark", "Kıvılcım", "Kapı kıvılcımlarla açılır.", 700, Currency.GOLD, 1),
        it(ShopCategory.GATE_FX, "gfx_bubble", "Baloncuk", "Kapı baloncuklara dönüşür.", 900, Currency.GOLD, 2),
        it(ShopCategory.GATE_FX, "gfx_bolt", "Şimşek", "Hızlı ve güçlü.", 1300, Currency.GOLD, 3),
        it(ShopCategory.GATE_FX, "gfx_flower", "Çiçek", "Yapraklar savrulur.", 1100, Currency.GOLD, 4),
        it(ShopCategory.GATE_FX, "gfx_snow", "Kar", "Kar taneleri dökülür.", 1100, Currency.GOLD, 5),
        it(ShopCategory.GATE_FX, "gfx_notes", "Notalar", "Müzikli geçiş.", 25, Currency.DIAMOND, 6),
        it(ShopCategory.GATE_FX, "gfx_firework", "Havai Fişek", "Görkemli bir patlama.", 45, Currency.DIAMOND, 7, lvl = 18),
    )

    val trails = listOf(
        it(ShopCategory.TRAIL, "trl_none", "İzsiz", "Sade görünüm.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.TRAIL, "trl_dots", "Nokta İzi", "Arkanda noktalar bırak.", 400, Currency.GOLD, 1),
        it(ShopCategory.TRAIL, "trl_line", "Işık Çizgisi", "Akıcı bir çizgi.", 700, Currency.GOLD, 2),
        it(ShopCategory.TRAIL, "trl_stars", "Yıldız İzi", "Parlayan yıldızlar.", 1000, Currency.GOLD, 3),
        it(ShopCategory.TRAIL, "trl_hearts", "Kalp İzi", "Kalpler süzülür.", 1000, Currency.GOLD, 4),
        it(ShopCategory.TRAIL, "trl_rainbow", "Gökkuşağı İzi", "Dört renkli şerit.", 35, Currency.DIAMOND, 5),
        it(ShopCategory.TRAIL, "trl_bubbles", "Baloncuk İzi", "Su altı esintisi.", 900, Currency.GOLD, 6),
        it(ShopCategory.TRAIL, "trl_comet", "Kuyruklu Yıldız", "Geniş, parlak kuyruk.", 1600, Currency.GOLD, 7, lvl = 8),
        it(ShopCategory.TRAIL, "trl_pixel", "Piksel", "Retro kareler.", 800, Currency.GOLD, 8),
        it(ShopCategory.TRAIL, "trl_confetti", "Konfeti İzi", "Karnaval havası.", 30, Currency.DIAMOND, 9),
    )

    val animations = listOf(
        it(ShopCategory.ANIMATION, "anm_jump", "Zıplama", "Bölüm sonunda sevinçle zıpla.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.ANIMATION, "anm_spin", "Dönüş", "Kendi etrafında dön.", 600, Currency.GOLD, 1),
        it(ShopCategory.ANIMATION, "anm_dance", "Dans", "Sağa sola dans et.", 900, Currency.GOLD, 2),
        it(ShopCategory.ANIMATION, "anm_wave", "Dalga", "Dalga gibi esne.", 800, Currency.GOLD, 3),
        it(ShopCategory.ANIMATION, "anm_flip", "Takla", "Havada takla at.", 1400, Currency.GOLD, 4),
        it(ShopCategory.ANIMATION, "anm_pose", "Zafer Pozu", "Büyü ve parla.", 20, Currency.DIAMOND, 5),
        it(ShopCategory.ANIMATION, "anm_rain", "Konfeti Yağmuru", "Gökyüzünden konfeti.", 1800, Currency.GOLD, 6, lvl = 10),
        it(ShopCategory.ANIMATION, "anm_firework", "Havai Fişek Gösterisi", "Büyük kutlama.", 50, Currency.DIAMOND, 7),
    )

    val boosts = listOf(
        ShopItem("boost_shield", ShopCategory.BOOST, "Can Kalkanı", "Bir sonraki başarısız bölümde can kaybetmezsin.", 450, Currency.GOLD, consumable = true, style = 0),
        ShopItem("boost_endless_shield", ShopCategory.BOOST, "Sonsuz Kalkan", "Sonsuz modda bir hatayı affeder. Koşu başında kullanılır.", 300, Currency.GOLD, consumable = true, style = 1),
        ShopItem("boost_magnet", ShopCategory.BOOST, "Altın Çarpanı", "Bir sonraki koşuda kazanılan altın ×2.", 350, Currency.GOLD, consumable = true, style = 2),
    )

    val lifeItems = listOf(
        ShopItem("life_single", ShopCategory.LIFE, "Tek Can", "1 can hemen dolar.", 250, Currency.GOLD, consumable = true, style = 0),
        ShopItem("life_full", ShopCategory.LIFE, "Tam Can", "Tüm canlar hemen dolar.", 12, Currency.DIAMOND, consumable = true, style = 1),
        ShopItem("life_single_d", ShopCategory.LIFE, "Tek Can (Elmas)", "1 can hemen dolar.", 4, Currency.DIAMOND, consumable = true, style = 2),
    )

    val themes = listOf(
        it(ShopCategory.THEME, "thm_sky", "Gökyüzü", "Varsayılan aydınlık tema.", 0, Currency.GOLD, 0, free = true),
        it(ShopCategory.THEME, "thm_pink", "Pembe Rüya", "Pastel pembe menüler.", 800, Currency.GOLD, 1),
        it(ShopCategory.THEME, "thm_mint", "Nane", "Ferah yeşil tonlar.", 800, Currency.GOLD, 2),
        it(ShopCategory.THEME, "thm_sunset", "Gün Batımı", "Turuncu ve sarı.", 1200, Currency.GOLD, 3),
        it(ShopCategory.THEME, "thm_lavender", "Lavanta", "Sakin mor tonlar.", 20, Currency.DIAMOND, 4),
        it(ShopCategory.THEME, "thm_lemon", "Limonata", "Sarı ve canlı.", 1200, Currency.GOLD, 5, lvl = 6),
    )

    val collectibles = listOf(
        it(ShopCategory.COLLECTIBLE, "col_cup", "Renk Kupası", "Rafının yıldızı.", 1500, Currency.GOLD, 0),
        it(ShopCategory.COLLECTIBLE, "col_prism", "Prizma Taşı", "Işığı dört renge böler.", 25, Currency.DIAMOND, 1),
        it(ShopCategory.COLLECTIBLE, "col_globe", "Kar Küresi", "İçinde minik bir kapı var.", 1200, Currency.GOLD, 2),
        it(ShopCategory.COLLECTIBLE, "col_plant", "Saksı Çiçeği", "Dört renkli yapraklar.", 800, Currency.GOLD, 3),
        it(ShopCategory.COLLECTIBLE, "col_rocket", "Oyuncak Roket", "Uzay İstasyonu hatırası.", 1800, Currency.GOLD, 4),
        it(ShopCategory.COLLECTIBLE, "col_clock", "Masa Saati", "Saat Kulesi hatırası.", 1400, Currency.GOLD, 5),
        it(ShopCategory.COLLECTIBLE, "col_book", "Sihirli Kitap", "Kütüphaneden ödünç.", 30, Currency.DIAMOND, 6),
        it(ShopCategory.COLLECTIBLE, "col_balloon", "Balon Demeti", "Balon Dünyası'ndan.", 900, Currency.GOLD, 7),
        it(ShopCategory.COLLECTIBLE, "col_robot", "Mini Robot", "Robot Şehri'nden.", 2000, Currency.GOLD, 8),
        it(ShopCategory.COLLECTIBLE, "col_mask", "Karnaval Maskesi", "Renk Karnavalı hatırası.", 40, Currency.DIAMOND, 9),
    )

    val all: List<ShopItem> = characters + costumes + colorFx + gateFx + trails + animations + boosts + lifeItems + themes + collectibles
    private val byId = all.associateBy { it.id }

    fun item(id: String): ShopItem? = byId[id]
    fun inCategory(c: ShopCategory): List<ShopItem> = all.filter { it.category == c }

    /** Ekipman kategorisi (aynı anda biri seçili). */
    val equipCategories = listOf(ShopCategory.CHARACTER, ShopCategory.COSTUME, ShopCategory.COLOR_FX, ShopCategory.GATE_FX,
        ShopCategory.TRAIL, ShopCategory.ANIMATION, ShopCategory.THEME)

    val defaults: Map<ShopCategory, String> = equipCategories.associateWith { cat -> all.first { it.category == cat && it.free }.id }
}
