package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.yeraltiimparatorlugu.sim.ResourceType

fun <T : Actor> T.onClick(f: () -> Unit): T {
    addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) = f()
    })
    return this
}

/** Kodla üretilmiş açık temalı arayüz stilleri. */
class Ui(val art: Art) {
    fun bg(c: Color): Drawable = NinePatchDrawable(art.panelPatch).tint(c)

    /** Düğmeler için daha az iç boşluklu zemin (dar ekranda sığması için). */
    fun buttonBg(c: Color): Drawable = NinePatchDrawable(art.panelPatch).tint(c).apply {
        leftWidth = 8f; rightWidth = 8f; topHeight = 4f; bottomHeight = 4f
    }
    fun flat(c: Color): Drawable = TextureRegionDrawable(art.white).tint(c)

    val panel = bg(Palette.uiPanel)
    val panelSoft = bg(Palette.uiBg)
    val chip = bg(Palette.hex("FFF1DA"))

    private val labelStyle = Label.LabelStyle(art.font, Palette.uiText)
    private val smallStyle = Label.LabelStyle(art.fontSmall, Palette.uiText)
    private val bigStyle = Label.LabelStyle(art.fontBig, Palette.uiText)

    private fun buttonStyle(up: Color, down: Color, checked: Color?, font: com.badlogic.gdx.graphics.g2d.BitmapFont = art.font, text: Color = Palette.uiText) =
        TextButton.TextButtonStyle(buttonBg(up), buttonBg(down), checked?.let { buttonBg(it) }, font).apply {
            fontColor = text
            checkedFontColor = Color.WHITE
            disabledFontColor = Palette.uiTextSoft
            disabled = buttonBg(Palette.hex("EFE7DA"))
        }

    val buttonStyle = buttonStyle(Palette.button, Palette.buttonDown, Palette.buttonChecked)
    val smallButtonStyle = buttonStyle(Palette.button, Palette.buttonDown, Palette.buttonChecked, art.fontSmall)
    val accentButtonStyle = buttonStyle(Palette.accent, Palette.accentDark, null, art.font, Color.WHITE)
    val dangerButtonStyle = buttonStyle(Palette.hex("FFD9D2"), Palette.hex("F7B2A6"), null, art.font, Palette.hex("A33A2C"))

    val scrollStyle = ScrollPane.ScrollPaneStyle().apply { vScrollKnob = flat(Palette.uiBorder) }

    fun label(text: CharSequence, color: Color = Palette.uiText, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, labelStyle).apply { this.color = color; this.wrap = wrap; setAlignment(align) }

    fun small(text: CharSequence, color: Color = Palette.uiText, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, smallStyle).apply { this.color = color; this.wrap = wrap; setAlignment(align) }

    fun title(text: CharSequence, color: Color = Palette.uiText) = Label(text, bigStyle).apply { this.color = color }

    fun button(text: String, small: Boolean = false, onClick: () -> Unit) =
        TextButton(text, if (small) smallButtonStyle else buttonStyle).onClick(onClick)

    fun accent(text: String, onClick: () -> Unit) = TextButton(text, accentButtonStyle).onClick(onClick)

    fun danger(text: String, onClick: () -> Unit) = TextButton(text, dangerButtonStyle).onClick(onClick)

    fun icon(r: ResourceType) = Image(art.icons[r])

    fun swatch(c: Color) = Image(flat(c))
}
