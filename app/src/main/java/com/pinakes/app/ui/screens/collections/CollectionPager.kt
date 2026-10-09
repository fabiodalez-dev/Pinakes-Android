package com.pinakes.app.ui.screens.collections

import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CollectionPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionListState<T>(
    val items: List<T> = emptyList(), val nextCursor: String? = null, val total: Int? = null,
    val loading: Boolean = true, val loadingMore: Boolean = false,
    val error: ApiResult.Failure? = null, val moreError: ApiResult.Failure? = null,
)

/** Independent cursors, duplicate protection and generation checks for optional collections. */
internal class CollectionPager<F, T>(
    private val scope: CoroutineScope,
    private val load: suspend (F, String?) -> ApiResult<CollectionPage<T>>,
    private val id: (T) -> Int,
) {
    private val mutableState = MutableStateFlow(CollectionListState<T>())
    val state = mutableState.asStateFlow()
    private var generation = 0
    private var firstJob: Job? = null
    private var filters: F? = null

    fun reload(value: F, debounce: Boolean = false) {
        filters = value
        val request = ++generation
        firstJob?.cancel()
        mutableState.value = CollectionListState()
        firstJob = scope.launch {
            if (debounce) delay(350)
            val result = load(value, null)
            if (request != generation) return@launch
            when (result) {
                is ApiResult.Success -> mutableState.value = CollectionListState(result.data.items.distinctBy(id), result.data.nextCursor, result.data.total, loading = false)
                is ApiResult.Failure -> mutableState.value = CollectionListState(loading = false, error = result)
            }
        }
    }

    fun more() {
        val current = mutableState.value
        val value = filters ?: return
        if (current.loading || current.loadingMore || current.nextCursor == null) return
        val request = generation
        mutableState.update { it.copy(loadingMore = true, moreError = null) }
        scope.launch {
            val result = load(value, current.nextCursor)
            if (request != generation) return@launch
            when (result) {
                is ApiResult.Success -> mutableState.update { it.copy(
                    items = (it.items + result.data.items).distinctBy(id),
                    nextCursor = result.data.nextCursor?.takeUnless { cursor -> cursor == current.nextCursor },
                    total = result.data.total ?: it.total, loadingMore = false,
                ) }
                is ApiResult.Failure -> mutableState.update { it.copy(loadingMore = false, moreError = result) }
            }
        }
    }
}
