package com.emirzinciri.robotkomutan.logic.level

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Zemin türleri. Harita dizgesindeki karakterle eşlenir.
 * Nesneler (kutu, enerji hücresi) ayrıca tutulur; 'B', 'X', 'c' karakterleri kolaylık içindir.
 */
enum class Tile(val char: Char, val title: String) {
    FLOOR('.', "Zemin"),
    WALL('#', "Duvar"),
    VOID(' ', "Boşluk"),
    PIT('o', "Çukur"),
    EXIT('E', "Çıkış"),
    TARGET('x', "Kutu hedefi"),
    PLATE('p', "Basınç plakası"),
    SWITCH('s', "Anahtar paneli"),
    TIMER('t', "Zamanlı düğme"),
    DOOR('D', "Kapı"),
    DOOR_INV('d', "Ters kapı"),
    CHARGER('Q', "Şarj istasyonu"),
    SOCKET('K', "Enerji soketi"),
    CONV_N('^', "Bant"),
    CONV_E('>', "Bant"),
    CONV_S('v', "Bant"),
    CONV_W('<', "Bant"),
    LASER('L', "Lazer yayıcı");

    /** Bantların taşıma yönü. */
    val conveyorDir: Dir? get() = when (this) { CONV_N -> Dir.N; CONV_E -> Dir.E; CONV_S -> Dir.S; CONV_W -> Dir.W; else -> null }

    /** Robotun üzerinde durabileceği zemin (kapılar ayrıca duruma göre değerlendirilir). */
    val isFloorLike: Boolean get() = this == FLOOR || this == EXIT || this == TARGET || this == PLATE || this == CHARGER || conveyorDir != null

    /** Robotun önünde durup kullandığı, üzerine çıkılamayan paneller. */
    val isConsole: Boolean get() = this == SWITCH || this == TIMER || this == SOCKET || this == LASER

    val isDoor: Boolean get() = this == DOOR || this == DOOR_INV

    companion object {
        private val byChar = entries.associateBy { it.char }
        fun of(c: Char): Tile? = byChar[c]
    }
}

/** Robot sınıfları. Bulmaca bölümlerinde standart özellikler kullanılır (kalıcı geliştirmeler bölüm kuralını değiştirmez). */
enum class RobotClass(val title: String, val role: String) {
    BASIC("Temel Robot", "Genel görevler"),
    CARRIER("Taşıyıcı Robot", "Ağır nesneler"),
    SPEEDY("Hız Robotu", "Hızlı hareket"),
    ENGINEER("Mühendis Robot", "Onarım ve inşa"),
    WARRIOR("Savaş Robotu", "Saldırı"),
    SHIELD("Kalkan Robotu", "Savunma"),
    ENERGY("Enerji Robotu", "Enerji aktarımı"),
    FLYER("Uçan Robot", "Engelleri aşma"),
    DIGGER("Kazıcı Robot", "Tünel açma"),
    MAGNET("Manyetik Robot", "Metal çekme"),
    LASER("Lazer Robot", "Menzilli saldırı"),
    GHOST("Görünmez Robot", "Gizli ilerleme"),
    TIME("Zaman Robotu", "Zaman etkileri"),
    COPY("Kopyacı Robot", "Komut çoğaltma"),
    COMMANDER("Komutan Robot", "Robot koordinasyonu"),
}

@Serializable
data class RobotDef(
    val id: String,
    val x: Int,
    val y: Int,
    val dir: Dir = Dir.E,
    val type: RobotClass = RobotClass.BASIC,
    val energy: Int = 10,
    val maxEnergy: Int = 10,
)

enum class ObjectiveType(val title: String) {
    REACH_EXIT("Çıkışa ulaş"),
    BOXES_ON_TARGETS("Kutuları hedeflere taşı"),
    COLLECT_ALL_ENERGY("Bütün enerjiyi topla"),
    POWER_SOCKETS("Enerji soketlerini doldur"),
    SYNC_EXIT("Robotlar çıkışlara aynı anda ulaşsın"),
}

@Serializable
data class ObjectiveDef(
    val type: ObjectiveType,
    /** REACH_EXIT için belirli robotlar; boşsa bütün robotlar. */
    val robots: List<String> = emptyList(),
)

enum class StarRuleType(val title: String) {
    TICKS("adımda bitir"),
    ENERGY_USED("enerjiden az harca"),
    NO_BUMPS("hiç çarpmadan bitir"),
    COMMANDS("kartla bitir"),
}

@Serializable
data class StarRule(val type: StarRuleType, val value: Int = 0)

