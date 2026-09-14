package com.pinakes.app.ui.screens.periodicals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.ui.components.*
import com.pinakes.app.ui.theme.Spacing

/** Articles are readable citation rows, without suggesting possession or lending status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneArticlesScreen(
    onNavigateUp: () -> Unit,
    onOpenArticle: (Int) -> Unit,
    vm: StandaloneArticlesViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        PinakesTopBar(title = stringResource(R.string.standalone_articles_title), onNavigateUp = onNavigateUp,
            actions = {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query, onQueryChange = vm::onQueryChange, onSearch = vm::refresh,
                placeholder = stringResource(R.string.standalone_articles_search),
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            )
            when {
                state.loading -> LoadingState(label = stringResource(R.string.standalone_articles_loading))
                state.unavailable -> EmptyState(
                    title = stringResource(R.string.standalone_articles_title),
                    subtitle = stringResource(R.string.standalone_articles_unavailable),
                )
                state.error -> ErrorState(message = stringResource(R.string.standalone_articles_error), onRetry = vm::refresh)
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.standalone_articles_empty),
                    subtitle = stringResource(R.string.standalone_articles_empty_hint),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    items(state.items, key = { it.id }) { article ->
                        StandaloneArticleRow(article, onClick = { onOpenArticle(article.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (state.nextCursor != null) item {
                        if (state.moreError) Text(
                            stringResource(R.string.standalone_articles_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        TextButton(
                            onClick = vm::loadMore, enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                        ) {
                            if (state.loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (state.moreError) R.string.action_retry else R.string.action_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandaloneArticleRow(article: StandaloneArticle, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(article.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        article.authors?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        article.containerTitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val citation = listOfNotNull(
            article.dateLabel,
            article.volume?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.standalone_article_volume, it) },
            article.number?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.standalone_article_number, it) },
            article.pages?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.standalone_article_pages, it) },
        ).joinToString(" · ")
        if (citation.isNotBlank()) Text(citation, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
