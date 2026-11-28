package com.yanlisrenk.game.ui

import com.yanlisrenk.logic.i18n.L
import com.yanlisrenk.logic.i18n.lupper

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.yanlisrenk.game.Assets
import com.yanlisrenk.game.Audio
import java.util.Locale

/** Oyunun aydınlık renk paleti. */
object Pal {
    fun hex(rgb: Int, a: Float = 1f) = Color(((rgb shr 16) and 255) / 255f, ((rgb shr 8) and 255) / 255f, (rgb and 255) / 255f, a)
    val SKY = hex(0x87CEFA)
    val TURQ = hex(0x58D6D0)
    val YELLOW = hex(0xFFD54F)
    val ORANGE = hex(0xFF9F43)
    val PINK = hex(0xFFB6C9)
    val GREEN = hex(0x73D65C)
    val BG = hex(0xF8FBFF)
    val TEXT = hex(0x253858)
    val TEXT_SOFT = hex(0x5B6B8C)
    val WHITE = Color(1f, 1f, 1f, 1f)
    val CARD = hex(0xFFFFFF)
    val GREY = hex(0xDDE3EE)
    val BLUE = hex(0x3D8BFF)
    val RED = hex(0xFF5A5F)
    val PURPLE = hex(0xA98BFF)
    val SHADE = hex(0x253858, 0.35f)
    val GOLD = hex(0xFFB800)
    val game = arrayOf(hex(0xFF5A5F), hex(0x3D8BFF), hex(0x3CC46B), hex(0xFFC933))
    fun gameColor(i: Int): Color = if (i in 0..3) game[i] else GREY
    fun darker(c: Color, f: Float = 0.82f) = Color(c.r * f, c.g * f, c.b * f, c.a)
    fun lighter(c: Color, f: Float = 0.35f) = Color(c.r + (1 - c.r) * f, c.g + (1 - c.g) * f, c.b + (1 - c.b) * f, c.a)
}

fun String.lupper(): String = uppercase(Locale.forLanguageTag("tr-TR"))

/** Programatik arayüz bileşenleri (Scene2D). */
class Ui(val assets: Assets, val audio: Audio) {
    enum class Size { S, M, L, XL, HUGE }

    fun font(s: Size): BitmapFont = when (s) {
        Size.S -> assets.fontS; Size.M -> assets.fontM; Size.L -> assets.fontL; Size.XL -> assets.fontXL; Size.HUGE -> assets.fontHuge
    }

    fun label(text: String, size: Size = Size.M, color: Color = Pal.TEXT, align: Int = Align.center, wrap: Boolean = false): Label {
        val l = FitLabel(text, Label.LabelStyle(font(size), Color.WHITE))
        l.color = color
        l.setAlignment(align)
        l.wrap = wrap
        return l
    }

    fun panel(color: Color = Pal.CARD): Drawable =
        if (assets.has("ui2_panel")) assets.nineScaled("ui2_panel", 80, 80, 80, 80, 0.5f, color)
        else assets.nine("panel", 36, 36, 36, 36, color)

    /** Parlak 3B buton zemini (kullanıcı görseli varsa onu kullanır, boyanabilir). */
    fun buttonBg(color: Color, scale: Float = 0.36f): Drawable =
        if (assets.has("ui2_button")) assets.nineScaled("ui2_button", 150, 150, 140, 150, scale, color)
        else assets.nine("button", 36, 36, 36, 40, color)
    fun pill(color: Color): Drawable = assets.nine("pill", 30, 30, 30, 30, color)
    fun icon(name: String, size: Float): Image = Image(assets.drawable(name), Scaling.fit).also { it.setSize(size, size) }

    /** Basılınca küçülen, sesli, renkli 3B buton. */
    fun button(text: String, color: Color = Pal.TURQ, icon: String? = null, size: Size = Size.L, textColor: Color = Pal.WHITE, onClick: () -> Unit): Button {
        val st = Button.ButtonStyle()
        st.up = buttonBg(color)
        st.down = buttonBg(Pal.darker(color, 0.9f))
        st.disabled = buttonBg(Pal.GREY)
        val b = Button(st)
        b.pad(14f, 26f, 22f, 26f)
        if (icon != null) b.add(icon(icon, if (size == Size.S) 44f else 54f)).size(if (size == Size.S) 44f else 54f).padRight(if (text.isEmpty()) 0f else 12f)
        if (text.isNotEmpty()) b.add(label(text, if (size == Size.S) Size.M else size, textColor))
        onTap(b, onClick)
        return b
    }

