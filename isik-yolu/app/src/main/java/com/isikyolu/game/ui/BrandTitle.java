package com.isikyolu.game.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.isikyolu.game.i18n.Brand;

/**
 * İki dilli oyun adı: ana ad büyük ve ışıltılı, ikinci ad altında küçük, aralıklı büyük harflerle.
 * Türkçede "Işık Yolu / PRISMWAY", diğer dillerde "Prismway / IŞIK YOLU".
 */
public final class BrandTitle extends LinearLayout {
    public final TextView main, second;

    public BrandTitle(Context c, float mainSp) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        main = Ui.text(c, Brand.primary(), mainSp, Palette.WHITE, Ui.BOLD);
        main.setLetterSpacing(0.02f);
        main.setSingleLine(true);
        Ui.gradientText(main, 0xFFFFE08A, 0xFFFFFFFF, 0xFF7DF0E0);
        main.setShadowLayer(Ui.dp(c, 14), 0, 0, 0x6620B8A6);
        addView(main, new LayoutParams(-2, -2));
        // ikincil ad: ana adın yaklaşık %38'i boyunda, geniş harf aralığıyla
        second = Ui.text(c, Brand.secondary().toUpperCase(Brand.turkishFirst() ? java.util.Locale.ROOT : new java.util.Locale("tr", "TR")),
                Math.max(11f, mainSp * 0.36f), 0xFFBFD3F2, Ui.MEDIUM);
        second.setLetterSpacing(0.32f);
        second.setSingleLine(true);
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.topMargin = -Ui.dp(c, 2);
        addView(second, lp);
        setContentDescription(Brand.full());
    }
}
