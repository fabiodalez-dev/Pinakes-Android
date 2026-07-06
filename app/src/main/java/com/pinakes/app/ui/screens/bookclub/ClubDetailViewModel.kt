package com.pinakes.app.ui.screens.bookclub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.BookClubDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.BookClubRepository
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClubDetailUiState(
    val content: UiState<BookClubDetail> = UiState.Loading,
    val refreshing: Boolean = false,
    val joining: Boolean = false,
    val votingPollId: Int? = null,
    val rsvpMeetingId: Int? = null,
    val progressBookId: Int? = null,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
)

@HiltViewModel
class ClubDetailViewModel @Inject constructor(
    private val repo: BookClubRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val slug: String = savedStateHandle.get<String>(Routes.ARG_CLUB_SLUG).orEmpty()

    private val _state = MutableStateFlow(ClubDetailUiState())
    val state: StateFlow<ClubDetailUiState> = _state.asStateFlow()

    /** Instance web origin, for deep-linking the flows the API marks web-only. */
    val webBaseUrl: String get() = repo.webBaseUrl()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        if (initial) _state.update { it.copy(content = UiState.Loading) }
        else _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val res = repo.clubDetail(slug)) {
                is ApiResult.Success -> _state.update {
                    it.copy(content = UiState.Success(res.data), refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        content = if (it.content is UiState.Success) it.content
                        else UiState.Error(res.message, res.code, R.string.book_club_error_load),
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            when (val res = repo.join(slug)) {
                is ApiResult.Success -> {
                    val res2 = if (res.data.status == "pending") R.string.book_club_join_pending
                    else R.string.book_club_join_active
                    _state.update { it.copy(joining = false, snackbarRes = res2) }
                    load(initial = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(joining = false).withError(res) }
            }
        }
    }

    fun vote(pollId: Int, optionIds: List<Int>) {
        if (_state.value.votingPollId != null) return
        if (optionIds.isEmpty()) {
            _state.update { it.copy(snackbarRes = R.string.book_club_vote_empty) }
            return
        }
        _state.update { it.copy(votingPollId = pollId) }
        viewModelScope.launch {
            when (val res = repo.vote(slug, pollId, optionIds)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(votingPollId = null, snackbarRes = R.string.book_club_vote_saved) }
                    load(initial = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(votingPollId = null).withError(res) }
            }
        }
    }

    fun rsvp(meetingId: Int, response: String) {
        if (_state.value.rsvpMeetingId != null) return
        _state.update { it.copy(rsvpMeetingId = meetingId) }
        viewModelScope.launch {
            when (val res = repo.rsvp(slug, meetingId, response)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(rsvpMeetingId = null, snackbarRes = R.string.book_club_rsvp_saved) }
                    load(initial = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(rsvpMeetingId = null).withError(res) }
            }
        }
    }

    fun updateProgress(clubBookId: Int, percent: Int, finished: Boolean) {
        if (_state.value.progressBookId != null) return
        _state.update { it.copy(progressBookId = clubBookId) }
        viewModelScope.launch {
            when (val res = repo.progress(slug, clubBookId, percent, finished)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(progressBookId = null, snackbarRes = R.string.book_club_progress_saved) }
                    load(initial = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(progressBookId = null).withError(res) }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    /** Prefer the server's (localized) message; fall back to a generic string when it's blank. */
    private fun ClubDetailUiState.withError(failure: ApiResult.Failure): ClubDetailUiState =
        if (failure.message.isNotBlank()) copy(snackbar = failure.message, snackbarRes = null)
        else copy(snackbar = null, snackbarRes = R.string.book_club_action_error)
}
