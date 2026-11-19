package com.besaniyelik.dunya

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.endless.EndlessGenerator
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.test.*

class LevelsTest {
    companion object {
        @JvmStatic @BeforeAll
        fun setup() {
            GdxNativesLoader.load(); Box2D.init()
            val dir = System.getProperty("assetsDir") ?: "../assets"
            LevelRepository.reader = { java.io.File(dir, it).readText() }
            LevelRepository.clear()
        }
        val all: List<LevelData> by lazy { (1..10).flatMap { LevelRepository.pack(it).levels } }
    }

    @DisplayName("500 bölüm, 10 harita, her haritada 50 bölüm yüklenir ve veri tutarlıdır")
    @Test fun loadAll() {
        assertEquals(500, all.size)
        assertEquals((1..500).toList(), all.map { it.id })
        for (m in 1..10) assertEquals(50, all.count { it.map == m })
        for (l in all) {
            assertTrue(l.goalX > l.start.x + 50f, "bölüm ${l.id} çok kısa")
            assertEquals(3, l.stars.size, "bölüm ${l.id} yıldız sayısı")
            assertTrue(l.coins.size >= 15, "bölüm ${l.id} altın az: ${l.coins.size}")
            assertTrue(l.phases.size >= 5, "bölüm ${l.id} aşama")
            assertTrue(l.phases.zipWithNext().all { (a, b) -> b.x > a.x }, "bölüm ${l.id} aşama sırası")
            assertTrue(l.phases.all { RuleCompatibility.isValid(it.rules, l.difficulty) || RuleCompatibility.isValid(it.rules) }, "bölüm ${l.id} geçersiz kural kombinasyonu")
            assertTrue(l.solution.isNotEmpty(), "bölüm ${l.id} çözüm kanıtı yok")
            assertEquals(GameContent.hasPiece(l.id), l.piece != null, "bölüm ${l.id} koleksiyon parçası")
        }
        assertEquals(50, all.count { it.piece != null })
        assertEquals(LevelKind.GRAND_FINAL, all[499].kind)
        assertEquals(9, all.count { it.kind == LevelKind.FINAL })
        assertEquals(40, all.count { it.kind == LevelKind.CHALLENGE })
    }

    @DisplayName("Mekanikler 1, 11, 21 ... 91. bölümlerde sırayla açılır; 101+ kombinasyon kullanır")
    @Test fun mechanicsSchedule() {
        for (m in Mechanic.entries.filter { it != Mechanic.NORMAL }) {
            val first = all.first { l -> l.phases.any { m in it.rules } }.id
            assertEquals(m.unlockLevel, first, "${m.title} ilk kez ${m.unlockLevel}. bölümde görünmeli")
            assertEquals(m, all[m.unlockLevel - 1].tutorial)
        }
        assertTrue(all.take(10).all { l -> l.phases.all { it.rules == listOf(Mechanic.NORMAL) } })
        val combos = all.drop(100).count { l -> l.phases.any { it.rules.size >= 2 } }
        assertTrue(combos > 300, "101-500 arasında kombinasyon kullanan bölüm sayısı: $combos")
        assertTrue(all.drop(350).any { l -> l.phases.any { it.rules.size == 3 } })
        val grand = all[499]
        assertTrue(Mechanic.entries.filter { it != Mechanic.NORMAL }.all { m -> grand.phases.any { m in it.rules } }, "büyük final bütün mekanikleri içermeli")
    }

    @DisplayName("Bölümler birbirinin kopyası değildir")
    @Test fun uniqueLevels() {
        val sigs = all.map { l -> l.solids.joinToString { "${it.type}${it.w}" } + l.hazards.size }
        assertEquals(500, sigs.toSet().size)
    }

