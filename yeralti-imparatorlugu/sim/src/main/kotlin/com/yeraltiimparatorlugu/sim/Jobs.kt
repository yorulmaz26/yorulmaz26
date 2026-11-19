package com.yeraltiimparatorlugu.sim

enum class JobStatus { RUNNING, DONE, FAILED }

/**
 * Karıncanın yürüttüğü iş. İşler aşamalardan oluşur (yürü → çalış → taşı). Rezervasyonlar [cancel] ile serbest bırakılır.
 */
abstract class Job(val category: TaskCategory?) {
    var phase = 0
    var targetX = 0
    var targetY = 0
    var timer = 0f

    abstract fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus
    open fun cancel(sim: Simulation, ant: Ant) {}

    protected fun walk(sim: Simulation, ant: Ant, dt: Float): MoveResult = sim.moveAnt(ant, targetX, targetY, dt)

    protected fun goRoom(sim: Simulation, room: Room) { val c = sim.floorCell(room); targetX = keyX(c); targetY = keyY(c) }
}

class DeliverJob : Job(TaskCategory.HAUL) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        val type = ant.carryType ?: return JobStatus.DONE
        if (ant.carryAmount <= 0) { ant.carryType = null; return JobStatus.DONE }
        ant.activity = AntActivity.HAULING
        when (phase) {
            0 -> {
                if (sim.freeSpaceFor(type) <= 0) {
                    if (type == ResourceType.SOIL) { phase = 2; targetX = sim.dumpX; targetY = -1 } else { sim.dropCarried(ant); return JobStatus.DONE }
                } else {
                    val room = sim.storageRoomFor(type.storage, ant.cellX, ant.cellY) ?: run { sim.dropCarried(ant); return JobStatus.DONE }
                    goRoom(sim, room); phase = 1
                }
            }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> { sim.depositCarried(ant); if (ant.carryAmount > 0) phase = 0 else return JobStatus.DONE }
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
                val stand = sim.bestStandCell(order.x, order.y, ant.cellX, ant.cellY) ?: run { order.unreachableUntil = sim.time + 8.0; return JobStatus.FAILED }
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
                val terrain = sim.world.get(order.x, order.y)
                var rate = ant.workRate(TaskCategory.DIG, sim.mods) * sim.mods.digSpeed
                if (terrain.needsHardRockTech && !sim.mods.hardRock) rate *= 0.5f // yalnızca Tünel Uzmanı buraya gelir
                order.progress += rate * dt
                if (order.progress >= terrain.digWork) { sim.completeDig(order, ant); return JobStatus.DONE }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (order.reservedBy == ant.id) order.reservedBy = -1 }
}

class FillJob(val order: FillOrder) : Job(TaskCategory.REPAIR) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (sim.fillOrders[cellKey(order.x, order.y)] !== order) return JobStatus.DONE
        when (phase) {
            0 -> {
                if (ant.carryType == ResourceType.SOIL && ant.carryAmount > 0) { phase = 2; return JobStatus.RUNNING }
                val store = sim.storageRoomFor(StorageKind.MATERIAL, ant.cellX, ant.cellY) ?: return JobStatus.FAILED
                goRoom(sim, store); phase = 1
            }
            1 -> {
                ant.activity = AntActivity.FILLING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> {
                        if (sim.resources.take(ResourceType.SOIL, 2) < 2) { order.unreachableUntil = sim.time + 20.0; return JobStatus.FAILED }
                        ant.carryType = ResourceType.SOIL; ant.carryAmount = 2; phase = 2
                    }
                    MoveResult.UNREACHABLE -> return JobStatus.FAILED
                    else -> {}
                }
            }
            2 -> {
                val stand = sim.bestStandCell(order.x, order.y, ant.cellX, ant.cellY) ?: run { order.unreachableUntil = sim.time + 10.0; return JobStatus.FAILED }
                targetX = keyX(stand); targetY = keyY(stand); phase = 3
            }
            3 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> phase = 4
                MoveResult.UNREACHABLE -> { order.unreachableUntil = sim.time + 10.0; return JobStatus.FAILED }
                else -> {}
            }
            4 -> {
                ant.activity = AntActivity.FILLING
                order.progress += ant.workRate(TaskCategory.REPAIR, sim.mods) * sim.mods.buildSpeed * dt
                if (order.progress >= 3f) {
                    ant.carryAmount = 0; ant.carryType = null
                    sim.completeFill(order)
                    return JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (order.reservedBy == ant.id) order.reservedBy = -1 }
}

