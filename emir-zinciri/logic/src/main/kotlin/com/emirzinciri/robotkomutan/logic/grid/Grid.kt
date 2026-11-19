package com.emirzinciri.robotkomutan.logic.grid

/** Mantıksal kare ızgarada yön. Harita satırları yukarıdan aşağı y'yi artırır. */
enum class Dir(val dx: Int, val dy: Int, val symbol: String) {
    N(0, -1, "↑"), E(1, 0, "→"), S(0, 1, "↓"), W(-1, 0, "←");

    fun left(): Dir = entries[(ordinal + 3) % 4]
    fun right(): Dir = entries[(ordinal + 1) % 4]
    fun opposite(): Dir = entries[(ordinal + 2) % 4]
}

/** Değişmez hücre konumu. */
data class Pos(val x: Int, val y: Int) {
    operator fun plus(d: Dir) = Pos(x + d.dx, y + d.dy)
    fun step(d: Dir, n: Int) = Pos(x + d.dx * n, y + d.dy * n)
    override fun toString() = "($x,$y)"

    companion object {
        /** "x,y" biçimindeki anahtarı çözer. */
        fun parse(key: String): Pos {
            val parts = key.split(',')
            require(parts.size == 2) { "Geçersiz konum anahtarı: $key" }
            return Pos(parts[0].trim().toInt(), parts[1].trim().toInt())
        }
    }
}
