package com.isikyolu.game.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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
        void onFixedMirrorTapped();
        /** Hamle kabul ediliyor mu (ör. tahtanın üstünde pencere yokken). */
        default boolean acceptsInput() { return true; }
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
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader glassShader, glassFixedShader, plateShader, mountShader, frameShader;
    private final android.graphics.Matrix shaderMatrix = new android.graphics.Matrix();
    private Skins.Mirror skinMirror;
    private Skins.Board skinBoard;
    private Skins.Beam skinBeam;
    private String overMirror, overBoard, overBeam;

    /** Önizleme için görünümü geçersiz kıl (null = oyuncunun seçimi). */
    public void setSkinOverride(String mirror, String board, String beamFx) {
        overMirror = mirror; overBoard = board; overBeam = beamFx;
        loadSkins();
        buildShaders();
        invalidate();
    }

    private void loadSkins() {
        com.isikyolu.engine.Progress p = Game.progress();
        skinMirror = Skins.mirror(overMirror != null ? overMirror : p.mirrorSkin);
        skinBoard = Skins.board(overBoard != null ? overBoard : p.boardSkin);
        skinBeam = Skins.beam(overBeam != null ? overBeam : p.beamSkin);
    }

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
    public void setInputEnabled(boolean e) {
        if (!e) cancelTouch(); // süren sürükleme bloğu yarım kaymış bırakmasın
        inputEnabled = e;
    }

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
        loadSkins();
        if (getWidth() > 0) layoutBoard();
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
                // sürüklenerek itildiyse blok parmağın bıraktığı yerden yeni hücreye kayar
                boolean fromDrag = dragging && !undo && from == touchCell;
                blockOffX[to] = (fx - tx) * cell + (fromDrag ? dragOffX : 0);
                blockOffY[to] = (fy - ty) * cell + (fromDrag ? dragOffY : 0);
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
        buildShaders();
    }

    /** Çizimde ayırma yapmamak için gölgelendiriciler boyut değişince bir kez kurulur. */
    private void buildShaders() {
        if (cell <= 0) return;
        if (skinMirror == null) loadSkins();
        float T = cell * 0.085f;
        if (skinMirror.rainbow)
            glassShader = new LinearGradient(-cell * 0.43f, 0, cell * 0.43f, 0, skinMirror.glass, null, Shader.TileMode.MIRROR);
        else
            glassShader = new LinearGradient(0, -T, 0, T, skinMirror.glass, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        glassFixedShader = new LinearGradient(0, -T, 0, T, new int[]{0xFFF2F0EA, 0xFFC9CCD6, 0xFF8E95A8, 0xFF656C80},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        plateShader = new RadialGradient(-cell * 0.1f, -cell * 0.12f, cell * 0.5f, new int[]{0xFF34466E, 0xFF394A74, 0xFF2B3658},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        mountShader = new LinearGradient(0, -cell * 0.4f, 0, cell * 0.4f, 0xFF46526F, 0xFF455275, Shader.TileMode.CLAMP);
        frameShader = new LinearGradient(ox, oy, ox + gw * cell, oy + gh * cell, skinBoard.frame, null, Shader.TileMode.CLAMP);
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
        float r = cell * 0.32f, m = cell * 0.2f;
        rect.set(ox - m, oy - m, ox + gw * cell + m, oy + gh * cell + m);
        // dış parıltı
        stroke.setStrokeWidth(cell * 0.22f);
        stroke.setColor(0x1420B8A6);
        c.drawRoundRect(rect, r, r, stroke);
        fill.setColor(skinBoard.back);
        c.drawRoundRect(rect, r, r, fill);
        if (skinBoard.stars) { // galaksi zemini: sabit desenli yıldızlar
            for (int k = 0; k < 40; k++) {
                float sx = rect.left + ((k * 73) % 97) / 97f * rect.width();
                float sy = rect.top + ((k * 151) % 89) / 89f * rect.height();
                float tw = 0.5f + 0.5f * (float) Math.sin(time * 2f + k);
                fill.setColor(Palette.withAlpha(Palette.WHITE, (int) (60 + 120 * tw)));
                c.drawCircle(sx, sy, cell * (0.012f + 0.01f * (k % 3)), fill);
            }
        }
        // renk geçişli çerçeve
        if (frameShader != null) {
            shaderPaint.setShader(frameShader);
            shaderPaint.setStyle(Paint.Style.STROKE);
            shaderPaint.setStrokeWidth(cell * 0.035f);
            shaderPaint.setAlpha(150);
            c.drawRoundRect(rect, r, r, shaderPaint);
            shaderPaint.setStyle(Paint.Style.FILL);
            shaderPaint.setAlpha(255);
        }
        float g = cell * 0.05f;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                rect.set(ox + x * cell + g, oy + y * cell + g, ox + (x + 1) * cell - g, oy + (y + 1) * cell - g);
                fill.setColor(((x + y) & 1) == 0 ? skinBoard.tileA : skinBoard.tileB);
                if (skinBoard.stars) fill.setAlpha(200);
                c.drawRoundRect(rect, cell * 0.16f, cell * 0.16f, fill);
                // üst kenar ışığı
                stroke.setStrokeWidth(cell * 0.015f);
                stroke.setColor(0x14FFFFFF);
                c.drawLine(rect.left + cell * 0.15f, rect.top + cell * 0.02f, rect.right - cell * 0.15f, rect.top + cell * 0.02f, stroke);
            }
        }
    }

    private void drawBeams(Canvas c, Board b) {
        if (shown == null) return;
        List<BeamTracer.Segment> segs = shown.segments;
        float half = cell / 2f;
        float boost = (winMode ? 1.35f : 1f) * skinBeam.width;
        int photon = skinBeam.photon;
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
                    float pl = photon == 2 ? 1f + 0.35f * (float) Math.sin(time * 14f + s.startDist) : 1f;
                    beam.setStrokeWidth(cell * 0.17f * boost * pl);
                    beam.setColor(Palette.withAlpha(col, 150));
                } else {
                    beam.setStrokeWidth(cell * 0.06f * boost);
                    beam.setColor(Palette.lighten(col, 0.65f));
                }
                c.drawLine(x1, y1, x2, y2, beam);
                if (pass == 2 && frac >= 1f && len > 0) {
                    // akan foton noktaları
                    float period = photon == 1 ? 3f : 5f;
                    float s0 = ((time * (photon == 2 ? 12f : 7f) - s.startDist) % period + period) % period;
                    fill.setColor(photon == 1 ? Palette.withAlpha(0xFFFFE9A8, 230) : photon == 2 ? Palette.withAlpha(Palette.lighten(col, 0.3f), 240) : Palette.withAlpha(Palette.WHITE, 210));
                    for (float d = s0; d <= len; d += period) {
                        float t = d / len;
                        float px = x1 + (x2 - x1) * t, py = y1 + (y2 - y1) * t;
                        if (photon == 1) { // yıldız tozu: dönen küçük yıldızlar
                            float rr = cell * 0.06f;
                            c.save();
                            c.rotate(time * 120f + d * 30f, px, py);
                            path.reset();
                            path.moveTo(px, py - rr); path.lineTo(px + rr * 0.25f, py - rr * 0.25f); path.lineTo(px + rr, py);
                            path.lineTo(px + rr * 0.25f, py + rr * 0.25f); path.lineTo(px, py + rr); path.lineTo(px - rr * 0.25f, py + rr * 0.25f);
                            path.lineTo(px - rr, py); path.lineTo(px - rr * 0.25f, py - rr * 0.25f); path.close();
                            c.drawPath(path, fill);
                            c.restore();
                        } else {
                            c.drawCircle(px, py, cell * (photon == 2 ? 0.05f : 0.035f), fill);
                        }
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
        fill.setColor(0xFF4F5F8E);
        c.drawRoundRect(rect, rr, rr, fill);
        rect.set(cx - h, cy - h, cx + h, cy + h - cell * 0.1f);
        fill.setColor(0xFF8392C2);
        c.drawRoundRect(rect, rr, rr, fill);
        // tuğla deseni
        stroke.setStrokeWidth(cell * 0.03f);
        stroke.setColor(0xFF6474A6);
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
        fill.setColor(0xFF455379);
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
        fill.setColor(0xFF283351);
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
        c.save();
        c.translate(cx, cy);
        int litCol = lit != 0 ? Palette.light(lit) : 0;
        if (lit != 0) {
            fill.setColor(Palette.withAlpha(litCol, 40));
            c.drawCircle(0, 0, cell * 0.47f, fill);
        }
        if (rot) {
            // döner taban: koyu disk, 90° adım çentikleri, turkuaz halka
            shaderPaint.setShader(plateShader);
            c.drawCircle(0, 0, cell * 0.42f, shaderPaint);
            stroke.setStrokeWidth(cell * 0.022f);
            stroke.setColor(Palette.withAlpha(Palette.TURQUOISE, 90 + (int) (150 * pulse[i])));
            c.drawCircle(0, 0, cell * (0.41f + 0.04f * pulse[i]), stroke);
            stroke.setStrokeWidth(cell * 0.03f);
            stroke.setColor(Palette.withAlpha(Palette.TURQUOISE, 170));
            for (int k = 0; k < 4; k++) {
                float a = (float) (k * Math.PI / 2);
                float r1 = cell * 0.35f, r2 = cell * 0.41f;
                c.drawLine((float) Math.cos(a) * r1, (float) Math.sin(a) * r1, (float) Math.cos(a) * r2, (float) Math.sin(a) * r2, stroke);
            }
        } else {
            // sabit yuva: çelik plaka ve cıvatalar
            float h = cell * 0.4f;
            rect.set(-h, -h + cell * 0.03f, h, h + cell * 0.03f);
            fill.setColor(0xFF070B14);
            c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, fill);
            rect.set(-h, -h, h, h);
            shaderPaint.setShader(mountShader);
            c.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, shaderPaint);
            if (pulse[i] > 0.01f) { // dokunuldu ama dönmez: kırmızı halka ve hafif sallanma
                stroke.setStrokeWidth(cell * 0.05f);
                stroke.setColor(Palette.withAlpha(0xFFFF5C7A, (int) (230 * pulse[i])));
                rect.set(-h - cell * 0.03f, -h - cell * 0.03f, h + cell * 0.03f, h + cell * 0.03f);
                c.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, stroke);
                c.translate((float) Math.sin(pulse[i] * 28f) * cell * 0.03f * pulse[i], 0);
            }
            float q = h * 0.74f;
            for (int k = 0; k < 4; k++) {
                float bx = (k & 1) == 0 ? -q : q, by = k < 2 ? -q : q;
                fill.setColor(0xFF394361);
                c.drawCircle(bx, by + cell * 0.008f, cell * 0.038f, fill);
                fill.setColor(0xFF8D98B3);
                c.drawCircle(bx, by, cell * 0.032f, fill);
            }
        }
        c.rotate(mirrorAngle[i]);
        float L = cell * 0.43f, T = cell * 0.085f;
        // gölge
        rect.set(-L, -T + cell * 0.045f, L, T + cell * 0.045f);
        fill.setColor(0x80050810);
        c.drawRoundRect(rect, T, T, fill);
        // metal çerçeve
        rect.set(-L, -T, L, T);
        fill.setColor(rot ? skinMirror.frame : 0xFF6E5B3E);
        c.drawRoundRect(rect, T, T, fill);
        // cam yüzey
        float g = cell * 0.022f;
        rect.set(-L + g, -T + g, L - g, T - g);
        if (rot && skinMirror.rainbow) {
            shaderMatrix.setTranslate((time * cell * 0.6f) % (cell * 1.72f), 0);
            glassShader.setLocalMatrix(shaderMatrix);
        }
        shaderPaint.setShader(rot ? glassShader : glassFixedShader);
        c.drawRoundRect(rect, T - g, T - g, shaderPaint);
        if (lit != 0) {
            fill.setColor(Palette.withAlpha(litCol, 110));
            c.drawRoundRect(rect, T - g, T - g, fill);
        }
        // parlama çizgisi
        stroke.setStrokeWidth(cell * 0.018f);
        stroke.setColor(0xDDFFFFFF);
        c.drawLine(-L * 0.72f, -T * 0.38f, L * 0.55f, -T * 0.38f, stroke);
        stroke.setColor(0x66FFFFFF);
        c.drawLine(L * 0.64f, -T * 0.38f, L * 0.74f, -T * 0.38f, stroke);
        // uç kapakları
        fill.setColor(rot ? skinMirror.cap : 0xFFC9A45A);
        c.drawCircle(-L + T * 0.55f, 0, T * 0.42f, fill);
        c.drawCircle(L - T * 0.55f, 0, T * 0.42f, fill);
        c.rotate(-mirrorAngle[i]);
        // merkez mil
        if (rot) {
            fill.setColor(0xFF233351);
            c.drawCircle(0, 0, cell * 0.07f, fill);
            fill.setColor(Palette.TURQUOISE);
            c.drawCircle(0, 0, cell * 0.052f, fill);
            fill.setColor(0xCCFFFFFF);
            c.drawCircle(-cell * 0.015f, -cell * 0.015f, cell * 0.018f, fill);
        }
        // ışık vurunca dönen yansıma parıltısı
        if (lit != 0) {
            float fl = cell * (0.2f + 0.04f * (float) Math.sin(time * 7f + i));
            c.rotate(time * 40f + i * 17f);
            path.reset();
            path.moveTo(0, -fl); path.lineTo(fl * 0.16f, -fl * 0.16f); path.lineTo(fl, 0); path.lineTo(fl * 0.16f, fl * 0.16f);
            path.lineTo(0, fl); path.lineTo(-fl * 0.16f, fl * 0.16f); path.lineTo(-fl, 0); path.lineTo(-fl * 0.16f, -fl * 0.16f);
            path.close();
            fill.setColor(Palette.withAlpha(Palette.lighten(litCol, 0.6f), 230));
            c.drawPath(path, fill);
        }
        c.restore();
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
        fill.setColor(0x660E0E16);
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
            fill.setColor(Palette.withAlpha(0xFF353F5E, (int) (230 * (1 - o))));
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

    /** Süren dokunmayı iptal eder (ör. can kalmadığında). */
    public void cancelTouch() {
        if (dragging && touchCell >= 0 && blockOffX != null && session != null && session.board().isMovable(touchCell)) {
            blockOffX[touchCell] = dragOffX; blockOffY[touchCell] = dragOffY; // bırakılan yerden hücresine kayar
        }
        dragging = false; pushed = true; dragOffX = dragOffY = 0; touchCell = -1;
        invalidate();
    }

    /** Takip edilen parmak ve bu dokunmada blok itildi mi (bir dokunmada en fazla bir itme). */
    private int pointerId = -1;
    private boolean pushed;

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (session == null || !inputEnabled || cell == 0) return false;
        Board b = session.board();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pointerId = e.getPointerId(0);
                downX = e.getX(); downY = e.getY();
                touchCell = cellAt(downX, downY);
                dragging = touchCell >= 0 && b.isMovable(touchCell);
                dragMoved = false;
                pushed = false;
                dragX0 = downX; dragY0 = downY; dragOffX = dragOffY = 0;
                if (dragging) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int pi = e.findPointerIndex(pointerId);
                if (pi < 0) return true;
                float ex = e.getX(pi), ey = e.getY(pi);
                if (dragging && !pushed) {
                    float dx = ex - dragX0, dy = ey - dragY0;
                    if (Math.abs(ex - downX) > slop || Math.abs(ey - downY) > slop) dragMoved = true;
                    int d;
                    float mag;
                    if (Math.abs(dx) > Math.abs(dy)) { d = dx > 0 ? Dir.RIGHT : Dir.LEFT; mag = Math.abs(dx); }
                    else { d = dy > 0 ? Dir.DOWN : Dir.UP; mag = Math.abs(dy); }
                    boolean can = b.canPush(touchCell, d);
                    float lim = can ? cell * 0.55f : cell * 0.12f;
                    float vis = Math.min(mag, lim);
                    dragOffX = Dir.DX[d] * vis; dragOffY = Dir.DY[d] * vis;
                    if (can && mag > cell * 0.5f && host != null) {
                        if (!host.acceptsInput()) { cancelTouch(); return true; }
                        // bir dokunmada tek itme: titreme ya da uzun/eğik sürükleme fazladan hak yemez
                        pushed = true;
                        int from = touchCell, x = from % gw, y = from / gw;
                        if (host.onPush(x, y, d)) touchCell = b.cell(x + Dir.DX[d], y + Dir.DY[d]);
                        else if (from >= 0) { blockOffX[from] = dragOffX; blockOffY[from] = dragOffY; } // reddedildi: geri kayar
                        dragOffX = dragOffY = 0;
                        dragging = false;
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                // takip edilen parmak kalktı: dokunma biter, kalan parmak itme yapmaz
                if (e.getPointerId(e.getActionIndex()) == pointerId) {
                    if (dragging && touchCell >= 0) { blockOffX[touchCell] = dragOffX; blockOffY[touchCell] = dragOffY; }
                    dragging = false; pushed = true; dragOffX = dragOffY = 0; touchCell = -1; pointerId = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                if (pointerId < 0 || e.getPointerId(0) != pointerId) { touchCell = -1; return true; }
                float ux = e.getX(), uy = e.getY();
                if (dragging) {
                    if (!dragMoved && host != null) host.onBlockTapped();
                    if (touchCell >= 0) { blockOffX[touchCell] = dragOffX; blockOffY[touchCell] = dragOffY; }
                    dragging = false; dragOffX = dragOffY = 0;
                } else if (!pushed && Math.abs(ux - downX) < slop * 2 && Math.abs(uy - downY) < slop * 2) {
                    int c2 = cellAt(ux, uy);
                    if (c2 >= 0 && c2 == touchCell && host != null && host.acceptsInput()) {
                        if (b.isRotatable(c2)) host.onRotate(c2 % gw, c2 / gw);
                        else if (b.typeAt(c2) == Cell.MIRROR) {
                            pulse[c2] = 1f; // sabit ayna: kırmızı titreşim halkası çizilir
                            Game.audio().play(Audio.LOCKED);
                            Game.haptics().error();
                            host.onFixedMirrorTapped();
                        }
                    }
                }
                touchCell = -1;
                pointerId = -1;
                invalidate();
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                pointerId = -1;
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }
}
