package com.besaniyelik.dunya

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import com.besaniyelik.dunya.sim.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import kotlin.test.*

/** Basit test bölümü: uzun düz zemin, tavan ve isteğe bağlı aşamalar. */
fun flatLevel(
    phases: List<PhaseDef> = listOf(PhaseDef(0f, listOf(Mechanic.NORMAL))),
    extraSolids: List<SolidDef> = emptyList(),
    hazards: List<HazardDef> = emptyList(),
    pads: List<PadDef> = emptyList(),
    coins: List<Vec> = emptyList(),
    stars: List<Vec> = emptyList(),
    goal: Float = 400f,
    floorGaps: List<Pair<Float, Float>> = emptyList(),
): LevelData {
    val solids = ArrayList<SolidDef>()
    var x = -10f
    for ((a, b) in floorGaps.sortedBy { it.first }) { solids.add(SolidDef(x, -3f, a - x, 3f)); x = b }
    solids.add(SolidDef(x, -3f, goal + 20f - x, 3f))
    solids.add(SolidDef(-10f, Physics.CEIL, goal + 30f, 3f, SolidType.CEILING))
    solids.addAll(extraSolids)
    return LevelData(1, 1, 1, "Test", LevelKind.NORMAL, 1, "test", Vec(2f, 0.41f), goal, solids, hazards, pads, coins, stars, phases = phases)
}

class SimulationTest {
    companion object {
        @JvmStatic @BeforeAll
        fun natives() { GdxNativesLoader.load(); Box2D.init() }
    }

    private fun run(sim: Simulation, ticks: Int, input: (Int) -> Boolean = { false }) {
        repeat(ticks) { sim.step(input(sim.tick)) }
    }

    @DisplayName("karakter otomatik koşar ve zeminde durur")
    @Test fun karakter_otomatik_kosar_ve_zeminde_durur() {
        val sim = Simulation(flatLevel())
        run(sim, 120)
        assertTrue(sim.player.grounded)
        assertEquals(Physics.RUN_SPEED * 2f + 2f, sim.player.x, 0.4f)
        assertEquals(0.4f, sim.player.y, 0.05f)
        sim.dispose()
    }

    @DisplayName("dokunma zıplatır, basılı tutma daha yükseğe zıplatır")
    @Test fun dokunma_ziplatir_basili_tutma_daha_yuksege_ziplatir() {
        fun apex(hold: Int): Float {
            val sim = Simulation(flatLevel())
            run(sim, 30)
            var maxY = 0f
            val t0 = sim.tick
            run(sim, 70) { (it - t0) < hold }.also { }
            // ikinci geçiş: tepe noktasını ölç
            sim.dispose()
            val s2 = Simulation(flatLevel())
            run(s2, 30)
            val start = s2.tick
            repeat(70) { s2.step(s2.tick - start < hold); maxY = maxOf(maxY, s2.player.y) }
            s2.dispose()
            return maxY
        }
        val tap = apex(2)
        val full = apex(40)
        assertTrue(tap > 0.9f, "kısa zıplama en az ~0.5 karo yükselmeli: $tap")
        assertTrue(full > tap + 1.2f, "basılı tutma belirgin şekilde yükseğe çıkarmalı: tap=$tap full=$full")
        assertEquals(0.4f + Physics.JUMP_V * Physics.JUMP_V / (2 * Physics.GRAVITY), full, 0.25f)
    }

    @DisplayName("coyote time kenardan sonra kısa süre zıplamaya izin verir")
    @Test fun coyote_time_kenardan_sonra_kisa_sure_ziplamaya_izin_verir() {
        // Zemin x=10'da biter
        val lvl = flatLevel(floorGaps = listOf(10f to 30f))
        val sim = Simulation(lvl)
        // Kenarı geçene kadar koş
        while (sim.player.x - sim.player.hw < 10.02f) sim.step(false)
        run(sim, 3) // 3 tick havada
        assertFalse(sim.player.grounded)
        val vy0 = sim.player.vy
        sim.step(true)
        assertTrue(sim.player.vy > vy0 + 5f, "coyote süresinde zıplama gerçekleşmeli")
        sim.dispose()
    }

