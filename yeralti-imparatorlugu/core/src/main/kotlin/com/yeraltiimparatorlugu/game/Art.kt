package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.SourceKind
import com.yeraltiimparatorlugu.sim.Terrain
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bütün görseller açılışta kodla (Pixmap) tek bir doku atlasına çizilir: harici görsel dosyası gerekmez,
 * tek doku kullanıldığı için çizim çağrıları toplu (batch) kalır.
 */
class Art(uiScale: Float) : Disposable {
    private val size = 1024
    private val pm = Pixmap(size, size, Pixmap.Format.RGBA8888).apply { blending = Pixmap.Blending.SourceOver }
    private var cursorX = 0
    private var cursorY = 0
    private var rowH = 0
    private val pending = ArrayList<Pair<TextureRegion, IntArray>>()

    lateinit var texture: Texture
        private set

    val white: TextureRegion
    val tiles = HashMap<Terrain, Array<TextureRegion>>()
    val unexplored: Array<TextureRegion>
    val tunnel: Array<TextureRegion>
    val antFrames: Array<TextureRegion>
    val queenFrames: Array<TextureRegion>
    /** Gri tonlu karınca kareleri: sınıf/kozmetik rengiyle boyanarak çizilir. */
    val grayAnt: Array<TextureRegion>
    val grayStripedAnt: Array<TextureRegion>
    val soldierFrames: Array<TextureRegion>
    val creatures = HashMap<CreatureKind, Array<TextureRegion>>()
    val tree: TextureRegion
    val bush: TextureRegion
    val rock: TextureRegion
    val brick: TextureRegion
    val pond: TextureRegion
    val flag: TextureRegion
    val drop: TextureRegion
    val snow: TextureRegion
    val statue: TextureRegion
    val lamp: TextureRegion
    val corpse: TextureRegion
    val crownFlower: TextureRegion
    val crownCrystal: TextureRegion
    val cactus: TextureRegion
    val reed: TextureRegion
    val egg: TextureRegion
    val larva: TextureRegion
    val pupa: TextureRegion
    val icons = HashMap<ResourceType, TextureRegion>()
    val sources = HashMap<SourceKind, TextureRegion>()
    val mushroom: TextureRegion
    val bed: TextureRegion
    val cloud: TextureRegion
    val sun: TextureRegion
    val tuft: TextureRegion
    val flower: TextureRegion
    val circle: TextureRegion
    val ring: TextureRegion
    val stripes: TextureRegion
    val crown: TextureRegion
    val panelPatch: NinePatch
    val fontSmall: BitmapFont
    val font: BitmapFont
    val fontBig: BitmapFont

