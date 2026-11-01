package com.pinakes.app.ui.screens.staff.s090

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pinakes.app.R
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.StaffActionRow

/** The staff hub's Pinakes 0.9.0 sections; [onOpen] receives a [Staff090Routes] entry. */
@Composable
internal fun Staff090HubSections(areas: Staff090Areas, onOpen: (String) -> Unit) {
    if (areas.stocktake || areas.anyPatronDesk) {
        FormSectionTitle(stringResource(R.string.staff090_section_desk))
        if (areas.attendance) StaffActionRow(Icons.Outlined.HowToReg, stringResource(R.string.staff090_attendance),
            stringResource(R.string.staff090_attendance_subtitle)) { onOpen(Staff090Routes.ATTENDANCE) }
        if (areas.stocktake) StaffActionRow(Icons.Outlined.FactCheck, stringResource(R.string.staff090_stocktake),
            stringResource(R.string.staff090_stocktake_subtitle)) { onOpen(Staff090Routes.STOCKTAKE) }
        if (areas.userImport) StaffActionRow(Icons.Outlined.GroupAdd, stringResource(R.string.staff090_user_import),
            stringResource(R.string.staff090_user_import_subtitle)) { onOpen(Staff090Routes.USER_IMPORT) }
    }
    if (areas.anyCatalogue) {
        FormSectionTitle(stringResource(R.string.staff090_section_cataloguing))
        if (areas.marc) {
            StaffActionRow(Icons.Outlined.SwapVert, stringResource(R.string.staff090_marc),
                stringResource(R.string.staff090_marc_subtitle)) { onOpen(Staff090Routes.MARC) }
            StaffActionRow(Icons.Outlined.TravelExplore, stringResource(R.string.staff090_copy_cataloging),
                stringResource(R.string.staff090_copy_cataloging_subtitle)) { onOpen(Staff090Routes.COPY_CATALOGING) }
        }
        if (areas.suppliers) StaffActionRow(Icons.Outlined.LocalShipping, stringResource(R.string.staff090_suppliers),
            stringResource(R.string.staff090_suppliers_subtitle)) { onOpen(Staff090Routes.SUPPLIERS) }
    }
    if (areas.anyLibrary || areas.statistics) {
        FormSectionTitle(stringResource(R.string.staff090_section_library))
        if (areas.statistics) StaffActionRow(Icons.Outlined.BarChart, stringResource(R.string.staff090_statistics),
            stringResource(R.string.staff090_statistics_subtitle)) { onOpen(Staff090Routes.STATISTICS) }
        if (areas.closuresCalendar) StaffActionRow(Icons.Outlined.CalendarMonth, stringResource(R.string.staff090_calendar),
            stringResource(R.string.staff090_calendar_subtitle)) { onOpen(Staff090Routes.CLOSURES_CALENDAR) }
        if (areas.circulationRules) StaffActionRow(Icons.Outlined.Rule, stringResource(R.string.staff090_circulation_rules),
            stringResource(R.string.staff090_circulation_rules_subtitle)) { onOpen(Staff090Routes.CIRCULATION_RULES) }
        if (areas.librarySettings) StaffActionRow(Icons.Outlined.Settings, stringResource(R.string.staff090_settings),
            stringResource(R.string.staff090_settings_subtitle)) { onOpen(Staff090Routes.LIBRARY_SETTINGS) }
    }
}
