package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.pinakes.app.data.model.ReservationWrite
import com.pinakes.app.data.model.StaffReservation
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.ReasonDialog
import com.pinakes.app.ui.screens.staff.StaffFab
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

/** The reservation status filters of `GET /staff/reservations`. */
enum class ReservationFilter(val wire: String, val label: Int) {
    ACTIVE("attiva", R.string.staff_reservations_active),
    COMPLETED("completata", R.string.staff_reservations_completed),
    CANCELLED("annullata", R.string.staff_reservations_cancelled),
    ALL("all", R.string.staff_reservations_all),
}

/** The edit sheet's draft; unchanged dates are not sent. */
data class ReservationDraft(val id: Int, val from: String?, val to: String?, val expires: String?)

data class StaffReservationsUiState(
    val filter: ReservationFilter = ReservationFilter.ACTIVE,
    val query: String = "",
    val items: List<StaffReservation> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val selected: StaffReservation? = null,
    val cancelling: StaffReservation? = null,
    val editing: ReservationDraft? = null,
    val busy: Boolean = false,
    val message: StaffMessage? = null,
) {
    /** Book → its queue, in the server's order (book, then queue position). */
    val groups: List<Pair<Int, List<StaffReservation>>> get() = items.groupBy { it.book.id }.toList()
}

@HiltViewModel
class StaffReservationsViewModel @Inject constructor(private val circulation: StaffCirculationRepository) : ViewModel() {
    private val _state = MutableStateFlow(StaffReservationsUiState())
    val state: StateFlow<StaffReservationsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            val res = circulation.reservations(s.filter.wire, s.query)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun setFilter(filter: ReservationFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, items = emptyList()) }
        reload()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); reload() }
    }

    fun select(r: StaffReservation?) = _state.update { it.copy(selected = r) }

    fun askCancel() = _state.update { it.copy(cancelling = it.selected, selected = null) }
    fun dismissCancel() = _state.update { it.copy(cancelling = null) }

    fun confirmCancel(reason: String) {
        val r = _state.value.cancelling ?: return
        _state.update { it.copy(cancelling = null) }
        mutate(R.string.staff_reservation_cancelled) { circulation.cancelReservation(r.id, reason) }
    }

    fun reactivate() {
        val r = _state.value.selected ?: return
        _state.update { it.copy(selected = null) }
        mutate(R.string.staff_reservation_reactivated) { circulation.updateReservation(r.id, ReservationWrite(status = "attiva")) }
    }

    fun startEdit() = _state.update { s ->
        val r = s.selected ?: return@update s
        s.copy(selected = null, editing = ReservationDraft(r.id, r.requestedFrom?.take(10), r.requestedTo?.take(10), r.expiresAt?.take(10)))
    }

    fun editDraft(transform: (ReservationDraft) -> ReservationDraft) = _state.update { s -> s.copy(editing = s.editing?.let(transform)) }
    fun closeEdit() = _state.update { it.copy(editing = null) }

    fun saveEdit() {
        val draft = _state.value.editing ?: return
        val original = _state.value.items.firstOrNull { it.id == draft.id } ?: return
        // The server merges over the row: only changed dates go out.
        val body = ReservationWrite(
            requestedFrom = draft.from?.takeIf { it != original.requestedFrom?.take(10) },
            requestedTo = draft.to?.takeIf { it != original.requestedTo?.take(10) },
            expiresAt = draft.expires?.takeIf { it != original.expiresAt?.take(10) },
        )
        if (body == ReservationWrite()) {
            closeEdit()
            return
        }
        mutate(R.string.staff_reservation_updated, closeEdit = true) { circulation.updateReservation(draft.id, body) }
    }

    private fun mutate(success: Int, closeEdit: Boolean = false, call: suspend () -> ApiResult<StaffReservation>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = call()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, message = StaffMessage(success), editing = if (closeEdit) null else it.editing)
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) reload()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Reservation queues grouped by title, with edit, cancel and reactivate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffReservationsScreen(
    onNavigateUp: () -> Unit,
    onNewReservation: () -> Unit,
    onOpenUser: (Int) -> Unit,
    onNewLoan: (userId: Int, bookId: Int) -> Unit,
) {
    val vm: StaffReservationsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_reservations), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_reservation_new), onNewReservation) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = state.filter.ordinal, edgePadding = Spacing.lg) {
                ReservationFilter.entries.forEach { f ->
                    Tab(selected = f == state.filter, onClick = { vm.setFilter(f) }, text = { Text(stringResource(f.label)) })
                }
            }
            SearchField(
                query = state.query, onQueryChange = vm::onQueryChange, placeholder = stringResource(R.string.staff_reservations_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff_reservations_empty), emptyIcon = Icons.Outlined.Event, onRetry = vm::refresh,
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                        state.groups.forEach { (bookId, queue) ->
                            item(key = "book-$bookId") {
                                Text(
                                    queue.first().book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                                )
                            }
                            items(queue, key = { it.id }) { r -> StaffReservationRow(r, onClick = { vm.select(r) }, showBook = false) }
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { r ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { vm.select(null) }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(r.book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.user.name, style = MaterialTheme.typography.bodyLarge)
                if (r.status == "attiva") {
                    PrimaryButton(stringResource(R.string.staff_loan_new), onClick = { vm.select(null); onNewLoan(r.user.id, r.book.id) }, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_reservation_edit), onClick = vm::startEdit, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_reservation_cancel), onClick = vm::askCancel, modifier = Modifier.fillMaxWidth())
                }
                if (r.status == "annullata") {
                    PrimaryButton(stringResource(R.string.staff_reservation_reactivate), onClick = vm::reactivate, loading = state.busy, modifier = Modifier.fillMaxWidth())
                }
                SecondaryButton(stringResource(R.string.staff_user_open), onClick = { vm.select(null); onOpenUser(r.user.id) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
    state.cancelling?.let {
        ReasonDialog(
            title = stringResource(R.string.staff_reservation_cancel), body = stringResource(R.string.staff_reservation_cancel_body, it.user.name),
            confirmLabel = stringResource(R.string.staff_reservation_cancel), onConfirm = vm::confirmCancel, onDismiss = vm::dismissCancel,
        )
    }
    state.editing?.let { draft ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!state.busy) vm.closeEdit() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.staff_reservation_edit), style = MaterialTheme.typography.headlineSmall)
                FormSectionTitle(stringResource(R.string.staff_loan_dates))
                DatePickerField(stringResource(R.string.staff_reservation_from), draft.from, { v -> vm.editDraft { it.copy(from = v) } })
                DatePickerField(stringResource(R.string.staff_reservation_to), draft.to, { v -> vm.editDraft { it.copy(to = v) } })
                DatePickerField(stringResource(R.string.staff_reservation_expiry), draft.expires, { v -> vm.editDraft { it.copy(expires = v) } })
                PrimaryButton(stringResource(R.string.action_save), onClick = vm::saveEdit, loading = state.busy, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}
