package com.pinakes.app.ui.screens.staff.s090.patrons

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.AttendanceOverview
import com.pinakes.app.data.model.AttendanceVisit
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090PatronsRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.AttendanceRules
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.data.staff.VisitOutcome
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.scan.BarcodeAnalyzer
import com.pinakes.app.ui.screens.staff.scan.ScannerBox
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AttendanceTab(val label: Int) { DESK(R.string.staff090_patrons_tab_desk), STATS(R.string.staff090_patrons_tab_stats) }

/** The last check-in at the desk: the visit, or why there is none. */
data class VisitResult(
    val outcome: VisitOutcome,
    val visit: AttendanceVisit? = null,
    val code: String? = null,
    val message: StaffMessage? = null,
    val photo: ByteArray? = null,
)

private data class CheckInSubmission(val code: String, val branchId: Int?)
private data class GuestSubmission(val name: String, val reason: String, val branchId: Int?)

data class AttendanceUiState(
    val tab: AttendanceTab = AttendanceTab.DESK,
    val days: Int = 30,
    val overview: AttendanceOverview? = null,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val branchId: Int? = null,
    val checking: Boolean = false,
    val last: VisitResult? = null,
    /** Bumped on every result, so the screen plays its tone once. */
    val toneTick: Int = 0,
    /** The last [toneTick] whose tone was played: a recomposition or a rotation does not replay it. */
    val tonePlayed: Int = 0,
    val manualError: Boolean = false,
    val guestSheet: Boolean = false,
    val savingGuest: Boolean = false,
    val guestError: StaffMessage? = null,
    val savingSettings: Boolean = false,
    val confirmPurge: Boolean = false,
    val export: File? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
    val disabled: Boolean = false,
)

/**
 * The visitor log at the entrance: the phone scans the patron card (CODE 128 or QR), the
 * server records the visit and answers the kiosk's message; guests are recorded by hand. The
 * statistics tab shows the counters of the web page, the CSV export and, for administrators,
 * the retention and the purge.
 */
