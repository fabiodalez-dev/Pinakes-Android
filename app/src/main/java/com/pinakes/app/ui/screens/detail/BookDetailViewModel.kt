package com.pinakes.app.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.DigitalAttachment
import com.pinakes.app.data.model.CirculationRequestKind
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.connectivity.ConnectivityObserver
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.serverMessage
import kotlinx.coroutines.flow.drop
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
    // Authoritative POST /reservations outcome: #384 may turn an apparent loan
    // request into a FIFO reservation, while auto-approval can make it ready.
    val requestConfirmation: RequestConfirmation? = null,
    // Loan-request calendar: availability fetch lifecycle for the date-picker sheet.
    val availabilityLoading: Boolean = false,
    val availability: AvailabilityCalendar? = null,
    // True when the availability fetch failed and the picker falls back to "any future date".
    val availabilityFallback: Boolean = false,
    // When true the loan-request sheet is open.
    val showLoanSheet: Boolean = false,
    /** The page comes from the offline cache (server unreachable): actions are hidden. */
    val offline: Boolean = false,
    /** When the offline copy was last synced (server time), for "last known" wording. */
    val syncedAt: String? = null,
    /** Device connectivity: a page loaded online hides its loan actions when it drops. */
    val connected: Boolean = true,
) {
    /** Cached page or no connection: no loan/reservation affordance, no current availability. */
    val offlineNow: Boolean get() = offline || !connected
}

data class RequestConfirmation(
    val desiredDate: String,
    val kind: CirculationRequestKind,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val wishlist: WishlistRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    // The book id arrives as a navigation argument; Hilt populates SavedStateHandle from it.
    private val bookId: Int = savedStateHandle.get<Int>(Routes.ARG_BOOK_ID) ?: 0

    private val _state = MutableStateFlow(BookDetailUiState(connected = connectivity.isOnline.value))
    val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            connectivity.isOnline.drop(1).collect { online ->
                // Offline: close the loan sheet at once (availability can't be guaranteed).
                _state.update { it.copy(connected = online, showLoanSheet = it.showLoanSheet && online) }
                if (online && (_state.value.offline || _state.value.content is UiState.Error)) load()
            }
        }
    }

    fun load() {
        _state.update { it.copy(content = UiState.Loading) }
        viewModelScope.launch {
            when (val res = catalog.book(bookId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        content = UiState.Success(res.data),
                        wishlisted = res.data.personalHistory?.hasWishlisted ?: it.wishlisted,
                        offline = false,
                    )
                }
                is ApiResult.Failure -> {
                    val cached = if (res.code == ErrorCodes.NETWORK) catalog.bookOffline(bookId) else null
                    val syncedAt = if (cached != null) catalog.lastSyncedAt() else null
                    _state.update {
                        if (cached != null) it.copy(content = UiState.Success(cached), offline = true, syncedAt = syncedAt)
                        else it.copy(content = UiState.Error(res.message, res.code, R.string.book_error_load))
                    }
                }
            }
        }
    }

    /** Refresh in place: a failed renewal must not replace the page or reuse a cached URL. */
    suspend fun refreshDigitalFile(file: DigitalAttachment): ApiResult<DigitalAttachment> {
        return when (val result = catalog.book(bookId, forceRefresh = true)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                _state.update {
                    it.copy(content = UiState.Success(result.data), offline = false,
                        wishlisted = result.data.personalHistory?.hasWishlisted ?: it.wishlisted)
                }
                val renewed = result.data.digitalFiles.firstOrNull {
                    it.resourceKey == file.resourceKey && it.kind == file.kind && !it.locked && it.safeUrl != null
                }
                if (renewed != null) ApiResult.Success(renewed)
                else ApiResult.Failure(ErrorCodes.FORBIDDEN, "", 403)
            }
        }
    }

    fun toggleWishlist() {
        if (_state.value.wishlistBusy || _state.value.offlineNow) return
        val nowWishlisted = !_state.value.wishlisted
        _state.update { it.copy(wishlistBusy = true) }
        viewModelScope.launch {
            val res = if (nowWishlisted) wishlist.add(bookId) else wishlist.remove(bookId)
            when (res) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            wishlisted = nowWishlisted,
                            wishlistBusy = false,
                            snackbar = null,
                            snackbarRes = if (nowWishlisted) R.string.snackbar_added_to_wishlist else R.string.snackbar_removed_from_wishlist,
                        )
                    }
                    // Bring the offline copy up to date after the confirmation, not before it.
                    if (nowWishlisted) launch { runCatching { wishlist.refreshCache() } }
                }
                is ApiResult.Failure -> _state.update {
                    if (res.serverMessage() != null) it.copy(wishlistBusy = false, snackbar = res.serverMessage(), snackbarRes = null)
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
        if (_state.value.offlineNow) return
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
     * backend sets the return from the library's configured loan duration. On success we surface
     * the chosen date so the UI can confirm exactly what was requested.
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
                            requestConfirmation = RequestConfirmation(desiredDate, res.data.kind),
                            showLoanSheet = false,
                            availability = null,
                            availabilityFallback = false,
                        )
                    }
                    load()
                    // The request is now a pending loan or a reservation: refresh the offline
                    // copy after the confirmation instead of holding it back.
                    launch { runCatching { library.refreshCache() } }
                }
                is ApiResult.Failure -> {
                    // Close the calendar dialog so the error is visible: the snackbar
                    // lives at the screen Scaffold level, behind the open DatePickerDialog.
                    when {
                        res.code == ErrorCodes.CONFLICT ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = null, snackbarRes = R.string.snackbar_request_conflict) }
                        res.serverMessage() != null ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = res.serverMessage(), snackbarRes = null) }
                        else ->
                            _state.update { it.copy(reserveBusy = false, showLoanSheet = false, snackbar = null, snackbarRes = R.string.snackbar_request_error) }
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    fun consumeRequestConfirmation() = _state.update { it.copy(requestConfirmation = null) }
}