/** Bölüm dosyasının (JSON) birebir karşılığı. */
@Serializable
data class LevelDef(
    val id: Int,
    val area: Int,
    val name: String,
    val goalText: String? = null,
    val hint: String? = null,
    val story: String? = null,
    val map: List<String>,
    val robots: List<RobotDef>,
    val commands: List<CommandType>,
    val maxCommands: Int,
    val par: Int,
    val third: StarRule? = null,
    val objectives: List<ObjectiveDef> = listOf(ObjectiveDef(ObjectiveType.REACH_EXIT)),
    val energy: Boolean = false,
    /** "x,y" -> kanal. Plakalar, anahtarlar, soketler ve kapılar aynı kanalda birbirine bağlanır. Varsayılan kanal 1. */
    val channels: Map<String, Int> = emptyMap(),
    /** "x,y" -> değer: enerji hücresinin miktarı, zamanlı düğmenin süresi, soketin ihtiyacı. */
    val values: Map<String, Int> = emptyMap(),
    val boxes: List<String> = emptyList(),
    /** Bu kanallardaki plakaların HEPSİ aynı anda basılı olmalı (koordinasyon kilitleri). */
    val andChannels: List<Int> = emptyList(),
    val cells: List<String> = emptyList(),
    val maxTicks: Int = 200,
    val tutorial: String? = null,
    val difficulty: Int = 1,
    /** Doğrulama sisteminin çalıştırdığı referans çözüm (robot kimliği -> kartlar). */
    val solution: Map<String, List<String>> = emptyMap(),
) {
    val width: Int get() = map.maxOf { it.length }
    val height: Int get() = map.size
}

object LevelParser {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }

    fun parse(text: String): LevelDef = json.decodeFromString(LevelDef.serializer(), text)

    fun encode(def: LevelDef): String = json.encodeToString(LevelDef.serializer(), def)
}

/** Ayrıştırılmış, değişmez bölüm verisi. Simülasyon bunu okur, değiştirmez. */
class LevelData(val def: LevelDef) {
    val width = def.width
    val height = def.height
    private val tiles: Array<Tile> = Array(width * height) { Tile.VOID }
    private val channelOf = IntArray(width * height) { 1 }
    private val valueOf = IntArray(width * height) { -1 }
    val initialBoxes: Set<Pos>
    val initialCells: Map<Pos, Int>

    init {
        val boxes = LinkedHashSet<Pos>()
        val cells = LinkedHashMap<Pos, Int>()
        def.map.forEachIndexed { y, row ->
            for (x in 0 until width) {
                val c = row.getOrElse(x) { ' ' }
                val tile = when (c) {
                    'B' -> { boxes += Pos(x, y); Tile.FLOOR }
                    'X' -> { boxes += Pos(x, y); Tile.TARGET }
                    'c' -> { cells[Pos(x, y)] = -1; Tile.FLOOR }
                    else -> Tile.of(c) ?: error("Bölüm ${def.id}: bilinmeyen harita karakteri '$c' ($x,$y)")
                }
                tiles[y * width + x] = tile
            }
        }
        def.boxes.forEach { boxes += Pos.parse(it) }
        def.cells.forEach { cells[Pos.parse(it)] = -1 }
        def.channels.forEach { (k, v) -> val p = Pos.parse(k); require(inBounds(p)); channelOf[idx(p)] = v }
        def.values.forEach { (k, v) -> val p = Pos.parse(k); require(inBounds(p)); valueOf[idx(p)] = v }
        initialBoxes = boxes
        initialCells = cells.mapValues { (p, _) -> valueOf[idx(p)].takeIf { it > 0 } ?: DEFAULT_CELL_VALUE }
    }

    fun inBounds(p: Pos) = p.x in 0 until width && p.y in 0 until height
    fun idx(p: Pos) = p.y * width + p.x
    fun tile(p: Pos): Tile = if (inBounds(p)) tiles[idx(p)] else Tile.VOID
    fun channel(p: Pos): Int = channelOf[idx(p)]

    /** Zamanlı düğmenin açık kalma süresi (adım). */
    fun timerDuration(p: Pos): Int = valueOf[idx(p)].takeIf { it > 0 } ?: DEFAULT_TIMER
    /** Lazer yayıcının yönü (values: 0=K,1=D,2=G,3=B; varsayılan doğu). */
    fun laserDir(p: Pos): Dir = Dir.entries[(valueOf[idx(p)].takeIf { it in 0..3 } ?: 1)]

    /** Soketin dolması için gereken enerji. */
    fun socketNeed(p: Pos): Int = valueOf[idx(p)].takeIf { it > 0 } ?: DEFAULT_SOCKET_NEED

    val plates: List<Pos> by lazy { positionsOf(Tile.PLATE) }
    val sockets: List<Pos> by lazy { positionsOf(Tile.SOCKET) }
    val lasers: List<Pos> by lazy { positionsOf(Tile.LASER) }
    val doors: List<Pos> by lazy { allPositions.filter { tile(it).isDoor } }
    val exits: List<Pos> by lazy { positionsOf(Tile.EXIT) }
    val targets: List<Pos> by lazy { positionsOf(Tile.TARGET) }
    val hasConveyors: Boolean by lazy { allPositions.any { tile(it).conveyorDir != null } }
    val andChannels: Set<Int> = def.andChannels.toSet()

    val allPositions: List<Pos> by lazy { (0 until height).flatMap { y -> (0 until width).map { x -> Pos(x, y) } } }
    fun positionsOf(t: Tile): List<Pos> = allPositions.filter { tile(it) == t }

    companion object {
        const val DEFAULT_CELL_VALUE = 5
        const val DEFAULT_TIMER = 4
        const val DEFAULT_SOCKET_NEED = 3
    }
}
