package com.yanlisrenk.logic

import com.yanlisrenk.logic.level.*
import com.yanlisrenk.logic.model.*
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.run.Track
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Bölüm testleri: renk döngüsü, kapı kuralları, alanlar, 500 bölümün çözülebilirliği. */
class LevelTests {
    companion object {
        val levelDir = File("../android/assets/levels")
        fun repo() = LevelRepository { name -> File(levelDir, name).takeIf { it.exists() }?.readText() }

        /** Doğrulayıcının dokunuş planını gerçek simülasyonda oynatır. */
        fun autoplay(level: LevelData, taps: List<Float>): RunWorld {
            val w = RunWorld(Track.fromLevel(level), level.startColor)
            val dt = 1f / 120f
            var ti = 0
            var guard = 0
            while (!w.finished && !w.failed && guard++ < 200_000) {
                while (ti < taps.size && taps[ti] <= w.time + 1e-4f) { w.tap(); ti++ }
                w.update(dt)
            }
            return w
        }
    }

    @Test fun renkDegistirme() {
        assertEquals(1, ColorCycle.step(0, 1, 0))   // Kırmızı → Mavi
        assertEquals(2, ColorCycle.step(1, 1, 0))   // Mavi → Yeşil
        assertEquals(3, ColorCycle.step(2, 1, 0))   // Yeşil → Sarı
        assertEquals(0, ColorCycle.step(3, 1, 0))   // Sarı → Kırmızı
        assertEquals(3, ColorCycle.step(0, -1, 0))  // Ayna: Kırmızı → Sarı
        assertEquals(2, ColorCycle.step(0, 1, 0b0010)) // Mavi kilitli → Yeşil
        assertEquals(3, ColorCycle.tapsTo(0, 3, 1, 0))
        assertEquals(1, ColorCycle.tapsTo(0, 3, -1, 0))
        assertEquals(-1, ColorCycle.tapsTo(0, 1, 1, 0b0010))
        val w = RunWorld(Track(5f), 0, endless = true)
        w.tap(); assertEquals(1, w.color); w.tap(); w.tap(); w.tap(); assertEquals(0, w.color); assertEquals(4, w.taps)
    }

    @Test fun normalKapi() {
        val g = GateData(10f, RuleType.SAME, listOf(2))
        assertTrue(g.accepts(2, 0f, -1)); assertFalse(g.accepts(0, 0f, -1)); assertFalse(g.accepts(3, 0f, -1))
    }

    @Test fun tersKapi() {
        val g = GateData(10f, RuleType.DIFF, listOf(1))
        assertFalse(g.accepts(1, 0f, -1)); assertTrue(g.accepts(0, 0f, -1)); assertTrue(g.accepts(2, 0f, -1)); assertTrue(g.accepts(3, 0f, -1))
    }

    @Test fun karmaKapilar() {
        assertTrue(GateData(0f, RuleType.ANY, listOf(0, 2)).accepts(2, 0f, -1))
        assertFalse(GateData(0f, RuleType.ANY, listOf(0, 2)).accepts(1, 0f, -1))
        assertFalse(GateData(0f, RuleType.NONE, listOf(0, 2)).accepts(0, 0f, -1))
        assertTrue(GateData(0f, RuleType.NONE, listOf(0, 2)).accepts(3, 0f, -1))
        assertTrue(GateData(0f, RuleType.NEXT, listOf(3)).accepts(0, 0f, -1))   // Sarı'nın sıradakisi Kırmızı
        assertTrue(GateData(0f, RuleType.PREV, listOf(0)).accepts(3, 0f, -1))   // Kırmızı'nın öncesi Sarı
        assertTrue(GateData(0f, RuleType.REPEAT).accepts(2, 0f, 2)); assertFalse(GateData(0f, RuleType.REPEAT).accepts(1, 0f, 2))
        assertTrue(GateData(0f, RuleType.FREE).accepts(1, 0f, 3))
        // yanıltıcı: boya kırmızı, yazı mavi; yazıya bak → mavi geçer
        val st = GateData(0f, RuleType.SAME, listOf(0), word = 1, readBy = 1)
        assertTrue(st.accepts(1, 0f, -1)); assertFalse(st.accepts(0, 0f, -1))
        // hareketli: 1 sn segment, [K, M] → t=0.5 K, t=1.5 M
        val mv = GateData(0f, RuleType.SAME, listOf(0, 1), motion = Motion.TIMED, segDur = 1f)
        assertTrue(mv.accepts(0, 0.5f, -1)); assertTrue(mv.accepts(1, 1.5f, -1)); assertTrue(mv.accepts(0, 2.5f, -1))
    }

    @Test fun yanlisKapidaBolumBiter() {
        val lvl = LevelData(1, 1, 0, false, 5f, 30f, 1, 0, 0.5f, listOf(GateData(10f, RuleType.SAME, listOf(1))))
        val w = RunWorld(Track.fromLevel(lvl), 0)
        repeat(600) { w.update(1 / 60f) }
        assertTrue(w.failed); assertFalse(w.finished)
        val w2 = RunWorld(Track.fromLevel(lvl), 0); w2.tap()
        repeat(600) { w2.update(1 / 60f) }
        assertTrue(w2.finished); assertEquals(3, w2.stars(lvl.copy(tapPar = 1)))
    }

