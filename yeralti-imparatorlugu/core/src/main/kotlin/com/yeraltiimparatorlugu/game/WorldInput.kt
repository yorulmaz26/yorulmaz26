package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.yeraltiimparatorlugu.sim.DigDesignation

/**
 * Dünya üzerindeki dokunma hareketleri: dokun, sürükle (kaydır veya kazı boya), iki parmakla yakınlaştır.
 * Arayüz önce olayı alır; arayüze denk gelmeyen dokunuşlar buraya düşer.
 */
class WorldInput(
    private val view: ViewState,
    private val hud: Hud,
    private val renderer: WorldRenderer,
    private val actions: GameActions,
) : GestureDetector.GestureAdapter() {
    private val v = Vector3()
    private var lastPaint: Pair<Int, Int>? = null
    private var pinching = false
    private var pinchZoom = 1f
    private val pinchMid = Vector2()

    val detector = GestureDetector(20f, 0.4f, 1.1f, 0.15f, this)

    val keysAndScroll = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            zoomBy(1f + amountY * 0.12f)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                when {
                    hud.sheetOpen -> hud.closeSheet()
                    view.mode != ToolMode.VIEW -> hud.setMode(ToolMode.VIEW)
                    else -> return false
                }
                return true
            }
            return false
        }
    }

    private fun cellAt(sx: Float, sy: Float): Pair<Int, Int> {
        v.set(sx, sy, 0f)
        renderer.camera.unproject(v)
        return MathUtils.floor(v.x) to MathUtils.floor(-v.y)
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        lastPaint = null
        return false
    }

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        val (cx, cy) = cellAt(x, y)
        val sim = actions.sim
        when (view.mode) {
            ToolMode.VIEW -> hud.selectCell(cx, cy)
            ToolMode.DIG -> designate(cx, cy, true)
            ToolMode.CANCEL -> sim.cancelDig(cx, cy)
            ToolMode.BUILD -> {
                val t = view.ghostType ?: return true
                view.ghostX = cx - t.width / 2
                view.ghostY = cy - t.height / 2
                hud.onGhostMoved()
            }
            ToolMode.PHEROMONE -> {
                sim.placePheromone(view.pheromone, cx, maxOf(cy, -1))
                hud.showToast("${view.pheromone.title} feromonu: ${view.pheromone.description}")
            }
        }
        return true
    }

    private fun designate(cx: Int, cy: Int, report: Boolean) {
        val r = actions.sim.designateDig(cx, cy)
        if (report && r != DigDesignation.OK && r != DigDesignation.ALREADY) hud.showToast(r.message ?: "")
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        if (pinching || Gdx.input.isTouched(1)) return true
        if (view.mode == ToolMode.DIG || view.mode == ToolMode.CANCEL) {
            val (cx, cy) = cellAt(x, y)
            val (px, py) = lastPaint ?: cellAt(x - deltaX, y - deltaY)
            // Hızlı sürüklemede boşluk kalmasın diye iki hücre arası doldurulur.
            val steps = maxOf(kotlin.math.abs(cx - px), kotlin.math.abs(cy - py), 1)
            for (i in 0..steps) {
                val ix = px + (cx - px) * i / steps
                val iy = py + (cy - py) * i / steps
                if (view.mode == ToolMode.DIG) designate(ix, iy, false) else actions.sim.cancelDig(ix, iy)
            }
            lastPaint = cx to cy
            return true
        }
        val cam = renderer.camera
        val perPixel = cam.viewportWidth * cam.zoom / Gdx.graphics.width
        cam.position.add(-deltaX * perPixel, deltaY * perPixel, 0f)
        clamp()
        return true
    }

    override fun pinch(initialPointer1: Vector2, initialPointer2: Vector2, pointer1: Vector2, pointer2: Vector2): Boolean {
        val cam = renderer.camera
        val mid = Vector2(pointer1).add(pointer2).scl(0.5f)
        if (!pinching) {
            pinching = true
            pinchZoom = cam.zoom
            pinchMid.set(mid)
        }
        val d0 = initialPointer1.dst(initialPointer2).coerceAtLeast(1f)
        val d1 = pointer1.dst(pointer2).coerceAtLeast(1f)
        cam.zoom = MathUtils.clamp(pinchZoom * d0 / d1, MIN_ZOOM, MAX_ZOOM)
        val perPixel = cam.viewportWidth * cam.zoom / Gdx.graphics.width
        cam.position.add(-(mid.x - pinchMid.x) * perPixel, (mid.y - pinchMid.y) * perPixel, 0f)
        pinchMid.set(mid)
        clamp()
        return true
    }

    override fun pinchStop() {
        pinching = false
    }

    fun zoomBy(f: Float) {
        renderer.camera.zoom = MathUtils.clamp(renderer.camera.zoom * f, MIN_ZOOM, MAX_ZOOM)
        clamp()
    }

    fun clamp() {
        val p = renderer.camera.position
        p.x = MathUtils.clamp(p.x, -150f, 150f)
        p.y = MathUtils.clamp(p.y, -260f, 10f)
    }

    companion object {
        const val MIN_ZOOM = 0.45f
        const val MAX_ZOOM = 3.2f
    }
}