    @DisplayName("zıplama tamponu yere değmeden önceki dokunuşu hatırlar")
    @Test fun ziplama_tamponu_yere_degmeden_onceki_dokunusu_hatirlar() {
        val sim = Simulation(flatLevel())
        run(sim, 20)
        run(sim, 10) { true } // zıpla
        run(sim, 25) { false }
        // inişten hemen önce dokun
        while (sim.player.y > 0.75f && sim.player.vy < 0f || sim.player.vy > 0f) sim.step(false)
        sim.step(true) // havadayken bas
        var jumped = false
        repeat(10) { sim.step(true); if (sim.player.vy > 5f) jumped = true }
        assertTrue(jumped, "tampondaki dokunuş inişte zıplatmalı")
        sim.dispose()
    }

    @DisplayName("ters yer çekimi oyuncuyu tavana taşır")
    @Test fun ters_yer_cekimi_oyuncuyu_tavana_tasir() {
        val lvl = flatLevel(listOf(PhaseDef(0f, listOf(Mechanic.NORMAL)), PhaseDef(10f, listOf(Mechanic.REVERSE_GRAVITY))))
        val sim = Simulation(lvl)
        run(sim, 200)
        assertEquals(-1, sim.effects.gravitySign)
        assertTrue(sim.player.grounded)
        assertEquals(Physics.CEIL - 0.4f, sim.player.y, 0.08f)
        // Tavanda zıplama aşağı doğrudur
        sim.step(true); run(sim, 6) { true }
        assertTrue(sim.player.y < Physics.CEIL - 0.8f)
        sim.dispose()
    }

