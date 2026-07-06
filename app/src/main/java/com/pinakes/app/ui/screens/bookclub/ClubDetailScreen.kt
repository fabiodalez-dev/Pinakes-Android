package com.pinakes.app.ui.screens.bookclub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.BookClubDetail
import com.pinakes.app.data.model.ClubBook
import com.pinakes.app.data.model.ClubMeeting
import com.pinakes.app.data.model.ClubPoll
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailScreen(onNavigateUp: () -> Unit) {
    val vm: ClubDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    val snackbarMessage = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() }
    }

    var progressBook by remember { mutableStateOf<ClubBook?>(null) }

    val title = (state.content as? UiState.Success)?.data?.club?.name
        ?: stringResource(R.string.book_club_title)

    Scaffold(
        topBar = { PinakesTopBar(title = title, onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.book_club_loading))
                is UiState.Error ->
                    if (state.pluginGone) EmptyState(
                        title = stringResource(R.string.book_club_gone_title),
                        subtitle = stringResource(R.string.book_club_gone_subtitle),
                    ) else ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> ClubDetailContent(
                    detail = content.data,
                    state = state,
                    onJoin = vm::join,
                    onVote = vm::vote,
                    onRsvp = vm::rsvp,
                    onUpdateProgress = { book -> progressBook = book },
                    onOpenPollWeb = { poll -> openWeb(context, vm.pollWebUrl(poll.id)) },
                    onOpenVideo = { url -> openWeb(context, url) },
                )
            }
        }
    }

    progressBook?.let { book ->
        ProgressDialog(
            book = book,
            saving = state.progressBookId == book.id,
            onSave = { percent, finished ->
                vm.updateProgress(book.id, percent, finished)
                progressBook = null
            },
            onDismiss = { progressBook = null },
        )
    }
}

