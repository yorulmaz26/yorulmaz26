package com.kayipadalar.logic.engine

import com.kayipadalar.logic.model.Cell
import com.kayipadalar.logic.model.Dir
import com.kayipadalar.logic.model.Res

/** Oyuncu eylemleri. [encode]/[decode] çözüm kayıtları için kullanılır. */
sealed class Action {
    data class Slide(val id: String, val dir: Dir) : Action()
    data class Rotate(val id: String) : Action()
    data class Swap(val a: String, val b: String) : Action()
    data class Raise(val id: String, val up: Boolean) : Action()
    data class Jump(val id: String, val dir: Dir) : Action()
    data class MoveUnit(val unitId: String, val islandId: String) : Action()
    data object Wait : Action()
    data class Merge(val a: String, val b: String) : Action()
    data class Split(val id: String) : Action()

    fun encode(): String = when (this) {
        is Slide -> "S:$id:${dir.name}"
        is Rotate -> "R:$id"
        is Swap -> "X:$a:$b"
        is Raise -> if (up) "H+:$id" else "H-:$id"
        is Jump -> "J:$id:${dir.name}"
        is MoveUnit -> "U:$unitId:$islandId"
        Wait -> "W"
        is Merge -> "M:$a:$b"
        is Split -> "D:$id"
    }

    companion object {
        fun decode(s: String): Action {
            val p = s.split(':')
            return when (p[0]) {
                "S" -> Slide(p[1], Dir.valueOf(p[2]))
                "R" -> Rotate(p[1])
                "X" -> Swap(p[1], p[2])
                "H+" -> Raise(p[1], true)
                "H-" -> Raise(p[1], false)
                "J" -> Jump(p[1], Dir.valueOf(p[2]))
                "U" -> MoveUnit(p[1], p[2])
                "W" -> Wait
                "M" -> Merge(p[1], p[2])
                "D" -> Split(p[1])
                else -> throw IllegalArgumentException("Bilinmeyen eylem: $s")
            }
        }
    }
}

/** Arayüzün animasyon ve geri bildirim için tükettiği olaylar. */
sealed class GameEvent {
    data class IslandMoved(val id: String, val from: Cell, val to: Cell, val pushed: Boolean = false) : GameEvent()
    data class IslandRotated(val id: String) : GameEvent()
    data class IslandHeight(val id: String, val height: Int) : GameEvent()
    data class Merged(val a: String, val b: String) : GameEvent()
    data class SplitOff(val id: String) : GameEvent()
    data class BridgeFormed(val a: String, val b: String) : GameEvent()
    data class BridgeBroken(val a: String, val b: String) : GameEvent()
    data class Combo(val comboId: String, val a: String, val b: String) : GameEvent()
    data class TripleCombo(val type: String, val size: Int, val at: String) : GameEvent()
    data class Produced(val res: Res, val amount: Int, val islandId: String) : GameEvent()
    data class Burned(val id: String) : GameEvent()
    data class Thawed(val id: String) : GameEvent()
    data class Revealed(val id: String) : GameEvent()
    data class Collapsed(val id: String) : GameEvent()
    data class Collected(val id: String, val kind: String) : GameEvent()
    data class Transformed(val id: String, val to: String) : GameEvent()
    data class UnitMoved(val unitId: String, val from: String, val to: String) : GameEvent()
    data class Attack(val attacker: String, val target: String, val damage: Int, val targetIsland: String) : GameEvent()
    data class CastleHit(val attacker: String, val damage: Int) : GameEvent()
    data class Healed(val unitId: String, val amount: Int) : GameEvent()
    data class UnitDied(val unitId: String, val islandId: String) : GameEvent()
    data class UnitSpawned(val unitId: String, val islandId: String) : GameEvent()
    data class Captured(val islandId: String) : GameEvent()
    data class BossAbility(val ability: String, val target: String?) : GameEvent()
    data class EnemyTrick(val kind: String, val target: String) : GameEvent()
    data class Eruption(val id: String) : GameEvent()
    data class Info(val key: String, val value: Int = 0) : GameEvent()
}

data class ActionResult(val ok: Boolean, val error: String? = null, val events: List<GameEvent> = emptyList())
