package com.kuaforumcepte.app.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kuaforumcepte.app.feature.auth.AuthEntryScreen
import com.kuaforumcepte.app.feature.role.RoleSelectionScreen
import com.kuaforumcepte.app.feature.setup.FirebaseSetupRequiredScreen
import com.kuaforumcepte.app.feature.splash.SplashDecision
import com.kuaforumcepte.app.feature.splash.SplashScreen
import com.kuaforumcepte.domain.model.EntryRole

@Composable
fun KcNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(280)) + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280)) + fadeOut(tween(280)) },
    ) {
        composable<SplashRoute>(enterTransition = { fadeIn() }, exitTransition = { fadeOut(tween(350)) }) {
            SplashScreen(onDecision = { decision ->
                val target: Any = when (decision) {
                    SplashDecision.SetupRequired -> FirebaseSetupRoute
                    SplashDecision.RoleSelection -> RoleSelectionRoute
                    is SplashDecision.Resume -> AuthRoute(decision.role.name)
                }
                navController.navigate(target) { popUpTo(SplashRoute) { inclusive = true } }
            })
        }
        composable<FirebaseSetupRoute> { FirebaseSetupRequiredScreen() }
        composable<RoleSelectionRoute> {
            RoleSelectionScreen(onRoleChosen = { role -> navController.navigate(AuthRoute(role.name)) })
        }
        composable<AuthRoute> { entry ->
            val route = entry.toRoute<AuthRoute>()
            val role = EntryRole.entries.firstOrNull { it.name == route.role } ?: EntryRole.CUSTOMER
            AuthEntryScreen(role = role, onBack = {
                if (!navController.popBackStack()) {
                    navController.navigate(RoleSelectionRoute) { popUpTo(0) }
                }
            })
        }
    }
}
