package com.kuaforumcepte.app.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import com.kuaforumcepte.app.core.common.UiState
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.component.ErrorState
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.notifications.InboxItem
import com.kuaforumcepte.app.data.notifications.NotificationRepository
import com.kuaforumcepte.app.data.session.SessionRepository
import com.kuaforumcepte.domain.time.BusinessTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val session: SessionRepository,
    private val repo: NotificationRepository,
) : ViewModel() {
    val state: StateFlow<UiState<List<InboxItem>>> = session.uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(UiState.Empty())
        else repo.observe(uid).map<List<InboxItem>, UiState<List<InboxItem>>> { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
            .onStart { emit(UiState.Loading) }.catch { emit(UiState.Error(ErrorMessages.of(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val unread: StateFlow<Int> = session.uidFlow.flatMapLatest { uid -> if (uid == null) flowOf(0) else repo.unreadCount(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun open(item: InboxItem) = viewModelScope.launch { session.currentUid?.let { runCatching { repo.markRead(it, listOf(item.id)) } } }

    fun markAllRead() = viewModelScope.launch {
        val items = (state.value as? UiState.Success)?.data ?: return@launch
        session.currentUid?.let { uid -> runCatching { repo.markRead(uid, items.filter { !it.read }.map { it.id }) } }
    }
}

private val fmt = DateTimeFormatter.ofPattern("d MMM HH:mm", BusinessTime.LOCALE).withZone(BusinessTime.ZONE)

/** Uygulama içi bildirim kutusu. Push izni kapalı veya cihaz çevrimdışı olsa bile tüm bildirimler burada. */
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenAppointment: (String) -> Unit,
    /** Randevusuz bildirimler (bekleme listesinde yer açıldı, salon onayı) için salon sayfası. */
    onOpenSalon: ((String) -> Unit)? = null,
    /** "Onay bekleyen değerlendirme" bildirimi salon sahibini onay ekranına götürür. */
    onOpenReviewApproval: (() -> Unit)? = null,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        KcTopBar("Bildirimler", onBack, actions = { if (unread > 0) TextButton(onClick = { viewModel.markAllRead() }) { Text("Tümünü okundu yap") } })
    }) { padding ->
        when (val s = state) {
            UiState.Loading -> SkeletonCardList(4, Modifier.padding(padding))
            is UiState.Empty -> EmptyState(icon = Icons.Outlined.NotificationsNone, title = "Bildirim yok", message = "Randevu talepleri, onaylar ve hatırlatmalar burada görünür.", modifier = Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message.asString(), Modifier.padding(padding))
            is UiState.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(s.data, key = { it.id }) { item ->
                    Surface(
                        onClick = {
                            viewModel.open(item)
                            val appt = item.appointmentId
                            if (item.type == "REVIEW_PENDING" && onOpenReviewApproval != null) onOpenReviewApproval()
                            else if (appt != null && item.type != "REVIEW_REPLIED") onOpenAppointment(appt) else item.salonId?.let { sid -> onOpenSalon?.invoke(sid) }
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (item.read) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.Top) {
                            if (!item.read) Box(Modifier.padding(top = 6.dp, end = Spacing.s).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (item.read) FontWeight.Normal else FontWeight.Bold)
                                Text(item.body, style = MaterialTheme.typography.bodySmall)
                                item.createdAt?.let { Text(fmt.format(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Üst çubukta okunmamış sayısıyla zil. */
@Composable
fun InboxBell(onClick: () -> Unit, viewModel: InboxViewModel = hiltViewModel()) {
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
            Icon(Icons.Outlined.Notifications, contentDescription = if (unread > 0) "Bildirimler, $unread okunmamış" else "Bildirimler")
        }
    }
}
