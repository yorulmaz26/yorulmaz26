package com.yuruyensehir.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.yuruyensehir.game.ui.Palette
import com.yuruyensehir.logic.*

/** Alt sayfa panellerinin içerikleri. Her çağrıda güncel durumdan yeniden kurulur. */
object Panels {
    private const val GREEN = "[#2E8B57]"
    private const val RED = "[#D84A2B]"
    private const val SOFT = "[#8A7660]"

    private fun Table.section(s: GameScreen, text: String, width: Float) {
        add(s.ui.label(text, "default")).width(width).padTop(14f).padBottom(4f).row()
    }

    private fun Table.text(s: GameScreen, text: String, width: Float, style: String = "small") {
        add(s.ui.label(text, style, wrap = true)).width(width).padTop(2f).row()
    }

    private fun Table.cardRow(card: Table, width: Float) {
        add(card).width(width).padTop(8f).row()
    }

    fun effectText(def: BuildingDef): String {
        val parts = ArrayList<String>()
        if (def.produces.isNotEmpty()) parts += "Üretir: " + def.produces.entries.joinToString(", ") { "${it.key.title} ${fmt(it.value)}/dk" }
        if (def.consumes.isNotEmpty()) parts += "Tüketir: " + def.consumes.entries.joinToString(", ") { "${it.key.title} ${fmt(it.value)}/dk" }
        if (def.housing > 0) parts += "Barınma: ${def.housing} kişi"
        if (def.storage.isNotEmpty()) parts += "Depo: " + def.storage.entries.filter { it.key != Resource.WASTE }.take(4).joinToString(", ") { "${it.key.title} +${fmt(it.value)}" } + if (def.storage.size > 4) "…" else ""
        if (def.workers > 0) parts += "İşçi: ${def.workers} (${def.profession?.title ?: "-"})"
        if (def.happiness > 0) parts += "Mutluluk +${fmt(def.happiness)}"
        if (def.researchPerMin > 0) parts += "Bilgi +${fmt(def.researchPerMin)}/dk"
        if (def.taxPerPop > 0) parts += "Vergi: kişi başı ${fmt(def.taxPerPop)} altın/dk"
        if (def.carrierCarePerMin > 0) parts += "Taşıyıcı bakımı ${fmt(def.carrierCarePerMin)}/dk"
        if (def.gatherPerMin > 0) parts += "Toplama ${fmt(def.gatherPerMin)}/dk (biyoma göre)"
        if (def.supportRadius > 0) parts += "Çevredeki binalar %20 hafifler"
        if (def.healthRadius > 0) parts += "Sağlık kapsamı ${def.healthRadius} kare"
        if (def.revealBonus > 0) parts += "Keşif menzili +${def.revealBonus}"
        if (def.travelSpeedBonus > 0) parts += "Yolculuk hızı +%${(def.travelSpeedBonus * 100).toInt()}"
        if (def.towerDps > 0) parts += "Savunma: ${fmt(def.towerDps)} hasar/sn, menzil ${fmt(def.towerRange)}" + if (def.towerEnergyPerShot > 0) " (atış başı ${fmt(def.towerEnergyPerShot)} enerji)" else ""
        if (def.shieldCapacity > 0) parts += "Kalkan ${fmt(def.shieldCapacity)}, yarıçap ${def.shieldRadius}"
        if (def.guardCount > 0) parts += "${def.guardCount} muhafız"
        if (def.upkeepGold > 0) parts += "Bakım: ${fmt(def.upkeepGold)} altın/dk"
        return parts.joinToString(" • ")
    }

    // ------------------------------------------------------------------ İnşa
    fun build(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val tabs = Table()
        for (c in BuildingCategory.entries) {
            val b = s.ui.button(c.title, "chip") { s.currentBuildCategory = c }
            b.pad(8f, 12f, 8f, 12f)
            b.isChecked = s.currentBuildCategory == c
            tabs.add(b).padRight(6f)
        }
        t.add(tabs).left().row()
        val r = s.core.lastBalance
        t.text(s, "${SOFT}Yük ${r.totalWeight.toInt()}/${r.capacity.toInt()} • Denge %${r.balancePercent} • Alan: ${CityGrid.usableCells(st)} kare[]", width)
        val defs = BuildingCatalog.all.filter { it.category == s.currentBuildCategory && it.buildable }
        for (def in defs) {
            val card = s.ui.card(Color.WHITE)
            val lock = BuildingSystem.lockReason(st, def)
            val uniqueDone = def.unique && st.count(def.type) > 0
            val left = Table()
            left.add(s.ui.label(def.title + if (st.count(def.type) > 0) "  ${SOFT}(${st.count(def.type)} adet)[]" else "", "default")).left().row()
            left.add(s.ui.label("${SOFT}${def.w}x${def.h} kare • Ağırlık ${fmt(def.weight)} • ${fmt(def.buildSeconds)} sn[]", "small")).left().row()
            left.add(s.ui.label(def.description, "small", wrap = true)).width(width - 190f).left().padTop(2f).row()
            left.add(s.ui.label(effectText(def), "small", wrap = true)).width(width - 190f).left().padTop(2f).row()
            def.adjacency.forEach { a -> left.add(s.ui.label("${GREEN}Komşuluk: ${a.note}[]", "small", wrap = true)).width(width - 190f).left().row() }
            left.add(s.ui.label("Maliyet: " + s.ui.costText(def.cost, st.store), "small", wrap = true)).width(width - 190f).left().padTop(4f).row()
            if (lock != null) left.add(s.ui.label("$RED$lock[]", "small", wrap = true)).width(width - 190f).left().row()
            if (uniqueDone) left.add(s.ui.label("${SOFT}Bu yapıdan yalnızca bir tane olabilir.[]", "small")).left().row()
            card.add(left).expandX().left().top()
            val btn = s.ui.button("Seç", "primary") { s.beginPlacement(def.type) }
            btn.isDisabled = lock != null || uniqueDone || !st.store.canAfford(def.cost)
            card.add(btn).width(140f).right().top()
            t.cardRow(card, width)
        }
    }

