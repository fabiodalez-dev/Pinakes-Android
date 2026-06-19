package com.pinakes.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.SearchFilters
import com.pinakes.app.data.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(libraryName = session.libraryName?.takeIf { it.isNotBlank() })
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // "Available now" shelf: full catalog filtered to currently-loanable copies.
            when (val res = catalog.search(SearchFilters(availableOnly = true), limit = 20)) {
                is ApiResult.Success -> _state.update {
                    it.copy(available = res.data.items, loading = false, error = null)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = res.message.takeIf { m -> m.isNotBlank() })
                }
            }
        }
    }

    fun retry() = load()

    class Factory(
        private val catalog: CatalogRepository,
        private val session: SessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(catalog, session) as T
    }
}
