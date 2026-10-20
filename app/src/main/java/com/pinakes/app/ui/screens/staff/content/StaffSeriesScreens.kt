package com.pinakes.app.ui.screens.staff.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.SeriesBook
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.StaffSeries
import com.pinakes.app.data.model.StaffSeriesDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.SeriesTree
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The series types of the web form, used when the server's lookups predate `series_types`. */
private val DEFAULT_SERIES_TYPES = listOf("serie", "universo", "ciclo", "stagione", "spin_off", "arco", "collezione_editoriale", "altro")

/** Series form (create and edit). Numbers stay text until sent. */
data class SeriesForm(
    val name: String = "",
    val type: String = "serie",
    val parentName: String = "",
    val group: String = "",
    val cycle: String = "",
    val cycleOrder: String = "",
    val description: String = "",
    val complete: Boolean = false,
) {
    val cycleOrderValid: Boolean get() = cycleOrder.isBlank() || cycleOrder.trim().toIntOrNull()?.let { it in 0..65535 } == true
    val valid: Boolean get() = name.isNotBlank() && name.trim().length <= 100 && cycleOrderValid

    /**
     * Every key is sent so an edit can empty a field: cleared text goes as "", a cleared order
     * as JSON null (an absent key would keep the stored value on the server).
     */
    fun toJson(): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("name", kotlinx.serialization.json.JsonPrimitive(name.trim()))
        put("type", type.ifBlank { null }?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("parent_name", kotlinx.serialization.json.JsonPrimitive(parentName.trim()))
        put("group", kotlinx.serialization.json.JsonPrimitive(group.trim()))
        put("cycle", kotlinx.serialization.json.JsonPrimitive(cycle.trim()))
        put("cycle_order", cycleOrder.trim().toIntOrNull()?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("description", kotlinx.serialization.json.JsonPrimitive(description.trim()))
        put("complete", kotlinx.serialization.json.JsonPrimitive(complete))
    }

    companion object {
        fun from(d: StaffSeriesDetail) = SeriesForm(
            name = d.name, type = d.type.orEmpty(), parentName = d.parentName.orEmpty(), group = d.group.orEmpty(), cycle = d.cycle.orEmpty(),
            cycleOrder = d.cycleOrder?.toString().orEmpty(), description = d.description.orEmpty(), complete = d.complete,
        )
    }
}

internal fun seriesTypeOptions(lookups: StaffLookups): List<LookupOption> =
    lookups.seriesTypeOptions.ifEmpty { DEFAULT_SERIES_TYPES.map { LookupOption(it, it.replace('_', ' ').replaceFirstChar(Char::uppercase)) } }

// =============================================================================================
// List
// =============================================================================================

data class SeriesListUiState(
    val roots: List<StaffSeries> = emptyList(),
    val query: String = "",
    val expanded: Set<Int> = emptySet(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val lookups: StaffLookups = StaffLookups(),
    val creating: SeriesForm? = null,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val duplicate: IdName? = null,
    val createdId: Int? = null,
)

@HiltViewModel
class StaffSeriesViewModel @Inject constructor(
    private val content: StaffContentRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesListUiState())
    val state: StateFlow<SeriesListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { (staff.lookups() as? ApiResult.Success)?.data?.let { l -> _state.update { it.copy(lookups = l) } } }
    }

    fun reload() {
        _state.update { it.copy(loading = it.roots.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = content.series()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(roots = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }
    fun toggle(id: Int) = _state.update { s -> s.copy(expanded = if (id in s.expanded) s.expanded - id else s.expanded + id) }

    fun startCreate() = _state.update { it.copy(creating = SeriesForm(name = it.query.trim()), formError = null) }
    fun edit(transform: (SeriesForm) -> SeriesForm) = _state.update { s -> s.copy(creating = s.creating?.let(transform), formError = null) }
    fun closeCreate() = _state.update { it.copy(creating = null, duplicate = null) }

    fun create() {
        val form = _state.value.creating ?: return
        if (!form.valid || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res = content.saveSeries(null, form.toJson())
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(saving = false, creating = null, createdId = res.data.id)
                    is ApiResult.Failure -> when {
                        res.isForbidden() -> it.copy(saving = false, forbidden = true)
                        res.isDuplicate() -> it.copy(saving = false, duplicate = content.existingSeries(res) ?: IdName(0, form.name))
                        else -> it.copy(saving = false, formError = res.toStaffMessage(R.string.staff_error_save))
                    }
                }
            }
        }
    }

    fun consumeCreated() = _state.update { it.copy(createdId = null) }
    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }
}

