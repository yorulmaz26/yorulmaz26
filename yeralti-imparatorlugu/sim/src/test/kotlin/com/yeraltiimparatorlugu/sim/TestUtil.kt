package com.yeraltiimparatorlugu.sim

fun Simulation.run(seconds: Float) {
    val steps = (seconds / Simulation.STEP).toInt()
    repeat(steps) { step(Simulation.STEP) }
}

fun Simulation.clearSources() { sources.clear() }

/** Testler için odayı anında kurar (alanı kazar, aktif yapar) ve tünelle kraliçe odasına bağlar. */
fun Simulation.instantRoom(type: RoomType, x: Int, y: Int): Room {
    val r = Room(newId(), type, x, y).also { it.state = RoomState.ACTIVE }
    r.forEachCell { xx, yy -> world.set(xx, yy, Terrain.TUNNEL) }
    // Kraliçe odasının alt sırasına (y=9) yatay + dikey bağlantı
    val ty = r.anchorY
    val stepX = if (r.anchorX > 0) -1 else 1
    var cx = r.anchorX
    while (cx != 0) { world.set(cx, ty, Terrain.TUNNEL); cx += stepX }
    val dir = if (ty > 9) -1 else 1
    var cy = ty
    while (cy != 9) { world.set(0, cy, Terrain.TUNNEL); cy += dir }
    rooms.add(r)
    registerRoomCells(r)
    return r
}

fun Simulation.completeAllResearch(vararg ids: String) {
    for (id in ids) research.completed.add(id)
    recomputeModifiers()
}

/** Bol kaynak verir; kapasiteyi aşmaması için ek bir malzeme deposu kurar. */
fun Simulation.richResources() {
    if (rooms.none { it.type == RoomType.MATERIAL_STORAGE }) instantRoom(RoomType.MATERIAL_STORAGE, -17, 9)
    for (t in ResourceType.entries) resources.set(t, if (t.storage == StorageKind.MATERIAL) 8 else 15)
    resources.set(ResourceType.SOIL, 24)
    resources.set(ResourceType.WOOD, 14)
    resources.set(ResourceType.FOOD, 40)
}
