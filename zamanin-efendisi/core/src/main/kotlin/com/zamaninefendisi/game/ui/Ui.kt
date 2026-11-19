package com.zamaninefendisi.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.zamaninefendisi.game.ZamaninEfendisiGame

/** Kod ile üretilen arayüz bileşenleri. */
class Ui(private val game: ZamaninEfendisiGame) {
    private val a get() = game.assets
    private val styles = HashMap<String, TextButton.TextButtonStyle>()

    fun buttonStyle(color: Color, font: BitmapFont = a.fontM, fontColor: Color = Color.WHITE): TextButton.TextButtonStyle =
        styles.getOrPut("$color/${font.hashCode()}/$fontColor") {
            TextButton.TextButtonStyle().apply {
                up = a.panel(color)
                down = a.panel(Theme.darker(color, 0.85f))
                over = a.panel(Theme.lighter(color, 0.1f))
                disabled = a.panel(Theme.grey)
                this.font = font
                this.fontColor = fontColor
                downFontColor = fontColor
                disabledFontColor = Color.WHITE
            }
        }

    fun button(text: String, color: Color = Theme.orange, font: BitmapFont = a.fontM, fontColor: Color = Color.WHITE, onClick: () -> Unit): TextButton {
        val b = TextButton(text, buttonStyle(color, font, fontColor))
        b.label.wrap = true
        b.label.setAlignment(Align.center)
        b.pad(14f, 22f, 14f, 22f)
        b.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (b.isDisabled) return
                game.audio.play("click")
                onClick()
            }
        })
        return b
    }

    fun label(text: String, font: BitmapFont = a.fontM, color: Color = Theme.ink, wrap: Boolean = false, align: Int = Align.left): Label {
        val l = Label(text, Label.LabelStyle(font, color))
        l.wrap = wrap
        l.setAlignment(align)
        return l
    }

    fun panel(color: Color = Theme.panel, small: Boolean = false): Table = Table().apply {
        background = a.panel(color, small)
        touchable = Touchable.enabled
    }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content).apply {
        setScrollingDisabled(true, false)
        setFadeScrollBars(true)
        setOverscroll(false, true)
    }

    /** Yarı saydam arka planlı modal pencere. */
    fun modal(stageRoot: Table, content: Table, dismissOnOutside: Boolean = false, onDismiss: (() -> Unit)? = null): Table {
        val overlay = Table()
        overlay.setFillParent(true)
        overlay.background = a.solid(Theme.dim)
        overlay.touchable = Touchable.enabled
        overlay.userObject = "modal"
        overlay.add(content).width(640f).pad(24f)
        if (dismissOnOutside) overlay.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (event?.target == overlay) { overlay.remove(); onDismiss?.invoke() }
            }
        })
        overlay.color.a = 0f
        overlay.addAction(Actions.fadeIn(0.15f))
        stageRoot.stage.addActor(overlay)
        return overlay
    }

    fun toast(stageRoot: Table, text: String, color: Color = Theme.ink) {
        val t = panel(Theme.panel, small = true)
        t.touchable = Touchable.disabled
        t.add(label(text, a.fontS, color, wrap = true, align = Align.center)).width(560f).pad(14f, 20f, 14f, 20f)
        t.pack()
        val st = stageRoot.stage
        t.setPosition((st.width - t.width) / 2, st.height * 0.62f)
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.fadeIn(0.15f), Actions.delay(1.8f), Actions.fadeOut(0.35f), Actions.removeActor()))
        st.addActor(t)
    }
}

fun formatTime(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
