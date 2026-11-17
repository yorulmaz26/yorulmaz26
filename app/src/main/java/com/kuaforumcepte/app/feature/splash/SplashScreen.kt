package com.kuaforumcepte.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuaforumcepte.app.core.designsystem.component.BrandMark
import com.kuaforumcepte.app.core.designsystem.theme.Brand
import com.kuaforumcepte.app.core.designsystem.theme.Spacing

@Composable
fun SplashScreen(onDecision: (SplashDecision) -> Unit, viewModel: SplashViewModel = hiltViewModel()) {
    val decision by viewModel.decision.collectAsStateWithLifecycle()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    LaunchedEffect(decision) { decision?.let(onDecision) }

    Box(Modifier.fillMaxSize().background(Brand.Navy).padding(Spacing.screen), contentAlignment = Alignment.Center) {
        BrandMark(
            color = Brand.OffWhite,
            accent = Brand.Gold,
            modifier = Modifier.graphicsLayer {
                alpha = progress.value
                val scale = 0.94f + 0.06f * progress.value
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
