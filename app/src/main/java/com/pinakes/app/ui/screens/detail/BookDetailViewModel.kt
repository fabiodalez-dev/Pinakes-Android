package com.pinakes.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.data.repository.WishlistRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val content: UiState<BookDetail> = UiState.Loading,
    val wishlisted: Boolean = false,
    val wishlistBusy: Boolean = false,
    val reserveBusy: Boolean = false,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    // Set after a successful loan request so the UI can confirm "Loan requested for <date>".
    val requestedDate: String? = null,
    // Loan-request calendar: availability fetch lifecycle for the date-picker sheet.
    val availabilityLoading: Boolean = false,
    val availability: AvailabilityCalendar? = null,
    // True when the availability fetch failed and the picker falls back to "any future date".
    val availabilityFallback: Boolean = false,
    // When true the loan-request sheet is open.
    val showLoanSheet: Boolean = false,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val wishlist: WishlistRepository,
) : ViewModel() {

    // The book id arrives as a navigation argument; Hilt populates SavedStateHandle from it.
    private val bookId: Int = savedStateHandle.get<Int>(Routes.ARG_BOOK_ID) ?: 0

    private val _state = MutableStateFlow(BookDetailUiState())
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(content = UiState.Loading) }
        viewModelScope.launch {
            when (val res = catalog.book(bookId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        content = UiState.Success(res.data),
                        wishlisted = res.data.personalHistory?.hasWishlisted ?: it.wishlisted,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(content = UiState.Error(res.message, res.code, R.string.book_error_load))
                }
            }
        }
    }

    fun toggleWishlist() {
        if (_state.value.wishlistBusy) return
        val nowWishlisted = !_state.value.wishlisted
        _state.update { it.copy(wishlistBusy = true) }
        viewModelScope.launch {
            val res = if (nowWishlisted) wishlist.add(bookId) else wishlist.remove(bookId)
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        wishlisted = nowWishlisted,
                        wishlistBusy = false,
                        snackbar = null,
                        snackbarRes = if (nowWishlisted) R.string.snackbar_added_to_wishlist else R.string.snackbar_removed_from_wishlist,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    if (res.message.isNotBlank()) it.copy(wishlistBusy = false, snackbar = res.message, snackbarRes = null)
                    else it.copy(wishlistBusy = false, snackbar = null, snackbarRes = R.string.snackbar_wishlist_update_error)
                }
            }
        }
    }

    /**
     * Open the loan-request sheet and fetch the per-day availability calendar. On success the
     * sheet shows the colored calendar; on failure it falls back to a plain "any future date"
     * picker and flags [BookDetailUiState.availabilityFallback].
     */
    fun openLoanSheet() {
        _state.update { it.copy(showLoanSheet = true, availabilityLoading = true, availabilityFallback = false, availability = null) }
        viewModelScope.launch {
            when (val res = catalog.bookAvailability(bookId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(availabilityLoading = false, availability = res.data, availabilityFallback = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(availabilityLoading = false, availability = null, availabilityFallback = true)
                }
            }
        }
    }

    fun dismissLoanSheet() = _state.update {
        it.copy(showLoanSheet = false, availabilityLoading = false, availability = null, availabilityFallback = false)
    }

    /**
     * Submit a loan/reservation request for [desiredDate] (yyyy-MM-dd, today-or-future). The
     * backend computes the return as +1 month. On success we surface the chosen date so the UI
     * can confirm exactly what was requested.
     */
    fun reserve(desiredDate: String) {
        if (_state.value.reserveBusy) return
        _state.update { it.copy(reserveBusy = true) }
        viewModelScope.launch {
            when (val res = library.reserve(bookId, desiredDate)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            reserveBusy = false,
                            snackbar = null,
                            snackbarRes = null,
                            requestedDate = desiredDate,
                            showLoanSheet = false,
                            availability = null,
                            availabilityFallback = false,
                        )
                    }
                    load()
                }
                is ApiResult.Failure -> {
                    // Close the calendar dialog so the error is visible: the snackbar
                    // lives at the screen Scaffold level, behind the open DatePickerDialog.
                    when {
                        res.code == ErrorCodes.CONFLICT ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = null, snackbarRes = R.string.snackbar_request_conflict) }
                        res.message.isNotBlank() ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = res.message, snackbarRes = null) }
                        else ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = null, snackbarRes = R.string.snackbar_request_error) }
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    fun consumeRequestedDate() = _state.update { it.copy(requestedDate = null) }
}
