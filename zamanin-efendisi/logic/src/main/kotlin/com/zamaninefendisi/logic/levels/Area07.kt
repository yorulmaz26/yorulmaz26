package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Action.Build
import com.zamaninefendisi.logic.Action.Move
import com.zamaninefendisi.logic.Action.SwitchEra
import com.zamaninefendisi.logic.Action.UnitAttack
import com.zamaninefendisi.logic.Action.UnitMove
import com.zamaninefendisi.logic.Action.Wait
import com.zamaninefendisi.logic.BuildKind.*
import com.zamaninefendisi.logic.EnemyKind
import com.zamaninefendisi.logic.EnemySpawn
import com.zamaninefendisi.logic.Era.FUTURE
import com.zamaninefendisi.logic.Era.PAST
import com.zamaninefendisi.logic.Era.PRESENT
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.TutorialHint
import com.zamaninefendisi.logic.UnitSpawn
import com.zamaninefendisi.logic.UnitType

private fun p(x: Int, y: Int) = Pos(x, y)

/** Alan 7 — Yıkılmış Krallık: müttefik birlikler, savunma kuleleri ve farklı stratejilerle savaşan düşmanlar. */
object Area07 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level31 = LevelDef(
        id = 31, area = 7, name = "Şövalyenin Nöbeti",
        intro = "Krallığın son birlikleri emrinde! Bir birliğe dokunup seç, sonra mavi hücreye dokunarak yürüt ya da kırmızı hedefe saldırt. " +
            "Komuta etmediğin birlikler menzillerindeki düşmana kendiliğinden ateş eder (nöbet). Şövalyenin kalkanı ağır muhafızların darbesini emer.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMMGMMM",
            "GGGGGGG",
            "GGGGGGG",
            "GGG@GGG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = listOf(PAST, PRESENT), startEra = PRESENT,
        allowed = mapOf(PAST to emptySet(), PRESENT to emptySet()),
        units = listOf(UnitSpawn(UnitType.KNIGHT, p(1, 6)), UnitSpawn(UnitType.ARCHER, p(5, 7))),
        enemies = listOf(
            EnemySpawn(p(3, 0), EnemyKind.BRUTE), EnemySpawn(p(1, 0), EnemyKind.RAIDER),
            EnemySpawn(p(5, 0), EnemyKind.BRUTE), EnemySpawn(p(3, 1), EnemyKind.RAIDER),
        ),
        protect = listOf(p(3, 9)),
        goals = listOf(Goal.DefeatAll("Bütün düşmanları yen"), Goal.KeepUnits(2, "İki birliğin de hayatta kalsın")),
        maxTurns = 25, par = 4,
        hints = listOf(
            TutorialHint("Şövalyeye dokun ve geçide doğru yürüt. Geçidi tutan şövalyeyi kimse geçemez.") { s -> s.units.any { it.type == UnitType.KNIGHT && it.pos.y <= 5 } },
            TutorialHint("Okçuyu geçidin 2–3 hücre arkasına yerleştir; sonra Bekle ile nöbeti izle.") { false },
        ),
        solution = listOf(UnitMove(0, p(2, 5)), UnitMove(0, p(3, 4)), UnitMove(1, p(4, 6)), UnitMove(1, p(3, 6))) + List(8) { Wait },
    )

    val level32 = LevelDef(
        id = 32, area = 7, name = "Kule Hattı",
        intro = "Yağmacılar savaşmaz, tarlaları yağmalar. Geçmiş'te kurulan gözcü kulesi Şimdi'de taş kule olur: her tur 2 menzildeki düşmana " +
            "2 hasar verir ve yıkılmaz. Şimdi'de kurulan gözcü kulesi daha zayıftır ve yıkılabilir.",
        map = listOf(
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "TGGGGGGT",
            "GGG@GGGG",
            "GFGGGGFG",
            "GFGGGGFG",
            "WWWWWWWW",
        ),
        eras = all, startEra = PRESENT, startWood = 4,
        allowed = mapOf(PAST to setOf(TOWER, CUT), PRESENT to setOf(TOWER, CUT), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(0, 0), EnemyKind.PLUNDERER), EnemySpawn(p(7, 0), EnemyKind.PLUNDERER), EnemySpawn(p(4, 0), EnemyKind.PLUNDERER)),
        goals = listOf(
            Goal.Survive(12, "12 tur dayan"),
            Goal.FeatureAt(PRESENT, listOf(p(1, 7), p(1, 8), p(6, 7), p(6, 8)), Feature.FIELD, "Şimdi'de dört tarla da sağlam kalsın"),
        ),
        maxTurns = 12, par = 7,
        hints = listOf(
            TutorialHint("Kuleleri Geçmiş'te kur: Şimdi'de yıkılmaz taş kuleye dönüşürler.") { it.timeline.changes.any { c -> c.kind == TOWER } },
            TutorialHint("Her tarla grubunun önüne bir kule yeter.") { false },
        ),
        solution = listOf(SwitchEra(PAST), Move(p(2, 6)), Build(TOWER, p(1, 6)), Move(p(3, 6)), Move(p(4, 6)), Move(p(5, 6)), Build(TOWER, p(6, 6))) + List(5) { Wait },
    )

    val level33 = LevelDef(
        id = 33, area = 7, name = "Gölge Ajanları",
        intro = "Paradoks Ajanları senin yaptığın her şeyi zaman çizgisinden silmeye çalışır. Gelecek'teki kaleye köprüsüz ulaşamazsın; " +
            "ama Şimdi'deki ajanlar taş köprünü yıkarsa Gelecek'teki ışık geçidi de hiç var olmamış olur.",
        map = listOf(
            "MGGGGGGCM",
            "MGGGGGGGM",
            "WWWWWWWWW",
            "MGGGGGGGM",
            "MGGGGGGGM",
            "MGGGG@GTM",
            "MMMMMMMMM",
        ),
        overrides = mapOf(PRESENT to listOf(".K.......", ".........", ".........", ".........", ".........", ".........", ".........")),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT), FUTURE to emptySet()),
        units = listOf(UnitSpawn(UnitType.KNIGHT, p(5, 3))),
        enemyEra = PRESENT, spawnEvery = 3, maxEnemies = 2,
        enemyKindOverride = EnemyKind.SABOTEUR,
        goals = listOf(Goal.Reach(FUTURE, p(7, 0), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 30, par = 15,
        hints = listOf(
            TutorialHint("Ajanlar Şimdi'deki köprünü yıkarsa, Gelecek'te üstünde durduğun ışık geçidi de yok olur!") { it.timeline.changes.any { c -> c.kind == BRIDGE } },
            TutorialHint("Bir birliğin durduğu yapıya ajan dokunamaz. Şövalyeyi Şimdi'de köprünün üstüne yerleştir.") { s -> s.units.any { u -> s.cell(PRESENT, u.pos).feature.bridge } },
            TutorialHint("Köprü güvende: Gelecek'e geç ve kaleye yürü.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(6, 5)), Build(CUT, p(7, 5)), Move(p(6, 4)), Move(p(6, 3)), Build(BRIDGE, p(6, 2)), Move(p(6, 4)),
            SwitchEra(PRESENT), UnitMove(0, p(6, 2)), SwitchEra(FUTURE),
            Move(p(6, 3)), Move(p(6, 2)), Move(p(6, 1)), Move(p(7, 1)), Move(p(7, 0)),
        ),
    )

    val level34 = LevelDef(
        id = 34, area = 7, name = "Faz Hırsızları",
        intro = "Zaman Hırsızları duvarların üstünden faz atlar ve sen başka bir çağdayken iki kat hızlı ilerler. Kuzeydeki kaleyi savunmacı okçular korur; " +
            "kale yıkılırsa öfkelenip saldırırlar. Kuşatma Mühendisi kaleye üç kat hasar verir.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMGGGMM",
            "GGGGGGG",
            "GGG@GGG",
            "GGGCGGG",
        ),
        overrides = mapOf(PRESENT to listOf("...K...", ".......", ".......", ".......", ".......", ".......", ".......", ".......")),
        eras = listOf(PAST, PRESENT), startEra = PRESENT, startWood = 3,
        allowed = mapOf(PAST to setOf(WALL, TOWER), PRESENT to setOf(WALL)),
        units = listOf(UnitSpawn(UnitType.ENGINEER, p(1, 5)), UnitSpawn(UnitType.KNIGHT, p(5, 5))),
        enemies = listOf(
            EnemySpawn(p(2, 1), EnemyKind.ARCHER), EnemySpawn(p(4, 1), EnemyKind.ARCHER),
            EnemySpawn(p(0, 2), EnemyKind.PHASER), EnemySpawn(p(6, 2), EnemyKind.PHASER),
        ),
        protect = listOf(p(3, 7)),
        goals = listOf(Goal.NoFeature(PRESENT, Feature.ENEMY_FORT, "Şimdi'deki düşman kalesini yık"), Goal.DefeatAll("Bütün düşmanları yen")),
        maxTurns = 35, par = 13,
        solution = listOf(
            UnitMove(1, p(4, 4)), UnitMove(1, p(4, 3)), UnitMove(0, p(2, 5)), UnitMove(0, p(2, 4)), Wait, UnitMove(1, p(3, 2)), UnitMove(0, p(2, 5)),
            UnitMove(0, p(2, 4)), UnitMove(0, p(2, 3)), UnitMove(0, p(2, 2)), UnitMove(0, p(2, 1)), UnitMove(0, p(2, 0)), UnitAttack(0, p(3, 0)), UnitAttack(0, p(3, 0)),
        ),
    )

    val level35 = LevelDef(
        id = 35, area = 7, name = "Yıkılmış Kral",
        intro = "Alan patronu! Yıkılmış Kral'ın kalesi Geçmiş'teki kamptan doğdu. Kalesini Teknoloji Uzmanları ve ağır muhafızlar korur; " +
            "uzmanlar her 3 turda bir güçlenir. Ordunu akıllıca yerleştir — Zaman Büyücüsü vurduğu düşmanı 2 tur dondurur.",
        map = listOf(
            "GGGGEGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGMGGMGG",
            "GGGGGGGG",
            "GGGG@GGG",
            "GGGGGGGG",
            "GGGGCGGG",
        ),
        eras = listOf(PAST, PRESENT), startEra = PRESENT, startWood = 0,
        allowed = mapOf(PAST to setOf(DISMANTLE), PRESENT to emptySet()),
        units = listOf(
            UnitSpawn(UnitType.KNIGHT, p(3, 6)), UnitSpawn(UnitType.ARCHER, p(5, 6)),
            UnitSpawn(UnitType.MAGE, p(2, 6)), UnitSpawn(UnitType.ENGINEER, p(6, 6)),
        ),
        enemies = listOf(
            EnemySpawn(p(3, 1), EnemyKind.TECHNICIAN), EnemySpawn(p(5, 1), EnemyKind.TECHNICIAN),
            EnemySpawn(p(4, 2), EnemyKind.BRUTE),
        ),
        spawnEvery = 5, maxEnemies = 4,
        protect = listOf(p(4, 7)),
        goals = listOf(
            Goal.NoFeature(PRESENT, Feature.ENEMY_FORT, "Şimdi'deki krallık kalesini yok et"),
            Goal.DefeatAll("Bütün düşmanları yen"),
            Goal.KeepUnits(4, "Dört birliğin de hayatta kalsın"),
        ),
        maxTurns = 40, par = 10,
        hints = listOf(
            TutorialHint("Şimdi'de durursan Teknoloji Uzmanları sana nişan alır. Birlikler sen yokken de nöbet tutar.") { it.heroEra != PRESENT || it.turn > 3 },
            TutorialHint("Kale Geçmiş'teki kamptan doğdu: kampı dağıt.") { it.timeline.changes.any { c -> c.kind == DISMANTLE } },
            TutorialHint("Uzmanlar her 3 turda güçlenir ve şövalyenin kalkanını delmeye başlar. Okçuyla ya da büyücüyle onları erken avla.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(4, 4)), Move(p(4, 3)), Move(p(4, 2)), Move(p(4, 1)), Build(DISMANTLE, p(4, 0)), SwitchEra(PRESENT),
            Action.Attack(p(5, 1)), UnitMove(1, p(4, 6)), UnitAttack(1, p(3, 4)),
        ),
    )

    val area = AreaDef(
        7, "Yıkılmış Krallık", "Birlikler ve savunma",
        "Yıkılmış bir krallığın son birlikleri. Her birliğin canı, saldırısı, savunması, menzili ve özel yeteneği farklıdır; düşmanlar da farklı stratejilerle savaşır.",
        listOf(level31, level32, level33, level34, level35),
    )
}
