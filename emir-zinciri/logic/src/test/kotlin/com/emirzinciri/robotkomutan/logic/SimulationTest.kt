package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.ObjectiveType
import com.emirzinciri.robotkomutan.logic.sim.FailReason
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.RobotStatus
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SimulationTest {

    private fun fail(o: Outcome?): Outcome.Fail = assertIs<Outcome.Fail>(o)

    // ------------------------------------------------------------- temel hareket

    @Test fun forwardReachesExit() {
        val s = sim(level("..E"), "A" to "FORWARD FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(2, s.state.tick)
    }

    @Test fun winsImmediatelyEvenWithCardsLeft() {
        val s = sim(level(".E"), "A" to "FORWARD FORWARD FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(1, s.state.tick)
    }

    @Test fun turnsChangeDirection() {
        val s = sim(level("..", ".E"), "A" to "TURN_RIGHT FORWARD TURN_LEFT FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(Dir.E, s.state.robot("A").dir)
    }

    @Test fun backMovesWithoutTurning() {
        val s = sim(level("E.."), robots(robot("A", 2, 0, Dir.E)), "A" to "BACK BACK")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(Dir.E, s.state.robot("A").dir)
    }

    @Test fun wallBumpKeepsRobotAndRecordsEvent() {
        val s = sim(level(".#E"), "A" to "FORWARD")
        val r = s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
        assertTrue(r.events.any { it is SimEvent.Bumped && it.pc == 0 })
        assertEquals(FailReason.PROGRAM_ENDED, fail(s.runToEnd()).reason)
        assertEquals(1, s.stats().bumps)
    }

    @Test fun fallingIntoVoidLosesRobot() {
        val s = sim(level(". E"), "A" to "FORWARD FORWARD")
        val o = fail(s.runToEnd())
        assertEquals(FailReason.ROBOT_LOST, o.reason)
        assertEquals(RobotStatus.LOST, s.state.robot("A").status)
    }

    @Test fun jumpCrossesPitButNotWall() {
        assertIs<Outcome.Win>(sim(level(".oE"), "A" to "JUMP").runToEnd())
        val s = sim(level(".#E"), "A" to "JUMP")
        s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
    }

    // ------------------------------------------------------------- kutular

    @Test fun pickAndDropBox() {
        val s = sim(level(".B.x"), "A" to "PICK FORWARD FORWARD DROP", objectives = ObjectiveType.BOXES_ON_TARGETS)
        assertIs<Outcome.Win>(s.runToEnd())
        assertTrue(Pos(3, 0) in s.state.boxes)
        assertFalse(s.state.robot("A").carrying)
    }

    @Test fun pickWithNothingInFrontFailsCommandButContinues() {
        val s = sim(level("..E"), "A" to "PICK FORWARD FORWARD")
        val r = s.step()
        assertTrue(r.events.any { it is SimEvent.CommandFailed && it.pc == 0 })
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun pushMovesBoxAndRobot() {
        val s = sim(level(".B.x"), "A" to "PUSH PUSH", objectives = ObjectiveType.BOXES_ON_TARGETS)
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(Pos(2, 0), s.state.robot("A").pos)
    }

    @Test fun pushBlockedByWallBehindBox() {
        val s = sim(level(".B#"), "A" to "PUSH")
        s.step()
        assertEquals(Pos(1, 0), s.state.boxes.single())
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
    }

    @Test fun forwardIntoBoxIsBlocked() {
        val s = sim(level(".B."), "A" to "FORWARD")
        val r = s.step()
        assertTrue(r.events.any { it is SimEvent.Bumped })
    }

    @Test fun boxFillsPit() {
        val s = sim(level(".BoE"), "A" to "PUSH FORWARD FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
        assertTrue(Pos(2, 0) in s.state.filledPits)
        assertTrue(s.state.boxes.isEmpty())
    }

    @Test fun pullDragsBox() {
        val s = sim(level("x.B"), robots(robot("A", 1, 0, Dir.E)), "A" to "PULL", objectives = ObjectiveType.BOXES_ON_TARGETS)
        s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
        assertEquals(setOf(Pos(1, 0)), s.state.boxes)
    }

    // ------------------------------------------------------------- kapılar

    @Test fun switchOpensDoorNextTick() {
        val l = level(" s  ", "..DE")
        val s = sim(l, robots(robot("A", 0, 1)), "A" to "FORWARD TURN_LEFT PRESS TURN_RIGHT FORWARD FORWARD")
        repeat(3) { s.step() }
        assertTrue(Pos(2, 1) in s.state.openDoors)
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun closedDoorBlocks() {
        val s = sim(level(".DE"), "A" to "FORWARD")
        val r = s.step()
        assertTrue(r.events.any { it is SimEvent.Bumped && it.reason.contains("Kapı") })
    }

    @Test fun plateHoldsDoorOnlyWhileOccupied() {
        // Robot plakaya basar, kapı açılır; plakadan inince kapı kapanır.
        val l = level("p.D")
        val s = sim(l, robots(robot("A", 1, 0, Dir.E)), "A" to "BACK FORWARD WAIT")
        s.step()
        assertTrue(Pos(2, 0) in s.state.openDoors)
        s.step()
        assertFalse(Pos(2, 0) in s.state.openDoors)
    }

    @Test fun boxOnPlateKeepsDoorOpen() {
        val s = sim(level(".Bp", "..DE"), "A" to "PUSH TURN_RIGHT FORWARD TURN_LEFT FORWARD FORWARD FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun invertedDoorClosesWhenChannelActive() {
        val l = level("s", ".", "d")
        val s = sim(l, robots(robot("A", 0, 1, Dir.N)), "A" to "PRESS")
        assertTrue(Pos(0, 2) in s.state.openDoors)
        s.step()
        assertFalse(Pos(0, 2) in s.state.openDoors)
    }

    @Test fun doorDoesNotCloseOnRobot() {
        // Robot plakadan kapıya geçer: plaka boşalsa da üzerinde robot olan kapı açık kalır.
        val l = level("pDE")
        val s = sim(l, "A" to "WAIT FORWARD WAIT FORWARD")
        s.step(); s.step()
        assertEquals(Pos(1, 0), s.state.robot("A").pos)
        assertTrue(Pos(1, 0) in s.state.openDoors)
        s.step()
        assertTrue(Pos(1, 0) in s.state.openDoors)
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun timerKeepsDoorOpenForDurationTicks() {
        // Basıldıktan sonraki 'süre' kadar adımda kapılardan geçilebilir.
        val prog = "PRESS TURN_RIGHT FORWARD FORWARD FORWARD"
        val start = robots(robot("A", 0, 1, Dir.N))
        val ok = sim(level("t...", ".DDE", values = mapOf("0,0" to 3)), start, "A" to prog)
        assertIs<Outcome.Win>(ok.runToEnd())
        val late = sim(level("t...", ".DDE", values = mapOf("0,0" to 2)), start, "A" to prog)
        assertIs<Outcome.Fail>(late.runToEnd())
        assertEquals(Pos(1, 1), late.state.robot("A").pos)
    }

    // ------------------------------------------------------------- enerji

    @Test fun runningOutOfEnergyStopsRobot() {
        val s = sim(level("...E"), robots(robot("A", 0, 0, energy = 2)), "A" to "FORWARD FORWARD FORWARD", energy = true)
        val o = fail(s.runToEnd())
        assertEquals(FailReason.NO_ENERGY, o.reason)
        assertEquals(2, o.pc)
        assertEquals(Pos(2, 0), s.state.robot("A").pos)
    }

    @Test fun collectCellAndCharger() {
        val s = sim(level(".c..E"), robots(robot("A", 0, 0, energy = 1, max = 4)), "A" to "FORWARD COLLECT FORWARD FORWARD FORWARD", energy = true)
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(1, s.state.robot("A").energy) // 0 + min(5, 4) - 3
        val q = sim(level("Q..E"), robots(robot("A", 0, 0, energy = 0, max = 3)), "A" to "COLLECT FORWARD FORWARD FORWARD", energy = true)
        assertIs<Outcome.Win>(q.runToEnd())
    }

    @Test fun transferPowersSocketAndOpensDoor() {
        val l = level("K  ", ".DE")
        val s = sim(l, robots(robot("A", 0, 1, Dir.N, energy = 6)), "A" to "TRANSFER TURN_RIGHT FORWARD FORWARD", energy = true)
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(1, s.state.robot("A").energy)
    }

    @Test fun energyIgnoredWhenDisabled() {
        val s = sim(level("....E"), robots(robot("A", 0, 0, energy = 0)), "A" to "FORWARD FORWARD FORWARD FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
    }

    // ------------------------------------------------------------- akış kartları

    @Test fun repeatRunsBodyNTimes() {
        val s = sim(level(".....E"), "A" to "REPEAT:5 FORWARD END")
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(5, s.state.tick)
    }

    @Test fun nestedRepeat() {
        val s = sim(level("..........", "         E"), robots(robot("A", 0, 0)), "A" to "REPEAT:3 REPEAT:3 FORWARD END END TURN_RIGHT FORWARD")
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun ifClearBranches() {
        // Önü boşsa ilerle; duvara gelince sağa dön.
        val s = sim(level("...#", "  .#", "  E#"), "A" to "REPEAT:4 IF_CLEAR FORWARD END IF_BLOCKED TURN_RIGHT FORWARD END END")
        assertIs<Outcome.Win>(s.runToEnd())
    }

    @Test fun stopEndsProgram() {
        val s = sim(level("...E"), "A" to "FORWARD STOP FORWARD FORWARD")
        assertEquals(FailReason.PROGRAM_ENDED, fail(s.runToEnd()).reason)
        assertEquals(Pos(1, 0), s.state.robot("A").pos)
    }

    @Test fun emptyInfiniteFlowLoopDoesNotHang() {
        // Gövdesi boş bir tekrar bloğu adım harcamaz; motor kilitlenmeden programı bitirir.
        val s = sim(level("..E"), "A" to "REPEAT:9 REPEAT:9 REPEAT:9 REPEAT:9 END END END END")
        assertIs<Outcome.Fail>(s.runToEnd())
    }

    @Test fun timeoutStopsLongPrograms() {
        val s = sim(level("..E", maxTicks = 20), "A" to "REPEAT:9 REPEAT:9 TURN_LEFT END END")
        assertEquals(FailReason.TIMEOUT, fail(s.runToEnd()).reason)
        assertEquals(20, s.state.tick)
    }

    // ------------------------------------------------------------- çoklu robot

    @Test fun twoRobotsSameTargetBothBlocked() {
        val l = level(". .", "...", robots = listOf(robot("A", 0, 0, Dir.S), robot("B", 2, 0, Dir.S)))
        val s = sim(l, "A" to "FORWARD TURN_LEFT FORWARD", "B" to "FORWARD TURN_RIGHT FORWARD")
        s.step(); s.step()
        val r = s.step()
        assertEquals(Pos(0, 1), s.state.robot("A").pos)
        assertEquals(Pos(2, 1), s.state.robot("B").pos)
        assertEquals(2, r.events.count { it is SimEvent.Bumped })
    }

    @Test fun robotsCannotSwap() {
        val l = level("..", robots = listOf(robot("A", 0, 0, Dir.E), robot("B", 1, 0, Dir.W)))
        val s = sim(l, "A" to "FORWARD", "B" to "FORWARD")
        s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
        assertEquals(Pos(1, 0), s.state.robot("B").pos)
    }

    @Test fun followingRobotMovesIntoVacatedCell() {
        // A, B'nin hemen arkasında: ikisi aynı adımda ilerleyebilir (zincir hareket).
        val l = level("...", robots = listOf(robot("A", 0, 0), robot("B", 1, 0)))
        val s = sim(l, "A" to "FORWARD", "B" to "FORWARD")
        s.step()
        assertEquals(Pos(1, 0), s.state.robot("A").pos)
        assertEquals(Pos(2, 0), s.state.robot("B").pos)
    }

    @Test fun robotBlockedByStationaryRobot() {
        val l = level("...", robots = listOf(robot("A", 0, 0), robot("B", 1, 0)))
        val s = sim(l, "A" to "FORWARD", "B" to "WAIT")
        s.step()
        assertEquals(Pos(0, 0), s.state.robot("A").pos)
    }

    @Test fun messagesSynchroniseRobots() {
        // A plakaya basıp mesaj gönderir; B mesajı bekleyip açılan kapıdan geçer.
        val base = level("p....", "..D.E", robots = listOf(robot("A", 1, 0, Dir.W), robot("B", 1, 1)))
        val l = com.emirzinciri.robotkomutan.logic.level.LevelData(
            base.def.copy(objectives = listOf(com.emirzinciri.robotkomutan.logic.level.ObjectiveDef(ObjectiveType.REACH_EXIT, listOf("B"))))
        )
        val s = sim(l, "A" to "FORWARD SEND", "B" to "WAIT_MSG FORWARD FORWARD FORWARD")
        s.step()
        assertTrue(s.state.robot("B").waitingForMessage)
        assertIs<Outcome.Win>(s.runToEnd())
        assertEquals(Pos(4, 1), s.state.robot("B").pos)
        // t1 A plakaya, t2 A mesaj gönderir, t3 B mesajı alır, t4-t6 B üç adım ilerler.
        assertEquals(6, s.state.tick)
    }

    @Test fun deadlockDetected() {
        val l = level("..E", robots = listOf(robot("A", 0, 0)))
        val s = sim(l, "A" to "WAIT_MSG FORWARD FORWARD")
        assertEquals(FailReason.DEADLOCK, fail(s.runToEnd()).reason)
    }

    @Test fun transferEnergyBetweenRobots() {
        val l = level("...E", robots = listOf(robot("A", 0, 0, energy = 5), robot("B", 1, 0, energy = 0, max = 5)))
        val s = sim(l, "A" to "TRANSFER", "B" to "WAIT FORWARD FORWARD", energy = true)
        s.step()
        assertEquals(3, s.state.robot("B").energy)
        assertEquals(2, s.state.robot("A").energy)
    }

    // ------------------------------------------------------------- determinizm ve geri sarma

    @Test fun sameInputSameResult() {
        val l = level(" s  K ", ".BD..DE", "....Q.", channels = mapOf("4,0" to 2, "5,1" to 2), energy = true)
        val prog = "PUSH TURN_LEFT PRESS TURN_RIGHT FORWARD FORWARD TURN_LEFT TRANSFER TURN_RIGHT FORWARD FORWARD"
        val a = sim(l, "A" to prog)
        val b = sim(l, "A" to prog)
        while (a.outcome == null) {
            a.step(); b.step()
            assertEquals(a.state.signature(), b.state.signature())
        }
        assertEquals(a.outcome, b.outcome)
    }

    @Test fun stepBackRestoresPreviousState() {
        val s = sim(level(".B.x"), "A" to "PUSH PUSH", objectives = ObjectiveType.BOXES_ON_TARGETS)
        val before = s.state.signature()
        s.step()
        val mid = s.state.signature()
        s.step()
        assertTrue(s.stepBack())
        assertEquals(mid, s.state.signature())
        assertTrue(s.stepBack())
        assertEquals(before, s.state.signature())
        assertFalse(s.stepBack())
        assertIs<Outcome.Win>(s.runToEnd())
    }

    // ------------------------------------------------------------- yardımcılar

    private fun robots(vararg r: com.emirzinciri.robotkomutan.logic.level.RobotDef) = r.toList()

    private fun level(vararg map: String, objectives: ObjectiveType) = level(*map, objectives = listOf(objectives))

    private fun sim(
        l: com.emirzinciri.robotkomutan.logic.level.LevelData,
        robots: List<com.emirzinciri.robotkomutan.logic.level.RobotDef>,
        vararg p: Pair<String, String>,
        energy: Boolean = false,
        objectives: ObjectiveType? = null,
    ) = sim(
        level(*l.def.map.toTypedArray(), robots = robots, energy = energy || l.def.energy,
            channels = l.def.channels, values = l.def.values,
            objectives = objectives?.let { listOf(it) } ?: l.def.objectives.map { it.type }),
        *p,
    )

    private fun sim(l: com.emirzinciri.robotkomutan.logic.level.LevelData, p: Pair<String, String>, objectives: ObjectiveType) =
        sim(level(*l.def.map.toTypedArray(), robots = l.def.robots, objectives = listOf(objectives)), p)

    private fun sim(l: com.emirzinciri.robotkomutan.logic.level.LevelData, p: Pair<String, String>, energy: Boolean) =
        sim(level(*l.def.map.toTypedArray(), robots = l.def.robots, energy = energy, channels = l.def.channels), p)

    private fun sim(l: com.emirzinciri.robotkomutan.logic.level.LevelData, a: Pair<String, String>, b: Pair<String, String>, energy: Boolean) =
        sim(level(*l.def.map.toTypedArray(), robots = l.def.robots, energy = energy), a, b)
}
