package com.pinakes.app.ui.screens.staff.s090.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.pinakes.app.data.model.Staff090LibraryCardSettings
import com.pinakes.app.data.model.Staff090LibraryCards
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModule
import com.pinakes.app.data.model.Staff090LibraryModules
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090LibraryRepository
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsTab { MODULES, LOANS, CARDS }

data class LibrarySettingsUiState(
    val tab: SettingsTab = SettingsTab.MODULES,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    /** Set when the screen must close (role gone): the reason shown on the way out. */
    val exit: StaffMessage? = null,
    val modules: Staff090LibraryModules? = null,
    val moduleValues: Map<String, Boolean> = emptyMap(),
    val retention: String = "",
    val loans: Staff090LibraryLoans? = null,
    val loanOriginal: Map<String, String> = emptyMap(),
    val loanValues: Map<String, String> = emptyMap(),
    /** `meta.fields` of the last refused save: field → message. */
    val fieldErrors: Map<String, String> = emptyMap(),
    /** Null when this user may not see the card settings (module off, not an administrator). */
    val cards: Staff090LibraryCards? = null,
    val cardValues: Staff090LibraryCardSettings = Staff090LibraryCardSettings(),
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val message: StaffMessage? = null,
) {
    val tabs: List<SettingsTab> get() = listOfNotNull(SettingsTab.MODULES, SettingsTab.LOANS, if (cards != null) SettingsTab.CARDS else null)

    val modulesBody get() = modules?.let { ModulesForm.body(it.modules.associate { m -> m.key to m.stored }, moduleValues, it.attendanceRetentionDays, retention) }
    val retentionInvalid: Boolean get() = modules?.let { IntInput.parse(retention, com.pinakes.app.data.model.Staff090LibraryRange(0, it.attendanceRetentionMaxDays.toLong())) !is IntInput.Valid } ?: false
    val loanInvalid: Set<String> get() = loans?.let { LoansForm.invalid(it, loanValues) }.orEmpty()
    val loansBody get() = loans?.let { LoansForm.body(it, loanOriginal, loanValues) }
    val cardsBody get() = cards?.let { CardsForm.body(it.settings, cardValues) }

    /** Whether the open tab shows a save action at all (hidden, not disabled, without permission). */
    val canSave: Boolean get() = when (tab) {
        SettingsTab.MODULES -> modules?.canConfigure == true
        SettingsTab.LOANS -> loans?.editableFields?.isNotEmpty() == true
        SettingsTab.CARDS -> cards?.canConfigure == true
    }

    val hasChanges: Boolean get() = when (tab) {
        SettingsTab.MODULES -> modulesBody != null && !retentionInvalid
        SettingsTab.LOANS -> loansBody != null && loanInvalid.none { LoansForm.editable(loans!!, it) }
        SettingsTab.CARDS -> cardsBody != null
    }
}

