package com.golgen.oyun.core;

/** Zaman kaynakları (Android'de sistem saatleri, testlerde sahte saatler). */
public interface TimeSource {
    /** Duvar saati (kullanıcı değiştirebilir). */
    long wallMs();

    /** Açılıştan beri geçen monoton süre; kullanıcı değiştiremez, uyku dahil sayar (SystemClock.elapsedRealtime). */
    long elapsedMs();

    /** Cihaz açılış sayacı; yeniden başlatmayı tespit eder (bilinmiyorsa -1). */
    int bootCount();
}
