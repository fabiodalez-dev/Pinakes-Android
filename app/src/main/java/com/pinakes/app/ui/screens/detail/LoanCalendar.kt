package com.pinakes.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.ui.theme.AvailableContainerDark
import com.pinakes.app.ui.theme.AvailableContainerLight
import com.pinakes.app.ui.theme.AvailableOnContainerDark
import com.pinakes.app.ui.theme.AvailableOnContainerLight
import com.pinakes.app.ui.theme.DueSoonContainerDark
import com.pinakes.app.ui.theme.DueSoonContainerLight
import com.pinakes.app.ui.theme.DueSoonOnContainerDark
import com.pinakes.app.ui.theme.DueSoonOnContainerLight
import com.pinakes.app.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------
// Day-cell tints. These reuse the ONLY allowed semantic colours: the availability
// tints from theme/Color.kt (green = free, amber = partial) and the error role
// (red = full). No invented hexes. (DESIGN.md.)
// ---------------------------------------------------------------------------

private data class DayTint(val container: Color, val onContainer: Color)

/** Per-day availability state used to colour a calendar cell. */
private enum class DayKind { Free, Partial, Full, PastOrUnknown }

@Composable
private fun tintFor(kind: DayKind, dark: Boolean): DayTint = when (kind) {
    DayKind.Free ->
        if (dark) DayTint(AvailableContainerDark, AvailableOnContainerDark)
        else DayTint(AvailableContainerLight, AvailableOnContainerLight)
    DayKind.Partial ->
        if (dark) DayTint(DueSoonContainerDark, DueSoonOnContainerDark)
        else DayTint(DueSoonContainerLight, DueSoonOnContainerLight)
    DayKind.Full ->
        DayTint(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    DayKind.PastOrUnknown ->
        DayTint(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
}

/** Index per-day data by ISO date for O(1) lookup, and the set of unavailable dates. */
private class CalendarModel(calendar: AvailabilityCalendar) {
    private val byDate: Map<String, String> =
        calendar.days.associate { it.date to it.state } // date -> state
    private val available: Map<String, Int> =
        calendar.days.associate { it.date to it.available }
    private val unavailable: Set<String> = calendar.unavailableDates.toSet()
    private val knownDates: Set<String> = byDate.keys

    /** True only when we have fetched data covering this day at all. */
    fun hasData(iso: String): Boolean = knownDates.contains(iso) || unavailable.contains(iso)

    fun kind(date: LocalDate, today: LocalDate): DayKind {
        val iso = date.toString()
        if (date.isBefore(today)) return DayKind.PastOrUnknown
        if (unavailable.contains(iso)) return DayKind.Full
        return when (byDate[iso]) {
            "free" -> if ((available[iso] ?: 1) > 0) DayKind.Free else DayKind.Full
            "partial" -> DayKind.Partial
            "full" -> DayKind.Full
            else -> DayKind.PastOrUnknown // beyond the fetched window → unknown/disabled
        }
    }

    fun isSelectable(date: LocalDate, today: LocalDate): Boolean {
        if (date.isBefore(today)) return false
        return when (kind(date, today)) {
            DayKind.Free, DayKind.Partial -> true
            else -> false
        }
    }
}

/**
 * Month grid (Mon–Sun rows) that colours each day by availability and disables fully-booked /
 * past / out-of-window days. [selected] is the currently chosen ISO date (yyyy-MM-dd) or null.
 * Tapping a selectable day calls [onSelect] with its ISO date.
 */
@Composable
fun LoanCalendar(
    calendar: AvailabilityCalendar,
    selected: String?,
    onSelect: (isoDate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val today = remember { LocalDate.now() }
    val model = remember(calendar) { CalendarModel(calendar) }

    // Visible month: start on the selected date's month, else the earliest-available month, else now.
    val initialMonth = remember(calendar, selected) {
        val anchor = selected ?: calendar.earliestAvailable
        runCatching { YearMonth.from(LocalDate.parse(anchor)) }.getOrNull()
            ?: YearMonth.from(today)
    }
    var visibleMonth by remember(initialMonth) { mutableStateOf(initialMonth) }

    Column(modifier.fillMaxWidth()) {
        MonthHeader(
            month = visibleMonth,
            onPrev = { visibleMonth = visibleMonth.minusMonths(1) },
            onNext = { visibleMonth = visibleMonth.plusMonths(1) },
        )
        Spacer(Modifier.height(Spacing.sm))
        WeekdayHeader()
        Spacer(Modifier.height(Spacing.xs))
        MonthGrid(
            month = visibleMonth,
            today = today,
            model = model,
            selected = selected,
            dark = dark,
            onSelect = onSelect,
        )
        Spacer(Modifier.height(Spacing.md))
        Legend(dark = dark)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_prev_month),
            )
        }
        Text(
            text = monthTitle(month),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = Locale.getDefault()
    // Mon-first week order.
    val days = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
    Row(Modifier.fillMaxWidth()) {
        days.forEach { dow ->
            Text(
                text = dow.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    model: CalendarModel,
    selected: String?,
    dark: Boolean,
    onSelect: (String) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    // Mon=1 .. Sun=7 → leading blanks before day 1.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        for (row in 0 until rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                today = today,
                                model = model,
                                selected = selected == date.toString(),
                                dark = dark,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    today: LocalDate,
    model: CalendarModel,
    selected: Boolean,
    dark: Boolean,
    onSelect: (String) -> Unit,
) {
    val iso = date.toString()
    val kind = model.kind(date, today)
    val selectable = model.isSelectable(date, today)
    val tint = tintFor(kind, dark)

    val container = if (selected) MaterialTheme.colorScheme.primary else tint.container
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimary else tint.onContainer

    val cellModifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(container)
        .then(
            if (date == today && !selected)
                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            else Modifier
        )
        .then(
            if (selectable) Modifier.clickable { onSelect(iso) } else Modifier
        )

    Box(cellModifier, contentAlignment = Alignment.Center) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = onContainer,
        )
    }
}

@Composable
private fun Legend(dark: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        LegendItem(tintFor(DayKind.Free, dark).container, stringResource(R.string.book_loan_legend_available))
        LegendItem(tintFor(DayKind.Partial, dark).container, stringResource(R.string.book_loan_legend_limited))
        LegendItem(tintFor(DayKind.Full, dark).container, stringResource(R.string.book_loan_legend_unavailable))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "June 2026" style month + year title, localized. */
private fun monthTitle(month: YearMonth): String {
    val locale = Locale.getDefault()
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$name ${month.year}"
}
