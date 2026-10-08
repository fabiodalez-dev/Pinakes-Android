package com.pinakes.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinakes.app.data.store.AuthState
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.screens.bookclub.BookClubHomeScreen
import com.pinakes.app.ui.screens.bookclub.ClubDetailScreen
import com.pinakes.app.ui.screens.collections.*
import com.pinakes.app.ui.screens.contact.ContactScreen
import com.pinakes.app.ui.screens.detail.BookDetailScreen
import com.pinakes.app.ui.screens.login.ForgotPasswordScreen
import com.pinakes.app.ui.screens.login.LoginScreen
import com.pinakes.app.ui.screens.login.RegisterScreen
import com.pinakes.app.ui.screens.notifications.NotificationsScreen
import com.pinakes.app.ui.screens.onboarding.OnboardingScreen
import com.pinakes.app.ui.screens.periodicals.IssueDetailScreen
import com.pinakes.app.ui.screens.periodicals.IssueListScreen
import com.pinakes.app.ui.screens.periodicals.PeriodicalDetailScreen
import com.pinakes.app.ui.screens.periodicals.StandaloneArticlesScreen
import com.pinakes.app.ui.screens.periodicals.StandaloneArticleScreen
import com.pinakes.app.ui.screens.periodicals.PeriodicalsScreen
import com.pinakes.app.ui.screens.reviews.MyReviewsScreen

