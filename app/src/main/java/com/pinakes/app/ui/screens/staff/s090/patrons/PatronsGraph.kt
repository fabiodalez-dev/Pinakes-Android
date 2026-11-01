package com.pinakes.app.ui.screens.staff.s090.patrons

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

/** The patron area: visitor log, patron CSV import and a patron's ledger. */
internal fun NavGraphBuilder.patronsGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.ATTENDANCE, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090AttendanceScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.USER_IMPORT, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090UserImportScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(
        Staff090Routes.PATRON_LEDGER,
        arguments = listOf(navArgument(Staff090Routes.ARG_USER_ID) { type = NavType.IntType }),
        enterTransition = t.enter, popExitTransition = t.exit,
    ) {
        Staff090LedgerScreen(onNavigateUp = { nav.popBackStack() })
    }
}
