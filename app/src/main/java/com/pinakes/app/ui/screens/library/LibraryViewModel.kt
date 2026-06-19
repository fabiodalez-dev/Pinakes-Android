package com.pinakes.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
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
)

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init { load(initial = true) }

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
                    )
                }
            } else {
                val f = loans as ApiResult.Failure
                _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(f.message, f.code, R.string.library_error_load),
                        refreshing = false,
                        snackbarRes = if (it.content is UiState.Success) R.string.library_refresh_error else null,
                    )
                }
            }
        }
    }

    fun cancelReservation(id: Int) {
        if (_state.value.cancelingId != null) return
        _state.update { it.copy(cancelingId = id) }
        viewModelScope.launch {
            when (val res = library.cancelReservation(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancelled) }
                    load(initial = false)
                }
                is ApiResult.Failure -> {
                    when {
                        res.code == ErrorCodes.CONFLICT ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancel_conflict) }
                        res.message.isNotBlank() ->
                            _state.update { it.copy(cancelingId = null, snackbar = res.message, snackbarRes = null) }
                        else ->
                            _state.update { it.copy(cancelingId = null, snackbar = null, snackbarRes = R.string.snackbar_reservation_cancel_error) }
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    class Factory(private val library: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(library) as T
    }
}
