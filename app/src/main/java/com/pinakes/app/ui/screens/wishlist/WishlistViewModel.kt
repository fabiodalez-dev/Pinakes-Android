package com.pinakes.app.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.WishlistItem
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.WishlistRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
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
)

class WishlistViewModel(private val wishlist: WishlistRepository) : ViewModel() {

    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val res = wishlist.wishlist()) {
                is ApiResult.Success -> _state.update {
                    it.copy(content = UiState.Success(res.data), refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(res.message, res.code, R.string.wishlist_error_load),
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun remove(bookId: Int) {
        if (_state.value.removingId != null) return
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
                    if (res.message.isNotBlank()) it.copy(removingId = null, snackbar = res.message, snackbarRes = null)
                    else it.copy(removingId = null, snackbar = null, snackbarRes = R.string.snackbar_wishlist_remove_error)
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    class Factory(private val wishlist: WishlistRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WishlistViewModel(wishlist) as T
    }
}
