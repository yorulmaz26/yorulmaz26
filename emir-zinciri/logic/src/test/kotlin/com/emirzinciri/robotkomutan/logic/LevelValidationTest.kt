package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.content.Areas
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.level.LevelCatalog
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.progress.Stars
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.Simulation
import com.emirzinciri.robotkomutan.logic.sim.Solver
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Bütün bölümlerin otomatik doğrulaması: veri tutarlılığı, tasarım kuralları ve çözülebilirlik. */
class LevelValidationTest {

    private val catalog: LevelCatalog by lazy {
        val dir = File(System.getProperty("levels.dir") ?: "../assets/levels")
        val files = dir.listFiles { f -> f.name.matches(Regex("area_\\d{3}\\.json")) }!!.sortedBy { it.name }
        LevelCatalog.fromAreaFiles(files.map { it.readText() })
    }

    @Test fun idsAreContiguousAndAreasMatch() {
        assertTrue(catalog.count >= 25, "En az 25 bölüm olmalı")
        assertEquals((1..catalog.maxId).toList(), catalog.ids)
        for (d in catalog.all()) assertEquals(Areas.forLevel(d.id).id, d.area, "Bölüm ${d.id} yanlış alanda")
    }

    @TestFactory fun everyLevel(): List<DynamicTest> = catalog.all().map { d ->
        DynamicTest.dynamicTest("Bölüm ${d.id}: ${d.name}") { validate(d) }
    }

    private fun validate(d: LevelDef) {
        val level = LevelData(d)
        // Tasarım kuralı: robot sayısı sınırı (ilk 25 bölüm tek robot).
        assertTrue(d.robots.size in 1..Areas.maxRobotsFor(d.id), "Robot sayısı kuralı ihlal edildi")
        assertEquals(d.robots.size, d.robots.map { it.id }.toSet().size, "Robot kimlikleri benzersiz olmalı")
        for (r in d.robots) {
            val t = level.tile(Pos(r.x, r.y))
            assertTrue(t.isFloorLike || t.isDoor, "Robot ${r.id} zemin dışında başlıyor: $t")
            assertTrue(r.energy <= r.maxEnergy)
        }
        assertTrue(d.par in 1..d.maxCommands, "par kart sınırı içinde olmalı")
        // Referans çözüm yalnızca bölümde açık olan kartları kullanmalı ve sınırı aşmamalı.
        assertTrue(d.solution.isNotEmpty(), "Referans çözüm yok")
        for ((id, cards) in d.solution) {
            assertTrue(d.robots.any { it.id == id })
            val types = cards.map { Instr.decode(it).type }
            assertTrue(types.filter { it != CommandType.END }.all { it in d.commands }, "Çözüm kapalı kart kullanıyor")
            assertTrue(types.count { it != CommandType.END } <= d.maxCommands)
        }
        // Çözülebilirlik: referans çözüm kazanır ve üç yıldız alır.
        val sim = Simulation.create(level, d.solution).getOrThrow()
        val out = sim.runToEnd()
        assertIs<Outcome.Win>(out, "Referans çözüm kazanmıyor: $out")
        assertEquals(3, Stars.count(d, out), "Referans çözüm 3 yıldız almalı: ${Stars.evaluate(d, out)}")
        // Determinizm: aynı çözüm ikinci kez aynı sonucu verir.
        assertEquals(out, Simulation.create(level, d.solution).getOrThrow().runToEnd())
        // Hiç kart koymadan kazanılmamalı.
        assertIs<Outcome.Fail>(Simulation.create(level, emptyMap()).getOrThrow().runToEnd())
        // par, çözücünün bulduğu kesin optimum olmalı (yıldız 2 adil ve ulaşılabilir).
        val usesSignals = d.commands.any { it.isFlow || it == CommandType.SEND || it == CommandType.WAIT_MSG }
        if (!usesSignals) {
            val best = assertNotNull(Solver(level, maxNodes = 4_000_000).solve(d.par), "Çözücü çözüm bulamadı")
            assertEquals(d.par, best.cards.size, "par optimum değil")
        } else {
            // Sinyal kartlı bölümler: sinyal kullanmadan par'dan kısa çözüm olmamalı.
            assertEquals(null, Solver(level, maxNodes = 4_000_000).solve(d.par - 1), "Sinyalsiz daha kısa çözüm var")
        }
    }

    @Test fun commandsUnlockGradually() {
        // Kart seti alan alan büyür; yeni bölümler önceki kartları geri almaz (ilk 25 bölüm).
        var seen = emptySet<CommandType>()
        for (d in catalog.all().filter { it.id <= 25 }) {
            if (d.id % 5 == 1 && d.id > 1) assertTrue(d.commands.toSet().containsAll(seen), "Bölüm ${d.id} önceki kartları içermeli")
            seen = seen + d.commands
        }
        assertEquals(listOf(CommandType.FORWARD), catalog[1]!!.commands)
    }

    @Test fun secondRobotArrivesAtLevel26() {
        assertTrue(catalog.all().filter { it.id <= 25 }.all { it.robots.size == 1 })
        assertEquals(2, catalog[26]!!.robots.size)
    }
}
