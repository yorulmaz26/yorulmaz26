package com.besaniyelik.dunya

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.endless.EndlessGenerator
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.*

/** Denetimde bulunan mantık hatalarının düzeltmelerini kilitleyen testler. */
class LogicFixesTest {
    companion object {
        @JvmStatic @BeforeAll
        fun natives() { GdxNativesLoader.load(); Box2D.init() }
    }

    private fun run(sim: Simulation, ticks: Int, input: (Int) -> Boolean = { false }) {
        repeat(ticks) { sim.step(input(sim.tick)) }
    }

    private fun runUntilX(sim: Simulation, x: Float, input: (Int) -> Boolean = { false }) {
        var guard = 0
        while (sim.player.x < x && sim.status == SimStatus.RUNNING && guard++ < 20000) sim.step(input(sim.tick))
    }

    @DisplayName("aynı kurallı ardışık aşamalar kural değişimi sayılmaz (uyarı, ses ve sayaç tetiklenmez)")
    @Test fun noPhantomRuleChange() {
        val normal = listOf(Mechanic.NORMAL)
        val sim = Simulation(flatLevel(listOf(PhaseDef(0f, normal), PhaseDef(20f, normal), PhaseDef(40f, listOf(Mechanic.REVERSE_GRAVITY)))))
        var changes = 0; var warnings = 0
        sim.listener = object : SimListener {
            override fun onRuleChange(prev: List<Mechanic>, next: List<Mechanic>) { changes++ }
            override fun onRuleWarning(next: List<Mechanic>) { warnings++ }
        }
        // sayaç, 20'deki sahte sınırı değil 40'taki gerçek değişimi gösterir
        assertEquals((40f - 2f) / Physics.RUN_SPEED, sim.secondsToNextRule(), 0.2f)
        runUntilX(sim, 30f)
        assertEquals(0, changes); assertEquals(0, warnings); assertEquals(0, sim.rulesChanged)
        runUntilX(sim, 45f)
        assertEquals(1, changes); assertEquals(1, warnings); assertEquals(1, sim.rulesChanged)
        sim.dispose()
    }

    @DisplayName("yükselen platformun üstünde zıplama girişi çalışır")
    @Test fun jumpOnRisingPlatform() {
        // geniş, dikey hareketli platform: t=0'da en yüksek hızla (3,1 karo/sn) yukarı çıkıyor
        val platform = SolidDef(6f, 0f, 80f, 0.7f, SolidType.MOVE, 0f, 1.5f, 3f, 0f)
        val lvl = flatLevel(extraSolids = listOf(platform)).copy(start = Vec(10f, 0.7f + Physics.PLAYER_H / 2f + 0.02f))
        val sim = Simulation(lvl)
        run(sim, 8)
        assertTrue(sim.player.grounded, "yükselen platformda yerde sayılmalı")
        val before = sim.player.lastJumpTick
        run(sim, 3) { true }
        assertTrue(sim.player.lastJumpTick > before, "yükselen platformda zıplama başlamalı")
        sim.dispose()
    }

    @DisplayName("bayrak havadayken de kaydedilir ve yeniden doğuş bayrağın dibinde, yerde olur")
    @Test fun checkpointCanonicalPose() {
        val sim = Simulation(flatLevel().copy(checkpoints = listOf(22f)), SimConfig(hearts = 3))
        // bayrağın üstünden zıplayarak geç
        runUntilX(sim, 19.4f)
        while (sim.player.x < 22.05f) sim.step(true)
        assertFalse(sim.player.grounded, "test kurulumu: bayrak havada geçilmeli")
        assertEquals(22f, sim.lastCheckpointX, "bayrak geçildiği anda kaydedilmeli")
        runUntilX(sim, 40f)
        sim.kill(DeathCause.SPIKE)
        var guard = 0
        while (sim.status != SimStatus.RUNNING && guard++ < 200) sim.step(false)
        assertEquals(SimStatus.RUNNING, sim.status)
        assertEquals(22f, sim.player.x, 0.3f)
        assertEquals(Physics.PLAYER_H / 2f, sim.player.y, 0.05f)
        assertEquals(0f, sim.player.blocked)
        sim.dispose()
    }

