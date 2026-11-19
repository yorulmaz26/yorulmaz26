package com.kayipadalar.game.view

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.kayipadalar.game.ui.Theme

/** Doluluk çubuğu; [value] her karede sağlayıcıdan okunur (zamanlayıcılar canlı güncellenir). */
class Bar(private val theme: Theme, private val gold: Boolean = false, private val h: Float = 18f, private val value: () -> Float) : Widget() {
    override fun getPrefHeight() = h
    override fun getPrefWidth() = 200f
    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.setColor(1f, 1f, 1f, parentAlpha)
        theme.patch("bar").draw(batch, x, y, width, height)
        val f = value().coerceIn(0f, 1f)
        if (f > 0f) theme.patch(if (gold) "barGold" else "barFill").draw(batch, x + 2f, y + 2f, maxOf(12f, (width - 4f) * f), height - 4f)
    }
}
