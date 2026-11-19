package com.kayipadalar.logic

import com.kayipadalar.logic.engine.*
import com.kayipadalar.logic.level.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** 500 bölümün tamamının çözülebilirliğini ve çeşitliliğini doğrular. */
class AllLevelsTest {
    private val levels: List<LevelDef> by lazy {
        (1..AreaCatalog.AREAS).flatMap { a ->
            val f = File(TestAssets.dir, "levels/area_%03d.json".format(a))
            assertTrue("Eksik alan dosyası: ${f.name}", f.exists())
            TestAssets.area(a).levels
        }
    }

    @Test fun has500LevelsIn100Areas() {
        assertEquals(500, levels.size)
        assertEquals((1..500).toList(), levels.map { it.id })
        assertEquals(100, AreaCatalog.AREAS)
        assertEquals(100, (1..100).map { AreaCatalog.area(it).name }.toSet().size)
    }

    @Test fun everyLevelIsSolvableWithStoredSolution() {
        val failures = ArrayList<String>()
        for (lv in levels) {
            if (lv.solution.isEmpty()) { failures.add("${lv.id}: çözüm yok"); continue }
            val b = Solver.replay(lv, TestAssets.config, lv.solution)
            if (b.status != Status.WON) failures.add("${lv.id}: ${b.status} ${b.loseReason}")
            else if (b.movesUsed > lv.moveLimit) failures.add("${lv.id}: sınır aşıldı")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun levelsAreNotCopies() {
        val sigs = levels.map { lv -> lv.islands.joinToString(";") { "${it.type}@${it.x},${it.y}:${it.ports}" } + lv.objectives.map { it.type } }
        assertEquals(500, sigs.toSet().size)
    }

    @Test fun bossCadence() {
        for (lv in levels) {
            if (lv.id % 25 == 0) assertNotNull("${lv.id}. bölümde patron olmalı", lv.boss)
        }
        val world = levels.filter { it.id % 50 == 0 }
        assertTrue(world.all { (it.boss?.phases?.size ?: 0) >= 3 })
    }

    @Test fun mechanicsAreReused() {
        // Her mekanik tanıtıldıktan sonra en az 10 bölümde yeniden kullanılmalı.
        for (m in listOf("ROTATE", "SWAP", "RAISE", "JUMP", "UNIT_MOVE")) {
            val first = levels.firstOrNull { m in it.mechanics }?.id ?: continue
            val later = levels.count { it.id > first + 5 && m in it.mechanics }
            assertTrue("$m yalnızca $later bölümde tekrar kullanıldı", later >= 10)
        }
        for (t in listOf("FIRE", "ICE", "PORTAL", "GRAVITY", "POISON", "VOLCANO", "LIFE", "ANCIENT", "TRADE", "TIME")) {
            val first = levels.firstOrNull { lv -> lv.islands.any { it.type == t } }?.id ?: fail("$t hiç kullanılmadı")
            val later = levels.count { lv -> lv.id > (first as Int) + 5 && lv.islands.any { it.type == t } }
            assertTrue("$t yalnızca $later bölümde tekrar kullanıldı", later >= 5)
        }
    }

    @Test fun objectiveVarietyAtLeast15() {
        val types = levels.flatMap { it.objectives.map { o -> o.type } }.toSet()
        assertTrue("Hedef türü sayısı: ${types.size} $types", types.size >= 15)
    }
}
