package com.example.gezegenkacisi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
 * Akış: MENÜ (harita + seviye seçimi) → HAZIR (dokun ve başla) → OYUN → PATLAMA → OYUN BİTTİ.
 *
 * Dünya koordinatları: ekran yüksekliği her zaman 1000 birimdir, genişlik ekran oranına göre değişir.
 * Böylece oyun her telefonda aynı hissettirir.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {

    private enum class State { MENU, READY, PLAYING, DYING, OVER, MP_LOBBY, MP_COUNTDOWN, MP_PLAYING, MP_OVER }

    /** 2 oyuncu lobisinin aşamaları. */
    private enum class Lobby { CHOOSE, HOSTING, JOINING, CONNECTED }

    private class Palette(val base: Int, val dark: Int, val light: Int, val accent: Int, val atmo: Int)

    /** Önceden çizilmiş gezegen görseli (her karede yeniden çizmemek için). */
    private class PlanetArt(val bitmap: Bitmap, val baseR: Float, val padX: Float, val padY: Float)

    /** [rot] başlangıç açısı, [spin] derece/saniye dönüş hızı (yalnızca asteroitler döner). */
    private class Planet(
        val dx: Float, val y: Float, val r: Float, val art: PlanetArt,
        val rot: Float = 0f, val spin: Float = 0f
    )

    /** Haritaya özgü atmosfer parçacığı: kar tanesi, kıvılcım, rüzgâr çizgisi, taş, uydu. */
    private class Mote {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var size = 1f; var phase = 0f; var len = 0f
    }

    /** Lav Denizi'nde yerden fırlayan ateş topu. Önce [warn] süresi boyunca zemin kızarır. */
    private class Fireball(var x: Float, var y: Float, var vy: Float, var warn: Float)

    /** Bir engel kulesi. Asteroit Kuşağı'nda tüm kule [swayAmp] kadar yukarı aşağı salınır. */
    private class Column(var x: Float, val planets: List<Planet>, val swayAmp: Float, val swayPhase: Float) {
        var scored = false
        var offset = 0f
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
        var owner = 0 // 0: ben, 1: rakip (bölünmüş ekranda hangi yarıda çizileceği), -1: ikisi de
    }

    companion object {
        // --- Temel fizik (seviye ve harita ayarları GameConfig.kt dosyasında) ---
        private const val WORLD_H = 1000f
        private const val GRAVITY = 1850f        // yer çekimi
        private const val FLAP_V = -540f         // dokununca kazanılan yukarı hız
        private const val MAX_FALL = 820f        // en yüksek düşüş hızı
        private const val MAIN_R = 86f
        private const val MOON_R = 56f
        private const val SWAY_SPEED = 1.7f      // asteroit kulelerinin salınım hızı
        private const val WIND_BASE = 430f       // gaz devlerinde rüzgârın en büyük kuvveti (Orta)
        private const val FIRE_GRAVITY = 1150f   // ateş topunun yer çekimi
        private const val FIRE_R = 15f           // ateş topunun çarpışma yarıçapı

        // --- 2 oyuncu ---
        private const val MP_STEP = 1f / 120f     // iki telefonda aynı sabit adımla simülasyon
        private const val MP_ROCKET_X = 180f      // ekran oranından bağımsız sabit roket konumu
        private const val MP_FIRST_X = 900f       // ilk kulenin konumu
        private const val MP_SPAWN_X = 2100f      // yeni kulelerin üretildiği uzaklık
        private const val MP_COUNTDOWN_NS = 3_000_000_000L

        private const val PREFS = "gezegen_kacisi"
        private const val KEY_BEST_LEGACY = "best"
        private const val KEY_MAP = "map"
        private const val KEY_DIFF = "difficulty"
        private const val KEY_SOUND = "sound"
        private const val KEY_MP_HELP_SEEN = "mp_help_seen"

        private val DEG = (PI / 180.0).toFloat()
        private val TWO_PI = (PI * 2.0).toFloat()

        private fun hex(v: Long): Int = v.toInt()
        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        private fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sound = SoundManager(context)

    // --- Seçimler ---
    private var mapIndex = prefs.getInt(KEY_MAP, 0).coerceIn(0, Maps.ALL.size - 1)
    private var map: GameMap = Maps.ALL[mapIndex]
    private var difficulty: Difficulty =
        Difficulty.entries.getOrElse(prefs.getInt(KEY_DIFF, Difficulty.NORMAL.ordinal)) { Difficulty.NORMAL }

    // --- Ekran ölçeği ---
    private var scale = 1f
    private var worldW = 500f   // oyun dünyasının görünen genişliği (2 oyuncuda yarım ekrana göre)
    private var uiW = 500f      // tam ekran arayüz genişliği
    private var curW = 500f     // drawFit için geçerli genişlik
    private var artScale = 0f
    private var artMap = -1

    // --- Oyun durumu ---
    private var state = State.MENU
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

    private var speed = difficulty.baseSpeed
    private var score = 0
    private var best = 0
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

    // --- Harita tehlikeleri ---
    private val motes = ArrayList<Mote>()
    private val fireballs = ArrayList<Fireball>()
    private var fireTimer = 0f
    private var wind = 0f          // + aşağı, - yukarı iter
    private var windPhase = 0f

    // --- Paylaşılan dünya: 2 oyuncuda iki telefon aynı tohumla aynı parkuru üretir ---
    private var worldRng = Random(1)
    private var progress = 0       // roketin hizasından geçen kule sayısı
    private var worldTime = 0f     // rüzgâr ve salınım bu zamana göre hesaplanır

    // --- 2 oyuncu ---
    private var net: NetLink? = null
    private var lobby = Lobby.CHOOSE
    private var netMessage = ""
    private var mpScale = 1f
    private var mpStartNs = 0L
    private var simTime = 0f
    private var myAlive = true
    private var oppAlive = true
    private var oppY = WORLD_H * 0.45f
    private var oppDispY = WORLD_H * 0.45f
    private var oppAngle = 0f
    private var oppBoost = 0f
    private var oppScore = 0
    private var sendTimer = 0f
    private var endTimer = 0f
    private var mpResult = 0          // 1 kazandın, -1 kaybettin, 0 berabere
    private var mpDisconnected = false
    private var lobbyHelp = false      // "Nasıl kurulur?" penceresi açık mı
    private val emitAccs = FloatArray(2)
    private var spawnOwner = 0

    // --- Menü ---
    private var cardAnim = 0f      // harita değişince kart içeriği kayar (-1..1 → 0)
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0f

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
    private val strPaused = context.getString(R.string.paused)
    private val strTapToContinue = context.getString(R.string.tap_to_continue)
    private val strMapLabel = context.getString(R.string.label_map)
    private val strLevelLabel = context.getString(R.string.label_level)
    private val strStart = context.getString(R.string.start)
    private val strRetry = context.getString(R.string.retry)
    private val strMenu = context.getString(R.string.menu)
    private val strWind = context.getString(R.string.wind)
    private val mapTips = Maps.ALL.map { context.getString(it.tipRes) }
    private val strTwoPlayers = context.getString(R.string.two_players)
    private val strMpInfo1 = context.getString(R.string.mp_info1)
    private val strMpInfo2 = context.getString(R.string.mp_info2)
    private val strHost = context.getString(R.string.mp_host)
    private val strJoin = context.getString(R.string.mp_join)
    private val strBack = context.getString(R.string.back)
    private val strLeave = context.getString(R.string.mp_leave)
    private val strRoomOpen = context.getString(R.string.mp_room_open)
    private val strWaiting = context.getString(R.string.mp_waiting)
    private val strSearching = context.getString(R.string.mp_searching)
    private val strJoinHint = context.getString(R.string.mp_join_hint)
    private val strConnected = context.getString(R.string.mp_connected)
    private val strMpStart = context.getString(R.string.mp_start)
    private val strWaitHost = context.getString(R.string.mp_wait_host)
    private val strYou = context.getString(R.string.mp_you)
    private val strOpponent = context.getString(R.string.mp_opponent)
    private val strYouCrashed = context.getString(R.string.mp_you_crashed)
    private val strWatching = context.getString(R.string.mp_watching)
    private val strOppCrashed = context.getString(R.string.mp_opp_crashed)
    private val strWin = context.getString(R.string.mp_win)
    private val strLose = context.getString(R.string.mp_lose)
    private val strDraw = context.getString(R.string.mp_draw)
    private val strDisconnected = context.getString(R.string.mp_disconnected)
    private val strRetryWait = context.getString(R.string.mp_retry_wait)
    private val strNoNetwork = context.getString(R.string.mp_failed)
    private val strMpPick = context.getString(R.string.mp_pick)
    private val strBothCanPick = context.getString(R.string.mp_both_pick)
    private val strHelpTitle = context.getString(R.string.mp_help_title)
    private val strHelpClose = context.getString(R.string.mp_help_close)
    private val strHelpTip = context.getString(R.string.mp_help_tip)
    private val helpSteps = listOf(
        context.getString(R.string.mp_help_1),
        context.getString(R.string.mp_help_2),
        context.getString(R.string.mp_help_3),
        context.getString(R.string.mp_help_4),
        context.getString(R.string.mp_help_5),
        context.getString(R.string.mp_help_6)
    )
    private val mapNames = Maps.ALL.map { context.getString(it.nameRes) }
    private val mapDescs = Maps.ALL.map { context.getString(it.descRes) }
    private val diffNames = Difficulty.entries.map { context.getString(it.nameRes) }
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
    private val colDarkText = hex(0xFF2A1A00)
    private val colGreen = hex(0xFF4CD98A)
    private val colBlue = hex(0xFF4F8BFF)       // rakibin roket rengi
    private val colBlueDark = hex(0xFF2F5FD0)

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
    private val helpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
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
    private val iconPath = Path()

    // --- Arayüz düğmeleri (dünya birimi; layoutUi() içinde yerleştirilir) ---
    private val soundBtn = RectF()
    private val mapCard = RectF()
    private val arrowLeft = RectF()
    private val arrowRight = RectF()
    private val diffBtns = Array(Difficulty.entries.size) { RectF() }
    private val startBtn = RectF()
    private val retryBtn = RectF()
    private val menuBtn = RectF()
    private val multiBtn = RectF()
    private val hostBtn = RectF()
    private val joinBtn = RectF()
    private val backBtn = RectF()
    private val backCircle = RectF()   // lobide sol üstteki geri düğmesi
    private val helpBtn = RectF()      // lobide sağ üstteki "?" düğmesi

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
        PlanetType.CRYSTAL to listOf(
            Palette(hex(0xFF7FE8FF), hex(0xFF1F6FA8), hex(0xFFF0FDFF), hex(0xFFBFF6FF), hex(0xFF8CF0FF)),
            Palette(hex(0xFFA8C8FF), hex(0xFF3A4FB0), hex(0xFFFFFFFF), hex(0xFFDDE6FF), hex(0xFFB0C8FF)),
            Palette(hex(0xFF8CFFE0), hex(0xFF1F8A78), hex(0xFFEFFFFA), hex(0xFFC0FFEE), hex(0xFF9CFFE6))
        ),
        PlanetType.ASTEROID to listOf(
            Palette(hex(0xFF8A7B6A), hex(0xFF3A3028), hex(0xFFC8B8A0), hex(0xFF5A4C40), hex(0xFF8A7B6A)),
            Palette(hex(0xFF7A7C86), hex(0xFF2E3038), hex(0xFFB8BCC8), hex(0xFF50525C), hex(0xFF7A7C86)),
            Palette(hex(0xFF9A7350), hex(0xFF3E2A1A), hex(0xFFD8B088), hex(0xFF6A4A30), hex(0xFF9A7350))
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
        sound.enabled = prefs.getBoolean(KEY_SOUND, true)
        sound.setMusicTheme(map.musicTheme)
        refreshBestTexts()
    }

    // =====================================================================
    // Yaşam döngüsü
    // =====================================================================

    fun resume() {
        sound.onResume()
        if (running) return
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        if (state == State.PLAYING) paused = true
        // 2 oyuncuda oyun bekletilemez: uygulamadan çıkan oyuncu yanmış sayılır
        if (state == State.MP_PLAYING && myAlive) mpCrash()
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        sound.onPause()
    }

    fun release() {
        net?.close()
        net = null
        sound.release()
    }

    /** Geri tuşu. true dönerse olay tüketildi; false ise uygulama kapanabilir. */
    fun onBack(): Boolean {
        when (state) {
            State.MENU -> return false
            State.PLAYING -> if (!paused) paused = true else toMenu()
            State.MP_LOBBY, State.MP_COUNTDOWN, State.MP_PLAYING, State.MP_OVER -> leaveMp()
            else -> toMenu()
        }
        return true
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
        uiW = w / scale
        curW = uiW
        if (isMpView()) {
            mpScale = (h / 2f) / WORLD_H
            worldW = w / mpScale
            rocketX = MP_ROCKET_X
        } else {
            worldW = uiW
            rocketX = worldW * 0.3f
        }
        buildBackground(w, h)
        // Gezegen görselleri pahalı; ölçek çok değişmediyse yeniden üretme.
        if (mainArts.isEmpty() || artMap != mapIndex || abs(scale - artScale) / artScale > 0.15f) buildPlanetArts()
        buildStars()
        buildMotes()
        layoutUi()
        if (state == State.MENU || state == State.READY) resetGame()
    }

    /** Menü ve oyun sonu düğmelerinin yerleri. */
    private fun layoutUi() {
        val cx = uiW / 2f
        soundBtn.set(uiW - 78f, 24f, uiW - 22f, 80f)

        val cardHalf = min(uiW / 2f - 60f, 210f)
        mapCard.set(cx - cardHalf, 262f, cx + cardHalf, 592f)
        arrowLeft.set(mapCard.left - 54f, 398f, mapCard.left - 6f, 458f)
        arrowRight.set(mapCard.right + 6f, 398f, mapCard.right + 54f, 458f)

        val rowHalf = min(uiW / 2f - 26f, 230f)
        val gap = 10f
        val n = diffBtns.size
        val bw = (rowHalf * 2f - gap * (n - 1)) / n
        for (i in 0 until n) {
            val l = cx - rowHalf + i * (bw + gap)
            diffBtns[i].set(l, 694f, l + bw, 752f)
        }

        val sw = min(uiW - 96f, 300f)
        startBtn.set(cx - sw / 2f, 778f, cx + sw / 2f, 850f)
        val mw = min(uiW - 140f, 240f)
        multiBtn.set(cx - mw / 2f, 868f, cx + mw / 2f, 922f)

        val lw = min(uiW / 2f - 50f, 170f)
        hostBtn.set(cx - lw, 470f, cx + lw, 540f)
        joinBtn.set(cx - lw, 562f, cx + lw, 632f)
        backBtn.set(cx - 110f, 870f, cx + 110f, 928f)
        backCircle.set(22f, 24f, 78f, 80f)
        helpBtn.set(uiW - 78f, 24f, uiW - 22f, 80f)

        val half = min(uiW / 2f - 28f, 180f)
        retryBtn.set(cx - half, 684f, cx - 7f, 748f)
        menuBtn.set(cx + 7f, 684f, cx + half, 748f)
    }

    // =====================================================================
    // Girdi
    // =====================================================================

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wx = event.x / scale
        val wy = event.y / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = wx
                downY = wy
                downTime = time
                onPress()
            }
            // İkinci parmakla da itiş yapılabilsin
            MotionEvent.ACTION_POINTER_DOWN -> if (state == State.PLAYING || state == State.MP_PLAYING) onPress()
            MotionEvent.ACTION_UP -> onRelease(wx, wy)
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP -> {
                when (state) {
                    State.MENU -> if (keyCode != KeyEvent.KEYCODE_DPAD_UP) startReady()
                    State.OVER -> if (stateTimer > 0.6f) startReady()
                    else -> onPress()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (state == State.MENU) { changeMap(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (state == State.MENU) { changeMap(1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Parmak ekrana değdiği an: oyunda gecikmesiz itiş. */
    private fun onPress() {
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
            State.MP_PLAYING -> if (myAlive) flap()
            else -> Unit
        }
    }

    /** Parmak kalktığında: menü ve oyun sonu düğmeleri. */
    private fun onRelease(x: Float, y: Float) {
        val dx = x - downX
        val dy = y - downY
        val moved = abs(dx) > 28f || abs(dy) > 28f
        when (state) {
            State.MENU -> {
                val swipe = abs(dx) > 60f && abs(dx) > abs(dy) * 1.3f &&
                    downY > mapCard.top - 40f && downY < mapCard.bottom + 40f
                if (swipe) changeMap(if (dx < 0f) 1 else -1)
                else if (!moved) menuTap(downX, downY)
            }
            State.MP_LOBBY -> {
                val swipe = abs(dx) > 60f && abs(dx) > abs(dy) * 1.3f &&
                    downY > mapCard.top - 40f && downY < mapCard.bottom + 40f
                val pickable = lobby == Lobby.CHOOSE || lobby == Lobby.CONNECTED
                if (lobbyHelp) {
                    if (!moved) { lobbyHelp = false; sound.play(SoundManager.Sfx.CLICK) }
                } else if (pickable && swipe) {
                    pickMap(if (dx < 0f) 1 else -1)
                } else if (!moved) lobbyTap(downX, downY)
            }
            State.MP_OVER -> if (!moved && stateTimer > 0.6f) mpOverTap(downX, downY)
            State.OVER -> if (!moved && stateTimer > 0.6f) {
                when {
                    hit(soundBtn, downX, downY) -> toggleSound()
                    hit(menuBtn, downX, downY) -> { sound.play(SoundManager.Sfx.CLICK); toMenu() }
                    else -> startReady() // "Tekrar" ya da ekranın herhangi bir yeri
                }
            }
            else -> Unit
        }
    }

    private fun menuTap(x: Float, y: Float) {
        when {
            hit(soundBtn, x, y) -> toggleSound()
            hit(arrowLeft, x, y, 18f) -> changeMap(-1)
            hit(arrowRight, x, y, 18f) -> changeMap(1)
            hit(startBtn, x, y) -> startReady()
            hit(multiBtn, x, y, 6f) -> enterLobby()
            else -> {
                for (i in diffBtns.indices) {
                    if (hit(diffBtns[i], x, y, 6f)) {
                        selectDifficulty(Difficulty.entries[i])
                        return
                    }
                }
            }
        }
    }

    private fun hit(r: RectF, x: Float, y: Float, pad: Float = 10f): Boolean =
        x >= r.left - pad && x <= r.right + pad && y >= r.top - pad && y <= r.bottom + pad

    private fun flap() {
        rocketVy = FLAP_V * map.flapMul
        boost = 1f
        repeat(8) { emitExhaust(1.3f) }
        sound.play(SoundManager.Sfx.FLAP)
    }

    // =====================================================================
    // Menü işlemleri
    // =====================================================================

    private fun changeMap(dir: Int) {
        val n = Maps.ALL.size
        mapIndex = ((mapIndex + dir) % n + n) % n
        map = Maps.ALL[mapIndex]
        prefs.edit().putInt(KEY_MAP, mapIndex).apply()
        if (width > 0 && height > 0) {
            buildBackground(width, height)
            buildPlanetArts()
            buildStars()
            buildMotes()
        }
        cardAnim = dir.toFloat()
        sound.setMusicTheme(map.musicTheme)
        sound.play(SoundManager.Sfx.SWIPE)
        refreshBestTexts()
        sendSelection()
    }

    private fun selectDifficulty(d: Difficulty) {
        if (d != difficulty) {
            difficulty = d
            prefs.edit().putInt(KEY_DIFF, d.ordinal).apply()
            refreshBestTexts()
            sendSelection()
        }
        sound.play(SoundManager.Sfx.CLICK)
    }

    private fun toggleSound() {
        sound.enabled = !sound.enabled
        prefs.edit().putBoolean(KEY_SOUND, sound.enabled).apply()
        sound.play(SoundManager.Sfx.CLICK)
    }

    private fun toMenu() {
        resetGame()
        state = State.MENU
    }

    private fun startReady() {
        resetGame()
        state = State.READY
        sound.play(SoundManager.Sfx.START)
    }

    // =====================================================================
    // Oyun mantığı
    // =====================================================================

    private fun resetGame(seed: Long = System.nanoTime()) {
        worldRng = Random(seed)
        progress = 0
        worldTime = 0f
        columns.clear()
        stateTimer = 0f
        rocketY = WORLD_H * 0.45f
        rocketVy = 0f
        rocketAngle = 0f
        score = 0
        scoreText = "0"
        newBest = false
        speed = difficulty.baseSpeed * map.speedMul
        lastGapY = WORLD_H / 2f
        paused = false
        fireballs.clear()
        fireTimer = 2.2f
        windPhase = wr(0f, TWO_PI)
    }

    /** Dünya üretimi için tohumlu rastgele sayı: iki telefonda aynı sonucu verir. */
    private fun wr(a: Float, b: Float): Float = a + worldRng.nextFloat() * (b - a)

    private fun computeWind(): Float = if (map.kind == MapKind.GAS) {
        WIND_BASE * difficulty.hazard * sin(worldTime * 0.62f + windPhase) *
            (0.75f + 0.25f * sin(worldTime * 1.7f))
    } else 0f

    private fun isMpView() =
        state == State.MP_COUNTDOWN || state == State.MP_PLAYING || state == State.MP_OVER

    private fun worldRunning() = state == State.PLAYING || state == State.MP_PLAYING

    /** Roketin bir adımlık fiziği: yer çekimi, rüzgâr, tavan ve eğim. */
    private fun stepRocket(dt: Float) {
        val maxFall = MAX_FALL * min(1f, map.gravityMul)
        rocketVy = min(rocketVy + (GRAVITY * map.gravityMul + wind) * dt, maxFall)
        rocketY += rocketVy * dt
        if (rocketY < 22f) {
            rocketY = 22f
            if (rocketVy < 0f) rocketVy = 0f
        }
        val target = (rocketVy / MAX_FALL * 75f).coerceIn(-28f, 75f)
        rocketAngle += (target - rocketAngle) * min(1f, dt * 12f)
    }

    private fun currentSpeed(): Float =
        (difficulty.baseSpeed + min(progress, difficulty.speedCapScore) * difficulty.speedPerScore) * map.speedMul

    private fun update(dt: Float) {
        time += dt
        stateTimer += dt
        boost = max(0f, boost - dt * 3.5f)
        scorePulse = max(0f, scorePulse - dt * 4f)
        shake = max(0f, shake - dt)
        flash = max(0f, flash - dt)
        shockT += dt
        cardAnim *= exp(-dt * 12f)
        if (abs(cardAnim) < 0.002f) cardAnim = 0f

        val base = difficulty.baseSpeed * map.speedMul
        val worldSpeed = when (state) {
            State.MENU -> base * 0.5f
            State.READY -> base * 0.7f
            State.PLAYING, State.MP_PLAYING -> speed
            State.DYING, State.OVER, State.MP_OVER -> base * 0.12f
            State.MP_LOBBY -> base * 0.5f
            State.MP_COUNTDOWN -> base * 0.3f
        }
        pollNet()
        updateStars(dt, worldSpeed)
        updateShootingStar(dt)
        // Gaz devlerinde rüzgâr: yavaşça yön değiştirir; menüde de görsel olarak eser
        if (!isMpView()) {
            worldTime += dt
            wind = computeWind()
        }
        updateMotes(dt, worldSpeed)

        when (state) {
            State.MENU -> Unit
            State.READY -> {
                // Başlamadan önce roket hafifçe süzülür
                rocketY = WORLD_H * 0.45f + sin(time * 2.6f) * 14f
                rocketAngle = cos(time * 2.6f) * -7f
                emit(dt, 28f, 0.8f)
            }
            State.PLAYING -> {
                stepRocket(dt)
                speed = currentSpeed()
                updateColumns(dt)
                updateFireballs(dt)
                emit(dt, 55f, 1f + boost * 0.6f)
                if (rocketHits()) crash()
            }
            State.DYING -> {
                updateFireballs(dt)
                if (stateTimer > 0.9f) {
                state = State.OVER
                stateTimer = 0f
                sound.play(if (newBest) SoundManager.Sfx.RECORD else SoundManager.Sfx.GAME_OVER)
                }
            }
            State.OVER -> Unit
            State.MP_LOBBY -> Unit
            State.MP_COUNTDOWN -> {
                rocketY = WORLD_H * 0.45f + sin(time * 2.6f) * 14f
                rocketAngle = cos(time * 2.6f) * -7f
                oppDispY = WORLD_H * 0.45f + sin(time * 2.6f + 1.2f) * 14f
                oppAngle = cos(time * 2.6f + 1.2f) * -7f
                emit(dt, 28f, 0.8f)
                emitFor(1, dt, 28f, 0.8f, rocketX, oppDispY, oppAngle)
                if (System.nanoTime() >= mpStartNs) {
                    state = State.MP_PLAYING
                    stateTimer = 0f
                    simTime = 0f
                    rocketY = WORLD_H * 0.45f
                    rocketVy = 0f
                    rocketAngle = 0f
                    oppY = rocketY
                    flap()
                }
            }
            State.MP_PLAYING -> updateMp(dt)
            State.MP_OVER -> Unit
        }
        updateParticles(dt)
    }

    private fun updateColumns(dt: Float) {
        for (col in columns) {
            col.x -= speed * dt
            if (col.swayAmp > 0f) col.offset = col.swayAmp * sin(worldTime * SWAY_SPEED + col.swayPhase)
        }

        val mp = isMpView()
        val spawnX = if (mp) MP_SPAWN_X else worldW + 200f
        val last = columns.lastOrNull()
        if (last == null) {
            columns.add(makeColumn(if (mp) MP_FIRST_X else spawnX))
        } else if (last.x + difficulty.spacing <= spawnX) {
            columns.add(makeColumn(last.x + difficulty.spacing))
        }

        val iter = columns.iterator()
        while (iter.hasNext()) {
            val col = iter.next()
            if (col.x + col.halfWidth < -10f) iter.remove()
        }

        for (col in columns) {
            if (!col.scored && col.x < rocketX) {
                col.scored = true
                progress++
                if (state == State.PLAYING || (state == State.MP_PLAYING && myAlive)) {
                    score = progress
                    scoreText = score.toString()
                    scorePulse = 1f
                    sound.play(SoundManager.Sfx.SCORE)
                }
            }
        }
    }

    /** Ortada bir geçiş boşluğu bırakan, üstte ve altta gezegen kulesi olan bir engel üretir. */
    private fun makeColumn(x: Float): Column {
        val d = difficulty
        val gapH = max(d.minGap, d.startGap - progress * d.gapShrink)
        val amp = if (map.kind == MapKind.ASTEROID) d.swayAmp else 0f
        val minY = 110f + gapH / 2f + amp
        val maxY = WORLD_H - 110f - gapH / 2f - amp
        val gapY = (lastGapY + wr(-d.gapJump, d.gapJump)).coerceIn(minY, maxY)
        lastGapY = gapY

        val planets = ArrayList<Planet>()
        buildStack(planets, gapY - gapH / 2f, -1f, amp) // üst kule
        buildStack(planets, gapY + gapH / 2f, 1f, amp)  // alt kule
        val col = Column(x, planets, amp, wr(0f, TWO_PI))
        if (amp > 0f) col.offset = amp * sin(worldTime * SWAY_SPEED + col.swayPhase)
        return col
    }

    /**
     * Boşluğun kenarına büyük bir gezegen koyar, ekranın kenarına kadar da uydularla doldurur.
     * Daireler birbirine bindiği için aralarından sızılamaz.
     */
    private fun buildStack(out: MutableList<Planet>, gapEdge: Float, dir: Float, extra: Float) {
        val stack = ArrayList<Planet>()
        val mainR = wr(60f, MAIN_R) * map.planetMul
        var cy = gapEdge + dir * mainR
        val spinning = map.kind == MapKind.ASTEROID
        stack.add(
            Planet(
                0f, cy, mainR, mainArts[worldRng.nextInt(mainArts.size)],
                if (spinning) wr(0f, 360f) else 0f, if (spinning) wr(-35f, 35f) else 0f
            )
        )

        var prevR = mainR
        var farEdge = cy + dir * mainR
        var guard = 0
        val topLimit = -10f - extra
        val bottomLimit = WORLD_H + 10f + extra
        while (guard++ < 16 && (if (dir < 0f) farEdge > topLimit else farEdge < bottomLimit)) {
            val r = wr(34f, MOON_R)
            val dx = wr(-0.25f, 0.25f) * min(r, prevR)
            cy = farEdge + dir * (r * 0.7f)
            stack.add(
                Planet(
                    dx, cy, r, moonArts[worldRng.nextInt(moonArts.size)],
                    if (spinning) wr(0f, 360f) else 0f, if (spinning) wr(-60f, 60f) else 0f
                )
            )
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
            for (fb in fireballs) {
                if (fb.warn > 0f) continue
                val dx = fb.x - hx
                val dy = fb.y - hy
                val rr = FIRE_R + hr
                if (dx * dx + dy * dy < rr * rr) return true
            }
            for (col in columns) {
                if (abs(col.x - hx) > col.halfWidth + hr) continue
                for (pl in col.planets) {
                    val dx = col.x + pl.dx - hx
                    val dy = pl.y + col.offset - hy
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
        sound.play(SoundManager.Sfx.CRASH)
        if (score > best) {
            best = score
            newBest = true
            prefs.edit().putInt(bestKey(), best).apply()
            refreshBestTexts()
        }
    }

    /** Her harita + seviye ikilisinin kendi rekoru var. */
    private fun bestKey() = "best_${mapIndex}_${difficulty.ordinal}"

    private fun refreshBestTexts() {
        best = prefs.getInt(bestKey(), 0)
        // Eski sürümdeki tek rekor, ilk harita + orta seviyeye taşınır
        if (best == 0 && mapIndex == 0 && difficulty == Difficulty.NORMAL) {
            best = prefs.getInt(KEY_BEST_LEGACY, 0)
        }
        bestValueText = best.toString()
        bestText = context.getString(R.string.best_format, best)
    }

    // =====================================================================
    // 2 oyuncu: lobi, bağlantı ve maç
    // =====================================================================

    private fun enterLobby() {
        sound.play(SoundManager.Sfx.CLICK)
        resetGame()
        state = State.MP_LOBBY
        lobby = Lobby.CHOOSE
        netMessage = ""
        // İlk kez 2 oyuncuya girilince "Nasıl kurulur?" kendiliğinden açılır
        lobbyHelp = !prefs.getBoolean(KEY_MP_HELP_SEEN, false)
        if (lobbyHelp) prefs.edit().putBoolean(KEY_MP_HELP_SEEN, true).apply()
    }

    private fun closeNet() {
        net?.close()
        net = null
    }

    private fun startHost() {
        closeNet()
        net = NetLink(context).also { it.host() }
        lobby = Lobby.HOSTING
        netMessage = ""
        sound.play(SoundManager.Sfx.CLICK)
    }

    private fun startJoin() {
        closeNet()
        net = NetLink(context).also { it.join() }
        lobby = Lobby.JOINING
        netMessage = ""
        sound.play(SoundManager.Sfx.CLICK)
    }

    /** 2 oyuncudan çıkıp ana menüye döner. */
    private fun leaveMp() {
        closeNet()
        val wasMpView = isMpView()
        sound.play(SoundManager.Sfx.CLICK)
        toMenu()
        if (wasMpView) {
            worldW = uiW
            rocketX = worldW * 0.3f
            buildStars()
            buildMotes()
        }
    }

    private fun lobbyTap(x: Float, y: Float) {
        if (hit(helpBtn, x, y)) {
            lobbyHelp = true
            sound.play(SoundManager.Sfx.CLICK)
            return
        }
        when (lobby) {
            Lobby.CHOOSE -> when {
                hit(backCircle, x, y) -> { sound.play(SoundManager.Sfx.CLICK); toMenu() }
                hit(startBtn, x, y) -> startHost()
                hit(multiBtn, x, y, 6f) -> startJoin()
                else -> pickTap(x, y)
            }
            Lobby.HOSTING, Lobby.JOINING -> if (hit(backBtn, x, y) || hit(backCircle, x, y)) {
                closeNet()
                lobby = Lobby.CHOOSE
                sound.play(SoundManager.Sfx.CLICK)
            }
            Lobby.CONNECTED -> when {
                hit(backCircle, x, y) || hit(multiBtn, x, y, 6f) -> leaveMp()
                hit(startBtn, x, y) && net?.isHost == true -> hostStart()
                else -> pickTap(x, y)
            }
        }
    }

    /** Harita okları ve seviye düğmeleri (lobide). */
    private fun pickTap(x: Float, y: Float) {
        when {
            hit(arrowLeft, x, y, 18f) -> pickMap(-1)
            hit(arrowRight, x, y, 18f) -> pickMap(1)
            else -> for (i in diffBtns.indices) {
                if (hit(diffBtns[i], x, y, 6f)) {
                    pickDifficulty(Difficulty.entries[i])
                    return
                }
            }
        }
    }

    /**
     * Lobide harita değiştirme. Oda kurucusu doğrudan değiştirir ve karşıya bildirir;
     * katılan oyuncu istek gönderir, kurucu onaylayıp iki telefona da uygular.
     */
    private fun pickMap(dir: Int) {
        val n = net
        if (n != null && n.connected && !n.isHost) {
            val count = Maps.ALL.size
            n.send("R", ((mapIndex + dir) % count + count) % count, difficulty.ordinal)
            sound.play(SoundManager.Sfx.CLICK)
        } else {
            changeMap(dir)
        }
    }

    private fun pickDifficulty(d: Difficulty) {
        val n = net
        if (n != null && n.connected && !n.isHost) {
            n.send("R", mapIndex, d.ordinal)
            sound.play(SoundManager.Sfx.CLICK)
        } else {
            selectDifficulty(d)
        }
    }

    private fun mpOverTap(x: Float, y: Float) {
        val canRetry = !mpDisconnected && net?.isHost == true
        if (canRetry) {
            when {
                hit(retryBtn, x, y) -> hostStart()
                hit(menuBtn, x, y) -> leaveMp()
            }
        } else if (hit(backBtn, x, y)) {
            leaveMp()
        }
    }

    /** Oda kurucusu harita/seviye değiştirince katılan telefona bildirir. */
    private fun sendSelection() {
        val n = net ?: return
        if (n.isHost && n.connected) n.send("C", mapIndex, difficulty.ordinal)
    }

    private fun applySelection(m: Int, d: Int) {
        val nd = Difficulty.entries.getOrNull(d) ?: return
        if (nd != difficulty) {
            difficulty = nd
            refreshBestTexts()
        }
        if (m in Maps.ALL.indices && m != mapIndex) changeMap(m - mapIndex)
    }

    /** Ağdan gelen olayları oyun döngüsünde işler. */
    private fun pollNet() {
        val n = net ?: return
        while (net === n) {
            val e = n.events.poll() ?: break
            when (e) {
                is NetLink.Event.Connected -> {
                    lobby = Lobby.CONNECTED
                    netMessage = ""
                    sound.play(SoundManager.Sfx.START)
                    n.send("H", NetLink.PROTOCOL)
                    sendSelection()
                }
                is NetLink.Event.Failed -> {
                    closeNet()
                    lobby = Lobby.CHOOSE
                    netMessage = strNoNetwork
                }
                is NetLink.Event.Disconnected -> onPeerLost()
                is NetLink.Event.Message -> handleMessage(e.parts)
            }
        }
    }

    private fun handleMessage(p: List<String>) {
        val host = net?.isHost == true
        when (p[0]) {
            "C" -> if (!host && p.size >= 3) {
                applySelection(p[1].toIntOrNull() ?: return, p[2].toIntOrNull() ?: return)
            }
            "R" -> if (host && p.size >= 3 && state == State.MP_LOBBY) {
                applySelection(p[1].toIntOrNull() ?: return, p[2].toIntOrNull() ?: return)
                sendSelection()
            }
            "G" -> if (!host && p.size >= 2) beginMatch(p[1].toLongOrNull() ?: return)
            "S" -> if (p.size >= 5) {
                oppY = (p[1].toIntOrNull() ?: return) / 10f
                oppAngle = (p[2].toIntOrNull() ?: return).toFloat()
                oppBoost = max(oppBoost, (p[3].toIntOrNull() ?: 0) / 100f)
                oppScore = p[4].toIntOrNull() ?: oppScore
            }
            "D" -> if (p.size >= 3 && (state == State.MP_PLAYING || state == State.MP_COUNTDOWN)) {
                oppDead(p[1].toIntOrNull() ?: oppScore, (p[2].toIntOrNull() ?: 0) / 10f)
            }
            "BYE" -> onPeerLost()
        }
    }

    private fun onPeerLost() {
        closeNet()
        when (state) {
            State.MP_LOBBY -> {
                lobby = Lobby.CHOOSE
                netMessage = strDisconnected
            }
            State.MP_COUNTDOWN, State.MP_PLAYING -> {
                mpDisconnected = true
                state = State.MP_OVER
                stateTimer = 0f
                sound.play(SoundManager.Sfx.GAME_OVER)
            }
            State.MP_OVER -> mpDisconnected = true
            else -> Unit
        }
    }

    /** Oda kurucusu yeni maçı başlatır: tohumu gönderir, iki telefon aynı parkuru üretir. */
    private fun hostStart() {
        val n = net ?: return
        if (!n.connected) return
        val seed = Random.nextLong()
        n.send("G", seed)
        beginMatch(seed)
    }

    private fun beginMatch(seed: Long) {
        state = State.MP_COUNTDOWN
        resetGame(seed)
        mpScale = (height / 2f) / WORLD_H
        worldW = width / mpScale
        rocketX = MP_ROCKET_X
        buildStars()
        buildMotes()
        myAlive = true
        oppAlive = true
        oppY = WORLD_H * 0.45f
        oppDispY = oppY
        oppAngle = 0f
        oppBoost = 0f
        oppScore = 0
        mpResult = 0
        mpDisconnected = false
        endTimer = 0f
        sendTimer = 0f
        simTime = 0f
        mpStartNs = System.nanoTime() + MP_COUNTDOWN_NS
        stateTimer = 0f
        sound.play(SoundManager.Sfx.START)
    }

    private fun updateMp(dt: Float) {
        // Dünya, başlangıçtan geçen gerçek süreye göre sabit adımlarla ilerler: iki telefon senkron kalır
        val target = (System.nanoTime() - mpStartNs) / 1_000_000_000f
        var steps = 0
        while (simTime + MP_STEP <= target && steps < 240) {
            mpStep(MP_STEP)
            simTime += MP_STEP
            steps++
        }
        if (steps == 240) simTime = target

        oppBoost = max(0f, oppBoost - dt * 3.5f)
        oppDispY += (oppY - oppDispY) * min(1f, dt * 14f)

        val n = net
        sendTimer -= dt
        if (myAlive && n != null && sendTimer <= 0f) {
            sendTimer = 0.05f
            n.send("S", (rocketY * 10f).toInt(), rocketAngle.toInt(), (boost * 100f).toInt(), score)
        }
        if (myAlive) emit(dt, 55f, 1f + boost * 0.6f)
        if (oppAlive) emitFor(1, dt, 55f, 1f + oppBoost * 0.6f, rocketX, oppDispY, oppAngle)

        if (!myAlive && !oppAlive) {
            endTimer += dt
            if (endTimer > 1.3f) finishMatch()
        }
    }

    private fun mpStep(dt: Float) {
        worldTime += dt
        wind = computeWind()
        speed = currentSpeed()
        if (myAlive) stepRocket(dt)
        updateColumns(dt)
        updateFireballs(dt)
        if (myAlive && rocketHits()) mpCrash()
    }

    private fun mpCrash() {
        if (!myAlive) return
        myAlive = false
        explode(rocketX, rocketY, 0)
        shake = 0.45f
        flash = 0.2f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sound.play(SoundManager.Sfx.CRASH)
        net?.send("D", score, (rocketY * 10f).toInt())
    }

    private fun oppDead(sc: Int, y: Float) {
        if (!oppAlive) return
        oppAlive = false
        oppScore = sc
        oppY = y
        oppDispY = y
        explode(rocketX, y, 1)
        sound.play(SoundManager.Sfx.CRASH)
    }

    private fun finishMatch() {
        state = State.MP_OVER
        stateTimer = 0f
        mpResult = when {
            score > oppScore -> 1
            score < oppScore -> -1
            else -> 0
        }
        sound.play(if (mpResult > 0) SoundManager.Sfx.RECORD else SoundManager.Sfx.GAME_OVER)
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
                0 -> map.starTint
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

    // --- Harita atmosferi: kar, kıvılcım, rüzgâr çizgileri, taşlar, uydular ---

    private fun buildMotes() {
        motes.clear()
        val count = when (map.kind) {
            MapKind.EARTH -> 4
            MapKind.ICE -> 90
            MapKind.LAVA -> 55
            MapKind.GAS -> 30
            MapKind.ASTEROID -> 22
        }
        repeat(count) { motes.add(Mote().also { resetMote(it, true) }) }
    }

    /** Parçacığı yeniden konumlar. [anywhere] ise ekranın herhangi bir yerine, değilse giriş kenarına. */
    private fun resetMote(m: Mote, anywhere: Boolean) {
        m.phase = rnd(0f, TWO_PI)
        when (map.kind) {
            MapKind.EARTH -> { // yavaş geçen uydular
                m.x = if (anywhere) rnd(0f, worldW) else worldW + 20f
                m.y = rnd(40f, WORLD_H * 0.6f)
                m.vx = -rnd(8f, 22f); m.vy = rnd(-3f, 3f)
                m.size = rnd(2.5f, 4f)
            }
            MapKind.ICE -> { // kar taneleri
                m.x = if (anywhere) rnd(0f, worldW) else rnd(0f, worldW + 80f)
                m.y = if (anywhere) rnd(0f, WORLD_H) else -10f
                m.vx = -rnd(10f, 30f); m.vy = rnd(28f, 70f)
                m.size = rnd(1.2f, 3.4f)
            }
            MapKind.LAVA -> { // yükselen kıvılcımlar
                m.x = rnd(0f, worldW + 40f)
                m.y = if (anywhere) rnd(0f, WORLD_H) else WORLD_H + 10f
                m.vx = rnd(-15f, 5f); m.vy = -rnd(40f, 120f)
                m.size = rnd(1.3f, 3.2f)
            }
            MapKind.GAS -> { // rüzgâr çizgileri
                m.x = if (anywhere) rnd(0f, worldW) else worldW + rnd(10f, 120f)
                m.y = rnd(0f, WORLD_H)
                m.vx = -rnd(120f, 220f); m.vy = 0f
                m.size = rnd(1.2f, 2.4f)
                m.len = rnd(40f, 110f)
            }
            MapKind.ASTEROID -> { // önden geçen küçük taşlar
                m.x = if (anywhere) rnd(0f, worldW) else worldW + rnd(10f, 200f)
                m.y = rnd(0f, WORLD_H)
                m.vx = -rnd(0.9f, 1.6f); m.vy = rnd(-12f, 12f) // vx burada dünya hızının katı
                m.size = rnd(2.5f, 7f)
                m.len = rnd(-120f, 120f) // dönüş hızı
            }
        }
    }

    private fun updateMotes(dt: Float, worldSpeed: Float) {
        for (m in motes) {
            when (map.kind) {
                MapKind.EARTH -> { m.x += m.vx * dt; m.y += m.vy * dt }
                MapKind.ICE -> {
                    m.x += (m.vx - worldSpeed * 0.25f + sin(time * 1.3f + m.phase) * 18f) * dt
                    m.y += m.vy * dt
                }
                MapKind.LAVA -> {
                    m.x += (m.vx - worldSpeed * 0.2f + sin(time * 3f + m.phase) * 12f) * dt
                    m.y += m.vy * dt
                }
                MapKind.GAS -> {
                    m.x += (m.vx - worldSpeed * 0.6f) * dt
                    m.y += wind * 0.22f * dt
                }
                MapKind.ASTEROID -> {
                    m.x += m.vx * worldSpeed * dt
                    m.y += m.vy * dt
                    m.phase += m.len * DEG * dt
                }
            }
            val out = m.x < -130f || m.y > WORLD_H + 20f || m.y < -30f
            if (out) resetMote(m, false)
        }
    }

    private fun drawMotes(cv: Canvas) {
        when (map.kind) {
            MapKind.EARTH -> for (m in motes) {
                paint.color = hex(0xFF9AA3C6)
                cv.drawRect(m.x - m.size * 1.6f, m.y - 0.8f, m.x + m.size * 1.6f, m.y + 0.8f, paint)
                cv.drawRect(m.x - 1.2f, m.y - 1.6f, m.x + 1.2f, m.y + 1.6f, paint)
                val blink = sin(time * 4f + m.phase) > 0.6f
                if (blink) {
                    paint.color = hex(0xFFFF5B4A)
                    cv.drawCircle(m.x + m.size * 1.6f, m.y, 1.6f, paint)
                }
            }
            MapKind.ICE -> for (m in motes) {
                paint.color = withAlpha(Color.WHITE, (120 + 80 * sin(time * 2f + m.phase)).toInt())
                cv.drawCircle(m.x, m.y, m.size, paint)
            }
            MapKind.LAVA -> for (m in motes) {
                val f = 0.6f + 0.4f * sin(time * 9f + m.phase)
                paint.color = withAlpha(hex(0xFFFF7A2B), (60 * f).toInt())
                cv.drawCircle(m.x, m.y, m.size * 2.6f, paint)
                paint.color = withAlpha(hex(0xFFFFD27A), (230 * f).toInt())
                cv.drawCircle(m.x, m.y, m.size, paint)
            }
            MapKind.GAS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (m in motes) {
                    // Çizgi, rüzgârın yönüne göre eğilir
                    val slope = wind * 0.22f / (abs(m.vx) + 1f)
                    paint.strokeWidth = m.size
                    paint.color = withAlpha(hex(0xFFF2C8FF), 70)
                    cv.drawLine(m.x, m.y, m.x + m.len, m.y - m.len * slope, paint)
                }
                paint.style = Paint.Style.FILL
            }
            MapKind.ASTEROID -> for (m in motes) {
                cv.save()
                cv.translate(m.x, m.y)
                cv.rotate(m.phase / DEG)
                paint.color = hex(0xFF5A5048)
                tmpRect.set(-m.size, -m.size * 0.7f, m.size, m.size * 0.7f)
                cv.drawOval(tmpRect, paint)
                paint.color = hex(0xFF8C7C68)
                tmpRect.set(-m.size * 0.7f, -m.size * 0.6f, m.size * 0.3f, m.size * 0.1f)
                cv.drawOval(tmpRect, paint)
                cv.restore()
            }
        }
    }

    // --- Lav Denizi: ateş topları ---

    private fun updateFireballs(dt: Float) {
        if (map.kind != MapKind.LAVA) return
        if (worldRunning()) {
            fireTimer -= dt
            if (fireTimer <= 0f) {
                fireTimer = wr(2.3f, 3.6f) / difficulty.hazard
                // Topu, roketin hizasına tepeye yakınken varacak şekilde ileriye koy
                val warn = 0.8f
                val maxX = if (isMpView()) 860f else worldW - 30f
                val x = min(maxX, rocketX + speed * (warn + wr(0.35f, 0.85f)))
                fireballs.add(Fireball(x, WORLD_H + 40f, -wr(980f, 1200f), warn))
            }
        }
        val iter = fireballs.iterator()
        while (iter.hasNext()) {
            val fb = iter.next()
            if (worldRunning()) fb.x -= speed * dt
            if (fb.warn > 0f) {
                fb.warn -= dt
                if (fb.warn <= 0f) sound.play(SoundManager.Sfx.FIRE)
                continue
            }
            fb.vy += FIRE_GRAVITY * dt
            fb.y += fb.vy * dt
            if (Random.nextFloat() < 0.7f) {
                spawnOwner = -1
                spawn(
                    fb.x + rnd(-6f, 6f), fb.y + rnd(-6f, 6f), rnd(-30f, 30f), -fb.vy * 0.15f,
                    rnd(0.25f, 0.5f), rnd(2f, 4.5f),
                    if (Random.nextBoolean()) hex(0xFFFFB347) else hex(0xFFFF5A1F), 2f
                )
                spawnOwner = 0
            }
            if (fb.vy > 0f && fb.y > WORLD_H + 60f || fb.x < -60f) iter.remove()
        }
    }

    private fun drawFireballs(cv: Canvas) {
        for (fb in fireballs) {
            if (fb.warn > 0f) {
                // Uyarı: zemin kızarır ve titreşir
                val k = 1f - fb.warn / 0.8f
                val pulse = 0.6f + 0.4f * sin(time * 30f)
                paint.color = withAlpha(hex(0xFFFF3B1F), (k * 150 * pulse).toInt())
                tmpRect.set(fb.x - 34f, WORLD_H - 26f, fb.x + 34f, WORLD_H + 26f)
                cv.drawOval(tmpRect, paint)
                paint.color = withAlpha(hex(0xFFFFD27A), (k * 220 * pulse).toInt())
                tmpRect.set(fb.x - 16f, WORLD_H - 10f, fb.x + 16f, WORLD_H + 10f)
                cv.drawOval(tmpRect, paint)
                continue
            }
            paint.color = withAlpha(hex(0xFFFF5A1F), 70)
            cv.drawCircle(fb.x, fb.y, FIRE_R * 2.4f, paint)
            paint.color = withAlpha(hex(0xFFFF8A2B), 160)
            cv.drawCircle(fb.x, fb.y, FIRE_R * 1.45f, paint)
            paint.color = hex(0xFFFFD27A)
            cv.drawCircle(fb.x, fb.y, FIRE_R, paint)
            paint.color = hex(0xFFFFFBE6)
            cv.drawCircle(fb.x - 3f, fb.y - 3f, FIRE_R * 0.45f, paint)
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
        p.owner = spawnOwner
    }

    private fun emit(dt: Float, rate: Float, strength: Float) =
        emitFor(0, dt, rate, strength, rocketX, rocketY, rocketAngle)

    /** [owner] roketinin egzozu (0: ben, 1: rakip). */
    private fun emitFor(owner: Int, dt: Float, rate: Float, strength: Float, x: Float, y: Float, angle: Float) {
        emitAccs[owner] += dt * rate
        while (emitAccs[owner] >= 1f) {
            emitAccs[owner] -= 1f
            emitExhaust(strength, x, y, angle, owner)
        }
    }

    private fun emitExhaust(
        strength: Float, x: Float = rocketX, y: Float = rocketY, angle: Float = rocketAngle, owner: Int = 0
    ) {
        spawnOwner = owner
        val rad = angle * DEG
        val cs = cos(rad)
        val sn = sin(rad)
        val tx = x - 24f * cs
        val ty = y - 24f * sn
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
        spawnOwner = 0
    }

    private fun explode(x: Float, y: Float, owner: Int = 0) {
        spawnOwner = owner
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
        spawnOwner = 0
        if (owner != 0) return
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
        if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null) else canvas.drawColor(map.bgColors[0])
        curW = uiW
        if (isMpView()) {
            drawMp(canvas)
            return
        }

        canvas.save()
        canvas.scale(scale, scale)
        if (shake > 0f) {
            val m = shake / 0.45f * 9f
            canvas.translate(rnd(-m, m), rnd(-m, m))
        }
        drawStars(canvas)
        drawShootingStar(canvas)
        drawMotes(canvas)
        drawColumns(canvas)
        drawFireballs(canvas)
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
                val py = pl.y + col.offset
                dst.set(px - art.padX * k, py - art.padY * k, px + art.padX * k, py + art.padY * k)
                if (pl.spin != 0f || pl.rot != 0f) {
                    cv.save()
                    cv.rotate(pl.rot + pl.spin * time, px, py)
                    cv.drawBitmap(art.bitmap, null, dst, bitmapPaint)
                    cv.restore()
                } else {
                    cv.drawBitmap(art.bitmap, null, dst, bitmapPaint)
                }
            }
        }
    }

    private fun drawParticles(cv: Canvas, owner: Int = 0) {
        for (p in particles) {
            if (!p.alive || (p.owner != owner && p.owner >= 0)) continue
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

    private fun drawRocket(
        cv: Canvas, x: Float = rocketX, y: Float = rocketY, angle: Float = rocketAngle,
        boostV: Float = boost, alt: Boolean = false
    ) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(angle)

        // Alev: dokununca bir anlığına uzar
        val flicker = 0.85f + 0.15f * sin(time * 47f) + rnd(-0.06f, 0.06f)
        val len = (13f + boostV * 24f) * flicker
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
        paint.color = if (alt) colBlueDark else colFin
        cv.drawPath(finTop, paint)
        cv.drawPath(finBottom, paint)

        // Gövde
        paint.color = Color.WHITE
        paint.shader = bodyShader
        cv.drawPath(bodyPath, paint)
        paint.shader = null
        paint.color = if (alt) colBlue else colAccent
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
        val cx = uiW / 2f
        when (state) {
            State.MENU -> drawMenu(cv, cx)
            State.READY -> drawReady(cv, cx)
            State.PLAYING, State.DYING -> drawScore(cv, cx)
            State.OVER -> drawGameOver(cv, cx)
            State.MP_LOBBY -> drawLobby(cv, cx)
            State.MP_COUNTDOWN, State.MP_PLAYING, State.MP_OVER -> Unit
        }
        if (paused) drawPaused(cv, cx)
    }

    /**
     * Ana menü. Aynı ekran 2 oyuncu lobisinde de kullanılır:
     * [mp] = 1 oda kurmadan önce harita/seviye seçimi, 2 bağlandıktan sonra.
     */
    private fun drawMenu(cv: Canvas, cx: Float, mp: Int = 0) {
        val bob = sin(time * 1.6f) * 3f
        val host = net?.isHost == true
        if (mp > 0) {
            titlePaint.textSize = 54f
            drawShadowText(cv, strTwoPlayers, cx, 132f + bob, titlePaint, colWarm)
            textPaint.textSize = 22f
            textPaint.color = if (mp == 2) colGreen else colDim
            drawFit(cv, if (mp == 2) "$strConnected  ·  $strBothCanPick" else strMpPick, cx, 186f, textPaint)
            drawCircleIcon(cv, backCircle, "‹")
            drawCircleIcon(cv, helpBtn, "?")
        } else {
            titlePaint.textSize = 60f
            drawShadowText(cv, strTitle1, cx, 122f + bob, titlePaint, colText)
            drawShadowText(cv, strTitle2, cx, 184f + bob, titlePaint, colWarm)
            drawSoundButton(cv, 255)
        }

        // --- Harita kartı ---
        labelPaint.textSize = 19f
        labelPaint.color = colDim
        cv.drawText(strMapLabel, cx, mapCard.top - 16f, labelPaint)

        paint.color = withAlpha(colPanel, 215)
        cv.drawRoundRect(mapCard, 30f, 30f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = withAlpha(map.accent, 200)
        cv.drawRoundRect(mapCard, 30f, 30f, paint)
        paint.style = Paint.Style.FILL

        cv.save()
        cv.clipRect(mapCard.left + 2f, mapCard.top + 2f, mapCard.right - 2f, mapCard.bottom - 2f)
        val slide = cardAnim * 70f
        val ca = ((1f - abs(cardAnim)) * 255).toInt()
        bitmapPaint.alpha = ca
        val pcx = mapCard.centerX() + slide
        val pcy = mapCard.top + 128f + sin(time * 1.3f) * 4f
        moonArts.firstOrNull()?.let { drawArtAt(cv, it, pcx + 92f, pcy + 58f, 24f) }
        mainArts.firstOrNull()?.let { drawArtAt(cv, it, pcx, pcy, 72f * map.planetMul.coerceAtMost(1.1f)) }
        bitmapPaint.alpha = 255

        titlePaint.textSize = 31f
        titlePaint.color = withAlpha(colText, ca)
        drawFitW(cv, mapNames[mapIndex], pcx, mapCard.bottom - 66f, titlePaint, mapCard.width() - 28f)
        textPaint.textSize = 18f
        textPaint.color = withAlpha(colDim, ca)
        drawFitW(cv, mapDescs[mapIndex], pcx, mapCard.bottom - 32f, textPaint, mapCard.width() - 28f)
        cv.restore()

        drawArrow(cv, arrowLeft, -1f)
        drawArrow(cv, arrowRight, 1f)

        // Sayfa noktaları
        val n = Maps.ALL.size
        for (i in 0 until n) {
            val x = cx + (i - (n - 1) / 2f) * 20f
            paint.color = if (i == mapIndex) map.accent else withAlpha(colDim, 90)
            cv.drawCircle(x, mapCard.bottom + 24f, if (i == mapIndex) 5.5f else 4f, paint)
        }

        // --- Seviye ---
        labelPaint.textSize = 19f
        labelPaint.color = colDim
        cv.drawText(strLevelLabel, cx, diffBtns[0].top - 16f, labelPaint)
        for (i in diffBtns.indices) {
            val d = Difficulty.entries[i]
            val sel = d == difficulty
            drawButton(cv, diffBtns[i], diffNames[i], d.color, if (sel) colDarkText else d.color, sel, 255)
        }

        if (mp == 1) {
            drawPulseButton(cv, startBtn, strHost)
            drawButton(cv, multiBtn, strJoin, colBlue, colText, false, 255, 19f)
            if (netMessage.isNotEmpty()) {
                textPaint.textSize = 20f
                textPaint.color = colAccent
                drawFit(cv, netMessage, cx, multiBtn.bottom + 44f, textPaint)
            }
            return
        }
        if (mp == 2) {
            if (host) {
                drawPulseButton(cv, startBtn, strMpStart)
            } else {
                val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
                textPaint.textSize = 21f
                textPaint.color = colText
                drawFit(cv, strWaitHost + dots, cx, startBtn.centerY() + 8f, textPaint)
            }
            drawButton(cv, multiBtn, strLeave, colPanelEdge, colText, false, 255, 19f)
            return
        }

        // --- Başla ---
        drawPulseButton(cv, startBtn, strStart)
        drawButton(cv, multiBtn, strTwoPlayers, colBlue, colText, false, 255, 19f)

        labelPaint.textSize = 19f
        labelPaint.color = colGold
        drawFit(cv, bestText, cx, multiBtn.bottom + 44f, labelPaint)
    }

    private fun drawPulseButton(cv: Canvas, r: RectF, text: String) {
        val pulse = 1f + 0.025f * sin(time * 4f)
        cv.save()
        cv.scale(pulse, pulse, r.centerX(), r.centerY())
        paint.color = hex(0x55000000)
        tmpRect.set(r)
        tmpRect.offset(0f, 5f)
        cv.drawRoundRect(tmpRect, r.height() / 2f, r.height() / 2f, paint)
        drawButton(cv, r, text, colWarm, colDarkText, true, 255, 28f)
        cv.restore()
    }

    private fun drawArtAt(cv: Canvas, art: PlanetArt, x: Float, y: Float, r: Float) {
        val k = r / art.baseR
        dst.set(x - art.padX * k, y - art.padY * k, x + art.padX * k, y + art.padY * k)
        cv.drawBitmap(art.bitmap, null, dst, bitmapPaint)
    }

    private fun drawArrow(cv: Canvas, r: RectF, dir: Float) {
        val cx = r.centerX()
        val cy = r.centerY()
        paint.color = withAlpha(colPanel, 200)
        cv.drawCircle(cx, cy, 22f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = colText
        iconPath.reset()
        iconPath.moveTo(cx - 4f * dir, cy - 9f)
        iconPath.lineTo(cx + 5f * dir, cy)
        iconPath.lineTo(cx - 4f * dir, cy + 9f)
        cv.drawPath(iconPath, paint)
        paint.style = Paint.Style.FILL
    }

    /** Hap şeklinde düğme. Seçili/dolu ise renkle doldurulur, değilse yalnızca kenarlık. */
    private fun drawButton(
        cv: Canvas, r: RectF, text: String, color: Int, textColor: Int, filled: Boolean, alpha: Int,
        size: Float = 20f
    ) {
        val rad = r.height() / 2f
        if (filled) {
            paint.color = withAlpha(color, alpha)
            cv.drawRoundRect(r, rad, rad, paint)
        } else {
            paint.color = withAlpha(colPanel, (alpha * 0.8f).toInt())
            cv.drawRoundRect(r, rad, rad, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = withAlpha(color, (alpha * 0.7f).toInt())
            cv.drawRoundRect(r, rad, rad, paint)
            paint.style = Paint.Style.FILL
        }
        labelPaint.textSize = size
        labelPaint.color = withAlpha(textColor, alpha)
        val y = r.centerY() + labelPaint.textSize * 0.36f
        drawFitW(cv, text, r.centerX(), y, labelPaint, r.width() - 14f)
        labelPaint.textSize = 20f
    }

    private fun drawSoundButton(cv: Canvas, alpha: Int) {
        val cx = soundBtn.centerX()
        val cy = soundBtn.centerY()
        paint.color = withAlpha(colPanel, (alpha * 0.8f).toInt())
        cv.drawCircle(cx, cy, 26f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, (alpha * 0.5f).toInt())
        cv.drawCircle(cx, cy, 26f, paint)
        paint.style = Paint.Style.FILL

        // Hoparlör
        val x0 = cx - 6f
        paint.color = withAlpha(colText, alpha)
        iconPath.reset()
        iconPath.moveTo(x0 - 7f, cy - 5f)
        iconPath.lineTo(x0 - 1f, cy - 5f)
        iconPath.lineTo(x0 + 7f, cy - 12f)
        iconPath.lineTo(x0 + 7f, cy + 12f)
        iconPath.lineTo(x0 - 1f, cy + 5f)
        iconPath.lineTo(x0 - 7f, cy + 5f)
        iconPath.close()
        cv.drawPath(iconPath, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.6f
        if (sound.enabled) {
            tmpRect.set(x0 + 3f, cy - 7f, x0 + 17f, cy + 7f)
            cv.drawArc(tmpRect, -50f, 100f, false, paint)
            tmpRect.set(x0 + 1f, cy - 13f, x0 + 25f, cy + 13f)
            cv.drawArc(tmpRect, -50f, 100f, false, paint)
        } else {
            paint.color = withAlpha(colAccent, alpha)
            cv.drawLine(x0 + 12f, cy - 6f, x0 + 22f, cy + 6f, paint)
            cv.drawLine(x0 + 22f, cy - 6f, x0 + 12f, cy + 6f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawReady(cv: Canvas, cx: Float) {
        val bob = sin(time * 1.6f) * 4f
        titlePaint.textSize = 50f
        drawShadowText(cv, mapNames[mapIndex], cx, 210f + bob, titlePaint, colText)

        // Seviye etiketi
        val d = difficulty
        labelPaint.textSize = 20f
        val tw = labelPaint.measureText(diffNames[d.ordinal]) + 44f
        tmpRect.set(cx - tw / 2f, 240f, cx + tw / 2f, 282f)
        drawButton(cv, tmpRect, diffNames[d.ordinal], d.color, colDarkText, true, 255)

        // Haritaya özel ipucu
        textPaint.textSize = 21f
        textPaint.color = map.accent
        drawFit(cv, mapTips[mapIndex], cx, 330f, textPaint)
        if (map.kind == MapKind.GAS) drawWindGauge(cv)

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
        if (map.kind == MapKind.GAS) drawWindGauge(cv)
    }

    /** Sol üstte rüzgârın yönünü ve gücünü gösteren ok. */
    private fun drawWindGauge(cv: Canvas, gx: Float = 52f, gy: Float = 74f) {
        val maxW = WIND_BASE * difficulty.hazard
        val k = (wind / maxW).coerceIn(-1f, 1f)
        paint.color = withAlpha(colPanel, 190)
        cv.drawCircle(gx, gy, 32f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(map.accent, 150)
        cv.drawCircle(gx, gy, 32f, paint)

        val len = 6f + 16f * abs(k)
        val dir = if (k >= 0f) 1f else -1f
        val strong = abs(k) > 0.6f
        paint.color = if (strong) colAccent else colText
        paint.strokeWidth = 4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        cv.drawLine(gx, gy - len * dir, gx, gy + len * dir, paint)
        iconPath.reset()
        iconPath.moveTo(gx - 8f, gy + (len - 8f) * dir)
        iconPath.lineTo(gx, gy + len * dir)
        iconPath.lineTo(gx + 8f, gy + (len - 8f) * dir)
        cv.drawPath(iconPath, paint)
        paint.style = Paint.Style.FILL

        labelPaint.textSize = 14f
        labelPaint.color = withAlpha(colDim, 230)
        cv.drawText(strWind, gx, gy + 54f, labelPaint)
    }

    private fun drawGameOver(cv: Canvas, cx: Float) {
        val t = (stateTimer / 0.35f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t).pow(3)
        val a = (ease * 255).toInt()
        val top = 330f + (1f - ease) * 70f
        val panelW = min(uiW - 48f, 360f)
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
            labelPaint.color = withAlpha(colDarkText, a)
            labelPaint.textSize = 20f
            cv.drawText(strNewRecord, cx, py + 7f, labelPaint)
        }

        // Düğmeler, panel yerine oturduktan sonra belirir
        val ba = (((stateTimer - 0.45f) / 0.25f).coerceIn(0f, 1f) * 255).toInt()
        if (ba > 0) {
            drawButton(cv, retryBtn, strRetry, colWarm, colDarkText, true, ba, 22f)
            drawButton(cv, menuBtn, strMenu, colPanelEdge, colText, false, ba, 22f)
            drawSoundButton(cv, ba)

            labelPaint.textSize = 18f
            labelPaint.color = withAlpha(colDim, ba)
            drawFit(cv, mapNames[mapIndex] + "  ·  " + diffNames[difficulty.ordinal], cx, retryBtn.bottom + 56f, labelPaint)
        }
    }

    private fun drawPaused(cv: Canvas, cx: Float) {
        paint.color = hex(0xAA05071A)
        cv.drawRect(0f, 0f, uiW, WORLD_H, paint)
        titlePaint.textSize = 54f
        drawShadowText(cv, strPaused, cx, 470f, titlePaint, colText)
        textPaint.textSize = 26f
        textPaint.color = colDim
        drawFit(cv, strTapToContinue, cx, 530f, textPaint)
    }

    // --- 2 oyuncu çizimi ---

    /** Bölünmüş ekran: üst yarı ben, alt yarı rakip. İkisi de aynı parkuru gösterir. */
    private fun drawMp(canvas: Canvas) {
        val halfH = height / 2f
        for (owner in 0..1) {
            curW = worldW
            canvas.save()
            canvas.clipRect(0f, owner * halfH, width.toFloat(), (owner + 1) * halfH)
            canvas.translate(0f, owner * halfH)
            canvas.scale(mpScale, mpScale)
            if (owner == 0 && shake > 0f) {
                val m = shake / 0.45f * 9f
                canvas.translate(rnd(-m, m), rnd(-m, m))
            }
            drawStars(canvas)
            drawShootingStar(canvas)
            drawMotes(canvas)
            drawColumns(canvas)
            drawFireballs(canvas)
            drawParticles(canvas, owner)
            if (owner == 0) {
                if (myAlive) drawRocket(canvas)
                drawShockwave(canvas)
            } else if (oppAlive) {
                drawRocket(canvas, rocketX, oppDispY, oppAngle, oppBoost, alt = true)
            }
            drawHalfHud(canvas, owner)
            canvas.restore()
        }

        // Ayırıcı çizgi
        paint.color = hex(0xFF0A0D26)
        canvas.drawRect(0f, halfH - 4f, width.toFloat(), halfH + 4f, paint)
        paint.color = withAlpha(map.accent, 210)
        canvas.drawRect(0f, halfH - 1.5f, width.toFloat(), halfH + 1.5f, paint)

        canvas.save()
        canvas.scale(scale, scale)
        curW = uiW
        if (state == State.MP_COUNTDOWN) drawCountdown(canvas)
        if (state == State.MP_OVER) drawMpOver(canvas)
        canvas.restore()

        if (flash > 0f) canvas.drawColor(withAlpha(Color.WHITE, (flash / 0.2f * 140).toInt()))
    }

    /** Yarım ekranın üstündeki isim, skor ve "yandı" katmanı (dünya birimi; yarı ölçekli). */
    private fun drawHalfHud(cv: Canvas, owner: Int) {
        val cx = worldW / 2f
        val me = owner == 0
        val alive = if (me) myAlive else oppAlive

        if (!alive) {
            paint.color = hex(0x8805071A)
            cv.drawRect(0f, 0f, worldW, WORLD_H, paint)
        }

        val name = if (me) strYou else strOpponent
        labelPaint.textSize = 32f
        val tw = labelPaint.measureText(name) + 56f
        tmpRect.set(30f, 30f, 30f + tw, 96f)
        drawButton(cv, tmpRect, name, if (me) colAccent else colBlue, Color.WHITE, true, 235, 32f)

        titlePaint.textSize = 130f * (if (me) 1f + 0.18f * scorePulse else 1f)
        drawShadowText(cv, (if (me) score else oppScore).toString(), cx, 175f, titlePaint, colText)

        if (map.kind == MapKind.GAS && alive) drawWindGauge(cv, worldW - 70f, 70f)

        if (state == State.MP_COUNTDOWN && me) {
            textPaint.textSize = 42f
            textPaint.color = map.accent
            drawFit(cv, mapTips[mapIndex], cx, 300f, textPaint)
        }

        if (!alive) {
            titlePaint.textSize = 120f
            drawShadowText(cv, if (me) strYouCrashed else strOppCrashed, cx, 540f, titlePaint, if (me) colAccent else colBlue)
            if (me && oppAlive && state == State.MP_PLAYING) {
                textPaint.textSize = 48f
                textPaint.color = colText
                drawFit(cv, strWatching, cx, 640f, textPaint)
            }
        }
    }

    private fun drawCountdown(cv: Canvas) {
        val cx = uiW / 2f
        val cy = 500f
        val left = (mpStartNs - System.nanoTime()) / 1_000_000_000f
        val n = ceil(left).toInt().coerceIn(1, 3)
        val k = (left - (n - 1)).coerceIn(0f, 1f) // 1 → 0 her saniye
        paint.color = withAlpha(colPanel, 235)
        cv.drawCircle(cx, cy, 66f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = map.accent
        tmpRect.set(cx - 66f, cy - 66f, cx + 66f, cy + 66f)
        cv.drawArc(tmpRect, -90f, 360f * k, false, paint)
        paint.style = Paint.Style.FILL
        titlePaint.textSize = 84f * (0.9f + 0.25f * k)
        drawShadowText(cv, n.toString(), cx, cy + titlePaint.textSize * 0.35f, titlePaint, colWarm)
    }

    private fun drawMpOver(cv: Canvas) {
        val cx = uiW / 2f
        val t = (stateTimer / 0.35f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t).pow(3)
        val a = (ease * 255).toInt()

        paint.color = withAlpha(hex(0xFF05071A), (a * 0.6f).toInt())
        cv.drawRect(0f, 0f, uiW, WORLD_H, paint)

        val title: String
        val titleColor: Int
        when {
            mpDisconnected -> { title = strDisconnected; titleColor = colDim }
            mpResult > 0 -> { title = strWin; titleColor = colGold }
            mpResult < 0 -> { title = strLose; titleColor = colAccent }
            else -> { title = strDraw; titleColor = colText }
        }
        val top = 340f + (1f - ease) * 60f
        titlePaint.textSize = 62f
        titlePaint.color = withAlpha(Color.BLACK, (a * 0.4f).toInt())
        drawFit(cv, title, cx, top - 37f, titlePaint)
        titlePaint.color = withAlpha(titleColor, a)
        drawFit(cv, title, cx, top - 42f, titlePaint)

        val panelW = min(uiW - 48f, 360f)
        val panelH = 220f
        tmpRect.set(cx - panelW / 2f, top, cx + panelW / 2f, top + panelH)
        paint.color = withAlpha(colPanel, (a * 0.92f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, (a * 0.45f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(colPanelEdge, (a * 0.25f).toInt())
        cv.drawRect(cx - 1f, top + 30f, cx + 1f, top + panelH - 30f, paint)

        for (side in 0..1) {
            val x = cx + (if (side == 0) -1f else 1f) * panelW / 4f
            labelPaint.textSize = 22f
            labelPaint.color = withAlpha(if (side == 0) colAccent else colBlue, a)
            cv.drawText(if (side == 0) strYou else strOpponent, x, top + 58f, labelPaint)
            titlePaint.textSize = 80f
            val winner = !mpDisconnected && ((side == 0 && mpResult > 0) || (side == 1 && mpResult < 0))
            titlePaint.color = withAlpha(if (winner) colGold else colText, a)
            cv.drawText((if (side == 0) score else oppScore).toString(), x, top + 158f, titlePaint)
        }

        labelPaint.textSize = 18f
        labelPaint.color = withAlpha(colDim, a)
        drawFit(cv, mapNames[mapIndex] + "  ·  " + diffNames[difficulty.ordinal], cx, top + panelH + 44f, labelPaint)

        val ba = (((stateTimer - 0.45f) / 0.25f).coerceIn(0f, 1f) * 255).toInt()
        if (ba <= 0) return
        if (!mpDisconnected && net?.isHost == true) {
            drawButton(cv, retryBtn, strRetry, colWarm, colDarkText, true, ba, 22f)
            drawButton(cv, menuBtn, strMenu, colPanelEdge, colText, false, ba, 22f)
        } else {
            if (!mpDisconnected) {
                val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
                textPaint.textSize = 21f
                textPaint.color = withAlpha(colText, ba)
                drawFit(cv, strRetryWait + dots, cx, retryBtn.centerY() + 8f, textPaint)
            }
            drawButton(cv, backBtn, strMenu, colPanelEdge, colText, false, ba, 20f)
        }
    }

    private fun drawLobby(cv: Canvas, cx: Float) {
        when (lobby) {
            Lobby.CHOOSE -> drawMenu(cv, cx, mp = 1)
            Lobby.CONNECTED -> drawMenu(cv, cx, mp = 2)
            else -> drawWaiting(cv, cx)
        }
        if (lobbyHelp) drawHelp(cv, cx)
    }

    /** Oda kurulmuş ya da aranıyorken gösterilen bekleme ekranı. */
    private fun drawWaiting(cv: Canvas, cx: Float) {
        drawCircleIcon(cv, backCircle, "‹")
        drawCircleIcon(cv, helpBtn, "?")
        val bob = sin(time * 1.6f) * 3f
        titlePaint.textSize = 56f
        drawShadowText(cv, strTwoPlayers, cx, 170f + bob, titlePaint, colWarm)
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)

        when (lobby) {
            Lobby.HOSTING -> {
                labelPaint.textSize = 20f
                labelPaint.color = map.accent
                drawFit(cv, mapNames[mapIndex] + "  ·  " + diffNames[difficulty.ordinal], cx, 236f, labelPaint)
                textPaint.textSize = 26f
                textPaint.color = colText
                drawFit(cv, strRoomOpen, cx, 290f, textPaint)
                textPaint.textSize = 24f
                textPaint.color = colGreen
                drawFit(cv, strWaiting + dots, cx, 350f, textPaint)
                val ip = net?.localAddress.orEmpty()
                if (ip.isNotEmpty()) {
                    labelPaint.textSize = 19f
                    labelPaint.color = colDim
                    drawFit(cv, "IP  $ip", cx, 410f, labelPaint)
                }
                textPaint.textSize = 20f
                textPaint.color = colDim
                drawFit(cv, strJoinHint, cx, 480f, textPaint)
                drawFit(cv, strMpInfo1, cx, 516f, textPaint)
                drawSpinner(cv, cx, 680f)
            }
            Lobby.JOINING -> {
                textPaint.textSize = 24f
                textPaint.color = colGreen
                drawFit(cv, strSearching + dots, cx, 350f, textPaint)
                textPaint.textSize = 20f
                textPaint.color = colDim
                drawFit(cv, strMpInfo1, cx, 440f, textPaint)
                drawFit(cv, strMpInfo2, cx, 472f, textPaint)
                drawSpinner(cv, cx, 680f)
            }
            else -> Unit
        }
        drawButton(cv, backBtn, strBack, colPanelEdge, colText, false, 255, 20f)
    }

    /** Lobideki küçük yuvarlak düğme ("‹" geri, "?" yardım). */
    private fun drawCircleIcon(cv: Canvas, r: RectF, icon: String) {
        val x = r.centerX()
        val y = r.centerY()
        paint.color = withAlpha(colPanel, 210)
        cv.drawCircle(x, y, 26f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, 130)
        cv.drawCircle(x, y, 26f, paint)
        paint.style = Paint.Style.FILL
        titlePaint.textSize = 32f
        titlePaint.color = colText
        cv.drawText(icon, x, y + 11f, titlePaint)
    }

    /** "Nasıl kurulur?" penceresi: numaralı adımlar. Herhangi bir yere dokununca kapanır. */
    private fun drawHelp(cv: Canvas, cx: Float) {
        paint.color = hex(0xDD05071A)
        cv.drawRect(0f, 0f, uiW, WORLD_H, paint)
        val pw = min(uiW - 28f, 470f)
        val left = cx - pw / 2f
        val panelTop = 70f
        val panelBottom = 930f
        tmpRect.set(left, panelTop, left + pw, panelBottom)
        paint.color = colPanel
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(map.accent, 200)
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        titlePaint.textSize = 34f
        titlePaint.color = colWarm
        drawFit(cv, strHelpTitle, cx, 128f, titlePaint)

        // Metnin kullanabileceği alan: başlığın altından "kapatmak için dokun" yazısının üstüne kadar
        val areaTop = 160f
        val areaBottom = 868f
        val textX = left + 66f
        val textW = pw - 88f
        val tipW = pw - 48f

        // Her şey sığana kadar yazı boyunu küçült
        var size = 20f
        var stepLines: List<List<String>>
        var tipLines: List<String>
        while (true) {
            helpPaint.textSize = size
            stepLines = helpSteps.map { wrapLines(it, textW, helpPaint) }
            helpPaint.textSize = size * 0.9f
            tipLines = wrapLines(strHelpTip, tipW, helpPaint)
            val lineH = size * 1.32f
            val gap = size * 0.85f
            val total = stepLines.sumOf { it.size } * lineH + gap * (stepLines.size - 1) +
                size * 1.4f + tipLines.size * lineH * 0.9f
            if (total <= areaBottom - areaTop || size <= 12f) break
            size -= 1f
        }

        val lineH = size * 1.32f
        val gap = size * 0.85f
        var y = areaTop + size // ilk satırın taban çizgisi
        helpPaint.textSize = size
        helpPaint.color = colText
        stepLines.forEachIndexed { i, lines ->
            // Numara rozeti, adımın ilk satırıyla aynı hizada
            val badgeY = y - size * 0.35f
            paint.color = map.accent
            cv.drawCircle(left + 36f, badgeY, size * 0.8f, paint)
            labelPaint.textSize = size * 0.85f
            labelPaint.color = colDarkText
            cv.drawText((i + 1).toString(), left + 36f, badgeY + size * 0.3f, labelPaint)
            for (line in lines) {
                cv.drawText(line, textX, y, helpPaint)
                y += lineH
            }
            y += gap
        }

        // İpucu: adımların altında, ince bir çizgiyle ayrılmış
        y += size * 0.4f - gap
        paint.color = withAlpha(colPanelEdge, 60)
        cv.drawRect(left + 24f, y - size * 0.9f, left + pw - 24f, y - size * 0.9f + 1.5f, paint)
        y += size * 0.5f
        helpPaint.textSize = size * 0.9f
        helpPaint.color = colGold
        for (line in tipLines) {
            cv.drawText(line, left + 24f, y, helpPaint)
            y += lineH * 0.9f
        }

        labelPaint.textSize = 18f
        labelPaint.color = colDim
        cv.drawText(strHelpClose, cx, 905f, labelPaint)
    }

    /** Metni kelime kelime, verilen genişliğe sığan satırlara böler. */
    private fun wrapLines(text: String, maxW: Float, p: Paint): List<String> {
        val out = ArrayList<String>()
        val line = StringBuilder()
        for (word in text.split(' ')) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(trial) > maxW && line.isNotEmpty()) {
                out.add(line.toString())
                line.setLength(0)
                line.append(word)
            } else {
                line.setLength(0)
                line.append(trial)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    /** Dönen nokta halkası: bekleniyor göstergesi. */
    private fun drawSpinner(cv: Canvas, x: Float, y: Float) {
        for (i in 0 until 10) {
            val a = i / 10f * TWO_PI + time * 3f
            val k = i / 9f
            paint.color = withAlpha(map.accent, (40 + 200 * k).toInt())
            cv.drawCircle(x + cos(a) * 34f, y + sin(a) * 34f, 3f + 4f * k, paint)
        }
    }

    /** Metni verilen genişliğe sığmazsa küçülterek çizer. */
    private fun drawFitW(cv: Canvas, text: String, x: Float, y: Float, p: Paint, maxW: Float) {
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

    private fun drawShadowText(cv: Canvas, text: String, x: Float, y: Float, p: Paint, color: Int) {
        p.color = hex(0x66000000)
        drawFit(cv, text, x, y + 5f, p)
        p.color = color
        drawFit(cv, text, x, y, p)
    }

    /** Metni ekrana sığmazsa küçülterek çizer. */
    private fun drawFit(cv: Canvas, text: String, x: Float, y: Float, p: Paint) {
        val maxW = curW - 40f
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
            map.bgColors,
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // Bulutsular
        val nebula = map.nebula
        for (i in nebula.indices) {
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
        drawScenery(cv, w.toFloat(), h.toFloat())
        bgBitmap = bmp
    }

    /** Haritaya özgü, hareketsiz büyük dekor (piksel koordinatlarında, arka plan bitmap'ine). */
    private fun drawScenery(cv: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (map.kind) {
            MapKind.EARTH -> {
                // Sol üstte Güneş ışığı
                p.shader = RadialGradient(
                    w * 0.05f, h * 0.04f, w * 0.7f,
                    intArrayOf(hex(0x88FFF4D6), hex(0x22FFD9A0), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, 0f, w, h, p)
                // Sağ altta dev Dünya ufku
                val r = h * 0.42f
                val cx = w * 0.95f
                val cy = h + r * 0.45f
                p.shader = RadialGradient(
                    cx, cy, r * 1.18f,
                    intArrayOf(hex(0x664FB4FF), hex(0x334FB4FF), 0), floatArrayOf(0.8f, 0.88f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r * 1.18f, p)
                p.shader = RadialGradient(
                    cx - r * 0.5f, cy - r * 0.6f, r * 1.6f,
                    intArrayOf(hex(0xFF3C8EE8), hex(0xFF1A4FA0), hex(0xFF061A40)), floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r, p)
                p.shader = null
                val save = cv.save()
                val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
                cv.clipPath(clip)
                repeat(9) { // kıtalar
                    val bx = cx + rnd(-0.9f, 0.4f) * r
                    val by = cy + rnd(-1f, -0.5f) * r
                    repeat(6) {
                        p.color = withAlpha(if (Random.nextInt(3) == 0) hex(0xFF8A7A4A) else hex(0xFF3E8F55), 200)
                        cv.drawCircle(bx + rnd(-0.12f, 0.12f) * r, by + rnd(-0.06f, 0.06f) * r, rnd(0.03f, 0.08f) * r, p)
                    }
                }
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = r * 0.025f
                p.color = withAlpha(Color.WHITE, 120)
                repeat(10) { // bulutlar
                    val y = cy + rnd(-1f, -0.45f) * r
                    val x = cx + rnd(-1f, 0.3f) * r
                    cv.drawLine(x, y, x + rnd(0.1f, 0.3f) * r, y + rnd(-0.01f, 0.01f) * r, p)
                }
                p.style = Paint.Style.FILL
                cv.restoreToCount(save)
                p.color = hex(0x55000818) // oyun alanı okunaklı kalsın
                cv.drawCircle(cx, cy, r, p)
            }
            MapKind.ICE -> {
                // Kutup ışıkları
                p.maskFilter = BlurMaskFilter(h * 0.025f, BlurMaskFilter.Blur.NORMAL)
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                val colors = intArrayOf(hex(0xFF39FFB0), hex(0xFF39D9FF), hex(0xFF9B7BFF))
                val path = Path()
                for (band in 0 until 3) {
                    val baseY = h * (0.12f + band * 0.09f)
                    val ph = rnd(0f, 6f)
                    path.reset()
                    path.moveTo(-20f, baseY)
                    var x = -20f
                    while (x < w + 40f) {
                        x += w / 12f
                        path.lineTo(x, baseY + sin(x / w * 7f + ph) * h * 0.04f)
                    }
                    p.strokeWidth = h * (0.05f - band * 0.01f)
                    p.color = withAlpha(colors[band], 70)
                    cv.drawPath(path, p)
                    // Aşağı sarkan ışık perdeleri
                    p.strokeWidth = w * 0.006f
                    var rx = rnd(0f, w / 18f)
                    while (rx < w) {
                        val ry = baseY + sin(rx / w * 7f + ph) * h * 0.04f
                        p.color = withAlpha(colors[band], Random.nextInt(18, 45))
                        cv.drawLine(rx, ry, rx, ry + h * rnd(0.05f, 0.14f), p)
                        rx += w / rnd(14f, 26f)
                    }
                }
                p.maskFilter = null
                p.style = Paint.Style.FILL
                // Uzakta soluk buz gezegeni
                p.shader = RadialGradient(
                    w * 0.18f, h * 0.6f, w * 0.1f,
                    intArrayOf(hex(0x66E6FBFF), hex(0x445FB0D0), hex(0x00203040)), null, Shader.TileMode.CLAMP
                )
                cv.drawCircle(w * 0.2f, h * 0.62f, w * 0.1f, p)
                p.shader = null
                // Alt kısımda buz sisi
                p.shader = LinearGradient(
                    0f, h * 0.7f, 0f, h, 0, hex(0x5596E6FF), Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, h * 0.7f, w, h, p)
                p.shader = null
            }
            MapKind.LAVA -> {
                // Dev kızıl güneş
                val r = w * 0.34f
                val cx = w * 0.8f
                val cy = h * 0.15f
                p.shader = RadialGradient(
                    cx, cy, r * 2.1f,
                    intArrayOf(hex(0x99FF5A1F), hex(0x33FF3B1F), 0), floatArrayOf(0.3f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r * 2.1f, p)
                p.shader = RadialGradient(
                    cx - r * 0.2f, cy - r * 0.2f, r * 1.1f,
                    intArrayOf(hex(0xFFFFE08A), hex(0xFFFF7A1F), hex(0xFFB3200F)), floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r, p)
                p.shader = null
                repeat(6) { // güneş lekeleri
                    p.color = withAlpha(hex(0xFF7A1505), 110)
                    val a = rnd(0f, TWO_PI)
                    val d = rnd(0f, 0.7f) * r
                    cv.drawCircle(cx + cos(a) * d, cy + sin(a) * d, rnd(0.04f, 0.1f) * r, p)
                }
                p.color = hex(0x44000000)
                cv.drawCircle(cx, cy, r, p)
                // Alttan yükselen kor ışığı
                p.shader = LinearGradient(
                    0f, h * 0.6f, 0f, h,
                    intArrayOf(0, hex(0x55FF5A1F), hex(0xAAFF8A2B)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, h * 0.6f, w, h, p)
                p.shader = null
            }
            MapKind.GAS -> {
                // Arkada dev, halkalı gaz devi
                val r = w * 0.48f
                val cx = w * 0.22f
                val cy = h * 0.36f
                val save = cv.save()
                cv.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
                val bands = intArrayOf(hex(0xFF8E4BC8), hex(0xFFD98AD0), hex(0xFF6A2E9E), hex(0xFFF0B0E0), hex(0xFF7B3BB0))
                var y = cy - r
                var i = 0
                while (y < cy + r) {
                    val bh = rnd(0.06f, 0.16f) * r
                    p.color = bands[i++ % bands.size]
                    cv.drawRect(cx - r, y, cx + r, y + bh, p)
                    y += bh
                }
                p.shader = RadialGradient(
                    cx - r * 0.4f, cy - r * 0.4f, r * 1.9f,
                    intArrayOf(0, hex(0x66000000), hex(0xEE05010C)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r, p)
                p.shader = null
                cv.restoreToCount(save)
                p.style = Paint.Style.STROKE
                cv.save()
                cv.rotate(-14f, cx, cy)
                for (k in 0 until 5) {
                    val rr = r * (1.3f + k * 0.12f)
                    p.strokeWidth = r * 0.05f
                    p.color = withAlpha(hex(0xFFF0C8FF), 70 - k * 10)
                    tmpArtRect.set(cx - rr, cy - rr * 0.22f, cx + rr, cy + rr * 0.22f)
                    cv.drawOval(tmpArtRect, p)
                }
                cv.restore()
                p.style = Paint.Style.FILL
                p.color = hex(0x77100420) // derinlik için karart
                cv.drawRect(0f, 0f, w, h, p)
            }
            MapKind.ASTEROID -> {
                // Çapraz toz ve taş kuşağı
                repeat(1400) {
                    val t = Random.nextFloat()
                    val spread = (Random.nextFloat() + Random.nextFloat() + Random.nextFloat() - 1.5f) * h * 0.12f
                    val x = t * w
                    val y = h * 0.78f - t * h * 0.42f + spread
                    p.color = withAlpha(
                        if (Random.nextBoolean()) hex(0xFFB8A48A) else hex(0xFF8A8A96), Random.nextInt(30, 120)
                    )
                    cv.drawCircle(x, y, rnd(0.5f, 2.6f) * scale, p)
                }
                p.shader = LinearGradient(
                    0f, h * 0.78f, w, h * 0.36f,
                    intArrayOf(hex(0x22C8A060), hex(0x11C8A060)), null, Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, 0f, w, h, p)
                p.shader = null
                // Uzakta kahverengi cüce gezegen
                p.shader = RadialGradient(
                    w * 0.78f, h * 0.13f, w * 0.12f,
                    intArrayOf(hex(0xFFC8A070), hex(0xFF6A4A2A), hex(0xFF2A1A10)), floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                cv.drawCircle(w * 0.8f, h * 0.15f, w * 0.1f, p)
                p.shader = null
            }
        }
    }

    /** Altıgen, sivri buz kristali silueti. */
    private fun crystalShape(r: Float): Path {
        val path = Path()
        val rot = rnd(0f, 60f) * DEG
        for (i in 0 until 6) {
            val a = rot + i * 60f * DEG
            val rr = r * (if (i % 2 == 0) rnd(1.06f, 1.16f) else rnd(0.98f, 1.06f))
            val x = cos(a) * rr
            val y = sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Düzensiz, yumrulu asteroit silueti. */
    private fun asteroidShape(r: Float): Path {
        val n = 11
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            val a = i * TWO_PI / n
            val rr = r * rnd(0.9f, 1.12f)
            xs[i] = cos(a) * rr
            ys[i] = sin(a) * rr
        }
        val path = Path()
        path.moveTo((xs[0] + xs[n - 1]) / 2f, (ys[0] + ys[n - 1]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            path.quadTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
        }
        path.close()
        return path
    }

    /** Kristal yüzeyleri: merkezden kenarlara açık/koyu üçgen yüzler, iç parıltı. */
    private fun paintCrystal(cv: Canvas, p: Paint, r: Float, pal: Palette) {
        val path = Path()
        val n = 6
        val rot = rnd(0f, 60f) * DEG
        val ox = -0.12f * r
        val oy = -0.15f * r
        for (i in 0 until n) {
            val a1 = rot + i * TWO_PI / n
            val a2 = rot + (i + 1) * TWO_PI / n
            path.reset()
            path.moveTo(ox, oy)
            path.lineTo(cos(a1) * r * 1.3f, sin(a1) * r * 1.3f)
            path.lineTo(cos(a2) * r * 1.3f, sin(a2) * r * 1.3f)
            path.close()
            p.color = withAlpha(
                when (i % 3) { 0 -> pal.light; 1 -> pal.base; else -> pal.dark }, Random.nextInt(70, 140)
            )
            cv.drawPath(path, p)
        }
        // İç kristal ve çizgiler
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.03f
        p.color = withAlpha(Color.WHITE, 150)
        for (i in 0 until n) {
            val a = rot + i * TWO_PI / n
            cv.drawLine(ox, oy, cos(a) * r * 1.2f, sin(a) * r * 1.2f, p)
        }
        p.style = Paint.Style.FILL
        p.color = withAlpha(Color.WHITE, 200)
        cv.drawCircle(-0.35f * r, -0.4f * r, r * 0.1f, p)
        p.color = withAlpha(Color.WHITE, 120)
        cv.drawCircle(0.25f * r, 0.3f * r, r * 0.05f, p)
    }

    /** Seçili haritanın gezegen ve uydu görsellerini üretir. */
    private fun buildPlanetArts() {
        artScale = scale
        artMap = mapIndex
        mainArts.clear()
        moonArts.clear()
        val mainR = MAIN_R * map.planetMul
        repeat(8) { i ->
            val t = map.mainTypes[i % map.mainTypes.size]
            // İlk görsel menüde önizleme olarak gösterilir; halkalı haritada halkalı olsun
            val ringed = if (i == 0) map.ringChance >= 0.3f else Random.nextFloat() < map.ringChance
            mainArts.add(renderArt(mainR, t, ringed))
        }
        repeat(10) { i ->
            moonArts.add(renderArt(MOON_R, map.moonTypes[i % map.moonTypes.size], false))
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

        val shape: Path? = when (type) {
            PlanetType.CRYSTAL -> crystalShape(r)
            PlanetType.ASTEROID -> asteroidShape(r)
            else -> null
        }

        // Atmosfer parıltısı
        val glowAlpha = when (type) {
            PlanetType.MOON -> 35
            PlanetType.ASTEROID -> 0
            PlanetType.CRYSTAL -> 80
            else -> 105
        }
        p.shader = RadialGradient(
            0f, 0f, r * 1.2f,
            intArrayOf(withAlpha(pal.atmo, glowAlpha), withAlpha(pal.atmo, glowAlpha), withAlpha(pal.atmo, 0)),
            floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(0f, 0f, r * 1.2f, p)
        p.shader = null

        if (ringed) drawRing(cv, p, r, pal.light, front = false)

        // Gövde + yüzey: ayrı katmanda, SRC_ATOP ile yalnızca gezegenin içine çizilir
        val lr = r * 1.2f
        val layer = cv.saveLayer(-lr, -lr, lr, lr, null)
        p.color = Color.WHITE
        p.shader = RadialGradient(
            -0.35f * r, -0.4f * r, r * 1.5f,
            intArrayOf(pal.light, pal.base, pal.dark),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        if (shape != null) cv.drawPath(shape, p) else cv.drawCircle(0f, 0f, r, p)
        p.shader = null
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        when (type) {
            PlanetType.GAS -> paintGas(cv, p, path, r, pal)
            PlanetType.ROCKY -> paintRocky(cv, p, r, pal, 7)
            PlanetType.MOON -> paintRocky(cv, p, r, pal, 12)
            PlanetType.ICE -> paintIce(cv, p, path, r, pal)
            PlanetType.LAVA -> paintLava(cv, p, path, r, pal)
            PlanetType.OCEAN -> paintOcean(cv, p, path, r, pal)
            PlanetType.CRYSTAL -> paintCrystal(cv, p, r, pal)
            PlanetType.ASTEROID -> paintRocky(cv, p, r, pal, 10)
        }
        // Gece tarafı gölgesi (ışık sol üstten)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.shader = RadialGradient(
            -0.4f * r, -0.45f * r, r * 1.8f,
            intArrayOf(0, 0, hex(if (type == PlanetType.CRYSTAL) 0x90000000 else 0xC8000000)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawCircle(0f, 0f, lr, p)
        p.shader = null
        p.xfermode = null
        cv.restoreToCount(layer)

        // Kenar ışığı
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.035f
        if (shape != null) {
            p.color = withAlpha(pal.light, if (type == PlanetType.CRYSTAL) 200 else 70)
            p.strokeJoin = Paint.Join.ROUND
            cv.drawPath(shape, p)
        } else {
            p.color = withAlpha(pal.light, 130)
            tmpArtRect.set(-r * 0.975f, -r * 0.975f, r * 0.975f, r * 0.975f)
            cv.drawArc(tmpArtRect, 195f, 90f, false, p)
        }
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
