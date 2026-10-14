package com.pinakes.app.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.WishlistItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.connectivity.ConnectivityObserver
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import kotlinx.coroutines.flow.drop
import com.pinakes.app.data.repository.WishlistRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WishlistUiState(
    val content: UiState<List<WishlistItem>> = UiState.Loading,
    val refreshing: Boolean = false,
    val removingId: Int? = null,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    /** Showing the cached list because the server is unreachable: removing is disabled. */
    val offline: Boolean = false,
    /** Device connectivity: removing and availability disappear the moment it drops. */
    val connected: Boolean = true,
) {
    val offlineNow: Boolean get() = offline || !connected
}

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlist: WishlistRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _state = MutableStateFlow(WishlistUiState(connected = connectivity.isOnline.value))
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

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
            when (val res = wishlist.wishlist()) {
                is ApiResult.Success -> _state.update {
                    it.copy(content = UiState.Success(res.data), refreshing = false, offline = false)
                }
                is ApiResult.Failure -> {
                    val cached = if (res.code == ErrorCodes.NETWORK && _state.value.content !is UiState.Success) {
                        wishlist.wishlistCached()
                    } else null
                    if (cached != null) {
                        _state.update { it.copy(content = UiState.Success(cached), refreshing = false, offline = true) }
                    } else {
                        _state.update {
                            it.copy(
                                content = if (it.content is UiState.Success) it.content
                                else if (res.isOffline) UiState.Error("", ErrorCodes.NETWORK, R.string.offline_no_copy)
                                else UiState.Error(res.message, res.code, R.string.wishlist_error_load),
                                refreshing = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun remove(bookId: Int) {
        if (_state.value.removingId != null || _state.value.offlineNow) return
        _state.update { it.copy(removingId = bookId) }
        viewModelScope.launch {
            when (val res = wishlist.remove(bookId)) {
                is ApiResult.Success -> {
                    val current = (_state.value.content as? UiState.Success)?.data ?: emptyList()
                    _state.update {
                        it.copy(
                            content = UiState.Success(current.filterNot { w -> w.bookId == bookId }),
                            removingId = null,
                            snackbar = null,
                            snackbarRes = R.string.snackbar_removed_from_wishlist,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    if (res.serverMessage() != null) it.copy(removingId = null, snackbar = res.serverMessage(), snackbarRes = null)
                    else it.copy(removingId = null, snackbar = null, snackbarRes = R.string.snackbar_wishlist_remove_error)
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }
}