    // ------------------------------------------------------------------ Bina
    fun building(s: GameScreen, t: Table, width: Float, b: PlacedBuilding) {
        val st = s.state
        val def = b.def
        val status = st.buildingStatus[b.id]
        val eff = ProductionSystem.efficiency(st, b)
        val stText = when {
            b.underConstruction -> "${SOFT}${if (b.level > 1) "Geliştiriliyor" else "İnşa ediliyor"}: ${b.constructionLeft.toInt()} sn kaldı[]"
            !b.enabled -> "${RED}Kapalı[]"
            def.workers > 0 && b.workers == 0 -> "${RED}Çalışan yok![]"
            status?.missingInput != null && status.inputRatio < 0.99 -> "${RED}Girdi eksik: ${status.missingInput?.title} (%${(status.inputRatio * 100).toInt()})[]"
            else -> "${GREEN}Çalışıyor • Verim %${(eff * 100).toInt()}[]"
        }
        t.text(s, def.description, width)
        t.text(s, stText, width, "default")

        val hp = Table()
        hp.add(s.ui.label("Sağlamlık %${b.hp.toInt()}", "small")).width(200f).left()
        hp.add(s.bar((b.hp / 100).toFloat(), if (b.hp > 60) Palette.green else Palette.orange, width - 220f)).left()
        t.add(hp).width(width).padTop(6f).row()

        if (def.workers > 0) {
            val w = Table()
            w.add(s.ui.label("Çalışanlar: ${b.workers}/${def.workers} ${def.profession?.title ?: ""} • Boşta: ${PopulationSystem.freeWorkers(st)}", "small")).expandX().left()
            w.add(s.ui.button("-", "default") { s.act(BuildingSystem.setWorkers(st, b.id, b.workers - 1)) }).width(70f).padRight(6f)
            w.add(s.ui.button("+", "default") { s.act(BuildingSystem.setWorkers(st, b.id, b.workers + 1)) }).width(70f)
            t.add(w).width(width).padTop(6f).row()
        }

        val ratio = status?.inputRatio ?: 1.0
        val bio = ProductionSystem.biomeOutputFactor(st, b)
        if (def.produces.isNotEmpty() && !b.underConstruction) t.text(s, "Üretim: " + def.produces.entries.joinToString(", ") { "${it.key.title} ${fmt(it.value * eff * ratio * bio)}/dk" }, width)
        if (def.consumes.isNotEmpty() && !b.underConstruction) t.text(s, "Tüketim: " + ProductionSystem.consumesWithModifiers(st, b).entries.joinToString(", ") { "${it.key.title} ${fmt(it.value * eff * ratio)}/dk" }, width)
        if (def.gatherPerMin > 0) {
            val biome = st.world.biomeAt(st.travel.position)
            t.text(s, "Toplanan (${biome.title}): " + biome.yields.entries.joinToString(", ") { "${it.key.title} ${fmt(def.gatherPerMin * eff * it.value * if (st.travel.traveling) 0.4 else 1.0)}/dk" } +
                " • Kalan yatak: ${fmt(ProductionSystem.depositLeft(st, st.travel.position))}", width)
        }
        if (def.housing > 0) t.text(s, "Barınma: ${(def.housing * (1 + 0.5 * (b.level - 1))).toInt()} kişi", width)
        val others = effectText(def).split(" • ").filter { !it.startsWith("Üretir") && !it.startsWith("Tüketir") && !it.startsWith("Barınma") && !it.startsWith("İşçi") }
        if (others.isNotEmpty()) t.text(s, others.joinToString(" • "), width)
        def.adjacency.forEach { a ->
            val on = BuildingSystem.activeAdjacencies(st, b).contains(a)
            t.text(s, (if (on) "$GREEN• " else "$SOFT• ") + a.note + if (on) " (etkin)[]" else " (komşu yok)[]", width)
        }
        if (def.towerDps > 0) {
            t.text(s, "Kule: ${fmt(CombatSystem.towerDps(st, b))} hasar/sn • menzil ${fmt(CombatSystem.towerRange(st, b))} kare (menzil halkası şehirde gösterilir)", width)
            val pr = Table()
            val gen = s.ui.button("Genel", "chip") { s.act(CombatSystem.setTowerPriority(st, b.id, null)) }
            gen.pad(6f, 8f, 6f, 8f); gen.isChecked = b.targetPriority == null
            pr.add(gen).padRight(4f)
            for (p in TargetPriority.entries) {
                val bt = s.ui.button(p.title, "chip") { s.act(CombatSystem.setTowerPriority(st, b.id, p)) }
                bt.pad(6f, 8f, 6f, 8f); bt.isChecked = b.targetPriority == p
                pr.add(bt).padRight(4f).padTop(4f)
                if (p.ordinal == 1) pr.row()
            }
            t.add(s.ui.label("Bu kulenin hedef önceliği:", "small")).left().padTop(4f).row()
            t.add(pr).left().row()
        }
        val supported = st.buildings.any { it.def.supportRadius > 0 && it.id != b.id && it.gapTo(b) <= 1 } && def.supportRadius == 0
        t.text(s, "${SOFT}Ağırlık: ${fmt(WeightBalanceSystem.weightOf(st, b, supported))}${if (supported) " (destekli)" else ""} • Konum ${b.x},${b.y}[]", width)

        val actions = Table()
        actions.defaults().expandX().fillX().pad(4f)
        if (b.level < def.maxLevel) {
            val cost = BuildingSystem.upgradeCost(b)
            val up = s.ui.button("Geliştir › Sv ${b.level + 1}", "primary") { s.act(BuildingSystem.upgrade(st, b.id)) }
            up.isDisabled = b.underConstruction || !st.store.canAfford(cost)
            actions.add(up).colspan(2).row()
            actions.add(s.ui.label("Maliyet: " + s.ui.costText(cost, st.store) + "  ${SOFT}(geliştirme sırasında bina çalışmaz)[]", "small", wrap = true)).colspan(2).row()
        } else actions.add(s.ui.label("${SOFT}En yüksek seviye.[]", "small")).colspan(2).row()
        if (b.hp < 99.5) {
            val cost = BuildingSystem.repairCost(b)
            val rep = s.ui.button("Onar (${cost.describe()})", "gold") { s.act(BuildingSystem.repair(st, b.id)) }
            rep.isDisabled = !st.store.canAfford(cost)
            actions.add(rep).colspan(2).row()
        }
        if (b.type != BuildingType.CITY_CENTER) {
            val mv = s.ui.button("Taşı", "default") { s.startMove(b) }
            mv.isDisabled = b.underConstruction
            actions.add(mv)
            actions.add(s.ui.button(if (b.enabled) "Kapat" else "Aç", "default") { s.act(BuildingSystem.setEnabled(st, b.id, !b.enabled)) }).row()
            actions.add(s.ui.label("${SOFT}Taşıma ücreti: ${BuildingSystem.moveCost(b).describe()}[]", "small", wrap = true)).colspan(2).row()
            actions.add(s.ui.button(if (s.demolishPending(b)) "Emin misiniz? Tekrar dokunun" else "Sök (%50 iade)", "danger") { s.requestDemolish(b) }).colspan(2).row()
        }
        t.add(actions).width(width).padTop(10f).row()
    }

    // ------------------------------------------------------------------ Taşıyıcı
    fun carrier(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val c = st.carrier
        val r = s.core.lastBalance
        t.text(s, "${c.name} • ${c.kind.title} (${c.kind.trait}) • Seviye ${c.level}", width, "default")
        val xp = Table()
        xp.add(s.ui.label("Deneyim ${c.xp.toInt()}/${CarrierSystem.xpForNext(c.level).toInt()}", "small")).width(240f).left()
        xp.add(s.bar((c.xp / CarrierSystem.xpForNext(c.level)).toFloat(), Palette.gold, width - 260f))
        t.add(xp).width(width).padTop(4f).row()

        fun stat(name: String, v: Double, max: Double, col: Color, note: String = "") {
            val row = Table()
            row.add(s.ui.label("$name %${(v / max * 100).toInt()}$note", "small")).width(240f).left()
            row.add(s.bar((v / max).toFloat(), col, width - 260f))
            t.add(row).width(width).padTop(4f).row()
        }
        stat("Can", c.health, CarrierSystem.maxHealth(st), Palette.green)
        stat("Açlık", c.hunger, 100.0, if (c.hunger > 70) Palette.red else Palette.orange)
        stat("Susuzluk", c.thirst, 100.0, if (c.thirst > 70) Palette.red else Color.valueOf("4FC3F7"))
        stat("Yorgunluk", c.fatigue, 100.0, if (c.fatigue > 80) Palette.red else Color.valueOf("B39DDB"))
        stat("Enerji", c.energy, 100.0, Palette.gold)
        stat("Güven", c.trust, 100.0, Palette.turquoise)

        val biome = st.world.biomeAt(st.travel.position)
        t.text(s, "Hız: ${fmt(CarrierSystem.speed(st, r))} bölge/dk (arazi: ${biome.title} ×${fmt(CarrierSystem.terrainFactor(st, biome))}) • Kapasite ${r.capacity.toInt()} • Hareket verimliliği %${r.efficiencyPercent}", width)
        val (food, water) = CarrierSystem.estimatedFeedPerMinute(st)
        t.text(s, "Tahmini ihtiyaç: yiyecek ${fmt(food)}/dk, su ${fmt(water)}/dk. Dinlenirken bulunduğu araziden kendini besler (${biome.title}: otlak %${(biome.forage * 100).toInt()}, su %${(biome.waterAvail * 100).toInt()}).", width)
        if (c.forcedRest) t.text(s, "${RED}${c.name} bitkin! Yorgunluk %55'in altına inene kadar yürümeyecek.[]", width)
        if (r.overloaded) t.text(s, "${RED}Aşırı yük! Taşıyıcı yavaşlar ve hızla yorulur. Bina sökün, kaynak harcayın veya kapasiteyi geliştirin.[]", width)

        val ctl = Table()
        ctl.defaults().expandX().fillX().pad(4f)
        ctl.add(s.ui.button("Besle", "green") { s.act(CarrierSystem.feed(st)) })
        ctl.add(s.ui.button(if (c.resting) "Uyandır" else "Dinlendir", "primary") { s.act(CarrierSystem.setResting(st, !c.resting)) }).row()
        ctl.add(s.ui.button("Otomatik besleme: ${if (c.autoFeed) "Açık" else "Kapalı"}", "default") { c.autoFeed = !c.autoFeed; s.act(ActionResult.ok(if (c.autoFeed) "Otomatik besleme açıldı." else "Otomatik besleme kapatıldı.")) }).colspan(2)
        t.add(ctl).width(width).padTop(8f).row()

        t.section(s, "Özel Yetenekler", width)
        for (a in CarrierAbility.entries) {
            val card = s.ui.card(Color.WHITE)
            val left = Table()
            val active = c.abilityActiveLeft[a] ?: 0.0
            val cd = c.abilityCooldown[a] ?: 0.0
            val state = when {
                c.level < CarrierSystem.unlockLevel(st, a) -> "${SOFT}Seviye ${CarrierSystem.unlockLevel(st, a)}'de açılır[]"
                active > 0 -> "${GREEN}Etkin: ${active.toInt()} sn[]"
                cd > 0 -> "${SOFT}Hazırlanıyor: ${cd.toInt()} sn[]"
                else -> "${GREEN}Hazır[]" + if (a.energyCost > 0) " • Enerji ${a.energyCost.toInt()}" else ""
            }
            left.add(s.ui.label(a.title, "default")).left().row()
            left.add(s.ui.label(a.description, "small", wrap = true)).width(width - 190f).left().row()
            left.add(s.ui.label(state, "small")).left()
            card.add(left).expandX().left()
            val b = s.ui.button("Kullan", "primary") { s.act(CarrierSystem.activateAbility(st, a)) }
            b.isDisabled = c.level < CarrierSystem.unlockLevel(st, a) || cd > 0 || c.energy < a.energyCost
            card.add(b).width(140f)
            t.cardRow(card, width)
        }

        t.section(s, "Taşıyıcı Koleksiyonu (${st.collection.species.size}/${CarrierSpecies.entries.size})", width)
        t.text(s, "${SOFT}Yeni türler Keşif Merkezi'nin 'Yeni Taşıyıcı Türü Keşfi' göreviyle, türün yaşadığı biyomda bulunur. Şehri yeni bir taşıyıcıya aktarmak bütün binaları ve geliştirmeleri korur; binalar yeni sırta sığmalıdır. Boştaki türlerle yan şehir kurulabilir (Filo).[]", width)
        for (sp in CarrierSpecies.entries) {
            val known = sp in st.collection.species
            val card = s.ui.card(if (c.kind == sp) Color.valueOf("E6F7F5") else Color.WHITE)
            val l = Table()
            if (known) {
                val use = when {
                    c.kind == sp -> "${GREEN}Ana şehri taşıyor[]"
                    st.sideCities.any { it.species == sp } -> "${SOFT}Yan şehri taşıyor: ${st.sideCities.first { it.species == sp }.name}[]"
                    else -> "${SOFT}Boşta[]"
                }
                l.add(s.ui.label("${sp.title} • ${sp.trait}", "default")).left().row()
                l.add(s.ui.label(sp.description, "small", wrap = true)).width(width - 190f).left().row()
                l.add(s.ui.label("Kapasite ×${fmt(sp.capacityMult)} • Hız ×${fmt(sp.speedMult)} • Hasar ×${fmt(sp.damageMult)} • İhtiyaç ×${fmt(sp.needMult)}" +
                    (if (sp.backBonus != 0) " • Sırt ${if (sp.backBonus > 0) "geniş" else "dar"}" else "") +
                    (if (sp.adapted.isNotEmpty()) " • Uyum: ${sp.adapted.joinToString { it.title }}" else "") + " • $use", "small", wrap = true)).width(width - 190f).left()
                card.add(l).expandX().left()
                if (c.kind != sp && st.sideCities.none { it.species == sp }) {
                    val reason = CollectionSystem.switchReason(st, sp)
                    val b = s.ui.button("Aktar", "primary") { s.act(CollectionSystem.switchCarrier(st, sp)) }
                    b.isDisabled = reason != null
                    card.add(b).width(140f)
                    t.cardRow(card, width)
                    if (reason != null) t.text(s, "$SOFT$reason[]", width)
                    continue
                }
            } else {
                l.add(s.ui.label("${SOFT}??? — keşfedilmedi[]", "default")).left().row()
                l.add(s.ui.label("${SOFT}İpucu: ${sp.habitat.joinToString { it.title }} bölgelerinde aranabilir${if (sp.minStage > 0) " (en az ${StageSystem.title(sp.minStage)})" else ""}.[]", "small", wrap = true)).width(width - 40f).left()
                card.add(l).expandX().left()
            }
            t.cardRow(card, width)
        }

        t.section(s, "Taşıyıcı Gelişimi", width)
        for (u in CarrierUpgrade.entries) {
            val card = s.ui.card(Color.WHITE)
            val left = Table()
            val lvl = c.upgrade(u)
            left.add(s.ui.label("${u.title}  ${SOFT}$lvl/${u.maxLevel}[]", "default")).left().row()
            left.add(s.ui.label(u.description, "small", wrap = true)).width(width - 190f).left().row()
            if (lvl < u.maxLevel) {
                val req = CarrierSystem.requiredLevel(st, u)
                val cost = CarrierSystem.upgradeCost(st, u)
                left.add(s.ui.label("Maliyet: " + s.ui.costText(cost, st.store) + if (c.level < req) "  ${RED}Seviye $req gerekli[]" else "", "small", wrap = true)).width(width - 190f).left()
                val b = s.ui.button("Geliştir", "primary") { s.act(CarrierSystem.upgrade(st, u)) }
                b.isDisabled = c.level < req || !st.store.canAfford(cost)
                card.add(left).expandX().left()
                card.add(b).width(140f)
            } else {
                left.add(s.ui.label("${GREEN}Tamamlandı[]", "small")).left()
                card.add(left).expandX().left()
            }
            t.cardRow(card, width)
        }
    }

