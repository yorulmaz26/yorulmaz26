package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.engine.Chapters;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.Level;

import java.util.ArrayList;
import java.util.List;

/** Türkçe öğretici: sayfalar halinde açıklama + canlı örnek tahta. */
public final class TutorialOverlay extends Overlay {

    private static final class Page {
        final String tag, title, text; final Level demo;
        Page(String tag, String title, String text, Level demo) { this.tag = tag; this.title = title; this.text = text; this.demo = demo; }
    }

    private final List<Page> pages;
    private int index;
    private final TextView tag, title, body;
    private final BoardView board;
    private final LinearLayout dots;
    private final TextView nextBtnLabel;
    private Runnable done;

    private TutorialOverlay(Context c, List<Page> pages) {
        super(c);
        this.pages = pages;
        tag = Ui.text(c, "", 13, Palette.TURQUOISE, Ui.BOLD);
        tag.setLetterSpacing(0.08f);
        card.addView(tag);
        title = Ui.text(c, "", 24, Palette.WHITE, Ui.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, Ui.lpMargins(c, -2, -2, 0, 4, 0, 10));
        board = new BoardView(c);
        board.setInputEnabled(false);
        card.addView(board, new LinearLayout.LayoutParams(-1, Ui.dp(c, 190)));
        body = Ui.text(c, "", 16, Palette.TEXT_DIM, Ui.REGULAR);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.15f);
        card.addView(body, Ui.lpMargins(c, -1, -2, 0, 12, 0, 8));
        dots = Ui.horizontal(c);
        dots.setGravity(Gravity.CENTER);
        card.addView(dots, Ui.lpMargins(c, -1, Ui.dp(c, 16), 0, 4, 0, 4));
        LinearLayout row = Ui.horizontal(c);
        LinearLayout prev = Ui.button(c, "Geri", Ui.SECONDARY, -1, v -> go(-1));
        LinearLayout next = Ui.button(c, "İleri", Ui.PRIMARY, -1, v -> go(1));
        nextBtnLabel = (TextView) next.getChildAt(0);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f);
        l1.rightMargin = Ui.dp(c, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1.4f);
        l2.leftMargin = Ui.dp(c, 6);
        row.addView(prev, l1);
        row.addView(next, l2);
        card.addView(row, Ui.lpMargins(c, -1, -2, 0, 10, 0, 0));
        setCancelable(false);
        render();
    }

    public TutorialOverlay onDone(Runnable r) { done = r; return this; }

    private void go(int d) {
        int n = index + d;
        if (n < 0) return;
        if (n >= pages.size()) {
            dismiss();
            if (done != null) done.run();
            return;
        }
        index = n;
        render();
    }

    private void render() {
        Page p = pages.get(index);
        tag.setText(p.tag);
        title.setText(p.title);
        body.setText(p.text);
        board.bind(new GameSession(p.demo));
        board.setAlpha(0f);
        board.animate().alpha(1f).setDuration(250).start();
        nextBtnLabel.setText(index == pages.size() - 1 ? "Başla" : "İleri");
        dots.removeAllViews();
        Context c = getContext();
        for (int i = 0; i < pages.size(); i++) {
            android.view.View d = new android.view.View(c);
            d.setBackground(Ui.round(i == index ? Palette.TURQUOISE : 0xFF576A94, Ui.dp(c, 4)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, i == index ? 18 : 8), Ui.dp(c, 8));
            lp.setMargins(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            dots.addView(d, lp);
        }
    }

    private static List<Page> basics() {
        List<Page> l = new ArrayList<>();
        l.add(new Page("ÖĞRETİCİ · 1/4", "Hoş geldin!", "Işık kaynağından çıkan ışını hedef halkaya ulaştır. Bütün hedefler yandığında bölüm tamamlanır. Duvarlar ışığı durdurur.", Demos.intro()));
        l.add(new Page("ÖĞRETİCİ · 2/4", "Aynaları döndür", "Turkuaz halkalı aynalara dokun: her dokunuş aynayı 90° çevirir. Cıvatalı koyu yuvadaki aynalar sabittir, dönmez.", Demos.chapter(1)));
        l.add(new Page("ÖĞRETİCİ · 3/4", "Yıldızları topla", "En az hamleyle bitirirsen 3 yıldız kazanırsın. Takılırsan İpucu bir sonraki doğru hamleyi (gerekirse önce Geri Al'ı) gösterir.", Demos.chapter(2)));
        l.add(new Page("ÖĞRETİCİ · 4/4", "Hamle hakkı ve canlar", "Her bölümde sınırlı hamle hakkın var (sağ üstte HAK). Geri Al hakkı geri vermez. "
                + "İlk hamlede 1 can düşer, bölümü bitirince geri gelir; hak bitince, yeniden başlayınca ya da çıkınca can gider. Canlar 15 dakikada bir yenilenir.", Demos.chapter(7)));
        return l;
    }

    private static Page mechanic(int ch) {
        return new Page("YENİ MEKANİK · " + Chapters.TOPICS[ch - 1].toUpperCase(new java.util.Locale("tr", "TR")),
                Chapters.NAMES[ch - 1], Chapters.MECHANICS[ch - 1], Demos.chapter(ch));
    }

    /** Yeni dünyaya giriş kartı (101, 201, ...). */
    public static TutorialOverlay world(Context c, int w) {
        List<Page> l = new ArrayList<>();
        l.add(new Page("YENİ DÜNYA · " + w + "/" + Chapters.WORLDS, Chapters.WORLD_NAMES[w - 1],
                Chapters.WORLD_INTROS[w - 1], Demos.chapter(10)));
        return new TutorialOverlay(c, l);
    }

    public static TutorialOverlay first(Context c) { return new TutorialOverlay(c, basics()); }

    public static TutorialOverlay chapter(Context c, int ch) {
        List<Page> l = new ArrayList<>();
        l.add(mechanic(ch));
        return new TutorialOverlay(c, l);
    }

    public static TutorialOverlay full(Context c) {
        List<Page> l = basics();
        for (int ch = 1; ch <= Chapters.MECHANIC_COUNT; ch++) l.add(mechanic(ch));
        TutorialOverlay t = new TutorialOverlay(c, l);
        t.setCancelable(true);
        return t;
    }
}
