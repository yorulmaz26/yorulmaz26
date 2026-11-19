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

/** Alan 3 — Kayıp Orman: doğa evrimi. Ormanlar hem kalkan hem engeldir; her kesilen ağacın bir bedeli vardır. */
object Area03 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level11 = LevelDef(
        id = 11, area = 3, name = "Orman Yolu",
        intro = "Şimdi'de kuzeydeki geçit sel altında; Gelecek'te sular çekilmiş ama yaşlı ağaçlar geçilmez bir kadim ormana dönüşmüş. " +
            "Ormanın oluşmasını engellemek için tek bir ağacı doğru zamanda kesmek yeter.",
        map = listOf(
            "MMMCMMM",
            "MMGGGMM",
            "MTTTTTM",
            "MGGGGGM",
            "MGGGGGM",
            "MMGGGMM",
            "MMM@MMM",
        ),
        overrides = mapOf(
            PRESENT to listOf(".......", "..WWW..", ".......", ".......", ".......", ".......", "......."),
            FUTURE to listOf(".......", "..GGG..", ".......", ".......", ".......", ".......", "......."),
        ),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(CUT, PLANT), PRESENT to setOf(CUT, PLANT), FUTURE to emptySet()),
        goals = listOf(Goal.Reach(FUTURE, p(3, 0), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 20, par = 8,
        hints = listOf(
            TutorialHint("Gelecek düğmesindeki küçük haritaya bak: ağaç sırası ormana dönüşmüş. Orman, yanında 2 bitki olan ağaçtan doğar.") { it.timeline.changes.isNotEmpty() },
            TutorialHint("Ortadaki ağacı kesersen komşuları yalnız kalır ve orman oluşmaz.") { false },
        ),
        solution = listOf(Move(p(3, 5)), Move(p(3, 4)), Move(p(3, 3)), Build(CUT, p(3, 2)), SwitchEra(FUTURE), Move(p(3, 2)), Move(p(3, 1)), Move(p(3, 0))),
    )

    val level12 = LevelDef(
        id = 12, area = 3, name = "Yeşil Sınır",
        intro = "Çöl her çağda bir adım ilerliyor ve Gelecek'e kadar dört tarlayı da yutacak. Fidanlar kumu durdurur; ama yan yana " +
            "dikilen fidanlar ve ağaçlar Gelecek'te geçilmez ormana döner. Tarlaları kurtar ve kaleye giden yolu açık tut.",
        map = listOf(
            "SGGCMMM",
            "SFGGMMM",
            "SGGTMMM",
            "SFGGMMM",
            "SGGGMMM",
            "SFGGMMM",
            "SGGTMMM",
            "SFGGMMM",
            "SGG@MMM",
        ),
        eras = all, startEra = PRESENT, desertSpeed = 1,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureAt(FUTURE, listOf(p(1, 1), p(1, 3), p(1, 5), p(1, 7)), Feature.FIELD, "Gelecek'te dört tarlayı da koru"),
            Goal.Reach(FUTURE, p(3, 0), "Gelecek'te Zaman Kalesi'ne ulaş"),
        ),
        maxTurns = 25, par = 14,
        hints = listOf(
            TutorialHint("Tarlalar Şimdi'de çoktan kurumuş: fidanlar Geçmiş'te dikilmeli.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("İki tarlanın tam arasına dikilen bir fidan ikisini birden korur.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(2, 8)), Move(p(2, 7)), Move(p(2, 6)), Build(PLANT, p(1, 6)),
            Move(p(2, 5)), Move(p(2, 4)), Move(p(2, 3)), Move(p(2, 2)), Build(PLANT, p(1, 2)),
            Move(p(2, 1)), Move(p(2, 0)), Move(p(3, 0)), SwitchEra(FUTURE),
        ),
    )

    val level13 = LevelDef(
        id = 13, area = 3, name = "Ormanın Bedeli",
        intro = "Gelecek'te bir şehir yükselmeli. Geçmiş'te nehir kıyısına kurulan köy, çevresi iki çağ boyunca kumdan korunursa şehre dönüşür. " +
            "Köy için 3 odun gerekir: hangi iki ağacı kesersen keş, çöl o boşluktan içeri sızar.",
        map = listOf(
            "SSGGGGG",
            "SGTGWGG",
            "SGGGWGG",
            "STG@WGT",
            "SGGGWGG",
            "SGTGWGG",
            "SGGGWGT",
        ),
        eras = all, startEra = PRESENT, startWood = 0, desertSpeed = 1,
        allowed = mapOf(PAST to setOf(CUT, VILLAGE, PLANT), PRESENT to setOf(CUT, PLANT), FUTURE to emptySet()),
        goals = listOf(Goal.FeatureCount(FUTURE, Feature.CITY, 1, "Gelecek'te bir şehir yükselsin")),
        maxTurns = 20, par = 9,
        hints = listOf(
            TutorialHint("Köy Şimdi'de kasaba, Gelecek'te şehir olur — yanına iki çağ boyunca hiç kum gelmemeli.") { it.timeline.changes.any { c -> c.kind == VILLAGE } },
            TutorialHint("Batıdaki yaşlı ağaç, köyün kıyısını tek başına korur. Onu kesme!") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(3, 2)), Move(p(2, 2)), Build(CUT, p(2, 1)), Move(p(2, 3)), Move(p(2, 4)), Build(CUT, p(2, 5)),
            Move(p(3, 4)), Build(VILLAGE, p(3, 3)),
        ),
    )

    val level14 = LevelDef(
        id = 14, area = 3, name = "Kadim Labirent",
        intro = "Üç robot Gelecek'te dağ sırasının iki ucundaki geçitlerden kaleye iniyor. Odun yok; yalnızca fidanların var. " +
            "İki geçidi birden ormanla kapatmaya zaman yetmeyebilir — Gelecek'te bir robotla yüz yüze dövüşmek de bir seçenektir.",
        map = listOf(
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "GGGGGGGG",
            "TGMMMMGT",
            "GGGGGGGG",
            "G@GGGGGG",
            "GGGGGGGG",
            "GGGCGGGG",
        ),
        eras = all, startEra = FUTURE, heroHp = 3,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(0, 0)), EnemySpawn(p(1, 0)), EnemySpawn(p(7, 0))),
        enemyEra = FUTURE,
        protect = listOf(p(3, 8)),
        goals = listOf(Goal.Survive(14, "Gelecek'te Zaman Kalesi'ni 14 tur koru")),
        maxTurns = 14, par = 11,
        hints = listOf(
            TutorialHint("Batı geçidine iki robot geliyor. Geçitlerin kenarındaki ağaçlar, yanlarına fidan dikersen ormana dönüşür.") { it.timeline.changes.isNotEmpty() },
            TutorialHint("Bir geçidi kapattıktan sonra Gelecek'e dön ve diğer robotu kendin durdur.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(1, 5)), Build(PLANT, p(1, 4)), Build(PLANT, p(1, 5)), Move(p(2, 5)), SwitchEra(FUTURE),
            Move(p(2, 6)), Move(p(2, 7)), Move(p(3, 7)), Move(p(3, 8)), Wait, Action.Attack(p(4, 8)), Wait, Wait,
        ),
    )

    val level15 = LevelDef(
        id = 15, area = 3, name = "Orman Ruhu",
        intro = "Alan patronu! Şimdi'deki akıncı kalesi her 3 turda bir akıncı çıkarıyor ve iki kasabaya saldırıyor. Bu kale Geçmiş'ten gelmiyor; " +
            "dağıtılamaz. Ama yaşlı ağaçlar, yanlarına Geçmiş'te fidan dikilirse Şimdi'de geçilmez ormana döner...",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "MMAGAMM",
            "GGGGGGG",
            "GGG@GGG",
            "GHGTGHG",
            "GWGGGWG",
        ),
        overrides = mapOf(PRESENT to listOf("...K...", ".......", ".......", ".......", ".......", ".......", ".......")),
        eras = listOf(PAST, PRESENT), startEra = PRESENT, startWood = 0,
        allowed = mapOf(PAST to setOf(PLANT, CUT, WALL), PRESENT to setOf(PLANT, CUT, WALL)),
        enemyEra = PRESENT, spawnEvery = 3, maxEnemies = 3,
        protect = listOf(p(1, 5), p(5, 5)),
        goals = listOf(Goal.Survive(15, "İki kasabayı 15 tur koru")),
        maxTurns = 15, par = 7,
        hints = listOf(
            TutorialHint("Geçidin iki yanındaki yaşlı ağaçlara Geçmiş'te birer fidan komşu yap: Şimdi'de orman olurlar.") { it.timeline.changes.count { c -> c.kind == PLANT } >= 2 },
            TutorialHint("Ortadaki boşluğu da kapat: orada kurulan fidan iki ağaca birden komşudur.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Build(CUT, p(3, 5)), Move(p(3, 3)), Build(PLANT, p(4, 3)), Build(PLANT, p(3, 3)), Build(WALL, p(3, 2)), Build(PLANT, p(2, 3)),
        ) + List(8) { Wait },
    )

    val area = AreaDef(
        3, "Kayıp Orman", "Doğa evrimi",
        "Yaşlı ağaçların kümelenip geçilmez kadim ormanlara dönüştüğü vadi. Bir orman, duvar da olabilir engel de.",
        listOf(level11, level12, level13, level14, level15),
    )
}
