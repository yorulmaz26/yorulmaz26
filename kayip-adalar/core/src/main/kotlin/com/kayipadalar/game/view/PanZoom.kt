package com.kayipadalar.game.view

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

/**
 * Tek parmakla kaydırma, iki parmakla yakınlaştırma ve dokunma algılama.
 * [onTap] yerel koordinat verir; kaydırma eşiği aşılırsa dokunma sayılmaz.
 */
class PanZoom(
    var zoom: Float = 1f,
    val minZoom: Float = 0.45f,
    val maxZoom: Float = 1.8f,
    private val onTap: (Float, Float) -> Unit,
) : InputListener() {
    var panX = 0f
    var panY = 0f
    var bounds = 900f
    private val px = FloatArray(2); private val py = FloatArray(2); private val down = BooleanArray(2)
    private var startX = 0f; private var startY = 0f
    private var moved = false; private var pinch = false
    private var pinchDist = 1f; private var pinchZoom = 1f

    private fun dist() = Vector2.dst(px[0], py[0], px[1], py[1])

    override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (pointer > 1) return false
        px[pointer] = x; py[pointer] = y; down[pointer] = true
        if (pointer == 0) { startX = x; startY = y; moved = false; pinch = false }
        if (pointer == 1 && down[0]) { pinch = true; pinchDist = dist().coerceAtLeast(1f); pinchZoom = zoom }
        return true
    }

    override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        if (pointer > 1) return
        val dx = x - px[pointer]; val dy = y - py[pointer]
        px[pointer] = x; py[pointer] = y
        if (pinch && down[0] && down[1]) { zoom = (pinchZoom * dist() / pinchDist).coerceIn(minZoom, maxZoom); return }
        if (pointer == 0) {
            if (Vector2.dst(x, y, startX, startY) > 14f) moved = true
            if (moved) { panX += dx; panY += dy; clamp() }
        }
    }

    override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
        if (pointer > 1) return
        down[pointer] = false
        if (pinch) { if (!down[0] && !down[1]) pinch = false; return }
        if (pointer == 0 && !moved) onTap(x, y)
    }

    override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        zoom = (zoom * (1f - amountY * 0.1f)).coerceIn(minZoom, maxZoom); return true
    }

    fun clamp() {
        val b = bounds * zoom
        panX = panX.coerceIn(-b, b); panY = panY.coerceIn(-b, b)
    }
}