/** Series as a tree (universes, cycles, spin-offs), searchable, with a create sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffSeriesScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffSeriesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.series, onNavigateUp)
    LaunchedEffect(Unit) { vm.reload() }
    LaunchedEffect(state.createdId) { state.createdId?.let { vm.consumeCreated(); onOpen(it) } }
    val rows = remember(state.roots, state.expanded, state.query) { SeriesTree.rows(state.roots, state.expanded, state.query) }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_series), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_series_new), vm::startCreate) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_series_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = rows.isEmpty(),
                    emptyTitle = stringResource(R.string.staff_series_empty), emptyIcon = Icons.Outlined.CollectionsBookmark, onRetry = vm::refresh,
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                        items(rows, key = { it.series.id }) { row ->
                            Surface(
                                onClick = { onOpen(row.series.id) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().padding(start = (row.depth * 20).dp, top = Spacing.xs, bottom = Spacing.xs),
                            ) {
                                Row(Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.series.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                seriesTypeOptions(state.lookups).firstOrNull { it.value == row.series.type }?.label,
                                                stringResource(R.string.staff_series_book_count, row.series.bookCount),
                                                if (row.series.complete) stringResource(R.string.staff_series_complete) else null,
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (row.hasChildren && state.query.isBlank()) {
                                        IconButton(onClick = { vm.toggle(row.series.id) }) {
                                            Icon(
                                                if (row.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                contentDescription = stringResource(if (row.expanded) R.string.staff_collapse else R.string.staff_expand),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.creating?.let { form ->
        SeriesFormSheet(
            title = stringResource(R.string.staff_series_new), form = form, lookups = state.lookups, saving = state.saving,
            error = state.formError, nameEditable = true, onEdit = vm::edit, onSave = vm::create, onDismiss = vm::closeCreate,
        )
    }
    state.duplicate?.let { existing ->
        ConfirmDialog(
            title = stringResource(R.string.staff_record_duplicate_title),
            body = stringResource(R.string.staff_series_exists, existing.name),
            confirmLabel = stringResource(R.string.staff_series_open_existing),
            onConfirm = { vm.dismissDuplicate(); vm.closeCreate(); if (existing.id > 0) onOpen(existing.id) },
            onDismiss = vm::dismissDuplicate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesFormSheet(
    title: String,
    form: SeriesForm,
    lookups: StaffLookups,
    saving: Boolean,
    error: StaffMessage?,
    nameEditable: Boolean,
    onEdit: ((SeriesForm) -> SeriesForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            error?.let { InlineErrorBanner(message = it.resolve()) }
            PinakesTextField(
                value = form.name, onValueChange = { v -> onEdit { it.copy(name = v) } }, label = stringResource(R.string.staff_field_name),
                maxLength = 100, enabled = nameEditable, modifier = Modifier.fillMaxWidth(),
                supportingText = if (nameEditable) "" else stringResource(R.string.staff_series_rename_hint),
            )
            val types = seriesTypeOptions(lookups)
            LookupField(
                label = stringResource(R.string.staff_series_type), value = form.type,
                displayValue = types.firstOrNull { it.value == form.type }?.label ?: form.type, options = types,
                editable = false, allowClear = false, onValueChange = { v -> onEdit { it.copy(type = v) } }, modifier = Modifier.fillMaxWidth(),
            )
            PinakesTextField(value = form.parentName, onValueChange = { v -> onEdit { it.copy(parentName = v) } },
                label = stringResource(R.string.staff_series_parent), supportingText = stringResource(R.string.staff_series_parent_hint), modifier = Modifier.fillMaxWidth())
            PinakesTextField(value = form.group, onValueChange = { v -> onEdit { it.copy(group = v) } }, label = stringResource(R.string.staff_series_group), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PinakesTextField(value = form.cycle, onValueChange = { v -> onEdit { it.copy(cycle = v) } }, label = stringResource(R.string.staff_series_cycle), modifier = Modifier.weight(2f))
                PinakesTextField(
                    value = form.cycleOrder, onValueChange = { v -> onEdit { it.copy(cycleOrder = v.filter(Char::isDigit)) } },
                    label = stringResource(R.string.staff_series_cycle_order), isError = !form.cycleOrderValid, errorText = stringResource(R.string.staff_field_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 5, modifier = Modifier.weight(1f),
                )
            }
            PinakesTextField(value = form.description, onValueChange = { v -> onEdit { it.copy(description = v) } },
                label = stringResource(R.string.staff_field_description), singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
            SwitchRow(stringResource(R.string.staff_series_complete), form.complete, { v -> onEdit { it.copy(complete = v) } })
            PrimaryButton(stringResource(R.string.action_save), onClick = onSave, loading = saving, enabled = form.valid, modifier = Modifier.fillMaxWidth().testTag("series-save"))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// =============================================================================================
// Detail
// =============================================================================================

enum class SeriesDialog { RENAME, MERGE, DELETE, PARENT_WORK, ADD_BOOKS, EDIT }

data class SeriesDetailUiState(
    val detail: StaffSeriesDetail? = null,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val lookups: StaffLookups = StaffLookups(),
    val dialog: SeriesDialog? = null,
    val form: SeriesForm? = null,
    val busy: Boolean = false,
    val formError: StaffMessage? = null,
    /** Rename target that already exists: ask before merging into it. */
    val renameClash: Pair<String, IdName>? = null,
    val numbering: SeriesBook? = null,
    val mergeTargets: List<StaffSeries> = emptyList(),
    val selectedBooks: Set<Int> = emptySet(),
    val message: StaffMessage? = null,
    /** Set when the series no longer exists here (merged or deleted): the screen moves on. */
    val goneTo: Int? = null,
    val openBook: Int? = null,
)

