package com.golgen.oyun.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class SynthTest {
    @Test public void allSfxAreAudibleAndBounded() {
        for (int id = 0; id < Synth.SFX_COUNT; id++) {
            short[] pcm = Synth.sfx(id);
            assertTrue(pcm.length > 1000);
            int peak = 0;
            for (short s : pcm) peak = Math.max(peak, Math.abs(s));
            assertTrue("sfx " + id + " peak " + peak, peak > 3000);
        }
    }

    @Test public void musicLoopIsLongAndStartsSilentForSeamlessLoop() {
        short[] m = Synth.music();
        assertTrue(m.length > Synth.MUSIC_RATE * 15);
        assertEquals(0, m[0]);
        int peak = 0;
        for (short s : m) peak = Math.max(peak, Math.abs(s));
        assertTrue(peak > 4000);
    }

    @Test public void wavHeaderIsValid() {
        byte[] b = Synth.wav(new short[]{0, 1000, -1000}, 22050);
        assertEquals('R', b[0]);
        assertEquals('W', b[8]);
        assertEquals(44 + 6, b.length);
        int rate = (b[24] & 0xff) | (b[25] & 0xff) << 8 | (b[26] & 0xff) << 16;
        assertEquals(22050, rate);
        assertEquals((byte) (1000 & 0xff), b[46]);
    }
}
