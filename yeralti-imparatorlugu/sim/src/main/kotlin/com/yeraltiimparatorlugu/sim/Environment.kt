package com.yeraltiimparatorlugu.sim

enum class Season(val title: String, val baseTemp: Float, val sourceMult: Float, val rainChance: Float) {
    SPRING("İlkbahar", 17f, 1.2f, 0.45f),
    SUMMER("Yaz", 28f, 1.1f, 0.2f),
    AUTUMN("Sonbahar", 14f, 0.9f, 0.4f),
    WINTER("Kış", 3f, 0.45f, 0.25f),
}

enum class Weather(val title: String, val tempDelta: Float, val humidityTarget: Float, val rainInflow: Float) {
    CLEAR("Açık", 0f, 55f, 0f),
    CLOUDY("Bulutlu", -2f, 62f, 0f),
    RAIN("Yağmurlu", -3f, 80f, 0.025f),
    STORM("Sağanak", -5f, 95f, 0.09f),
    DROUGHT("Kuraklık", 6f, 22f, 0f),
}

/**
 * Ekosistem: mevsimler (her biri 3 gün), hava durumu, yüzey ve koloni sıcaklığı, nem ve yeraltı suyu.
 * Su hücre hücre akar: aşağı düşer, yana yayılır, zemin tarafından emilir. Su Deposu odaları suyu toplar.
 */
class Environment {
    companion object {
        const val DAYS_PER_SEASON = 3
        const val MAX_WATER_CELLS = 2500
    }

    var weather = Weather.CLEAR
    var weatherUntil = 300.0
    var surfaceTemp = 18f
    var colonyTemp = 20f
    var humidity = 55f
    var droughtLevel = 0f

    /** Hücre anahtarı → su miktarı (0..1). Yalnızca ıslak hücreler tutulur. */
    val water = HashMap<Long, Float>()
    private var flowTimer = 0f

    fun season(day: Int): Season = Season.entries[((day - 1) / DAYS_PER_SEASON) % 4]

    fun year(day: Int): Int = (day - 1) / (DAYS_PER_SEASON * 4) + 1

    fun waterAt(x: Int, y: Int): Float = water[cellKey(x, y)] ?: 0f

    /** Yavru gelişimi için iklim çarpanı (0.35..1.15): ideal 22–28°C ve %50–70 nem. */
    fun climateFactor(mods: Modifiers): Float {
        val t = colonyTemp
        var tempPenalty = when {
            t < 22f -> (22f - t) / 18f
            t > 28f -> (t - 28f) / 12f
            else -> 0f
        }
        if (t < 22f) tempPenalty *= (1f - mods.coldTolerance).coerceAtLeast(0.2f)
        var humPenalty = when {
            humidity < 50f -> (50f - humidity) / 50f
            humidity > 70f -> (humidity - 70f) / 40f
            else -> 0f
        }
        humPenalty *= (1f - mods.climateTolerance).coerceAtLeast(0.2f)
        val bonus = if (tempPenalty == 0f && humPenalty == 0f) 1.15f else 1f
        return (bonus - tempPenalty * 0.6f - humPenalty * 0.5f).coerceIn(0.35f, 1.15f)
    }

    fun update(sim: Simulation, dt: Float) {
        val season = season(sim.day)
        // Hava değişimi
        if (sim.time >= weatherUntil) chooseWeather(sim, season)
        val dayWave = kotlin.math.sin((sim.dayFraction - 0.25f) * 2f * Math.PI.toFloat()) * 4f
        surfaceTemp = season.baseTemp + dayWave + weather.tempDelta
        if (weather == Weather.DROUGHT) droughtLevel = (droughtLevel + dt / 300f).coerceAtMost(1f) else droughtLevel = (droughtLevel - dt / 200f).coerceAtLeast(0f)

        // Koloni sıcaklığı: derinlik yüzey dalgalanmasını yumuşatır; havalandırma ideale çeker.
        val nest = sim.rooms.firstOrNull { it.type == RoomType.QUEEN_CHAMBER }
        val depth = (nest?.y ?: 8).toFloat()
        var target = surfaceTemp + (17f - surfaceTemp) * minOf(1f, depth / 25f)
        val vent = sim.rooms.filter { it.type == RoomType.VENTILATION && it.functional }.sumOf { it.level }
        if (vent > 0) target += (24f - target) * minOf(0.9f, vent * 0.3f)
        colonyTemp += (target - colonyTemp) * minOf(1f, dt / 60f)

        // Nem: havaya doğru yavaşça; Nem Kontrol Odası su harcayarak %60'a çeker.
        humidity += (weather.humidityTarget - humidity) * minOf(1f, dt / 240f)
        val hum = sim.rooms.filter { it.type == RoomType.HUMIDITY_CONTROL && it.functional }
        if (hum.isNotEmpty() && kotlin.math.abs(humidity - 60f) > 4f) {
            val power = hum.sumOf { it.level }
            val needWater = humidity < 60f
            if (!needWater || sim.resources[ResourceType.WATER] > 0) {
                humidity += (60f - humidity) * minOf(1f, dt * power / 40f)
                if (needWater && sim.rng.nextFloat() < dt * power / 30f) sim.resources.take(ResourceType.WATER, 1)
            }
        }

        flowTimer += dt
        if (flowTimer >= 0.5f) { flowWater(sim, flowTimer); flowTimer = 0f }
    }