    init {
        white = alloc(4, 4) { x, y, w, h -> fillRect(x, y, w, h, Color.WHITE) }
        for (t in Terrain.entries) {
            if (t == Terrain.AIR || t == Terrain.TUNNEL) continue
            tiles[t] = Array(3) { v -> alloc(32, 32) { x, y, _, _ -> drawTerrain(x, y, t, v) } }
        }
        unexplored = Array(3) { v -> alloc(32, 32) { x, y, _, _ -> drawUnexplored(x, y, v) } }
        tunnel = Array(3) { v -> alloc(32, 32) { x, y, _, _ -> drawTunnel(x, y, v) } }
        antFrames = Array(4) { f -> alloc(64, 64) { x, y, _, _ -> drawAnt(x, y, f, false) } }
        queenFrames = Array(2) { f -> alloc(96, 96) { x, y, _, _ -> drawAnt(x, y, f, true) } }
        grayAnt = Array(4) { f -> alloc(64, 64) { x, y, _, _ -> drawAnt(x, y, f, false, gray = true) } }
        grayStripedAnt = Array(4) { f -> alloc(64, 64) { x, y, _, _ -> drawAnt(x, y, f, false, gray = true, stripes = true) } }
        soldierFrames = Array(4) { f -> alloc(64, 64) { x, y, _, _ -> drawAnt(x, y, f, false, gray = true, soldier = true) } }
        for (k in CreatureKind.entries) creatures[k] = Array(2) { f -> alloc(64, 64) { x, y, _, _ -> drawCreature(x, y, k, f) } }
        tree = alloc(64, 96) { x, y, _, _ ->
            fillRect(x + 28, y + 50, 8, 46, Palette.hex("8A5A36"))
            for ((dx, dy, r) in listOf(Triple(32, 30, 22), Triple(18, 42, 15), Triple(46, 42, 15), Triple(32, 16, 14))) dot(x + dx, y + dy, r, if (r > 18) Palette.hex("5DBB63") else Palette.hex("6FCB5A"))
        }
        bush = alloc(48, 32) { x, y, _, _ -> for ((dx, r) in listOf(14 to 12, 26 to 15, 36 to 11)) dot(x + dx, y + 20, r, Palette.hex("6FCB5A")); dot(x + 20, y + 14, 3, Palette.hex("FF8FB1")) }
        rock = alloc(48, 32) { x, y, _, _ -> ellipse(x + 24, y + 20, 20, 11, Palette.hex("B9B2AA")); ellipse(x + 18, y + 16, 8, 4, Palette.hex("D8D2CA")) }
        brick = alloc(48, 32) { x, y, _, _ ->
            fillRect(x + 4, y + 10, 40, 20, Palette.hex("D9805E")); for (i in 0..1) line(x + 4, y + 20 + i, x + 43, y + 20 + i, Palette.hex("F0C9B0"))
            line(x + 24, y + 10, x + 24, y + 20, Palette.hex("F0C9B0")); line(x + 14, y + 21, x + 14, y + 29, Palette.hex("F0C9B0")); line(x + 34, y + 21, x + 34, y + 29, Palette.hex("F0C9B0"))
        }
        pond = alloc(96, 24) { x, y, _, _ -> ellipse(x + 48, y + 12, 46, 10, Palette.hex("7EC8E3")); ellipse(x + 36, y + 9, 16, 3, Color(1f, 1f, 1f, 0.6f)) }
        flag = alloc(32, 48) { x, y, _, _ -> fillRect(x + 6, y + 4, 3, 44, Palette.hex("8A5A36")); pm.setColor(Color.WHITE); pm.fillTriangle(x + 9, y + 4, x + 30, y + 12, x + 9, y + 20) }
        drop = alloc(8, 16) { x, y, _, _ -> fillRect(x + 3, y, 2, 16, Color(0.55f, 0.75f, 0.95f, 0.85f)) }
        snow = alloc(16, 16) { x, y, _, _ -> dot(x + 8, y + 8, 5, Color.WHITE) }
        statue = alloc(48, 64) { x, y, _, _ ->
            val st = Palette.hex("D8D2CA"); fillRect(x + 8, y + 52, 32, 12, Palette.hex("B9B2AA"))
            ellipse(x + 24, y + 40, 10, 12, st); ellipse(x + 24, y + 24, 6, 6, st); dot(x + 24, y + 13, 6, st)
            pm.setColor(Palette.yellow); pm.fillTriangle(x + 18, y + 8, x + 24, y, x + 30, y + 8)
        }
        lamp = alloc(32, 32) { x, y, _, _ -> dot(x + 16, y + 16, 15, Color(0.6f, 1f, 0.85f, 0.35f)); fillRect(x + 14, y + 16, 4, 12, Palette.hex("FFF6E5")); ellipse(x + 16, y + 15, 9, 6, Palette.hex("7FE8C8")) }
        corpse = alloc(32, 24) { x, y, _, _ -> val c = Palette.hex("9A8F86"); ellipse(x + 10, y + 12, 7, 5, c); ellipse(x + 20, y + 12, 4, 3, c); dot(x + 27, y + 12, 3, c); for (i in 0..2) line(x + 16 + i * 2, y + 14, x + 14 + i * 3, y + 22, c) }
        crownFlower = alloc(24, 16) { x, y, _, _ -> for (i in 0..3) dot(x + 4 + i * 5, y + 8, 4, if (i % 2 == 0) Palette.hex("FF8FB1") else Palette.yellow) }
        crownCrystal = alloc(24, 16) { x, y, _, _ -> val c = Palette.hex("2BC4C4"); fillRect(x + 3, y + 10, 18, 5, c); pm.setColor(c); pm.fillTriangle(x + 4, y + 10, x + 8, y, x + 12, y + 10); pm.fillTriangle(x + 12, y + 10, x + 16, y + 1, x + 20, y + 10) }
        cactus = alloc(32, 48) { x, y, _, _ -> val c = Palette.hex("6FAF5A"); fillRect(x + 12, y + 8, 8, 40, c); fillRect(x + 4, y + 18, 6, 14, c); fillRect(x + 22, y + 14, 6, 14, c); fillRect(x + 4, y + 28, 10, 4, c); fillRect(x + 18, y + 24, 10, 4, c) }
        reed = alloc(24, 48) { x, y, _, _ -> for (i in 0..3) { line(x + 4 + i * 5, y + 47, x + 3 + i * 5, y + 8 + i * 3, Palette.hex("6F9A4A")) }; ellipse(x + 8, y + 10, 2, 5, Palette.hex("8A5A36")); ellipse(x + 18, y + 14, 2, 5, Palette.hex("8A5A36")) }
        egg = alloc(16, 16) { x, y, _, _ -> ellipse(x + 8, y + 8, 5, 7, Palette.hex("FFFDF3")); ellipse(x + 6, y + 5, 1, 2, Color.WHITE) }
        larva = alloc(24, 16) { x, y, _, _ -> for (i in 0..4) ellipse(x + 4 + i * 4, y + 8 + (i % 2), 4, 5, Palette.hex("FFF1CF")); dot(x + 20, y + 7, 1, Palette.hex("8A6E55")) }
        pupa = alloc(24, 16) { x, y, _, _ -> ellipse(x + 12, y + 8, 10, 6, Palette.hex("E9C990")); for (i in 0..3) line(x + 6 + i * 4, y + 3, x + 6 + i * 4, y + 13, Palette.hex("C9A26A")) }
        for (r in ResourceType.entries) icons[r] = alloc(32, 32) { x, y, _, _ -> drawIcon(x, y, r) }
        for (s in SourceKind.entries) sources[s] = alloc(48, 48) { x, y, _, _ -> drawSource(x, y, s) }
        mushroom = alloc(24, 24) { x, y, _, _ ->
            fillRect(x + 10, y + 11, 4, 11, Palette.hex("FFF6E5"))
            ellipse(x + 12, y + 10, 10, 7, Palette.hex("F2A65A")); dot(x + 8, y + 8, 2, Color.WHITE); dot(x + 15, y + 6, 1, Color.WHITE)
        }
        bed = alloc(32, 16) { x, y, _, _ -> ellipse(x + 16, y + 9, 15, 6, Palette.hex("BFD0FF")); ellipse(x + 7, y + 7, 5, 4, Color.WHITE) }
        cloud = alloc(96, 48) { x, y, _, _ ->
            val c = Color(1f, 1f, 1f, 0.92f); ellipse(x + 30, y + 30, 22, 14, c); ellipse(x + 55, y + 24, 26, 18, c); ellipse(x + 74, y + 32, 18, 12, c)
        }
        sun = alloc(64, 64) { x, y, _, _ -> dot(x + 32, y + 32, 30, Color(1f, 0.93f, 0.55f, 0.45f)); dot(x + 32, y + 32, 22, Palette.hex("FFE27A")) }
        tuft = alloc(32, 16) { x, y, _, _ -> for (i in 0..6) line(x + 3 + i * 4, y + 15, x + 2 + i * 4 + (i % 3) * 2, y + 3 + (i % 2) * 4, if (i % 2 == 0) Palette.grass else Palette.grassDark) }
        flower = alloc(16, 24) { x, y, _, _ ->
            line(x + 8, y + 23, x + 8, y + 9, Palette.grassDark)
            for (a in 0 until 5) dot(x + 8 + (cos(a * 1.256) * 4).toInt(), y + 7 + (sin(a * 1.256) * 4).toInt(), 3, Palette.hex("FF8FB1"))
            dot(x + 8, y + 7, 2, Palette.yellow)
        }
        circle = alloc(128, 128) { x, y, w, _ -> softDisc(x, y, w, 0f) }
        ring = alloc(128, 128) { x, y, w, _ -> softDisc(x, y, w, 0.86f) }
        stripes = alloc(32, 32) { x, y, _, _ ->
            // Desen yalnızca kendi 32x32 bölgesine çizilir (atlas komşularına taşmaz).
            pm.setColor(Color.WHITE)
            for (py in 0 until 32) for (px in 0 until 32) if ((px + py) % 8 < 3) pm.drawPixel(x + px, y + py)
        }
        crown = alloc(24, 16) { x, y, _, _ ->
            val gold = Palette.hex("FFC93C"); fillRect(x + 3, y + 9, 18, 6, gold)
            pm.setColor(gold); pm.fillTriangle(x + 3, y + 10, x + 6, y + 1, x + 9, y + 10); pm.fillTriangle(x + 9, y + 10, x + 12, y, x + 15, y + 10); pm.fillTriangle(x + 15, y + 10, x + 18, y + 1, x + 21, y + 10)
        }
        val patch = alloc(48, 48) { x, y, w, h -> roundRect(x, y, w, h, 14, Color.WHITE) }
        texture = Texture(pm).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        for ((region, r) in pending) { region.texture = texture; region.setRegion(r[0], r[1], r[2], r[3]) }
        // Kenar taşmasını önlemek için atlas bölgeleri yarım piksel içeriden örneklenir.
        for ((region, _) in pending) inset(region)
        panelPatch = NinePatch(patch, 16, 16, 16, 16)
        pm.dispose()

        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans.ttf"))
        val genBold = FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans-Bold.ttf"))
        fun make(g: FreeTypeFontGenerator, px: Int): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
                size = (px * uiScale).toInt().coerceAtLeast(8)
                characters = FreeTypeFontGenerator.DEFAULT_CHARS + "çÇğĞıİöÖşŞüÜâÂîÎûÛ•→←↑↓×…–—−’“”°½✓⚠›★☆●○⚑"
                minFilter = Texture.TextureFilter.Linear
                magFilter = Texture.TextureFilter.Linear
                color = Color.WHITE
                incremental = false
            }
            return g.generateFont(p).apply { data.setScale(1f / uiScale); setUseIntegerPositions(false); data.markupEnabled = true }
        }
        fontSmall = make(gen, 21)
        font = make(gen, 25)
        fontBig = make(genBold, 32)
        gen.dispose()
        genBold.dispose()
    }

    private fun inset(r: TextureRegion) {
        val du = 0.5f / size
        r.setRegion(r.u + du, r.v + du, r.u2 - du, r.v2 - du)
    }

    /** Atlasta yer ayırıp [draw] ile çizer; bölge, doku oluşunca bağlanır. */
    private fun alloc(w: Int, h: Int, draw: (Int, Int, Int, Int) -> Unit): TextureRegion {
        if (cursorX + w + 2 > size) { cursorX = 0; cursorY += rowH + 2; rowH = 0 }
        check(cursorY + h <= size) { "Atlas doldu" }
        val x = cursorX
        val y = cursorY
        draw(x, y, w, h)
        cursorX += w + 2
        rowH = maxOf(rowH, h)
        val region = TextureRegion()
        pending.add(region to intArrayOf(x, y, w, h))
        return region
    }

    // ------------------------------------------------------------ Çizim yardımcıları

    private fun fillRect(x: Int, y: Int, w: Int, h: Int, c: Color) { pm.setColor(c); pm.fillRectangle(x, y, w, h) }
    private fun dot(x: Int, y: Int, r: Int, c: Color) { pm.setColor(c); pm.fillCircle(x, y, r) }
    private fun line(x1: Int, y1: Int, x2: Int, y2: Int, c: Color) { pm.setColor(c); pm.drawLine(x1, y1, x2, y2) }
    private fun thick(x1: Int, y1: Int, x2: Int, y2: Int, c: Color) { line(x1, y1, x2, y2, c); line(x1 + 1, y1, x2 + 1, y2, c); line(x1, y1 + 1, x2, y2 + 1, c) }

    /** Kenarı yumuşatılmış daire veya halka ([inner] > 0 ise iç yarıçap oranı). */
    private fun softDisc(x: Int, y: Int, size: Int, inner: Float) {
        val r = size / 2f - 1f
        val c = Color()
        for (py in 0 until size) for (px in 0 until size) {
            val d = kotlin.math.sqrt((px + 0.5f - size / 2f) * (px + 0.5f - size / 2f) + (py + 0.5f - size / 2f) * (py + 0.5f - size / 2f))
            var a = (r - d + 0.5f).coerceIn(0f, 1f)
            if (inner > 0f) a = minOf(a, (d - r * inner + 0.5f).coerceIn(0f, 1f))
            if (a <= 0f) continue
            c.set(1f, 1f, 1f, a)
            pm.drawPixel(x + px, y + py, Color.rgba8888(c))
        }
    }

    private fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, c: Color) {
        pm.setColor(c)
        for (yy in -ry..ry) {
            val span = (rx * kotlin.math.sqrt(1.0 - (yy * yy).toDouble() / (ry * ry).toDouble())).toInt()
            pm.drawLine(cx - span, cy + yy, cx + span, cy + yy)
        }
    }

    private fun roundRect(x: Int, y: Int, w: Int, h: Int, r: Int, c: Color) {
        pm.setColor(c)
        pm.fillRectangle(x + r, y, w - 2 * r, h)
        pm.fillRectangle(x, y + r, w, h - 2 * r)
        pm.fillCircle(x + r, y + r, r); pm.fillCircle(x + w - r - 1, y + r, r)
        pm.fillCircle(x + r, y + h - r - 1, r); pm.fillCircle(x + w - r - 1, y + h - r - 1, r)
    }

    private fun shade(c: Color, f: Float) = Color((c.r * f).coerceIn(0f, 1f), (c.g * f).coerceIn(0f, 1f), (c.b * f).coerceIn(0f, 1f), c.a)

    private fun speckle(x: Int, y: Int, base: Color, rnd: Random, n: Int, spread: Float) {
        repeat(n) {
            val f = 1f + (rnd.nextFloat() - 0.5f) * spread
            pm.setColor(shade(base, f))
            val px = x + rnd.nextInt(32)
            val py = y + rnd.nextInt(32)
            if (rnd.nextInt(3) == 0) pm.fillRectangle(px, py, 2, 2) else pm.drawPixel(px, py)
        }
    }

    private fun drawTerrain(x: Int, y: Int, t: Terrain, v: Int) {
        val rnd = Random(t.id * 131L + v)
        val base = Palette.terrain(t)
        fillRect(x, y, 32, 32, base)
        when (t) {
            Terrain.SOFT_SOIL -> speckle(x, y, base, rnd, 90, 0.22f)
            Terrain.HARD_SOIL -> {
                speckle(x, y, base, rnd, 70, 0.2f)
                repeat(4) { dot(x + 4 + rnd.nextInt(24), y + 4 + rnd.nextInt(24), 2 + rnd.nextInt(2), shade(base, 0.8f)) }
            }
            Terrain.CLAY -> {
                for (i in 0..3) { val yy = y + 4 + i * 8 + rnd.nextInt(3); line(x, yy, x + 31, yy + rnd.nextInt(3) - 1, shade(base, 1.12f)) }
                speckle(x, y, base, rnd, 30, 0.12f)
            }
            Terrain.SAND -> speckle(x, y, base, rnd, 140, 0.14f)
            Terrain.ROCK -> {
                speckle(x, y, base, rnd, 60, 0.18f)
                var cx = x + rnd.nextInt(32); var cy = y
                repeat(5) { val nx = (cx + rnd.nextInt(13) - 6).coerceIn(x, x + 31); val ny = (cy + 6).coerceAtMost(y + 31); line(cx, cy, nx, ny, shade(base, 0.72f)); cx = nx; cy = ny }
                dot(x + 8 + rnd.nextInt(16), y + 8 + rnd.nextInt(16), 4, shade(base, 1.12f))
            }
            Terrain.WATER_SOIL -> {
                speckle(x, y, base, rnd, 60, 0.15f)
                repeat(3) { val dx = x + 5 + rnd.nextInt(22); val dy = y + 5 + rnd.nextInt(22); dot(dx, dy, 3, Palette.hex("5FB8E6")); dot(dx - 1, dy - 1, 1, Color.WHITE) }
            }
            Terrain.FUNGUS -> {
                speckle(x, y, base, rnd, 50, 0.18f)
                repeat(3) { val mx = x + 5 + rnd.nextInt(22); val my = y + 8 + rnd.nextInt(18); fillRect(mx - 1, my, 2, 4, Palette.hex("FFF6E5")); ellipse(mx, my, 4, 3, Palette.hex("F7B267")) }
            }
            Terrain.MINERAL -> {
                speckle(x, y, base, rnd, 60, 0.2f)
                repeat(3) {
                    val mx = x + 6 + rnd.nextInt(20); val my = y + 6 + rnd.nextInt(20)
                    pm.setColor(Palette.hex("2BC4C4")); pm.fillTriangle(mx, my - 5, mx - 4, my + 3, mx + 4, my + 3)
                    pm.setColor(Palette.hex("A6F0EE")); pm.fillTriangle(mx, my - 4, mx - 1, my + 1, mx + 2, my)
                }
            }
            Terrain.FOSSIL -> {
                speckle(x, y, base, rnd, 60, 0.2f)
                val bone = Palette.hex("F6EEDB")
                thick(x + 8, y + 22, x + 23, y + 10, bone); dot(x + 7, y + 22, 3, bone); dot(x + 9, y + 25, 3, bone); dot(x + 24, y + 8, 3, bone); dot(x + 22, y + 11, 3, bone)
            }
            Terrain.RUBBLE -> {
                speckle(x, y, base, rnd, 40, 0.2f)
                repeat(7) { dot(x + 3 + rnd.nextInt(26), y + 3 + rnd.nextInt(26), 2 + rnd.nextInt(3), shade(base, 0.8f + rnd.nextFloat() * 0.4f)) }
            }
            Terrain.RARE_VEIN -> {
                speckle(x, y, base, rnd, 50, 0.18f)
                repeat(3) {
                    val mx = x + 6 + rnd.nextInt(20); val my = y + 6 + rnd.nextInt(20)
                    pm.setColor(Palette.hex("B07CE8")); pm.fillTriangle(mx, my - 6, mx - 4, my + 3, mx + 4, my + 3)
                    pm.setColor(Palette.hex("E6D2FF")); pm.fillTriangle(mx, my - 5, mx - 1, my, mx + 2, my)
                }
            }
            Terrain.ANCIENT_NEST -> {
                speckle(x, y, base, rnd, 40, 0.15f)
                ellipse(x + 16, y + 18, 11, 7, shade(base, 0.82f)); ellipse(x + 13, y + 17, 2, 3, Palette.hex("FFF6E5")); ellipse(x + 19, y + 19, 2, 3, Palette.hex("FFF6E5"))
            }
            Terrain.CREATURE_DEN -> {
                speckle(x, y, base, rnd, 40, 0.15f)
                ellipse(x + 16, y + 17, 11, 9, shade(base, 0.7f))
                for (a in 0 until 6) line(x + 16, y + 17, x + 16 + (cos(a * 1.05) * 14).toInt(), y + 17 + (sin(a * 1.05) * 12).toInt(), Color(1f, 1f, 1f, 0.55f))
                dot(x + 13, y + 16, 1, Palette.hex("FFD25A")); dot(x + 19, y + 16, 1, Palette.hex("FFD25A"))
            }
            Terrain.WATER_POCKET -> {
                speckle(x, y, base, rnd, 40, 0.08f)
                for (i in 0..2) line(x + 4 + i * 3, y + 9 + i * 8, x + 26 - i * 2, y + 9 + i * 8, Color(1f, 1f, 1f, 0.7f))
            }
            Terrain.FILL -> {
                speckle(x, y, base, rnd, 50, 0.15f)
                for (i in 0..3) line(x, y + 4 + i * 8, x + 31, y + 4 + i * 8, shade(base, 0.85f))
            }
            else -> {}
        }
    }

    private fun drawCreature(ox: Int, oy: Int, k: CreatureKind, f: Int) {
        val c = Palette.creature(k)
        val dark = shade(c, 0.75f)
        val cy = oy + 32
        val sw = if (f == 0) 3 else -3
        when (k) {
            CreatureKind.SPIDER, CreatureKind.CAVE_HUNTER -> {
                if (k == CreatureKind.SPIDER) {
                    for (i in 0..3) { val a = -0.9 + i * 0.6; thick(ox + 30, cy, ox + 30 + (cos(a) * 26).toInt() + sw * (i % 2), cy - (sin(a) * 4).toInt() - 22, dark); thick(ox + 30, cy, ox + 30 + (cos(a) * 26).toInt() - sw * (i % 2), cy + 22, dark) }
                    dot(ox + 22, cy, 12, c); dot(ox + 38, cy, 7, c); dot(ox + 41, cy - 3, 1, Color.WHITE); dot(ox + 41, cy + 3, 1, Color.WHITE)
                } else {
                    for (i in 0..2) { thick(ox + 20 + i * 10, cy, ox + 16 + i * 10 + sw, cy - 20, dark); thick(ox + 20 + i * 10, cy, ox + 16 + i * 10 - sw, cy + 20, dark) }
                    ellipse(ox + 28, cy, 22, 14, c); ellipse(ox + 24, cy - 5, 12, 4, shade(c, 1.25f)); dot(ox + 52, cy, 7, dark)
                    thick(ox + 56, cy - 4, ox + 63, cy - 12, dark); thick(ox + 56, cy + 4, ox + 63, cy + 12, dark)
                }
            }
            CreatureKind.CENTIPEDE -> for (i in 0..6) {
                val px = ox + 6 + i * 8
                val py = cy + (if ((i + f) % 2 == 0) 2 else -2)
                line(px, py, px - 3, py - 9, dark); line(px, py, px - 3, py + 9, dark)
                dot(px, py, 5, if (i == 6) dark else c)
            }
            CreatureKind.WORM -> for (i in 0..6) dot(ox + 8 + i * 7, cy + (if ((i + f) % 2 == 0) 2 else -2), 5, if (i == 4) shade(c, 0.85f) else c)
            CreatureKind.BEETLE_LARVA -> { for (i in 0..4) ellipse(ox + 14 + i * 8, cy + (i % 2), 6, 8, c); dot(ox + 52, cy, 4, Palette.hex("C9A26A")) }
            CreatureKind.CAVE_SALAMANDER -> {
                ellipse(ox + 30, cy, 18, 6, c); dot(ox + 50, cy, 6, c); thick(ox + 4, cy + sw, ox + 14, cy, c)
                for (i in 0..1) { thick(ox + 22 + i * 16, cy, ox + 18 + i * 16 + sw, cy - 10, c); thick(ox + 22 + i * 16, cy, ox + 18 + i * 16 - sw, cy + 10, c) }
                dot(ox + 52, cy - 2, 1, Palette.uiText)
            }
            else -> {
                // Termit ve rakip karıncalar: karınca biçimi
                val body = c
                for (i in 0..2) { val lx = ox + 30 + i * 4; thick(lx, cy, lx - 6 + sw + i * 3, cy - 14, dark); thick(lx, cy, lx - 6 - sw + i * 3, cy + 14, dark) }
                ellipse(ox + 16, cy, 11, 9, body); ellipse(ox + 31, cy, 7, 5, body)
                dot(ox + 44, cy, if (k == CreatureKind.TERMITE) 6 else 8, if (k == CreatureKind.TERMITE) Palette.hex("C9A26A") else body)
                thick(ox + 48, cy - 4, ox + 58, cy - 10, dark); thick(ox + 48, cy + 4, ox + 58, cy + 10, dark)
            }
        }
    }

    private fun drawUnexplored(x: Int, y: Int, v: Int) {
        val rnd = Random(900L + v)
        val base = Palette.unexplored
        fillRect(x, y, 32, 32, base)
        speckle(x, y, base, rnd, 80, 0.16f)
        for (i in 0 until 4) dot(x + 4 + i * 8, y + 4 + ((i + v) % 3) * 10, 1, shade(base, 0.85f))
    }

    private fun drawTunnel(x: Int, y: Int, v: Int) {
        val rnd = Random(500L + v)
        val base = Palette.tunnel
        fillRect(x, y, 32, 32, base)
        speckle(x, y, base, rnd, 45, 0.08f)
    }

    /** Sağa bakan karınca; [frame] bacak adımını belirler. */
    private fun drawAnt(ox: Int, oy: Int, frame: Int, queen: Boolean, gray: Boolean = false, soldier: Boolean = false, stripes: Boolean = false) {
        val s = if (queen) 1.5f else 1f
        val body = when { queen -> Palette.hex("8A3E16"); gray -> Palette.hex("D6D6D6"); else -> Palette.hex("4A2C17") }
        val hi = when { queen -> Palette.hex("C9703A"); gray -> Color.WHITE; else -> Palette.hex("7A4A2A") }
        val cy = oy + (32 * s).toInt()
        fun sx(v: Float) = ox + (v * s).toInt()
        val legColor = shade(body, 0.85f)
        val swing = if (frame % 2 == 0) 4 else -4
        for (i in 0..2) {
            val lx = sx(30f + i * 4f)
            val off = if (i == 1) -swing else swing
            thick(lx, cy, lx - 6 + off + i * 3, cy - (14 * s).toInt(), legColor)
            thick(lx, cy, lx - 6 - off + i * 3, cy + (14 * s).toInt(), legColor)
        }
        if (queen) {
            val wing = Color(0.85f, 0.95f, 1f, 0.55f)
            ellipse(sx(22f), cy - (9 * s).toInt(), (14 * s).toInt(), (6 * s).toInt(), wing)
            ellipse(sx(22f), cy + (9 * s).toInt(), (14 * s).toInt(), (6 * s).toInt(), wing)
        }
        ellipse(sx(16f), cy, ((if (queen) 15f else 11f) * s).toInt(), (9 * s).toInt(), body)
        ellipse(sx(13f), cy - (3 * s).toInt(), (4 * s).toInt(), (2 * s).toInt(), hi)
        if (stripes) for (i in 0..2) line(sx(10f + i * 5f), cy - (7 * s).toInt(), sx(10f + i * 5f), cy + (7 * s).toInt(), Color.WHITE)
        ellipse(sx(31f), cy, (7 * s).toInt(), (5 * s).toInt(), body)
        dot(sx(44f), cy, ((if (soldier) 10 else 7) * s).toInt(), body)
        dot(sx(46f), cy - (2 * s).toInt(), (2 * s).toInt(), hi)
        thick(sx(48f), cy - (4 * s).toInt(), sx(57f), cy - (12 * s).toInt() + frame % 2, legColor)
        thick(sx(48f), cy + (4 * s).toInt(), sx(57f), cy + (12 * s).toInt() - frame % 2, legColor)
        if (soldier) {
            thick(sx(52f), cy - (4 * s).toInt(), sx(62f), cy - (1 * s).toInt(), legColor)
            thick(sx(52f), cy + (4 * s).toInt(), sx(62f), cy + (1 * s).toInt(), legColor)
        } else {
            line(sx(50f), cy - (2 * s).toInt(), sx(53f), cy, legColor)
            line(sx(50f), cy + (2 * s).toInt(), sx(53f), cy, legColor)
        }
    }

    private fun drawIcon(x: Int, y: Int, r: ResourceType) {
        val c = Palette.resource(r)
        when (r) {
            ResourceType.FOOD -> { ellipse(x + 16, y + 17, 12, 9, c); dot(x + 12, y + 14, 3, Color(1f, 1f, 1f, 0.6f)) }
            ResourceType.SEED -> { ellipse(x + 16, y + 16, 8, 12, c); line(x + 16, y + 6, x + 16, y + 26, shade(c, 0.8f)) }
            ResourceType.MUSHROOM -> { fillRect(x + 13, y + 15, 6, 13, Palette.hex("FFF6E5")); ellipse(x + 16, y + 14, 13, 9, c); dot(x + 11, y + 11, 2, Color.WHITE) }
            ResourceType.LEAF -> { ellipse(x + 16, y + 16, 8, 13, c); line(x + 16, y + 4, x + 16, y + 29, shade(c, 0.7f)); line(x + 16, y + 14, x + 21, y + 10, shade(c, 0.7f)) }
            ResourceType.WOOD -> { thick(x + 5, y + 25, x + 26, y + 7, c); thick(x + 6, y + 26, x + 27, y + 8, c); thick(x + 14, y + 16, x + 10, y + 7, c) }
            ResourceType.SOIL -> { ellipse(x + 16, y + 21, 13, 8, c); ellipse(x + 16, y + 15, 8, 6, shade(c, 1.1f)) }
            ResourceType.WATER -> { dot(x + 16, y + 20, 9, c); pm.setColor(c); pm.fillTriangle(x + 16, y + 3, x + 8, y + 17, x + 24, y + 17); dot(x + 13, y + 19, 2, Color.WHITE) }
            ResourceType.MINERAL -> { pm.setColor(c); pm.fillTriangle(x + 16, y + 3, x + 6, y + 15, x + 26, y + 15); pm.fillTriangle(x + 6, y + 15, x + 26, y + 15, x + 16, y + 29); pm.setColor(Palette.hex("A6F0EE")); pm.fillTriangle(x + 16, y + 5, x + 10, y + 14, x + 16, y + 14) }
            ResourceType.RESIN -> { dot(x + 16, y + 19, 10, c); pm.setColor(c); pm.fillTriangle(x + 16, y + 2, x + 7, y + 16, x + 25, y + 16); dot(x + 12, y + 17, 3, Color(1f, 1f, 1f, 0.6f)) }
            ResourceType.ENZYME -> { fillRect(x + 12, y + 3, 8, 9, Palette.hex("D8EEF7")); dot(x + 16, y + 20, 10, Palette.hex("D8EEF7")); dot(x + 16, y + 21, 8, c); dot(x + 13, y + 18, 2, Color.WHITE) }
            ResourceType.RARE_MINERAL -> { pm.setColor(c); pm.fillTriangle(x + 16, y + 2, x + 5, y + 14, x + 27, y + 14); pm.fillTriangle(x + 5, y + 14, x + 27, y + 14, x + 16, y + 30); pm.setColor(Palette.hex("E6D2FF")); pm.fillTriangle(x + 16, y + 4, x + 10, y + 13, x + 16, y + 13) }
            ResourceType.GENETIC -> { for (i in 0..12) { val t = i / 12.0 * Math.PI * 2; val yy = y + 3 + i * 2; dot(x + 16 + (sin(t) * 8).toInt(), yy, 2, c); dot(x + 16 - (sin(t) * 8).toInt(), yy, 2, Palette.hex("4FB6E8")) } }
            ResourceType.ROYAL_ESSENCE -> { dot(x + 16, y + 20, 10, c); pm.setColor(c); pm.fillTriangle(x + 16, y + 3, x + 7, y + 17, x + 25, y + 17); dot(x + 13, y + 18, 3, Color.WHITE); dot(x + 24, y + 6, 2, Color.WHITE) }
            ResourceType.FOSSIL -> { thick(x + 8, y + 24, x + 24, y + 8, c); dot(x + 7, y + 23, 4, c); dot(x + 9, y + 26, 4, c); dot(x + 25, y + 7, 4, c); dot(x + 23, y + 10, 4, c); line(x + 8, y + 24, x + 24, y + 8, shade(c, 0.85f)) }
        }
    }

    private fun drawSource(x: Int, y: Int, s: SourceKind) {
        when (s) {
            SourceKind.LEAF_BUSH -> {
                line(x + 24, y + 47, x + 24, y + 25, Palette.grassDark)
                for ((dx, dy) in listOf(10 to 22, 24 to 14, 37 to 22, 16 to 32, 31 to 32)) ellipse(x + dx, y + dy, 9, 7, if ((dx + dy) % 2 == 0) Palette.grass else Palette.hex("7FD35A"))
            }
            SourceKind.SEED_PILE -> for ((dx, dy) in listOf(12 to 40, 20 to 41, 28 to 40, 36 to 41, 16 to 34, 25 to 33, 33 to 34, 21 to 27, 29 to 27)) { ellipse(x + dx, y + dy, 4, 5, Palette.resource(ResourceType.SEED)); dot(x + dx - 1, y + dy - 2, 1, Color.WHITE) }
            SourceKind.DEAD_INSECT -> {
                val c = Palette.hex("6E5A8E")
                ellipse(x + 24, y + 36, 14, 8, c); dot(x + 38, y + 34, 5, c)
                for (i in 0..2) { line(x + 18 + i * 6, y + 40, x + 14 + i * 6, y + 46, c); line(x + 18 + i * 6, y + 31, x + 13 + i * 6, y + 26, c) }
                ellipse(x + 22, y + 32, 9, 3, Color(1f, 1f, 1f, 0.45f))
            }
            SourceKind.TWIGS -> { val c = Palette.resource(ResourceType.WOOD); thick(x + 4, y + 44, x + 44, y + 34, c); thick(x + 8, y + 36, x + 40, y + 44, shade(c, 1.15f)); thick(x + 26, y + 40, x + 33, y + 28, c) }
            SourceKind.RESIN_DROP -> { fillRect(x + 18, y + 8, 12, 40, Palette.hex("8A5A36")); for ((dx, dy) in listOf(24 to 22, 21 to 34)) { dot(x + dx, y + dy, 4, Palette.resource(ResourceType.RESIN)); dot(x + dx - 1, y + dy - 1, 1, Color.WHITE) } }
            SourceKind.CRUMBS -> for ((dx, dy) in listOf(12 to 40, 22 to 42, 32 to 39, 18 to 34, 28 to 33)) { fillRect(x + dx - 4, y + dy - 3, 8, 6, Palette.hex("E8C890")); fillRect(x + dx - 3, y + dy - 2, 4, 2, Palette.hex("FFF0C8")) }
            SourceKind.PEBBLES -> for ((dx, dy) in listOf(14 to 40, 26 to 41, 34 to 37, 20 to 34)) { dot(x + dx, y + dy, 5, Palette.hex("B9B2AA")); dot(x + dx + 1, y + dy - 1, 2, Palette.resource(ResourceType.MINERAL)) }
            SourceKind.DEW -> for ((dx, dy) in listOf(14 to 38, 28 to 34, 36 to 42, 22 to 44)) { dot(x + dx, y + dy, 5, Color(0.4f, 0.75f, 0.95f, 0.9f)); dot(x + dx - 2, y + dy - 2, 1, Color.WHITE) }
        }
    }

    override fun dispose() {
        texture.dispose()
        fontSmall.dispose(); font.dispose(); fontBig.dispose()
    }
}
