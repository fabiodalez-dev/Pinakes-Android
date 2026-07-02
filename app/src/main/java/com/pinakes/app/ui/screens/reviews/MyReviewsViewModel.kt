package com.pinakes.app.ui.screens.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.MyReview
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.ReviewsRepository
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyReviewsUiState(
    val content: UiState<List<MyReview>> = UiState.Loading,
    val refreshing: Boolean = false,
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/** The user's own reviews across all books, cursor-paginated (load-more). */
@HiltViewModel
class MyReviewsViewModel @Inject constructor(
    private val reviews: ReviewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyReviewsUiState())
    val state: StateFlow<MyReviewsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(content = UiState.Loading) }
        fetchFirstPage()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        fetchFirstPage()
    }

    private fun fetchFirstPage() {
        viewModelScope.launch {
            when (val res = reviews.myReviews()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        content = UiState.Success(res.data.items),
                        nextCursor = res.data.nextCursor,
                        refreshing = false,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    // Keep any already-loaded list on a refresh failure; otherwise show the error.
                    if (it.content is UiState.Success) it.copy(refreshing = false)
                    else it.copy(content = UiState.Error(res.message, res.code, R.string.reviews_error_load), refreshing = false)
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        val current = (s.content as? UiState.Success)?.data ?: return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val res = reviews.myReviews(cursor = cursor)) {
                is ApiResult.Success -> _state.update {
                    // Dedup by id on append: a cursor overlap (or a review that
                    // moved pages between requests) must not surface the same
                    // row twice — LazyColumn keys on id would then crash.
                    val seen = current.mapTo(HashSet()) { r -> r.id }
                    val merged = current + res.data.items.filter { r -> seen.add(r.id) }
                    it.copy(
                        content = UiState.Success(merged),
                        nextCursor = res.data.nextCursor,
                        loadingMore = false,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
