package com.pinakes.app.ui.screens.staff.s090.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryHoliday
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.SearchState
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle

// The calendar's bottom sheets: a closure, one of the library's own holidays, the national preset.

@Composable
private fun branchOptions(cal: Staff090LibraryCalendar): List<LookupOption> =
    listOf(LookupOption("", stringResource(R.string.staff090_library_all_branches))) + cal.branches.map { LookupOption(it.id.toString(), it.name) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClosureSheet(form: ClosureForm, cal: Staff090LibraryCalendar, state: ClosuresCalendarUiState, vm: ClosuresCalendarViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val errors = state.fieldErrors
    ModalBottomSheet(onDismissRequest = { if (!state.saving) vm.closeClosure() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(if (form.id == null) R.string.staff090_library_closure_new else R.string.staff090_library_closure_edit), style = MaterialTheme.typography.headlineSmall)
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            DatePickerField(stringResource(R.string.staff090_library_closure_start), form.startDate, { v -> vm.changeClosure { it.copy(startDate = v) } })
            DatePickerField(stringResource(R.string.staff090_library_closure_end), form.endDate, { v -> vm.changeClosure { it.copy(endDate = v) } }, allowClear = true)
            val dateError = errors["start_date"] ?: errors["end_date"] ?: if (form.startDate != null && !form.datesValid) stringResource(R.string.staff090_library_closure_end_before) else null
            dateError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.staff090_library_closure_end_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PinakesTextField(
                value = form.description, onValueChange = { v -> vm.changeClosure { it.copy(description = v) } },
                label = stringResource(R.string.staff_field_description), maxLength = 255,
                isError = "description" in errors, errorText = errors["description"].orEmpty(), modifier = Modifier.fillMaxWidth().testTag("closure-description"),
            )
            SwitchRow(stringResource(R.string.staff090_library_closure_yearly_switch), form.yearly, { v -> vm.changeClosure { it.copy(yearly = v) } },
                subtitle = stringResource(R.string.staff090_library_closure_yearly_hint, cal.limits.yearlySpanDays))
            if (cal.branches.isNotEmpty()) {
                ChoiceField(stringResource(R.string.staff090_library_branch), form.branchId?.toString().orEmpty(), branchOptions(cal), { v -> vm.changeClosure { it.copy(branchId = v.toIntOrNull()) } })
                errors["branch_id"]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::saveClosure, loading = state.saving, enabled = form.valid,
                modifier = Modifier.fillMaxWidth().testTag("closure-save"))
            if (form.id != null) {
                val closure = cal.closures.firstOrNull { it.id == form.id }
                if (closure != null) SecondaryButton(stringResource(R.string.staff090_library_delete), onClick = { vm.askDeleteClosure(closure) }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomHolidaySheet(form: CustomForm, cal: Staff090LibraryCalendar, state: ClosuresCalendarUiState, vm: ClosuresCalendarViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = cal.customHolidayOptions
    val calendar = options.calendars.firstOrNull { it.code == form.calendar }
    val invalid = CustomForms.invalid(form, options.maxDurationDays, options.maxDates, calendar?.months?.size ?: 12)
    val errors = state.fieldErrors
    val fields = CustomForms.fields(form.kind)
    fun bad(key: String) = key in invalid || key in errors
    ModalBottomSheet(onDismissRequest = { if (!state.saving) vm.closeCustom() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(if (form.id == null) R.string.staff090_library_custom_new else R.string.staff090_library_custom_edit), style = MaterialTheme.typography.headlineSmall)
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            PinakesTextField(
                value = form.name, onValueChange = { v -> vm.changeCustom { it.copy(name = v) } }, label = stringResource(R.string.staff_field_name),
                maxLength = 255, isError = "name" in errors, errorText = errors["name"].orEmpty(), placeholder = stringResource(R.string.staff090_library_custom_name_hint),
                modifier = Modifier.fillMaxWidth().testTag("custom-name"),
            )
            ChoiceField(
                stringResource(R.string.staff090_library_custom_kind), form.kind,
                options.kinds.map { LookupOption(it, kindLabel(it)?.let { r -> stringResource(r) } ?: it) },
                { v -> vm.changeCustom { it.copy(kind = v) } }, modifier = Modifier.fillMaxWidth().testTag("custom-kind"),
            )
            if (CustomField.CALENDAR in fields) {
                val calendars = options.calendars.filter { it.available || it.code == form.calendar }
                ChoiceField(stringResource(R.string.staff090_library_custom_calendar), form.calendar, calendars.map { LookupOption(it.code, it.label) },
                    { v -> vm.changeCustom { it.copy(calendar = v, month = "1") } })
                Text(stringResource(R.string.staff090_library_custom_calendar_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (CustomField.OCCURRENCE in fields) {
                ChoiceField(stringResource(R.string.staff090_library_custom_occurrence), form.occurrence.toString(),
                    options.occurrences.ifEmpty { listOf(1, 2, 3, 4, 5, -1) }.map { LookupOption(it.toString(), stringResource(occurrenceLabel(it))) },
                    { v -> vm.changeCustom { it.copy(occurrence = v.toIntOrNull() ?: 1) } })
            }
            if (CustomField.WEEKDAY in fields) {
                ChoiceField(stringResource(R.string.staff090_library_custom_weekday), form.weekday.toString(),
                    CalendarGrid.MONDAY_FIRST.map { LookupOption(it.toString(), weekdayName(it, TextStyle.FULL)) },
                    { v -> vm.changeCustom { it.copy(weekday = v.toIntOrNull() ?: 1) } })
            }
            if (CustomField.MONTH in fields) {
                val months = if (form.kind == "calendar") calendar?.months.orEmpty().mapIndexed { i, name -> LookupOption((i + 1).toString(), name) }
                else (1..12).map { m -> LookupOption(m.toString(), Month.of(m).getDisplayName(TextStyle.FULL_STANDALONE, AppLocale.current()).replaceFirstChar { it.titlecase(AppLocale.current()) }) }
                ChoiceField(stringResource(R.string.staff090_library_custom_month), form.month, months, { v -> vm.changeCustom { it.copy(month = v) } })
                if (bad("month")) Text(errors["month"] ?: stringResource(R.string.staff_field_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (CustomField.DAY in fields) {
                NumberField(stringResource(R.string.staff090_library_custom_day), form.day, { v -> vm.changeCustom { it.copy(day = v) } }, invalid = bad("day"),
                    hint = errors["day"] ?: stringResource(R.string.staff090_library_range, 1, CustomForms.maxDay(form.kind)), modifier = Modifier.fillMaxWidth().testTag("custom-day"))
            }
            if (CustomField.OFFSET in fields) {
                NumberField(stringResource(R.string.staff090_library_custom_offset), form.offset, { v -> vm.changeCustom { it.copy(offset = v) } }, invalid = bad("offset_days"),
                    hint = errors["offset_days"] ?: stringResource(R.string.staff090_library_custom_offset_hint), signed = true)
            }
            if (CustomField.DATES in fields) {
                PinakesTextField(
                    value = form.dates, onValueChange = { v -> vm.changeCustom { it.copy(dates = v) } }, label = stringResource(R.string.staff090_library_custom_dates),
                    singleLine = false, isError = bad("dates"), errorText = errors["dates"] ?: stringResource(R.string.staff090_library_custom_dates_hint, options.maxDates),
                    supportingText = stringResource(R.string.staff090_library_custom_dates_hint, options.maxDates),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).testTag("custom-dates"),
                )
            }
            FormSectionTitle(stringResource(R.string.staff090_library_custom_more))
            NumberField(stringResource(R.string.staff090_library_custom_duration), form.duration, { v -> vm.changeCustom { it.copy(duration = v) } }, invalid = bad("duration_days"),
                hint = errors["duration_days"] ?: stringResource(R.string.staff090_library_range, 1, options.maxDurationDays))
            ChoiceField(
                stringResource(R.string.staff090_library_custom_weekend), form.weekendRule.orEmpty(),
                listOf(LookupOption("", stringResource(R.string.staff090_library_weekend_none))) +
                    options.weekendRules.map { LookupOption(it, weekendLabel(it)?.let { r -> stringResource(r) } ?: it) },
                { v -> vm.changeCustom { it.copy(weekendRule = v.ifBlank { null }) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(stringResource(R.string.staff090_library_custom_year_from), form.yearFrom, { v -> vm.changeCustom { it.copy(yearFrom = v) } }, invalid = bad("year_from"),
                    hint = errors["year_from"].orEmpty(), modifier = Modifier.weight(1f))
                NumberField(stringResource(R.string.staff090_library_custom_year_until), form.yearUntil, { v -> vm.changeCustom { it.copy(yearUntil = v) } }, invalid = bad("year_until"),
                    hint = errors["year_until"].orEmpty(), modifier = Modifier.weight(1f))
            }
            if (cal.branches.isNotEmpty()) {
                ChoiceField(stringResource(R.string.staff090_library_branch), form.branchId?.toString().orEmpty(), branchOptions(cal), { v -> vm.changeCustom { it.copy(branchId = v.toIntOrNull()) } })
            }
            SwitchRow(stringResource(R.string.staff090_library_active), form.active, { v -> vm.changeCustom { it.copy(active = v) } })
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::saveCustom, loading = state.saving, enabled = invalid.isEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("custom-save"))
            if (form.id != null) {
                val holiday = cal.customHolidays.firstOrNull { it.id == form.id }
                if (holiday != null) SecondaryButton(stringResource(R.string.staff090_library_delete), onClick = { vm.askDeleteCustom(holiday) }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HolidaySheet(h: HolidayPickerState, vm: ClosuresCalendarViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val preset = h.preset
    var picking by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { if (!h.applying) vm.closeHolidays() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff090_library_holidays_national), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff090_library_holidays_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.error?.let { InlineErrorBanner(message = it.resolve()) }
            PickerRow(
                label = stringResource(R.string.staff090_library_country), value = preset.countryName.ifBlank { preset.country },
                placeholder = stringResource(R.string.staff090_library_country), onClick = { if (h.countries.isNotEmpty()) picking = true },
            )
            if (preset.regions.isNotEmpty()) {
                ChoiceField(
                    stringResource(R.string.staff090_library_region), preset.region,
                    listOf(LookupOption("", stringResource(R.string.staff090_library_region_all))) + preset.regions.map { LookupOption(it.code, it.name) },
                    { v -> vm.previewHolidays(preset.country, v, preset.year) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val years = preset.years
                IconButton(onClick = { vm.previewHolidays(preset.country, preset.region, preset.year - 1) }, enabled = years == null || preset.year > years.min) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.staff090_library_year_prev))
                }
                Text(stringResource(R.string.staff090_library_preview_year, preset.year), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.previewHolidays(preset.country, preset.region, preset.year + 1) }, enabled = years == null || preset.year < years.max) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.staff090_library_year_next))
                }
            }
            preset.dataYears?.takeIf { it.size == 2 }?.let { (from, to) ->
                Text(stringResource(R.string.staff090_library_data_years, from, to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (h.loading) {
                LoadingState(modifier = Modifier.heightIn(max = 120.dp))
            } else {
                preset.holidays.forEach { holiday ->
                    SwitchRow(holiday.name, holiday.id in h.selected, { vm.toggleHoliday(holiday.id) }, subtitle = holidaySubtitle(holiday))
                }
            }
            PrimaryButton(
                stringResource(R.string.staff090_library_holidays_apply, h.selected.count { id -> preset.holidays.any { it.id == id } }),
                onClick = vm::applyHolidays, loading = h.applying, enabled = !h.loading && preset.schemaReady,
                modifier = Modifier.fillMaxWidth().testTag("holidays-apply"),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
    if (picking) {
        val matches = h.countries.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.code.equals(query.trim(), ignoreCase = true) }
        SearchPickerSheet(
            title = stringResource(R.string.staff090_library_country), placeholder = stringResource(R.string.staff090_library_country_search),
            state = SearchState(query = query, results = matches.mapIndexed { i, c -> PickItem(i, c.name, c.code) }, searched = true),
            onQuery = { query = it },
            onPick = { item -> matches.getOrNull(item.id)?.let { vm.previewHolidays(it.code, null, preset.year) }; picking = false; query = "" },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun holidaySubtitle(h: Staff090LibraryHoliday): String {
    val parts = mutableListOf<String>()
    (h.dates.ifEmpty { listOfNotNull(h.date) }).takeIf { it.isNotEmpty() }?.let { d -> parts += d.joinToString(", ") { DateFormat.date(it) } }
    if (h.yearly) parts += stringResource(R.string.staff090_library_holiday_yearly)
    if (h.regional) parts += stringResource(R.string.staff090_library_holiday_regional)
    if (h.optional) parts += stringResource(R.string.staff090_library_holiday_optional)
    if (h.estimated) parts += stringResource(R.string.staff090_library_holiday_estimated)
    return parts.joinToString(" · ")
}

private fun kindLabel(kind: String) = when (kind) {
    "fixed" -> R.string.staff090_library_kind_fixed
    "nth_weekday" -> R.string.staff090_library_kind_nth_weekday
    "easter" -> R.string.staff090_library_kind_easter
    "orthodox_easter" -> R.string.staff090_library_kind_orthodox_easter
    "calendar" -> R.string.staff090_library_kind_calendar
    "dates" -> R.string.staff090_library_kind_dates
    else -> null
}

private fun occurrenceLabel(n: Int) = when (n) {
    1 -> R.string.staff090_library_nth_1
    2 -> R.string.staff090_library_nth_2
    3 -> R.string.staff090_library_nth_3
    4 -> R.string.staff090_library_nth_4
    5 -> R.string.staff090_library_nth_5
    else -> R.string.staff090_library_nth_last
}

private fun weekendLabel(rule: String) = when (rule) {
    "next_monday" -> R.string.staff090_library_weekend_next_monday
    "us" -> R.string.staff090_library_weekend_us
    else -> null
}