    // ------------------------------------------------------------------ Ekonomi
    fun economy(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val r = s.core.lastBalance
        t.section(s, "Ağırlık ve Denge", width)
        val bal = Table()
        bal.add(s.ui.label("Denge %${r.balancePercent}", "small")).width(240f).left()
        bal.add(s.bar(r.balance.toFloat(), if (r.balance > 0.85) Palette.green else if (r.balance > 0.6) Palette.gold else Palette.red, width - 260f))
        bal.row()
        bal.add(s.ui.label("Yük ${r.totalWeight.toInt()}/${r.capacity.toInt()}", "small")).width(240f).left().padTop(4f)
        bal.add(s.bar(r.loadRatio.toFloat().coerceAtMost(1f), if (r.loadRatio > 1) Palette.red else if (r.loadRatio > 0.8) Palette.gold else Palette.turquoise, width - 260f)).padTop(4f)
        t.add(bal).width(width).row()
        t.text(s, "Bina ağırlığı ${r.buildingWeight.toInt()} • Depolanan yük ${r.cargoWeight.toInt()} • Ön %${(r.frontShare * 100).toInt()} / Arka %${100 - (r.frontShare * 100).toInt()} • Sol %${(r.leftShare * 100).toInt()} / Sağ %${100 - (r.leftShare * 100).toInt()} • Hareket verimliliği %${r.efficiencyPercent}", width)
        val tips = ArrayList<String>()
        if (r.offsetX < -0.12) tips += "Ağırlık arkada: ağır binaları veya depoları öne taşıyın."
        if (r.offsetX > 0.12) tips += "Ağırlık önde: bazı ağır binaları arkaya alın."
        if (r.offsetY > 0.1) tips += "Sol taraf ağır: dengeyi sağa kaydırın."
        if (r.offsetY < -0.1) tips += "Sağ taraf ağır: dengeyi sola kaydırın."
        if (r.loadRatio > 0.8) tips += "Yük kapasiteye yakın: Destek İskelesi kurun, hafif yapı araştırın veya stokları kullanın."
        if (tips.isEmpty()) tips += "Denge iyi durumda. Depolanan kaynaklar da ağırlık yapar ve depolara dağılır."
        tips.forEach { t.text(s, "• $it", width) }

        t.section(s, "Kaynaklar (dakika başına)", width)
        val flow = EconomySystem.estimate(st)
        val tbl = Table()
        tbl.defaults().pad(3f)
        tbl.add(s.ui.label("${SOFT}Kaynak[]", "small")).width(width * 0.36f).left()
        tbl.add(s.ui.label("${SOFT}Stok[]", "small")).width(width * 0.22f).left()
        tbl.add(s.ui.label("${SOFT}Üretim[]", "small")).width(width * 0.14f).left()
        tbl.add(s.ui.label("${SOFT}Tüketim[]", "small")).width(width * 0.14f).left()
        tbl.add(s.ui.label("${SOFT}Net[]", "small")).width(width * 0.12f).left().row()
        for (res in Resource.entries) {
            val p = flow.produced[res] ?: 0.0; val c = flow.consumed[res] ?: 0.0
            if (st.store[res] < 0.5 && p == 0.0 && c == 0.0 && res.advanced) continue
            val name = Table()
            name.add(s.ui.dot(Palette.resource(res), 14f)).size(14f).padRight(6f)
            name.add(s.ui.label(res.title, "small"))
            tbl.add(name).left()
            tbl.add(s.ui.label(if (res == Resource.GOLD) fmt(st.store[res]) else "${fmt(st.store[res])}/${fmt(st.store.cap(res))}", "small")).left()
            tbl.add(s.ui.label(fmt(p), "small")).left()
            tbl.add(s.ui.label(fmt(c), "small")).left()
            tbl.add(s.ui.label(s.ui.rate(p - c), "small")).left().row()
        }
        t.add(tbl).width(width).row()
        t.text(s, "${SOFT}Tüketime nüfus (kişi başı yiyecek ${fmt(PopulationSystem.FOOD_PER_POP)}, su ${fmt(PopulationSystem.WATER_PER_POP)}/dk) ve taşıyıcının otomatik beslenmesi dahildir.[]", width)

        t.section(s, "Üretim Zincirleri", width)
        val chains = listOf(
            "Su + Tohum › Tarla › Yiyecek" to listOf(BuildingType.WATER_COLLECTOR, BuildingType.FARM),
            "Odun + Metal › Atölye › Alet" to listOf(BuildingType.WORKSHOP),
            "Taş + Enerji › Metal İşleme › Metal" to listOf(BuildingType.METALWORKS, BuildingType.POWER_PLANT),
            "Odun › Enerji Üretim Tesisi › Enerji" to listOf(BuildingType.POWER_PLANT),
            "Yiyecek + Su › Tekstil Atölyesi › Kumaş" to listOf(BuildingType.TEXTILE),
            "Metal + Kristal + Nadir Maden › Teknoloji Laboratuvarı › Teknoloji Bileşeni, Hafif Malzeme" to listOf(BuildingType.TECH_LAB),
            "Yiyecek + Su › Taşıyıcı Bakım Merkezi › Dayanıklılık ve Güven" to listOf(BuildingType.CARRIER_CARE),
            "Atık › Geri Dönüşüm Merkezi › Taş + Metal" to listOf(BuildingType.RECYCLER),
            "Arazi › Toplayıcı Rampası › Biyom kaynakları" to listOf(BuildingType.GATHERER),
        )
        for ((txt, req) in chains) {
            val ok = req.all { st.count(it) > 0 }
            t.text(s, (if (ok) "$GREEN• " else "$SOFT• ") + txt + "[]", width)
        }
    }

