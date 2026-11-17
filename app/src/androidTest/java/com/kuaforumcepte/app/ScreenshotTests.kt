package com.kuaforumcepte.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Loyalty
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kuaforumcepte.app.core.designsystem.theme.KuaforumTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.appointments.Appointment
import com.kuaforumcepte.app.data.appointments.AppointmentItem
import com.kuaforumcepte.app.feature.appointments.AppointmentTicket
import com.kuaforumcepte.app.feature.business.MenuSection
import com.kuaforumcepte.app.feature.business.MenuTile
import com.kuaforumcepte.app.feature.role.RoleSelectionContent
import com.kuaforumcepte.app.core.datastore.ThemeMode
import com.kuaforumcepte.domain.model.AppointmentStatus
import com.kuaforumcepte.domain.model.Kurus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Tasarım incelemesi için ekran görüntüleri (CI'da artefakt olarak yüklenir). Doğrulama yapmaz;
 * yalnızca ekranların çizilebildiğini kanıtlar ve görüntüleri kaydeder.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTests {
    @get:Rule val compose = createComposeRule()

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun girisEkraniKoyu() {
        compose.setContent { KuaforumTheme(ThemeMode.DARK) { RoleSelectionContent(onChoose = {}) } }
        save("01_giris_koyu")
    }

    @Test fun girisEkraniAcik() {
        compose.setContent { KuaforumTheme(ThemeMode.LIGHT) { RoleSelectionContent(onChoose = {}) } }
        save("02_giris_acik")
    }

    @Test fun salonumMenusuKoyu() {
        compose.setContent {
            KuaforumTheme(ThemeMode.DARK) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                        MenuSection("Müşteri kazan", "Salonunuzu paylaşın ve görünür olun") {
                            MenuTile(Icons.Outlined.QrCode2, "Link ve QR Kod", "WhatsApp, yazdır") {}
                            MenuTile(Icons.Outlined.Reviews, "Salon Sayfam", "Yorumlar ve yanıtlar") {}
                            MenuTile(Icons.Outlined.Loyalty, "Kampanyalar", "İndirim kodu, sadakat") {}
                        }
                        MenuSection("Benim çalışma düzenim", "Sadece sizin takviminiz ve hizmetleriniz") {
                            MenuTile(Icons.Outlined.ContentCut, "Hizmetlerim", "Fiyat ve süre") {}
                            MenuTile(Icons.Outlined.Schedule, "Çalışma Saatleri", "Vardiya ve molalar") {}
                            MenuTile(Icons.Outlined.BeachAccess, "İzin Günleri", "Haftalık izin") {}
                        }
                        MenuSection("Salon yönetimi", "Tüm salonu etkileyen ayarlar") {
                            MenuTile(Icons.Outlined.Groups, "Ekibim", "Uzman, davet, prim") {}
                            MenuTile(Icons.Outlined.Storefront, "Dükkan Bilgileri", "Ad, adres, konum") {}
                        }
                    }
                }
            }
        }
        save("03_salonum_koyu")
    }

    @Test fun randevuBiletiKoyuVeAcik() {
        val a = Appointment(
            id = "ap3", number = "KC-5BRDQKXP", salonId = "s", salonName = "Yorulmaz Kuaför", salonAddress = "Kızılay Mah. 1, Çankaya / Ankara",
            staffUid = "u", staffName = "Can Er", customerUid = "c", customerName = "Emel Çiçek", customerPhoneE164 = "+905550000003",
            items = listOf(AppointmentItem("kesim", "Saç Kesimi", Kurus(40000), 30), AppointmentItem("fon", "Fön", Kurus(15000), 15)),
            total = Kurus(55000), finalTotal = Kurus(55000), totalMinutes = 45,
            startAt = Instant.parse("2026-11-18T07:00:00Z"), endAt = Instant.parse("2026-11-18T07:45:00Z"),
            status = AppointmentStatus.CONFIRMED, pendingExpiresAt = Instant.parse("2026-11-18T07:00:00Z"),
            cancelReason = null, attentionReason = null, hiddenFor = emptyList(),
        )
        compose.setContent {
            KuaforumTheme(ThemeMode.DARK) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(Spacing.screen)) { AppointmentTicket(a, Instant.parse("2026-11-17T10:00:00Z")) }
                }
            }
        }
        save("04_bilet_koyu")
    }
}
