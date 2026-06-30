package com.pinakes.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.store.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    init {
        observeCache()
        refresh()
    }

    /**
     * Offline-first: render the "Available now" shelf from the locally-cached catalog
     * snapshot immediately (works with no network), filtering to currently-loanable
     * copies. The shelf updates automatically when [refresh] replaces the cache.
     */
    private fun observeCache() {
        viewModelScope.launch {
            catalog.observeCachedCatalog().collectLatest { books ->
                val available = books.filter { it.available }
                _state.update { it.copy(available = available, loading = false, error = null) }
            }
        }
    }

    /**
     * Pull a fresh catalog snapshot from the network into the cache. Called on init and
     * on every app foreground. A failure only surfaces an error when there is nothing
     * cached to show — otherwise the cached catalog stays on screen.
     */
    fun refresh() {
        viewModelScope.launch {
            when (val res = catalog.refreshCatalog()) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, error = null) }
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
}