@Composable
private fun ClubDetailContent(
    detail: BookClubDetail,
    state: ClubDetailUiState,
    onJoin: () -> Unit,
    onVote: (Int, List<Int>) -> Unit,
    onRsvp: (Int, String) -> Unit,
    onUpdateProgress: (ClubBook) -> Unit,
    onOpenPollWeb: (ClubPoll) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ClubHeader(detail = detail, joining = state.joining, onJoin = onJoin) }

        if (detail.books.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.book_club_reading_list)) }
            items(detail.books, key = { "book-${it.id}" }) { book ->
                ClubBookCard(
                    book = book,
                    canParticipate = detail.canParticipate,
                    onUpdateProgress = { onUpdateProgress(book) },
                )
            }
        }

        if (detail.polls.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.book_club_polls)) }
            items(detail.polls, key = { "poll-${it.id}" }) { poll ->
                PollCard(
                    poll = poll,
                    canParticipate = detail.canParticipate,
                    voting = state.votingPollId == poll.id,
                    onVote = { options -> onVote(poll.id, options) },
                    onOpenWeb = { onOpenPollWeb(poll) },
                )
            }
        }

        if (detail.meetings.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.book_club_meetings)) }
            items(detail.meetings, key = { "meeting-${it.id}" }) { meeting ->
                MeetingCard(
                    meeting = meeting,
                    canParticipate = detail.canParticipate,
                    rsvping = state.rsvpMeetingId == meeting.id,
                    onRsvp = { response -> onRsvp(meeting.id, response) },
                    onOpenVideo = { onOpenVideo(meeting.videoUrl) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun ClubHeader(detail: BookClubDetail, joining: Boolean, onJoin: () -> Unit) {
    val club = detail.club
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(club.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            val members = if (club.maxMembers != null) {
                stringResource(R.string.book_club_members_count_max, club.memberCount, club.maxMembers)
            } else {
                stringResource(R.string.book_club_members_count, club.memberCount)
            }
            Text(
                "${stringResource(privacyLabelRes(club.privacy))} · $members",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (club.description.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(club.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail.isActiveMember && club.rules.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.book_club_rules), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(club.rules, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Spacing.md))
            when {
                detail.canJoin -> PrimaryButton(
                    label = stringResource(R.string.book_club_join),
                    onClick = onJoin,
                    loading = joining,
                    modifier = Modifier.fillMaxWidth(),
                )
                detail.myMembership?.status == "pending" ->
                    StatusPill(stringResource(R.string.book_club_membership_pending))
                detail.isGuest ->
                    StatusPill(stringResource(R.string.book_club_guest_readonly))
                detail.isActiveMember ->
                    StatusPill(stringResource(R.string.book_club_membership_member, stringResource(roleLabelRes(detail.myMembership?.role ?: "member"))))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun ClubBookCard(book: ClubBook, canParticipate: Boolean, onUpdateProgress: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.authors.isNotBlank()) {
                        Text(book.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                StateChip(label = book.stateLabel, colorHex = book.stateColor)
            }
            if (book.motivation.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text("“${book.motivation}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            book.myProgress?.let { progress ->
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(progress = { progress.percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (progress.finished) stringResource(R.string.book_club_progress_finished)
                    else stringResource(R.string.book_club_progress_percent, progress.percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canParticipate && book.isCurrent) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    label = stringResource(R.string.book_club_update_progress),
                    onClick = onUpdateProgress,
                )
            }
        }
    }
}

@Composable
private fun PollCard(
    poll: ClubPoll,
    canParticipate: Boolean,
    voting: Boolean,
    onVote: (List<Int>) -> Unit,
    onOpenWeb: () -> Unit,
) {
    // Selection is seeded from the user's existing ballot and resets when the poll reloads.
    var selected by remember(poll.id, poll.myOptionIds) { mutableStateOf(poll.myOptionIds.toSet()) }
    // The server only flips status to 'closed' from the cron or the web page (never on the
    // mobile read path), yet vote() rejects past-deadline ballots with 409 poll_closed —
    // treat an expired closes_at as closed so we never render a ballot that can only fail.
    val open = poll.isOpen && !DateFormat.isPast(poll.closesAt)
    val interactive = canParticipate && open && poll.votableInApp

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(poll.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val meta = buildList {
                add(stringResource(if (open) R.string.book_club_poll_open else R.string.book_club_poll_closed))
                poll.closesAt?.let { add(stringResource(R.string.book_club_poll_closes, DateFormat.dateTime(it))) }
                add(stringResource(R.string.book_club_poll_voters, poll.voterCount))
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (interactive && poll.maxChoices > 1) {
                Text(
                    stringResource(R.string.book_club_poll_choose_up_to, poll.maxChoices),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            poll.options.forEach { option ->
                val isSelected = option.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (interactive) Modifier.clickable {
                                selected = toggleSelection(selected, option.id, poll.maxChoices)
                            } else Modifier
                        )
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (interactive) {
                        if (poll.maxChoices == 1) {
                            RadioButton(selected = isSelected, onClick = { selected = setOf(option.id) })
                        } else {
                            Checkbox(checked = isSelected, onCheckedChange = { selected = toggleSelection(selected, option.id, poll.maxChoices) })
                        }
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(option.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (option.id in poll.myOptionIds) {
                        Text(stringResource(R.string.book_club_poll_your_vote), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (interactive) {
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton(
                    label = stringResource(R.string.book_club_vote),
                    onClick = { onVote(selected.toList()) },
                    enabled = selected.isNotEmpty() && !voting,
                    loading = voting,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (canParticipate && open && !poll.votableInApp) {
                // Advanced ballots (stars/ranking/elimination) are web-only per the API contract.
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    label = stringResource(R.string.book_club_vote_on_web),
                    onClick = onOpenWeb,
                    leadingIcon = Icons.Outlined.OpenInNew,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MeetingCard(
    meeting: ClubMeeting,
    canParticipate: Boolean,
    rsvping: Boolean,
    onRsvp: (String) -> Unit,
    onOpenVideo: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(meeting.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(DateFormat.dateTime(meeting.startsAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (meeting.location.isNotBlank()) {
                Text(meeting.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (meeting.agenda.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(meeting.agenda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val seatsLabel = meeting.seats?.let { stringResource(R.string.book_club_meeting_seats, meeting.yesCount, it) }
                ?: stringResource(R.string.book_club_meeting_yes, meeting.yesCount)
            Spacer(Modifier.height(Spacing.xs))
            Text(seatsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (meeting.videoUrl.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                AssistChip(
                    onClick = onOpenVideo,
                    label = { Text(stringResource(R.string.book_club_meeting_join_online)) },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.width(18.dp)) },
                )
            }

            if (canParticipate && meeting.status == "scheduled") {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A full meeting always 409s (no_seats) on a NEW 'yes' — mirror the
                    // server rule client-side; users already going stay free to re-confirm,
                    // and 'maybe'/'no' are never seat-gated.
                    val yesBlocked = meeting.isFull && meeting.myRsvp != "yes"
                    RsvpChip(stringResource(R.string.book_club_rsvp_yes), meeting.myRsvp == "yes", rsvping || yesBlocked) { onRsvp("yes") }
                    RsvpChip(stringResource(R.string.book_club_rsvp_maybe), meeting.myRsvp == "maybe", rsvping) { onRsvp("maybe") }
                    RsvpChip(stringResource(R.string.book_club_rsvp_no), meeting.myRsvp == "no", rsvping) { onRsvp("no") }
                }
            }
        }
    }
}

@Composable
private fun RsvpChip(label: String, selected: Boolean, disabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = !disabled,
        label = { Text(label) },
    )
}

@Composable
private fun StateChip(label: String, colorHex: String) {
    // Soft-tint chip: the state's colour as text on a low-alpha wash of the same colour.
    val base = parseHexColor(colorHex, MaterialTheme.colorScheme.secondary)
    Box(
        Modifier
            .background(base.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    ) {
        Text(label.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = base, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressDialog(
    book: ClubBook,
    saving: Boolean,
    onSave: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var percent by remember { mutableFloatStateOf((book.myProgress?.percent ?: 0).toFloat()) }
    var finished by remember { mutableStateOf(book.myProgress?.finished ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.book_club_update_progress), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.book_club_progress_percent, percent.toInt()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = percent,
                    onValueChange = { percent = it; if (it >= 100f) finished = true },
                    valueRange = 0f..100f,
                    steps = 19,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { finished = !finished }) {
                    Checkbox(checked = finished, onCheckedChange = { finished = it; if (it) percent = 100f })
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.book_club_progress_mark_finished), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                label = stringResource(R.string.action_save),
                onClick = { onSave(percent.toInt(), finished) },
                loading = saving,
            )
        },
        dismissButton = {
            PinakesTextButton(label = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}

/** Toggle option [id] within [current], respecting [max] (single-choice replaces; multi caps). */
private fun toggleSelection(current: Set<Int>, id: Int, max: Int): Set<Int> = when {
    max <= 1 -> setOf(id)
    id in current -> current - id
    current.size < max -> current + id
    else -> current // at cap: ignore extra picks
}
