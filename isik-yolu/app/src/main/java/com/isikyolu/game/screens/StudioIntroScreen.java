package com.isikyolu.game.screens;

import android.view.MotionEvent;

import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;
import com.isikyolu.game.GameLoader;
import com.isikyolu.game.MainActivity;
import com.isikyolu.game.ui.StudioIntroView;

/** Magic Lumos Software stüdyo açılışı. Dokununca atlanır; bölümler bu sırada arka planda yüklenir. */
public final class StudioIntroScreen extends Screen {
    private boolean done;
    private final Runnable finish = this::finish;

    public StudioIntroScreen(MainActivity a) {
        super(a);
        setBackgroundColor(StudioIntroView.BG);
        StudioIntroView v = new StudioIntroView(a);
        v.setOnLight(() -> Game.audio().play(Audio.INTRO));
        addView(v, new LayoutParams(-1, -1));
    }

    private void bars(int top, int bottom) {
        try {
            act.getWindow().setStatusBarColor(top);
            act.getWindow().setNavigationBarColor(bottom);
        } catch (RuntimeException ignored) { }
    }

    @Override public void onShow() {
        bars(StudioIntroView.BG, StudioIntroView.BG);
        GameLoader.load(act, ok -> { }); // bölümleri önceden yükle
        postDelayed(finish, StudioIntroView.DURATION_MS);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return true; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    private void finish() {
        if (done) return;
        done = true;
        removeCallbacks(finish);
        bars(0xFF3D4D92, 0xFF213062);
        act.show(new SplashScreen(act), true);
    }

    @Override public boolean onBack() { finish(); return true; }
}
