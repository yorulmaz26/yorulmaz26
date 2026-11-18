package com.isikyolu.engine;

/** Hücre türleri. */
public final class Cell {
    public static final int EMPTY = 0;
    public static final int WALL = 1;
    public static final int SOURCE = 2;
    public static final int TARGET = 3;
    public static final int MIRROR = 4;
    public static final int FILTER = 5;
    public static final int PRISM = 6;
    public static final int BLOCK = 7;
    public static final int KEY = 8;
    public static final int DOOR = 9;

    private static final String[] CODES = {"E", "W", "S", "T", "M", "F", "P", "B", "K", "D"};

    private Cell() {}

    public static String code(int type) { return CODES[type]; }

    public static int fromCode(String c) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(c)) return i;
        throw new IllegalArgumentException("Bilinmeyen hücre kodu: " + c);
    }
}
