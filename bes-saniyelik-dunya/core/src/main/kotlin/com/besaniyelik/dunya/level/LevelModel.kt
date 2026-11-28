package com.besaniyelik.dunya.level

import com.besaniyelik.dunya.sim.Mechanic
import kotlinx.serialization.Serializable

@Serializable
enum class LevelKind { TUTORIAL, NORMAL, CHALLENGE, FINAL, GRAND_FINAL }

@Serializable
enum class SolidType { GROUND, CEILING, BLOCK, GHOST, SHIFT, MOVE, LOW }

@Serializable
enum class HazardType { SPIKE, SAW }

@Serializable
enum class PadType { SPRING, BOOST }

@Serializable
enum class PowerupType(val trTitle: String, val trDesc: String, val icon: String, val duration: Float) {
    SHIELD("Kalkan", "Bir kez hasarı engeller.", "p_shield", 0f),
    MAGNET("Mıknatıs", "Yakındaki altınları çeker.", "p_magnet", 12f),
    DOUBLE_JUMP("Çift Zıplama", "Havada bir kez daha zıplayabilirsin.", "p_double", 12f),
    INVINCIBLE("Dokunulmazlık", "Kısa süre engellerden etkilenmezsin.", "p_invincible", 5f),
    EXTRA_LIFE("Ekstra Kalp", "Bir kalp daha kazanırsın.", "p_life", 0f),
    COIN_MULTIPLIER("Altın Çarpanı", "Altınlar iki kat sayılır.", "p_coinx2", 15f),
    STAR_FINDER("Yıldız Algılayıcı", "Yıldızların ve gizli parçaların yerini gösterir.", "p_finder", 20f),
    CHECKPOINT_GUARD("Kontrol Noktası Koruması", "Bir sonraki ölümde kalp kaybetmezsin.", "p_guard", 0f);

    val title: String get() = com.besaniyelik.dunya.i18n.L.t("power.$name.title")
    val desc: String get() = com.besaniyelik.dunya.i18n.L.t("power.$name.desc")
}

@Serializable
data class Vec(val x: Float, val y: Float)

/**
 * Katı platform. MOVE tipi her zaman (ax, ay) genliğiyle salınır; SHIFT tipi yalnızca
 * "Değişen Platformlar" kuralı etkinken ay genliğiyle kayar. GHOST tipi "Hayalet Dünya"da yanıp söner.
 * [group] hayalet platformların hangi ritimle kaybolacağını belirler.
 */
@Serializable
data class SolidDef(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val type: SolidType = SolidType.GROUND,
    val ax: Float = 0f, val ay: Float = 0f, val period: Float = 0f, val phase: Float = 0f,
    val group: Int = 0,
)

/** SPIKE: (x,y) tabanın sol ucu, dir=+1 yukarı bakar, -1 aşağı bakar. SAW: (x,y) merkez, w yarıçap. */
@Serializable
data class HazardDef(
    val type: HazardType, val x: Float, val y: Float, val w: Float, val dir: Int = 1,
    val ax: Float = 0f, val ay: Float = 0f, val period: Float = 0f, val phase: Float = 0f,
    /**
     * true ise salınım küresel zamana değil oyuncunun engele uzaklığına kilitlidir: oyuncu ne zaman gelirse gelsin
     * engel aynı konumda olur (aksi halde hareketli testere bazı varış anlarında geçilemez oluyordu).
     */
    val sync: Boolean = false,
)

@Serializable
data class PadDef(val type: PadType, val x: Float, val y: Float, val w: Float, val dir: Int = 1)

@Serializable
data class PowerupDef(val x: Float, val y: Float, val type: PowerupType)

/** Kural aşaması: oyuncu [x] konumunu geçtiğinde [rules] etkin olur (nominal olarak 5 saniyelik mesafe). */
@Serializable
data class PhaseDef(val x: Float, val rules: List<Mechanic>)

/** Bölüm içi ipucu tabelası. [waitTap] true ise oyun yavaşlar ve oyuncunun dokunması beklenir (etkileşimli eğitim). */
@Serializable
data class HintDef(val x: Float, val text: String, val waitTap: Boolean = false, val icon: String = "")

/** Bölümdeki bir engel deseninin başladığı yer (analiz, rapor ve bölüm şeridi için). */
@Serializable
data class FeatureMark(val p: Pattern, val x: Float)

@Serializable
data class LevelData(
    val id: Int,
    val map: Int,
    val indexInMap: Int,
    val name: String,
    val kind: LevelKind,
    val difficulty: Int,
    val idea: String,
    val start: Vec,
    val goalX: Float,
    val solids: List<SolidDef>,
    val hazards: List<HazardDef> = emptyList(),
    val pads: List<PadDef> = emptyList(),
    val coins: List<Vec> = emptyList(),
    val stars: List<Vec> = emptyList(),
    val piece: Vec? = null,
    val powerups: List<PowerupDef> = emptyList(),
    val checkpoints: List<Float> = emptyList(),
    val phases: List<PhaseDef>,
    val mechanics: List<Mechanic> = emptyList(),
    val tutorial: Mechanic? = null,
    val hints: List<HintDef> = emptyList(),
    val chaser: Boolean = false,
    /** 2 ve 3 yıldız için gereken altın oranı. */
    val coinTarget: Int = 0,
    /** Çözücünün bulduğu çözüm: giriş durumunun değiştiği tick'ler (doğrulama kanıtı). */
    val solution: List<Int> = emptyList(),
    val solveTicks: Int = 0,
    val seed: Long = 0,
    val features: List<FeatureMark> = emptyList(),
)

@Serializable
data class MapPack(val map: Int, val levels: List<LevelData>)