    /** Yuvarlak simge butonu. */
    fun iconButton(icon: String, size: Float = 96f, bg: Color? = Pal.WHITE, onClick: () -> Unit): Button {
        val st = Button.ButtonStyle()
        if (bg != null) { st.up = buttonBg(bg, 0.29f); st.down = buttonBg(Pal.darker(bg, 0.92f), 0.29f) }
        val b = Button(st)
        b.add(icon(icon, size * 0.66f)).size(size * 0.66f).padBottom(if (bg != null) 6f else 0f)
        onTap(b, onClick)
        return b
    }

    fun onTap(a: Actor, onClick: () -> Unit) {
        a.addListener(object : ClickListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                a.setOrigin(Align.center)
                (a as? com.badlogic.gdx.scenes.scene2d.Group)?.isTransform = true
                a.clearActions(); a.addAction(Actions.scaleTo(0.94f, 0.94f, 0.06f))
                return super.touchDown(event, x, y, pointer, button)
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                a.addAction(Actions.scaleTo(1f, 1f, 0.1f))
                super.touchUp(event, x, y, pointer, button)
            }
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (a is Button && a.isDisabled) return
                audio.play("button")
                onClick()
            }
        })
    }

    fun card(color: Color = Pal.CARD, pad: Float = 24f): Table = Table().also { it.background = panel(color); it.pad(pad) }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content).also {
        it.setScrollingDisabled(true, false); it.setOverscroll(false, true); it.fadeScrollBars = true; it.setFlickScroll(true)
    }

    /** Değer göstergesi (ör. altın: simge + sayı) */
    fun chip(icon: String, text: String, color: Color = Pal.WHITE): Table {
        val t = Table()
        t.background = pill(color)
        t.pad(6f, 10f, 6f, 22f)
        t.add(icon(icon, 52f)).size(52f).padRight(8f)
        t.add(label(text, Size.M))
        return t
    }

    fun progress(fraction: Float, color: Color, width: Float, height: Float = 26f): Actor = ProgressBarActor(this, fraction, color).also { it.setSize(width, height) }

    fun stars(count: Int, max: Int = 3, size: Float = 40f): Table {
        val t = Table()
        for (i in 0 until max) t.add(icon(if (i < count) "star_full" else "star_empty", size)).size(size).pad(2f)
        return t
    }

    fun spacer(): Actor = Actor()

    /** Tam ekran yarı saydam örtü + ortada kart. */
    fun overlay(stage: Stage, content: Table, dimAlpha: Float = 0.45f, closeOnBg: (() -> Unit)? = null): Table {
        val root = Table()
        root.setFillParent(true)
        root.background = assets.nine("white", 1, 1, 1, 1, Pal.hex(0x253858, dimAlpha))
        root.touchable = Touchable.enabled
        root.add(content)
        if (closeOnBg != null) root.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) { if (event?.target == root) closeOnBg() }
        })
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.15f))
        content.isTransform = true
        content.setScale(0.9f)
        content.addAction(Actions.scaleTo(1f, 1f, 0.2f, com.badlogic.gdx.math.Interpolation.swingOut))
        stage.addActor(root)
        root.layout(); content.setOrigin(Align.center)
        return root
    }

    fun toast(stage: Stage, text: String, icon: String? = null, color: Color = Pal.TEXT) {
        val t = Table()
        t.background = pill(Pal.WHITE)
        t.pad(16f, 30f, 16f, 30f)
        if (icon != null) t.add(icon(icon, 56f)).size(56f).padRight(12f)
        t.add(label(text, Size.M, color))
        t.pack()
        t.setPosition((stage.width - t.width) / 2, stage.height * 0.8f)
        t.touchable = Touchable.disabled
        t.color.a = 0f
        t.addAction(Actions.sequence(Actions.parallel(Actions.fadeIn(0.2f), Actions.moveBy(0f, 30f, 0.3f)), Actions.delay(1.8f), Actions.fadeOut(0.4f), Actions.removeActor()))
        stage.addActor(t)
    }

    fun confirm(stage: Stage, title: String, message: String, yes: String, no: String = L.t("common.vazgec"), yesColor: Color = Pal.RED, onYes: () -> Unit) {
        val c = card(pad = 40f)
        lateinit var ov: Table
        c.add(label(title, Size.L)).padBottom(16f).row()
        c.add(label(message, Size.M, Pal.TEXT_SOFT, wrap = true)).width(520f).padBottom(30f).row()
        val row = Table()
        row.add(button(no, Pal.GREY, textColor = Pal.TEXT, size = Size.M) { ov.remove() }).padRight(16f)
        row.add(button(yes, yesColor, size = Size.M) { ov.remove(); onYes() })
        c.add(row)
        ov = overlay(stage, c) { ov.remove() }
    }
}

