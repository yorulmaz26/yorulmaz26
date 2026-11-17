package com.kuaforumcepte.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.designsystem.theme.KcTheme
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Res -> LocalContext.current.getString(id, *args.toTypedArray())
}

/** Yanıp sönen iskelet blok (skeleton loading). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .height(height)
            .clip(MaterialTheme.shapes.small)
            .alpha(alpha)
            .background(KcTheme.colors.skeleton),
    )
}

/** Kart listesi yüklenirken gösterilen iskelet. */
@Composable
fun SkeletonCardList(count: Int = 4, modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.state_loading)
    Column(
        modifier.fillMaxWidth().padding(Spacing.screen).semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        repeat(count) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(Modifier.size(56.dp).clip(CircleShape), height = 56.dp)
                    Spacer(Modifier.width(Spacing.l))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        SkeletonBlock(Modifier.fillMaxWidth(0.7f), 18.dp)
                        SkeletonBlock(Modifier.fillMaxWidth(0.45f), 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    message: String?,
    modifier: Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(Spacing.xxl).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        actions()
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = CenteredMessage(icon, MaterialTheme.colorScheme.secondary, title, message, modifier) {
    if (actionLabel != null && onAction != null) KcPrimaryButton(actionLabel, onAction)
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.error_generic),
    onRetry: (() -> Unit)? = null,
) = CenteredMessage(Icons.Outlined.CloudOff, KcTheme.colors.cancel, title, message, modifier) {
    if (onRetry != null) KcSecondaryButton(stringResource(R.string.action_retry), onRetry)
}

@Composable
fun SuccessState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = CenteredMessage(Icons.Outlined.CheckCircle, KcTheme.colors.success, title, message, modifier) {
    if (actionLabel != null && onAction != null) KcPrimaryButton(actionLabel, onAction)
}
