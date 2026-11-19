package com.kayipadalar.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.kayipadalar.logic.GameCore
import com.kayipadalar.logic.model.IslandType
import kotlin.math.sin

/**
 * Ana krallığın görsel temsili: bina seviyeleri arttıkça yapılar büyür, kale seviyesine göre
 * çevreye yeni uydu adalar ve köprüler eklenir, satın alınan dekorasyonlar adada görünür.
 */
class KingdomView(private val core: GameCore, private val shapes: ShapeRenderer) : Actor() {
    private var time = 0f

    override fun act(delta: Float) { super.act(delta); time += delta }

    private val slots = listOf(
        "LUMBER" to (-0.55f to 0.1f), "QUARRY" to (0.5f to 0.15f), "FARM" to (-0.25f to -0.3f), "CRYSTAL_LAB" to (0.3f to -0.35f),
        "BARRACKS" to (-0.6f to -0.15f), "HARBOR" to (0.65f to -0.12f), "ENGINEERING" to (-0.2f to 0.4f), "ACADEMY" to (0.22f to 0.42f),
        "TREASURY" to (0f to -0.5f), "WATCHTOWER" to (-0.75f to 0.35f), "MUSEUM" to (0.75f to 0.35f), "QUEST_HALL" to (-0.42f to -0.5f),
        "MAGIC_WORKSHOP" to (0.45f to -0.55f), "DEFENSE_TOWER" to (0f to 0.62f),
    )

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.end()
        val sr = shapes
        sr.projectionMatrix = batch.projectionMatrix
        sr.begin(ShapeRenderer.ShapeType.Filled)
        val cx = x + width / 2; val cy = y + height * 0.52f + sin(time) * 4f
        val castleLv = core.kingdom.level("CASTLE")
        val size = (width * (0.62f + castleLv * 0.025f)).coerceAtMost(width * 0.9f)
        val tw = size; val th = size / 2

        // Uydu adalar ve köprüler (kale seviyesiyle artar)
        val sats = listOf(IslandType.FOREST, IslandType.FARM, IslandType.MINE, IslandType.CRYSTAL, IslandType.WATER, IslandType.TRADE)
        val satCount = (castleLv + 1) / 2
        for (k in 0 until satCount.coerceAtMost(sats.size)) {
            val ang = -0.4f + k * 1.05f
            val sx = cx + kotlin.math.cos(ang) * width * 0.42f
            val sy = cy - height * 0.33f + kotlin.math.sin(ang) * height * 0.08f + sin(time * 1.3f + k) * 5f
            sr.color = Color.valueOf("C49A6C")
            sr.rectLine(cx + (sx - cx) * 0.45f, cy - th * 0.2f + (sy - cy) * 0.45f, sx, sy, 10f)
            IslandArt.body(sr, sx, sy, tw * 0.28f, th * 0.28f, IslandArt.topColors[sats[k]]!!)
            IslandArt.decor(sr, sats[k], sx, sy, tw * 0.28f, th * 0.28f, time, null, false)
        }

        IslandArt.body(sr, cx, cy, tw, th, IslandArt.topColors[IslandType.CASTLE]!!, scale = 1f)
        val castleColor = core.shop.equippedColor("CASTLE_SKIN")?.takeIf { it.isNotEmpty() }?.let { Color.valueOf(it) }

        // Binalar arka sıradan öne doğru çizilir.
        val drawList = slots.filter { core.kingdom.level(it.first) > 0 }.sortedByDescending { it.second.second }
        for ((id, pos) in drawList) {
            val lv = core.kingdom.level(id)
            val bx = cx + pos.first * tw / 2; val by = cy + pos.second * th / 2
            building(sr, id, bx, by, 0.8f + lv * 0.07f)
        }
        // Dekorasyonlar
        val decos = core.save.owned.filter { it.startsWith("DEC_") }
        for ((n, d) in decos.withIndex()) {
            val item = core.meta.shop.first { it.id == d }
            val px = cx + (-0.82f + n * 0.3f) * tw / 2; val py = cy - th * 0.3f
            sr.color = Color.valueOf(item.color)
            sr.circle(px, py + 10f, 9f, 10)
            sr.color = Color.valueOf("8D6E63"); sr.rect(px - 2f, py - 2f, 4f, 10f)
        }
        // Kale (merkez)
        IslandArt.decor(sr, IslandType.CASTLE, cx, cy - th * 0.05f, tw * (0.5f + castleLv * 0.03f), th * (0.5f + castleLv * 0.03f), time, castleColor, false)
        sr.end()
        batch.begin()
    }

    private fun building(sr: ShapeRenderer, id: String, x: Float, y: Float, k: Float) {
        val s = k * 1.2f
        val (wall, roof) = when (id) {
            "LUMBER" -> "D7A86E" to "6D4C41"; "QUARRY" -> "B0BEC5" to "546E7A"; "FARM" -> "FFF3DC" to "C0392B"
            "CRYSTAL_LAB" -> "EDE7F6" to "7E57C2"; "BARRACKS" -> "FFE0B2" to "C62828"; "HARBOR" -> "E1F5FE" to "0277BD"
            "ENGINEERING" -> "ECEFF1" to "FF8F00"; "ACADEMY" -> "FFFDE7" to "3949AB"; "TREASURY" -> "FFF8E1" to "F9A825"
            "WATCHTOWER" -> "D7CCC8" to "5D4037"; "MUSEUM" -> "FAFAFA" to "8D6E63"; "QUEST_HALL" -> "E8F5E9" to "2E7D32"
            "MAGIC_WORKSHOP" -> "F3E5F5" to "AB47BC"; else -> "CFD8DC" to "455A64"
        }
        val tall = id == "WATCHTOWER" || id == "DEFENSE_TOWER" || id == "MAGIC_WORKSHOP"
        val w = 22f * s; val h = (if (tall) 34f else 18f) * s
        sr.color = Color(0f, 0f, 0f, 0.12f); sr.ellipse(x - w * 0.7f, y - 4f * s, w * 1.4f, 8f * s)
        sr.color = Color.valueOf(wall); sr.rect(x - w / 2, y, w, h)
        sr.color = Color.valueOf(roof); sr.triangle(x - w * 0.62f, y + h, x + w * 0.62f, y + h, x, y + h + 14f * s)
        sr.color = Color.valueOf("8B5A2B"); sr.rect(x - 3f * s, y, 6f * s, 8f * s)
    }
}