class HaulJob(val pile: Pile) : Job(TaskCategory.HAUL) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.HAULING
        if (phase == 0) { targetX = pile.x; targetY = pile.y; phase = 1 }
        when (walk(sim, ant, dt)) {
            MoveResult.ARRIVED -> { sim.pickUpPile(pile, ant); return JobStatus.DONE }
            MoveResult.UNREACHABLE -> { pile.unreachableUntil = sim.time + 10.0; return JobStatus.FAILED }
            else -> {}
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (pile.reservedBy == ant.id) pile.reservedBy = -1 }
}

class CleanJob(val corpse: Corpse) : Job(TaskCategory.CLEAN) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.CLEANING
        when (phase) {
            0 -> { targetX = corpse.x; targetY = corpse.y; phase = 1 }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    if (!sim.corpses.remove(corpse)) return JobStatus.DONE
                    ant.carryingCorpse = true; targetX = sim.dumpX + 3; targetY = -1; phase = 2
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> { ant.carryingCorpse = false; return JobStatus.DONE }
                MoveResult.UNREACHABLE -> { ant.carryingCorpse = false; return JobStatus.FAILED }
                else -> {}
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) {
        if (ant.carryingCorpse) { ant.carryingCorpse = false; sim.corpses.add(Corpse(sim.newId(), ant.cellX, ant.cellY, "Kalıntı")) }
        if (corpse.reservedBy == ant.id) corpse.reservedBy = -1
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
                timer += ant.workRate(TaskCategory.GATHER, sim.mods) * sim.mods.gatherMult * dt
                if (timer >= source.kind.gatherTime * reservedAmount) {
                    val taken = minOf(reservedAmount, source.amount)
                    source.amount -= taken
                    source.reserved -= reservedAmount
                    released = true
                    if (taken > 0) { ant.carryType = source.kind.resource; ant.carryAmount = taken }
                    sim.onSourceGathered(source)
                    return JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (!released) { source.reserved -= reservedAmount; released = true } }
}

class BuildJob(val room: Room) : Job(TaskCategory.BUILD) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (room.state != RoomState.CONSTRUCTING && room.state != RoomState.UPGRADING) return JobStatus.DONE
        when (phase) {
            0 -> { goRoom(sim, room); phase = 1 }
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
                room.buildProgress += ant.workRate(TaskCategory.BUILD, sim.mods) * sim.mods.buildSpeed * dt
                if (room.buildProgress >= room.workRequired) { sim.completeConstruction(room); return JobStatus.DONE }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (!released) { room.builders--; released = true } }
}

class SupplyFarmJob(val farm: Room, val amount: Int) : Job(TaskCategory.PRODUCE) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (!farm.functional) return JobStatus.FAILED
        ant.activity = AntActivity.PRODUCING
        when (phase) {
            0 -> { val store = sim.storageRoomFor(StorageKind.MATERIAL, ant.cellX, ant.cellY) ?: return JobStatus.FAILED; goRoom(sim, store); phase = 1 }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    val got = sim.resources.take(ResourceType.LEAF, amount)
                    if (got <= 0) return JobStatus.FAILED
                    ant.carryType = ResourceType.LEAF; ant.carryAmount = got
                    goRoom(sim, farm); phase = 2
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    farm.leafBuffer += ant.carryAmount
                    ant.carryAmount = 0; ant.carryType = null; ant.xp++
                    return JobStatus.DONE
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (!released) { farm.supplyReserved -= amount; released = true } }
}

/** İstasyon odasında çalışma: araştırma, atölye tarifi, genetik, kraliyet özü veya sınıf eğitimi. */
class StationJob(val room: Room, category: TaskCategory) : Job(category) {
    private var released = false

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (!room.functional) return JobStatus.FAILED
        when (phase) {
            0 -> { goRoom(sim, room); phase = 1 }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> phase = 2
                    MoveResult.UNREACHABLE -> return JobStatus.FAILED
                    else -> {}
                }
            }
            2 -> {
                ant.activity = when (room.type) {
                    RoomType.RESEARCH_LAB, RoomType.GENETICS_LAB -> AntActivity.RESEARCHING
                    RoomType.TRAINING_ROOM -> AntActivity.TRAINING
                    else -> AntActivity.PRODUCING
                }
                timer += dt
                if (!sim.stationWork(room, ant, ant.workRate(category, sim.mods) * dt)) return JobStatus.DONE
                if (timer > 25f) return JobStatus.DONE
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (!released) { room.workers--; released = true } }
}

