package com.pinakes.app.ui.screens.staff.s090.library

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

/** Library area routes beyond the hub's entry doors. */
internal object LibraryRoutes {
    const val RULES_MATRIX = "staff/s090/circulation-rules/matrix"
}

internal fun NavGraphBuilder.libraryGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.LIBRARY_SETTINGS, enterTransition = t.enter, popExitTransition = t.exit) {
        LibrarySettingsScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.CIRCULATION_RULES, enterTransition = t.enter, popExitTransition = t.exit) {
        PatronTypesScreen(onNavigateUp = { nav.popBackStack() }, onOpenMatrix = { nav.navigate(LibraryRoutes.RULES_MATRIX) })
    }
    composable(LibraryRoutes.RULES_MATRIX, enterTransition = t.enter, popExitTransition = t.exit) {
        RulesMatrixScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.CLOSURES_CALENDAR, enterTransition = t.enter, popExitTransition = t.exit) {
        ClosuresCalendarScreen(onNavigateUp = { nav.popBackStack() })
    }
}
