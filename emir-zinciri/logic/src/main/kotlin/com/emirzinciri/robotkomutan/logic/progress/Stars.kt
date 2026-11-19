package com.emirzinciri.robotkomutan.logic.progress

import com.emirzinciri.robotkomutan.logic.level.LevelDef
import com.emirzinciri.robotkomutan.logic.level.StarRuleType
import com.emirzinciri.robotkomutan.logic.sim.Outcome
import com.emirzinciri.robotkomutan.logic.sim.RunStats

/** Bir yıldız ölçütü ve sağlanıp sağlanmadığı. */
data class StarCheck(val text: String, val met: Boolean)

object Stars {
    /** Üç yıldız ölçütünün metni ve durumu: 1) tamamla 2) kart sınırı (par) 3) özel koşul. */
    fun evaluate(def: LevelDef, outcome: Outcome?): List<StarCheck> {
        val won = outcome is Outcome.Win
        val st = outcome?.stats
        return listOf(
            StarCheck("Bölümü tamamla", won),
            StarCheck("En fazla ${def.par} kart kullan", won && st != null && st.cards <= def.par),
            thirdCheck(def, if (won) st else null),
        )
    }

    fun count(def: LevelDef, outcome: Outcome?): Int = evaluate(def, outcome).count { it.met }

    private fun thirdCheck(def: LevelDef, st: RunStats?): StarCheck {
        val rule = def.third ?: return StarCheck("Hiç çarpmadan bitir", st != null && st.bumps == 0)
        return when (rule.type) {
            StarRuleType.TICKS -> StarCheck("En fazla ${rule.value} adımda bitir", st != null && st.ticks <= rule.value)
            StarRuleType.ENERGY_USED -> StarCheck("En fazla ${rule.value} enerji harca", st != null && st.energyUsed <= rule.value)
            StarRuleType.NO_BUMPS -> StarCheck("Hiç çarpmadan bitir", st != null && st.bumps == 0)
            StarRuleType.COMMANDS -> StarCheck("En fazla ${rule.value} kartla bitir", st != null && st.cards <= rule.value)
        }
    }
}
