package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class MotionRecorderTest {
    @Test public void interpolatesBetweenSamples() {
        MotionRecorder r = new MotionRecorder(16);
        r.record(0f, 0f, 0f);
        r.record(1f, 100f, 50f);
        float[] o = new float[2];
        assertTrue(r.sample(0.5f, o));
        assertEquals(50f, o[0], 1e-3);
        assertEquals(25f, o[1], 1e-3);
    }

    @Test public void clampsOutsideRange() {
        MotionRecorder r = new MotionRecorder(8);
        float[] o = new float[2];
        assertFalse(r.sample(0f, o));
        r.record(1f, 10f, 20f);
        r.record(2f, 30f, 40f);
        r.sample(-5f, o);
        assertEquals(10f, o[0], 0);
        r.sample(99f, o);
        assertEquals(30f, o[0], 0);
    }

    @Test public void ringBufferKeepsNewest() {
        MotionRecorder r = new MotionRecorder(10);
        for (int i = 0; i < 25; i++) r.record(i, i * 2f, 0f);
        assertEquals(10, r.size());
        assertEquals(15f, r.oldestTime(), 0);
        assertEquals(24f, r.newestTime(), 0);
        float[] o = new float[2];
        r.sample(20.5f, o);
        assertEquals(41f, o[0], 1e-3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTimeGoingBack() {
        MotionRecorder r = new MotionRecorder(4);
        r.record(2f, 0, 0);
        r.record(1f, 0, 0);
    }
}
