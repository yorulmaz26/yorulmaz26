package com.besaniyelik.dunya.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.besaniyelik.dunya.Assets
import com.besaniyelik.dunya.audio.AudioManager
import com.besaniyelik.dunya.audio.Sfx

object Palette {
    val INK = Color.valueOf("253858")
    val INK_SOFT = Color.valueOf("5B6B86")
    val SKY = Color.valueOf("87CEFA")
    val TURQ = Color.valueOf("58D6D0")
    val GRASS = Color.valueOf("73D65C")
    val SUN = Color.valueOf("FFD54F")
    val ORANGE = Color.valueOf("FF9F43")
    val PINK = Color.valueOf("FFB6C9")
    val BG = Color.valueOf("F8FBFF")
    val WHITE = Color.WHITE
    val GOLD = Color.valueOf("F5A623")
    val RED = Color.valueOf("FF6B6B")
}

/** Arayüz yapı taşları; bütün ekranlar aynı görsel dili kullanır. */
class UiKit(val a: Assets, val audio: AudioManager) {
    fun patch(name: String): NinePatchDrawable = NinePatchDrawable(a.patch(name))
    fun region(name: String): TextureRegionDrawable = TextureRegionDrawable(a.ui(name))

    fun label(text: String, font: BitmapFont = a.f28, color: Color = Palette.INK, align: Int = Align.center, wrap: Boolean = false): Label {
        val l = Label(text, Label.LabelStyle(font, color))
        l.setAlignment(align)
        l.wrap = wrap
        return l
    }

    fun image(name: String, size: Float = 64f): Image = Image(a.ui(name)).apply { setSize(size, size) }

    /** Basınca küçülen, tık sesi çıkaran renkli buton. */
    fun button(text: String, color: String = "yellow", icon: String? = null, font: BitmapFont = a.f36o, textColor: Color = Color.WHITE, onClick: () -> Unit): UiButton {
        val b = UiButton(patch("btn_$color"), this)
        if (icon != null) b.add(Image(a.ui(icon)).apply { if (color == "white" && icon.startsWith("g_")) this.color = Palette.INK_SOFT }).size(52f).padRight(if (text.isEmpty()) 0f else 10f)
        if (text.isNotEmpty()) b.add(label(text, font, textColor))
        b.pad(14f, 26f, 26f, 26f)
        b.onClick(onClick)
        return b
    }

    fun iconButton(icon: String, color: String = "white", size: Float = 96f, iconSize: Float = 56f, onClick: () -> Unit): UiButton {
        val b = UiButton(patch("btn_$color"), this)
        val img = Image(a.ui(icon))
        if ((color == "white" || color == "gray") && icon.startsWith("g_")) img.color = Palette.INK_SOFT
        b.add(img).size(iconSize).padBottom(10f)
        b.onClick(onClick)
        b.setSize(size, size)
        return b
    }

    fun panel(name: String = "panel"): Table = Table().apply { background = patch(name); pad(34f) }

    fun bar(value: Float, fill: String = "bar_fill", height: Float = 28f): ProgressWidget = ProgressWidget(this, value, fill, height)

    fun stars(n: Int, size: Float = 48f, of: Int = 3): Table = Table().apply {
        for (i in 0 until of) add(Image(a.ui(if (i < n) "b_star" else "b_star_empty"))).size(size).pad(2f)
    }

    fun currency(icon: String, value: String, font: BitmapFont = a.f28): Table = Table().apply {
        background = patch("chip")
        pad(6f, 10f, 10f, 18f)
        add(Image(a.ui(icon))).size(44f).padRight(6f)
        add(label(value, font, Palette.INK))
    }

    fun click() = audio.play(Sfx.CLICK)
}

class UiButton(bg: Drawable, private val kit: UiKit) : Table() {
    private var action: (() -> Unit)? = null
    var enabled = true
        set(v) { field = v; color.a = if (v) 1f else 0.55f }

    init {
        background = bg
        touchable = Touchable.enabled
        isTransform = true
        addListener(object : ClickListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (!enabled) return false
                setOrigin(Align.center)
                clearActions(); addAction(Actions.scaleTo(0.94f, 0.94f, 0.06f))
                return super.touchDown(event, x, y, pointer, button)
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                super.touchUp(event, x, y, pointer, button)
                clearActions(); addAction(Actions.scaleTo(1f, 1f, 0.12f, Interpolation.swingOut))
            }
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (!enabled) return
                kit.click()
                action?.invoke()
            }
        })
    }

    fun onClick(f: () -> Unit) { action = f }
}

class ProgressWidget(private val kit: UiKit, var value: Float, private val fillName: String, private val h: Float) : Widget() {
    private val bg = kit.patch("bar_bg")
    private val fill = kit.patch(fillName)
    override fun getPrefHeight() = h
    override fun getPrefWidth() = 200f
    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.setColor(1f, 1f, 1f, parentAlpha * color.a)
        bg.draw(batch, x, y, width, height)
        val w = (width * value.coerceIn(0f, 1f))
        if (w > 4f) fill.draw(batch, x, y, kotlin.math.max(w, height), height)
    }
}

/** Basit dönen/salınan görsel (menü süsü). */
class Bobber(region: TextureRegion, private val amp: Float = 8f, private val speed: Float = 2f) : Image(region) {
    private var t = (Math.random() * 6).toFloat()
    private var baseY = Float.NaN
    override fun act(delta: Float) {
        super.act(delta)
        t += delta
        if (baseY.isNaN()) baseY = y
    }
    override fun draw(batch: Batch, parentAlpha: Float) {
        val oy = y
        y = oy + kotlin.math.sin(t * speed) * amp
        super.draw(batch, parentAlpha)
        y = oy
    }
}

fun Actor.popIn(delay: Float = 0f) {
    setOrigin(Align.center)
    setScale(0.6f); color.a = 0f
    if (this is Table) isTransform = true
    addAction(Actions.sequence(Actions.delay(delay), Actions.parallel(Actions.fadeIn(0.18f), Actions.scaleTo(1f, 1f, 0.35f, Interpolation.swingOut))))
}
