package com.isikyolu.engine;

/** Dört yön ve ayna yansıma kuralları. 0=Yukarı, 1=Sağ, 2=Aşağı, 3=Sol. */
public final class Dir {
    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DY = {-1, 0, 1, 0};

    private Dir() {}

    public static int left(int d) { return (d + 3) & 3; }
    public static int right(int d) { return (d + 1) & 3; }
    public static int opposite(int d) { return (d + 2) & 3; }
    public static boolean horizontal(int d) { return d == LEFT || d == RIGHT; }

    /**
     * Aynada yansıma. orient 0 = '/' , orient 1 = '\'.
     * '/' : sağa giden yukarı, yukarı giden sağa, aşağı giden sola, sola giden aşağı döner.
     */
    public static int reflect(int d, int orient) {
        if (orient == 0) {
            switch (d) {
                case UP: return RIGHT;
                case RIGHT: return UP;
                case DOWN: return LEFT;
                default: return DOWN;
            }
        } else {
            switch (d) {
                case UP: return LEFT;
                case LEFT: return UP;
                case DOWN: return RIGHT;
                default: return DOWN;
            }
        }
    }

    /** d yönünden gelen ışını nd yönüne çeviren ayna yönelimi; mümkün değilse -1. */
    public static int orientFor(int d, int nd) {
        if (reflect(d, 0) == nd) return 0;
        if (reflect(d, 1) == nd) return 1;
        return -1;
    }
}
