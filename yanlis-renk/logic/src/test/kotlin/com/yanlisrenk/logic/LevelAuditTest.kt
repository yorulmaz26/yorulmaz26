package com.yanlisrenk.logic

import com.yanlisrenk.logic.level.AreaCatalog
import com.yanlisrenk.logic.level.LevelValidator
import com.yanlisrenk.logic.level.Timeline
import com.yanlisrenk.logic.model.LevelData
import com.yanlisrenk.logic.model.Mechanic
import com.yanlisrenk.logic.model.Motion
import com.yanlisrenk.logic.model.PadType
import com.yanlisrenk.logic.model.RuleType
import com.yanlisrenk.logic.model.ZoneType
import com.yanlisrenk.logic.run.RunWorld
import com.yanlisrenk.logic.run.Track
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bağımsız bölüm denetimi. Doğrulayıcının "oyuncu ne zaman bilir" varsayımını,
 * oyunu kare kare oynatıp ekranda gerçekten görünenle karşılaştırır ve mantık/görsel tutarlılık
 * kontrolleri yapar. Sonuçlar build/level_audit.md dosyasına yazılır.
 */
class LevelAuditTest {
    private val view = LevelValidator.VIEW_AHEAD
    private val dt = 1f / 240f

    private class Finding(val level: Int, val severity: String, val text: String)

    /** Kapının rengi (gereksinimi) bu anda ekranda okunabilir mi? */
    private fun readable(w: RunWorld, tr: Track, gi: Int): Boolean {
        val g = tr.gates[gi]
        val dist = g.y - w.y
        if (g.chain) {
            val sign = tr.signs.firstOrNull { gi in it.gates } ?: return false
            val sd = sign.y - w.y
            return sd <= minOf(view, w.visibleDistanceAt(sign.y)) || dist <= 0f
        }
        if (dist > minOf(view, w.visibleDistanceAt(g.y, g.revealDist))) return false
        if (g.flicker) {
            val ph = (w.time / LevelValidator.FLICKER_PERIOD + gi * 0.37f) % 1f
            if (ph >= 0.6f) return false
        }
        return true
    }

