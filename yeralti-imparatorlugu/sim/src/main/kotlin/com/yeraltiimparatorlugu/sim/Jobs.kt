package com.yeraltiimparatorlugu.sim

enum class JobStatus { RUNNING, DONE, FAILED }

/**
 * Bir karıncanın yürüttüğü iş. İşler aşamalardan oluşur (yürü → çalış → taşı). Ayırmalar (rezervasyon)
 * [cancel] ile serbest bırakılır, böylece aynı iş iki karıncaya verilmez.
 */
abstract class Job(val category: TaskCategory?) {
    var phase = 0
    var targetX = 0
    var targetY = 0
    var timer = 0f

    abstract fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus

    open fun cancel(sim: Simulation, ant: Ant) {}

    /** Yürüme sonucu ortak işleme: ulaşılamıyorsa iş başarısız olur. */
    protected fun walk(sim: Simulation, ant: Ant, dt: Float): MoveResult = sim.moveAnt(ant, targetX, targetY, dt)
}

/** Taşınan yükü uygun depoya (veya depo doluysa toprağı yüzeydeki yığına) götürür. */
class DeliverJob : Job(TaskCategory.HAUL) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        val type = ant.carryType ?: return JobStatus.DONE
        if (ant.carryAmount <= 0) { ant.carryType = null; return JobStatus.DONE }
        ant.activity = AntActivity.HAULING
        when (phase) {
            0 -> {
                if (sim.freeSpaceFor(type) <= 0) {
                    if (type == ResourceType.SOIL) { phase = 2; targetX = sim.dumpX; targetY = -1 } else {
                        sim.dropCarried(ant); return JobStatus.DONE
                    }
                } else {
                    val room = sim.storageRoomFor(type.storage, ant.cellX, ant.cellY) ?: run { sim.dropCarried(ant); return JobStatus.DONE }
                    val c = sim.floorCell(room)
                    targetX = keyX(c); targetY = keyY(c); phase = 1
                }
            }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    sim.depositCarried(ant)
                    if (ant.carryAmount > 0) phase = 0 else return JobStatus.DONE
                }
                MoveResult.UNREACHABLE -> { sim.dropCarried(ant); return JobStatus.FAILED }
                else -> {}
            }
            2 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> { sim.dumpCarriedSoil(ant); return JobStatus.DONE }
                MoveResult.UNREACHABLE -> { sim.dropCarried(ant); return JobStatus.FAILED }
                else -> {}
            }
        }
        return JobStatus.RUNNING
    }
}

class DigJob(val order: DigOrder) : Job(TaskCategory.DIG) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (!sim.isOrderActive(order)) return JobStatus.DONE
        when (phase) {
            0 -> {
                val stand = sim.bestStandCell(order.x, order.y, ant.cellX, ant.cellY) ?: run {
                    order.unreachableUntil = sim.time + 8.0; return JobStatus.FAILED
                }
                targetX = keyX(stand); targetY = keyY(stand); phase = 1
            }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> phase = 2
                    MoveResult.UNREACHABLE -> { order.unreachableUntil = sim.time + 8.0; return JobStatus.FAILED }
                    else -> {}
                }
            }
            2 -> {
                ant.activity = AntActivity.DIGGING
                ant.facing = kotlin.math.atan2((order.y + 0.5f - ant.y), (order.x + 0.5f - ant.x))
                order.progress += ant.workRate(TaskCategory.DIG) * sim.digSpeedMultiplier * dt
                val terrain = sim.world.get(order.x, order.y)
                if (order.progress >= terrain.digWork) {
                    sim.completeDig(order, ant)
                    return JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (order.reservedBy == ant.id) order.reservedBy = -1
    }
}

class HaulJob(val pile: Pile) : Job(TaskCategory.HAUL) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.HAULING
        if (phase == 0) { targetX = pile.x; targetY = pile.y; phase = 1 }
        when (walk(sim, ant, dt)) {
            MoveResult.ARRIVED -> {
                sim.pickUpPile(pile, ant)
                return JobStatus.DONE
            }
            MoveResult.UNREACHABLE -> { pile.unreachableUntil = sim.time + 10.0; return JobStatus.FAILED }
            else -> {}
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (pile.reservedBy == ant.id) pile.reservedBy = -1
    }
}

class GatherJob(val source: FoodSource, val reservedAmount: Int) : Job(TaskCategory.GATHER) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        when (phase) {
            0 -> { targetX = source.x; targetY = -1; phase = 1 }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> phase = 2
                    MoveResult.UNREACHABLE -> { source.unreachableUntil = sim.time + 15.0; return JobStatus.FAILED }
                    else -> {}
                }
            }
            2 -> {
                ant.activity = AntActivity.GATHERING
                timer += ant.workRate(TaskCategory.GATHER) * dt
                if (timer >= source.kind.gatherTime * reservedAmount) {
                    val taken = minOf(reservedAmount, source.amount)
                    source.amount -= taken
                    source.reserved -= reservedAmount
                    released = true
                    if (taken > 0) {
                        ant.carryType = source.kind.resource
                        ant.carryAmount = taken
                    }
                    sim.onSourceGathered(source)
                    return JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (!released) { source.reserved -= reservedAmount; released = true }
    }
}

