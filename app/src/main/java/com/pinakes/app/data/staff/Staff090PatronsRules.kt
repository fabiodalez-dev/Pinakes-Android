package com.pinakes.app.data.staff

import com.pinakes.app.data.model.AttendanceVisit
import com.pinakes.app.data.model.StaffLedger
import com.pinakes.app.data.model.StaffLoanLateFine
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.model.UserImportField
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.network.ApiResult

/**
 * Amount typed at the desk → whole cents, exactly: the decimal string is parsed digit by digit,
 * never through a floating-point number ("0.29" is 29 cents, not 28). Comma or dot as the
 * decimal separator, at most two decimals, no sign, no thousands separators. Pure.
 */
object MoneyInput {
    sealed interface Parsed {
        data object Empty : Parsed
        data object Invalid : Parsed
        data object TooLarge : Parsed
        data class Cents(val value: Int) : Parsed
    }

    private val PATTERN = Regex("""^(\d{1,9})(?:[.,](\d{1,2}))?$""")

    fun parse(raw: String, maxCents: Int = 99_999_999): Parsed {
        val text = raw.trim()
        if (text.isEmpty()) return Parsed.Empty
        val m = PATTERN.matchEntire(text) ?: return Parsed.Invalid
        val units = m.groupValues[1].toLong()
        val decimals = m.groupValues[2].padEnd(2, '0').toLong()
        val cents = units * 100 + decimals
        return when {
            cents <= 0 -> Parsed.Invalid
            cents > maxCents -> Parsed.TooLarge
            else -> Parsed.Cents(cents.toInt())
        }
    }

    /** Cents → the text the amount field starts from ("12.50"), never localised. */
    fun text(cents: Int): String = "%d.%02d".format(java.util.Locale.ROOT, cents / 100, kotlin.math.abs(cents % 100))
}

/** The three ledger forms of the desk. */
enum class LedgerAction { PAYMENT, CHARGE, WAIVER }

/** Which parts of the patron record a desk operator sees. Hidden, never disabled. Pure. */
data class PatronTools(
    val section: Boolean,
    val photo: Boolean,
    val photoEdit: Boolean,
    val patronType: Boolean,
    val patronTypeEdit: Boolean,
    val renewCard: Boolean,
    val printCard: Boolean,
    val ledger: Boolean,
) {
    companion object {
        fun of(areas: Staff090Areas, user: StaffUserDetail): PatronTools {
            val photo = areas.patronPhotos
            val type = areas.patronTypes && user.patronType != null
            // Readers carry a card; administrators and staff accounts answer 409 card_not_renewable.
            val reader = user.role == null || user.role in setOf("standard", "premium")
            val renew = areas.patrons && reader
            val print = areas.patronCards && !user.cardNumber.isNullOrBlank()
            val ledger = areas.ledger && user.ledger != null
            return PatronTools(
                section = photo || type || renew || print || ledger,
                photo = photo,
                photoEdit = photo && user.canEdit,
                patronType = type,
                patronTypeEdit = type && user.canEdit,
                renewCard = renew,
                printCard = print,
                ledger = ledger,
            )
        }
    }
}

/** The ledger actions the server allows, in the order the desk offers them. Pure. */
object LedgerRules {
    fun actions(ledger: StaffLedger): List<LedgerAction> = buildList {
        if (ledger.canRecordPayment && ledger.balanceCents > 0) add(LedgerAction.PAYMENT)
        if (ledger.canCharge) add(LedgerAction.CHARGE)
        if (ledger.canWaive && ledger.balanceCents > 0) add(LedgerAction.WAIVER)
    }

    /** Payments and waivers never exceed what the patron owes (the server re-checks: 409). */
    fun maxCents(action: LedgerAction, ledger: StaffLedger): Int = when (action) {
        LedgerAction.CHARGE -> ledger.maxAmountCents
        LedgerAction.PAYMENT, LedgerAction.WAIVER -> minOf(ledger.balanceCents.coerceAtLeast(0), ledger.maxAmountCents)
    }

    /** The form can be sent: a valid amount within [maxCents] and the text the action requires. */
    fun canSubmit(action: LedgerAction, amount: MoneyInput.Parsed, method: String?, text: String): Boolean =
        amount is MoneyInput.Parsed.Cents && when (action) {
            LedgerAction.PAYMENT -> !method.isNullOrBlank()
            LedgerAction.CHARGE, LedgerAction.WAIVER -> text.isNotBlank()
        }
}

/** The return form's late fine: shown only when a return today would charge one. Pure. */
object LateFineRules {
    fun shows(fine: StaffLoanLateFine?): Boolean = fine != null && fine.automaticFines && fine.accruingCents > 0

