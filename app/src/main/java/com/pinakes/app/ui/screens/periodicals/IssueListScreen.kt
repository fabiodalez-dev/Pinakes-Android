package com.pinakes.app.ui.screens.periodicals

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.PeriodicalIssue
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.theme.Spacing

/** Issues (fascicoli) of one year: cover, number, date and a status badge per row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueListScreen(
    onNavigateUp: () -> Unit,
    onOpenIssue: (Int) -> Unit,
) {
    val vm: IssueListViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.periodicals_issues_title, vm.year),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.periodicals_issues_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success ->
                    if (content.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.periodicals_issues_empty_title),
                            subtitle = stringResource(R.string.periodicals_issues_empty_subtitle),
                            icon = Icons.Outlined.Newspaper,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(content.data, key = { it.id }) { issue ->
                                IssueRow(issue = issue, onClick = { onOpenIssue(issue.id) })
                            }
                        }
                    }
            }
        }
    }
}

/** "No. 12 · Title" heading, or just the localized fallback when both are absent. */
@Composable
internal fun issueHeading(number: String?, title: String?): String {
    val parts = listOfNotNull(
        number?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.periodicals_issue_number, it) },
        title?.takeIf { it.isNotBlank() },
    )
    return if (parts.isEmpty()) stringResource(R.string.periodicals_issue_fallback)
    else parts.joinToString(" · ")
}

@Composable
private fun IssueRow(issue: PeriodicalIssue, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            PeriodicalLogo(
                url = issue.coverUrl,
                contentDescription = issue.title ?: issue.number.orEmpty(),
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    issueHeading(issue.number, issue.title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val date = issue.coverDate?.takeIf { it.isNotBlank() }
                    ?: issue.publicationDate?.takeIf { it.isNotBlank() }
                date?.let {
                    Text(
                        DateFormat.date(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                AvailabilityChip(
                    status = issueStatusBadge(issue.status),
                    label = stringResource(issueStatusLabelRes(issue.status)),
                )
            }
        }
    }
}
