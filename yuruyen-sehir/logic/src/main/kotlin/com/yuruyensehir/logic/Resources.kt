package com.yuruyensehir.logic

import kotlinx.serialization.Serializable

/** Oyundaki bütün kaynaklar. [unitWeight]: depolanan her birimin şehre eklediği ağırlık. */
@Serializable
enum class Resource(val title: String, val unitWeight: Double, val advanced: Boolean = false) {
    FOOD("Yiyecek", 0.05),
    WATER("Su", 0.08),
    WOOD("Odun", 0.10),
    STONE("Taş", 0.15),
    METAL("Metal", 0.20),
    CLOTH("Kumaş", 0.03),
    ENERGY("Enerji", 0.0),
    GOLD("Altın", 0.0),
    TOOLS("Alet", 0.10),
    WASTE("Atık", 0.05),
    CRYSTAL("Kristal", 0.12, advanced = true),
    RARE_ORE("Nadir Maden", 0.20, advanced = true),
    ANCIENT_PART("Antik Parça", 0.10, advanced = true),
    TECH_PART("Teknoloji Bileşeni", 0.06, advanced = true),
    LIGHT_MATERIAL("Hafif Malzeme", 0.02, advanced = true),
    CARE_ESSENCE("Bakım Özü", 0.02, advanced = true);

    companion object {
        /** Üst çubukta her zaman gösterilen temel kaynaklar. */
        val basic = listOf(FOOD, WATER, WOOD, STONE, METAL, CLOTH, ENERGY, GOLD)
    }
}

typealias Amounts = Map<Resource, Double>

fun amounts(vararg pairs: Pair<Resource, Number>): Amounts =
    pairs.associate { it.first to it.second.toDouble() }

operator fun Amounts.times(factor: Double): Amounts = mapValues { it.value * factor }

fun Amounts.describe(): String =
    entries.filter { it.value > 0.0 }.joinToString(", ") { "${it.key.title} ${fmt(it.value)}" }

fun fmt(v: Double): String {
    val a = kotlin.math.abs(v)
    return when {
        a >= 10_000 -> "${(v / 1000).toInt()}b"
        a >= 10 -> kotlin.math.round(v).toLong().toString()
        a == kotlin.math.floor(a) -> v.toLong().toString()
        else -> String.format(java.util.Locale.ROOT, "%.1f", v)
    }
}
