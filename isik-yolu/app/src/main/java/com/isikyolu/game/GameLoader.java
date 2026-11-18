package com.isikyolu.game;

import android.os.Handler;
import android.os.Looper;

/** Bölüm dosyasını ana iş parçacığını bloklamadan yükler. */
public final class GameLoader {
    public interface Callback { void done(boolean ok); }

    private GameLoader() {}

    public static void load(MainActivity a, Callback cb) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok;
            try { ok = Game.loadLevels(a).size() == 100; } catch (Exception e) { ok = false; }
            final boolean r = ok;
            h.post(() -> cb.done(r));
        }, "bolum-yukleyici").start();
    }

    /** Genel amaçlı arka plan işi. */
    public static <T> void background(java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> done) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            T v;
            try { v = work.call(); } catch (Exception e) { v = null; }
            final T r = v;
            h.post(() -> done.accept(r));
        }, "isik-yolu-is").start();
    }
}
