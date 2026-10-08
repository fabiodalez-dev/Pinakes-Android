package com.pinakes.app.ui.screens.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.*
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.components.*
import com.pinakes.app.ui.screens.bookclub.openWeb
import com.pinakes.app.ui.screens.detail.PdfReaderDialog
import com.pinakes.app.ui.theme.PublicationTitleStyle
import com.pinakes.app.ui.theme.Spacing

@Composable
fun DesiderataScreen(onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit, onDonate: (Int) -> Unit,
                     vm: DesiderataViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    CollectionScaffold(stringResource(R.string.desiderata_title), onNavigateUp) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item { Text(stringResource(R.string.desiderata_intro), style = MaterialTheme.typography.bodyLarge) }
            item { SearchField(query, vm::changeQuery, modifier = Modifier.fillMaxWidth(), placeholder = stringResource(R.string.collection_search), onSearch = vm::refresh) }
            item { PrimaryButton(stringResource(R.string.desiderata_free_offer), { onDonate(0) }, Modifier.fillMaxWidth()) }
            item { CollectionLinks(health) }
            item { ListStatus(state, vm::refresh, R.string.desiderata_empty) }
            items(state.items, key = { it.id }) { book ->
                CollectionRow(book.title, listOfNotNull(book.author, book.publisher).joinToString(" · "), book.coverUrl,
                    stringResource(R.string.desiderata_wanted), { onOpenBook(book.id) })
            }
            item { MoreButton(state, vm::more) }
        }
    }
}