@HiltViewModel
class LibrarySettingsViewModel @Inject constructor(
    private val repo: Staff090LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibrarySettingsUiState())
    val state: StateFlow<LibrarySettingsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.modules == null, error = null) }
        viewModelScope.launch {
            val modules = async { repo.modules() }
            val loans = async { repo.loans() }
            val cards = async { repo.cards() }
            val m = modules.await()
            val l = loans.await()
            val c = cards.await()
            _state.update { s ->
                val failure = (m as? ApiResult.Failure) ?: (l as? ApiResult.Failure)
                if (failure != null) {
                    return@update s.copy(loading = false, exit = failure.exitMessage(), error = failure.toLibraryMessage(R.string.staff_error_load))
                }
                var next = s.copy(loading = false, error = null)
                next = next.withModules((m as ApiResult.Success).data).withLoans((l as ApiResult.Success).data)
                // Cards: a 404 feature_disabled (module off, Staff) only hides the tab.
                next = if (c is ApiResult.Success) next.withCards(c.data) else next.copy(cards = null)
                if (next.tab !in next.tabs) next.copy(tab = SettingsTab.MODULES) else next
            }
        }
    }

    private fun LibrarySettingsUiState.withModules(m: Staff090LibraryModules) =
        copy(modules = m, moduleValues = m.modules.associate { it.key to it.stored }, retention = m.attendanceRetentionDays.toString())

    private fun LibrarySettingsUiState.withLoans(l: Staff090LibraryLoans): LibrarySettingsUiState {
        val initial = LoansForm.initial(l, AppLocale.current())
        return copy(loans = l, loanOriginal = initial, loanValues = initial)
    }

    private fun LibrarySettingsUiState.withCards(c: Staff090LibraryCards) = copy(cards = c, cardValues = c.settings)

    fun setTab(tab: SettingsTab) = _state.update { it.copy(tab = tab, formError = null, fieldErrors = emptyMap()) }
    fun setModule(key: String, on: Boolean) = _state.update { it.copy(moduleValues = it.moduleValues + (key to on), formError = null) }
    fun setRetention(value: String) = _state.update { it.copy(retention = value, formError = null) }
    fun setLoan(key: String, value: String) = _state.update { it.copy(loanValues = it.loanValues + (key to value), fieldErrors = it.fieldErrors - key, formError = null) }
    fun editCards(transform: (Staff090LibraryCardSettings) -> Staff090LibraryCardSettings) = _state.update { it.copy(cardValues = transform(it.cardValues), formError = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.canSave || !s.hasChanges) return
        _state.update { it.copy(saving = true, formError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            when (s.tab) {
                SettingsTab.MODULES -> {
                    val saved = repo.saveModules(s.modulesBody ?: return@launch)
                    finish(saved.result, saved.fields) { st, data -> st.withModules(data) }
                    // A module switched on or off changes what the loans tab and the cards tab show.
                    if (saved.result is ApiResult.Success) refreshSecondary()
                }
                SettingsTab.LOANS -> {
                    val saved = repo.saveLoans(s.loansBody ?: return@launch)
                    finish(saved.result, saved.fields) { st, data -> st.withLoans(data) }
                }
                SettingsTab.CARDS -> {
                    val saved = repo.saveCards(s.cardsBody ?: return@launch)
                    finish(saved.result, saved.fields) { st, data -> st.withCards(data) }
                }
            }
        }
    }

    private fun refreshSecondary() {
        viewModelScope.launch {
            val c = repo.cards()
            (repo.loans() as? ApiResult.Success)?.data?.let { l -> _state.update { it.withLoans(l) } }
            _state.update { if (c is ApiResult.Success) it.withCards(c.data) else it.copy(cards = null, tab = if (it.tab == SettingsTab.CARDS) SettingsTab.MODULES else it.tab) }
        }
    }

    private fun <T> finish(res: ApiResult<T>, fields: Map<String, String>, apply: (LibrarySettingsUiState, T) -> LibrarySettingsUiState) {
        _state.update {
            when (res) {
                is ApiResult.Success -> apply(it, res.data).copy(saving = false, message = StaffMessage(R.string.staff090_library_saved))
                is ApiResult.Failure -> it.copy(
                    saving = false, fieldErrors = fields,
                    // Fields named by the server show under their input; the banner carries the message.
                    formError = res.toLibraryMessage(R.string.staff_error_save),
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Settings → Modules, Loans and Cards of the 0.9.0 web admin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsScreen(onNavigateUp: () -> Unit) {
    val vm: LibrarySettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LeaveWhen(!areas.s090.librarySettings, onNavigateUp)
    LibraryExit(state.exit, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_settings), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
        // The save action stays in reach under a long tab; hidden (not disabled) without permission.
        bottomBar = {
            if (!state.loading && state.error == null && state.canSave) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        stringResource(R.string.action_save), onClick = vm::save, loading = state.saving, enabled = state.hasChanges,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("settings-save"),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loading && state.error == null) {
                PrimaryScrollableTabRow(selectedTabIndex = state.tabs.indexOf(state.tab).coerceAtLeast(0), edgePadding = Spacing.lg) {
                    state.tabs.forEach { tab ->
                        Tab(selected = tab == state.tab, onClick = { vm.setTab(tab) }, text = { Text(stringResource(tabLabel(tab))) },
                            modifier = Modifier.testTag("settings-tab-${tab.name.lowercase()}"))
                    }
                }
            }
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = false,
                emptyTitle = "", emptyIcon = Icons.Outlined.Settings, onRetry = vm::load,
            ) {
                val list = rememberLazyListState()
                // A refused save explains itself in the banner at the top: bring it into view.
                LaunchedEffect(state.formError) { if (state.formError != null) list.animateScrollToItem(0) }
                LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                    item(key = "banner") { state.formError?.let { InlineErrorBanner(message = it.resolve(), modifier = Modifier.padding(top = Spacing.sm)) } }
                    when (state.tab) {
                        SettingsTab.MODULES -> state.modules?.let { m -> item(key = "modules") { ModulesTab(m, state, vm::setModule, vm::setRetention) } }
                        SettingsTab.LOANS -> state.loans?.let { l -> item(key = "loans") { LoansTab(l, state, vm::setLoan) } }
                        SettingsTab.CARDS -> state.cards?.let { c -> item(key = "cards") { CardsTab(c, state, vm::editCards) } }
                    }
                }
            }
        }
    }
}

private fun tabLabel(tab: SettingsTab) = when (tab) {
    SettingsTab.MODULES -> R.string.staff090_library_tab_modules
    SettingsTab.LOANS -> R.string.staff090_library_tab_loans
    SettingsTab.CARDS -> R.string.staff090_library_tab_cards
}

/** Settings → Modules: switches for an administrator, the plain state otherwise. Stateless. */
@Composable
internal fun ModulesTab(m: Staff090LibraryModules, state: LibrarySettingsUiState, onModule: (String, Boolean) -> Unit, onRetention: (String) -> Unit) {
    Column {
        if (!m.canConfigure) {
            InfoCard(Modifier.padding(top = Spacing.sm)) {
                Text(stringResource(R.string.staff090_library_modules_read_only), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (m.catalogueMode) {
            InfoCard(Modifier.padding(top = Spacing.sm)) {
                Text(stringResource(R.string.staff090_library_catalogue_mode), style = MaterialTheme.typography.bodyMedium)
            }
        }
        listOf("modules" to R.string.staff090_library_group_modules, "loans" to R.string.staff090_library_group_loans).forEach { (group, title) ->
            val list = ModulesForm.group(m.modules, group)
            if (list.isEmpty()) return@forEach
            FormSectionTitle(stringResource(title))
            list.forEach { module ->
                ModuleRow(module, m, state.moduleValues[module.key] ?: module.stored, onModule)
                if (module.key == "attendance_retention") RetentionRow(m, state, onRetention)
            }
        }
    }
}

@Composable
private fun ModuleRow(module: Staff090LibraryModule, m: Staff090LibraryModules, checked: Boolean, onModule: (String, Boolean) -> Unit) {
    val notes = ModulesForm.notes(module, m.modules, m.catalogueMode).map { note ->
        when (note) {
            is ModulesForm.Note.NeedsParent -> stringResource(
                if (note.parentOn) R.string.staff090_library_module_needs else R.string.staff090_library_module_needs_off, note.parentLabel,
            )
            ModulesForm.Note.CatalogueMode -> stringResource(R.string.staff090_library_module_catalogue_off)
            is ModulesForm.Note.Follows -> stringResource(R.string.staff090_library_module_follows, note.label)
        }
    }
    val subtitle = (listOf(module.help) + notes).filter { it.isNotBlank() }.joinToString("\n")
    if (m.canConfigure) {
        SwitchRow(module.label, checked, { onModule(module.key, it) }, subtitle = subtitle)
    } else {
        ReadOnlySetting(
            module.label,
            stringResource(if (module.effective) R.string.staff090_library_on else R.string.staff090_library_off),
            subtitle,
        )
    }
}

@Composable
private fun RetentionRow(m: Staff090LibraryModules, state: LibrarySettingsUiState, onRetention: (String) -> Unit) {
    val label = stringResource(R.string.staff090_library_retention_days)
    if (m.canConfigure) {
        NumberField(
            label, state.retention, onRetention, invalid = state.retentionInvalid,
            hint = stringResource(R.string.staff090_library_retention_hint, m.attendanceRetentionMaxDays),
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("retention-days"),
        )
    } else {
        ReadOnlySetting(label, m.attendanceRetentionDays.toString(), stringResource(R.string.staff090_library_retention_hint, m.attendanceRetentionMaxDays))
    }
}

/** Settings → Loans: inputs for the fields this user may save, the value for the others. Stateless. */
@Composable
internal fun LoansTab(l: Staff090LibraryLoans, state: LibrarySettingsUiState, onLoan: (String, String) -> Unit) {
    val fields = LoansForm.fields(l)
    val invalid = state.loanInvalid
    val adminOnlyShown = fields.any { it.key in l.adminOnlyFields && !LoansForm.editable(l, it.key) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LoanSection.entries.forEach { section ->
            val inSection = fields.filter { it.section == section }
            if (inSection.isEmpty()) return@forEach
            FormSectionTitle(stringResource(loanSectionTitle(section)))
            if (section == LoanSection.FINES && adminOnlyShown) {
                Text(stringResource(R.string.staff090_library_fines_admin_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            inSection.forEach { field -> LoanFieldRow(l, field, state.loanValues[field.key].orEmpty(), field.key in invalid, state.fieldErrors[field.key], onLoan) }
        }
    }
}

@Composable
private fun LoanFieldRow(l: Staff090LibraryLoans, field: LoanField, value: String, invalid: Boolean, serverError: String?, onLoan: (String, String) -> Unit) {
    val label = stringResource(loanFieldLabel(field.key))
    val help = loanFieldHelp(field.key)?.let { stringResource(it) }.orEmpty()
    val range = l.limits[field.key]
    val editable = LoansForm.editable(l, field.key)
    val tag = Modifier.fillMaxWidth().testTag("loan-${field.key}")
    if (!editable) {
        val shown = when (field.kind) {
            LoanFieldKind.BOOL -> stringResource(if (value.toBoolean()) R.string.staff090_library_on else R.string.staff090_library_off)
            LoanFieldKind.MONEY -> money((LibraryMoney.parse(value) as? CentsInput.Valid)?.cents, l.currency)
            LoanFieldKind.CHOICE -> loanOptionLabel(field.key, value)?.let { stringResource(it) } ?: value
            else -> value.ifBlank { "—" }
        }
        ReadOnlySetting(label, shown, help)
        return
    }
    when (field.kind) {
        LoanFieldKind.BOOL -> SwitchRow(label, value.toBoolean(), { onLoan(field.key, it.toString()) }, subtitle = help)
        LoanFieldKind.INT -> NumberField(
            label, value, { onLoan(field.key, it) }, invalid = invalid || serverError != null,
            hint = serverError ?: listOf(help, range?.let { stringResource(R.string.staff090_library_range, it.min, it.max) }.orEmpty()).filter { it.isNotBlank() }.joinToString(" "),
            modifier = tag,
        )
        LoanFieldKind.MONEY -> MoneyField(
            label, value, l.currency, { onLoan(field.key, it) }, invalid = invalid || serverError != null,
            hint = serverError ?: help, modifier = tag,
        )
        LoanFieldKind.CHOICE -> ChoiceField(
            label, value, l.options[field.key].orEmpty().map { LookupOption(it, loanOptionLabel(field.key, it)?.let { r -> stringResource(r) } ?: it) },
            { onLoan(field.key, it) }, modifier = tag,
        )
        LoanFieldKind.TIMEZONE -> {
            val zones = remember { timeZones() }
            LookupField(
                label = label, value = value, options = zones, editable = true, allowClear = false,
                onValueChange = { onLoan(field.key, it) }, modifier = tag,
            )
            if (help.isNotBlank() || serverError != null) {
                Text(serverError ?: help, style = MaterialTheme.typography.bodySmall,
                    color = if (serverError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Region-based time zone ids (what PHP's DateTimeZone::listIdentifiers() offers). */
private fun timeZones(): List<LookupOption> = java.time.ZoneId.getAvailableZoneIds()
    .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
    .sorted().map { LookupOption(it, it) }

/** Settings → Cards. Stateless. */
@Composable
internal fun CardsTab(c: Staff090LibraryCards, state: LibrarySettingsUiState, onCards: ((Staff090LibraryCardSettings) -> Staff090LibraryCardSettings) -> Unit) {
    val v = state.cardValues
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (!c.moduleEnabled) {
            InfoCard(Modifier.padding(top = Spacing.sm)) {
                Text(stringResource(R.string.staff090_library_cards_module_off), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FormSectionTitle(stringResource(R.string.staff090_library_cards_layout_section))
        ChoiceField(stringResource(R.string.staff090_library_card_layout), v.layout,
            c.options["layout"].orEmpty().map { LookupOption(it, cardOptionLabel(it)?.let { r -> stringResource(r) } ?: it) },
            { x -> onCards { it.copy(layout = x) } }, modifier = Modifier.fillMaxWidth().testTag("card-layout"))
        ChoiceField(stringResource(R.string.staff090_library_card_code_type), v.codeType,
            c.options["code_type"].orEmpty().map { LookupOption(it, cardOptionLabel(it)?.let { r -> stringResource(r) } ?: it) },
            { x -> onCards { it.copy(codeType = x) } })
        ChoiceField(stringResource(R.string.staff090_library_card_format), v.format,
            c.options["format"].orEmpty().map { LookupOption(it, cardOptionLabel(it)?.let { r -> stringResource(r) } ?: it) },
            { x -> onCards { it.copy(format = x) } })
        FormSectionTitle(stringResource(R.string.staff090_library_cards_text_section))
        PinakesTextField(
            value = v.title, onValueChange = { x -> onCards { it.copy(title = x) } }, label = stringResource(R.string.staff090_library_card_title),
            maxLength = c.limits.titleMax, supportingText = stringResource(R.string.staff090_library_card_title_hint),
            isError = state.fieldErrors["title"] != null, errorText = state.fieldErrors["title"].orEmpty(), modifier = Modifier.fillMaxWidth(),
        )
        PinakesTextField(
            value = v.backText, onValueChange = { x -> onCards { it.copy(backText = x) } }, label = stringResource(R.string.staff090_library_card_back_text),
            maxLength = c.limits.backTextMax, singleLine = false, supportingText = stringResource(R.string.staff090_library_card_back_text_hint),
            isError = state.fieldErrors["back_text"] != null, errorText = state.fieldErrors["back_text"].orEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        )
        FormSectionTitle(stringResource(R.string.staff090_library_cards_show_section))
        SwitchRow(stringResource(R.string.staff090_library_card_show_photo), v.showPhoto, { x -> onCards { it.copy(showPhoto = x) } })
        SwitchRow(stringResource(R.string.staff090_library_card_show_logo), v.showLogo, { x -> onCards { it.copy(showLogo = x) } })
        SwitchRow(stringResource(R.string.staff090_library_card_show_expiry), v.showExpiry, { x -> onCards { it.copy(showExpiry = x) } })
    }
}

private fun loanSectionTitle(s: LoanSection) = when (s) {
    LoanSection.APPROVAL -> R.string.staff090_library_loans_approval
    LoanSection.DURATION -> R.string.staff090_library_loans_duration
    LoanSection.PICKUP -> R.string.staff090_library_loans_pickup
    LoanSection.CARD -> R.string.staff090_library_loans_card
    LoanSection.RENEWALS -> R.string.staff090_library_loans_renewals
    LoanSection.LIMITS -> R.string.staff090_library_loans_limits
    LoanSection.RECALLS -> R.string.staff090_library_loans_recalls
    LoanSection.FINES -> R.string.staff090_library_loans_fines
}

internal fun loanFieldLabel(key: String) = when (key) {
    "auto_approve_requests" -> R.string.staff090_library_loan_auto_approve
    "loan_duration_days" -> R.string.staff090_library_loan_duration
    "max_loan_duration_days" -> R.string.staff090_library_loan_max_duration
    "app_timezone" -> R.string.staff090_library_loan_timezone
    "pickup_expiry_days" -> R.string.staff090_library_loan_pickup_days
    "pickup_reminder_days" -> R.string.staff090_library_loan_pickup_reminder
    "card_expiry_warning_days" -> R.string.staff090_library_loan_card_warning
    "card_renewal_base" -> R.string.staff090_library_loan_card_renewal_base
    "max_renewals" -> R.string.staff090_library_loan_max_renewals
    "max_active_loans_per_user" -> R.string.staff090_library_loan_max_active
    "allow_multiple_loans_same_book" -> R.string.staff090_library_loan_multiple_copies
    "recall_auto_enabled" -> R.string.staff090_library_loan_recalls
    "recall_interval_days" -> R.string.staff090_library_loan_recall_interval
    "recall_max_count" -> R.string.staff090_library_loan_recall_max
    "fine_grace_mode" -> R.string.staff090_library_loan_grace_mode
    "daily_fine_cents" -> R.string.staff090_library_loan_daily_fine
    "fine_grace_days" -> R.string.staff090_library_loan_grace_days
    "fine_max_per_loan_cents" -> R.string.staff090_library_loan_max_fine
    "debt_block_threshold_cents" -> R.string.staff090_library_loan_debt_block
    "fine_charged_email" -> R.string.staff090_library_loan_fine_email
    else -> R.string.staff090_library_loan_unknown
}

private fun loanFieldHelp(key: String): Int? = when (key) {
    "auto_approve_requests" -> R.string.staff090_library_loan_auto_approve_help
    "loan_duration_days" -> R.string.staff090_library_loan_duration_help
    "max_loan_duration_days" -> R.string.staff090_library_loan_max_duration_help
    "app_timezone" -> R.string.staff090_library_loan_timezone_help
    "pickup_expiry_days" -> R.string.staff090_library_loan_pickup_days_help
    "pickup_reminder_days" -> R.string.staff090_library_loan_pickup_reminder_help
    "card_expiry_warning_days" -> R.string.staff090_library_loan_card_warning_help
    "max_renewals" -> R.string.staff090_library_loan_max_renewals_help
    "max_active_loans_per_user" -> R.string.staff090_library_loan_max_active_help
    "allow_multiple_loans_same_book" -> R.string.staff090_library_loan_multiple_copies_help
    "recall_auto_enabled" -> R.string.staff090_library_loan_recalls_help
    "daily_fine_cents" -> R.string.staff090_library_loan_daily_fine_help
    "fine_grace_days" -> R.string.staff090_library_loan_grace_days_help
    "fine_max_per_loan_cents" -> R.string.staff090_library_loan_max_fine_help
    "debt_block_threshold_cents" -> R.string.staff090_library_loan_debt_block_help
    "fine_charged_email" -> R.string.staff090_library_loan_fine_email_help
    else -> null
}

private fun loanOptionLabel(key: String, value: String): Int? = when ("$key=$value") {
    "fine_grace_mode=threshold" -> R.string.staff090_library_grace_threshold
    "fine_grace_mode=deduct" -> R.string.staff090_library_grace_deduct
    "card_renewal_base=today" -> R.string.staff090_library_renewal_today
    "card_renewal_base=expiry" -> R.string.staff090_library_renewal_expiry
    else -> null
}

private fun cardOptionLabel(value: String): Int? = when (value) {
    "classic" -> R.string.staff090_library_card_layout_classic
    "minimal" -> R.string.staff090_library_card_layout_minimal
    "portrait" -> R.string.staff090_library_card_layout_portrait
    "code128" -> R.string.staff090_library_card_code_barcode
    "qr" -> R.string.staff090_library_card_code_qr
    "single" -> R.string.staff090_library_card_format_single
    "sheet" -> R.string.staff090_library_card_format_sheet
    else -> null
}
