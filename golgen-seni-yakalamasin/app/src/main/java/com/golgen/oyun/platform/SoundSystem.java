package com.golgen.oyun.platform;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.golgen.oyun.core.Synth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Ses efektleri (SoundPool) ve döngüsel arka plan müziği (MediaPlayer).
 * Tüm sesler ilk açılışta arka planda sentezlenip önbellek klasörüne WAV olarak yazılır.
 */
public final class SoundSystem {
    private static final String TAG = "SoundSystem";
    private static final int AUDIO_VERSION = 1;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SoundPool pool;
    private final int[] ids = new int[Synth.SFX_COUNT];
    private volatile boolean sfxReady;
    private MediaPlayer music;
    private File musicFile;
    public boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private boolean resumed = true;
    private boolean released;

    public SoundSystem(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        Thread t = new Thread(this::prepare, "ses-hazirla");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private void prepare() {
        try {
            File dir = new File(ctx.getCacheDir(), "ses_v" + AUDIO_VERSION);
            if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Ses klasörü oluşturulamadı");
            final File[] files = new File[Synth.SFX_COUNT];
            for (int i = 0; i < Synth.SFX_COUNT; i++) {
                files[i] = new File(dir, "sfx_" + i + ".wav");
                if (!files[i].exists() || files[i].length() < 100) write(files[i], Synth.wav(Synth.sfx(i), Synth.SFX_RATE));
            }
            File m = new File(dir, "muzik.wav");
            if (!m.exists() || m.length() < 1000) write(m, Synth.wav(Synth.music(), Synth.MUSIC_RATE));
            main.post(() -> {
                if (released) return;
                for (int i = 0; i < Synth.SFX_COUNT; i++) ids[i] = pool.load(files[i].getAbsolutePath(), 1);
                sfxReady = true;
                musicFile = m;
                updateMusic();
            });
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Sesler hazırlanamadı", e);
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) throw new IOException("Yeniden adlandırılamadı: " + f);
    }

    public void play(int id) {
        play(id, 1f, 1f);
    }

    public void play(int id, float volume, float rate) {
        if (!soundEnabled || !sfxReady || released) return;
        pool.play(ids[id], volume, volume, 1, 0, rate);
    }

    public void setMusicEnabled(boolean on) {
        musicEnabled = on;
        updateMusic();
    }

    public void onResume() {
        resumed = true;
        updateMusic();
    }

    public void onPause() {
        resumed = false;
        updateMusic();
    }

    private void updateMusic() {
        if (released) return;
        boolean shouldPlay = musicEnabled && resumed && musicFile != null;
        try {
            if (shouldPlay) {
                if (music == null) {
                    music = new MediaPlayer();
                    music.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                    music.setDataSource(musicFile.getAbsolutePath());
                    music.setLooping(true);
                    music.setVolume(0.55f, 0.55f);
                    music.prepare();
                }
                if (!music.isPlaying()) music.start();
            } else if (music != null && music.isPlaying()) {
                music.pause();
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Müzik çalınamadı", e);
            if (music != null) { music.release(); music = null; }
        }
    }

    public void release() {
        released = true;
        if (music != null) { music.release(); music = null; }
        if (pool != null) { pool.release(); pool = null; }
    }
}
