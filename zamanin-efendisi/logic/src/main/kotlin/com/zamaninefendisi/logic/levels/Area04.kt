package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Action.Build
import com.zamaninefendisi.logic.Action.Move
import com.zamaninefendisi.logic.Action.SwitchEra
import com.zamaninefendisi.logic.Action.Wait
import com.zamaninefendisi.logic.BuildKind.*
import com.zamaninefendisi.logic.CausalLink
import com.zamaninefendisi.logic.EnemySpawn
import com.zamaninefendisi.logic.Era.FUTURE
import com.zamaninefendisi.logic.Era.PAST
import com.zamaninefendisi.logic.Era.PRESENT
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.Terrain
import com.zamaninefendisi.logic.TutorialHint

private fun p(x: Int, y: Int) = Pos(x, y)

/** Alan 4 — Taş Köprüler: yapı evrimi. Ne zaman inşa ettiğin, neyin ortaya çıkacağını belirler. */
object Area04 {
    private val all = listOf(PAST, PRESENT, FUTURE)

    val level16 = LevelDef(
        id = 16, area = 4, name = "Üç Köprü",
        intro = "Taş ustaları Şimdi'de iki taş köprü bekliyor. Taş köprü yalnızca Geçmiş'te kurulmuş ahşap köprünün yüzyıllar içinde " +
            "taşlaşmasıyla oluşur; Şimdi'de kurulan köprü ahşap kalır. İki nehri aş ve Gelecek'teki kaleye ulaş.",
        map = listOf(
            "MMMMMCM",
            "MGGGGGM",
            "WWWWWWW",
            "MGGGGTM",
            "MGGGGGM",
            "WWWWWWW",
            "MTGGGGM",
            "MGG@GGM",
        ),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT, BRIDGE), FUTURE to emptySet()),
        goals = listOf(
            Goal.FeatureCount(PRESENT, Feature.BRIDGE_STONE, 2, "Şimdi'de iki taş köprü olsun"),
            Goal.Reach(FUTURE, p(5, 0), "Gelecek'te Zaman Kalesi'ne ulaş"),
        ),
        maxTurns = 30, par = 17,
        hints = listOf(
            TutorialHint("Şimdi'de kurulan köprü ahşaptır. Taş köprü için Geçmiş'e git.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Her köprü 2 odun ister: her nehir kıyısında kesilecek bir ağaç var.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Move(p(3, 6)), Move(p(2, 6)), Build(CUT, p(1, 6)), Build(BRIDGE, p(2, 5)), Move(p(2, 5)), Move(p(2, 4)),
            Move(p(3, 4)), Move(p(4, 4)), Move(p(5, 4)), Build(CUT, p(5, 3)), Move(p(5, 3)), Build(BRIDGE, p(5, 2)),
            Move(p(5, 2)), Move(p(5, 1)), Move(p(5, 0)), SwitchEra(FUTURE),
        ),
    )

    val level17 = LevelDef(
        id = 17, area = 4, name = "Şehrin Temeli",
        intro = "Gelecek'te iki şehir yükselmeli. Geçmiş'te nehir kıyısına kurulan her köy önce kasabaya, sonra şehre evrilir — " +
            "yeter ki çöl yanına ulaşmasın. Odun için ağaç keseceksin; kestiğin her ağaç, kuma bir kapı açar.",
        map = listOf(
            "SGGGWGGG",
            "SGTGWGTG",
            "SGGGWGGG",
            "STG@WGTG",
            "SGGGWGGG",
            "SGTGWGGG",
            "SGGGWGGG",
        ),
        eras = all, startEra = PAST, startWood = 0, desertSpeed = 1,
        allowed = mapOf(PAST to setOf(CUT, VILLAGE, PLANT, BRIDGE), PRESENT to setOf(CUT, PLANT), FUTURE to emptySet()),
        goals = listOf(Goal.FeatureCount(FUTURE, Feature.CITY, 2, "Gelecek'te iki şehir yükselsin")),
        maxTurns = 30, par = 13,
        hints = listOf(
            TutorialHint("Her köy 3 odun ister. Bir ağacı kestiğinde yerine yakına bir fidan dikersen çöl yine duvara çarpar.") { false },
        ),
        solution = listOf(
            Move(p(3, 2)), Move(p(2, 2)), Build(CUT, p(2, 1)), Build(PLANT, p(1, 2)), Move(p(2, 3)), Build(CUT, p(1, 3)), Move(p(2, 4)), Build(CUT, p(2, 5)),
            Build(PLANT, p(1, 4)), Build(VILLAGE, p(3, 4)), Move(p(2, 3)), Move(p(2, 2)), Build(VILLAGE, p(3, 2)),
        ),
    )

    val level18 = LevelDef(
        id = 18, area = 4, name = "Kırık Sur",
        intro = "Akıncılar Şimdi'de kaleye saldırıyor. Bu kez kalenin üç sura ihtiyacı var — ve Zaman Konseyi, Gelecek'te bu surların " +
            "enerji kalkanına dönüşmesini istiyor. Enerji kalkanı yalnızca Geçmiş'te kurulan çitten doğar.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "GGGGGGG",
            "MMG@GMM",
            "GGGTGGG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = all, startEra = PAST, startWood = 1,
        allowed = mapOf(PAST to setOf(WALL, CUT), PRESENT to setOf(WALL, CUT), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(1, 0)), EnemySpawn(p(5, 0)), EnemySpawn(p(3, 0))),
        protect = listOf(p(3, 7)),
        goals = listOf(
            Goal.Survive(12, "Zaman Kalesi'ni 12 tur koru"),
            Goal.FeatureCount(FUTURE, Feature.ENERGY_WALL, 3, "Gelecek'te üç enerji kalkanı olsun"),
        ),
        maxTurns = 12, par = 5,
        hints = listOf(
            TutorialHint("Üç çit için 3 odun gerekir: önce bir ağaç kes.") { it.wood >= 3 || it.timeline.changes.count { c -> c.kind == WALL } > 0 },
            TutorialHint("Çitleri Geçmiş'te kur: Şimdi'de taş sur, Gelecek'te enerji kalkanı olurlar.") { false },
        ),
        solution = listOf(Build(CUT, p(3, 5)), Build(WALL, p(2, 4)), Build(WALL, p(4, 4)), Move(p(3, 5)), Build(WALL, p(3, 4))) + List(7) { Wait },
    )

    val level19 = LevelDef(
        id = 19, area = 4, name = "Sel Kapısı",
        intro = "Bu vadide kadim zaman mühürleri var. Geçmiş'te uyandırılan mühür, Şimdi'de nehri kurutup bir geçit açar. " +
            "Şimdi'de uyandırılan mühür ise Gelecek'te dağları yarar. Hangi mührü hangi çağda uyandıracağını bul.",
        map = listOf(
            "MMMMCMM",
            "MGGGGGM",
            "MGGGGGM",
            "MMMMGMM",
            "MXGGGGM",
            "WWWWWWW",
            "MXGG@GM",
        ),
        links = listOf(
            CausalLink("sel", PAST, p(1, 6), Feature.SHRINE_ACTIVE, PRESENT, p(2, 5), setTerrain = Terrain.GRASS, description = "Geçmiş'teki mühür Şimdi'de nehri kurutur"),
            CausalLink("dag", PRESENT, p(1, 4), Feature.SHRINE_ACTIVE, FUTURE, p(4, 3), setTerrain = Terrain.GRASS, description = "Şimdi'deki mühür Gelecek'te dağı yarar"),
        ),
        overrides = mapOf(PRESENT to listOf(".......", ".......", ".......", "....M..", ".......", ".......", ".......")),
        eras = all, startEra = PRESENT,
        allowed = mapOf(PAST to setOf(ACTIVATE), PRESENT to setOf(ACTIVATE), FUTURE to emptySet()),
        goals = listOf(Goal.Reach(FUTURE, p(4, 0), "Gelecek'te Zaman Kalesi'ne ulaş")),
        maxTurns = 25, par = 15,
        hints = listOf(
            TutorialHint("Mühürlere dokunduğunda neyi değiştireceklerini hücre bilgisinde görebilirsin. Önce nehri aşmanın yolunu bul.") { it.timeline.changes.isNotEmpty() },
            TutorialHint("Kuzeydeki dağ geçidi Şimdi'de çökmüş. Şimdi'deki mühür onu Gelecek'te yeniden açabilir.") { false },
        ),
        solution = listOf(
            Move(p(3, 6)), Move(p(2, 6)), SwitchEra(PAST), Build(ACTIVATE, p(1, 6)), SwitchEra(PRESENT), Move(p(2, 5)), Move(p(2, 4)),
            Build(ACTIVATE, p(1, 4)), Move(p(3, 4)), Move(p(4, 4)), SwitchEra(FUTURE), Move(p(4, 3)), Move(p(4, 2)), Move(p(4, 1)), Move(p(4, 0)),
        ),
    )

    val level20 = LevelDef(
        id = 20, area = 4, name = "Taş Kral",
        intro = "Alan patronu! Taş Kral'ın kalesi Geçmiş'teki bir kamptan doğdu ve her 4 turda akıncı çıkarıyor. Kampı dağıt, bütün akıncıları yen " +
            "ve vadinin geleceğini güvenceye al: Gelecek'te bir şehir yükselmeli.",
        map = listOf(
            "GGGGWGEG",
            "GTGGWGGG",
            "GGGGWGGG",
            "GG@GWGTG",
            "GGGGWGGG",
            "GGGGWGGM",
            "GGGGWGGG",
            "GCGGWMMM",
        ),
        eras = all, startEra = PRESENT, startWood = 2,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE, WALL, DISMANTLE, VILLAGE), PRESENT to setOf(CUT, WALL), FUTURE to emptySet()),
        enemies = listOf(EnemySpawn(p(1, 0))),
        spawnEvery = 4, maxEnemies = 3,
        protect = listOf(p(1, 7)),
        goals = listOf(
            Goal.NoFeature(PRESENT, Feature.ENEMY_FORT, "Şimdi'deki düşman kalesini yok et"),
            Goal.DefeatAll("Bütün akıncıları yen"),
            Goal.FeatureCount(FUTURE, Feature.CITY, 1, "Gelecek'te bir şehir yükselsin"),
        ),
        maxTurns = 45, par = 22,
        hints = listOf(
            TutorialHint("Kaleye yürüyen akıncıyı durdur, sonra Geçmiş'e geç.") { it.enemiesDefeated > 0 },
            TutorialHint("Gelecek'te şehir için Geçmiş'te nehir kıyısına bir köy kur (3 odun).") { it.timeline.changes.any { c -> c.kind == VILLAGE } },
            TutorialHint("Kampa ulaşmak için Geçmiş'te köprü kur ve kampı dağıt.") { it.timeline.changes.any { c -> c.kind == DISMANTLE } },
            TutorialHint("Kalan akıncıları Şimdi'de yen.") { false },
        ),
        solution = listOf(
            Move(p(2, 2)), Wait, Action.Attack(p(1, 2)), SwitchEra(PAST), Move(p(1, 2)), Build(CUT, p(1, 1)), Move(p(2, 2)), Move(p(3, 2)),
            Build(BRIDGE, p(4, 2)), Move(p(4, 2)), Build(WALL, p(3, 2)), Move(p(5, 2)), Move(p(6, 2)), Move(p(6, 1)), Build(DISMANTLE, p(6, 0)),
            Move(p(6, 2)), Build(CUT, p(6, 3)), Move(p(6, 3)), Build(VILLAGE, p(5, 3)), SwitchEra(PRESENT),
            Action.Attack(p(6, 2)), Action.Attack(p(5, 3)), Action.Attack(p(7, 3)),
        ),
    )

    val area = AreaDef(
        4, "Taş Köprüler", "Yapı evrimi",
        "Köprülerin, surların ve kentlerin yüzyıllar boyunca büyüdüğü vadi. Ne zaman inşa ettiğin, ne ortaya çıkacağını belirler.",
        listOf(level16, level17, level18, level19, level20),
    )
}
