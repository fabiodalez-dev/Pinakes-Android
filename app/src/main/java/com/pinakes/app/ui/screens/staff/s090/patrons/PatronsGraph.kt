package com.pinakes.app.ui.screens.staff.s090.patrons

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.R
import com.pinakes.app.ui.screens.staff.s090.Staff090Placeholder
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

internal fun NavGraphBuilder.patronsGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.ATTENDANCE, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_attendance)) { nav.popBackStack() }
    }
    composable(Staff090Routes.USER_IMPORT, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_user_import)) { nav.popBackStack() }
    }
    composable(Staff090Routes.PATRON_LEDGER, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_ledger)) { nav.popBackStack() }
    }
}
