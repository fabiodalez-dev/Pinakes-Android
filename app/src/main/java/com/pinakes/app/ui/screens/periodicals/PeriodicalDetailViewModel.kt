package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.PeriodicalDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.PeriodicalsRepository
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeriodicalDetailUiState(
    val content: UiState<PeriodicalDetail> = UiState.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class PeriodicalDetailViewModel @Inject constructor(
    private val repo: PeriodicalsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The masthead id arrives as a navigation argument; Hilt populates SavedStateHandle.
    private val periodicalId: Int = savedStateHandle.get<Int>(Routes.ARG_PERIODICAL_ID) ?: 0

    private val _state = MutableStateFlow(PeriodicalDetailUiState())
    val state: StateFlow<PeriodicalDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val res = repo.periodical(periodicalId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(content = UiState.Success(res.data), refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(res.message, res.code, R.string.periodicals_detail_error),
                        refreshing = false,
                    )
                }
            }
        }
    }
}
