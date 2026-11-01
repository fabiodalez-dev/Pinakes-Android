package com.pinakes.app.ui.screens.staff.s090.patrons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffLedgerMovement
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.LedgerWrite
import com.pinakes.app.data.repository.Staff090PatronsRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.LedgerAction
import com.pinakes.app.data.staff.LedgerRules
import com.pinakes.app.data.staff.MoneyInput
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.components.SectionHeader
import com.pinakes.app.ui.screens.movements.AccruedRow
import com.pinakes.app.ui.screens.movements.BalanceCard
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.movements.MovementRow
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.s090.Staff090Routes
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The open ledger form: what the operator typed. */
data class LedgerForm(
    val action: LedgerAction,
    val amount: String = "",
    val method: String? = null,
    /** The payment note, the charge description or the waiver reason. */
    val text: String = "",
)

/** One submission, compared by value for its Idempotency-Key. */
private data class LedgerSubmission(val userId: Int, val action: LedgerAction, val cents: Int, val method: String?, val text: String)

data class StaffLedgerUiState(
    val ledger: StaffLedger? = null,
    val items: List<StaffLedgerMovement> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    /** The fines module went off (404 feature_disabled): the screen leaves. */
    val disabled: Boolean = false,
    val form: LedgerForm? = null,
    val formError: StaffMessage? = null,
    val saving: Boolean = false,
    val receiptBusy: Int? = null,
    val receipt: File? = null,
    val message: StaffMessage? = null,
)

