package com.pinakes.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.BookReviews
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.ReviewsRepository
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookReviewsUiState(
    val content: UiState<BookReviews> = UiState.Loading,
    // Composer (open when the user is editing/writing their review).
    val editorOpen: Boolean = false,
    val draftRating: Int = 0,
    val draftText: String = "",
    val submitting: Boolean = false,
    val deleting: Boolean = false,
    val snackbarRes: Int? = null,
    val snackbar: String? = null,
)

/**
 * Reviews for a single book: loads the aggregate + the user's own review + other users' reviews,
 * and drives the create/edit/delete composer. Lives alongside [BookDetailViewModel] but is scoped
 * to the reviews section so book detail stays focused on catalog data.
 */
@HiltViewModel
class BookReviewsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviews: ReviewsRepository,
) : ViewModel() {

    // The book id arrives as a navigation argument; Hilt populates SavedStateHandle
    // from it (same nav backstack entry as BookDetailViewModel, so same arg).
    private val bookId: Int = savedStateHandle.get<Int>(Routes.ARG_BOOK_ID) ?: 0

    private val _state = MutableStateFlow(BookReviewsUiState())
    val state: StateFlow<BookReviewsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(content = UiState.Loading) }
        viewModelScope.launch {
            when (val res = reviews.bookReviews(bookId)) {
                is ApiResult.Success -> _state.update { it.copy(content = UiState.Success(res.data)) }
                is ApiResult.Failure -> _state.update {
                    it.copy(content = UiState.Error(res.message, res.code, R.string.reviews_error_load))
                }
            }
        }
    }

    /** Open the composer seeded with the user's existing review (edit) or empty (new). */
    fun openEditor() {
        val mine = (_state.value.content as? UiState.Success)?.data?.mine
        _state.update {
            it.copy(
                editorOpen = true,
                draftRating = mine?.rating ?: 0,
                draftText = mine?.text.orEmpty(),
            )
        }
    }

    fun dismissEditor() = _state.update { it.copy(editorOpen = false) }

    fun onDraftRating(rating: Int) = _state.update { it.copy(draftRating = rating) }

    fun onDraftText(text: String) = _state.update { it.copy(draftText = text) }

    fun submit() {
        val s = _state.value
        if (s.submitting || s.draftRating !in 1..5) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val res = reviews.submit(bookId, s.draftRating, s.draftText)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(submitting = false, editorOpen = false, snackbar = null, snackbarRes = R.string.reviews_saved)
                    }
                    load()
                }
                is ApiResult.Failure -> _state.update {
                    if (res.message.isNotBlank()) it.copy(submitting = false, snackbar = res.message, snackbarRes = null)
                    else it.copy(submitting = false, snackbar = null, snackbarRes = R.string.reviews_save_error)
                }
            }
        }
    }

    fun delete() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val res = reviews.delete(bookId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(deleting = false, editorOpen = false, snackbar = null, snackbarRes = R.string.reviews_deleted)
                    }
                    load()
                }
                is ApiResult.Failure -> _state.update {
                    if (res.message.isNotBlank()) it.copy(deleting = false, snackbar = res.message, snackbarRes = null)
                    else it.copy(deleting = false, snackbar = null, snackbarRes = R.string.reviews_save_error)
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }
}
