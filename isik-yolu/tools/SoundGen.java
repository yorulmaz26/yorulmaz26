import java.io.*;
import java.util.Random;

/**
 * Işık Yolu ses üreticisi: tüm ses efektlerini ve arka plan müziğini matematiksel olarak sentezler.
 * Kullanım: java tools/SoundGen.java app/src/main/res/raw
 * Müzik WAV olarak üretilir ve APK boyutu için OGG Vorbis'e çevrilip WAV silinir (aynı adla iki kaynak olamaz):
 *   ffmpeg -i app/src/main/res/raw/music.wav -c:a libvorbis -q:a 6 -metadata ANDROID_LOOP=true app/src/main/res/raw/music.ogg
 *   rm app/src/main/res/raw/music.wav
 */
public class SoundGen {
    static final int SR = 22050;

    public static void main(String[] a) throws IOException {
        File dir = new File(a[0]);
        dir.mkdirs();
        write(new File(dir, "click.wav"), tone(new double[]{1320}, 0.045, 0.002, 60, 0.35));
        write(new File(dir, "rotate.wav"), mixAll(tone(new double[]{1568, 2349}, 0.16, 0.002, 28, 0.4), sweep(900, 1500, 0.06, 0.15)));
        write(new File(dir, "push.wav"), mixAll(tone(new double[]{110, 165}, 0.18, 0.004, 22, 0.6), noise(0.08, 40, 0.18)));
        write(new File(dir, "target.wav"), arp(new double[]{1318.5, 1975.5}, 0.07, 0.5, 0.45));
        write(new File(dir, "win.wav"), arp(new double[]{523.25, 659.25, 783.99, 1046.5, 1318.5}, 0.11, 0.9, 0.42));
        write(new File(dir, "star.wav"), mixAll(tone(new double[]{2093, 3136}, 0.35, 0.003, 10, 0.35), sweep(1800, 2600, 0.12, 0.12)));
        write(new File(dir, "undo.wav"), sweep(880, 440, 0.14, 0.35));
        write(new File(dir, "hint.wav"), arp(new double[]{783.99, 1174.66, 1567.98}, 0.09, 0.45, 0.4));
        write(new File(dir, "locked.wav"), tone(new double[]{196, 207}, 0.16, 0.003, 18, 0.35));
        write(new File(dir, "achieve.wav"), arp(new double[]{659.25, 830.61, 987.77, 1318.5, 1661.2}, 0.08, 1.0, 0.38));
        write(new File(dir, "intro.wav"), intro());
        write(new File(dir, "music.wav"), music());
        System.out.println("Sesler üretildi: " + dir);
    }

