package com.pinakes.app.ui.screens.search

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.screens.periodicals.StandaloneArticleRow
import com.pinakes.app.R
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.BookCardGrid
import com.pinakes.app.ui.components.CatalogViewToggle
import com.pinakes.app.ui.components.BookCard
import com.pinakes.app.ui.components.BookCardSkeleton
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBookClick: (Int) -> Unit, initialQuery: String? = null, onQueryConsumed: () -> Unit = {},
    onArticleClick: (Int) -> Unit = {}, onWantedClick: (Int) -> Unit = {}, onArchiveClick: (Int) -> Unit = {}, initialAuthor: String? = null, initialAuthorId: Int? = null) {
    val vm: SearchViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()
    val collectionVm: CollectionSearchViewModel = hiltViewModel()
    val articles by collectionVm.articleState.collectAsStateWithLifecycle()
    val wanted by collectionVm.wantedState.collectAsStateWithLifecycle()
    val archives by collectionVm.archiveState.collectAsStateWithLifecycle()
    val showArticles by collectionVm.showArticles.collectAsStateWithLifecycle()
    val showWanted by collectionVm.showWanted.collectAsStateWithLifecycle()
    val showArchives by collectionVm.showArchives.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(state.searchRevision, features) { collectionVm.search(state.appliedFilters, features) }
    androidx.compose.runtime.LaunchedEffect(initialAuthor) { initialAuthor?.let { vm.setAuthorIdentity(it, initialAuthorId?.takeIf { id -> id > 0 }); vm.applyFilters() } }
    val otherResults = (showArticles && (articles.items.isNotEmpty() || articles.error != null)) || (showWanted && (wanted.items.isNotEmpty() || wanted.error != null)) || (showArchives && (archives.items.isNotEmpty() || archives.error != null))
    val otherLoading = (showArticles && articles.loading) || (showWanted && wanted.loading) || (showArchives && archives.loading)
    var gridView by rememberSaveable { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        if (initialQuery != null) { vm.onQueryChange(initialQuery); vm.submitSearch(); onQueryConsumed() }
    }
    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Infinite scroll: load the next page when nearing the end.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= layout.totalItemsCount - 4 && state.hasMore
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    if (state.filtersOpen) {
        SearchFilterSheet(
            state = state,
            sheetState = sheetState,
            onAvailableChange = vm::setAvailableOnlyDraft,
            onGenreChange = vm::setGenreDraft,
            onAuthorChange = vm::setAuthorDraft,
            onPublisherChange = vm::setPublisherDraft,
            onLanguageChange = vm::setLanguageDraft,
            onApply = {
                vm.applyFilters()
                vm.toggleFilters()
            },
            onClear = vm::clearFilters,
            onDismiss = vm::toggleFilters,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = state.query,
                    onQueryChange = vm::onQueryChange,
                    onSearch = vm::submitSearch,
                    placeholder = stringResource(R.string.catalog_field_placeholder),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(0.dp))
                SortMenu(
                    current = state.sort,
                    query = state.query,
                    onSelect = vm::setSort,
                )
                BadgedBox(
                    badge = {
                        if (state.hasActiveFilters) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) { Text(state.activeFilterCount.toString()) }
                        }
                    },
                ) {
                    IconButton(onClick = vm::toggleFilters) {
                        Icon(
                            Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.cd_filters),
                            tint = if (state.hasActiveFilters)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(stringResource(R.string.sort_label, stringResource(state.sort.labelRes)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm))
        }

        Box(Modifier.fillMaxSize()) {
            // Crossfade between the high-level UI states for a smooth load→loaded swap.
            val phase = when {
                state.loading && state.items.isEmpty() && !otherResults -> SearchPhase.Loading
                state.error != null && state.items.isEmpty() && !otherResults -> SearchPhase.Error
                state.isInitial && !otherResults && !otherLoading -> SearchPhase.Initial
                state.items.isEmpty() && !otherResults && !otherLoading -> SearchPhase.Empty
                else -> SearchPhase.Results
            }
            Crossfade(
                targetState = phase,
                animationSpec = tween(220),
                label = "search_phase",
            ) { p ->
                when (p) {
                    SearchPhase.Loading -> {
                        Column(Modifier.padding(horizontal = Spacing.lg)) {
                            repeat(6) {
                                BookCardSkeleton()
                                Spacer(Modifier.height(Spacing.md))
                            }
                        }
                    }
                    SearchPhase.Error -> {
                        ErrorState(message = state.error ?: stringResource(R.string.search_error_failed), onRetry = vm::retry)
                    }
                    SearchPhase.Initial -> {
                        EmptyState(
                            title = stringResource(R.string.search_initial_title),
                            subtitle = stringResource(R.string.search_initial_subtitle),
                            icon = Icons.Outlined.Search,
                        )
                    }
                    SearchPhase.Empty -> {
                        val browsingAll = state.query.isBlank() && !state.hasActiveFilters
                        EmptyState(
                            title = stringResource(
                                if (browsingAll) R.string.catalog_empty_title
                                else R.string.search_empty_title,
                            ),
                            subtitle = stringResource(
                                if (browsingAll) R.string.catalog_empty_subtitle
                                else R.string.search_empty_subtitle,
                            ),
                            icon = Icons.Outlined.Search,
                        )
                    }
                    SearchPhase.Results -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm,
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (gridView) 28.dp else Spacing.md),
                        ) {
                            item {
                                val browsingAll = state.query.isBlank() && !state.hasActiveFilters
                                val header = when {
                                    browsingAll -> stringResource(R.string.catalog_browsing_all)
                                    else -> state.totalCount?.let { total ->
                                        stringResource(
                                            if (total == 1) R.string.search_result_singular
                                            else R.string.search_result_plural,
                                            total,
                                        )
                                    }
                                }
                                // Surface the active sort order alongside the result count so the
                                // current order is visible without opening the sort menu.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = header.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    CatalogViewToggle(gridView, onChange = { gridView = it })
                                }
                            }
                            if (showArticles) {
                                item { Text(stringResource(R.string.standalone_articles_title), style = MaterialTheme.typography.titleMedium) }
                                if (articles.loading) item { CircularProgressIndicator(Modifier.size(20.dp)) }
                                articles.error?.let { error -> item { Text(error.message.ifBlank { stringResource(if (error.code == "upgrade_required") R.string.collection_upgrade_required else R.string.standalone_articles_error) }, color = MaterialTheme.colorScheme.error) } }
                                items(articles.items, key = { "article-${it.id}" }) { article -> StandaloneArticleRow(article) { onArticleClick(article.id) } }
                                if (articles.nextCursor != null) item { TextButton(collectionVm::moreArticles, enabled = !articles.loadingMore) { Text(stringResource(R.string.collection_more)) } }
                            }
                            if (showWanted) {
                                item { Text(stringResource(R.string.desiderata_title), style = MaterialTheme.typography.titleMedium) }
                                wanted.error?.let { error -> item { Text(error.message.ifBlank { stringResource(if (error.code == "upgrade_required") R.string.collection_upgrade_required else R.string.standalone_articles_error) }, color = MaterialTheme.colorScheme.error) } }
                                items(wanted.items, key = { "wanted-${it.id}" }) { book -> SearchCollectionRow(book.title, book.author.orEmpty(), book.coverUrl, stringResource(R.string.desiderata_wanted)) { onWantedClick(book.id) } }
                                if (wanted.nextCursor != null) item { TextButton(collectionVm::moreWanted, enabled = !wanted.loadingMore) { Text(stringResource(R.string.collection_more)) } }
                            }
                            if (showArchives) {
                                item { Text(stringResource(R.string.archives_title), style = MaterialTheme.typography.titleMedium) }
                                archives.error?.let { error -> item { Text(error.message.ifBlank { stringResource(if (error.code == "upgrade_required") R.string.collection_upgrade_required else R.string.standalone_articles_error) }, color = MaterialTheme.colorScheme.error) } }
                                items(archives.items, key = { "archive-${it.id}" }) { record -> SearchCollectionRow(record.title, record.referenceCode, record.coverUrl, record.datesLabel.orEmpty()) { onArchiveClick(record.id) } }
                                if (archives.nextCursor != null) item { TextButton(collectionVm::moreArchives, enabled = !archives.loadingMore) { Text(stringResource(R.string.collection_more)) } }
                            }
                            if (gridView) {
                                items(state.items.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        row.forEach { book ->
                                            BookCardGrid(title = book.title, author = book.authorsLabel, coverUrl = book.coverUrl,
                                                status = book.availabilityStatus(), subtitle = book.subtitle, publisher = book.publisher,
                                                year = book.year?.toString(), mediaType = book.mediaType,
                                                onClick = { onBookClick(book.id) }, modifier = Modifier.weight(1f))
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                items(state.items, key = { it.id }) { book ->
                                    BookCard(title = book.title, author = book.authorsLabel, coverUrl = book.coverUrl,
                                        status = book.availabilityStatus(), year = book.year?.toString(), publisher = book.publisher,
                                        subtitle = book.subtitle, mediaType = book.mediaType,
                                        onClick = { onBookClick(book.id) }, modifier = Modifier.animateItem())
                                }
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
}

/**
 * Sort-order picker: an icon button that opens a dropdown of the supported catalog
 * orders. The active order carries a check. Choosing an order resets pagination and reloads
 * from the first page (handled in the ViewModel); the choice survives filter changes.
 */
@Composable
private fun SortMenu(
    current: BookSort,
    query: String,
    onSelect: (BookSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Announce the active order to TalkBack as the button's state (e.g. "Sort, Newest").
    val activeSortLabel = stringResource(current.labelRes)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { stateDescription = activeSortLabel },
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.cd_sort),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookSort.entries.filter { it.isAvailableFor(query) }.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = (option == current) },
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == current) {
                        {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

private enum class SearchPhase { Loading, Error, Initial, Empty, Results }

fun BookSummary.availabilityStatus(): AvailabilityStatus =
    if (available) AvailabilityStatus.Available else AvailabilityStatus.Unavailable

@Composable
private fun SearchCollectionRow(title: String, metadata: String, cover: String?, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BookCover(title, cover, Modifier.width(64.dp).height(96.dp), compact = true)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(metadata, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
