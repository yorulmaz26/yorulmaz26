package com.golgen.oyun.platform;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Titreşim geri bildirimi (ayarlardan kapatılabilir). */
public final class Haptics {
    private final Vibrator vibrator;
    public boolean enabled = true;

    public Haptics(Context ctx) {
        vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void pulse(int ms, int amplitude) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, Math.max(1, Math.min(255, amplitude))));
        } catch (RuntimeException ignored) {
            // Bazı cihazlarda titreşim servisi kısıtlı olabilir; oyun etkilenmemeli.
        }
    }

    public void light() { pulse(12, 60); }
    public void medium() { pulse(35, 140); }
    public void heavy() { pulse(110, 255); }
}