@Composable
fun WantedBookScreen(onNavigateUp: () -> Unit, onDonate: (Int) -> Unit, vm: WantedBookViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectionScaffold(stringResource(R.string.desiderata_title), onNavigateUp) { padding ->
        DetailResult(state, padding, vm::refresh) { book ->
            CollectionCover(book.title, book.coverUrl, book.author, book.publisher)
            Text(stringResource(R.string.desiderata_wanted), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(book.title, style = PublicationTitleStyle)
            book.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Metadata(stringResource(R.string.donation_author), book.author)
            Metadata(stringResource(R.string.book_meta_publisher), book.publisher)
            Metadata(stringResource(R.string.book_meta_year), book.year?.toString())
            Metadata("ISBN", book.isbn)
            book.description?.let { HtmlText(it) }
            PrimaryButton(stringResource(R.string.desiderata_offer), { onDonate(book.id) }, Modifier.fillMaxWidth())
            book.webUrl?.let { url -> SecondaryButton(stringResource(R.string.collection_web), { openWeb(context, url) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun ArchivesScreen(onNavigateUp: () -> Unit, onOpenArchive: (Int) -> Unit, vm: ArchivesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val invalid by vm.invalidDates.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    CollectionScaffold(stringResource(R.string.archives_title), onNavigateUp) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item { Text(stringResource(R.string.archives_intro), style = MaterialTheme.typography.bodyLarge) }
            item { SearchField(query, vm::changeQuery, modifier = Modifier.fillMaxWidth(), placeholder = stringResource(R.string.collection_search), onSearch = vm::refresh) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("fonds", "series", "file", "item").forEach { value -> FilterChip(level == value, { vm.changeLevel(value) }, { Text(archiveLevel(value)) }) }
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PinakesTextField(from, vm::changeFrom, stringResource(R.string.archives_from), Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 6)
                PinakesTextField(to, vm::changeTo, stringResource(R.string.archives_to), Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 6)
            } }
            if (invalid) item { Text(stringResource(R.string.archives_dates_error), color = MaterialTheme.colorScheme.error) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(stringResource(R.string.archives_apply), vm::refresh, Modifier.weight(1f))
                PinakesTextButton(stringResource(R.string.collection_clear), vm::clear, Modifier.weight(1f))
            } }
            item { CollectionLinks(health) }
            item { ListStatus(state, vm::refresh, R.string.collection_empty) }
            items(state.items, key = { it.id }) { record -> CollectionRow(record.title,
                listOfNotNull(record.referenceCode.takeIf(String::isNotBlank), record.datesLabel, record.extent).joinToString(" · "),
                record.coverUrl, archiveLevel(record.level), { onOpenArchive(record.id) }) }
            item { MoreButton(state, vm::more) }
        }
    }
}

@Composable
fun ArchiveScreen(onNavigateUp: () -> Unit, onOpenArchive: (Int) -> Unit, onOpenChildren: (Int) -> Unit,
                  vm: ArchiveViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pdf by remember { mutableStateOf<String?>(null) }
    pdf?.let { PdfReaderDialog(pdfUrl = it, onDismiss = { pdf = null }) }
    CollectionScaffold(stringResource(R.string.archives_title), onNavigateUp) { padding ->
        DetailResult(state, padding, vm::refresh) { record ->
            record.ancestors.forEach { ancestor -> TextButton({ onOpenArchive(ancestor.id) }) { Text(ancestor.title) } }
            CollectionCover(record.title, record.coverUrl)
            Text(record.title, style = PublicationTitleStyle)
            record.formalTitle?.takeIf { it != record.title }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Metadata(stringResource(R.string.archives_reference), record.referenceCode)
            Metadata(stringResource(R.string.archives_level), archiveLevel(record.level))
            Metadata(stringResource(R.string.archives_dates), record.datesLabel)
            Metadata(stringResource(R.string.archives_extent), record.extent)
            Metadata(stringResource(R.string.archives_material), record.material)
            record.fields.forEach { (key, value) -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(archiveFieldLabel(key)), style = MaterialTheme.typography.labelLarge)
                HtmlText(value)
            } }
            if (record.authorities.isNotEmpty()) {
                Text(stringResource(R.string.archives_authorities), style = MaterialTheme.typography.titleMedium)
                record.authorities.forEach { authority -> Metadata(authority.name, listOfNotNull(authority.dates, authority.role.takeIf(String::isNotBlank)).joinToString(" · ")) }
            }
            if (record.documents.isNotEmpty()) Text(stringResource(R.string.archives_documents), style = MaterialTheme.typography.titleMedium)
            record.documents.forEach { document -> SecondaryButton(document.label.ifBlank { stringResource(R.string.archives_documents) }, {
                if (document.mime.equals("application/pdf", true)) pdf = document.url else openWeb(context, document.url)
            }, Modifier.fillMaxWidth()) }
            PrimaryButton(stringResource(R.string.archives_children), { onOpenChildren(record.id) }, Modifier.fillMaxWidth())
            record.webUrl?.let { url -> TextButton({ openWeb(context, url) }) { Text(stringResource(R.string.collection_web)) } }
            if (record.exports.isNotEmpty()) Text(stringResource(R.string.archives_exports), style = MaterialTheme.typography.titleMedium)
            record.exports.forEach { (label, url) -> TextButton({ openWeb(context, url) }) { Text(label) } }
        }
    }
}

@Composable
fun DonationScreen(onNavigateUp: () -> Unit, onDone: () -> Unit, vm: DonationViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    CollectionScaffold(stringResource(R.string.donation_title), onNavigateUp) { padding ->
        DonationContent(state, padding, vm::edit, vm::consent, vm::submit, vm::loadBook, vm::proposeFreely, onDone)
    }
}

@Composable
internal fun DonationContent(state: DonationState, padding: PaddingValues = PaddingValues(), onEdit: (String, String) -> Unit,
                             onConsent: (Boolean) -> Unit, onSubmit: () -> Unit, onReload: () -> Unit,
                             onFree: () -> Unit, onDone: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (state.submitted) {
            item { Text(stringResource(R.string.donation_success), style = MaterialTheme.typography.headlineSmall) }
            item { Text(stringResource(R.string.donation_success_body)) }
            item { PrimaryButton(stringResource(R.string.donation_done), onDone, Modifier.fillMaxWidth()) }
        } else {
            item { Text(stringResource(R.string.donation_contact), style = MaterialTheme.typography.bodyLarge) }
            if (state.loadingBook) item { CircularProgressIndicator() }
            item { PinakesTextField(state.title, { onEdit("title", it) }, stringResource(R.string.donation_book_title), Modifier.fillMaxWidth(), maxLength = 255,
                enabled = !state.sending && !state.uncertain && !state.loadingBook, readOnly = state.bookId != null) }
            item { PinakesTextField(state.author, { onEdit("author", it) }, stringResource(R.string.donation_author), Modifier.fillMaxWidth(), maxLength = 255, enabled = !state.sending && !state.uncertain && !state.loadingBook) }
            item { PinakesTextField(state.publisher, { onEdit("publisher", it) }, stringResource(R.string.book_meta_publisher), Modifier.fillMaxWidth(), maxLength = 255, enabled = !state.sending && !state.uncertain && !state.loadingBook) }
            item { PinakesTextField(state.isbn, { onEdit("isbn", it) }, "ISBN", Modifier.fillMaxWidth(), maxLength = 20, enabled = !state.sending && !state.uncertain && !state.loadingBook) }
            item { PinakesTextField(state.notes, { onEdit("notes", it) }, stringResource(R.string.donation_notes), Modifier.fillMaxWidth(), maxLength = 2000, singleLine = false, enabled = !state.sending && !state.uncertain && !state.loadingBook) }
            item { Row(Modifier.fillMaxWidth().clickable(enabled = !state.sending && !state.uncertain && !state.loadingBook) { onConsent(!state.consent) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.consent, null, enabled = !state.sending && !state.uncertain && !state.loadingBook)
                Text(stringResource(R.string.donation_consent), modifier = Modifier.padding(start = Spacing.sm))
            } }
            if (state.uncertain) item { Text(stringResource(R.string.donation_uncertain), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let { error -> item { Text(error.message.ifBlank { stringResource(R.string.standalone_articles_error) }, color = MaterialTheme.colorScheme.error) } }
            if (state.bookId != null && state.error?.code in listOf("no_longer_wanted", "not_found") && !state.uncertain) item {
                SecondaryButton(stringResource(R.string.desiderata_free_offer), onFree, Modifier.fillMaxWidth())
            }
            if (state.title.isBlank() && state.bookId != null && state.error != null) item { SecondaryButton(stringResource(R.string.action_retry), onReload) }
            item { PrimaryButton(stringResource(if (state.uncertain) R.string.action_retry else R.string.donation_send), onSubmit, Modifier.fillMaxWidth(), loading = state.sending, enabled = state.canSubmit) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionScaffold(title: String, onNavigateUp: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { PinakesTopBar(title, onNavigateUp = onNavigateUp) }, content = content)
}

@Composable
private fun CollectionRow(title: String, metadata: String, cover: String?, badge: String, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BookCover(title, cover, Modifier.width(72.dp).height(108.dp), compact = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun CollectionCover(title: String, cover: String?, author: String? = null, publisher: String? = null) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BookCover(title, cover, Modifier.width(160.dp).height(240.dp), author = author, publisher = publisher) }
}

@Composable
private fun Metadata(label: String, value: String?) {
    if (!value.isNullOrBlank()) Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CollectionLinks(health: CollectionHealth?) {
    val context = LocalContext.current
    health?.let {
        it.webUrl?.let { url -> TextButton({ openWeb(context, url) }) { Text(stringResource(R.string.collection_web)) } }
        it.manageUrl?.let { url -> TextButton({ openWeb(context, url) }) { Text(stringResource(R.string.collection_manage)) } }
    }
}

@Composable
private fun <T> ListStatus(state: CollectionListState<T>, retry: () -> Unit, emptyLabel: Int) {
    when {
        state.loading -> LoadingState(label = stringResource(R.string.collection_loading))
        state.error != null -> ErrorState(state.error.message.ifBlank { stringResource(R.string.standalone_articles_error) }, onRetry = retry)
        state.items.isEmpty() -> Text(stringResource(emptyLabel), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun <T> MoreButton(state: CollectionListState<T>, more: () -> Unit) {
    if (state.nextCursor != null) {
        state.moreError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        PrimaryButton(stringResource(if (state.moreError != null) R.string.action_retry else R.string.collection_more), more, Modifier.fillMaxWidth(), loading = state.loadingMore)
    }
}

@Composable
private fun <T> DetailResult(state: ApiResult<T>?, padding: PaddingValues, retry: () -> Unit, content: @Composable ColumnScope.(T) -> Unit) {
    when (state) {
        null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingState(stringResource(R.string.collection_loading)) }
        is ApiResult.Failure -> Box(Modifier.fillMaxSize().padding(padding)) { ErrorState(state.message, onRetry = retry) }
        is ApiResult.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content(state.data) } }
        }
    }
}

@Composable
private fun archiveLevel(level: String): String = stringResource(when (level) {
    "fonds" -> R.string.archives_fonds; "series" -> R.string.archives_series; "file" -> R.string.archives_files; else -> R.string.archives_items
})

private fun archiveFieldLabel(key: String): Int = when (key) {
    "scope_content" -> R.string.archives_description; "archival_history" -> R.string.archives_history
    "access_conditions" -> R.string.archives_access; "reproduction_rules" -> R.string.archives_reproduction
    "arrangement_system" -> R.string.archives_arrangement; "finding_aids" -> R.string.archives_finding_aids
    "originals_location" -> R.string.archives_originals; "copies_location" -> R.string.archives_copies
    "related_units" -> R.string.archives_related; "institution_code" -> R.string.archives_institution
    "local_classification" -> R.string.archives_classification; "collection_name" -> R.string.archives_collection
    "photographer" -> R.string.archives_photographer; "language_codes" -> R.string.book_meta_language
    "dimensions" -> R.string.archives_dimensions; "color_mode" -> R.string.archives_color
    "publisher" -> R.string.book_meta_publisher; "predominant_dates" -> R.string.archives_predominant_dates; "date_gaps" -> R.string.archives_date_gaps
    "extent" -> R.string.archives_extent; else -> R.string.archives_description
}
