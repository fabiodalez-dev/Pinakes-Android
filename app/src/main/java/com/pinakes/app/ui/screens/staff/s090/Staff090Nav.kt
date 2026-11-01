package com.pinakes.app.ui.screens.staff.s090

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.pinakes.app.ui.screens.staff.s090.catalogue.catalogueGraph
import com.pinakes.app.ui.screens.staff.s090.library.libraryGraph
import com.pinakes.app.ui.screens.staff.s090.patrons.patronsGraph
import com.pinakes.app.ui.screens.staff.s090.stocktake.stocktakeGraph

/**
 * Entry routes of the Pinakes 0.9.0 staff tools (Mobile API 1.10.0). Each area registers
 * its own screens in its package (`stocktake`, `patrons`, `library`, `catalogue`); this file
 * only names the doors other screens open.
 */
object Staff090Routes {
    const val STOCKTAKE = "staff/s090/stocktake"
    const val STATISTICS = "staff/s090/statistics"
    const val ATTENDANCE = "staff/s090/attendance"
    const val USER_IMPORT = "staff/s090/user-import"
    const val LIBRARY_SETTINGS = "staff/s090/settings"
    const val CIRCULATION_RULES = "staff/s090/circulation-rules"
    const val CLOSURES_CALENDAR = "staff/s090/calendar"
    const val SUPPLIERS = "staff/s090/suppliers"
    const val MARC = "staff/s090/marc"
    const val COPY_CATALOGING = "staff/s090/copy-cataloging"

    const val ARG_USER_ID = "userId"
    const val PATRON_LEDGER = "staff/s090/patrons/{$ARG_USER_ID}/ledger"
    fun patronLedger(userId: Int) = "staff/s090/patrons/$userId/ledger"
}

/** The NavHost's screen transitions, handed to the area graphs. */
class Staff090Transitions(
    val enter: AnimatedContentTransitionScope<*>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<*>.() -> ExitTransition,
)

fun NavGraphBuilder.staff090Graph(nav: NavHostController, transitions: Staff090Transitions) {
    stocktakeGraph(nav, transitions)
    patronsGraph(nav, transitions)
    libraryGraph(nav, transitions)
    catalogueGraph(nav, transitions)
}
