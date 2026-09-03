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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.IssueArticle
import com.pinakes.app.data.model.PeriodicalIssueDetail
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.bookclub.openWeb
import com.pinakes.app.ui.theme.Spacing

/**
 * Issue (fascicolo) detail: large cover, metadata + status badge, the spoglio articles and —
 * ONLY when the server exposes a public `pdf_url` — an "Open PDF" action (external viewer,
 * same [openWeb] deep-link path the app already uses for web-only flows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(onNavigateUp: () -> Unit) {
    val vm: IssueDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val title = (state.content as? UiState.Success)?.data?.masthead?.title
        ?: stringResource(R.string.periodicals_issue_fallback)

    Scaffold(
        topBar = { PinakesTopBar(title = title, onNavigateUp = onNavigateUp) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.periodicals_issue_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> {
                    val issue = content.data
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        item { IssueHeader(issue) }
                        item {
                            // pdf_url is nullable and only present when the PDF is public:
                            // the action is rendered exclusively behind that gate.
                            if (issue.canOpenPdf) {
                                PrimaryButton(
                                    label = stringResource(R.string.periodicals_open_pdf),
                                    onClick = { openWeb(context, issue.pdfUrl.orEmpty()) },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                                )
                            }
                        }
                        if (issue.articles.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.periodicals_articles_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                            items(issue.articles.size) { index ->
                                ArticleRow(issue.articles[index])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueHeader(issue: PeriodicalIssueDetail) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            PeriodicalLogo(
                url = issue.coverUrl,
                contentDescription = issue.title ?: issue.number.orEmpty(),
                modifier = Modifier
                    .size(width = 160.dp, height = 220.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                issueHeading(issue.number, issue.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val yearLine = issue.year?.let { y ->
                listOfNotNull(
                    y.year.toString(),
                    y.volume?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.periodicals_year_volume, it) },
                ).joinToString(" · ")
            }
            yearLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val date = issue.coverDate?.takeIf { it.isNotBlank() }
                ?: issue.publicationDate?.takeIf { it.isNotBlank() }
            val meta = listOfNotNull(
                date?.let { DateFormat.date(it) },
                issue.pages?.let { stringResource(R.string.periodicals_issue_pages, it) },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            AvailabilityChip(
                status = issueStatusBadge(issue.status),
                label = stringResource(issueStatusLabelRes(issue.status)),
            )
        }
    }
}

@Composable
private fun ArticleRow(article: IssueArticle) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                article.authors?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            articlePagesLabel(article.pageStart, article.pageEnd)?.let {
                Spacer(Modifier.width(Spacing.md))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "pp. 12–18" for a range, "p. 12" for a single page, null when the server has no data. */
@Composable
private fun articlePagesLabel(start: Int?, end: Int?): String? = when {
    start != null && end != null && end != start ->
        stringResource(R.string.periodicals_article_pages, start, end)
    start != null -> stringResource(R.string.periodicals_article_page, start)
    else -> null
}
