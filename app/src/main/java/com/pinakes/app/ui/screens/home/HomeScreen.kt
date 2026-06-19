package com.pinakes.app.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.components.BookCard
import com.pinakes.app.ui.components.BookCardSkeleton
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.search.availabilityStatus
import com.pinakes.app.ui.theme.Spacing

/**
 * Landing tab. Greets the reader and surfaces the books they can borrow *right now*, then
 * offers a clear path into the full catalog. Never shows an empty "search" placeholder.
 */
@Composable
fun HomeScreen(
    onBookClick: (Int) -> Unit,
    onBrowseCatalog: () -> Unit,
) {
    val services = LocalServices.current
    val features by services.features.features.collectAsStateWithLifecycle()
    val vm: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(services.catalogRepository, services.session)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val catalogueMode = features.catalogueMode

    Crossfade(
        targetState = when {
            state.loading -> HomePhase.Loading
            state.error != null -> HomePhase.Error
            state.isEmpty -> HomePhase.Empty
            else -> HomePhase.Content
        },
        animationSpec = tween(220),
        label = "home_phase",
    ) { phase ->
        when (phase) {
            HomePhase.Loading -> Column(Modifier.fillMaxSize()) {
                HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode)
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader(showSeeAll = false, onSeeAll = {})
                    Spacer(Modifier.height(Spacing.md))
                    repeat(4) {
                        BookCardSkeleton()
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }

            HomePhase.Error -> ErrorState(
                message = state.error ?: stringResource(R.string.home_error),
                onRetry = vm::retry,
            )

            HomePhase.Empty -> Column(Modifier.fillMaxSize()) {
                HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode)
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    actionLabel = stringResource(R.string.home_browse_catalog),
                    onAction = onBrowseCatalog,
                )
            }

            HomePhase.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode) }
                item {
                    Box(Modifier.padding(horizontal = Spacing.lg)) {
                        SectionHeader(showSeeAll = true, onSeeAll = onBrowseCatalog)
                    }
                }
                items(state.available, key = { it.id }) { book ->
                    Box(Modifier.padding(horizontal = Spacing.lg)) {
                        BookCard(
                            title = book.title,
                            author = book.authorsLabel,
                            coverUrl = book.coverUrl,
                            status = book.availabilityStatus(),
                            year = book.year?.toString(),
                            publisher = book.publisher,
                            onClick = { onBookClick(book.id) },
                        )
                    }
                }
                item {
                    Box(Modifier.padding(Spacing.lg)) {
                        PrimaryButton(
                            label = stringResource(R.string.home_browse_catalog),
                            onClick = onBrowseCatalog,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = Icons.Outlined.AutoStories,
                        )
                    }
                }
            }
        }
    }
}

private enum class HomePhase { Loading, Error, Empty, Content }

/** Minimal plain-surface header: a quiet greeting, the library name in magenta, a subtitle. */
@Composable
private fun HomeHeader(libraryName: String?, catalogueMode: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xl, bottom = Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = libraryName ?: stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            // In CATALOGUE-ONLY MODE the catalog is read-only, so avoid "borrow" wording.
            text = if (catalogueMode) stringResource(R.string.home_subtitle_catalogue)
            else stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Subtle one-time-style caption that this is a browse-only library. Minimal, no banner.
        if (catalogueMode) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.home_browse_only_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionHeader(showSeeAll: Boolean, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_section_available),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_section_available_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showSeeAll) {
            TextButton(onClick = onSeeAll) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
