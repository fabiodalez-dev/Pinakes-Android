package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.PeriodicalIssueDetail
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

data class IssueDetailUiState(
    val content: UiState<PeriodicalIssueDetail> = UiState.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val repo: PeriodicalsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val issueId: Int = savedStateHandle.get<Int>(Routes.ARG_PERIODICAL_ISSUE_ID) ?: 0

    private val _state = MutableStateFlow(IssueDetailUiState())
    val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val res = repo.issue(issueId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(content = UiState.Success(res.data), refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(res.message, res.code, R.string.periodicals_issue_error),
                        refreshing = false,
                    )
                }
            }
        }
    }
}
