package com.emirzinciri.robotkomutan.logic.sim

import com.emirzinciri.robotkomutan.logic.grid.Pos

/** Simülasyonun her adımda ürettiği olaylar. Animasyon, ses ve hata ayıklama paneli bunları kullanır. */
sealed class SimEvent {
    data class Executed(val robot: String, val pc: Int) : SimEvent()
    data class Moved(val robot: String, val from: Pos, val to: Pos, val jump: Boolean = false, val conveyor: Boolean = false) : SimEvent()
    data class Turned(val robot: String) : SimEvent()
    data class Bumped(val robot: String, val at: Pos, val pc: Int, val reason: String) : SimEvent()
    data class CommandFailed(val robot: String, val pc: Int, val reason: String) : SimEvent()
    data class BoxMoved(val from: Pos, val to: Pos) : SimEvent()
    data class BoxPicked(val robot: String, val at: Pos) : SimEvent()
    data class BoxDropped(val robot: String, val at: Pos) : SimEvent()
    data class PitFilled(val at: Pos) : SimEvent()
    data class BoxLost(val at: Pos) : SimEvent()
    data class Pressed(val robot: String, val at: Pos, val channel: Int) : SimEvent()
    data class EnergyCollected(val robot: String, val amount: Int) : SimEvent()
    data class EnergyTransferred(val robot: String, val to: Pos, val amount: Int) : SimEvent()
    data class SocketPowered(val at: Pos) : SimEvent()
    data class MessageSent(val robot: String) : SimEvent()
    data class MessageReceived(val robot: String) : SimEvent()
    data class DoorChanged(val at: Pos, val open: Boolean) : SimEvent()
    data class RobotLost(val robot: String, val at: Pos, val laser: Boolean = false) : SimEvent()
    data class OutOfEnergy(val robot: String, val pc: Int) : SimEvent()
    data class ProgramFinished(val robot: String) : SimEvent()
    data class ShieldUp(val robot: String) : SimEvent()
    data class EnemyHit(val enemy: String, val at: Pos, val damage: Int, val by: String?) : SimEvent()
    data class EnemyDefeated(val enemy: String, val at: Pos) : SimEvent()
    data class EnemyMoved(val enemy: String, val from: Pos, val to: Pos) : SimEvent()
    data class EnemyAttack(val enemy: String, val robot: String, val damage: Int, val blocked: Boolean, val ranged: Boolean) : SimEvent()
    data class RobotDestroyed(val robot: String, val at: Pos) : SimEvent()
}

enum class FailReason(val title: String) {
    ROBOT_LOST("Robot kayboldu"),
    LASER("Robot lazere yakalandı"),
    DESTROYED("Robot yok edildi"),
    NO_ENERGY("Enerji bitti"),
    PROGRAM_ENDED("Komutlar bitti ama hedef tamamlanmadı"),
    DEADLOCK("Robotlar birbirini bekliyor"),
    TIMEOUT("Süre doldu"),
    INVALID_PROGRAM("Komut zinciri hatalı"),
}

data class RunStats(val ticks: Int, val energyUsed: Int, val bumps: Int, val cards: Int)

sealed class Outcome {
    abstract val stats: RunStats

    data class Win(override val stats: RunStats) : Outcome()
    data class Fail(
        val reason: FailReason,
        val detail: String,
        override val stats: RunStats,
        val robot: String? = null,
        val pc: Int = -1,
    ) : Outcome()
}

class StepResult(val tick: Int, val events: List<SimEvent>, val outcome: Outcome?)
