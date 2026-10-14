package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffBookRow
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.ui.components.BookCard
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MetadataRow
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.theme.CardPadding
import com.pinakes.app.ui.theme.Fraunces
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Name, facts and record dates of an author or publisher, before its books are loaded. */
data class RecordHead(val name: String, val facts: List<RecordFact>, val booksCount: Int?, val createdAt: String?, val updatedAt: String?)

/** One labelled fact of the info card (label resource + value). */
data class RecordFact(val labelRes: Int, val value: String)

data class RecordDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val errorOffline: Boolean = false,
    val name: String = "",
    val facts: List<RecordFact> = emptyList(),
    val books: List<StaffBookRow> = emptyList(),
    /** True when the list stopped at [MAX_PAGES] pages: the count is a lower bound ("50+"). */
    val booksTruncated: Boolean = false,
    /** The server's own count when it sends one (newer servers), else the loaded list size. */
    val booksCount: Int? = null,
    val forbidden: Boolean = false,
) {
    val displayedCount: Int get() = booksCount ?: books.size
}

/**
 * Author or publisher detail: a padded info card (books count, record facts, created/updated
 * when the server sends them) and the record's books with whole covers. Nav args: `recordId`
 * plus the route decides the kind ([RecordKind]).
 */
@HiltViewModel
class StaffRecordDetailViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val staff: StaffRepository,
) : ViewModel() {
    private val id: Int = saved.get<Int>(Routes.ARG_RECORD_ID) ?: 0
    val recordId: Int get() = id
    private val _state = MutableStateFlow(RecordDetailUiState())
    val state: StateFlow<RecordDetailUiState> = _state.asStateFlow()
    private var kind: RecordKind = RecordKind.AUTHOR

    fun start(kind: RecordKind) {
        if (this.kind == kind && !_state.value.loading) return
        this.kind = kind
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.name.isBlank(), error = null) }
        viewModelScope.launch {
            val record: ApiResult<RecordHead> = when (kind) {
                RecordKind.AUTHOR -> when (val res = staff.author(id)) {
                    is ApiResult.Success -> res.data.let { a ->
                        val life = listOfNotNull(a.born?.takeIf { it.isNotBlank() }, a.died?.takeIf { it.isNotBlank() }).joinToString(" – ")
                        ApiResult.Success(RecordHead(a.name, listOfNotNull(
                            a.pseudonym?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_pseudonym, it) },
                            life.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_record_life, it) },
                            a.nationality?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_nationality, it) },
                            a.website?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_website, it) },
                            a.gndId?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_gnd, it) },
                        ), a.booksCount, a.createdAt, a.updatedAt))
                    }
                    is ApiResult.Failure -> res
                }
                RecordKind.PUBLISHER -> when (val res = staff.publisher(id)) {
                    is ApiResult.Success -> res.data.let { p ->
                        ApiResult.Success(RecordHead(p.name, listOfNotNull(
                            p.website?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_website, it) },
                            p.address?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_address, it) },
                            p.phone?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_phone, it) },
                            p.email?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_field_email, it) },
                        ), p.booksCount, p.createdAt, p.updatedAt))
                    }
                    is ApiResult.Failure -> res
                }
            }
            when (record) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, forbidden = record.isForbidden(), error = record.serverMessage() ?: "", errorOffline = record.isOffline)
                }
                is ApiResult.Success -> {
                    val head = record.data
                    val name = head.name
                    val dated = head.facts + listOfNotNull(
                        head.createdAt?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_record_created, DateFormat.dateTime(it)) },
                        head.updatedAt?.takeIf { it.isNotBlank() }?.let { RecordFact(R.string.staff_record_updated, DateFormat.dateTime(it)) },
                    )
                    val count = head.booksCount
                    val (books, truncated) = loadBooks(name)
                    _state.update {
                        it.copy(loading = false, name = name, facts = dated, booksCount = count, books = books, booksTruncated = truncated)
                    }
                }
            }
        }
    }

    /** Every page of the record's books (bounded): authors by id, publishers by name. */
    private suspend fun loadBooks(name: String): Pair<List<StaffBookRow>, Boolean> {
        val out = ArrayList<StaffBookRow>()
        var cursor: String? = null
        var pages = 0
        do {
            val res = if (kind == RecordKind.AUTHOR) staff.books(null, cursor, limit = 50, authorId = id)
            else staff.books(null, cursor, limit = 50, publisher = name)
            if (res !is ApiResult.Success) break
            out += res.data.items
            cursor = res.data.nextCursor
        } while (cursor != null && ++pages < MAX_PAGES)
        return out.distinctBy { it.id } to (cursor != null)
    }

    private companion object {
        const val MAX_PAGES = 10
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffRecordDetailScreen(kind: RecordKind, onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: StaffRecordDetailViewModel = hiltViewModel()
    // The list ViewModel of the same kind drives the shared editor sheet.
    val editor: RecordsViewModel = if (kind == RecordKind.AUTHOR) hiltViewModel<StaffAuthorsViewModel>() else hiltViewModel<StaffPublishersViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(kind) { vm.start(kind) }
    LaunchedEffect(editorState.savedCount) { if (editorState.savedCount > 0) vm.load() }
    val message = editorState.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); editor.consumeSnackbar() } }
    ForbiddenExit(state.forbidden || editorState.forbidden, onNavigateUp)

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(if (kind == RecordKind.AUTHOR) R.string.staff_record_author else R.string.staff_record_publisher),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { editor.startEdit(vm.recordId) }, enabled = !state.loading && state.error == null) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.staff_record_edit))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = if (state.errorOffline) stringResource(R.string.offline_generic)
                    else state.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load),
                    onRetry = vm::load,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item(key = "card") { RecordInfoCard(state) }
                    item(key = "books-title") { FormSectionTitle(stringResource(R.string.staff_books)) }
                    if (state.books.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                title = stringResource(R.string.staff_record_no_books),
                                icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    items(state.books, key = { it.id }) { book ->
                        // BookCard draws the whole cover (ContentScale.Fit in a 2:3 box): never cropped.
                        BookCard(
                            title = book.title,
                            author = book.author.orEmpty(),
                            coverUrl = book.coverUrl,
                            status = null,
                            year = book.year?.toString(),
                            publisher = book.publisher,
                            subtitle = book.subtitle,
                            mediaType = book.mediaType,
                            onClick = { onOpenBook(book.id) },
                        )
                    }
                }
            }
        }
    }
    RecordDialogs(editorState, editor)
}

@Composable
private fun RecordInfoCard(state: RecordDetailUiState) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("record-info-card"),
    ) {
        Column(Modifier.padding(CardPadding)) {
            Text(state.name, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Fraunces), color = MaterialTheme.colorScheme.onSurface)
            val count = state.displayedCount.toString() + if (state.booksTruncated && state.booksCount == null) "+" else ""
            MetadataRow(stringResource(R.string.staff_record_books_count), count)
            state.facts.forEach { fact ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MetadataRow(stringResource(fact.labelRes), fact.value)
            }
        }
    }
}
