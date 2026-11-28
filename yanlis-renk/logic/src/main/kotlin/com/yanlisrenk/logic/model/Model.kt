package com.yanlisrenk.logic.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Oyunun dört temel rengi. Sıra: Kırmızı → Mavi → Yeşil → Sarı → Kırmızı. */
enum class GameColor(val trName: String, val trUpper: String, val shape: String, val rgb: Int) {
    RED("Kırmızı", "KIRMIZI", "circle", 0xFF5A5F),
    BLUE("Mavi", "MAVİ", "square", 0x3D8BFF),
    GREEN("Yeşil", "YEŞİL", "triangle", 0x3CC46B),
    YELLOW("Sarı", "SARI", "star", 0xFFC933);

    /** Seçili dildeki renk adı. */
    val tr: String get() = com.yanlisrenk.logic.i18n.L.tOr("color.$name", trName)
    /** Seçili dildeki büyük harfli renk adı (kapı yazıları). */
    val upper: String get() = com.yanlisrenk.logic.i18n.L.tOr("color.$name.upper", trUpper)

    companion object {
        const val COUNT = 4
        fun of(i: Int): GameColor = entries[norm(i)]
        fun norm(i: Int): Int = ((i % COUNT) + COUNT) % COUNT
    }
}

/** Renk döngüsü kuralları: yön (ayna) ve kilitli renkler. */
object ColorCycle {
    /** Bir dokunuşla gelinecek renk. Kilitli renkler atlanır. */
    fun step(color: Int, dir: Int, lockedMask: Int): Int {
        var c = color
        repeat(GameColor.COUNT) {
            c = GameColor.norm(c + dir)
            if (lockedMask and (1 shl c) == 0) return c
        }
        return color // hepsi kilitliyse (olmamalı) değişmez
    }

    fun stepN(color: Int, n: Int, dir: Int, lockedMask: Int): Int {
        var c = color
        repeat(n) { c = step(c, dir, lockedMask) }
        return c
    }

    /** from → to için gereken en az dokunuş; ulaşılamazsa -1. */
    fun tapsTo(from: Int, to: Int, dir: Int, lockedMask: Int): Int {
        if (from == to) return 0
        if (lockedMask and (1 shl to) != 0) return -1
        var c = from
        for (k in 1..GameColor.COUNT) {
            c = step(c, dir, lockedMask)
            if (c == to) return k
        }
        return -1
    }

    /** Kilit başlarken mevcut renk kilitliyse ilk serbest renge geçilir (standart yönde). */
    fun forceUnlocked(color: Int, lockedMask: Int): Int =
        if (lockedMask and (1 shl color) == 0) color else step(color, 1, lockedMask)
}

@Serializable
enum class RuleType {
    /** Aynı renk (=) */
    SAME,
    /** Farklı renk (≠) */
    DIFF,
    /** Listedeki renklerden biri (gökkuşağı / karışım) */
    ANY,
    /** Listedeki renklerin hiçbiri (yasak renkler) */
    NONE,
    /** Kapı renginin sıradaki rengi (→) */
    NEXT,
    /** Kapı renginin önceki rengi (←) */
    PREV,
    /** Bir önceki kapıdan geçtiğin renk (↺) */
    REPEAT,
    /** Sahte kapı: her renk geçer */
    FREE
}

@Serializable
enum class Motion { STATIC, ROTATE, SLIDE, TIMED }

/**
 * Bir kapı. [colors] anlamı kurala göre değişir:
 * SAME/DIFF/NEXT/PREV → colors[0] temel renk (hareketli kapıda döngü listesi);
 * ANY/NONE → renk kümesi.
 */
