package com.isikyolu.engine;

import static org.junit.Assert.*;

import com.isikyolu.engine.tools.Audit;
import org.junit.Test;

/**
 * Bütün bölümlerin mekanik denetimi: her bölümün ulaşılabilir BÜTÜN durumları gezilir.
 * Çözümsüzlük, çıkmaz/tersinmez hamle, yanlış optimum, hak yetersizliği, ışın kuralı ihlali, ipucu hatası,
 * açılamayan kapı, dışarı bakan kaynak, kopya düzen, işlevsiz döner ayna ve günlük bulmacalar denetlenir.
 */
public class LevelAuditTest {
    @Test public void everyLevelPassesFullAudit() throws Exception {
        Audit.Report r = Audit.run(TestUtil.pack(), 30);
        System.out.println("Denetim: " + r.levels + " bölüm, " + r.states + " durum, " + r.ms / 1000 + " sn; HATA "
                + r.count("HATA") + ", UYARI " + r.count("UYARI") + ", BİLGİ " + r.count("BİLGİ"));
        StringBuilder bad = new StringBuilder();
        for (Audit.Finding f : r.findings) if (!f.severity.equals("BİLGİ")) bad.append(f).append('\n');
        assertEquals("denetim bulguları:\n" + bad, 0, r.count("HATA") + r.count("UYARI"));
    }
}
