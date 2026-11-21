package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Karartılmış arka planlı açılır pencere (duraklatma, ipucu, tamamlandı...). */
public class Overlay extends FrameLayout {
    protected final LinearLayout card;
    private boolean cancelable = true;
    private Runnable onDismiss;
    private boolean closing;

    public Overlay(Context c) {
        super(c);
        setBackgroundColor(0x99161E40);
        setClickable(true);
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        FrameLayout holder = new FrameLayout(c);
        card = Ui.vertical(c);
        card.setBackground(Ui.card(c));
        int p = Ui.dp(c, 22);
        card.setPadding(p, p, p, p);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int m = Ui.dp(c, 22);
        lp.setMargins(m, m, m, m);
        holder.addView(card, lp);
        sv.addView(holder, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.setOnClickListener(v -> { if (cancelable) dismiss(); });
    }

    public Overlay setCancelable(boolean c) { cancelable = c; return this; }

    /** Kapanma animasyonu sırasında düğmeler ikinci kez tetiklenmesin (çift dokunuş koruması). */
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) { return closing || super.onInterceptTouchEvent(e); }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) { return closing || super.onTouchEvent(e); }
    public Overlay setOnDismiss(Runnable r) { onDismiss = r; return this; }
    public boolean isCancelable() { return cancelable; }

    /** Kapanma animasyonu sürüyor mu (artık "açık pencere" sayılmaz). */
    public boolean isClosing() { return closing; }

    public void show(ViewGroup parent) {
        parent.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setAlpha(0f);
        animate().alpha(1f).setDuration(180).start();
        card.setScaleX(0.88f); card.setScaleY(0.88f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    public void dismiss() {
        if (closing) return;
        closing = true;
        card.animate().scaleX(0.92f).scaleY(0.92f).setDuration(160).start();
        animate().alpha(0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
            if (onDismiss != null) onDismiss.run();
        }).start();
    }

    public LinearLayout cardView() { return card; }

    public TextView title(String t) {
        TextView v = Ui.text(getContext(), t, 26, Palette.WHITE, Ui.BOLD);
        v.setGravity(Gravity.CENTER);
        card.addView(v, Ui.lpMargins(getContext(), -2, -2, 0, 6, 0, 6));
        return v;
    }

    public TextView body(String t) {
        TextView v = Ui.text(getContext(), t, 16, Palette.TEXT_DIM, Ui.REGULAR);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.15f);
        card.addView(v, Ui.lpMargins(getContext(), -1, -2, 0, 4, 0, 10));
        return v;
    }

    public View button(String label, int style, int icon, View.OnClickListener l) { return addButton(label, style, icon, l); }

    /** Kartın altına tam genişlikte buton ekler. */
    protected View addButton(String label, int style, int icon, View.OnClickListener l) {
        View b = Ui.button(getContext(), label, style, icon, l);
        card.addView(b, Ui.lpMargins(getContext(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
        return b;
    }
}