@HiltViewModel
class StaffSeriesDetailViewModel @Inject constructor(
    private val content: StaffContentRepository,
    private val staff: StaffRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val seriesId: Int = savedState.get<Int>(Routes.ARG_SERIES_ID) ?: 0
    private val _state = MutableStateFlow(SeriesDetailUiState())
    val state: StateFlow<SeriesDetailUiState> = _state.asStateFlow()

    val books = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = staff.books(q, cursor = null, limit = 30)) {
            is ApiResult.Success -> ApiResult.Success(res.data.items.map { PickItem(it.id, it.title, it.author) })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init {
        load()
        viewModelScope.launch { (staff.lookups() as? ApiResult.Success)?.data?.let { l -> _state.update { it.copy(lookups = l) } } }
    }

    fun load() {
        viewModelScope.launch {
            val res = content.seriesDetail(seriesId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(detail = res.data, loading = false, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun open(dialog: SeriesDialog) {
        val d = _state.value.detail ?: return
        _state.update { it.copy(dialog = dialog, formError = null, form = if (dialog == SeriesDialog.EDIT) SeriesForm.from(d) else null, selectedBooks = emptySet()) }
        if (dialog == SeriesDialog.MERGE) viewModelScope.launch {
            (content.series() as? ApiResult.Success)?.data?.let { roots ->
                val excluded = SeriesTree.subtreeIds(roots, d.id)
                _state.update { it.copy(mergeTargets = SeriesTree.flatten(roots).filter { s -> s.id !in excluded }) }
            }
        }
    }

    fun close() {
        _state.update { it.copy(dialog = null, form = null, formError = null, renameClash = null, numbering = null) }
        books.clear()
    }

    fun editForm(transform: (SeriesForm) -> SeriesForm) = _state.update { s -> s.copy(form = s.form?.let(transform), formError = null) }

    fun saveEdit() {
        val form = _state.value.form ?: return
        if (!form.valid) return
        mutate(R.string.staff_series_saved) { content.saveSeries(seriesId, form.toJson()) }
    }

    fun rename(name: String, merge: Boolean = false) {
        val clean = name.trim()
        if (clean.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val res = content.renameSeries(seriesId, clean, merge)
            _state.update {
                when (res) {
                    is ApiResult.Success -> {
                        val merged = res.meta?.merged == true
                        it.copy(
                            busy = false, dialog = null, renameClash = null, detail = res.data,
                            message = StaffMessage(if (merged) R.string.staff_series_merged else R.string.staff_series_renamed),
                            goneTo = if (merged && res.data.id != seriesId) res.data.id else null,
                        )
                    }
                    is ApiResult.Failure -> when {
                        res.isForbidden() -> it.copy(busy = false, forbidden = true)
                        res.isDuplicate() -> it.copy(busy = false, renameClash = clean to (content.existingSeries(res) ?: IdName(0, clean)))
                        else -> it.copy(busy = false, formError = res.toStaffMessage(R.string.staff_error_save))
                    }
                }
            }
        }
    }

    fun merge(targetId: Int) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = content.mergeSeries(seriesId, targetId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, dialog = null, goneTo = res.data.target.id.takeIf { id -> id > 0 } ?: targetId,
                        message = StaffMessage(R.string.staff_series_merged_count, args = listOf(res.data.booksMoved)))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun delete() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = content.deleteSeries(seriesId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, dialog = null, goneTo = 0)
                    is ApiResult.Failure -> it.copy(busy = false, dialog = null, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun parentWork(title: String) {
        if (title.isBlank() || _state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val res = content.parentWork(seriesId, title)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, dialog = null, openBook = res.data.bookId.takeIf { id -> id > 0 },
                        message = StaffMessage(R.string.staff_series_parent_work_created))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) load()
        }
    }

    fun toggleBook(item: PickItem) = _state.update { s ->
        s.copy(selectedBooks = if (item.id in s.selectedBooks) s.selectedBooks - item.id else s.selectedBooks + item.id)
    }

    fun assignSelected() {
        val ids = _state.value.selectedBooks.toList()
        if (ids.isEmpty() || _state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = content.assignBooks(seriesId, ids)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, dialog = null, selectedBooks = emptySet(),
                        message = StaffMessage(R.string.staff_series_books_added, args = listOf(res.data.assigned)))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            books.clear()
            if (res is ApiResult.Success) load()
        }
    }

    fun openNumbering(book: SeriesBook) = _state.update { it.copy(numbering = book) }

    fun setNumber(book: SeriesBook, number: String) =
        mutate(R.string.staff_series_number_saved) { content.setSeriesNumber(seriesId, book.id, number) }

    fun removeBook(book: SeriesBook) =
        mutate(R.string.staff_series_book_removed) { content.removeFromSeries(seriesId, book.id) }

    private fun mutate(success: Int, call: suspend () -> ApiResult<StaffSeriesDetail>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val res = call()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, detail = res.data, dialog = null, form = null, numbering = null, message = StaffMessage(success))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save),
                        message = if (it.dialog == null && it.numbering == null) res.toStaffMessage(R.string.staff_error_save) else null)
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeGone() = _state.update { it.copy(goneTo = null) }
    fun consumeOpenBook() = _state.update { it.copy(openBook = null) }
}

