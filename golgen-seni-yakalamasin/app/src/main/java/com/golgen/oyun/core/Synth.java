package com.golgen.oyun.core;

import java.util.Random;

/**
 * Programatik ses üretimi: tüm ses efektleri ve arka plan müziği kodla sentezlenir
 * (lisans gerektiren harici ses dosyası yoktur). 16 bit mono PCM + WAV başlığı.
 */
public final class Synth {
    private Synth() {}

    public static final int SFX_RATE = 22050;
    public static final int MUSIC_RATE = 22050;

    public static final int TAP = 0, DIAMOND = 1, HIT = 2, SHADOW = 3, DODGE = 4, GAME_OVER = 5, SUCCESS = 6, PURCHASE = 7;
    public static final int SFX_COUNT = 8;

    private static float freq(int midi) {
        return (float) (440.0 * Math.pow(2.0, (midi - 69) / 12.0));
    }

    private static float tri(double phase) {
        double p = phase - Math.floor(phase);
        return (float) (p < 0.5 ? 4 * p - 1 : 3 - 4 * p);
    }

    private static float sq(double phase, double duty) {
        double p = phase - Math.floor(phase);
        return p < duty ? 1f : -1f;
    }

    /** Basit ADSR zarfı. */
    private static float env(float t, float a, float d, float s, float len, float r) {
        if (t < a) return t / a;
        if (t < a + d) return 1f - (1f - s) * ((t - a) / d);
        if (t < len) return s;
        float k = 1f - (t - len) / r;
        return k > 0f ? s * k : 0f;
    }