    private fun chooseWeather(sim: Simulation, season: Season) {
        val r = sim.rng.nextFloat()
        val old = weather
        weather = when {
            r < season.rainChance * 0.3f -> Weather.STORM
            r < season.rainChance -> Weather.RAIN
            season == Season.SUMMER && r < season.rainChance + 0.22f -> Weather.DROUGHT
            r < 0.75f -> Weather.CLEAR
            else -> Weather.CLOUDY
        }
        if (season == Season.WINTER && weather == Weather.STORM) weather = Weather.CLOUDY
        weatherUntil = sim.time + 180.0 + sim.rng.nextInt(360)
        if (weather != old) announce(sim)
    }

    fun forceWeather(sim: Simulation, w: Weather, seconds: Double) {
        weather = w
        weatherUntil = sim.time + seconds
        announce(sim)
    }

    private fun announce(sim: Simulation) {
        when (weather) {
            Weather.STORM -> { sim.notify("Sağanak başladı! Alt tüneller su basabilir; drenaj kuyusu kazın."); sim.addLog("Sağanak yağmur başladı.") }
            Weather.DROUGHT -> { sim.notify("Kuraklık: yüzeyde yiyecek azalıyor. Mantar üretimini artırın."); sim.addLog("Kuraklık başladı.") }
            Weather.RAIN -> sim.addLog("Yağmur yağıyor.")
            else -> {}
        }
    }

    fun addWater(x: Int, y: Int, amount: Float) {
        if (water.size >= MAX_WATER_CELLS && !water.containsKey(cellKey(x, y))) return
        val k = cellKey(x, y)
        water[k] = ((water[k] ?: 0f) + amount).coerceAtMost(1f)
    }

    /** Hücresel su akışı: aşağı, sonra yana; zemin emer. */
    private fun flowWater(sim: Simulation, dt: Float) {
        val w = sim.world
        // Yağmur girişi: yüzeye açılan hücreler ve su taşıyan katman komşuları.
        if (weather.rainInflow > 0f) {
            for (k in sim.entranceCells()) addWater(keyX(k), keyY(k), weather.rainInflow * dt * 2f)
            val seep = weather.rainInflow * 0.25f * dt * 2f
            for (k in sim.seepCells()) addWater(keyX(k), keyY(k), seep)
        }
        if (water.isEmpty()) return
        val absorbBase = 0.012f * dt * sim.mods.floodAbsorbMult * (1f + droughtLevel * 2f)
        val keys = water.keys.sortedByDescending { keyY(it) } // alttan üste: düşüş tek adımda zincirlenmez
        for (k in keys) {
            var amt = water[k] ?: continue
            val x = keyX(k)
            val y = keyY(k)
            if (!w.isWalkable(x, y) || y < 0) { water.remove(k); continue }
            // Su Deposu suyu toplar.
            val room = sim.roomAt(x, y)
            if (room != null && room.type == RoomType.WATER_STORAGE && room.functional && amt > 0.05f) {
                val take = minOf(amt, 0.2f * dt * room.level)
                val units = (take * 10).toInt().coerceAtLeast(if (take > 0.05f) 1 else 0)
                if (units > 0) sim.addCapped(ResourceType.WATER, units)
                amt -= take
            }
            // Aşağı düş
            if (w.isWalkable(x, y + 1)) {
                val bk = cellKey(x, y + 1)
                val below = water[bk] ?: 0f
                val move = minOf(amt, 1f - below)
                if (move > 0f) { water[bk] = below + move; amt -= move }
            }
            // Yana yayıl
            if (amt > 0.04f) {
                for (dx in intArrayOf(-1, 1)) {
                    if (!w.isWalkable(x + dx, y)) continue
                    val nk = cellKey(x + dx, y)
                    val n = water[nk] ?: 0f
                    if (n < amt) {
                        val move = (amt - n) * 0.25f
                        water[nk] = n + move
                        amt -= move
                    }
                }
            }
            // Emilme: altı katıysa zemin emer (kum çok emer).
            if (!w.isWalkable(x, y + 1)) {
                val below = w.get(x, y + 1)
                val f = when (below) { Terrain.SAND -> 3f; Terrain.WATER_SOIL, Terrain.CLAY, Terrain.ROCK -> 0.3f; else -> 1f }
                amt -= absorbBase * f
            } else amt -= absorbBase * 0.2f
            if (amt < 0.02f) water.remove(k) else water[k] = amt
        }
    }

    /** Çevrimdışı/uzun aralıklarda su çekilir. */
    fun drainAll() = water.clear()
}
