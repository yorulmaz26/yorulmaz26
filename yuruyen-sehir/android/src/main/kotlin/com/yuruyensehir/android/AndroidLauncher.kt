package com.yuruyensehir.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.yuruyensehir.game.YuruyenSehirGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            numSamples = 2
        }
        initialize(YuruyenSehirGame(store = PlayBillingStore(this)), config)
    }

    /**
     * libGDX 1.13.1: klavye yüksekliği sağlayıcısı başlamadan aktivite kapanırsa
     * AndroidXKeyboardHeightProvider.close() boş görünüm yüzünden NPE fırlatır.
     * Activity.onDestroy zaten çağrılmış olduğundan bu hatayı yutmak güvenlidir.
     */
    override fun onDestroy() {
        try {
            super.onDestroy()
        } catch (e: NullPointerException) {
            android.util.Log.w("YuruyenSehir", "Kapanışta libGDX klavye sağlayıcısı hatası yok sayıldı", e)
        }
    }
}
