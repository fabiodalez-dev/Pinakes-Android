package com.pinakes.app.ui.screens.staff.s090.stocktake

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.Staff090StocktakeRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The list's status filter; [wire] null = every session. */
enum class StocktakeStatusFilter(val wire: String?, val label: Int) {
    OPEN("open", R.string.staff090_stocktake_filter_open),
    CLOSED("closed", R.string.staff090_stocktake_filter_closed),
    CANCELLED("cancelled", R.string.staff090_stocktake_filter_cancelled),
    ALL(null, R.string.staff090_stocktake_filter_all),
}

/** Which picker of the create sheet is open (the sheet steps aside meanwhile). */
enum class StocktakePicker { SHELF, GENRE }

data class StocktakeSessionsUiState(
    val filter: StocktakeStatusFilter = StocktakeStatusFilter.OPEN,
    val items: List<Staff090StocktakeSession> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val options: Staff090StocktakeOptions? = null,
    /** The create sheet's form; null = closed. */
    val creating: Staff090StocktakeForm? = null,
    val picker: StocktakePicker? = null,
    val pickerQuery: String = "",
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val createdId: Int? = null,
    val message: StaffMessage? = null,
)

/** Stocktake sessions by status, with the create sheet built from `/options`. */
@HiltViewModel
class StocktakeSessionsViewModel @Inject constructor(private val repo: Staff090StocktakeRepository) : ViewModel() {
    private val _state = MutableStateFlow(StocktakeSessionsUiState())
    val state: StateFlow<StocktakeSessionsUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()
    private var generation = 0

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = repo.sessions(_state.value.filter.wire)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val res = repo.sessions(s.filter.wire, cursor)
            if (gen != generation) {
                _state.update { it.copy(loadingMore = false) }
                return@launch
            }
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = (it.items + res.data.items).distinctBy(Staff090StocktakeSession::id), nextCursor = res.data.nextCursor, loadingMore = false)
                    is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun selectFilter(filter: StocktakeStatusFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, items = emptyList(), nextCursor = null) }
        reload()
    }

    /** Open the create sheet; the options load with it (and stay for the next one). */
    fun startCreate() {
        keys.reset()
        _state.update { it.copy(creating = Staff090StocktakeForm(), formError = null) }
        if (_state.value.options != null) return
        viewModelScope.launch {
            when (val res = repo.options()) {
                is ApiResult.Success -> _state.update { it.copy(options = res.data) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), formError = res.toStocktakeMessage(R.string.staff_error_load)) }
            }
        }
    }

    fun edit(change: (Staff090StocktakeForm) -> Staff090StocktakeForm) = _state.update { s -> s.copy(creating = s.creating?.let(change), formError = null) }

    fun closeCreate() = _state.update { it.copy(creating = null, picker = null, saving = false, formError = null) }

    fun openPicker(picker: StocktakePicker) = _state.update { it.copy(picker = picker, pickerQuery = "") }
    fun closePicker() = _state.update { it.copy(picker = null, pickerQuery = "") }
    fun setPickerQuery(value: String) = _state.update { it.copy(pickerQuery = value) }

    fun pick(item: PickItem) {
        when (_state.value.picker) {
            StocktakePicker.SHELF -> edit { it.withShelf(item.id) }
            StocktakePicker.GENRE -> edit { it.copy(genreId = item.id) }
            null -> Unit
        }
        closePicker()
    }

    /** Open the session under one submission key: a retry after a dropped connection replays it, never a second session. */
    fun create() {
        val s = _state.value
        val form = s.creating ?: return
        val limits = s.options?.limits ?: return
        if (!form.valid(limits) || s.saving) return
        val request = form.request()
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val key = keys.keyFor(request)
            var res = repo.create(request, key)
            if (res is ApiResult.Failure && (res.code == ErrorCodes.NETWORK || res.code == "concurrent_retry")) res = repo.create(request, key)
            if (res is ApiResult.Success || (res as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) keys.reset()
            val result = res
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, creating = null, createdId = result.data.id)
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = result.isForbidden(), formError = result.toStocktakeMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun consumeCreated() = _state.update { it.copy(createdId = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

private val FILTERS = StocktakeStatusFilter.entries

/** Stocktake sessions (open first, then the history) with their counters; the FAB opens a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Staff090StocktakeSessionsScreen(onNavigateUp: () -> Unit, onOpenSession: (Int) -> Unit) {
    val vm: StocktakeSessionsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.stocktake, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.createdId) { state.createdId?.let { vm.consumeCreated(); onOpenSession(it) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_stocktake), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff090_stocktake_new), vm::startCreate) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = FILTERS.indexOf(state.filter).coerceAtLeast(0), edgePadding = Spacing.lg) {
                FILTERS.forEach { filter ->
                    Tab(selected = filter == state.filter, onClick = { vm.selectFilter(filter) }, text = { Text(stringResource(filter.label)) })
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff090_stocktake_empty), emptyIcon = Icons.AutoMirrored.Outlined.FactCheck, onRetry = vm::refresh,
                    emptySubtitle = stringResource(R.string.staff090_stocktake_empty_hint),
                ) {
                    val listState = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.items.size - 5 } == true } }
                    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd) vm.loadMore() }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                        items(state.items, key = { it.id }) { session -> SessionRow(session) { onOpenSession(session.id) } }
                    }
                }
            }
        }
    }

    state.creating?.let { form ->
        val options = state.options
        when (state.picker) {
            StocktakePicker.SHELF -> LocalPickerSheet(
                title = stringResource(R.string.staff090_stocktake_field_shelf), placeholder = stringResource(R.string.staff090_stocktake_search_shelf),
                items = options?.shelves.orEmpty().map { PickItem(it.id, it.label.ifBlank { it.name }) },
                query = state.pickerQuery, onQuery = vm::setPickerQuery, onPick = vm::pick, onDismiss = vm::closePicker,
            )
            StocktakePicker.GENRE -> LocalPickerSheet(
                title = stringResource(R.string.staff090_stocktake_field_genre), placeholder = stringResource(R.string.staff090_stocktake_search_genre),
                items = options?.genres.orEmpty().map { PickItem(it.id, it.label) },
                query = state.pickerQuery, onQuery = vm::setPickerQuery, onPick = vm::pick, onDismiss = vm::closePicker,
            )
            null -> CreateSheet(
                form = form, options = options, saving = state.saving, error = state.formError,
                onEdit = vm::edit, onPicker = vm::openPicker, onSave = vm::create, onDismiss = vm::closeCreate,
            )
        }
    }
}

@Composable
private fun SessionRow(session: Staff090StocktakeSession, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("session-${session.id}"),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(session.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                StocktakeBadge(session.statusLabel.ifBlank { session.status }, session.statusColor)
            }
            if (session.filterLabels.isNotEmpty()) {
                Text(session.filterLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val s = session.summary
            Text(
                stringResource(R.string.staff090_stocktake_row_counts, s.expected, s.found, s.missing, s.scans),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
            )
            val opened = listOfNotNull(stocktakeDateTime(session.openedAt).takeIf { it.isNotBlank() }, session.openedBy?.name?.takeIf { it.isNotBlank() })
            if (opened.isNotEmpty()) {
                Text(stringResource(R.string.staff090_stocktake_opened, opened.joinToString(" · ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSheet(
    form: Staff090StocktakeForm,
    options: Staff090StocktakeOptions?,
    saving: Boolean,
    error: StaffMessage?,
    onEdit: ((Staff090StocktakeForm) -> Staff090StocktakeForm) -> Unit,
    onPicker: (StocktakePicker) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff090_stocktake_new), style = MaterialTheme.typography.headlineSmall)
            error?.let { InlineErrorBanner(message = it.resolve()) }
            if (options == null) {
                if (error == null) com.pinakes.app.ui.components.LoadingState(modifier = Modifier.heightIn(max = 120.dp))
                Spacer(Modifier.height(Spacing.lg))
                return@Column
            }
            val limits = options.limits
            PinakesTextField(
                value = form.name, onValueChange = { v -> onEdit { it.copy(name = v) } }, label = stringResource(R.string.staff090_stocktake_field_name),
                maxLength = limits.maxNameLength, isError = form.name.isNotEmpty() && !form.nameValid(limits),
                errorText = stringResource(R.string.staff090_stocktake_name_required), modifier = Modifier.fillMaxWidth().testTag("stocktake-name"),
            )
            PinakesTextField(
                value = form.note, onValueChange = { v -> onEdit { it.copy(note = v) } }, label = stringResource(R.string.staff090_stocktake_field_note),
                singleLine = false, maxLength = 5000, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )
            FormSectionTitle(stringResource(R.string.staff090_stocktake_scope))
            Text(stringResource(R.string.staff090_stocktake_scope_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (options.branches.isNotEmpty()) {
                val branches = options.branches.map { LookupOption(it.id.toString(), it.name) }
                LookupField(
                    label = stringResource(R.string.staff090_stocktake_field_branch), value = form.branchId?.toString().orEmpty(),
                    displayValue = branches.firstOrNull { it.value == form.branchId?.toString() }?.label.orEmpty(), options = branches,
                    editable = false, onValueChange = { v -> onEdit { it.copy(branchId = v.toIntOrNull()) } }, modifier = Modifier.fillMaxWidth(),
                )
            }
            val shelf = options.shelves.firstOrNull { it.id == form.shelfId }
            PickerRow(
                label = stringResource(R.string.staff090_stocktake_field_shelf), value = shelf?.label?.ifBlank { shelf.name },
                placeholder = stringResource(R.string.staff090_stocktake_any_shelf), onClick = { onPicker(StocktakePicker.SHELF) },
                onClear = { onEdit { it.withShelf(null) } },
            )
            val levels = form.levels(options.shelves).map { LookupOption(it.id.toString(), stringResource(R.string.staff090_stocktake_level, it.level)) }
            if (levels.isNotEmpty()) {
                LookupField(
                    label = stringResource(R.string.staff090_stocktake_field_level), value = form.levelId?.toString().orEmpty(),
                    displayValue = levels.firstOrNull { it.value == form.levelId?.toString() }?.label.orEmpty(), options = levels,
                    editable = false, onValueChange = { v -> onEdit { it.copy(levelId = v.toIntOrNull()) } }, modifier = Modifier.fillMaxWidth(),
                )
            }
            PickerRow(
                label = stringResource(R.string.staff090_stocktake_field_genre), value = options.genres.firstOrNull { it.id == form.genreId }?.label,
                placeholder = stringResource(R.string.staff090_stocktake_any_genre), onClick = { onPicker(StocktakePicker.GENRE) },
                onClear = { onEdit { it.copy(genreId = null) } },
            )
            val media = options.mediaTypes.map { LookupOption(it.code, it.label) }
            LookupField(
                label = stringResource(R.string.staff090_stocktake_field_media), value = form.mediaType.orEmpty(),
                displayValue = media.firstOrNull { it.value == form.mediaType }?.label.orEmpty(), options = media,
                editable = false, onValueChange = { v -> onEdit { it.copy(mediaType = v.ifBlank { null }) } }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PinakesTextField(
                    value = form.collocationFrom, onValueChange = { v -> onEdit { it.copy(collocationFrom = v) } },
                    label = stringResource(R.string.staff090_stocktake_field_collocation_from), maxLength = limits.maxCollocationLength, modifier = Modifier.weight(1f),
                )
                PinakesTextField(
                    value = form.collocationTo, onValueChange = { v -> onEdit { it.copy(collocationTo = v) } },
                    label = stringResource(R.string.staff090_stocktake_field_collocation_to), maxLength = limits.maxCollocationLength, modifier = Modifier.weight(1f),
                )
            }
            PrimaryButton(
                stringResource(R.string.staff090_stocktake_open_session), onClick = onSave, loading = saving, enabled = form.valid(limits),
                modifier = Modifier.fillMaxWidth().testTag("stocktake-create"),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
