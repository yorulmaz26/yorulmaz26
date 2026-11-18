package com.isikyolu.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

/** Ses efektleri (SoundPool) ve döngüsel arka plan müziği (MediaPlayer). */
public final class Audio {
    public static final int CLICK = 0, ROTATE = 1, PUSH = 2, TARGET = 3, WIN = 4, STAR = 5, UNDO = 6, HINT = 7, LOCKED = 8, ACHIEVE = 9;
    private static final int[] RES = {R.raw.click, R.raw.rotate, R.raw.push, R.raw.target, R.raw.win, R.raw.star,
            R.raw.undo, R.raw.hint, R.raw.locked, R.raw.achieve};
    private static final float[] VOL = {0.45f, 0.7f, 0.8f, 0.8f, 0.9f, 0.8f, 0.6f, 0.7f, 0.6f, 0.9f};

    private final Context ctx;
    private SoundPool pool;
    private final int[] ids = new int[RES.length];
    private MediaPlayer music;
    private boolean musicWanted;
    private boolean foreground;

    Audio(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
            for (int i = 0; i < RES.length; i++) ids[i] = pool.load(this.ctx, RES[i], 1);
        } catch (RuntimeException e) {
            pool = null;
        }
    }

    public void play(int sfx) { play(sfx, 1f); }

    public void play(int sfx, float rate) {
        if (pool == null || !Game.progress().sound) return;
        try { pool.play(ids[sfx], VOL[sfx], VOL[sfx], 1, 0, rate); } catch (RuntimeException ignored) { }
    }

    /** Müzik ayarına göre çalar/durdurur. */
    public void updateMusic() {
        musicWanted = Game.progress().music;
        if (musicWanted && foreground) {
            try {
                if (music == null) {
                    music = MediaPlayer.create(ctx, R.raw.music);
                    if (music == null) return;
                    music.setLooping(true);
                    music.setVolume(0.35f, 0.35f);
                }
                if (!music.isPlaying()) music.start();
            } catch (RuntimeException e) {
                music = null;
            }
        } else if (music != null) {
            try { if (music.isPlaying()) music.pause(); } catch (RuntimeException ignored) { }
        }
    }

    public void onResume() { foreground = true; updateMusic(); }

    public void onPause() {
        foreground = false;
        if (music != null) try { if (music.isPlaying()) music.pause(); } catch (RuntimeException ignored) { }
    }

    public void release() {
        if (music != null) { try { music.release(); } catch (RuntimeException ignored) { } music = null; }
    }
}
