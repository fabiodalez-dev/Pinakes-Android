package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffBookRow
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffBooksUiState(
    val query: String = "",
    val items: List<StaffBookRow> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** The load failed for lack of a connection (localized offline text, never the exception). */
    val errorOffline: Boolean = false,
    val deletingId: Int? = null,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffBooksViewModel @Inject constructor(private val staff: StaffRepository) : ViewModel() {

    private val _state = MutableStateFlow(StaffBooksUiState())
    val state: StateFlow<StaffBooksUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    init { reload() }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            reload()
        }
    }

    fun refresh() = reload(pull = true)

    fun reload(pull: Boolean = false) {
        val gen = ++generation
        // A reload supersedes any page in flight (its result is dropped by the generation
        // check), so the pagination flag must be cleared here or loadMore() stays blocked.
        _state.update { it.copy(loading = !pull && it.items.isEmpty(), refreshing = pull, error = null, loadingMore = false) }
        viewModelScope.launch {
            val res = staff.books(_state.value.query, cursor = null)
            if (gen != generation) return@launch
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(items = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        forbidden = res.isForbidden(),
                        error = if (it.items.isEmpty()) res.serverMessage() ?: "" else null,
                        errorOffline = res.isOffline,
                        snackbar = if (it.items.isNotEmpty()) res.serverMessage() else null,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val res = staff.books(s.query, cursor = cursor)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        items = (it.items + res.data.items).distinctBy { b -> b.id },
                        nextCursor = res.data.nextCursor,
                        loadingMore = false,
                    )
                    is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden())
                }
            }
        }
    }

    fun delete(id: Int) {
        if (_state.value.deletingId != null) return
        _state.update { it.copy(deletingId = id) }
        viewModelScope.launch {
            when (val res = staff.deleteBook(id)) {
                is ApiResult.Success -> _state.update {
                    it.copy(items = it.items.filterNot { b -> b.id == id }, deletingId = null, snackbarRes = R.string.staff_book_deleted)
                }
                is ApiResult.Failure -> _state.update {
                    when {
                        res.isForbidden() -> it.copy(deletingId = null, forbidden = true)
                        res.httpStatus == 409 || res.code == ErrorCodes.CONFLICT ->
                            it.copy(deletingId = null, snackbarRes = R.string.staff_book_delete_active_circulation)
                        res.code == ErrorCodes.NOT_FOUND ->
                            it.copy(deletingId = null, items = it.items.filterNot { b -> b.id == id })
                        else -> it.copy(deletingId = null, snackbar = res.serverMessage(), snackbarRes = R.string.staff_error_save)
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }
}

/** Staff list of every catalogue record (including hidden ones), with edit and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffBooksScreen(onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit, onNewBook: () -> Unit) {
    val vm: StaffBooksViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<StaffBookRow?>(null) }
    val listState = rememberLazyListState()
    ForbiddenExit(state.forbidden, onNavigateUp)

    // Coming back from the edit screen: show the saved changes.
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedOnce) vm.reload() else resumedOnce = true
        onPauseOrDispose { }
    }

    val message = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() } }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.nextCursor) { if (shouldLoadMore) vm.loadMore() }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_books), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_add_manual), onNewBook) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                onSearch = { vm.reload() },
                placeholder = stringResource(R.string.staff_books_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingState()
                    state.error != null -> ErrorState(
                        message = if (state.errorOffline) stringResource(R.string.offline_generic)
                        else state.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load),
                        onRetry = { vm.reload() },
                    )
                    state.items.isEmpty() -> EmptyState(
                        title = stringResource(R.string.staff_books_empty),
                        icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                        actionLabel = stringResource(R.string.staff_add_manual),
                        onAction = onNewBook,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.items, key = { it.id }) { book ->
                            StaffBookListRow(
                                book = book,
                                deleting = state.deletingId == book.id,
                                onOpen = { onOpenBook(book.id) },
                                onDelete = { confirmDelete = book },
                            )
                        }
                        if (state.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { book ->
        ConfirmDialog(
            title = stringResource(R.string.staff_book_delete),
            body = stringResource(R.string.staff_book_delete_confirm, book.title),
            confirmLabel = stringResource(R.string.staff_book_delete),
            onConfirm = { vm.delete(book.id); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun StaffBookListRow(book: StaffBookRow, deleting: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val copies = stringResource(R.string.staff_copies_count, book.copiesAvailable, book.copiesTotal)
    MediaRow(
        title = book.title,
        coverUrl = book.coverUrl,
        line1 = book.author,
        line2 = listOfNotNull(copies, book.isbn13?.takeIf { it.isNotBlank() }).joinToString(" · "),
        // Keep the "wanted by the library" badge the public catalogue shows.
        status = if (book.isWanted) AvailabilityStatus.Digital else null,
        statusLabel = if (book.isWanted) stringResource(R.string.desiderata_wanted) else null,
        onClick = onOpen,
        trailing = {
            Box {
                IconButton(onClick = { menu = true }, enabled = !deleting) {
                    if (deleting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.staff_book_edit)) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.staff_book_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
    )
}