class BuildJob(val room: Room) : Job(TaskCategory.BUILD) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (room.state != RoomState.CONSTRUCTING && room.state != RoomState.UPGRADING) return JobStatus.DONE
        when (phase) {
            0 -> { val c = sim.floorCell(room); targetX = keyX(c); targetY = keyY(c); phase = 1 }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> phase = 2
                    MoveResult.UNREACHABLE -> return JobStatus.FAILED
                    else -> {}
                }
            }
            2 -> {
                ant.activity = AntActivity.BUILDING
                room.buildProgress += ant.workRate(TaskCategory.BUILD) * dt
                if (room.buildProgress >= room.workRequired) {
                    sim.completeConstruction(room)
                    return JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (!released) { room.builders--; released = true }
    }
}

/** Mantar çiftliğine malzeme deposundan yaprak taşır (üretim zinciri: yaprak → mantar → besin). */
class SupplyFarmJob(val farm: Room, val amount: Int) : Job(TaskCategory.PRODUCE) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (!farm.functional) return JobStatus.FAILED
        ant.activity = AntActivity.PRODUCING
        when (phase) {
            0 -> {
                val store = sim.storageRoomFor(StorageKind.MATERIAL, ant.cellX, ant.cellY) ?: return JobStatus.FAILED
                val c = sim.floorCell(store); targetX = keyX(c); targetY = keyY(c); phase = 1
            }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    val got = sim.resources.take(ResourceType.LEAF, amount)
                    if (got <= 0) return JobStatus.FAILED
                    ant.carryType = ResourceType.LEAF
                    ant.carryAmount = got
                    val c = sim.floorCell(farm); targetX = keyX(c); targetY = keyY(c); phase = 2
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    farm.leafBuffer += ant.carryAmount
                    ant.carryAmount = 0
                    ant.carryType = null
                    ant.xp++
                    return JobStatus.DONE
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (!released) { farm.supplyReserved -= amount; released = true }
    }
}

/** Besin deposundan yiyecek alıp aç bir larvayı besler. */
class NurseJob(val brood: Brood) : Job(TaskCategory.NURSE) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.NURSING
        when (phase) {
            0 -> {
                val store = sim.storageRoomFor(StorageKind.FOOD, ant.cellX, ant.cellY) ?: return JobStatus.FAILED
                val c = sim.floorCell(store); targetX = keyX(c); targetY = keyY(c); phase = 1
            }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    val t = sim.resources.bestEdible() ?: return JobStatus.FAILED
                    sim.resources.take(t, 1)
                    ant.carryType = t
                    ant.carryAmount = 1
                    val room = sim.roomById(brood.roomId) ?: return JobStatus.FAILED
                    val c = sim.floorCell(room); targetX = keyX(c); targetY = keyY(c); phase = 2
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> {
                if (brood !in sim.brood) return JobStatus.DONE
                val room = sim.roomById(brood.roomId)
                if (room != null && (targetX < room.x || targetX >= room.x + room.width || targetY < room.y || targetY >= room.y + room.height)) {
                    val c = sim.floorCell(room); targetX = keyX(c); targetY = keyY(c)
                }
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> {
                        brood.fed = 1f
                        ant.carryAmount = 0
                        ant.carryType = null
                        ant.xp++
                        return JobStatus.DONE
                    }
                    MoveResult.UNREACHABLE -> return JobStatus.FAILED
                    else -> {}
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (brood.nurseReservedBy == ant.id) brood.nurseReservedBy = -1
    }
}

class EatJob : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.EATING
        when (phase) {
            0 -> {
                val store = sim.storageRoomFor(StorageKind.FOOD, ant.cellX, ant.cellY) ?: return JobStatus.FAILED
                val c = sim.floorCell(store); targetX = keyX(c); targetY = keyY(c); phase = 1
            }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> { phase = 2; timer = 0f }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> {
                timer += dt
                if (timer >= 1.5f) {
                    val t = sim.resources.bestEdible() ?: return JobStatus.FAILED
                    sim.resources.take(t, 1)
                    ant.hunger = (ant.hunger - t.nutrition).coerceAtLeast(0f)
                    sim.stats.foodEaten++
                    return if (ant.hunger > 40f) { phase = 2; timer = 0f; JobStatus.RUNNING } else JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }
}

class RestJob(val room: Room?) : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        when (phase) {
            0 -> {
                if (room == null || !room.functional) { phase = 2 } else {
                    val c = sim.floorCell(room); targetX = keyX(c); targetY = keyY(c); phase = 1
                }
            }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> phase = 2
                    MoveResult.UNREACHABLE -> phase = 2
                    else -> {}
                }
            }
            2 -> {
                ant.activity = AntActivity.RESTING
                val inRoom = room != null && room.functional && room.contains(ant.cellX, ant.cellY)
                ant.energy += (if (inRoom) 10f else 4f) * dt
                if (ant.energy >= 100f) { ant.energy = 100f; return JobStatus.DONE }
            }
        }
        return JobStatus.RUNNING
    }

    val restingInRoom: Boolean get() = phase == 2 && room != null
}

/** Boştaki karıncanın yakınlarda dolaşması veya toplanma feromonuna gitmesi. */
class WanderJob(x: Int, y: Int, private val waitTime: Float) : Job(null) {
    init { targetX = x; targetY = y }

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (phase == 0) {
            ant.activity = AntActivity.IDLE
            when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> phase = 1
                MoveResult.UNREACHABLE -> return JobStatus.DONE
                else -> {}
            }
        } else {
            ant.activity = AntActivity.IDLE
            timer += dt
            if (timer >= waitTime) return JobStatus.DONE
        }
        return JobStatus.RUNNING
    }
}