    @DisplayName("yeniden doğuşta geri sarılan kural değişimleri yeniden sayılmaz")
    @Test fun respawnDoesNotRecountRules() {
        val lvl = flatLevel(listOf(PhaseDef(0f, listOf(Mechanic.NORMAL)), PhaseDef(30f, listOf(Mechanic.SLOW)), PhaseDef(50f, listOf(Mechanic.NORMAL))))
            .copy(checkpoints = listOf(10f))
        val sim = Simulation(lvl, SimConfig(hearts = 3))
        runUntilX(sim, 55f)
        assertEquals(2, sim.rulesChanged)
        sim.kill(DeathCause.SPIKE)
        var guard = 0
        while (sim.status != SimStatus.RUNNING && guard++ < 200) sim.step(false)
        assertEquals(0, sim.rulesChanged, "kontrol noktasına dönünce sayaç da geri dönmeli")
        runUntilX(sim, 55f)
        assertEquals(2, sim.rulesChanged)
        sim.dispose()
    }

    @DisplayName("Sonsuz Dünya: ekstra can koşuyu başa sarmaz, olduğu yerde diriltir")
    @Test fun endlessExtraLifeRevivesInPlace() {
        val sim = Simulation(null, SimConfig(hearts = 1), EndlessGenerator(7L))
        sim.activatePowerup(PowerupType.EXTRA_LIFE)
        var deathX = -1f; var deathTick = -1
        sim.listener = object : SimListener { override fun onDeath(cause: DeathCause) { deathX = sim.player.x; deathTick = sim.tick } }
        var guard = 0
        while (deathX < 0f && guard++ < 6000) sim.step(false)
        assertTrue(deathX > 8f, "test kurulumu: girişsiz oyuncu bir engelde ölmeli (x=$deathX)")
        val distance = sim.distance
        guard = 0
        while (sim.status != SimStatus.RUNNING && guard++ < 200) sim.step(false)
        assertEquals(SimStatus.RUNNING, sim.status)
        assertTrue(sim.player.x > deathX - 10f && sim.player.x <= deathX + 0.5f, "dirilme ölüm yerinin yakınında olmalı: ${sim.player.x} / $deathX")
        assertTrue(sim.tick > deathTick, "zaman geri sarılmamalı")
        assertTrue(sim.distance >= distance - 0.01f, "mesafe azalmamalı")
        sim.dispose()
    }

    @DisplayName("küçülme, yer çekimi dönüşüyle aynı anda bitince karakter hemen normal boya döner")
    @Test fun growWhenGravityFlips() {
        val sim = Simulation(flatLevel(listOf(PhaseDef(0f, listOf(Mechanic.SMALL)), PhaseDef(20f, listOf(Mechanic.REVERSE_GRAVITY)))))
        runUntilX(sim, 19f)
        assertEquals(Physics.SMALL_SCALE, sim.player.scale, 0.001f)
        runUntilX(sim, 20.01f)
        run(sim, 3)
        assertEquals(1f, sim.player.scale, 0.001f)
        sim.dispose()
    }

    @DisplayName("bitiş: son kural ters yer çekimi olsa bile karakter kapıya Normal Dünya'da ve yerde varır")
    @Test fun finishInNormalWorld() {
        val lvl = flatLevel(listOf(PhaseDef(0f, listOf(Mechanic.NORMAL)), PhaseDef(15f, listOf(Mechanic.REVERSE_GRAVITY, Mechanic.SMALL)),
            PhaseDef(40f, listOf(Mechanic.NORMAL))), goal = 40f + Physics.FINISH_LEN)
        val sim = Simulation(lvl)
        run(sim, 3000)
        assertEquals(SimStatus.COMPLETE, sim.status)
        assertEquals(RuleEffects.DEFAULT, sim.effects)
        assertEquals(1f, sim.player.scale, 0.001f)
        assertEquals(Physics.PLAYER_H / 2f, sim.player.y, 0.05f)
        sim.dispose()
    }