class NurseJob(val brood: Brood) : Job(TaskCategory.NURSE) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.NURSING
        when (phase) {
            0 -> { val store = sim.storageRoomFor(StorageKind.FOOD, ant.cellX, ant.cellY) ?: return JobStatus.FAILED; goRoom(sim, store); phase = 1 }
            1 -> when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> {
                    val t = sim.resources.bestEdible() ?: return JobStatus.FAILED
                    sim.resources.take(t, 1)
                    ant.carryType = t; ant.carryAmount = 1
                    val room = sim.roomById(brood.roomId) ?: return JobStatus.FAILED
                    goRoom(sim, room); phase = 2
                }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
            2 -> {
                if (brood !in sim.brood) return JobStatus.DONE
                val room = sim.roomById(brood.roomId)
                if (room != null && !room.contains(targetX, targetY)) goRoom(sim, room)
                when (walk(sim, ant, dt)) {
                    MoveResult.ARRIVED -> { brood.fed = 1f; ant.carryAmount = 0; ant.carryType = null; ant.xp++; return JobStatus.DONE }
                    MoveResult.UNREACHABLE -> return JobStatus.FAILED
                    else -> {}
                }
            }
        }
        return JobStatus.RUNNING
    }

    override fun cancel(sim: Simulation, ant: Ant) { if (brood.nurseReservedBy == ant.id) brood.nurseReservedBy = -1 }
}

/** Şifacının yaralı/hasta karıncayı iyileştirmesi. */
class HealJob(val patient: Ant) : Job(TaskCategory.NURSE) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (patient !in sim.ants) return JobStatus.DONE
        ant.activity = AntActivity.HEALING
        timer += dt
        if (phase == 0 || timer > 2f && kotlin.math.abs(patient.cellX - targetX) + kotlin.math.abs(patient.cellY - targetY) > 1) {
            targetX = patient.cellX; targetY = patient.cellY; phase = 1; timer = 0f; ant.clearPath()
        }
        val dx = patient.x - ant.x
        val dy = patient.y - ant.y
        if (dx * dx + dy * dy <= 1.6f) {
            patient.hp = (patient.hp + 4f * dt).coerceAtMost(patient.maxHp(sim.mods))
            if (sim.rng.nextFloat() < dt * 0.25f) patient.sick = false
            if (patient.hp >= patient.maxHp(sim.mods) * 0.95f && !patient.sick) { ant.xp++; return JobStatus.DONE }
            return JobStatus.RUNNING
        }
        return if (walk(sim, ant, dt) == MoveResult.UNREACHABLE) JobStatus.FAILED else JobStatus.RUNNING
    }
}

class EatJob : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.EATING
        when (phase) {
            0 -> { val store = sim.storageRoomFor(StorageKind.FOOD, ant.cellX, ant.cellY) ?: return JobStatus.FAILED; goRoom(sim, store); phase = 1 }
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
                    return if (ant.hunger > 40f) { timer = 0f; JobStatus.RUNNING } else JobStatus.DONE
                }
            }
        }
        return JobStatus.RUNNING
    }
}

/** Dinlenme: işçiler Dinlenme Odasında, askerler Kışlada, hastalar Sağlık Odasında daha hızlı toparlanır. */
class RestJob(val room: Room?) : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        when (phase) {
            0 -> if (room == null || !room.functional) phase = 2 else { goRoom(sim, room); phase = 1 }
            1 -> {
                ant.activity = AntActivity.WALKING
                when (walk(sim, ant, dt)) { MoveResult.ARRIVED, MoveResult.UNREACHABLE -> phase = 2; else -> {} }
            }
            2 -> {
                ant.activity = AntActivity.RESTING
                val inRoom = room != null && room.functional && room.contains(ant.cellX, ant.cellY)
                val infirmary = inRoom && room!!.type == RoomType.INFIRMARY
                ant.energy += (if (inRoom) 10f else 4f) * dt
                val maxHp = ant.maxHp(sim.mods)
                ant.hp = (ant.hp + (if (infirmary) 2.5f * room!!.level else 0.3f) * dt).coerceAtMost(maxHp)
                if (infirmary && ant.sick && sim.rng.nextFloat() < dt * 0.2f) ant.sick = false
                val healthy = ant.hp >= maxHp * 0.9f && !ant.sick
                if (ant.energy >= 100f && (healthy || !infirmary)) { ant.energy = 100f; return JobStatus.DONE }
                if (infirmary && ant.energy >= 100f) ant.energy = 100f
            }
        }
        return JobStatus.RUNNING
    }
}

