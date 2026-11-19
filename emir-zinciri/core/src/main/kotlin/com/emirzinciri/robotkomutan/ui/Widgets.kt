package com.emirzinciri.robotkomutan.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.emirzinciri.robotkomutan.logic.command.CommandType

/** Arayüz fabrikası: bütün ekranlar aynı tasarım dilini kullanır. */
class Ui(val a: Assets) {

    fun label(text: CharSequence, font: BitmapFont = a.font, color: Color = Theme.ink, wrap: Boolean = false, align: Int = Align.left): Label =
        Label(text, Label.LabelStyle(font, color)).apply { setWrap(wrap); setAlignment(align) }

    fun image(icon: String, color: Color = Theme.white, size: Float = 36f): Image =
        Image(a.icons.named(icon)).apply { this.color = Color(color); setScaling(Scaling.fit); setSize(size, size) }

    fun cmdImage(t: CommandType, color: Color = Theme.white): Image =
        Image(a.icons.command(t)).apply { this.color = Color(color); setScaling(Scaling.fit) }

    private fun style(color: Color, radius: Int, outline: Color? = null): Button.ButtonStyle = Button.ButtonStyle().apply {
        up = a.panel(color, radius, 5f, outline)
        down = a.panel(Theme.shade(color, 0.9f), radius, 2f, outline)
        disabled = a.panel(Theme.mix(color, Theme.line, 0.75f), radius, 3f)
    }

    /** Metinli (isteğe bağlı ikonlu) buton. */
    fun button(
        text: String,
        color: Color = Theme.turquoise,
        textColor: Color = Theme.white,
        icon: String? = null,
        font: BitmapFont = a.fontBold,
        radius: Int = 22,
        onClick: () -> Unit,
    ): Button {
        val b = Button(style(color, radius))
        if (icon != null) b.add(image(icon, textColor)).size(32f).padRight(10f)
        b.add(label(text, font, textColor))
        b.pad(14f, 22f, 14f, 22f)
        onTap(b, onClick = onClick)
        return b
    }

    fun iconButton(icon: String, color: Color = Theme.white, iconColor: Color = Theme.ink, size: Float = 76f, onClick: () -> Unit): Button {
        val b = Button(style(color, 20, if (color == Theme.white) Theme.line else null))
        b.add(image(icon, iconColor)).size(size * 0.46f)
        b.setSize(size, size)
        onTap(b, onClick = onClick)
        return b
    }

    fun onTap(actor: Actor, sound: String = "click", onClick: () -> Unit) {
        actor.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (actor is Button && actor.isDisabled) return
                a.play(sound, 0.6f)
                onClick()
            }

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (actor is Button && actor.isDisabled) return false
                if (actor is com.badlogic.gdx.scenes.scene2d.Group) actor.isTransform = true
                actor.setOrigin(Align.center)
                actor.clearActions()
                actor.addAction(Actions.scaleTo(0.94f, 0.94f, 0.06f))
                return super.touchDown(event, x, y, pointer, button)
            }

            override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
                actor.addAction(Actions.scaleTo(1f, 1f, 0.12f, Interpolation.swingOut))
                super.touchUp(event, x, y, pointer, button)
            }
        })
    }

    fun panel(color: Color = Theme.panel, radius: Int = 26, shadow: Float = 6f, outline: Color? = null): Table =
        Table().apply { background = a.panel(color, radius, shadow, outline) }

    /** Ekranın üstünde, arka planı soluklaştıran modal pencere. */
    fun modal(stage: Stage, content: Table, dismissable: Boolean = true, onDismiss: (() -> Unit)? = null): Table {
        val overlay = Table()
        overlay.setFillParent(true)
        overlay.background = a.panel(Color(0.85f, 0.92f, 0.98f, 0.82f), 2, 0f)
        overlay.touchable = Touchable.enabled
        overlay.add(content).width(stage.width * 0.88f).pad(24f)
        if (dismissable) overlay.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                if (event.target === overlay) { close(overlay); onDismiss?.invoke() }
            }
        })
        overlay.color.a = 0f
        content.isTransform = true
        content.setOrigin(Align.center)
        content.setScale(0.92f)
        content.addAction(Actions.scaleTo(1f, 1f, 0.22f, Interpolation.swingOut))
        overlay.addAction(Actions.fadeIn(0.15f))
        stage.addActor(overlay)
        return overlay
    }

    fun close(overlay: Actor) {
        overlay.touchable = Touchable.disabled
        overlay.addAction(Actions.sequence(Actions.fadeOut(0.12f), Actions.removeActor()))
    }

    /** Başlık + gövde + butonlar içeren hazır modal. */
    fun dialog(stage: Stage, title: String, body: String, vararg buttons: Pair<String, () -> Unit>, color: Color = Theme.turquoise): Table {
        val c = panel()
        c.pad(32f)
        c.add(label(title, a.fontTitle, Theme.ink, align = Align.center)).growX().row()
        c.add(label(body, a.font, Theme.inkSoft, wrap = true, align = Align.center)).growX().padTop(14f).row()
        var overlay: Table? = null
        val row = Table()
        buttons.forEachIndexed { i, (t, fn) ->
            val primary = i == buttons.lastIndex
            row.add(button(t, if (primary) color else Theme.panelAlt, if (primary) Theme.white else Theme.ink) {
                overlay?.let { close(it) }; fn()
            }).growX().uniformX().pad(6f)
        }
        c.add(row).growX().padTop(22f)
        overlay = modal(stage, c)
        return overlay
    }

    /** Küçük bilgi rozeti: ikon + değer. */
    fun chip(icon: String, text: String, iconColor: Color, bg: Color = Theme.white): Table {
        val t = Table()
        t.background = a.panel(bg, 18, 3f)
        t.add(image(icon, iconColor)).size(26f).padRight(6f)
        t.add(label(text, a.fontBold, Theme.ink))
        t.pad(6f, 12f, 6f, 14f)
        return t
    }

    fun stars(count: Int, size: Float = 30f, max: Int = 3): Table {
        val t = Table()
        for (i in 0 until max) t.add(image("star", if (i < count) Theme.yellow else Theme.line)).size(size).pad(1f)
        return t
    }
}

