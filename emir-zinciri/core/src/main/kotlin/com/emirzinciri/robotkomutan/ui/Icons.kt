package com.emirzinciri.robotkomutan.ui

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.emirzinciri.robotkomutan.logic.command.CommandType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Çalışma zamanında çizilen beyaz ikonlar (renk, çizim sırasında verilir). Harici görsel dosyasına gerek kalmaz,
 * her ekran yoğunluğunda net görünür.
 */
class Icons(private val size: Int) : com.badlogic.gdx.utils.Disposable {
    private val cache = HashMap<String, TextureRegion>()
    private val textures = ArrayList<Texture>()

    fun command(t: CommandType): TextureRegion = get("cmd_${t.name}") { p -> drawCommand(p, t) }
    fun named(name: String): TextureRegion = get(name) { p -> drawNamed(p, name) }

    private fun get(key: String, draw: (Pixmap) -> Unit): TextureRegion = cache.getOrPut(key) {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(0f, 0f, 0f, 0f); p.fill()
        p.setColor(1f, 1f, 1f, 1f)
        draw(p)
        val tex = Texture(p, true).apply { setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear) }
        p.dispose()
        textures += tex
        TextureRegion(tex)
    }

    override fun dispose() = textures.forEach { it.dispose() }

    // --------------------------------------------------------------- çizim yardımcıları (0..1 birim koordinat, y aşağı)

    private val s get() = size.toFloat()
    private fun X(v: Float) = (v * s).toInt()

    private fun Pixmap.tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        fillTriangle(X(x1), X(y1), X(x2), X(y2), X(x3), X(y3))

    private fun Pixmap.rect(x: Float, y: Float, w: Float, h: Float) = fillRectangle(X(x), X(y), X(w), X(h))
    private fun Pixmap.circle(x: Float, y: Float, r: Float) = fillCircle(X(x), X(y), X(r))

    private fun Pixmap.thick(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val nx = -dy / len * w / 2; val ny = dx / len * w / 2
        tri(x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny)
        tri(x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny)
        circle(x1, y1, w / 2); circle(x2, y2, w / 2)
    }

    /** Yay çizer (derece, saat yönünün tersine; ekran y'si aşağı). */
    private fun Pixmap.arc(cx: Float, cy: Float, r: Float, from: Float, to: Float, w: Float) {
        val steps = 18
        var px = cx + r * cos(Math.toRadians(from.toDouble())).toFloat()
        var py = cy - r * sin(Math.toRadians(from.toDouble())).toFloat()
        for (i in 1..steps) {
            val a = Math.toRadians((from + (to - from) * i / steps).toDouble())
            val nx = cx + r * cos(a).toFloat(); val ny = cy - r * sin(a).toFloat()
            thick(px, py, nx, ny, w); px = nx; py = ny
        }
    }

    private fun Pixmap.arrowUp(cx: Float, top: Float, bottom: Float) {
        tri(cx, top, cx - 0.22f, top + 0.24f, cx + 0.22f, top + 0.24f)
        rect(cx - 0.08f, top + 0.2f, 0.16f, bottom - top - 0.2f)
    }

    private fun Pixmap.box(x: Float, y: Float, w: Float) {
        rect(x, y, w, w)
        setColor(0f, 0f, 0f, 0f)
        rect(x + 0.06f, y + 0.06f, w - 0.12f, w - 0.12f)
        setColor(1f, 1f, 1f, 1f)
        rect(x + w / 2 - 0.04f, y, 0.08f, w)
    }

    private fun Pixmap.bolt(ox: Float, oy: Float, k: Float) {
        tri(ox + 0.55f * k, oy, ox + 0.15f * k, oy + 0.55f * k, ox + 0.5f * k, oy + 0.55f * k)
        tri(ox + 0.5f * k, oy + 0.45f * k, ox + 0.85f * k, oy + 0.45f * k, ox + 0.4f * k, oy + 1.0f * k)
    }

    private fun drawCommand(p: Pixmap, t: CommandType) = with(p) {
        when (t) {
            CommandType.FORWARD -> arrowUp(0.5f, 0.12f, 0.88f)
            CommandType.BACK -> { tri(0.5f, 0.88f, 0.28f, 0.64f, 0.72f, 0.64f); rect(0.42f, 0.12f, 0.16f, 0.56f) }
            CommandType.TURN_LEFT -> { arc(0.56f, 0.6f, 0.26f, 0f, 120f, 0.14f); tri(0.12f, 0.36f, 0.42f, 0.18f, 0.42f, 0.54f) }
            CommandType.TURN_RIGHT -> { arc(0.44f, 0.6f, 0.26f, 60f, 180f, 0.14f); tri(0.88f, 0.36f, 0.58f, 0.18f, 0.58f, 0.54f) }
            CommandType.WAIT -> {
                circle(0.5f, 0.5f, 0.38f); setColor(0f, 0f, 0f, 0f); circle(0.5f, 0.5f, 0.28f); setColor(1f, 1f, 1f, 1f)
                thick(0.5f, 0.5f, 0.5f, 0.3f, 0.08f); thick(0.5f, 0.5f, 0.64f, 0.58f, 0.08f)
            }
            CommandType.JUMP -> { arc(0.5f, 0.78f, 0.34f, 20f, 160f, 0.1f); tri(0.86f, 0.74f, 0.7f, 0.56f, 0.94f, 0.54f); rect(0.1f, 0.84f, 0.8f, 0.06f) }
            CommandType.PICK -> { box(0.27f, 0.46f, 0.46f); arrowUpSmall(this, 0.5f, 0.08f) }
            CommandType.DROP -> { box(0.27f, 0.46f, 0.46f); tri(0.5f, 0.4f, 0.36f, 0.24f, 0.64f, 0.24f); rect(0.45f, 0.06f, 0.1f, 0.2f) }
            CommandType.PUSH -> { box(0.46f, 0.3f, 0.42f); tri(0.4f, 0.51f, 0.2f, 0.36f, 0.2f, 0.66f); rect(0.06f, 0.46f, 0.16f, 0.1f) }
            CommandType.PULL -> { box(0.46f, 0.3f, 0.42f); tri(0.06f, 0.51f, 0.26f, 0.36f, 0.26f, 0.66f); rect(0.24f, 0.46f, 0.18f, 0.1f) }
            CommandType.PRESS -> {
                rect(0.18f, 0.66f, 0.64f, 0.16f); rect(0.32f, 0.5f, 0.36f, 0.18f)
                tri(0.5f, 0.42f, 0.36f, 0.24f, 0.64f, 0.24f); rect(0.45f, 0.06f, 0.1f, 0.2f)
            }
            CommandType.COLLECT -> { bolt(0.12f, 0.08f, 0.84f) }
            CommandType.TRANSFER -> { bolt(0.0f, 0.12f, 0.6f); tri(0.92f, 0.5f, 0.62f, 0.3f, 0.62f, 0.7f); rect(0.44f, 0.44f, 0.2f, 0.12f) }
            CommandType.SEND -> {
                arc(0.3f, 0.5f, 0.16f, -60f, 60f, 0.08f); arc(0.3f, 0.5f, 0.32f, -55f, 55f, 0.08f); arc(0.3f, 0.5f, 0.48f, -50f, 50f, 0.08f)
                circle(0.24f, 0.5f, 0.08f)
            }
            CommandType.WAIT_MSG -> {
                rect(0.14f, 0.26f, 0.72f, 0.48f); setColor(0f, 0f, 0f, 0f); rect(0.2f, 0.32f, 0.6f, 0.36f); setColor(1f, 1f, 1f, 1f)
                thick(0.18f, 0.3f, 0.5f, 0.54f, 0.07f); thick(0.82f, 0.3f, 0.5f, 0.54f, 0.07f)
            }
            CommandType.REPEAT -> {
                arc(0.5f, 0.5f, 0.3f, 100f, 350f, 0.12f); tri(0.62f, 0.12f, 0.88f, 0.26f, 0.6f, 0.4f)
            }
            CommandType.IF_CLEAR, CommandType.IF_BLOCKED -> {
                tri(0.5f, 0.08f, 0.9f, 0.5f, 0.5f, 0.92f); tri(0.5f, 0.08f, 0.1f, 0.5f, 0.5f, 0.92f)
                setColor(0f, 0f, 0f, 0f); tri(0.5f, 0.2f, 0.78f, 0.5f, 0.5f, 0.8f); tri(0.5f, 0.2f, 0.22f, 0.5f, 0.5f, 0.8f); setColor(1f, 1f, 1f, 1f)
                if (t == CommandType.IF_CLEAR) arrowUpSmall(this, 0.5f, 0.3f) else rect(0.36f, 0.44f, 0.28f, 0.12f)
            }
            CommandType.END -> { rect(0.2f, 0.16f, 0.12f, 0.68f); rect(0.2f, 0.16f, 0.4f, 0.12f); rect(0.2f, 0.72f, 0.4f, 0.12f) }
            CommandType.STOP -> {
                tri(0.3f, 0.1f, 0.7f, 0.1f, 0.9f, 0.3f); tri(0.3f, 0.1f, 0.9f, 0.3f, 0.1f, 0.3f)
                rect(0.1f, 0.3f, 0.8f, 0.4f)
                tri(0.1f, 0.7f, 0.9f, 0.7f, 0.7f, 0.9f); tri(0.1f, 0.7f, 0.7f, 0.9f, 0.3f, 0.9f)
                setColor(0f, 0f, 0f, 0f); rect(0.24f, 0.44f, 0.52f, 0.12f); setColor(1f, 1f, 1f, 1f)
            }
        }
    }

    private fun arrowUpSmall(p: Pixmap, cx: Float, top: Float) = with(p) {
        tri(cx, top, cx - 0.14f, top + 0.16f, cx + 0.14f, top + 0.16f); rect(cx - 0.05f, top + 0.14f, 0.1f, 0.16f)
    }

    private fun drawNamed(p: Pixmap, name: String) = with(p) {
        when (name) {
            "play" -> tri(0.28f, 0.16f, 0.28f, 0.84f, 0.86f, 0.5f)
            "pause" -> { rect(0.24f, 0.18f, 0.18f, 0.64f); rect(0.58f, 0.18f, 0.18f, 0.64f) }
            "step" -> { tri(0.16f, 0.18f, 0.16f, 0.82f, 0.62f, 0.5f); rect(0.66f, 0.18f, 0.16f, 0.64f) }
            "stepback" -> { tri(0.84f, 0.18f, 0.84f, 0.82f, 0.38f, 0.5f); rect(0.18f, 0.18f, 0.16f, 0.64f) }
            "reset" -> { arc(0.5f, 0.52f, 0.3f, 120f, 400f, 0.12f); tri(0.1f, 0.3f, 0.4f, 0.12f, 0.36f, 0.44f) }
            "trash" -> { rect(0.24f, 0.3f, 0.52f, 0.58f); rect(0.16f, 0.18f, 0.68f, 0.1f); rect(0.4f, 0.1f, 0.2f, 0.1f)
                setColor(0f, 0f, 0f, 0f); rect(0.34f, 0.38f, 0.08f, 0.42f); rect(0.58f, 0.38f, 0.08f, 0.42f); setColor(1f, 1f, 1f, 1f) }
            "copy" -> { rect(0.3f, 0.3f, 0.52f, 0.56f); setColor(0f, 0f, 0f, 0f); rect(0.38f, 0.38f, 0.36f, 0.4f); setColor(1f, 1f, 1f, 1f)
                rect(0.16f, 0.12f, 0.08f, 0.56f); rect(0.16f, 0.12f, 0.5f, 0.08f) }
            "left" -> tri(0.66f, 0.14f, 0.66f, 0.86f, 0.22f, 0.5f)
            "right" -> tri(0.34f, 0.14f, 0.34f, 0.86f, 0.78f, 0.5f)
            "plus" -> { rect(0.42f, 0.16f, 0.16f, 0.68f); rect(0.16f, 0.42f, 0.68f, 0.16f) }
            "minus" -> rect(0.16f, 0.42f, 0.68f, 0.16f)
            "close" -> { thick(0.22f, 0.22f, 0.78f, 0.78f, 0.14f); thick(0.78f, 0.22f, 0.22f, 0.78f, 0.14f) }
            "back" -> { thick(0.62f, 0.18f, 0.3f, 0.5f, 0.14f); thick(0.3f, 0.5f, 0.62f, 0.82f, 0.14f) }
            "menu" -> { rect(0.16f, 0.22f, 0.68f, 0.12f); rect(0.16f, 0.44f, 0.68f, 0.12f); rect(0.16f, 0.66f, 0.68f, 0.12f) }
            "lock" -> { rect(0.22f, 0.44f, 0.56f, 0.44f); arc(0.5f, 0.44f, 0.2f, 0f, 180f, 0.1f) }
            "check" -> { thick(0.16f, 0.52f, 0.4f, 0.76f, 0.16f); thick(0.4f, 0.76f, 0.84f, 0.26f, 0.16f) }
            "hint" -> { circle(0.5f, 0.4f, 0.28f); rect(0.38f, 0.62f, 0.24f, 0.18f); rect(0.4f, 0.84f, 0.2f, 0.06f) }
            "gear" -> {
                for (i in 0 until 8) { val a = i * Math.PI / 4; thick(0.5f, 0.5f, 0.5f + 0.36f * cos(a).toFloat(), 0.5f + 0.36f * sin(a).toFloat(), 0.16f) }
                circle(0.5f, 0.5f, 0.28f); setColor(0f, 0f, 0f, 0f); circle(0.5f, 0.5f, 0.12f); setColor(1f, 1f, 1f, 1f)
            }
            "book" -> { rect(0.14f, 0.2f, 0.32f, 0.6f); rect(0.54f, 0.2f, 0.32f, 0.6f); rect(0.14f, 0.76f, 0.72f, 0.06f) }
            "chart" -> { rect(0.16f, 0.56f, 0.16f, 0.28f); rect(0.42f, 0.36f, 0.16f, 0.48f); rect(0.68f, 0.16f, 0.16f, 0.68f) }
            "map" -> { tri(0.1f, 0.2f, 0.1f, 0.86f, 0.36f, 0.76f); tri(0.1f, 0.2f, 0.36f, 0.76f, 0.36f, 0.12f)
                tri(0.4f, 0.12f, 0.4f, 0.76f, 0.6f, 0.86f); tri(0.4f, 0.12f, 0.6f, 0.86f, 0.6f, 0.22f)
                tri(0.64f, 0.22f, 0.64f, 0.86f, 0.9f, 0.76f); tri(0.64f, 0.22f, 0.9f, 0.76f, 0.9f, 0.12f) }
            "star" -> star(this, 0.5f, 0.53f, 0.46f, 0.2f)
            "heart" -> { circle(0.32f, 0.36f, 0.2f); circle(0.68f, 0.36f, 0.2f); tri(0.13f, 0.42f, 0.87f, 0.42f, 0.5f, 0.86f) }
            "coin" -> { circle(0.5f, 0.5f, 0.42f); setColor(0f, 0f, 0f, 0f); circle(0.5f, 0.5f, 0.3f); setColor(1f, 1f, 1f, 1f); rect(0.44f, 0.3f, 0.12f, 0.4f) }
            "gearSmall" -> { circle(0.5f, 0.5f, 0.4f); setColor(0f, 0f, 0f, 0f); circle(0.5f, 0.5f, 0.16f) }
            "bolt" -> bolt(0.12f, 0.06f, 0.88f)
            "chip" -> { rect(0.26f, 0.26f, 0.48f, 0.48f); for (i in 0..2) { val o = 0.32f + i * 0.14f; rect(o, 0.1f, 0.06f, 0.14f); rect(o, 0.76f, 0.06f, 0.14f); rect(0.1f, o, 0.14f, 0.06f); rect(0.76f, o, 0.14f, 0.06f) } }
            "robot" -> { rect(0.2f, 0.34f, 0.6f, 0.46f); rect(0.46f, 0.12f, 0.08f, 0.2f); circle(0.5f, 0.12f, 0.08f)
                setColor(0f, 0f, 0f, 0f); circle(0.37f, 0.52f, 0.07f); circle(0.63f, 0.52f, 0.07f); rect(0.36f, 0.66f, 0.28f, 0.06f); setColor(1f, 1f, 1f, 1f) }
            "dot" -> circle(0.5f, 0.5f, 0.45f)
            "ring" -> { circle(0.5f, 0.5f, 0.46f); setColor(0f, 0f, 0f, 0f); circle(0.5f, 0.5f, 0.36f) }
            "hand" -> { rect(0.36f, 0.08f, 0.16f, 0.5f); rect(0.36f, 0.46f, 0.46f, 0.4f); rect(0.54f, 0.34f, 0.12f, 0.2f); rect(0.68f, 0.38f, 0.12f, 0.2f)
                circle(0.44f, 0.1f, 0.08f); tri(0.36f, 0.86f, 0.82f, 0.86f, 0.6f, 0.96f) }
            "sound" -> { rect(0.12f, 0.36f, 0.2f, 0.28f); tri(0.3f, 0.36f, 0.56f, 0.12f, 0.56f, 0.88f); tri(0.3f, 0.64f, 0.56f, 0.88f, 0.3f, 0.36f)
                arc(0.56f, 0.5f, 0.18f, -60f, 60f, 0.07f); arc(0.56f, 0.5f, 0.32f, -55f, 55f, 0.07f) }
            "speed" -> { tri(0.08f, 0.2f, 0.08f, 0.8f, 0.48f, 0.5f); tri(0.48f, 0.2f, 0.48f, 0.8f, 0.9f, 0.5f) }
            else -> circle(0.5f, 0.5f, 0.3f)
        }
    }

    private fun star(p: Pixmap, cx: Float, cy: Float, ro: Float, ri: Float) {
        val pts = (0 until 10).map { i ->
            val r = if (i % 2 == 0) ro else ri
            val a = Math.PI / 2 + i * Math.PI / 5
            (cx + r * cos(a).toFloat()) to (cy - r * sin(a).toFloat())
        }
        for (i in 0 until 10) {
            val (x1, y1) = pts[i]; val (x2, y2) = pts[(i + 1) % 10]
            p.fillTriangle(X(cx), X(cy), X(x1), X(y1), X(x2), X(y2))
        }
    }
}
