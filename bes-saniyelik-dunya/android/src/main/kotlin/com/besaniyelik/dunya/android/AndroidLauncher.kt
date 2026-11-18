package com.besaniyelik.dunya.android

import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.besaniyelik.dunya.BesSaniyelikDunya
import com.besaniyelik.dunya.Platform

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            numSamples = 0
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val platform = object : Platform {
            override val name = "android"
            override fun vibrate(ms: Int) {
                val v = vibrator ?: return
                try {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") v.vibrate(ms.toLong())
                } catch (_: Throwable) {}
            }
        }
        initialize(BesSaniyelikDunya(platform), config)
    }
}