    private fun audit(l: LevelData, mastery: Boolean, out: MutableList<Finding>, stats: MutableMap<String, Float>) {
        val id = l.id
        val v = LevelValidator.validate(l, coinTargetRequired = l.coinTarget)
        if (!v.ok) { out.add(Finding(id, "HATA", "doğrulanamadı: ${v.reason}")); return }
        val tr = Track.fromLevel(l)
        val w = RunWorld(tr, l.startColor)
        val tl = Timeline.build(l.speed, l.zones)
        // her kapının ekranda ilk okunabildiği an (kare kare)
        val firstSeen = FloatArray(l.gates.size) { -1f }
        val taps = v.tapTimes
        var ti = 0
        // dokunuşun hedef kapısı: dokunuştan sonraki ilk kapı
        val tapTarget = IntArray(taps.size) { k -> l.gates.indices.first { tl.timeAt(l.gates[it].y) > taps[k] } }
        var guard = 0
        while (!w.finished && !w.failed && guard++ < 2_000_000) {
            for (gi in l.gates.indices) if (firstSeen[gi] < 0f && l.gates[gi].y >= w.y && readable(w, tr, gi)) firstSeen[gi] = w.time
            while (ti < taps.size && taps[ti] <= w.time + 1e-4f) {
                val g = tapTarget[ti]
                val seen = firstSeen[g]
                if (seen < 0f) out.add(Finding(id, "HATA", "kapı $g görünmeden dokunuş gerekiyor (t=${"%.2f".format(taps[ti])})"))
                else if (taps[ti] + 1e-3f < seen + l.reaction - 0.02f)
                    out.add(Finding(id, "UYARI", "kapı $g için tepki süresi kısa: görüldü ${"%.2f".format(seen)}, dokunuş ${"%.2f".format(taps[ti])} (gereken ${"%.2f".format(l.reaction)})"))
                w.tap(); ti++
            }
            w.update(dt)
        }
        if (!w.finished) out.add(Finding(id, "HATA", "çözüm oyunda bitmedi (kapı ${w.gatesPassed})"))
        else if (w.stars(l) < 3) out.add(Finding(id, "HATA", "3 yıldız alınamadı (${w.stars(l)})"))

        // görünür → varış süresi istatistiği
        var minLead = 99f
        for (gi in l.gates.indices) if (firstSeen[gi] >= 0f) minLead = minOf(minLead, tl.timeAt(l.gates[gi].y) - firstSeen[gi])
        stats["minLead"] = minOf(stats["minLead"] ?: 99f, minLead)
        // saniyedeki en yüksek dokunuş
        var peak = 0
        for (k in taps.indices) { var c = 0; for (j in k until taps.size) if (taps[j] - taps[k] < 1f) c++; peak = maxOf(peak, c) }
        stats["peakTaps"] = maxOf(stats["peakTaps"] ?: 0f, peak.toFloat())

        if (mastery) return
        // --- görsel / mantık tutarlılığı ---
        val ys = l.gates.map { it.y }
        for (k in 1 until ys.size) if (ys[k] - ys[k - 1] < 1.55f) out.add(Finding(id, "UYARI", "kapı ${k - 1} ve $k çok yakın (${"%.2f".format(ys[k] - ys[k - 1])} birim, görseller üst üste biner)"))
        for (p in l.pads) ys.forEach { gy -> if (kotlin.math.abs(p.y - gy) < 0.95f) out.add(Finding(id, "HATA", "ped bir kapıyla çakışıyor (y=${p.y})")) }
        for (s in l.signs) ys.forEach { gy -> if (s.y in gy - 1.2f..gy + 1.0f) out.add(Finding(id, "UYARI", "tabela bir kapıyla çakışıyor (y=${s.y})")) }
        for ((gi, g) in l.gates.withIndex()) {
            if (g.word >= 0 && g.word == g.colors.firstOrNull()) out.add(Finding(id, "HATA", "yanıltıcı kapı $gi: yazı ve boya aynı renk"))
            if (g.motion != Motion.STATIC && g.segDur > 0f) {
                val f = tl.timeAt(g.y) / g.segDur + g.phase
                val frac = f - kotlin.math.floor(f)
                val margin = minOf(frac, 1f - frac) * g.segDur
                if (margin < 0.2f) out.add(Finding(id, "HATA", "hareketli kapı $gi: varış renk değişimine ${"%.2f".format(margin)} sn yakın"))
            }
            if (g.rule == RuleType.REPEAT && gi == 0) out.add(Finding(id, "HATA", "ilk kapı yankı kapısı (önceki kapı yok)"))
        }
        if (l.coinTarget > l.maxCoins) out.add(Finding(id, "HATA", "altın hedefi ${l.coinTarget} > toplanabilir ${l.maxCoins}"))
        if (l.tapPar < l.minTaps) out.add(Finding(id, "HATA", "dokunuş sınırı ${l.tapPar} < en az ${l.minTaps}"))
        if (l.length <= ys.last()) out.add(Finding(id, "HATA", "bitiş çizgisi son kapıdan önce"))
        // giriş bölümünde "YENİ" diye tanıtılan mekanik gerçekten var mı?
        if (l.index == 0) {
            val area = AreaCatalog.area(l.area)
            for (m in area.introduces) if (!present(l, m)) out.add(Finding(id, "HATA", "giriş bölümü '${m.title}' tanıtıyor ama bölümde yok"))
        }
        // listelenen mekaniklerin bölümde bulunması (bilgi kartı doğruluğu)
        for (m in l.mechanics) if (!present(l, m) && m != Mechanic.SAME) out.add(Finding(id, "HATA", "'${m.title}' listede ama bu bölümde görünmüyor"))
    }

