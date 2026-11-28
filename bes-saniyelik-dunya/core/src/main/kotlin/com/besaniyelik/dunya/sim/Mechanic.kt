package com.besaniyelik.dunya.sim

/** Aynı fiziksel parametreyi değiştiren kurallar aynı gruptadır; bir grupta aynı anda yalnızca bir kural etkin olabilir. */
enum class RuleGroup { NONE, GRAVITY, TIME, SURFACE, PLATFORM, JUMP, SIZE, CONTROL }

/** On temel dünya mekaniği. [unlockLevel] mekaniğin bölümlü modda ilk öğretildiği bölümdür. */
enum class Mechanic(
    /** Türkçe kaynak metin (bölüm üretici bunu kullanır); ekranda [title] gösterilir. */
    val trTitle: String,
    val trHint: String,
    val unlockLevel: Int,
    val group: RuleGroup,
    /** Çakışma durumunda yüksek öncelikli kural kazanır. */
    val priority: Int,
    val icon: String,
) {
    NORMAL("Normal Dünya", "Koş, zıpla ve engelleri aş!", 1, RuleGroup.NONE, 0, "m_normal"),
    REVERSE_GRAVITY("Ters Yer Çekimi", "Yer çekimi ters döndü! Tavanda koşuyorsun.", 11, RuleGroup.GRAVITY, 9, "m_gravity"),
    FAST("Hızlı Dünya", "Her şey hızlandı! Erken zıpla.", 21, RuleGroup.TIME, 6, "m_fast"),
    ICE("Buz Dünya", "Zemin kaygan! Hızın yavaşça artar.", 31, RuleGroup.SURFACE, 4, "m_ice"),
    GHOST("Hayalet Dünya", "Mor platformlar yanıp söner ve kaybolur!", 41, RuleGroup.PLATFORM, 5, "m_ghost"),
    BOUNCY("Zıplayan Dünya", "Çok daha yükseğe zıplıyorsun!", 51, RuleGroup.JUMP, 3, "m_bouncy"),
    SMALL("Küçük Dünya", "Küçüldün! Dar geçitlerden geçebilirsin.", 61, RuleGroup.SIZE, 7, "m_small"),
    REVERSE_CONTROL("Ters Kontrol", "Karakter kendi zıplar. Basılı tut, yerde kal!", 71, RuleGroup.CONTROL, 8, "m_control"),
    SLOW("Yavaş Zaman", "Zaman yavaşladı. Sakin ol ve zamanla.", 81, RuleGroup.TIME, 7, "m_slow"),
    SHIFTING("Değişen Platformlar", "Oklu platformlar yukarı aşağı kayar!", 91, RuleGroup.PLATFORM, 5, "m_shift");

    /** Oyuncunun dilindeki ad, kısa açıklama ve öğretici ayrıntısı. */
    val title: String get() = com.besaniyelik.dunya.i18n.L.t("mech.$name.title")
    val hint: String get() = com.besaniyelik.dunya.i18n.L.t("mech.$name.hint")
    val detail: String get() = com.besaniyelik.dunya.i18n.L.t("mech.$name.detail")
    /** Kural seçim kartlarında tek kelimelik kısa ad. */
    val short: String get() = com.besaniyelik.dunya.i18n.L.t("mech.$name.short")

    companion object {
        val learnOrder: List<Mechanic> = entries.sortedBy { it.unlockLevel }
        fun unlockedAt(levelId: Int): List<Mechanic> = entries.filter { it.unlockLevel <= levelId }
    }
}

