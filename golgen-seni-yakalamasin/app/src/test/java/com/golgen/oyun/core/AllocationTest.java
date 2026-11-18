package com.golgen.oyun.core;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;

import org.junit.Assume;
import org.junit.Test;

/** Oyun döngüsünün (simülasyon) çalışma sırasında bellek tahsis etmediğini doğrular (GC takılmalarını önlemek için). */
public class AllocationTest {
    @Test public void simulationDoesNotAllocatePerFrame() {
        java.lang.management.ThreadMXBean raw = ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(raw instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) raw;
        Assume.assumeTrue(mx.isThreadAllocatedMemorySupported());
        mx.setThreadAllocatedMemoryEnabled(true);

        GameWorld w = new GameWorld(11);
        w.setArenaHeight(1778f);
        w.start(DifficultyProfile.endless(), null, 11);
        w.shadowsHarmless = true;
        w.obstaclesHarmless = true;
        final int[] events = new int[1];
        w.setEvents((type, x, y) -> events[0]++);
        // Isınma: JIT, havuzlar ve tüm gölgeler oluşsun
        for (int f = 0; f < 60 * 100; f++) { w.moveTargetBy((float) Math.sin(f * 0.05) * 9f, (float) Math.cos(f * 0.03) * 6f); w.update(1f / 60f); }
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int f = 0; f < 60 * 30; f++) { w.moveTargetBy((float) Math.sin(f * 0.05) * 9f, (float) Math.cos(f * 0.03) * 6f); w.update(1f / 60f); }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        // 1800 kare (30 sn) boyunca: kare başına ortalama 8 bayttan az (pratikte 0)
        assertTrue("tahsis=" + allocated + " bayt", allocated < 1800 * 8);
        assertEquals(GameWorld.RUNNING, w.state);
        assertTrue(events[0] > 0);
    }
}
