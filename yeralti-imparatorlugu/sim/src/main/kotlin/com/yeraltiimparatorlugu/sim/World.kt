package com.yeraltiimparatorlugu.sim

const val CHUNK_SIZE = 32

/**
 * 32x32 hücrelik harita parçası. Yalnızca erişilen parçalar üretilir; oyuncunun değiştirdiği
 * parçalar ([modified]) kayda yazılır, diğerleri tohumdan yeniden üretilir.
 */
class Chunk(val cx: Int, val cy: Int, val cells: ByteArray) {
    val revealed = BooleanArray(CHUNK_SIZE * CHUNK_SIZE)
    var modified = false
    var anyRevealed = false
}

/**
 * Yeraltı dünyası. Koordinatlar: x yatay (sonsuz), y derinlik. y < 0 yüzeyin üstüdür (hava);
 * y = -1 karıncaların yürüdüğü yüzey şerididir. y >= 0 yeraltıdır.
 */
class World(val seed: Long) {
    private val chunks = HashMap<Long, Chunk>()
    private val generator = WorldGenerator(seed)

    /** Arazi her değiştiğinde artar; önbellekler bu sayıyla geçersiz kılınır. */
    var version = 0
        private set

    val loadedChunkCount: Int get() = chunks.size

    fun chunk(cx: Int, cy: Int): Chunk = chunks.getOrPut(cellKey(cx, cy)) { Chunk(cx, cy, generator.generateChunk(cx, cy)) }

    fun get(x: Int, y: Int): Terrain {
        if (y < 0) return Terrain.AIR
        val c = chunk(floorDiv(x, CHUNK_SIZE), floorDiv(y, CHUNK_SIZE))
        return Terrain.of(c.cells[floorMod(y, CHUNK_SIZE) * CHUNK_SIZE + floorMod(x, CHUNK_SIZE)].toInt())
    }

    fun set(x: Int, y: Int, t: Terrain) {
        require(y >= 0) { "Yüzeyin üstü değiştirilemez" }
        val c = chunk(floorDiv(x, CHUNK_SIZE), floorDiv(y, CHUNK_SIZE))
        val i = floorMod(y, CHUNK_SIZE) * CHUNK_SIZE + floorMod(x, CHUNK_SIZE)
        if (c.cells[i].toInt() == t.id) return
        c.cells[i] = t.id.toByte()
        c.modified = true
        version++
    }

    /** Karıncaların yürüyebildiği hücre: yüzey şeridi veya kazılmış boşluk. */
    fun isWalkable(x: Int, y: Int): Boolean = when {
        y == -1 -> true
        y < -1 -> false
        else -> get(x, y).walkable
    }

    fun isRevealed(x: Int, y: Int): Boolean {
        if (y < 0) return true
        val c = chunk(floorDiv(x, CHUNK_SIZE), floorDiv(y, CHUNK_SIZE))
        return c.revealed[floorMod(y, CHUNK_SIZE) * CHUNK_SIZE + floorMod(x, CHUNK_SIZE)]
    }

    /** Kazılan bir hücrenin çevresini keşfedilmiş yapar (gizli damarlar görünür olur). */
    fun reveal(x: Int, y: Int, radius: Int) {
        for (dy in -radius..radius) for (dx in -radius..radius) {
            if (dx * dx + dy * dy > radius * radius + 1) continue
            val yy = y + dy
            if (yy < 0) continue
            val xx = x + dx
            val c = chunk(floorDiv(xx, CHUNK_SIZE), floorDiv(yy, CHUNK_SIZE))
            c.revealed[floorMod(yy, CHUNK_SIZE) * CHUNK_SIZE + floorMod(xx, CHUNK_SIZE)] = true
            c.anyRevealed = true
        }
    }

    fun chunksToSave(): List<Chunk> = chunks.values.filter { it.modified || it.anyRevealed }

    /** Kayıttan parça yükler (yüklenen parça tohumdan üretilenin yerine geçer). */
    fun restoreChunk(cx: Int, cy: Int, cells: ByteArray?, revealed: BooleanArray?) {
        val c = Chunk(cx, cy, cells ?: generator.generateChunk(cx, cy))
        c.modified = cells != null
        if (revealed != null) {
            revealed.copyInto(c.revealed)
            c.anyRevealed = revealed.any { it }
        }
        chunks[cellKey(cx, cy)] = c
        version++
    }

