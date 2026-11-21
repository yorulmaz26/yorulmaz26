package com.besaniyelik.dunya.tools

import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.utils.GdxNativesLoader
import com.besaniyelik.dunya.level.*
import java.io.File
import java.util.Random

/** Teşhis: bir bölümün denemelerinde, verilen desende dar pencereli ilk adayı bulur ve şerit çizimi için yazar. */
object ProbeAttempt {
    @JvmStatic
    fun main(args: Array<String>) {
        GdxNativesLoader.load(); Box2D.init()
        val id = args[0].toInt(); val pattern = args[1]; val out = File(args[2]); val limit = args.getOrElse(3) { "9" }.toInt()
        File(out, "levels").mkdirs(); File(out, "dump").mkdirs()
        for (attempt in 0 until 40) {
            val draft = LevelGenerator.generate(id, attempt, 0f)
            val res = LevelForge.trySolve(draft.data, 160_000)
            if (!res.success) continue
            val tg = LevelAnalyzer.humanize(draft.data, res.toggles)
            val traj = LevelForge.trace(draft.data, tg) ?: continue
            val lv = LevelForge.placeItems(draft, traj, tg, Random(draft.data.seed + 99))
            val m = LevelAnalyzer.measure(lv, tg, trials = 16) ?: continue
            val hit = (0 until m.jumps).firstOrNull { j ->
                val f = lv.features.lastOrNull { it.x <= m.jumpX[j] + 0.5f }
                f?.p?.name == pattern && (m.windows[j] < limit || m.holdWindows[j] < 6)
            } ?: continue
            println("deneme $attempt: x=${m.jumpX[hit]} pencere=${m.windows[hit]} tutma=${m.holdWindows[hit]} ölçülen=${m.score}")
            println(LevelAnalyzer.debug(lv, tg))
            File(out, "levels/map_%02d.json".format(lv.map)).writeText(LevelRepository.json.encodeToString(MapPack.serializer(), MapPack(lv.map, listOf(lv))))
            LevelAnalyzeTool.dumpLevel(File(out, "dump/lv_%03d.json".format(id)), lv, tg, m)
            return
        }
        println("bulunamadı")
    }
}
