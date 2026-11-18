package com.golgen.oyun.core;

/** Çarpışma testleri için yardımcı geometri fonksiyonları. */
public final class Geometry {
    private Geometry() {}

    public static boolean circleCircle(float x1, float y1, float r1, float x2, float y2, float r2) {
        float dx = x1 - x2, dy = y1 - y2, r = r1 + r2;
        return dx * dx + dy * dy < r * r;
    }

    public static boolean circleRect(float cx, float cy, float r, float left, float top, float right, float bottom) {
        float nx = clamp(cx, left, right);
        float ny = clamp(cy, top, bottom);
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    /** Kalınlığı thick olan doğru parçası (ax,ay)-(bx,by) ile çember çarpışması. */
    public static boolean circleSegment(float cx, float cy, float r, float ax, float ay, float bx, float by, float thick) {
        float abx = bx - ax, aby = by - ay;
        float len2 = abx * abx + aby * aby;
        float t = len2 == 0f ? 0f : ((cx - ax) * abx + (cy - ay) * aby) / len2;
        t = clamp(t, 0f, 1f);
        float px = ax + abx * t, py = ay + aby * t;
        float dx = cx - px, dy = cy - py;
        float rr = r + thick * 0.5f;
        return dx * dx + dy * dy < rr * rr;
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