/** A series: facts, its books in order (number, remove), and rename / merge / delete / parent work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffSeriesDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    val vm: StaffSeriesDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.series, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.goneTo) {
        when (val target = state.goneTo) {
            null -> Unit
            0 -> { vm.consumeGone(); onNavigateUp() }
            else -> { vm.consumeGone(); onOpenSeries(target) }
        }
    }
    LaunchedEffect(state.openBook) { state.openBook?.let { vm.consumeOpenBook(); onOpenBook(it) } }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = state.detail?.name ?: stringResource(R.string.staff_series),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.detail != null) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.staff_more_actions)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            listOf(
                                SeriesDialog.EDIT to R.string.staff_series_edit, SeriesDialog.RENAME to R.string.staff_series_rename,
                                SeriesDialog.MERGE to R.string.staff_series_merge, SeriesDialog.PARENT_WORK to R.string.staff_series_parent_work,
                                SeriesDialog.DELETE to R.string.staff_series_delete,
                            ).forEach { (dialog, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; vm.open(dialog) })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = { if (state.detail != null) StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_series_add_books)) { vm.open(SeriesDialog.ADD_BOOKS) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val d = state.detail
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            d == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                item(key = "facts") {
                    InfoCard(Modifier.padding(top = Spacing.sm)) {
                        InfoLine(stringResource(R.string.staff_series_type), seriesTypeOptions(state.lookups).firstOrNull { it.value == d.type }?.label ?: d.type)
                        InfoLine(stringResource(R.string.staff_series_parent), d.parentName)
                        InfoLine(stringResource(R.string.staff_series_group), d.group)
                        InfoLine(stringResource(R.string.staff_series_cycle), listOfNotNull(d.cycle, d.cycleOrder?.toString()).joinToString(" · ").ifBlank { null })
                        InfoLine(stringResource(R.string.staff_series_numbers), listOfNotNull(d.minNumber, d.maxNumber).distinct().joinToString("–").ifBlank { null })
                        InfoLine(stringResource(R.string.staff_series_complete), if (d.complete) stringResource(R.string.staff_yes) else null)
                        d.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm)) }
                    }
                }
                if (d.children.isNotEmpty()) {
                    item(key = "children-title") { FormSectionTitle(stringResource(R.string.staff_series_children)) }
                    items(d.children, key = { "c${it.id}" }) { child ->
                        SecondaryButton(child.name, onClick = { onOpenSeries(child.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
                    }
                }
                item(key = "books-title") { FormSectionTitle(stringResource(R.string.staff_series_books, d.books.size)) }
                items(d.books, key = { "b${it.id}" }) { book ->
                    MediaRow(
                        title = book.title, coverUrl = book.coverUrl,
                        line1 = book.seriesNumber?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.staff_series_number_value, it) }
                            ?: stringResource(R.string.staff_series_no_number),
                        line2 = book.year, onClick = { vm.openNumbering(book) },
                        modifier = Modifier.padding(vertical = Spacing.xs).testTag("series-book-${book.id}"),
                    )
                }
            }
        }
    }

    val d = state.detail ?: return
    when (state.dialog) {
        SeriesDialog.EDIT -> state.form?.let { form ->
            SeriesFormSheet(
                title = stringResource(R.string.staff_series_edit), form = form, lookups = state.lookups, saving = state.busy, error = state.formError,
                nameEditable = false, onEdit = vm::editForm, onSave = vm::saveEdit, onDismiss = vm::close,
            )
        }
        SeriesDialog.RENAME -> TextInputDialog(
            title = stringResource(R.string.staff_series_rename), label = stringResource(R.string.staff_field_name), initial = d.name,
            confirm = stringResource(R.string.staff_series_rename), busy = state.busy, error = state.formError,
            onConfirm = { vm.rename(it) }, onDismiss = vm::close,
        )
        SeriesDialog.PARENT_WORK -> TextInputDialog(
            title = stringResource(R.string.staff_series_parent_work), label = stringResource(R.string.staff_field_title), initial = d.name,
            body = stringResource(R.string.staff_series_parent_work_body), confirm = stringResource(R.string.staff_series_parent_work_create),
            busy = state.busy, error = state.formError, onConfirm = vm::parentWork, onDismiss = vm::close,
        )
        SeriesDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.staff_series_delete), body = stringResource(R.string.staff_series_delete_body, d.name, d.books.size),
            confirmLabel = stringResource(R.string.staff_series_delete), onConfirm = vm::delete, onDismiss = vm::close,
        )
        SeriesDialog.MERGE -> MergeTargetSheet(state, vm)
        SeriesDialog.ADD_BOOKS -> {
            val s by vm.books.state.collectAsStateWithLifecycle()
            SearchPickerSheet(
                title = stringResource(R.string.staff_series_add_books), placeholder = stringResource(R.string.staff_books_search), state = s,
                onQuery = vm.books::onQuery, onPick = vm::toggleBook, onDismiss = vm::close, selected = state.selectedBooks,
                onConfirm = vm::assignSelected, hint = stringResource(R.string.staff_search_min_chars),
            )
        }
        null -> Unit
    }
    state.renameClash?.let { (name, existing) ->
        ConfirmDialog(
            title = stringResource(R.string.staff_record_duplicate_title),
            body = stringResource(R.string.staff_series_rename_clash, name),
            confirmLabel = stringResource(R.string.staff_series_merge_into, existing.name.ifBlank { name }),
            onConfirm = { vm.rename(name, merge = true) }, onDismiss = vm::close,
        )
    }
    state.numbering?.let { book -> NumberDialog(book, state, vm, onOpenBook) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeTargetSheet(state: SeriesDetailUiState, vm: StaffSeriesDetailViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<StaffSeries?>(null) }
    ModalBottomSheet(onDismissRequest = vm::close, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.staff_series_merge), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff_series_merge_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            SearchField(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.staff_series_search), modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(state.mergeTargets.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }, key = { it.id }) { s ->
                    Surface(
                        onClick = { target = s }, shape = MaterialTheme.shapes.medium,
                        color = if (target?.id == s.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    ) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
            PrimaryButton(
                target?.let { stringResource(R.string.staff_series_merge_into, it.name) } ?: stringResource(R.string.staff_series_merge),
                onClick = { target?.let { vm.merge(it.id) } }, enabled = target != null, loading = state.busy, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun NumberDialog(book: SeriesBook, state: SeriesDetailUiState, vm: StaffSeriesDetailViewModel, onOpenBook: (Int) -> Unit) {
    var number by rememberSaveable(book.id) { mutableStateOf(book.seriesNumber.orEmpty()) }
    AlertDialog(
        onDismissRequest = vm::close,
        shape = MaterialTheme.shapes.large,
        title = { Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.formError?.let { InlineErrorBanner(message = it.resolve()) }
                PinakesTextField(value = number, onValueChange = { number = it }, label = stringResource(R.string.staff_field_series_number), maxLength = 50,
                    supportingText = stringResource(R.string.staff_series_number_hint), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PinakesTextButton(stringResource(R.string.staff_series_remove_book), onClick = { vm.removeBook(book) })
                    Spacer(Modifier.width(Spacing.xs))
                    PinakesTextButton(stringResource(R.string.staff_open_book), onClick = { vm.close(); onOpenBook(book.id) })
                }
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.action_save), onClick = { vm.setNumber(book, number) }, loading = state.busy) },
        dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = vm::close) },
    )
}

/** A one-field dialog (rename, parent work title). */
@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    busy: Boolean,
    error: StaffMessage?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                error?.let { InlineErrorBanner(message = it.resolve()) }
                PinakesTextField(value = value, onValueChange = { value = it }, label = label, maxLength = 255, modifier = Modifier.fillMaxWidth().testTag("text-input"))
            }
        },
        confirmButton = { PrimaryButton(confirm, onClick = { onConfirm(value) }, enabled = value.isNotBlank(), loading = busy) },
        dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
