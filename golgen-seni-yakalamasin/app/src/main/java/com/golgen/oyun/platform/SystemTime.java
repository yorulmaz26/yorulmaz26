package com.golgen.oyun.platform;

import android.content.ContentResolver;
import android.os.SystemClock;
import android.provider.Settings;

import com.golgen.oyun.core.TimeSource;

/**
 * Android zaman kaynakları. elapsedRealtime kullanıcı tarafından değiştirilemez ve uyku dahil sayar;
 * BOOT_COUNT yeniden başlatmayı tespit eder.
 */
public final class SystemTime implements TimeSource {
    private final ContentResolver resolver;

    public SystemTime(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Override public long wallMs() { return System.currentTimeMillis(); }

    @Override public long elapsedMs() { return SystemClock.elapsedRealtime(); }

    @Override public int bootCount() {
        try {
            return Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
