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

/** Alan 5 — Enerji Vadisi: zaman enerjisi. Her çağ geçişi 1 enerji harcar; zaman kristalleri enerji verir. */
object Area05 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level21 = LevelDef(
        id = 21, area = 5, name = "İlk Kıvılcım",
        intro = "Bu vadide zaman geçişi enerji ister: her geçiş 1 zaman enerjisi harcar. Tek bir enerjin var — gidebilirsin ama dönemezsin. " +
            "Zaman kristali toplamak enerji verir. Dikkat: Geçmiş'te toplanan kristal, sonraki çağlarda hiç var olmamış olur.",
        map = listOf(
            "MMGGGMM",
            "GTGWGGG",
            "G@*WGCG",
            "GGGWGGG",
            "MGGWGGM",
            "MMGWGMM",
            "MMMWMMM",
        ),
        eras = all, startEra = FUTURE, energy = 1,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT), FUTURE to emptySet()),
        goals = listOf(Goal.Reach(FUTURE, p(5, 2), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 25, par = 8,
        hints = listOf(
            TutorialHint("Enerji göstergesi üst çubukta. Geçmiş'e gidersen geri dönmek için bir kristal bulmalısın.") { it.heroEra == PAST },
            TutorialHint("Kristalin üstüne yürüyünce enerji kazanırsın. Sonra köprüyü kur ve Gelecek'e dön.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Build(CUT, p(1, 1)), Move(p(2, 2)), Build(BRIDGE, p(3, 2)), SwitchEra(FUTURE),
            Move(p(3, 2)), Move(p(4, 2)), Move(p(5, 2)),
        ),
    )

    val level22 = LevelDef(
        id = 22, area = 5, name = "Kristal Yolu",
        intro = "Hiç enerjin yok. Bazı kristaller yalnızca tek bir çağda parlar; bazıları ise Geçmiş'ten beri oradadır ve bütün çağlarda görünür. " +
            "Aynı kristali iki çağda toplamaya çalışırsan paradoks kazancını siler. Yolunu planla.",
        map = listOf(
            "MMMMMMC",
            "MGGWGGG",
            "MTGWGGG",
            "MG*WGMM",
            "MGGWGGM",
            "MG@GGGM",
            "MMMMMMM",
        ),
        overrides = mapOf(PRESENT to listOf(".......", ".......", ".......", ".......", ".......", "...*...", ".......")),
        eras = all, startEra = PRESENT, energy = 0,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT), FUTURE to emptySet()),
        goals = listOf(Goal.Reach(FUTURE, p(6, 0), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 25, par = 15,
        hints = listOf(
            TutorialHint("Şimdi'de parlayan kristal yalnızca bu çağa ait. Onu topla ve Geçmiş'e git.") { it.heroEra == PAST },
            TutorialHint("Geçmiş'teki kristal Gelecek'e dönüş biletin.") { false },
        ),
        solution = listOf(
            Move(p(3, 5)), SwitchEra(PAST), Move(p(2, 5)), Move(p(2, 4)), Move(p(2, 3)), Move(p(2, 2)), Build(CUT, p(1, 2)), Build(BRIDGE, p(3, 2)),
            SwitchEra(FUTURE), Move(p(3, 2)), Move(p(4, 2)), Move(p(5, 2)), Move(p(5, 1)), Move(p(6, 1)), Move(p(6, 0)),
        ),
    )

    val level23 = LevelDef(
        id = 23, area = 5, name = "Kum ve Kıvılcım",
        intro = "Çöl tarlalara doğru ilerliyor ve tek bir enerjin var. Geçmiş'te fidan dikip tarlaları kurtarmalı, sonra Gelecek'e geçip " +
            "kaleye ulaşmalısın. Dönüş için gereken enerji vadinin ortasındaki kristalde.",
        map = listOf(
            "SGGGGGC",
            "SFGGGGG",
            "SGG*GGG",
            "SFGGGGG",
            "SGGG@GG",
            "SFGGGGG",
            "SGGGGGG",
        ),
        eras = all, startEra = PRESENT, energy = 1, desertSpeed = 1,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(FUTURE, listOf(p(1, 1), p(1, 3), p(1, 5)), Feature.FIELD, "Gelecek'te üç tarlayı koru"),
            Goal.Reach(FUTURE, p(6, 0), "Gelecek'te Zaman Kalesi'ne ulaş"),
        ),
        maxTurns = 25, par = 14,
        solution = listOf(
            SwitchEra(PAST), Move(p(3, 4)), Move(p(2, 4)), Build(PLANT, p(2, 5)), Move(p(2, 3)), Build(PLANT, p(2, 2)),
            Move(p(3, 3)), Move(p(3, 2)), SwitchEra(FUTURE), Move(p(3, 1)), Move(p(4, 1)), Move(p(5, 1)), Move(p(5, 0)), Move(p(6, 0)),
        ),
    )

    val level24 = LevelDef(
        id = 24, area = 5, name = "Kıt Zaman",
        intro = "İki robot Gelecek'te iki hücrelik geçide yürüyor. Şimdi'de kurulan çit Gelecek'te taş surdur — ama Şimdi'ye gidip dönmek " +
            "iki enerji ister ve sende yalnızca bir tane var. Geçidin birini kapat, diğerinden gelenleri tek tek karşıla.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMGGMMM",
            "GG@GGGG",
            "GGGGGGG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        overrides = mapOf(
            PRESENT to listOf(".......", ".......", ".......", ".......", "....*..", ".......", ".......", "......."),
            FUTURE to listOf(".......", ".......", ".......", ".......", "....G..", ".......", ".......", "......."),
        ),
        eras = all, startEra = FUTURE, energy = 1, startWood = 1,
        allowed = mapOf(PAST to setOf(WALL), PRESENT to setOf(WALL), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(2, 0)), EnemySpawn(p(4, 0))),
        enemyEra = FUTURE,
        protect = listOf(p(3, 7)),
        goals = listOf(Goal.DefeatAll("Bütün robotları yen")),
        maxTurns = 20, par = 7,
        hints = listOf(
            TutorialHint("Şimdi'deki kristal yalnızca o çağda var: onu topla ki Gelecek'e dönebilesin.") { it.heroEra != FUTURE || it.turn > 6 },
            TutorialHint("Robotlar açık kalan geçitten tek tek gelir: yanına geldiklerinde saldır.") { false },
        ),
        solution = listOf(SwitchEra(PRESENT), Build(WALL, p(2, 3)), Move(p(3, 4)), Move(p(4, 4)), SwitchEra(FUTURE), Action.Attack(p(3, 4)), Wait, Wait, Action.Attack(p(3, 4))),
    )

    val level25 = LevelDef(
        id = 25, area = 5, name = "Enerji Lordu",
        intro = "Özel patron! Enerji Lordu'nun robot fabrikası Gelecek'te her 3 turda bir robot üretiyor. Fabrika, Geçmiş'teki bir kamptan doğdu. " +
            "Köylülerin taş geçidi robotlara yol açıyor ve zaman enerjin kısıtlı: her geçişi hesapla.",
        map = listOf(
            "GGGGWGEG",
            "GGGGWGGG",
            "GTGGWGGG",
            "GG@GWGGG",
            "GGGGWGGG",
            "MGGGWGGM",
            "GGGGWGGG",
            "GCGGWMMM",
        ),
        overrides = mapOf(
            PRESENT to listOf("........", "........", "........", "........", "........", "....Q...", "........", "........"),
            PAST to listOf("........", ".....*..", "........", "........", "........", "........", "........", "........"),
        ),
        eras = all, startEra = FUTURE, startWood = 1, energy = 2,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE, WALL, DISMANTLE), PRESENT to setOf(CUT, WALL), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(2, 0))),
        enemyEra = FUTURE, spawnEvery = 3, maxEnemies = 3,
        protect = listOf(p(1, 7)),
        goals = listOf(
            Goal.NoFeature(FUTURE, Feature.ENEMY_FORT, "Gelecek'teki robot fabrikasını yok et"),
            Goal.DefeatAll("Bütün robotları yen"),
        ),
        maxTurns = 40, par = 21,
        hints = listOf(
            TutorialHint("Üç geçiş yapman gerekecek ama 2 enerjin var. Geçmiş'te, kampın yakınında bir kristal parlıyor.") { false },
        ),
        solution = listOf(
            Move(p(2, 2)), Action.Attack(p(2, 1)), SwitchEra(PRESENT), Move(p(2, 3)), Move(p(2, 4)), Move(p(3, 4)), Move(p(3, 5)),
            Move(p(4, 5)), Move(p(5, 5)), Move(p(5, 4)), Build(WALL, p(5, 5)), SwitchEra(PAST), Move(p(5, 3)), Move(p(5, 2)),
            Move(p(5, 1)), Move(p(5, 0)), Build(DISMANTLE, p(6, 0)), SwitchEra(FUTURE), Action.Attack(p(5, 1)), Action.Attack(p(6, 0)), Wait, Action.Attack(p(6, 0)),
        ),
    )

    val area = AreaDef(
        5, "Enerji Vadisi", "Zaman enerjisi",
        "Zamanın kendisinin kıt olduğu vadi. Her geçişin bir bedeli var; kristaller ise geçmişle geleceği birbirine bağlar.",
        listOf(level21, level22, level23, level24, level25),
    )
}
