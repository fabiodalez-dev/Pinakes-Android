package com.pinakes.app.ui.screens.staff.s090.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryClosure
import com.pinakes.app.data.model.Staff090LibraryCustomHoliday
import com.pinakes.app.data.model.Staff090LibraryDay
import com.pinakes.app.data.model.Staff090LibraryHolidayPreset
import com.pinakes.app.data.model.Staff090LibraryCodeName
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090LibraryRepository
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.content.PickedFile
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The national holiday picker: a country (and region) previewed for a year, the rules ticked. */
data class HolidayPickerState(
    val countries: List<Staff090LibraryCodeName> = emptyList(),
    val preset: Staff090LibraryHolidayPreset = Staff090LibraryHolidayPreset(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val applying: Boolean = false,
    val error: StaffMessage? = null,
)

data class ClosuresCalendarUiState(
    val cal: Staff090LibraryCalendar? = null,
    /** The month and branch the operator is looking at (null = the server's default). */
    val month: String? = null,
    val branchId: Int? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val exit: StaffMessage? = null,
    val busy: Boolean = false,
    val message: StaffMessage? = null,
    val weekdays: Set<Int> = emptySet(),
    val yearly: Map<Int, Boolean> = emptyMap(),
    val day: Staff090LibraryDay? = null,
    val closureForm: ClosureForm? = null,
    val customForm: CustomForm? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: StaffMessage? = null,
    val saving: Boolean = false,
    val confirmClosure: Staff090LibraryClosure? = null,
    val confirmCustom: Staff090LibraryCustomHoliday? = null,
    val holidays: HolidayPickerState? = null,
    val ics: PickedFile? = null,
    val icsBranch: Int? = null,
    val icsError: StaffMessage? = null,
    val icsUploading: Boolean = false,
) {
    val weekdaysChanged: Boolean get() = cal != null && weekdays != cal.closedWeekdays.toSet()
    val yearlyChanged: Map<Int, Boolean> get() = cal?.let { c -> CalendarGrid.yearly(c.closures).filter { yearly[it.id] != null && yearly[it.id] != it.active }.associate { it.id to yearly.getValue(it.id) } }.orEmpty()
}

@HiltViewModel
class ClosuresCalendarViewModel @Inject constructor(
    private val repo: Staff090LibraryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ClosuresCalendarUiState())
    val state: StateFlow<ClosuresCalendarUiState> = _state.asStateFlow()
    private val closureKeys = SubmissionKeys()
    private val customKeys = SubmissionKeys()

    init { load() }

    fun load() {
        val s = _state.value
        _state.update { it.copy(loading = it.cal == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = repo.calendar(s.month, s.branchId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        cal = res.data, loading = false, refreshing = false, month = res.data.month,
                        weekdays = res.data.closedWeekdays.toSet(),
                        yearly = CalendarGrid.yearly(res.data.closures).associate { c -> c.id to c.active },
                    )
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, exit = res.exitMessage(), error = res.toLibraryMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    fun shiftMonth(delta: Long) {
        val current = _state.value.cal?.month ?: return
        _state.update { it.copy(month = CalendarGrid.shift(current, delta)) }
        load()
    }

    fun setBranch(id: Int?) {
        _state.update { it.copy(branchId = id) }
        load()
    }

    fun openDay(day: Staff090LibraryDay) = _state.update { it.copy(day = day) }
    fun closeDay() = _state.update { it.copy(day = null) }

    // ---------- weekly closed days ----------

    fun toggleWeekday(day: Int) = _state.update { s -> s.copy(weekdays = if (day in s.weekdays) s.weekdays - day else s.weekdays + day) }

    fun saveWeekdays() {
        val s = _state.value
        if (s.busy || !s.weekdaysChanged) return
        if (!CalendarGrid.weekdaysValid(s.weekdays)) {
            _state.update { it.copy(message = StaffMessage(R.string.staff090_library_weekdays_all_closed)) }
            return
        }
        write { repo.saveWeekdays(s.weekdays.toList()).let { it.result } }
    }

    // ---------- yearly switches ----------

    fun setYearly(id: Int, on: Boolean) = _state.update { it.copy(yearly = it.yearly + (id to on)) }

    fun saveYearly() {
        val changed = _state.value.yearlyChanged
        if (_state.value.busy || changed.isEmpty()) return
        write { repo.saveYearly(changed).result }
    }

    // ---------- closures ----------

    fun startClosure(day: String? = null) = _state.update {
        it.copy(closureForm = ClosureForm(startDate = day, branchId = it.branchId), fieldErrors = emptyMap(), formError = null, day = null)
    }

    fun editClosure(c: Staff090LibraryClosure) = _state.update { it.copy(closureForm = ClosureForm.from(c), fieldErrors = emptyMap(), formError = null, day = null) }
    fun changeClosure(transform: (ClosureForm) -> ClosureForm) = _state.update { s -> s.copy(closureForm = s.closureForm?.let(transform), formError = null) }
    fun closeClosure() = _state.update { it.copy(closureForm = null, fieldErrors = emptyMap()) }

    fun saveClosure() {
        val form = _state.value.closureForm ?: return
        if (_state.value.saving || !form.valid) return
        _state.update { it.copy(saving = true, formError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val body = form.body()
            val saved = if (form.id == null) {
                repo.createClosure(body, closureKeys.keyFor(body)).also { r ->
                    if (r.result is ApiResult.Success || (r.result as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) closureKeys.reset()
                }
            } else repo.updateClosure(form.id, body)
            formDone(saved.result, saved.fields) { it.copy(closureForm = null) }
        }
    }

    fun askDeleteClosure(c: Staff090LibraryClosure) = _state.update { it.copy(confirmClosure = c, closureForm = null) }
    fun deleteClosure() {
        val c = _state.value.confirmClosure ?: return
        _state.update { it.copy(confirmClosure = null) }
        write(R.string.staff090_library_deleted) { repo.deleteClosure(c.id) }
    }

    // ---------- custom holidays ----------

    fun startCustom() = _state.update {
        val options = it.cal?.customHolidayOptions
        it.copy(customForm = CustomForm(kind = options?.kinds?.firstOrNull() ?: "fixed", calendar = options?.calendars?.firstOrNull { c -> c.available }?.code.orEmpty(), branchId = it.branchId),
            fieldErrors = emptyMap(), formError = null)
    }

    fun editCustom(h: Staff090LibraryCustomHoliday) = _state.update { it.copy(customForm = CustomForm.from(h), fieldErrors = emptyMap(), formError = null) }
    fun changeCustom(transform: (CustomForm) -> CustomForm) = _state.update { s -> s.copy(customForm = s.customForm?.let(transform), formError = null) }
    fun closeCustom() = _state.update { it.copy(customForm = null, fieldErrors = emptyMap()) }

    fun saveCustom() {
        val s = _state.value
        val form = s.customForm ?: return
        val options = s.cal?.customHolidayOptions ?: return
        val months = options.calendars.firstOrNull { it.code == form.calendar }?.months?.size ?: 12
        if (s.saving || CustomForms.invalid(form, options.maxDurationDays, options.maxDates, months).isNotEmpty()) return
        _state.update { it.copy(saving = true, formError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val body = CustomForms.body(form)
            val saved = if (form.id == null) {
                repo.createCustom(body, customKeys.keyFor(body)).also { r ->
                    if (r.result is ApiResult.Success || (r.result as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) customKeys.reset()
                }
            } else repo.updateCustom(form.id, body)
            formDone(saved.result, saved.fields) { it.copy(customForm = null) }
        }
    }

    fun toggleCustom(h: Staff090LibraryCustomHoliday) = write(null) { repo.toggleCustom(h.id) }

    fun askDeleteCustom(h: Staff090LibraryCustomHoliday) = _state.update { it.copy(confirmCustom = h, customForm = null) }
    fun deleteCustom() {
        val h = _state.value.confirmCustom ?: return
        _state.update { it.copy(confirmCustom = null) }
        write(R.string.staff090_library_deleted) { repo.deleteCustom(h.id) }
    }

    // ---------- national holidays ----------

    fun openHolidays() {
        val preset = _state.value.cal?.holidayPreset ?: return
        _state.update { it.copy(holidays = HolidayPickerState(preset = preset, selected = preset.holidays.filter { h -> h.checked }.map { h -> h.id }.toSet())) }
        previewHolidays(preset.country, preset.region, preset.year)
    }

    fun previewHolidays(country: String, region: String?, year: Int?) {
        _state.update { it.copy(holidays = it.holidays?.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val res = repo.countries(country, region, year)
            _state.update { s ->
                val h = s.holidays ?: return@update s
                when (res) {
                    is ApiResult.Success -> s.copy(holidays = h.copy(
                        countries = res.data.countries, preset = res.data.preset, loading = false,
                        selected = res.data.holidays.filter { it.checked }.map { it.id }.toSet(),
                    ))
                    is ApiResult.Failure -> s.copy(exit = res.exitMessage(), holidays = h.copy(loading = false, error = res.toLibraryMessage(R.string.staff_error_load)))
                }
            }
        }
    }

    fun toggleHoliday(id: String) = _state.update { s ->
        s.copy(holidays = s.holidays?.let { h -> h.copy(selected = if (id in h.selected) h.selected - id else h.selected + id) })
    }

    fun closeHolidays() = _state.update { it.copy(holidays = null) }

    fun applyHolidays() {
        val h = _state.value.holidays ?: return
        if (h.applying || h.loading) return
        _state.update { it.copy(holidays = h.copy(applying = true, error = null)) }
        viewModelScope.launch {
            // Only rules of the previewed list (the server keeps the selection per country).
            val rules = h.preset.holidays.map { it.id }.filter { it in h.selected }
            val saved = repo.applyPreset(h.preset.country, h.preset.region, rules)
            when (val res = saved.result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(holidays = null, message = res.data.message.takeIf { m -> m.isNotBlank() }?.let { m -> StaffMessage(text = m) } ?: StaffMessage(R.string.staff090_library_saved)) }
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(exit = res.exitMessage(), holidays = it.holidays?.copy(applying = false, error = res.toLibraryMessage(R.string.staff_error_save))) }
            }
        }
    }

    // ---------- ICS import ----------

    fun onIcsPicked(uri: Uri?) {
        uri ?: return
        val max = _state.value.cal?.limits?.icsMaxBytes ?: 1_048_576
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { readIcs(uri, max) }.getOrNull() }
            _state.update {
                when {
                    read == null -> it.copy(ics = null, icsError = StaffMessage(R.string.staff090_library_ics_read_failed))
                    read.second -> it.copy(ics = null, icsError = StaffMessage(R.string.staff090_library_err_ics_too_large))
                    else -> it.copy(ics = read.first, icsError = null)
                }
            }
        }
    }

    /** The picked file and whether it is over [max] bytes (then it is not read past the limit). */
    private fun readIcs(uri: Uri, max: Int): Pair<PickedFile?, Boolean>? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "calendar.ics"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i) && c.getLong(i) > max) return null to true }
            }
        }
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > max) return null to true
                out.write(buffer, 0, n)
            }
        } ?: return null
        return PickedFile(name, resolver.getType(uri), out.toByteArray()) to false
    }

    fun setIcsBranch(id: Int?) = _state.update { it.copy(icsBranch = id) }

    fun importIcs() {
        val s = _state.value
        val file = s.ics ?: return
        if (s.icsUploading) return
        _state.update { it.copy(icsUploading = true, icsError = null) }
        viewModelScope.launch {
            val saved = repo.importIcs(file.name, file.bytes, file.mime, s.icsBranch)
            when (val res = saved.result) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(icsUploading = false, ics = null, message = res.data.message.takeIf { m -> m.isNotBlank() }?.let { m -> StaffMessage(text = m) }
                            ?: StaffMessage(R.string.staff090_library_ics_added, args = listOf(res.data.added)))
                    }
                    load()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(icsUploading = false, exit = res.exitMessage(),
                        icsError = saved.fields["ics_file"]?.let { m -> StaffMessage(text = m) } ?: res.toLibraryMessage(R.string.staff090_library_ics_failed))
                }
            }
        }
    }

    // ---------- shared ----------

    private fun <T> formDone(res: ApiResult<T>, fields: Map<String, String>, close: (ClosuresCalendarUiState) -> ClosuresCalendarUiState) {
        when (res) {
            is ApiResult.Success -> {
                _state.update { close(it).copy(saving = false, message = StaffMessage(R.string.staff090_library_saved)) }
                load()
            }
            is ApiResult.Failure -> _state.update { it.copy(saving = false, exit = res.exitMessage(), fieldErrors = fields, formError = res.toLibraryMessage(R.string.staff_error_save)) }
        }
    }

    private fun write(done: Int? = R.string.staff090_library_saved, call: suspend () -> ApiResult<*>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val res = call()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, message = done?.let { r -> StaffMessage(r) }) }
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, exit = res.exitMessage(), message = res.toLibraryMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirmClosure = null, confirmCustom = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** The closures calendar of the 0.9.0 web admin, section by section. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClosuresCalendarScreen(onNavigateUp: () -> Unit) {
    val vm: ClosuresCalendarViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LeaveWhen(!areas.s090.closuresCalendar, onNavigateUp)
    LibraryExit(state.exit, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::onIcsPicked)
    val cal = state.cal

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_calendar), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = false,
                emptyTitle = "", emptyIcon = Icons.Outlined.CalendarMonth, onRetry = vm::refresh,
            ) {
                val c = cal ?: return@StaffLoadBody
                val branches = listOf(LookupOption("", stringResource(R.string.staff090_library_all_branches))) +
                    c.branches.map { LookupOption(it.id.toString(), it.name) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                    if (!c.moduleEnabled) {
                        item(key = "off") {
                            InfoCard(Modifier.padding(top = Spacing.sm)) {
                                Text(stringResource(R.string.staff090_library_calendar_module_off), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item(key = "month") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
                            if (c.branches.isNotEmpty()) {
                                ChoiceField(stringResource(R.string.staff090_library_branch_preview), state.branchId?.toString().orEmpty(), branches,
                                    { v -> vm.setBranch(v.toIntOrNull()) })
                            }
                            MonthGrid(c, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) }, onDay = vm::openDay)
                        }
                    }

                    item(key = "weekly") {
                        FormSectionTitle(stringResource(R.string.staff090_library_weekly))
                        Text(stringResource(R.string.staff090_library_weekly_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(vertical = Spacing.xs)) {
                            CalendarGrid.MONDAY_FIRST.forEach { d ->
                                FilterChip(
                                    selected = d in state.weekdays, onClick = { vm.toggleWeekday(d) },
                                    label = { Text(weekdayName(d, TextStyle.SHORT)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                    modifier = Modifier.testTag("weekday-$d"),
                                )
                            }
                        }
                        if (state.weekdaysChanged) {
                            PrimaryButton(stringResource(R.string.staff090_library_save_weekdays), onClick = vm::saveWeekdays, loading = state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("weekdays-save"))
                        }
                    }

                    item(key = "closures-title") {
                        FormSectionTitle(stringResource(R.string.staff090_library_closures))
                        SecondaryButton(stringResource(R.string.staff090_library_closure_new), onClick = { vm.startClosure() }, leadingIcon = Icons.Outlined.Add,
                            modifier = Modifier.fillMaxWidth().testTag("closure-new"))
                    }
                    if (c.closures.isEmpty()) {
                        item(key = "closures-empty") {
                            Text(stringResource(R.string.staff090_library_closures_empty), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.sm))
                        }
                    }
                    items(c.closures, key = { "closure-${it.id}" }) { closure -> ClosureRow(closure) { vm.editClosure(closure) } }

                    val yearly = CalendarGrid.yearly(c.closures)
                    if (yearly.isNotEmpty()) {
                        item(key = "yearly") {
                            FormSectionTitle(stringResource(R.string.staff090_library_yearly))
                            Text(stringResource(R.string.staff090_library_yearly_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            yearly.forEach { y ->
                                SwitchRow(y.description.ifBlank { stringResource(R.string.staff090_library_closure_untitled) }, state.yearly[y.id] ?: y.active,
                                    { on -> vm.setYearly(y.id, on) }, subtitle = dateRange(y))
                            }
                            if (state.yearlyChanged.isNotEmpty()) {
                                PrimaryButton(stringResource(R.string.action_save), onClick = vm::saveYearly, loading = state.busy, modifier = Modifier.fillMaxWidth().testTag("yearly-save"))
                            }
                        }
                    }

                    item(key = "holidays") {
                        FormSectionTitle(stringResource(R.string.staff090_library_holidays))
                        val preset = c.holidayPreset
                        StaffActionRow(
                            Icons.Outlined.Flag, stringResource(R.string.staff090_library_holidays_national),
                            if (preset.saved) stringResource(R.string.staff090_library_holidays_saved, preset.countryName.ifBlank { preset.country }, preset.holidays.count { it.checked })
                            else stringResource(R.string.staff090_library_holidays_none),
                        ) { vm.openHolidays() }
                    }

                    if (c.customHolidaysAvailable) {
                        item(key = "custom-title") {
                            FormSectionTitle(stringResource(R.string.staff090_library_custom))
                            Text(stringResource(R.string.staff090_library_custom_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SecondaryButton(stringResource(R.string.staff090_library_custom_new), onClick = vm::startCustom, leadingIcon = Icons.Outlined.Add,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag("custom-new"))
                        }
                        items(c.customHolidays, key = { "custom-${it.id}" }) { h ->
                            CustomRow(h, busy = state.busy, onOpen = { vm.editCustom(h) }, onToggle = { vm.toggleCustom(h) })
                        }
                    }

                    item(key = "ics") {
                        FormSectionTitle(stringResource(R.string.staff090_library_ics))
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(stringResource(R.string.staff090_library_ics_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.icsError?.let { InlineErrorBanner(message = it.resolve()) }
                            PickerRow(
                                label = stringResource(R.string.staff090_library_ics_file), value = state.ics?.name,
                                placeholder = stringResource(R.string.staff_import_choose_file), onClick = { picker.launch(ICS_TYPES) },
                            )
                            if (c.branches.isNotEmpty()) {
                                ChoiceField(stringResource(R.string.staff090_library_branch), state.icsBranch?.toString().orEmpty(), branches, { v -> vm.setIcsBranch(v.toIntOrNull()) })
                            }
                            PrimaryButton(
                                stringResource(R.string.staff090_library_ics_import), onClick = vm::importIcs, loading = state.icsUploading, enabled = state.ics != null,
                                leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("ics-import"),
                            )
                        }
                    }
                }
            }
        }
    }

    if (cal != null) {
        state.day?.let { day -> DayDialog(day, cal, onAddClosure = { vm.startClosure(day.date) }, onDismiss = vm::closeDay) }
        state.closureForm?.let { form -> ClosureSheet(form, cal, state, vm) }
        state.customForm?.let { form -> CustomHolidaySheet(form, cal, state, vm) }
        state.holidays?.let { h -> HolidaySheet(h, vm) }
    }
    state.confirmClosure?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.staff090_library_closure_delete), body = stringResource(R.string.staff090_library_closure_delete_body, dateRange(c)),
            confirmLabel = stringResource(R.string.staff090_library_delete), onConfirm = vm::deleteClosure, onDismiss = vm::dismissConfirm,
        )
    }
    state.confirmCustom?.let { h ->
        ConfirmDialog(
            title = stringResource(R.string.staff090_library_custom_delete), body = stringResource(R.string.staff090_library_custom_delete_body, h.name),
            confirmLabel = stringResource(R.string.staff090_library_delete), onConfirm = vm::deleteCustom, onDismiss = vm::dismissConfirm,
        )
    }
}

internal val ICS_TYPES = arrayOf("text/calendar", "text/x-vcalendar", "application/ics", "text/plain", "application/octet-stream")

/** A weekday name for the server's 0 = Sunday … 6 = Saturday. */
internal fun weekdayName(day: Int, style: TextStyle): String =
    DayOfWeek.of(if (day == 0) 7 else day).getDisplayName(style, AppLocale.current())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(AppLocale.current()) else it.toString() }

