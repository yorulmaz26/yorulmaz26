package com.kuaforumcepte.app.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuaforumcepte.app.R
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

/**
 * Marka alanı. res/values/brand.xml → has_brand_logo=true ve res/drawable/brand_logo
 * gerçek logo olduğunda logo gösterilir; aksi halde yalnızca tipografik isim.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    accent: Color = MaterialTheme.colorScheme.secondary,
    logoSize: Dp = 96.dp,
    showSlogan: Boolean = true,
) {
    val hasLogo = booleanResource(R.bool.has_brand_logo)
    val appName = stringResource(R.string.app_name)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (hasLogo) {
            Image(painterResource(R.drawable.brand_logo), contentDescription = "$appName logosu", modifier = Modifier.size(logoSize))
        }
        Text(
            appName,
            style = MaterialTheme.typography.displaySmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (showSlogan) {
            Text(
                stringResource(R.string.slogan),
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
    }
}
