package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.EnemyDef
import com.emirzinciri.robotkomutan.logic.level.EnemyType
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.ObjectiveDef
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.level.RobotClass
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.sim.FailReason
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CombatTest {
    private fun lv(map: List<String>, enemies: List<EnemyDef>, robots: List<RobotDef> = listOf(robot("A", 0, 0)),
                   obj: ObjectiveType = ObjectiveType.DEFEAT_ENEMIES) =
        LevelData(level(*map.toTypedArray(), robots = robots).def.copy(enemies = enemies, objectives = listOf(ObjectiveDef(obj))))

    private fun run(l: LevelData, vararg p: Pair<String, String>) =
        Simulation.create(l, p.associate { (id, c) -> id to c.split(' ') }).getOrThrow()

    @Test fun attackDefeatsEnemyAndRobotIsBlockedByIt() {
        val l = lv(listOf("..."), listOf(EnemyDef("X", EnemyType.TURRET, 1, 0, Dir.N)))
        val s = run(l, "A" to "FORWARD ATTACK ATTACK")
        val r = s.step()
        assertTrue(r.events.any { it is SimEvent.Bumped })
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(3, s.state.tick)
    }

    @Test fun attackWithNoEnemyFails() {
        val s = run(lv(listOf(".."), listOf(EnemyDef("X", EnemyType.TURRET, 1, 0, Dir.N))), "A" to "TURN_RIGHT ATTACK")
        s.step()
        assertTrue(s.step().events.any { it is SimEvent.CommandFailed })
    }

    @Test fun warriorDealsDoubleDamage() {
        val l = lv(listOf(".."), listOf(EnemyDef("X", EnemyType.TURRET, 1, 0, Dir.N)), listOf(RobotDef("A", 0, 0, Dir.E, RobotClass.WARRIOR)))
        assertIs<Outcome.Win>(run(l, "A" to "ATTACK").runToEnd())
    }

    @Test fun turretShootsDownLineAndShieldBlocks() {
        val l = lv(listOf("....E"), listOf(EnemyDef("X", EnemyType.TURRET, 4, 0, Dir.W, range = 5)), obj = ObjectiveType.REACH_EXIT)
        val open = run(l, "A" to "WAIT WAIT WAIT WAIT")
        val o = open.runToEnd()
        assertEquals(FailReason.DESTROYED, (o as Outcome.Fail).reason)
        assertEquals(3, open.state.tick) // 3 can, her adım 1 hasar
        val sh = run(l, "A" to "SHIELD WAIT SHIELD WAIT")
        sh.step(); sh.step()
        assertEquals(3, sh.state.robot("A").hp)
    }

    @Test fun boxBlocksTurret() {
        val l = LevelData(lv(listOf(".B.."), listOf(EnemyDef("X", EnemyType.TURRET, 3, 0, Dir.W)), obj = ObjectiveType.REACH_EXIT).def)
        val s = run(l, "A" to "WAIT WAIT WAIT WAIT")
        s.runToEnd()
        assertEquals(3, s.state.robot("A").hp)
    }

    @Test fun patrolTurnsAtWallAndAttacksInFront() {
        val l = lv(listOf("#..#", "...."), listOf(EnemyDef("P", EnemyType.PATROL, 2, 0, Dir.E)), listOf(robot("A", 0, 1)), ObjectiveType.REACH_EXIT)
        val s = run(l, "A" to "WAIT WAIT WAIT")
        s.step(); assertEquals(Dir.W, s.state.enemies[0].dir); assertEquals(Pos(2, 0), s.state.enemies[0].pos)
        s.step(); assertEquals(Pos(1, 0), s.state.enemies[0].pos)
        val l2 = lv(listOf("...."), listOf(EnemyDef("P", EnemyType.PATROL, 2, 0, Dir.W)), obj = ObjectiveType.REACH_EXIT)
        val s2 = run(l2, "A" to "WAIT WAIT")
        s2.step(); s2.step()
        assertEquals(2, s2.state.robot("A").hp)
    }

    @Test fun chaserApproachesThenAttacks() {
        val l = lv(listOf("....."), listOf(EnemyDef("C", EnemyType.CHASER, 4, 0)), obj = ObjectiveType.REACH_EXIT)
        val s = run(l, "A" to "WAIT WAIT WAIT WAIT")
        s.step(); assertEquals(Pos(3, 0), s.state.enemies[0].pos)
        s.step(); s.step(); assertEquals(Pos(1, 0), s.state.enemies[0].pos)
        s.step(); assertEquals(2, s.state.robot("A").hp)
    }

    @Test fun killingEnemyBeforeItActsPreventsItsAttack() {
        val l = lv(listOf(".."), listOf(EnemyDef("C", EnemyType.CHASER, 1, 0, hp = 1)))
        val s = run(l, "A" to "ATTACK")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(3, s.state.robot("A").hp)
    }

    @Test fun laserDestroysEnemies() {
        val l = LevelData(lv(listOf("L...", "...."), listOf(EnemyDef("P", EnemyType.PATROL, 3, 1, Dir.N)), listOf(robot("A", 0, 1))).def)
        assertIs<Outcome.Win>(run(l, "A" to "WAIT").runToEnd())
    }

    @Test fun bossRotatesThenChasesInSecondPhase() {
        val l = lv(listOf(".....", "....."), listOf(EnemyDef("Z", EnemyType.BOSS, 2, 0, Dir.N, hp = 4)), listOf(RobotDef("A", 1, 0, Dir.E, RobotClass.WARRIOR)))
        val s = run(l, "A" to "WAIT ATTACK BACK WAIT ATTACK")
        s.step(); assertEquals(Dir.E, s.state.enemies[0].dir) // 1. aşama: döner
        s.step(); assertEquals(2, s.state.enemies[0].hp)      // savaş robotu 2 hasar → 2. aşama
        s.step()  // robot geri çekilir; patron kovalar
        assertEquals(Pos(1, 0), s.state.enemies[0].pos)
    }

    @Test fun combatIsDeterministic() {
        val l = lv(listOf("......", "......"), listOf(EnemyDef("C", EnemyType.CHASER, 5, 1), EnemyDef("P", EnemyType.PATROL, 3, 0, Dir.E)),
            listOf(robot("A", 0, 0), robot("B", 0, 1)))
        val p = arrayOf("A" to "FORWARD SHIELD FORWARD ATTACK ATTACK", "B" to "FORWARD FORWARD ATTACK ATTACK SHIELD")
        val a = run(l, *p); val b = run(l, *p)
        while (a.outcome == null) { a.step(); b.step(); assertEquals(a.state.signature(), b.state.signature()) }
    }
}