    @DisplayName("kapıdan zıplayarak geçen karakter bitişten sonra yere iner (havada donmaz)")
    @Test fun settleAfterGoal() {
        val sim = Simulation(flatLevel(goal = 30f))
        runUntilX(sim, 27.8f)
        while (sim.status == SimStatus.RUNNING) sim.step(true)
        assertEquals(SimStatus.COMPLETE, sim.status)
        assertTrue(sim.player.y > Physics.PLAYER_H / 2f + 0.5f, "test kurulumu: kapı havada geçilmeli")
        run(sim, 90)
        assertEquals(Physics.PLAYER_H / 2f, sim.player.y, 0.02f)
        sim.dispose()
    }

    @DisplayName("oyuncuya kilitli testere her varışta aynı konumdadır (varış anından bağımsız)")
    @Test fun syncSawIndependentOfArrivalTime() {
        val def = HazardDef(HazardType.SAW, 40f, 2.3f, 0.85f, 1, 0f, 1.3f, 2.8f, 1.2f, sync = true)
        val a = HazardEntity(def, 0); val b = HazardEntity(def, 1)
        a.update(1.0f, 37f); b.update(9.7f, 37f)
        assertEquals(a.cy, b.cy, 1e-5f)
        b.update(9.7f, 35f)
        assertTrue(abs(a.cy - b.cy) > 0.05f, "konumla birlikte hareket etmeli")
        // zamana bağlı (eski tip) testere ise zamanla değişir
        val old = HazardEntity(def.copy(sync = false), 2)
        old.update(1.0f, 37f); val y1 = old.cy; old.update(1.7f, 37f)
        assertTrue(abs(y1 - old.cy) > 0.05f)
    }

    @DisplayName("kalkan kurtarması oyuncuyu düştüğü kenarın 2-6 karo gerisine koyar ve sonsuz döngüye girmez")
    @Test fun shieldRescueNearEdge() {
        val sim = Simulation(flatLevel(floorGaps = listOf(30f to 60f)), SimConfig(hearts = 2))
        sim.activatePowerup(PowerupType.SHIELD)
        var saves = 0; var rescueX = -1f
        sim.listener = object : SimListener { override fun onShieldSave() { saves++; rescueX = sim.player.x } }
        var guard = 0
        while (saves == 0 && guard++ < 2000) sim.step(false)
        assertEquals(1, saves)
        assertTrue(rescueX in 23f..28.6f, "kurtarma noktası kenarın 2-6 karo gerisinde olmalı: $rescueX")
        // girişsiz oyuncu yeniden düşer; dokunulmazlık bittiğinde normal biçimde ölür (sonsuz kurtarma yok)
        guard = 0
        while (sim.deaths == 0 && guard++ < 1200) sim.step(false)
        assertEquals(1, sim.deaths, "kalkan tek sefer korur")
        sim.dispose()
    }