@HiltViewModel
class Staff090AttendanceViewModel @Inject constructor(
    private val patrons: Staff090PatronsRepository,
    private val files: StaffFilesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()
    private val debouncer = ScanDebouncer()
    private val checkInKeys = SubmissionKeys()
    private val guestKeys = SubmissionKeys()

    init { load() }

    fun load(days: Int = _state.value.days) {
        _state.update { it.copy(days = days, loading = it.overview == null, error = null) }
        viewModelScope.launch {
            val res = patrons.attendance(days)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        overview = res.data, loading = false,
                        // A branch that closed meanwhile is dropped from the picker.
                        branchId = it.branchId?.takeIf { id -> res.data.branches.any { b -> b.id == id } },
                    )
                    is ApiResult.Failure -> it.copy(
                        loading = false, forbidden = res.isForbidden(), disabled = res.code == "feature_disabled",
                        error = if (it.overview == null) res.toPatronsMessage(R.string.staff_error_load) else null,
                        message = if (it.overview != null) res.toPatronsMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    fun selectTab(tab: AttendanceTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == AttendanceTab.STATS) load()
    }

    fun selectBranch(id: Int?) = _state.update { it.copy(branchId = id) }

    fun onDecoded(raw: String) {
        if (_state.value.checking || _state.value.guestSheet) return
        val code = AttendanceRules.normalizeCard(raw).takeIf { it.isNotEmpty() } ?: return
        val accepted = debouncer.offer(code, SystemClock.elapsedRealtime()) ?: return
        checkIn(accepted)
    }

    fun clearManualError() = _state.update { if (it.manualError) it.copy(manualError = false) else it }

    /** A typed (or keyboard-wedge) card code; true when it was taken. */
    fun submitManual(input: String): Boolean {
        val code = AttendanceRules.normalizeCard(input)
        if (code.isEmpty()) {
            _state.update { it.copy(manualError = true) }
            return false
        }
        if (_state.value.checking) return false
        checkIn(code)
        return true
    }

    private fun checkIn(code: String) {
        val branch = _state.value.branchId
        val key = checkInKeys.keyFor(CheckInSubmission(code, branch))
        debouncer.pause()
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val res = patrons.checkIn(code, branch, key)
            val result = when (res) {
                is ApiResult.Success -> {
                    checkInKeys.reset()
                    VisitResult(AttendanceRules.outcome(res.data), visit = res.data, code = code)
                }
                is ApiResult.Failure -> {
                    // Nothing recorded: a new scan of the same card is a new attempt only
                    // after a definite refusal; a lost answer keeps its key for the replay.
                    if (res.fromEnvelope) checkInKeys.reset()
                    if (res.isForbidden()) _state.update { it.copy(forbidden = true) }
                    VisitResult(AttendanceRules.outcome(res), code = code, message = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
            _state.update { it.copy(checking = false, last = result, toneTick = it.toneTick + 1) }
            debouncer.resume(SystemClock.elapsedRealtime())
            if (res is ApiResult.Success) loadPhoto(res.data)
        }
    }

    private fun loadPhoto(visit: AttendanceVisit) {
        val member = visit.member ?: return
        if (!member.hasPhoto) return
        viewModelScope.launch {
            val res = patrons.photo(member.id)
            if (res is ApiResult.Success) _state.update { s -> if (s.last?.visit?.id == visit.id) s.copy(last = s.last.copy(photo = res.data)) else s }
        }
    }

    fun openGuest() {
        debouncer.pause()
        _state.update { it.copy(guestSheet = true, guestError = null) }
    }

    fun closeGuest() {
        if (_state.value.savingGuest) return
        _state.update { it.copy(guestSheet = false) }
        debouncer.resume(SystemClock.elapsedRealtime())
    }

    fun saveGuest(name: String, reason: String) {
        if (_state.value.savingGuest) return
        val branch = _state.value.branchId
        val key = guestKeys.keyFor(GuestSubmission(name.trim(), reason.trim(), branch))
        _state.update { it.copy(savingGuest = true, guestError = null) }
        viewModelScope.launch {
            when (val res = patrons.guest(name, reason, branch, key)) {
                is ApiResult.Success -> {
                    guestKeys.reset()
                    _state.update {
                        it.copy(savingGuest = false, guestSheet = false, last = VisitResult(VisitOutcome.GUEST, visit = res.data), toneTick = it.toneTick + 1)
                    }
                    debouncer.resume(SystemClock.elapsedRealtime())
                }
                is ApiResult.Failure -> {
                    if (res.code == SubmissionKeys.CONFLICT) guestKeys.reset()
                    _state.update { it.copy(savingGuest = false, forbidden = res.isForbidden(), guestError = res.toPatronsMessage(R.string.staff_error_save)) }
                }
            }
        }
    }

    fun export() {
        val days = _state.value.days
        viewModelScope.launch {
            val res = files.download("staff/attendance/export.csv?days=$days", "presenze_${days}g.csv")
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(export = res.data)
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }

    fun saveRetention(days: Int) {
        if (_state.value.savingSettings) return
        _state.update { it.copy(savingSettings = true) }
        viewModelScope.launch {
            val res = patrons.saveRetention(days)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        savingSettings = false, overview = it.overview?.copy(retention = res.data.retention),
                        message = res.data.message?.takeIf { m -> m.isNotBlank() }?.let { m -> StaffMessage(text = m) } ?: StaffMessage(R.string.staff090_patrons_retention_saved),
                    )
                    is ApiResult.Failure -> it.copy(savingSettings = false, forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun askPurge() = _state.update { it.copy(confirmPurge = true) }
    fun dismissPurge() = _state.update { it.copy(confirmPurge = false) }

    fun purge() {
        if (_state.value.savingSettings) return
        _state.update { it.copy(confirmPurge = false, savingSettings = true) }
        viewModelScope.launch {
            val res = patrons.purgeAttendance()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(savingSettings = false, message = StaffMessage(R.string.staff090_patrons_purged, args = listOf(res.data.deleted)))
                    is ApiResult.Failure -> it.copy(savingSettings = false, forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) load()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun tonePlayed(tick: Int) = _state.update { it.copy(tonePlayed = maxOf(it.tonePlayed, tick)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Staff090AttendanceScreen(onNavigateUp: () -> Unit) {
    val vm: Staff090AttendanceViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.attendance || state.disabled, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.toneTick) {
        val last = state.last
        if (AttendanceRules.tonePending(state.toneTick, state.tonePlayed) && last != null) {
            val ok = AttendanceRules.successTone(last.outcome)
            DeskTone.play(ok)
            haptics.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
            vm.tonePlayed(state.toneTick)
        }
    }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_attendance), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                AttendanceTab.entries.forEach { tab ->
                    Tab(selected = tab == state.tab, onClick = { vm.selectTab(tab) }, text = { Text(stringResource(tab.label)) }, modifier = Modifier.testTag("tab-${tab.name.lowercase()}"))
                }
            }
            if (state.tab == AttendanceTab.DESK) DeskTab(state, vm) else StatsTab(state, vm)
        }
    }
    if (state.guestSheet) GuestSheet(state, vm)
    if (state.confirmPurge) ConfirmDialog(
        title = stringResource(R.string.staff090_patrons_purge),
        body = stringResource(R.string.staff090_patrons_purge_body, state.overview?.retention?.days ?: 0),
        confirmLabel = stringResource(R.string.staff090_patrons_purge),
        onConfirm = vm::purge, onDismiss = vm::dismissPurge,
    )
    state.export?.let { ExportFileDialog(it, "text/csv", stringResource(R.string.staff090_attendance), onDismiss = vm::closeExport) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeskTab(state: AttendanceUiState, vm: Staff090AttendanceViewModel) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScannerBox(
            formats = BarcodeAnalyzer.PATRON_CARD_FORMATS, onCode = vm::onDecoded, looking = state.checking,
            permissionText = stringResource(R.string.staff090_patrons_scan_permission),
        )
        Text(stringResource(R.string.staff090_patrons_scan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var manual by rememberSaveable { mutableStateOf("") }
        val submit = { if (vm.submitManual(manual)) manual = "" }
        PinakesTextField(
            value = manual,
            onValueChange = { manual = it.take(64); vm.clearManualError() },
            label = stringResource(R.string.staff090_patrons_card_code),
            isError = state.manualError, errorText = stringResource(R.string.staff090_patrons_card_code_required),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = submit, enabled = !state.checking) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.staff090_patrons_check_in))
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("card-code-input"),
        )
        val branches = state.overview?.branches.orEmpty()
        if (branches.isNotEmpty()) {
            Text(stringResource(R.string.staff090_patrons_branch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val chip = @Composable { selected: Boolean, label: String, onClick: () -> Unit ->
                    FilterChip(
                        selected = selected, onClick = onClick, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
                chip(state.branchId == null, stringResource(R.string.staff090_patrons_branch_none)) { vm.selectBranch(null) }
                branches.forEach { b -> chip(state.branchId == b.id, b.name) { vm.selectBranch(b.id) } }
            }
        }
        state.last?.let { VisitCard(it) }
        SecondaryButton(
            stringResource(R.string.staff090_patrons_guest), onClick = vm::openGuest, leadingIcon = Icons.Outlined.PersonAdd,
            modifier = Modifier.fillMaxWidth().testTag("guest-open"),
        )
        state.overview?.let { o ->
            Text(
                stringResource(R.string.staff090_patrons_today_counts, o.today.members, o.today.guests),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** The last check-in: green for a welcome, amber for a card to look at, red for an unknown card. */
@Composable
private fun VisitCard(result: VisitResult) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (result.outcome) {
        VisitOutcome.WELCOME, VisitOutcome.GUEST -> colors.primaryContainer to colors.onPrimaryContainer
        VisitOutcome.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
        VisitOutcome.UNKNOWN_CARD, VisitOutcome.ERROR -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth().testTag("visit-${result.outcome.name.lowercase()}")) {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            val member = result.visit?.member
            if (member != null) PatronPhoto(result.photo, stringResource(R.string.staff090_patrons_photo), size = 72.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val visit = result.visit
                val title = when {
                    member != null -> member.shortName
                    visit?.type == "guest" -> visit.guest?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff090_patrons_guest_anonymous)
                    result.outcome == VisitOutcome.UNKNOWN_CARD -> stringResource(R.string.staff090_patrons_err_card_not_found)
                    else -> result.message?.resolve().orEmpty()
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = content)
                // The server's kiosk message, in the operator's language.
                val detail = visit?.message?.takeIf { it.isNotBlank() } ?: result.code?.let { stringResource(R.string.staff090_patrons_card_line, it) }
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = content) }
                if (member != null && member.cardExpired) {
                    Text(stringResource(R.string.staff090_patrons_card_expired, DateFormat.date(member.cardExpiresAt)), style = MaterialTheme.typography.bodySmall, color = content)
                }
                if (member != null && !member.cardActive) {
                    Text(stringResource(R.string.staff090_patrons_card_not_active), style = MaterialTheme.typography.bodySmall, color = content)
                }
                visit?.branch?.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = content) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestSheet(state: AttendanceUiState, vm: Staff090AttendanceViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = vm::closeGuest, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.staff090_patrons_guest), style = MaterialTheme.typography.headlineSmall)
            PinakesTextField(name, { name = it }, stringResource(R.string.staff090_patrons_guest_name), maxLength = 150, modifier = Modifier.fillMaxWidth().testTag("guest-name"))
            PinakesTextField(reason, { reason = it }, stringResource(R.string.staff090_patrons_guest_reason), maxLength = 255, modifier = Modifier.fillMaxWidth().testTag("guest-reason"))
            state.guestError?.let { InlineErrorBanner(message = it.resolve()) }
            PrimaryButton(
                stringResource(R.string.staff090_patrons_guest_save), onClick = { vm.saveGuest(name, reason) }, loading = state.savingGuest,
                modifier = Modifier.fillMaxWidth().testTag("guest-save"),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsTab(state: AttendanceUiState, vm: Staff090AttendanceViewModel) {
    val overview = state.overview
    when {
        state.loading -> LoadingState()
        overview == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = { vm.load() })
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                overview.periods.forEach { days ->
                    FilterChip(
                        selected = days == state.days, onClick = { vm.load(days) },
                        label = { Text(stringResource(R.string.staff090_patrons_period_days, days)) },
                        modifier = Modifier.testTag("period-$days"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            InfoCard(Modifier.testTag("attendance-totals")) {
                InfoLine(stringResource(R.string.staff090_patrons_today), stringResource(R.string.staff090_patrons_counts, overview.today.total, overview.today.members, overview.today.guests))
                InfoLine(stringResource(R.string.staff090_patrons_period_total), stringResource(R.string.staff090_patrons_counts, overview.totals.total, overview.totals.members, overview.totals.guests))
            }
            val days = overview.daily.filter { it.total > 0 }.reversed()
            FormSectionTitle(stringResource(R.string.staff090_patrons_by_day))
            if (days.isEmpty()) Text(stringResource(R.string.staff090_patrons_no_visits), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val dayBars = AttendanceRules.barFractions(days.map { it.total })
            days.forEachIndexed { i, d -> BarLine(DateFormat.date(d.date), d.total, dayBars[i]) }
            val hours = overview.hourly.filter { it.total > 0 }
            if (hours.isNotEmpty()) {
                FormSectionTitle(stringResource(R.string.staff090_patrons_by_hour))
                val hourBars = AttendanceRules.barFractions(hours.map { it.total })
                hours.forEachIndexed { i, h -> BarLine("%02d:00".format(java.util.Locale.ROOT, h.hour), h.total, hourBars[i]) }
            }
            if (overview.recent.isNotEmpty()) {
                FormSectionTitle(stringResource(R.string.staff090_patrons_recent))
                InfoCard {
                    overview.recent.forEach { r ->
                        InfoLine(
                            DateFormat.dateTime(r.createdAt),
                            r.label.ifBlank { stringResource(if (r.type == "guest") R.string.staff090_patrons_guest_anonymous else R.string.staff090_patrons_member) },
                        )
                    }
                }
            }
            SecondaryButton(
                stringResource(R.string.staff090_patrons_export_csv), onClick = vm::export, leadingIcon = Icons.Outlined.Download,
                modifier = Modifier.fillMaxWidth().testTag("attendance-export"),
            )
            if (overview.canConfigure) RetentionCard(overview, state.savingSettings, vm)
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun BarLine(label: String, value: Int, fraction: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f).heightIn(min = 6.dp))
        Text(value.toString(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp).padding(start = Spacing.sm))
    }
}

@Composable
private fun RetentionCard(overview: AttendanceOverview, saving: Boolean, vm: Staff090AttendanceViewModel) {
    val retention = overview.retention
    var text by rememberSaveable(retention.days) { mutableStateOf(retention.days.toString()) }
    val parsed = AttendanceRules.parseRetention(text, retention.maxDays)
    FormSectionTitle(stringResource(R.string.staff090_patrons_retention))
    InfoCard(Modifier.testTag("attendance-retention")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.staff090_patrons_retention_hint, retention.maxDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PinakesTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) }, label = stringResource(R.string.staff090_patrons_retention_days),
                isError = parsed == null, errorText = stringResource(R.string.staff090_patrons_retention_invalid, retention.maxDays),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("retention-days"),
            )
            PrimaryButton(stringResource(R.string.action_save), onClick = { parsed?.let(vm::saveRetention) }, loading = saving, enabled = parsed != null, modifier = Modifier.fillMaxWidth())
            SecondaryButton(
                stringResource(R.string.staff090_patrons_purge), onClick = vm::askPurge, enabled = !saving && retention.days > 0,
                leadingIcon = Icons.Outlined.DeleteSweep, modifier = Modifier.fillMaxWidth().testTag("attendance-purge"),
            )
        }
    }
}
