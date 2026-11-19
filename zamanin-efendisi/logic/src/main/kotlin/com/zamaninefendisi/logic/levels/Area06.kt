package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Action.Build
import com.zamaninefendisi.logic.Action.Move
import com.zamaninefendisi.logic.Action.SwitchEra
import com.zamaninefendisi.logic.Action.Wait
import com.zamaninefendisi.logic.BuildKind.*
import com.zamaninefendisi.logic.EnemySpawn
import com.zamaninefendisi.logic.Era.FUTURE
import com.zamaninefendisi.logic.Era.PAST
import com.zamaninefendisi.logic.Era.PRESENT
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.TutorialHint

private fun p(x: Int, y: Int) = Pos(x, y)

/** Alan 6 — Donmuş Yıllar: zamanı dondurulan nesne sonraki çağlarda hiç değişmez. */
object Area06 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level26 = LevelDef(
        id = 26, area = 6, name = "Buz Saati",
        intro = "Yeni güç: Zamanı Dondur. Dondurduğun nesne sonraki çağlara olduğu gibi aktarılır — ne büyür ne yıkılır. " +
            "Susuz kalan tarihî köy evi Şimdi'de harabeye dönmüş. Onu Geçmiş'te dondurup koru.",
        map = listOf(
            "MMMMMMM",
            "MGGGGGM",
            "MGHGGGM",
            "MGGGGGM",
            "MGGG@GM",
            "MGGGGGM",
            "MMMMMMM",
        ),
        eras = all, startEra = PRESENT, freezes = 1,
        allowed = mapOf(PAST to setOf(FREEZE), PRESENT to emptySet(), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(PRESENT, listOf(p(2, 2)), Feature.HUT, "Şimdi'de tarihî köy evi ayakta olsun"),
            Goal.FeatureAt(FUTURE, listOf(p(2, 2)), Feature.HUT, "Gelecek'te de ayakta olsun"),
        ),
        maxTurns = 15, par = 5,
        hints = listOf(
            TutorialHint("Geçmiş'e git ve köy evinin yanına yürü.") { it.heroEra == PAST },
            TutorialHint("Köy evine dokunup Zamanı Dondur'u seç. Tek bir dondurma hakkın var.") { false },
        ),
        solution = listOf(SwitchEra(PAST), Move(p(3, 4)), Move(p(3, 3)), Move(p(2, 3)), Build(FREEZE, p(2, 2))),
    )

    val level27 = LevelDef(
        id = 27, area = 6, name = "Donmuş Koru",
        intro = "Koru iki yana genişlemeli: Gelecek'te iki yan ağaç da kadim orman olmalı. Ama ortadaki ağaç da ormana dönerse kaleye giden yol kapanır. " +
            "Dondurulan ağaç olduğu gibi kalır — ama komşularına bitki olarak sayılmaya devam eder.",
        map = listOf(
            "MMMCMMM",
            "MMGGGMM",
            "MGTTTGM",
            "MGGGGGM",
            "MGGGGGM",
            "MMGGGMM",
            "MMM@MMM",
        ),
        eras = all, startEra = PRESENT, freezes = 1,
        allowed = mapOf(PAST to setOf(FREEZE, PLANT), PRESENT to setOf(FREEZE, PLANT), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(FUTURE, listOf(p(2, 2), p(4, 2)), Feature.FOREST, "Gelecek'te iki yan ağaç kadim orman olsun"),
            Goal.Reach(FUTURE, p(3, 0), "Gelecek'te Zaman Kalesi'ne ulaş"),
        ),
        maxTurns = 20, par = 10,
        hints = listOf(
            TutorialHint("Ortadaki ağacı dondur, sonra yan ağaçların altına birer fidan dik.") { false },
        ),
        solution = listOf(
            Move(p(3, 5)), Move(p(3, 4)), Move(p(3, 3)), Build(FREEZE, p(3, 2)), Build(PLANT, p(2, 3)), Build(PLANT, p(4, 3)),
            Move(p(3, 2)), Move(p(3, 1)), Move(p(3, 0)), SwitchEra(FUTURE),
        ),
    )

    val level28 = LevelDef(
        id = 28, area = 6, name = "Kampın Kışı",
        intro = "Şimdi'deki düşman kalesi Geçmiş'teki bir kamptan doğdu ve her 3 turda akıncı çıkarıyor. Geçmiş'te kamp çok kalabalık; orada dağıtamazsın. " +
            "Ama kampın zamanını dondurursan Şimdi'ye bir kale olarak değil, küçük bir kamp olarak ulaşır...",
        map = listOf(
            "GGGGGGG",
            "GGGEGGG",
            "GGGGGGG",
            "GGG@GGG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = listOf(PAST, PRESENT), startEra = PRESENT, freezes = 1,
        allowed = mapOf(PAST to setOf(FREEZE), PRESENT to setOf(DISMANTLE)),
        enemies = listOf(EnemySpawn(p(0, 0))),
        spawnEvery = 3, maxEnemies = 3,
        protect = listOf(p(3, 5)),
        goals = listOf(
            Goal.NoFeature(PRESENT, Feature.ENEMY_FORT, "Şimdi'de düşman kalesi kalmasın"),
            Goal.NoFeature(PRESENT, Feature.ENEMY_CAMP, "Şimdi'de düşman kampını dağıt"),
            Goal.DefeatAll("Bütün akıncıları yen"),
        ),
        maxTurns = 25, par = 6,
        solution = listOf(SwitchEra(PAST), Move(p(3, 2)), Build(FREEZE, p(3, 1)), SwitchEra(PRESENT), Build(DISMANTLE, p(3, 1)), Action.Attack(p(2, 2))),
    )

    val level29 = LevelDef(
        id = 29, area = 6, name = "Buz ve Kum",
        intro = "Çöl tarlalara yaklaşıyor. Fidan dikebileceğin tek yer ağaç sırasının yanındaki dar geçit — ama orada büyüyen ağaç Gelecek'te " +
            "kadim ormana dönüşüp geçidi kapatır. Dondurulmuş bir fidan sonsuza dek fidan kalır.",
        map = listOf(
            "SGGGMMM",
            "SFGTMMM",
            "SMGTMMM",
            "SFGTMMM",
            "SGGGMMM",
            "SGG@MMM",
        ),
        eras = all, startEra = PRESENT, freezes = 1, desertSpeed = 1,
        allowed = mapOf(PAST to setOf(PLANT, FREEZE), PRESENT to setOf(PLANT, FREEZE), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(FUTURE, listOf(p(1, 1), p(1, 3)), Feature.FIELD, "Gelecek'te iki tarlayı koru"),
            Goal.NotFeatureAt(FUTURE, listOf(p(2, 1), p(2, 2), p(2, 3)), Feature.FOREST, "Gelecek'te geçit ormanla kapanmasın"),
        ),
        maxTurns = 15, par = 6,
        solution = listOf(SwitchEra(PAST), Move(p(2, 5)), Move(p(2, 4)), Move(p(2, 3)), Build(PLANT, p(2, 2)), Build(FREEZE, p(2, 2))),
    )

    val level30 = LevelDef(
        id = 30, area = 6, name = "Buz Devri",
        intro = "Alan patronu! Gelecek'teki robot fabrikası Geçmiş'teki bir kamptan doğdu. Fabrikayı Gelecek'te kendin dağıtabilirsin — " +
            "yeter ki oraya bir fabrika olarak değil, dondurulmuş bir kamp olarak ulaşsın. Bu arada robotlar kaleye yürüyor.",
        map = listOf(
            "GGGGGGEG",
            "GGGGGGGG",
            "GMMGMMGG",
            "GGGGGGGG",
            "GTG@GGGG",
            "GGGGGGGG",
            "GGMCMGGG",
        ),
        eras = all, startEra = FUTURE, freezes = 1, startWood = 0,
        units = listOf(com.zamaninefendisi.logic.UnitSpawn(com.zamaninefendisi.logic.UnitType.ENERGY, p(3, 5))),
        allowed = mapOf(PAST to setOf(FREEZE, PLANT, CUT, WALL), PRESENT to setOf(PLANT, CUT, WALL), FUTURE to setOf(DISMANTLE)),
        enemies = listOf(EnemySpawn(p(0, 0)), EnemySpawn(p(3, 0))),
        enemyEra = FUTURE, spawnEvery = 3, maxEnemies = 3,
        protect = listOf(p(3, 6)),
        hints = listOf(
            TutorialHint("Enerji Birliği kaleyi korur: menzilindeki robotlara her tur kendiliğinden ateş eder. Ona dokunup yerini de değiştirebilirsin.") { it.heroEra != FUTURE },
            TutorialHint("Geçmiş'te kampın zamanını dondur; sonra Gelecek'te dondurulmuş kampı dağıt.") { it.timeline.changes.any { c -> c.kind == FREEZE } },
            TutorialHint("Gelecek'e dön ve kampı dağıt.") { false },
        ),
        goals = listOf(
            Goal.NoFeature(FUTURE, Feature.ENEMY_FORT, "Gelecek'te robot fabrikası kalmasın"),
            Goal.NoFeature(FUTURE, Feature.ENEMY_CAMP, "Gelecek'teki kampı dağıt"),
            Goal.DefeatAll("Bütün robotları yen"),
        ),
        maxTurns = 40, par = 11,
        solution = listOf(
            SwitchEra(PAST), Move(p(3, 3)), Move(p(3, 2)), Move(p(3, 1)), Move(p(4, 1)), Move(p(5, 1)), Move(p(6, 1)), Build(FREEZE, p(6, 0)),
            SwitchEra(FUTURE), Build(DISMANTLE, p(6, 0)), Action.Attack(p(7, 1)),
        ),
    )

    val area = AreaDef(
        6, "Donmuş Yıllar", "Zaman dondurma",
        "Zamanın buz tuttuğu topraklar. Dondurulan bir nesne yüzyıllar boyunca aynı kalır: ne büyür ne yıkılır.",
        listOf(level26, level27, level28, level29, level30),
    )
}
