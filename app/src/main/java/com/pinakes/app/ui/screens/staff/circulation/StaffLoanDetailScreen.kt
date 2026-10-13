package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LoanEditRequest
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.LoanAction
import com.pinakes.app.data.staff.LoanActionUi
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.data.staff.ReturnOutcome
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickedField
import com.pinakes.app.ui.screens.staff.ReasonDialog
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The edit sheet's draft: only fields that differ from the loan are sent. */
data class LoanEditDraft(
    val userId: Int,
    val userName: String,
    val loanedAt: String?,
    val dueAt: String?,
)

data class StaffLoanDetailUiState(
    val loan: StaffLoan? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val busy: LoanAction? = null,
    val pending: LoanAction? = null,
    val edit: LoanEditDraft? = null,
    val userPicker: Boolean = false,
    val receipt: File? = null,
    val viewReceipt: Boolean = false,
    val message: StaffMessage? = null,
)

@HiltViewModel
class StaffLoanDetailViewModel @Inject constructor(
    private val circulation: StaffCirculationRepository,
    private val files: StaffFilesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val loanId: Int = savedState.get<Int>(Routes.ARG_LOAN_ID) ?: 0
    private val _state = MutableStateFlow(StaffLoanDetailUiState())
    val state: StateFlow<StaffLoanDetailUiState> = _state.asStateFlow()

    val users = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = circulation.users(q)) {
            is ApiResult.Success -> ApiResult.Success(res.data.map { u -> u.toPickItem() })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.loan == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = circulation.loan(loanId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(loan = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(
                        loading = false, refreshing = false, forbidden = res.isForbidden(),
                        error = if (it.loan == null) res.toStaffMessage(R.string.staff_error_load) else null,
                        message = if (it.loan != null) res.toStaffMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    /** A tap on an action button: confirm/reason/sheet first, or run it straight away. */
    fun onAction(action: LoanAction) {
        val loan = _state.value.loan ?: return
        when (LoanActions.ui(action)) {
            LoanActionUi.IMMEDIATE -> run(action) {
                if (action == LoanAction.RECALL) circulation.recall(loan.id).sent() else circulation.emailReceipt(loan.id).sent()
            }
            LoanActionUi.CONFIRM, LoanActionUi.REASON, LoanActionUi.RETURN_SHEET -> _state.update { it.copy(pending = action) }
            LoanActionUi.EDIT_SHEET -> _state.update {
                it.copy(edit = LoanEditDraft(loan.user.id, loan.user.name, loan.loanedAt?.take(10), loan.dueAt?.take(10)))
            }
            LoanActionUi.DOWNLOAD -> downloadReceipt()
        }
    }

    fun dismissPending() = _state.update { it.copy(pending = null) }

    fun confirmPending(reason: String? = null) {
        val action = _state.value.pending ?: return
        val id = _state.value.loan?.id ?: return
        _state.update { it.copy(pending = null) }
        when (action) {
            LoanAction.APPROVE -> mutate(action, R.string.staff_loan_approved) { circulation.approve(id) }
            LoanAction.REJECT -> mutate(action, R.string.staff_loan_rejected) { circulation.reject(id, reason) }
            LoanAction.CONFIRM_PICKUP -> mutate(action, R.string.staff_loan_picked_up) { circulation.pickup(id) }
            LoanAction.CANCEL_PICKUP -> mutate(action, R.string.staff_loan_pickup_cancelled) { circulation.cancelPickup(id, reason) }
            LoanAction.RENEW -> mutate(action, R.string.staff_loan_renewed) { circulation.renew(id) }
            else -> Unit
        }
    }

    fun returnLoan(outcome: ReturnOutcome, notes: String, penalty: Double?) {
        val id = _state.value.loan?.id ?: return
        _state.update { it.copy(pending = null) }
        mutate(LoanAction.RETURN, R.string.staff_loan_returned, retryOnConcurrent = true) {
            circulation.returnLoan(id, outcome.wire, notes, penalty)
        }
    }

    fun editUser(item: PickItem) {
        _state.update { s -> s.copy(edit = s.edit?.copy(userId = item.id, userName = item.title), userPicker = false) }
        users.clear()
    }

    fun openUserPicker() = _state.update { it.copy(userPicker = true) }
    fun closeUserPicker() { _state.update { it.copy(userPicker = false) }; users.clear() }
    fun editDates(loanedAt: String?, dueAt: String?) = _state.update { s -> s.copy(edit = s.edit?.copy(loanedAt = loanedAt, dueAt = dueAt)) }
    fun closeEdit() = _state.update { it.copy(edit = null) }

    fun saveEdit() {
        val loan = _state.value.loan ?: return
        val draft = _state.value.edit ?: return
        // The server merges over the row; send only what changed (never blank a date).
        val request = LoanEditRequest(
            userId = draft.userId.takeIf { it != loan.user.id },
            loanedAt = draft.loanedAt?.takeIf { it != loan.loanedAt?.take(10) },
            dueAt = draft.dueAt?.takeIf { it != loan.dueAt?.take(10) },
        )
        if (request.userId == null && request.loanedAt == null && request.dueAt == null) {
            _state.update { it.copy(edit = null) }
            return
        }
        mutate(LoanAction.EDIT, R.string.staff_loan_updated, retryOnConcurrent = true, closeEdit = true) { circulation.updateLoan(loan.id, request) }
    }

    private fun mutate(
        action: LoanAction,
        success: Int,
        retryOnConcurrent: Boolean = false,
        closeEdit: Boolean = false,
        call: suspend () -> ApiResult<StaffLoan>,
    ) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = action) }
        viewModelScope.launch {
            var res = call()
            // The core asks for a retry when two desks touched the copy at once: retry once.
            if (retryOnConcurrent && res is ApiResult.Failure && res.code == "concurrent_retry") res = call()
            val outcome = res
            _state.update {
                when (outcome) {
                    is ApiResult.Success -> it.copy(
                        busy = null, loan = outcome.data, message = StaffMessage(success),
                        edit = if (closeEdit) null else it.edit,
                    )
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = outcome.isForbidden(), message = outcome.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    private fun run(action: LoanAction, call: suspend () -> Pair<Boolean, String?>?) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = action) }
        viewModelScope.launch {
            val result = call()
            _state.update {
                it.copy(
                    busy = null,
                    message = when {
                        result == null -> StaffMessage(R.string.staff_error_save)
                        result.first -> StaffMessage(if (action == LoanAction.RECALL) R.string.staff_recall_sent else R.string.staff_receipt_emailed)
                        else -> result.second?.takeIf { m -> m.isNotBlank() }?.let { m -> StaffMessage(text = m) } ?: StaffMessage(R.string.staff_mail_not_sent)
                    },
                )
            }
            if (action == LoanAction.RECALL && result?.first == true) load()
        }
    }

    private fun ApiResult<com.pinakes.app.data.model.SentResult>.sent(): Pair<Boolean, String?>? = when (this) {
        is ApiResult.Success -> data.sent to (data.message ?: meta?.message)
        is ApiResult.Failure -> {
            if (isForbidden()) _state.update { it.copy(forbidden = true) }
            null
        }
    }

    private fun downloadReceipt() {
        val loan = _state.value.loan ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = LoanAction.RECEIPT) }
        viewModelScope.launch {
            val res = files.download("staff/loans/${loan.id}/receipt.pdf", "prestito_${loan.id}.pdf")
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = null, receipt = res.data, viewReceipt = true)
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeReceipt() = _state.update { it.copy(viewReceipt = false) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

internal fun com.pinakes.app.data.model.StaffUser.toPickItem(): PickItem = PickItem(
    id = id,
    title = name,
    subtitle = listOfNotNull(cardNumber?.takeIf { it.isNotBlank() }, email?.takeIf { it.isNotBlank() }).joinToString(" · "),
    note = if (!eligible) eligibilityReason?.takeIf { it.isNotBlank() } ?: status else null,
)

internal fun loanActionLabel(action: LoanAction): Int = when (action) {
    LoanAction.APPROVE -> R.string.staff_loan_action_approve
    LoanAction.REJECT -> R.string.staff_loan_action_reject
    LoanAction.CONFIRM_PICKUP -> R.string.staff_loan_action_confirm_pickup
    LoanAction.CANCEL_PICKUP -> R.string.staff_loan_action_cancel_pickup
    LoanAction.RETURN -> R.string.staff_loan_action_return
    LoanAction.RENEW -> R.string.staff_loan_action_renew
    LoanAction.EDIT -> R.string.staff_loan_action_edit
    LoanAction.RECALL -> R.string.staff_loan_action_recall
    LoanAction.RECEIPT -> R.string.staff_loan_action_receipt
    LoanAction.EMAIL_RECEIPT -> R.string.staff_loan_action_email_receipt
}

/** One loan at the desk: who, which copy, the dates, and the actions the server offers now. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffLoanDetailScreen(onNavigateUp: () -> Unit, onOpenUser: (Int) -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: StaffLoanDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_loan_title), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            val loan = state.loan
            when {
                state.loading -> LoadingState()
                loan == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::refresh)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        BookCover(loan.book.title, loan.book.coverUrl, Modifier.width(88.dp).aspectRatio(2f / 3f), compact = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(loan.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val (status, label) = loanChip(loan)
                            AvailabilityChip(status = status, label = label)
                            loanDateLine(loan)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    val actions = LoanActions.of(loan)
                    if (actions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            actions.forEachIndexed { index, action ->
                                val label = stringResource(loanActionLabel(action))
                                val tag = Modifier.testTag("action-${action.wire}")
                                if (index == 0) {
                                    PrimaryButton(label, onClick = { vm.onAction(action) }, loading = state.busy == action, enabled = state.busy == null, modifier = tag)
                                } else {
                                    SecondaryButton(label, onClick = { vm.onAction(action) }, enabled = state.busy == null, modifier = tag)
                                }
                            }
                        }
                    }
                    FormSectionTitle(stringResource(R.string.staff_loan_borrower))
                    InfoCard(Modifier.testTag("loan-user")) {
                        Text(loan.user.name, style = MaterialTheme.typography.titleMedium)
                        InfoLine(stringResource(R.string.staff_user_card), loan.user.cardNumber)
                        InfoLine(stringResource(R.string.staff_field_email), loan.user.email)
                        Spacer(Modifier.height(Spacing.xs))
                        SecondaryButton(stringResource(R.string.staff_user_open), onClick = { onOpenUser(loan.user.id) }, enabled = loan.user.id > 0)
                    }
                    FormSectionTitle(stringResource(R.string.staff_loan_details))
                    InfoCard {
                        InfoLine(stringResource(R.string.staff_copy_inventory), loan.copy?.inventoryNumber)
                        InfoLine(stringResource(R.string.staff_loan_requested), loan.requestedAt?.let(DateFormat::date))
                        InfoLine(stringResource(R.string.staff_loan_loaned), loan.loanedAt?.let(DateFormat::date))
                        InfoLine(stringResource(R.string.staff_loan_due), loan.dueAt?.let(DateFormat::date))
                        InfoLine(stringResource(R.string.staff_loan_pickup_deadline), loan.pickupDeadline?.let(DateFormat::date))
                        InfoLine(stringResource(R.string.staff_loan_returned_at), loan.returnedAt?.let(DateFormat::date))
                        InfoLine(stringResource(R.string.staff_loan_renewals), stringResource(R.string.staff_loan_renewals_value, loan.renewals, loan.maxRenewals))
                        InfoLine(stringResource(R.string.staff_penalty), loan.penalty?.let { "%.2f".format(java.util.Locale.ROOT, it) })
                        InfoLine(stringResource(R.string.staff_field_notes), loan.notes)
                        InfoLine(stringResource(R.string.staff_loan_recalls), loan.recallCount.takeIf { it > 0 }?.let { n ->
                            listOfNotNull(n.toString(), loan.lastRecallAt?.let(DateFormat::date)).joinToString(" · ")
                        })
                        InfoLine(stringResource(R.string.staff_loan_processed_by), loan.processedBy?.name)
                    }
                    SecondaryButton(stringResource(R.string.staff_open_book), onClick = { onOpenBook(loan.book.id) }, enabled = loan.book.id > 0, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }
    }

    when (val pending = state.pending) {
        LoanAction.REJECT, LoanAction.CANCEL_PICKUP -> ReasonDialog(
            title = stringResource(loanActionLabel(pending)),
            body = stringResource(if (pending == LoanAction.REJECT) R.string.staff_loan_reject_body else R.string.staff_loan_cancel_pickup_body),
            confirmLabel = stringResource(loanActionLabel(pending)),
            onConfirm = { vm.confirmPending(it) },
            onDismiss = vm::dismissPending,
        )
        LoanAction.APPROVE, LoanAction.CONFIRM_PICKUP, LoanAction.RENEW -> ConfirmDialog(
            title = stringResource(loanActionLabel(pending)),
            body = stringResource(
                when (pending) {
                    LoanAction.APPROVE -> R.string.staff_loan_approve_body
                    LoanAction.CONFIRM_PICKUP -> R.string.staff_loan_pickup_body
                    else -> R.string.staff_loan_renew_body
                },
            ),
            confirmLabel = stringResource(loanActionLabel(pending)),
            onConfirm = { vm.confirmPending() },
            onDismiss = vm::dismissPending,
        )
        LoanAction.RETURN -> ReturnSheet(
            title = stringResource(R.string.staff_return_title),
            saving = state.busy == LoanAction.RETURN,
            onConfirm = vm::returnLoan,
            onDismiss = vm::dismissPending,
        )
        else -> Unit
    }
    state.edit?.let { draft -> EditLoanSheet(draft, state, vm) }
    if (state.userPicker) {
        val users by vm.users.state.collectAsStateWithLifecycle()
        SearchPickerSheet(
            title = stringResource(R.string.staff_pick_user), placeholder = stringResource(R.string.staff_search_users),
            state = users, onQuery = vm.users::onQuery, onPick = vm::editUser, onDismiss = vm::closeUserPicker,
            hint = stringResource(R.string.staff_search_min_chars),
        )
    }
    val receipt = state.receipt
    if (state.viewReceipt && receipt != null) {
        ExportFileDialog(receipt, "application/pdf", stringResource(R.string.staff_receipt_ready), onDismiss = vm::closeReceipt)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLoanSheet(draft: LoanEditDraft, state: StaffLoanDetailUiState, vm: StaffLoanDetailViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (state.busy == null) vm.closeEdit() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff_loan_action_edit), style = MaterialTheme.typography.headlineSmall)
            PickedField(stringResource(R.string.staff_pick_user), draft.userName, stringResource(R.string.staff_pick_user), onClick = vm::openUserPicker)
            DatePickerField(stringResource(R.string.staff_loan_loaned), draft.loanedAt, { vm.editDates(it, draft.dueAt) })
            DatePickerField(stringResource(R.string.staff_loan_due), draft.dueAt, { vm.editDates(draft.loanedAt, it) })
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::saveEdit, loading = state.busy == LoanAction.EDIT, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
