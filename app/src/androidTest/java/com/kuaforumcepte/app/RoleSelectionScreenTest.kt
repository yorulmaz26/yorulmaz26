package com.kuaforumcepte.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kuaforumcepte.app.core.designsystem.theme.KuaforumTheme
import com.kuaforumcepte.app.feature.role.RoleSelectionContent
import com.kuaforumcepte.domain.model.EntryRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleSelectionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rolKartlariSecimiIletir() {
        val chosen = mutableListOf<EntryRole>()
        compose.setContent { KuaforumTheme { RoleSelectionContent(onChoose = { chosen += it }) } }
        compose.onNodeWithText("Müşteri Girişi").performClick()
        compose.onNodeWithText("Kuaför / Uzman Girişi").performClick()
        assertEquals(listOf(EntryRole.CUSTOMER, EntryRole.BUSINESS), chosen)
    }
}