    static double[] tone(double[] freqs, double dur, double attack, double decay, double vol) {
        int n = (int) (dur * SR);
        double[] s = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double env = Math.min(1, t / attack) * Math.exp(-decay * t);
            double v = 0;
            for (int k = 0; k < freqs.length; k++) v += Math.sin(2 * Math.PI * freqs[k] * t) / (k + 1);
            s[i] = v * env * vol;
        }
        fadeOut(s, 0.01);
        return s;
    }

    static double[] sweep(double f0, double f1, double dur, double vol) {
        int n = (int) (dur * SR);
        double[] s = new double[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t;
            ph += 2 * Math.PI * f / SR;
            s[i] = Math.sin(ph) * vol * Math.sin(Math.PI * t);
        }
        return s;
    }

    static double[] noise(double dur, double decay, double vol) {
        Random r = new Random(5);
        int n = (int) (dur * SR);
        double[] s = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            lp += (r.nextGaussian() - lp) * 0.15;
            s[i] = lp * vol * Math.exp(-decay * i / (double) SR);
        }
        return s;
    }

    /** Çan sesli arpej. */
    static double[] arp(double[] notes, double step, double tail, double vol) {
        int n = (int) ((step * notes.length + tail) * SR);
        double[] s = new double[n];
        for (int k = 0; k < notes.length; k++) {
            int off = (int) (k * step * SR);
            for (int i = 0; off + i < n; i++) {
                double t = i / (double) SR;
                double env = Math.min(1, t / 0.004) * Math.exp(-5.5 * t);
                double v = Math.sin(2 * Math.PI * notes[k] * t) + 0.35 * Math.sin(2 * Math.PI * notes[k] * 2.01 * t) * Math.exp(-9 * t);
                s[off + i] += v * env * vol * 0.6;
            }
        }
        fadeOut(s, 0.05);
        return s;
    }

    /** 32 saniyelik ortam müziği: yumuşak pad akorları + seyrek kristal notalar (döngüsel). */
    static double[] music() {
        double dur = 32;
        int n = (int) (dur * SR);
        double[] s = new double[n];
        double[][] chords = {
                {220.00, 261.63, 329.63, 440.00},   // Am
                {174.61, 220.00, 261.63, 349.23},   // F
                {196.00, 261.63, 329.63, 392.00},   // C/G
                {196.00, 246.94, 293.66, 392.00}};  // G
        double cl = dur / chords.length;
        for (int ci = 0; ci < chords.length; ci++) {
            int start = (int) (ci * cl * SR);
            int len = (int) ((cl + 2.0) * SR);
            for (int i = 0; i < len; i++) {
                int idx = (start + i) % n; // son akorun kuyruğu başa sarar → kesintisiz döngü
                double t = i / (double) SR;
                double env = Math.min(1, t / 2.0) * Math.min(1, Math.max(0, (cl + 2.0 - t) / 2.5));
                double v = 0;
                for (double f : chords[ci]) {
                    v += Math.sin(2 * Math.PI * f * t) + 0.5 * Math.sin(2 * Math.PI * f * 1.003 * t + 1.3);
                }
                s[idx] += v * env * 0.035;
            }
        }
        // kristal notalar (pentatonik)
        double[] pent = {880, 987.77, 1174.66, 1318.5, 1567.98, 1760};
        Random r = new Random(11);
        for (double t0 = 0.5; t0 < dur - 0.2; t0 += 1.0 + r.nextInt(3) * 0.5) {
            double f = pent[r.nextInt(pent.length)];
            int off = (int) (t0 * SR);
            for (int i = 0; i < (int) (2.5 * SR); i++) {
                int idx = (off + i) % n;
                double t = i / (double) SR;
                double env = Math.min(1, t / 0.01) * Math.exp(-2.2 * t);
                s[idx] += Math.sin(2 * Math.PI * f * t) * env * 0.05;
            }
        }
        // hafif yankı
        int d = (int) (0.37 * SR);
        double[] out = s.clone();
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < n; i++) out[i] += out[(i - d + n) % n] * 0.28;
        normalize(out, 0.6);
        return out;
    }

    /** Stüdyo açılışı: yükselen parıltı + yumuşak çan akoru ("lumos"). */
    static double[] intro() {
        double dur = 2.6;
        int n = (int) (dur * SR);
        double[] s = new double[n];
        Random r = new Random(3);
        // yükselen kristal parıltılar
        for (int k = 0; k < 18; k++) {
            double t0 = 0.02 + k * 0.022;
            double f = 1200 + k * 95 + r.nextInt(60);
            int off = (int) (t0 * SR);
            for (int i = 0; off + i < n && i < (int) (0.6 * SR); i++) {
                double t = i / (double) SR;
                s[off + i] += Math.sin(2 * Math.PI * f * t) * Math.exp(-7 * t) * Math.min(1, t / 0.003) * 0.05;
            }
        }
        // çan akoru (Re majör + oktav)
        double[] ch = {587.33, 739.99, 880.0, 1174.66};
        int off = (int) (0.38 * SR);
        for (int i = 0; off + i < n; i++) {
            double t = i / (double) SR;
            double env = Math.min(1, t / 0.01) * Math.exp(-1.6 * t);
            double v = 0;
            for (int q = 0; q < ch.length; q++) v += Math.sin(2 * Math.PI * ch[q] * t) / (q + 1.5) + 0.3 * Math.sin(2 * Math.PI * ch[q] * 2.005 * t) * Math.exp(-4 * t) / (q + 1.5);
            s[off + i] += v * env * 0.28;
        }
        fadeOut(s, 0.3);
        normalize(s, 0.7);
        return s;
    }

    static double[] mixAll(double[]... parts) {
        int n = 0;
        for (double[] p : parts) n = Math.max(n, p.length);
        double[] s = new double[n];
        for (double[] p : parts) for (int i = 0; i < p.length; i++) s[i] += p[i];
        return s;
    }

    static void fadeOut(double[] s, double sec) {
        int f = Math.min(s.length, (int) (sec * SR));
        for (int i = 0; i < f; i++) s[s.length - 1 - i] *= i / (double) f;
    }

    static void normalize(double[] s, double peak) {
        double m = 1e-9;
        for (double v : s) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < s.length; i++) s[i] = s[i] / m * peak;
    }

    static void write(File f, double[] s) throws IOException {
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)))) {
            int bytes = s.length * 2;
            o.writeBytes("RIFF"); le32(o, 36 + bytes); o.writeBytes("WAVE");
            o.writeBytes("fmt "); le32(o, 16); le16(o, 1); le16(o, 1); le32(o, SR); le32(o, SR * 2); le16(o, 2); le16(o, 16);
            o.writeBytes("data"); le32(o, bytes);
            for (double v : s) le16(o, (int) Math.round(Math.max(-1, Math.min(1, v)) * 32767));
        }
    }

    static void le32(DataOutputStream o, int v) throws IOException { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }
    static void le16(DataOutputStream o, int v) throws IOException { o.write(v); o.write(v >> 8); }
}