    public static short[] sfx(int id) {
        int rate = SFX_RATE;
        Random rnd = new Random(id * 31L + 7);
        float dur;
        switch (id) {
            case TAP: dur = 0.07f; break;
            case DIAMOND: dur = 0.32f; break;
            case HIT: dur = 0.35f; break;
            case SHADOW: dur = 0.7f; break;
            case DODGE: dur = 0.3f; break;
            case GAME_OVER: dur = 1.1f; break;
            case SUCCESS: dur = 0.75f; break;
            default: dur = 0.4f; break;
        }
        int n = (int) (dur * rate);
        short[] out = new short[n];
        double ph = 0, ph2 = 0;
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float k = t / dur;
            float v;
            switch (id) {
                case TAP: {
                    ph += 1400.0 / rate;
                    v = tri(ph) * (1f - k) * 0.6f;
                    break;
                }
                case DIAMOND: {
                    float f = t < 0.08f ? freq(88) : freq(95);
                    ph += f / rate;
                    ph2 += f * 2.0 / rate;
                    v = ((float) Math.sin(ph * 2 * Math.PI) * 0.7f + (float) Math.sin(ph2 * 2 * Math.PI) * 0.2f)
                            * env(t, 0.005f, 0.05f, 0.6f, 0.12f, 0.2f);
                    break;
                }
                case HIT: {
                    float noise = rnd.nextFloat() * 2f - 1f;
                    lp += (noise - lp) * 0.25f;
                    ph += (180.0 - 120.0 * k) / rate;
                    v = (lp * 0.8f + sq(ph, 0.5) * 0.35f) * (1f - k) * (1f - k);
                    break;
                }
                case SHADOW: {
                    ph += (220.0 - 150.0 * k) / rate;
                    ph2 += (223.0 - 150.0 * k) / rate;
                    v = (tri(ph) + tri(ph2)) * 0.32f * env(t, 0.15f, 0.2f, 0.7f, 0.45f, 0.25f);
                    break;
                }
                case DODGE: {
                    float noise = rnd.nextFloat() * 2f - 1f;
                    float c = 0.05f + 0.5f * (float) Math.sin(Math.PI * k);
                    lp += (noise - lp) * c;
                    v = lp * 0.55f * (float) Math.sin(Math.PI * k);
                    break;
                }
                case GAME_OVER: {
                    int step = Math.min(3, (int) (t / 0.22f));
                    int[] notes = {67, 63, 60, 55};
                    ph += freq(notes[step]) / rate;
                    float local = t - step * 0.22f;
                    float e = step < 3 ? env(local, 0.01f, 0.1f, 0.5f, 0.18f, 0.04f) : env(local, 0.01f, 0.2f, 0.5f, 0.5f, 0.38f);
                    v = (sq(ph, 0.3) * 0.25f + tri(ph) * 0.4f) * e;
                    break;
                }
                case SUCCESS: {
                    int step = Math.min(3, (int) (t / 0.12f));
                    int[] notes = {72, 76, 79, 84};
                    ph += freq(notes[step]) / rate;
                    float local = t - step * 0.12f;
                    float e = step < 3 ? env(local, 0.005f, 0.05f, 0.6f, 0.1f, 0.02f) : env(local, 0.005f, 0.1f, 0.6f, 0.25f, 0.3f);
                    v = (tri(ph) * 0.5f + sq(ph, 0.25) * 0.15f) * e;
                    break;
                }
                default: { // PURCHASE
                    ph += (freq(79) + 400f * k) / rate;
                    v = (float) Math.sin(ph * 2 * Math.PI) * 0.6f * env(t, 0.005f, 0.08f, 0.5f, 0.2f, 0.2f);
                    break;
                }
            }
            out[i] = toShort(v * 0.9f);
        }
        return out;
    }

    private static short toShort(float v) {
        if (v > 1f) v = 1f;
        if (v < -1f) v = -1f;
        return (short) (v * 32767f);
    }

    /**
     * 16 ölçülük (Am - F - C - G) synthwave döngüsü, 96 BPM.
     * Bas, yumuşak pad ve arpej katmanlarından oluşur; sorunsuz döngü için sonu başa bağlanır.
     */
    public static short[] music() {
        int rate = MUSIC_RATE;
        float bpm = 96f;
        float beat = 60f / bpm;
        int bars = 8;
        float dur = bars * 4 * beat;
        int n = (int) (dur * rate);
        short[] out = new short[n];
        int[][] chords = {{57, 60, 64}, {53, 57, 60}, {48, 52, 55}, {55, 59, 62}};
        int[] bass = {45, 41, 36, 43};
        double[] padPh = new double[3];
        double bassPh = 0, arpPh = 0;
        Random noise = new Random(99);
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            int bar = (int) (t / (4 * beat));
            int chord = (bar / 2) % 4;
            float inBeat = (t % beat) / beat;
            // Pad
            float pad = 0f;
            for (int c = 0; c < 3; c++) {
                padPh[c] += freq(chords[chord][c] + 12) / rate;
                pad += tri(padPh[c]);
            }
            pad *= 0.06f;
            // Bas: sekizlik nabız
            bassPh += freq(bass[chord]) / rate;
            float eighth = (t % (beat / 2)) / (beat / 2);
            float bassEnv = (float) Math.exp(-eighth * 4.0);
            float b = sq(bassPh, 0.5) * 0.12f * bassEnv;
            lp += (b - lp) * 0.08f;
            // Arpej: onaltılık
            float sixteenth = beat / 4f;
            int stepIdx = (int) (t / sixteenth);
            int note = chords[chord][stepIdx % 3] + 24 + ((stepIdx / 3) % 2 == 0 ? 0 : 12);
            arpPh += freq(note) / rate;
            float local = (t % sixteenth) / sixteenth;
            float arp = (float) Math.sin(arpPh * 2 * Math.PI) * 0.05f * (float) Math.exp(-local * 3.0);
            // Hafif tik (hi-hat)
            float hat = 0f;
            if (inBeat > 0.5f && inBeat < 0.53f) hat = (noise.nextFloat() * 2f - 1f) * 0.025f;
            float v = pad + lp * 2.2f + arp + hat;
            // Döngü kenarlarında tıklamayı önlemek için kısa geçiş
            float edge = Math.min(1f, Math.min(t, dur - t) / 0.02f);
            out[i] = toShort(v * edge);
        }
        return out;
    }

    /** PCM verisini WAV (RIFF) baytlarına çevirir. */
    public static byte[] wav(short[] pcm, int rate) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        writeStr(b, 0, "RIFF");
        writeInt(b, 4, 36 + dataLen);
        writeStr(b, 8, "WAVE");
        writeStr(b, 12, "fmt ");
        writeInt(b, 16, 16);
        writeShort(b, 20, 1);          // PCM
        writeShort(b, 22, 1);          // mono
        writeInt(b, 24, rate);
        writeInt(b, 28, rate * 2);     // byte rate
        writeShort(b, 32, 2);          // block align
        writeShort(b, 34, 16);         // bit derinliği
        writeStr(b, 36, "data");
        writeInt(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) {
            b[44 + i * 2] = (byte) (pcm[i] & 0xff);
            b[45 + i * 2] = (byte) ((pcm[i] >> 8) & 0xff);
        }
        return b;
    }

    private static void writeStr(byte[] b, int o, String s) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void writeInt(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    private static void writeShort(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }
}
