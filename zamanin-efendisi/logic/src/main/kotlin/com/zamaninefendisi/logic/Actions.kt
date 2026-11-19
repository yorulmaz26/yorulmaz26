package com.zamaninefendisi.logic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Oyuncunun hücrelere uyguladığı inşa/değişiklik türleri. [woodCost] negatifse odun kazandırır. */
enum class BuildKind(val title: String, val woodCost: Int, val verb: String) {
    PLANT("Fidan Dik", 0, "fidan diktin"),
    CUT("Ağacı Kes", -2, "ağaç kestin"),
    BRIDGE("Köprü Kur", 2, "köprü kurdun"),
    VILLAGE("Köy Kur", 3, "köy kurdun"),
    WALL("Çit Kur", 1, "çit kurdun"),
    DISMANTLE("Kampı Dağıt", 0, "düşman kampını dağıttın"),
    ACTIVATE("Mührü Uyandır", 0, "zaman mührünü uyandırdın"),
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
