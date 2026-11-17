package com.kuaforumcepte.app.feature.splash

import com.kuaforumcepte.domain.model.EntryRole
import kotlin.test.Test
import kotlin.test.assertEquals

class SplashDeciderTest {
    private fun decide(configured: Boolean = true, signedIn: Boolean = false, remember: Boolean = true, role: EntryRole? = null) =
        SplashDecider.decide(SplashDecider.Input(configured, signedIn, remember, role))

    @Test fun `firebase yoksa kurulum ekranı`() {
        assertEquals(SplashDecision.SetupRequired, decide(configured = false, signedIn = true).decision)
    }

    @Test fun `oturum yoksa rol seçimi`() {
        assertEquals(SplashDecider.Output(SplashDecision.RoleSelection, false), decide())
    }

    @Test fun `beni hatırla kapalıysa oturum kapatılır`() {
        assertEquals(SplashDecider.Output(SplashDecision.RoleSelection, true), decide(signedIn = true, remember = false, role = EntryRole.BUSINESS))
    }

    @Test fun `beni hatırla açıksa son role devam`() {
        assertEquals(SplashDecision.Resume(EntryRole.BUSINESS), decide(signedIn = true, role = EntryRole.BUSINESS).decision)
    }

    @Test fun `rol bilinmiyorsa rol seçimi`() {
        assertEquals(SplashDecision.RoleSelection, decide(signedIn = true, role = null).decision)
    }
}
