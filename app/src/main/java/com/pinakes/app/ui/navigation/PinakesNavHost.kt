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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pinakes.app.ui.components.PinakesTab
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
import com.pinakes.app.ui.screens.movements.MovementsScreen
import com.pinakes.app.ui.screens.staff.StaffAuthorsScreen
import com.pinakes.app.ui.screens.staff.StaffBookEditScreen
import com.pinakes.app.ui.screens.staff.StaffBooksScreen
import com.pinakes.app.ui.screens.staff.StaffGenresScreen
import com.pinakes.app.ui.screens.staff.StaffHubScreen
import com.pinakes.app.ui.screens.staff.StaffPublishersScreen
import com.pinakes.app.ui.screens.staff.StaffRecordDetailScreen
import com.pinakes.app.ui.screens.staff.RecordKind
import com.pinakes.app.ui.screens.staff.StaffShelvesScreen
import com.pinakes.app.ui.screens.staff.scan.StaffScanScreen
import com.pinakes.app.ui.screens.staff.StaffHubPhase2
import com.pinakes.app.ui.screens.staff.circulation.StaffCirculationHomeScreen
import com.pinakes.app.ui.screens.staff.circulation.StaffLoanCreateScreen
import com.pinakes.app.ui.screens.staff.circulation.StaffLoanDetailScreen
import com.pinakes.app.ui.screens.staff.circulation.StaffLoansScreen
import com.pinakes.app.ui.screens.staff.circulation.StaffReservationsScreen
import com.pinakes.app.ui.screens.staff.circulation.StaffUserScreen
import com.pinakes.app.ui.screens.staff.scan.StaffCopyScanScreen
import com.pinakes.app.ui.screens.staff.content.StaffMergeScreen
import com.pinakes.app.ui.screens.staff.plugins.StaffArchiveEditScreen
import com.pinakes.app.ui.screens.staff.plugins.StaffArchivesScreen
import com.pinakes.app.ui.screens.staff.plugins.StaffAuthoritiesScreen
import com.pinakes.app.ui.screens.staff.plugins.StaffAuthorityEditScreen
import com.pinakes.app.ui.screens.staff.plugins.StaffDesiderataScreen
import com.pinakes.app.ui.screens.staff.content.StaffCoversScreen
import com.pinakes.app.ui.screens.staff.content.StaffImportJobScreen
import com.pinakes.app.ui.screens.staff.content.StaffImportsScreen
import com.pinakes.app.ui.screens.staff.content.StaffSeriesDetailScreen
import com.pinakes.app.ui.screens.staff.content.StaffSeriesScreen