/**
 * Root navigation. The high-level [AuthState] decides the start destination; within the
 * authenticated graph, [MainScaffold] hosts the bottom-nav tabs and nested routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinakesNavHost(navController: NavHostController = rememberNavController()) {
    val app: AppViewModel = hiltViewModel()
    val authState by app.authState.collectAsStateWithLifecycle()

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
                onOpenMyReviews = { navController.navigate(Routes.MY_REVIEWS) },
                onOpenBookClub = { navController.navigate(Routes.BOOK_CLUB) },
                onOpenPeriodicals = { navController.navigate(Routes.PERIODICALS) },
                onOpenArticles = { navController.navigate(Routes.standaloneArticles()) },
                onOpenDesiderata = { navController.navigate(Routes.DESIDERATA) },
                onOpenArchives = { navController.navigate(Routes.archives()) },
                onOpenArticle = { navController.navigate(Routes.standaloneArticle(it)) },
                onOpenWanted = { navController.navigate(Routes.wantedBook(it)) },
                onOpenArchive = { navController.navigate(Routes.archive(it)) },
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
        ) {
            BookDetailScreen(onNavigateUp = { navController.popBackStack() }, onFindWorks = { name, id -> navController.navigate(Routes.authorWorks(name, id)) })
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

        composable(
            Routes.MY_REVIEWS,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            MyReviewsScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(Routes.bookDetail(id)) },
            )
        }

        composable(
            Routes.BOOK_CLUB,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            BookClubHomeScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenClub = { slug -> navController.navigate(Routes.clubDetail(slug)) },
            )
        }

        composable(
            route = Routes.CLUB_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_CLUB_SLUG) { type = NavType.StringType }),
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            ClubDetailScreen(onNavigateUp = { navController.popBackStack() })
        }

        composable(
            Routes.STANDALONE_ARTICLES,
            arguments = listOf(navArgument(Routes.ARG_PERIODICAL_ID) { type = NavType.IntType; defaultValue = 0 },
                navArgument(Routes.ARG_PERIODICAL_ISSUE_ID) { type = NavType.IntType; defaultValue = 0 },
                navArgument("container") { defaultValue = "" }, navArgument("keyword") { defaultValue = "" },
                navArgument("genreId") { type = NavType.IntType; defaultValue = 0 }, navArgument("q") { defaultValue = "" }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StandaloneArticlesScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenArticle = { id -> navController.navigate(Routes.standaloneArticle(id)) },
            )
        }
        composable(
            Routes.STANDALONE_ARTICLE,
            arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StandaloneArticleScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenPeriodical = { id -> navController.navigate(Routes.periodicalDetail(id)) },
                onOpenIssue = { id -> navController.navigate(Routes.periodicalIssue(id)) },
                onFindWorks = { name, id -> navController.navigate(Routes.authorWorks(name, id)) },
                onFindArticles = { container, keyword, genre -> navController.navigate(Routes.standaloneArticles(container = container, keyword = keyword, genreId = genre)) },
            )
        }

        composable(Routes.AUTHOR_WORKS, arguments = listOf(navArgument("author") { defaultValue = "" }, navArgument("authorId") { type = NavType.IntType; defaultValue = 0 })) { entry ->
            androidx.compose.material3.Scaffold(topBar = { com.pinakes.app.ui.components.PinakesTopBar(entry.arguments?.getString("author").orEmpty(), onNavigateUp = { navController.popBackStack() }) }) { padding ->
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(padding)) {
                    com.pinakes.app.ui.screens.search.SearchScreen(onBookClick = { navController.navigate(Routes.bookDetail(it)) },
                        onArticleClick = { navController.navigate(Routes.standaloneArticle(it)) }, initialAuthor = entry.arguments?.getString("author"), initialAuthorId = entry.arguments?.getInt("authorId"))
                }
            }
        }
        composable(Routes.DESIDERATA) {
            DesiderataScreen({ navController.popBackStack() }, { navController.navigate(Routes.wantedBook(it)) }, { navController.navigate(Routes.donation(it)) })
        }
        composable(Routes.WANTED_BOOK, arguments = listOf(navArgument(Routes.ARG_WANTED_ID) { type = NavType.IntType })) {
            WantedBookScreen({ navController.popBackStack() }, { navController.navigate(Routes.donation(it)) })
        }
        composable(Routes.DONATION, arguments = listOf(navArgument(Routes.ARG_WANTED_ID) { type = NavType.IntType; defaultValue = 0 })) {
            DonationScreen({ navController.popBackStack() }, { if (!navController.popBackStack(Routes.DESIDERATA, false)) navController.navigate(Routes.DESIDERATA) { popUpTo(Routes.DONATION) { inclusive = true } } })
        }
        composable(Routes.ARCHIVES, arguments = listOf(navArgument(Routes.ARG_ARCHIVE_PARENT) { type = NavType.IntType; defaultValue = 0 })) {
            ArchivesScreen({ navController.popBackStack() }, { navController.navigate(Routes.archive(it)) })
        }
        composable(Routes.ARCHIVE, arguments = listOf(navArgument(Routes.ARG_ARCHIVE_ID) { type = NavType.IntType })) {
            ArchiveScreen({ navController.popBackStack() }, { navController.navigate(Routes.archive(it)) }, { navController.navigate(Routes.archives(it)) })
        }

        // ---- Periodicals / Emeroteca (optional plugin) ----
        composable(
            Routes.PERIODICALS,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            PeriodicalsScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenPeriodical = { id -> navController.navigate(Routes.periodicalDetail(id)) },
                onOpenArticles = { navController.navigate(Routes.standaloneArticles()) },
            )
        }

        composable(
            route = Routes.PERIODICAL_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_PERIODICAL_ID) { type = NavType.IntType }),
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            PeriodicalDetailScreen(
                onOpenArticles = { id -> navController.navigate(Routes.standaloneArticles(id)) },
                onNavigateUp = { navController.popBackStack() },
                onOpenYear = { yearId, year ->
                    navController.navigate(Routes.periodicalYearIssues(yearId, year))
                },
            )
        }

        composable(
            route = Routes.PERIODICAL_YEAR_ISSUES,
            arguments = listOf(
                navArgument(Routes.ARG_PERIODICAL_YEAR_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_PERIODICAL_YEAR) { type = NavType.IntType },
            ),
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            IssueListScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenIssue = { id -> navController.navigate(Routes.periodicalIssue(id)) },
            )
        }

        composable(
            route = Routes.PERIODICAL_ISSUE,
            arguments = listOf(navArgument(Routes.ARG_PERIODICAL_ISSUE_ID) { type = NavType.IntType }),
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            IssueDetailScreen(onNavigateUp = { navController.popBackStack() }, onOpenArticle = { navController.navigate(Routes.standaloneArticle(it)) }, onFindArticles = { navController.navigate(Routes.standaloneArticles(issueId = it)) })
        }
    }
}
