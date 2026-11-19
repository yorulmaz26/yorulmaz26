package com.emirzinciri.robotkomutan.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.emirzinciri.robotkomutan.logic.grid.Pos
import com.emirzinciri.robotkomutan.logic.sim.SimEvent
import com.emirzinciri.robotkomutan.play.PlaySession
import com.emirzinciri.robotkomutan.ui.Assets
import com.emirzinciri.robotkomutan.ui.Theme

/** Oyun ekranının ortasındaki izometrik harita; [PlaySession] durumunu çizer. */
class MapView(private val assets: Assets, private val session: PlaySession, private val showGrid: () -> Boolean) : Widget() {
    val renderer = IsoRenderer(assets)
    private var time = 0f
    private var lw = -1f; private var lh = -1f; private var lx = 0f; private var ly = 0f
    var focusRobot: String? = null
    /** Çok robotlu bölümlerde haritada robota dokunmak onun zincirini seçer. */
    var onRobotTapped: ((String) -> Unit)? = null

    init {
        addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: Float, y: Float) {
                if (session.robotIds.size < 2) return
                val sx = x + this@MapView.x; val sy = y + this@MapView.y
                val hit = session.current.robots.filter { it.alive }.minByOrNull { r ->
                    val dx = renderer.sx(r.pos.x.toFloat(), r.pos.y.toFloat()) - sx
                    val dy = renderer.sy(r.pos.x.toFloat(), r.pos.y.toFloat()) + renderer.tw * 0.3f - sy
                    dx * dx + dy * dy
                } ?: return
                val dx = renderer.sx(hit.pos.x.toFloat(), hit.pos.y.toFloat()) - sx
                val dy = renderer.sy(hit.pos.x.toFloat(), hit.pos.y.toFloat()) + renderer.tw * 0.3f - sy
                if (dx * dx + dy * dy < renderer.tw * renderer.tw * 0.6f) onRobotTapped?.invoke(hit.id)
            }
        })
    }

    private class Popup(val text: String, val color: Color, val gx: Float, val gy: Float, var life: Float = 1.1f)
    private val popups = ArrayList<Popup>()
    private val layout = GlyphLayout()

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        popups.forEach { it.life -= delta }
        popups.removeAll { it.life <= 0f }
    }

    fun onEvents(events: List<SimEvent>) {
        val st = session.current
        fun at(robot: String): Pos? = st.robots.firstOrNull { it.id == robot }?.pos
        for (e in events) {
            val (text, color, p) = when (e) {
                is SimEvent.Bumped -> Triple("Çarptı!", Theme.red, at(e.robot))
                is SimEvent.CommandFailed -> Triple("Olmadı!", Theme.red, at(e.robot))
                is SimEvent.EnergyCollected -> Triple("+${e.amount}", Theme.shade(Theme.energyColor, 0.8f), at(e.robot))
                is SimEvent.EnergyTransferred -> Triple("−${e.amount}", Theme.orange, at(e.robot))
                is SimEvent.SocketPowered -> Triple("Doldu!", Theme.green, e.at)
                is SimEvent.Pressed -> Triple("Klik", Theme.purple, e.at)
                is SimEvent.MessageSent -> Triple("Sinyal!", Theme.blue, at(e.robot))
                is SimEvent.PitFilled -> Triple("Doldu", Theme.green, e.at)
                is SimEvent.OutOfEnergy -> Triple("Enerji yok!", Theme.red, at(e.robot))
                is SimEvent.RobotLost -> Triple(if (e.laser) "Lazer!" else "Düştü!", Theme.red, e.at)
                is SimEvent.EnemyHit -> Triple("−${e.damage}", Theme.orange, e.at)
                is SimEvent.EnemyDefeated -> Triple("Etkisiz!", Theme.green, e.at)
                is SimEvent.EnemyAttack -> Triple(if (e.blocked) "Engellendi" else "−${e.damage}", if (e.blocked) Theme.blue else Theme.red, at(e.robot))
                is SimEvent.ShieldUp -> Triple("Kalkan", Theme.blue, at(e.robot))
                else -> continue
            }
            if (p != null) popups += Popup(text, color, p.x.toFloat(), p.y.toFloat())
        }
    }

    fun clearPopups() = popups.clear()

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        if (width != lw || height != lh || x != lx || y != ly) {
            renderer.fit(session.level, x + 10f, y + 10f, width - 20f, height - 20f)
            lw = width; lh = height; lx = x; ly = y
        }
        val frame = IsoRenderer.Frame(
            session.level, session.from, session.current, session.t, session.lastEvents, time,
            showGrid(), focusRobot, session.level.def.energy,
        )
        renderer.draw(batch, frame, parentAlpha)
        val font = assets.fontBold
        for (p in popups) {
            val k = 1f - p.life / 1.1f
            val x = renderer.sx(p.gx, p.gy)
            val y = renderer.sy(p.gx, p.gy) + renderer.tw * (1.1f + k * 0.5f)
            layout.setText(font, p.text)
            val a = MathUtils.clamp(p.life * 2f, 0f, 1f) * parentAlpha
            font.setColor(1f, 1f, 1f, a)
            for ((dx, dy) in OUTLINE) font.draw(batch, p.text, x - layout.width / 2 + dx, y + dy)
            font.setColor(p.color.r, p.color.g, p.color.b, a)
            font.draw(batch, p.text, x - layout.width / 2, y)
        }
        font.setColor(1f, 1f, 1f, 1f)
    }

    fun dispose() = renderer.dispose()

    companion object {
        private val OUTLINE = listOf(2f to 0f, -2f to 0f, 0f to 2f, 0f to -2f)
    }
}
