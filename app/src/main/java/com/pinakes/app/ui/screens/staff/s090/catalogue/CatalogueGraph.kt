package com.pinakes.app.ui.screens.staff.s090.catalogue

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.screens.staff.s090.Staff090Transitions

/** Cataloguing (Pinakes 0.9.0): suppliers, MARC import/export, copy cataloguing. */
internal fun NavGraphBuilder.catalogueGraph(nav: NavHostController, t: Staff090Transitions) {
    composable(Staff090Routes.SUPPLIERS, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090CatalogueSuppliersScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.MARC, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090CatalogueMarcScreen(onNavigateUp = { nav.popBackStack() })
    }
    composable(Staff090Routes.COPY_CATALOGING, enterTransition = t.enter, popExitTransition = t.exit) {
        Staff090CatalogueCopyCatalogingScreen(
            onNavigateUp = { nav.popBackStack() },
            onOpenBook = { nav.navigate(Routes.staffBookEdit(it)) },
        )
    }
}
