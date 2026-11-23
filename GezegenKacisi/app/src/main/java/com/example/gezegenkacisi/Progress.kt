package com.example.gezegenkacisi

import kotlin.math.min
import kotlin.random.Random

/**
 * Bölüm özellikleri. Her bölüm haritanın kendi kuralına ek olarak bunlardan birkaçını alır.
 * [mech] doluysa o mekaniği oyuna ekler.
 */
enum class Mod(val nameRes: Int, val color: Int, val mech: Mech? = null) {
    FAST(R.string.mod_fast, 0xFFFF8A4B.toInt()),
    NARROW(R.string.mod_narrow, 0xFFFF5B5B.toInt()),
    DENSE(R.string.mod_dense, 0xFFFFB347.toInt()),
    HEAVY(R.string.mod_heavy, 0xFFB08AFF.toInt()),
    LIGHT(R.string.mod_light, 0xFF8CE6FF.toInt()),
    GOLD(R.string.mod_gold, 0xFFFFD66B.toInt()),
    WIND(R.string.mod_wind, 0xFFD05BFF.toInt(), Mech.WIND),
    FIRE(R.string.mod_fire, 0xFFFF5A1F.toInt(), Mech.FIRE),
    METEOR(R.string.mod_meteor, 0xFFFF8A4B.toInt(), Mech.METEOR),
    SWAY(R.string.mod_sway, 0xFFC8A060.toInt(), Mech.SWAY),
    PULL(R.string.mod_pull, 0xFFFF9A3B.toInt(), Mech.PULL),
    LIGHTNING(R.string.mod_lightning, 0xFF8FB8FF.toInt(), Mech.LIGHTNING)
}

/** Bir bölümün bütün bilgileri. Bölüm numarasından her zaman aynı şekilde üretilir. */
class LevelInfo(
    val number: Int,          // 1..500
    val mapIndex: Int,        // 0..9
    val round: Int,           // 0..9 (her tur 10 haritanın hepsinden geçer)
    val stepInMap: Int,       // 0..4 (haritanın kaçıncı bölümü)
    val target: Int,          // geçmek için gereken puan
    val mods: List<Mod>,
    val params: DiffParams,
    val reward: Int           // ilk kez geçince verilen para
) {
    val isFinalOfMap get() = stepInMap == 4
    val mechs: Set<Mech> get() = mods.mapNotNull { it.mech }.toSet()
    fun has(m: Mod) = m in mods
}

object Levels {
    const val COUNT = 500
    const val PER_MAP = 5
    const val MAPS = 10
    const val FIRST_TARGET = 15
    const val TARGET_STEP = 2     // her 5'li harita grubunda hedef bu kadar artar
    const val TARGET_MAX = 120
    const val MAX_SPEED = 400f    // bölümlerde ulaşılabilecek en yüksek kaydırma hızı

