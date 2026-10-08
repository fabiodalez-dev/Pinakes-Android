package com.pinakes.app.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pinakes.app.ui.screens.search.BookSort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.BookCardGrid
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.HeroStyle
import com.pinakes.app.ui.theme.LocalPinakesColors
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
import androidx.compose.material.icons.outlined.Search
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.ui.common.AppViewModel
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
    onSearch: (String) -> Unit = { onBrowseCatalog() },
    onOpenPeriodicals: (() -> Unit)? = null,
    onOpenArticles: (() -> Unit)? = null,
    onOpenDesiderata: (() -> Unit)? = null,
    onOpenArchives: (() -> Unit)? = null,
) {
    // Feature flags come from the Hilt-provided AppViewModel; the screen ViewModel is also
    // created by Hilt via hiltViewModel() instead of a hand-written ViewModelProvider.Factory.
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, features.catalogueMode, onBookClick, onBrowseCatalog, onSearch, vm::retry, onOpenPeriodicals, onOpenArticles, onOpenDesiderata, onOpenArchives, vm::setSort)
}

/** Query state belongs above the phase transition, so loading cannot clear a draft. */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    catalogueMode: Boolean,
    onBookClick: (Int) -> Unit,
    onBrowseCatalog: () -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenPeriodicals: (() -> Unit)? = null,
    onOpenArticles: (() -> Unit)? = null,
    onOpenDesiderata: (() -> Unit)? = null,
    onOpenArchives: (() -> Unit)? = null,
    onSort: (BookSort) -> Unit = {},
) {
    var heroQuery by rememberSaveable { mutableStateOf("") }
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
            HomePhase.Loading -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode, books = state.available, onBookClick = onBookClick, onSearch = onSearch, query = heroQuery, onQueryChange = { heroQuery = it })
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader(showSeeAll = false, catalogueMode = catalogueMode, onSeeAll = {})
                    Spacer(Modifier.height(Spacing.md))
                    repeat(4) {
                        BookCardSkeleton()
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }

            HomePhase.Error -> ErrorState(
                message = state.error ?: stringResource(R.string.home_error),
                onRetry = onRetry,
            )

            HomePhase.Empty -> LazyColumn(Modifier.fillMaxSize()) {
                item { HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode, books = state.available,
                    onBookClick = onBookClick, onSearch = onSearch, query = heroQuery, onQueryChange = { heroQuery = it }) }
                item { CollectionDestinations(onOpenPeriodicals, onOpenDesiderata, onOpenArchives, onOpenArticles) }
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title), subtitle = stringResource(R.string.home_empty_subtitle),
                        icon = Icons.AutoMirrored.Outlined.MenuBook, actionLabel = stringResource(R.string.home_browse_catalog),
                        onAction = onBrowseCatalog,
                    )
                }
            }

            HomePhase.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { HomeHeader(libraryName = state.libraryName, catalogueMode = catalogueMode, books = state.available, onBookClick = onBookClick, onSearch = onSearch, query = heroQuery, onQueryChange = { heroQuery = it }) }
                item { CollectionDestinations(onOpenPeriodicals, onOpenDesiderata, onOpenArchives, onOpenArticles) }
                item {
                    Box(Modifier.padding(horizontal = Spacing.lg)) {
                        SectionHeader(showSeeAll = true, catalogueMode = catalogueMode, onSeeAll = onBrowseCatalog, sort = state.sort, onSort = onSort)
                    }
                }
                items(state.available.chunked(2), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                    Row(Modifier.padding(horizontal = Spacing.lg, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { book ->
                            BookCardGrid(title = book.title, author = book.authorsLabel, coverUrl = book.coverUrl,
                                status = book.availabilityStatus(), subtitle = book.subtitle, publisher = book.publisher,
                                year = book.year?.toString(), mediaType = book.mediaType,
                                onClick = { onBookClick(book.id) }, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Box(Modifier.padding(Spacing.lg)) {
                        PrimaryButton(
                            label = stringResource(R.string.home_browse_catalog),
                            onClick = onBrowseCatalog,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = Icons.Outlined.AutoStories,
                            dark = true,
                        )
                    }
                }
            }
        }
    }
}

private enum class HomePhase { Loading, Error, Empty, Content }

