package com.kuaforumcepte.app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

/** Ana eylem butonu. loading iken tıklanamaz (çift gönderimi önler) ve ekran okuyucuya bildirilir. */
@Composable
fun KcPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .semantics { if (loading) stateDescription = "İşleniyor" },
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

@Composable
fun KcSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
    ) {
        ButtonContent(text, loading, leadingIcon)
    }
}

@Composable
fun KcTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = Spacing.touch)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    AnimatedContent(targetState = loading, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "btn") { isLoading ->
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
