package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StandaloneArticlesSource
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StandaloneArticlesUiState(
    val query: String = "",
    val items: List<StandaloneArticle> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val moreError: Boolean = false,
    val unavailable: Boolean = false,
)

@HiltViewModel
class StandaloneArticlesViewModel @Inject constructor(
    private val source: StandaloneArticlesSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mastheadId = savedStateHandle.get<Int>(Routes.ARG_PERIODICAL_ID)?.takeIf { it > 0 }
    private val mutableState = MutableStateFlow(StandaloneArticlesUiState())
    val state = mutableState.asStateFlow()
    private var supported: Boolean? = null
    private var generation = 0
    private var searchJob: Job? = null

    init { refresh() }

    fun onQueryChange(query: String) {
        mutableState.update { it.copy(query = query) }
        reload(debounce = true)
    }

    fun refresh() = reload(debounce = false)

    private fun reload(debounce: Boolean) {
        // Invalidate immediately, before debounce: an older response must never display
        // underneath the newly typed query, even if cancellation is swallowed by transport.
        val request = ++generation
        searchJob?.cancel()
        mutableState.update {
            it.copy(items = emptyList(), nextCursor = null, loading = true,
                loadingMore = false, error = false, moreError = false, unavailable = false)
        }
        val query = mutableState.value.query.takeIf { it.isNotBlank() }
        searchJob = viewModelScope.launch {
            if (debounce) delay(350)
            if (!debounce || supported == null) {
                val capability = source.standaloneArticlesSupported()
                if (request != generation) return@launch
                if (capability != null) supported = capability
            }
            if (request != generation) return@launch
            if (supported == false) {
                mutableState.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            val result = source.articles(query, mastheadId)
            if (request != generation) return@launch
            when (result) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(items = result.data.items.distinctBy { article -> article.id },
                        nextCursor = result.data.nextCursor, loading = false)
                }
                is ApiResult.Failure -> {
                    val gone = isNotFoundFailure(result) && source.confirmGone()
                    if (request != generation) return@launch
                    mutableState.update { it.copy(loading = false, error = !gone, unavailable = gone) }
                }
            }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.loadingMore || current.nextCursor == null) return
        val request = generation
        mutableState.update { it.copy(loadingMore = true, moreError = false) }
        viewModelScope.launch {
            val result = source.articles(current.query.takeIf { it.isNotBlank() }, mastheadId, current.nextCursor)
            if (request != generation) return@launch
            when (result) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(items = (it.items + result.data.items).distinctBy { article -> article.id },
                        nextCursor = result.data.nextCursor?.takeUnless { cursor -> cursor == current.nextCursor },
                        loadingMore = false)
                }
                is ApiResult.Failure -> mutableState.update { it.copy(loadingMore = false, moreError = true) }
            }
        }
    }
}
