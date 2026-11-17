package com.kuaforumcepte.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.theme.KuaforumTheme
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentItem
import com.kuaforumcepte.app.data.booking.Availability
import com.kuaforumcepte.app.data.booking.AvailabilityDay
import com.kuaforumcepte.app.data.booking.Slot
import com.kuaforumcepte.app.data.schedule.ScheduleConflict
import com.kuaforumcepte.app.feature.appointments.AppointmentTicket
import com.kuaforumcepte.app.feature.booking.SlotPickerContent
import com.kuaforumcepte.app.feature.booking.SlotPickerUi
import com.kuaforumcepte.app.feature.business.SalonCodeCard
import com.kuaforumcepte.app.feature.reviews.StarPicker
import com.kuaforumcepte.app.feature.schedule.ConflictDialog
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.Kurus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Durumsuz arayüz bileşenleri için Compose UI testleri (Firebase gerektirmez).
 * Çalıştırma: ./gradlew :app:connectedDebugAndroidTest (bağlı cihaz/emülatör).
 */
@RunWith(AndroidJUnit4::class)
class ComponentUiTests {
    @get:Rule val compose = createComposeRule()

    @Test fun bosDurumEylemiCalisir() {
        var clicked = false
        compose.setContent { KuaforumTheme { EmptyState(title = "Henüz randevunuz yok", message = "Açıklama", actionLabel = "Randevu Al", onAction = { clicked = true }) } }
        compose.onNodeWithText("Henüz randevunuz yok").assertIsDisplayed()
        compose.onNodeWithText("Randevu Al").performClick()
        assertTrue(clicked)
    }

    @Test fun hataDurumuTekrarDeneGosterir() {
        var retried = 0
        compose.setContent { KuaforumTheme { ErrorState("Görsel yüklenemedi. Bağlantınızı kontrol edip tekrar deneyin.", onRetry = { retried++ }) } }
        compose.onNodeWithText("Görsel yüklenemedi. Bağlantınızı kontrol edip tekrar deneyin.").assertIsDisplayed()
        compose.onNodeWithText("Tekrar Dene").performClick()
        assertEquals(1, retried)
    }

    @Test fun yukleniyorkenButonTiklanamaz() {
        compose.setContent { KuaforumTheme { KcPrimaryButton("Randevuyu Onayla", {}, loading = true) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "İşleniyor")).assertIsNotEnabled()
    }

    @Test fun alanHatasiErisilebilirlikIcinBildirilir() {
        compose.setContent { KuaforumTheme { KcTextField("12", {}, "Cep telefonu", error = "Geçerli bir cep telefonu girin") } }
        compose.onNodeWithText("Geçerli bir cep telefonu girin").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)).assertIsDisplayed()
    }

    @Test fun yildizSeciciDegerVerir() {
        var value by mutableIntStateOf(0)
        compose.setContent { KuaforumTheme { StarPicker("Salon deneyimi", value, { value = it }) } }
        compose.onNodeWithContentDescription("Salon deneyimi: 4 / 5 yıldız").performClick()
        assertEquals(4, value)
    }

    @Test fun cakismaPenceresiListelerVeOnaylar() {
        var confirmed = false
        val conflicts = listOf(ScheduleConflict("a1", LocalDate.of(2026, 11, 24), "17:00", "18:00", "Ali Kaya", "OUTSIDE_WORKING_HOURS"))
        compose.setContent { KuaforumTheme { ConflictDialog(conflicts, applying = false, onConfirm = { confirmed = true }, onDismiss = {}) } }
        compose.onNodeWithText("1 randevu etkileniyor").assertIsDisplayed()
        compose.onNodeWithText("Ali Kaya · Çalışma saatleri dışında kalıyor").assertIsDisplayed()
        compose.onNodeWithText("Yine de Uygula").performClick()
        assertTrue(confirmed)
    }

    @Test fun salonKoduOkunurBicimdeGosterilir() {
        compose.setContent { KuaforumTheme { SalonCodeCard("482913") } }
        compose.onNodeWithText("482 913").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kodu kopyala").assertIsEnabled()
    }

    @Test fun biletSuresiDolanTalebiDogruGosterir() {
        val now = Instant.parse("2026-11-17T10:00:00Z")
        val a = Appointment(
            id = "ap1", number = "KC-AP100000", salonId = "s", salonName = "Işıltı", salonAddress = "Kızılay Mah. 1, Çankaya / Ankara",
            staffUid = "u", staffName = "Can Er", customerUid = "c", customerName = "Ali Kaya", customerPhoneE164 = "+905320000001",
            items = listOf(AppointmentItem("kesim", "Kesim", Kurus(40000), 30)), total = Kurus(40000), finalTotal = Kurus(40000), totalMinutes = 30,
            startAt = Instant.parse("2026-11-18T07:00:00Z"), endAt = Instant.parse("2026-11-18T07:30:00Z"),
            status = AppointmentStatus.PENDING, pendingExpiresAt = Instant.parse("2026-11-17T09:30:00Z"),
            cancelReason = null, attentionReason = null, hiddenFor = emptyList(),
        )
        compose.setContent { KuaforumTheme { AppointmentTicket(a, now) } }
        compose.onNodeWithText("Süresi Doldu").assertIsDisplayed()
        compose.onNodeWithText("KC-AP100000").assertIsDisplayed()
    }

    @Test fun saatSeciciDoluGunuVeSlotSeciminiGosterir() {
        val days = listOf(
            AvailabilityDay(LocalDate.of(2026, 11, 18), listOf(Slot(Instant.parse("2026-11-18T06:00:00Z"), "09:00"))),
            AvailabilityDay(LocalDate.of(2026, 11, 22), emptyList()),
        )
        var ui by mutableStateOf(SlotPickerUi(LocalDate.of(2026, 11, 18), loading = false, availability = Availability(Kurus(40000), 30, 60, days), selectedDate = days[0].date))
        var picked: Slot? = null
        compose.setContent {
            KuaforumTheme {
                SlotPickerContent("Tarih ve Saat", ui, canPrev = false, canNext = true, onBack = {}, onReload = {},
                    onSelectDate = { d -> ui = ui.copy(selectedDate = d) }, onShift = {}, onSlotSelected = { picked = it }, footer = "Not")
            }
        }
        compose.onNodeWithText("09:00").performClick()
        assertEquals("09:00", picked?.label)
        compose.onNodeWithText("Dolu/kapalı").performClick()
        compose.onAllNodesWithContentDescription("Önceki günler")[0].assertIsNotEnabled()
        compose.onNodeWithText("uygun saat yok", substring = true).assertIsDisplayed()
    }
}
