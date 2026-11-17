package com.example.gezegenkacisi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Gezegen Kaçışı: dokununca roket itiş yapar, yer çekimi aşağı çeker.
 * Engeller, yukarıdan ve aşağıdan uzanan gezegen + uydu kuleleridir; aradaki boşluktan geçilir.
 *
 * Dünya koordinatları: ekran yüksekliği her zaman 1000 birimdir, genişlik ekran oranına göre değişir.
 * Böylece oyun her telefonda aynı hissettirir.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private enum class State { READY, PLAYING, DYING, OVER }
    private enum class PlanetType { GAS, ROCKY, ICE, LAVA, OCEAN, MOON }

    private class Palette(val base: Int, val dark: Int, val light: Int, val accent: Int, val atmo: Int)

    /** Önceden çizilmiş gezegen görseli (her karede yeniden çizmemek için). */
    private class PlanetArt(val bitmap: Bitmap, val baseR: Float, val padX: Float, val padY: Float)

    private class Planet(val dx: Float, val y: Float, val r: Float, val art: PlanetArt)

    private class Column(var x: Float, val planets: List<Planet>) {
        var scored = false
        val halfWidth: Float = planets.maxOf { abs(it.dx) + it.art.padX * it.r / it.art.baseR }
    }

    private class Star(
        var x: Float, var y: Float,
        val size: Float, val phase: Float, val speed: Float, val color: Int
    )

    private class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f; var size = 1f; var drag = 0f
        var color = 0; var alive = false
    }

    companion object {
        // --- Oyun ayarları (zorluğu buradan değiştirebilirsin) ---
        private const val WORLD_H = 1000f
        private const val GRAVITY = 1850f        // yer çekimi
        private const val FLAP_V = -540f         // dokununca kazanılan yukarı hız
        private const val MAX_FALL = 820f        // en yüksek düşüş hızı
        private const val BASE_SPEED = 210f      // başlangıç kaydırma hızı
        private const val COLUMN_SPACING = 330f  // iki gezegen kulesi arası mesafe
        private const val START_GAP = 300f       // başlangıçtaki geçiş boşluğu
        private const val MIN_GAP = 230f         // en dar geçiş boşluğu
        private const val MAIN_R = 86f
        private const val MOON_R = 56f

        private const val PREFS = "gezegen_kacisi"
        private const val KEY_BEST = "best"

        private val DEG = (PI / 180.0).toFloat()
        private val TWO_PI = (PI * 2.0).toFloat()

        private fun hex(v: Long): Int = v.toInt()
        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        private fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Ekran ölçeği ---
    private var scale = 1f
    private var worldW = 500f
    private var artScale = 0f

    // --- Oyun durumu ---
    private var state = State.READY
    private var paused = false
    private var running = false
    private var lastFrameNs = 0L
    private var time = 0f
    private var stateTimer = 0f

    private var rocketX = 150f
    private var rocketY = WORLD_H * 0.45f
    private var rocketVy = 0f
    private var rocketAngle = 0f
    private var boost = 0f
    private var emitAcc = 0f

    private var speed = BASE_SPEED
    private var score = 0
    private var best = prefs.getInt(KEY_BEST, 0)
    private var newBest = false
    private var scorePulse = 0f
    private var lastGapY = WORLD_H / 2f

    private var shake = 0f
    private var flash = 0f
    private var shockT = 1f
    private var shockX = 0f
    private var shockY = 0f

    private var shootTimer = 3f
    private var shootLife = 0f
    private var shootX = 0f
    private var shootY = 0f

    private val columns = ArrayList<Column>()
    private val mainArts = ArrayList<PlanetArt>()
    private val moonArts = ArrayList<PlanetArt>()
    private val stars = ArrayList<Star>()
    private val particles = Array(420) { Particle() }
    private var particleCursor = 0
    private var bgBitmap: Bitmap? = null

    // --- Metinler ---
    private val strTitle1 = context.getString(R.string.title_line1)
    private val strTitle2 = context.getString(R.string.title_line2)
    private val strTapToStart = context.getString(R.string.tap_to_start)
    private val strHint = context.getString(R.string.hint)
    private val strGameOver = context.getString(R.string.game_over)
    private val strScoreLabel = context.getString(R.string.score_label)
    private val strBestLabel = context.getString(R.string.best_label)
    private val strNewRecord = context.getString(R.string.new_record)
    private val strTapToRetry = context.getString(R.string.tap_to_retry)
    private val strPaused = context.getString(R.string.paused)
    private val strTapToContinue = context.getString(R.string.tap_to_continue)
    private var scoreText = "0"
    private var bestValueText = "0"
    private var bestText = ""

    // --- Renkler ---
    private val colText = hex(0xFFEEF0FF)
    private val colDim = hex(0xFF9AA3D6)
    private val colWarm = hex(0xFFFFB36B)
    private val colGold = hex(0xFFFFD66B)
    private val colPanel = hex(0xFF141A4A)
    private val colPanelEdge = hex(0xFF8FA2FF)
    private val colAccent = hex(0xFFFF5B4A)
    private val colFin = hex(0xFFD8402F)

    // --- Fırçalar ---
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        letterSpacing = 0.04f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.22f
    }
    private val dst = RectF()
    private val tmpRect = RectF()
    private val tmpArtRect = RectF()

    // --- Roket geometrisi (sağa bakar, merkez 0,0) ---
    private val bodyPath = Path().apply {
        moveTo(-20f, -10f)
        lineTo(10f, -10f)
        cubicTo(20f, -10f, 28f, -5f, 32f, 0f)
        cubicTo(28f, 5f, 20f, 10f, 10f, 10f)
        lineTo(-20f, 10f)
        cubicTo(-23f, 5f, -23f, -5f, -20f, -10f)
        close()
    }
    private val noseAccent = Path().apply {
        op(bodyPath, Path().apply { addRect(19f, -12f, 34f, 12f, Path.Direction.CW) }, Path.Op.INTERSECT)
    }
    private val stripeAccent = Path().apply {
        op(bodyPath, Path().apply { addRect(-15f, -12f, -11f, 12f, Path.Direction.CW) }, Path.Op.INTERSECT)
    }
    private val finTop = Path().apply {
        moveTo(-4f, -9f); lineTo(-16f, -22f); lineTo(-24f, -22f); lineTo(-20f, -9f); close()
    }
    private val finBottom = Path().apply {
        moveTo(-4f, 9f); lineTo(-16f, 22f); lineTo(-24f, 22f); lineTo(-20f, 9f); close()
    }
    private val flamePath = Path()
    private val flameMatrix = Matrix()
    private val bodyShader = LinearGradient(
        0f, -10f, 0f, 10f,
        intArrayOf(hex(0xFFFFFFFF), hex(0xFFD7DCF2), hex(0xFF8A90B8)),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
    )
    private val flameShader = LinearGradient(
        0f, 0f, -1f, 0f,
        intArrayOf(hex(0xFFFFF4C2), hex(0xFFFFB347), hex(0xFFFF5A1F), hex(0x00FF3A10)),
        floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP
    )
    private val windowShader = RadialGradient(
        2.8f, -1.8f, 6f,
        intArrayOf(hex(0xFFA6EEFF), hex(0xFF2E7BD6), hex(0xFF14285C)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
    )

    // Çarpışma için roketi boyunca dizilmiş 3 daire (yerel x, yarıçap)
    private val hitX = floatArrayOf(-12f, 4f, 20f)
    private val hitR = floatArrayOf(9f, 10f, 8f)

    private val palettes: Map<PlanetType, List<Palette>> = mapOf(
        PlanetType.GAS to listOf(
            Palette(hex(0xFFD9A066), hex(0xFF8E5530), hex(0xFFF6DDB4), hex(0xFFC2563A), hex(0xFFFFC48A)),
            Palette(hex(0xFF9C7FE6), hex(0xFF4B3496), hex(0xFFDCD0FF), hex(0xFFFF8FD8), hex(0xFFB9A2FF)),
            Palette(hex(0xFF4FB9AE), hex(0xFF1C6670), hex(0xFFC2F3EA), hex(0xFF2E8FA0), hex(0xFF86E6DA))
        ),
        PlanetType.ROCKY to listOf(
            Palette(hex(0xFFCC5A3E), hex(0xFF6E2A1E), hex(0xFFF09A72), hex(0xFF8A3524), hex(0xFFFF8A65)),
            Palette(hex(0xFFC9A46E), hex(0xFF6F5534), hex(0xFFF0D7A6), hex(0xFF8C6A3E), hex(0xFFFFD79A))
        ),
        PlanetType.ICE to listOf(
            Palette(hex(0xFF98D6F2), hex(0xFF3E78B0), hex(0xFFF2FBFF), hex(0xFFDDF4FF), hex(0xFFB4E9FF)),
            Palette(hex(0xFFB9C7F5), hex(0xFF5664B8), hex(0xFFFFFFFF), hex(0xFFE6EBFF), hex(0xFFC9D4FF))
        ),
        PlanetType.LAVA to listOf(
            Palette(hex(0xFF4A2420), hex(0xFF180909), hex(0xFF7A3524), hex(0xFFFF6A1A), hex(0xFFFF5A1F))
        ),
        PlanetType.OCEAN to listOf(
            Palette(hex(0xFF2F74DA), hex(0xFF10357A), hex(0xFF8CC4FF), hex(0xFF4DB36E), hex(0xFF86BEFF))
        ),
        PlanetType.MOON to listOf(
            Palette(hex(0xFFA3A3AE), hex(0xFF4E4F5E), hex(0xFFDADBE3), hex(0xFF6E6F7E), hex(0xFFDADBE3)),
            Palette(hex(0xFFB5A58C), hex(0xFF5A4E3E), hex(0xFFE6DAC3), hex(0xFF7A6B55), hex(0xFFE6DAC3)),
            Palette(hex(0xFF7D8296), hex(0xFF33364A), hex(0xFFB7BCCF), hex(0xFF545A70), hex(0xFFB7BCCF))
        )
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        refreshBestTexts()
    }

    // =====================================================================
    // Yaşam döngüsü
    // =====================================================================

    fun resume() {
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        if (state == State.PLAYING) paused = true
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrameNs != 0L) {
            val dt = ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
            if (paused) time += dt else update(dt)
        }
        lastFrameNs = frameTimeNanos
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        scale = h / WORLD_H
        worldW = w / scale
        rocketX = worldW * 0.3f
        buildBackground(w, h)
        // Gezegen görselleri pahalı; ölçek çok değişmediyse yeniden üretme.
        if (mainArts.isEmpty() || abs(scale - artScale) / artScale > 0.15f) buildPlanetArts()
        buildStars()
        if (state == State.READY) resetGame()
    }

    // =====================================================================
    // Girdi
    // =====================================================================

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) onTap()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_DPAD_UP
        ) {
            if (event.repeatCount == 0) onTap()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onTap() {
        if (paused) {
            paused = false
            if (state == State.PLAYING) flap()
            return
        }
        when (state) {
            State.READY -> {
                state = State.PLAYING
                stateTimer = 0f
                flap()
            }
            State.PLAYING -> flap()
            State.DYING -> Unit
            State.OVER -> if (stateTimer > 0.6f) resetGame()
        }
    }

    private fun flap() {
        rocketVy = FLAP_V
        boost = 1f
        repeat(8) { emitExhaust(1.3f) }
    }

    // =====================================================================
    // Oyun mantığı
    // =====================================================================

    private fun resetGame() {
        columns.clear()
        state = State.READY
        stateTimer = 0f
        rocketY = WORLD_H * 0.45f
        rocketVy = 0f
        rocketAngle = 0f
        score = 0
        scoreText = "0"
        newBest = false
        speed = BASE_SPEED
        lastGapY = WORLD_H / 2f
        paused = false
    }

    private fun update(dt: Float) {
        time += dt
        stateTimer += dt
        boost = max(0f, boost - dt * 3.5f)
        scorePulse = max(0f, scorePulse - dt * 4f)
        shake = max(0f, shake - dt)
        flash = max(0f, flash - dt)
        shockT += dt

        val worldSpeed = when (state) {
            State.READY -> BASE_SPEED * 0.7f
            State.PLAYING -> speed
            State.DYING, State.OVER -> BASE_SPEED * 0.12f
        }
        updateStars(dt, worldSpeed)
        updateShootingStar(dt)

        when (state) {
            State.READY -> {
                // Başlamadan önce roket hafifçe süzülür
                rocketY = WORLD_H * 0.45f + sin(time * 2.6f) * 14f
                rocketAngle = cos(time * 2.6f) * -7f
                emit(dt, 28f, 0.8f)
            }
            State.PLAYING -> {
                rocketVy = min(rocketVy + GRAVITY * dt, MAX_FALL)
                rocketY += rocketVy * dt
                if (rocketY < 22f) {
                    rocketY = 22f
                    if (rocketVy < 0f) rocketVy = 0f
                }
                val target = (rocketVy / MAX_FALL * 75f).coerceIn(-28f, 75f)
                rocketAngle += (target - rocketAngle) * min(1f, dt * 12f)
                speed = BASE_SPEED + min(score, 30) * 3f
                updateColumns(dt)
                emit(dt, 55f, 1f + boost * 0.6f)
                if (rocketHits()) crash()
            }
            State.DYING -> if (stateTimer > 0.9f) {
                state = State.OVER
                stateTimer = 0f
            }
            State.OVER -> Unit
        }
        updateParticles(dt)
    }

    private fun updateColumns(dt: Float) {
        for (col in columns) col.x -= speed * dt

        val spawnX = worldW + 200f
        val last = columns.lastOrNull()
        if (last == null) {
            columns.add(makeColumn(spawnX))
        } else if (last.x + COLUMN_SPACING <= spawnX) {
            columns.add(makeColumn(last.x + COLUMN_SPACING))
        }

        val iter = columns.iterator()
        while (iter.hasNext()) {
            val col = iter.next()
            if (col.x + col.halfWidth < -10f) iter.remove()
        }

        for (col in columns) {
            if (!col.scored && col.x < rocketX) {
                col.scored = true
                score++
                scoreText = score.toString()
                scorePulse = 1f
            }
        }
    }

    /** Ortada bir geçiş boşluğu bırakan, üstte ve altta gezegen kulesi olan bir engel üretir. */
    private fun makeColumn(x: Float): Column {
        val gapH = max(MIN_GAP, START_GAP - score * 3.5f)
        val minY = 110f + gapH / 2f
        val maxY = WORLD_H - 110f - gapH / 2f
        val gapY = (lastGapY + rnd(-240f, 240f)).coerceIn(minY, maxY)
        lastGapY = gapY

        val planets = ArrayList<Planet>()
        buildStack(planets, gapY - gapH / 2f, -1f) // üst kule
        buildStack(planets, gapY + gapH / 2f, 1f)  // alt kule
        return Column(x, planets)
    }

    /**
     * Boşluğun kenarına büyük bir gezegen koyar, ekranın kenarına kadar da uydularla doldurur.
     * Daireler birbirine bindiği için aralarından sızılamaz.
     */
    private fun buildStack(out: MutableList<Planet>, gapEdge: Float, dir: Float) {
        val stack = ArrayList<Planet>()
        val mainR = rnd(60f, MAIN_R)
        var cy = gapEdge + dir * mainR
        stack.add(Planet(0f, cy, mainR, mainArts[Random.nextInt(mainArts.size)]))

        var prevR = mainR
        var farEdge = cy + dir * mainR
        var guard = 0
        while (guard++ < 14 && (if (dir < 0f) farEdge > -10f else farEdge < WORLD_H + 10f)) {
            val r = rnd(34f, MOON_R)
            val dx = rnd(-0.25f, 0.25f) * min(r, prevR)
            cy = farEdge + dir * (r * 0.7f)
            stack.add(Planet(dx, cy, r, moonArts[Random.nextInt(moonArts.size)]))
            farEdge = cy + dir * r
            prevR = r
        }
        // Uzaktakiler önce çizilsin, ana gezegen en üstte görünsün
        for (i in stack.indices.reversed()) out.add(stack[i])
    }

    private fun rocketHits(): Boolean {
        val rad = rocketAngle * DEG
        val cs = cos(rad)
        val sn = sin(rad)
        for (i in hitX.indices) {
            val hx = rocketX + hitX[i] * cs
            val hy = rocketY + hitX[i] * sn
            val hr = hitR[i]
            if (hy + hr > WORLD_H) return true // uzayın derinliklerine düştü
            for (col in columns) {
                if (abs(col.x - hx) > col.halfWidth + hr) continue
                for (pl in col.planets) {
                    val dx = col.x + pl.dx - hx
                    val dy = pl.y - hy
                    val rr = pl.r * 0.96f + hr
                    if (dx * dx + dy * dy < rr * rr) return true
                }
            }
        }
        return false
    }

    private fun crash() {
        state = State.DYING
        stateTimer = 0f
        explode(rocketX, rocketY)
        shake = 0.45f
        flash = 0.2f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (score > best) {
            best = score
            newBest = true
            refreshBestTexts()
            prefs.edit().putInt(KEY_BEST, best).apply()
        }
    }

    private fun refreshBestTexts() {
        bestValueText = best.toString()
        bestText = context.getString(R.string.best_format, best)
    }

    // --- Yıldızlar ve kayan yıldız ---

    private fun buildStars() {
        stars.clear()
        val count = (worldW * 0.3f).toInt().coerceAtLeast(80)
        repeat(count) {
            val layer = Random.nextInt(3)
            val size = when (layer) {
                0 -> rnd(0.8f, 1.3f)
                1 -> rnd(1.2f, 1.9f)
                else -> rnd(1.8f, 2.8f)
            }
            val sp = when (layer) {
                0 -> 0.04f
                1 -> 0.1f
                else -> 0.22f
            }
            val color = when (Random.nextInt(5)) {
                0 -> hex(0xFFBFD4FF)
                1 -> hex(0xFFFFE6C2)
                else -> Color.WHITE
            }
            stars.add(Star(rnd(0f, worldW), rnd(0f, WORLD_H), size, rnd(0f, TWO_PI), sp, color))
        }
    }

    private fun updateStars(dt: Float, worldSpeed: Float) {
        for (s in stars) {
            s.x -= worldSpeed * s.speed * dt
            if (s.x < -4f) {
                s.x += worldW + 8f
                s.y = rnd(0f, WORLD_H)
            }
        }
    }

    private fun updateShootingStar(dt: Float) {
        if (shootLife > 0f) {
            shootLife -= dt
            shootX -= 620f * dt
            shootY += 300f * dt
        } else {
            shootTimer -= dt
            if (shootTimer <= 0f) {
                shootTimer = rnd(4f, 9f)
                shootLife = 0.8f
                shootX = rnd(worldW * 0.4f, worldW + 60f)
                shootY = rnd(-20f, WORLD_H * 0.35f)
            }
        }
    }

    // --- Parçacıklar (egzoz ve patlama) ---

    private fun spawn(
        x: Float, y: Float, vx: Float, vy: Float,
        life: Float, size: Float, color: Int, drag: Float
    ) {
        val p = particles[particleCursor]
        particleCursor = (particleCursor + 1) % particles.size
        p.x = x; p.y = y; p.vx = vx; p.vy = vy
        p.life = life; p.maxLife = life; p.size = size
        p.color = color; p.drag = drag; p.alive = true
    }

    private fun emit(dt: Float, rate: Float, strength: Float) {
        emitAcc += dt * rate
        while (emitAcc >= 1f) {
            emitAcc -= 1f
            emitExhaust(strength)
        }
    }

    private fun emitExhaust(strength: Float) {
        val rad = rocketAngle * DEG
        val cs = cos(rad)
        val sn = sin(rad)
        val tx = rocketX - 24f * cs
        val ty = rocketY - 24f * sn
        val spread = rnd(-0.3f, 0.3f)
        val sp = rnd(130f, 230f) * strength
        val vx = -cs * sp - sn * spread * sp
        val vy = -sn * sp + cs * spread * sp
        val color = when (Random.nextInt(3)) {
            0 -> hex(0xFFFFE08A)
            1 -> hex(0xFFFFB347)
            else -> hex(0xFFFF7A3A)
        }
        spawn(tx, ty, vx, vy, rnd(0.25f, 0.5f), rnd(2.2f, 4.5f), color, 2.2f)
    }

    private fun explode(x: Float, y: Float) {
        val colors = intArrayOf(
            hex(0xFFFFFFFF), hex(0xFFFFE27A), hex(0xFFFFB347),
            hex(0xFFFF6A3D), hex(0xFFE8432E), hex(0xFFC9CEE6)
        )
        repeat(70) {
            val a = rnd(0f, TWO_PI)
            val sp = rnd(60f, 430f)
            spawn(
                x, y, cos(a) * sp, sin(a) * sp,
                rnd(0.45f, 1.2f), rnd(2.5f, 7f), colors[Random.nextInt(colors.size)], 2.4f
            )
        }
        shockT = 0f
        shockX = x
        shockY = y
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            if (p.drag > 0f) {
                val d = exp(-p.drag * dt)
                p.vx *= d
                p.vy *= d
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    // =====================================================================
    // Çizim
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bg = bgBitmap
        if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null) else canvas.drawColor(hex(0xFF060920))

        canvas.save()
        canvas.scale(scale, scale)
        if (shake > 0f) {
            val m = shake / 0.45f * 9f
            canvas.translate(rnd(-m, m), rnd(-m, m))
        }
        drawStars(canvas)
        drawShootingStar(canvas)
        drawColumns(canvas)
        drawParticles(canvas)
        if (state == State.READY || state == State.PLAYING) drawRocket(canvas)
        drawShockwave(canvas)
        canvas.restore()

        canvas.save()
        canvas.scale(scale, scale)
        drawHud(canvas)
        canvas.restore()

        if (flash > 0f) canvas.drawColor(withAlpha(Color.WHITE, (flash / 0.2f * 140).toInt()))
    }

    private fun drawStars(cv: Canvas) {
        for (s in stars) {
            val tw = 0.55f + 0.45f * sin(time * 2.2f + s.phase)
            paint.color = withAlpha(s.color, (tw * 235).toInt())
            cv.drawCircle(s.x, s.y, s.size, paint)
        }
    }

    private fun drawShootingStar(cv: Canvas) {
        if (shootLife <= 0f) return
        val a = (shootLife / 0.8f).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 5f
        paint.color = withAlpha(hex(0xFF9FD0FF), (a * 60).toInt())
        cv.drawLine(shootX, shootY, shootX + 620f * 0.07f, shootY - 300f * 0.07f, paint)
        paint.strokeWidth = 2.2f
        paint.color = withAlpha(Color.WHITE, (a * 180).toInt())
        cv.drawLine(shootX, shootY, shootX + 620f * 0.12f, shootY - 300f * 0.12f, paint)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(Color.WHITE, (a * 255).toInt())
        cv.drawCircle(shootX, shootY, 2.2f, paint)
    }

    private fun drawColumns(cv: Canvas) {
        for (col in columns) {
            if (col.x - col.halfWidth > worldW || col.x + col.halfWidth < 0f) continue
            for (pl in col.planets) {
                val art = pl.art
                val k = pl.r / art.baseR
                val px = col.x + pl.dx
                dst.set(px - art.padX * k, pl.y - art.padY * k, px + art.padX * k, pl.y + art.padY * k)
                cv.drawBitmap(art.bitmap, null, dst, bitmapPaint)
            }
        }
    }

    private fun drawParticles(cv: Canvas) {
        for (p in particles) {
            if (!p.alive) continue
            val f = p.life / p.maxLife
            paint.color = withAlpha(p.color, (f * 235).toInt())
            cv.drawCircle(p.x, p.y, p.size * (0.35f + 0.65f * f), paint)
        }
    }

    private fun drawShockwave(cv: Canvas) {
        if (shockT >= 0.55f) return
        val k = shockT / 0.55f
        val radius = 16f + 170f * (1f - (1f - k).pow(3))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f + 7f * (1f - k)
        paint.color = withAlpha(hex(0xFFFFE08A), ((1f - k) * 210).toInt())
        cv.drawCircle(shockX, shockY, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawRocket(cv: Canvas) {
        cv.save()
        cv.translate(rocketX, rocketY)
        cv.rotate(rocketAngle)

        // Alev: dokununca bir anlığına uzar
        val flicker = 0.85f + 0.15f * sin(time * 47f) + rnd(-0.06f, 0.06f)
        val len = (13f + boost * 24f) * flicker
        cv.save()
        cv.translate(-19f, 0f)
        flamePath.reset()
        flamePath.moveTo(0f, -7f)
        flamePath.quadTo(-0.55f * len, -6f, -len, 0f)
        flamePath.quadTo(-0.55f * len, 6f, 0f, 7f)
        flamePath.close()
        flameMatrix.setScale(len, 1f)
        flameShader.setLocalMatrix(flameMatrix)
        paint.color = Color.WHITE
        paint.shader = flameShader
        cv.drawPath(flamePath, paint)
        paint.shader = null
        val inner = len * 0.55f
        flamePath.reset()
        flamePath.moveTo(0f, -4f)
        flamePath.quadTo(-0.55f * inner, -3.5f, -inner, 0f)
        flamePath.quadTo(-0.55f * inner, 3.5f, 0f, 4f)
        flamePath.close()
        paint.color = hex(0xEEFFFBE6)
        cv.drawPath(flamePath, paint)
        cv.restore()

        // Nozul ve kanatçıklar
        paint.color = hex(0xFF4A4F6E)
        cv.drawRect(-23f, -6.5f, -18f, 6.5f, paint)
        paint.color = colFin
        cv.drawPath(finTop, paint)
        cv.drawPath(finBottom, paint)

        // Gövde
        paint.color = Color.WHITE
        paint.shader = bodyShader
        cv.drawPath(bodyPath, paint)
        paint.shader = null
        paint.color = colAccent
        cv.drawPath(noseAccent, paint)
        cv.drawPath(stripeAccent, paint)

        // Pencere
        paint.color = hex(0xFF3B4266)
        cv.drawCircle(4f, 0f, 6.3f, paint)
        paint.color = Color.WHITE
        paint.shader = windowShader
        cv.drawCircle(4f, 0f, 4.9f, paint)
        paint.shader = null
        paint.color = hex(0xDDFFFFFF)
        cv.drawCircle(2.4f, -1.9f, 1.4f, paint)

        // İnce kontur
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f
        paint.color = hex(0x40000000)
        cv.drawPath(bodyPath, paint)
        paint.style = Paint.Style.FILL

        cv.restore()
    }

    // --- Arayüz ---

    private fun drawHud(cv: Canvas) {
        val cx = worldW / 2f
        when (state) {
            State.READY -> drawReady(cv, cx)
            State.PLAYING, State.DYING -> drawScore(cv, cx)
            State.OVER -> drawGameOver(cv, cx)
        }
        if (paused) drawPaused(cv, cx)
    }

    private fun drawReady(cv: Canvas, cx: Float) {
        val bob = sin(time * 1.6f) * 4f
        titlePaint.textSize = 78f
        drawShadowText(cv, strTitle1, cx, 215f + bob, titlePaint, colText)
        drawShadowText(cv, strTitle2, cx, 292f + bob, titlePaint, colWarm)

        val pulse = 0.5f + 0.5f * sin(time * 4f)
        textPaint.textSize = 30f
        textPaint.color = withAlpha(colText, (140 + 115 * pulse).toInt())
        drawFit(cv, strTapToStart, cx, 680f, textPaint)
        textPaint.textSize = 21f
        textPaint.color = colDim
        drawFit(cv, strHint, cx, 722f, textPaint)

        if (best > 0) {
            labelPaint.textSize = 20f
            labelPaint.color = colGold
            drawFit(cv, bestText, cx, 800f, labelPaint)
        }
    }

    private fun drawScore(cv: Canvas, cx: Float) {
        titlePaint.textSize = 92f * (1f + 0.18f * scorePulse)
        drawShadowText(cv, scoreText, cx, 150f, titlePaint, colText)
    }

    private fun drawGameOver(cv: Canvas, cx: Float) {
        val t = (stateTimer / 0.35f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t).pow(3)
        val a = (ease * 255).toInt()
        val top = 330f + (1f - ease) * 70f
        val panelW = min(worldW - 48f, 360f)
        val panelH = 290f

        titlePaint.textSize = 56f
        titlePaint.color = withAlpha(Color.BLACK, (a * 0.4f).toInt())
        drawFit(cv, strGameOver, cx, top - 29f, titlePaint)
        titlePaint.color = withAlpha(colWarm, a)
        drawFit(cv, strGameOver, cx, top - 34f, titlePaint)

        tmpRect.set(cx - panelW / 2f, top, cx + panelW / 2f, top + panelH)
        paint.color = withAlpha(colPanel, (a * 0.88f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, (a * 0.45f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        labelPaint.textSize = 20f
        labelPaint.color = withAlpha(colDim, a)
        cv.drawText(strScoreLabel, cx, top + 52f, labelPaint)
        titlePaint.textSize = 84f
        titlePaint.color = withAlpha(colText, a)
        cv.drawText(scoreText, cx, top + 140f, titlePaint)

        paint.color = withAlpha(colPanelEdge, (a * 0.25f).toInt())
        cv.drawRect(cx - panelW / 2f + 32f, top + 172f, cx + panelW / 2f - 32f, top + 174f, paint)

        cv.drawText(strBestLabel, cx, top + 212f, labelPaint)
        titlePaint.textSize = 44f
        titlePaint.color = withAlpha(colGold, a)
        cv.drawText(bestValueText, cx, top + 262f, titlePaint)

        if (newBest) {
            val pw = 210f
            val ph = 46f
            val py = top + panelH
            tmpRect.set(cx - pw / 2f, py - ph / 2f, cx + pw / 2f, py + ph / 2f)
            paint.color = withAlpha(colGold, a)
            cv.drawRoundRect(tmpRect, ph / 2f, ph / 2f, paint)
            labelPaint.color = withAlpha(hex(0xFF2A1A00), a)
            labelPaint.textSize = 20f
            cv.drawText(strNewRecord, cx, py + 7f, labelPaint)
        }

        if (stateTimer > 0.6f) {
            val pulse = 0.5f + 0.5f * sin(time * 4f)
            textPaint.textSize = 26f
            textPaint.color = withAlpha(colText, (130 + 125 * pulse).toInt())
            drawFit(cv, strTapToRetry, cx, top + panelH + 90f, textPaint)
        }
    }

    private fun drawPaused(cv: Canvas, cx: Float) {
        paint.color = hex(0xAA05071A)
        cv.drawRect(0f, 0f, worldW, WORLD_H, paint)
        titlePaint.textSize = 54f
        drawShadowText(cv, strPaused, cx, 470f, titlePaint, colText)
        textPaint.textSize = 26f
        textPaint.color = colDim
        drawFit(cv, strTapToContinue, cx, 530f, textPaint)
    }

    private fun drawShadowText(cv: Canvas, text: String, x: Float, y: Float, p: Paint, color: Int) {
        p.color = hex(0x66000000)
        drawFit(cv, text, x, y + 5f, p)
        p.color = color
        drawFit(cv, text, x, y, p)
    }

    /** Metni ekrana sığmazsa küçülterek çizer. */
    private fun drawFit(cv: Canvas, text: String, x: Float, y: Float, p: Paint) {
        val maxW = worldW - 40f
        val w = p.measureText(text)
        if (w <= maxW) {
            cv.drawText(text, x, y, p)
        } else {
            val old = p.textSize
            p.textSize = old * maxW / w
            cv.drawText(text, x, y, p)
            p.textSize = old
        }
    }

    // =====================================================================
    // Arka plan ve gezegen görsellerinin üretimi (bir kez yapılır)
    // =====================================================================

    private fun buildBackground(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(hex(0xFF060920), hex(0xFF0E1140), hex(0xFF1C0E3A)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Bulutsular
        val nebula = intArrayOf(hex(0xFF7B3BFF), hex(0xFF2BB8FF), hex(0xFFFF4B9A), hex(0xFF3B5BFF), hex(0xFF9B3BFF))
        for (i in 0 until 5) {
            val cx = rnd(-0.1f, 1.1f) * w
            val cy = rnd(0f, 1f) * h
            val rr = rnd(0.35f, 0.75f) * max(w, h) * 0.6f
            val col = nebula[i]
            p.shader = RadialGradient(
                cx, cy, rr,
                intArrayOf(withAlpha(col, 60), withAlpha(col, 22), withAlpha(col, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            cv.drawCircle(cx, cy, rr, p)
        }
        p.shader = null

        // Çok uzak, hareketsiz toz yıldızlar
        val dust = (w * h / 9000).coerceIn(150, 600)
        repeat(dust) {
            p.color = withAlpha(Color.WHITE, Random.nextInt(25, 110))
            cv.drawCircle(rnd(0f, w.toFloat()), rnd(0f, h.toFloat()), rnd(0.4f, 1.1f) * scale, p)
        }
        bgBitmap = bmp
    }

    private fun buildPlanetArts() {
        artScale = scale
        mainArts.clear()
        moonArts.clear()
        val mainTypes = listOf(
            PlanetType.GAS, PlanetType.GAS, PlanetType.ROCKY, PlanetType.ICE, PlanetType.LAVA,
            PlanetType.OCEAN, PlanetType.GAS, PlanetType.ROCKY, PlanetType.ICE, PlanetType.OCEAN
        )
        mainTypes.forEachIndexed { i, t ->
            val ringed = (t == PlanetType.GAS && i % 2 == 0) || (t == PlanetType.ICE && i < 5)
            mainArts.add(renderArt(MAIN_R, t, ringed))
        }
        repeat(9) { moonArts.add(renderArt(MOON_R, PlanetType.MOON, false)) }
        for (t in listOf(PlanetType.ROCKY, PlanetType.ICE, PlanetType.LAVA, PlanetType.GAS)) {
            moonArts.add(renderArt(MOON_R, t, false))
        }
    }

    private fun renderArt(baseR: Float, type: PlanetType, ringed: Boolean): PlanetArt {
        val padX = if (ringed) baseR * 2.0f else baseR * 1.22f
        val padY = baseR * 1.22f
        val w = ceil(padX * 2f * scale).toInt().coerceAtLeast(2)
        val h = ceil(padY * 2f * scale).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(w / (padX * 2f), h / (padY * 2f))
        cv.translate(padX, padY)
        paintPlanet(cv, baseR, type, ringed)
        return PlanetArt(bmp, baseR, padX, padY)
    }

    private fun paintPlanet(cv: Canvas, r: Float, type: PlanetType, ringed: Boolean) {
        val pal = palettes.getValue(type).random()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()

        // Atmosfer parıltısı
        val glowAlpha = if (type == PlanetType.MOON) 35 else 105
        p.shader = RadialGradient(
            0f, 0f, r * 1.2f,
            intArrayOf(withAlpha(pal.atmo, glowAlpha), withAlpha(pal.atmo, glowAlpha), withAlpha(pal.atmo, 0)),
            floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(0f, 0f, r * 1.2f, p)
        p.shader = null

        if (ringed) drawRing(cv, p, r, pal.light, front = false)

        // Gövde + yüzey: ayrı katmanda, SRC_ATOP ile yalnızca gezegenin içine çizilir
        val layer = cv.saveLayer(-r - 2f, -r - 2f, r + 2f, r + 2f, null)
        p.color = Color.WHITE
        p.shader = RadialGradient(
            -0.35f * r, -0.4f * r, r * 1.5f,
            intArrayOf(pal.light, pal.base, pal.dark),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(0f, 0f, r, p)
        p.shader = null
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        when (type) {
            PlanetType.GAS -> paintGas(cv, p, path, r, pal)
            PlanetType.ROCKY -> paintRocky(cv, p, r, pal, 7)
            PlanetType.MOON -> paintRocky(cv, p, r, pal, 12)
            PlanetType.ICE -> paintIce(cv, p, path, r, pal)
            PlanetType.LAVA -> paintLava(cv, p, path, r, pal)
            PlanetType.OCEAN -> paintOcean(cv, p, path, r, pal)
        }
        // Gece tarafı gölgesi (ışık sol üstten)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.shader = RadialGradient(
            -0.4f * r, -0.45f * r, r * 1.8f,
            intArrayOf(0, 0, hex(0xC8000000)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(0f, 0f, r, p)
        p.shader = null
        p.xfermode = null
        cv.restoreToCount(layer)

        // Kenar ışığı
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.035f
        p.color = withAlpha(pal.light, 130)
        tmpArtRect.set(-r * 0.975f, -r * 0.975f, r * 0.975f, r * 0.975f)
        cv.drawArc(tmpArtRect, 195f, 90f, false, p)
        p.style = Paint.Style.FILL

        if (ringed) drawRing(cv, p, r, pal.light, front = true)
    }

    /** Halka iki parça çizilir: arka yarısı gezegenin arkasında, ön yarısı önünde. */
    private fun drawRing(cv: Canvas, p: Paint, r: Float, color: Int, front: Boolean) {
        cv.save()
        cv.rotate(-16f)
        if (front) cv.clipRect(-r * 2.3f, 0f, r * 2.3f, r * 1.5f)
        else cv.clipRect(-r * 2.3f, -r * 1.5f, r * 2.3f, 0f)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.11f
        for (i in 0 until 6) {
            val t = i / 5f
            val rr = r * (1.35f + 0.5f * t)
            val alpha = (if (i % 2 == 0) 175f else 100f) * (1f - 0.4f * t)
            p.color = withAlpha(color, alpha.toInt())
            tmpArtRect.set(-rr, -rr * 0.28f, rr, rr * 0.28f)
            cv.drawOval(tmpArtRect, p)
        }
        p.style = Paint.Style.FILL
        cv.restore()
    }

    private fun paintGas(cv: Canvas, p: Paint, path: Path, r: Float, pal: Palette) {
        val bands = 7
        val step = 2f * r / bands
        for (i in 0 until bands) {
            val y = -r + (i + 0.5f) * step + rnd(-0.05f, 0.05f) * r
            val h = rnd(0.07f, 0.17f) * r
            val amp = rnd(0.01f, 0.04f) * r
            val ph = rnd(0f, 6f)
            p.color = withAlpha(if (i % 2 == 0) pal.dark else pal.light, Random.nextInt(70, 140))
            path.reset()
            path.moveTo(-r, y - h / 2f)
            for (k in 1..10) {
                val x = -r + k * (2f * r / 10f)
                path.lineTo(x, y - h / 2f + sin(k * 0.9f + ph) * amp)
            }
            for (k in 10 downTo 0) {
                val x = -r + k * (2f * r / 10f)
                path.lineTo(x, y + h / 2f + sin(k * 1.1f + ph * 1.7f) * amp)
            }
            path.close()
            cv.drawPath(path, p)
        }
        // Fırtına lekesi
        val sx = rnd(-0.35f, 0.25f) * r
        val sy = rnd(0.1f, 0.45f) * r
        p.color = withAlpha(pal.accent, 210)
        tmpArtRect.set(sx - 0.22f * r, sy - 0.1f * r, sx + 0.22f * r, sy + 0.1f * r)
        cv.drawOval(tmpArtRect, p)
        p.color = withAlpha(pal.light, 110)
        tmpArtRect.set(sx - 0.12f * r, sy - 0.045f * r, sx + 0.1f * r, sy + 0.04f * r)
        cv.drawOval(tmpArtRect, p)
    }

    private fun paintRocky(cv: Canvas, p: Paint, r: Float, pal: Palette, craters: Int) {
        repeat(6) {
            p.color = withAlpha(if (Random.nextBoolean()) pal.dark else pal.light, 40)
            cv.drawCircle(rnd(-0.7f, 0.7f) * r, rnd(-0.7f, 0.7f) * r, rnd(0.2f, 0.45f) * r, p)
        }
        repeat(craters) {
            val a = rnd(0f, TWO_PI)
            val d = sqrt(Random.nextFloat()) * r * 0.85f
            val cx = cos(a) * d
            val cy = sin(a) * d
            val cr = rnd(0.06f, 0.17f) * r
            p.color = withAlpha(pal.dark, 140)
            cv.drawCircle(cx, cy, cr, p)
            p.color = withAlpha(pal.base, 190)
            cv.drawCircle(cx + cr * 0.2f, cy + cr * 0.2f, cr * 0.75f, p)
        }
    }

    private fun paintIce(cv: Canvas, p: Paint, path: Path, r: Float, pal: Palette) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 7) {
            p.strokeWidth = rnd(0.02f, 0.045f) * r
            p.color = withAlpha(if (i % 2 == 0) pal.light else pal.dark, 110)
            val y = rnd(-0.6f, 0.65f) * r
            path.reset()
            path.moveTo(-r, y)
            path.quadTo(rnd(-0.3f, 0.3f) * r, y + rnd(-0.3f, 0.3f) * r, r, y + rnd(-0.2f, 0.2f) * r)
            cv.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        // Kutup başlıkları
        p.color = withAlpha(Color.WHITE, 210)
        tmpArtRect.set(-0.8f * r, -1.15f * r, 0.8f * r, -0.62f * r)
        cv.drawOval(tmpArtRect, p)
        p.color = withAlpha(Color.WHITE, 160)
        tmpArtRect.set(-0.55f * r, 0.74f * r, 0.55f * r, 1.12f * r)
        cv.drawOval(tmpArtRect, p)
    }

    private fun paintLava(cv: Canvas, p: Paint, path: Path, r: Float, pal: Palette) {
        repeat(4) {
            val gx = rnd(-0.6f, 0.6f) * r
            val gy = rnd(-0.6f, 0.6f) * r
            val gr = rnd(0.25f, 0.5f) * r
            p.color = Color.WHITE
            p.shader = RadialGradient(
                gx, gy, gr,
                withAlpha(pal.accent, 110), withAlpha(pal.accent, 0), Shader.TileMode.CLAMP
            )
            cv.drawCircle(gx, gy, gr, p)
        }
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        repeat(8) {
            path.reset()
            var x = rnd(-0.8f, 0.8f) * r
            var y = rnd(-0.8f, 0.8f) * r
            var ang = rnd(0f, TWO_PI)
            path.moveTo(x, y)
            repeat(7) {
                ang += rnd(-0.8f, 0.8f)
                val len = rnd(0.08f, 0.18f) * r
                x += cos(ang) * len
                y += sin(ang) * len
                path.lineTo(x, y)
            }
            p.strokeWidth = 0.09f * r
            p.color = withAlpha(pal.accent, 80)
            cv.drawPath(path, p)
            p.strokeWidth = 0.03f * r
            p.color = hex(0xEEFFD27A)
            cv.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun paintOcean(cv: Canvas, p: Paint, path: Path, r: Float, pal: Palette) {
        // Kıtalar
        repeat(4) {
            val bx = rnd(-0.6f, 0.6f) * r
            val by = rnd(-0.6f, 0.6f) * r
            repeat(7) {
                p.color = withAlpha(if (Random.nextInt(3) == 0) hex(0xFF3A8F55) else pal.accent, 235)
                cv.drawCircle(
                    bx + rnd(-0.25f, 0.25f) * r,
                    by + rnd(-0.2f, 0.2f) * r,
                    rnd(0.08f, 0.2f) * r, p
                )
            }
        }
        // Bulutlar
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.07f * r
        p.color = withAlpha(Color.WHITE, 150)
        repeat(5) {
            val y = rnd(-0.8f, 0.8f) * r
            val x = rnd(-0.9f, 0.2f) * r
            val l = rnd(0.35f, 0.75f) * r
            path.reset()
            path.moveTo(x, y)
            path.quadTo(x + l * 0.5f, y - 0.08f * r, x + l, y + rnd(-0.05f, 0.05f) * r)
            cv.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
    }
}
