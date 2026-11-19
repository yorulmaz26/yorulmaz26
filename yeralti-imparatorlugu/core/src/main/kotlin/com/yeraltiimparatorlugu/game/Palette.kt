package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.yeraltiimparatorlugu.sim.Biome
import com.yeraltiimparatorlugu.sim.CreatureKind
import com.yeraltiimparatorlugu.sim.PheromoneType
import com.yeraltiimparatorlugu.sim.Season
import com.yeraltiimparatorlugu.sim.ResourceType
import com.yeraltiimparatorlugu.sim.RoomType
import com.yeraltiimparatorlugu.sim.Terrain

/** Açık, sıcak ve canlı renk paleti (koyu tema kullanılmaz). */
object Palette {
    fun hex(s: String): Color = Color.valueOf(s)

    val skyTop = hex("8FD3F4")
    val skyBottom = hex("D9F3FF")
    val grass = hex("6CC04A")
    val grassDark = hex("4FA33A")
    val tunnel = hex("F1D7AE")
    val tunnelShade = hex("D9B27F")
    val unexplored = hex("C08453")

    val uiBg = hex("FFF8EC")
    val uiPanel = hex("FFFDF7")
    val uiBorder = hex("E2C9A0")
    val uiText = hex("4A3424")
    val uiTextSoft = hex("8A6E55")
    val accent = hex("2BB3A3")
    val accentDark = hex("1F8E81")
    val orange = hex("F59E3D")
    val yellow = hex("FFD25A")
    val red = hex("E86A5A")
    val green = hex("5DBB63")
    val button = hex("FFE7C2")
    val buttonDown = hex("F7C987")
    val buttonChecked = hex("2BB3A3")

    fun terrain(t: Terrain): Color = when (t) {
        Terrain.AIR -> skyBottom
        Terrain.TUNNEL -> tunnel
        Terrain.SOFT_SOIL -> hex("D39A5E")
        Terrain.HARD_SOIL -> hex("B5784A")
        Terrain.CLAY -> hex("D7805C")
        Terrain.SAND -> hex("F0CF86")
        Terrain.ROCK -> hex("A79C94")
        Terrain.WATER_SOIL -> hex("8FB9C9")
        Terrain.FUNGUS -> hex("C9A66E")
        Terrain.MINERAL -> hex("B07D53")
        Terrain.FOSSIL -> hex("C99566")
        Terrain.RUBBLE -> hex("C4925F")
        Terrain.RARE_VEIN -> hex("9C8FA8")
        Terrain.ANCIENT_NEST -> hex("C9A07A")
        Terrain.CREATURE_DEN -> hex("A9876A")
        Terrain.WATER_POCKET -> hex("7EC8E3")
        Terrain.RIVAL_TUNNEL -> hex("F2C9B8")
        Terrain.FILL -> hex("B98A5E")
    }

    fun room(t: RoomType): Color = when (t) {
        RoomType.QUEEN_CHAMBER -> hex("FFD9C7")
        RoomType.FOOD_STORAGE -> hex("FFF0B8")
        RoomType.MATERIAL_STORAGE -> hex("EDE1CF")
        RoomType.REST_ROOM -> hex("DCE6FF")
        RoomType.EGG_ROOM -> hex("FFE3EE")
        RoomType.LARVA_ROOM -> hex("D9F5E5")
        RoomType.FUNGUS_FARM -> hex("D6F0C2")
        RoomType.BARRACKS -> hex("F6D2CC")
        RoomType.TRAINING_ROOM -> hex("FBE3C8")
        RoomType.RESEARCH_LAB -> hex("D8EEF7")
        RoomType.DEFENSE_ROOM -> hex("E9D9D0")
        RoomType.TRAP_ROOM -> hex("F2D0A6")
        RoomType.VENTILATION -> hex("E3F4F7")
        RoomType.WATER_STORAGE -> hex("CFE9FA")
        RoomType.HUMIDITY_CONTROL -> hex("D7F0EE")
        RoomType.INFIRMARY -> hex("FFE4E4")
        RoomType.EXPLORATION_CENTER -> hex("E4F0D2")
        RoomType.TRADE_ROOM -> hex("FFEFC9")
        RoomType.GENETICS_LAB -> hex("E8DDF7")
        RoomType.WORKSHOP -> hex("EFDCC6")
        RoomType.FOSSIL_MUSEUM -> hex("F3EBDD")
        RoomType.ARCHIVE -> hex("EFE6D6")
        RoomType.FORTRESS -> hex("E2D6CF")
        RoomType.ELITE_QUARTERS -> hex("F7D9E6")
        RoomType.ROYAL_CENTER -> hex("FFE9A8")
    }