/** "1 Nov 2026" or "1 Nov 2026 – 3 Nov 2026". */
internal fun dateRange(c: Staff090LibraryClosure): String =
    if (c.endDate.isBlank() || c.endDate == c.startDate) DateFormat.date(c.startDate) else "${DateFormat.date(c.startDate)} – ${DateFormat.date(c.endDate)}"

private fun monthTitle(month: String): String = runCatching {
    val ym = YearMonth.parse(month)
    val locale = AppLocale.current()
    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() } + " " + ym.year
}.getOrDefault(month)

@Composable
internal fun MonthGrid(cal: Staff090LibraryCalendar, onPrev: () -> Unit, onNext: () -> Unit, onDay: (Staff090LibraryDay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_prev_month)) }
            Text(monthTitle(cal.month), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("calendar-month"))
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_month)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CalendarGrid.MONDAY_FIRST.forEach { d ->
                Text(weekdayName(d, TextStyle.NARROW), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        CalendarGrid.weeks(cal.days).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, today = day.date == cal.today, onClick = { onDay(day) })
                    }
                }
            }
        }
        // Key: a closed day is the neutral grey of the reader's loan calendar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.sizeIn(maxWidth = 12.dp, maxHeight = 12.dp).fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f), CircleShape))
            Text(stringResource(R.string.staff090_library_legend_closed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

@Composable
private fun DayCell(day: Staff090LibraryDay, today: Boolean, onClick: () -> Unit) {
    val container = if (day.closed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    val content = if (day.closed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val rule = when {
        today -> MaterialTheme.colorScheme.primary
        day.closed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    Box(
        Modifier.sizeIn(maxWidth = 44.dp, maxHeight = 44.dp).fillMaxSize().padding(1.dp)
            .testTag("calendar-day-${day.date}")
            .clip(CircleShape).background(container)
            .then(if (rule != Color.Transparent) Modifier.border(1.dp, rule, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.date.takeLast(2).trimStart('0'), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal, color = content)
    }
}

@Composable
private fun ClosureRow(c: Staff090LibraryClosure, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen, enabled = c.editable,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("closure-${c.id}"),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(c.description.ifBlank { stringResource(R.string.staff090_library_closure_untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(dateRange(c), c.branchName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.yearly || c.presetRule != null || !c.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (c.presetRule != null) AvailabilityChip(AvailabilityStatus.Digital, stringResource(R.string.staff090_library_closure_preset))
                    if (c.yearly) AvailabilityChip(AvailabilityStatus.ReservedReady, stringResource(R.string.staff090_library_closure_yearly))
                    if (!c.active) AvailabilityChip(AvailabilityStatus.Unavailable, stringResource(R.string.staff090_library_inactive))
                }
            }
        }
    }
}

@Composable
private fun CustomRow(h: Staff090LibraryCustomHoliday, busy: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("custom-${h.id}"),
    ) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(h.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(h.description.takeIf { it.isNotBlank() }, h.branchName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                h.nextDates.takeIf { it.isNotEmpty() }?.let { dates ->
                    Text(stringResource(R.string.staff090_library_custom_next, dates.take(3).joinToString(", ") { DateFormat.date(it) }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = h.active, onCheckedChange = { onToggle() }, enabled = !busy, modifier = Modifier.testTag("custom-toggle-${h.id}"))
        }
    }
}

@Composable
private fun DayDialog(day: Staff090LibraryDay, cal: Staff090LibraryCalendar, onAddClosure: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(DateFormat.date(day.date), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("day-reasons")) {
                if (!day.closed) {
                    Text(stringResource(R.string.staff090_library_day_open), style = MaterialTheme.typography.bodyMedium)
                } else {
                    day.reasons.forEach { r ->
                        // The weekly closure's server name is generic: the weekday says more.
                        val what = if (r.type == "weekly") weekdayName(day.weekday, TextStyle.FULL) else r.name
                        Text(stringResource(reasonLabel(r.type), what), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (day.reasons.isEmpty()) Text(stringResource(R.string.staff090_library_day_closed), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { PinakesTextButton(stringResource(R.string.staff090_library_close), onClick = onDismiss) },
        dismissButton = if (cal.canConfigure) ({ PinakesTextButton(stringResource(R.string.staff090_library_closure_new), onClick = onAddClosure) }) else null,
    )
}

private fun reasonLabel(type: String) = when (type) {
    "weekly" -> R.string.staff090_library_reason_weekly
    "closure" -> R.string.staff090_library_reason_closure
    "holiday" -> R.string.staff090_library_reason_holiday
    "custom_holiday" -> R.string.staff090_library_reason_custom
    else -> R.string.staff090_library_reason_other
}
