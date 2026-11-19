package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.yeraltiimparatorlugu.sim.ResourceType

fun <T : Actor> T.onClick(f: () -> Unit): T {
    addListener(object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) = f()
    })
    return this
}

/** Atlas tabanlı açık tema: gölgeli kartlar, kabarık düğmeler, Nunito yazı tipi. */
class Ui(val art: Art) {
    fun nine(name: String): Drawable = NinePatchDrawable(art.nine(name))
    fun flat(c: Color): Drawable = TextureRegionDrawable(art.white).tint(c)

    val panel = nine("ui_panel")
    val card = nine("ui_card")
    val cardHi = nine("ui_card_hi")
    val cardWarn = nine("ui_card_warn")
    val chip = nine("ui_chip")
    val toast = nine("ui_toast")
    val alert = nine("ui_alert")
    val bottom = nine("ui_bottom")
    val barTrack = art.nine("ui_bar_track")
    val barFill = art.nine("ui_bar_fill")
    val barWarn = art.nine("ui_bar_warn")

    private fun ls(f: BitmapFont, c: Color = Palette.uiText) = Label.LabelStyle(f, c)
    private val smallStyle = ls(art.fontSmall)
    private val normalStyle = ls(art.font)
    private val boldStyle = ls(art.fontBold)
    private val bigStyle = ls(art.fontBig)
    private val hugeStyle = ls(art.fontHuge)

    private fun bs(up: String, down: String, checked: String?, font: BitmapFont, text: Color, checkedText: Color = Color.WHITE) =
        TextButton.TextButtonStyle(nine(up), nine(down), checked?.let { nine(it) }, font).apply {
            fontColor = text; checkedFontColor = checkedText; downFontColor = text
            disabled = nine("ui_btn_disabled"); disabledFontColor = Palette.uiTextSoft
        }

    val buttonStyle = bs("ui_btn", "ui_btn_down", "ui_btn_selected", art.fontBold, Palette.uiText)
    val smallButtonStyle = bs("ui_btn", "ui_btn_down", "ui_btn_selected", art.font, Palette.uiText)
    val primaryStyle = bs("ui_btn_primary", "ui_btn_selected", null, art.fontBold, Color.WHITE)
    val dangerStyle = bs("ui_btn_danger", "ui_btn_danger", null, art.fontBold, Color.WHITE)
    val goldStyle = bs("ui_btn_gold", "ui_btn_gold", null, art.fontBold, Palette.uiText)

    val scrollStyle = ScrollPane.ScrollPaneStyle().apply { vScrollKnob = flat(Color(0.6f, 0.45f, 0.3f, 0.35f)) }

    fun label(text: CharSequence, color: Color = Palette.uiText, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, normalStyle).apply { this.color = color; this.wrap = wrap; setAlignment(align) }

    fun small(text: CharSequence, color: Color = Palette.uiText, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, smallStyle).apply { this.color = color; this.wrap = wrap; setAlignment(align) }

    fun bold(text: CharSequence, color: Color = Palette.uiText, wrap: Boolean = false, align: Int = Align.left) =
        Label(text, boldStyle).apply { this.color = color; this.wrap = wrap; setAlignment(align) }

    fun title(text: CharSequence, color: Color = Palette.uiText) = Label(text, bigStyle).apply { this.color = color }
    fun huge(text: CharSequence, color: Color = Palette.uiText) = Label(text, hugeStyle).apply { this.color = color }

    fun button(text: String, small: Boolean = false, onClick: () -> Unit) = TextButton(text, if (small) smallButtonStyle else buttonStyle).onClick(onClick)
    fun accent(text: String, onClick: () -> Unit) = TextButton(text, primaryStyle).onClick(onClick)
    fun danger(text: String, onClick: () -> Unit) = TextButton(text, dangerStyle).onClick(onClick)
    fun gold(text: String, onClick: () -> Unit) = TextButton(text, goldStyle).onClick(onClick)

    fun image(region: TextureRegion, tint: Color? = null) = Image(region).apply { setScaling(Scaling.fit); tint?.let { color = it } }
    fun icon(name: String, tint: Color = Palette.uiText) = image(art.icon(name), tint)
    fun icon(r: ResourceType) = image(art.icons[r]!!)
    fun swatch(c: Color) = Image(flat(c))

    /** İkonlu düğme: ikon üstte, yazı altta (alt çubuk) veya yan yana. */
    fun iconButton(iconName: String, text: String?, style: TextButton.TextButtonStyle = buttonStyle, vertical: Boolean = true, iconTint: Color? = null, iconSize: Float = 40f, onClick: () -> Unit): Button {
        val b = Button(Button.ButtonStyle(style.up, style.down, style.checked))
        val tint = iconTint ?: if (style === primaryStyle || style === dangerStyle) Color.WHITE else Palette.uiText
        val img = icon(iconName, tint)
        if (vertical) {
            b.add(img).size(iconSize).padTop(4f).row()
            if (text != null) b.add(Label(text, smallStyle).apply { color = tint }).padTop(2f)
        } else {
            b.add(img).size(iconSize).padRight(if (text != null) 8f else 0f)
            if (text != null) b.add(Label(text, boldStyle).apply { color = tint })
        }
        b.pad(6f, 10f, 10f, 10f)
        return b.onClick(onClick)
    }

    /** Yuvarlak ilerleme çubuğu. */
    inner class Bar(var value: Float, var warn: Boolean = false) : Actor() {
        override fun draw(batch: Batch, parentAlpha: Float) {
            batch.setColor(1f, 1f, 1f, parentAlpha)
            barTrack.draw(batch, x, y, width, height)
            val w = (width * value.coerceIn(0f, 1f))
            if (w >= height) (if (warn) barWarn else barFill).draw(batch, x, y, w, height)
        }
    }

    fun bar(value: Float, warn: Boolean = false) = Bar(value, warn)
}
