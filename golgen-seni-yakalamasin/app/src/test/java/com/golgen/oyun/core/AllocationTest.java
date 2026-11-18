package com.golgen.oyun.core;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Assume;
import org.junit.Test;

/**
 * Oyun döngüsünün (simülasyon) çalışma sırasında bellek tahsis etmediğini doğrular (GC takılmalarını önlemek için).
 * Android birim testleri android.jar ile derlendiğinden JVM yönetim API'sine yansıma ile erişilir.
 */
public class AllocationTest {
    private static Method allocMethod;
    private static Object mxBean;

    private static long allocatedBytes() throws Exception {
        return (Long) allocMethod.invoke(mxBean, Thread.currentThread().getId());
    }

    @Test public void simulationDoesNotAllocatePerFrame() throws Exception {
        try {
            Class<?> mf = Class.forName("java.lang.management.ManagementFactory");
            mxBean = mf.getMethod("getThreadMXBean").invoke(null);
            Class<?> sunMx = Class.forName("com.sun.management.ThreadMXBean");
            Assume.assumeTrue(sunMx.isInstance(mxBean));
            Assume.assumeTrue((Boolean) sunMx.getMethod("isThreadAllocatedMemorySupported").invoke(mxBean));
            sunMx.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(mxBean, true);
            allocMethod = sunMx.getMethod("getThreadAllocatedBytes", long.class);
        } catch (ClassNotFoundException e) {
            Assume.assumeNoException(e);
        }

        GameWorld w = new GameWorld(11);
        w.setArenaHeight(1778f);
        w.start(DifficultyProfile.endless(), null, 11);
        w.shadowsHarmless = true;
        w.obstaclesHarmless = true;
        final int[] events = new int[1];
        w.setEvents((type, x, y) -> events[0]++);
        // Isınma: JIT, havuzlar ve tüm gölgeler oluşsun
        for (int f = 0; f < 60 * 100; f++) { w.moveTargetBy((float) Math.sin(f * 0.05) * 9f, (float) Math.cos(f * 0.03) * 6f); w.update(1f / 60f); }
        long before = allocatedBytes();
        for (int f = 0; f < 60 * 30; f++) { w.moveTargetBy((float) Math.sin(f * 0.05) * 9f, (float) Math.cos(f * 0.03) * 6f); w.update(1f / 60f); }
        long allocated = allocatedBytes() - before;
        // 1800 kare (30 sn) boyunca: kare başına ortalama 8 bayttan az (pratikte 0)
        assertTrue("tahsis=" + allocated + " bayt", allocated < 1800 * 8);
        assertEquals(GameWorld.RUNNING, w.state);
        assertTrue(events[0] > 0);
    }
}
