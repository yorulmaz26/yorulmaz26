package com.yeraltiimparatorlugu.game

import com.badlogic.gdx.graphics.Color
import com.yeraltiimparatorlugu.sim.PheromoneType
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
    }

    fun room(t: RoomType): Color = when (t) {
        RoomType.QUEEN_CHAMBER -> hex("FFD9C7")
        RoomType.FOOD_STORAGE -> hex("FFF0B8")
        RoomType.MATERIAL_STORAGE -> hex("EDE1CF")
        RoomType.REST_ROOM -> hex("DCE6FF")
        RoomType.EGG_ROOM -> hex("FFE3EE")
        RoomType.LARVA_ROOM -> hex("D9F5E5")
        RoomType.FUNGUS_FARM -> hex("D6F0C2")
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
    }

    fun pheromone(t: PheromoneType): Color = when (t) {
        PheromoneType.FOOD -> hex("5DBB63")
        PheromoneType.DIG -> hex("F59E3D")
        PheromoneType.AVOID -> hex("E86A5A")
        PheromoneType.RALLY -> hex("4F8DE8")
    }
}
