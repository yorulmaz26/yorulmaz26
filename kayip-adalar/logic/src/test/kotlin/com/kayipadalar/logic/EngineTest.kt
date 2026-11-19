package com.kayipadalar.logic

import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.*
import com.kayipadalar.logic.model.*
import org.junit.Assert.*
import org.junit.Test

/** Ana mekaniklerin birim testleri: her test küçük, elle kurulmuş bir bölüm kullanır. */
class EngineTest {
    private val cfg = TestAssets.config

    private fun level(
        islands: List<IslandDef>, objectives: List<ObjectiveDef> = listOf(ObjectiveDef(ObjectiveType.CONNECT_ALL)),
        mechanics: List<String> = listOf("SLIDE", "ROTATE", "WAIT", "UNIT_MOVE", "RAISE", "JUMP", "SWAP"),
        units: List<UnitDef> = emptyList(), w: Int = 5, h: Int = 5, limit: Int = 20, features: List<CellFeature> = emptyList(),
        ai: String = "AGGRESSIVE", boss: BossDef? = null, energy: Int = 0,
    ) = LevelDef(999, 0, "test", w, h, limit, 1, 2, mechanics, islands, units, features = features, objectives = objectives, ai = ai, boss = boss, startEnergy = energy)

    private fun castle(x: Int = 0, y: Int = 0, ports: String = "E") = IslandDef("c", "CASTLE", x, y, ports, "PLAYER", fixed = true)

