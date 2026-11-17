package com.kuaforumcepte.app.feature.salon

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Salon görselleri: önce mekan fotoğrafları (≤5), en sonda logo; 3 sn'de bir otomatik geçer.
 * Elle kaydırma desteklenir; kullanıcı kaydırırken, duraklatıldığında veya ekran arka plandayken
 * zamanlayıcı çalışmaz (LaunchedEffect lifecycle ile iptal edilir). Görsel yoksa yalnızca logo.
 */
@Composable
fun SalonCarousel(photoUrls: List<String>, logoUrl: String, salonName: String, modifier: Modifier = Modifier) {
    val slides = remember(photoUrls, logoUrl) { photoUrls + logoUrl }
    val pager = rememberPagerState { slides.size }
    var paused by rememberSaveable { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    val dragged by pager.interactionSource.collectIsDraggedAsState()

    // Her yerleşmeden (otomatik veya elle) sonra 3 sn beklenir; sürükleme, duraklatma veya
    // arka plan durumunda efekt iptal edilir ve zamanlayıcı kalmaz.
    LaunchedEffect(paused, resumed, slides.size, dragged, pager.settledPage) {
        if (paused || !resumed || dragged || slides.size < 2) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        pager.animateScrollToPage((pager.settledPage + 1) % slides.size)
    }

    Box(modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val isLogo = page == slides.lastIndex
            AsyncImage(
                model = slides[page],
                contentDescription = if (isLogo) "$salonName logosu" else "$salonName mekan fotoğrafı ${page + 1}",
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (isLogo) Modifier.padding(Spacing.xxl) else Modifier),
            )
        }
        if (slides.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(Spacing.s)
                    .semantics { contentDescription = "Görsel ${pager.currentPage + 1} / ${slides.size}" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(slides.size) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.7f)))
                }
            }
            IconButton(
                onClick = { paused = !paused },
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (paused) "Otomatik geçişi başlat" else "Otomatik geçişi duraklat",
                    tint = Color.White,
                )
            }
        }
    }
}

private const val AUTO_ADVANCE_MS = 3_000L
