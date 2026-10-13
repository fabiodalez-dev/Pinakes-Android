package com.pinakes.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.connectivity.ConnectivityObserver
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.OfflinePolicy
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import kotlinx.coroutines.flow.drop
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryData(
    val loans: LoansData,
    val reservations: List<ReservationItem>,
)

data class LibraryUiState(
    val content: UiState<LibraryData> = UiState.Loading,
    val refreshing: Boolean = false,
    val cancelingId: Int? = null,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    /** Showing the cached lists because the server is unreachable: actions are disabled. */
    val offline: Boolean = false,
    /** Device connectivity: cancel actions disappear the moment it drops. */
    val connected: Boolean = true,
) {
    /** Loan/reservation actions need a live server: hidden when offline either way. */
    val showActions: Boolean get() = OfflinePolicy.showLoanActions(canBorrow = true, offline = offline || !connected)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState(connected = connectivity.isOnline.value))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            connectivity.isOnline.drop(1).collect { online ->
                _state.update { it.copy(connected = online) }
                if (online && (_state.value.offline || _state.value.content is UiState.Error)) load(initial = false)
            }
        }
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val loansDeferred = async { library.loans() }
            val reservationsDeferred = async { library.reservations() }
            val (loansRes, reservationsRes) = awaitAll(loansDeferred, reservationsDeferred)

            @Suppress("UNCHECKED_CAST")
            val loans = loansRes as ApiResult<LoansData>
            @Suppress("UNCHECKED_CAST")
            val reservations = reservationsRes as ApiResult<List<ReservationItem>>

            if (loans is ApiResult.Success) {
                val resv = (reservations as? ApiResult.Success)?.data ?: emptyList()
                _state.update {
                    it.copy(
                        content = UiState.Success(LibraryData(loans.data, resv)),
                        refreshing = false,
                        offline = false,
                    )
                }
            } else {
                val f = loans as ApiResult.Failure
                // No connection and nothing on screen yet: show the last lists the server sent.
                val cached = if (f.code == ErrorCodes.NETWORK && _state.value.content !is UiState.Success) {
                    library.loansCached()
                } else null
                if (cached != null) {
                    _state.update {
                        it.copy(
                            content = UiState.Success(LibraryData(cached, library.reservationsCached())),
                            refreshing = false,
                            offline = true,
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        // Offline with nothing saved: a localized empty state, never the raw exception.
                    else if (f.isOffline) UiState.Error("", ErrorCodes.NETWORK, R.string.offline_no_copy)
                    else UiState.Error(f.message, f.code, R.string.library_error_load),
                        refreshing = false,
                        snackbarRes = if (it.content is UiState.Success) R.string.library_refresh_error else null,
                    )
                }
            }
        }
    }

    fun cancelReservation(id: Int) {
        if (_state.value.cancelingId != null || !_state.value.showActions) return
        _state.update { it.copy(cancelingId = id) }
        viewModelScope.launch {
            when (val res = library.cancelReservation(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancelled) }
                    load(initial = false)
                }
                is ApiResult.Failure -> {
                    when {
                        res.code == ErrorCodes.CONFLICT || res.httpStatus == 409 ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancel_conflict) }
                        res.serverMessage() != null ->
                            _state.update { it.copy(cancelingId = null, snackbar = res.serverMessage(), snackbarRes = null) }
                        else ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancel_error) }
                    }
                }
            }
        }
    }

    fun cancelLoan(id: Int) {
        if (_state.value.cancelingId != null || !_state.value.showActions) return
        _state.update { it.copy(cancelingId = id) }
        viewModelScope.launch {
            when (val res = library.cancelLoan(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_loan_cancelled) }
                    load(initial = false)
                }
                is ApiResult.Failure -> {
                    when {
                        res.code == ErrorCodes.CONFLICT || res.httpStatus == 409 ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_loan_cancel_conflict) }
                        res.serverMessage() != null ->
                            _state.update { it.copy(cancelingId = null, snackbar = res.serverMessage(), snackbarRes = null) }
                        else ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_loan_cancel_error) }
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }
}