    @DisplayName("hızlı ve yavaş dünya zaman ölçeğini değiştirir")
    @Test fun hizli_ve_yavas_dunya_zaman_olcegini_degistirir() {
        fun distance(rule: Mechanic): Float {
            val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(rule)))))
            run(sim, 120)
            val d = sim.player.x
            sim.dispose(); return d
        }
        val n = distance(Mechanic.NORMAL); val f = distance(Mechanic.FAST); val s = distance(Mechanic.SLOW)
        assertTrue(f > n * 1.2f, "hızlı: $f normal: $n")
        assertTrue(s < n * 0.75f, "yavaş: $s normal: $n")
    }

    @DisplayName("buz dünyasında hız yavaşça artar")
    @Test fun buz_dunyasinda_hiz_yavasca_artar() {
        val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.ICE)))))
        run(sim, 10)
        val v0 = sim.player.vxCur
        run(sim, 60)
        val v1 = sim.player.vxCur
        assertTrue(v0 < Physics.RUN_SPEED * 1.1f)
        assertTrue(v1 > v0 + 1f, "buzda hızlanma olmalı: $v0 -> $v1")
        assertTrue(v1 <= Physics.RUN_SPEED * Physics.ICE_SPEED_MUL + 0.01f)
        sim.dispose()
    }

    @DisplayName("hayalet platformlar yanıp söner ve kaybolur")
    @Test fun hayalet_platformlar_yanip_soner_ve_kaybolur() {
        val ghost = SolidDef(50f, 3f, 3f, 0.5f, SolidType.GHOST)
        val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.GHOST))), extraSolids = listOf(ghost)))
        val g = sim.solids.first { it.def.type == SolidType.GHOST }
        val states = HashSet<Int>()
        var inactive = false
        repeat(400) { sim.step(false); states.add(g.ghostState); if (!g.active) inactive = true }
        assertTrue(inactive, "hayalet platform bir süre kaybolmalı")
        assertEquals(setOf(0, 1, 2), states)
        sim.dispose()
        // Kural yokken katı kalır
        val s2 = Simulation(flatLevel(extraSolids = listOf(ghost)))
        val g2 = s2.solids.first { it.def.type == SolidType.GHOST }
        repeat(400) { s2.step(false); assertTrue(g2.active) }
        s2.dispose()
    }

    @DisplayName("zıplayan dünya daha yükseğe zıplatır")
    @Test fun ziplayan_dunya_daha_yuksege_ziplatir() {
        fun apex(rule: Mechanic): Float {
            val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(rule)))))
            run(sim, 20)
            var m = 0f
            repeat(80) { sim.step(true); m = maxOf(m, sim.player.y) }
            sim.dispose(); return m
        }
        assertTrue(apex(Mechanic.BOUNCY) > apex(Mechanic.NORMAL) * 1.6f)
    }

    @DisplayName("küçük dünya karakteri küçültür ve dar geçitte büyüme ertelenir")
    @Test fun kucuk_dunya_karakteri_kucultur_ve_dar_gecitte_buyume_ertelenir() {
        // 0.62 yüksekliğinde tünel: x 20..40; küçük aşama 0..30, sonra normal
        val tunnel = SolidDef(20f, 0.62f, 20f, 3f, SolidType.LOW)
        val lvl = flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.SMALL)), PhaseDef(30f, listOf(Mechanic.NORMAL))), extraSolids = listOf(tunnel))
        val sim = Simulation(lvl)
        run(sim, 30)
        assertEquals(Physics.SMALL_SCALE, sim.player.scale, 0.001f)
        // tünel içinde kural biter ama büyüme olmaz
        while (sim.player.x < 35f && !sim.failed && sim.status == SimStatus.RUNNING) sim.step(false)
        assertEquals(SimStatus.RUNNING, sim.status, "tünelde kural değişimi öldürmemeli")
        assertEquals(Physics.SMALL_SCALE, sim.player.scale, 0.001f)
        while (sim.player.x < 44f) sim.step(false)
        assertEquals(1f, sim.player.scale, 0.001f, "tünelden çıkınca büyümeli")
        sim.dispose()
        // Normal boyutta tünele giren karakter takılır
        val s2 = Simulation(flatLevel(extraSolids = listOf(tunnel)))
        repeat(60 * 8) { if (s2.status == SimStatus.RUNNING) s2.step(false) }
        assertEquals(DeathCause.STUCK, s2.deathCause)
        s2.dispose()
    }

    @DisplayName("ters kontrolde karakter kendiliğinden zıplar, basılı tutunca yerde kalır")
    @Test fun ters_kontrolde_karakter_kendiliginden_ziplar_basili_tutunca_yerde_kalir() {
        val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.REVERSE_CONTROL)))))
        var maxY = 0f
        repeat(60) { sim.step(false); maxY = maxOf(maxY, sim.player.y) }
        assertTrue(maxY > 1.5f, "bırakınca otomatik zıplamalı")
        while (!sim.player.grounded) sim.step(true)
        var maxY2 = 0f
        repeat(60) { sim.step(true); maxY2 = maxOf(maxY2, sim.player.y) }
        assertTrue(maxY2 < 0.5f, "basılı tutunca yerde kalmalı: $maxY2")
        sim.dispose()
    }

    @DisplayName("değişen platformlar yalnızca kural etkinken kayar")
    @Test fun degisen_platformlar_yalnizca_kural_etkinken_kayar() {
        val sh = SolidDef(40f, 3f, 2f, 0.5f, SolidType.SHIFT, ay = 1f, period = 2f)
        val on = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.SHIFTING))), extraSolids = listOf(sh)))
        val off = Simulation(flatLevel(extraSolids = listOf(sh)))
        var dOn = 0f; var dOff = 0f
        repeat(300) {
            on.step(false); off.step(false)
            dOn = maxOf(dOn, kotlin.math.abs(on.solids.first { it.def.type == SolidType.SHIFT }.y - 3f))
            dOff = maxOf(dOff, kotlin.math.abs(off.solids.first { it.def.type == SolidType.SHIFT }.y - 3f))
        }
        assertTrue(dOn > 0.7f, "kayma olmalı: $dOn"); assertTrue(dOff < 0.01f)
        on.dispose(); off.dispose()
    }

    @DisplayName("Box2D çarpışmaları - platform üstüne iniş, duvara takılma, dikene çarpma")
    @Test fun Box2D_carpismalari_platform_ustune_inis_duvara_takilma_dikene_carpma() {
        val block = SolidDef(12f, 0f, 4f, 1.2f, SolidType.BLOCK)
        val sim = Simulation(flatLevel(extraSolids = listOf(block)))
        // basamağa çarpar (takılır), sonra zıplayıp üstüne çıkar
        while (sim.player.x < 11.4f) sim.step(false)
        repeat(10) { sim.step(false) }
        assertTrue(sim.player.x < 11.7f, "duvar ilerlemeyi durdurmalı")
        repeat(20) { sim.step(true) }
        repeat(30) { sim.step(false) }
        assertTrue(sim.player.grounded && sim.player.y > 1.5f, "bloğun üstüne inmeli (y=${sim.player.y})")
        sim.dispose()
        for ((h, expect) in listOf(HazardDef(HazardType.SPIKE, 10f, 0f, 2f, 1) to DeathCause.SPIKE, HazardDef(HazardType.SAW, 10f, 0.4f, 0.6f, 1) to DeathCause.SAW)) {
            val s2 = Simulation(flatLevel(hazards = listOf(h)))
            var cause: DeathCause? = null
            s2.listener = object : SimListener { override fun onDeath(c: DeathCause) { if (cause == null) cause = c } }
            repeat(200) { s2.step(false) }
            assertEquals(expect, cause)
            s2.dispose()
        }
    }

    @DisplayName("boşluğa düşmek öldürür, can varsa kontrol noktasından devam edilir")
    @Test fun bosluga_dusmek_oldurur_can_varsa_kontrol_noktasindan_devam_edilir() {
        val sim = Simulation(flatLevel(floorGaps = listOf(20f to 26f)).copy(checkpoints = listOf(8f)), SimConfig(hearts = 2))
        var died = false
        var respawned = false
        sim.listener = object : SimListener {
            override fun onDeath(cause: DeathCause) { died = true; assertEquals(DeathCause.FALL, cause) }
            override fun onRespawn() { respawned = true }
        }
        repeat(400) { sim.step(false) }
        assertTrue(died && respawned)
        assertEquals(1, sim.hearts)
        repeat(400) { sim.step(false) }
        assertEquals(SimStatus.GAME_OVER, sim.status)
        sim.dispose()
    }

    @DisplayName("kural değişimleri 5 saniyelik mesafede gerçekleşir ve önceden uyarılır")
    @Test fun kural_degisimleri_5_saniyelik_mesafede_gerceklesir_ve_onceden_uyarilir() {
        val e = RuleEffects.DEFAULT
        val len = Physics.RULE_PERIOD * Physics.realSpeed(e)
        val lenFast = Physics.RULE_PERIOD * Physics.realSpeed(RuleEffects.of(listOf(Mechanic.FAST)))
        val lvl = flatLevel(listOf(PhaseDef(0f, listOf(Mechanic.NORMAL)), PhaseDef(len, listOf(Mechanic.FAST)), PhaseDef(len + lenFast, listOf(Mechanic.ICE))), goal = 300f)
        val sim = Simulation(lvl)
        val changes = ArrayList<Int>(); val warnings = ArrayList<Int>()
        sim.listener = object : SimListener {
            override fun onRuleChange(prev: List<Mechanic>, next: List<Mechanic>) { changes.add(sim.tick) }
            override fun onRuleWarning(next: List<Mechanic>) { warnings.add(sim.tick) }
        }
        val startTicks = ((len - 2f) / Physics.RUN_SPEED * 60).toInt()
        repeat(60 * 12) { sim.step(false) }
        assertEquals(2, changes.size)
        assertEquals(2, warnings.size)
        // ilk değişim ~5 saniye (başlangıç x=2 olduğundan biraz önce)
        assertEquals(startTicks.toFloat(), changes[0].toFloat(), 20f)
        // ikinci aşama hızlı dünyada 5 gerçek saniye sürer
        assertEquals(5f * 60f, (changes[1] - changes[0]).toFloat(), 25f)
        assertTrue(warnings[0] < changes[0] && changes[0] - warnings[0] in 60..80)
        sim.dispose()
    }

    @DisplayName("mekanik kombinasyonları birlikte uygulanır")
    @Test fun mekanik_kombinasyonlari_birlikte_uygulanir() {
        val sim = Simulation(flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.REVERSE_GRAVITY, Mechanic.ICE, Mechanic.SMALL)))))
        repeat(150) { sim.step(false) }
        assertEquals(-1, sim.effects.gravitySign)
        assertTrue(sim.effects.ice)
        assertEquals(Physics.SMALL_SCALE, sim.player.scale, 0.001f)
        assertTrue(sim.player.grounded && sim.player.y > Physics.CEIL - 0.5f)
        assertTrue(sim.player.vxCur > Physics.RUN_SPEED * 1.1f)
        sim.dispose()
    }

    @DisplayName("uyumluluk matrisi çakışan kuralları engeller ve önceliğe göre çözer")
    @Test fun uyumluluk_matrisi_cakisan_kurallari_engeller_ve_oncelige_gore_cozer() {
        assertFalse(RuleCompatibility.isValid(listOf(Mechanic.FAST, Mechanic.SLOW)))
        assertFalse(RuleCompatibility.isValid(listOf(Mechanic.GHOST, Mechanic.SHIFTING)))
        assertFalse(RuleCompatibility.isValid(listOf(Mechanic.NORMAL, Mechanic.ICE)))
        assertTrue(RuleCompatibility.isValid(listOf(Mechanic.FAST, Mechanic.ICE, Mechanic.GHOST)))
        assertTrue(RuleCompatibility.isValid(listOf(Mechanic.REVERSE_GRAVITY, Mechanic.SLOW)))
        assertFalse(RuleCompatibility.isValid(listOf(Mechanic.REVERSE_GRAVITY, Mechanic.REVERSE_CONTROL), difficulty = 3))
        assertTrue(RuleCompatibility.isValid(listOf(Mechanic.REVERSE_GRAVITY, Mechanic.REVERSE_CONTROL), difficulty = 8))
        assertEquals(listOf(Mechanic.SLOW), RuleCompatibility.resolve(listOf(Mechanic.FAST, Mechanic.SLOW)))
        assertEquals(Physics.SLOW_SCALE, RuleEffects.of(listOf(Mechanic.FAST, Mechanic.SLOW)).timeScale)
        // bütün kombinasyonlar çözüldükten sonra geçerlidir
        val all = Mechanic.entries
        for (a in all) for (b in all) for (c in all) assertTrue(RuleCompatibility.isValid(RuleCompatibility.resolve(listOf(a, b, c))))
    }

    @DisplayName("anlık görüntü geri yükleme deterministiktir")
    @Test fun anlik_goruntu_geri_yukleme_deterministiktir() {
        val extra = listOf(
            SolidDef(20f, 1.5f, 3f, 0.6f, SolidType.MOVE, ax = 1.5f, period = 2.3f),
            SolidDef(30f, 2f, 3f, 0.6f, SolidType.GHOST),
            SolidDef(40f, 1.2f, 3f, 0.6f, SolidType.SHIFT, ay = 1f, period = 2.6f),
        )
        val lvl = flatLevel(listOf(PhaseDef(-100f, listOf(Mechanic.GHOST)), PhaseDef(35f, listOf(Mechanic.SHIFTING, Mechanic.ICE))), extraSolids = extra)
        val a = Simulation(lvl, SimConfig(solver = true, powerups = false))
        repeat(200) { a.step(it % 50 < 10) }
        assertEquals(SimStatus.RUNNING, a.status)
        val snap = a.snapshot()
        val trace1 = ArrayList<Float>()
        repeat(300) { a.step(it % 40 < 12); trace1.add(a.player.y) }
        a.restore(snap)
        val trace2 = ArrayList<Float>()
        repeat(300) { a.step(it % 40 < 12); trace2.add(a.player.y) }
        assertEquals(trace1, trace2)
        a.dispose()
    }

    @DisplayName("güçlendirmeler - kalkan, çift zıplama, ekstra can")
    @Test fun guclendirmeler_kalkan_cift_ziplama_ekstra_can() {
        val sim = Simulation(flatLevel(hazards = listOf(HazardDef(HazardType.SPIKE, 12f, 0f, 1f, 1))))
        sim.activatePowerup(PowerupType.SHIELD)
        repeat(200) { sim.step(false) }
        assertEquals(SimStatus.RUNNING, sim.status, "kalkan dikeni engellemeli")
        assertEquals(0, sim.shield)
        sim.activatePowerup(PowerupType.EXTRA_LIFE)
        assertEquals(4, sim.hearts)
        sim.activatePowerup(PowerupType.DOUBLE_JUMP)
        while (!sim.player.grounded) sim.step(false)
        repeat(8) { sim.step(true) }
        repeat(14) { sim.step(false) }
        val vy0 = sim.player.vy
        sim.step(true)
        assertTrue(sim.player.vy > vy0 + 5f, "havada ikinci zıplama")
        sim.dispose()
    }
}
