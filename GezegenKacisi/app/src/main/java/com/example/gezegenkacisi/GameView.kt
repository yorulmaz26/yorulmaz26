package com.example.gezegenkacisi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.os.SystemClock
import android.provider.Settings
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
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

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

    private enum class State { SPLASH, HUB, MENU, LEVELS, MARKET, READY, PLAYING, DYING, OVER, LEVEL_DONE, MP_LOBBY, MP_COUNTDOWN, MP_PLAYING, MP_OVER }

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
    private class Fireball(var x: Float, var y: Float, var vy: Float, var warn: Float, val fromTop: Boolean = false)

    /** Yıldırım: önce [warn] süresince kesik çizgiyle uyarır, sonra [life] süresince yukarıdan [len] kadar iner. */
    private class Bolt(var x: Float, val len: Float, var warn: Float, var life: Float)

    /** Bir engel kulesi. Asteroit Kuşağı'nda tüm kule [swayAmp] kadar yukarı aşağı salınır. */
    private class Column(var x: Float, val planets: List<Planet>, val swayAmp: Float, val swayPhase: Float) {
        var scored = false
        var offset = 0f
        var coinY = Float.NaN   // boşluktaki para (yoksa NaN)
        var coinTaken = false
        var gapTop = 0f         // geçidin üst kenarı (salınım hariç), yıldırımın güvenli boyu için
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
        private const val PULL_K = 2.4f          // kara delik çekiminin gücü
        private const val BOLT_W = 13f           // yıldırımın yarı genişliği
        private const val COIN_R = 15f           // paranın yarıçapı
        // Açılış animasyonunun zaman çizelgesi (saniye)
        private const val SPL_ROCKET_START = 0.45f
        private const val SPL_ROCKET_END = 2.05f
        private const val SPL_SHINE_START = 2.5f
        private const val SPL_TITLE_START = 2.75f
        private const val SPL_FADE_START = 4.1f
        private const val SPL_END = 4.6f
        private const val LEVEL_LIST_TOP = 140f  // bölüm listesinin başladığı yer
        private const val LEVEL_ROW_H = 150f     // listede bir harita satırının yüksekliği

        // --- 2 oyuncu ---
        private const val MP_STEP = 1f / 120f     // iki telefonda aynı sabit adımla simülasyon
        private const val MP_ROCKET_X = 180f      // ekran oranından bağımsız sabit roket konumu
        private const val MP_FIRST_X = 900f       // ilk kulenin konumu
        private const val MP_SPAWN_X = 2100f      // yeni kulelerin üretildiği uzaklık
        private const val MP_COUNTDOWN_NS = 3_000_000_000L

        // Oyuncu renkleri: 1 kırmızı, 2 mavi, 3 yeşil, 4 sarı (roket gövdesi ve kanatçık)
        private val PLAYER_COLORS = intArrayOf(0xFFFF5B4A.toInt(), 0xFF4F8BFF.toInt(), 0xFF4CD98A.toInt(), 0xFFFFC14D.toInt())
        private val PLAYER_FINS = intArrayOf(0xFFD8402F.toInt(), 0xFF2F5FD0.toInt(), 0xFF2FA866.toInt(), 0xFFD99A20.toInt())

        private const val PREFS = "gezegen_kacisi"
        private const val KEY_BEST_LEGACY = "best"
        private const val KEY_MAP = "map"
        private const val KEY_DIFF = "difficulty"
        private const val KEY_SOUND = "sound"
        private const val KEY_MP_HELP_SEEN = "mp_help_seen"
        private const val KEY_MP_FULL = "mp_full"
        private const val KEY_COINS = "coins"
        private const val KEY_LEVEL_MAX = "level_max"   // geçilen en yüksek bölüm
        private const val KEY_SKINS = "skins"           // satın alınan araçlar
        private const val KEY_SKIN = "skin"             // seçili araç

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
    private var state = State.SPLASH
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

    // --- Bu oyunun kuralları (sonsuz modda seviyeden, bölüm modunda bölümden) ---
    private var cfg: DiffParams = difficulty.params
    private var mechs: Set<Mech> = emptySet()
    private var gravMul = 1f
    private var flapMul = 1f
    private var coinChance = 0f
    private var coinValue = 1

    // --- Bölümler, para ve market (kalıcı) ---
    private var levelMode = false
    private var level: LevelInfo? = null
    private var levelMax = prefs.getInt(KEY_LEVEL_MAX, 0)
    private var coins = prefs.getInt(KEY_COINS, 0)
    private var runCoins = 0
    private var levelBonus = 0
    private var coinRng = Random(1)
    private val ownedSkins = HashSet<Skin>().apply {
        add(Skin.CLASSIC)
        prefs.getString(KEY_SKINS, "").orEmpty().split(',').forEach { n ->
            Skin.entries.firstOrNull { it.name == n }?.let { add(it) }
        }
    }
    private var skin: Skin = Skin.entries.firstOrNull { it.name == prefs.getString(KEY_SKIN, "") }
        ?.takeIf { it in ownedSkins } ?: Skin.CLASSIC
    // --- Canlar (bölüm modu): 4 can, her biri 15 dakikada dolar, saat hilesine karşı korumalı ---
    @Volatile private var netBaseMs = -1L       // internetten alınan gerçek saat
    @Volatile private var netBaseElapsed = 0L   // o andaki değiştirilemeyen sayaç
    private var netRetry = 0f
    private val lives = Lives(
        object : Lives.Store {
            override fun getLong(key: String, def: Long) = prefs.getLong(key, def)
            override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
        },
        object : Lives.Clock {
            override fun elapsed() = SystemClock.elapsedRealtime()
            override fun bootCount() =
                runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
            override fun trustedNow(): Long {
                val b = netBaseMs
                return if (b < 0L) -1L else b + (SystemClock.elapsedRealtime() - netBaseElapsed)
            }
        }
    )
    private var livesTick = 0f
    private var lifeSpent = false               // bu bölüm denemesi için can harcandı mı

    // --- Reklamlar (MainActivity verir; testlerde yoktur) ---
    var ads: AdsBridge? = null
    private enum class Reward { NONE, LIFE, DOUBLE_LEVEL, DOUBLE_RUN }
    private var bannerOn = false
    private var bannerReserve = 0f              // banner'ın kapladığı alt alan (dünya birimi)
    private var runsSinceAd = 0                 // son geçiş reklamından beri biten oyun sayısı
    private var lastAdAt = 0L
    private var lastRewardAt = -1L
    private val appStartAt = SystemClock.elapsedRealtime()
    private var adShowing = false               // reklam açıkken dokunuşlar yok sayılır
    private var rewardUsed = false              // bu ekrandaki ödüllü video kullanıldı mı
    private val rewardBtn = RectF()
    private val privacyRect = RectF()

    private val bolts = ArrayList<Bolt>()
    private var boltTimer = 0f
    private var meteorTimer = 0f
    private var levelScroll = 0f
    private var scrollVel = 0f
    private var dragging = false
    private var lastMoveY = 0f
    private var marketMsg = ""
    private var marketMsgT = 0f

    private var speed = difficulty.params.baseSpeed
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
    private var myId = 0                         // bu telefonun oyuncu numarası (0: oda kurucusu)
    private val roster = ArrayList<Int>()        // lobideki oyuncular
    private val players = ArrayList<Int>()       // maçtaki oyuncular
    private val mpCells = ArrayList<Pair<Int, RectF>>() // bölünmüş ekran hücreleri (piksel)
    // Diğer oyuncuların durumu, oyuncu numarasına göre (en fazla 4)
    private val pY = FloatArray(4)
    private val pDispY = FloatArray(4)
    private val pAngle = FloatArray(4)
    private val pBoost = FloatArray(4)
    private val pScore = IntArray(4)
    private val pAlive = BooleanArray(4)
    private val pGone = BooleanArray(4)          // bağlantısı koptu
    private var watchId = -1                     // tam ekranda yanınca izlenen oyuncu
    private var sendTimer = 0f
    private var endTimer = 0f
    private var mpPlace = 1                      // maç sonundaki sıran
    private var mpTie = false                    // birinciliği paylaşıyor musun
    private var mpDisconnected = false
    private var lobbyHelp = false      // "Nasıl kurulur?" penceresi açık mı
    private var mpFull = prefs.getBoolean(KEY_MP_FULL, false) // true: tam ekran, false: bölünmüş ekran
    private var myDeadTime = 0f        // yandıktan sonra geçen süre (tam ekranda izlemeye geçiş için)
    private val emitAccs = FloatArray(4)
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
    private val strViewSplit = context.getString(R.string.mp_view_split)
    private val strViewFull = context.getString(R.string.mp_view_full)
    private val strViewLabel = context.getString(R.string.mp_view_label)
    private val strPlaying = context.getString(R.string.mp_playing)
    private val strCrashedShort = context.getString(R.string.mp_crashed_short)
    private val strSpectating = context.getString(R.string.mp_spectating)
    private val strPlayerFormat = context.getString(R.string.mp_player_format)
    private val strLeftGame = context.getString(R.string.mp_left)
    private val strPlaceFormat = context.getString(R.string.mp_place_format)
    private val strWatchFormat = context.getString(R.string.mp_watch_format)
    private val strTapToSwitch = context.getString(R.string.mp_tap_switch)
    private val strNobodyLeft = context.getString(R.string.mp_nobody_left)
    private val strPlayersCount = context.getString(R.string.mp_players_count)
    private val strWatchingMany = context.getString(R.string.mp_watching_many)
    private val strLevels = context.getString(R.string.hub_levels)
    private val strEndless = context.getString(R.string.hub_endless)
    private val strMarket = context.getString(R.string.hub_market)
    private val strHubHint = context.getString(R.string.hub_hint)
    private val strLevelProgress = context.getString(R.string.level_progress)
    private val strLevelTitle = context.getString(R.string.level_title)
    private val strRound = context.getString(R.string.level_round)
    private val strTarget = context.getString(R.string.level_target)
    private val strPractice = context.getString(R.string.level_practice)
    private val strMapFinal = context.getString(R.string.level_final)
    private val strReward = context.getString(R.string.level_reward)
    private val strReplay = context.getString(R.string.level_replay)
    private val strLevelFailed = context.getString(R.string.level_failed)
    private val strLevelDone = context.getString(R.string.level_done)
    private val strAllDone = context.getString(R.string.level_all_done)
    private val strAllDoneDesc = context.getString(R.string.level_all_done_desc)
    private val strNext = context.getString(R.string.next)
    private val strLevelsPassed = context.getString(R.string.levels_passed)
    private val strTargetLabel = context.getString(R.string.target_label)
    private val strRewardLabel = context.getString(R.string.reward_label)
    private val strCoinsCollected = context.getString(R.string.coins_collected)
    private val strSelected = context.getString(R.string.market_selected)
    private val strSelect = context.getString(R.string.market_select)
    private val strNoMoney = context.getString(R.string.market_no_money)
    private val strBought = context.getString(R.string.market_bought)
    private val strLevelLocked = context.getString(R.string.level_locked)
    private val strNoLives = context.getString(R.string.lives_empty)
    private val strTapToSkip = context.getString(R.string.splash_skip)
    private val strRewardLife = context.getString(R.string.ad_reward_life)
    private val strRewardDouble = context.getString(R.string.ad_reward_double)
    private val strRewardCoins = context.getString(R.string.ad_reward_coins)
    private val strGotLife = context.getString(R.string.ad_got_life)
    private val strGotCoins = context.getString(R.string.ad_got_coins)
    private val strPrivacy = context.getString(R.string.ad_privacy)
    private val strLifeRule = context.getString(R.string.lives_rule)
    private val modNames = Mod.entries.map { context.getString(it.nameRes) }
    private val skinNames = Skin.entries.map { context.getString(it.nameRes) }
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
        context.getString(R.string.mp_help_6),
        context.getString(R.string.mp_help_7)
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
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
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
    private val viewBtns = Array(2) { RectF() } // lobide ekran seçimi: bölünmüş / tam ekran

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
        PlanetType.DARK to listOf(
            Palette(hex(0xFF3A2A4A), hex(0xFF120A1A), hex(0xFF6A5A8A), hex(0xFF2A1A3A), hex(0xFF8A5BFF)),
            Palette(hex(0xFF4A3028), hex(0xFF160C08), hex(0xFF8A6A50), hex(0xFF2A1A10), hex(0xFFFF9A3B))
        ),
        PlanetType.TOXIC to listOf(
            Palette(hex(0xFF5FBF3B), hex(0xFF1F5A12), hex(0xFFD8FF8A), hex(0xFFC8FF3B), hex(0xFF9BFF5B)),
            Palette(hex(0xFF3BBF8A), hex(0xFF0F5A3A), hex(0xFFA8FFD8), hex(0xFF7BFF3B), hex(0xFF5BFFB8))
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
        fetchNetTime()
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
        saveCoins()
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
            State.SPLASH -> finishSplash()
            State.HUB -> return false
            State.MENU, State.LEVELS, State.MARKET -> { sound.play(SoundManager.Sfx.CLICK); toHub() }
            State.PLAYING -> if (!paused) paused = true else leaveRun()
            State.MP_LOBBY, State.MP_COUNTDOWN, State.MP_PLAYING, State.MP_OVER -> leaveMp()
            else -> leaveRun()
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
            applyMpView()
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
        val vw = min((uiW - 52f) / 2f, 170f)
        viewBtns[0].set(cx - vw - 4f, 944f, cx - 4f, 986f)
        viewBtns[1].set(cx + 4f, 944f, cx + vw + 4f, 986f)
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
                lastMoveY = wy
                dragging = false
                scrollVel = 0f
                onPress()
            }
            MotionEvent.ACTION_MOVE -> if (state == State.LEVELS) {
                val dy = wy - lastMoveY
                if (abs(wy - downY) > 14f) dragging = true
                if (dragging) {
                    levelScroll = (levelScroll - dy).coerceIn(0f, maxLevelScroll())
                    scrollVel = -dy * 60f
                }
                lastMoveY = wy
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
        if (adShowing) return
        if (paused) {
            paused = false
            if (state == State.PLAYING) flap()
            return
        }
        when (state) {
            State.READY -> {
                // Can bittiyse: "video izle +1 can" düğmesi
                if (currentReward() != Reward.NONE && hit(rewardBtn, downX, downY, 6f)) {
                    watchReward()
                    return
                }
                if (levelMode && !lifeSpent) {
                    if (!lives.spend()) {
                        marketMsg = String.format(strNoLives, lives.nextText())
                        marketMsgT = 2f
                        sound.play(SoundManager.Sfx.GAME_OVER)
                        return
                    }
                    lifeSpent = true
                }
                state = State.PLAYING
                stateTimer = 0f
                flap()
            }
            State.PLAYING -> flap()
            State.MP_PLAYING -> if (myAlive) flap() else if (spectating()) cycleWatch()
            else -> Unit
        }
    }

    /** Parmak kalktığında: menü ve oyun sonu düğmeleri. */
    private fun onRelease(x: Float, y: Float) {
        if (adShowing) return
        val dx = x - downX
        val dy = y - downY
        val moved = abs(dx) > 28f || abs(dy) > 28f
        when (state) {
            State.SPLASH -> if (splashT > 0.6f) finishSplash()
            State.HUB -> if (!moved) hubTap(downX, downY)
            State.LEVELS -> if (!dragging) levelsTap(downX, downY)
            State.MARKET -> if (!moved) marketTap(downX, downY)
            State.LEVEL_DONE -> if (!moved && stateTimer > 0.6f) levelDoneTap(downX, downY)
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
                val lv = level
                when {
                    currentReward() != Reward.NONE && hit(rewardBtn, downX, downY, 4f) -> watchReward()
                    hit(soundBtn, downX, downY) -> toggleSound()
                    hit(menuBtn, downX, downY) -> {
                        sound.play(SoundManager.Sfx.CLICK)
                        afterBreak { leaveRun() }
                    }
                    levelMode && lv != null -> afterBreak { openLevel(lv.number) }
                    else -> afterBreak { startReady() } // "Tekrar" ya da ekranın herhangi bir yeri
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
            hit(backCircle, x, y) -> { sound.play(SoundManager.Sfx.CLICK); toHub() }
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
        rocketVy = FLAP_V * flapMul
        boost = 1f
        repeat(8) { emitExhaust(1.3f) }
        sound.play(SoundManager.Sfx.FLAP)
    }

    // =====================================================================
    // Menü işlemleri
    // =====================================================================

    private fun changeMap(dir: Int) {
        setMap(mapIndex + dir, save = true)
        cardAnim = dir.toFloat()
        sound.play(SoundManager.Sfx.SWIPE)
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

    /** Sonsuz mod ayar ekranı (harita + seviye seçimi). */
    private fun toMenu() {
        levelMode = false
        resetGame()
        state = State.MENU
    }

    /** Oyundan çıkış: bölüm modunda bölüm listesine, sonsuz modda ayar ekranına. */
    private fun leaveRun() {
        saveCoins()
        if (levelMode) enterLevels() else toMenu()
    }

    private fun startReady() {
        levelMode = false
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
        applyRunConfig()
        speed = cfg.baseSpeed * map.speedMul
        lastGapY = WORLD_H / 2f
        paused = false
        fireballs.clear()
        bolts.clear()
        fireTimer = 2.2f
        meteorTimer = 2.6f
        boltTimer = 2.4f
        windPhase = wr(0f, TWO_PI)
        runCoins = 0
        rewardUsed = false
        coinRng = Random(seed xor 0x5EED)
    }

    /**
     * Gerçek saati internetten (sunucunun "Date" başlığı) alır. Telefonun saati değiştirilse de etkilenmez;
     * yalnızca telefon kapalıyken geçen sürenin can dolumuna sayılmasını doğrulamak için kullanılır.
     */
    private fun fetchNetTime() {
        if (netBaseMs >= 0L) return
        thread(name = "gk-time", isDaemon = true) {
            runCatching {
                val t0 = SystemClock.elapsedRealtime()
                val c = URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.useCaches = false
                c.connect()
                val date = c.date
                c.disconnect()
                val t1 = SystemClock.elapsedRealtime()
                if (date > 0L) {
                    netBaseElapsed = (t0 + t1) / 2
                    netBaseMs = date + 500L // Date başlığı saniye hassasiyetinde
                }
            }
        }
    }

    /** Oyunun kurallarını belirler: harita + (bölüm modunda) bölüm özellikleri. */
    private fun applyRunConfig() {
        val lv = if (levelMode) level else null
        cfg = lv?.params ?: difficulty.params
        mechs = if (lv != null) map.mechs + lv.mechs else map.mechs
        gravMul = map.gravityMul
        flapMul = map.flapMul
        if (lv != null && lv.has(Mod.HEAVY)) { gravMul *= 1.18f; flapMul *= 1.06f }
        if (lv != null && lv.has(Mod.LIGHT)) { gravMul *= 0.8f; flapMul *= 0.88f }
        // Çok oyunculuda para yok (herkes aynı parkuru oynar, para tek kişilik ilerlemedir)
        val mp = isMpView() || state == State.MP_LOBBY
        coinChance = if (mp) 0f else if (lv != null && lv.has(Mod.GOLD)) 1f else 0.45f
        coinValue = if (lv != null && lv.has(Mod.GOLD)) 2 else 1
    }

    private fun hazard() = cfg.hazard * map.hazardMul

    /** Dünya üretimi için tohumlu rastgele sayı: iki telefonda aynı sonucu verir. */
    private fun wr(a: Float, b: Float): Float = a + worldRng.nextFloat() * (b - a)

    private fun computeWind(): Float = if (Mech.WIND in mechs) {
        WIND_BASE * hazard() * sin(worldTime * 0.62f + windPhase) *
            (0.75f + 0.25f * sin(worldTime * 1.7f))
    } else 0f

    private fun isMpView() =
        state == State.MP_COUNTDOWN || state == State.MP_PLAYING || state == State.MP_OVER

    private fun worldRunning() = state == State.PLAYING || state == State.MP_PLAYING

    /** Roketin bir adımlık fiziği: yer çekimi, rüzgâr, tavan ve eğim. */
    private fun stepRocket(dt: Float) {
        val maxFall = MAX_FALL * min(1f, gravMul)
        val g = GRAVITY * gravMul
        var force = g + wind
        // Kara delik: roket ekranın ortasına doğru çekilir
        if (Mech.PULL in mechs) force += (WORLD_H * 0.5f - rocketY) * PULL_K * hazard()
        // Rüzgâr ve çekim ne kadar güçlü olursa olsun roket her zaman inebilmeli (en az %35 yer çekimi)
        // ve aşağı itilirken dokunarak karşı koyabilmeli (en fazla 2 kat). Simülasyonda bu sınır olmadan
        // alçak geçitlere inmek imkânsızlaşıyordu.
        force = force.coerceIn(g * 0.35f, g * 2f)
        rocketVy = min(rocketVy + force * dt, maxFall)
        rocketY += rocketVy * dt
        if (rocketY < 22f) {
            rocketY = 22f
            if (rocketVy < 0f) rocketVy = 0f
        }
        val target = (rocketVy / MAX_FALL * 75f).coerceIn(-28f, 75f)
        rocketAngle += (target - rocketAngle) * min(1f, dt * 12f)
    }

    private fun currentSpeed(): Float =
        (cfg.baseSpeed + min(progress, cfg.speedCapScore) * cfg.speedPerScore) * map.speedMul

    private fun update(dt: Float) {
        time += dt
        if (state == State.SPLASH) {
            updateStars(dt, 40f)
            updateSplash(dt)
            return
        }
        hubFade = max(0f, hubFade - dt)
        stateTimer += dt
        boost = max(0f, boost - dt * 3.5f)
        scorePulse = max(0f, scorePulse - dt * 4f)
        shake = max(0f, shake - dt)
        flash = max(0f, flash - dt)
        shockT += dt
        cardAnim *= exp(-dt * 12f)
        if (abs(cardAnim) < 0.002f) cardAnim = 0f

        val base = cfg.baseSpeed * map.speedMul
        if (marketMsgT > 0f) marketMsgT -= dt
        updateBanner()
        livesTick -= dt
        if (livesTick <= 0f) {
            livesTick = 1f
            lives.update()
            if (netBaseMs < 0L) {
                netRetry -= 1f
                if (netRetry <= 0f) { netRetry = 60f; fetchNetTime() }
            }
        }
        val worldSpeed = when (state) {
            State.SPLASH, State.HUB, State.LEVELS, State.MARKET -> base * 0.45f
            State.LEVEL_DONE -> base * 0.25f
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
            State.SPLASH, State.MENU, State.HUB, State.MARKET -> Unit
            State.LEVELS -> if (!dragging && abs(scrollVel) > 1f) {
                levelScroll = (levelScroll + scrollVel * dt).coerceIn(0f, maxLevelScroll())
                scrollVel *= exp(-dt * 4f)
            }
            State.LEVEL_DONE -> {
                rocketY += (WORLD_H * 0.42f - rocketY) * min(1f, dt * 2.5f)
                rocketAngle += (-8f - rocketAngle) * min(1f, dt * 4f)
                emit(dt, 45f, 1.2f)
            }
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
                // Hedefe ulaşıldıysa bu karede artık çarpışma sayılmaz
                if (state == State.PLAYING) {
                    updateFireballs(dt)
                    emit(dt, 55f, 1f + boost * 0.6f)
                    if (rocketHits()) crash()
                }
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
                emit(dt, 28f, 0.8f)
                for (id in others()) {
                    pDispY[id] = WORLD_H * 0.45f + sin(time * 2.6f + id * 1.2f) * 14f
                    pAngle[id] = cos(time * 2.6f + id * 1.2f) * -7f
                    emitFor(id, dt, 28f, 0.8f, rocketX, pDispY[id], pAngle[id])
                }
                if (System.nanoTime() >= mpStartNs) {
                    state = State.MP_PLAYING
                    stateTimer = 0f
                    simTime = 0f
                    rocketY = WORLD_H * 0.45f
                    rocketVy = 0f
                    rocketAngle = 0f
                    for (id in others()) pY[id] = rocketY
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
        } else if (last.x + cfg.spacing <= spawnX) {
            columns.add(makeColumn(last.x + cfg.spacing))
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
                    val lv = level
                    if (state == State.PLAYING && levelMode && lv != null && score >= lv.target) {
                        levelComplete()
                        return
                    }
                }
            }
            // Para toplama
            if (state == State.PLAYING && !col.coinY.isNaN() && !col.coinTaken && abs(col.x - rocketX) < 60f) {
                if (rocketTouches(col.x, col.coinY + col.offset, COIN_R)) {
                    col.coinTaken = true
                    runCoins += coinValue
                    coins += coinValue
                    sound.play(SoundManager.Sfx.COIN)
                    repeat(10) {
                        val a = rnd(0f, TWO_PI)
                        spawn(col.x, col.coinY + col.offset, cos(a) * 140f, sin(a) * 140f, 0.4f, rnd(2f, 3.5f), colGold, 3f)
                    }
                }
            }
        }
    }

    /** Ortada bir geçiş boşluğu bırakan, üstte ve altta gezegen kulesi olan bir engel üretir. */
    private fun makeColumn(x: Float): Column {
        val d = cfg
        val gapH = max(d.minGap, d.startGap - progress * d.gapShrink)
        val amp = if (Mech.SWAY in mechs) d.swayAmp * map.hazardMul.coerceAtLeast(0.8f) else 0f
        var minY = 110f + gapH / 2f + amp
        var maxY = WORLD_H - 110f - gapH / 2f - amp
        // Kara delik çekimi varken en üst/en alt geçitlere ulaşılamıyordu: geçitler orta bölgede kalır
        if (Mech.PULL in mechs) {
            minY = max(minY, 250f)
            maxY = max(minY, min(maxY, WORLD_H - 250f))
        }
        val gapY = (lastGapY + wr(-maxGapJump(d), maxGapJump(d))).coerceIn(minY, maxY)
        lastGapY = gapY

        val planets = ArrayList<Planet>()
        buildStack(planets, gapY - gapH / 2f, -1f, amp) // üst kule
        buildStack(planets, gapY + gapH / 2f, 1f, amp)  // alt kule
        val col = Column(x, planets, amp, wr(0f, TWO_PI))
        col.gapTop = gapY - gapH / 2f
        if (amp > 0f) col.offset = amp * sin(worldTime * SWAY_SPEED + col.swayPhase)
        // Para: boşluğun içinde, ayrı rastgele sayı üreticisiyle (parkuru değiştirmez)
        if (coinChance > 0f && coinRng.nextFloat() < coinChance) {
            col.coinY = gapY + (coinRng.nextFloat() - 0.5f) * gapH * 0.45f
        }
        return col
    }

    /**
     * İki geçit arasındaki en büyük dikey fark: kuleler arası sürede, saniyede ~8 dokunuşla roketin
     * çıkabileceği yüksekliğin %80'i (hız arttıkça ve yer çekimi ağırlaştıkça küçülür).
     */
    private fun maxGapJump(d: DiffParams): Float {
        val tCol = d.spacing / max(60f, speed)
        val tap = 0.125f
        val v = -FLAP_V * flapMul
        val g = GRAVITY * gravMul
        val rise = max(40f, v * tap - 0.5f * g * tap * tap) / tap
        return min(d.gapJump, max(70f, rise * tCol * 0.8f))
    }

    /**
     * Boşluğun kenarına büyük bir gezegen koyar, ekranın kenarına kadar da uydularla doldurur.
     * Daireler birbirine bindiği için aralarından sızılamaz.
     */
    private fun buildStack(out: MutableList<Planet>, gapEdge: Float, dir: Float, extra: Float) {
        val stack = ArrayList<Planet>()
        val mainR = wr(60f, MAIN_R) * map.planetMul
        var cy = gapEdge + dir * mainR
        val spinning = Mech.SPIN in mechs
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

    /** Roketin çarpışma dairelerinden biri (x, y, r) dairesine değiyor mu. */
    private fun rocketTouches(x: Float, y: Float, r: Float): Boolean {
        val rad = rocketAngle * DEG
        val cs = cos(rad)
        val sn = sin(rad)
        for (i in hitX.indices) {
            val dx = x - (rocketX + hitX[i] * cs)
            val dy = y - (rocketY + hitX[i] * sn)
            val rr = r + hitR[i]
            if (dx * dx + dy * dy < rr * rr) return true
        }
        return false
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
            for (b in bolts) {
                if (b.warn > 0f || b.life <= 0f) continue
                if (abs(b.x - hx) < BOLT_W + hr && hy - hr < b.len) return true
            }
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
        saveCoins()
        runsSinceAd++
        if (!levelMode && score > best) {
            best = score
            newBest = true
            prefs.edit().putInt(bestKey(), best).apply()
            refreshBestTexts()
        }
    }

    private fun saveCoins() {
        prefs.edit().putInt(KEY_COINS, coins).apply()
    }

    /** Bölüm hedefine ulaşıldı. İlk kez geçilen bölüm ödül parası verir ve sonrakini açar. */
    private fun levelComplete() {
        val lv = level ?: return
        state = State.LEVEL_DONE
        stateTimer = 0f
        // Bölüm geçilince harcanan can geri verilir
        if (lifeSpent) {
            lives.refund()
            lifeSpent = false
        }
        runsSinceAd++
        rewardUsed = false
        val first = lv.number > levelMax
        levelBonus = if (first) lv.reward else 3
        coins += levelBonus
        if (first) {
            levelMax = lv.number
            prefs.edit().putInt(KEY_LEVEL_MAX, levelMax).apply()
        }
        saveCoins()
        sound.play(SoundManager.Sfx.RECORD)
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // Konfeti
        val colors = intArrayOf(colGold, colAccent, colBlue, colGreen, hex(0xFFFF8FD8), Color.WHITE)
        repeat(90) {
            val a = rnd(0f, TWO_PI)
            val sp = rnd(120f, 520f)
            spawn(rocketX, rocketY, cos(a) * sp, sin(a) * sp - 120f, rnd(0.8f, 1.8f), rnd(2.5f, 5f), colors[Random.nextInt(colors.size)], 1.4f)
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
    // Çok oyuncu (2-4 kişi): lobi, bağlantı ve maç
    // =====================================================================

    private fun playerName(id: Int) = if (id == myId) strYou else String.format(strPlayerFormat, id + 1)
    private fun playerColor(id: Int) = PLAYER_COLORS[id.coerceIn(0, 3)]
    private fun playerFin(id: Int) = PLAYER_FINS[id.coerceIn(0, 3)]
    private fun others() = players.filter { it != myId }
    private fun isAlive(id: Int) = if (id == myId) myAlive else pAlive[id]
    private fun scoreOf(id: Int) = if (id == myId) score else pScore[id]

    private fun enterLobby() {
        sound.play(SoundManager.Sfx.CLICK)
        resetGame()
        state = State.MP_LOBBY
        lobby = Lobby.CHOOSE
        netMessage = ""
        myId = 0
        roster.clear()
        // İlk kez çok oyunculuya girilince "Nasıl kurulur?" kendiliğinden açılır
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
        myId = 0
        roster.clear()
        roster.add(0)
        lobby = Lobby.HOSTING
        netMessage = ""
        sound.play(SoundManager.Sfx.CLICK)
    }

    private fun startJoin() {
        closeNet()
        net = NetLink(context).also { it.join() }
        roster.clear()
        lobby = Lobby.JOINING
        netMessage = ""
        sound.play(SoundManager.Sfx.CLICK)
    }

    /** Çok oyunculudan çıkıp ana menüye döner. */
    private fun leaveMp() {
        closeNet()
        val wasMpView = isMpView()
        sound.play(SoundManager.Sfx.CLICK)
        toHub()
        myId = 0
        roster.clear()
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
                hit(viewBtns[0], x, y, 4f) -> setMpFull(false)
                hit(viewBtns[1], x, y, 4f) -> setMpFull(true)
                hit(backCircle, x, y) -> { sound.play(SoundManager.Sfx.CLICK); toHub() }
                hit(startBtn, x, y) -> startHost()
                hit(multiBtn, x, y, 6f) -> startJoin()
                else -> pickTap(x, y)
            }
            Lobby.HOSTING, Lobby.JOINING -> if (hit(backBtn, x, y) || hit(backCircle, x, y)) {
                closeNet()
                roster.clear()
                lobby = Lobby.CHOOSE
                sound.play(SoundManager.Sfx.CLICK)
            }
            Lobby.CONNECTED -> when {
                hit(viewBtns[0], x, y, 4f) -> setMpFull(false)
                hit(viewBtns[1], x, y, 4f) -> setMpFull(true)
                hit(backCircle, x, y) || hit(multiBtn, x, y, 6f) -> leaveMp()
                hit(startBtn, x, y) && net?.isHost == true -> hostStart()
                else -> pickTap(x, y)
            }
        }
    }

    /** Ekran seçimi her oyuncunun kendi telefonuna aittir; diğerlerine gönderilmez. */
    private fun setMpFull(full: Boolean) {
        mpFull = full
        prefs.edit().putBoolean(KEY_MP_FULL, full).apply()
        sound.play(SoundManager.Sfx.CLICK)
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
     * Lobide harita değiştirme. Oda kurucusu doğrudan değiştirir ve herkese bildirir;
     * katılan oyuncu istek gönderir, kurucu onaylayıp herkese uygular.
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
        if (canRetry()) {
            when {
                hit(retryBtn, x, y) -> hostStart()
                hit(menuBtn, x, y) -> leaveMp()
            }
        } else if (hit(backBtn, x, y)) {
            leaveMp()
        }
    }

    private fun canRetry() = !mpDisconnected && net?.isHost == true && roster.size >= 2

    /** Oda kurucusu harita/seviye değiştirince herkese bildirir. */
    private fun sendSelection() {
        val n = net ?: return
        if (n.isHost) n.send("C", mapIndex, difficulty.ordinal)
    }

    private fun broadcastRoster() {
        net?.takeIf { it.isHost }?.send("L", roster.joinToString(","))
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
                is NetLink.Event.Connected -> Unit // oyuncu numarası ("W") gelince lobiye geçilir
                is NetLink.Event.Joined -> {
                    if (e.id !in roster) roster.add(e.id)
                    roster.sort()
                    broadcastRoster()
                    sendSelection()
                    if (lobby == Lobby.HOSTING) lobby = Lobby.CONNECTED
                    sound.play(SoundManager.Sfx.START)
                }
                is NetLink.Event.Left -> {
                    roster.remove(e.id)
                    broadcastRoster()
                    if ((state == State.MP_COUNTDOWN || state == State.MP_PLAYING) && e.id in players) {
                        playerOut(e.id)
                        n.send("X", e.id)
                    }
                    if (state == State.MP_LOBBY && lobby == Lobby.CONNECTED && roster.size < 2) lobby = Lobby.HOSTING
                }
                is NetLink.Event.Failed -> {
                    closeNet()
                    roster.clear()
                    lobby = Lobby.CHOOSE
                    netMessage = strNoNetwork
                }
                is NetLink.Event.Lost -> onHostLost()
                is NetLink.Event.Message -> handleMessage(e.from, e.parts)
            }
        }
    }

    private fun handleMessage(from: Int, p: List<String>) {
        val n = net ?: return
        val host = n.isHost
        when (p[0]) {
            "W" -> if (!host && p.size >= 2) {
                myId = (p[1].toIntOrNull() ?: return).coerceIn(1, NetLink.MAX_PLAYERS - 1)
                lobby = Lobby.CONNECTED
                netMessage = ""
                sound.play(SoundManager.Sfx.START)
            }
            "L" -> if (!host && p.size >= 2) {
                roster.clear()
                roster.addAll(p[1].split(',').mapNotNull { it.toIntOrNull() }.sorted())
            }
            "C" -> if (!host && p.size >= 3) {
                applySelection(p[1].toIntOrNull() ?: return, p[2].toIntOrNull() ?: return)
            }
            "R" -> if (host && p.size >= 3 && state == State.MP_LOBBY) {
                applySelection(p[1].toIntOrNull() ?: return, p[2].toIntOrNull() ?: return)
                sendSelection()
            }
            "G" -> if (!host && p.size >= 3) {
                val seed = p[1].toLongOrNull() ?: return
                beginMatch(seed, p[2].split(',').mapNotNull { it.toIntOrNull() })
            }
            "S" -> if (p.size >= 6) {
                if (host) n.relay(from, p)
                val id = p[1].toIntOrNull() ?: return
                if (id == myId || id !in 0 until NetLink.MAX_PLAYERS) return
                pY[id] = (p[2].toIntOrNull() ?: return) / 10f
                pAngle[id] = (p[3].toIntOrNull() ?: return).toFloat()
                pBoost[id] = max(pBoost[id], (p[4].toIntOrNull() ?: 0) / 100f)
                pScore[id] = p[5].toIntOrNull() ?: pScore[id]
            }
            "D" -> if (p.size >= 4) {
                if (host) n.relay(from, p)
                val id = p[1].toIntOrNull() ?: return
                if (id == myId || id !in 0 until NetLink.MAX_PLAYERS) return
                if (state == State.MP_PLAYING || state == State.MP_COUNTDOWN) {
                    playerDead(id, p[2].toIntOrNull() ?: pScore[id], (p[3].toIntOrNull() ?: 0) / 10f)
                }
            }
            "X" -> if (!host && p.size >= 2) playerOut(p[1].toIntOrNull() ?: return)
            "BYE" -> if (!host) onHostLost()
        }
    }

    /** Katılan oyuncu için: oda kurucusuyla bağlantı koptu. */
    private fun onHostLost() {
        closeNet()
        when (state) {
            State.MP_LOBBY -> {
                lobby = Lobby.CHOOSE
                roster.clear()
                netMessage = strDisconnected
            }
            State.MP_COUNTDOWN, State.MP_PLAYING -> {
                mpDisconnected = true
                finishMatch()
            }
            State.MP_OVER -> mpDisconnected = true
            else -> Unit
        }
    }

    /** Oda kurucusu yeni maçı başlatır: tohumu ve oyuncu listesini gönderir, herkes aynı parkuru üretir. */
    private fun hostStart() {
        val n = net ?: return
        if (!n.isHost || roster.size < 2) return
        val seed = Random.nextLong()
        val ids = roster.sorted()
        n.open = false // maç sırasında yeni oyuncu alınmaz
        n.send("G", seed, ids.joinToString(","))
        beginMatch(seed, ids)
    }

    /** Görünümün ölçeği ve bölünmüş ekran hücreleri. */
    private fun applyMpView() {
        mpScale = if (mpFull) height / WORLD_H else (height / 2f) / WORLD_H
        worldW = width / mpScale
        rocketX = MP_ROCKET_X
        val w = width.toFloat()
        val h = height.toFloat()
        val order = listOf(myId) + others()
        mpCells.clear()
        when (order.size) {
            1 -> mpCells.add(order[0] to RectF(0f, 0f, w, h / 2f))
            2 -> {
                mpCells.add(order[0] to RectF(0f, 0f, w, h / 2f))
                mpCells.add(order[1] to RectF(0f, h / 2f, w, h))
            }
            3 -> {
                mpCells.add(order[0] to RectF(0f, 0f, w, h / 2f))
                mpCells.add(order[1] to RectF(0f, h / 2f, w / 2f, h))
                mpCells.add(order[2] to RectF(w / 2f, h / 2f, w, h))
            }
            else -> {
                mpCells.add(order[0] to RectF(0f, 0f, w / 2f, h / 2f))
                mpCells.add(order[1] to RectF(w / 2f, 0f, w, h / 2f))
                mpCells.add(order[2] to RectF(0f, h / 2f, w / 2f, h))
                mpCells.add(order[3] to RectF(w / 2f, h / 2f, w, h))
            }
        }
    }

    private fun beginMatch(seed: Long, ids: List<Int>) {
        state = State.MP_COUNTDOWN
        resetGame(seed)
        players.clear()
        players.addAll(ids.distinct().filter { it in 0 until NetLink.MAX_PLAYERS }.sorted())
        if (myId !in players) players.add(myId)
        players.sort()
        applyMpView()
        buildStars()
        buildMotes()
        myAlive = true
        myDeadTime = 0f
        for (i in 0 until NetLink.MAX_PLAYERS) {
            pAlive[i] = i in players
            pGone[i] = false
            pScore[i] = 0
            pY[i] = WORLD_H * 0.45f
            pDispY[i] = pY[i]
            pAngle[i] = 0f
            pBoost[i] = 0f
        }
        watchId = -1
        mpPlace = 1
        mpTie = false
        mpDisconnected = false
        endTimer = 0f
        sendTimer = 0f
        simTime = 0f
        mpStartNs = System.nanoTime() + MP_COUNTDOWN_NS
        stateTimer = 0f
        sound.play(SoundManager.Sfx.START)
    }

    private fun updateMp(dt: Float) {
        // Dünya, başlangıçtan geçen gerçek süreye göre sabit adımlarla ilerler: telefonlar senkron kalır
        val target = (System.nanoTime() - mpStartNs) / 1_000_000_000f
        var steps = 0
        while (simTime + MP_STEP <= target && steps < 240) {
            mpStep(MP_STEP)
            simTime += MP_STEP
            steps++
        }
        if (steps == 240) simTime = target

        for (id in others()) {
            pBoost[id] = max(0f, pBoost[id] - dt * 3.5f)
            pDispY[id] += (pY[id] - pDispY[id]) * min(1f, dt * 14f)
            if (pAlive[id]) emitFor(id, dt, 55f, 1f + pBoost[id] * 0.6f, rocketX, pDispY[id], pAngle[id])
        }

        val n = net
        sendTimer -= dt
        if (myAlive && n != null && sendTimer <= 0f) {
            sendTimer = 0.05f
            n.send("S", myId, (rocketY * 10f).toInt(), rocketAngle.toInt(), (boost * 100f).toInt(), score)
        }
        if (myAlive) emit(dt, 55f, 1f + boost * 0.6f) else myDeadTime += dt

        if (players.none { isAlive(it) }) {
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
        pAlive[myId] = false
        pScore[myId] = score
        explode(rocketX, rocketY, myId)
        shake = 0.45f
        flash = 0.2f
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        sound.play(SoundManager.Sfx.CRASH)
        net?.send("D", myId, score, (rocketY * 10f).toInt())
    }

    private fun playerDead(id: Int, sc: Int, y: Float) {
        if (!pAlive[id]) return
        pAlive[id] = false
        pScore[id] = sc
        pY[id] = y
        pDispY[id] = y
        explode(rocketX, y, id)
        sound.play(SoundManager.Sfx.CRASH)
    }

    /** Oyuncunun bağlantısı koptu: yanmış sayılır, o ana kadarki puanı geçerlidir. */
    private fun playerOut(id: Int) {
        if (id == myId) return
        pGone[id] = true
        pAlive[id] = false
    }

    private fun finishMatch() {
        state = State.MP_OVER
        stateTimer = 0f
        pScore[myId] = score
        val top = players.maxOf { scoreOf(it) }
        mpPlace = 1 + players.count { scoreOf(it) > score }
        mpTie = mpPlace == 1 && players.count { scoreOf(it) == top } > 1
        val won = !mpDisconnected && mpPlace == 1 && !mpTie
        sound.play(if (won) SoundManager.Sfx.RECORD else SoundManager.Sfx.GAME_OVER)
    }

    /** Tam ekranda yanınca: hâlâ oynayan bir oyuncu izlenir. */
    private fun spectating() =
        mpFull && !myAlive && myDeadTime > 1f && state == State.MP_PLAYING && others().any { pAlive[it] }

    private fun shownId(): Int {
        if (!spectating()) return myId
        if (watchId < 0 || watchId == myId || !pAlive[watchId]) {
            watchId = others().filter { pAlive[it] }.maxByOrNull { pScore[it] } ?: myId
        }
        return watchId
    }

    /** İzlerken dokununca sıradaki oyuncuya geçilir. */
    private fun cycleWatch() {
        val alive = others().filter { pAlive[it] }
        if (alive.size < 2) return
        val i = alive.indexOf(shownId())
        watchId = alive[(i + 1) % alive.size]
        sound.play(SoundManager.Sfx.CLICK)
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
            MapKind.BLACKHOLE -> 45
            MapKind.STORM -> 80
            MapKind.METEOR -> 24
            MapKind.TOXIC -> 36
            MapKind.CORE -> 50
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
            MapKind.BLACKHOLE -> { // kara deliğe sürüklenen toz
                m.x = if (anywhere) rnd(0f, worldW) else worldW + rnd(10f, 80f)
                m.y = rnd(0f, WORLD_H)
                m.vx = -rnd(20f, 60f); m.vy = 0f
                m.size = rnd(1.2f, 2.6f)
            }
            MapKind.STORM -> { // yağmur
                m.x = rnd(0f, worldW + 200f)
                m.y = if (anywhere) rnd(0f, WORLD_H) else -rnd(10f, 60f)
                m.vx = -rnd(140f, 200f); m.vy = rnd(620f, 820f)
                m.size = rnd(1f, 2f)
                m.len = rnd(14f, 26f)
            }
            MapKind.METEOR -> { // uzaktan geçen küçük kuyruklu taşlar
                m.x = rnd(worldW * 0.2f, worldW + 260f)
                m.y = if (anywhere) rnd(0f, WORLD_H) else -rnd(10f, 120f)
                m.vx = -rnd(200f, 320f); m.vy = rnd(140f, 230f)
                m.size = rnd(1.2f, 2.4f)
            }
            MapKind.TOXIC -> { // yükselen zehirli kabarcıklar
                m.x = rnd(0f, worldW + 40f)
                m.y = if (anywhere) rnd(0f, WORLD_H) else WORLD_H + 12f
                m.vx = rnd(-10f, 10f); m.vy = -rnd(20f, 55f)
                m.size = rnd(3f, 9f)
            }
            MapKind.CORE -> { // parıltılar
                m.x = if (anywhere) rnd(0f, worldW) else worldW + rnd(5f, 60f)
                m.y = rnd(0f, WORLD_H)
                m.vx = -rnd(10f, 40f); m.vy = 0f
                m.size = rnd(2f, 5f)
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
                MapKind.BLACKHOLE -> {
                    m.x += (m.vx - worldSpeed * 0.2f) * dt
                    m.y += (WORLD_H * 0.5f - m.y) * 0.25f * dt + sin(time * 1.5f + m.phase) * 14f * dt
                }
                MapKind.STORM, MapKind.METEOR -> {
                    m.x += (m.vx - worldSpeed * 0.3f) * dt
                    m.y += m.vy * dt
                }
                MapKind.TOXIC -> {
                    m.x += (m.vx - worldSpeed * 0.15f + sin(time * 2f + m.phase) * 10f) * dt
                    m.y += m.vy * dt
                }
                MapKind.CORE -> m.x += (m.vx - worldSpeed * 0.15f) * dt
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
            MapKind.BLACKHOLE -> for (m in motes) {
                val f = 0.6f + 0.4f * sin(time * 3f + m.phase)
                paint.color = withAlpha(if (m.phase > 3f) hex(0xFFFFB86B) else hex(0xFFB08AFF), (180 * f).toInt())
                cv.drawCircle(m.x, m.y, m.size, paint)
            }
            MapKind.STORM -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (m in motes) {
                    paint.strokeWidth = m.size
                    paint.color = withAlpha(hex(0xFFBFD4FF), 90)
                    val k = m.len / m.vy
                    cv.drawLine(m.x, m.y, m.x - m.vx * k, m.y - m.len, paint)
                }
                paint.style = Paint.Style.FILL
            }
            MapKind.METEOR -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for (m in motes) {
                    paint.strokeWidth = m.size
                    paint.color = withAlpha(hex(0xFFFFB86B), 90)
                    cv.drawLine(m.x, m.y, m.x - m.vx * 0.12f, m.y - m.vy * 0.12f, paint)
                }
                paint.style = Paint.Style.FILL
                for (m in motes) {
                    paint.color = withAlpha(hex(0xFFFFE6C2), 200)
                    cv.drawCircle(m.x, m.y, m.size * 1.2f, paint)
                }
            }
            MapKind.TOXIC -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                for (m in motes) {
                    paint.color = withAlpha(hex(0xFF9BFF5B), 110)
                    cv.drawCircle(m.x, m.y, m.size, paint)
                }
                paint.style = Paint.Style.FILL
                for (m in motes) {
                    paint.color = withAlpha(Color.WHITE, 90)
                    cv.drawCircle(m.x - m.size * 0.35f, m.y - m.size * 0.35f, m.size * 0.25f, paint)
                }
            }
            MapKind.CORE -> for (m in motes) {
                val f = (0.5f + 0.5f * sin(time * 3f + m.phase)).coerceIn(0f, 1f)
                paint.color = withAlpha(hex(0xFFFFE6A0), (230 * f).toInt())
                val r = m.size * (0.5f + 0.5f * f)
                cv.drawRect(m.x - r, m.y - 0.8f, m.x + r, m.y + 0.8f, paint)
                cv.drawRect(m.x - 0.8f, m.y - r, m.x + 0.8f, m.y + r, paint)
            }
        }
    }

    // --- Lav Denizi: ateş topları ---

    private fun updateFireballs(dt: Float) {
        val running = worldRunning()
        val maxX = if (isMpView()) 420f else worldW - 30f
        if (running && Mech.FIRE in mechs) {
            fireTimer -= dt
            if (fireTimer <= 0f) {
                fireTimer = wr(2.3f, 3.6f) / hazard()
                // Topu, roketin hizasına tepeye yakınken varacak şekilde ileriye koy
                val warn = 0.8f
                val x = min(maxX, rocketX + speed * (warn + wr(0.35f, 0.85f)))
                val vy = -wr(980f, 1200f)
                // Kulenin içinden (geçidin içinden) fırlamaz; yalnızca kuleler arasından çıkar
                if (columns.none { abs(it.x - x) < 120f }) fireballs.add(Fireball(x, WORLD_H + 40f, vy, warn))
            }
        }
        if (running && Mech.METEOR in mechs) {
            meteorTimer -= dt
            if (meteorTimer <= 0f) {
                meteorTimer = wr(2.0f, 3.2f) / hazard()
                val warn = 0.8f
                val x = min(maxX, rocketX + speed * (warn + wr(0.7f, 1.3f)))
                fireballs.add(Fireball(x, -40f, wr(120f, 240f), warn, fromTop = true))
            }
        }
        if (running && Mech.LIGHTNING in mechs) {
            boltTimer -= dt
            if (boltTimer <= 0f) {
                boltTimer = wr(2.1f, 3.3f) / hazard()
                val warn = 0.95f
                val x = min(maxX, rocketX + speed * (warn + wr(0.1f, 0.5f)))
                val len = safeBoltLen(x, wr(360f, 700f))
                if (len > 0f) bolts.add(Bolt(x, len, warn, 0.4f))
            }
        }

        val iter = fireballs.iterator()
        while (iter.hasNext()) {
            val fb = iter.next()
            if (running) fb.x -= speed * dt
            if (fb.warn > 0f) {
                fb.warn -= dt
                if (fb.warn <= 0f) sound.play(SoundManager.Sfx.FIRE)
                continue
            }
            if (fb.fromTop) {
                fb.vy += FIRE_GRAVITY * 0.45f * dt
                fb.x -= 90f * dt // çapraz düşüş
            } else {
                fb.vy += FIRE_GRAVITY * dt
            }
            fb.y += fb.vy * dt
            if (Random.nextFloat() < 0.7f) {
                spawnOwner = -1
                spawn(
                    fb.x + rnd(-6f, 6f), fb.y + rnd(-6f, 6f), rnd(-30f, 30f), -fb.vy * 0.15f,
                    rnd(0.25f, 0.5f), rnd(2f, 4.5f),
                    if (Random.nextBoolean()) hex(0xFFFFB347) else hex(0xFFFF5A1F), 2f
                )
                spawnOwner = myId
            }
            val gone = if (fb.fromTop) fb.y > WORLD_H + 60f else (fb.vy > 0f && fb.y > WORLD_H + 60f)
            if (gone || fb.x < -60f) iter.remove()
        }

        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            if (running) b.x -= speed * dt
            if (b.warn > 0f) {
                b.warn -= dt
                if (b.warn <= 0f) {
                    sound.play(SoundManager.Sfx.FIRE)
                    if (!isMpView()) flash = max(flash, 0.06f)
                }
            } else {
                b.life -= dt
                if (b.life <= 0f) bi.remove()
            }
        }
    }

    /**
     * Yıldırım, iki yanındaki kulelerin geçitlerinin üstünde biter; böylece roket geçitlerin
     * yüksekliğinde kalarak her zaman altından geçebilir. Yer kalmıyorsa (0) yıldırım düşmez.
     * (Simülasyonla doğrulandı: bu kural olmadan bazı anlarda hiçbir hamle kurtarmıyordu.)
     */
    private fun safeBoltLen(x: Float, len: Float): Float {
        var l = len
        for (col in columns) {
            if (abs(col.x - x) < cfg.spacing + 80f) l = min(l, col.gapTop - col.swayAmp - 30f)
        }
        return if (l < 120f) 0f else l
    }

    private fun drawFireballs(cv: Canvas) {
        drawBolts(cv)
        for (fb in fireballs) {
            if (fb.warn > 0f && fb.fromTop) {
                // Meteor uyarısı: ekranın üstünde kırmızı işaret
                val k = 1f - fb.warn / 0.8f
                val pulse = 0.6f + 0.4f * sin(time * 30f)
                paint.color = withAlpha(hex(0xFFFF5A1F), (k * 170 * pulse).toInt())
                iconPath.reset()
                iconPath.moveTo(fb.x - 16f, 8f)
                iconPath.lineTo(fb.x + 16f, 8f)
                iconPath.lineTo(fb.x, 34f)
                iconPath.close()
                cv.drawPath(iconPath, paint)
                continue
            }
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
        emitFor(myId, dt, rate, strength, rocketX, rocketY, rocketAngle)

    /** [owner] roketinin egzozu (0: ben, 1: rakip). */
    private fun emitFor(owner: Int, dt: Float, rate: Float, strength: Float, x: Float, y: Float, angle: Float) {
        emitAccs[owner] += dt * rate
        while (emitAccs[owner] >= 1f) {
            emitAccs[owner] -= 1f
            emitExhaust(strength, x, y, angle, owner)
        }
    }

    private fun emitExhaust(
        strength: Float, x: Float = rocketX, y: Float = rocketY, angle: Float = rocketAngle, owner: Int = myId
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

    private fun explode(x: Float, y: Float, owner: Int = myId) {
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
        spawnOwner = myId
        if (owner != myId) return
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
        if (state == State.SPLASH) {
            drawSplash(canvas)
            return
        }
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
        if (state == State.READY || state == State.PLAYING || state == State.LEVEL_DONE) drawRocket(canvas)
        drawShockwave(canvas)
        canvas.restore()

        canvas.save()
        canvas.scale(scale, scale)
        drawHud(canvas)
        canvas.restore()

        if (flash > 0f) canvas.drawColor(withAlpha(Color.WHITE, (flash / 0.2f * 140).toInt()))
        // Açılıştan ana ekrana yumuşak geçiş
        if (hubFade > 0f) canvas.drawColor(withAlpha(hex(0xFF020814), (hubFade / 0.5f * 255).toInt()))
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
        drawColumnPlanets(cv)
        // Paralar boşluğun içinde, kendi ekseninde döner
        for (col in columns) {
            if (col.coinY.isNaN() || col.coinTaken) continue
            if (col.x < -30f || col.x > worldW + 30f) continue
            drawCoin(cv, col.x, col.coinY + col.offset, COIN_R, time * 3f + col.swayPhase)
        }
    }

    /** Dönen para: genişliği cos ile daralıp genişler. */
    private fun drawCoin(cv: Canvas, x: Float, y: Float, r: Float, phase: Float) {
        val k = 0.25f + 0.75f * abs(cos(phase))
        paint.color = withAlpha(colGold, 70)
        cv.drawCircle(x, y, r * 1.6f, paint)
        tmpRect.set(x - r * k, y - r, x + r * k, y + r)
        paint.color = hex(0xFFC8901F)
        cv.drawOval(tmpRect, paint)
        tmpRect.set(x - r * k * 0.82f, y - r * 0.82f, x + r * k * 0.82f, y + r * 0.82f)
        paint.color = colGold
        cv.drawOval(tmpRect, paint)
        paint.color = hex(0xFFFFF4C2)
        tmpRect.set(x - r * k * 0.3f, y - r * 0.55f, x + r * k * 0.1f, y + r * 0.1f)
        cv.drawOval(tmpRect, paint)
    }

    /** Yukarıdan inen yıldırımlar: önce kesik çizgi uyarısı, sonra parlak zikzak. */
    private fun drawBolts(cv: Canvas) {
        for (b in bolts) {
            if (b.warn > 0f) {
                val k = 1f - b.warn / 0.95f
                val pulse = 0.55f + 0.45f * sin(time * 26f)
                paint.color = withAlpha(hex(0xFF9BC8FF), (60 + 150 * k * pulse).toInt())
                var y = 0f
                while (y < b.len) {
                    cv.drawRect(b.x - 2f, y, b.x + 2f, min(b.len, y + 22f), paint)
                    y += 38f
                }
                // Ucunda: yıldırımın ineceği son nokta
                paint.color = withAlpha(hex(0xFFFFFFFF), (120 + 120 * k).toInt())
                cv.drawCircle(b.x, b.len, 6f, paint)
                continue
            }
            val a = (b.life / 0.4f).coerceIn(0f, 1f)
            iconPath.reset()
            iconPath.moveTo(b.x, 0f)
            var y = 0f
            while (y < b.len) {
                y = min(b.len, y + rnd(30f, 60f))
                iconPath.lineTo(b.x + rnd(-BOLT_W, BOLT_W), y)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 16f
            paint.color = withAlpha(hex(0xFF5B7BFF), (a * 110).toInt())
            cv.drawPath(iconPath, paint)
            paint.strokeWidth = 5f
            paint.color = withAlpha(Color.WHITE, (a * 255).toInt())
            cv.drawPath(iconPath, paint)
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(hex(0xFFBFD4FF), (a * 200).toInt())
            cv.drawCircle(b.x, b.len, 14f, paint)
        }
    }

    private fun drawColumnPlanets(cv: Canvas) {
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

    private fun drawParticles(cv: Canvas, owner: Int = myId) {
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
        boostV: Float = boost, colorId: Int = myId
    ) {
        cv.save()
        cv.translate(x, y)
        cv.rotate(angle)
        // Tek kişilik oyunda marketten seçilen araç; çok oyunculuda oyuncu renkli klasik roket
        if (!isMpView()) {
            drawSkinBody(cv, skin, boostV)
            cv.restore()
            return
        }

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
        paint.color = playerFin(colorId)
        cv.drawPath(finTop, paint)
        cv.drawPath(finBottom, paint)

        // Gövde
        paint.color = Color.WHITE
        paint.shader = bodyShader
        cv.drawPath(bodyPath, paint)
        paint.shader = null
        paint.color = playerColor(colorId)
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

    // --- Marketteki uçan araçlar ---

    private val skinShaders = HashMap<Skin, Shader>()
    private val skinPath = Path()
    private val cometShader = LinearGradient(
        0f, 0f, -1f, 0f,
        intArrayOf(hex(0xEEFFFFFF), hex(0xAA9BE0FF), hex(0x445FB8FF), hex(0x005FB8FF)),
        floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP
    )

    private fun mixColor(c: Int, to: Int, t: Float): Int {
        fun ch(v: Int, s: Int) = ((v shr s) and 0xFF)
        val r = (ch(c, 16) + (ch(to, 16) - ch(c, 16)) * t).toInt()
        val g = (ch(c, 8) + (ch(to, 8) - ch(c, 8)) * t).toInt()
        val b = (ch(c, 0) + (ch(to, 0) - ch(c, 0)) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Menüde ve markette önizleme: aracı (x, y) noktasına [s] ölçeğinde çizer. */
    private fun drawSkinPreview(cv: Canvas, sk: Skin, x: Float, y: Float, s: Float, boostV: Float) {
        cv.save()
        cv.translate(x, y)
        cv.scale(s, s)
        drawSkinBody(cv, sk, boostV)
        cv.restore()
    }

    /** Alev: dokununca bir anlığına uzar. [ox] alevin başladığı x, [w] yarı kalınlık. */
    private fun drawFlame(cv: Canvas, boostV: Float, ox: Float, w: Float) {
        val flicker = 0.85f + 0.15f * sin(time * 47f) + rnd(-0.06f, 0.06f)
        val len = (13f + boostV * 24f) * flicker * (w / 7f)
        cv.save()
        cv.translate(ox, 0f)
        flamePath.reset()
        flamePath.moveTo(0f, -w)
        flamePath.quadTo(-0.55f * len, -w * 0.86f, -len, 0f)
        flamePath.quadTo(-0.55f * len, w * 0.86f, 0f, w)
        flamePath.close()
        flameMatrix.setScale(len, 1f)
        flameShader.setLocalMatrix(flameMatrix)
        paint.color = Color.WHITE
        paint.shader = flameShader
        cv.drawPath(flamePath, paint)
        paint.shader = null
        val inner = len * 0.55f
        flamePath.reset()
        flamePath.moveTo(0f, -w * 0.57f)
        flamePath.quadTo(-0.55f * inner, -w * 0.5f, -inner, 0f)
        flamePath.quadTo(-0.55f * inner, w * 0.5f, 0f, w * 0.57f)
        flamePath.close()
        paint.color = hex(0xEEFFFBE6)
        cv.drawPath(flamePath, paint)
        cv.restore()
    }

    private fun drawWindow(cv: Canvas, x: Float, r: Float) {
        paint.color = hex(0xFF3B4266)
        cv.drawCircle(x, 0f, r * 1.29f, paint)
        paint.color = Color.WHITE
        paint.shader = windowShader
        cv.save()
        cv.translate(x - 4f, 0f)
        cv.drawCircle(4f, 0f, r, paint)
        cv.restore()
        paint.shader = null
        paint.color = hex(0xDDFFFFFF)
        cv.drawCircle(x - r * 0.33f, -r * 0.39f, r * 0.29f, paint)
    }

    /** Aracı merkez (0, 0), sağa bakacak şekilde çizer. Çarpışma alanı bütün araçlarda aynıdır. */
    private fun drawSkinBody(cv: Canvas, sk: Skin, boostV: Float) {
        when (sk.shape) {
            SkinShape.ROCKET -> {
                drawFlame(cv, boostV, -19f, 7f)
                paint.color = hex(0xFF4A4F6E)
                cv.drawRect(-23f, -6.5f, -18f, 6.5f, paint)
                paint.color = sk.trim
                cv.drawPath(finTop, paint)
                cv.drawPath(finBottom, paint)
                paint.color = Color.WHITE
                paint.shader = if (sk == Skin.CLASSIC) bodyShader else skinShaders.getOrPut(sk) {
                    LinearGradient(
                        0f, -10f, 0f, 10f,
                        intArrayOf(mixColor(sk.body, Color.WHITE, 0.55f), sk.body, mixColor(sk.body, Color.BLACK, 0.4f)),
                        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                    )
                }
                cv.drawPath(bodyPath, paint)
                paint.shader = null
                paint.color = sk.accent
                cv.drawPath(noseAccent, paint)
                cv.drawPath(stripeAccent, paint)
                drawWindow(cv, 4f, 4.9f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.1f
                paint.color = hex(0x40000000)
                cv.drawPath(bodyPath, paint)
                paint.style = Paint.Style.FILL
            }
            SkinShape.SHUTTLE -> {
                drawFlame(cv, boostV, -26f, 6f)
                // Kanatlar
                paint.color = mixColor(sk.body, Color.BLACK, 0.25f)
                skinPath.reset()
                skinPath.moveTo(-2f, 5f); skinPath.lineTo(-20f, 21f); skinPath.lineTo(-27f, 21f); skinPath.lineTo(-24f, 5f); skinPath.close()
                cv.drawPath(skinPath, paint)
                skinPath.reset()
                skinPath.moveTo(-12f, -6f); skinPath.lineTo(-24f, -19f); skinPath.lineTo(-28f, -19f); skinPath.lineTo(-26f, -6f); skinPath.close()
                cv.drawPath(skinPath, paint)
                // Gövde
                skinPath.reset()
                skinPath.moveTo(-27f, -7f)
                skinPath.lineTo(16f, -7f)
                skinPath.cubicTo(27f, -7f, 33f, -3f, 36f, 0f)
                skinPath.cubicTo(33f, 3f, 27f, 7f, 16f, 7f)
                skinPath.lineTo(-27f, 7f)
                skinPath.close()
                paint.color = Color.WHITE
                paint.shader = skinShaders.getOrPut(sk) {
                    LinearGradient(
                        0f, -7f, 0f, 7f,
                        intArrayOf(Color.WHITE, sk.body, mixColor(sk.body, Color.BLACK, 0.3f)),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                    )
                }
                cv.drawPath(skinPath, paint)
                paint.shader = null
                // Siyah burun ve turuncu şerit
                paint.color = sk.accent
                cv.save()
                cv.clipRect(24f, -8f, 37f, 8f)
                cv.drawPath(skinPath, paint)
                cv.restore()
                paint.color = sk.trim
                cv.drawRect(-24f, 1.5f, 18f, 3.5f, paint)
                paint.color = hex(0xFF2A3050)
                for (i in 0 until 3) cv.drawRect(14f + i * 4f, -4f, 16.5f + i * 4f, -1.5f, paint)
            }
            SkinShape.UFO -> {
                // Alt ışık huzmesi: dokununca parlar
                paint.color = withAlpha(sk.accent, (50 + 120 * boostV).toInt())
                skinPath.reset()
                skinPath.moveTo(-9f, 5f); skinPath.lineTo(9f, 5f); skinPath.lineTo(16f, 26f); skinPath.lineTo(-16f, 26f); skinPath.close()
                cv.drawPath(skinPath, paint)
                // Kubbe
                tmpRect.set(-12f, -17f, 12f, 3f)
                paint.color = withAlpha(sk.accent, 170)
                cv.drawOval(tmpRect, paint)
                paint.color = withAlpha(Color.WHITE, 140)
                tmpRect.set(-7f, -14f, -1f, -9f)
                cv.drawOval(tmpRect, paint)
                // Daire gövde
                tmpRect.set(-27f, -7f, 27f, 8f)
                paint.color = Color.WHITE
                paint.shader = skinShaders.getOrPut(sk) {
                    LinearGradient(
                        0f, -7f, 0f, 8f,
                        intArrayOf(mixColor(sk.body, Color.WHITE, 0.5f), sk.body, sk.trim),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                    )
                }
                cv.drawOval(tmpRect, paint)
                paint.shader = null
                paint.color = sk.trim
                cv.drawRect(-24f, 1f, 24f, 3f, paint)
                // Yanıp sönen ışıklar
                for (i in 0 until 5) {
                    val on = ((time * 6f).toInt() + i) % 5 < 2
                    paint.color = if (on) sk.accent else withAlpha(sk.accent, 70)
                    cv.drawCircle(-18f + i * 9f, 4.5f, 2.2f, paint)
                }
            }
            SkinShape.PLANET -> {
                drawFlame(cv, boostV, -12f, 6f)
                val ringed = Color.alpha(sk.trim) > 0
                tmpRect.set(-27f, -8f, 27f, 8f)
                if (ringed) {
                    cv.save()
                    cv.rotate(-18f)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.5f
                    paint.color = withAlpha(sk.trim, 220)
                    cv.drawArc(tmpRect, 180f, 180f, false, paint) // arka yarı
                    paint.style = Paint.Style.FILL
                    cv.restore()
                }
                paint.color = Color.WHITE
                paint.shader = skinShaders.getOrPut(sk) {
                    RadialGradient(
                        -5f, -6f, 22f,
                        intArrayOf(mixColor(sk.body, Color.WHITE, 0.45f), sk.body, mixColor(sk.body, Color.BLACK, 0.55f)),
                        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                    )
                }
                cv.drawCircle(0f, 0f, 15f, paint)
                paint.shader = null
                cv.save()
                cv.clipPath(skinPath.apply { reset(); addCircle(0f, 0f, 15f, Path.Direction.CW) })
                if (ringed) {
                    paint.color = withAlpha(sk.accent, 120)
                    cv.drawRect(-16f, -6f, 16f, -3f, paint)
                    cv.drawRect(-16f, 3f, 16f, 6f, paint)
                } else {
                    // Lav çatlakları parlar
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = withAlpha(sk.accent, (170 + 80 * sin(time * 5f)).toInt().coerceIn(0, 255))
                    cv.drawLine(-12f, -4f, -2f, 2f, paint)
                    cv.drawLine(-2f, 2f, 6f, -5f, paint)
                    cv.drawLine(2f, 9f, 10f, 4f, paint)
                    paint.style = Paint.Style.FILL
                }
                cv.restore()
                if (ringed) {
                    cv.save()
                    cv.rotate(-18f)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3.5f
                    paint.color = withAlpha(sk.trim, 240)
                    cv.drawArc(tmpRect, 0f, 180f, false, paint) // ön yarı
                    paint.style = Paint.Style.FILL
                    cv.restore()
                } else {
                    paint.color = withAlpha(sk.accent, 60)
                    cv.drawCircle(0f, 0f, 19f, paint)
                }
            }
            SkinShape.COMET -> {
                val len = 46f + boostV * 34f + 4f * sin(time * 20f)
                flamePath.reset()
                flamePath.moveTo(4f, -11f)
                flamePath.quadTo(-0.5f * len, -7f, -len, 0f)
                flamePath.quadTo(-0.5f * len, 7f, 4f, 11f)
                flamePath.close()
                flameMatrix.setScale(len, 1f)
                cometShader.setLocalMatrix(flameMatrix)
                paint.color = Color.WHITE
                paint.shader = cometShader
                cv.drawPath(flamePath, paint)
                paint.shader = null
                paint.color = withAlpha(sk.accent, 90)
                cv.drawCircle(4f, 0f, 21f, paint)
                paint.color = sk.body
                cv.drawCircle(4f, 0f, 12f, paint)
                paint.color = Color.WHITE
                cv.drawCircle(6f, -1f, 6.5f, paint)
            }
            SkinShape.CRYSTAL -> {
                drawFlame(cv, boostV, -20f, 6f)
                paint.color = withAlpha(sk.trim, 80)
                cv.drawCircle(4f, 0f, 24f, paint)
                skinPath.reset()
                skinPath.moveTo(33f, 0f); skinPath.lineTo(4f, -15f); skinPath.lineTo(-22f, 0f); skinPath.close()
                paint.color = mixColor(sk.body, Color.WHITE, 0.4f)
                cv.drawPath(skinPath, paint)
                skinPath.reset()
                skinPath.moveTo(33f, 0f); skinPath.lineTo(4f, 15f); skinPath.lineTo(-22f, 0f); skinPath.close()
                paint.color = mixColor(sk.body, sk.trim, 0.45f)
                cv.drawPath(skinPath, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = withAlpha(sk.accent, 220)
                cv.drawLine(-22f, 0f, 33f, 0f, paint)
                cv.drawLine(4f, -15f, 4f, 15f, paint)
                skinPath.reset()
                skinPath.moveTo(33f, 0f); skinPath.lineTo(4f, -15f); skinPath.lineTo(-22f, 0f); skinPath.lineTo(4f, 15f); skinPath.close()
                cv.drawPath(skinPath, paint)
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(Color.WHITE, (150 + 100 * sin(time * 4f)).toInt().coerceIn(0, 255))
                cv.drawCircle(12f, -5f, 2.2f, paint)
            }
        }
    }

    // =====================================================================
    // Ana menü, bölümler, market
    // =====================================================================

    private val hubLevelsBtn = RectF()
    private val hubEndlessBtn = RectF()
    private val hubMultiBtn = RectF()
    private val hubMarketBtn = RectF()
    private val coinPillRect = RectF()
    private val marketCards = Array(Skin.entries.size) { RectF() }

    private fun layoutHub() {
        val cx = uiW / 2f
        val bw = min(uiW - 80f, 320f) / 2f
        hubLevelsBtn.set(cx - bw, 476f, cx + bw, 556f)
        hubEndlessBtn.set(cx - bw, 578f, cx + bw, 640f)
        hubMultiBtn.set(cx - bw, 658f, cx + bw, 720f)
        hubMarketBtn.set(cx - bw, 738f, cx + bw, 800f)
        val cw = (uiW - 32f - 12f) / 2f
        for (i in marketCards.indices) {
            val col = i % 2
            val row = i / 2
            val x0 = 16f + col * (cw + 12f)
            // Kartlar banner'ın üstünde kalacak şekilde sığdırılır
            val pitch = min(166f, (WORLD_H - bannerReserve - 8f - 148f) / 5f)
            val y0 = 148f + row * pitch
            marketCards[i].set(x0, y0, x0 + cw, y0 + pitch - 8f)
        }
    }

    private fun toHub() {
        closeNet()
        levelMode = false
        resetGame()
        state = State.HUB
    }

    private fun enterLevels() {
        sound.play(SoundManager.Sfx.CLICK)
        levelMode = false
        resetGame()
        state = State.LEVELS
        val currentBlock = (levelMax.coerceAtMost(Levels.COUNT - 1)) / Levels.PER_MAP
        levelScroll = (currentBlock * LEVEL_ROW_H - 260f).coerceIn(0f, maxLevelScroll())
        scrollVel = 0f
    }

    private fun maxLevelScroll() =
        max(0f, (Levels.COUNT / Levels.PER_MAP) * LEVEL_ROW_H - (WORLD_H - bannerReserve - LEVEL_LIST_TOP) + 30f)

    /** Haritayı değiştirir (görseller ve müzik dahil). [save] ise menüdeki seçim olarak da hatırlanır. */
    private fun setMap(index: Int, save: Boolean) {
        val n = Maps.ALL.size
        mapIndex = ((index % n) + n) % n
        map = Maps.ALL[mapIndex]
        if (save) prefs.edit().putInt(KEY_MAP, mapIndex).apply()
        if (width > 0 && height > 0) {
            buildBackground(width, height)
            buildPlanetArts()
            buildStars()
            buildMotes()
        }
        sound.setMusicTheme(map.musicTheme)
        refreshBestTexts()
    }

    private fun openLevel(n: Int) {
        val lv = Levels.info(n)
        lifeSpent = false
        levelMode = true
        level = lv
        if (lv.mapIndex != mapIndex) setMap(lv.mapIndex, save = false)
        state = State.READY
        resetGame()
        sound.play(SoundManager.Sfx.START)
    }

    private fun hubTap(x: Float, y: Float) {
        when {
            !privacyRect.isEmpty && hit(privacyRect, x, y, 4f) -> ads?.showPrivacyOptions()
            hit(soundBtn, x, y) -> toggleSound()
            hit(coinPillRect, x, y) || hit(hubMarketBtn, x, y) -> { sound.play(SoundManager.Sfx.CLICK); state = State.MARKET }
            hit(hubLevelsBtn, x, y) -> enterLevels()
            hit(hubEndlessBtn, x, y) -> {
                sound.play(SoundManager.Sfx.CLICK)
                setMap(prefs.getInt(KEY_MAP, mapIndex), save = false)
                toMenu()
            }
            hit(hubMultiBtn, x, y) -> enterLobby()
        }
    }

    private fun levelsTap(x: Float, y: Float) {
        if (hit(backCircle, x, y)) {
            sound.play(SoundManager.Sfx.CLICK)
            toHub()
            return
        }
        if (y < LEVEL_LIST_TOP) return
        val b = ((y - LEVEL_LIST_TOP + levelScroll) / LEVEL_ROW_H).toInt()
        if (b < 0 || b >= Levels.COUNT / Levels.PER_MAP) return
        val y0 = LEVEL_LIST_TOP + b * LEVEL_ROW_H - levelScroll
        val sp = (uiW - 100f) / Levels.PER_MAP
        for (i in 0 until Levels.PER_MAP) {
            val cx = 50f + sp * (i + 0.5f)
            val cy = y0 + 100f
            if (abs(x - cx) < sp / 2f && abs(y - cy) < 40f) {
                val n = b * Levels.PER_MAP + i + 1
                if (n <= levelMax + 1) openLevel(n)
                else {
                    marketMsg = strLevelLocked
                    marketMsgT = 1.6f
                    sound.play(SoundManager.Sfx.CLICK)
                }
                return
            }
        }
    }

    private fun marketTap(x: Float, y: Float) {
        if (hit(backCircle, x, y)) {
            sound.play(SoundManager.Sfx.CLICK)
            state = State.HUB
            return
        }
        for (i in marketCards.indices) {
            if (!hit(marketCards[i], x, y, 0f)) continue
            val sk = Skin.entries[i]
            when {
                sk in ownedSkins -> {
                    skin = sk
                    prefs.edit().putString(KEY_SKIN, sk.name).apply()
                    sound.play(SoundManager.Sfx.CLICK)
                }
                coins >= sk.price -> {
                    coins -= sk.price
                    ownedSkins.add(sk)
                    skin = sk
                    prefs.edit()
                        .putInt(KEY_COINS, coins)
                        .putString(KEY_SKINS, ownedSkins.joinToString(",") { it.name })
                        .putString(KEY_SKIN, sk.name)
                        .apply()
                    marketMsg = strBought
                    marketMsgT = 1.6f
                    sound.play(SoundManager.Sfx.RECORD)
                }
                else -> {
                    marketMsg = String.format(strNoMoney, sk.price - coins)
                    marketMsgT = 1.8f
                    sound.play(SoundManager.Sfx.GAME_OVER)
                }
            }
            return
        }
    }

    private fun levelDoneTap(x: Float, y: Float) {
        val lv = level ?: return
        when {
            currentReward() != Reward.NONE && hit(rewardBtn, x, y, 4f) -> watchReward()
            hit(retryBtn, x, y) && lv.number < Levels.COUNT -> afterBreak { openLevel(lv.number + 1) }
            hit(menuBtn, x, y) -> afterBreak { enterLevels() }
        }
    }

    // =====================================================================
    // Reklamlar: banner yalnızca menülerde, geçiş reklamı yalnızca oyun aralarında, ödüllü video isteğe bağlı
    // =====================================================================

    /** Banner'ın görüneceği ekranlar: oyun oynanırken asla. */
    private fun bannerScreen() =
        (state == State.HUB || state == State.MENU || state == State.LEVELS || state == State.MARKET) && !adShowing

    private fun updateBanner() {
        val a = ads ?: return
        val want = bannerScreen()
        if (want != bannerOn) {
            bannerOn = want
            a.setBannerVisible(want)
        }
        bannerReserve = if (want && scale > 0f) a.bannerHeightPx / scale else 0f
    }

    /**
     * Oyun arası geçişlerde (oyun bitti / bölüm sonu ekranından çıkarken) bazen geçiş reklamı gösterir,
     * sonra [then] ile devam eder. Sıklık sınırları: en az 3 oyunda bir, araya en az 150 sn,
     * uygulama açıldıktan sonraki ilk 90 sn ve ödüllü videodan sonraki 60 sn hiç yok.
     */
    private fun afterBreak(then: () -> Unit) {
        val a = ads
        val now = SystemClock.elapsedRealtime()
        val allowed = a != null && !isMpView() &&
            runsSinceAd >= 3 &&
            now - lastAdAt >= 150_000L &&
            now - appStartAt >= 90_000L &&
            (lastRewardAt < 0L || now - lastRewardAt >= 60_000L) &&
            a.interstitialReady()
        if (allowed && a!!.showInterstitial {
                adShowing = false
                lastAdAt = SystemClock.elapsedRealtime()
                then()
            }) {
            adShowing = true
            runsSinceAd = 0
            lastAdAt = now
        } else {
            then()
        }
    }

    /** Bu ekranda sunulabilecek ödüllü video (video hazır değilse NONE). */
    private fun currentReward(): Reward {
        val a = ads ?: return Reward.NONE
        if (adShowing || isMpView()) return Reward.NONE
        val r = when (state) {
            State.READY -> if (levelMode && !lifeSpent && lives.count == 0) Reward.LIFE else Reward.NONE
            State.OVER -> when {
                rewardUsed -> Reward.NONE
                levelMode && lives.count < Lives.MAX -> Reward.LIFE
                !levelMode && runCoins > 0 -> Reward.DOUBLE_RUN
                else -> Reward.NONE
            }
            State.LEVEL_DONE -> if (!rewardUsed && levelBonus + runCoins > 0) Reward.DOUBLE_LEVEL else Reward.NONE
            else -> Reward.NONE
        }
        return if (r != Reward.NONE && a.rewardedReady()) r else Reward.NONE
    }

    private fun watchReward() {
        val a = ads ?: return
        val kind = currentReward()
        if (kind == Reward.NONE) return
        sound.play(SoundManager.Sfx.CLICK)
        val started = a.showRewarded(
            onReward = {
                lastRewardAt = SystemClock.elapsedRealtime()
                when (kind) {
                    Reward.LIFE -> {
                        lives.refund()
                        marketMsg = strGotLife
                    }
                    Reward.DOUBLE_LEVEL -> {
                        val extra = levelBonus + runCoins
                        coins += extra
                        saveCoins()
                        marketMsg = String.format(strGotCoins, extra)
                    }
                    Reward.DOUBLE_RUN -> {
                        coins += runCoins
                        saveCoins()
                        marketMsg = String.format(strGotCoins, runCoins)
                    }
                    Reward.NONE -> Unit
                }
                if (kind != Reward.LIFE || state == State.OVER) rewardUsed = true
                marketMsgT = 2f
                sound.play(SoundManager.Sfx.RECORD)
            },
            onDone = { adShowing = false }
        )
        if (started) adShowing = true
    }

    /** Ödüllü video düğmesi: oynat simgesi + metin, yeşil. */
    private fun drawRewardButton(cv: Canvas, cx: Float, top: Float, alpha: Int) {
        val kind = currentReward()
        if (kind == Reward.NONE) {
            rewardBtn.setEmpty()
            return
        }
        val text = when (kind) {
            Reward.LIFE -> strRewardLife
            Reward.DOUBLE_LEVEL -> strRewardDouble
            else -> strRewardCoins
        }
        val w = min(uiW - 60f, 340f)
        rewardBtn.set(cx - w / 2f, top, cx + w / 2f, top + 56f)
        val pulse = 1f + 0.03f * sin(time * 5f)
        cv.save()
        cv.scale(pulse, pulse, rewardBtn.centerX(), rewardBtn.centerY())
        drawButton(cv, rewardBtn, "     $text", colGreen, colDarkText, true, alpha, 17f)
        // Oynat üçgeni
        val tx = rewardBtn.left + 30f
        val ty = rewardBtn.centerY()
        paint.color = withAlpha(colDarkText, alpha)
        iconPath.reset()
        iconPath.moveTo(tx - 7f, ty - 10f)
        iconPath.lineTo(tx + 10f, ty)
        iconPath.lineTo(tx - 7f, ty + 10f)
        iconPath.close()
        cv.drawPath(iconPath, paint)
        cv.restore()
    }

    // --- Çizim ---

    private val heartPath = Path().apply {
        // Birim kalp: merkez (0, 0), genişlik ~2
        moveTo(0f, 0.85f)
        cubicTo(-1.3f, -0.05f, -1.05f, -1.1f, -0.5f, -1.05f)
        cubicTo(-0.2f, -1.02f, -0.05f, -0.8f, 0f, -0.6f)
        cubicTo(0.05f, -0.8f, 0.2f, -1.02f, 0.5f, -1.05f)
        cubicTo(1.05f, -1.1f, 1.3f, -0.05f, 0f, 0.85f)
        close()
    }

    private fun drawHeart(cv: Canvas, x: Float, y: Float, r: Float, filled: Boolean) {
        cv.save()
        cv.translate(x, y + r * 0.1f)
        cv.scale(r, r)
        if (filled) {
            paint.color = hex(0xFFFF4D6D)
            cv.drawPath(heartPath, paint)
            paint.color = withAlpha(Color.WHITE, 150)
            cv.drawCircle(-0.45f, -0.5f, 0.2f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.16f
            paint.color = withAlpha(colDim, 170)
            cv.drawPath(heartPath, paint)
            paint.style = Paint.Style.FILL
        }
        cv.restore()
    }

    /**
     * Can kutucuğu: 4 kalp, dolmuyorsa sıradaki cana kalan süre.
     * [anchor] -1 sol kenar, 0 orta, 1 sağ kenar hizası.
     */
    private fun drawLivesPill(cv: Canvas, x: Float, y: Float, anchor: Int) {
        val r = 10f
        val full = lives.count >= Lives.MAX
        rightPaint.textSize = 17f
        val timer = if (full) "" else lives.nextText()
        val tw = if (full) 0f else rightPaint.measureText(timer) + 10f
        val w = 18f + Lives.MAX * 24f + tw + 4f
        val left = when (anchor) { -1 -> x; 0 -> x - w / 2f; else -> x - w }
        tmpRect.set(left, y, left + w, y + 40f)
        paint.color = withAlpha(colPanel, 220)
        cv.drawRoundRect(tmpRect, 20f, 20f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(hex(0xFFFF4D6D), 150)
        cv.drawRoundRect(tmpRect, 20f, 20f, paint)
        paint.style = Paint.Style.FILL
        for (i in 0 until Lives.MAX) drawHeart(cv, left + 21f + i * 24f, y + 20f, r, i < lives.count)
        if (!full) {
            rightPaint.color = colText
            cv.drawText(timer, left + w - 12f, y + 26f, rightPaint)
        }
    }

    private fun drawCoinIcon(cv: Canvas, x: Float, y: Float, r: Float) {
        paint.color = hex(0xFFC8901F)
        cv.drawCircle(x, y, r, paint)
        paint.color = colGold
        cv.drawCircle(x, y, r * 0.8f, paint)
        paint.color = hex(0xFFFFF4C2)
        cv.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.25f, paint)
    }

    /** Para kutucuğu. [right] ise sağ kenarı x'e hizalanır. Dokunma alanı [coinPillRect]'e yazılır. */
    private fun drawCoinPill(cv: Canvas, x: Float, y: Float, amount: Int, right: Boolean) {
        rightPaint.textSize = 22f
        val txt = amount.toString()
        val w = rightPaint.measureText(txt) + 62f
        val left = if (right) x - w else x
        coinPillRect.set(left, y, left + w, y + 46f)
        paint.color = withAlpha(colPanel, 220)
        cv.drawRoundRect(coinPillRect, 23f, 23f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colGold, 170)
        cv.drawRoundRect(coinPillRect, 23f, 23f, paint)
        paint.style = Paint.Style.FILL
        drawCoinIcon(cv, left + 24f, y + 23f, 12f)
        rightPaint.color = colText
        cv.drawText(txt, left + w - 16f, y + 31f, rightPaint)
    }

    private fun drawHub(cv: Canvas, cx: Float) {
        layoutHub()
        val bob = sin(time * 1.6f) * 3f
        drawCoinPill(cv, 22f, 28f, coins, right = false)
        drawLivesPill(cv, 22f, 84f, -1)
        drawSoundButton(cv, 255)
        titlePaint.textSize = 58f
        drawShadowText(cv, strTitle1, cx, 176f + bob, titlePaint, colText)
        drawShadowText(cv, strTitle2, cx, 234f + bob, titlePaint, colWarm)

        // Seçili araç süzülür
        val boostV = (0.5f + 0.5f * sin(time * 3f)).pow(3)
        drawSkinPreview(cv, skin, cx, 345f + sin(time * 2f) * 8f, 2.4f, boostV)

        labelPaint.textSize = 19f
        labelPaint.color = colGold
        val next = (levelMax + 1).coerceAtMost(Levels.COUNT)
        drawFit(cv, String.format(strLevelProgress, next, Levels.COUNT), cx, 446f, labelPaint)

        drawPulseButton(cv, hubLevelsBtn, strLevels)
        drawButton(cv, hubEndlessBtn, strEndless, colWarm, colText, false, 255, 20f)
        drawButton(cv, hubMultiBtn, strTwoPlayers, colBlue, colText, false, 255, 20f)
        drawButton(cv, hubMarketBtn, strMarket, colGold, colText, false, 255, 20f)

        textPaint.textSize = 19f
        textPaint.color = colDim
        drawFit(cv, strHubHint, cx, 870f, textPaint)
        if (ads?.privacyOptionsRequired() == true) {
            labelPaint.textSize = 14f
            labelPaint.color = colDim
            val tw = labelPaint.measureText(strPrivacy)
            privacyRect.set(cx - tw / 2f - 10f, 886f, cx + tw / 2f + 10f, 916f)
            cv.drawText(strPrivacy, cx, 906f, labelPaint)
        } else {
            privacyRect.setEmpty()
        }
    }

    private fun drawScreenHeader(cv: Canvas, cx: Float, title: String) {
        paint.color = withAlpha(hex(0xFF05071A), 200)
        cv.drawRect(0f, 0f, uiW, LEVEL_LIST_TOP, paint)
        drawCircleIcon(cv, backCircle, "‹")
        drawCoinPill(cv, uiW - 22f, 28f, coins, right = true)
        titlePaint.textSize = 36f
        titlePaint.color = colWarm
        // Başlık, soldaki geri düğmesiyle sağdaki para kutusunun arasına sığsın
        val room = 2f * min(cx - backCircle.right - 10f, coinPillRect.left - 10f - cx)
        drawFitW(cv, title, cx, 66f, titlePaint, max(80f, room))
    }

    private fun drawLevels(cv: Canvas, cx: Float) {
        val sp = (uiW - 100f) / Levels.PER_MAP
        val r = min(28f, sp * 0.38f)
        val blocks = Levels.COUNT / Levels.PER_MAP
        cv.save()
        cv.clipRect(0f, LEVEL_LIST_TOP, uiW, WORLD_H - bannerReserve)
        val first = (levelScroll / LEVEL_ROW_H).toInt().coerceAtLeast(0)
        val last = ((levelScroll + WORLD_H) / LEVEL_ROW_H).toInt().coerceAtMost(blocks - 1)
        for (b in first..last) {
            val y0 = LEVEL_LIST_TOP + b * LEVEL_ROW_H - levelScroll
            val m = Maps.ALL[b % Levels.MAPS]
            val round = b / Levels.MAPS
            tmpRect.set(16f, y0 + 6f, uiW - 16f, y0 + LEVEL_ROW_H - 6f)
            paint.color = withAlpha(colPanel, 215)
            cv.drawRoundRect(tmpRect, 22f, 22f, paint)
            paint.color = m.accent
            cv.drawRoundRect(16f, y0 + 6f, 24f, y0 + LEVEL_ROW_H - 6f, 4f, 4f, paint)

            helpPaint.textSize = 21f
            helpPaint.color = colText
            cv.drawText(mapNames[b % Levels.MAPS], 38f, y0 + 42f, helpPaint)
            rightPaint.textSize = 15f
            rightPaint.color = colDim
            cv.drawText(String.format(strRound, round + 1), uiW - 34f, y0 + 40f, rightPaint)

            for (i in 0 until Levels.PER_MAP) {
                val n = b * Levels.PER_MAP + i + 1
                val ccx = 50f + sp * (i + 0.5f)
                val ccy = y0 + 100f
                val passed = n <= levelMax
                val current = n == levelMax + 1
                when {
                    passed -> {
                        paint.color = colGold
                        cv.drawCircle(ccx, ccy, r, paint)
                    }
                    current -> {
                        val pr = r * (1f + 0.07f * sin(time * 5f))
                        paint.color = withAlpha(m.accent, 90)
                        cv.drawCircle(ccx, ccy, pr * 1.3f, paint)
                        paint.color = m.accent
                        cv.drawCircle(ccx, ccy, pr, paint)
                    }
                    else -> {
                        paint.color = hex(0xFF0C1030)
                        cv.drawCircle(ccx, ccy, r, paint)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        paint.color = withAlpha(colDim, 90)
                        cv.drawCircle(ccx, ccy, r, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
                if (i == Levels.PER_MAP - 1) {
                    // Haritanın finali: çift halka
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.5f
                    paint.color = if (passed) colGold else withAlpha(colAccent, 200)
                    cv.drawCircle(ccx, ccy, r + 6f, paint)
                    paint.style = Paint.Style.FILL
                }
                labelPaint.textSize = 17f
                labelPaint.color = when {
                    passed -> colDarkText
                    current -> Color.WHITE
                    else -> withAlpha(colDim, 150)
                }
                drawFitW(cv, n.toString(), ccx, ccy + 6f, labelPaint, r * 1.8f)
            }
        }
        cv.restore()

        drawScreenHeader(cv, cx, strLevels)
        drawLivesPill(cv, cx, 92f, 0)
        drawToast(cv, cx)
    }

    private fun drawToast(cv: Canvas, cx: Float) {
        if (marketMsgT <= 0f || marketMsg.isEmpty()) return
        val a = (marketMsgT / 0.3f).coerceIn(0f, 1f)
        textPaint.textSize = 20f
        val tw = min(textPaint.measureText(marketMsg) + 48f, uiW - 24f)
        tmpRect.set(cx - tw / 2f, 470f, cx + tw / 2f, 524f)
        paint.color = withAlpha(hex(0xFF05071A), (235 * a).toInt())
        cv.drawRoundRect(tmpRect, 27f, 27f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colGold, (200 * a).toInt())
        cv.drawRoundRect(tmpRect, 27f, 27f, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = withAlpha(colText, (255 * a).toInt())
        drawFitW(cv, marketMsg, cx, 504f, textPaint, tw - 24f)
    }

    private fun drawMarket(cv: Canvas, cx: Float) {
        layoutHub()
        for (i in marketCards.indices) {
            val sk = Skin.entries[i]
            val r = marketCards[i]
            val owned = sk in ownedSkins
            val selected = sk == skin
            paint.color = withAlpha(colPanel, 225)
            cv.drawRoundRect(r, 20f, 20f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (selected) 3.5f else 2f
            paint.color = when {
                selected -> colGreen
                owned -> withAlpha(colPanelEdge, 150)
                else -> withAlpha(colGold, 110)
            }
            cv.drawRoundRect(r, 20f, 20f, paint)
            paint.style = Paint.Style.FILL

            val boostV = (0.5f + 0.5f * sin(time * 3f + i)).pow(3)
            cv.save()
            cv.clipRect(r)
            val ck = (r.height() / 158f).coerceAtMost(1f)
            drawSkinPreview(cv, sk, r.centerX() + 6f, r.top + 62f * ck + sin(time * 2f + i) * 3f, 1.75f * ck, boostV)
            cv.restore()

            labelPaint.textSize = 15f
            labelPaint.color = colText
            drawFitW(cv, skinNames[i], r.centerX(), r.top + 116f * ck, labelPaint, r.width() - 16f)
            when {
                selected -> {
                    labelPaint.textSize = 15f
                    labelPaint.color = colGreen
                    cv.drawText(strSelected, r.centerX(), r.top + 144f * ck, labelPaint)
                }
                owned -> {
                    labelPaint.textSize = 15f
                    labelPaint.color = colDim
                    cv.drawText(strSelect, r.centerX(), r.top + 144f * ck, labelPaint)
                }
                else -> {
                    rightPaint.textSize = 19f
                    val txt = sk.price.toString()
                    val tw = rightPaint.measureText(txt) + 26f
                    val x0 = r.centerX() - tw / 2f
                    drawCoinIcon(cv, x0 + 9f, r.top + 138f * ck, 9f)
                    rightPaint.color = if (coins >= sk.price) colGold else colDim
                    cv.drawText(txt, x0 + tw, r.top + 145f * ck, rightPaint)
                }
            }
        }
        drawScreenHeader(cv, cx, strMarket)
        drawToast(cv, cx)
    }

    /** Bölüm başlamadan önce: numara, hedef ve bu bölümün özellikleri. */
    private fun drawLevelIntro(cv: Canvas, cx: Float) {
        val lv = level ?: return
        val bob = sin(time * 1.6f) * 3f
        if (lv.isFinalOfMap) {
            labelPaint.textSize = 16f
            val t = strMapFinal
            val tw = labelPaint.measureText(t) + 36f
            tmpRect.set(cx - tw / 2f, 82f, cx + tw / 2f, 114f)
            drawButton(cv, tmpRect, t, colAccent, Color.WHITE, true, 255, 16f)
        }
        titlePaint.textSize = 54f
        drawShadowText(cv, String.format(strLevelTitle, lv.number), cx, 168f + bob, titlePaint, colText)
        labelPaint.textSize = 18f
        labelPaint.color = map.accent
        drawFit(cv, mapNames[mapIndex] + "  ·  " + String.format(strRound, lv.round + 1), cx, 206f, labelPaint)

        val tt = String.format(strTarget, lv.target)
        labelPaint.textSize = 20f
        val tw = labelPaint.measureText(tt) + 48f
        tmpRect.set(cx - tw / 2f, 224f, cx + tw / 2f, 268f)
        drawButton(cv, tmpRect, tt, colGold, colDarkText, true, 255, 20f)

        var y = 312f
        if (lv.mods.isEmpty()) {
            textPaint.textSize = 19f
            textPaint.color = colDim
            drawFit(cv, strPractice, cx, y, textPaint)
            y += 40f
        } else {
            for (m in lv.mods) {
                val name = modNames[m.ordinal]
                textPaint.textSize = 19f
                val w = min(textPaint.measureText(name) + 44f, uiW - 40f)
                tmpRect.set(cx - w / 2f, y - 26f, cx + w / 2f, y + 10f)
                paint.color = withAlpha(colPanel, 220)
                cv.drawRoundRect(tmpRect, 18f, 18f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = m.color
                cv.drawRoundRect(tmpRect, 18f, 18f, paint)
                paint.style = Paint.Style.FILL
                textPaint.color = m.color
                drawFitW(cv, name, cx, y - 1f, textPaint, w - 20f)
                y += 46f
            }
        }

        val pulse = 0.5f + 0.5f * sin(time * 4f)
        textPaint.textSize = 30f
        textPaint.color = withAlpha(colText, (140 + 115 * pulse).toInt())
        if (lives.count > 0) {
            drawFit(cv, strTapToStart, cx, 690f, textPaint)
        } else {
            textPaint.textSize = 24f
            textPaint.color = hex(0xFFFF4D6D)
            drawFit(cv, String.format(strNoLives, lives.nextText()), cx, 690f, textPaint)
        }
        textPaint.textSize = 20f
        textPaint.color = map.accent
        drawFit(cv, mapTips[mapIndex], cx, 732f, textPaint)
        labelPaint.textSize = 18f
        labelPaint.color = colGold
        val first = lv.number > levelMax
        drawFit(cv, if (first) String.format(strReward, lv.reward) else strReplay, cx, 800f, labelPaint)
        labelPaint.textSize = 15f
        labelPaint.color = colDim
        drawFit(cv, strLifeRule, cx, 836f, labelPaint)
        drawLivesPill(cv, uiW - 16f, 24f, 1)
        drawRewardButton(cv, cx, 596f, 255)
        if (Mech.WIND in mechs) drawWindGauge(cv)
        drawToast(cv, cx)
    }

    /** Bölüm sırasında: puan / hedef ve ilerleme çubuğu. */
    private fun drawLevelHud(cv: Canvas, cx: Float) {
        val lv = level ?: return
        titlePaint.textSize = 76f * (1f + 0.15f * scorePulse)
        drawShadowText(cv, "$score/${lv.target}", cx, 132f, titlePaint, colText)
        val bw = min(uiW - 150f, 300f)
        val k = (score / lv.target.toFloat()).coerceIn(0f, 1f)
        tmpRect.set(cx - bw / 2f, 152f, cx + bw / 2f, 164f)
        paint.color = withAlpha(colPanel, 220)
        cv.drawRoundRect(tmpRect, 6f, 6f, paint)
        tmpRect.set(cx - bw / 2f, 152f, cx - bw / 2f + bw * k, 164f)
        paint.color = map.accent
        cv.drawRoundRect(tmpRect, 6f, 6f, paint)
        labelPaint.textSize = 15f
        labelPaint.color = colDim
        drawFit(cv, String.format(strLevelTitle, lv.number), cx, 192f, labelPaint)
    }

    /** Toplanan para: sağ üstte küçük kutu. */
    private fun drawRunCoins(cv: Canvas) {
        if (coinChance <= 0f) return
        rightPaint.textSize = 20f
        val txt = runCoins.toString()
        val w = rightPaint.measureText(txt) + 54f
        tmpRect.set(uiW - 18f - w, 30f, uiW - 18f, 72f)
        paint.color = withAlpha(colPanel, 200)
        cv.drawRoundRect(tmpRect, 21f, 21f, paint)
        drawCoinIcon(cv, tmpRect.left + 21f, 51f, 11f)
        rightPaint.color = colText
        cv.drawText(txt, tmpRect.right - 14f, 58f, rightPaint)
    }

    private fun drawLevelDone(cv: Canvas, cx: Float) {
        val lv = level ?: return
        val t = (stateTimer / 0.35f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t).pow(3)
        val a = (ease * 255).toInt()
        val top = 330f + (1f - ease) * 70f
        val panelW = min(uiW - 48f, 360f)
        val panelH = 280f
        val all = lv.number >= Levels.COUNT

        val title = if (all) strAllDone else strLevelDone
        titlePaint.textSize = 52f
        titlePaint.color = withAlpha(Color.BLACK, (a * 0.4f).toInt())
        drawFit(cv, title, cx, top - 29f, titlePaint)
        titlePaint.color = withAlpha(colGold, a)
        drawFit(cv, title, cx, top - 34f, titlePaint)

        tmpRect.set(cx - panelW / 2f, top, cx + panelW / 2f, top + panelH)
        paint.color = withAlpha(colPanel, (a * 0.9f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = withAlpha(colGold, (a * 0.6f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        labelPaint.textSize = 19f
        labelPaint.color = withAlpha(colDim, a)
        cv.drawText(String.format(strLevelTitle, lv.number), cx, top + 48f, labelPaint)
        titlePaint.textSize = 76f
        titlePaint.color = withAlpha(colText, a)
        cv.drawText("$score/${lv.target}", cx, top + 128f, titlePaint)
        paint.color = withAlpha(colPanelEdge, (a * 0.25f).toInt())
        cv.drawRect(cx - panelW / 2f + 32f, top + 156f, cx + panelW / 2f - 32f, top + 158f, paint)

        labelPaint.color = withAlpha(colDim, a)
        cv.drawText(strRewardLabel, cx, top + 192f, labelPaint)
        rightPaint.textSize = 40f
        val txt = "+$levelBonus"
        val tw = rightPaint.measureText(txt) + 40f
        drawCoinIcon(cv, cx - tw / 2f + 14f, top + 228f, 14f)
        rightPaint.color = withAlpha(colGold, a)
        cv.drawText(txt, cx + tw / 2f, top + 242f, rightPaint)
        labelPaint.textSize = 15f
        labelPaint.color = withAlpha(colDim, a)
        cv.drawText(String.format(strCoinsCollected, runCoins), cx, top + 270f, labelPaint)

        val ba = (((stateTimer - 0.45f) / 0.25f).coerceIn(0f, 1f) * 255).toInt()
        if (ba > 0) {
            if (!all) drawButton(cv, retryBtn, strNext, colGold, colDarkText, true, ba, 22f)
            drawButton(cv, menuBtn, strLevels, colPanelEdge, colText, false, ba, 20f)
            if (all) {
                textPaint.textSize = 20f
                textPaint.color = withAlpha(colGold, ba)
                drawFit(cv, strAllDoneDesc, cx, top + panelH + 40f, textPaint)
            }
            drawRewardButton(cv, cx, retryBtn.bottom + 26f, ba)
        }
        drawToast(cv, cx)
    }

    // =====================================================================
    // Açılış: Magic Lumos Software logosu
    // =====================================================================
    //
    // Roket karanlık uzaydan geçer, ışığı logoyu soldan sağa aydınlatarak ortaya çıkarır ("Lumos").
    // Ekrandan çıkınca logoda ışık patlaması olur, üstünden parıltı geçer, altında oyunun adı belirir.
    // Dokununca atlanır. Yalnızca uygulama ilk açıldığında oynar.

    private class Spark {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var max = 1f; var size = 1f; var color = 0
    }

    private var splashT = 0f
    private var logoBmp: Bitmap? = null
    private val sparks = ArrayList<Spark>()
    private var splashBurst = false
    private var splashWhoosh = false
    private var hubFade = 0f
    private val logoRect = RectF()
    private val logoSrc = android.graphics.Rect()
    // Parıltı: koyu zemini sıfıra indirip harfleri parlatan renk süzgeci (yalnızca parlak yerler ışır)
    private val shineFilter = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix(
            floatArrayOf(
                2.4f, 0f, 0f, 0f, -95f,
                0f, 2.4f, 0f, 0f, -95f,
                0f, 0f, 2.4f, 0f, -95f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val splashBgTop = hex(0xFF0B223B)
    private val splashBgBottom = hex(0xFF07182B)

    private fun loadLogo(): Bitmap? {
        if (logoBmp == null) {
            logoBmp = runCatching {
                BitmapFactory.decodeResource(resources, R.drawable.splash_logo, BitmapFactory.Options().apply { inScaled = false })
            }.getOrNull()
        }
        return logoBmp
    }

    private fun easeInOut(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /** Roketin piksel konumu (logo merkez çizgisinin biraz üstünden, hafif yay çizerek geçer). */
    private fun splashRocketX(): Float {
        val w = width.toFloat()
        val k = easeInOut((splashT - SPL_ROCKET_START) / (SPL_ROCKET_END - SPL_ROCKET_START))
        return -0.25f * w + k * 1.5f * w
    }

    private fun splashRocketY(): Float {
        val k = ((splashT - SPL_ROCKET_START) / (SPL_ROCKET_END - SPL_ROCKET_START)).coerceIn(0f, 1f)
        return logoRect.centerY() - logoRect.height() * 0.05f - sin(k * PI.toFloat()) * logoRect.height() * 0.08f
    }

    private fun updateSplash(dt: Float) {
        splashT += dt
        val w = width.toFloat()
        val inFlight = splashT in SPL_ROCKET_START..SPL_ROCKET_END
        if (inFlight && !splashWhoosh) {
            splashWhoosh = true
            sound.play(SoundManager.Sfx.SWIPE)
        }
        // Motor kıvılcımları
        if (inFlight) {
            val rx = splashRocketX()
            val ry = splashRocketY()
            val s = w / 330f
            repeat(3) {
                if (sparks.size < 400) sparks.add(Spark().apply {
                    x = rx - 24f * s
                    y = ry + rnd(-5f, 5f) * s
                    vx = -rnd(120f, 320f) * s
                    vy = rnd(-60f, 60f) * s
                    life = rnd(0.3f, 0.7f); max = life
                    size = rnd(2f, 5f) * s * 0.5f
                    color = if (Random.nextInt(3) == 0) hex(0xFFFFFFFF) else if (Random.nextBoolean()) hex(0xFFFFB347) else hex(0xFFFFE08A)
                })
            }
        }
        // Işık patlaması: roket logoyu tamamen aydınlatıp çıkınca
        if (!splashBurst && splashT >= SPL_ROCKET_END) {
            splashBurst = true
            sound.play(SoundManager.Sfx.START)
            val cx = logoRect.centerX()
            val cy = logoRect.centerY()
            repeat(120) {
                val a = rnd(0f, TWO_PI)
                val sp = rnd(80f, 620f) * (w / 1080f)
                sparks.add(Spark().apply {
                    x = cx + rnd(-0.3f, 0.3f) * logoRect.width()
                    y = cy + rnd(-0.12f, 0.12f) * logoRect.height()
                    vx = cos(a) * sp
                    vy = sin(a) * sp
                    life = rnd(0.6f, 1.5f); max = life
                    size = rnd(2f, 6f) * (w / 1080f)
                    color = when (Random.nextInt(4)) {
                        0 -> hex(0xFFE8D5A0)   // logonun altın tonu
                        1 -> hex(0xFF8FC8FF)   // logonun mavi tonu
                        2 -> Color.WHITE
                        else -> hex(0xFF5FB8FF)
                    }
                })
            }
        }
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            val d = exp(-2.2f * dt)
            p.vx *= d
            p.vy *= d
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        if (splashT >= SPL_END) finishSplash()
    }

    private fun finishSplash() {
        state = State.HUB
        hubFade = 0.5f
        sparks.clear()
        logoBmp?.recycle()
        logoBmp = null
    }

    private fun drawSplash(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bmp = loadLogo()

        // Arka plan: logonun kenar renkleriyle aynı lacivert
        splashPaint.shader = LinearGradient(0f, 0f, 0f, h, splashBgTop, splashBgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, splashPaint)
        splashPaint.shader = null

        // Uzak yıldızlar (oyunun yıldızlarıyla aynı)
        canvas.save()
        canvas.scale(scale, scale)
        drawStars(canvas)
        canvas.restore()

        // Resmin kenarlarındaki boşluk kırpılır, logo yazısı ekranda büyük görünür
        if (bmp != null) logoSrc.set(
            (bmp.width * 0.16f).toInt(), (bmp.height * 0.2f).toInt(),
            (bmp.width * 0.84f).toInt(), (bmp.height * 0.8f).toInt()
        )
        val lw = w
        val lh = if (bmp != null) lw * logoSrc.height() / logoSrc.width() else lw * 0.56f
        val top = h * 0.42f - lh / 2f
        logoRect.set(0f, top, lw, top + lh)

        val rx = splashRocketX()
        val ry = splashRocketY()
        val revealX = if (splashT >= SPL_ROCKET_END) w * 2f else rx

        if (bmp != null && revealX > 0f) {
            // Logo yalnızca roketin geçtiği yere kadar görünür; ışık kenarı yumuşaktır
            val layer = canvas.saveLayer(logoRect, null)
            splashPaint.alpha = 255
            canvas.drawBitmap(bmp, logoSrc, logoRect, splashPaint)
            splashPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            val soft = w * 0.28f
            splashPaint.shader = LinearGradient(
                revealX - soft, 0f, revealX + w * 0.02f, 0f,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(logoRect, splashPaint)
            // Üst ve alt kenarları arka plana karıştır
            splashPaint.shader = LinearGradient(
                0f, logoRect.top, 0f, logoRect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.24f, 0.76f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(logoRect, splashPaint)
            splashPaint.shader = null
            splashPaint.xfermode = null
            canvas.restoreToCount(layer)

            // Parıltı: logonun üstünden çapraz bir ışık bandı geçer; yalnızca harfler ve devre çizgileri ışır
            val sk = (splashT - SPL_SHINE_START) / 0.8f
            if (sk in 0f..1f) {
                val bx = -0.3f * w + sk * 1.6f * w
                val addPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
                val l2 = canvas.saveLayer(logoRect, addPaint)
                splashPaint.colorFilter = shineFilter
                canvas.drawBitmap(bmp, logoSrc, logoRect, splashPaint)
                splashPaint.colorFilter = null
                splashPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                splashPaint.shader = LinearGradient(
                    bx - w * 0.14f, logoRect.top, bx + w * 0.14f, logoRect.bottom,
                    intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 210), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(logoRect, splashPaint)
                splashPaint.shader = null
                splashPaint.xfermode = null
                canvas.restoreToCount(l2)
            }
        }

        // Roketin ışığı ve roket
        if (splashT in SPL_ROCKET_START..SPL_ROCKET_END) {
            val glowR = w * 0.3f
            splashPaint.shader = RadialGradient(
                rx, ry, glowR,
                intArrayOf(withAlpha(hex(0xFFFFF2C8), 120), withAlpha(hex(0xFF8FC8FF), 40), 0),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(rx, ry, glowR, splashPaint)
            splashPaint.shader = null
        }
        for (p in sparks) {
            val f = p.life / p.max
            paint.color = withAlpha(p.color, (f * 240).toInt())
            canvas.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * f), paint)
        }
        if (splashT in SPL_ROCKET_START..SPL_ROCKET_END) {
            val s = w / 330f
            canvas.save()
            canvas.translate(rx, ry)
            canvas.rotate(-6f + 10f * ((splashT - SPL_ROCKET_START) / (SPL_ROCKET_END - SPL_ROCKET_START)))
            canvas.scale(s, s)
            drawSkinBody(canvas, skin, 1f)
            canvas.restore()
        }

        // Işık patlaması: parlama ve genişleyen halka
        val bt = splashT - SPL_ROCKET_END
        if (bt in 0f..0.7f) {
            val k = bt / 0.7f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.012f * (1f - k)
            paint.color = withAlpha(hex(0xFFE8D5A0), ((1f - k) * 220).toInt())
            canvas.drawCircle(logoRect.centerX(), logoRect.centerY(), w * (0.08f + 0.6f * (1f - (1f - k).pow(3))), paint)
            paint.style = Paint.Style.FILL
            val fr = w * 0.75f
            splashPaint.shader = RadialGradient(
                logoRect.centerX(), logoRect.centerY(), fr,
                intArrayOf(withAlpha(Color.WHITE, ((1f - k).pow(2) * 170).toInt()), withAlpha(hex(0xFF8FC8FF), ((1f - k).pow(2) * 60).toInt()), 0),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(logoRect.centerX(), logoRect.centerY(), fr, splashPaint)
            splashPaint.shader = null
        }

        // Altta: oyunun adı
        canvas.save()
        canvas.scale(scale, scale)
        curW = uiW
        val cx = uiW / 2f
        val logoBottom = logoRect.bottom / scale
        val ta = ((splashT - SPL_TITLE_START) / 0.5f).coerceIn(0f, 1f)
        if (ta > 0f) {
            val rise = (1f - ta) * 20f
            titlePaint.textSize = 46f
            titlePaint.color = withAlpha(Color.BLACK, (ta * 100).toInt())
            drawFit(canvas, strTitle1 + " " + strTitle2, cx, logoBottom + 76f + rise, titlePaint)
            titlePaint.color = withAlpha(colWarm, (ta * 255).toInt())
            drawFit(canvas, strTitle1 + " " + strTitle2, cx, logoBottom + 72f + rise, titlePaint)
        }
        if (splashT > 0.8f && splashT < SPL_FADE_START) {
            textPaint.textSize = 16f
            textPaint.color = withAlpha(hex(0xFF6F8BB0), 160)
            drawFit(canvas, strTapToSkip, cx, 960f, textPaint)
        }
        canvas.restore()

        // Başta karanlıktan açılış, sonda karanlığa geçiş
        val fadeIn = (1f - splashT / 0.4f).coerceIn(0f, 1f)
        val fadeOut = ((splashT - SPL_FADE_START) / (SPL_END - SPL_FADE_START)).coerceIn(0f, 1f)
        val dark = max(fadeIn, fadeOut)
        if (dark > 0f) canvas.drawColor(withAlpha(hex(0xFF020814), (dark * 255).toInt()))
    }

    // --- Arayüz ---

    private fun drawHud(cv: Canvas) {
        val cx = uiW / 2f
        when (state) {
            State.SPLASH -> Unit
            State.HUB -> drawHub(cv, cx)
            State.LEVELS -> drawLevels(cv, cx)
            State.MARKET -> drawMarket(cv, cx)
            State.LEVEL_DONE -> drawLevelDone(cv, cx)
            State.MENU -> drawMenu(cv, cx)
            State.READY -> if (levelMode) drawLevelIntro(cv, cx) else drawReady(cv, cx)
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
            if (mp == 2) {
                drawRoster(cv, cx, 190f)
            } else {
                if (netMessage.isNotEmpty()) textPaint.color = colAccent
                drawFit(cv, if (netMessage.isNotEmpty()) netMessage else strMpPick, cx, 186f, textPaint)
            }
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
            drawViewToggle(cv, cx)
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
            drawViewToggle(cv, cx)
            return
        }

        // --- Başla ---
        drawPulseButton(cv, startBtn, strStart)
        drawCircleIcon(cv, backCircle, "‹")

        labelPaint.textSize = 19f
        labelPaint.color = colGold
        drawFit(cv, bestText, cx, multiBtn.centerY() + 8f, labelPaint)
    }

    /** Lobinin altında: bölünmüş ekran / tam ekran seçimi. */
    private fun drawViewToggle(cv: Canvas, cx: Float) {
        labelPaint.textSize = 15f
        labelPaint.color = colDim
        cv.drawText(strViewLabel, cx, viewBtns[0].top - 8f, labelPaint)
        drawButton(cv, viewBtns[0], strViewSplit, colGreen, if (!mpFull) colDarkText else colDim, !mpFull, 255, 16f)
        drawButton(cv, viewBtns[1], strViewFull, colGreen, if (mpFull) colDarkText else colDim, mpFull, 255, 16f)
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
        if (Mech.WIND in mechs) drawWindGauge(cv)

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
        if (levelMode) {
            drawLevelHud(cv, cx)
        } else {
            titlePaint.textSize = 92f * (1f + 0.18f * scorePulse)
            drawShadowText(cv, scoreText, cx, 150f, titlePaint, colText)
        }
        if (Mech.WIND in mechs) drawWindGauge(cv)
        drawRunCoins(cv)
    }

    /** Sol üstte rüzgârın yönünü ve gücünü gösteren ok. */
    private fun drawWindGauge(cv: Canvas, gx: Float = 52f, gy: Float = 74f) {
        val maxW = WIND_BASE * hazard()
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

        val lv = if (levelMode) level else null
        val title = if (lv != null) strLevelFailed else strGameOver
        titlePaint.textSize = 56f
        titlePaint.color = withAlpha(Color.BLACK, (a * 0.4f).toInt())
        drawFit(cv, title, cx, top - 29f, titlePaint)
        titlePaint.color = withAlpha(if (lv != null) colAccent else colWarm, a)
        drawFit(cv, title, cx, top - 34f, titlePaint)

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

        cv.drawText(if (lv != null) strTargetLabel else strBestLabel, cx, top + 212f, labelPaint)
        titlePaint.textSize = 44f
        titlePaint.color = withAlpha(colGold, a)
        cv.drawText(if (lv != null) lv.target.toString() else bestValueText, cx, top + 262f, titlePaint)

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
            drawButton(cv, menuBtn, if (lv != null) strLevels else strMenu, colPanelEdge, colText, false, ba, if (lv != null) 19f else 22f)
            drawSoundButton(cv, ba)

            labelPaint.textSize = 18f
            labelPaint.color = withAlpha(colDim, ba)
            val info = if (lv != null) String.format(strLevelTitle, lv.number) + "  ·  " + mapNames[mapIndex]
                else mapNames[mapIndex] + "  ·  " + diffNames[difficulty.ordinal]
            drawFit(cv, info, cx, retryBtn.bottom + 56f, labelPaint)
            if (lv != null) drawLivesPill(cv, cx, retryBtn.bottom + 122f, 0)
            if (runCoins > 0) {
                rightPaint.textSize = 22f
                val txt = "+$runCoins"
                val tw = rightPaint.measureText(txt) + 32f
                drawCoinIcon(cv, cx - tw / 2f + 11f, retryBtn.bottom + 92f, 11f)
                rightPaint.color = withAlpha(colGold, ba)
                cv.drawText(txt, cx + tw / 2f, retryBtn.bottom + 100f, rightPaint)
            }
            drawRewardButton(cv, cx, retryBtn.bottom + 172f, ba)
        }
        drawToast(cv, cx)
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

    // --- Çok oyuncu çizimi ---

    private fun drawMp(canvas: Canvas) {
        if (mpFull) {
            drawMpFull(canvas)
            return
        }
        for ((id, r) in mpCells) drawCell(canvas, id, r)

        // Hücreler arası çizgiler
        val w = width.toFloat()
        val h = height.toFloat()
        val n = mpCells.size
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
            paint.color = hex(0xFF0A0D26)
            canvas.drawRect(x0 - 4f, y0 - 4f, x1 + 4f, y1 + 4f, paint)
            paint.color = withAlpha(map.accent, 210)
            canvas.drawRect(x0 - 1.5f, y0 - 1.5f, x1 + 1.5f, y1 + 1.5f, paint)
        }
        line(0f, h / 2f, w, h / 2f)
        if (n >= 3) line(w / 2f, h / 2f, w / 2f, h)
        if (n >= 4) line(w / 2f, 0f, w / 2f, h / 2f)

        canvas.save()
        canvas.scale(scale, scale)
        curW = uiW
        if (state == State.MP_COUNTDOWN) drawCountdown(canvas)
        if (state == State.MP_OVER) drawMpOver(canvas)
        canvas.restore()

        if (flash > 0f) canvas.drawColor(withAlpha(Color.WHITE, (flash / 0.2f * 140).toInt()))
    }

    /** Bir oyuncunun hücresi: aynı parkur, o oyuncunun roketi, üstünde adı ve puanı. */
    private fun drawCell(canvas: Canvas, id: Int, r: RectF) {
        val cs = r.height() / WORLD_H
        canvas.save()
        canvas.clipRect(r)
        canvas.translate(r.left, r.top)
        canvas.scale(cs, cs)
        if (id == myId && shake > 0f) {
            val m = shake / 0.45f * 9f
            canvas.translate(rnd(-m, m), rnd(-m, m))
        }
        drawWorld(canvas, id)
        val cw = r.width() / cs
        curW = cw
        drawCellHud(canvas, id, cw)
        canvas.restore()
    }

    /** Paylaşılan dünyayı ve [id] oyuncusunun roketini çizer (dünya birimi). */
    private fun drawWorld(cv: Canvas, id: Int) {
        drawStars(cv)
        drawShootingStar(cv)
        drawMotes(cv)
        drawColumns(cv)
        drawFireballs(cv)
        drawParticles(cv, id)
        if (id == myId) {
            if (myAlive) drawRocket(cv)
            drawShockwave(cv)
        } else if (pAlive[id]) {
            drawRocket(cv, rocketX, pDispY[id], pAngle[id], pBoost[id], colorId = id)
        }
    }

    /** Hücrenin üstündeki isim, puan ve "yandı" katmanı (dünya birimi; [cw] hücre genişliği). */
    private fun drawCellHud(cv: Canvas, id: Int, cw: Float) {
        val cx = cw / 2f
        val me = id == myId
        val alive = isAlive(id)

        if (!alive) {
            paint.color = hex(0x8805071A)
            cv.drawRect(0f, 0f, cw, WORLD_H, paint)
        }

        val name = playerName(id)
        labelPaint.textSize = 32f
        val tw = min(labelPaint.measureText(name) + 56f, cw - 60f)
        tmpRect.set(30f, 30f, 30f + tw, 96f)
        drawButton(cv, tmpRect, name, playerColor(id), Color.WHITE, true, 235, 32f)

        titlePaint.textSize = 130f * (if (me) 1f + 0.18f * scorePulse else 1f)
        drawShadowText(cv, scoreOf(id).toString(), cx, 175f, titlePaint, colText)

        if (Mech.WIND in mechs && alive) drawWindGauge(cv, cw - 70f, 70f)

        if (state == State.MP_COUNTDOWN && me) {
            textPaint.textSize = 42f
            textPaint.color = map.accent
            drawFit(cv, mapTips[mapIndex], cx, 300f, textPaint)
        }

        if (!alive) {
            val label = when {
                me -> strYouCrashed
                pGone[id] -> strLeftGame
                else -> strCrashedShort
            }
            titlePaint.textSize = 120f
            drawShadowText(cv, label, cx, 540f, titlePaint, playerColor(id))
            if (me && state == State.MP_PLAYING && others().any { pAlive[it] }) {
                textPaint.textSize = 48f
                textPaint.color = colText
                drawFit(cv, if (others().size > 1) strWatchingMany else strWatching, cx, 640f, textPaint)
            }
        }
    }

    /** Tam ekran: kendi oyunun büyük, diğerlerinin puanı sağ üst köşede. */
    private fun drawMpFull(canvas: Canvas) {
        val id = shownId()
        canvas.save()
        canvas.scale(mpScale, mpScale)
        if (id == myId && shake > 0f) {
            val m = shake / 0.45f * 9f
            canvas.translate(rnd(-m, m), rnd(-m, m))
        }
        drawWorld(canvas, id)
        canvas.restore()

        canvas.save()
        canvas.scale(scale, scale)
        curW = uiW
        drawFullHud(canvas, id)
        if (state == State.MP_COUNTDOWN) drawCountdown(canvas)
        if (state == State.MP_OVER) drawMpOver(canvas)
        canvas.restore()

        if (flash > 0f) canvas.drawColor(withAlpha(Color.WHITE, (flash / 0.2f * 140).toInt()))
    }

    private fun drawFullHud(cv: Canvas, id: Int) {
        val cx = uiW / 2f
        val me = id == myId

        titlePaint.textSize = 92f * (if (me) 1f + 0.18f * scorePulse else 1f)
        drawShadowText(cv, scoreOf(id).toString(), cx, 150f, titlePaint, colText)
        if (!me) {
            val t = String.format(strWatchFormat, playerName(id))
            labelPaint.textSize = 19f
            val tw = min(labelPaint.measureText(t) + 40f, uiW - 190f)
            tmpRect.set(cx - tw / 2f, 178f, cx + tw / 2f, 216f)
            drawButton(cv, tmpRect, t, playerColor(id), Color.WHITE, true, 230, 19f)
            if (others().count { pAlive[it] } > 1) {
                textPaint.textSize = 18f
                textPaint.color = colDim
                drawFit(cv, strTapToSwitch, cx, 246f, textPaint)
            }
        }
        if (Mech.WIND in mechs && state != State.MP_OVER) drawWindGauge(cv)
        if (state != State.MP_OVER) drawScoreBoard(cv, players.filter { it != id })

        if (state == State.MP_COUNTDOWN) {
            textPaint.textSize = 22f
            textPaint.color = map.accent
            drawFit(cv, mapTips[mapIndex], cx, 330f, textPaint)
        }
        if (me && !myAlive && state == State.MP_PLAYING) {
            titlePaint.textSize = 64f
            drawShadowText(cv, strYouCrashed, cx, 470f, titlePaint, colAccent)
            if (others().any { pAlive[it] }) {
                textPaint.textSize = 24f
                textPaint.color = colText
                drawFit(cv, if (others().size > 1) strWatchingMany else strWatching, cx, 520f, textPaint)
            }
        }
    }

    /** Sağ üst köşe: diğer oyuncuların puanı ve durumu. */
    private fun drawScoreBoard(cv: Canvas, ids: List<Int>) {
        if (ids.isEmpty()) return
        val boxW = if (ids.size == 1) 128f else 160f
        val right = uiW - 16f
        val left = right - boxW
        if (ids.size == 1) {
            val id = ids[0]
            tmpRect.set(left, 22f, right, 128f)
            paint.color = withAlpha(colPanel, 220)
            cv.drawRoundRect(tmpRect, 18f, 18f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = playerColor(id)
            cv.drawRoundRect(tmpRect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            val bx = tmpRect.centerX()
            labelPaint.textSize = 15f
            labelPaint.color = playerColor(id)
            drawFitW(cv, playerName(id), bx, 48f, labelPaint, boxW - 16f)
            titlePaint.textSize = 44f
            titlePaint.color = colText
            cv.drawText(scoreOf(id).toString(), bx, 95f, titlePaint)
            labelPaint.textSize = 13f
            labelPaint.color = if (isAlive(id)) colGreen else colAccent
            cv.drawText(statusText(id), bx, 117f, labelPaint)
            return
        }
        val rowH = 38f
        val top = 22f
        tmpRect.set(left, top, right, top + 14f + rowH * ids.size)
        paint.color = withAlpha(colPanel, 220)
        cv.drawRoundRect(tmpRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, 150)
        cv.drawRoundRect(tmpRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        ids.forEachIndexed { i, id ->
            val cy = top + 7f + rowH * i + rowH / 2f
            val alive = isAlive(id)
            // Renk noktası: oynuyorsa dolu, yandıysa halka
            paint.color = playerColor(id)
            if (alive) {
                cv.drawCircle(left + 18f, cy, 7f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                cv.drawCircle(left + 18f, cy, 6f, paint)
                paint.style = Paint.Style.FILL
            }
            helpPaint.textSize = 15f
            helpPaint.color = if (alive) colText else colDim
            val nm = playerName(id)
            val maxName = boxW - 84f
            val nw = helpPaint.measureText(nm)
            if (nw > maxName) helpPaint.textSize = 15f * maxName / nw
            cv.drawText(nm, left + 32f, cy + 5f, helpPaint)
            rightPaint.textSize = 22f
            rightPaint.color = if (alive) colText else colDim
            cv.drawText(scoreOf(id).toString(), right - 12f, cy + 8f, rightPaint)
        }
    }

    private fun statusText(id: Int) = when {
        isAlive(id) -> strPlaying
        id != myId && pGone[id] -> strLeftGame
        else -> strCrashedShort
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

    /** Maç sonu: sıralama tablosu. */
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
            mpPlace == 1 && !mpTie -> { title = strWin; titleColor = colGold }
            mpPlace == 1 -> { title = strDraw; titleColor = colText }
            players.size == 2 -> { title = strLose; titleColor = colAccent }
            else -> { title = String.format(strPlaceFormat, mpPlace); titleColor = colWarm }
        }
        val top = 300f + (1f - ease) * 60f
        titlePaint.textSize = 60f
        titlePaint.color = withAlpha(Color.BLACK, (a * 0.4f).toInt())
        drawFit(cv, title, cx, top - 37f, titlePaint)
        titlePaint.color = withAlpha(titleColor, a)
        drawFit(cv, title, cx, top - 42f, titlePaint)

        val ranked = players.sortedWith(compareByDescending<Int> { scoreOf(it) }.thenBy { it })
        val rowH = 58f
        val panelW = min(uiW - 48f, 380f)
        val left = cx - panelW / 2f
        val panelH = 24f + rowH * ranked.size
        tmpRect.set(left, top, left + panelW, top + panelH)
        paint.color = withAlpha(colPanel, (a * 0.92f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = withAlpha(colPanelEdge, (a * 0.45f).toInt())
        cv.drawRoundRect(tmpRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        val topScore = ranked.firstOrNull()?.let { scoreOf(it) } ?: 0
        ranked.forEachIndexed { i, id ->
            val cy = top + 12f + rowH * i + rowH / 2f
            val place = 1 + players.count { scoreOf(it) > scoreOf(id) }
            if (id == myId) {
                paint.color = withAlpha(playerColor(id), (a * 0.18f).toInt())
                tmpRect.set(left + 10f, cy - rowH / 2f + 4f, left + panelW - 10f, cy + rowH / 2f - 4f)
                cv.drawRoundRect(tmpRect, 16f, 16f, paint)
            }
            titlePaint.textSize = 30f
            titlePaint.color = withAlpha(if (scoreOf(id) == topScore) colGold else colDim, a)
            cv.drawText("$place.", left + 34f, cy + 11f, titlePaint)
            paint.color = withAlpha(playerColor(id), a)
            cv.drawCircle(left + 70f, cy, 9f, paint)
            helpPaint.textSize = 22f
            helpPaint.color = withAlpha(colText, a)
            val nm = playerName(id) + if (id != myId && pGone[id]) "  ($strLeftGame)" else ""
            cv.drawText(nm, left + 90f, cy + 8f, helpPaint)
            rightPaint.textSize = 34f
            rightPaint.color = withAlpha(if (scoreOf(id) == topScore) colGold else colText, a)
            cv.drawText(scoreOf(id).toString(), left + panelW - 24f, cy + 12f, rightPaint)
        }

        labelPaint.textSize = 18f
        labelPaint.color = withAlpha(colDim, a)
        drawFit(cv, mapNames[mapIndex] + "  ·  " + diffNames[difficulty.ordinal], cx, top + panelH + 40f, labelPaint)

        val ba = (((stateTimer - 0.45f) / 0.25f).coerceIn(0f, 1f) * 255).toInt()
        if (ba <= 0) return
        if (canRetry()) {
            drawButton(cv, retryBtn, strRetry, colWarm, colDarkText, true, ba, 22f)
            drawButton(cv, menuBtn, strMenu, colPanelEdge, colText, false, ba, 22f)
        } else {
            val msg = when {
                mpDisconnected -> ""
                net?.isHost == true -> strNobodyLeft
                else -> strRetryWait + ".".repeat(1 + (time * 2f).toInt() % 3)
            }
            if (msg.isNotEmpty()) {
                textPaint.textSize = 21f
                textPaint.color = withAlpha(colText, ba)
                drawFit(cv, msg, cx, retryBtn.centerY() + 8f, textPaint)
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

    /** Odadaki oyuncular: renkli nokta + isim, ortalanmış tek satır. */
    private fun drawRoster(cv: Canvas, cx: Float, y: Float) {
        helpPaint.textSize = 17f
        val count = String.format(strPlayersCount, roster.size, NetLink.MAX_PLAYERS)
        val items = roster.map { playerName(it) }
        val gap = 18f
        var total = helpPaint.measureText(count) + gap
        for (s in items) total += 18f + helpPaint.measureText(s) + gap
        val k = if (total > uiW - 30f) (uiW - 30f) / total else 1f
        helpPaint.textSize = 17f * k
        var x = cx - total * k / 2f
        helpPaint.color = colDim
        cv.drawText(count, x, y, helpPaint)
        x += (helpPaint.measureText(count) + gap * k)
        roster.forEachIndexed { i, id ->
            paint.color = playerColor(id)
            cv.drawCircle(x + 7f * k, y - 6f * k, 7f * k, paint)
            helpPaint.color = if (id == myId) colText else colDim
            cv.drawText(items[i], x + 18f * k, y, helpPaint)
            x += (18f + helpPaint.measureText(items[i]) / k + gap) * k
        }
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
            MapKind.BLACKHOLE -> {
                val cx = w * 0.72f
                val cy = h * 0.28f
                val r = w * 0.2f
                p.shader = RadialGradient(
                    cx, cy, r * 2.6f,
                    intArrayOf(hex(0x88FF9A3B), hex(0x228A3BFF), 0), floatArrayOf(0.2f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r * 2.6f, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                cv.save()
                cv.rotate(-14f, cx, cy)
                for (k in 0 until 9) {
                    val rr = r * (1.05f + k * 0.12f)
                    p.strokeWidth = r * 0.09f
                    p.color = withAlpha(mixColor(hex(0xFFFFE0A0), hex(0xFF8A3BFF), k / 8f), 150 - k * 12)
                    tmpArtRect.set(cx - rr, cy - rr * 0.28f, cx + rr, cy + rr * 0.28f)
                    cv.drawArc(tmpArtRect, 180f, 180f, false, p) // arka yarı
                }
                cv.restore()
                p.style = Paint.Style.FILL
                p.color = Color.BLACK
                cv.drawCircle(cx, cy, r * 0.62f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.05f
                p.color = hex(0xFFFFD9A0)
                cv.drawCircle(cx, cy, r * 0.65f, p)
                cv.save()
                cv.rotate(-14f, cx, cy)
                for (k in 0 until 9) {
                    val rr = r * (1.05f + k * 0.12f)
                    p.strokeWidth = r * 0.09f
                    p.color = withAlpha(mixColor(hex(0xFFFFE0A0), hex(0xFF8A3BFF), k / 8f), 170 - k * 12)
                    tmpArtRect.set(cx - rr, cy - rr * 0.28f, cx + rr, cy + rr * 0.28f)
                    cv.drawArc(tmpArtRect, 0f, 180f, false, p) // ön yarı
                }
                cv.restore()
                p.style = Paint.Style.FILL
                p.color = hex(0x44000000)
                cv.drawRect(0f, 0f, w, h, p)
            }
            MapKind.STORM -> {
                // Üstte ağır bulutlar
                p.maskFilter = BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL)
                repeat(26) {
                    p.color = withAlpha(if (Random.nextBoolean()) hex(0xFF2A3560) else hex(0xFF3A4A80), Random.nextInt(90, 170))
                    cv.drawCircle(rnd(-0.1f, 1.1f) * w, rnd(-0.05f, 0.2f) * h, rnd(0.08f, 0.18f) * w, p)
                }
                p.maskFilter = null
                // Uzakta donmuş bir şimşek izi
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.006f
                p.color = withAlpha(hex(0xFFBFD4FF), 70)
                val path = Path()
                var x = w * 0.3f
                var y = h * 0.18f
                path.moveTo(x, y)
                while (y < h * 0.55f) {
                    x += rnd(-0.04f, 0.04f) * w
                    y += rnd(0.03f, 0.06f) * h
                    path.lineTo(x, y)
                }
                cv.drawPath(path, p)
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, h * 0.75f, 0f, h, 0, hex(0x553A4A80), Shader.TileMode.CLAMP)
                cv.drawRect(0f, h * 0.75f, w, h, p)
                p.shader = null
            }
            MapKind.METEOR -> {
                // Sol altta kraterli dev ay
                val r = h * 0.36f
                val cx = w * 0.05f
                val cy = h + r * 0.35f
                p.shader = RadialGradient(
                    cx + r * 0.3f, cy - r * 0.4f, r * 1.4f,
                    intArrayOf(hex(0xFF9A7B62), hex(0xFF5A4232), hex(0xFF1A120C)), floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, r, p)
                p.shader = null
                val save = cv.save()
                cv.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
                repeat(16) {
                    val a = rnd(-1.6f, 0.2f)
                    val d = rnd(0.2f, 0.95f) * r
                    val cr = rnd(0.03f, 0.09f) * r
                    p.color = hex(0x553A2A1E)
                    cv.drawCircle(cx + cos(a) * d, cy + sin(a) * d, cr, p)
                }
                cv.restoreToCount(save)
                p.color = hex(0x55000000)
                cv.drawCircle(cx, cy, r, p)
                // Gökyüzünde donmuş meteor izleri
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.ROUND
                repeat(7) {
                    val x0 = rnd(0.2f, 1f) * w
                    val y0 = rnd(0f, 0.5f) * h
                    val l = rnd(0.05f, 0.12f) * w
                    p.strokeWidth = rnd(1f, 2.5f) * scale
                    p.color = withAlpha(hex(0xFFFFB86B), Random.nextInt(30, 80))
                    cv.drawLine(x0, y0, x0 + l, y0 - l * 0.6f, p)
                }
                p.style = Paint.Style.FILL
            }
            MapKind.TOXIC -> {
                p.maskFilter = BlurMaskFilter(h * 0.04f, BlurMaskFilter.Blur.NORMAL)
                repeat(14) {
                    p.color = withAlpha(if (Random.nextBoolean()) hex(0xFF3BFF6B) else hex(0xFFC8FF3B), Random.nextInt(25, 60))
                    cv.drawCircle(rnd(-0.1f, 1.1f) * w, rnd(0f, 1f) * h, rnd(0.1f, 0.25f) * w, p)
                }
                p.maskFilter = null
                p.shader = LinearGradient(0f, h * 0.6f, 0f, h, 0, hex(0x667BFF3B), Shader.TileMode.CLAMP)
                cv.drawRect(0f, h * 0.6f, w, h, p)
                p.shader = null
            }
            MapKind.CORE -> {
                // Sarmal galaksi
                val cx = w * 0.62f
                val cy = h * 0.32f
                val R = w * 0.55f
                repeat(1800) {
                    val arm = Random.nextInt(2)
                    val t = sqrt(Random.nextFloat())
                    val a = arm * PI.toFloat() + t * 5.2f + rnd(-0.25f, 0.25f)
                    val d = t * R
                    val x = cx + cos(a) * d
                    val y = cy + sin(a) * d * 0.42f
                    p.color = withAlpha(
                        if (t < 0.35f || Random.nextInt(3) == 0) hex(0xFFFFE6A0) else hex(0xFF8FA8FF), Random.nextInt(30, 150)
                    )
                    cv.drawCircle(x, y, rnd(0.5f, 2.2f) * scale, p)
                }
                p.shader = RadialGradient(
                    cx, cy, R * 0.35f,
                    intArrayOf(hex(0xCCFFF4D0), hex(0x44FFD66B), 0), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
                )
                cv.drawCircle(cx, cy, R * 0.35f, p)
                p.shader = null
                p.color = hex(0x33000000)
                cv.drawRect(0f, 0f, w, h, p)
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
            PlanetType.DARK -> paintRocky(cv, p, r, pal, 9)
            PlanetType.TOXIC -> paintGas(cv, p, path, r, pal)
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
