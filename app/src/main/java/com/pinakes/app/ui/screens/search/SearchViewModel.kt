package com.pinakes.app.ui.screens.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.data.model.LanguageValue
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Catalog sort order. `apiValue` is the exact `sort` query param the backend accepts on
 * `/catalog/search`; `labelRes` is the localized label shown in the sort picker. Author
 * sorts are intentionally absent — the backend does not support them.
 */
enum class BookSort(val apiValue: String, @StringRes val labelRes: Int) {
    NEWEST("newest", R.string.sort_newest),
    OLDEST("oldest", R.string.sort_oldest),
    TITLE_ASC("title_asc", R.string.sort_title_asc),
    TITLE_DESC("title_desc", R.string.sort_title_desc),
}

data class SearchUiState(
    val query: String = "",
    val availableOnly: Boolean = false,
    val selectedGenreId: Int? = null,
    val author: String = "",
    val publisher: String = "",
    val language: String? = null,
    val sort: BookSort = BookSort.NEWEST,
    val genres: List<GenreNode> = emptyList(),
    val languages: List<LanguageValue> = emptyList(),
    val items: List<BookSummary> = emptyList(),
    val nextCursor: String? = null,
    val totalCount: Int? = null,
    val loading: Boolean = false,      // first page
    val loadingMore: Boolean = false,  // pagination
    val error: String? = null,
    val filtersOpen: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null

    /** How many facet filters (excluding the free-text query) are currently active. */
    val activeFilterCount: Int
        get() = listOf(
            availableOnly,
            selectedGenreId != null,
            author.isNotBlank(),
            publisher.isNotBlank(),
            language != null,
        ).count { it }

    val hasActiveFilters: Boolean get() = activeFilterCount > 0

    val isInitial: Boolean
        get() = query.isBlank() && !hasActiveFilters && items.isEmpty() && !loading && error == null
}

@HiltViewModel
class SearchViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Monotonic request generation. Bumped on every reset ([runSearch] with `reset = true`,
     * i.e. a new query/sort/filter set). Network coroutines capture the generation at launch
     * and discard their result if it changed meanwhile, so a slow [loadMore] or debounced
     * search that resolves AFTER a sort/filter reset can't append a stale-sort page or restore
     * a mismatched cursor.
     */
    private var searchGeneration = 0

    init {
        loadGenres()
        loadLanguages()
        // Catalog lands on the full listing — browse-all (empty query, no filters).
        runSearch(reset = true)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            when (val res = catalog.genres()) {
                is ApiResult.Success -> _state.update { it.copy(genres = res.data) }
                is ApiResult.Failure -> { /* non-fatal: filters still usable without genre tree */ }
            }
        }
    }

    // #282: populate the language filter from the real catalogue values instead
    // of a hardcoded ISO-code list that never matched the free-text libri.lingua.
    private fun loadLanguages() {
        viewModelScope.launch {
            when (val res = catalog.languages()) {
                is ApiResult.Success -> _state.update { it.copy(languages = res.data) }
                is ApiResult.Failure -> { /* non-fatal: filter simply has no language chips */ }
            }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        // Debounced auto-search as the user types.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(reset = true)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        runSearch(reset = true)
    }

    fun toggleFilters() = _state.update { it.copy(filtersOpen = !it.filtersOpen) }

    // --- Draft setters for the filter sheet (no search until "Applica") ---

    fun setAuthorDraft(value: String) = _state.update { it.copy(author = value) }

    fun setPublisherDraft(value: String) = _state.update { it.copy(publisher = value) }

    fun setLanguageDraft(code: String?) = _state.update { it.copy(language = code) }

    fun setAvailableOnlyDraft(value: Boolean) = _state.update { it.copy(availableOnly = value) }

    fun setGenreDraft(id: Int?) = _state.update { it.copy(selectedGenreId = id) }

    /**
     * Change the catalog sort order. The cursor is sort-specific, so a change resets
     * pagination and reloads from the first page. No-op if the order is unchanged.
     */
    fun setSort(sort: BookSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        runSearch(reset = true)
    }

    /** Run the search with the currently staged facet filters. */
    fun applyFilters() = runSearch(reset = true)

    /** Clear every facet filter (keeps the free-text query) and re-run. */
    fun clearFilters() {
        _state.update {
            it.copy(
                availableOnly = false,
                selectedGenreId = null,
                author = "",
                publisher = "",
                language = null,
            )
        }
        runSearch(reset = true)
    }

    fun retry() = runSearch(reset = true)

    private fun filters(): SearchFilters = _state.value.let {
        SearchFilters(
            query = it.query.takeIf { q -> q.isNotBlank() },
            author = it.author.takeIf { a -> a.isNotBlank() },
            publisher = it.publisher.takeIf { p -> p.isNotBlank() },
            genreId = it.selectedGenreId,
            language = it.language,
            availableOnly = if (it.availableOnly) true else null,
        )
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.loading || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        val generation = searchGeneration
        viewModelScope.launch {
            val res = catalog.search(filters(), cursor = s.nextCursor, sort = s.sort.apiValue)
            // A sort/filter reset superseded this page mid-flight: drop it so we neither append
            // stale-sort items nor overwrite the fresh cursor. The reset already cleared loadingMore.
            if (generation != searchGeneration) return@launch
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = it.items + res.data.items,
                        nextCursor = res.data.nextCursor,
                        totalCount = res.data.totalCount ?: it.totalCount,
                        loadingMore = false,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun runSearch(reset: Boolean) {
        if (reset) searchGeneration++
        val generation = searchGeneration
        _state.update {
            it.copy(
                loading = true,
                error = null,
                items = if (reset) emptyList() else it.items,
                nextCursor = null,
                // A reset also cancels any in-flight pagination so a stale loadMore can't leave
                // the spinner stuck (its result is dropped by the generation guard).
                loadingMore = if (reset) false else it.loadingMore,
            )
        }
        val sort = _state.value.sort.apiValue
        viewModelScope.launch {
            val res = catalog.search(filters(), sort = sort)
            // Discard if a newer reset (query/sort/filter change) has since superseded this run.
            if (generation != searchGeneration) return@launch
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = res.data.items,
                        nextCursor = res.data.nextCursor,
                        totalCount = res.data.totalCount,
                        loading = false,
                        error = null,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    // null when the server message is blank → the screen shows the localized fallback.
                    it.copy(loading = false, error = res.message.takeIf { m -> m.isNotBlank() })
                }
            }
        }
    }
}