    @DisplayName("buzda hız pedi: hız 2 katına çıkmaz ve pedden sonra çabuk söner")
    @Test fun boostOnIceDecays() {
        val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.ICE))), pads = listOf(PadDef(PadType.BOOST, 20f, 0f, 2f, 1))))
        var maxV = 0f
        runUntilX(sim, 19f)
        repeat(60) { sim.step(false); maxV = maxOf(maxV, sim.player.vxCur) }
        assertTrue(maxV <= Physics.RUN_SPEED * Physics.BOOST_MUL + 0.05f, "hız pedi buzla çarpılmamalı: $maxV")
        run(sim, (Physics.BOOST_TIME * 60).toInt() + 30)
        assertTrue(sim.player.vxCur <= Physics.RUN_SPEED * Physics.ICE_SPEED_MUL + 0.3f, "pedden 0,5 sn sonra buz seyir hızına inmeli: ${sim.player.vxCur}")
        sim.dispose()
    }

    @DisplayName("Kaos Duvarı ekranda görünecek kadar yakından takip eder ve duran oyuncuyu yakalar")
    @Test fun chaserFollowsClosely() {
        val wall = SolidDef(60f, 0f, 3f, 6f, SolidType.BLOCK)
        val sim = Simulation(flatLevel(extraSolids = listOf(wall)).copy(chaser = true), SimConfig(hearts = 1))
        runUntilX(sim, 50f)
        assertTrue(sim.player.x - sim.chaserX <= Physics.CHASER_GAP + 0.01f)
        var cause: DeathCause? = null
        sim.listener = object : SimListener { override fun onDeath(c: DeathCause) { cause = c } }
        run(sim, 600)
        assertEquals(DeathCause.CHASER, cause, "duvara takılan oyuncuyu Kaos Duvarı yakalamalı (takılma ölümünden önce)")
        sim.dispose()
    }

    @DisplayName("Kaos Duvarı bölümünde kalkan kurtarması oyuncuyu duvarın içine koymaz")
    @Test fun shieldRescueWithChaser() {
        val sim = Simulation(flatLevel(floorGaps = listOf(30f to 60f)).copy(chaser = true), SimConfig(hearts = 2))
        sim.activatePowerup(PowerupType.SHIELD)
        var saves = 0; var cause: DeathCause? = null
        sim.listener = object : SimListener {
            override fun onShieldSave() { saves++ }
            override fun onDeath(c: DeathCause) { cause = c }
        }
        var guard = 0
        while (saves == 0 && guard++ < 2000) sim.step(false)
        assertEquals(1, saves)
        assertTrue(sim.player.x - sim.chaserX >= Physics.CHASER_GAP - 0.01f, "duvar kurtarılan oyuncunun gerisine çekilmeli")
        // kurtarıldıktan sonra zıplayan oyuncu en az 1 sn yaşar (duvar hemen yakalamaz)
        repeat(60) { sim.step(it < 20) }
        assertNotEquals(DeathCause.CHASER, cause, "kurtarılan oyuncu Kaos Duvarı'na yem olmamalı")
        sim.dispose()
    }

    @DisplayName("kalkan kurtarması aşılmış boşluğun gerisine dönmez: oyuncu son bastığı platformda kalır")
    @Test fun rescueStaysOnLastPlatform() {
        // zemin ..30 | boşluk 30-32 | platform 32-35,2 | boşluk 35,2-70
        val sim = Simulation(flatLevel(floorGaps = listOf(30f to 32f, 35.2f to 70f)), SimConfig(hearts = 2))
        sim.activatePowerup(PowerupType.SHIELD)
        val rescues = ArrayList<Float>()
        sim.listener = object : SimListener { override fun onShieldSave() { rescues += sim.player.x } }
        // ilk boşluğun üstünden kısa bir zıplamayla platforma in, sonra hiç basma
        runUntilX(sim, 29.3f)
        run(sim, 8) { true }
        var guard = 0
        while (rescues.isEmpty() && guard++ < 2000) sim.step(false)
        assertEquals(1, rescues.size, "kalkan bir kez kurtarmalı")
        assertTrue(rescues[0] >= 32f, "kurtarma noktası son basılan platformda olmalı (önceki zeminde değil): ${rescues[0]}")
        assertTrue(rescues[0] <= 34.9f, "kurtarma noktası platformun üstünde olmalı: ${rescues[0]}")
        // ikinci düşüşte (dokunulmazlık kurtarması) de aynı platforma döner, kenara değil
        val firstX = rescues[0]
        var minAfter = Float.MAX_VALUE; var lastX = sim.player.x; var teleports = 0
        guard = 0
        while (sim.deaths == 0 && guard++ < 1200) {
            sim.step(false)
            if (sim.player.x < lastX - 0.5f) { teleports++; minAfter = minOf(minAfter, sim.player.x) }
            lastX = sim.player.x
        }
        if (teleports > 0) assertTrue(minAfter >= 32f && minAfter <= firstX + 0.05f, "ikinci kurtarma da platformun gerisine olmalı: $minAfter")
        assertEquals(1, sim.deaths, "kalkan tek sefer korur")
        sim.dispose()
    }

    @DisplayName("canlar bitince devam: bölümde son kontrol noktasından, Sonsuz Dünya'da koşu geri sarılmadan sürer")
    @Test fun continueAfterGameOver() {
        // bölüm modu: 1 can, boşluğa düşüş -> oyun bitti -> 2 canla devam
        val lvl = flatLevel(floorGaps = listOf(30f to 60f))
        val sim = Simulation(lvl, SimConfig(hearts = 1))
        var guard = 0
        while (sim.status != SimStatus.GAME_OVER && guard++ < 4000) sim.step(false)
        assertEquals(SimStatus.GAME_OVER, sim.status)
        sim.continueAfterGameOver(2)
        assertEquals(SimStatus.RUNNING, sim.status); assertEquals(2, sim.hearts)
        assertTrue(sim.player.x < 30f, "boşluğun gerisinden devam etmeli: ${sim.player.x}")
        sim.continueAfterGameOver(5)
        assertEquals(2, sim.hearts, "oyun sürerken ikinci çağrı etkisiz olmalı")
        sim.dispose()
        // Sonsuz Dünya: ölüm -> oyun bitti -> devam: mesafe ve süre geri sarılmaz
        val endless = Simulation(null, SimConfig(hearts = 1), EndlessGenerator(7L))
        guard = 0
        while (endless.player.x < 12f && guard++ < 2000) endless.step(false)
        endless.kill(DeathCause.STUCK)
        guard = 0
        while (endless.status != SimStatus.GAME_OVER && guard++ < 600) endless.step(false)
        assertEquals(SimStatus.GAME_OVER, endless.status)
        val tick = endless.tick; val dist = endless.distance; val x = endless.player.x
        endless.continueAfterGameOver(1)
        assertEquals(SimStatus.RUNNING, endless.status); assertEquals(1, endless.hearts)
        assertTrue(endless.tick >= tick && endless.distance >= dist - 0.01f, "süre ve mesafe geri sarılmamalı")
        assertTrue(endless.player.x > x - 8f, "oyuncu dünyanın başına atılmamalı: ${endless.player.x} (ölüm x=$x)")
        var alive = 0
        while (alive < 60 && endless.status == SimStatus.RUNNING) { endless.step(false); alive++ }
        assertEquals(60, alive, "devam ettikten sonra en az 1 sn yaşamalı")
        endless.dispose()
    }

    @DisplayName("hedef zorluk eğrisi: harita içinde artar, yeni harita öncekinin sonundan kolay başlar, 1..10 aralığındadır")
    @Test fun targetCurveIsSawtooth() {
        for (id in 1..500) { val t = GameContent.targetDifficulty(id); assertTrue(t in 1f..10f, "bölüm $id hedef $t") }
        for (map in 0 until 10) {
            val first = map * 50 + 1; val last = map * 50 + 50
            val normals = (first..last).filter { GameContent.kindOf(it) == LevelKind.NORMAL }
            // aynı alan konumundaki bölümler harita boyunca zorlaşır
            for ((a, b) in normals.zip(normals.drop(5))) assertTrue(GameContent.targetDifficulty(b) > GameContent.targetDifficulty(a), "bölüm $a -> $b")
            if (map > 0) assertTrue(GameContent.targetDifficulty(first + 1) < GameContent.targetDifficulty(first - 2) - 0.3f, "harita ${map + 1} nefes alarak başlamalı")
            // meydan okuma bölümü, alanındaki normal bölümlerden zordur
            for (c in (first..last).filter { GameContent.kindOf(it) == LevelKind.CHALLENGE }) assertTrue(GameContent.targetDifficulty(c) > GameContent.targetDifficulty(c - 1))
        }
        assertTrue(GameContent.targetDifficulty(1) <= 1.05f)
        assertTrue(GameContent.targetDifficulty(500) >= GameContent.targetDifficulty(499))
    }
}