    /** Kullanılmayan, değişmemiş parçaları bellekten atar (yeniden erişimde tohumdan üretilir). */
    fun unloadFarChunks(centerX: Int, centerY: Int, keepRadiusChunks: Int) {
        val ccx = floorDiv(centerX, CHUNK_SIZE)
        val ccy = floorDiv(centerY, CHUNK_SIZE)
        chunks.values.removeAll { !it.modified && !it.anyRevealed && (kotlin.math.abs(it.cx - ccx) > keepRadiusChunks || kotlin.math.abs(it.cy - ccy) > keepRadiusChunks) }
    }
}

/** Tohumdan katmanlı yeraltı üretir. Aynı tohum her zaman aynı dünyayı verir. */
class WorldGenerator(private val seed: Long) {
    private val hardness = ValueNoise(seed, 1)
    private val pockets = ValueNoise(seed, 2)
    private val ores = ValueNoise(seed, 3)
    private val band = ValueNoise(seed, 4)
    private val lakes = ValueNoise(seed, 5)
    private val rivalXs = RivalSystem.nestPositions(seed)

    fun generateChunk(cx: Int, cy: Int): ByteArray {
        val out = ByteArray(CHUNK_SIZE * CHUNK_SIZE)
        for (ly in 0 until CHUNK_SIZE) for (lx in 0 until CHUNK_SIZE) {
            out[ly * CHUNK_SIZE + lx] = terrainAt(cx * CHUNK_SIZE + lx, cy * CHUNK_SIZE + ly).id.toByte()
        }
        return out
    }

    fun terrainAt(x: Int, y: Int): Terrain {
        if (y < 0) return Terrain.AIR
        val depth = y.toDouble()
        val depthFactor = minOf(1.0, depth / 60.0)
        val h = hardness.fbm(x / 16.0, y / 11.0)
        val p = pockets.fbm(x / 9.0, y / 8.0)
        val o = ores.sample(x / 5.0, y / 5.0)
        val startArea = kotlin.math.abs(x) <= 12 && y <= 14

        var t = when {
            y < 3 -> Terrain.SOFT_SOIL
            !startArea && y >= 12 && h + depthFactor * 0.55 - 0.32 > 0.58 -> Terrain.ROCK
            h + depthFactor * 0.45 > 0.56 -> Terrain.HARD_SOIL
            else -> Terrain.SOFT_SOIL
        }
        if (t != Terrain.ROCK && y >= 3) {
            if (p > 0.72) t = if (y < 28) Terrain.SAND else Terrain.CLAY
            else if (p < 0.2 && y > 6) t = Terrain.CLAY
        }
        // Su taşıyan katman: dalgalı yatay bant.
        val bandCenter = 24.0 + (band.sample(x / 30.0, 0.5) - 0.5) * 12.0
        if (!startArea && kotlin.math.abs(depth - bandCenter) < 1.6 && p > 0.3 && t != Terrain.ROCK) t = Terrain.WATER_SOIL
        if (y in 5..60 && o > 0.84 && t != Terrain.ROCK) t = Terrain.FUNGUS
        if (y > 9 && o < 0.1) t = Terrain.MINERAL
        if (y > 16 && Hash.unit(seed, x, y, 77) < 0.005) t = Terrain.FOSSIL
        if (y > 40 && t == Terrain.ROCK && o > 0.8) t = Terrain.RARE_VEIN
        if (!startArea && y > 8 && Hash.unit(seed, x, y, 78) < 0.003) t = Terrain.ANCIENT_NEST
        if (!startArea && y > 14 && t != Terrain.ROCK && Hash.unit(seed, x, y, 79) < 0.0015) t = Terrain.CREATURE_DEN
        if (!startArea && y > 28 && lakes.fbm(x / 7.0, y / 5.0, 2) > 0.8) t = Terrain.WATER_POCKET
        // Rakip koloni tünelleri: yuvalarının altında kazılmış tünel ağları.
        for ((i, rx) in rivalXs.withIndex()) {
            val dx = x - rx
            val reach = if (i % 3 == 0) 16 else 9
            if (kotlin.math.abs(dx) > reach + 1 || y > 20) continue
            if ((dx == 0 && y <= 12) || ((y == 6 || y == 12) && kotlin.math.abs(dx) <= reach) || (y in 13..18 && kotlin.math.abs(dx) <= 3)) t = Terrain.RIVAL_TUNNEL
        }
        return t
    }
}
