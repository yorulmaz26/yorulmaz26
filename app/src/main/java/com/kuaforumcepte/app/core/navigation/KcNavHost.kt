package com.kuaforumcepte.app.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kuaforumcepte.app.data.registration.LegalDocument
import com.kuaforumcepte.app.feature.account.AccountScreen
import com.kuaforumcepte.app.feature.auth.OtpScreen
import com.kuaforumcepte.app.feature.auth.PhoneEntryScreen
import com.kuaforumcepte.app.feature.auth.SessionGateScreen
import com.kuaforumcepte.app.feature.auth.SessionTarget
import com.kuaforumcepte.app.feature.business.BusinessHomeScreen
import com.kuaforumcepte.app.feature.business.SalonPickerScreen
import com.kuaforumcepte.app.feature.customer.CustomerHomeScreen
import com.kuaforumcepte.app.feature.invitations.InvitationsScreen
import com.kuaforumcepte.app.feature.legal.LegalDocumentScreen
import com.kuaforumcepte.app.feature.register.CustomerRegisterScreen
import com.kuaforumcepte.app.feature.register.OwnerRegisterScreen
import com.kuaforumcepte.app.feature.register.OwnerRegisterSuccessScreen
import com.kuaforumcepte.app.feature.role.RoleSelectionScreen
import com.kuaforumcepte.app.feature.setup.FirebaseSetupRequiredScreen
import com.kuaforumcepte.app.feature.splash.SplashDecision
import com.kuaforumcepte.app.feature.splash.SplashScreen
import com.kuaforumcepte.app.feature.staff.StaffManagementScreen
import com.kuaforumcepte.domain.model.EntryRole

private fun roleOf(name: String): EntryRole = EntryRole.entries.firstOrNull { it.name == name } ?: EntryRole.CUSTOMER

/** Yığını tamamen temizleyip yeni bir kök ekrana geçer (giriş/çıkış, rol değişimi). */
private fun NavHostController.navigateAsRoot(route: Any) {
    navigate(route) { popUpTo(graph.id) { inclusive = true }; launchSingleTop = true }
}

private fun NavOptionsBuilder.single() { launchSingleTop = true }

@Composable
fun KcNavHost(navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.popBackStack() }
    val openLegal: (LegalDocument) -> Unit = { navController.navigate(LegalRoute(it.name)) { single() } }
    val toRoleSelection: () -> Unit = { navController.navigateAsRoot(RoleSelectionRoute) }

    fun routeAfterSession(target: SessionTarget) {
        val route: Any = when (target) {
            SessionTarget.CustomerRegister -> CustomerRegisterRoute
            SessionTarget.CustomerHome -> CustomerHomeRoute
            SessionTarget.Invitations -> InvitationsRoute
            SessionTarget.OwnerRegister -> OwnerRegisterRoute
            SessionTarget.SalonPicker -> SalonPickerRoute
            is SessionTarget.BusinessHome -> BusinessHomeRoute
        }
        navController.navigateAsRoot(route)
    }

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
                navController.navigateAsRoot(
                    when (decision) {
                        SplashDecision.SetupRequired -> FirebaseSetupRoute
                        SplashDecision.RoleSelection -> RoleSelectionRoute
                        is SplashDecision.Resume -> SessionGateRoute(decision.role.name)
                    },
                )
            })
        }
        composable<FirebaseSetupRoute> { FirebaseSetupRequiredScreen() }
        composable<RoleSelectionRoute> {
            RoleSelectionScreen(onRoleChosen = { role -> navController.navigate(PhoneEntryRoute(role.name)) { single() } })
        }

        // ---- Kimlik ----
        composable<PhoneEntryRoute> { entry ->
            val role = roleOf(entry.toRoute<PhoneEntryRoute>().role)
            PhoneEntryScreen(
                role = role,
                onBack = { if (!navController.popBackStack()) toRoleSelection() },
                onCodeSent = { navController.navigate(OtpRoute(role.name)) { single() } },
                onSignedIn = { navController.navigateAsRoot(SessionGateRoute(role.name)) },
            )
        }
        composable<OtpRoute> { entry ->
            val role = roleOf(entry.toRoute<OtpRoute>().role)
            OtpScreen(onBack = back, onSignedIn = { navController.navigateAsRoot(SessionGateRoute(role.name)) })
        }
        composable<SessionGateRoute> { entry ->
            SessionGateScreen(
                role = roleOf(entry.toRoute<SessionGateRoute>().role),
                onRoute = ::routeAfterSession,
                onSignedOut = toRoleSelection,
            )
        }

        // ---- Kayıt ----
        composable<CustomerRegisterRoute> {
            CustomerRegisterScreen(
                onRegistered = { navController.navigateAsRoot(SessionGateRoute(EntryRole.CUSTOMER.name)) },
                onOpenLegal = openLegal,
                onCancel = toRoleSelection,
            )
        }
        composable<OwnerRegisterRoute> {
            OwnerRegisterScreen(
                onCreated = { created -> navController.navigateAsRoot(OwnerRegisterSuccessRoute(created.salonName, created.code)) },
                onOpenLegal = openLegal,
                onExit = toRoleSelection,
            )
        }
        composable<OwnerRegisterSuccessRoute> { entry ->
            val r = entry.toRoute<OwnerRegisterSuccessRoute>()
            OwnerRegisterSuccessScreen(r.salonName, r.code, onContinue = { navController.navigateAsRoot(SessionGateRoute(EntryRole.BUSINESS.name)) })
        }
        composable<LegalRoute> { entry ->
            val doc = LegalDocument.entries.firstOrNull { it.name == entry.toRoute<LegalRoute>().document } ?: LegalDocument.KVKK
            LegalDocumentScreen(doc, onBack = back)
        }

        // ---- İşletme ----
        composable<InvitationsRoute> {
            InvitationsScreen(
                onJoined = { navController.navigateAsRoot(BusinessHomeRoute) },
                onRegisterOwnSalon = { navController.navigate(OwnerRegisterRoute) { single() } },
                onContinue = { navController.navigateAsRoot(SalonPickerRoute) },
                onOpenLegal = openLegal,
                onBack = { if (!navController.popBackStack()) toRoleSelection() },
            )
        }
        composable<SalonPickerRoute> {
            SalonPickerScreen(
                onSelected = { navController.navigateAsRoot(BusinessHomeRoute) },
                onBack = if (navController.previousBackStackEntry != null) back else null,
            )
        }
        composable<BusinessHomeRoute> {
            BusinessHomeScreen(
                onOpenStaff = { navController.navigate(StaffManagementRoute) { single() } },
                onOpenAccount = { navController.navigate(AccountRoute(EntryRole.BUSINESS.name)) { single() } },
                onSwitchSalon = { navController.navigate(SalonPickerRoute) { single() } },
            )
        }
        composable<StaffManagementRoute> { StaffManagementScreen(onBack = back) }

        // ---- Müşteri ----
        composable<CustomerHomeRoute> {
            CustomerHomeScreen(onOpenAccount = { navController.navigate(AccountRoute(EntryRole.CUSTOMER.name)) { single() } })
        }

        // ---- Hesap ----
        composable<AccountRoute> { entry ->
            AccountScreen(
                currentRole = roleOf(entry.toRoute<AccountRoute>().role),
                onBack = back,
                onSwitchRole = { role -> navController.navigateAsRoot(SessionGateRoute(role.name)) },
                onSignedOut = toRoleSelection,
            )
        }
    }
}
