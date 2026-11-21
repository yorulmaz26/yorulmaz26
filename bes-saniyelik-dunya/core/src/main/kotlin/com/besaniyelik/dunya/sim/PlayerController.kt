package com.besaniyelik.dunya.sim

import com.badlogic.gdx.physics.box2d.*
import com.besaniyelik.dunya.level.SolidType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Oyuncu karakteri: otomatik koşu, değişken yükseklikte zıplama, coyote time, zıplama tamponu,
 * ters yer çekimi, ters kontrol, boyut değişimi ve buz fiziği.
 */
class PlayerController(private val sim: Simulation) {
    lateinit var body: Body
        private set
    private var fixture: Fixture? = null

    var scale = 1f
    val hw get() = Physics.PLAYER_W * scale / 2f
    val hh get() = Physics.PLAYER_H * scale / 2f
    val x get() = body.position.x
    val y get() = body.position.y
    val vx get() = body.linearVelocity.x
    val vy get() = body.linearVelocity.y
    val gravSign get() = sim.effects.gravitySign

    var grounded = false
    var groundEntity: SolidEntity? = null
    var coyote = 0f
    var buffer = 0f
    var prevInput = false
    var cutAllowed = false
    var autoTimer = 0f
    var airJumps = 0
    var blocked = 0f
    var boost = 0f
    var vxCur = Physics.RUN_SPEED
    var jumpLock = 0
    var invuln = 0f
    var crushTicks = 0
    private var lastX = 0f
    /** Fizik adımından önceki y (gömülme düzeltmesinin yönünü belirlemek için). */
    private var prevY = 0f

    // Animasyon/görsel bilgileri (mantığı etkilemez)
    var airTime = 0f
    var landImpact = 0f
    var lastJumpTick = -100
    var safeX = 0f
    var safeY = 0f
    var safeSign = 1
    // Kurtarma için güvenli konum geçmişi (6 tick'te bir örnek, ~0,8 sn): kurtarılan oyuncu düştüğü kenara değil,
    // biraz gerisine konur. Yalnızca gerçek oyunda (kalkan/dokunulmazlık) kullanılır; çözücü ve analiz hiç kurtarmaz.
    private val safeXs = FloatArray(16)
    private val safeYs = FloatArray(16)
    private val safeSigns = IntArray(16)
    private val safePhases = IntArray(16)
    private val safeEnts = arrayOfNulls<SolidEntity>(16)
    private var safeEnt: SolidEntity? = null
    private var safeN = 0
    private var safeI = 0
    private var lastSampleX = Float.NEGATIVE_INFINITY

    private fun resetSafe(x: Float, y: Float, sign: Int, ent: SolidEntity? = null) {
        safeN = 0; safeI = 0; lastSampleX = Float.NEGATIVE_INFINITY
        safeX = x; safeY = y; safeSign = sign; safeEnt = ent
        // Kurtarma noktası ilk örnek olarak kalır: aynı zeminden ikinci kez düşülürse yine buraya dönülür
        if (ent != null) addSample(x, y, sign, ent)
    }

    private fun addSample(x: Float, y: Float, sign: Int, ent: SolidEntity) {
        lastSampleX = x
        safeXs[safeI] = x; safeYs[safeI] = y; safeSigns[safeI] = sign; safePhases[safeI] = sim.phaseIdx; safeEnts[safeI] = ent
        safeI = (safeI + 1) % safeXs.size
        if (safeN < safeXs.size) safeN++
    }

