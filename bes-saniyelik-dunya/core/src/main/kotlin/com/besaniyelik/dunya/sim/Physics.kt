package com.besaniyelik.dunya.sim

/** Oyun fiziği sabitleri (birim: karo = 1 metre). */
object Physics {
    const val TICK = 1f / 60f
    const val GRAVITY = 34f
    const val JUMP_V = 13.3f
    const val JUMP_CUT = 0.45f
    const val BOUNCY_MUL = 1.38f
    const val SMALL_JUMP_MUL = 0.92f
    const val SPRING_V = 19f
    const val MAX_FALL = 20f
    const val RUN_SPEED = 5.2f
    const val FAST_SCALE = 1.3f
    const val SLOW_SCALE = 0.62f
    const val ICE_SPEED_MUL = 1.3f
    const val ICE_ACCEL = 2.4f
    const val NORMAL_ACCEL = 40f
    const val BOOST_MUL = 1.6f
    const val BOOST_TIME = 1.1f
    const val PLAYER_SIZE = 0.8f
    const val SMALL_SCALE = 0.55f
    const val COYOTE = 0.1f
    const val JUMP_BUFFER = 0.12f
    const val AUTO_JUMP_DELAY = 0.06f
    const val STUCK_TIME = 1.5f
    const val CEIL = 9f
    const val RULE_PERIOD = 5f
    const val WARNING_TIME = 1.2f
    const val GHOST_CYCLE = 2.7f
    const val GHOST_SOLID = 1.5f
    const val GHOST_WARN = 0.4f
    const val RESPAWN_DELAY = 0.9f

    /** Kural setinin nominal yatay hızı (dünya-saniyesi başına karo, zaman ölçeği dahil gerçek saniyeye göre). */
    fun realSpeed(e: RuleEffects, speedMul: Float = 1f): Float =
        RUN_SPEED * speedMul * e.timeScale * (if (e.ice) 1.18f else 1f)
}