    // ------------------------------------------------------------------ Nüfus
    fun population(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val p = st.population
        val housing = PopulationSystem.housing(st)
        t.text(s, "Nüfus ${p.count} / barınma $housing • Çalışan ${PopulationSystem.employed(st)} • Boşta ${PopulationSystem.freeWorkers(st)}", width, "default")
        val bars = Table()
        bars.add(s.ui.label("Mutluluk %${p.happiness.toInt()}", "small")).width(240f).left()
        bars.add(s.bar((p.happiness / 100).toFloat(), Palette.gold, width - 260f)).row()
        bars.add(s.ui.label("Sağlık %${p.health.toInt()}", "small")).width(240f).left().padTop(4f)
        bars.add(s.bar((p.health / 100).toFloat(), Palette.green, width - 260f)).padTop(4f).row()
        bars.add(s.ui.label("Yeni yerleşimci", "small")).width(240f).left().padTop(4f)
        bars.add(s.bar((p.growthTimer / PopulationSystem.GROWTH_SECONDS).toFloat(), Palette.turquoise, width - 260f)).padTop(4f)
        t.add(bars).width(width).padTop(4f).row()
        if (p.count >= housing) t.text(s, "${RED}Barınma dolu: nüfus artışı için ev inşa edin.[]", width)
        val hb = PopulationSystem.happinessBreakdown(st)
        t.text(s, "Mutluluk etkenleri: " + hb.factors.joinToString(", ") { (n, v) -> "$n ${if (v >= 0) "$GREEN+" else RED}${fmt(v)}[]" } + " › hedef %${hb.target.toInt()}", width)

        t.section(s, "Meslekler", width)
        val prof = PopulationSystem.byProfession(st)
        t.text(s, if (prof.isEmpty()) "${SOFT}Henüz kimse çalışmıyor.[]" else prof.entries.joinToString(" • ") { "${it.key.title}: ${it.value}" }, width)

        t.section(s, "Çalışan Atama", width)
        t.add(s.ui.button("Boştakileri Otomatik Ata", "primary") { PopulationSystem.autoAssign(st); s.act(ActionResult.ok("Çalışanlar dağıtıldı.")) }).width(width).row()
        for (b in st.buildings.filter { it.def.workers > 0 }.sortedBy { it.id }) {
            val row = Table()
            val col = if (b.workers < b.def.workers) RED else ""
            row.add(s.ui.label("${b.def.title} Sv${b.level}  $col${b.workers}/${b.def.workers}${if (col.isNotEmpty()) "[]" else ""} ${SOFT}${b.def.profession?.title ?: ""}${if (!b.enabled) " (kapalı)" else ""}[]", "small", wrap = true)).width(width - 170f).left()
            row.add(s.ui.button("-", "default") { s.act(BuildingSystem.setWorkers(st, b.id, b.workers - 1)) }).width(70f).padRight(6f)
            row.add(s.ui.button("+", "default") { s.act(BuildingSystem.setWorkers(st, b.id, b.workers + 1)) }).width(70f)
            t.add(row).width(width).padTop(6f).row()
        }
    }

    // ------------------------------------------------------------------ Araştırma
    fun research(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val rs = st.research
        var rate = 0.0
        for (b in st.buildings) if (b.def.researchPerMin > 0) rate += b.def.researchPerMin * ProductionSystem.efficiency(st, b)
        if (st.count(BuildingType.RESEARCH_CENTER) == 0) t.text(s, "${RED}Bilgi üretmek için Araştırma Merkezi kurun (Yönetim).[]", width)
        t.text(s, "Bilgi üretimi: ${fmt(rate)}/dk • Biriken puan: ${fmt(rs.bankedPoints)}", width, "default")
        val cur = rs.current
        if (cur != null) {
            val def = TechCatalog.get(cur)
            val row = Table()
            row.add(s.ui.label("${def.title} ${fmt(rs.progress)}/${fmt(def.points)}", "small")).width(320f).left()
            row.add(s.bar((rs.progress / def.points).toFloat(), Palette.turquoise, width - 340f))
            t.add(row).width(width).padTop(6f).row()
            t.add(s.ui.button("Araştırmayı Durdur (puanlar saklanır)", "default") { s.act(ResearchSystem.cancel(st)) }).width(width).padTop(6f).row()
        }
        if (MasterySystem.available(st)) {
            val card = s.ui.card(Color.valueOf("FFF1C9"))
            val l = Table()
            l.add(s.ui.label("Ustalık • Seviye ${rs.masteryLevel}", "default")).left().row()
            l.add(s.ui.label("Bütün araştırmalar tamamlandı. Ustalık sınırsızdır: her seviye bütün üretim ve bilgiye +%2 ekler. Sonraki seviye: ${fmt(rs.masteryProgress)}/${fmt(MasterySystem.cost(rs.masteryLevel))} bilgi.", "small", wrap = true)).width(width - 190f).left()
            card.add(l).expandX().left()
            if (!rs.masteryActive) card.add(s.ui.button("Başlat", "primary") { s.act(MasterySystem.start(st)) }).width(140f)
            else card.add(s.ui.label("${GREEN}Sürüyor[]", "small")).width(140f)
            t.cardRow(card, width)
        }
        for (branch in ResearchBranch.entries) {
            t.section(s, branch.title, width)
            for (def in TechCatalog.all.filter { it.branch == branch }) {
                val card = s.ui.card(if (ResearchSystem.has(st, def.id)) Color.valueOf("E8F6EA") else Color.WHITE)
                val left = Table()
                left.add(s.ui.label(def.title, "default")).left().row()
                left.add(s.ui.label(def.description, "small", wrap = true)).width(width - 190f).left().row()
                when {
                    ResearchSystem.has(st, def.id) -> left.add(s.ui.label("${GREEN}Tamamlandı[]", "small")).left()
                    rs.current == def.id -> left.add(s.ui.label("${GREEN}Araştırılıyor[]", "small")).left()
                    else -> {
                        val req = def.requires.filter { !ResearchSystem.has(st, it) }
                        left.add(s.ui.label("Bilgi ${fmt(def.points)} • " + s.ui.costText(def.cost, st.store) +
                            if (req.isNotEmpty()) "  ${RED}Gerekli: ${req.joinToString { TechCatalog.get(it).title }}[]" else "", "small", wrap = true)).width(width - 190f).left()
                    }
                }
                card.add(left).expandX().left()
                if (!ResearchSystem.has(st, def.id) && rs.current != def.id) {
                    val b = s.ui.button("Başlat", "primary") { s.act(ResearchSystem.start(st, def.id)) }
                    b.isDisabled = !ResearchSystem.available(st, def.id) || rs.current != null || !st.store.canAfford(def.cost)
                    card.add(b).width(140f)
                }
                t.cardRow(card, width)
            }
        }
    }

