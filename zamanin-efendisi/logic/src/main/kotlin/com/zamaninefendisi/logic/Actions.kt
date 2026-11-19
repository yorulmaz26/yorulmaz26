package com.zamaninefendisi.logic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Oyuncunun hücrelere uyguladığı inşa/değişiklik türleri. [woodCost] negatifse odun kazandırır. */
enum class BuildKind(
    val title: String,
    val woodCost: Int,
    val verb: String,
    val energyGain: Int = 0,
    val playerChoosable: Boolean = true,
    /** Harcanan dondurma hakkı. */
    val freezeCost: Int = 0,
) {
    PLANT("Fidan Dik", 0, "fidan diktin"),
    CUT("Ağacı Kes", -2, "ağaç kestin"),
    BRIDGE("Köprü Kur", 2, "köprü kurdun"),
    VILLAGE("Köy Kur", 3, "köy kurdun"),
    WALL("Çit Kur", 1, "çit kurdun"),
    DISMANTLE("Kampı Dağıt", 0, "düşman kampını dağıttın"),
    ACTIVATE("Mührü Uyandır", 0, "zaman mührünü uyandırdın"),
    /** Kristalin üstüne gelince kendiliğinden gerçekleşir. */
    COLLECT("Kristal Topla", 0, "zaman kristali topladın", energyGain = 1, playerChoosable = false),
    TOWER("Kule Kur", 2, "gözcü kulesi kurdun"),
    FREEZE("Zamanı Dondur", 0, "bir nesnenin zamanını dondurdun", freezeCost = 1),
}

@Serializable
sealed class Action {
    @Serializable @SerialName("move")
    data class Move(val to: Pos) : Action()

    @Serializable @SerialName("era")
    data class SwitchEra(val era: Era) : Action()

    @Serializable @SerialName("build")
    data class Build(val kind: BuildKind, val at: Pos) : Action()

    @Serializable @SerialName("attack")
    data class Attack(val at: Pos) : Action()

    @Serializable @SerialName("wait")
    data object Wait : Action()

    /** Müttefik birliği hareket ettir (hareket menzili içinde). */
    @Serializable @SerialName("unit_move")
    data class UnitMove(val unit: Int, val to: Pos) : Action()

    /** Müttefik birlikle menzildeki düşmana ya da düşman kalesine saldır. */
    @Serializable @SerialName("unit_attack")
    data class UnitAttack(val unit: Int, val at: Pos) : Action()
}

/** Oyuncunun bir çağda yaptığı kalıcı değişiklik. Nedensellik motorunun girdisidir. */
data class ChangeRecord(
    val id: Int,
    val era: Era,
    val pos: Pos,
    val kind: BuildKind,
    val turn: Int,
) {
    /** Sonraki bir geçmiş değişikliği bu değişikliği imkânsız kıldıysa (paradoks) true olur ve kalıcıdır. */
    var voided: Boolean = false
        internal set
}
