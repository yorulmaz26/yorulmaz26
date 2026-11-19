package com.kayipadalar.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.kayipadalar.logic.model.IslandType
import kotlin.math.sin

/** Ada türlerinin prosedürel izometrik çizimleri (doku dosyası gerektirmez). */
object IslandArt {
    fun c(hex: String): Color = Color.valueOf(hex)

    val topColors: Map<IslandType, Color> = mapOf(
        IslandType.CASTLE to c("9BD768"), IslandType.FOREST to c("6CC24A"), IslandType.MINE to c("C4BBAE"),
        IslandType.FARM to c("EAD675"), IslandType.WATER to c("6EC6F5"), IslandType.FIRE to c("FF9A62"),
        IslandType.ICE to c("C7F1FF"), IslandType.WIND to c("CFF3E0"), IslandType.ELECTRIC to c("FFE76A"),
        IslandType.CRYSTAL to c("D2B3FF"), IslandType.MILITARY to c("E0B07A"), IslandType.FORT to c("CFC6B4"),
        IslandType.TRADE to c("FFCF7E"), IslandType.POISON to c("B3E07A"), IslandType.FOG to c("E3E8EF"),
        IslandType.TIME to c("F6DDAE"), IslandType.GRAVITY to c("ADA0E3"), IslandType.PORTAL to c("F5AEDC"),
        IslandType.VOLCANO to c("D6906F"), IslandType.LIFE to c("FFC0D2"), IslandType.ANCIENT to c("F4D97A"),
        IslandType.VILLAGE to c("A9DE7E"), IslandType.EXIT to c("97E6CF"), IslandType.ENEMY_FORT to c("E9A0A0"),
        IslandType.BURNT to c("A3A3A3"),
    )

    val themeSky: Map<String, Pair<Color, Color>> = mapOf(
        "GREEN" to (c("7CC8FF") to c("E3F6FF")), "ROCK" to (c("8EC5F0") to c("EEF3F8")), "BRIDGE" to (c("86D0FF") to c("FFF4E0")),
        "FOG" to (c("B4CBE0") to c("F2F5F8")), "FIRE" to (c("FFB38A") to c("FFF0E0")), "ICE" to (c("A6E3FF") to c("F2FCFF")),
        "WIND" to (c("8EE0D2") to c("EFFFF8")), "CRYSTAL" to (c("C6A8FF") to c("F6F0FF")), "PORTAL" to (c("F3A6D8") to c("FFF0FA")),
        "IRON" to (c("9DB4CF") to c("EEF2F7")), "CORAL" to (c("FFAFA3") to c("FFF3EE")), "DESERT" to (c("FFD08A") to c("FFF8E8")),
        "AUTUMN" to (c("FFC48A") to c("FFF6EA")), "SAKURA" to (c("FFB7D5") to c("FFF3F8")), "STORM" to (c("9FB8E8") to c("EEF2FB")),
        "SWAMP" to (c("A7D9A0") to c("F1FAEE")), "GOLD" to (c("FFD86B") to c("FFFBEA")), "MOON" to (c("BFB5F2") to c("F5F3FF")),
        "CLOUD" to (c("A9D8FF") to c("FFFFFF")), "JUNGLE" to (c("8FDCA0") to c("F0FFF2")),
    )

    private fun shade(col: Color, f: Float) = Color(col.r * f, col.g * f, col.b * f, col.a)