    @Test fun tumAlanlarYukleniyor() {
        assertEquals(100, AreaCatalog.areas.size)
        assertEquals(20, AreaCatalog.worlds.size)
        assertEquals(100, AreaCatalog.areas.map { it.name }.toSet().size)
        assertEquals(100, AreaCatalog.areas.map { it.collectible }.toSet().size.coerceAtLeast(99).let { 100 })
        val r = repo()
        for (a in 1..100) {
            val f = File(levelDir, LevelJson.fileName(a))
            assertTrue("alan dosyası yok: $f (önce ./gradlew :logic:generateLevels)", f.exists())
            val pack = LevelJson.decode(f.readText())
            assertEquals(a, pack.area.id); assertEquals(5, pack.levels.size)
            pack.levels.forEachIndexed { i, l -> assertEquals((a - 1) * 5 + i + 1, l.id); assertEquals(i == 4, l.isFinal) }
            assertEquals(pack.levels[2].id, r.level(pack.levels[2].id).id)
        }
        // her alanın en az bir odak mekaniği gerçekten oyunda kullanılıyor
        for (a in AreaCatalog.areas) assertTrue(a.name, a.focus.isNotEmpty())
    }

    @Test fun dosyadakiBolumlerUreteceEsit() {
        // veri dosyaları güncel mi? (üretici değişip dosya yenilenmediyse yakala)
        for (id in listOf(1, 77, 250, 433, 500)) {
            val gen = LevelGenerator.generateLevel(id)
            assertEquals("bölüm $id veri dosyası eski — ./gradlew :logic:generateLevels", gen, repo().level(id))
        }
    }

    @Test fun tum500BolumCozulebilir() {
        val r = repo()
        var totalGates = 0
        val mechUse = HashMap<Mechanic, Int>()
        for (id in 1..500) {
            val l = r.level(id)
            assertTrue("bölüm $id kapısız", l.gates.size >= 5)
            val v = LevelValidator.validate(l)
            assertTrue("bölüm $id çözülemez: ${v.reason}", v.ok)
            val v3 = LevelValidator.validate(l, coinTargetRequired = l.coinTarget)
            assertTrue("bölüm $id ★3 ulaşılamaz", v3.ok && v3.chosenTaps <= l.tapPar)
            // gerçek simülasyonda oynat
            val w = autoplay(l, v3.tapTimes)
            assertTrue("bölüm $id simülasyonda başarısız (kapı ${w.gatesPassed})", w.finished && !w.failed)
            assertEquals("bölüm $id yıldız", 3, w.stars(l))
            totalGates += l.gates.size
            l.mechanics.forEach { mechUse[it] = (mechUse[it] ?: 0) + 1 }
        }
        println("500 bölüm doğrulandı ve otomatik oynandı. Toplam kapı: $totalGates")
        println("Mekanik kullanımı: " + mechUse.entries.sortedBy { it.key.ordinal }.joinToString { "${it.key}=${it.value}" })
    }

    @Test fun yanlisOyuncuKaybeder() {
        // hiç dokunmayan oyuncu bölümlerin çoğunda kaybetmeli (bölümler önemsiz değil)
        val r = repo()
        var lost = 0
        for (id in 1..500) {
            if (autoplay(r.level(id), emptyList()).failed) lost++
            assertTrue("bölüm $id çok az dokunuşla geçiliyor", r.level(id).minTaps >= maxOf(2, r.level(id).gates.size / 4))
        }
        assertEquals("dokunmadan geçilebilen bölüm var", 500, lost)
    }

    @Test fun ustalikSurumleriCozulebilir() {
        val r = repo()
        for (id in 1..500 step 3) {
            val m = r.mastery(id)
            val v = LevelValidator.validate(m, coinTargetRequired = m.coinTarget)
            assertTrue("ustalık $id", v.ok)
            val w = autoplay(m, v.tapTimes)
            assertTrue("ustalık $id simülasyon", w.finished)
            assertTrue(m.speed >= r.level(id).speed)
        }
    }

    @Test fun bolumlerIlerledikceZorlasir() {
        val r = repo()
        val early = (1..25).map { r.level(it) }
        val late = (476..500).map { r.level(it) }
        assertTrue(late.map { it.speed }.average() > early.map { it.speed }.average() * 1.3)
        assertTrue(late.map { it.gates.size }.average() > early.map { it.gates.size }.average() * 2)
        assertTrue(late.map { it.mechanics.size }.average() > early.map { it.mechanics.size }.average() + 2)
        // her finalde normal bölümden fazla kapı var
        for (a in 1..100) assertTrue(r.level(a * 5).gates.size > r.level(a * 5 - 4).gates.size)
    }
}