    @Test fun slideFormsBridgeAndWins() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 2, 0, "W"))), cfg)
        assertEquals(Status.PLAYING, b.status)
        val r = Rules.apply(b, Action.Slide("f", Dir.W))
        assertTrue(r.ok)
        assertTrue(r.events.any { it is GameEvent.BridgeFormed })
        assertEquals(Status.WON, b.status)
        assertEquals(3, Rules.stars(b))
    }

    @Test fun slideBlockedByIslandAndBounds() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 1, 0, "S"))), cfg)
        assertEquals("error.blocked", Rules.validate(b, Action.Slide("f", Dir.W))) // kale dolu
        assertEquals("error.blocked", Rules.validate(b, Action.Slide("f", Dir.N))) // harita dışı
        assertEquals("error.notMovable", Rules.validate(b, Action.Slide("c", Dir.S))) // kale sabit
    }

    @Test fun rotationAlignsPorts() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 1, 0, "N"))), cfg)
        assertEquals(Status.PLAYING, b.status)
        Rules.apply(b, Action.Rotate("f")) // N -> E
        assertEquals(Status.PLAYING, b.status)
        Rules.apply(b, Action.Rotate("f")) // E -> S
        Rules.apply(b, Action.Rotate("f")) // S -> W : kaleye bakar
        assertEquals(Status.WON, b.status)
    }

    @Test fun heightDifferenceBlocksBridge() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 1, 0, "W", height = 2)), energy = 3), cfg)
        assertEquals(Status.PLAYING, b.status)
        Rules.apply(b, Action.Raise("f", false))
        assertEquals(Status.WON, b.status)
        assertEquals(2, b.energy)
    }

    @Test fun steamComboTriggersOnce() {
        val isl = listOf(castle(ports = "E"), IslandDef("w", "WATER", 1, 0, "WE"), IslandDef("fi", "FIRE", 3, 0, "W"))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.MAKE_COMBO, combo = "STEAM"))), cfg)
        val r = Rules.apply(b, Action.Slide("fi", Dir.W))
        assertTrue(r.events.any { it is GameEvent.Combo && it.comboId == "STEAM" })
        assertEquals(Status.WON, b.status)
    }

    @Test fun forestBurnsNextToFireWithoutWater() {
        val isl = listOf(castle(ports = "E"), IslandDef("fo", "FOREST", 1, 0, "WE"), IslandDef("fi", "FIRE", 3, 0, "W"), IslandDef("x", "MINE", 4, 4, ""))
        val b = Board.create(level(isl), cfg)
        val r = Rules.apply(b, Action.Slide("fi", Dir.W))
        assertTrue(r.events.any { it is GameEvent.Burned })
        assertEquals(IslandType.BURNT, b.island("fo")!!.type)
    }

    @Test fun frozenIslandCannotMoveUntilThawed() {
        val isl = listOf(castle(ports = "E"), IslandDef("i", "MINE", 2, 0, "WS", frozen = 99), IslandDef("fi", "FIRE", 1, 1, "E"))
        val b = Board.create(level(isl), cfg)
        assertEquals("error.notMovable", Rules.validate(b, Action.Slide("i", Dir.W)))
        Rules.apply(b, Action.Slide("fi", Dir.E)) // ateş (2,1): donmuş adanın hemen güneyi
        Rules.apply(b, Action.Rotate("fi"))     // E -> S
        Rules.apply(b, Action.Rotate("fi"))     // S -> W
        Rules.apply(b, Action.Rotate("fi"))     // W -> N : i ile köprü kurulur ve buz erir
        assertEquals(0, b.island("i")!!.frozen)
        assertNull(Rules.validate(b, Action.Slide("i", Dir.W)))
    }

    @Test fun portalsLinkOnlyWhenPowered() {
        val isl = listOf(castle(ports = "E"), IslandDef("p1", "PORTAL", 2, 0, "W"), IslandDef("p2", "PORTAL", 4, 4, "N"), IslandDef("v", "VILLAGE", 4, 2, "S"))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.RESCUE))), cfg)
        Rules.apply(b, Action.Slide("p1", Dir.W)) // p1 kaleye bağlandı ama p2'nin köprüsü yok
        assertFalse(b.island("v")!!.collected)
        Rules.apply(b, Action.Slide("v", Dir.S)) // köy p2'nin kuzeyine iner: p2 enerjilenir, portal zinciri köyü bağlar
        assertTrue(b.island("v")!!.collected)
        assertEquals(Status.WON, b.status)
    }

    @Test fun windPushesIsland() {
        val isl = listOf(castle(ports = "E"), IslandDef("f", "FOREST", 1, 3, "W"))
        val b = Board.create(level(isl, features = listOf(CellFeature(1, 2, "WIND", "N"), CellFeature(1, 1, "WIND", "N"))), cfg)
        Rules.apply(b, Action.Slide("f", Dir.N))
        assertEquals(Cell(1, 0), b.island("f")!!.pos)
        assertEquals(Status.WON, b.status) // rüzgâr adayı kalenin yanına taşıdı
    }

    @Test fun gravityPullsIslands() {
        val isl = listOf(castle(ports = "S"), IslandDef("g", "GRAVITY", 4, 4, ""), IslandDef("m", "MINE", 4, 1, ""), IslandDef("x", "FARM", 0, 3, ""))
        val b = Board.create(level(isl), cfg)
        Rules.apply(b, Action.Slide("x", Dir.E))
        assertEquals(Cell(4, 2), b.island("m")!!.pos) // 3 kare uzaktan bir adım çekildi
    }

    @Test fun crumblingExitLoses() {
        val isl = listOf(castle(ports = "E"), IslandDef("e", "EXIT", 3, 3, "W", crumble = 2), IslandDef("a", "FARM", 4, 4, ""))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.ESCAPE))), cfg)
        Rules.apply(b, Action.Wait); Rules.apply(b, Action.Wait)
        assertEquals(Status.LOST, b.status)
        assertEquals(LoseReason.EXIT_FELL, b.loseReason)
    }

    @Test fun aggressiveEnemyMarchesAndHitsCastle() {
        val isl = listOf(castle(ports = "E"), IslandDef("a", "FARM", 1, 0, "WE"), IslandDef("b", "FARM", 2, 0, "WE"), IslandDef("f", "ENEMY_FORT", 3, 0, "W", "ENEMY", fixed = true))
        val units = listOf(UnitDef("e0", "SWORD", "ENEMY", "f"))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.SURVIVE, amount = 10)), units = units), cfg)
        val hp0 = b.castleHp
        repeat(4) { Rules.apply(b, Action.Wait) }
        assertTrue("Düşman kaleye ulaşıp vurmalı", b.castleHp < hp0)
    }

    @Test fun cuttingBridgeProtectsCastle() {
        val isl = listOf(castle(ports = "E"), IslandDef("a", "FARM", 1, 0, "WE"), IslandDef("b", "FARM", 2, 0, "WE"), IslandDef("f", "ENEMY_FORT", 3, 0, "W", "ENEMY", fixed = true))
        val units = listOf(UnitDef("e0", "SWORD", "ENEMY", "f"))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.TRAP_ENEMY)), units = units), cfg)
        Rules.apply(b, Action.Slide("a", Dir.S)) // köprüyü kopar
        assertEquals(Status.WON, b.status)
    }

    @Test fun playerUnitCapturesFort() {
        val isl = listOf(castle(ports = "E"), IslandDef("a", "MILITARY", 1, 0, "WE"), IslandDef("f", "ENEMY_FORT", 2, 0, "W", "ENEMY", fixed = true))
        val units = listOf(UnitDef("u0", "SWORD", "PLAYER", "a"), UnitDef("e0", "SWORD", "ENEMY", "f", hpBonus = -4))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.CAPTURE, target = "f")), units = units), cfg)
        var guard = 0
        while (b.status == Status.PLAYING && guard++ < 10) {
            val e = b.unit("e0")!!
            if (e.alive) Rules.apply(b, Action.MoveUnit("u0", "f")) else Rules.apply(b, Action.MoveUnit("u0", "f"))
        }
        assertEquals(Status.WON, b.status)
        assertEquals(Owner.PLAYER, b.island("f")!!.owner)
    }

    @Test fun bossTakesSealDamage() {
        val isl = listOf(castle(ports = "E"), IslandDef("s1", "CRYSTAL", 2, 0, "W"), IslandDef("bf", "ENEMY_FORT", 4, 4, "", "ENEMY", fixed = true))
        val boss = BossDef("boss", "Test", every = 2, phases = listOf(BossPhase(100, listOf("LOCK"))), sealDamage = 4)
        val units = listOf(UnitDef("boss", "BOSS", "ENEMY", "bf", hpBonus = 4 - 20))
        val b = Board.create(level(isl, listOf(ObjectiveDef(ObjectiveType.DEFEAT_BOSS)), units = units, boss = boss), cfg)
        Rules.apply(b, Action.Slide("s1", Dir.W))
        assertFalse(b.bossUnit!!.alive)
        assertEquals(Status.WON, b.status)
    }

    @Test fun tripleResonanceGivesMoves() {
        val isl = listOf(castle(ports = "E"), IslandDef("a", "FOREST", 1, 0, "WE"), IslandDef("b", "FOREST", 2, 0, "WE"), IslandDef("c2", "FOREST", 4, 0, "W"), IslandDef("z", "MINE", 4, 4, ""))
        val b = Board.create(level(isl), cfg)
        val limit = b.moveLimit
        val r = Rules.apply(b, Action.Slide("c2", Dir.W))
        assertTrue(r.events.any { it is GameEvent.TripleCombo })
        assertEquals(limit + cfg.rules.tripleBonusMoves, b.moveLimit)
    }

    @Test fun outOfMovesLoses() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 3, 3, "N")), limit = 1), cfg)
        Rules.apply(b, Action.Slide("f", Dir.W))
        assertEquals(Status.LOST, b.status)
        assertEquals(LoseReason.OUT_OF_MOVES, b.loseReason)
    }

    @Test fun mergedIslandsSlideTogetherAndSplit() {
        val isl = listOf(castle(ports = "E"), IslandDef("a", "FARM", 3, 2, "W"), IslandDef("b", "FOREST", 4, 2, "W"), IslandDef("z", "MINE", 0, 4, ""))
        val b = Board.create(level(isl, mechanics = listOf("SLIDE", "MERGE", "SWAP"), energy = 5), cfg)
        assertTrue(Rules.apply(b, Action.Merge("a", "b")).ok)
        assertEquals(b.island("a")!!.group, b.island("b")!!.group)
        assertEquals("error.merged", Rules.validate(b, Action.Swap("a", "b")))
        Rules.apply(b, Action.Slide("a", Dir.N))
        assertEquals(Cell(3, 1), b.island("a")!!.pos)
        assertEquals(Cell(4, 1), b.island("b")!!.pos) // grup birlikte kaydı
        assertEquals("error.blocked", Rules.validate(b, Action.Slide("b", Dir.E))) // grubun bir üyesi harita dışına çıkamaz
        Rules.apply(b, Action.Split("b"))
        assertEquals(0, b.island("a")!!.group)
        assertEquals(0, b.island("b")!!.group)
        assertTrue(Rules.legalActions(b).none { it is Action.Split })
    }

    @Test fun actionEncodingRoundTrips() {
        val list = listOf(Action.Merge("a", "b"), Action.Split("a"), Action.Slide("a", Dir.N), Action.Rotate("b"), Action.Swap("a", "b"), Action.Raise("x", true), Action.Raise("x", false), Action.Jump("j", Dir.W), Action.MoveUnit("u", "i"), Action.Wait)
        for (a in list) assertEquals(a, Action.decode(a.encode()))
    }

    @Test fun boardCopyIsIndependent() {
        val b = Board.create(level(listOf(castle(), IslandDef("f", "FOREST", 2, 0, "W"))), cfg)
        val c = b.copy()
        Rules.apply(c, Action.Slide("f", Dir.W))
        assertEquals(Cell(2, 0), b.island("f")!!.pos)
        assertEquals(Status.PLAYING, b.status)
    }
}
