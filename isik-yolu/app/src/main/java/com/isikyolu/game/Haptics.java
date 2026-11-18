package com.isikyolu.game;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Kısa dokunsal geri bildirim. */
public final class Haptics {
    private final Vibrator vib;

    Haptics(Context c) {
        Vibrator v = null;
        try { v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE); } catch (RuntimeException ignored) { }
        vib = v;
    }

    private void buzz(long ms, int amp) {
        if (vib == null || !Game.progress().vibration) return;
        try {
            if (!vib.hasVibrator()) return;
            vib.vibrate(VibrationEffect.createOneShot(ms, amp));
        } catch (RuntimeException ignored) { }
    }

    public void tick() { buzz(8, 60); }
    public void move() { buzz(14, 110); }
    public void success() { buzz(40, 200); }
    public void error() { buzz(24, 140); }
}
