package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.LoanBucket
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.toStaffMessage
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

data class StaffLoansUiState(
    val bucket: LoanBucket = LoanBucket.ALL,
    val query: String = "",
    val items: List<StaffLoan> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    val bulkBusy: Boolean = false,
    val message: StaffMessage? = null,
) {
    /** Bulk extend and recall apply to loans that are out. */
    val canSelect: Boolean get() = bucket == LoanBucket.ACTIVE || bucket == LoanBucket.OVERDUE
}

@HiltViewModel
class StaffLoansViewModel @Inject constructor(
    private val circulation: StaffCirculationRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(StaffLoansUiState(bucket = LoanBucket.fromWire(savedState.get<String>(Routes.ARG_BUCKET))))
    val state: StateFlow<StaffLoansUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            val res = circulation.loans(s.bucket, s.query)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        items = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false,
                        selected = it.selected.intersect(res.data.items.map(StaffLoan::id).toSet()),
                    )
                    is ApiResult.Failure -> it.copy(
                        loading = false, refreshing = false, forbidden = res.isForbidden(),
                        error = res.toStaffMessage(R.string.staff_error_load),
                    )
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
            val res = circulation.loans(s.bucket, s.query, cursor)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = (it.items + res.data.items).distinctBy(StaffLoan::id), nextCursor = res.data.nextCursor, loadingMore = false)
                    is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun selectBucket(bucket: LoanBucket) {
        if (bucket == _state.value.bucket) return
        _state.update { it.copy(bucket = bucket, items = emptyList(), nextCursor = null, selecting = false, selected = emptySet()) }
        reload()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); reload() }
    }

    fun toggleSelecting() = _state.update { it.copy(selecting = !it.selecting && it.canSelect, selected = emptySet()) }

    fun toggle(loan: StaffLoan) {
        if (!LoanActions.isBulkSelectable(loan)) return
        _state.update { s -> s.copy(selected = if (loan.id in s.selected) s.selected - loan.id else s.selected + loan.id) }
    }

    fun bulkExtend(days: Int) {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty() || _state.value.bulkBusy) return
        _state.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            val res = circulation.bulkExtend(ids, days)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        bulkBusy = false, selecting = false, selected = emptySet(),
                        message = StaffMessage(R.string.staff_bulk_extended, args = listOf(res.data.extended, res.data.days)),
                    )
                    is ApiResult.Failure -> it.copy(bulkBusy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) reload()
        }
    }

    fun bulkRecall() {
        val ids = _state.value.selected.toList()
        if (ids.isEmpty() || _state.value.bulkBusy) return
        _state.update { it.copy(bulkBusy = true) }
        viewModelScope.launch {
            val res = circulation.bulkRecall(ids)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        bulkBusy = false, selecting = false, selected = emptySet(),
                        message = StaffMessage(R.string.staff_bulk_recalled, args = listOf(res.data.sent, res.data.skipped)),
                    )
                    is ApiResult.Failure -> it.copy(bulkBusy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) reload()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

private val BUCKETS = listOf(LoanBucket.PENDING, LoanBucket.PICKUP, LoanBucket.SCHEDULED, LoanBucket.ACTIVE, LoanBucket.OVERDUE, LoanBucket.HISTORY, LoanBucket.ALL)

internal fun bucketLabel(bucket: LoanBucket): Int = when (bucket) {
    LoanBucket.PENDING -> R.string.staff_loans_pending
    LoanBucket.PICKUP -> R.string.staff_loans_pickup
    LoanBucket.SCHEDULED -> R.string.staff_loans_scheduled
    LoanBucket.ACTIVE -> R.string.staff_loans_active
    LoanBucket.OVERDUE -> R.string.staff_loans_overdue
    LoanBucket.HISTORY -> R.string.staff_loans_history
    LoanBucket.ALL -> R.string.staff_loans_all
}

/** Loans by desk bucket, with search and, on Active/Overdue, multi-select for bulk extend/recall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffLoansScreen(onNavigateUp: () -> Unit, onOpenLoan: (Int) -> Unit) {
    val vm: StaffLoansViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var extendOpen by rememberSaveable { mutableStateOf(false) }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.staff_loans_title),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.canSelect) {
                        IconButton(onClick = vm::toggleSelecting) {
                            Icon(
                                if (state.selecting) Icons.Outlined.Close else Icons.Outlined.Checklist,
                                contentDescription = stringResource(if (state.selecting) R.string.staff_select_done else R.string.staff_select_many),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.selecting) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.staff_selected_count, state.selected.size), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        SecondaryButton(stringResource(R.string.staff_loan_action_recall), onClick = vm::bulkRecall, enabled = state.selected.isNotEmpty() && !state.bulkBusy)
                        PrimaryButton(stringResource(R.string.staff_bulk_extend), onClick = { extendOpen = true }, enabled = state.selected.isNotEmpty(), loading = state.bulkBusy)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val selectedIndex = BUCKETS.indexOf(state.bucket).coerceAtLeast(0)
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = Spacing.lg) {
                BUCKETS.forEach { bucket ->
                    Tab(selected = bucket == state.bucket, onClick = { vm.selectBucket(bucket) }, text = { Text(stringResource(bucketLabel(bucket))) })
                }
            }
            SearchField(
                query = state.query, onQueryChange = vm::onQueryChange, placeholder = stringResource(R.string.staff_loans_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff_loans_empty), emptyIcon = Icons.Outlined.SwapHoriz, onRetry = vm::refresh,
                ) {
                    val listState = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.items.size - 5 } == true } }
                    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd) vm.loadMore() }
                    LazyColumn(
                        Modifier.fillMaxSize(), state = listState,
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp),
                    ) {
                        items(state.items, key = { it.id }) { loan ->
                            val selectable = state.selecting && LoanActions.isBulkSelectable(loan)
                            StaffLoanRow(
                                loan = loan,
                                selected = if (selectable) loan.id in state.selected else null,
                                onClick = { if (state.selecting) vm.toggle(loan) else onOpenLoan(loan.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (extendOpen) {
        ExtendDaysDialog(
            count = state.selected.size,
            onConfirm = { days -> extendOpen = false; vm.bulkExtend(days) },
            onDismiss = { extendOpen = false },
        )
    }
}

@Composable
private fun ExtendDaysDialog(count: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var days by rememberSaveable { mutableStateOf("14") }
    val value = days.toIntOrNull()?.takeIf { it in 1..365 }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.staff_bulk_extend), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.staff_bulk_extend_body, count), style = MaterialTheme.typography.bodyMedium)
                PinakesTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = stringResource(R.string.staff_bulk_extend_days),
                    isError = value == null, errorText = stringResource(R.string.staff_bulk_extend_days_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 3, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { PrimaryButton(stringResource(R.string.staff_bulk_extend), onClick = { value?.let(onConfirm) }, enabled = value != null) },
        dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
