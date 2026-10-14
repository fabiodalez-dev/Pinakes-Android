package com.pinakes.app.ui.screens.staff.tier2

import android.os.SystemClock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.PeriodicalDetail
import com.pinakes.app.data.model.PeriodicalIssue
import com.pinakes.app.data.model.PeriodicalScan
import com.pinakes.app.data.model.PeriodicalSummary
import com.pinakes.app.data.model.StaffSubscription
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffTier2Repository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.IssueStatus
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.data.staff.SubscriptionForm
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.scan.BarcodeAnalyzer
import com.pinakes.app.ui.screens.staff.scan.ScannerBox
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

/** Localized label of a periodical issue status (raw value for states the app does not know). */
internal fun issueStatusLabel(status: String): Int? = when (status) {
    "posseduto" -> R.string.staff_issue_status_owned
    "mancante" -> R.string.staff_issue_status_missing
    "atteso" -> R.string.staff_issue_status_expected
    "smarrito" -> R.string.staff_issue_status_lost
    "reclamato" -> R.string.staff_issue_status_claimed
    "scartato" -> R.string.staff_issue_status_discarded
    else -> null
}

// =============================================================================================
// Desk: scan an issue, find a masthead
// =============================================================================================

data class PeriodicalsDeskUiState(
    val looking: Boolean = false,
    val scan: PeriodicalScan? = null,
    val scannedCode: String = "",
    val busy: Boolean = false,
    val query: String = "",
    val periodicals: List<PeriodicalSummary> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffPeriodicalsDeskViewModel @Inject constructor(private val tier2: StaffTier2Repository) : ViewModel() {
    private val _state = MutableStateFlow(PeriodicalsDeskUiState())
    val state: StateFlow<PeriodicalsDeskUiState> = _state.asStateFlow()
    private val debouncer = ScanDebouncer()
    private var searchJob: Job? = null

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val res = tier2.periodicals(_state.value.query, null)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(periodicals = res.data.items, nextCursor = res.data.nextCursor, loading = false, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        val q = _state.value.query
        viewModelScope.launch {
            val res = tier2.periodicals(q, cursor)
            if (res is ApiResult.Success && _state.value.query == q) {
                _state.update { it.copy(periodicals = (it.periodicals + res.data.items).distinctBy(PeriodicalSummary::id), nextCursor = res.data.nextCursor) }
            }
        }
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); reload() }
    }

    fun onDecoded(raw: String) {
        if (_state.value.looking || _state.value.scan != null) return
        val code = raw.filter(Char::isDigit).takeIf { it.isNotEmpty() } ?: return
        val accepted = debouncer.offer(code, SystemClock.elapsedRealtime()) ?: return
        lookup(accepted)
    }

    /** The typed (or keyboard-wedge scanned) code; the field's text stays in the screen. */
    fun submitManual(input: String): Boolean {
        val code = input.trim()
        if (code.isEmpty() || _state.value.looking) return false
        lookup(code)
        return true
    }

    private fun lookup(code: String) {
        debouncer.pause()
        _state.update { it.copy(looking = true, scannedCode = code) }
        viewModelScope.launch {
            val res = tier2.scanLookup(code)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(looking = false, scan = res.data)
                    is ApiResult.Failure -> it.copy(looking = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                }
            }
            if (_state.value.scan == null) debouncer.resume(SystemClock.elapsedRealtime())
        }
    }

    fun dismissScan() {
        _state.update { it.copy(scan = null) }
        debouncer.resume(SystemClock.elapsedRealtime())
    }

    /** Receive or claim the scanned issue. */
    fun act(receive: Boolean) {
        val issue = _state.value.scan?.issue ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = if (receive) tier2.receiveIssue(issue.id) else tier2.claimIssue(issue.id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        busy = false, scan = null,
                        message = StaffMessage(if (receive) R.string.staff_issue_received else R.string.staff_issue_claimed, args = listOf(res.data.number, res.data.periodical)),
                    )
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (_state.value.scan == null) debouncer.resume(SystemClock.elapsedRealtime())
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** The periodicals desk: scan an arriving issue (EAN-13 with its add-on) or type the code, then receive or claim it; mastheads below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPeriodicalsDeskScreen(onNavigateUp: () -> Unit, onOpenPeriodical: (Int) -> Unit) {
    val vm: StaffPeriodicalsDeskViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.periodicals, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_periodicals), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
            item(key = "scanner") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    ScannerBox(
                        formats = BarcodeAnalyzer.ISBN_FORMATS, onCode = vm::onDecoded, looking = state.looking,
                        permissionText = stringResource(R.string.staff_periodicals_scan_permission),
                    )
                    var manual by rememberSaveable { mutableStateOf("") }
                    val submit = { if (vm.submitManual(manual)) manual = "" }
                    PinakesTextField(
                        value = manual, onValueChange = { manual = it.take(30) }, label = stringResource(R.string.staff_periodicals_code),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.staff_scan_lookup)) } },
                        modifier = Modifier.fillMaxWidth().testTag("issue-code"),
                    )
                    FormSectionTitle(stringResource(R.string.staff_periodicals_mastheads))
                    SearchField(query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_periodicals_search), modifier = Modifier.fillMaxWidth())
                    state.error?.let { InlineErrorBanner(message = it.resolve(), onRetry = vm::reload) }
                }
            }
            items(state.periodicals, key = { it.id }) { p ->
                StaffActionRow(
                    Icons.Outlined.Newspaper, p.title,
                    listOfNotNull(p.issn, stringResource(R.string.staff_periodicals_counts, p.yearsCount, p.issuesCount)).joinToString(" · "),
                ) { onOpenPeriodical(p.id) }
            }
            if (state.nextCursor != null) item(key = "more") { LaunchedEffect(state.periodicals.size) { vm.loadMore() } }
        }
    }

    state.scan?.let { scan ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!state.busy) vm.dismissScan() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().testTag("issue-sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val issue = scan.issue
                when {
                    scan.match == "issue" && issue != null -> {
                        Text(issue.title, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.staff_issue_line, issue.number, issue.year), style = MaterialTheme.typography.bodyLarge)
                        Text(issueStatusLabel(issue.status)?.let { stringResource(it) } ?: issue.status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (IssueStatus.receivable(issue.status)) {
                            PrimaryButton(stringResource(R.string.staff_issue_receive), onClick = { vm.act(receive = true) }, loading = state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("issue-receive"))
                        }
                        if (IssueStatus.claimable(issue.status)) {
                            SecondaryButton(stringResource(R.string.staff_issue_claim), onClick = { vm.act(receive = false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                        }
                        SecondaryButton(stringResource(R.string.staff_periodicals_open), onClick = { vm.dismissScan(); onOpenPeriodical(issue.periodicalId) }, modifier = Modifier.fillMaxWidth())
                    }
                    scan.title != null -> {
                        Text(scan.title.title, style = MaterialTheme.typography.headlineSmall)
                        Text(scan.message ?: stringResource(R.string.staff_periodicals_title_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PrimaryButton(stringResource(R.string.staff_periodicals_open), onClick = { vm.dismissScan(); onOpenPeriodical(scan.title.id) }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Text(stringResource(if (scan.match == "ambiguous") R.string.staff_periodicals_ambiguous else R.string.staff_periodicals_no_match), style = MaterialTheme.typography.titleMedium)
                        Text(scan.message ?: state.scannedCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PinakesTextButton(stringResource(R.string.staff_scan_next), onClick = vm::dismissScan, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

// =============================================================================================
// One masthead: years, issues, subscriptions
// =============================================================================================

data class IssueDraft(val yearId: Int, val number: String = "", val date: String? = null, val status: String = "atteso")
data class YearDraft(val year: String = "", val volume: String = "", val bound: Boolean = false)

data class PeriodicalUiState(
    val detail: PeriodicalDetail? = null,
    val loading: Boolean = true,
    val loadError: StaffMessage? = null,
    val expanded: Set<Int> = emptySet(),
    val issues: Map<Int, List<PeriodicalIssue>> = emptyMap(),
    val subscriptions: List<StaffSubscription> = emptyList(),
    val issueDraft: IssueDraft? = null,
    val yearDraft: YearDraft? = null,
    val subscription: SubscriptionForm? = null,
    val busy: Boolean = false,
    val formError: StaffMessage? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffPeriodicalViewModel @Inject constructor(
    private val tier2: StaffTier2Repository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val periodicalId: Int = savedState.get<Int>(Routes.ARG_PERIODICAL_ID) ?: 0
    private val _state = MutableStateFlow(PeriodicalUiState())
    val state: StateFlow<PeriodicalUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.detail == null, loadError = null) }
        viewModelScope.launch {
            val res = tier2.periodical(periodicalId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(detail = res.data, loading = false)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), loadError = res.toStaffMessage(R.string.staff_error_load))
                }
            }
            (tier2.subscriptions(periodicalId) as? ApiResult.Success)?.data?.let { subs -> _state.update { it.copy(subscriptions = subs) } }
            _state.value.expanded.forEach { loadIssues(it) }
        }
    }

    fun toggleYear(yearId: Int) {
        val open = yearId !in _state.value.expanded
        _state.update { s -> s.copy(expanded = if (open) s.expanded + yearId else s.expanded - yearId) }
        if (open) loadIssues(yearId)
    }

    private fun loadIssues(yearId: Int) {
        viewModelScope.launch {
            val res = tier2.yearIssues(yearId)
            if (res is ApiResult.Success) _state.update { it.copy(issues = it.issues + (yearId to res.data)) }
            else if (res is ApiResult.Failure) _state.update { it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load)) }
        }
    }

    fun issueAction(issue: PeriodicalIssue, yearId: Int, receive: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = if (receive) tier2.receiveIssue(issue.id) else tier2.claimIssue(issue.id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, message = StaffMessage(if (receive) R.string.staff_issue_received else R.string.staff_issue_claimed, args = listOf(res.data.number, res.data.periodical)))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            loadIssues(yearId)
        }
    }

    fun startIssue(yearId: Int) = _state.update { it.copy(issueDraft = IssueDraft(yearId), formError = null) }
    fun editIssue(transform: (IssueDraft) -> IssueDraft) = _state.update { s -> s.copy(issueDraft = s.issueDraft?.let(transform), formError = null) }
    fun startYear() = _state.update { it.copy(yearDraft = YearDraft(), formError = null) }
    fun editYear(transform: (YearDraft) -> YearDraft) = _state.update { s -> s.copy(yearDraft = s.yearDraft?.let(transform), formError = null) }
    fun startSubscription(existing: StaffSubscription?) = _state.update {
        it.copy(subscription = existing?.let(SubscriptionForm::from) ?: SubscriptionForm(periodicalId = periodicalId), formError = null)
    }
    fun editSubscription(transform: (SubscriptionForm) -> SubscriptionForm) = _state.update { s -> s.copy(subscription = s.subscription?.let(transform), formError = null) }
    fun closeForms() = _state.update { it.copy(issueDraft = null, yearDraft = null, subscription = null, formError = null) }

    fun saveIssue() {
        val draft = _state.value.issueDraft ?: return
        if (draft.number.isBlank() || _state.value.busy) return
        busy {
            when (val res = tier2.addIssue(draft.yearId, draft.number, draft.date, draft.status)) {
                is ApiResult.Success -> { done(R.string.staff_issue_added); loadIssues(draft.yearId); _state.update { it.copy(expanded = it.expanded + draft.yearId) } }
                is ApiResult.Failure -> failed(res)
            }
        }
    }

    fun saveYear() {
        val draft = _state.value.yearDraft ?: return
        val year = draft.year.trim().toIntOrNull() ?: return
        busy {
            when (val res = tier2.addYear(periodicalId, year, draft.volume, draft.bound)) {
                is ApiResult.Success -> { done(R.string.staff_year_added); load() }
                is ApiResult.Failure -> failed(res)
            }
        }
    }

    fun saveSubscription() {
        val form = _state.value.subscription ?: return
        if (!form.valid) return
        busy {
            when (val res = tier2.saveSubscription(form.id, form.toJson())) {
                is ApiResult.Success -> {
                    done(R.string.staff_subscription_saved)
                    (tier2.subscriptions(periodicalId) as? ApiResult.Success)?.data?.let { subs -> _state.update { it.copy(subscriptions = subs) } }
                }
                is ApiResult.Failure -> failed(res)
            }
        }
    }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch { block() }
    }

    private fun done(message: Int) = _state.update { it.copy(busy = false, issueDraft = null, yearDraft = null, subscription = null, message = StaffMessage(message)) }
    private fun failed(res: ApiResult.Failure) = _state.update { it.copy(busy = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save)) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPeriodicalScreen(onNavigateUp: () -> Unit) {
    val vm: StaffPeriodicalViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.periodicals, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = state.detail?.title ?: stringResource(R.string.staff_periodicals), onNavigateUp = onNavigateUp) },
        floatingActionButton = { if (state.detail != null) StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_year_add), vm::startYear) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            detail == null -> ErrorState(message = state.loadError?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                item(key = "facts") {
                    InfoCard(Modifier.padding(top = Spacing.sm)) {
                        InfoLine("ISSN", detail.issn)
                        InfoLine(stringResource(R.string.staff_periodicals_frequency), detail.frequency)
                        InfoLine(stringResource(R.string.staff_periodicals_holdings), detail.holdings)
                    }
                }
                item(key = "years-title") { FormSectionTitle(stringResource(R.string.staff_periodicals_years)) }
                detail.years.forEach { year ->
                    item(key = "y${year.id}") {
                        Surface(
                            onClick = { vm.toggleYear(year.id) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("year-${year.id}"),
                        ) {
                            Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(listOfNotNull(year.year.toString(), year.volume).joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(R.string.staff_periodicals_year_counts, year.ownedCount, year.issuesCount), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (year.id in state.expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                            }
                        }
                    }
                    if (year.id in state.expanded) {
                        items(state.issues[year.id].orEmpty(), key = { "i${it.id}" }) { issue ->
                            Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs).testTag("issue-${issue.id}"),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.staff_issue_number, issue.number ?: issue.sequence ?: issue.id.toString()), style = MaterialTheme.typography.bodyMedium)
                                    Text(listOfNotNull(issueStatusLabel(issue.status)?.let { stringResource(it) } ?: issue.status,
                                        (issue.coverDate ?: issue.publicationDate)?.let(DateFormat::date)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (IssueStatus.receivable(issue.status)) {
                                    PinakesTextButton(stringResource(R.string.staff_issue_receive), onClick = { vm.issueAction(issue, year.id, true) }, enabled = !state.busy)
                                } else if (IssueStatus.claimable(issue.status)) {
                                    PinakesTextButton(stringResource(R.string.staff_issue_claim), onClick = { vm.issueAction(issue, year.id, false) }, enabled = !state.busy)
                                }
                            }
                        }
                        item(key = "add-${year.id}") {
                            PinakesTextButton(stringResource(R.string.staff_issue_add), onClick = { vm.startIssue(year.id) }, modifier = Modifier.padding(start = Spacing.md))
                        }
                    }
                }
                item(key = "subs-title") { FormSectionTitle(stringResource(R.string.staff_subscriptions)) }
                items(state.subscriptions, key = { "s${it.id}" }) { s ->
                    StaffActionRow(Icons.Outlined.Newspaper, s.supplier,
                        listOfNotNull(s.endDate?.let { stringResource(R.string.staff_subscription_until, DateFormat.date(it)) },
                            if (s.active) null else stringResource(R.string.staff_subscription_inactive)).joinToString(" · ").ifBlank { null }) { vm.startSubscription(s) }
                }
                item(key = "subs-add") { SecondaryButton(stringResource(R.string.staff_subscription_add), onClick = { vm.startSubscription(null) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }

    state.issueDraft?.let { draft ->
        FormSheet(stringResource(R.string.staff_issue_add), state.formError, state.busy, enabled = draft.number.isNotBlank(), onSave = vm::saveIssue, onDismiss = vm::closeForms) {
            PinakesTextField(draft.number, { v -> vm.editIssue { it.copy(number = v) } }, stringResource(R.string.staff_issue_number_label), modifier = Modifier.fillMaxWidth().testTag("issue-number"))
            DatePickerField(stringResource(R.string.staff_issue_date), draft.date, { v -> vm.editIssue { it.copy(date = v) } }, allowClear = true)
            LookupField(
                label = stringResource(R.string.staff_copy_status), value = draft.status,
                displayValue = issueStatusLabel(draft.status)?.let { stringResource(it) } ?: draft.status,
                options = IssueStatus.ALL.map { LookupOption(it, issueStatusLabel(it)?.let { r -> stringResource(r) } ?: it) },
                editable = false, allowClear = false, onValueChange = { v -> vm.editIssue { it.copy(status = v) } }, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.yearDraft?.let { draft ->
        FormSheet(stringResource(R.string.staff_year_add), state.formError, state.busy, enabled = draft.year.trim().toIntOrNull() != null, onSave = vm::saveYear, onDismiss = vm::closeForms) {
            PinakesTextField(draft.year, { v -> vm.editYear { it.copy(year = v.filter(Char::isDigit).take(4)) } }, stringResource(R.string.staff_year_label),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("year-input"))
            PinakesTextField(draft.volume, { v -> vm.editYear { it.copy(volume = v) } }, stringResource(R.string.staff_year_volume), modifier = Modifier.fillMaxWidth())
            SwitchRow(stringResource(R.string.staff_year_bound), draft.bound, { v -> vm.editYear { it.copy(bound = v) } })
        }
    }
    state.subscription?.let { form ->
        FormSheet(stringResource(if (form.id > 0) R.string.staff_subscription_edit else R.string.staff_subscription_add), state.formError, state.busy,
            enabled = form.valid, onSave = vm::saveSubscription, onDismiss = vm::closeForms) {
            PinakesTextField(form.supplier, { v -> vm.editSubscription { it.copy(supplier = v) } }, stringResource(R.string.staff_subscription_supplier),
                modifier = Modifier.fillMaxWidth().testTag("subscription-supplier"))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PinakesTextField(form.cost, { v -> vm.editSubscription { it.copy(cost = v) } }, stringResource(R.string.staff_subscription_cost),
                    isError = !form.costValid, errorText = stringResource(R.string.staff_field_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(2f))
                PinakesTextField(form.currency, { v -> vm.editSubscription { it.copy(currency = v.take(3).uppercase()) } }, stringResource(R.string.staff_subscription_currency), modifier = Modifier.weight(1f))
            }
            DatePickerField(stringResource(R.string.staff_subscription_start), form.startDate, { v -> vm.editSubscription { it.copy(startDate = v) } }, allowClear = true)
            DatePickerField(stringResource(R.string.staff_subscription_end), form.endDate, { v -> vm.editSubscription { it.copy(endDate = v) } }, allowClear = true)
            SwitchRow(stringResource(R.string.staff_subscription_auto_renew), form.autoRenew, { v -> vm.editSubscription { it.copy(autoRenew = v) } })
            SwitchRow(stringResource(R.string.staff_subscription_active), form.active, { v -> vm.editSubscription { it.copy(active = v) } })
            PinakesTextField(form.notes, { v -> vm.editSubscription { it.copy(notes = v) } }, stringResource(R.string.staff_field_notes), singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
        }
    }
}

/** A bottom-sheet form with an error banner and a Save button (Tier 2 forms). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormSheet(
    title: String,
    error: StaffMessage?,
    saving: Boolean,
    enabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            error?.let { InlineErrorBanner(message = it.resolve()) }
            content()
            PrimaryButton(stringResource(R.string.action_save), onClick = onSave, loading = saving, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("form-save"))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
