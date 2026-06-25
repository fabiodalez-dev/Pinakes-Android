package com.pinakes.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.pinakes.app.data.store.AuthState
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.screens.contact.ContactScreen
import com.pinakes.app.ui.screens.detail.BookDetailScreen
import com.pinakes.app.ui.screens.login.ForgotPasswordScreen
import com.pinakes.app.ui.screens.login.LoginScreen
import com.pinakes.app.ui.screens.login.RegisterScreen
import com.pinakes.app.ui.screens.notifications.NotificationsScreen
import com.pinakes.app.ui.screens.onboarding.OnboardingScreen

/**
 * Root navigation. The high-level [AuthState] decides the start destination; within the
 * authenticated graph, [MainScaffold] hosts the bottom-nav tabs and nested routes.
 */
@Composable
fun PinakesNavHost(navController: NavHostController = rememberNavController()) {
    val services = LocalServices.current
    val authState by services.session.authState.collectAsStateWithLifecycle()

    val start = when (authState) {
        AuthState.NeedsOnboarding -> Routes.ONBOARDING
        AuthState.NeedsLogin -> Routes.LOGIN
        AuthState.Authenticated -> Routes.MAIN_GRAPH
    }

    val durationMs = 280

    NavHost(
        navController = navController,
        startDestination = start,
        // Default cross-fade for top-level auth transitions (onboarding/login/main).
        enterTransition = { fadeIn(animationSpec = tween(durationMs)) },
        exitTransition = { fadeOut(animationSpec = tween(durationMs)) },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onContinue = {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.MAIN_GRAPH) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onChangeLibrary = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(onBackToLogin = { navController.popBackStack(Routes.LOGIN, inclusive = false) })
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBackToLogin = { navController.popBackStack(Routes.LOGIN, inclusive = false) })
        }

        composable(Routes.MAIN_GRAPH) {
            MainScaffold(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN_GRAPH) { inclusive = true }
                    }
                },
                onOpenBook = { id -> navController.navigate(Routes.bookDetail(id)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenContact = { navController.navigate(Routes.CONTACT) },
            )
        }

        val slideIn: (AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition) = {
            slideInHorizontally(tween(durationMs)) { it / 3 } + fadeIn(tween(durationMs))
        }
        val slideOut: (AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition) = {
            slideOutHorizontally(tween(durationMs)) { it / 3 } + fadeOut(tween(durationMs))
        }

        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.IntType }),
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) { backStack ->
            val bookId = backStack.arguments?.getInt(Routes.ARG_BOOK_ID) ?: 0
            BookDetailScreen(bookId = bookId, onNavigateUp = { navController.popBackStack() })
        }

        composable(
            Routes.NOTIFICATIONS,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            NotificationsScreen(onNavigateUp = { navController.popBackStack() })
        }

        composable(
            Routes.CONTACT,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            ContactScreen(onNavigateUp = { navController.popBackStack() })
        }
    }
}
