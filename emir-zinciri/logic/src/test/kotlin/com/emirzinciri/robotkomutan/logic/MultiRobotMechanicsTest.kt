package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.sim.Environment
import com.emirzinciri.robotkomutan.logic.sim.FailReason
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiRobotMechanicsTest {

    // ------------------------------------------------------------- bantlar

    @Test fun conveyorCarriesWaitingRobot() {
        val s = sim(level(">>.E"), "A" to "WAIT WAIT FORWARD")
        s.step(); assertEquals(Pos(1, 0), s.state.robot("A").pos)
        s.step(); assertEquals(Pos(2, 0), s.state.robot("A").pos)
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun robotMovingOntoConveyorIsNotCarriedSameTick() {
        val s = sim(level(".>.."), "A" to "FORWARD WAIT")
        s.step(); assertEquals(Pos(1, 0), s.state.robot("A").pos)
        s.step(); assertEquals(Pos(2, 0), s.state.robot("A").pos)
    }

    @Test fun conveyorBlockedByWallAndDropsIntoVoid() {
        val blocked = sim(level(">#"), "A" to "WAIT")
        blocked.step(); assertEquals(Pos(0, 0), blocked.state.robot("A").pos)
        val fall = sim(level("> "), "A" to "WAIT WAIT")
        assertEquals(FailReason.ROBOT_LOST, (fall.runToEnd() as Outcome.Fail).reason)
    }

    @Test fun conveyorMovesBoxesAndRespectsCollisions() {
        // İki kutu art arda bantta: ikisi birlikte ilerler.
        val l = level(".>>>.", robots = listOf(robot("A", 0, 0)))
        val ld = LevelData(l.def.copy(boxes = listOf("1,0", "2,0")))
        val s = Simulation.create(ld, mapOf("A" to listOf("WAIT"))).getOrThrow()
        s.step()
        assertEquals(setOf(Pos(2, 0), Pos(3, 0)), s.state.boxes)
    }

    @Test fun twoConveyorsIntoSameCellBlockBoth() {
        val l = level(">.<", robots = listOf(robot("A", 0, 0), robot("B", 2, 0)))
        val s = sim(l, "A" to "WAIT", "B" to "WAIT")
        s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
        assertEquals(Pos(2, 0), s.state.robot("B").pos)
    }

    // ------------------------------------------------------------- lazerler

    @Test fun laserKillsRobotInBeam() {
        // Lazer (0,0) doğuya ateş eder; robot (1,1)'den kuzeye ışına girer.
        val l = level("L..", ".E.", robots = listOf(robot("A", 1, 1, Dir.N)))
        val s = sim(l, "A" to "FORWARD")
        val o = s.runToEnd()
        assertEquals(FailReason.LASER, (o as Outcome.Fail).reason)
    }

    @Test fun boxBlocksBeamAndSwitchDisablesLaser() {
        val withBox = LevelData(level("L.B.", robots = listOf(robot("A", 3, 0))).def.copy())
        val beams = Environment.beams(withBox, com.emirzinciri.robotkomutan.logic.sim.WorldState.initial(withBox, mapOf("A" to 0)))
        assertEquals(listOf(Pos(1, 0)), beams.values.single())
        // Anahtar lazerin kanalını etkinleştirince ışın kapanır ve geçilir.
        val l = level("s  ", ".L ", "...", "..E", robots = listOf(robot("A", 0, 1, Dir.N)),
            values = mapOf("1,1" to 2))
        val s = sim(l, "A" to "PRESS TURN_RIGHT TURN_RIGHT FORWARD TURN_LEFT FORWARD FORWARD TURN_RIGHT FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
    }

    // ------------------------------------------------------------- koordinasyon

    @Test fun andChannelNeedsAllPlates() {
        val base = level("p.p", "..D", "..E", robots = listOf(robot("A", 1, 0, Dir.W), robot("B", 1, 1, Dir.E)))
        val l = LevelData(base.def.copy(andChannels = listOf(1)))
        val oneOnly = Simulation.create(l, mapOf("A" to listOf("FORWARD"), "B" to listOf("WAIT"))).getOrThrow()
        oneOnly.step()
        assertTrue(Pos(2, 1) !in oneOnly.state.openDoors)
        // A sol plakaya, ikinci robot da sağ plakaya geçmeli: B kapıya bakarken ileri gidemez; bu yüzden ayrı bir kurulum.
        val l2 = LevelData(level("p.p", ".D.", ".E.", robots = listOf(robot("A", 1, 0, Dir.W), robot("B", 2, 1, Dir.N))).def.copy(andChannels = listOf(1)))
        val both = Simulation.create(l2, mapOf("A" to listOf("FORWARD"), "B" to listOf("FORWARD"))).getOrThrow()
        both.step()
        assertTrue(Pos(1, 1) in both.state.openDoors)
    }

    @Test fun syncExitRequiresSameArrivalTick() {
        val l = level("..E", "..E", robots = listOf(robot("A", 0, 0), robot("B", 0, 1)), objectives = listOf(ObjectiveType.SYNC_EXIT))
        assertIs<Outcome.Win>(sim(l, "A" to "FORWARD FORWARD", "B" to "FORWARD FORWARD").runToEnd())
        val late = sim(l, "A" to "FORWARD FORWARD", "B" to "WAIT FORWARD FORWARD").runToEnd()
        assertIs<Outcome.Fail>(late)
    }

    // ------------------------------------------------------------- çok robotlu çözücü

    @Test fun solverCoordinatesTwoRobots() {
        // A plakada durarak kapıyı açık tutmalı, B kapıdan geçmeli.
        val l = level("p.  ", "..DE", robots = listOf(robot("A", 1, 0, Dir.W), robot("B", 1, 1)),
            objectives = listOf(ObjectiveType.REACH_EXIT))
        val ld = LevelData(l.def.copy(objectives = listOf(com.emirzinciri.robotkomutan.logic.level.ObjectiveDef(ObjectiveType.REACH_EXIT, listOf("B"))),
            commands = listOf(CommandType.FORWARD, CommandType.WAIT, CommandType.TURN_LEFT)))
        val sol = assertNotNull(Solver(ld).solve(10))
        assertEquals(listOf("FORWARD"), sol.programs["A"])
        assertEquals(listOf("WAIT", "FORWARD", "FORWARD"), sol.programs["B"])
        assertIs<Outcome.Win>(Simulation.create(ld, sol.programs).getOrThrow().runToEnd())
    }
}
