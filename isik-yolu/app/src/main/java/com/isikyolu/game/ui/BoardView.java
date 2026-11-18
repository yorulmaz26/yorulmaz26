package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.isikyolu.engine.BeamTracer;
import com.isikyolu.engine.Board;
import com.isikyolu.engine.Cell;
import com.isikyolu.engine.Dir;
import com.isikyolu.engine.GameSession;
import com.isikyolu.engine.LightColor;
import com.isikyolu.engine.Move;
import com.isikyolu.game.Audio;
import com.isikyolu.game.Game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bulmaca tahtasının çizimi ve dokunmatik kontrolü. Motor durumunu yalnızca okur;
 * hamleler Host üzerinden oturuma iletilir. Tüm çizim nesneleri önceden ayrılır (60 FPS).
 */
public final class BoardView extends View {

    public interface Host {
        boolean onRotate(int x, int y);
        boolean onPush(int x, int y, int dir);
        void onBlockTapped();
    }

    private static final int MAXP = 280;
    private static final float REVEAL_SPEED = 90f; // yarım hücre / saniye

    private GameSession session;
    private Host host;
    private boolean inputEnabled = true;

    // yerleşim
    private float cell, ox, oy;
    private int gw, gh;

    // animasyon durumu
    private long lastNanos;
    private float time;
    private float[] mirrorAngle, mirrorTarget, doorAnim, targetGlow, pulse, blockOffX, blockOffY;
    private BeamTracer.Result shown;
    private boolean[] segNew = new boolean[0];
    private Set<Long> segKeys = new HashSet<>();
    private float reveal = 1e9f;
    private int revealBase;
    private boolean[] wasSatisfied;

    // ipucu
    private int hintCell = -1, hintKind = -1, hintDir;
    private float hintTime;

    // sürükleme
    private int touchCell = -1;
    private boolean dragging, dragMoved;
    private float downX, downY, dragX0, dragY0, dragOffX, dragOffY;
    private final float slop;

