package com.pinakes.app.ui.screens.staff.s090.stocktake

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

/** The session id argument of the stocktake session and report routes. */
internal const val STOCKTAKE_ARG_SESSION = "sessionId"
private const val STOCKTAKE_SESSION = "staff/s090/stocktake/{$STOCKTAKE_ARG_SESSION}"
private const val STOCKTAKE_REPORT = "staff/s090/stocktake/{$STOCKTAKE_ARG_SESSION}/report"
private fun stocktakeSession(id: Int) = "staff/s090/stocktake/$id"
private fun stocktakeReport(id: Int) = "staff/s090/stocktake/$id/report"

/** Stocktake (sessions → session scanner → report) and statistics. A tapped book opens the staff book editor. */
internal fun NavGraphBuilder.stocktakeGraph(nav: NavHostController, t: Staff090Transitions) {
    val sessionArg = listOf(navArgument(STOCKTAKE_ARG_SESSION) { type = NavType.IntType })
    composable(Staff090Routes.STOCKTAKE, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090StocktakeSessionsScreen(onNavigateUp = { nav.popBackStack() }, onOpenSession = { nav.navigate(stocktakeSession(it)) })
    }
    composable(STOCKTAKE_SESSION, arguments = sessionArg, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090StocktakeSessionScreen(
            onNavigateUp = { nav.popBackStack() },
            onOpenReport = { nav.navigate(stocktakeReport(it)) },
            onOpenBook = { nav.navigate(Routes.staffBookEdit(it)) },
        )
    }
    composable(STOCKTAKE_REPORT, arguments = sessionArg, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090StocktakeReportScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.STATISTICS, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090StatisticsScreen(onNavigateUp = { nav.popBackStack() }, onOpenBook = { nav.navigate(Routes.staffBookEdit(it)) })
    }
}
