package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.screens.staff.content.StaffLabelsSettingsSheet
import com.pinakes.app.ui.theme.Spacing

/** Navigation targets of the staff hub's Phase 2 sections (Mobile API 1.7.0). */
data class StaffHubPhase2(
    val onCirculation: () -> Unit = {},
    val onCopyScan: () -> Unit = {},
    val onNewLoan: () -> Unit = {},
    val onReservations: () -> Unit = {},
    val onSeries: () -> Unit = {},
    val onMerges: () -> Unit = {},
    val onImports: () -> Unit = {},
    val onCovers: () -> Unit = {},
    val onDesiderata: () -> Unit = {},
    val onArchives: () -> Unit = {},
    val onAuthorities: () -> Unit = {},
    val onPeriodicals: () -> Unit = {},
    val onClubs: () -> Unit = {},
    val onWorks: () -> Unit = {},
)

/**
 * Entry point of the staff area (Profile → Staff area). Each Phase 2 row shows only when the
 * server advertises its area; the circulation section also needs a connection and disappears
 * offline (hidden, not disabled).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHubScreen(
    onNavigateUp: () -> Unit,
    onScan: () -> Unit,
    onAddManual: () -> Unit,
    onBooks: () -> Unit,
    onAuthors: () -> Unit,
    onPublishers: () -> Unit,
    onGenres: () -> Unit,
    onShelves: () -> Unit,
    phase2: StaffHubPhase2 = StaffHubPhase2(),
) {
    val app: AppViewModel = hiltViewModel()
    val isStaff by app.isStaff.collectAsStateWithLifecycle()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val features by app.features.collectAsStateWithLifecycle()
    var labelsOpen by rememberSaveable { mutableStateOf(false) }
    // Role revoked (or the server dropped staff support) while the hub was open.
    ForbiddenExit(forbidden = !isStaff, onNavigateUp = onNavigateUp)

    Scaffold(topBar = { PinakesTopBar(title = stringResource(R.string.staff_title), onNavigateUp = onNavigateUp) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            if (areas.circulation) {
                FormSectionTitle(stringResource(R.string.staff_section_circulation))
                StaffActionRow(Icons.Outlined.SwapHoriz, stringResource(R.string.staff_circulation_desk),
                    stringResource(R.string.staff_circulation_desk_subtitle), phase2.onCirculation)
                StaffActionRow(Icons.AutoMirrored.Outlined.AssignmentReturn, stringResource(R.string.staff_scan_copy),
                    stringResource(R.string.staff_scan_copy_subtitle), phase2.onCopyScan)
                StaffActionRow(Icons.Outlined.PostAdd, stringResource(R.string.staff_loan_new), onClick = phase2.onNewLoan)
                StaffActionRow(Icons.Outlined.Event, stringResource(R.string.staff_reservations), onClick = phase2.onReservations)
            } else if (features.staffCirculationAvailable) {
                // The desk exists but the device is offline: say why it is not here.
                Text(
                    stringResource(R.string.staff_offline_circulation_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }

            FormSectionTitle(stringResource(R.string.staff_section_add))
            StaffActionRow(Icons.Outlined.QrCodeScanner, stringResource(R.string.staff_scan), stringResource(R.string.staff_scan_subtitle), onScan)
            StaffActionRow(Icons.Outlined.PostAdd, stringResource(R.string.staff_add_manual), stringResource(R.string.staff_add_manual_subtitle), onAddManual)

            FormSectionTitle(stringResource(R.string.staff_section_manage))
            StaffActionRow(Icons.AutoMirrored.Outlined.LibraryBooks, stringResource(R.string.staff_books), onClick = onBooks)
            StaffActionRow(Icons.Outlined.Person, stringResource(R.string.staff_authors), onClick = onAuthors)
            StaffActionRow(Icons.Outlined.Business, stringResource(R.string.staff_publishers), onClick = onPublishers)
            StaffActionRow(Icons.Outlined.Category, stringResource(R.string.staff_genres), onClick = onGenres)
            StaffActionRow(Icons.Outlined.Inventory2, stringResource(R.string.staff_shelves), onClick = onShelves)

            if (areas.anyBooksTool) {
                FormSectionTitle(stringResource(R.string.staff_section_books))
                if (areas.series) StaffActionRow(Icons.Outlined.CollectionsBookmark, stringResource(R.string.staff_series), onClick = phase2.onSeries)
                if (areas.merges) StaffActionRow(Icons.AutoMirrored.Outlined.MergeType, stringResource(R.string.staff_merges),
                    stringResource(R.string.staff_merges_subtitle), phase2.onMerges)
                if (areas.imports) StaffActionRow(Icons.Outlined.UploadFile, stringResource(R.string.staff_imports),
                    stringResource(R.string.staff_imports_subtitle), phase2.onImports)
                if (areas.covers) StaffActionRow(Icons.Outlined.AutoAwesome, stringResource(R.string.staff_covers),
                    stringResource(R.string.staff_covers_subtitle), phase2.onCovers)
                if (areas.labels) StaffActionRow(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.staff_labels_settings),
                    stringResource(R.string.staff_labels_settings_subtitle)) { labelsOpen = true }
            }

            if (areas.anyPlugin) {
                FormSectionTitle(stringResource(R.string.staff_section_plugins))
                if (areas.desiderata) StaffActionRow(Icons.Outlined.VolunteerActivism, stringResource(R.string.staff_desiderata),
                    stringResource(R.string.staff_desiderata_subtitle), phase2.onDesiderata)
                if (areas.archives) {
                    StaffActionRow(Icons.Outlined.Archive, stringResource(R.string.staff_archives), stringResource(R.string.staff_archives_subtitle), phase2.onArchives)
                    StaffActionRow(Icons.Outlined.Person, stringResource(R.string.staff_authorities), onClick = phase2.onAuthorities)
                }
                if (areas.periodicals) StaffActionRow(Icons.Outlined.Newspaper, stringResource(R.string.staff_periodicals),
                    stringResource(R.string.staff_periodicals_subtitle), phase2.onPeriodicals)
                if (areas.bookClub) StaffActionRow(Icons.Outlined.Groups, stringResource(R.string.staff_clubs), stringResource(R.string.staff_clubs_subtitle), phase2.onClubs)
                if (areas.works) StaffActionRow(Icons.Outlined.AccountTree, stringResource(R.string.staff_works), stringResource(R.string.staff_works_subtitle), phase2.onWorks)
            }
        }
    }

    if (labelsOpen && areas.labels) StaffLabelsSettingsSheet(onDismiss = { labelsOpen = false })
}