class RobotClassTest {
    private fun one(cls: com.emirzinciri.robotkomutan.logic.level.RobotClass, vararg map: String, enemies: List<EnemyDef> = emptyList(), obj: ObjectiveType = ObjectiveType.REACH_EXIT) =
        LevelData(level(*map).def.copy(robots = listOf(RobotDef("A", 0, 0, Dir.E, cls)), enemies = enemies, objectives = listOf(ObjectiveDef(obj))))

    @Test fun flyerCrossesVoidButCannotCarryOrPressPlates() {
        assertIs<Outcome.Win>(Simulation.create(one(RobotClass.FLYER, ". oE"), mapOf("A" to listOf("FORWARD", "FORWARD", "FORWARD"))).getOrThrow().runToEnd())
        val s = Simulation.create(one(RobotClass.FLYER, ".B."), mapOf("A" to listOf("PICK"))).getOrThrow()
        assertTrue(s.step().events.any { it is SimEvent.CommandFailed })
        val plate = Simulation.create(one(RobotClass.FLYER, ".pD"), mapOf("A" to listOf("FORWARD", "WAIT"))).getOrThrow()
        plate.step()
        assertTrue(Pos(2, 0) !in plate.state.openDoors)
    }

    @Test fun ghostIsIgnoredByEnemies() {
        val l = one(RobotClass.GHOST, ".....", enemies = listOf(EnemyDef("T", EnemyType.TURRET, 4, 0, Dir.W)))
        val s = Simulation.create(l, mapOf("A" to listOf("WAIT", "WAIT", "WAIT", "WAIT"))).getOrThrow()
        s.runToEnd()
        assertEquals(3, s.state.robot("A").hp)
    }

    @Test fun magnetPullsDistantBox() {
        val l = one(RobotClass.MAGNET, "...B", obj = ObjectiveType.REACH_EXIT)
        val s = Simulation.create(l, mapOf("A" to listOf("PULL", "PULL"))).getOrThrow()
        s.step(); assertEquals(setOf(Pos(2, 0)), s.state.boxes)
        s.step(); assertEquals(setOf(Pos(1, 0)), s.state.boxes)
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
    }
}