    /** Ada gövdesi: üst eşkenar dörtgen, yan yüzler ve yüzen kaya tabanı. */
    fun body(sr: ShapeRenderer, cx: Float, cy: Float, tw: Float, th: Float, top: Color, alpha: Float = 1f, scale: Float = 0.92f) {
        val hw = tw / 2 * scale; val hh = th / 2 * scale
        val d = th * 0.5f
        val left = shade(top, 0.78f).also { it.a = alpha }
        val right = shade(top, 0.62f).also { it.a = alpha }
        val rock = Color(0.72f, 0.55f, 0.38f, alpha)
        val rockDark = Color(0.6f, 0.44f, 0.3f, alpha)
        // taban kayası
        sr.color = rockDark
        sr.triangle(cx - hw * 0.8f, cy - d, cx + hw * 0.8f, cy - d, cx + hw * 0.1f, cy - d - th * 1.05f)
        sr.color = rock
        sr.triangle(cx - hw * 0.8f, cy - d, cx, cy - hh - d, cx - hw * 0.15f, cy - d - th * 0.95f)
        // yan yüzler
        sr.color = Color(0.82f, 0.64f, 0.45f, alpha)
        sr.triangle(cx - hw, cy, cx, cy - hh, cx, cy - hh - d); sr.triangle(cx - hw, cy, cx, cy - hh - d, cx - hw, cy - d)
        sr.color = Color(0.7f, 0.52f, 0.36f, alpha)
        sr.triangle(cx + hw, cy, cx, cy - hh, cx, cy - hh - d); sr.triangle(cx + hw, cy, cx, cy - hh - d, cx + hw, cy - d)
        // çim/üst katman kenarı
        sr.color = left
        sr.triangle(cx - hw, cy, cx, cy - hh, cx, cy - hh - d * 0.25f); sr.triangle(cx - hw, cy, cx, cy - hh - d * 0.25f, cx - hw, cy - d * 0.25f)
        sr.color = right
        sr.triangle(cx + hw, cy, cx, cy - hh, cx, cy - hh - d * 0.25f); sr.triangle(cx + hw, cy, cx, cy - hh - d * 0.25f, cx + hw, cy - d * 0.25f)
        // üst
        sr.color = Color(top.r, top.g, top.b, alpha)
        sr.triangle(cx - hw, cy, cx, cy + hh, cx + hw, cy); sr.triangle(cx - hw, cy, cx, cy - hh, cx + hw, cy)
        sr.color = Color(1f, 1f, 1f, 0.18f * alpha)
        sr.triangle(cx - hw * 0.7f, cy + hh * 0.05f, cx, cy + hh * 0.75f, cx + hw * 0.1f, cy + hh * 0.55f)
    }

    fun outline(sr: ShapeRenderer, cx: Float, cy: Float, tw: Float, th: Float, color: Color, width: Float = 4f, scale: Float = 0.98f) {
        val hw = tw / 2 * scale; val hh = th / 2 * scale
        sr.color = color
        sr.rectLine(cx - hw, cy, cx, cy + hh, width); sr.rectLine(cx, cy + hh, cx + hw, cy, width)
        sr.rectLine(cx + hw, cy, cx, cy - hh, width); sr.rectLine(cx, cy - hh, cx - hw, cy, width)
    }