    // parçacıklar
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP],
            plife = new float[MAXP], pmax = new float[MAXP], psize = new float[MAXP];
    private final int[] pcol = new int[MAXP];
    private int pcount;

    // kazanma
    private boolean winMode;
    private float winTime;

    // boyalar
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public BoardView(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        beam.setStyle(Paint.Style.STROKE);
        beam.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setHost(Host h) { host = h; }
    public void setInputEnabled(boolean e) { inputEnabled = e; }

    /** Yeni oturum veya sıfırlama sonrasında tüm görsel durumu kurar. */
    public void bind(GameSession s) {
        session = s;
        Board b = s.board();
        gw = b.width; gh = b.height;
        int n = gw * gh;
        mirrorAngle = new float[n]; mirrorTarget = new float[n]; doorAnim = new float[n]; targetGlow = new float[n];
        pulse = new float[n]; blockOffX = new float[n]; blockOffY = new float[n]; wasSatisfied = new boolean[n];
        for (int c = 0; c < n; c++) {
            if (b.typeAt(c) == Cell.MIRROR) mirrorAngle[c] = mirrorTarget[c] = b.orientAt(c) == 0 ? -45f : 45f;
        }
        shown = null;
        segKeys.clear();
        winMode = false;
        pcount = 0;
        clearHint();
        refreshBeams(true);
        requestLayout();
        invalidate();
    }

    public void showHint(Move m) {
        if (m == null) { clearHint(); return; }
        hintCell = m.cell; hintKind = m.kind; hintDir = m.dir; hintTime = 0;
        invalidate();
    }

    public void clearHint() { hintCell = -1; }

    public boolean hasHint() { return hintCell >= 0; }

    /** Hamle sonrası (veya geri alma) animasyon güncellemesi. */
    public void onMove(Move m, boolean undo) {
        Board b = session.board();
        if (m != null) {
            if (m.kind == Move.ROTATE) {
                mirrorTarget[m.cell] += undo ? -90f : 90f;
                pulse[m.cell] = 1f;
                float[] p = center(m.cell);
                emit(p[0], p[1], Palette.TURQUOISE, 6, cell * 1.2f, 0.35f);
            } else {
                int from = undo ? b.cell(m.cell % gw + Dir.DX[m.dir], m.cell / gw + Dir.DY[m.dir]) : m.cell;
                int to = undo ? m.cell : b.cell(m.cell % gw + Dir.DX[m.dir], m.cell / gw + Dir.DY[m.dir]);
                int fx = from % gw, fy = from / gw, tx = to % gw, ty = to / gw;
                if (!(dragging && !undo)) {
                    blockOffX[to] = (fx - tx) * cell;
                    blockOffY[to] = (fy - ty) * cell;
                } else {
                    blockOffX[to] = 0; blockOffY[to] = 0;
                }
                blockOffX[from] = 0; blockOffY[from] = 0;
            }
        } else {
            // sıfırlama: ayna açılarını doğrudan ayarla
            for (int c = 0; c < gw * gh; c++) if (b.typeAt(c) == Cell.MIRROR) {
                float want = b.orientAt(c) == 0 ? -45f : 45f;
                float cur = norm180(mirrorTarget[c]);
                if (Math.abs(cur - want) > 1f) mirrorTarget[c] += 90f;
                blockOffX[c] = blockOffY[c] = 0;
            }
        }
        if (hintCell >= 0) clearHint();
        refreshBeams(false);
        invalidate();
    }

    private static float norm180(float a) {
        float r = a % 180f;
        if (r > 90f) r -= 180f;
        if (r <= -90f) r += 180f;
        return r;
    }

    private static long segKey(BeamTracer.Segment s) {
        return ((long) s.x1 << 48) ^ ((long) s.y1 << 36) ^ ((long) s.x2 << 24) ^ ((long) s.y2 << 12) ^ s.color;
    }

    private void refreshBeams(boolean initial) {
        BeamTracer.Result r = session.result();
        List<BeamTracer.Segment> segs = r.segments;
        if (segNew.length < segs.size()) segNew = new boolean[segs.size() + 16];
        Set<Long> keys = new HashSet<>();
        int base = Integer.MAX_VALUE;
        for (int i = 0; i < segs.size(); i++) {
            long k = segKey(segs.get(i));
            keys.add(k);
            segNew[i] = initial || !segKeys.contains(k);
            if (segNew[i]) base = Math.min(base, segs.get(i).startDist);
        }
        segKeys = keys;
        revealBase = base == Integer.MAX_VALUE ? 0 : base;
        reveal = 0;
        shown = r;
        // yeni yanan hedefler
        Board b = session.board();
        for (int t : b.targetCells()) {
            boolean sat = r.received[t] == b.colorAt(t);
            if (sat && !wasSatisfied[t] && !initial) {
                float[] p = center(t);
                emit(p[0], p[1], Palette.light(b.colorAt(t)), 22, cell * 2.4f, 0.8f);
                Game.audio().play(Audio.TARGET, 1f + 0.1f * (r.satisfied - 1));
            }
            wasSatisfied[t] = sat;
        }
    }

    /** Bölüm tamamlandı efekti. */
    public void celebrate() {
        winMode = true;
        winTime = 0;
        Board b = session.board();
        for (int t : b.targetCells()) {
            float[] p = center(t);
            emit(p[0], p[1], Palette.light(b.colorAt(t)), 40, cell * 4f, 1.2f);
            emit(p[0], p[1], Palette.GOLD, 20, cell * 3f, 1.0f);
        }
        invalidate();
    }

    // ---------- yerleşim ----------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); layoutBoard(); }

    private void layoutBoard() {
        if (gw == 0) return;
        float pad = getResources().getDisplayMetrics().density * 10;
        cell = Math.min((getWidth() - 2 * pad) / gw, (getHeight() - 2 * pad) / gh);
        ox = (getWidth() - cell * gw) / 2f;
        oy = (getHeight() - cell * gh) / 2f;
    }

    private final float[] tmp = new float[2];

    private float[] center(int c) {
        tmp[0] = ox + (c % gw + 0.5f) * cell;
        tmp[1] = oy + (c / gw + 0.5f) * cell;
        return tmp;
    }

    // ---------- çizim ----------

    @Override protected void onDraw(Canvas c) {
        if (session == null) return;
        if (cell == 0) layoutBoard();
        long now = SystemClock.uptimeMillis();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1000f);
        lastNanos = now;
        time += dt;
        reveal += dt * REVEAL_SPEED;
        if (winMode) winTime += dt;
        hintTime += dt;
        step(dt);

        Board b = session.board();
        drawBoardBack(c);
        drawBeams(c, b);
        for (int i = 0; i < gw * gh; i++) drawPiece(c, b, i);
        drawParticles(c, dt);
        if (hintCell >= 0) drawHint(c);
        if (winMode && winTime < 0.6f) {
            fill.setColor(Palette.withAlpha(Palette.WHITE, (int) (60 * (1 - winTime / 0.6f))));
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
        }
        if (isShown()) postInvalidateOnAnimation();
    }

    private void step(float dt) {
        float k = Math.min(1f, dt * 14f);
        Board b = session.board();
        BeamTracer.Result r = session.result();
        for (int i = 0; i < gw * gh; i++) {
            mirrorAngle[i] += (mirrorTarget[i] - mirrorAngle[i]) * k;
            if (b.typeAt(i) == Cell.DOOR) {
                float want = r.doorOpen[b.linkAt(i)] ? 1f : 0f;
                doorAnim[i] += (want - doorAnim[i]) * Math.min(1f, dt * 8f);
            }
            if (b.typeAt(i) == Cell.TARGET) {
                float want = r.received[i] == b.colorAt(i) ? 1f : 0f;
                targetGlow[i] += (want - targetGlow[i]) * Math.min(1f, dt * 6f);
            }
            pulse[i] = Math.max(0, pulse[i] - dt * 3f);
            blockOffX[i] *= (1 - k); blockOffY[i] *= (1 - k);
        }
    }

    private void drawBoardBack(Canvas c) {
        float r = cell * 0.3f;
        rect.set(ox - cell * 0.18f, oy - cell * 0.18f, ox + gw * cell + cell * 0.18f, oy + gh * cell + cell * 0.18f);
        fill.setColor(0xCC0C1424);
        c.drawRoundRect(rect, r, r, fill);
        stroke.setStrokeWidth(cell * 0.03f);
        stroke.setColor(0x4420B8A6);
        c.drawRoundRect(rect, r, r, stroke);
        float g = cell * 0.05f;
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) {
            fill.setColor(((x + y) & 1) == 0 ? Palette.TILE : Palette.TILE_ALT);
            rect.set(ox + x * cell + g, oy + y * cell + g, ox + (x + 1) * cell - g, oy + (y + 1) * cell - g);
            c.drawRoundRect(rect, cell * 0.14f, cell * 0.14f, fill);
        }
    }

    private void drawBeams(Canvas c, Board b) {
        if (shown == null) return;
        List<BeamTracer.Segment> segs = shown.segments;
        float half = cell / 2f;
        float boost = winMode ? 1.35f : 1f;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < segs.size(); i++) {
                BeamTracer.Segment s = segs.get(i);
                float len = s.length();
                float frac = 1f;
                if (i < segNew.length && segNew[i]) {
                    float prog = reveal - (s.startDist - revealBase);
                    if (prog <= 0) continue;
                    frac = Math.min(1f, prog / len);
                }
                float x1 = ox + s.x1 * half, y1 = oy + s.y1 * half;
                float x2 = x1 + (s.x2 - s.x1) * half * frac, y2 = y1 + (s.y2 - s.y1) * half * frac;
                int col = Palette.light(s.color);
                float shimmer = 1f + 0.08f * (float) Math.sin(time * 5f + s.startDist * 0.4f);
                if (pass == 0) {
                    beam.setStrokeWidth(cell * 0.36f * shimmer * boost);
                    beam.setColor(Palette.withAlpha(col, 42));
                } else if (pass == 1) {
                    beam.setStrokeWidth(cell * 0.17f * boost);
                    beam.setColor(Palette.withAlpha(col, 150));
                } else {
                    beam.setStrokeWidth(cell * 0.06f * boost);
                    beam.setColor(Palette.lighten(col, 0.65f));
                }
                c.drawLine(x1, y1, x2, y2, beam);
                if (pass == 2 && frac >= 1f && len > 0) {
                    // akan foton noktaları
                    float period = 5f;
                    float s0 = ((time * 7f - s.startDist) % period + period) % period;
                    fill.setColor(Palette.withAlpha(Palette.WHITE, 210));
                    for (float d = s0; d <= len; d += period) {
                        float t = d / len;
                        c.drawCircle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, cell * 0.035f, fill);
                    }
                }
            }
        }
    }

    private void drawPiece(Canvas c, Board b, int i) {
        int t = b.typeAt(i);
        if (t == Cell.EMPTY) return;
        float cx = ox + (i % gw + 0.5f) * cell, cy = oy + (i / gw + 0.5f) * cell;
        BeamTracer.Result r = session.result();
        switch (t) {
            case Cell.WALL: drawWall(c, cx, cy); break;
            case Cell.SOURCE: drawSource(c, cx, cy, b.dirAt(i), b.colorAt(i)); break;
            case Cell.TARGET: drawTarget(c, cx, cy, i, b.colorAt(i), r.received[i]); break;
            case Cell.MIRROR: drawMirror(c, cx, cy, i, b.isRotatable(i), r.lit[i]); break;
            case Cell.FILTER: drawFilter(c, cx, cy, b.colorAt(i), r.lit[i]); break;
            case Cell.PRISM: drawPrism(c, cx, cy, r.lit[i]); break;
            case Cell.BLOCK: drawBlock(c, cx, cy, i); break;
            case Cell.KEY: drawKey(c, cx, cy, b.linkAt(i), r.keyLit[b.linkAt(i)]); break;
            case Cell.DOOR: drawDoor(c, cx, cy, i, b.linkAt(i)); break;
            default: break;
        }
    }

    private void drawWall(Canvas c, float cx, float cy) {
        float h = cell * 0.47f, rr = cell * 0.12f;
        // gölge + yan yüz + üst yüz: tahtadan kabarık görünür
        rect.set(cx - h, cy - h + cell * 0.04f, cx + h, cy + h + cell * 0.04f);
        fill.setColor(0xFF070B14);
        c.drawRoundRect(rect, rr, rr, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h);
        fill.setColor(0xFF2A3554);
        c.drawRoundRect(rect, rr, rr, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h - cell * 0.1f);
        fill.setColor(0xFF46557A);
        c.drawRoundRect(rect, rr, rr, fill);
        // tuğla deseni
        stroke.setStrokeWidth(cell * 0.03f);
        stroke.setColor(0xFF2E3A5A);
        float top = cy - h, bot = cy + h - cell * 0.1f, mid = (top + bot) / 2f;
        c.drawLine(cx - h * 0.85f, mid, cx + h * 0.85f, mid, stroke);
        c.drawLine(cx, top + cell * 0.06f, cx, mid, stroke);
        c.drawLine(cx - h * 0.5f, mid, cx - h * 0.5f, bot - cell * 0.05f, stroke);
        c.drawLine(cx + h * 0.5f, mid, cx + h * 0.5f, bot - cell * 0.05f, stroke);
        stroke.setColor(0x40FFFFFF);
        stroke.setStrokeWidth(cell * 0.025f);
        c.drawLine(cx - h * 0.7f, top + cell * 0.035f, cx + h * 0.7f, top + cell * 0.035f, stroke);
    }

    private void drawSource(Canvas c, float cx, float cy, int d, int color) {
        int col = Palette.light(color);
        float h = cell * 0.4f, rr = cell * 0.16f;
        fill.setColor(Palette.withAlpha(col, 40));
        c.drawCircle(cx, cy, cell * 0.5f, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h);
        fill.setColor(0xFF26304A);
        c.drawRoundRect(rect, rr, rr, fill);
        stroke.setStrokeWidth(cell * 0.04f);
        stroke.setColor(Palette.withAlpha(col, 220));
        c.drawRoundRect(rect, rr, rr, stroke);
        // yön çıkıntısı
        float ex = cx + Dir.DX[d] * h, ey = cy + Dir.DY[d] * h;
        float px1 = -Dir.DY[d], py1 = Dir.DX[d];
        path.reset();
        path.moveTo(ex + Dir.DX[d] * cell * 0.1f, ey + Dir.DY[d] * cell * 0.1f);
        path.lineTo(ex + px1 * cell * 0.13f, ey + py1 * cell * 0.13f);
        path.lineTo(ex - px1 * cell * 0.13f, ey - py1 * cell * 0.13f);
        path.close();
        fill.setColor(col);
        c.drawPath(path, fill);
        float pul = 1f + 0.08f * (float) Math.sin(time * 4f);
        fill.setColor(Palette.withAlpha(col, 90));
        c.drawCircle(cx, cy, cell * 0.22f * pul, fill);
        fill.setColor(Palette.lighten(col, 0.5f));
        c.drawCircle(cx, cy, cell * 0.13f, fill);
    }

    private void drawTarget(Canvas c, float cx, float cy, int i, int need, int got) {
        int col = Palette.light(need);
        float g = targetGlow[i];
        if (g > 0.01f) {
            float pul = 1f + 0.12f * (float) Math.sin(time * 6f);
            fill.setColor(Palette.withAlpha(col, (int) (70 * g)));
            c.drawCircle(cx, cy, cell * 0.5f * pul, fill);
        }
        fill.setColor(0xFF121A2C);
        c.drawCircle(cx, cy, cell * 0.36f, fill);
        stroke.setStrokeWidth(cell * 0.07f);
        stroke.setColor(col);
        c.drawCircle(cx, cy, cell * 0.33f, stroke);
        stroke.setStrokeWidth(cell * 0.025f);
        stroke.setColor(Palette.withAlpha(col, 120));
        c.drawCircle(cx, cy, cell * 0.2f, stroke);
        // karışım renkleri için bileşen noktaları
        if (!LightColor.isPrimary(need) && need != LightColor.WHITE) {
            int k = 0;
            for (int comp : new int[]{LightColor.RED, LightColor.GREEN, LightColor.BLUE}) {
                if ((need & comp) == 0) continue;
                float a = (float) (-Math.PI / 2 + (k == 0 ? -0.5 : 0.5));
                fill.setColor(Palette.light(comp));
                c.drawCircle(cx + (float) Math.cos(a) * cell * 0.33f, cy + (float) Math.sin(a) * cell * 0.33f, cell * 0.07f, fill);
                k++;
            }
        }
        if (got != 0 && got != need) {
            fill.setColor(Palette.withAlpha(Palette.light(got), 170));
            c.drawCircle(cx, cy, cell * 0.1f, fill);
        }
        if (g > 0.01f) {
            fill.setColor(Palette.withAlpha(Palette.lighten(col, 0.5f), (int) (255 * g)));
            c.drawCircle(cx, cy, cell * 0.17f * g, fill);
            // dönen ışıltı
            stroke.setStrokeWidth(cell * 0.03f);
            stroke.setColor(Palette.withAlpha(Palette.WHITE, (int) (200 * g)));
            float a = time * 2.2f;
            for (int k = 0; k < 4; k++) {
                double aa = a + k * Math.PI / 2;
                float r1 = cell * 0.4f, r2 = cell * 0.47f;
                c.drawLine(cx + (float) Math.cos(aa) * r1, cy + (float) Math.sin(aa) * r1,
                        cx + (float) Math.cos(aa) * r2, cy + (float) Math.sin(aa) * r2, stroke);
            }
        }
    }

    private void drawMirror(Canvas c, float cx, float cy, int i, boolean rot, int lit) {
        if (lit != 0) {
            fill.setColor(Palette.withAlpha(Palette.light(lit), 46));
            c.drawCircle(cx, cy, cell * 0.44f, fill);
        }
        if (!rot) {
            float h = cell * 0.38f;
            rect.set(cx - h, cy - h, cx + h, cy + h);
            fill.setColor(0xFF262F45);
            c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, fill);
            fill.setColor(0xFF4A5570);
            float q = h * 0.72f;
            c.drawCircle(cx - q, cy - q, cell * 0.035f, fill);
            c.drawCircle(cx + q, cy - q, cell * 0.035f, fill);
            c.drawCircle(cx - q, cy + q, cell * 0.035f, fill);
            c.drawCircle(cx + q, cy + q, cell * 0.035f, fill);
        } else {
            stroke.setStrokeWidth(cell * 0.025f);
            stroke.setColor(Palette.withAlpha(Palette.TURQUOISE, 70 + (int) (120 * pulse[i])));
            c.drawCircle(cx, cy, cell * (0.4f + 0.05f * pulse[i]), stroke);
        }
        c.save();
        c.rotate(mirrorAngle[i], cx, cy);
        float L = cell * 0.4f, T = cell * 0.075f;
        rect.set(cx - L, cy - T, cx + L, cy + T + cell * 0.03f);
        fill.setColor(rot ? 0xFF6F7FA0 : 0xFF4E5873);
        c.drawRoundRect(rect, T, T, fill);
        rect.set(cx - L, cy - T, cx + L, cy + T);
        fill.setColor(rot ? Palette.MIRROR : Palette.MIRROR_FIXED);
        c.drawRoundRect(rect, T, T, fill);
        stroke.setStrokeWidth(cell * 0.02f);
        stroke.setColor(0xAAFFFFFF);
        c.drawLine(cx - L * 0.8f, cy - T * 0.4f, cx + L * 0.8f, cy - T * 0.4f, stroke);
        c.restore();
        if (rot) {
            fill.setColor(Palette.TURQUOISE);
            c.drawCircle(cx, cy, cell * 0.06f, fill);
        }
    }

    private void drawFilter(Canvas c, float cx, float cy, int color, int lit) {
        int col = Palette.light(color);
        float h = cell * 0.34f;
        if (lit != 0) {
            fill.setColor(Palette.withAlpha(col, 50));
            c.drawCircle(cx, cy, cell * 0.48f, fill);
        }
        rect.set(cx - h, cy - h, cx + h, cy + h);
        fill.setColor(Palette.withAlpha(col, 85));
        c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, fill);
        stroke.setStrokeWidth(cell * 0.05f);
        stroke.setColor(Palette.withAlpha(col, 235));
        c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, stroke);
        stroke.setStrokeWidth(cell * 0.03f);
        stroke.setColor(0x88FFFFFF);
        c.drawLine(cx - h * 0.6f, cy + h * 0.1f, cx + h * 0.1f, cy - h * 0.6f, stroke);
    }

    private void drawPrism(Canvas c, float cx, float cy, int lit) {
        float r = cell * 0.4f;
        if (lit != 0) {
            fill.setColor(0x30FFFFFF);
            c.drawCircle(cx, cy, cell * 0.48f, fill);
        }
        path.reset();
        path.moveTo(cx, cy - r);
        path.lineTo(cx + r * 0.95f, cy + r * 0.72f);
        path.lineTo(cx - r * 0.95f, cy + r * 0.72f);
        path.close();
        fill.setColor(0x55B8C8FF);
        c.drawPath(path, fill);
        stroke.setStrokeWidth(cell * 0.045f);
        stroke.setColor(0xEEFFFFFF);
        c.drawPath(path, stroke);
        int[] cols = {Palette.light(LightColor.RED), Palette.light(LightColor.GREEN), Palette.light(LightColor.BLUE)};
        stroke.setStrokeWidth(cell * 0.035f);
        for (int k = 0; k < 3; k++) {
            stroke.setColor(Palette.withAlpha(cols[k], lit != 0 ? 255 : 150));
            float y = cy + r * (0.15f + 0.17f * k);
            float w = r * (0.25f + 0.2f * k);
            c.drawLine(cx - w, y, cx + w, y, stroke);
        }
    }

    private void drawBlock(Canvas c, float cx, float cy, int i) {
        boolean dragged = dragging && i == touchCell;
        float offX = blockOffX[i] + (dragged ? dragOffX : 0), offY = blockOffY[i] + (dragged ? dragOffY : 0);
        float s = dragged ? 1.06f : 1f;
        float h = cell * 0.42f * s, rr = cell * 0.13f;
        cx += offX; cy += offY;
        rect.set(cx - h, cy - h + cell * 0.06f, cx + h, cy + h + cell * 0.06f);
        fill.setColor(0x66000000);
        c.drawRoundRect(rect, rr, rr, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h);
        fill.setColor(0xFFC9952A);
        c.drawRoundRect(rect, rr, rr, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h - cell * 0.07f);
        fill.setColor(Palette.GOLD);
        c.drawRoundRect(rect, rr, rr, fill);
        fill.setColor(0xFF9C7420);
        float q = h * 0.35f;
        c.drawCircle(cx - q, cy - q - cell * 0.03f, cell * 0.045f, fill);
        c.drawCircle(cx + q, cy - q - cell * 0.03f, cell * 0.045f, fill);
        c.drawCircle(cx - q, cy + q - cell * 0.03f, cell * 0.045f, fill);
        c.drawCircle(cx + q, cy + q - cell * 0.03f, cell * 0.045f, fill);
    }

    private void drawKey(Canvas c, float cx, float cy, int link, boolean lit) {
        int col = Palette.LINKS[link % Palette.LINKS.length];
        float r = cell * 0.3f;
        if (lit) {
            float pul = 1f + 0.1f * (float) Math.sin(time * 6f);
            fill.setColor(Palette.withAlpha(col, 70));
            c.drawCircle(cx, cy, cell * 0.48f * pul, fill);
        }
        path.reset();
        path.moveTo(cx, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r, cy); path.close();
        fill.setColor(lit ? col : Palette.withAlpha(col, 60));
        c.drawPath(path, fill);
        stroke.setStrokeWidth(cell * 0.045f);
        stroke.setColor(col);
        c.drawPath(path, stroke);
        fill.setColor(lit ? 0xFF101827 : col);
        c.drawCircle(cx, cy - r * 0.15f, cell * 0.06f, fill);
        rect.set(cx - cell * 0.025f, cy - r * 0.15f, cx + cell * 0.025f, cy + r * 0.45f);
        c.drawRect(rect, fill);
    }

    private void drawDoor(Canvas c, float cx, float cy, int i, int link) {
        int col = Palette.LINKS[link % Palette.LINKS.length];
        float o = doorAnim[i];
        float h = cell * 0.44f;
        rect.set(cx - h, cy - h, cx + h, cy + h);
        stroke.setStrokeWidth(cell * 0.05f);
        stroke.setColor(Palette.withAlpha(col, (int) (255 - 150 * o)));
        c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, stroke);
        if (o < 0.98f) {
            fill.setColor(Palette.withAlpha(0xFF1B2236, (int) (230 * (1 - o))));
            c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, fill);
            stroke.setStrokeWidth(cell * 0.07f);
            stroke.setColor(Palette.withAlpha(col, (int) (255 * (1 - o))));
            for (int k = -1; k <= 1; k++) {
                float x = cx + k * cell * 0.22f;
                float top = cy - h * 0.75f * (1 - o), bot = cy + h * 0.75f * (1 - o);
                c.drawLine(x, top, x, bot, stroke);
            }
        }
    }

    private void drawHint(Canvas c) {
        float[] p = center(hintCell);
        float cx = p[0], cy = p[1];
        float pul = 0.5f + 0.5f * (float) Math.sin(hintTime * 6f);
        stroke.setStrokeWidth(cell * 0.06f);
        stroke.setColor(Palette.withAlpha(Palette.GOLD, (int) (140 + 115 * pul)));
        c.drawCircle(cx, cy, cell * (0.5f + 0.06f * pul), stroke);
        if (hintKind == Move.PUSH) {
            float ex = cx + Dir.DX[hintDir] * cell * (0.75f + 0.1f * pul), ey = cy + Dir.DY[hintDir] * cell * (0.75f + 0.1f * pul);
            float bx = cx + Dir.DX[hintDir] * cell * 0.45f, by = cy + Dir.DY[hintDir] * cell * 0.45f;
            stroke.setColor(Palette.GOLD);
            stroke.setStrokeWidth(cell * 0.08f);
            c.drawLine(bx, by, ex, ey, stroke);
            float pxx = -Dir.DY[hintDir] * cell * 0.14f, pyy = Dir.DX[hintDir] * cell * 0.14f;
            float bx2 = ex - Dir.DX[hintDir] * cell * 0.16f, by2 = ey - Dir.DY[hintDir] * cell * 0.16f;
            c.drawLine(ex, ey, bx2 + pxx, by2 + pyy, stroke);
            c.drawLine(ex, ey, bx2 - pxx, by2 - pyy, stroke);
        } else {
            // dönüş oku
            stroke.setColor(Palette.GOLD);
            stroke.setStrokeWidth(cell * 0.05f);
            rect.set(cx - cell * 0.62f, cy - cell * 0.62f, cx + cell * 0.62f, cy + cell * 0.62f);
            float a0 = (hintTime * 120f) % 360f;
            c.drawArc(rect, a0, 70, false, stroke);
        }
    }

    // ---------- parçacıklar ----------

    private void emit(float x, float y, int color, int n, float speed, float life) {
        for (int k = 0; k < n; k++) {
            if (pcount >= MAXP) return;
            double a = Math.random() * Math.PI * 2;
            float sp = (float) (speed * (0.3 + Math.random() * 0.7));
            int i = pcount++;
            px[i] = x; py[i] = y;
            pvx[i] = (float) Math.cos(a) * sp; pvy[i] = (float) Math.sin(a) * sp;
            pmax[i] = plife[i] = (float) (life * (0.6 + Math.random() * 0.4));
            psize[i] = (float) (cell * (0.03 + Math.random() * 0.05));
            pcol[i] = color;
        }
    }

    private void drawParticles(Canvas c, float dt) {
        for (int i = 0; i < pcount; ) {
            plife[i] -= dt;
            if (plife[i] <= 0) {
                int last = --pcount;
                px[i] = px[last]; py[i] = py[last]; pvx[i] = pvx[last]; pvy[i] = pvy[last];
                plife[i] = plife[last]; pmax[i] = pmax[last]; psize[i] = psize[last]; pcol[i] = pcol[last];
                continue;
            }
            float damp = (float) Math.pow(0.08, dt);
            pvx[i] *= damp; pvy[i] *= damp;
            pvy[i] += cell * 0.6f * dt;
            px[i] += pvx[i] * dt; py[i] += pvy[i] * dt;
            float a = plife[i] / pmax[i];
            fill.setColor(Palette.withAlpha(pcol[i], (int) (255 * a)));
            c.drawCircle(px[i], py[i], psize[i] * (0.5f + a), fill);
            i++;
        }
    }

    // ---------- dokunma ----------

    private int cellAt(float x, float y) {
        int cx = (int) Math.floor((x - ox) / cell), cy = (int) Math.floor((y - oy) / cell);
        if (cx < 0 || cy < 0 || cx >= gw || cy >= gh) return -1;
        return cy * gw + cx;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (session == null || !inputEnabled || cell == 0) return false;
        Board b = session.board();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                touchCell = cellAt(downX, downY);
                dragging = touchCell >= 0 && b.isMovable(touchCell);
                dragMoved = false;
                dragX0 = downX; dragY0 = downY; dragOffX = dragOffY = 0;
                if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dx = e.getX() - dragX0, dy = e.getY() - dragY0;
                    if (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop) dragMoved = true;
                    int d;
                    float mag;
                    if (Math.abs(dx) > Math.abs(dy)) { d = dx > 0 ? Dir.RIGHT : Dir.LEFT; mag = Math.abs(dx); }
                    else { d = dy > 0 ? Dir.DOWN : Dir.UP; mag = Math.abs(dy); }
                    boolean can = b.canPush(touchCell, d);
                    float lim = can ? cell * 0.55f : cell * 0.12f;
                    float vis = Math.min(mag, lim);
                    dragOffX = Dir.DX[d] * vis; dragOffY = Dir.DY[d] * vis;
                    if (can && mag > cell * 0.5f) {
                        int x = touchCell % gw, y = touchCell / gw;
                        if (host != null && host.onPush(x, y, d)) {
                            touchCell = b.cell(x + Dir.DX[d], y + Dir.DY[d]);
                            dragX0 += Dir.DX[d] * cell; dragY0 += Dir.DY[d] * cell;
                            dragOffX -= Dir.DX[d] * cell; dragOffY -= Dir.DY[d] * cell;
                            if (!inputEnabled) { dragging = false; dragOffX = dragOffY = 0; }
                        }
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    if (!dragMoved && host != null) host.onBlockTapped();
                    if (touchCell >= 0) { blockOffX[touchCell] = dragOffX; blockOffY[touchCell] = dragOffY; }
                    dragging = false; dragOffX = dragOffY = 0;
                } else if (Math.abs(e.getX() - downX) < slop * 2 && Math.abs(e.getY() - downY) < slop * 2) {
                    int c2 = cellAt(e.getX(), e.getY());
                    if (c2 >= 0 && c2 == touchCell && host != null) {
                        if (b.isRotatable(c2)) host.onRotate(c2 % gw, c2 / gw);
                        else if (b.typeAt(c2) == Cell.MIRROR) {
                            pulse[c2] = 0.6f;
                            Game.audio().play(Audio.LOCKED);
                            Game.haptics().error();
                        }
                    }
                }
                touchCell = -1;
                invalidate();
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false; dragOffX = dragOffY = 0; touchCell = -1;
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }
}
