package com.pinakes.app.ui.screens.staff.s090.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.Staff090LibraryRuleCell
import com.pinakes.app.data.model.Staff090LibraryRules
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090LibraryRepository
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.LeaveWhen
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

data class RulesMatrixUiState(
    val data: Staff090LibraryRules? = null,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val exit: StaffMessage? = null,
    /** Cells changed on the device and not yet saved: `typeId|media` → form. */
    val edits: Map<String, CellForm> = emptyMap(),
    /** The cell open in the editor and its working copy. */
    val open: Staff090LibraryRuleCell? = null,
    val openForm: CellForm? = null,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: StaffMessage? = null,
) {
    /** The changed cells only, as the save body. */
    val pending: Int get() = data?.let { RulesForm.body(it.cells, edits, AppLocale.current()).size } ?: 0

    fun formOf(c: Staff090LibraryRuleCell): CellForm = edits[RulesForm.key(c.typeId, c.media)] ?: CellForm.from(c, AppLocale.current())
}

@HiltViewModel
class RulesMatrixViewModel @Inject constructor(
    private val repo: Staff090LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RulesMatrixUiState())
    val state: StateFlow<RulesMatrixUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.data == null, error = null) }
        viewModelScope.launch {
            val res = repo.rules()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(data = res.data, loading = false)
                    is ApiResult.Failure -> it.copy(loading = false, exit = res.exitMessage(), error = res.toLibraryMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun open(c: Staff090LibraryRuleCell) = _state.update { it.copy(open = c, openForm = it.formOf(c)) }
    fun editOpen(transform: (CellForm) -> CellForm) = _state.update { s -> s.copy(openForm = s.openForm?.let(transform)) }
    fun close() = _state.update { it.copy(open = null, openForm = null) }

    /** Clears every value of the open cell: it inherits everything again. */
    fun inheritAll() = _state.update { s -> s.copy(openForm = CellForm()) }

    /** Keeps the open cell's values among the pending changes (saved together with the others). */
    fun apply() {
        val s = _state.value
        val cell = s.open ?: return
        val form = s.openForm ?: return
        val key = RulesForm.key(cell.typeId, cell.media)
        val unchanged = RulesForm.cellBody(cell, form, AppLocale.current()) == null
        _state.update { it.copy(edits = if (unchanged) it.edits - key else it.edits + (key to form), open = null, openForm = null, fieldErrors = emptyMap()) }
    }

    fun discard() = _state.update { it.copy(edits = emptyMap(), formError = null, fieldErrors = emptyMap()) }

    fun save() {
        val s = _state.value
        val data = s.data ?: return
        if (s.saving || s.pending == 0) return
        val body = RulesForm.body(data.cells, s.edits, AppLocale.current())
        _state.update { it.copy(saving = true, formError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val saved = repo.saveRules(body)
            _state.update {
                when (val res = saved.result) {
                    is ApiResult.Success -> it.copy(saving = false, data = res.data, edits = emptyMap(), message = StaffMessage(R.string.staff090_library_saved))
                    is ApiResult.Failure -> it.copy(saving = false, exit = res.exitMessage(), fieldErrors = saved.fields, formError = res.toLibraryMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** The rules matrix: patron type (and "all types") × material, each cell inheriting what it leaves empty. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesMatrixScreen(onNavigateUp: () -> Unit) {
    val vm: RulesMatrixViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LeaveWhen(!areas.s090.circulationRules, onNavigateUp)
    LibraryExit(state.exit, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val data = state.data

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_library_matrix), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (data?.canConfigure == true && state.pending > 0) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        SecondaryButton(stringResource(R.string.staff090_library_discard), onClick = vm::discard, enabled = !state.saving, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            stringResource(R.string.staff090_library_save_cells, state.pending), onClick = vm::save, loading = state.saving,
                            modifier = Modifier.weight(1f).testTag("rules-save"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        StaffLoadBody(
            loading = state.loading, error = state.error, isEmpty = data?.cells.isNullOrEmpty(),
            emptyTitle = stringResource(R.string.staff090_library_matrix_empty), emptyIcon = Icons.Outlined.GridOn, onRetry = vm::load,
        ) {
            val rules = data ?: return@StaffLoadBody
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                item(key = "intro") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        state.formError?.let { InlineErrorBanner(message = it.resolve()) }
                        if (!rules.moduleEnabled) {
                            InfoCard { Text(stringResource(R.string.staff090_library_rules_module_off), style = MaterialTheme.typography.bodyMedium) }
                        }
                        Text(stringResource(R.string.staff090_library_matrix_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                rules.rows.forEach { row ->
                    item(key = "row-${row.typeId}") {
                        FormSectionTitle(if (row.typeId == 0) stringResource(R.string.staff090_library_all_types) else row.name)
                    }
                    val cells = rules.cells.filter { it.typeId == row.typeId }
                    items(cells, key = { "cell-${it.typeId}-${it.media}" }) { cell ->
                        CellRow(
                            cell, rules.media.firstOrNull { it.key == cell.media }?.label ?: cell.media, state.formOf(cell), rules.currency,
                            pending = RulesForm.key(cell.typeId, cell.media) in state.edits,
                            onClick = if (rules.canConfigure) ({ vm.open(cell) }) else null,
                        )
                    }
                }
            }
        }
    }

    val cell = state.open
    val form = state.openForm
    if (data != null && cell != null && form != null) CellSheet(cell, form, data, state, vm)
}

/** What a cell means once its empty values are filled from what it inherits. */
@Composable
private fun cellSummary(cell: Staff090LibraryRuleCell, form: CellForm, currency: String?): String {
    val loanable = form.loanable ?: cell.inherited.loanable
    if (!loanable) return stringResource(R.string.staff090_library_cell_not_loanable)
    fun value(n: RuleNumber): Int? = form.text(n).let { t ->
        if (t.isBlank()) n.inherited(cell)
        else if (n.money) (LibraryMoney.parse(t) as? CentsInput.Valid)?.cents else t.trim().toIntOrNull()
    }
    val parts = mutableListOf(
        stringResource(R.string.staff090_library_cell_duration, value(RuleNumber.LOAN_DURATION) ?: 0),
        stringResource(R.string.staff090_library_cell_renewals, value(RuleNumber.MAX_RENEWALS) ?: 0),
    )
    value(RuleNumber.MAX_LOANS)?.takeIf { it > 0 }?.let { parts.add(0, stringResource(R.string.staff090_library_cell_max, it)) }
    value(RuleNumber.DAILY_FINE)?.takeIf { it > 0 }?.let { parts += stringResource(R.string.staff090_library_cell_fine, money(it, currency)) }
    return parts.joinToString(" · ")
}

@Composable
private fun CellRow(cell: Staff090LibraryRuleCell, mediaLabel: String, form: CellForm, currency: String?, pending: Boolean, onClick: (() -> Unit)?) {
    val customised = form.loanable != null || form.numbers.values.any { it.isNotBlank() }
    Surface(
        onClick = { onClick?.invoke() }, enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("cell-${cell.typeId}-${cell.media}"),
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mediaLabel, style = MaterialTheme.typography.bodyLarge)
                Text(cellSummary(cell, form, currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                pending -> AvailabilityChip(AvailabilityStatus.DueSoon, stringResource(R.string.staff090_library_cell_pending))
                customised -> AvailabilityChip(AvailabilityStatus.ReservedReady, stringResource(R.string.staff090_library_cell_custom))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellSheet(cell: Staff090LibraryRuleCell, form: CellForm, data: Staff090LibraryRules, state: RulesMatrixUiState, vm: RulesMatrixViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val invalid = RulesForm.invalid(form, data.limits)
    val rowName = data.rows.firstOrNull { it.typeId == cell.typeId }?.let { if (it.typeId == 0) stringResource(R.string.staff090_library_all_types) else it.name }.orEmpty()
    val mediaLabel = data.media.firstOrNull { it.key == cell.media }?.label ?: cell.media
    ModalBottomSheet(onDismissRequest = vm::close, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff090_library_cell_title, rowName, mediaLabel), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff090_library_inherit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val inheritLabel = stringResource(
                R.string.staff090_library_loanable_inherit,
                stringResource(if (cell.inherited.loanable) R.string.staff090_library_yes else R.string.staff090_library_no),
            )
            ChoiceField(
                stringResource(R.string.staff090_library_cell_loanable),
                when (form.loanable) { null -> "inherit"; true -> "yes"; false -> "no" },
                listOf(
                    LookupOption("inherit", inheritLabel),
                    LookupOption("yes", stringResource(R.string.staff090_library_yes)),
                    LookupOption("no", stringResource(R.string.staff090_library_no)),
                ),
                { v -> vm.editOpen { it.copy(loanable = when (v) { "yes" -> true; "no" -> false; else -> null }) } },
                modifier = Modifier.fillMaxWidth().testTag("cell-loanable"),
            )
            RuleNumber.entries.forEach { n ->
                val label = stringResource(ruleNumberLabel(n))
                val inherited = n.inherited(cell)
                val hint = stringResource(R.string.staff090_library_inherits, if (n.money) money(inherited, data.currency) else inherited?.toString() ?: "—")
                val errorKey = state.fieldErrors.keys.firstOrNull { it.endsWith(".${n.key}") }
                val modifier = Modifier.fillMaxWidth().testTag("cell-${n.key}")
                if (n.money) {
                    MoneyField(label, form.text(n), data.currency, { v -> vm.editOpen { it.copy(numbers = it.numbers + (n to v)) } }, invalid = n.key in invalid, hint = hint, modifier = modifier)
                } else {
                    NumberField(label, form.text(n), { v -> vm.editOpen { it.copy(numbers = it.numbers + (n to v)) } }, invalid = n.key in invalid || errorKey != null,
                        hint = errorKey?.let { state.fieldErrors[it] } ?: listOfNotNull(hint, data.limits[n.key]?.let { stringResource(R.string.staff090_library_range, it.min, it.max) }).joinToString(" "),
                        modifier = modifier)
                }
            }
            SecondaryButton(stringResource(R.string.staff090_library_inherit_all), onClick = vm::inheritAll, modifier = Modifier.fillMaxWidth())
            PrimaryButton(stringResource(R.string.staff090_library_apply), onClick = vm::apply, enabled = invalid.isEmpty(), modifier = Modifier.fillMaxWidth().testTag("cell-apply"))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

private fun ruleNumberLabel(n: RuleNumber) = when (n) {
    RuleNumber.MAX_LOANS -> R.string.staff090_library_num_max_loans
    RuleNumber.LOAN_DURATION -> R.string.staff090_library_num_loan_duration
    RuleNumber.MAX_RENEWALS -> R.string.staff090_library_num_max_renewals
    RuleNumber.RENEWAL_DAYS -> R.string.staff090_library_num_renewal_days
    RuleNumber.GRACE_DAYS -> R.string.staff090_library_num_grace_days
    RuleNumber.DAILY_FINE -> R.string.staff090_library_num_daily_fine
    RuleNumber.MAX_FINE -> R.string.staff090_library_num_max_fine
}
