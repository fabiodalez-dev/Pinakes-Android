package com.pinakes.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.pinakes.app.R

enum class PinakesTab {
    Home, Catalog, Library, Wishlist, Profile
}

private data class NavItem(
    val tab: PinakesTab,
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val homeNav = NavItem(PinakesTab.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)
private val catalogNav = NavItem(PinakesTab.Catalog, R.string.nav_catalog, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook)
private val libraryNav = NavItem(PinakesTab.Library, R.string.nav_library, Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks)
private val wishlistNav = NavItem(PinakesTab.Wishlist, R.string.nav_wishlist, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder)
private val profileNav = NavItem(PinakesTab.Profile, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.PersonOutline)

/**
 * Bottom-nav items visible for the given feature flags. Home / Catalog / Profile are always
 * present. Library is shown only when loans or reservations are enabled; Wishlist only when the
 * wishlist feature is enabled (CATALOGUE-ONLY MODE hides both).
 */
fun visibleNavTabs(showLibrary: Boolean, showWishlist: Boolean): List<PinakesTab> = buildList {
    add(PinakesTab.Home)
    add(PinakesTab.Catalog)
    if (showLibrary) add(PinakesTab.Library)
    if (showWishlist) add(PinakesTab.Wishlist)
    add(PinakesTab.Profile)
}

@Composable
fun PinakesBottomBar(
    selectedTab: PinakesTab,
    onTabSelected: (PinakesTab) -> Unit,
    wishlistBadge: Int = 0,
    notificationBadge: Int = 0,
    showLibrary: Boolean = true,
    showWishlist: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        add(homeNav)
        add(catalogNav)
        if (showLibrary) add(libraryNav)
        if (showWishlist) add(wishlistNav)
        add(profileNav)
    }
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        items.forEach { item ->
            val selected = item.tab == selectedTab
            val label = stringResource(item.labelRes)
            val badge = when (item.tab) {
                PinakesTab.Wishlist -> wishlistBadge
                PinakesTab.Profile  -> notificationBadge
                else                -> 0
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    if (badge > 0) {
                        BadgedBox(badge = {
                            Badge { Text(badge.toString()) }
                        }) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = label,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = label,
                        )
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    // Selected = magenta (DESIGN.md). Never the secondary/mauve tint.
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
