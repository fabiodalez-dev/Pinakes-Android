package com.pinakes.app.ui.screens.detail

import com.pinakes.app.data.model.AvailabilityCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The loan the reader can still ask for from a start day, read off the availability calendar.
 *
 * The server adds the library's `loan_duration_days` to the chosen start to set the return
 * date. When a scheduled loan or a reservation on every copy (a `full` day) begins before that
 * default return date, the server refuses the whole window, so the request must stop the day
 * before it instead: that is [endDate], sent as `end_date` of POST /reservations (Mobile API
 * 1.8.2), and [capped] says the default was shortened, which the sheet explains under the
 * calendar with [maxDays] and [firstBlocked]. Same rule as the website's calendar.
 */
data class LoanWindow(
    /** The return date to request: start + duration, or the day before [firstBlocked]. */
    val endDate: String,
    /** The first fully-booked day inside the default duration, or null. */
    val firstBlocked: String?,
    /** Days from the start to [endDate]. */
    val maxDays: Int,
    /** True when [firstBlocked] shortened the default duration. */
    val capped: Boolean,
    /** Send the displayed date when either a commitment or the configured maximum shortened it. */
    val requiresEndDate: Boolean = capped,
) {
    /** False when the very next day is blocked: no loan of at least one day exists from this start. */
    val possible: Boolean get() = maxDays >= 1
}

/**
 * A day the grid paints red and refuses to pick: listed in `unavailable_dates`, marked `full`,
 * or labelled `free` with no free copy (a stale label the server can send). One predicate for
 * the calendar and the window, so what the reader sees as blocked is what caps the loan.
 */
internal fun AvailabilityCalendar.isFullyBooked(isoDate: String): Boolean {
    if (unavailableDates.contains(isoDate)) return true
    val day = days.firstOrNull { it.date == isoDate } ?: return false
    return day.state == "full" || (day.state == "free" && day.available <= 0)
}

/** A day the library is closed (closures calendar, Mobile API 1.9.0): never a start or a return. */
internal fun AvailabilityCalendar.isClosed(isoDate: String): Boolean = closedDates.contains(isoDate)

/** How far [openDayFrom] looks for an open day: closures longer than this are not real. */
private const val MAX_CLOSED_RUN = 60

/**
 * The nearest open day from [date] (itself when open), walking [forward] or back, or null when
 * none exists within [MAX_CLOSED_RUN] days or before [notBefore] (going back).
 */
internal fun AvailabilityCalendar.openDayFrom(date: LocalDate, forward: Boolean, notBefore: LocalDate? = null): LocalDate? {
    var d = date
    repeat(MAX_CLOSED_RUN) {
        if (notBefore != null && d.isBefore(notBefore)) return null
        if (!isClosed(d.toString())) return d
        d = if (forward) d.plusDays(1) else d.minusDays(1)
    }
    return null
}

/**
 * The day the request sheet pre-selects: [earliestIso] (the server's `earliest_available`), or
 * the first day after it that is neither closed nor fully booked. Null when nothing qualifies
 * in the fetched window or [earliestIso] is not a date.
 */
internal fun AvailabilityCalendar.firstRequestableDay(earliestIso: String?): String? {
    val start = runCatching { LocalDate.parse(earliestIso) }.getOrNull() ?: return null
    if (closedDates.isEmpty()) return earliestIso
    var d = start
    repeat(MAX_CLOSED_RUN) {
        val iso = d.toString()
        if (!isClosed(iso) && !isFullyBooked(iso)) return iso
        d = d.plusDays(1)
    }
    return null
}

/**
 * The window from [startIso] (yyyy-MM-dd), or null when the duration is unknown (an older
 * server omits `loan_duration_days`) or the start is not a date: then the server's default
 * applies and nothing can be said about it.
 *
 * Only a server that reads `end_date` (Mobile API 1.8.2, Pinakes 0.8.7) can grant a shortened
 * window, and it is the one that sends `max_loan_duration_days`. Pinakes 0.8.6 (Mobile API
 * 1.8.1) sends `loan_duration_days` but ignores `end_date`: there the window stays the default
 * one, uncapped and without `end_date`, as in 1.9.0, instead of promising a shorter loan the
 * server would refuse or end on another day.
 */
internal fun loanWindowFrom(startIso: String, calendar: AvailabilityCalendar?, loanDurationDays: Int?): LoanWindow? {
    if (loanDurationDays == null || loanDurationDays < 1) return null
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull() ?: return null
    if (calendar?.maxLoanDurationDays == null) {
        // closed_dates comes with Mobile API 1.9.0, which always sends max_loan_duration_days:
        // a server without the maximum has no closures to apply here.
        return LoanWindow(
            endDate = start.plusDays(loanDurationDays.toLong()).toString(),
            firstBlocked = null,
            maxDays = loanDurationDays,
            capped = false,
            requiresEndDate = false,
        )
    }
    // Non-null from here: maxLoanDurationDays was read off it.
    val cal = calendar ?: return null
    val durationDays = cal.maxLoanDurationDays?.takeIf { it > 0 }
        ?.let { minOf(loanDurationDays, it) } ?: loanDurationDays
    var firstBlocked: String? = null
    for (offset in 1..durationDays) {
        val day = start.plusDays(offset.toLong()).toString()
        if (cal.isFullyBooked(day)) {
            firstBlocked = day
            break
        }
    }
    val defaultEnd = start.plusDays(durationDays.toLong())
    var rawEnd = firstBlocked?.let { LocalDate.parse(it).minusDays(1) } ?: defaultEnd
    // A default return on a closed day moves to the next open day, as the server's due date
    // does. When that walk meets a fully-booked day, the loan would run into the commitment:
    // the booked day caps it, as if it lay inside the duration (the server's renewal cap does
    // the same with closures).
    if (firstBlocked == null && cal.isClosed(rawEnd.toString())) {
        var d = rawEnd
        while (cal.isClosed(d.toString()) && ChronoUnit.DAYS.between(rawEnd, d) < 60) {
            if (cal.isFullyBooked(d.toString())) break
            d = d.plusDays(1)
        }
        if (cal.isFullyBooked(d.toString())) {
            firstBlocked = d.toString()
            rawEnd = d.minusDays(1)
        } else {
            rawEnd = d
        }
    }
    val shortened = rawEnd.isBefore(start.plusDays(loanDurationDays.toLong()))
    // A shortened return (sent as end_date) cannot move past the day that shortened it, so on a
    // closed day it moves back to the last open day after the start; when there is none, no
    // loan fits from this start.
    val end = when {
        !cal.isClosed(rawEnd.toString()) -> rawEnd
        else -> cal.openDayFrom(rawEnd, forward = false, notBefore = start.plusDays(1)) ?: start
    }
    return LoanWindow(
        endDate = end.toString(),
        firstBlocked = firstBlocked,
        maxDays = ChronoUnit.DAYS.between(start, end).toInt(),
        capped = firstBlocked != null,
        requiresEndDate = shortened || end.isBefore(rawEnd),
    )
}