    /** A waiver needs its reason (the server answers 422 waive_reason otherwise). */
    fun canConfirm(waive: Boolean, reason: String): Boolean = !waive || reason.isNotBlank()

    /** The reason sent with the return, or null when the fine is charged. */
    fun waiveReason(fine: StaffLoanLateFine?, waive: Boolean, reason: String): String? =
        if (shows(fine) && waive) reason.trim().takeIf { it.isNotEmpty() } else null
}

/** How the desk shows a check-in. */
enum class VisitOutcome { WELCOME, WARNING, GUEST, UNKNOWN_CARD, ERROR }

/** Visitor log decisions. Pure. */
object AttendanceRules {
    /** A visit's tone is due once: [tick] counts the visits, [played] the last one sounded. */
    fun tonePending(tick: Int, played: Int): Boolean = tick > played

    fun outcome(visit: AttendanceVisit): VisitOutcome = when {
        visit.type == "guest" -> VisitOutcome.GUEST
        visit.warning != null || visit.member?.cardActive == false || visit.member?.cardExpired == true -> VisitOutcome.WARNING
        else -> VisitOutcome.WELCOME
    }

    fun outcome(failure: ApiResult.Failure): VisitOutcome = if (failure.code == "card_not_found") VisitOutcome.UNKNOWN_CARD else VisitOutcome.ERROR

    /** A short confirmation beep for a welcome, a lower double tone for anything to look at. */
    fun successTone(outcome: VisitOutcome): Boolean = outcome == VisitOutcome.WELCOME || outcome == VisitOutcome.GUEST

    /** The scanned card code as the server matches it: trimmed, inner text untouched. */
    fun normalizeCard(raw: String): String = raw.trim()

    /** Bar widths 0..1 for a series (the largest value fills the row). */
    fun barFractions(values: List<Int>): List<Float> {
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: return values.map { 0f }
        return values.map { (it.coerceAtLeast(0).toFloat() / max).coerceIn(0f, 1f) }
    }

    /** A retention typed in the settings: 0..[max] whole days, else null. */
    fun parseRetention(raw: String, max: Int): Int? = raw.trim().toIntOrNull()?.takeIf { it in 0..max }
}

/**
 * The patron CSV import: the mapping editor and the chunk driver's rules. The chunk loop itself
 * follows [ImportProgress] (out_of_order → resume from `meta.processed`, job_busy → wait, gone →
 * stop). Pure.
 */
object UserImportRules {
    val DELIMITERS = listOf("", ";", ",", "tab")

    private val FINAL = setOf("completed", "failed", "cancelled")

    fun isFinished(job: UserImportJob): Boolean = job.complete || job.status in FINAL

    fun percent(job: UserImportJob): Int = when {
        job.totalRows <= 0 -> if (isFinished(job)) 100 else 0
        else -> ((job.processed.coerceIn(0, job.totalRows) * 100L) / job.totalRows).toInt()
    }

    fun next(job: UserImportJob): ChunkStep = if (isFinished(job)) ChunkStep.Done else ChunkStep.Next(job.processed)

    /** Same rules as the catalogue import, plus `not_started` and `job_cancelled` that stop the loop. */
    fun afterFailure(failure: ApiResult.Failure, start: Int, busyAttempts: Int): ChunkStep = ImportProgress.afterFailure(failure, start, busyAttempts)

    /** The jobs still to finish (the list also carries completed and cancelled ones until they expire). */
    fun openJobs(jobs: List<UserImportJob>): List<UserImportJob> = jobs.filterNot(::isFinished)

    /** The mapping aligned with the headers (missing entries are "ignored"). */
    fun aligned(job: UserImportJob, mapping: List<String> = job.mapping): List<String> =
        List(job.headers.size) { i -> mapping.getOrNull(i).orEmpty() }

    /** Map column [column] to [key] ("" = ignore); a field used elsewhere is taken from that column. */
    fun assign(mapping: List<String>, column: Int, key: String): List<String> =
        mapping.mapIndexed { i, current -> if (i == column) key else if (key.isNotEmpty() && current == key) "" else current }

    /** Required fields not mapped to any column. */
    fun missingRequired(fields: List<UserImportField>, mapping: List<String>): List<UserImportField> =
        fields.filter { it.required && it.key !in mapping }

    fun canStart(job: UserImportJob, mapping: List<String>): Boolean =
        !job.started && !isFinished(job) && missingRequired(job.fields, mapping).isEmpty()

    /** Accepted file names (the server re-checks): .csv, .tsv, .txt. */
    fun acceptsFileName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("csv", "tsv", "txt")

    /** The server's upload limit (5 MB). */
    const val MAX_BYTES = 5L * 1024 * 1024
}