/**
 * Tek satırlık metin sığmadığında yazı boyutunu (en fazla [minScale]'e kadar) küçülten etiket.
 * Uzun çeviriler (ör. Almanca) butonlardan ve kartlardan taşmaz; satır kaydırmalı etiketler aynen davranır.
 */
class FitLabel(text: CharSequence, style: LabelStyle) : Label(text, style) {
    var minScale = 0.55f
    // Not: üst sınıf kurucusu getPrefWidth() çağırır; bu alanlar o anda henüz atanmamıştır.
    private var measure: com.badlogic.gdx.graphics.g2d.GlyphLayout? = null
    private var measured: String? = null
    private var natural = 0f

    /** Yazı tipinin yoğunluğa göre temel ölçeği; etiket oluşturulurken (başka bir etiketin geçici ölçeği yokken) alınır. */
    private var baseScale = 0f
    init { baseScale = style.font.data.scaleX }

    private fun naturalWidth(): Float {
        val t = text.toString()
        val m = measure ?: com.badlogic.gdx.graphics.g2d.GlyphLayout().also { measure = it }
        if (t != measured) {
            // Ölçümü her zaman temel ölçekte yap (Label.layout sırasında yazı tipi geçici olarak ölçeklenmiş olabilir).
            val d = style.font.data
            val ox = d.scaleX; val oy = d.scaleY
            if (baseScale > 0f) d.setScale(baseScale)
            m.setText(style.font, t)
            d.setScale(ox, oy)
            natural = m.width; measured = t
        }
        val bg = style.background
        return natural + (bg?.leftWidth ?: 0f) + (bg?.rightWidth ?: 0f)
    }

    override fun getPrefWidth(): Float = if (wrap) super.getPrefWidth() else naturalWidth()
    override fun getMinWidth(): Float = if (wrap) super.getMinWidth() else naturalWidth() * minScale

    override fun layout() {
        if (!wrap && width > 0f) {
            val nat = naturalWidth()
            val s = if (nat > width + 0.5f) maxOf(minScale, width / nat) else 1f
            // Label.setFontScale değeri MUTLAK uygulanır (temel ölçeği ezer) ve bir kez ayarlanınca 1 de
            // "değişmedi" anlamına gelmez; bu yüzden hep temel ölçekle çarpıp veriyoruz.
            val base = if (baseScale > 0f) baseScale else style.font.data.scaleX
            val target = if (s >= 0.999f && fontScaleX == 1f) 1f else s * base
            if (kotlin.math.abs(fontScaleX - target) > 0.0001f) setFontScale(target)
        }
        super.layout()
    }
}

/** Yuvarlatılmış ilerleme çubuğu. */
class ProgressBarActor(private val ui: Ui, var fraction: Float, private val color: Color) : Widget() {
    private val bg = ui.pill(Pal.hex(0xE6ECF5))
    private val fg = ui.pill(color)
    override fun draw(batch: Batch, parentAlpha: Float) {
        val c = batch.color.cpy()
        batch.setColor(1f, 1f, 1f, parentAlpha * color.a)
        bg.draw(batch, x, y, width, height)
        val w = (width * fraction.coerceIn(0f, 1f))
        if (w > 1f) fg.draw(batch, x, y, maxOf(w, height), height)
        batch.color = c
    }
    override fun getPrefWidth() = width
    override fun getPrefHeight() = height
}

/** Bir aktörü bir başkasının üstüne rozet olarak bindirir. */
fun badge(ui: Ui, base: Actor, count: Int): Actor {
    if (count <= 0) return base
    val st = Stack()
    st.add(base)
    val t = Table()
    val b = Table(); b.background = ui.assets.drawable("circle").tint(Pal.RED)
    b.add(ui.label(if (count > 99) "99+" else count.toString(), Ui.Size.S, Pal.WHITE))
    t.add(b).size(46f).expand().top().right().padTop(-6f).padRight(-6f)
    t.touchable = Touchable.disabled
    st.add(t)
    return st
}
