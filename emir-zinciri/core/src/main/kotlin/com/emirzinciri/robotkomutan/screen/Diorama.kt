package com.emirzinciri.robotkomutan.screen

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.emirzinciri.robotkomutan.logic.grid.Dir
import com.emirzinciri.robotkomutan.logic.level.LevelData
import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.RobotDef
import com.emirzinciri.robotkomutan.logic.sim.WorldState
import com.emirzinciri.robotkomutan.render.IsoRenderer
import com.emirzinciri.robotkomutan.ui.Assets

/** Menüdeki küçük canlı sahne: robot kare bir yol üzerinde dolaşır. Gerçek çiziciyi kullanır. */
class Diorama(assets: Assets) : Widget() {
    private val renderer = IsoRenderer(assets)
    private val level = LevelData(
        LevelDef(
            id = 0, area = 1, name = "menu", map = listOf("#....", "..B.Q", ".x#..", "..c.E", "K...."),
            robots = listOf(RobotDef("A", 1, 1, Dir.E)), commands = emptyList(), maxCommands = 1, par = 1,
        )
    )
    private val path = listOf(1 to 1, 1 to 2, 1 to 3, 2 to 3, 3 to 3, 3 to 4, 4 to 4, 4 to 3, 4 to 2, 4 to 1, 3 to 1, 3 to 0, 2 to 0, 1 to 0)
    private var from: WorldState = WorldState.initial(level, mapOf("A" to 0))
    private var to: WorldState = from.copy()
    private var step = 0
    private var t = 0f
    private var time = 0f
    private var fitted = false

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        t += delta / 0.55f
        if (t >= 1f) {
            t = 0f
            from = to.copy()
            val r = to.robots.first()
            val (nx, ny) = path[(step + 1) % path.size]
            val dx = nx - r.pos.x; val dy = ny - r.pos.y
            val want = Dir.entries.first { it.dx == dx && it.dy == dy }
            if (want != r.dir) r.dir = if (r.dir.right() == want) r.dir.right() else r.dir.left()
            else { r.pos = r.pos + want; step = (step + 1) % path.size }
        }
    }

    override fun getPrefHeight() = 360f
    override fun getPrefWidth() = 520f

    override fun layout() { fitted = false }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        if (!fitted) { renderer.fit(level, x, y, width, height); fitted = true }
        renderer.draw(batch, IsoRenderer.Frame(level, from, to, t, emptyList(), time, true, null, false), parentAlpha)
    }

    fun dispose() = renderer.dispose()
}
