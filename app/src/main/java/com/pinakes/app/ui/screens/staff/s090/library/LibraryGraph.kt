package com.pinakes.app.ui.screens.staff.s090.library

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.R
import com.pinakes.app.ui.screens.staff.s090.Staff090Placeholder
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

internal fun NavGraphBuilder.libraryGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.LIBRARY_SETTINGS, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_settings)) { nav.popBackStack() }
    }
    composable(Staff090Routes.CIRCULATION_RULES, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_circulation_rules)) { nav.popBackStack() }
    }
    composable(Staff090Routes.CLOSURES_CALENDAR, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_calendar)) { nav.popBackStack() }
    }
}
