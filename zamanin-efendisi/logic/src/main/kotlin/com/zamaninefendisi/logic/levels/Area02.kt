package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Action.Build
import com.zamaninefendisi.logic.Action.Move
import com.zamaninefendisi.logic.Action.SwitchEra
import com.zamaninefendisi.logic.Action.Wait
import com.zamaninefendisi.logic.BuildKind.*
import com.zamaninefendisi.logic.EnemySpawn
import com.zamaninefendisi.logic.Era
import com.zamaninefendisi.logic.Era.FUTURE
import com.zamaninefendisi.logic.Era.PAST
import com.zamaninefendisi.logic.Era.PRESENT
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.TutorialHint

private fun p(x: Int, y: Int) = Pos(x, y)

/** Alan 2 — Geleceğin Kapısı: üçüncü çağ açılır. Geçmiş'teki bir değişiklik artık iki çağ ileriye uzanır. */
object Area02 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level6 = LevelDef(
        id = 6, area = 2, name = "Üçüncü Çağ",
        intro = "Zaman Kapısı aralandı: artık Gelecek'e de geçebilirsin. Geçmiş'te kurduğun ahşap köprü Şimdi'de taş köprü, " +
            "Gelecek'te ise bir ışık geçidi olur. Gelecek'teki kaleye ulaş.",
        map = listOf(
            "MMGGGMM",
            "GTGWGGG",
            "G@GWGCG",
            "GGGWGGG",
            "MGGWGGM",
            "MMGWGMM",
            "MMMWMMM",
        ),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT), FUTURE to emptySet()),
        goals = listOf(Goal.Reach(FUTURE, p(5, 2), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 30, par = 8,
        hints = listOf(
            TutorialHint("Üç çağ düğmesi var. Köprü yalnızca Geçmiş'te kurulabilir: önce Geçmiş'e git.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Ağacı kes, nehre köprü kur.") { it.timeline.changes.any { c -> c.kind == BRIDGE } },
            TutorialHint("Şimdi Gelecek düğmesine bas: köprün ışık geçidine dönüştü.") { it.heroEra == FUTURE },
            TutorialHint("Geçitten geçip kaleye yürü.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Build(CUT, p(1, 1)), Move(p(2, 2)), Build(BRIDGE, p(3, 2)),
            SwitchEra(FUTURE), Move(p(3, 2)), Move(p(4, 2)), Move(p(5, 2)),
        ),
    )

    val level7 = LevelDef(
        id = 7, area = 2, name = "Kadim Koru",
        intro = "Doğa evrimi: en az bir çağ yaşamış bir ağacın yanında 2 ya da daha fazla bitki varsa, bir sonraki çağda geçilmez bir " +
            "Kadim Orman'a dönüşür. Koru ağacını Gelecek'te ormana dönüştür — ama yolunu kapatma.",
        map = listOf(
            "MMMCMMM",
            "MMGGGMM",
            "MGGGGGM",
            "MGGTGGM",
            "MGGGGGM",
            "MMGGGMM",
            "MMM@MMM",
        ),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(FUTURE, listOf(p(3, 3)), Feature.FOREST, "Gelecek'te koru ağacı Kadim Orman olsun"),
            Goal.Reach(FUTURE, p(3, 0), "Gelecek'te Zaman Kalesi'ne ulaş"),
        ),
        maxTurns = 20, par = 9,
        hints = listOf(
            TutorialHint("Koru ağacının yanına (köşeler dahil) iki fidan dik. Birbirine değen fidanlar da ormana dönüşür!") { it.timeline.changes.count { c -> c.kind == PLANT } >= 2 },
            TutorialHint("Kadim orman geçilmez. Kaleye Şimdi'de yürüyüp en sonda Gelecek'e geçmek zaman kazandırır.") { false },
        ),
        solution = listOf(
            Move(p(3, 5)), Move(p(3, 4)), Build(PLANT, p(2, 4)), Build(PLANT, p(4, 4)),
            Move(p(3, 3)), Move(p(3, 2)), Move(p(3, 1)), Move(p(3, 0)), SwitchEra(FUTURE),
        ),
    )

    val level8 = LevelDef(
        id = 8, area = 2, name = "Yüzyıllık Sur",
        intro = "Gelecek'te robot muhafızlar geçitten Zaman Kalesi'ne ilerliyor. Şimdi'de kurduğun ahşap çit, Gelecek'e vardığında " +
            "yıkılmaz bir taş sur olur. Geçmiş'te kurulan çit ise bir enerji kalkanına dönüşür.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMGGGMM",
            "GGG@GGG",
            "GTGGGTG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = all, startEra = PRESENT, startWood = 3,
        allowed = mapOf(PAST to setOf(WALL, CUT), PRESENT to setOf(WALL, CUT), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(0, 0)), EnemySpawn(p(6, 0))),
        enemyEra = FUTURE,
        protect = listOf(p(3, 7)),
        goals = listOf(Goal.Survive(10, "Gelecek'te Zaman Kalesi'ni 10 tur koru")),
        maxTurns = 10, par = 6,
        hints = listOf(
            TutorialHint("Robotlar Gelecek'te: onları Gelecek düğmesindeki küçük haritada kırmızı nokta olarak görebilirsin.") { it.timeline.changes.isNotEmpty() },
            TutorialHint("Üç hücrelik geçidi Şimdi'de çitle kapat. Bir robotun üstüne kurulan çit onu zamanın içinde yok eder.") { false },
        ),
        solution = listOf(
            Build(WALL, p(3, 3)), Move(p(4, 4)), Build(WALL, p(4, 3)), Move(p(3, 4)), Move(p(2, 4)), Build(WALL, p(2, 3)),
        ) + List(4) { Wait },
    )

    val level9 = LevelDef(
        id = 9, area = 2, name = "Ormanın Kalkanı",
        intro = "Hiç odunun yok ve robotlar yine yolda. Ama ormanlar da geçilmez... Geçmiş'te dikilen fidanlar Şimdi'de ağaç, " +
            "birbirine yakınsa Gelecek'te kadim orman olur. Geçidi canlı bir surla kapat.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMMGGMM",
            "GGGGGGG",
            "GGG@GGG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = all, startEra = FUTURE,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(0, 0)), EnemySpawn(p(6, 0))),
        enemyEra = FUTURE,
        protect = listOf(p(3, 8)),
        goals = listOf(Goal.Survive(12, "Gelecek'te Zaman Kalesi'ni 12 tur koru")),
        maxTurns = 12, par = 6,
        hints = listOf(
            TutorialHint("Şimdi'de dikilen fidan Gelecek'te yalnızca genç bir ağaçtır. Ormana dönüşecek ağaçlar Geçmiş'ten gelmeli.") { it.heroEra == PAST },
            TutorialHint("Geçidin iki hücresine ve onlara değen üçüncü bir hücreye fidan dik.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(3, 5)), Build(PLANT, p(3, 4)), Build(PLANT, p(3, 5)), Move(p(4, 5)), Build(PLANT, p(4, 4)),
        ) + List(6) { Wait },
    )

    val level10 = LevelDef(
        id = 10, area = 2, name = "Geleceğin Bekçisi",
        intro = "Alan patronu! Gelecek'teki robot fabrikası, Geçmiş'teki küçük bir kamptan doğdu ve her 3 turda bir robot üretiyor. " +
            "Şimdi'deki köylülerin taş geçidi Gelecek'te bir ışık köprüsüdür — robotlar oradan geçebilir.",
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
            PRESENT to listOf(
                "........",
                "........",
                "........",
                "........",
                "........",
                "....Q...",
                "........",
                "........",
            ),
        ),
        eras = all, startEra = FUTURE, startWood = 1,
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
            TutorialHint("Yaklaşan robotu Gelecek'te yen: yanına geldiğinde ona dokun.") { it.enemiesDefeated > 0 },
            TutorialHint("Köylülerin geçidinin batı ucunu Şimdi'de çitle kapatırsan Gelecek'te robotlar geçemez.") { it.timeline.changes.any { c -> c.kind == WALL } },
            TutorialHint("Kampa ulaşmak için Geçmiş'te kendi köprünü kur, sonra kampı dağıt.") { it.timeline.changes.any { c -> c.kind == DISMANTLE } },
        ),
        solution = listOf(
            Move(p(2, 2)), Action.Attack(p(2, 1)), SwitchEra(PRESENT), Move(p(2, 3)), Move(p(2, 4)), Move(p(3, 4)), Move(p(3, 5)),
            Move(p(4, 5)), Move(p(5, 5)), Move(p(5, 4)), Build(WALL, p(5, 5)), SwitchEra(PAST), Move(p(5, 3)), Move(p(5, 2)),
            Move(p(5, 1)), Move(p(5, 0)), Build(DISMANTLE, p(6, 0)), SwitchEra(FUTURE), Action.Attack(p(5, 1)), Action.Attack(p(6, 0)), Wait, Action.Attack(p(6, 0)),
        ),
    )

    val area = AreaDef(
        2, "Geleceğin Kapısı", "Üçüncü çağ: Gelecek",
        "Zaman Kapısı açıldı. Geçmiş'teki küçük bir değişiklik artık iki çağ boyunca büyüyerek ilerler.",
        listOf(level6, level7, level8, level9, level10),
    )
}