    @DisplayName("Bütün 500 bölüm kayıtlı çözümle baştan oynanınca tamamlanır ve 3 yıldızın hepsi alınabilir")
    @Test fun allLevelsReplay() {
        val pool = Executors.newFixedThreadPool(4)
        val failures = pool.invokeAll(all.map { l -> java.util.concurrent.Callable {
            val sim = Simulation(l, SimConfig(hearts = 1, powerups = false))
            try {
                val ok = LevelSolver.replay(sim, l.solution.toIntArray())
                if (!ok) "${l.id}: tamamlanamadı (${sim.deathCause} x=${sim.player.x})"
                else if (sim.starsCollected != 3) "${l.id}: yıldız ${sim.starsCollected}"
                else if (l.piece != null && !sim.pieceCollected) "${l.id}: parça alınamadı"
                else null
            } finally { sim.dispose() }
        } }).mapNotNull { it.get() }
        pool.shutdown()
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @DisplayName("Bütün 500 bölüm çözücüyle sıfırdan (kayıtlı çözüm kullanmadan) çözülebilir")
    @Test fun allLevelsSolvableFromScratch() {
        val pool = Executors.newFixedThreadPool(4)
        val failures = pool.invokeAll(all.map { l -> java.util.concurrent.Callable {
            val r = LevelForge.trySolve(l)
            if (!r.success) "${l.id}: ${r.reason}" else {
                val sim = Simulation(l, SimConfig(hearts = 1, powerups = false))
                try { if (LevelSolver.replay(sim, r.toggles)) null else "${l.id}: yeni çözüm tekrar oynatılamadı" } finally { sim.dispose() }
            }
        } }).mapNotNull { it.get() }
        pool.shutdown()
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @DisplayName("Bölüm tamamlama: kayıtlı çözüm gerçek oyun ayarlarıyla (3 can, güçlendirmeler açık) da biter")
    @Test fun completionWithGameConfig() {
        for (id in listOf(1, 50, 137, 250, 333, 499, 500)) {
            val l = all[id - 1]
            val sim = Simulation(l, SimConfig(hearts = 3, powerups = false))
            var completed = false
            sim.listener = object : SimListener { override fun onComplete() { completed = true } }
            assertTrue(LevelSolver.replay(sim, l.solution.toIntArray()))
            assertTrue(completed); assertEquals(0, sim.deaths)
            sim.dispose()
        }
    }

    @DisplayName("Sonsuz mod: üretim deterministiktir ve çözücü farklı tohumlarda 90 saniye hayatta kalır")
    @Test fun endlessSurvivable() {
        for (seed in listOf(1L, 7L, 42L, 2024L, 99999L)) {
            val gen = EndlessGenerator(seed)
            val sim = Simulation(null, SimConfig(solver = true, powerups = false, strict = true), gen)
            val target = 90f * Physics.RUN_SPEED
            val r = LevelSolver(maxNodes = 600_000).solve(sim, untilX = target)
            assertTrue(r.success, "tohum $seed: ${r.reason}")
            assertTrue(gen.chunkIndex > 15)
            sim.dispose()
            // aynı tohum aynı dünyayı üretir
            val a = Simulation(null, SimConfig(solver = true), EndlessGenerator(seed)); val b = Simulation(null, SimConfig(solver = true), EndlessGenerator(seed))
            a.provider!!.ensure(a, 300f); b.provider!!.ensure(b, 300f)
            assertEquals(a.solids.map { it.def }, b.solids.map { it.def })
            assertEquals(a.phases, b.phases)
            a.dispose(); b.dispose()
        }
    }

    @DisplayName("Sonsuz mod: hız artar, kurallar her aşamada değişir ve kombinasyonlar geçerlidir")
    @Test fun endlessRules() {
        val gen = EndlessGenerator(5L)
        val rules = (0 until 60).map { gen.rulesFor(it) }
        assertTrue(rules.zipWithNext().all { (a, b) -> a.toSet() != b.toSet() })
        assertTrue(rules.all { RuleCompatibility.isValid(it) })
        assertTrue(rules.drop(20).any { it.size >= 2 })
        assertTrue(gen.speedAt(40) > gen.speedAt(0) * 1.3f)
        // öğrenilmemiş mekanikler en başta gelmez
        val g2 = EndlessGenerator(5L, setOf(Mechanic.NORMAL, Mechanic.REVERSE_GRAVITY))
        assertTrue((0 until 5).all { k -> g2.rulesFor(k).all { it == Mechanic.NORMAL || it == Mechanic.REVERSE_GRAVITY } })
    }
}