@Serializable
data class GateData(
    val y: Float,
    val rule: RuleType,
    val colors: List<Int> = emptyList(),
    val motion: Motion = Motion.STATIC,
    /** Hareketli kapılarda her rengin süresi (sn). */
    val segDur: Float = 0f,
    /** Hareketli kapılarda başlangıç fazı (segment cinsinden). */
    val phase: Float = 0f,
    /** >0 ise kapı bu mesafeye kadar görünmez (gizli kapı). */
    val revealDist: Float = 0f,
    /** >0 ise karakter bu mesafeye girince renk gizlenir (hafıza kapısı). */
    val memoryDist: Float = 0f,
    /** Yanıp sönen kapı. */
    val flicker: Boolean = false,
    /** Yanıltıcı kapı: yazının belirttiği renk (-1 yok). Boya rengi colors[0]. */
    val word: Int = -1,
    /** Yanıltıcı kapıda hangisine bakılacağı: 0 boya, 1 yazı. */
    val readBy: Int = 0,
    /** Renk yalnızca hafıza zinciri tabelasında gösterilir. */
    val chain: Boolean = false,
    /** İki renkli bölünmüş görünüm (renk karıştırma). */
    val split: Boolean = false,
) {
    /** t anındaki temel renk indeksi (hareketli kapılar için). */
    fun motionIndex(t: Float): Int {
        if (motion == Motion.STATIC || colors.size <= 1 || segDur <= 0f) return 0
        val f = t / segDur + phase
        return Math.floorMod(kotlin.math.floor(f).toInt(), colors.size)
    }

    /** t anında kapının gösterdiği temel renk (SAME/DIFF/NEXT/PREV). */
    fun baseColor(t: Float): Int = when {
        colors.isEmpty() -> -1
        motion != Motion.STATIC -> colors[motionIndex(t)]
        word >= 0 && readBy == 1 -> word
        else -> colors[0]
    }

    /** t anında geçerli renklerin bit maskesi. [prevGateColor] REPEAT için. */
    fun validMask(t: Float, prevGateColor: Int): Int = when (rule) {
        RuleType.SAME -> 1 shl baseColor(t)
        RuleType.DIFF -> 0xF and (1 shl baseColor(t)).inv()
        RuleType.ANY -> colors.fold(0) { m, c -> m or (1 shl c) }
        RuleType.NONE -> 0xF and colors.fold(0) { m, c -> m or (1 shl c) }.inv()
        RuleType.NEXT -> 1 shl GameColor.norm(baseColor(t) + 1)
        RuleType.PREV -> 1 shl GameColor.norm(baseColor(t) - 1)
        RuleType.REPEAT -> if (prevGateColor < 0) 0xF else 1 shl prevGateColor
        RuleType.FREE -> 0xF
    }

    fun accepts(color: Int, t: Float, prevGateColor: Int): Boolean =
        validMask(t, prevGateColor) and (1 shl color) != 0
}

@Serializable
enum class PickupType { COIN, DIAMOND, SHIELD }

/** Toplanabilir: renkli altın yalnızca aynı renkteyken toplanır (color = -1 → her renk). */
@Serializable
data class PickupData(val y: Float, val type: PickupType, val color: Int = -1)

@Serializable
enum class ZoneType { SPEED, MIRROR, LOCK, FOG }

/** Bölge: [y1, y2) aralığında etkili. SPEED → value çarpan, LOCK → colors kilitli, FOG → value görüş mesafesi. */
@Serializable
data class ZoneData(
    val y1: Float,
    val y2: Float,
    val type: ZoneType,
    val value: Float = 1f,
    val colors: List<Int> = emptyList(),
) {
    val lockMask: Int get() = colors.fold(0) { m, c -> m or (1 shl c) }
}

@Serializable
enum class PadType { SHIFT, SET }

/** Otomatik renk değiştirme pedi. SHIFT → value kadar ileri, SET → value rengine. */
@Serializable
data class PadData(val y: Float, val type: PadType, val value: Int)

/** Hafıza zinciri tabelası: sıradaki kapıların renklerini gösterir. */
@Serializable
data class SignData(val y: Float, val gates: List<Int>)

@Serializable
data class LevelData(
    val id: Int,
    val area: Int,
    /** Alan içindeki sıra 0..4 (4 = alan finali). */
    val index: Int,
    val isFinal: Boolean,
    val speed: Float,
    val length: Float,
    val difficulty: Int,
    val startColor: Int,
    /** Doğrulayıcıda varsayılan insan tepki süresi (sn). */
    val reaction: Float,
    val gates: List<GateData>,
    val pickups: List<PickupData> = emptyList(),
    val zones: List<ZoneData> = emptyList(),
    val pads: List<PadData> = emptyList(),
    val signs: List<SignData> = emptyList(),
    val mechanics: List<Mechanic> = emptyList(),
    /** ★2 için gereken altın. */
    val coinTarget: Int = 0,
    /** ★3 için izin verilen en fazla dokunuş. */
    val tapPar: Int = 0,
    /** Doğrulayıcının bulduğu bir çözüm: her kapıdan geçiş rengi. */
    val solution: List<Int> = emptyList(),
    val minTaps: Int = 0,
    val maxCoins: Int = 0,
    val fogDist: Float = 0f,
) {
    val goldCoins: Int get() = pickups.count { it.type == PickupType.COIN }
}