/** Kural kombinasyonunun fiziksel etkileri. Tek bir yerde hesaplanır, böylece çakışma imkânsızdır. */
data class RuleEffects(
    val timeScale: Float,
    val gravitySign: Int,
    val jumpMul: Float,
    val sizeScale: Float,
    val ice: Boolean,
    val reverseControl: Boolean,
    val ghost: Boolean,
    val shifting: Boolean,
) {
    companion object {
        val DEFAULT = RuleEffects(1f, 1, 1f, 1f, false, false, false, false)

        fun of(rules: Collection<Mechanic>): RuleEffects {
            val r = RuleCompatibility.resolve(rules)
            return RuleEffects(
                timeScale = when {
                    Mechanic.SLOW in r -> Physics.SLOW_SCALE
                    Mechanic.FAST in r -> Physics.FAST_SCALE
                    else -> 1f
                },
                gravitySign = if (Mechanic.REVERSE_GRAVITY in r) -1 else 1,
                jumpMul = (if (Mechanic.BOUNCY in r) Physics.BOUNCY_MUL else 1f) * (if (Mechanic.SMALL in r) Physics.SMALL_JUMP_MUL else 1f),
                sizeScale = if (Mechanic.SMALL in r) Physics.SMALL_SCALE else 1f,
                ice = Mechanic.ICE in r,
                reverseControl = Mechanic.REVERSE_CONTROL in r,
                ghost = Mechanic.GHOST in r,
                shifting = Mechanic.SHIFTING in r,
            )
        }
    }
}

enum class Compat { OK, HARD, NO }

/**
 * Uyumluluk matrisi ve öncelik sistemi.
 *  - NO: aynı anda uygulanamaz (aynı parametreyi farklı değerlerle değiştirir ya da anlamsızdır).
 *  - HARD: birlikte uygulanabilir ama yalnızca yüksek zorlukta (zorluk >= 7) kullanılır.
 */
object RuleCompatibility {
    private val table = HashMap<Pair<Mechanic, Mechanic>, Compat>()

    private fun set(a: Mechanic, b: Mechanic, c: Compat) {
        table[a to b] = c; table[b to a] = c
    }

    init {
        val all = Mechanic.entries
        for (a in all) for (b in all) if (a != b) {
            table[a to b] = when {
                a == Mechanic.NORMAL || b == Mechanic.NORMAL -> Compat.NO // Normal yalnızca tek başına kullanılır
                a.group == b.group -> Compat.NO
                else -> Compat.OK
            }
        }
        set(Mechanic.REVERSE_GRAVITY, Mechanic.REVERSE_CONTROL, Compat.HARD)
        set(Mechanic.REVERSE_CONTROL, Mechanic.GHOST, Compat.HARD)
        set(Mechanic.REVERSE_CONTROL, Mechanic.SHIFTING, Compat.HARD)
        set(Mechanic.FAST, Mechanic.REVERSE_CONTROL, Compat.HARD)
        set(Mechanic.SMALL, Mechanic.BOUNCY, Compat.HARD)
        set(Mechanic.ICE, Mechanic.REVERSE_CONTROL, Compat.HARD)
    }

    fun compat(a: Mechanic, b: Mechanic): Compat = if (a == b) Compat.OK else table[a to b] ?: Compat.NO

    fun isValid(rules: Collection<Mechanic>, difficulty: Int = 10): Boolean {
        val list = rules.distinct()
        if (list.isEmpty()) return false
        if (Mechanic.NORMAL in list && list.size > 1) return false
        for (i in list.indices) for (j in i + 1 until list.size) {
            when (compat(list[i], list[j])) {
                Compat.NO -> return false
                Compat.HARD -> if (difficulty < 7) return false
                Compat.OK -> {}
            }
        }
        return true
    }

    /** Çakışan kurallar arasında yüksek öncelikli olanı tutar. Sonuç her zaman geçerlidir. */
    fun resolve(rules: Collection<Mechanic>): List<Mechanic> {
        val sorted = rules.distinct().sortedByDescending { it.priority }
        val out = ArrayList<Mechanic>()
        for (m in sorted) {
            if (m == Mechanic.NORMAL) continue
            if (out.all { compat(it, m) != Compat.NO }) out.add(m)
        }
        if (out.isEmpty()) out.add(Mechanic.NORMAL)
        return out
    }
}