    fun create(x: Float, y: Float) {
        val bd = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            fixedRotation = true
        }
        body = sim.world.createBody(bd)
        body.isSleepingAllowed = false
        body.gravityScale = 1f
        rebuildFixture()
        lastX = x
        resetSafe(x, y, 1)
    }

    private fun rebuildFixture() {
        fixture?.let { body.destroyFixture(it) }
        val shape = PolygonShape()
        val a = hw
        val b = hh
        val c = 0.07f * scale
        // Köşeleri pahlı kutu: platform birleşimlerine takılmayı önler
        shape.set(floatArrayOf(-a + c, -b, a - c, -b, a, -b + c, a, b - c, a - c, b, -a + c, b, -a, b - c, -a, -b + c))
        val fx = body.createFixture(shape, 1f)
        fx.friction = 0f
        fx.restitution = 0f
        shape.dispose()
        fixture = fx
        body.resetMassData()
    }

    fun vyUp(): Float = vy * gravSign

    fun overlapsRect(x0: Float, y0: Float, x1: Float, y1: Float, margin: Float): Boolean =
        x - hw - margin < x1 && x + hw + margin > x0 && y - hh - margin < y1 && y + hh + margin > y0

    fun onEffectsChanged(old: RuleEffects, new: RuleEffects, initial: Boolean) {
        if (!initial && old.gravitySign != new.gravitySign) {
            cutAllowed = false
            grounded = false
            jumpLock = 2
        }
        if (new.sizeScale < scale) applyScale(new.sizeScale)
    }

    private fun applyScale(s: Float) {
        if (s == scale) return
        val sign = gravSign
        val oldHh = hh
        scale = s
        // Ayak tarafını sabit tut
        val ny = y - sign * oldHh + sign * hh
        body.setTransform(x, ny, 0f)
        rebuildFixture()
    }

    /** Büyümek için yer var mı? (Dar geçitte büyüme ertelenir, böylece kural değişimi oyuncuyu öldürmez.) */
    private fun tryGrow(target: Float) {
        val sign = gravSign
        val nh = Physics.PLAYER_H * target / 2f
        val nw = Physics.PLAYER_W * target / 2f
        val x0 = x - nw; val x1 = x + nw
        fun free(ny: Float): Boolean {
            val y0 = ny - nh; val y1 = ny + nh
            for (s in sim.solidsNear(x0 - 1, x1 + 1)) {
                if (!s.active || s.removed) continue
                if (s.overlaps(x0 + 0.02f, y0 + 0.02f, x1 - 0.02f, y1 - 0.02f)) return false
            }
            return true
        }
        // Önce ayak tarafı sabit kalarak büyü; orası doluysa (ör. küçülme, yer çekimi dönüşüyle aynı anda bittiğinde
        // karakter hâlâ eski yüzeyde durur) baş tarafı sabit kalarak büyü.
        val feet = y - sign * hh
        if (free(feet + sign * nh)) { applyScale(target); return }
        val head = y + sign * hh
        val ny2 = head - sign * nh
        if (free(ny2)) {
            scale = target
            body.setTransform(x, ny2, 0f)
            rebuildFixture()
        }
    }

    private fun checkGround(): SolidEntity? {
        val sign = gravSign
        val x0 = x - hw + 0.05f
        val x1 = x + hw - 0.05f
        val fy = y - sign * hh
        val y0 = if (sign > 0) fy - 0.06f else fy - 0.02f
        val y1 = if (sign > 0) fy + 0.02f else fy + 0.06f
        var best: SolidEntity? = null
        for (s in sim.solidsNear(x0, x1)) {
            if (!s.active || s.removed) continue
            if (s.overlaps(x0, y0, x1, y1)) {
                best = s
                if (!s.moving) break
            }
        }
        return best
    }

    fun preStep(input: Boolean, dt: Float) {
        val e = sim.effects
        val sign = e.gravitySign
        val wasGrounded = grounded
        if (jumpLock > 0) { jumpLock--; grounded = false; groundEntity = null } else {
            val g = checkGround()
            // Hız, üstünde durulan platforma GÖRE ölçülür: yükselen (kayan/dikey hareketli) platformda da yerde sayılır,
            // aksi halde platform yükselirken zıplama girişi boşa giderdi.
            val gv = if (g != null && g.moving) (g.body?.linearVelocity?.y ?: 0f) * sign else 0f
            grounded = g != null && vyUp() - gv <= 0.6f
            groundEntity = if (grounded) g else null
        }
        if (grounded && !wasGrounded) {
            landImpact = min(1f, airTime / 0.8f)
            if (airTime > 0.12f) sim.listener.onLand(landImpact)
            airJumps = if (sim.doubleJump > 0f) 1 else 0
        }
        if (grounded) {
            airTime = 0f
            // Çözücü (katı mod) coyote payını kullanmaz: bu pay insana kalır ve çözümler kenardan düşüp son anda
            // zıplayan türden olmaz.
            coyote = if (sim.config.strict) 0f else Physics.COYOTE
            val ge = groundEntity
            if (ge != null && ge.def.type != SolidType.GHOST && !ge.moving && blocked <= 0f) {
                safeX = x; safeY = y; safeSign = sign
                // yeni bir zemine basıldığında hemen, sonra 0,6 karoda bir örnek alınır
                if (ge !== safeEnt || x - lastSampleX >= 0.6f) addSample(x, y, sign, ge)
                safeEnt = ge
            }
        } else {
            airTime += Physics.TICK
            coyote -= Physics.TICK
        }
        if (sim.doubleJump > 0f && grounded) airJumps = 1

        // Boyut: küçülme hemen, büyüme yer açıldığında
        if (e.sizeScale > scale) tryGrow(e.sizeScale)

        val pressed = input && !prevInput
        var v = body.linearVelocity.y
        if (!e.reverseControl) {
            if (pressed) buffer = if (sim.config.strict) Physics.TICK * 1.5f else Physics.JUMP_BUFFER
            if (buffer > 0f && (grounded || coyote > 0f)) {
                v = jump(sign, e); buffer = 0f
            } else if (pressed && !grounded && coyote <= 0f && airJumps > 0) {
                airJumps--
                v = jump(sign, e)
                sim.listener.onJump(true)
            }
            if (!input && cutAllowed && v * sign > 0f) { v *= Physics.JUMP_CUT; cutAllowed = false }
        } else {
            // Ters kontrol: karakter kendiliğinden zıplar; basılı tutmak yerde tutar ve havada zıplamayı kısaltır.
            if (grounded && !input) {
                autoTimer += Physics.TICK
                if (autoTimer >= Physics.AUTO_JUMP_DELAY) { v = jump(sign, e); autoTimer = 0f }
            } else autoTimer = 0f
            if (input && cutAllowed && v * sign > 0f && !grounded) { v *= Physics.JUMP_CUT; cutAllowed = false }
        }
        if (v * sign <= 0f) cutAllowed = false
        buffer = max(0f, buffer - Physics.TICK)
        prevInput = input

        // Yatay hız
        boost = max(0f, boost - dt)
        // Hız pedi buzla çarpılmaz (aksi halde hız 2 katını aşıp iki kural boyunca sönmüyordu)
        val base = Physics.RUN_SPEED * sim.speedMul
        val cruise = base * (if (e.ice) Physics.ICE_SPEED_MUL else 1f)
        val target = if (boost > 0f) base * Physics.BOOST_MUL else cruise
        val accel = when {
            boost > 0f -> 30f
            e.ice && grounded -> Physics.ICE_ACCEL
            e.ice -> Physics.ICE_ACCEL * 0.4f
            else -> Physics.NORMAL_ACCEL
        }
        // Hedefin üstündeyken: buzda yalnızca buz seyir hızına kadarki fark yavaş söner; fazlası (hız pedi) çabuk söner
        val decel = if (e.ice && vxCur <= cruise + 0.01f) Physics.ICE_ACCEL else if (e.ice) 14f else Physics.NORMAL_ACCEL
        vxCur = if (vxCur < target) min(target, vxCur + accel * dt) else max(target, vxCur - decel * dt)
        var carry = 0f
        val ge = groundEntity
        if (ge != null && ge.def.type == SolidType.MOVE) carry = ge.body?.linearVelocity?.x ?: 0f
        // Düşüş hızı sınırı
        if (v * sign < -Physics.MAX_FALL) v = -Physics.MAX_FALL * sign
        body.setLinearVelocity(vxCur + carry, v)
        lastX = x
        prevY = y
    }

    private fun jump(sign: Int, e: RuleEffects): Float {
        grounded = false
        groundEntity = null
        coyote = 0f
        cutAllowed = true
        jumpLock = 3
        lastJumpTick = sim.tick
        sim.listener.onJump(false)
        return Physics.JUMP_V * e.jumpMul * sign
    }

    fun springLaunch() {
        val sign = gravSign
        val mul = if (sim.effects.jumpMul > 1.1f) 1.12f else 1f
        body.setLinearVelocity(body.linearVelocity.x, Physics.SPRING_V * mul * sign)
        grounded = false
        cutAllowed = false
        jumpLock = 4
        coyote = 0f
        lastJumpTick = sim.tick
    }

    fun postStep(dt: Float) {
        val dx = x - lastX
        val expected = vxCur * dt
        if (dx < expected * 0.3f) {
            blocked += Physics.TICK
            if (sim.effects.ice) vxCur = min(vxCur, Physics.RUN_SPEED * sim.speedMul)
        } else blocked = max(0f, blocked - Physics.TICK * 2f)
        invuln = max(0f, invuln - Physics.TICK)
        if (blocked > Physics.STUCK_TIME) { sim.kill(DeathCause.STUCK); return }
        // Çözücü duvar dibinde oyalanan çözümleri kabul etmez (insan beklemez; doğrulama doğal akış üzerinden yapılır)
        if (sim.config.solver && blocked > 0.3f) { sim.kill(DeathCause.STUCK); return }
        depenetrate()
        // Ezilme: hareketli bir platform oyuncuyu bir katıya birkaç tick boyunca derinlemesine gömerse
        val m = 0.16f * scale + 0.05f
        var deep = false
        for (s in sim.solidsNear(x - hw, x + hw)) {
            if (!s.active || s.removed) continue
            if (s.overlaps(x - hw + m, y - hh + m, x + hw - m, y + hh - m)) { deep = true; break }
        }
        crushTicks = if (deep) crushTicks + 1 else 0
        if (crushTicks >= 5) { sim.kill(DeathCause.CRUSH); return }
    }

    /**
     * Sürekli çarpışma (CCD) kapalı olduğundan hızlı düşüşte gövde bir adımda zeminin içine 0.2+ karo girebilir;
     * Box2D bunu birkaç adımda düzeltir ama o arada karakter zemine gömülü görünür. Sabit katılarla dikey
     * örtüşmeyi (yatay örtüşmeden küçükse) hemen giderir.
     */
    private fun depenetrate() {
        var px = x; var py = y
        var vy = body.linearVelocity.y
        var moved = false
        for (s in sim.solidsNear(px - hw, px + hw)) {
            if (!s.active || s.removed || s.moving) continue
            val ox = min(px + hw, s.x + s.w) - max(px - hw, s.x)
            if (ox <= 0.02f) continue
            val top = s.y + s.h
            val oy = min(py + hh, top) - max(py - hh, s.y)
            if (oy <= 0.015f) continue
            // Dikey düzeltme yalnızca yüzeye GERÇEKTEN üstten (ya da alttan) gelindiyse yapılır: adımdan önce ayaklar
            // yüzeyin üstündeydi. Aksi halde (yandan, ayaklar kenarın altındayken) bu bir basamak tırmanması olurdu ve
            // karakter dar boşlukların üstünden düşmeden koşardı.
            val fromAbove = py > s.y + s.h / 2f && prevY - hh >= top - 0.03f
            val fromBelow = py <= s.y + s.h / 2f && prevY + hh <= s.y + 0.03f
            if (oy < ox && oy <= 0.45f && (fromAbove || fromBelow)) {
                if (fromAbove) { py = top + hh; if (vy < 0f) vy = 0f } else { py = s.y - hh; if (vy > 0f) vy = 0f }
                moved = true
            } else if (ox < oy && ox > 0.03f && ox <= 0.3f) {
                // yandan duvara gömülme: geldiği tarafa it
                px = if (px < s.x + s.w / 2f) s.x - hw else s.x + s.w + hw
                moved = true
            }
        }
        if (moved) { body.setTransform(px, py, 0f); body.setLinearVelocity(body.linearVelocity.x, vy) }
    }

    /**
     * Kalkan veya dokunulmazlıkla kurtarma: yaklaşık yarım saniye önceki güvenli zemine taşı (düşülen kenarın gerisi).
     * [extend] yalnızca kalkan kurtarmasında dokunulmazlık verir; dokunulmazlık kurtarması süreyi uzatmaz.
     */
    fun rescue(extend: Boolean = true) {
        var rx = safeX; var ry = safeY; var rs = safeSign
        // Son basılan zeminin ÜSTÜNDE, kenarın en az 2 karo gerisindeki en yeni örnek; o kadar gerisi yoksa aynı zemindeki
        // en eski örnek (en uzun koşu payı). Önceki platforma (aşılmış bir boşluğun gerisine) asla dönülmez.
        val ent = safeEnt
        for (k in 1..safeN) {
            val i = ((safeI - k) % safeXs.size + safeXs.size) % safeXs.size
            if (safeEnts[i] !== ent || safePhases[i] != sim.phaseIdx) break
            val back = safeX - safeXs[i]
            if (back > 6f) break
            rx = safeXs[i]; ry = safeYs[i]; rs = safeSigns[i]
            if (back >= 2f) break
        }
        body.setTransform(rx, ry, 0f)
        body.setLinearVelocity(vxCur, 0f)
        if (extend) invuln = max(invuln, 1.6f)
        blocked = 0f
        if (rs != gravSign) {
            // güvenli nokta farklı yer çekimindeydiyse en yakın yüzeye yakın bırak
            body.setTransform(rx, if (gravSign > 0) 1.5f else Physics.CEIL - 1.5f, 0f)
        }
        resetSafe(rx, body.position.y, gravSign, if (rs == gravSign) ent else null)
    }

    fun restore(s: SimSnapshot, e: RuleEffects) {
        // Gövde yeniden oluşturulur: Box2D temas geçmişi sıfırlanır, böylece geri yükleme deterministik olur.
        sim.world.destroyBody(body)
        fixture = null
        scale = s.scale
        create(s.px, s.py)
        body.setLinearVelocity(s.vx, s.vy)
        grounded = s.grounded; coyote = s.coyote; buffer = s.buffer; prevInput = s.prevInput; cutAllowed = s.cutAllowed
        autoTimer = s.autoTimer; airJumps = s.airJumps; blocked = s.blocked; boost = s.boost; vxCur = s.vxCur
        jumpLock = s.jumpLock; invuln = s.invuln; crushTicks = 0
        lastX = s.px
        groundEntity = null
        resetSafe(s.px, s.py, e.gravitySign)
    }
}
