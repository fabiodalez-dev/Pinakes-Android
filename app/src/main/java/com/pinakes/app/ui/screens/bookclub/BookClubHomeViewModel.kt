package com.pinakes.app.ui.screens.bookclub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.BookClubClubs
import com.pinakes.app.data.model.DashboardCard
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.BookClubRepository
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Landing data: the personal dashboard cards on top, then the clubs directory. */
data class BookClubHome(
    val dashboard: List<DashboardCard> = emptyList(),
    val clubs: BookClubClubs = BookClubClubs(),
) {
    val isEmpty: Boolean
        get() = dashboard.isEmpty() && clubs.myClubs.isEmpty() && clubs.directory.isEmpty()
}

data class BookClubHomeUiState(
    val content: UiState<BookClubHome> = UiState.Loading,
    val refreshing: Boolean = false,
)

@HiltViewModel
class BookClubHomeViewModel @Inject constructor(
    private val repo: BookClubRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BookClubHomeUiState())
    val state: StateFlow<BookClubHomeUiState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            // The two reads are independent — fetch them together.
            val clubsDeferred = async { repo.clubs() }
            val dashboardDeferred = async { repo.dashboard() }
            val clubsRes = clubsDeferred.await()
            val dashboardRes = dashboardDeferred.await()

            when (clubsRes) {
                is ApiResult.Success -> {
                    val dashboard = (dashboardRes as? ApiResult.Success)?.data?.clubs ?: emptyList()
                    _state.update {
                        it.copy(
                            content = UiState.Success(BookClubHome(dashboard = dashboard, clubs = clubsRes.data)),
                            refreshing = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(clubsRes.message, clubsRes.code, R.string.book_club_error_load),
                        refreshing = false,
                    )
                }
            }
        }
    }
}
