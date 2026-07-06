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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.DashboardCard
import com.pinakes.app.data.model.DirectoryClub
import com.pinakes.app.data.model.MyClub
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookClubHomeScreen(
    onNavigateUp: () -> Unit,
    onOpenClub: (String) -> Unit,
) {
    val vm: BookClubHomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.book_club_title), onNavigateUp = onNavigateUp) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.book_club_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> {
                    val home = content.data
                    if (home.isEmpty) {
                        EmptyState(
                            title = stringResource(R.string.book_club_empty_title),
                            subtitle = stringResource(R.string.book_club_empty_subtitle),
                            icon = Icons.Outlined.Groups,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            if (home.dashboard.isNotEmpty()) {
                                item { SectionHeader(stringResource(R.string.book_club_section_reading)) }
                                items(home.dashboard, key = { "dash-${it.club.id}" }) { card ->
                                    DashboardCardView(card = card, onClick = { onOpenClub(card.club.slug) })
                                }
                            }
                            if (home.clubs.myClubs.isNotEmpty()) {
                                item { SectionHeader(stringResource(R.string.book_club_section_my_clubs)) }
                                items(home.clubs.myClubs, key = { "mine-${it.id}" }) { club ->
                                    MyClubRow(club = club, onClick = { onOpenClub(club.slug) })
                                }
                            }
                            if (home.clubs.directory.isNotEmpty()) {
                                item { SectionHeader(stringResource(R.string.book_club_section_discover)) }
                                items(home.clubs.directory, key = { "dir-${it.id}" }) { club ->
                                    DirectoryClubRow(club = club, onClick = { onOpenClub(club.slug) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun ClubColorDot(color: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(parseHexColor(color, MaterialTheme.colorScheme.primary), CircleShape),
    )
}

@Composable
private fun DashboardCardView(card: DashboardCard, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClubColorDot(card.club.color)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    card.club.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            card.currentBooks.forEach { book ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.book_club_reading_now, book.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress = book.myProgress
                if (progress != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { (progress.percent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.book_club_progress_percent, progress.percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            card.nextMeeting?.let { meeting ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.book_club_next_meeting, meeting.title, clubDateTime(meeting.startsAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.openPolls.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.book_club_open_polls, card.openPolls.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun MyClubRow(club: MyClub, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ClubColorDot(club.color)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    club.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (club.isPending) stringResource(R.string.book_club_membership_pending)
                else stringResource(roleLabelRes(club.role))
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectoryClubRow(club: DirectoryClub, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ClubColorDot(club.color)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    club.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (club.description.isNotBlank()) {
                    Text(
                        club.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val members = if (club.maxMembers != null) {
                    stringResource(R.string.book_club_members_count_max, club.memberCount, club.maxMembers)
                } else {
                    stringResource(R.string.book_club_members_count, club.memberCount)
                }
                Text(members, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
