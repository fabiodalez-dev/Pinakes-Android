package com.pinakes.app.ui.screens.staff.s090.stocktake

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.R
import com.pinakes.app.ui.screens.staff.s090.Staff090Placeholder
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

internal fun NavGraphBuilder.stocktakeGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.STOCKTAKE, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_stocktake)) { nav.popBackStack() }
    }
    composable(Staff090Routes.STATISTICS, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_statistics)) { nav.popBackStack() }
    }
}