/** The library identity and the real shelf covers, never a decorative stock photo. */
@Composable
private fun HomeHeader(libraryName: String?, catalogueMode: Boolean, books: List<BookSummary>,
    onBookClick: (Int) -> Unit, onSearch: (String) -> Unit, query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalPinakesColors.current
    val centered = colors.heroStyle == HeroStyle.Centered
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.accentSofter, colors.background)))
        .padding(horizontal = Spacing.lg, vertical = Spacing.xxl),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Spacer(Modifier.height(12.dp))
        val name = libraryName ?: stringResource(R.string.app_name)
        val lastSpace = name.lastIndexOf(' ')
        Text(buildAnnotatedString {
            if (lastSpace >= 0) append(name.substring(0, lastSpace + 1))
            withStyle(SpanStyle(color = colors.accentText, fontStyle = FontStyle.Italic)) { append(name.substring(lastSpace + 1)) }
        }, style = MaterialTheme.typography.displaySmall,
            textAlign = if (centered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start)
        Spacer(Modifier.height(12.dp))
        Text(if (catalogueMode) stringResource(R.string.home_subtitle_catalogue) else stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        if (catalogueMode) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_browse_only_note), style = MaterialTheme.typography.labelMedium, color = colors.accentText)
        }
        Spacer(Modifier.height(24.dp))
        SearchField(query, onQueryChange, Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.restyle_search_catalog), onSearch = { onSearch(query) })
        Spacer(Modifier.height(8.dp))
        PrimaryButton(stringResource(R.string.cd_search), { onSearch(query) }, modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Outlined.Search)
        if (!centered) {
            val fan = books.filter { com.pinakes.app.ui.components.bookCoverImageUrl(it.coverUrl) != null }.take(4)
            if (fan.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(210.dp).padding(top = 28.dp), contentAlignment = Alignment.Center) {
                    fan.forEachIndexed { index, book ->
                        val position = index - (fan.size - 1) / 2f
                        BookCover(book.title, book.coverUrl, Modifier.width(96.dp).height(144.dp)
                            .offset(x = (position * 58).dp, y = (kotlin.math.abs(position) * 7).dp)
                            .graphicsLayer { rotationZ = position * 8 }
                            .clickable { onBookClick(book.id) }, author = book.authorsLabel, publisher = book.publisher)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(showSeeAll: Boolean, catalogueMode: Boolean, onSeeAll: () -> Unit, sort: BookSort = BookSort.NEWEST, onSort: (BookSort) -> Unit = {}) {
    var choosingSort by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // In CATALOGUE-ONLY MODE the shelf isn't about borrowing — it's the newest
                // titles added to the library, so label it "Recently added" to match.
                text = if (sort != BookSort.NEWEST) stringResource(sort.labelRes) else if (catalogueMode) stringResource(R.string.home_section_recent)
                else stringResource(R.string.home_section_available),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box {
                androidx.compose.material3.TextButton({ choosingSort = true }) { Text(stringResource(R.string.sort_label, stringResource(sort.labelRes))) }
                DropdownMenu(choosingSort, { choosingSort = false }) {
                    listOf(BookSort.NEWEST, BookSort.TITLE_ASC, BookSort.AUTHOR_ASC).forEach { value ->
                        DropdownMenuItem(text = { Text(stringResource(value.labelRes)) }, onClick = { choosingSort = false; onSort(value) })
                    }
                }
            }
            Text(
                text = if (catalogueMode) stringResource(R.string.home_section_recent_subtitle)
                else stringResource(R.string.home_section_available_subtitle),
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

@Composable
private fun CollectionDestinations(periodicals: (() -> Unit)?, desiderata: (() -> Unit)?, archives: (() -> Unit)?, articles: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        articles?.let { androidx.compose.material3.TextButton(it) { Text(stringResource(R.string.standalone_articles_title)) } }
        periodicals?.let { androidx.compose.material3.TextButton(it) { Text(stringResource(R.string.periodicals_title)) } }
        desiderata?.let { androidx.compose.material3.TextButton(it) { Text(stringResource(R.string.desiderata_title)) } }
        archives?.let { androidx.compose.material3.TextButton(it) { Text(stringResource(R.string.archives_title)) } }
    }
}
