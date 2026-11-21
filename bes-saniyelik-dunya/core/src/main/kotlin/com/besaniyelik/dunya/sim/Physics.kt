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
    /** Çarpışma kutusu: karakter görselinin gövdesine uygun dikdörtgen (pelerin ve kapüşon ucu görsel olarak taşabilir). */
    const val PLAYER_W = 0.85f
    const val PLAYER_H = 1.42f
    /** Oyuncunun ekranda çizilen boyu (karo); çarpışma kutusuyla orantılı tutulur. */
    const val PLAYER_VISUAL_H = 1.55f
    /** Dar geçit alt yüzeyinin yerden yüksekliği: küçük boy (PLAYER_H * SMALL_SCALE ≈ 0.78) geçer, normal boy geçemez. */
    const val LOW_CLEARANCE = 0.9f
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
    /** Diken isabet yüksekliği ve çizim yüksekliği (karo). Dikenler karakterin yarı boyuna yakındır: uzaktan seçilir. */
    const val SPIKE_H = 0.78f
    const val SPIKE_VISUAL_H = 0.92f
    /** Testerenin çizilen yarıçapına göre isabet yarıçapı oranı. */
    const val SAW_HIT = 0.86f
    /** Final bölümlerindeki Kaos Duvarı'nın oyuncunun en fazla ne kadar gerisinde kalabildiği (ekranın sol kenarında görünür). */
    const val CHASER_GAP = 5.5f
    /** Her bölümün sonundaki, her zaman Normal Dünya olan bitiş düzlüğünün uzunluğu (karo). */
    const val FINISH_LEN = 12f

    /** Kural setinin nominal yatay hızı (dünya-saniyesi başına karo, zaman ölçeği dahil gerçek saniyeye göre). */
    fun realSpeed(e: RuleEffects, speedMul: Float = 1f): Float =
        RUN_SPEED * speedMul * e.timeScale * (if (e.ice) 1.18f else 1f)
}
