package com.pinakes.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesListTopBar
import com.pinakes.app.ui.components.PinakesTab
import com.pinakes.app.ui.components.PinakesBottomBar
import com.pinakes.app.ui.components.visibleNavTabs
import com.pinakes.app.ui.screens.home.HomeScreen
import com.pinakes.app.ui.screens.library.LibraryScreen
import com.pinakes.app.ui.screens.profile.ProfileScreen
import com.pinakes.app.ui.screens.search.SearchScreen
import com.pinakes.app.ui.screens.wishlist.WishlistScreen

/**
 * Hosts the four bottom-nav destinations. Each tab keeps its own composable; switching tabs is
 * a simple state change (the per-tab ViewModels are scoped to their composable instances).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    onLoggedOut: () -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenContact: () -> Unit,
) {
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(PinakesTab.Home) }

    // If the active tab is gated off (e.g. server switched to CATALOGUE-ONLY MODE while the
    // app was open), fall back to Home so we never render a hidden/blank destination.
    val visibleTabs = visibleNavTabs(features.showLibrary, features.showWishlist)
    LaunchedEffect(visibleTabs) {
        if (tab !in visibleTabs) tab = PinakesTab.Home
    }
    // Guard against the same condition during the frame the flags change, before the effect runs.
    if (tab !in visibleTabs) tab = PinakesTab.Home

    val title = when (tab) {
        PinakesTab.Home -> stringResource(R.string.title_home)
        PinakesTab.Catalog -> stringResource(R.string.title_catalog)
        PinakesTab.Library -> stringResource(R.string.title_my_library)
        PinakesTab.Wishlist -> stringResource(R.string.title_wishlist)
        PinakesTab.Profile -> stringResource(R.string.title_profile)
    }

    Scaffold(
        topBar = {
            // Home carries its own brand banner — skip the generic list top bar there.
            if (tab != PinakesTab.Home) {
                PinakesListTopBar(
                    title = title,
                    onNotifications = if (features.notifications) onOpenNotifications else null,
                )
            }
        },
        bottomBar = {
            PinakesBottomBar(
                selectedTab = tab,
                onTabSelected = { tab = it },
                showLibrary = features.showLibrary,
                showWishlist = features.showWishlist,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                PinakesTab.Home -> HomeScreen(
                    onBookClick = onOpenBook,
                    onBrowseCatalog = { tab = PinakesTab.Catalog },
                )
                PinakesTab.Catalog -> SearchScreen(onBookClick = onOpenBook)
                PinakesTab.Library -> LibraryScreen(onBookClick = onOpenBook)
                PinakesTab.Wishlist -> WishlistScreen(onBookClick = onOpenBook)
                PinakesTab.Profile -> ProfileScreen(
                    onLoggedOut = onLoggedOut,
                    onOpenNotifications = onOpenNotifications,
                    onOpenContact = onOpenContact,
                )
            }
        }
    }
}
