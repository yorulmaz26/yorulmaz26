package com.yeraltiimparatorlugu.sim

/** Hücre koordinatlarını tek bir Long anahtara paketler (harita anahtarları için). */
fun cellKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

fun keyX(key: Long): Int = (key shr 32).toInt()

fun keyY(key: Long): Int = key.toInt()

fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)

fun floorMod(a: Int, b: Int): Int = Math.floorMod(a, b)

/** Dört komşu yönü. */
val DIRS4: Array<IntArray> = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

fun manhattan(ax: Int, ay: Int, bx: Int, by: Int): Int = kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by)