    private val easy = Difficulty.EASY.params
    private val hard = Difficulty.HARD.params

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun info(number: Int): LevelInfo {
        val n = number.coerceIn(1, COUNT)
        val block = (n - 1) / PER_MAP          // 0..99: kaçıncı 5'li grup
        val step = (n - 1) % PER_MAP           // grubun içindeki sıra
        val mapIndex = block % MAPS
        val round = block / MAPS
        val target = min(TARGET_MAX, FIRST_TARGET + block * TARGET_STEP)

        // Zorluk: ilk bölümlerde Kolay, yaklaşık 250. bölümde Zor seviyesine ulaşır, sonra daha da artar
        val t = (block / 50f).coerceAtMost(1f) * 0.85f + step * 0.03f
        val over = ((block - 50) / 50f).coerceIn(0f, 1f) // son 250 bölümde ekstra
        var p = DiffParams(
            startGap = lerp(easy.startGap, hard.startGap, t) - over * 14f,
            minGap = lerp(easy.minGap, hard.minGap, t) - over * 10f,
            gapShrink = lerp(easy.gapShrink, hard.gapShrink, t),
            baseSpeed = lerp(easy.baseSpeed, hard.baseSpeed, t) + over * 18f,
            speedPerScore = lerp(easy.speedPerScore, hard.speedPerScore, t),
            speedCapScore = 30 + round * 3,
            spacing = lerp(easy.spacing, hard.spacing, t) - over * 12f,
            gapJump = lerp(easy.gapJump, hard.gapJump, t),
            swayAmp = lerp(easy.swayAmp, hard.swayAmp, t) + over * 10f,
            hazard = lerp(0.6f, 1.35f, t) + over * 0.25f
        )

        // Özellikler: ilk bölüm alıştırmadır; sonra 1, ileriki turlarda 2-3 özellik
        val rng = Random(n * 7919L + 17)
        val count = when {
            n == 1 -> 0
            else -> 1 + (if (round >= 2) 1 else 0) + (if (step == 4) 1 else 0) + (if (round >= 6 && step >= 2) 1 else 0)
        }.coerceAtMost(3)
        val native = Maps.ALL[mapIndex].mechs
        val pool = Mod.entries.filter { m ->
            (m.mech == null || m.mech !in native) &&
                // İlk turda tehlike eklemeyelim, hafif özellikler yeter
                !(round == 0 && m.mech != null && step < 3) &&
                // Ağır ve hafif yer çekimi aynı anda olmasın
                true
        }.shuffled(rng)
        val mods = ArrayList<Mod>()
        for (m in pool) {
            if (mods.size >= count) break
            if (m == Mod.HEAVY && Mod.LIGHT in mods) continue
            if (m == Mod.LIGHT && Mod.HEAVY in mods) continue
            mods.add(m)
        }

        if (Mod.NARROW in mods) p = p.copy(startGap = p.startGap * 0.88f, minGap = p.minGap * 0.9f)
        if (Mod.DENSE in mods) p = p.copy(spacing = p.spacing * 0.87f)
        if (Mod.FAST in mods) p = p.copy(baseSpeed = p.baseSpeed * 1.15f)

        // --- Oynanabilirlik sınırları (500 bölüm, geleceği bilen bir pilotla simüle edilerek ayarlandı) ---
        // Boşluk hiçbir zaman roketin geçemeyeceği kadar daralmasın
        p = p.copy(minGap = p.minGap.coerceAtLeast(185f), startGap = p.startGap.coerceAtLeast(210f))
        // Hız: harita çarpanı ve "Hızlı" dahil en fazla MAX_SPEED (daha hızlısında kuleler arası
        // dikey farkı dokunarak kapatmak fiziksel olarak imkânsızlaşıyordu)
        val maxBase = MAX_SPEED / Maps.ALL[mapIndex].speedMul
        if (p.baseSpeed > maxBase * 0.72f) p = p.copy(baseSpeed = maxBase * 0.72f)
        val maxPerScore = ((maxBase - p.baseSpeed) / p.speedCapScore).coerceAtLeast(0f)
        if (p.speedPerScore > maxPerScore) p = p.copy(speedPerScore = maxPerScore)
        // Salınım: dar geçitte büyük salınım geçilemez anlar yaratıyordu
        p = p.copy(swayAmp = min(p.swayAmp, if (Mod.NARROW in mods) 45f else 60f))

        val reward = 10 + block * 2 + (if (step == 4) 15 else 0)
        return LevelInfo(n, mapIndex, round, step, target, mods, p, reward)
    }
}

/** Marketteki uçan araçlar. [shape] çizim biçimini, renkler görünümü belirler. */
enum class SkinShape { ROCKET, SHUTTLE, UFO, PLANET, COMET, CRYSTAL }

enum class Skin(
    val nameRes: Int,
    val price: Int,
    val shape: SkinShape,
    val body: Int,      // ana renk
    val accent: Int,    // vurgu rengi
    val trim: Int       // kanatçık / halka / ikinci renk
) {
    CLASSIC(R.string.skin_classic, 0, SkinShape.ROCKET, 0xFFE8ECFA.toInt(), 0xFFFF5B4A.toInt(), 0xFFD8402F.toInt()),
    NIGHT(R.string.skin_night, 120, SkinShape.ROCKET, 0xFF2A3A7A.toInt(), 0xFF5FE6FF.toInt(), 0xFF1A2558.toInt()),
    EMERALD(R.string.skin_emerald, 180, SkinShape.ROCKET, 0xFFD8FFE8.toInt(), 0xFF2FBF6A.toInt(), 0xFF1F8A4A.toInt()),
    SHUTTLE(R.string.skin_shuttle, 250, SkinShape.SHUTTLE, 0xFFF4F4F4.toInt(), 0xFF3B3B48.toInt(), 0xFFFF8A2B.toInt()),
    COMET(R.string.skin_comet, 320, SkinShape.COMET, 0xFFBFF4FF.toInt(), 0xFF5FB8FF.toInt(), 0xFFFFFFFF.toInt()),
    UFO(R.string.skin_ufo, 400, SkinShape.UFO, 0xFFB8C0D8.toInt(), 0xFF7BFF9B.toInt(), 0xFF5B6488.toInt()),
    GOLD(R.string.skin_gold, 550, SkinShape.ROCKET, 0xFFFFE08A.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8901F.toInt()),
    RING_PLANET(R.string.skin_ring_planet, 700, SkinShape.PLANET, 0xFFD9A066.toInt(), 0xFFF6DDB4.toInt(), 0xFFFFE6C2.toInt()),
    LAVA_PLANET(R.string.skin_lava_planet, 900, SkinShape.PLANET, 0xFF5A1A10.toInt(), 0xFFFF6A1A.toInt(), 0x00000000),
    CRYSTAL(R.string.skin_crystal, 1200, SkinShape.CRYSTAL, 0xFF9CF0FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF5B7BFF.toInt())
}