    fun resource(t: ResourceType): Color = when (t) {
        ResourceType.FOOD -> hex("E8735A")
        ResourceType.SEED -> hex("E8C16A")
        ResourceType.MUSHROOM -> hex("F2A65A")
        ResourceType.LEAF -> hex("5DBB63")
        ResourceType.WOOD -> hex("A86D3E")
        ResourceType.SOIL -> hex("B5784A")
        ResourceType.WATER -> hex("4FB6E8")
        ResourceType.MINERAL -> hex("2BC4C4")
        ResourceType.FOSSIL -> hex("EFE6D2")
        ResourceType.RESIN -> hex("F0A830")
        ResourceType.ENZYME -> hex("9BD45A")
        ResourceType.RARE_MINERAL -> hex("B07CE8")
        ResourceType.GENETIC -> hex("F06FA8")
        ResourceType.ROYAL_ESSENCE -> hex("FFC93C")
    }

    fun pheromone(t: PheromoneType): Color = when (t) {
        PheromoneType.FOOD -> hex("5DBB63")
        PheromoneType.DIG -> hex("F59E3D")
        PheromoneType.AVOID -> hex("E86A5A")
        PheromoneType.RALLY -> hex("4F8DE8")
        PheromoneType.CARRY -> hex("C9A26A")
        PheromoneType.EXPLORE -> hex("2BB3A3")
        PheromoneType.DANGER -> hex("D9433B")
        PheromoneType.HELP -> hex("F06FA8")
        PheromoneType.ATTACK -> hex("B0302A")
        PheromoneType.DEFENSE -> hex("6B7FD7")
    }

    fun creature(k: CreatureKind): Color = when (k) {
        CreatureKind.RIVAL_SOLDIER -> hex("B5462E")
        CreatureKind.RIVAL_ELITE -> hex("7E2A6E")
        CreatureKind.TERMITE -> hex("E9D7B0")
        CreatureKind.SPIDER -> hex("5E4B7A")
        CreatureKind.CENTIPEDE -> hex("C0603A")
        CreatureKind.CAVE_HUNTER -> hex("6A5A8E")
        CreatureKind.WORM -> hex("F0A0A8")
        CreatureKind.BEETLE_LARVA -> hex("FFF0C8")
        CreatureKind.CAVE_SALAMANDER -> hex("9FD8F0")
    }

    fun ground(b: Biome, season: Season): Color {
        val base = when (b) {
            Biome.MEADOW, Biome.FLOWERS -> grass
            Biome.FOREST -> hex("4FA33A")
            Biome.ROCKY -> hex("A9B08A")
            Biome.DESERT -> hex("E8CF8A")
            Biome.SWAMP -> hex("7FA05A")
            Biome.HUMAN_RUINS -> hex("9DB87A")
            Biome.POND -> hex("6CC04A")
        }
        return when (season) {
            Season.WINTER -> Color(base).lerp(Color.WHITE, 0.65f)
            Season.AUTUMN -> Color(base).lerp(hex("D9A441"), 0.4f)
            Season.SUMMER -> Color(base).lerp(hex("C9D45A"), 0.15f)
            Season.SPRING -> base
        }
    }
}
