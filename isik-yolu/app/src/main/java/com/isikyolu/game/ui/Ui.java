package com.isikyolu.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.game.Game;

/** Kodla arayüz oluşturma yardımcıları (XML düzenine gerek kalmadan tutarlı stil). */
public final class Ui {
    public static final int PRIMARY = 0, GOLD = 1, SECONDARY = 2, GHOST = 3, PURPLE = 4;

    public static final Typeface BOLD = Typeface.create("sans-serif-medium", Typeface.BOLD);
    public static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(true);
        return t;
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Cam efektli kart: degrade dolgu, ince açık kenar ve üstte yumuşak ışık. */
    public static Drawable card(Context c) {
        float r = dp(c, 22);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF2394F7D, 0xF22F406C, 0xF2283558});
        g.setCornerRadius(r);
        g.setStroke(dp(c, 1), 0x33A9C1FF);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1AFFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(r);
        LayerDrawable l = new LayerDrawable(new Drawable[]{g, shine});
        l.setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 40));
        return l;
    }

    /** Metne yatay renk geçişi uygular (başlıklar için). */
    public static void gradientText(TextView t, int... colors) {
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            float w = t.getPaint().measureText(t.getText().toString());
            t.getPaint().setShader(new LinearGradient(0, 0, Math.max(1, w), 0, colors, null, Shader.TileMode.CLAMP));
            if (r - l != or - ol) t.invalidate();
        });
    }

    private static Drawable buttonBg(Context c, int style, float radius) {
        GradientDrawable g;
        switch (style) {
            case PRIMARY:
                g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF22C7B2, 0xFF1AA8C6, 0xFF3D8CE8});
                break;
            case GOLD:
                g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFD45E, 0xFFF5C542, 0xFFF09A38});
                break;
            case PURPLE:
                g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF6D5DFB, 0xFF9A5DFB});
                break;
            case GHOST:
                g = new GradientDrawable();
                g.setColor(0x00000000);
                g.setStroke(dp(c, 1.5f), 0x55FFFFFF);
                break;
            default:
                g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF3F537D, 0xFF35466D});
                g.setStroke(dp(c, 1), 0xFF506692);
        }
        g.setCornerRadius(radius);
        Drawable content = g;
        if (style != GHOST) {
            GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x2EFFFFFF, 0x00FFFFFF});
            shine.setCornerRadius(radius);
            LayerDrawable l = new LayerDrawable(new Drawable[]{g, shine});
            l.setLayerInset(1, 0, 0, 0, dp(c, 22));
            content = l;
        }
        GradientDrawable mask = round(0xFFFFFFFF, radius);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static int textColorFor(int style) { return style == GOLD ? Palette.NIGHT : Palette.WHITE; }

    /** Yazılı, isteğe bağlı ikonlu buton. */
    public static LinearLayout button(Context c, String label, int style, int icon, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(c, 54));
        b.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        b.setBackground(buttonBg(c, style, dp(c, 18)));
        int tc = textColorFor(style);
        if (icon >= 0) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(new Icon(icon, tc));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
            lp.rightMargin = dp(c, 10);
            b.addView(iv, lp);
        }
        TextView t = text(c, label, 17, tc, BOLD);
        b.addView(t);
        clickable(b, l);
        b.setContentDescription(label);
        return b;
    }

    /** Yuvarlak ikon butonu. */
    public static FrameLayout iconButton(Context c, int icon, int style, int sizeDp, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(buttonBg(c, style, dp(c, sizeDp / 2f)));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Icon(icon, textColorFor(style)));
        int pad = dp(c, sizeDp * 0.26f);
        iv.setPadding(pad, pad, pad, pad);
        f.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        f.setLayoutParams(new ViewGroup.MarginLayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        clickable(f, l);
        return f;
    }

    /** Basma animasyonu + tık sesi. */
    public static void clickable(final View v, final View.OnClickListener l) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        v.setOnClickListener(view -> {
            Game.audio().play(com.isikyolu.game.Audio.CLICK);
            Game.haptics().tick();
            if (l != null) l.onClick(view);
        });
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpMargins(Context c, int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View space(Context c, float dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    public static ImageView icon(Context c, int kind, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Icon(kind, color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** Ekranın üst başlığı: geri butonu + başlık + sağ alan. */
    public static LinearLayout header(Context c, String title, View.OnClickListener back, View right) {
        LinearLayout h = horizontal(c);
        h.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 8));
        h.addView(iconButton(c, Icon.BACK, SECONDARY, 46, back));
        TextView t = text(c, title, 22, Palette.WHITE, BOLD);
        t.setPadding(dp(c, 14), 0, 0, 0);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (right != null) h.addView(right);
        return h;
    }
}
