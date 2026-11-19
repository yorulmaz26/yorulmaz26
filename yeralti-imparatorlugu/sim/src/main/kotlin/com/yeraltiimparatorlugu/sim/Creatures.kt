package com.yeraltiimparatorlugu.sim

/** Karıncalar dışındaki canlılar ve rakip akıncılar. Bazıları düşman, bazıları av/kaynaktır. */
enum class CreatureKind(
    val title: String,
    val hp: Float,
    val attack: Float,
    val defense: Float,
    val speed: Float,
    val hostile: Boolean,
    val detect: Int,
    val loot: Map<ResourceType, Int>,
    val geneticChance: Float,
    val description: String,
) {
    RIVAL_SOLDIER("Rakip asker", 28f, 3.2f, 1.2f, 1.6f, true, 8, mapOf(ResourceType.FOOD to 2), 0.15f, "Rakip koloninin akıncısı; besin deposunu yağmalamaya çalışır."),
    RIVAL_ELITE("Rakip elit", 55f, 6f, 3f, 1.7f, true, 9, mapOf(ResourceType.FOOD to 4), 0.3f, "Elit koloninin seçkin savaşçısı."),
    TERMITE("Termit", 16f, 2f, 2f, 1.2f, true, 4, mapOf(ResourceType.FOOD to 2, ResourceType.WOOD to 1), 0.1f, "Odun stoklarına saldıran kalabalık böcekler."),
    SPIDER("Örümcek", 60f, 6f, 1f, 1.9f, true, 7, mapOf(ResourceType.FOOD to 8), 0.4f, "Yuvasından çıkan tehlikeli avcı. Kuraklıkta daha saldırgandır."),
    CENTIPEDE("Kırkayak", 42f, 4.5f, 2f, 2.3f, true, 6, mapOf(ResourceType.FOOD to 6), 0.3f, "Hızlı yeraltı avcısı."),
    CAVE_HUNTER("Mağara avcısı", 90f, 7f, 3f, 1.6f, true, 8, mapOf(ResourceType.FOOD to 10, ResourceType.MINERAL to 2), 0.6f, "Derinlerde yaşayan iri böcek."),
    WORM("Solucan", 22f, 0f, 0f, 0.6f, false, 0, mapOf(ResourceType.FOOD to 6), 0.1f, "Zararsız; yağmurda yüzeye yakın tünellere girer. İyi bir besin kaynağı."),
    BEETLE_LARVA("Böcek larvası", 12f, 0f, 0f, 0.3f, false, 0, mapOf(ResourceType.FOOD to 5), 0.15f, "Toprakta bulunan besleyici larva."),
    CAVE_SALAMANDER("Mağara semenderi", 35f, 1f, 1f, 1f, false, 0, mapOf(ResourceType.FOOD to 4), 0.9f, "Yeraltı göllerinde yaşayan nadir canlı; genetik örnek bırakır."),
}

class Creature(val id: Int, val kind: CreatureKind, var x: Float, var y: Float, val rivalId: Int = -1) {
    var prevX = x
    var prevY = y
    var hp = kind.hp
    var maxHp = kind.hp
    var attackMult = 1f
    var path: IntArray? = null
    var pathIndex = 0
    var pathTarget = Long.MIN_VALUE
    var goalX = 0
    var goalY = 0
    var hasGoal = false
    var attackCooldown = 0f
    var thinkTimer = 0f
    var retreating = false
    var lifetime = 0f
    var stolen = 0
    var facing = 0f
    var walkPhase = 0f
    var lastHit = -10.0
    var slowed = false
    var stuckTime = 0f

    val cellX get() = kotlin.math.floor(x).toInt()
    val cellY get() = kotlin.math.floor(y).toInt()

    fun clearPath() { path = null; pathIndex = 0; pathTarget = Long.MIN_VALUE }
}

enum class OrderKind(val title: String, val needsPoint: Boolean = false) {
    DEFEND("Savun"),
    ATTACK("Saldır"),
    RETREAT("Geri çekil"),
    GUARD("Bölgeyi koru", true),
    RALLY("Toplan", true),
    AMBUSH("Pusu kur", true),
    TARGET("Hedef seç"),
    RAID_RIVAL("Rakibe saldır"),
}

/** Bir askerî birliğin (A/B) emri. */
class MilitaryOrder(var kind: OrderKind = OrderKind.DEFEND) {
    var x = 0
    var y = 8
    var targetId = -1
}
