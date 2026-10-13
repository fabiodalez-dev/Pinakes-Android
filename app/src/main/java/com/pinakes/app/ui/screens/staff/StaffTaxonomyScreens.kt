package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.Shelf
import com.pinakes.app.data.model.StaffGenreNode
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Genres
// ---------------------------------------------------------------------------

/** A genre (parentId null) or subgenre being added. */
data class GenreDraft(val parentId: Int?, val parentName: String?, val name: String = "")

data class GenresUiState(
    val tree: List<StaffGenreNode> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val errorOffline: Boolean = false,
    val draft: GenreDraft? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val formErrorRes: Int? = null,
    val snackbarRes: Int? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffGenresViewModel @Inject constructor(private val staff: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(GenresUiState())
    val state: StateFlow<GenresUiState> = _state.asStateFlow()

    init { load() }

    fun load(pull: Boolean = false) {
        _state.update { it.copy(loading = !pull && it.tree.isEmpty(), refreshing = pull, error = null) }
        viewModelScope.launch {
            _state.update {
                when (val res = staff.genres()) {
                    is ApiResult.Success -> it.copy(tree = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.serverMessage() ?: "", errorOffline = res.isOffline)
                }
            }
        }
    }

    fun startDraft(parent: StaffGenreNode?) = _state.update {
        it.copy(draft = GenreDraft(parent?.id, parent?.name), formError = null, formErrorRes = null)
    }

    fun onDraftName(value: String) = _state.update { it.copy(draft = it.draft?.copy(name = value), formError = null, formErrorRes = null) }

    fun closeDraft() = _state.update { it.copy(draft = null) }

    fun submit() {
        val draft = _state.value.draft ?: return
        if (draft.name.isBlank()) {
            _state.update { it.copy(formErrorRes = R.string.staff_field_required) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val res = staff.createGenre(draft.name, draft.parentId)) {
                is ApiResult.Success -> {
                    // find-or-create: `created:false` means the genre was already there.
                    _state.update {
                        it.copy(saving = false, draft = null, snackbarRes = if (res.data.created) R.string.staff_record_saved else R.string.staff_genre_exists)
                    }
                    load(pull = true)
                }
                is ApiResult.Failure -> _state.update {
                    if (res.isForbidden()) it.copy(saving = false, forbidden = true)
                    else it.copy(saving = false, formError = res.serverMessage(), formErrorRes = R.string.staff_error_save)
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbarRes = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffGenresScreen(onNavigateUp: () -> Unit) {
    val vm: StaffGenresViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    val message = state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() } }
    var expanded by rememberSaveable { mutableStateOf(setOf<Int>()) }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_genres), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_genre_add), { vm.startDraft(null) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(if (state.errorOffline) stringResource(R.string.offline_generic) else state.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load), onRetry = { vm.load() })
                state.tree.isEmpty() -> EmptyState(
                    title = stringResource(R.string.staff_records_empty), icon = Icons.Outlined.Category,
                    actionLabel = stringResource(R.string.staff_genre_add), onAction = { vm.startDraft(null) },
                )
                else -> {
                    val rows = visibleGenreRows(state.tree, expanded)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp)) {
                        items(rows, key = { it.node.id }) { row ->
                            GenreTreeRow(
                                row = row,
                                isExpanded = row.node.id in expanded,
                                onToggle = { expanded = if (row.node.id in expanded) expanded - row.node.id else expanded + row.node.id },
                                onAddChild = { vm.startDraft(row.node) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    state.draft?.let { draft ->
        SimpleFormSheet(
            title = if (draft.parentName != null) stringResource(R.string.staff_subgenre_add_to, draft.parentName) else stringResource(R.string.staff_genre_add),
            saving = state.saving,
            error = state.formError ?: state.formErrorRes?.let { stringResource(it) },
            onSubmit = vm::submit,
            onDismiss = vm::closeDraft,
        ) {
            PinakesTextField(value = draft.name, onValueChange = vm::onDraftName, label = stringResource(R.string.staff_field_name), modifier = Modifier.fillMaxWidth())
        }
    }
}

private data class GenreRow(val node: StaffGenreNode, val depth: Int)

private fun visibleGenreRows(nodes: List<StaffGenreNode>, expanded: Set<Int>, depth: Int = 0): List<GenreRow> =
    nodes.flatMap { n ->
        listOf(GenreRow(n, depth)) + if (n.id in expanded) visibleGenreRows(n.children, expanded, depth + 1) else emptyList()
    }

@Composable
private fun GenreTreeRow(row: GenreRow, isExpanded: Boolean, onToggle: () -> Unit, onAddChild: () -> Unit) {
    val hasChildren = row.node.children.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasChildren, onClick = onToggle)
            .padding(start = (row.depth * 20).dp, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChildren) {
            Icon(
                if (isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(row.node.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onAddChild) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.staff_subgenre_add_to, row.node.name), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------
// Shelves
// ---------------------------------------------------------------------------

/** A shelf (shelfId null) or a level of [shelfId] being added. */
data class ShelfDraft(val shelfId: Int?, val shelfLabel: String?, val code: String = "", val name: String = "", val number: String = "")

data class ShelvesUiState(
    val shelves: List<Shelf> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val errorOffline: Boolean = false,
    val draft: ShelfDraft? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val formErrorRes: Int? = null,
    val snackbarRes: Int? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffShelvesViewModel @Inject constructor(private val staff: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(ShelvesUiState())
    val state: StateFlow<ShelvesUiState> = _state.asStateFlow()

    init { load() }

    fun load(pull: Boolean = false) {
        _state.update { it.copy(loading = !pull && it.shelves.isEmpty(), refreshing = pull, error = null) }
        viewModelScope.launch {
            _state.update {
                when (val res = staff.shelves()) {
                    is ApiResult.Success -> it.copy(shelves = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.serverMessage() ?: "", errorOffline = res.isOffline)
                }
            }
        }
    }

    fun startShelf() = _state.update { it.copy(draft = ShelfDraft(null, null), formError = null, formErrorRes = null) }

    fun startLevel(shelf: Shelf) = _state.update {
        val next = (shelf.levels.maxOfOrNull { l -> l.number } ?: 0) + 1
        it.copy(draft = ShelfDraft(shelf.id, shelf.display, number = next.toString()), formError = null, formErrorRes = null)
    }

    fun editDraft(transform: (ShelfDraft) -> ShelfDraft) = _state.update { it.copy(draft = it.draft?.let(transform), formError = null, formErrorRes = null) }

    fun closeDraft() = _state.update { it.copy(draft = null) }

    fun submit() {
        val d = _state.value.draft ?: return
        val isLevel = d.shelfId != null
        val number = d.number.trim().toIntOrNull()
        if ((isLevel && (number == null || number <= 0)) || (!isLevel && d.code.isBlank())) {
            _state.update { it.copy(formErrorRes = R.string.staff_field_required) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res: ApiResult<Any> = if (isLevel) staff.createLevel(d.shelfId!!, number!!, d.name)
            else staff.createShelf(d.code, d.name, null)
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, draft = null, snackbarRes = R.string.staff_record_saved) }
                    load(pull = true)
                }
                is ApiResult.Failure -> _state.update {
                    when {
                        res.isForbidden() -> it.copy(saving = false, forbidden = true)
                        res.isDuplicate() || res.httpStatus == 409 ->
                            it.copy(saving = false, formErrorRes = if (isLevel) R.string.staff_level_exists else R.string.staff_shelf_exists)
                        else -> it.copy(saving = false, formError = res.serverMessage(), formErrorRes = R.string.staff_error_save)
                    }
                }
            }
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbarRes = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffShelvesScreen(onNavigateUp: () -> Unit) {
    val vm: StaffShelvesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    val message = state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() } }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_shelves), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_shelf_add), vm::startShelf) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(if (state.errorOffline) stringResource(R.string.offline_generic) else state.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load), onRetry = { vm.load() })
                state.shelves.isEmpty() -> EmptyState(
                    title = stringResource(R.string.staff_records_empty), icon = Icons.Outlined.Inventory2,
                    actionLabel = stringResource(R.string.staff_shelf_add), onAction = vm::startShelf,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.shelves, key = { it.id }) { shelf -> ShelfCard(shelf, onAddLevel = { vm.startLevel(shelf) }) }
                }
            }
        }
    }

    state.draft?.let { d ->
        val isLevel = d.shelfId != null
        SimpleFormSheet(
            title = if (isLevel) stringResource(R.string.staff_level_add_to, d.shelfLabel.orEmpty()) else stringResource(R.string.staff_shelf_add),
            saving = state.saving,
            error = state.formError ?: state.formErrorRes?.let { stringResource(it) },
            onSubmit = vm::submit,
            onDismiss = vm::closeDraft,
        ) {
            if (isLevel) {
                PinakesTextField(
                    value = d.number, onValueChange = { v -> vm.editDraft { it.copy(number = v.filter(Char::isDigit)) } },
                    label = stringResource(R.string.staff_field_level_number), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), maxLength = 4,
                )
                PinakesTextField(value = d.name, onValueChange = { v -> vm.editDraft { it.copy(name = v) } },
                    label = stringResource(R.string.staff_field_description), modifier = Modifier.fillMaxWidth())
            } else {
                PinakesTextField(value = d.code, onValueChange = { v -> vm.editDraft { it.copy(code = v) } },
                    label = stringResource(R.string.staff_field_shelf_code), modifier = Modifier.fillMaxWidth(), maxLength = 20)
                PinakesTextField(value = d.name, onValueChange = { v -> vm.editDraft { it.copy(name = v) } },
                    label = stringResource(R.string.staff_field_name), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ShelfCard(shelf: Shelf, onAddLevel: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Spacing.md))
                Text(shelf.display, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (shelf.levels.isEmpty()) {
                Text(stringResource(R.string.staff_shelf_no_levels), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            } else {
                shelf.levels.sortedBy { it.number }.forEach { level ->
                    Text(
                        listOfNotNull(stringResource(R.string.staff_level_number, level.number), level.description?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            PinakesTextButton(stringResource(R.string.staff_level_add), onClick = onAddLevel)
        }
    }
}

/** Bottom sheet with a title, a few fields and a save button (genre, shelf, level). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleFormSheet(
    title: String,
    saving: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (error != null) InlineErrorBanner(message = error)
            content()
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(stringResource(R.string.action_save), onClick = onSubmit, loading = saving, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