    // ------------------------------------------------------------------ Harita / Rota
    fun map(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val tr = st.travel
        if (s.mode == ViewMode.MAP) t.add(s.ui.button("Şehre Dön", "default") { s.setMode(ViewMode.CITY); s.closePanel() }).width(width).row()
        else t.add(s.ui.button("Haritayı Aç", "primary") { s.setMode(ViewMode.MAP); s.showPanel(PanelId.MAP) }).width(width).row()

        val here = st.world.biomeAt(tr.position)
        val card = s.ui.card(Color.WHITE)
        val left = Table()
        left.add(s.ui.label("Bulunduğunuz yer: ${here.title}", "default")).left().row()
        left.add(s.ui.label(here.hint + " Tehlike %${(here.danger * 100).toInt()} • Hız ×${fmt(here.moveFactor)}", "small", wrap = true)).width(width - 40f).left().row()
        left.add(s.ui.label("Toplanabilir: " + here.yields.entries.joinToString(", ") { "${it.key.title} %${(it.value * 100).toInt()}" } + " • Kalan yatak ${fmt(ProductionSystem.depositLeft(st, tr.position))}", "small", wrap = true)).width(width - 40f).left()
        card.add(left).expandX().left()
        t.cardRow(card, width)

        if (tr.traveling) {
            val c2 = s.ui.card(Color.valueOf("E6F7F5"))
            val l2 = Table()
            l2.add(s.ui.label("Yolda: ${tr.routeName}", "default")).left().row()
            l2.add(s.ui.label("${tr.routeIndex}/${tr.route.size} bölge • kalan ~${fmt(TravelSystem.remainingMinutes(st))} dk • hedef ${st.world.biomeAt(tr.route.last()).title}" +
                if (!CarrierSystem.canMove(st)) " • ${RED}duraklatıldı[]" else "", "small", wrap = true)).width(width - 190f).left()
            c2.add(l2).expandX().left()
            c2.add(s.ui.button("Durdur", "danger") { s.act(TravelSystem.stop(st)) }).width(140f)
            t.cardRow(c2, width)
        }

        val target = s.mapTarget()
        if (target == null) {
            t.text(s, if (s.mode == ViewMode.MAP) "Rota planlamak için haritada keşfedilmiş bir bölgeye dokunun. Sürükleyerek gezinebilirsiniz." else "Haritayı açıp hedef seçin.", width)
            t.text(s, "${SOFT}Keşfedilen bölge: ${tr.explored.size} • Toplam yol: ${tr.distanceTraveled} bölge • Keşif menzili ${TravelSystem.revealRadius(st)}[]", width)
            val near = CivSystem.met(st).sortedBy { it.position.dist(tr.position) }.take(4)
            if (near.isNotEmpty()) {
                t.section(s, "Bilinen medeniyetler", width)
                near.forEach { civ -> t.text(s, "• ${civ.name} [#${Palette.relationHex(civ.status)}]${civ.status.title}[] — ${civ.position.dist(tr.position)} bölge", width) }
            }
            return
        }
        CivSystem.at(st, target)?.let { civ ->
            val cc = s.ui.card(Color.valueOf("FFF8E6"))
            val cl = Table()
            cl.add(s.ui.label("${civ.name} • [#${Palette.relationHex(civ.status)}]${civ.status.title}[]", "default")).left().row()
            cl.add(s.ui.label(civ.culture.title + " • Güç ${civ.power.toInt()}", "small")).left()
            cc.add(cl).expandX().left()
            val bt = Table()
            bt.add(s.ui.button("Ticaret", "gold") { s.openCiv(civ.id, PanelId.TRADE) }).width(150f).row()
            bt.add(s.ui.button("Diplomasi", "primary") { s.openCiv(civ.id, PanelId.DIPLOMACY) }).width(150f).padTop(4f)
            cc.add(bt)
            t.cardRow(cc, width)
        }
        val tb = st.world.biomeAt(target)
        val err = TravelSystem.canTarget(st, target)
        t.section(s, "Hedef: ${if (TravelSystem.isExplored(st, target)) tb.title else "Keşfedilmemiş bölge"} (${target.dist(tr.position)} bölge uzakta)", width)
        if (TravelSystem.isExplored(st, target)) t.text(s, tb.hint + " Toplanabilir: " + tb.yields.keys.joinToString { it.title }, width)
        if (target.dist(tr.position) >= ExplorationSystem.MIN_RANGE)
            t.add(s.ui.button("Buraya keşif ekibi gönder", "default") { s.expDirection = 4; s.showPanel(PanelId.EXPLORATION) }).width(width).padTop(4f).row()
        if (err != null) { t.text(s, "$RED$err[]", width); return }
        if (tr.traveling) t.text(s, "${SOFT}Yeni rota seçerseniz mevcut yolculuk değişir.[]", width)
        val routes = s.mapRoutes()
        routes.forEachIndexed { i, ro ->
            val chosen = i == s.chosenRouteIndex()
            val rc = s.ui.card(if (chosen) Color.valueOf("FFF1C9") else Color.WHITE)
            val rl = Table()
            val foodOk = st.store[Resource.FOOD] >= ro.foodCost * 0.5; val waterOk = st.store[Resource.WATER] >= ro.waterCost * 0.5
            rl.add(s.ui.label(ro.name + if (chosen) "  ${GREEN}(seçili)[]" else "", "default")).left().row()
            rl.add(s.ui.label("${ro.distance} bölge • ~${fmt(ro.estMinutes)} dk • Tehlike: ${ro.dangerLabel}", "small")).left().row()
            rl.add(s.ui.label("Tahmini erzak: ${if (foodOk) "" else RED}yiyecek ${fmt(ro.foodCost)}${if (foodOk) "" else "[]"}, ${if (waterOk) "" else RED}su ${fmt(ro.waterCost)}${if (waterOk) "" else "[]"}", "small", wrap = true)).width(width - 190f).left().row()
            rl.add(s.ui.label("${SOFT}Arazi: " + ro.biomes.entries.joinToString(", ") { "${it.key.title} ${it.value}" } + "[]", "small", wrap = true)).width(width - 190f).left()
            rc.add(rl).expandX().left()
            rc.add(s.ui.button(if (chosen) "Seçili" else "Seç", "default") { s.chooseRoute(i) }).width(140f)
            t.cardRow(rc, width)
        }
        if (routes.isNotEmpty()) {
            val go = s.ui.button("Yola Çık", "primary") { s.startRoute() }
            go.isDisabled = st.carrier.forcedRest
            t.add(go).width(width).padTop(10f).row()
            t.text(s, "${SOFT}Yolculuk sırasında taşıyıcı daha çok yer, içer ve yorulur. Dengesiz şehirler zorlu arazide hasar alabilir.[]", width)
        }
    }

    // ------------------------------------------------------------------ Günlük
    fun log(s: GameScreen, t: Table, width: Float) {
        val entries = s.state.log.asReversed().take(80)
        for (e in entries) {
            val col = when (e.kind) {
                LogKind.WARNING -> RED; LogKind.STAGE -> "[#C07A10]"; LogKind.RESEARCH -> "[#1A9C90]"
                LogKind.EVENT -> "[#8E5BB5]"; LogKind.TRAVEL -> "[#2E7DB5]"; LogKind.CARRIER -> "[#4E9F45]"
                LogKind.DIPLOMACY -> "[#B5651D]"; LogKind.BATTLE -> "[#C0392B]"; LogKind.EXPLORATION -> "[#1A9C90]"; else -> ""
            }
            t.add(s.ui.label("${SOFT}Gün ${e.day}[]  $col${e.text}${if (col.isNotEmpty()) "[]" else ""}", "small", wrap = true)).width(width).padTop(5f).row()
        }
    }

    // ------------------------------------------------------------------ Ayarlar
    fun settings(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val set = st.settings
        t.section(s, "Grafik", width)
        val g = Table()
        g.defaults().expandX().fillX().pad(4f)
        g.add(s.ui.button("Grafik: ${if (set.graphicsHigh) "Yüksek" else "Düşük (pil dostu)"}", "default") {
            set.graphicsHigh = !set.graphicsHigh; s.act(ActionResult.ok(if (set.graphicsHigh) "Yüksek grafik" else "Düşük grafik: daha az efekt ve insan"))
        }).row()
        g.add(s.ui.button("Ağırlık merkezi göstergesi: ${if (set.showWeightOverlay) "Açık" else "Kapalı"}", "default") {
            set.showWeightOverlay = !set.showWeightOverlay; s.act(ActionResult.ok("Gösterge ${if (set.showWeightOverlay) "açıldı" else "kapandı"}"))
        }).row()
        t.add(g).width(width).row()

        t.section(s, "Kayıt", width)
        t.text(s, "Oyun her 30 saniyede bir ve uygulama arka plana alındığında otomatik kaydedilir. Bozuk kayıtta yedekten yüklenir.", width)
        s.game.saveError?.let { t.text(s, "${RED}Son kayıt hatası: $it[]", width) }
        t.add(s.ui.button("Şimdi Kaydet", "primary") { s.game.save(); s.act(ActionResult(s.game.saveError == null, if (s.game.saveError == null) "Kaydedildi." else "Kayıt başarısız: ${s.game.saveError}")) }).width(width).padTop(6f).row()

        t.section(s, "İstatistikler", width)
        val stats = st.stats
        val lines = listOf(
            "Medeniyet aşaması: ${StageSystem.title(st.stage)} (puan ${StageSystem.score(st)}${StageSystem.nextThreshold(st.stage)?.let { " / sonraki $it" } ?: ""})",
            "Oyun günü: ${st.day} • Dünya tohumu: ${st.seed}",
            "İnşa edilen bina: ${stats.buildingsBuilt} • Geliştirme: ${stats.upgradesDone}",
            "En yüksek nüfus: ${stats.peakPopulation} • Çözülen olay: ${stats.eventsResolved}",
            "Üretilen yiyecek: ${fmt(stats.foodProduced)} • Su: ${fmt(stats.waterProduced)} • Toplanan: ${fmt(stats.gathered)}",
            "Katedilen yol: ${st.travel.distanceTraveled} bölge • Keşfedilen bölge: ${st.travel.explored.size}",
            "Tamamlanan araştırma: ${st.research.completed.size}/${TechCatalog.all.size}",
            "Savaş: ${stats.battlesWon} zafer, ${stats.battlesLost} geri çekilme • Keşif görevi: ${stats.expeditionsDone}",
            "Yan şehir: ${st.sideCities.size} • Taşıyıcı türü: ${st.collection.species.size} • Eser: ${st.collection.artifacts.size} • Başarım: ${st.achievements.size} • Tamamlanan hedef: ${stats.goalsCompleted}",
            "Ticaret işlemi: ${stats.trades} • Tanınan medeniyet: ${st.civs.count { it.met }} • İtibar %${st.reputation.toInt()}",
        )
        lines.forEach { t.text(s, it, width) }

        t.section(s, "Yeni Oyun", width)
        t.text(s, "${RED}Mevcut şehir ve bütün ilerleme silinir.[]", width)
        t.add(s.ui.button(if (s.newGameConfirm()) "Emin misiniz? Silmek için tekrar dokunun" else "Yeni Oyun Başlat", "danger") { s.requestNewGame() }).width(width).padTop(6f).row()
        t.add(s.ui.label("${SOFT}Yürüyen Şehir: Göçebe Medeniyet • sürüm 0.4.0 (Aşama 1-6)[]", "small", wrap = true, align = Align.center)).width(width).padTop(16f).row()
    }

    // ------------------------------------------------------------------ Ticaret
    private fun civSelector(s: GameScreen, t: Table, width: Float): Civilization? {
        val st = s.state
        val met = CivSystem.met(st).sortedBy { it.position.dist(st.travel.position) }
        if (met.isEmpty()) {
            t.text(s, "Henüz hiçbir medeniyetle karşılaşmadınız. Yolculuk ederek keşfedin; başlangıç bölgesinin doğusunda bir tüccar loncası dolaşıyor.", width, "default")
            return null
        }
        if (met.none { it.id == s.selectedCivId }) s.selectedCivId = met.first().id
        val chips = Table()
        met.take(6).forEachIndexed { i, c ->
            val b = s.ui.button(c.name.substringBeforeLast(' '), "chip") { s.selectCiv(c.id) }
            b.pad(8f, 10f, 8f, 10f)
            b.isChecked = c.id == s.selectedCivId
            chips.add(b).padRight(6f).padTop(4f)
            if (i == 2) chips.row()
        }
        t.add(chips).left().row()
        return met.first { it.id == s.selectedCivId }
    }