/** Tekrar kullanılabilir oynanış mekanikleri. */
@Serializable
enum class Mechanic(val titleTr: String, val hintTr: String) {
    SAME("Renk Eşleştirme", "= işaretli kapıdan AYNI renkte geç. Rengini değiştirmek için ekrana dokun."),
    DIFF("Ters Kapı", "Eşit değil işaretli kapıdan FARKLI bir renkte geç."),
    DOUBLE("Çift Kapı", "İki kapı art arda geliyor. İkisini de düşünerek hazırlan."),
    SPEED("Hız Yolu", "Ok işaretli hız bölgesinde karakter hızlanır."),
    MEMORY("Renk Hafızası", "Kapı rengi yaklaşınca gizlenir. Rengi aklında tut!"),
    LOCK("Kilitli Renk", "Kilit bölgesinde bazı renkler kullanılamaz; dokunuş onları atlar."),
    ROTATE("Dönen Kapı", "Kapının renkleri kayar. Ulaştığın anda ortada hangi renk olacak?"),
    RAINBOW("Gökkuşağı Kapısı", "Birden çok renk gösteren kapıda bu renklerden herhangi biri geçer."),
    TIMED("Zaman Kapısı", "Kapının rengi belirli sürelerle değişir. Sayaç halkasını izle."),
    MAZE("Renk Labirenti", "Farklı kapılar karışık ve sık geliyor. Sakin kal!"),
    MIRROR("Ayna Dünyası", "Ayna bölgesinde renk sırası tersine döner: Sarı > Yeşil > Mavi > Kırmızı."),
    HIDDEN("Gizli Kapı", "Bazı kapılar ancak yaklaşınca görünür."),
    STROOP("Yanıltıcı Renk", "Kapıdaki göz simgesi neye bakacağını söyler: fırça = BOYA rengi, kitap = YAZI."),
    TRIPLE("Üçlü Kapı", "Üç kapı peş peşe! Hızlı düşün."),
    AUTOSHIFT("Renk Değişim Pedi", "Pedlerin üzerinden geçince rengin kendiliğinden değişir."),
    SLIDE("Hareketli Yol", "İki renkli kapı sağa sola kayar. Ortadaki renge göre geç."),
    CHAIN("Hafıza Zinciri", "Tabela sıradaki kapıların renklerini gösterir; kapılar gri gelir."),
    MIX("Renk Karıştırma", "İki renkli kapıdan bu iki renkten biriyle geçebilirsin."),
    QUICK("Hızlı Karar", "Kapılar daha sık geliyor. Tepki süren kısalıyor!"),
    EXAM("Büyük Sınav", "Öğrendiğin her şey bir arada."),
    NONE("Yasak Renkler", "Üstü çizili renklerin hiçbiriyle geçme."),
    NEXT("Sıradaki Renk", "İleri ok kapısı: gösterilen rengin SIRADAKİ rengiyle geç (Kırmızı > Mavi > Yeşil > Sarı)."),
    PREV("Önceki Renk", "Geri ok kapısı: gösterilen rengin ÖNCEKİ rengiyle geç."),
    REPEAT("Yankı Kapısı", "Dönen ok kapısı: bir önceki kapıdan hangi renkle geçtiysen yine o renkle geç."),
    DECOY("Sahte Kapı", "Kesik çizgili sahte kapılar seni etkilemez; her renkle geçilir."),
    FLICKER("Yanıp Sönen Kapı", "Bu kapılar yanıp söner. Göründükleri anı yakala."),
    FOG("Sis", "Sis bölgesinde uzağı göremezsin."),
    COINS("Renkli Altınlar", "Renkli altınları yalnızca aynı renkteyken toplarsın."),
    WAVE("Dalga Hızı", "Hız sürekli artıp azalıyor. Ritmi yakala.");

    val title: String get() = com.yanlisrenk.logic.i18n.L.tOr("mech.$name.title", titleTr)
    val hint: String get() = com.yanlisrenk.logic.i18n.L.tOr("mech.$name.hint", hintTr)
}
