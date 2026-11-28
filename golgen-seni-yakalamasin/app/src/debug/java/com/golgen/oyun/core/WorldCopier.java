package com.golgen.oyun.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/** GameWorld durumunu başka bir GameWorld örneğine (ileri tahmin için) birebir kopyalar. Oyun koduna dokunmaz. */
final class WorldCopier {
    private static final Field[] SCALARS;
    private static final Field RNG, REC_T, REC_X, REC_Y, REC_HEAD, REC_COUNT, POOL_ITEMS, POOL_ACTIVE, RAND_SEED;

    static {
        try {
            ArrayList<Field> fs = new ArrayList<>();
            for (Field f : GameWorld.class.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) || Modifier.isFinal(m)) continue;
                if (f.getName().equals("events")) continue;
                f.setAccessible(true);
                fs.add(f);
            }
            SCALARS = fs.toArray(new Field[0]);
            RNG = acc(GameWorld.class, "rng");
            REC_T = acc(MotionRecorder.class, "times");
            REC_X = acc(MotionRecorder.class, "xs");
            REC_Y = acc(MotionRecorder.class, "ys");
            REC_HEAD = acc(MotionRecorder.class, "head");
            REC_COUNT = acc(MotionRecorder.class, "count");
            POOL_ITEMS = acc(Pool.class, "items");
            POOL_ACTIVE = acc(Pool.class, "active");
            RAND_SEED = acc(Random.class, "seed");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field acc(Class<?> c, String n) throws Exception {
        Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    static void copy(GameWorld s, GameWorld d) {
        try {
            for (Field f : SCALARS) {
                Class<?> t = f.getType();
                if (t == float.class) f.setFloat(d, f.getFloat(s));
                else if (t == int.class) f.setInt(d, f.getInt(s));
                else if (t == boolean.class) f.setBoolean(d, f.getBoolean(s));
                else if (t == long.class) f.setLong(d, f.getLong(s));
                else f.set(d, f.get(s));
            }
            ((AtomicLong) RAND_SEED.get(RNG.get(d))).set(((AtomicLong) RAND_SEED.get(RNG.get(s))).get());
            MotionRecorder rs = s.recorder, rd = d.recorder;
            int cnt = REC_COUNT.getInt(rs);
            System.arraycopy((float[]) REC_T.get(rs), 0, (float[]) REC_T.get(rd), 0, rs.capacity());
            System.arraycopy((float[]) REC_X.get(rs), 0, (float[]) REC_X.get(rd), 0, rs.capacity());
            System.arraycopy((float[]) REC_Y.get(rs), 0, (float[]) REC_Y.get(rd), 0, rs.capacity());
            REC_HEAD.setInt(rd, REC_HEAD.getInt(rs));
            REC_COUNT.setInt(rd, cnt);
            copyObstacles(s.obstacles, d.obstacles);
            copyDiamonds(s.diamondPool, d.diamondPool);
            copyDiamonds(s.starPool, d.starPool);
            copyShadows(s.shadows, d.shadows);
            copyPowerups(s.powerups, d.powerups);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object[] items(Pool<?> p) throws IllegalAccessException { return (Object[]) POOL_ITEMS.get(p); }

    private static void copyObstacles(Pool<Obstacle> s, Pool<Obstacle> d) throws IllegalAccessException {
        Object[] a = items(s), b = items(d);
        int n = s.size();
        for (int i = 0; i < n; i++) {
            Obstacle x = (Obstacle) a[i], y = (Obstacle) b[i];
            y.type = x.type; y.y = x.y; y.x = x.x; y.gapX = x.gapX; y.gapW = x.gapW; y.w = x.w; y.h = x.h; y.r = x.r;
            y.phase = x.phase; y.speed = x.speed; y.amp = x.amp; y.baseX = x.baseX; y.angle = x.angle; y.passed = x.passed; y.vx = x.vx;
        }
        POOL_ACTIVE.setInt(d, n);
    }

    private static void copyDiamonds(Pool<Diamond> s, Pool<Diamond> d) throws IllegalAccessException {
        Object[] a = items(s), b = items(d);
        int n = s.size();
        for (int i = 0; i < n; i++) {
            Diamond x = (Diamond) a[i], y = (Diamond) b[i];
            y.x = x.x; y.y = x.y; y.phase = x.phase;
        }
        POOL_ACTIVE.setInt(d, n);
    }

    private static void copyShadows(Pool<Shadow> s, Pool<Shadow> d) throws IllegalAccessException {
        Object[] a = items(s), b = items(d);
        int n = s.size();
        for (int i = 0; i < n; i++) {
            Shadow x = (Shadow) a[i], y = (Shadow) b[i];
            y.delay = x.delay; y.spawnTime = x.spawnTime; y.x = x.x; y.y = x.y; y.dodgeCooldown = x.dodgeCooldown; y.wasClose = x.wasClose;
        }
        POOL_ACTIVE.setInt(d, n);
    }

    private static void copyPowerups(Pool<PowerUp> s, Pool<PowerUp> d) throws IllegalAccessException {
        Object[] a = items(s), b = items(d);
        int n = s.size();
        for (int i = 0; i < n; i++) {
            PowerUp x = (PowerUp) a[i], y = (PowerUp) b[i];
            y.type = x.type; y.x = x.x; y.y = x.y; y.phase = x.phase;
        }
        POOL_ACTIVE.setInt(d, n);
    }
}