/** A patron's ledger at the desk: balance, accruing fines, movements, payments, charges, waivers. */
@HiltViewModel
class Staff090LedgerViewModel @Inject constructor(
    private val patrons: Staff090PatronsRepository,
    private val files: StaffFilesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val userId: Int = savedState.get<Int>(Staff090Routes.ARG_USER_ID) ?: 0
    private val _state = MutableStateFlow(StaffLedgerUiState())
    val state: StateFlow<StaffLedgerUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.ledger == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = patrons.ledger(userId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> {
                        val ledger = res.data.items.first()
                        it.copy(ledger = ledger, items = ledger.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                    }
                    is ApiResult.Failure -> it.copy(
                        loading = false, refreshing = false, forbidden = res.isForbidden(),
                        disabled = res.code == "feature_disabled",
                        error = if (it.ledger == null) res.toPatronsMessage(R.string.staff_error_load) else null,
                        message = if (it.ledger != null) res.toPatronsMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val res = patrons.ledger(userId, cursor)
            _state.update {
                when (res) {
                    is ApiResult.Success -> {
                        val page = res.data.items.first().items
                        // The cursor pins the first page's newest row; never show a row twice.
                        val seen = it.items.map { m -> m.id }.toSet()
                        it.copy(items = it.items + page.filter { m -> m.id !in seen }, nextCursor = res.data.nextCursor, loadingMore = false)
                    }
                    is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun open(action: LedgerAction) {
        val ledger = _state.value.ledger ?: return
        val amount = if (action == LedgerAction.CHARGE) "" else MoneyInput.text(LedgerRules.maxCents(action, ledger))
        _state.update { it.copy(form = LedgerForm(action, amount = amount, method = ledger.paymentMethods.firstOrNull()?.code), formError = null) }
    }

    fun edit(form: LedgerForm) = _state.update { it.copy(form = form, formError = null) }
    fun close() { if (!_state.value.saving) _state.update { it.copy(form = null) } }

    fun submit() {
        val s = _state.value
        val form = s.form ?: return
        val ledger = s.ledger ?: return
        if (s.saving) return
        val parsed = MoneyInput.parse(form.amount, LedgerRules.maxCents(form.action, ledger))
        if (!LedgerRules.canSubmit(form.action, parsed, form.method, form.text)) return
        val cents = (parsed as MoneyInput.Parsed.Cents).value
        val write = when (form.action) {
            LedgerAction.PAYMENT -> LedgerWrite.Payment(cents, form.method.orEmpty(), form.text)
            LedgerAction.CHARGE -> LedgerWrite.Charge(cents, form.text)
            LedgerAction.WAIVER -> LedgerWrite.Waiver(cents, form.text)
        }
        val key = keys.keyFor(LedgerSubmission(userId, form.action, cents, form.method.takeIf { form.action == LedgerAction.PAYMENT }, form.text.trim()))
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            var res = patrons.recordLedger(userId, write, key)
            // The core asks for a retry when two desks wrote at once: the same key, once more.
            if (res is ApiResult.Failure && res.code == "concurrent_retry") res = patrons.recordLedger(userId, write, key)
            val outcome = res
            when (outcome) {
                is ApiResult.Success -> {
                    keys.reset()
                    _state.update {
                        it.copy(
                            saving = false, form = null,
                            message = StaffMessage(
                                when (form.action) {
                                    LedgerAction.PAYMENT -> R.string.staff090_patrons_payment_recorded
                                    LedgerAction.CHARGE -> R.string.staff090_patrons_charge_recorded
                                    LedgerAction.WAIVER -> R.string.staff090_patrons_waiver_recorded
                                },
                            ),
                        )
                    }
                    load()
                }
                is ApiResult.Failure -> {
                    if (outcome.code == SubmissionKeys.CONFLICT) keys.reset()
                    _state.update { it.copy(saving = false, forbidden = outcome.isForbidden(), formError = outcome.toPatronsMessage(R.string.staff_error_save)) }
                    // More than the patron owes: the balance changed meanwhile, show the real one.
                    if (outcome.code == "amount_exceeds_balance") load()
                }
            }
        }
    }

    fun receipt(movement: StaffLedgerMovement) {
        if (_state.value.receiptBusy != null) return
        _state.update { it.copy(receiptBusy = movement.id) }
        viewModelScope.launch {
            val res = files.download("staff/users/$userId/ledger/movements/${movement.id}/receipt.pdf", "ricevuta_${movement.id}.pdf")
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(receiptBusy = null, receipt = res.data)
                    is ApiResult.Failure -> it.copy(receiptBusy = null, forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeReceipt() = _state.update { it.copy(receipt = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

internal fun ledgerActionLabel(action: LedgerAction): Int = when (action) {
    LedgerAction.PAYMENT -> R.string.staff090_patrons_record_payment
    LedgerAction.CHARGE -> R.string.staff090_patrons_add_charge
    LedgerAction.WAIVER -> R.string.staff090_patrons_waive
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Staff090LedgerScreen(onNavigateUp: () -> Unit) {
    val vm: Staff090LedgerViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.ledger || state.disabled, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_ledger), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            val ledger = state.ledger
            when {
                state.loading -> LoadingState()
                ledger == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::refresh)
                else -> {
                    val money = { cents: Int -> MoneyFormat.format(cents, ledger.currency, com.pinakes.app.ui.common.AppLocale.current()) }
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("ledger-list"),
                        contentPadding = PaddingValues(top = Spacing.sm, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        item(key = "balance") { BalanceCard(ledger.toPayload(), money) }
                        val actions = LedgerRules.actions(ledger)
                        if (actions.isNotEmpty()) item(key = "actions") {
                            FlowRow(
                                Modifier.padding(horizontal = Spacing.lg),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                actions.forEachIndexed { index, action ->
                                    val label = stringResource(ledgerActionLabel(action))
                                    val icon = when (action) {
                                        LedgerAction.PAYMENT -> Icons.Outlined.Payments
                                        LedgerAction.CHARGE -> Icons.Outlined.Add
                                        LedgerAction.WAIVER -> Icons.Outlined.RemoveCircleOutline
                                    }
                                    val tag = Modifier.testTag("ledger-${action.name.lowercase()}")
                                    if (index == 0) PrimaryButton(label, onClick = { vm.open(action) }, leadingIcon = icon, modifier = tag)
                                    else SecondaryButton(label, onClick = { vm.open(action) }, leadingIcon = icon, modifier = tag)
                                }
                            }
                        }
                        if (ledger.accruedCents != 0 || ledger.accrued.isNotEmpty()) {
                            item(key = "accrued-title") { SectionHeader(title = stringResource(R.string.movements_accrued_title, money(ledger.accruedCents))) }
                            itemsIndexed(ledger.accrued, key = { i, a -> MoneyFormat.rowKey("accrued", i, a.loanId) }) { _, a -> AccruedRow(a, money) }
                        }
                        item(key = "items-title") { SectionHeader(title = stringResource(R.string.movements_history_title)) }
                        if (state.items.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = stringResource(R.string.movements_empty_title), subtitle = stringResource(R.string.movements_empty_subtitle),
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                )
                            }
                        }
                        itemsIndexed(state.items, key = { i, m -> MoneyFormat.rowKey("movement", i, m.id) }) { index, m ->
                            if (index >= state.items.size - 5 && state.nextCursor != null) androidx.compose.runtime.LaunchedEffect(state.items.size) { vm.loadMore() }
                            Column {
                                MovementRow(m.toMovement(), money)
                                val operator = m.operator?.name?.takeIf { it.isNotBlank() }
                                if (operator != null || m.receiptAvailable) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            operator?.let { stringResource(R.string.staff090_patrons_recorded_by, it) }.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (m.receiptAvailable) {
                                            PinakesTextButton(
                                                stringResource(if (state.receiptBusy == m.id) R.string.staff090_patrons_receipt_loading else R.string.staff090_patrons_receipt),
                                                onClick = { vm.receipt(m) }, enabled = state.receiptBusy == null,
                                                modifier = Modifier.testTag("receipt-${m.id}"),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (state.loadingMore) item(key = "more") { LoadingState(modifier = Modifier.heightIn(max = 80.dp)) }
                    }
                }
            }
        }
    }
    val ledger = state.ledger
    val form = state.form
    if (form != null && ledger != null) LedgerFormSheet(form, ledger, state, vm)
    state.receipt?.let { ExportFileDialog(it, "application/pdf", stringResource(R.string.staff090_patrons_receipt_ready), onDismiss = vm::closeReceipt) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LedgerFormSheet(form: LedgerForm, ledger: StaffLedger, state: StaffLedgerUiState, vm: Staff090LedgerViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val max = LedgerRules.maxCents(form.action, ledger)
    val parsed = MoneyInput.parse(form.amount, max)
    val locale = com.pinakes.app.ui.common.AppLocale.current()
    ModalBottomSheet(onDismissRequest = vm::close, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(ledgerActionLabel(form.action)), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.staff090_patrons_balance_line, MoneyFormat.format(ledger.balanceCents, ledger.currency, locale)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PinakesTextField(
                value = form.amount, onValueChange = { vm.edit(form.copy(amount = it)) },
                label = stringResource(R.string.staff090_patrons_amount, ledger.currency.orEmpty()),
                isError = parsed is MoneyInput.Parsed.Invalid || parsed is MoneyInput.Parsed.TooLarge,
                errorText = if (parsed is MoneyInput.Parsed.TooLarge) stringResource(R.string.staff090_patrons_amount_too_large, MoneyFormat.format(max, ledger.currency, locale))
                else stringResource(R.string.staff090_patrons_amount_invalid),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 13,
                modifier = Modifier.fillMaxWidth().testTag("ledger-amount"),
            )
            when (form.action) {
                LedgerAction.PAYMENT -> {
                    Text(stringResource(R.string.staff090_patrons_method), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ledger.paymentMethods.forEach { m ->
                            FilterChip(
                                selected = form.method == m.code, onClick = { vm.edit(form.copy(method = m.code)) },
                                label = { Text(m.label.ifBlank { m.code }) }, modifier = Modifier.testTag("method-${m.code}"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    PinakesTextField(
                        value = form.text, onValueChange = { vm.edit(form.copy(text = it)) }, label = stringResource(R.string.staff090_patrons_note_optional),
                        singleLine = false, maxLength = 255, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("ledger-text"),
                    )
                }
                LedgerAction.CHARGE, LedgerAction.WAIVER -> PinakesTextField(
                    value = form.text, onValueChange = { vm.edit(form.copy(text = it)) },
                    label = stringResource(if (form.action == LedgerAction.CHARGE) R.string.staff090_patrons_description else R.string.staff090_patrons_waive_reason),
                    singleLine = false, maxLength = 255, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("ledger-text"),
                )
            }
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            PrimaryButton(
                stringResource(ledgerActionLabel(form.action)), onClick = vm::submit, loading = state.saving,
                enabled = LedgerRules.canSubmit(form.action, parsed, form.method, form.text), leadingIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
                modifier = Modifier.fillMaxWidth().testTag("ledger-submit"),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