class WanderJob(x: Int, y: Int, private val waitTime: Float) : Job(null) {
    init { targetX = x; targetY = y }

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = if (ant.type.military) AntActivity.GUARDING else AntActivity.IDLE
        if (phase == 0) {
            when (walk(sim, ant, dt)) { MoveResult.ARRIVED -> phase = 1; MoveResult.UNREACHABLE -> return JobStatus.DONE; else -> {} }
        } else {
            timer += dt
            if (timer >= waitTime) return JobStatus.DONE
        }
        return JobStatus.RUNNING
    }
}

/** Keşif: yüzey sınırına veya Keşif feromonuna gidip bölgeyi açığa çıkarır. */
class ExploreJob(x: Int, y: Int) : Job(TaskCategory.EXPLORE) {
    init { targetX = x; targetY = y }

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.EXPLORING
        return when (walk(sim, ant, dt)) {
            MoveResult.ARRIVED -> { sim.explored(ant, targetX, targetY); JobStatus.DONE }
            MoveResult.UNREACHABLE -> JobStatus.FAILED
            else -> { if (ant.cellY < 0) sim.exploreSurface(ant.cellX); JobStatus.RUNNING }
        }
    }
}

/** Tehlikeden kaçış (Kraliçe Odasına). */
class FleeJob : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        ant.activity = AntActivity.FLEEING
        if (phase == 0) { val q = sim.rooms.first { it.type == RoomType.QUEEN_CHAMBER }; goRoom(sim, q); phase = 1 }
        timer += dt
        return when (walk(sim, ant, dt)) {
            MoveResult.ARRIVED -> if (timer > 4f) JobStatus.DONE else JobStatus.RUNNING
            MoveResult.UNREACHABLE -> JobStatus.DONE
            else -> if (timer > 15f) JobStatus.DONE else JobStatus.RUNNING
        }
    }
}

/** Düşmanı kovalayıp saldırma (askerler ve savunmaya katılan işçiler). */
class FightJob(val target: Creature) : Job(TaskCategory.DEFENSE) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (target.hp <= 0f || target !in sim.creatures) return JobStatus.DONE
        ant.activity = AntActivity.FIGHTING
        val dx = target.x - ant.x
        val dy = target.y - ant.y
        if (dx * dx + dy * dy <= 1.3f) {
            ant.facing = kotlin.math.atan2(dy, dx)
            sim.antAttacks(ant, target)
            return JobStatus.RUNNING
        }
        timer += dt
        if (phase == 0 || timer > 1.5f) { targetX = target.cellX; targetY = target.cellY; phase = 1; timer = 0f; ant.clearPath() }
        return when (walk(sim, ant, dt)) {
            MoveResult.UNREACHABLE -> JobStatus.FAILED
            MoveResult.ARRIVED -> { targetX = target.cellX; targetY = target.cellY; JobStatus.RUNNING }
            else -> JobStatus.RUNNING
        }
    }
}

/** Askerin emir noktasına gidip beklemesi (savun, koru, toplan, pusu, geri çekil). */
class GuardJob(x: Int, y: Int, private val ambush: Boolean) : Job(null) {
    init { targetX = x; targetY = y }

    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        if (phase == 0) {
            ant.activity = AntActivity.WALKING
            when (walk(sim, ant, dt)) {
                MoveResult.ARRIVED -> { phase = 1; if (ambush) ant.ambushReady = true }
                MoveResult.UNREACHABLE -> return JobStatus.FAILED
                else -> {}
            }
        } else {
            ant.activity = AntActivity.GUARDING
            timer += dt
            if (timer > 3f) return JobStatus.DONE
        }
        return JobStatus.RUNNING
    }
}

/** Rakip koloniye sefer: yüzeyden yürüyüp kuşatmaya katılır. */
class MarchJob(val rivalId: Int) : Job(null) {
    override fun update(sim: Simulation, ant: Ant, dt: Float): JobStatus {
        val r = sim.rivalSystem.byId(rivalId) ?: return JobStatus.DONE
        if (!r.alive) return JobStatus.DONE
        ant.activity = AntActivity.MARCHING
        if (phase == 0) { targetX = r.x; targetY = -1; phase = 1 }
        return when (walk(sim, ant, dt)) {
            MoveResult.ARRIVED -> { sim.siege(ant, r, dt); JobStatus.RUNNING }
            MoveResult.UNREACHABLE -> JobStatus.FAILED
            else -> JobStatus.RUNNING
        }
    }
}