    private fun present(l: LevelData, m: Mechanic): Boolean = when (m) {
        Mechanic.SAME -> l.gates.any { it.rule == RuleType.SAME }
        Mechanic.DIFF -> l.gates.any { it.rule == RuleType.DIFF }
        Mechanic.RAINBOW -> l.gates.any { it.rule == RuleType.ANY && !it.split && it.colors.size >= 2 }
        Mechanic.MIX -> l.gates.any { it.split }
        Mechanic.NONE -> l.gates.any { it.rule == RuleType.NONE }
        Mechanic.NEXT -> l.gates.any { it.rule == RuleType.NEXT }
        Mechanic.PREV -> l.gates.any { it.rule == RuleType.PREV }
        Mechanic.REPEAT -> l.gates.any { it.rule == RuleType.REPEAT }
        Mechanic.DECOY -> l.gates.any { it.rule == RuleType.FREE }
        Mechanic.ROTATE -> l.gates.any { it.motion == Motion.ROTATE }
        Mechanic.SLIDE -> l.gates.any { it.motion == Motion.SLIDE }
        Mechanic.TIMED -> l.gates.any { it.motion == Motion.TIMED }
        Mechanic.MEMORY -> l.gates.any { it.memoryDist > 0f }
        Mechanic.HIDDEN -> l.gates.any { it.revealDist > 0f }
        Mechanic.FLICKER -> l.gates.any { it.flicker }
        Mechanic.STROOP -> l.gates.any { it.word >= 0 }
        Mechanic.CHAIN -> l.signs.isNotEmpty()
        Mechanic.AUTOSHIFT -> l.pads.isNotEmpty()
        Mechanic.SPEED, Mechanic.WAVE -> l.zones.any { it.type == ZoneType.SPEED }
        Mechanic.MIRROR -> l.zones.any { it.type == ZoneType.MIRROR }
        Mechanic.LOCK -> l.zones.any { it.type == ZoneType.LOCK }
        Mechanic.FOG -> l.zones.any { it.type == ZoneType.FOG }
        Mechanic.COINS -> l.pickups.count { it.color >= 0 } >= 3
        Mechanic.DOUBLE, Mechanic.TRIPLE, Mechanic.QUICK, Mechanic.MAZE, Mechanic.EXAM -> true
    }

    @Test fun tumBolumlerDenetim() {
        val repo = LevelTests.repo()
        val findings = ArrayList<Finding>()
        val stats = HashMap<String, Float>()
        val mstats = HashMap<String, Float>()
        val perLevelLead = ArrayList<Pair<Int, Float>>()
        for (id in 1..500) {
            val l = repo.level(id)
            val st = HashMap<String, Float>()
            audit(l, false, findings, st)
            perLevelLead.add(id to (st["minLead"] ?: 99f))
            st.forEach { (k, v) -> stats[k] = if (k == "minLead") minOf(stats[k] ?: 99f, v) else maxOf(stats[k] ?: 0f, v) }
            val m = repo.mastery(id)
            val ms = HashMap<String, Float>()
            audit(m, true, findings, ms)
            ms.forEach { (k, v) -> mstats[k] = if (k == "minLead") minOf(mstats[k] ?: 99f, v) else maxOf(mstats[k] ?: 0f, v) }
        }
        val errors = findings.filter { it.severity == "HATA" }
        val warns = findings.filter { it.severity == "UYARI" }
        val infos = findings.filter { it.severity == "BİLGİ" }
        val sb = StringBuilder()
        sb.append("# Bölüm Denetim Raporu\n\n")
        sb.append("500 bölüm + 500 ustalık sürümü, gerçek simülasyonda 240 Hz ile oynatıldı; her dokunuş, hedef kapının ekranda okunabildiği an ile karşılaştırıldı.\n\n")
        sb.append("| | Sayı |\n|---|---|\n| HATA | ${errors.size} |\n| UYARI | ${warns.size} |\n| BİLGİ | ${infos.size} |\n\n")
        sb.append("En kısa \"görünür → kapıya varış\" süresi: bölümler ${"%.2f".format(stats["minLead"])} sn, ustalık ${"%.2f".format(mstats["minLead"])} sn.  \n")
        sb.append("1 sn içindeki en fazla dokunuş: bölümler ${stats["peakTaps"]?.toInt()}, ustalık ${mstats["peakTaps"]?.toInt()}.\n\n")
        sb.append("En dar 10 bölüm (görünür → varış):\n\n")
        perLevelLead.sortedBy { it.second }.take(10).forEach { (id, t) -> sb.append("- Bölüm $id: ${"%.2f".format(t)} sn\n") }
        sb.append("\n## Bulgular\n\n")
        (errors + warns + infos).groupBy { it.severity }.forEach { (sev, list) ->
            sb.append("### $sev (${list.size})\n\n")
            list.take(200).forEach { sb.append("- Bölüm ${it.level}: ${it.text}\n") }
            if (list.size > 200) sb.append("- … ve ${list.size - 200} tane daha\n")
            sb.append("\n")
        }
        File("build").mkdirs()
        File("build/level_audit.md").writeText(sb.toString())
        println(sb.toString().take(4000))
        assertTrue("denetim hataları: " + errors.take(10).joinToString { "${it.level}: ${it.text}" }, errors.isEmpty())
    }
}
