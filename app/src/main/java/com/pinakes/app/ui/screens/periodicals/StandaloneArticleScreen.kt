package com.pinakes.app.ui.screens.periodicals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.*
import com.pinakes.app.ui.screens.bookclub.openWeb
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneArticleScreen(
    onNavigateUp: () -> Unit,
    onOpenPeriodical: (Int) -> Unit,
    onOpenIssue: (Int) -> Unit,
    onFindWorks: (String, Int?) -> Unit = { _, _ -> },
    onFindArticles: (String, String, Int) -> Unit = { _, _, _ -> },
    vm: StandaloneArticleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(topBar = {
        PinakesTopBar(title = stringResource(R.string.standalone_article_title), onNavigateUp = onNavigateUp,
            actions = {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val content = state) {
                UiState.Loading -> LoadingState(label = stringResource(R.string.standalone_articles_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> StandaloneArticleContent(
                    article = content.data,
                    onOpenPdf = { content.data.publicPdfUrl?.let { openWeb(context, it) } },
                    onOpenPeriodical = onOpenPeriodical, onOpenIssue = onOpenIssue,
                    onOpenResource = { content.data.publicResourceUrl?.let { openWeb(context, it) } },
                    onFindWorks = onFindWorks, onFindArticles = onFindArticles,
                    onOpenWeb = vm.webUrl?.let { url -> { openWeb(context, url) } },
                )
            }
        }
    }
}

@Composable
internal fun StandaloneArticleContent(
    article: StandaloneArticle,
    onOpenPdf: () -> Unit,
    onOpenPeriodical: (Int) -> Unit,
    onOpenIssue: (Int) -> Unit,
    onOpenResource: () -> Unit = {},
    onOpenWeb: (() -> Unit)? = null,
    onFindWorks: (String, Int?) -> Unit = { _, _ -> },
    onFindArticles: (String, String, Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BookCover(article.title, article.coverUrl, Modifier.widthIn(max = 160.dp).fillMaxWidth().aspectRatio(2f / 3f))
                }
        }
        item {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    article.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val names = article.authorCredits.map { it.name }.ifEmpty { article.authors.orEmpty().split(';').map(String::trim).filter(String::isNotBlank) }
                    names.forEach { name -> TextButton({ onFindWorks(name, article.authorCredits.firstOrNull { it.name == name }?.id) }) { Text(name) } }
                    article.authorCredits.forEach { credit ->
                        credit.identifiers.forEach { identifier -> Text(identifier.toString(), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { CitationButton(article.citations, article.risUrl, article.marcXmlUrl) }
        article.manageUrl?.let { url -> item { TextButton({ openWeb(context, url) }) { Text(stringResource(R.string.collection_manage)) } } }
        if (article.canOpenPdf) item {
            PrimaryButton(label = stringResource(R.string.periodicals_open_pdf), onClick = onOpenPdf,
                modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew)
        }
        article.publicResourceAddress?.let { address ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (article.publicResourceUrl != null) {
                        TextButton(onClick = onOpenResource, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(article.resourceLabel?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.standalone_article_online_resource))
                        }
                    } else {
                        SelectionContainer { ArticleMetadata(R.string.standalone_article_resource_reference, address) }
                    }
                    article.resourceAccess?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        onOpenWeb?.let { open ->
            item { TextButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.standalone_article_open_web))
            } }
        }
        item {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    article.containerTitle?.let { title -> TextButton({ onFindArticles(title, "", 0) }) { Text(title) } }
                    article.containerType?.takeIf { it.isNotBlank() }?.let {
                        ArticleMetadata(R.string.standalone_article_publication_type, stringResource(periodicalTypeLabelRes(it)))
                    }
                    ArticleMetadata(R.string.standalone_article_date, article.dateLabel)
                    ArticleMetadata(R.string.standalone_article_volume_label, article.volume)
                    ArticleMetadata(R.string.standalone_article_number_label, article.number)
                    ArticleMetadata(R.string.standalone_article_pages_label, article.pages)
                    ArticleMetadata(R.string.standalone_article_issn, article.issn)
                    ArticleMetadata(R.string.standalone_article_doi, article.doi)
                    article.keywords.orEmpty().split(';', ',').map(String::trim).filter(String::isNotBlank).forEach { keyword ->
                        TextButton({ onFindArticles("", keyword, 0) }) { Text(keyword) }
                    }
                    article.genrePath.forEach { genre -> TextButton({ onFindArticles("", "", genre.id) }) { Text(genre.name) } }
                    ArticleMetadata(R.string.book_meta_language, article.language)
                    ArticleMetadata(R.string.article_country, article.country)
                    ArticleMetadata(R.string.article_classification, listOfNotNull(article.classificationScheme, article.classification).joinToString(" · "))
                    ArticleMetadata(R.string.article_holdings, article.holdingsNote)
                    ArticleMetadata(R.string.article_editors, article.editors)
                    ArticleMetadata(R.string.book_meta_publisher, article.publisher)
                    ArticleMetadata(R.string.book_meta_publication_place, article.publicationPlace)
                    ArticleMetadata(R.string.book_meta_isbn13, article.isbn)
                    ArticleMetadata(R.string.book_meta_format, stringResource(when (article.medium) {
                        "cartaceo" -> R.string.article_print; "digitale" -> R.string.article_digital; "entrambi" -> R.string.article_both; else -> R.string.article_format_unknown
                    }))
                }
            }
        }
        article.description?.takeIf { it.isNotBlank() }?.let { description ->
            item { SelectionContainer { HtmlText(description, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface) } }
        }
        article.mastheadId?.takeIf { it > 0 }?.let { id ->
            item { TextButton(onClick = { onOpenPeriodical(id) }) { Text(stringResource(R.string.standalone_article_open_masthead)) } }
        }
        article.issueId?.takeIf { it > 0 }?.let { id ->
            item { TextButton(onClick = { onOpenIssue(id) }) { Text(stringResource(R.string.standalone_article_open_issue)) } }
        }
    }
}

@Composable
private fun ArticleMetadata(@androidx.annotation.StringRes label: Int, value: String?) {
    if (!value.isNullOrBlank()) Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
