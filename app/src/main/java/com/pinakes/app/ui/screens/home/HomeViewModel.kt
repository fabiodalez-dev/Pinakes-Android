package com.pinakes.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.SearchFilters
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Landing-screen state: the library name plus the "available now" shelf. */
data class HomeUiState(
    val libraryName: String? = null,
    val available: List<BookSummary> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && available.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val session: SessionStore,
    private val features: FeatureStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(libraryName = session.libraryName?.takeIf { it.isNotBlank() })
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * True once a server-side `available=true` page has been rendered this session. From
     * that point the shelf is authoritative and cache emissions must not overwrite it —
     * the cache only holds the first unfiltered page, a subset of the real answer.
     */
    private var hasFreshShelf = false

    init {
        observeCache()
        observeCatalogueMode()
    }

    /**
     * `catalogueMode` alone, de-duplicated, so downstream only reacts when the lending↔catalogue
     * flag actually flips — not on every unrelated `/health` emission that leaves it unchanged.
     */
    private fun catalogueMode(): Flow<Boolean> =
        features.features.map { it.catalogueMode }.distinctUntilChanged()

    /**
     * Drive [refresh] off [catalogueMode]: once on the initial (persisted) value, then again each
     * time the flag flips. This is what makes the shelf reactive — on a cold start `/health` can
     * resolve AFTER the first refresh, and a mode switch can land at any time; either way the
     * server-side shelf query (availableOnly vs unfiltered) is re-driven to match. [collectLatest]
     * abandons a stale re-drive when the flag flips again mid-flight.
     */
    private fun observeCatalogueMode() {
        viewModelScope.launch {
            catalogueMode().collectLatest { refresh() }
        }
    }

    /**
     * Offline fallback: render the home shelf from the locally-cached catalog snapshot
     * immediately. Loan mode keeps the "Available now" shelf filtered to currently-loanable
     * copies; catalogue-only mode labels the shelf "Recently added", so the cache fallback
     * must stay unfiltered. Only a partial answer — the cache holds the first unfiltered page —
     * so it is superseded as soon as [refresh] gets the server-side query through.
     */
    private fun observeCache() {
        viewModelScope.launch {
            // Re-run the filter whenever EITHER the cache OR catalogueMode changes, so a mode flip
            // that lands before the first server-side shelf resolves still re-labels/re-filters the
            // offline fallback instead of leaving it wrong until [refresh] gets through.
            combine(
                catalog.observeCachedCatalog(),
                catalogueMode(),
            ) { books, catalogueMode -> books to catalogueMode }.collectLatest { (books, catalogueMode) ->
                if (hasFreshShelf) return@collectLatest
                val shelfBooks =
                    if (catalogueMode) books
                    else books.filter { it.available }
                _state.update {
                    it.copy(
                        available = shelfBooks,
                        // Keep the loading skeleton until the first [refresh]
                        // resolves when the cache is still empty (cold start):
                        // an empty first emission must not flash the
                        // "empty library" state while the network is in flight.
                        // A non-empty cache clears loading immediately.
                        loading = if (books.isEmpty()) it.loading else false,
                        error = if (books.isEmpty()) it.error else null,
                    )
                }
            }
        }
    }

    /**
     * Refresh the shelf and the offline cache concurrently. The shelf is the server-side
     * `available=true` query (the catalog can hold hundreds of titles whose newest page is
     * fully on loan — only the server knows what's loanable now); the unfiltered first
     * page keeps feeding the Room cache for offline starts. A shelf failure only surfaces
     * an error when there is nothing cached to fall back on.
     */
    fun refresh() {
        viewModelScope.launch {
            // Catalogue-only mode labels the shelf "Recently added / latest additions", so
            // query the newest titles unfiltered (no availability filter) to make the label
            // honest. Loan mode keeps the availability filter behind the "Available now" shelf.
            // Both lean on the server's default newest-first sort.
            val shelfFilters =
                if (features.features.value.catalogueMode) SearchFilters()
                else SearchFilters(availableOnly = true)
            val shelf = async { catalog.search(shelfFilters, limit = SHELF_LIMIT) }
            launch { catalog.refreshCatalog() }
            when (val res = shelf.await()) {
                is ApiResult.Success -> {
                    hasFreshShelf = true
                    _state.update {
                        it.copy(available = res.data.items, loading = false, error = null)
                    }
                }
                is ApiResult.Failure -> {
                    val hasCache = catalog.hasCachedCatalog()
                    _state.update {
                        it.copy(
                            loading = false,
                            error = if (hasCache) null else res.message.takeIf { m -> m.isNotBlank() },
                        )
                    }
                }
            }
        }
    }

    fun retry() = refresh()

    private companion object {
        /** Server page cap is 50; 40 matches the cached-catalog page size. */
        const val SHELF_LIMIT = 40
    }
}
