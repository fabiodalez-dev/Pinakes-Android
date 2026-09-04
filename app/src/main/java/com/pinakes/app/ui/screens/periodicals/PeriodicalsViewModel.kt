package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.PeriodicalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeriodicalsUiState(
    val query: String = "",
    /** Active type filter chip (server `type` param), or null for all types. */
    val type: String? = null,
    val items: List<PeriodicalSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,       // first page
    val loadingMore: Boolean = false,  // pagination
    val error: String? = null,
    /** The plugin was deactivated server-side (confirmed via health re-probe). */
    val pluginGone: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/** Apply a query edit (pagination is reset by the reload the ViewModel triggers). */
internal fun PeriodicalsUiState.withQuery(value: String): PeriodicalsUiState =
    copy(query = value)

/** Toggle a type filter chip: tapping the active chip clears the filter. */
internal fun PeriodicalsUiState.withTypeToggled(value: String): PeriodicalsUiState =
    copy(type = if (type == value) null else value)

/**
 * Append the next cursor page, dropping any item whose id is already listed. Cursor
 * pagination can hand back a boundary row twice (an insert/delete shifts the window
 * between requests) and LazyColumn keys must stay unique.
 */
internal fun PeriodicalsUiState.appendPage(
    page: List<PeriodicalSummary>,
    cursor: String?,
): PeriodicalsUiState {
    val seen = items.mapTo(HashSet()) { it.id }
    return copy(
        items = items + page.filter { seen.add(it.id) },
        nextCursor = cursor,
        loadingMore = false,
    )
}

@HiltViewModel
class PeriodicalsViewModel @Inject constructor(
    private val repo: PeriodicalsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PeriodicalsUiState())
    val state: StateFlow<PeriodicalsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Monotonic request generation, bumped on every reset (query/type change, refresh).
     * In-flight coroutines capture it at launch and drop their result when superseded, so
     * a slow first page or loadMore can never append stale-filter rows (same pattern as
     * SearchViewModel).
     */
    private var generation = 0

    init { load(reset = true) }

    fun refresh() = load(reset = true)

    fun onQueryChange(value: String) {
        _state.update { it.withQuery(value) }
        // Debounced auto-search as the user types (mirrors the catalog search).
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load(reset = true)
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        load(reset = true)
    }

    fun onTypeToggled(value: String) {
        _state.update { it.withTypeToggled(value) }
        searchJob?.cancel()
        load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        val gen = generation
        viewModelScope.launch {
            val res = repo.periodicals(
                query = s.query.takeIf { it.isNotBlank() },
                type = s.type,
                cursor = s.nextCursor,
            )
            // A reset superseded this page mid-flight: drop it (the reset cleared loadingMore).
            if (gen != generation) return@launch
            when (res) {
                is ApiResult.Success -> _state.update { it.appendPage(res.data.items, res.data.nextCursor) }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun load(reset: Boolean) {
        if (reset) generation++
        val gen = generation
        _state.update {
            it.copy(
                loading = true,
                error = null,
                items = if (reset) emptyList() else it.items,
                nextCursor = null,
                loadingMore = false,
            )
        }
        val s = _state.value
        viewModelScope.launch {
            val res = repo.periodicals(
                query = s.query.takeIf { q -> q.isNotBlank() },
                type = s.type,
            )
            if (gen != generation) return@launch
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        items = res.data.items,
                        nextCursor = res.data.nextCursor,
                        loading = false,
                        error = null,
                    )
                }
                is ApiResult.Failure -> {
                    // 404 usually means the plugin was deactivated: confirm via the health
                    // probe (which also flips the feature flag so the Profile entry hides)
                    // and degrade to a friendly "gone" state instead of a retryable error.
                    val gone = (res.httpStatus == 404 || res.code == ErrorCodes.NOT_FOUND) &&
                        repo.confirmGone()
                    if (gen != generation) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            error = res.message.ifBlank { res.code },
                            pluginGone = gone,
                        )
                    }
                }
            }
        }
    }
}
