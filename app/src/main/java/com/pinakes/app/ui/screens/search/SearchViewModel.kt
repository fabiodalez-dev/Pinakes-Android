package com.pinakes.app.ui.screens.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.SearchFilters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val availableOnly: Boolean = false,
    val selectedGenreId: Int? = null,
    val author: String = "",
    val publisher: String = "",
    val language: String? = null,
    val genres: List<GenreNode> = emptyList(),
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

/** Languages offered in the filter sheet. `code` is sent to the API; `labelRes` is shown. */
data class LanguageOption(val code: String, @StringRes val labelRes: Int)

val SearchLanguageOptions: List<LanguageOption> = listOf(
    LanguageOption("ita", R.string.lang_italian),
    LanguageOption("eng", R.string.lang_english),
    LanguageOption("fra", R.string.lang_french),
    LanguageOption("deu", R.string.lang_german),
    LanguageOption("spa", R.string.lang_spanish),
    LanguageOption("lat", R.string.lang_latin),
)

class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadGenres()
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
        viewModelScope.launch {
            when (val res = catalog.search(filters(), cursor = s.nextCursor)) {
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
        _state.update { it.copy(loading = true, error = null, items = if (reset) emptyList() else it.items, nextCursor = null) }
        viewModelScope.launch {
            when (val res = catalog.search(filters())) {
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

    class Factory(private val catalog: CatalogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(catalog) as T
    }
}