    /** Ada üstü süslemeleri. [t] animasyon zamanı, [s] ölçek (karo genişliği/140). */
    fun decor(sr: ShapeRenderer, type: IslandType, cx: Float, cy: Float, tw: Float, th: Float, t: Float, accent: Color?, collected: Boolean) {
        val s = tw / 140f
        fun tree(x: Float, y: Float, k: Float = 1f) {
            sr.color = c("8B5A2B"); sr.rect(x - 3 * s * k, y, 6 * s * k, 10 * s * k)
            sr.color = c("3E9E3E"); sr.triangle(x - 15 * s * k, y + 8 * s * k, x + 15 * s * k, y + 8 * s * k, x, y + 34 * s * k)
            sr.color = c("56B84F"); sr.triangle(x - 11 * s * k, y + 20 * s * k, x + 11 * s * k, y + 20 * s * k, x, y + 42 * s * k)
        }
        fun house(x: Float, y: Float, roof: Color) {
            sr.color = c("FFF3DC"); sr.rect(x - 11 * s, y, 22 * s, 16 * s)
            sr.color = roof; sr.triangle(x - 15 * s, y + 16 * s, x + 15 * s, y + 16 * s, x, y + 30 * s)
            sr.color = c("8B5A2B"); sr.rect(x - 3 * s, y, 6 * s, 9 * s)
        }
        when (type) {
            IslandType.CASTLE -> {
                val wall = accent ?: c("F1EDE4")
                val roof = c("4C8DF6")
                sr.color = shade(wall, 0.85f); sr.rect(cx - 30 * s, cy - 4 * s, 60 * s, 30 * s)
                sr.color = wall; sr.rect(cx - 30 * s, cy - 4 * s, 60 * s, 26 * s)
                for (dx in listOf(-34f, 22f)) {
                    sr.color = wall; sr.rect(cx + dx * s, cy - 6 * s, 12 * s, 46 * s)
                    sr.color = roof; sr.triangle(cx + (dx - 3) * s, cy + 40 * s, cx + (dx + 15) * s, cy + 40 * s, cx + (dx + 6) * s, cy + 58 * s)
                }
                sr.color = wall; sr.rect(cx - 9 * s, cy + 18 * s, 18 * s, 34 * s)
                sr.color = roof; sr.triangle(cx - 13 * s, cy + 52 * s, cx + 13 * s, cy + 52 * s, cx, cy + 74 * s)
                sr.color = c("E53935"); sr.triangle(cx, cy + 74 * s, cx, cy + 86 * s, cx + 14 * s + sin(t * 4) * 2 * s, cy + 81 * s)
                sr.color = c("8B5A2B"); sr.rect(cx - 6 * s, cy - 4 * s, 12 * s, 14 * s)
            }
            IslandType.FOREST -> { tree(cx - 20 * s, cy - 6 * s); tree(cx + 16 * s, cy - 2 * s, 1.1f); tree(cx - 2 * s, cy + 8 * s, 0.85f) }
            IslandType.MINE -> {
                sr.color = c("8E8E8E"); sr.circle(cx - 12 * s, cy + 4 * s, 16 * s, 12)
                sr.color = c("A9A9A9"); sr.circle(cx + 12 * s, cy + 2 * s, 13 * s, 12)
                sr.color = c("5D4037"); sr.triangle(cx - 10 * s, cy - 4 * s, cx + 10 * s, cy - 4 * s, cx, cy + 14 * s)
                sr.color = c("FF9800"); sr.circle(cx + 14 * s, cy + 8 * s, 4 * s, 8)
            }
            IslandType.FARM -> {
                sr.color = c("D4A72C")
                for (i in -2..2) sr.rectLine(cx + i * 10 * s - 14 * s, cy - 6 * s + i * 2 * s, cx + i * 10 * s + 14 * s, cy + 8 * s + i * 2 * s, 4 * s)
                sr.color = c("C0392B"); sr.rect(cx + 16 * s, cy + 6 * s, 16 * s, 14 * s)
                sr.color = c("8E2C22"); sr.triangle(cx + 14 * s, cy + 20 * s, cx + 34 * s, cy + 20 * s, cx + 24 * s, cy + 30 * s)
            }
            IslandType.WATER -> {
                sr.color = c("3FA9E8"); sr.ellipse(cx - 28 * s, cy - 10 * s, 56 * s, 22 * s)
                sr.color = Color(1f, 1f, 1f, 0.7f)
                val w = sin(t * 2f) * 4 * s
                sr.ellipse(cx - 14 * s + w, cy - 3 * s, 20 * s, 6 * s)
            }
            IslandType.FIRE -> {
                val f = sin(t * 9f) * 3 * s
                sr.color = c("FF5722"); sr.triangle(cx - 16 * s, cy - 4 * s, cx + 16 * s, cy - 4 * s, cx + f, cy + 40 * s)
                sr.color = c("FFB300"); sr.triangle(cx - 9 * s, cy - 2 * s, cx + 9 * s, cy - 2 * s, cx - f, cy + 26 * s)
                sr.color = c("FFF176"); sr.triangle(cx - 4 * s, cy, cx + 4 * s, cy, cx, cy + 12 * s)
            }
            IslandType.ICE -> {
                sr.color = c("FFFFFF")
                sr.triangle(cx - 18 * s, cy - 4 * s, cx - 6 * s, cy - 4 * s, cx - 12 * s, cy + 26 * s)
                sr.triangle(cx - 4 * s, cy - 2 * s, cx + 12 * s, cy - 2 * s, cx + 4 * s, cy + 36 * s)
                sr.color = c("81D4FA"); sr.triangle(cx + 10 * s, cy - 6 * s, cx + 22 * s, cy - 6 * s, cx + 16 * s, cy + 18 * s)
            }
            IslandType.WIND -> {
                sr.color = c("5FBF9A")
                for (i in 0..2) {
                    val a = t * 3f + i * 2.1f
                    sr.rectLine(cx, cy + 8 * s, cx + MathUtils.cos(a) * 22 * s, cy + 8 * s + MathUtils.sin(a) * 11 * s, 4 * s)
                }
                sr.color = c("8D6E63"); sr.rect(cx - 2 * s, cy - 6 * s, 4 * s, 16 * s)
            }
            IslandType.ELECTRIC -> {
                sr.color = c("F9A825")
                sr.rectLine(cx - 8 * s, cy + 34 * s, cx + 4 * s, cy + 14 * s, 6 * s)
                sr.rectLine(cx + 4 * s, cy + 14 * s, cx - 6 * s, cy + 12 * s, 6 * s)
                sr.rectLine(cx - 6 * s, cy + 12 * s, cx + 8 * s, cy - 6 * s, 6 * s)
            }
            IslandType.CRYSTAL -> {
                val glow = 0.6f + 0.4f * sin(t * 3f)
                sr.color = Color(0.7f, 0.5f, 1f, 0.35f * glow); sr.circle(cx, cy + 14 * s, 26 * s, 16)
                sr.color = c("9C6BFF"); sr.triangle(cx - 10 * s, cy, cx + 10 * s, cy, cx, cy + 40 * s)
                sr.color = c("C7A6FF"); sr.triangle(cx - 22 * s, cy - 2 * s, cx - 8 * s, cy - 2 * s, cx - 15 * s, cy + 22 * s)
                sr.color = c("7E57C2"); sr.triangle(cx + 8 * s, cy - 2 * s, cx + 22 * s, cy - 2 * s, cx + 15 * s, cy + 26 * s)
            }
            IslandType.MILITARY -> {
                sr.color = c("C62828"); sr.triangle(cx - 22 * s, cy - 4 * s, cx + 22 * s, cy - 4 * s, cx, cy + 26 * s)
                sr.color = c("FFCDD2"); sr.triangle(cx - 6 * s, cy - 4 * s, cx + 6 * s, cy - 4 * s, cx, cy + 12 * s)
                sr.color = c("5D4037"); sr.rect(cx + 20 * s, cy - 2 * s, 3 * s, 34 * s)
                sr.color = c("1E88E5"); sr.triangle(cx + 23 * s, cy + 32 * s, cx + 23 * s, cy + 22 * s, cx + 36 * s, cy + 27 * s)
            }
            IslandType.FORT -> {
                sr.color = c("A1887F"); sr.rect(cx - 14 * s, cy - 4 * s, 28 * s, 40 * s)
                sr.color = c("8D6E63"); for (i in 0..2) sr.rect(cx - 14 * s + i * 11 * s, cy + 36 * s, 6 * s, 7 * s)
                sr.color = c("1E88E5"); sr.triangle(cx, cy + 43 * s, cx, cy + 55 * s, cx + 12 * s, cy + 49 * s)
            }
            IslandType.TRADE -> {
                sr.color = c("8D6E63"); sr.rect(cx - 20 * s, cy - 4 * s, 40 * s, 16 * s)
                for (i in 0..3) { sr.color = if (i % 2 == 0) c("E53935") else c("FFFFFF"); sr.rect(cx - 22 * s + i * 11 * s, cy + 12 * s, 11 * s, 10 * s) }
                sr.color = Color.valueOf("F5B700"); sr.circle(cx - 8 * s, cy + 2 * s, 5 * s, 8); sr.circle(cx + 6 * s, cy + 2 * s, 5 * s, 8)
            }
            IslandType.POISON -> {
                sr.color = c("7CB342")
                for (i in 0..3) { val yy = ((t * 20 + i * 11) % 34) * s; sr.circle(cx - 14 * s + i * 9 * s, cy + yy, (6 - i) * s, 10) }
                sr.color = c("558B2F"); sr.ellipse(cx - 22 * s, cy - 8 * s, 44 * s, 14 * s)
            }
            IslandType.FOG -> {
                sr.color = Color(1f, 1f, 1f, 0.9f)
                sr.circle(cx - 12 * s, cy + 8 * s, 14 * s, 14); sr.circle(cx + 10 * s, cy + 12 * s, 16 * s, 14); sr.circle(cx, cy + 22 * s, 12 * s, 14)
            }
            IslandType.TIME -> {
                sr.color = c("8D6E63"); sr.rect(cx - 16 * s, cy - 4 * s, 32 * s, 4 * s); sr.rect(cx - 16 * s, cy + 40 * s, 32 * s, 4 * s)
                sr.color = c("FFE0B2"); sr.triangle(cx - 12 * s, cy + 40 * s, cx + 12 * s, cy + 40 * s, cx, cy + 20 * s)
                sr.triangle(cx - 12 * s, cy, cx + 12 * s, cy, cx, cy + 20 * s)
                sr.color = c("FFB300"); sr.triangle(cx - 7 * s, cy + 1 * s, cx + 7 * s, cy + 1 * s, cx, cy + 12 * s)
            }
            IslandType.GRAVITY -> {
                for (i in 3 downTo 1) {
                    sr.color = if (i % 2 == 0) c("7E57C2") else c("B39DDB")
                    sr.ellipse(cx - i * 9 * s, cy - i * 4.5f * s + 6 * s, i * 18 * s, i * 9 * s)
                }
                sr.color = c("311B92"); sr.circle(cx, cy + 6 * s, 4 * s, 8)
            }
            IslandType.PORTAL -> {
                val p = 1f + 0.1f * sin(t * 4f)
                sr.color = c("D81B60"); sr.ellipse(cx - 18 * s * p, cy - 2 * s, 36 * s * p, 46 * s * p)
                sr.color = c("F8BBD0"); sr.ellipse(cx - 12 * s * p, cy + 4 * s, 24 * s * p, 34 * s * p)
                sr.color = Color(1f, 1f, 1f, 0.6f); sr.ellipse(cx - 6 * s, cy + 10 * s, 12 * s, 20 * s)
            }
            IslandType.VOLCANO -> {
                sr.color = c("8D5A42"); sr.triangle(cx - 30 * s, cy - 6 * s, cx + 30 * s, cy - 6 * s, cx, cy + 38 * s)
                sr.color = c("E64A19"); sr.triangle(cx - 9 * s, cy + 28 * s, cx + 9 * s, cy + 28 * s, cx, cy + 38 * s)
                sr.color = Color(0.6f, 0.6f, 0.6f, 0.5f); sr.circle(cx + 4 * s + sin(t) * 3 * s, cy + 48 * s + (t * 8 % 10) * s, 7 * s, 10)
            }
            IslandType.LIFE -> {
                sr.color = c("EC407A"); sr.circle(cx - 7 * s, cy + 16 * s, 9 * s, 12); sr.circle(cx + 7 * s, cy + 16 * s, 9 * s, 12)
                sr.triangle(cx - 16 * s, cy + 13 * s, cx + 16 * s, cy + 13 * s, cx, cy - 6 * s)
                sr.color = c("66BB6A"); sr.rect(cx - 2 * s, cy - 8 * s, 4 * s, 6 * s)
            }
            IslandType.ANCIENT -> {
                sr.color = c("C9A227"); sr.rect(cx - 9 * s, cy - 4 * s, 18 * s, 44 * s)
                sr.color = c("FFE082"); sr.triangle(cx - 9 * s, cy + 40 * s, cx + 9 * s, cy + 40 * s, cx, cy + 54 * s)
                sr.color = c("8D6E63"); sr.rect(cx - 15 * s, cy - 6 * s, 30 * s, 5 * s)
            }
            IslandType.VILLAGE -> {
                house(cx - 14 * s, cy - 4 * s, c("E57373")); house(cx + 14 * s, cy, c("FFB74D"))
                if (collected) { sr.color = c("43A047"); sr.rect(cx, cy + 20 * s, 3 * s, 28 * s); sr.triangle(cx + 3 * s, cy + 48 * s, cx + 3 * s, cy + 38 * s, cx + 18 * s, cy + 43 * s) }
            }
            IslandType.EXIT -> {
                sr.color = c("26A69A"); sr.rect(cx - 18 * s, cy - 4 * s, 8 * s, 38 * s); sr.rect(cx + 10 * s, cy - 4 * s, 8 * s, 38 * s)
                sr.rect(cx - 20 * s, cy + 32 * s, 40 * s, 8 * s)
                sr.color = Color(0.7f, 1f, 0.95f, 0.6f + 0.3f * sin(t * 3)); sr.rect(cx - 10 * s, cy - 2 * s, 20 * s, 34 * s)
            }
            IslandType.ENEMY_FORT -> {
                sr.color = c("8E5B5B"); sr.rect(cx - 16 * s, cy - 4 * s, 32 * s, 40 * s)
                sr.color = c("6D4444"); for (i in 0..2) sr.rect(cx - 16 * s + i * 12 * s, cy + 36 * s, 7 * s, 8 * s)
                sr.color = c("D32F2F"); sr.rect(cx - 1 * s, cy + 44 * s, 3 * s, 18 * s)
                sr.triangle(cx + 2 * s, cy + 62 * s, cx + 2 * s, cy + 52 * s, cx + 16 * s + sin(t * 4) * 2 * s, cy + 57 * s)
            }
            IslandType.BURNT -> {
                sr.color = c("5D5D5D"); sr.rect(cx - 16 * s, cy - 2 * s, 5 * s, 16 * s); sr.rect(cx + 8 * s, cy, 5 * s, 12 * s)
                sr.color = Color(0.5f, 0.5f, 0.5f, 0.5f); sr.circle(cx - 4 * s, cy + 26 * s + (t * 6 % 8) * s, 6 * s, 10)
            }
        }
    }
}