/**
 * Komut kartı. Kategori rengiyle yuvarlak köşeli zemin, ortada ikon, altta kısa ad.
 * Vurgu durumları: çalışan kart, hatalı kart, seçili kart.
 */
class CardActor(val a: Assets, var type: CommandType, var arg: Int = type.defaultArg, val compact: Boolean = false) : Widget() {
    var highlight = false
    var error = false
    var selected = false
    var ghost = false
    var newBadge = false
    var indexLabel: String? = null
    private val layout = GlyphLayout()

    override fun getPrefWidth() = if (compact) 82f else 92f
    override fun getPrefHeight() = if (compact) 92f else 104f

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        val col = Theme.category(type.category)
        val alpha = color.a * parentAlpha * (if (ghost) 0.45f else 1f)
        val p = a.patch(18)
        val x = x; val y = y; val w = width; val h = height
        batch.setColor(0.15f, 0.2f, 0.32f, 0.18f * alpha); p.draw(batch, x, y - 4f, w, h)
        if (highlight) { batch.setColor(Theme.yellow.r, Theme.yellow.g, Theme.yellow.b, alpha); p.draw(batch, x - 5f, y - 5f, w + 10f, h + 10f) }
        if (error) { batch.setColor(Theme.red.r, Theme.red.g, Theme.red.b, alpha); p.draw(batch, x - 5f, y - 5f, w + 10f, h + 10f) }
        if (selected) { batch.setColor(Theme.ink.r, Theme.ink.g, Theme.ink.b, alpha); p.draw(batch, x - 4f, y - 4f, w + 8f, h + 8f) }
        batch.setColor(col.r, col.g, col.b, alpha); p.draw(batch, x, y, w, h)
        // Üst parlak şerit
        batch.setColor(1f, 1f, 1f, 0.16f * alpha); p.draw(batch, x + 4f, y + h * 0.5f, w - 8f, h * 0.5f - 4f)
        val icon = a.icons.command(type)
        val isz = w * 0.5f
        batch.setColor(1f, 1f, 1f, alpha)
        batch.draw(icon, x + (w - isz) / 2f, y + h - isz - 12f, isz, isz)
        val font = a.fontSmall
        font.setColor(1f, 1f, 1f, alpha)
        val text = if (type.hasArg) "${type.shortLabel} ×$arg" else type.shortLabel
        layout.setText(font, text, font.color, w - 6f, Align.center, false)
        font.draw(batch, layout, x + 3f, y + 10f + layout.height)
        indexLabel?.let {
            val f = a.fontSmall
            layout.setText(f, it)
            batch.setColor(1f, 1f, 1f, 0.95f * alpha)
            val bw = layout.width + 12f
            a.patch(10).draw(batch, x - 6f, y + h - 20f, bw, 26f)
            f.setColor(col.r * 0.7f, col.g * 0.7f, col.b * 0.7f, alpha)
            f.draw(batch, it, x, y + h - 20f + 13f + layout.height / 2f)
        }
        if (newBadge) {
            val f = a.fontSmall
            layout.setText(f, "YENİ")
            batch.setColor(Theme.red.r, Theme.red.g, Theme.red.b, alpha)
            a.patch(10).draw(batch, x + w - layout.width - 10f, y + h - 16f, layout.width + 16f, 26f)
            f.setColor(1f, 1f, 1f, alpha)
            f.draw(batch, "YENİ", x + w - layout.width - 2f, y + h - 3f + layout.height / 2f - 4f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }
}
