package com.zamaninefendisi.logic.levels

import com.zamaninefendisi.logic.Action
import com.zamaninefendisi.logic.Action.Build
import com.zamaninefendisi.logic.Action.Move
import com.zamaninefendisi.logic.Action.SwitchEra
import com.zamaninefendisi.logic.Action.Wait
import com.zamaninefendisi.logic.BuildKind.*
import com.zamaninefendisi.logic.Era.PAST
import com.zamaninefendisi.logic.Era.PRESENT
import com.zamaninefendisi.logic.Feature
import com.zamaninefendisi.logic.Goal
import com.zamaninefendisi.logic.LevelDef
import com.zamaninefendisi.logic.Pos
import com.zamaninefendisi.logic.TutorialHint

private fun p(x: Int, y: Int) = Pos(x, y)

/** Alan 1 — İlk Kırılma: Geçmiş ve Şimdi. Her bölüm nedenselliğin farklı bir yüzünü öğretir. */
object Area01 {
    private val pastPresent = listOf(PAST, PRESENT)

    val level1 = LevelDef(
        id = 1, area = 1, name = "Kırık Köprü",
        intro = "Zaman Kalesi'ne giden köprü Şimdi'de yıkılmış ve nehir köprü kurulamayacak kadar hızlı akıyor. " +
            "Ama Geçmiş'te nehir sakindi... Geçmiş'te kurduğun her yapı Şimdi'ye evrilerek ulaşır.",
        map = listOf(
            "TTGWGGG",
            "TTGWGGG",
            "G@GWGCG",
            "GGGWGGG",
            "MGGWGGM",
            "MMGWGMM",
            "MMMWMMM",
        ),
        eras = pastPresent, startEra = PRESENT, startWood = 0,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE), PRESENT to setOf(CUT)),
        goals = listOf(Goal.Reach(PRESENT, p(5, 2), "Şimdi'de Zaman Kalesi'ne ulaş")),
        maxTurns = 30, par = 8,
        hints = listOf(
            TutorialHint("Köprü Şimdi'de yok. Alttaki GEÇMİŞ düğmesine dokunarak geçmişe git.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Geçmiş'te yanındaki ağaca dokun ve keserek 2 odun topla.") { it.wood >= 2 || it.timeline.changes.any { c -> c.kind == BRIDGE } },
            TutorialHint("Nehrin kıyısına yürü, sonra nehre dokunup Köprü Kur'u seç.") { it.timeline.changes.any { c -> c.kind == BRIDGE } },
            TutorialHint("Şimdi'ye dön: ahşap köprün yüzyıllar içinde taş köprüye dönüştü!") { it.heroEra == PRESENT || it.heroPos.x > 3 },
            TutorialHint("Köprüden geçip bayraklı Zaman Kalesi'ne yürü.") { false },
        ),
        solution = listOf(
            SwitchEra(PAST), Build(CUT, p(1, 1)), Move(p(2, 2)), Build(BRIDGE, p(3, 2)),
            SwitchEra(PRESENT), Move(p(3, 2)), Move(p(4, 2)), Move(p(5, 2)),
        ),
    )

    val level2 = LevelDef(
        id = 2, area = 1, name = "Kumların Yürüyüşü",
        intro = "Batıdaki çöl her çağda iki adım ilerliyor ve Şimdi'de köyün tarlalarını yutmuş. " +
            "Bir çayırın 1 hücre yakınında bitki varsa kum oraya giremez. Geçmiş'e dikilen fidan Şimdi'de ağaç olur.",
        map = listOf(
            "SSGGGGM",
            "SSGFGGG",
            "SSGGGTG",
            "SSGFGGG",
            "SSGGG@G",
            "SSGFGGG",
            "SSGGGGW",
            "SMGGGWW",
        ),
        eras = pastPresent, startEra = PRESENT, desertSpeed = 2,
        allowed = mapOf(PAST to setOf(PLANT), PRESENT to setOf(PLANT)),
        goals = listOf(Goal.FeatureAt(PRESENT, listOf(p(3, 1), p(3, 3), p(3, 5)), Feature.FIELD, "Şimdi'de üç tarlayı da yeşil tut")),
        maxTurns = 12, par = 5,
        hints = listOf(
            TutorialHint("Tarlalar Şimdi'de kurumuş. Çölü durdurmak için Geçmiş'e git.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Bir fidan, çevresindeki 8 hücreyi kumdan korur. İki fidanla üç tarlayı kurtarabilirsin.") { false },
        ),
        solution = listOf(SwitchEra(PAST), Build(PLANT, p(4, 4)), Move(p(4, 4)), Move(p(4, 3)), Build(PLANT, p(4, 2))),
    )

    val level3 = LevelDef(
        id = 3, area = 1, name = "Köyün Doğuşu",
        intro = "Bu vadide Şimdi'de hiçbir kasaba yok. Geçmiş'te kurulan bir köy, suya yakınsa ve çevresi kumla kaplanmazsa kasabaya dönüşür. " +
            "Köy için 3 odun gerekir — ama hangi ağacı kestiğin, çölün nereye ilerleyeceğini belirler.",
        map = listOf(
            "MMGGGGG",
            "SGGGGGG",
            "SGGTWGG",
            "SG@GWGG",
            "STGGWGG",
            "SGGGWGT",
            "SGGGWGG",
            "MMMMWMM",
        ),
        eras = pastPresent, startEra = PRESENT, startWood = 1, desertSpeed = 2,
        allowed = mapOf(PAST to setOf(CUT, VILLAGE, PLANT), PRESENT to setOf(CUT, PLANT)),
        goals = listOf(Goal.FeatureCount(PRESENT, Feature.TOWN, 1, "Şimdi'de bir kasaba oluştur")),
        maxTurns = 14, par = 4,
        hints = listOf(
            TutorialHint("Geçmiş'e git: köyler yalnızca orada kurulabilir.") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Kestiğin ağacın koruduğu çayırlar Şimdi'de kuma döner. Hangi ağacın gerçekten gerekli olduğunu düşün.") { false },
        ),
        solution = listOf(SwitchEra(PAST), Move(p(3, 3)), Build(CUT, p(3, 2)), Build(VILLAGE, p(3, 4))),
    )

    val level4 = LevelDef(
        id = 4, area = 1, name = "Ahşap ve Taş",
        intro = "Şimdi'de iki akıncı dağ geçidinden Zaman Kalesi'ne yürüyor. Şimdi'de kurulan ahşap çiti iki darbede yıkarlar. " +
            "Geçmiş'te kurulan çit ise yüzyıllar içinde yıkılmaz bir taş sura dönüşür.",
        map = listOf(
            "GGGGGGG",
            "GGGGGGG",
            "MMMGGMM",
            "MMMGGMM",
            "GGG@GGG",
            "GTGGGTG",
            "GGGGGGG",
            "GGGCGGG",
        ),
        eras = pastPresent, startEra = PRESENT, startWood = 2,
        allowed = mapOf(PAST to setOf(WALL, CUT), PRESENT to setOf(WALL, CUT)),
        enemies = listOf(com.zamaninefendisi.logic.EnemySpawn(p(2, 0)), com.zamaninefendisi.logic.EnemySpawn(p(4, 0))),
        protect = listOf(p(3, 7)),
        goals = listOf(Goal.Survive(10, "Zaman Kalesi'ni 10 tur koru")),
        maxTurns = 10, par = 4,
        hints = listOf(
            TutorialHint("Akıncılar her turda bir adım ilerler. Geçmiş'e geçip geçidi kapat!") { it.heroEra == PAST || it.timeline.changes.isNotEmpty() },
            TutorialHint("Geçmiş'te bir akıncının Şimdi'de durduğu hücreye çit kurarsan, akıncı zamanın içinde kaybolur.") { it.enemiesDefeated > 0 },
            TutorialHint("Geçit kapandıysa Bekle düğmesiyle turları geçir.") { false },
        ),
        solution = listOf(SwitchEra(PAST), Build(WALL, p(3, 3)), Move(p(4, 4)), Build(WALL, p(4, 3))) + List(6) { Wait },
    )

    val level5 = LevelDef(
        id = 5, area = 1, name = "Kırılma Komutanı",
        intro = "Alan patronu! Nehrin doğusundaki düşman kalesi her 4 turda bir akıncı çıkarıyor. Kale, Geçmiş'teki küçük bir kamptan doğdu. " +
            "Kampı Geçmiş'te dağıtırsan kale hiç var olmaz — ama zaten yola çıkmış akıncılar kaybolmaz.",
        map = listOf(
            "GGGGWGGG",
            "GGGGWGEG",
            "GGGGWGGG",
            "MGTGWGGG",
            "MG@GWGGM",
            "GGGGWGGM",
            "GGGGWGGG",
            "GCGGWMMM",
        ),
        eras = pastPresent, startEra = PRESENT, startWood = 1,
        allowed = mapOf(PAST to setOf(CUT, BRIDGE, WALL, DISMANTLE), PRESENT to setOf(CUT, WALL)),
        enemies = listOf(com.zamaninefendisi.logic.EnemySpawn(p(2, 0))),
        spawnEvery = 4, maxEnemies = 3,
        protect = listOf(p(1, 7)),
        goals = listOf(
            Goal.NoFeature(PRESENT, Feature.ENEMY_FORT, "Şimdi'deki düşman kalesini yok et"),
            Goal.DefeatAll("Bütün akıncıları yen"),
        ),
        maxTurns = 30, par = 14,
        hints = listOf(
            TutorialHint("Önce kaleye yürüyen akıncıyı durdur: yanına geldiğinde ona dokunup saldır.") { it.enemiesDefeated > 0 },
            TutorialHint("Düşman kampı Geçmiş'te nehrin doğusunda. Oraya ulaşmak için Geçmiş'te köprü gerekir.") { it.timeline.changes.any { c -> c.kind == DISMANTLE } },
            TutorialHint("Kalan akıncıları Şimdi'de yen.") { false },
        ),
        solution = listOf(
            Build(CUT, p(2, 3)), Move(p(2, 3)), Action.Attack(p(2, 2)),
            SwitchEra(PAST), Move(p(3, 3)), Build(BRIDGE, p(4, 3)), Move(p(4, 3)), Move(p(5, 3)), Move(p(5, 2)),
            Build(WALL, p(5, 3)), Move(p(5, 1)), Build(DISMANTLE, p(6, 1)), SwitchEra(PRESENT), Action.Attack(p(6, 1)),
        ),
    )

    val area = AreaDef(
        1, "İlk Kırılma", "Geçmiş ve Şimdi",
        "Zaman Çekirdeği'nin ilk kırıldığı vadi. Geçmiş'te yaptığın her şey Şimdi'yi değiştirir.",
        listOf(level1, level2, level3, level4, level5),
    )
}