    private fun civHeader(s: GameScreen, t: Table, width: Float, c: Civilization) {
        val st = s.state
        val hex = Palette.relationHex(c.status)
        t.text(s, "${c.name} • [#$hex]${c.status.title}[] • ${c.position.dist(st.travel.position)} bölge uzakta • ${st.world.biomeAt(c.position).title}", width, "default")
        t.text(s, "${SOFT}${c.culture.title}: ${c.culture.description} Sunar: ${c.culture.offers.joinToString { it.title }} • İster: ${c.culture.wants.joinToString { it.title }}[]", width)
    }

    fun trade(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        t.text(s, "İtibar %${st.reputation.toInt()} • Ticaret Pazarı: ${if (TradeSystem.hasMarket(st)) "${GREEN}var (satış fiyatları daha iyi)[]" else "${SOFT}yok (Özel menüsünden kurun)[]"}", width)
        val c = civSelector(s, t, width) ?: return
        civHeader(s, t, width, c)
        val reason = TradeSystem.canTrade(st, c)
        if (reason != null) t.text(s, "$RED$reason[]", width)
        else t.text(s, "${GREEN}Ticaret menzilinde (${TradeSystem.range(st, c)} bölge). Fiyatlar 10 birim içindir; her alışverişte stok ve fiyat değişir.[]", width)

        val tbl = Table()
        tbl.defaults().pad(3f)
        val wName = width * 0.28f; val wNum = width * 0.13f; val wBtn = width * 0.21f
        tbl.add(s.ui.label("${SOFT}Kaynak[]", "small")).width(wName).left()
        tbl.add(s.ui.label("${SOFT}Sizde[]", "small")).width(wNum).left()
        tbl.add(s.ui.label("${SOFT}Onlarda[]", "small")).width(wNum).left()
        tbl.add(s.ui.label("${SOFT}10 al[]", "small")).width(wBtn)
        tbl.add(s.ui.label("${SOFT}10 sat[]", "small")).width(wBtn).row()
        for (r in Resource.entries.filter { TradeSystem.tradable(it) }) {
            val q = TradeSystem.quote(st, c, r)
            val name = Table()
            name.add(s.ui.dot(Palette.resource(r), 14f)).size(14f).padRight(6f)
            val tag = when (r) { in c.culture.offers -> " ${GREEN}ucuz[]"; in c.culture.wants -> " [#C07A10]aranan[]"; else -> "" }
            name.add(s.ui.label(r.title + tag, "small"))
            tbl.add(name).left()
            tbl.add(s.ui.label(fmt(st.store[r]), "small")).left()
            tbl.add(s.ui.label(fmt(c.stock[r] ?: 0.0), "small")).left()
            val buy = s.ui.button("${fmt(q.buy * TradeSystem.LOT)} A", "default") { s.act(TradeSystem.buy(st, c.id, r)) }
            buy.pad(6f, 4f, 6f, 4f)
            buy.isDisabled = reason != null || st.store[Resource.GOLD] < q.buy * TradeSystem.LOT || (c.stock[r] ?: 0.0) < TradeSystem.LOT
            val sell = s.ui.button("${fmt(q.sell * TradeSystem.LOT)} A", "gold") { s.act(TradeSystem.sell(st, c.id, r)) }
            sell.pad(6f, 4f, 6f, 4f)
            sell.isDisabled = reason != null || st.store[r] < TradeSystem.LOT
            tbl.add(buy).width(wBtn).fillX()
            tbl.add(sell).width(wBtn).fillX().row()
        }
        t.add(tbl).width(width).row()
        t.text(s, "${SOFT}A = altın. Bölge fiyatı etkiler: çölde su pahalı, ormanda odun ucuzdur. İyi ilişki ve ticaret anlaşması fiyatları düşürür.[]", width)

        t.section(s, "Kervanlar (${st.caravans.size}/${TradeSystem.caravanSlots(st)} yuva)", width)
        for (k in st.caravans) {
            val row = Table()
            val name = CivSystem.civ(st, k.civId)?.name ?: "?"
            row.add(s.ui.label("${fmt(k.amount)} ${k.resource.title} › $name (~${fmt(k.payout)} A)", "small", wrap = true)).width(width * 0.55f).left()
            row.add(s.bar((1 - k.secondsLeft / k.totalSeconds).toFloat(), Palette.gold, width * 0.4f))
            t.add(row).width(width).padTop(4f).row()
        }
        val cr = TradeSystem.caravanReason(st, c)
        if (cr != null) t.text(s, "$SOFT$cr[]", width)
        else {
            t.text(s, "Kervanlar 30 birim malı ${c.name} pazarına götürüp neredeyse alış fiyatından satar; dönüş ~${fmt(TradeSystem.caravanSeconds(st, c) / 60)} dk. Küçük bir haydut riski vardır (müttefiklerde yok).", width)
            val grid = Table()
            var n = 0
            for (r in Resource.entries.filter { TradeSystem.tradable(it) && st.store[it] >= TradeSystem.CARAVAN_LOT }) {
                grid.add(s.ui.button("${r.title} › ${fmt(TradeSystem.caravanPayout(st, c, r))} A", "primary") { s.act(TradeSystem.sendCaravan(st, c.id, r)) }.apply { pad(8f, 6f, 8f, 6f) })
                    .width(width / 2 - 8f).pad(3f)
                if (++n % 2 == 0) grid.row()
            }
            if (n == 0) t.text(s, "${SOFT}Kervan için en az 30 birimlik bir mal gerekli.[]", width) else t.add(grid).width(width).row()
        }
    }

    // ------------------------------------------------------------------ Diplomasi
    fun diplomacy(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val rep = Table()
        rep.add(s.ui.label("Medeniyet itibarı %${st.reputation.toInt()}", "small")).width(260f).left()
        rep.add(s.bar((st.reputation / 100).toFloat(), Palette.turquoise, width - 280f))
        t.add(rep).width(width).row()
        t.text(s, "${SOFT}İtibar ticaret, yardım ve anlaşmalarla artar; savaş ilanı düşürür. Yeni karşılaşılan medeniyetlerin ilk tavrını ve fiyatları etkiler.[]", width)
        val c = civSelector(s, t, width) ?: return
        civHeader(s, t, width, c)
        val relRow = Table()
        relRow.add(s.ui.label("İlişki ${c.relation.toInt()}", "small")).width(260f).left()
        relRow.add(s.bar(((c.relation + 100) / 200).toFloat(), Palette.relation(c.status), width - 280f))
        t.add(relRow).width(width).padTop(4f).row()
        t.text(s, "Güç: ${DiplomacySystem.powerLabel(c)} • Anlaşmalar: ${if (c.treaties.isEmpty()) "yok" else c.treaties.joinToString { it.title }}" +
            (if (c.culture.nomadic) " • Göçebe (yer değiştirir)" else " • Yerleşik"), width)
        if (!DiplomacySystem.inContact(st, c)) t.text(s, "${RED}Elçi göndermek için en fazla 8 bölge yakında olmalısınız.[]", width)

        val g = Table()
        g.defaults().pad(4f).width(width / 2 - 8f)
        fun action(label: String, style: String, reason: String?, f: () -> ActionResult) {
            val b = s.ui.button(label, style) { s.act(f()) }
            b.pad(10f, 6f, 10f, 6f)
            b.isDisabled = reason != null
            g.add(b)
        }
        val contact = if (DiplomacySystem.inContact(st, c)) null else "uzak"
        action("Altın hediye (${fmt(DiplomacySystem.GIFT_GOLD)})", "gold", contact ?: if (c.atWar || st.store[Resource.GOLD] < DiplomacySystem.GIFT_GOLD) "x" else null) { DiplomacySystem.giftGold(st, c.id) }
        val aidRes = c.culture.wants.firstOrNull { st.store[it] >= DiplomacySystem.AID_AMOUNT } ?: c.culture.wants.first()
        action("Yardım: ${fmt(DiplomacySystem.AID_AMOUNT)} ${aidRes.title}", "green", contact ?: if (c.atWar || st.store[aidRes] < DiplomacySystem.AID_AMOUNT) "x" else null) { DiplomacySystem.sendAid(st, c.id, aidRes) }
        g.row()
        for ((i, tr) in Treaty.entries.withIndex()) {
            action(tr.title, "primary", DiplomacySystem.treatyReason(st, c, tr)) { DiplomacySystem.propose(st, c.id, tr) }
            if (i % 2 == 1) g.row()
        }
        if (c.atWar) action("Barış görüşmesi", "primary", DiplomacySystem.peaceReason(st, c)) { DiplomacySystem.makePeace(st, c.id) }
        else {
            val b = s.ui.button(if (s.warPending(c.id)) "Emin misiniz? Tekrar dokunun" else "Savaş ilan et", "danger") { s.requestWar(c.id) }
            b.pad(10f, 6f, 10f, 6f)
            g.add(b)
        }
        g.row()
        t.add(g).width(width).padTop(6f).row()
        // Gerekçeler
        for (tr in Treaty.entries) DiplomacySystem.treatyReason(st, c, tr)?.let { t.text(s, "${SOFT}${tr.title}: $it[]", width) }
        if (c.atWar) DiplomacySystem.peaceReason(st, c)?.let { t.text(s, "${SOFT}Barış: $it[]", width) }
        t.text(s, "${SOFT}Etkiler: Ticaret anlaşması fiyatları %10 düşürür, menzili +2 artırır ve ilişkiyi zamanla güçlendirir. Geçiş izni sınır ücretlerini kaldırır. Müttefikler yardım gönderir ve kervanlarınızı korur; fakat geleneksel rakipleri (${c.culture.rival.title}) size soğur. Savaşta yakındaki düşman baskın yapar.[]", width)
        t.add(s.ui.button("Bu medeniyetle ticaret yap", "default") { s.openCiv(c.id, PanelId.TRADE) }).width(width).padTop(8f).row()
    }

