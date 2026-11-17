package com.kuaforumcepte.app.feature.setup

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.designsystem.component.EmptyState
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

/** google-services.json olmadan derlenen build'lerde gösterilir; Firebase'e hiç dokunulmaz. */
@Composable
fun FirebaseSetupRequiredScreen() {
    val activity = LocalContext.current as? Activity
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding().padding(Spacing.screen)) {
            EmptyState(
                icon = Icons.Outlined.SettingsSuggest,
                title = stringResource(R.string.setup_title),
                message = stringResource(R.string.setup_body),
                actionLabel = stringResource(R.string.setup_close),
                onAction = { activity?.finishAffinity() },
            )
        }
    }
}
