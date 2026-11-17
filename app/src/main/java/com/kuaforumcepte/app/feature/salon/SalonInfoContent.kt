package com.kuaforumcepte.app.feature.salon

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.kuaforumcepte.app.core.designsystem.component.KcSecondaryButton
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.domain.phone.PhoneNumbers

/**
 * Salonun kamuya açık bilgileri. Salon detay sayfası ile müşterinin "Salon Bilgileri" sekmesi
 * aynı bileşeni ve aynı veri kaynağını kullanır.
 */
@Composable
fun SalonInfoContent(salon: SalonProfile, onToast: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SalonCarousel(salon.photos.map { it.url }, salon.logoUrl, salon.name)
        Text(salon.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Text(
                " ${salon.rating.label()}" + (if (salon.rating.count > 0) " (${salon.rating.count} değerlendirme)" else "") +
                    " · ${salon.type?.label.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("Salon sahibi: ${salon.ownerDisplayName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (salon.description.isNotBlank()) {
            Text(salon.description, style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
        Text("Adres", style = MaterialTheme.typography.titleMedium)
        Text(salon.fullAddress, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            KcSecondaryButton("Haritada Göster", {
                val loc = salon.location
                context.launch(if (loc != null) MapIntents.show(loc.lat, loc.lng, salon.name) else MapIntents.showAddress(salon.fullAddress), onToast)
            }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Map)
            KcSecondaryButton("Yol Tarifi", {
                context.launch(MapIntents.directions(salon.location?.lat, salon.location?.lng, salon.fullAddress), onToast)
            }, Modifier.weight(1f), leadingIcon = Icons.Outlined.Directions)
        }
        if (salon.businessPhoneE164.isNotBlank()) {
            KcSecondaryButton("Salonu Ara · ${PhoneNumbers.formatForDisplay(salon.businessPhoneE164)}", {
                context.launch(MapIntents.dial(salon.businessPhoneE164), onToast)
            }, leadingIcon = Icons.Outlined.Call)
        }
    }
}

private fun Context.launch(intent: Intent, onToast: (String) -> Unit) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onToast("Bu işlemi açabilecek bir uygulama bulunamadı.")
    }
}
