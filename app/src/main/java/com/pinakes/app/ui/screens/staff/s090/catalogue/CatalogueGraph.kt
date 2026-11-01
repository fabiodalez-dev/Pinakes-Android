package com.pinakes.app.ui.screens.staff.s090.catalogue

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.R
import com.pinakes.app.ui.screens.staff.s090.Staff090Placeholder
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

internal fun NavGraphBuilder.catalogueGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.SUPPLIERS, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_suppliers)) { nav.popBackStack() }
    }
    composable(Staff090Routes.MARC, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_marc)) { nav.popBackStack() }
    }
    composable(Staff090Routes.COPY_CATALOGING, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090Placeholder(stringResource(R.string.staff090_copy_cataloging)) { nav.popBackStack() }
    }
}
