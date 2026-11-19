package com.yeraltiimparatorlugu.sim

import java.util.PriorityQueue

/**
 * Izgara üzerinde A* yol bulma. Yalnızca yürünebilir hücreler (tüneller, odalar, yüzey şeridi) kullanılır;
 * karıncalar kazılmamış toprağın içinden geçemez. Kaçınma feromonu yol maliyetini artırır.
 *
 * Performans: her arama düğüm sınırıyla kesilir, simülasyon da adım başına arama sayısını sınırlar
 * ([Simulation] içindeki yol bütçesi). Diziler yeniden kullanılarak bellek tahsisi azaltılır.
 */
class Pathfinder(private val world: World, private val pheromones: PheromoneField) {
    private var cap = 4096
    private var xs = IntArray(cap)
    private var ys = IntArray(cap)
    private var g = IntArray(cap)
    private var parent = IntArray(cap)
    private val index = HashMap<Long, Int>(4096)
    private val open = PriorityQueue<Long>(1024)

    var searches = 0L
        private set

    /**
     * (sx, sy) hücresinden (tx, ty) hücresine yol. Sonuç başlangıç hücresini içermez: [x0, y0, x1, y1, ...].
     * Yol yoksa null döner. Başlangıç ve hedef aynıysa boş dizi döner.
     */
    fun find(sx: Int, sy: Int, tx: Int, ty: Int, maxNodes: Int = 12000): IntArray? {
        searches++
        if (sx == tx && sy == ty) return IntArray(0)
        if (!world.isWalkable(tx, ty)) return null
        val avoid = pheromones.hasAvoid()
        index.clear()
        open.clear()
        var count = 0
        fun add(x: Int, y: Int, gs: Int, par: Int): Int {
            if (count == cap) grow()
            xs[count] = x; ys[count] = y; g[count] = gs; parent[count] = par
            index[cellKey(x, y)] = count
            return count++
        }
        val start = add(sx, sy, 0, -1)
        open.add(encode(manhattan(sx, sy, tx, ty) * 10, start))
        var expanded = 0
        while (open.isNotEmpty()) {
            val n = (open.poll() and 0xffffffffL).toInt()
            val cx = xs[n]
            val cy = ys[n]
            if (cx == tx && cy == ty) return build(n)
            if (++expanded > maxNodes) return null
            val cg = g[n]
            for (d in DIRS4) {
                val nx = cx + d[0]
                val ny = cy + d[1]
                if (!world.isWalkable(nx, ny)) continue
                var step = 10
                if (avoid && pheromones.strengthAt(PheromoneType.AVOID, nx, ny) > 0f) step += 80
                val ng = cg + step
                val key = cellKey(nx, ny)
                val existing = index[key]
                if (existing != null) {
                    if (ng >= g[existing]) continue
                    g[existing] = ng
                    parent[existing] = n
                    open.add(encode(ng + manhattan(nx, ny, tx, ty) * 10, existing))
                } else {
                    val id = add(nx, ny, ng, n)
                    open.add(encode(ng + manhattan(nx, ny, tx, ty) * 10, id))
                }
            }
        }
        return null
    }

    private fun encode(f: Int, node: Int): Long = (f.toLong() shl 32) or (node.toLong() and 0xffffffffL)

    private fun build(end: Int): IntArray {
        var len = 0
        var n = end
        while (parent[n] != -1) { len++; n = parent[n] }
        val out = IntArray(len * 2)
        n = end
        var i = len - 1
        while (parent[n] != -1) {
            out[i * 2] = xs[n]
            out[i * 2 + 1] = ys[n]
            i--
            n = parent[n]
        }
        return out
    }

    private fun grow() {
        cap *= 2
        xs = xs.copyOf(cap); ys = ys.copyOf(cap); g = g.copyOf(cap); parent = parent.copyOf(cap)
    }
}
