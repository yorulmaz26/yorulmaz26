package com.isikyolu.game.ui;

import android.content.Context;
import android.widget.FrameLayout;

/** Genişliği kadar yüksek kutu. */
public final class SquareFrame extends FrameLayout {
    public SquareFrame(Context c) { super(c); }

    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, w); }
}
