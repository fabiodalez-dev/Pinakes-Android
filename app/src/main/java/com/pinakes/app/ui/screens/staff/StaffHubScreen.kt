package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.theme.Spacing

/** Entry point of the staff area (Profile → Staff area). */
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
) {
    val app: AppViewModel = hiltViewModel()
    val isStaff by app.isStaff.collectAsStateWithLifecycle()
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
            FormSectionTitle(stringResource(R.string.staff_section_add))
            StaffActionRow(Icons.Outlined.QrCodeScanner, stringResource(R.string.staff_scan), stringResource(R.string.staff_scan_subtitle), onScan)
            StaffActionRow(Icons.Outlined.PostAdd, stringResource(R.string.staff_add_manual), stringResource(R.string.staff_add_manual_subtitle), onAddManual)

            FormSectionTitle(stringResource(R.string.staff_section_manage))
            StaffActionRow(Icons.AutoMirrored.Outlined.LibraryBooks, stringResource(R.string.staff_books), onClick = onBooks)
            StaffActionRow(Icons.Outlined.Person, stringResource(R.string.staff_authors), onClick = onAuthors)
            StaffActionRow(Icons.Outlined.Business, stringResource(R.string.staff_publishers), onClick = onPublishers)
            StaffActionRow(Icons.Outlined.Category, stringResource(R.string.staff_genres), onClick = onGenres)
            StaffActionRow(Icons.Outlined.Inventory2, stringResource(R.string.staff_shelves), onClick = onShelves)
        }
    }
}