    // ------------------------------------------------------------------ Savunma
    fun defense(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val towers = CombatSystem.towers(st)
        t.text(s, "Savunma gücü: ${CombatSystem.defenseRating(st).toInt()} • Kule ${towers.size} • Muhafız ${CombatSystem.guardCount(st)} • Kalkan ${CombatSystem.shieldCapacity(st).toInt()}", width, "default")
        t.text(s, "${SOFT}Savaşlar gerçek zamanlıdır ve 'Dur' ile duraklatılır. Kuleler menzile giren düşmana saniyede bir atış yapar; muhafızlar düşmanı durdurur; kalkan yakındaki binaların hasarını emer. Binalar en fazla %20 sağlamlığa düşer, yok olmaz. Kulelerin yeri, düşmanın geldiği yön ve enerji stoku sonucu belirler.[]", width)
        t.section(s, "Genel hedef önceliği", width)
        val pr = Table()
        for (p in TargetPriority.entries) {
            val b = s.ui.button(p.title, "chip") { s.act(CombatSystem.setPriority(st, p)) }
            b.pad(8f, 10f, 8f, 10f); b.isChecked = st.defensePriority == p
            pr.add(b).padRight(4f).padTop(4f)
            if (p.ordinal == 2) pr.row()
        }
        t.add(pr).left().row()

        t.section(s, "Savunma yapıları", width)
        val defs = st.buildings.filter { it.def.towerDps > 0 || it.def.shieldCapacity > 0 || it.def.guardCount > 0 }
        if (defs.isEmpty()) t.text(s, "${RED}Hiç savunma yapınız yok. İnşa › Savunma menüsünden Savunma Kulesi ve Gözetleme Kulesi kurun.[]", width)
        for (b in defs) {
            val card = s.ui.card(Color.WHITE)
            val l = Table()
            val info = when {
                b.def.towerDps > 0 -> "${fmt(CombatSystem.towerDps(st, b))} hasar/sn • menzil ${fmt(CombatSystem.towerRange(st, b))} • hedef: ${(b.targetPriority ?: st.defensePriority).title}" + if (b.targetPriority == null) " (genel)" else ""
                b.def.shieldCapacity > 0 -> "Kalkan ${fmt(b.def.shieldCapacity * b.level)} • yarıçap ${b.def.shieldRadius}"
                else -> "${b.def.guardCount * b.level} muhafız"
            }
            val staffed = CombatSystem.staffed(b)
            l.add(s.ui.label("${b.def.title} Sv${b.level}" + if (!staffed) "  ${RED}(çalışmıyor)[]" else "", "default")).left().row()
            l.add(s.ui.label(info + " • Sağlamlık %${b.hp.toInt()}", "small", wrap = true)).width(width - 190f).left()
            card.add(l).expandX().left()
            card.add(s.ui.button("Seç", "default") { s.selectBuilding(b.id) }).width(140f)
            t.cardRow(card, width)
        }

        t.section(s, "Tehditler", width)
        val biome = st.world.biomeAt(st.travel.position)
        t.text(s, "Bulunduğunuz bölge: ${biome.title} • tehlike %${(biome.danger * 100).toInt()}" + if (st.scoutedSeconds > 0) " • ${GREEN}tehlike tespiti etkin (${st.scoutedSeconds.toInt()} sn)[]" else "", width)
        val hostile = CivSystem.met(st).filter { it.hostile || it.status == Relation.RIVAL }.sortedBy { it.position.dist(st.travel.position) }
        if (hostile.isEmpty()) t.text(s, "${SOFT}Bilinen düşman veya rakip medeniyet yok.[]", width)
        hostile.forEach { c -> t.text(s, "• ${c.name} [#${Palette.relationHex(c.status)}]${c.status.title}[] • güç ${c.power.toInt()} • ${c.position.dist(st.travel.position)} bölge" + if (c.hostile && c.position.dist(st.travel.position) <= 3) " ${RED}(baskın riski!)[]" else "", width) }
        t.text(s, "${SOFT}Kazanılan savaş ${st.stats.battlesWon} • Kaybedilen ${st.stats.battlesLost}[]", width)
    }

    // ------------------------------------------------------------------ Keşif
    fun exploration(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        val center = ExplorationSystem.center(st)
        if (center == null) t.text(s, "${RED}Keşif ekibi göndermek için Özel menüsünden Keşif Merkezi kurun (ve bir keşifçi atayın).[]", width)
        t.text(s, "Ekipler: ${st.expeditions.size}/${ExplorationSystem.slots(st)} görevde • Ekip başına ${ExplorationSystem.TEAM} yerleşimci • Boşta ${PopulationSystem.freeWorkers(st)}", width, "default")
        for (e in st.expeditions) {
            val row = Table()
            row.add(s.ui.label("${e.type.title} › ${e.target.dist(st.travel.position)} bölge • risk %${(e.risk * 100).toInt()}", "small", wrap = true)).width(width * 0.55f).left()
            row.add(s.bar((1 - e.secondsLeft / e.totalSeconds).toFloat(), Palette.turquoise, width * 0.4f))
            t.add(row).width(width).padTop(4f).row()
        }

        t.section(s, "Hedef", width)
        val dirs = listOf("Kuzey" to Cell(0, 1), "Doğu" to Cell(1, 0), "Güney" to Cell(0, -1), "Batı" to Cell(-1, 0))
        val mapTarget = s.mapTarget()
        val dirRow = Table()
        dirs.forEachIndexed { i, (n, _) ->
            val b = s.ui.button(n, "chip") { s.expDirection = i; s.act(ActionResult.ok("Yön: $n")) }
            b.pad(8f, 12f, 8f, 12f); b.isChecked = s.expDirection == i
            dirRow.add(b).padRight(4f)
        }
        if (mapTarget != null) {
            val b = s.ui.button("Harita hedefi", "chip") { s.expDirection = 4; s.act(ActionResult.ok("Hedef: haritada seçilen bölge")) }
            b.pad(8f, 12f, 8f, 12f); b.isChecked = s.expDirection == 4
            dirRow.add(b)
        } else if (s.expDirection == 4) s.expDirection = 1
        t.add(dirRow).left().row()
        if (s.expDirection != 4) {
            val distRow = Table()
            for (d in listOf(3, 5, 8, 12)) {
                val b = s.ui.button("$d bölge", "chip") { s.expDistance = d; s.act(ActionResult.ok("Uzaklık: $d")) }
                b.pad(8f, 12f, 8f, 12f); b.isChecked = s.expDistance == d
                distRow.add(b).padRight(4f).padTop(4f)
            }
            t.add(distRow).left().row()
        }
        val p = st.travel.position
        val target = if (s.expDirection == 4 && mapTarget != null) mapTarget else dirs[s.expDirection].second.let { Cell(p.x + it.x * s.expDistance, p.y + it.y * s.expDistance) }
        val known = TravelSystem.isExplored(st, target)
        t.text(s, "Hedef: ${if (known) st.world.biomeAt(target).title else "keşfedilmemiş bölge"} • ${target.dist(p)} bölge • dönüş ~${fmt(ExplorationSystem.duration(st, target) / 60)} dk", width)

        t.section(s, "Görevler", width)
        for (m in MissionType.entries) {
            val card = s.ui.card(Color.WHITE)
            val l = Table()
            val risk = ExplorationSystem.risk(st, m, target)
            val riskCol = if (risk < 0.2) GREEN else if (risk < 0.4) "[#C07A10]" else RED
            l.add(s.ui.label(m.title, "default")).left().row()
            l.add(s.ui.label(m.description, "small", wrap = true)).width(width - 190f).left().row()
            l.add(s.ui.label("Risk $riskCol%${(risk * 100).toInt()}[] • Erzak: " + s.ui.costText(ExplorationSystem.cost(m), st.store), "small", wrap = true)).width(width - 190f).left()
            card.add(l).expandX().left()
            val reason = ExplorationSystem.reason(st, m, target)
            val b = s.ui.button("Gönder", "primary") { s.act(ExplorationSystem.send(st, m, target)) }
            b.isDisabled = reason != null
            card.add(b).width(140f)
            t.cardRow(card, width)
        }
        ExplorationSystem.reason(st, MissionType.RESOURCES, target)?.let { t.text(s, "$RED$it[]", width) }
        t.text(s, "${SOFT}Başarısız görevde ekip eli boş döner ve bazen bir keşifçi kaybolur. Keşif Merkezi seviyesi, Keşif Donanımı araştırması ve yakındaki müttefikler riski azaltır. Görevler siz yokken de sürer.[]", width)
    }