/**
 * Root navigation. The high-level [AuthState] decides the start destination; within the
 * authenticated graph, [MainScaffold] hosts the bottom-nav tabs and nested routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinakesNavHost(navController: NavHostController = rememberNavController()) {
    // The selected bottom-nav tab lives here, not in MainScaffold, so a book page (its own
    // destination) can show the same bar and send the reader back to any tab.
    var mainTab by rememberSaveable { mutableStateOf(PinakesTab.Home) }
    val app: AppViewModel = hiltViewModel()
    val authState by app.authState.collectAsStateWithLifecycle()

    // A new session (another account, another library, or the same one after a logout or an
    // expired token) always opens on Home, not on the tab the previous session left open.
    LaunchedEffect(authState) {
        if (authState != AuthState.Authenticated) mainTab = PinakesTab.Home
    }

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
                tab = mainTab,
                onTabChange = { mainTab = it },
                onLoggedOut = {
                    mainTab = PinakesTab.Home
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN_GRAPH) { inclusive = true }
                    }
                },
                onOpenBook = { id -> navController.navigate(Routes.bookDetail(id)) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenContact = { navController.navigate(Routes.CONTACT) },
                onOpenMyReviews = { navController.navigate(Routes.MY_REVIEWS) },
                onOpenMovements = { navController.navigate(Routes.MOVEMENTS) },
                onOpenBookClub = { navController.navigate(Routes.BOOK_CLUB) },
                onOpenPeriodicals = { navController.navigate(Routes.PERIODICALS) },
                onOpenArticles = { navController.navigate(Routes.standaloneArticles()) },
                onOpenDesiderata = { navController.navigate(Routes.DESIDERATA) },
                onOpenArchives = { navController.navigate(Routes.archives()) },
                onOpenArticle = { navController.navigate(Routes.standaloneArticle(it)) },
                onOpenWanted = { navController.navigate(Routes.wantedBook(it)) },
                onOpenArchive = { navController.navigate(Routes.archive(it)) },
                onOpenStaff = { navController.navigate(Routes.STAFF) },
                onOpenStaffScan = { navController.navigate(Routes.STAFF_SCAN) },
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
            BookDetailScreen(
                onNavigateUp = { navController.popBackStack() },
                onFindWorks = { name, id -> navController.navigate(Routes.authorWorks(name, id)) },
                selectedTab = mainTab,
                onTabSelected = { tab ->
                    mainTab = tab
                    navController.popBackStack(Routes.MAIN_GRAPH, inclusive = false)
                },
            )
        }

        // ---- Staff area (admin/staff + Mobile API 1.6.0) ----
        composable(Routes.STAFF, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffHubScreen(
                onNavigateUp = { navController.popBackStack() },
                onScan = { navController.navigate(Routes.STAFF_SCAN) },
                onAddManual = { navController.navigate(Routes.staffBookNew()) },
                onBooks = { navController.navigate(Routes.STAFF_BOOKS) },
                onAuthors = { navController.navigate(Routes.STAFF_AUTHORS) },
                onPublishers = { navController.navigate(Routes.STAFF_PUBLISHERS) },
                onGenres = { navController.navigate(Routes.STAFF_GENRES) },
                onShelves = { navController.navigate(Routes.STAFF_SHELVES) },
                phase2 = StaffHubPhase2(
                    onCirculation = { navController.navigate(Routes.STAFF_CIRCULATION) },
                    onCopyScan = { navController.navigate(Routes.STAFF_COPY_SCAN) },
                    onNewLoan = { navController.navigate(Routes.staffLoanNew()) },
                    onReservations = { navController.navigate(Routes.STAFF_RESERVATIONS) },
                    onSeries = { navController.navigate(Routes.STAFF_SERIES) },
                    onMerges = { navController.navigate(Routes.STAFF_MERGES) },
                    onImports = { navController.navigate(Routes.STAFF_IMPORTS) },
                    onCovers = { navController.navigate(Routes.STAFF_COVERS) },
                    onDesiderata = { navController.navigate(Routes.STAFF_DESIDERATA) },
                    onArchives = { navController.navigate(Routes.STAFF_ARCHIVES) },
                    onAuthorities = { navController.navigate(Routes.STAFF_AUTHORITIES) },
                    onPeriodicals = { navController.navigate(Routes.STAFF_PERIODICALS) },
                    onClubs = { navController.navigate(Routes.STAFF_CLUBS) },
                    onWorks = { navController.navigate(Routes.STAFF_WORKS) },
                ),
            )
        }

        // ---- Staff circulation desk (Mobile API 1.7.0) ----
        composable(Routes.STAFF_CIRCULATION, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffCirculationHomeScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenLoans = { navController.navigate(Routes.staffLoans(it)) },
                onScanCopy = { navController.navigate(Routes.STAFF_COPY_SCAN) },
                onNewLoan = { navController.navigate(Routes.staffLoanNew()) },
                onReservations = { navController.navigate(Routes.STAFF_RESERVATIONS) },
            )
        }
        composable(
            Routes.STAFF_LOANS,
            arguments = listOf(navArgument(Routes.ARG_BUCKET) { type = NavType.StringType; defaultValue = "all" }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffLoansScreen(onNavigateUp = { navController.popBackStack() }, onOpenLoan = { navController.navigate(Routes.staffLoan(it)) })
        }
        composable(
            Routes.STAFF_LOAN,
            arguments = listOf(navArgument(Routes.ARG_LOAN_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffLoanDetailScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenUser = { navController.navigate(Routes.staffUser(it)) },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) },
            )
        }
        composable(
            Routes.STAFF_LOAN_NEW,
            arguments = listOf(
                navArgument(Routes.ARG_USER_ID) { type = NavType.IntType; defaultValue = 0 },
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.IntType; defaultValue = 0 },
                navArgument(Routes.ARG_COPY_CODE) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ARG_RESERVATION_MODE) { type = NavType.BoolType; defaultValue = false },
            ),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffLoanCreateScreen(
                onNavigateUp = { navController.popBackStack() },
                onCreated = { id -> navController.navigate(Routes.staffLoan(id)) { popUpTo(Routes.STAFF_LOAN_NEW) { inclusive = true } } },
                onReservationCreated = { navController.popBackStack() },
            )
        }
        composable(Routes.STAFF_RESERVATIONS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffReservationsScreen(
                onNavigateUp = { navController.popBackStack() },
                onNewReservation = { navController.navigate(Routes.staffLoanNew(reservation = true)) },
                onOpenUser = { navController.navigate(Routes.staffUser(it)) },
                onNewLoan = { user, book -> navController.navigate(Routes.staffLoanNew(userId = user, bookId = book)) },
            )
        }
        composable(
            Routes.STAFF_USER,
            arguments = listOf(navArgument(Routes.ARG_USER_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffUserScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenLoan = { navController.navigate(Routes.staffLoan(it)) },
                onNewLoan = { navController.navigate(Routes.staffLoanNew(userId = it)) },
            )
        }
        // ---- Staff book tools (Mobile API 1.7.0) ----
        composable(Routes.STAFF_SERIES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffSeriesScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffSeriesDetail(it)) })
        }
        composable(
            Routes.STAFF_SERIES_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffSeriesDetailScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.staffSeriesDetail(id)) { popUpTo(Routes.STAFF_SERIES) } },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) },
            )
        }
        composable(Routes.STAFF_MERGES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffMergeScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_IMPORTS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffImportsScreen(onNavigateUp = { navController.popBackStack() }, onOpenJob = { navController.navigate(Routes.staffImport(it)) })
        }
        composable(
            Routes.STAFF_IMPORT,
            arguments = listOf(navArgument(Routes.ARG_JOB_ID) { type = NavType.StringType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffImportJobScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_COVERS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffCoversScreen(onNavigateUp = { navController.popBackStack() })
        }
        // ---- Staff plugin content (Mobile API 1.7.0) ----
        composable(Routes.STAFF_DESIDERATA, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffDesiderataScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) },
                onOpenCopy = { book, copy -> navController.navigate(Routes.staffBookEdit(book, copy)) },
            )
        }
        composable(Routes.STAFF_ARCHIVES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffArchivesScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpen = { navController.navigate(Routes.staffArchiveEdit(it)) },
                onNew = { navController.navigate(Routes.staffArchiveEdit(0)) },
            )
        }
        composable(
            Routes.STAFF_ARCHIVE_EDIT,
            arguments = listOf(navArgument(Routes.ARG_ARCHIVE_ID) { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffArchiveEditScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_AUTHORITIES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffAuthoritiesScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffAuthorityEdit(it)) })
        }
        composable(
            Routes.STAFF_AUTHORITY_EDIT,
            arguments = listOf(navArgument(Routes.ARG_AUTHORITY_ID) { type = NavType.IntType; defaultValue = 0 }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffAuthorityEditScreen(onNavigateUp = { navController.popBackStack() })
        }
        // ---- Staff Tier 2 bridges (periodicals desk, book clubs, FRBR works) ----
        composable(Routes.STAFF_PERIODICALS, enterTransition = slideIn, popExitTransition = slideOut) {
            com.pinakes.app.ui.screens.staff.tier2.StaffPeriodicalsDeskScreen(
                onNavigateUp = { navController.popBackStack() }, onOpenPeriodical = { navController.navigate(Routes.staffPeriodical(it)) },
            )
        }
        composable(
            Routes.STAFF_PERIODICAL,
            arguments = listOf(navArgument(Routes.ARG_PERIODICAL_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            com.pinakes.app.ui.screens.staff.tier2.StaffPeriodicalScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_CLUBS, enterTransition = slideIn, popExitTransition = slideOut) {
            com.pinakes.app.ui.screens.staff.tier2.StaffClubsScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffClub(it)) })
        }
        composable(
            Routes.STAFF_CLUB,
            arguments = listOf(navArgument(Routes.ARG_CLUB_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            com.pinakes.app.ui.screens.staff.tier2.StaffClubScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_WORKS, enterTransition = slideIn, popExitTransition = slideOut) {
            com.pinakes.app.ui.screens.staff.tier2.StaffWorksScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffWork(it)) })
        }
        composable(
            Routes.STAFF_WORK,
            arguments = listOf(navArgument(Routes.ARG_WORK_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            com.pinakes.app.ui.screens.staff.tier2.StaffWorkScreen(
                onNavigateUp = { navController.popBackStack() }, onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) },
            )
        }

        composable(Routes.STAFF_COPY_SCAN, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffCopyScanScreen(
                onNavigateUp = { navController.popBackStack() },
                onNewLoan = { bookId, code -> navController.navigate(Routes.staffLoanNew(bookId = bookId, copyCode = code)) },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) },
                onOpenLoan = { navController.navigate(Routes.staffLoan(it)) },
            )
        }
        composable(Routes.STAFF_SCAN, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffScanScreen(
                onNavigateUp = { navController.popBackStack() },
                onEditNew = { code -> navController.navigate(Routes.staffBookNew(code)) },
                onOpenBook = { id -> navController.navigate(Routes.staffBookEdit(id)) },
            )
        }
        composable(Routes.STAFF_BOOKS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffBooksScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(Routes.staffBookEdit(id)) },
                onNewBook = { navController.navigate(Routes.staffBookNew()) },
            )
        }
        composable(
            Routes.STAFF_BOOK_NEW,
            arguments = listOf(navArgument(Routes.ARG_ISBN) { type = NavType.StringType; defaultValue = "" }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffBookEditScreen(
                onNavigateUp = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                onOpenExisting = { id ->
                    navController.navigate(Routes.staffBookEdit(id)) { popUpTo(Routes.STAFF_BOOK_NEW) { inclusive = true } }
                },
            )
        }
        composable(
            Routes.STAFF_BOOK_EDIT,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_COPY_ID) { type = NavType.IntType; defaultValue = 0 },
            ),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffBookEditScreen(
                onNavigateUp = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                onOpenExisting = { id ->
                    navController.navigate(Routes.staffBookEdit(id)) { popUpTo(Routes.STAFF_BOOK_EDIT) { inclusive = true } }
                },
            )
        }
        composable(Routes.STAFF_AUTHORS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffAuthorsScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffAuthor(it)) })
        }
        composable(Routes.STAFF_PUBLISHERS, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffPublishersScreen(onNavigateUp = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.staffPublisher(it)) })
        }
        composable(
            Routes.STAFF_AUTHOR,
            arguments = listOf(navArgument(Routes.ARG_RECORD_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffRecordDetailScreen(RecordKind.AUTHOR, onNavigateUp = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) })
        }
        composable(
            Routes.STAFF_PUBLISHER,
            arguments = listOf(navArgument(Routes.ARG_RECORD_ID) { type = NavType.IntType }),
            enterTransition = slideIn, popExitTransition = slideOut,
        ) {
            StaffRecordDetailScreen(RecordKind.PUBLISHER, onNavigateUp = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.staffBookEdit(it)) })
        }
        composable(Routes.STAFF_GENRES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffGenresScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(Routes.STAFF_SHELVES, enterTransition = slideIn, popExitTransition = slideOut) {
            StaffShelvesScreen(onNavigateUp = { navController.popBackStack() })
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
            Routes.MOVEMENTS,
            enterTransition = slideIn,
            popExitTransition = slideOut,
        ) {
            MovementsScreen(onNavigateUp = { navController.popBackStack() })
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
                    val authorName = entry.arguments?.getString("author").orEmpty()
                    com.pinakes.app.ui.screens.search.SearchScreen(onBookClick = { navController.navigate(Routes.bookDetail(it)) },
                        onArticleClick = { navController.navigate(Routes.standaloneArticle(it)) }, initialAuthor = entry.arguments?.getString("author"), initialAuthorId = entry.arguments?.getInt("authorId"),
                        header = { count, more -> com.pinakes.app.ui.screens.search.AuthorInfoCard(authorName, count, more) })
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
