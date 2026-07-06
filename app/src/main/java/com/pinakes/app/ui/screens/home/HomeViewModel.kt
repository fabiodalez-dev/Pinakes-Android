package com.pinakes.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.SearchFilters
import com.pinakes.app.data.store.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
        refresh()
    }

    /**
     * Offline fallback: render the "Available now" shelf from the locally-cached catalog
     * snapshot immediately (works with no network), filtering to currently-loanable
     * copies. Only a partial answer — the cache holds the first unfiltered page — so it is
     * superseded as soon as [refresh] gets the server-side availability query through.
     */
    private fun observeCache() {
        viewModelScope.launch {
            catalog.observeCachedCatalog().collectLatest { books ->
                if (hasFreshShelf) return@collectLatest
                val available = books.filter { it.available }
                _state.update {
                    it.copy(
                        available = available,
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
            val shelf = async { catalog.search(SearchFilters(availableOnly = true), limit = SHELF_LIMIT) }
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