    // ------------------------------------------------------------------ Filo
    fun fleet(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        t.text(s, "Yan şehir: ${st.sideCities.size}/${FleetSystem.maxSideCities(st)} • Filo hız çarpanı ×${fmt(FleetSystem.fleetSpeedFactor(st))} (en yavaş taşıyıcı belirler)", width, "default")
        t.text(s, "${SOFT}Yan şehirler ana şehirle birlikte yürür, kendi uzmanlığında üretir ve her 30 saniyede bir konvoyla ana şehre mal gönderir. Yerleşimcileri önce kendi ürettiklerini, yetmezse ana şehrin yiyecek ve suyunu tüketir.[]", width)
        for (c in st.sideCities) {
            val card = s.ui.card(Color.WHITE)
            val l = Table()
            l.add(s.ui.label("${c.name} • ${c.specialty.title} • Sv${c.level}", "default")).left().row()
            l.add(s.ui.label("${c.species.title} • Yerleşimci ${c.workers}/${c.capacity}", "small")).left().row()
            val out = FleetSystem.output(c)
            val outText = (out.entries.joinToString { "${it.key.title} ${fmt(it.value)}/dk" }).ifEmpty {
                if (c.specialty == SideSpecialty.RESEARCH) "Bilgi ${fmt(c.specialty.researchPerWorker * c.workers * (1 + 0.5 * (c.level - 1)))}/dk" else "Savaşta ${c.workers / 2} muhafız"
            }
            l.add(s.ui.label("Üretim: $outText • Tüketim: yiyecek ve su ${fmt(c.workers * PopulationSystem.FOOD_PER_POP)}/dk", "small", wrap = true)).width(width - 40f).left().row()
            val stock = c.stockpile.entries.filter { it.value >= 1 }.joinToString { "${it.key.title} ${fmt(it.value)}" }
            if (stock.isNotEmpty()) l.add(s.ui.label("${SOFT}Konvoy bekleyen: $stock[]", "small", wrap = true)).width(width - 40f).left().row()
            card.add(l).expandX().fillX().left().row()
            val btns = Table()
            btns.defaults().pad(3f).expandX().fillX()
            btns.add(s.ui.button("+1 kişi", "default") { s.act(FleetSystem.moveWorkers(st, c.id, 1)) })
            btns.add(s.ui.button("-1 kişi", "default") { s.act(FleetSystem.moveWorkers(st, c.id, -1)) })
            btns.add(s.ui.button("Erzak", "green") { s.act(FleetSystem.sendSupplies(st, c.id)) }).row()
            val up = s.ui.button(if (c.level < 5) "Geliştir (${FleetSystem.upgradeCost(c).describe()})" else "En yüksek seviye", "primary") { s.act(FleetSystem.upgrade(st, c.id)) }
            up.isDisabled = c.level >= 5 || !st.store.canAfford(FleetSystem.upgradeCost(c))
            btns.add(up).colspan(2)
            btns.add(s.ui.button("Konvoy: ${if (c.autoSend) "Açık" else "Kapalı"}", "default") { c.autoSend = !c.autoSend; s.act(ActionResult.ok("Konvoy ${if (c.autoSend) "açıldı" else "kapandı"}")) }).row()
            card.add(btns).expandX().fillX().row()
            val spec = Table()
            spec.add(s.ui.label("${SOFT}Uzmanlık (50 altın):[]", "small")).padRight(6f)
            SideSpecialty.entries.forEachIndexed { i, sp ->
                val b = s.ui.button(sp.title.substringBefore(' '), "chip") { s.act(FleetSystem.setSpecialty(st, c.id, sp)) }
                b.pad(6f, 8f, 6f, 8f); b.isChecked = c.specialty == sp
                spec.add(b).padRight(3f).padTop(3f)
                if (i == 2) spec.row().padLeft(0f)
            }
            card.add(spec).left().padTop(4f)
            t.cardRow(card, width)
        }

        t.section(s, "Yeni yan şehir kur", width)
        val free = st.collection.species.filter { !CollectionSystem.inUse(st, it) }
        if (free.isEmpty()) {
            t.text(s, "${SOFT}Boşta taşıyıcı türü yok. Keşif ekipleriyle yeni bir tür keşfedin.[]", width)
        } else {
            if (s.foundSpecies !in free) s.foundSpecies = free.first()
            val sr = Table()
            for (sp in free) {
                val b = s.ui.button(sp.title, "chip") { s.foundSpecies = sp; s.act(ActionResult.ok(sp.title)) }
                b.pad(8f, 10f, 8f, 10f); b.isChecked = s.foundSpecies == sp
                sr.add(b).padRight(4f).padTop(4f)
            }
            t.add(sr).left().row()
            val pr = Table()
            SideSpecialty.entries.forEachIndexed { i, sp ->
                val b = s.ui.button(sp.title, "chip") { s.foundSpecialty = sp; s.act(ActionResult.ok(sp.title)) }
                b.pad(8f, 10f, 8f, 10f); b.isChecked = s.foundSpecialty == sp
                pr.add(b).padRight(4f).padTop(4f)
                if (i == 2) pr.row()
            }
            t.add(pr).left().row()
            t.text(s, "${s.foundSpecialty.description} Maliyet: " + s.ui.costText(FleetSystem.foundCost, st.store) + " • ${FleetSystem.FOUND_WORKERS} yerleşimci taşınır.", width)
            val sp = s.foundSpecies!!
            val reason = FleetSystem.foundReason(st, sp)
            val b = s.ui.button("Yan Şehri Kur", "primary") { s.act(FleetSystem.found(st, sp, s.foundSpecialty)) }
            b.isDisabled = reason != null
            t.add(b).width(width).padTop(6f).row()
            if (reason != null) t.text(s, "$RED$reason[]", width)
        }
    }

    // ------------------------------------------------------------------ Hedefler
    fun goals(s: GameScreen, t: Table, width: Float) {
        val st = s.state
        t.section(s, "Medeniyet Uzmanlaşması (${st.specializations.size}/${SpecializationSystem.slots(st)})", width)
        t.text(s, "${SOFT}Kalıcı bir yön seçin; ${StageSystem.title(4)} aşamasında ikinci bir yön seçerek hibrit strateji kurabilirsiniz.[]", width)
        for (sp in Specialization.entries) {
            val card = s.ui.card(if (SpecializationSystem.has(st, sp)) Color.valueOf("E8F6EA") else Color.WHITE)
            val l = Table()
            l.add(s.ui.label(sp.title, "default")).left().row()
            l.add(s.ui.label(sp.description, "small", wrap = true)).width(width - 190f).left()
            card.add(l).expandX().left()
            if (SpecializationSystem.has(st, sp)) card.add(s.ui.label("${GREEN}Seçildi[]", "small")).width(140f)
            else {
                val b = s.ui.button("Seç", "primary") { s.act(SpecializationSystem.choose(st, sp)) }
                b.isDisabled = SpecializationSystem.reason(st, sp) != null
                card.add(b).width(140f)
            }
            t.cardRow(card, width)
        }

        fun goalList(weekly: Boolean) {
            for ((i, g) in st.goals.withIndex()) {
                if (g.weekly != weekly) continue
                val card = s.ui.card(if (g.claimed) Color.valueOf("E8F6EA") else Color.WHITE)
                val l = Table()
                val p = GoalSystem.progress(st, g).coerceAtMost(g.target)
                l.add(s.ui.label(g.kind.text(g.target), "default")).left().row()
                val row = Table()
                row.add(s.ui.label("${fmt(p.coerceAtLeast(0.0))}/${fmt(g.target)}", "small")).width(110f).left()
                row.add(s.bar((p / g.target).toFloat(), Palette.turquoise, width - 330f))
                l.add(row).left().row()
                l.add(s.ui.label("${SOFT}Ödül: ${g.reward.describe()}${if (GoalSystem.questCenter(st)) " (+%25)" else ""}[]", "small")).left()
                card.add(l).expandX().left()
                if (g.claimed) card.add(s.ui.label("${GREEN}Alındı[]", "small")).width(140f)
                else {
                    val b = s.ui.button("Ödülü Al", "gold") { s.act(GoalSystem.claim(st, i)) }
                    b.isDisabled = !GoalSystem.done(st, g)
                    card.add(b).width(140f)
                }
                t.cardRow(card, width)
            }
        }
        t.section(s, "Günlük Hedefler (Gün ${st.day})", width)
        if (!GoalSystem.questCenter(st)) t.text(s, "${SOFT}Görev Merkezi kurarak +1 günlük hedef ve %25 fazla ödül kazanın.[]", width)
        goalList(false)
        t.section(s, "Haftalık Hedefler (Hafta ${(st.day - 1) / 7 + 1}, ${7 - (st.day - 1) % 7} gün kaldı)", width)
        goalList(true)

        t.section(s, "Başarımlar (${st.achievements.size}/${Achievement.entries.size})", width)
        for (a in Achievement.entries) {
            val day = st.achievements[a.name]
            t.text(s, if (day != null) "$GREEN• ${a.title}[] — ${a.description} ${SOFT}(Gün $day)[]" else "$SOFT• ${a.title} — ${a.description} (+${a.rewardGold} altın)[]", width)
        }

        t.section(s, "Antik Eser Koleksiyonu (${st.collection.artifacts.size}/${Artifact.entries.size})", width)
        t.text(s, "${SOFT}Eserler kalıntı kazılarında ve keşif görevlerinde bulunur; her biri kalıcı bir avantaj sağlar.[]", width)
        for (a in Artifact.entries) {
            t.text(s, if (a in st.collection.artifacts) "$GREEN• ${a.title}[] — ${a.description}" else "$SOFT• ??? — bulunmadı[]", width)
        }
        t.add(s.ui.button("Dünya Günlüğünü Aç", "default") { s.showPanel(PanelId.LOG) }).width(width).padTop(10f).row()
    }
}
