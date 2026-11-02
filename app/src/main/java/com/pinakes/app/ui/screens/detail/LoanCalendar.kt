package com.pinakes.app.ui.screens.detail

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.pinakes.app.ui.theme.DueSoonContainerDark
import com.pinakes.app.ui.theme.DueSoonContainerLight
import com.pinakes.app.ui.theme.DueSoonOnContainerDark
import com.pinakes.app.ui.theme.DueSoonOnContainerLight
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Spacing
import com.pinakes.app.ui.theme.mix
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ---------------------------------------------------------------------------
// Day-cell tints, as on the web loan calendar: a free day is a plain dot in the
// ink colour (a coloured month reads as noise), only the days that need a word
// carry a tint — amber = partial (theme/Color.kt), red = full (the error role) —
// each ringed by a faint rule of its own text colour. No invented hexes.
// (DESIGN.md.)
// ---------------------------------------------------------------------------

private data class DayTint(val container: Color, val onContainer: Color, val rule: Color = Color.Transparent)

/** Per-day availability state used to colour a calendar cell. */
internal enum class DayKind { Free, Partial, Full, Closed, PastOrUnknown }

@Composable
private fun tintFor(kind: DayKind, dark: Boolean): DayTint = when (kind) {
    DayKind.Free ->
        DayTint(Color.Transparent, MaterialTheme.colorScheme.onSurface)
    DayKind.Partial ->
        if (dark) ruled(DueSoonContainerDark, DueSoonOnContainerDark)
        else ruled(DueSoonContainerLight, DueSoonOnContainerLight)
    DayKind.Full ->
        ruled(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    // The library is closed: a neutral grey, never the red of a booked day (nothing is on loan).
    DayKind.Closed ->
        ruled(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    DayKind.PastOrUnknown ->
        DayTint(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
}

/** A tinted day: its rule is its own text colour, faded into the tint. */
private fun ruled(container: Color, onContainer: Color) =
    DayTint(container, onContainer, mix(onContainer, container, .22f))

/** Index per-day data by ISO date for O(1) lookup, and the set of unavailable dates. */
internal class CalendarModel(private val source: AvailabilityCalendar) {
    private val byDate: Map<String, String> =
        source.days.associate { it.date to it.state } // date -> state
    private val unavailable: Set<String> = source.unavailableDates.toSet()
    private val knownDates: Set<String> = byDate.keys

    /** True only when we have fetched data covering this day at all. */
    fun hasData(iso: String): Boolean = knownDates.contains(iso) || unavailable.contains(iso)

    fun kind(date: LocalDate, today: LocalDate): DayKind {
        val iso = date.toString()
        if (date.isBefore(today)) return DayKind.PastOrUnknown
        // Closures calendar: no start or pickup on a day the library is closed.
        if (source.isClosed(iso)) return DayKind.Closed
        // Same predicate as loanWindowFrom(): a red day is a day that caps the loan.
        if (source.isFullyBooked(iso)) return DayKind.Full
        return when (byDate[iso]) {
            "free" -> DayKind.Free
            "partial" -> DayKind.Partial
            else -> DayKind.PastOrUnknown // beyond the fetched window → unknown/disabled
        }
    }

    fun isSelectable(date: LocalDate, today: LocalDate, anyFutureDay: Boolean = false): Boolean {
        if (date.isBefore(today)) return false
        // Availability unknown (fetch failed): every future day can be requested; the server decides.
        if (anyFutureDay) return true
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
    /** Availability could not be loaded: every future day is selectable, no colour legend. */
    anyFutureDay: Boolean = false,
    /** The day the grid treats as today; null reads the device clock once. Tests pin it. */
    today: LocalDate? = null,
    /**
     * A fully-booked or closed future day cannot be picked; tapping it calls this so the sheet
     * can say why (it tells the two apart with [AvailabilityCalendar.isClosed]).
     */
    onBlockedTap: ((isoDate: String) -> Unit)? = null,
) {
    val dark = LocalPinakesColors.current.darkMode
    val deviceToday = remember { LocalDate.now() }
    val today = today ?: deviceToday
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
            anyFutureDay = anyFutureDay,
            onBlockedTap = onBlockedTap,
        )
        if (!anyFutureDay) {
            Spacer(Modifier.height(Spacing.md))
            Legend(dark = dark, showClosed = calendar.closedDates.isNotEmpty())
        }
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
    val locale = com.pinakes.app.ui.common.AppLocale.current()
    // Mon-first week order.
    val days = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
    // Same 7 weighted columns and gaps as the grid below, so headers sit over their days.
    Row(Modifier.fillMaxWidth().testTag("calendar-weekdays"), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
    anyFutureDay: Boolean = false,
    onBlockedTap: ((String) -> Unit)? = null,
) {
    val firstOfMonth = month.atDay(1)
    // Mon=1 .. Sun=7 → leading blanks before day 1.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(Modifier.testTag("calendar-grid"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                                anyFutureDay = anyFutureDay,
                                onBlockedTap = onBlockedTap,
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
    anyFutureDay: Boolean = false,
    onBlockedTap: ((String) -> Unit)? = null,
) {
    val iso = date.toString()
    val kind = model.kind(date, today)
    val selectable = model.isSelectable(date, today, anyFutureDay)
    // Unknown availability: future days look plain but selectable (no false "unavailable" tint).
    val tint = if (anyFutureDay && selectable) DayTint(Color.Transparent, MaterialTheme.colorScheme.onSurface) else tintFor(kind, dark)

    // The pick is the theme's button colour, as the "Request loan" button that opened the sheet.
    val tokens = LocalPinakesColors.current
    val container = if (selected) tokens.button else tint.container
    val onContainer = if (selected) tokens.buttonText else tint.onContainer
    val rule = when {
        selected -> Color.Transparent
        date == today -> MaterialTheme.colorScheme.primary
        else -> tint.rule
    }

    // Fills its weighted square cell (at most 44dp) instead of a fixed 40dp: on a 320-360dp
    // phone a dialog leaves ~38dp per column, and a fixed size overflowed into its neighbours.
    val cellModifier = Modifier
        .sizeIn(maxWidth = 44.dp, maxHeight = 44.dp)
        .fillMaxSize()
        .padding(1.dp)
        .testTag("calendar-day-$iso")
        .clip(CircleShape)
        .background(container)
        .then(
            if (rule != Color.Transparent) Modifier.border(1.dp, rule, CircleShape) else Modifier
        )
        .then(
            when {
                selectable -> Modifier.clickable { onSelect(iso) }
                // Not pickable, but a tap must still explain why (every copy is booked, or the
                // library is closed that day).
                onBlockedTap != null && (kind == DayKind.Full || kind == DayKind.Closed) -> Modifier.clickable { onBlockedTap(iso) }
                else -> Modifier
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(dark: Boolean, showClosed: Boolean = false) {
    // Wraps on narrow screens and with long translations instead of running off the edge.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // A free day is an untinted dot, so its key is one too: the surface ringed by the rule.
        LegendItem(MaterialTheme.colorScheme.surface, stringResource(R.string.book_loan_legend_available), MaterialTheme.colorScheme.outline)
        tintFor(DayKind.Partial, dark).let { LegendItem(it.container, stringResource(R.string.book_loan_legend_limited), it.rule) }
        tintFor(DayKind.Full, dark).let { LegendItem(it.container, stringResource(R.string.book_loan_legend_unavailable), it.rule) }
        // Only when the library publishes closures: an empty key would explain nothing.
        if (showClosed) {
            tintFor(DayKind.Closed, dark).let { LegendItem(it.container, stringResource(R.string.book_loan_legend_closed), it.rule) }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, rule: Color = Color.Transparent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Dots, like the days they explain.
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, rule, CircleShape),
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
    val locale = com.pinakes.app.ui.common.AppLocale.current()
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    return "$name ${month.year}"
}
