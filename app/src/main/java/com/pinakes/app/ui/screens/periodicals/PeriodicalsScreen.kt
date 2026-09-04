package com.pinakes.app.ui.screens.periodicals

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.pinakes.app.R
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.Spacing

/**
 * Emeroteca landing: searchable, type-filterable list of the library's periodical
 * mastheads, cursor-paginated with load-more on scroll (mirrors the catalog search).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicalsScreen(
    onNavigateUp: () -> Unit,
    onOpenPeriodical: (Int) -> Unit,
) {
    val vm: PeriodicalsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Infinite scroll: load the next page when nearing the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= layout.totalItemsCount - 4 && state.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.periodicals_title), onNavigateUp = onNavigateUp) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) {
                SearchField(
                    query = state.query,
                    onQueryChange = vm::onQueryChange,
                    onSearch = vm::submitSearch,
                    placeholder = stringResource(R.string.periodicals_search_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                TypeFilterRow(selected = state.type, onToggle = vm::onTypeToggled)
                Spacer(Modifier.height(Spacing.sm))
            }

            PullToRefreshBox(
                isRefreshing = state.loading && state.items.isNotEmpty(),
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.items.isEmpty() ->
                        LoadingState(label = stringResource(R.string.periodicals_loading))
                    state.error != null && state.items.isEmpty() ->
                        // Plugin deactivated server-side: a friendly terminal state, not a
                        // retryable error (the feature flag is already flipped off).
                        if (state.pluginGone) EmptyState(
                            title = stringResource(R.string.periodicals_gone_title),
                            subtitle = stringResource(R.string.periodicals_gone_subtitle),
                        ) else ErrorState(
                            message = stringResource(R.string.periodicals_error_load),
                            onRetry = vm::refresh,
                        )
                    state.items.isEmpty() -> EmptyState(
                        title = stringResource(R.string.periodicals_empty_title),
                        subtitle = stringResource(R.string.periodicals_empty_subtitle),
                        icon = Icons.Outlined.Newspaper,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.items, key = { it.id }) { p ->
                            PeriodicalCard(periodical = p, onClick = { onOpenPeriodical(p.id) })
                        }
                        if (state.loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(Spacing.lg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFilterRow(selected: String?, onToggle: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PERIODICAL_TYPES.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onToggle(type) },
                label = { Text(stringResource(periodicalTypeLabelRes(type))) },
            )
        }
    }
}

@Composable
private fun PeriodicalCard(periodical: PeriodicalSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            PeriodicalLogo(
                url = periodical.logoUrl,
                contentDescription = periodical.title,
                modifier = Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    periodical.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                periodical.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = listOfNotNull(
                    stringResource(periodicalTypeLabelRes(periodical.type)),
                    periodicalFrequencyLabelRes(periodical.frequency)?.let { stringResource(it) },
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.periodicals_years_count, periodical.yearsCount) +
                        " · " +
                        stringResource(R.string.periodicals_issues_count, periodical.issuesCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Masthead logo / issue cover thumbnail with the same placeholder styling as book covers. */
@Composable
fun PeriodicalLogo(
    url: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { PeriodicalLogoPlaceholder() },
        error = { PeriodicalLogoPlaceholder() },
    )
}

@Composable
private fun PeriodicalLogoPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Newspaper,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.height(28.dp),
        )
    }
}
