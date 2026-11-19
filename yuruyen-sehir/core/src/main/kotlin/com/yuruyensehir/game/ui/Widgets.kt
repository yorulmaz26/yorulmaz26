package com.yuruyensehir.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.yuruyensehir.logic.Amounts
import com.yuruyensehir.logic.Resource
import com.yuruyensehir.logic.ResourceStore
import com.yuruyensehir.logic.fmt

/** Yuvarlatılmış ilerleme çubuğu. */
class Bar(private val theme: Theme, var fillTint: Color, private val prefW: Float = 180f, private val prefH: Float = 14f) : com.badlogic.gdx.scenes.scene2d.ui.Widget() {
    var value = 0f
    private val bg = theme.tinted("roundSmall", Color.valueOf("E8DDC8"))
    private var fill = theme.tinted("roundSmall", fillTint)
    private val fillColor = Color(fillTint)

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.setColor(1f, 1f, 1f, parentAlpha)
        bg.draw(batch, x, y, width, height)
        val w = width * value.coerceIn(0f, 1f)
        if (w >= 10f) {
            if (fillColor != fillTint) { fill = theme.tinted("roundSmall", fillTint); fillColor.set(fillTint) }
            fill.draw(batch, x, y, w, height)
        }
    }

    override fun getPrefWidth(): Float = prefW
    override fun getPrefHeight(): Float = prefH
}

class Ui(val theme: Theme) {
    /** Düğmelere basıldığında çağrılır (tıklama sesi). */
    var onClick: (() -> Unit)? = null

    val skin get() = theme.skin

    fun label(text: String, style: String = "default", wrap: Boolean = false, align: Int = Align.left): Label =
        Label(text, skin, style).apply { this.wrap = wrap; setAlignment(align) }

    fun button(text: String, style: String = "default", onClick: () -> Unit): TextButton =
        TextButton(text, skin, style).apply {
            label.wrap = false
            setProgrammaticChangeEvents(false)
            pad(10f, 18f, 10f, 18f)
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) { if (!isDisabled) { this@Ui.onClick?.invoke(); onClick() } }
            })
        }

    fun resIcon(r: Resource, size: Float = 22f): Image = Image(theme.resIcon(r)).apply { setSize(size, size) }

    /** Simgeli gezinme düğmesi: simge üstte, yazı altta. */
    fun navButton(icon: String, text: String, onClick: () -> Unit): TextButton {
        val b = button(text, "nav", onClick)
        val lbl = b.label
        b.clearChildren()
        b.add(Image(theme.icon(icon))).size(34f).padTop(2f).row()
        b.add(lbl).padTop(2f)
        b.pad(8f, 1f, 6f, 1f)
        return b
    }

    fun dot(color: Color, size: Float = 18f): Image = Image(theme.circle(color)).apply { setSize(size, size) }

    fun card(color: Color = Color.WHITE): Table = Table().apply {
        background = theme.panel(color, 3f)
        pad(14f)
    }

    /** Maliyet metni: karşılanamayan kalemler kırmızı. */
    fun costText(cost: Amounts, store: ResourceStore): String =
        if (cost.isEmpty()) "Ücretsiz" else cost.entries.filter { it.value > 0 }.joinToString("  ") { (r, v) ->
            val ok = store[r] + 1e-9 >= v
            (if (ok) "[#4A3826]" else "[#D84A2B]") + "${r.title} ${fmt(v)}[]"
        }

    fun rate(v: Double): String = when {
        v > 0.05 -> "[#2E8B57]+${fmt(v)}[]"
        v < -0.05 -> "[#D84A2B]${fmt(v)}[]"
        else -> "[#7D6750]0[]"
    }

    fun resColor(r: Resource) = Palette.resource(r)

    /** Arka plan dokunuşlarını yakalar (alttaki dünyaya geçmesin). */
    fun blockTouches(t: Table) {
        t.touchable = com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        t.addListener(object : ClickListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean = true
        })
    }
}
