package com.pinakes.app.ui.screens.staff.s090.library

import com.pinakes.app.data.model.Staff090LibraryCardSettings
import com.pinakes.app.data.model.Staff090LibraryClosure
import com.pinakes.app.data.model.Staff090LibraryCustomHoliday
import com.pinakes.app.data.model.Staff090LibraryDay
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModule
import com.pinakes.app.data.model.Staff090LibraryPatronType
import com.pinakes.app.data.model.Staff090LibraryRange
import com.pinakes.app.data.model.Staff090LibraryRuleCell
import com.pinakes.app.data.model.Staff090LibraryTypeValues
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

// The pure side of the library settings screens: what the operator typed, what it means for
// the server (a partial JSON body, cents, null = inherit) and what the screen shows. No Android.

/** A typed amount: empty (no amount, i.e. inherit), valid cents, or not an amount. */
sealed interface CentsInput {
    data object Empty : CentsInput
    data class Valid(val cents: Int) : CentsInput
    data object Invalid : CentsInput
}

object LibraryMoney {
    private val AMOUNT = Regex("""^\d{1,9}([.,]\d{1,2})?$""")

    /**
     * "2", "2.5", "2,50" → cents, exactly (the decimal string is parsed, never a float). Either
     * separator is accepted, no thousands separator, no sign; more than two decimals is refused.
     */
    fun parse(text: String, max: Long = Long.MAX_VALUE): CentsInput {
        val t = text.trim()
        if (t.isEmpty()) return CentsInput.Empty
        if (!AMOUNT.matches(t)) return CentsInput.Invalid
        val cents = BigDecimal(t.replace(',', '.')).movePointRight(2)
        return if (cents > BigDecimal.valueOf(minOf(max, Int.MAX_VALUE.toLong()))) CentsInput.Invalid else CentsInput.Valid(cents.toInt())
    }

    /** Cents as the text the amount field starts with, in the locale's decimal separator; "" for null. */
    fun input(cents: Int?, locale: Locale): String {
        if (cents == null) return ""
        val plain = BigDecimal.valueOf(cents.toLong(), 2).toPlainString()
        val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return if (sep == '.') plain else plain.replace('.', sep)
    }
}

/** A typed whole number: empty, valid, or out of [range] / not a number. */
sealed interface IntInput {
    data object Empty : IntInput
    data class Valid(val value: Int) : IntInput
    data object Invalid : IntInput

    companion object {
        fun parse(text: String, range: Staff090LibraryRange?, allowNegative: Boolean = false): IntInput {
            val t = text.trim()
            if (t.isEmpty()) return Empty
            if (!(if (allowNegative) Regex("^-?\\d{1,9}$") else Regex("^\\d{1,9}$")).matches(t)) return Invalid
            val v = t.toLong()
            if (range != null && (v < range.min || v > range.max)) return Invalid
            return Valid(v.toInt())
        }
    }
}

// =============================================================================================
// Settings → Modules
// =============================================================================================

object ModulesForm {
    /** The modules of one group, in the server's order. */
    fun group(modules: List<Staff090LibraryModule>, group: String) = modules.filter { it.group == group }

    /** Why a module that is switched on does not work, or what it depends on. */
    sealed interface Note {
        data class NeedsParent(val parentLabel: String, val parentOn: Boolean) : Note
        data object CatalogueMode : Note
        data class Follows(val label: String) : Note
    }

    fun notes(module: Staff090LibraryModule, all: List<Staff090LibraryModule>, catalogueMode: Boolean): List<Note> {
        val byKey = all.associateBy { it.key }
        val out = mutableListOf<Note>()
        module.parent?.let { p -> out += Note.NeedsParent(byKey[p]?.label ?: p, byKey[p]?.effective == true) }
        if (module.circulation && catalogueMode) out += Note.CatalogueMode
        module.inheritsFrom?.takeIf { it != module.parent }?.let { p -> out += Note.Follows(byKey[p]?.label ?: p) }
        return out
    }

    /** `{features: {changed…}, attendance_retention_days?}`; null when nothing changed. */
    fun body(original: Map<String, Boolean>, current: Map<String, Boolean>, retentionOriginal: Int, retentionText: String): JsonObject? {
        val changed = current.filter { (k, v) -> original[k] != v }
        val retention = (IntInput.parse(retentionText, null) as? IntInput.Valid)?.value
        val retentionChanged = retention != null && retention != retentionOriginal
        if (changed.isEmpty() && !retentionChanged) return null
        return buildJsonObject {
            if (changed.isNotEmpty()) put("features", buildJsonObject { changed.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
            if (retentionChanged) put("attendance_retention_days", JsonPrimitive(retention))
        }
    }
}

// =============================================================================================
// Settings → Loans
// =============================================================================================

enum class LoanFieldKind { INT, BOOL, CHOICE, MONEY, TIMEZONE }

/** One field of the loans tab, in the web page's order. */
data class LoanField(val key: String, val kind: LoanFieldKind, val section: LoanSection)

enum class LoanSection { APPROVAL, DURATION, CLOSURES, PICKUP, CARD, RENEWALS, LIMITS, RECALLS, FINES }

object LoansForm {
    /**
     * The loan fields after a re-read of the server (a module saved on another tab): a field the
     * operator edited and did not save keeps the edit, every other one takes the server's value.
     */
    fun reseed(draft: Map<String, String>, before: Map<String, String>, server: Map<String, String>): Map<String, String> =
        server.mapValues { (key, value) -> draft[key]?.takeIf { it != before[key] } ?: value }

    val FIELDS = listOf(
        LoanField("auto_approve_requests", LoanFieldKind.BOOL, LoanSection.APPROVAL),
        LoanField("loan_duration_days", LoanFieldKind.INT, LoanSection.DURATION),
        LoanField("max_loan_duration_days", LoanFieldKind.INT, LoanSection.DURATION),
        LoanField("app_timezone", LoanFieldKind.TIMEZONE, LoanSection.DURATION),
        LoanField("same_day_handover_after_closure", LoanFieldKind.BOOL, LoanSection.CLOSURES),
        LoanField("pickup_expiry_days", LoanFieldKind.INT, LoanSection.PICKUP),
        LoanField("pickup_reminder_days", LoanFieldKind.INT, LoanSection.PICKUP),
        LoanField("card_expiry_warning_days", LoanFieldKind.INT, LoanSection.CARD),
        LoanField("card_renewal_base", LoanFieldKind.CHOICE, LoanSection.CARD),
        LoanField("max_renewals", LoanFieldKind.INT, LoanSection.RENEWALS),
        LoanField("max_active_loans_per_user", LoanFieldKind.INT, LoanSection.LIMITS),
        LoanField("allow_multiple_loans_same_book", LoanFieldKind.BOOL, LoanSection.LIMITS),
        LoanField("recall_auto_enabled", LoanFieldKind.BOOL, LoanSection.RECALLS),
        LoanField("recall_interval_days", LoanFieldKind.INT, LoanSection.RECALLS),
        LoanField("recall_max_count", LoanFieldKind.INT, LoanSection.RECALLS),
        LoanField("fine_grace_mode", LoanFieldKind.CHOICE, LoanSection.FINES),
        LoanField("daily_fine_cents", LoanFieldKind.MONEY, LoanSection.FINES),
        LoanField("fine_grace_days", LoanFieldKind.INT, LoanSection.FINES),
        LoanField("fine_max_per_loan_cents", LoanFieldKind.MONEY, LoanSection.FINES),
        LoanField("debt_block_threshold_cents", LoanFieldKind.MONEY, LoanSection.FINES),
        LoanField("fine_charged_email", LoanFieldKind.BOOL, LoanSection.FINES),
    )

    /** The fields this server sent, known ones first in page order (an unknown key is left out). */
    fun fields(loans: Staff090LibraryLoans): List<LoanField> = FIELDS.filter { it.key in loans.settings }

    fun editable(loans: Staff090LibraryLoans, key: String): Boolean = key in loans.editableFields

    /** The stored value as the text the form edits ("true"/"false" for switches, "" for no amount). */
    fun initial(loans: Staff090LibraryLoans, locale: Locale): Map<String, String> = fields(loans).associate { f ->
        val raw = loans.settings[f.key] as? JsonPrimitive
        f.key to when (f.kind) {
            LoanFieldKind.MONEY -> LibraryMoney.input(raw?.intOrNull, locale)
            LoanFieldKind.BOOL -> (raw?.booleanOrNull ?: false).toString()
            else -> if (raw == null || raw is JsonNull) "" else raw.contentOrNull.orEmpty()
        }
    }

    /** Field → true when its text cannot be sent. */
    fun invalid(loans: Staff090LibraryLoans, values: Map<String, String>): Set<String> = fields(loans).filter { f ->
        val text = values[f.key].orEmpty()
        when (f.kind) {
            LoanFieldKind.INT -> IntInput.parse(text, loans.limits[f.key]) !is IntInput.Valid
            LoanFieldKind.MONEY -> LibraryMoney.parse(text, loans.limits["money_max_cents"]?.max ?: Long.MAX_VALUE) is CentsInput.Invalid
            LoanFieldKind.CHOICE -> loans.options[f.key]?.let { text !in it } ?: false
            LoanFieldKind.TIMEZONE -> text.isBlank()
            LoanFieldKind.BOOL -> false
        }
    }.map { it.key }.toSet()

    /** Only the editable fields whose value changed, typed as the server wants them; null when none. */
    fun body(loans: Staff090LibraryLoans, original: Map<String, String>, values: Map<String, String>): JsonObject? {
        val changed = fields(loans).filter { editable(loans, it.key) && values[it.key] != original[it.key] }
        if (changed.isEmpty()) return null
        return buildJsonObject {
            changed.forEach { f ->
                val text = values[f.key].orEmpty()
                put(f.key, when (f.kind) {
                    LoanFieldKind.INT -> (IntInput.parse(text, null) as? IntInput.Valid)?.let { JsonPrimitive(it.value) } ?: JsonNull
                    LoanFieldKind.BOOL -> JsonPrimitive(text.toBoolean())
                    LoanFieldKind.MONEY -> (LibraryMoney.parse(text) as? CentsInput.Valid)?.let { JsonPrimitive(it.cents) } ?: JsonNull
                    LoanFieldKind.CHOICE, LoanFieldKind.TIMEZONE -> JsonPrimitive(text)
                })
            }
        }
    }
}

// =============================================================================================
// Settings → Cards
// =============================================================================================

object CardsForm {
    /** The changed card settings; null when none. Titles are sent trimmed. */
    fun body(original: Staff090LibraryCardSettings, current: Staff090LibraryCardSettings): JsonObject? {
        val out = buildJsonObject {
            if (current.layout != original.layout) put("layout", JsonPrimitive(current.layout))
            if (current.codeType != original.codeType) put("code_type", JsonPrimitive(current.codeType))
            if (current.format != original.format) put("format", JsonPrimitive(current.format))
            if (current.title.trim() != original.title) put("title", JsonPrimitive(current.title.trim()))
            if (current.backText.trim() != original.backText) put("back_text", JsonPrimitive(current.backText.trim()))
            if (current.showPhoto != original.showPhoto) put("show_photo", JsonPrimitive(current.showPhoto))
            if (current.showLogo != original.showLogo) put("show_logo", JsonPrimitive(current.showLogo))
            if (current.showExpiry != original.showExpiry) put("show_expiry", JsonPrimitive(current.showExpiry))
        }
        return out.takeIf { it.isNotEmpty() }
    }
}

// =============================================================================================
// Patron types
// =============================================================================================

/** The numeric fields of a patron type, in the web form's order. */
enum class TypeNumber(val key: String, val money: Boolean = false) {
    MAX_LOANS("max_loans"),
    LOAN_DURATION("loan_duration_days"),
    MAX_REQUEST_DURATION("max_request_duration_days"),
    MAX_RENEWALS("max_renewals"),
    RENEWAL_DAYS("renewal_days"),
    MAX_RESERVATIONS("max_reservations"),
    CARD_DURATION("card_duration_days"),
    GRACE_DAYS("grace_days"),
    DAILY_FINE("daily_fine_cents", money = true),
    MAX_FINE("max_fine_per_loan_cents", money = true),
    DEBT_BLOCK("debt_block_threshold_cents", money = true);

    fun of(t: Staff090LibraryPatronType): Int? = when (this) {
        MAX_LOANS -> t.maxLoans; LOAN_DURATION -> t.loanDurationDays; MAX_REQUEST_DURATION -> t.maxRequestDurationDays
        MAX_RENEWALS -> t.maxRenewals; RENEWAL_DAYS -> t.renewalDays; MAX_RESERVATIONS -> t.maxReservations
        CARD_DURATION -> t.cardDurationDays; GRACE_DAYS -> t.graceDays; DAILY_FINE -> t.dailyFineCents
        MAX_FINE -> t.maxFinePerLoanCents; DEBT_BLOCK -> t.debtBlockThresholdCents
    }

    fun of(v: Staff090LibraryTypeValues): Int? = when (this) {
        MAX_LOANS -> v.maxLoans; LOAN_DURATION -> v.loanDurationDays; MAX_REQUEST_DURATION -> v.maxRequestDurationDays
        MAX_RENEWALS -> v.maxRenewals; RENEWAL_DAYS -> v.renewalDays; MAX_RESERVATIONS -> v.maxReservations
        CARD_DURATION -> v.cardDurationDays; GRACE_DAYS -> v.graceDays; DAILY_FINE -> v.dailyFineCents
        MAX_FINE -> v.maxFinePerLoanCents; DEBT_BLOCK -> v.debtBlockThresholdCents
    }
}

/** Patron type form; numbers stay text, empty = inherit the global value. */
data class TypeForm(
    val id: Int? = null,
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val reservationsEnabled: Boolean = true,
    val active: Boolean = true,
    val isDefault: Boolean = false,
    val numbers: Map<TypeNumber, String> = emptyMap(),
) {
    fun text(n: TypeNumber) = numbers[n].orEmpty()

    companion object {
        val CODE = Regex("^[a-z0-9_-]{0,40}$")

        fun from(t: Staff090LibraryPatronType, locale: Locale) = TypeForm(
            id = t.id, name = t.name, code = t.code, description = t.description.orEmpty(),
            reservationsEnabled = t.reservationsEnabled, active = t.active, isDefault = t.isDefault,
            numbers = TypeNumber.entries.associateWith { n -> if (n.money) LibraryMoney.input(n.of(t), locale) else n.of(t)?.toString().orEmpty() },
        )
    }
}

object TypeForms {
    fun invalid(form: TypeForm, limits: Map<String, Staff090LibraryRange>): Set<String> {
        val out = mutableSetOf<String>()
        if (form.name.isBlank() || form.name.trim().length > 100) out += "name"
        if (!TypeForm.CODE.matches(form.code.trim())) out += "code"
        if (form.description.length > 2000) out += "description"
        TypeNumber.entries.forEach { n ->
            val text = form.text(n)
            val bad = if (n.money) LibraryMoney.parse(text, limits["money_max_cents"]?.max ?: Long.MAX_VALUE) is CentsInput.Invalid
            else IntInput.parse(text, limits[n.key]) is IntInput.Invalid
            if (bad) out += n.key
        }
        return out
    }

    private fun number(n: TypeNumber, text: String): JsonElement =
        if (n.money) (LibraryMoney.parse(text) as? CentsInput.Valid)?.let { JsonPrimitive(it.cents) } ?: JsonNull
        else (IntInput.parse(text, null) as? IntInput.Valid)?.let { JsonPrimitive(it.value) } ?: JsonNull

    /** A new type: every key the operator filled (an empty number is left to inherit). */
    fun createBody(form: TypeForm): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(form.name.trim()))
        form.code.trim().takeIf { it.isNotEmpty() }?.let { put("code", JsonPrimitive(it)) }
        form.description.trim().takeIf { it.isNotEmpty() }?.let { put("description", JsonPrimitive(it)) }
        put("reservations_enabled", JsonPrimitive(form.reservationsEnabled))
        put("active", JsonPrimitive(form.active))
        if (form.isDefault) put("is_default", JsonPrimitive(true))
        TypeNumber.entries.forEach { n -> number(n, form.text(n)).takeIf { it !is JsonNull }?.let { put(n.key, it) } }
    }

    /** An edit: only what changed; a cleared number goes as null (back to inheriting). */
    fun updateBody(original: TypeForm, form: TypeForm): JsonObject? {
        val out = buildJsonObject {
            if (form.name.trim() != original.name) put("name", JsonPrimitive(form.name.trim()))
            if (form.code.trim() != original.code) put("code", JsonPrimitive(form.code.trim()))
            if (form.description.trim() != original.description) {
                put("description", form.description.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (form.reservationsEnabled != original.reservationsEnabled) put("reservations_enabled", JsonPrimitive(form.reservationsEnabled))
            if (form.active != original.active) put("active", JsonPrimitive(form.active))
            if (form.isDefault && !original.isDefault) put("is_default", JsonPrimitive(true))
            TypeNumber.entries.forEach { n ->
                val now = number(n, form.text(n))
                if (now != number(n, original.text(n))) put(n.key, now)
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /**
     * The value an empty field falls back to. While the type leaves the field empty its
     * `effective` policy is exactly that; once the type sets its own value `effective` holds
     * that value instead, so the fallback comes from the globals (an empty renewal period
     * still follows the loan duration the type would get).
     */
    fun inherited(n: TypeNumber, type: Staff090LibraryPatronType?, globals: Staff090LibraryTypeValues): Int? {
        if (type == null || n.of(type) == null) return type?.effective?.let { n.of(it) } ?: n.of(globals)
        return n.of(globals) ?: if (n == TypeNumber.RENEWAL_DAYS) {
            TypeNumber.LOAN_DURATION.of(type) ?: TypeNumber.LOAN_DURATION.of(globals)
        } else null
    }
}

// =============================================================================================
// Rules matrix
// =============================================================================================

enum class RuleNumber(val key: String, val money: Boolean = false) {
    MAX_LOANS("max_loans"),
    LOAN_DURATION("loan_duration_days"),
    MAX_RENEWALS("max_renewals"),
    RENEWAL_DAYS("renewal_days"),
    GRACE_DAYS("grace_days"),
    DAILY_FINE("daily_fine_cents", money = true),
    MAX_FINE("max_fine_per_loan_cents", money = true);

    fun of(c: Staff090LibraryRuleCell): Int? = when (this) {
        MAX_LOANS -> c.maxLoans; LOAN_DURATION -> c.loanDurationDays; MAX_RENEWALS -> c.maxRenewals
        RENEWAL_DAYS -> c.renewalDays; GRACE_DAYS -> c.graceDays; DAILY_FINE -> c.dailyFineCents; MAX_FINE -> c.maxFinePerLoanCents
    }

    fun inherited(c: Staff090LibraryRuleCell): Int? = when (this) {
        MAX_LOANS -> c.inherited.maxLoans; LOAN_DURATION -> c.inherited.loanDurationDays; MAX_RENEWALS -> c.inherited.maxRenewals
        RENEWAL_DAYS -> c.inherited.renewalDays; GRACE_DAYS -> c.inherited.graceDays
        DAILY_FINE -> c.inherited.dailyFineCents; MAX_FINE -> c.inherited.maxFinePerLoanCents
    }
}

/** One cell being edited: loanable null = inherit; numbers as text, empty = inherit. */
data class CellForm(val loanable: Boolean? = null, val numbers: Map<RuleNumber, String> = emptyMap()) {
    fun text(n: RuleNumber) = numbers[n].orEmpty()

    companion object {
        fun from(c: Staff090LibraryRuleCell, locale: Locale) = CellForm(
            loanable = c.loanable,
            numbers = RuleNumber.entries.associateWith { n -> if (n.money) LibraryMoney.input(n.of(c), locale) else n.of(c)?.toString().orEmpty() },
        )
    }
}

object RulesForm {
    fun key(typeId: Int, media: String) = "$typeId|$media"

    fun invalid(form: CellForm, limits: Map<String, Staff090LibraryRange>): Set<String> = RuleNumber.entries.filter { n ->
        val text = form.text(n)
        if (n.money) LibraryMoney.parse(text, limits["money_max_cents"]?.max ?: Long.MAX_VALUE) is CentsInput.Invalid
        else IntInput.parse(text, limits[n.key]) is IntInput.Invalid
    }.map { it.key }.toSet()

    private fun number(n: RuleNumber, text: String): JsonElement =
        if (n.money) (LibraryMoney.parse(text) as? CentsInput.Valid)?.let { JsonPrimitive(it.cents) } ?: JsonNull
        else (IntInput.parse(text, null) as? IntInput.Valid)?.let { JsonPrimitive(it.value) } ?: JsonNull

    /** One cell of `PUT /staff/circulation/rules`: its address and only the changed values; null when none. */
    fun cellBody(cell: Staff090LibraryRuleCell, form: CellForm, locale: Locale): JsonObject? {
        val original = CellForm.from(cell, locale)
        val values = buildJsonObject {
            if (form.loanable != original.loanable) put("loanable", form.loanable?.let(::JsonPrimitive) ?: JsonNull)
            RuleNumber.entries.forEach { n ->
                val now = number(n, form.text(n))
                if (now != number(n, original.text(n))) put(n.key, now)
            }
        }
        if (values.isEmpty()) return null
        return JsonObject(mapOf("type_id" to JsonPrimitive(cell.typeId), "media" to JsonPrimitive(cell.media)) + values)
    }

    /** The changed cells among [edits] (key → form), in matrix order. */
    fun body(cells: List<Staff090LibraryRuleCell>, edits: Map<String, CellForm>, locale: Locale): JsonArray =
        JsonArray(cells.mapNotNull { c -> edits[key(c.typeId, c.media)]?.let { cellBody(c, it, locale) } })

    /** True when a cell differs from what it inherits (it has its own value somewhere). */
    fun customised(c: Staff090LibraryRuleCell): Boolean = c.loanable != null || RuleNumber.entries.any { it.of(c) != null }
}

// =============================================================================================
// Closures calendar
// =============================================================================================

object CalendarGrid {
    /**
     * The month as rows of seven, weeks starting on Monday: null for the blank cells before the
     * first day and after the last. `weekday` is the server's 0 = Sunday … 6 = Saturday.
     */
    fun weeks(days: List<Staff090LibraryDay>): List<List<Staff090LibraryDay?>> {
        if (days.isEmpty()) return emptyList()
        val lead = (days.first().weekday + 6) % 7
        val cells: List<Staff090LibraryDay?> = List(lead) { null } + days
        val padded = cells + List((7 - cells.size % 7) % 7) { null }
        return padded.chunked(7)
    }

    /** Monday-first order of the server's weekday numbers. */
    val MONDAY_FIRST = listOf(1, 2, 3, 4, 5, 6, 0)

    /** "2026-11" moved by [delta] months. */
    fun shift(month: String, delta: Long): String = runCatching {
        LocalDate.parse("$month-01").plusMonths(delta).toString().substring(0, 7)
    }.getOrDefault(month)

    /** Closures that are toggled as a whole each year (yearly ones not made by a preset). */
    fun yearly(closures: List<Staff090LibraryClosure>) = closures.filter { it.yearly && it.presetRule == null }

    /** At least one weekday must stay open (the server refuses all seven). */
    fun weekdaysValid(closed: Set<Int>) = closed.size < 7 && closed.all { it in 0..6 }

    /**
     * The weekday ticks after a reload (another month, another branch, a save elsewhere): the
     * server's days, unless the operator changed them since the last answer ([before]), in which
     * case the unsaved ticks stay.
     */
    fun reseedWeekdays(draft: Set<Int>, before: Set<Int>?, server: Set<Int>): Set<Int> =
        if (before == null || draft == before) server else draft

    /**
     * The yearly switches after a reload: each switch the operator flipped and did not save keeps
     * its position (while the closure still exists); every other one takes the server's.
     */
    fun reseedYearly(draft: Map<Int, Boolean>, before: List<Staff090LibraryClosure>?, server: List<Staff090LibraryClosure>): Map<Int, Boolean> {
        val old = yearly(before.orEmpty()).associate { it.id to it.active }
        return yearly(server).associate { c ->
            val mine = draft[c.id]
            c.id to if (mine != null && c.id in old && mine != old[c.id]) mine else c.active
        }
    }

    /**
     * The ticked national holidays after a new preview: another year or region of the same
     * country keeps what the operator ticked or unticked (for the rules still listed); a new
     * country, or a list the operator has not touched, takes the server's selection.
     */
    fun reseedHolidays(selected: Set<String>, touched: Boolean, sameCountry: Boolean, listed: List<String>, serverChecked: Set<String>): Set<String> =
        if (touched && sameCountry) selected.filter { it in listed }.toSet() else serverChecked
}

/** Closure form (create and edit). */
data class ClosureForm(
    val id: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val description: String = "",
    val yearly: Boolean = false,
    val branchId: Int? = null,
) {
    val datesValid: Boolean get() = startDate != null && (endDate == null || endDate >= startDate)
    val valid: Boolean get() = datesValid && description.length <= 255

    fun body(): JsonObject = buildJsonObject {
        put("start_date", JsonPrimitive(startDate.orEmpty()))
        put("end_date", endDate?.let(::JsonPrimitive) ?: JsonNull)
        put("description", JsonPrimitive(description.trim()))
        put("yearly", JsonPrimitive(yearly))
        put("branch_id", branchId?.let(::JsonPrimitive) ?: JsonNull)
    }

    companion object {
        fun from(c: Staff090LibraryClosure) = ClosureForm(
            id = c.id, startDate = c.startDate, endDate = c.endDate.takeIf { it.isNotBlank() && it != c.startDate },
            description = c.description, yearly = c.yearly, branchId = c.branchId,
        )
    }
}

/** The fields of a custom holiday that its kind uses, besides name, duration, weekend rule, years, branch. */
enum class CustomField { MONTH, DAY, WEEKDAY, OCCURRENCE, OFFSET, CALENDAR, DATES }

/** Custom holiday form; numbers as text. */
data class CustomForm(
    val id: Int? = null,
    val name: String = "",
    val kind: String = "fixed",
    val month: String = "1",
    val day: String = "1",
    val weekday: Int = 1,
    val occurrence: Int = 1,
    val offset: String = "0",
    val calendar: String = "",
    val dates: String = "",
    val duration: String = "1",
    val weekendRule: String? = null,
    val yearFrom: String = "",
    val yearUntil: String = "",
    val branchId: Int? = null,
    val active: Boolean = true,
) {
    companion object {
        fun from(h: Staff090LibraryCustomHoliday) = CustomForm(
            id = h.id, name = h.name, kind = h.kind, month = h.month?.toString() ?: "1", day = h.day?.toString() ?: "1",
            weekday = h.weekday ?: 1, occurrence = h.occurrence ?: 1, offset = h.offsetDays?.toString() ?: "0",
            calendar = h.calendar.orEmpty(), dates = h.dates.joinToString("\n"), duration = h.durationDays.toString(),
            weekendRule = h.weekendRule, yearFrom = h.yearFrom?.toString().orEmpty(), yearUntil = h.yearUntil?.toString().orEmpty(),
            branchId = h.branchId, active = h.active,
        )
    }
}

object CustomForms {
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun fields(kind: String): Set<CustomField> = when (kind) {
        "fixed" -> setOf(CustomField.MONTH, CustomField.DAY)
        "nth_weekday" -> setOf(CustomField.OCCURRENCE, CustomField.WEEKDAY, CustomField.MONTH)
        "easter", "orthodox_easter" -> setOf(CustomField.OFFSET)
        "calendar" -> setOf(CustomField.CALENDAR, CustomField.MONTH, CustomField.DAY)
        "dates" -> setOf(CustomField.DATES)
        else -> emptySet()
    }

    /** The dates typed one per line (or separated by commas/spaces), trimmed, without blanks. */
    fun dates(text: String): List<String> = text.split('\n', ',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    /** Days of [month] the form allows: 31 for the Gregorian kinds, 30 in another calendar. */
    fun maxDay(kind: String) = if (kind == "calendar") 30 else 31

    fun invalid(form: CustomForm, maxDuration: Int, maxDates: Int, monthsInCalendar: Int): Set<String> {
        val out = mutableSetOf<String>()
        val fields = fields(form.kind)
        if (form.name.isBlank() || form.name.length > 255) out += "name"
        if (form.kind.isBlank()) out += "kind"
        val maxMonth = if (form.kind == "calendar") monthsInCalendar.coerceAtLeast(1) else 12
        if (CustomField.MONTH in fields && IntInput.parse(form.month, com.pinakes.app.data.model.Staff090LibraryRange(1, maxMonth.toLong())) !is IntInput.Valid) out += "month"
        if (CustomField.DAY in fields && IntInput.parse(form.day, com.pinakes.app.data.model.Staff090LibraryRange(1, maxDay(form.kind).toLong())) !is IntInput.Valid) out += "day"
        if (CustomField.OFFSET in fields && IntInput.parse(form.offset, com.pinakes.app.data.model.Staff090LibraryRange(-200, 200), allowNegative = true) !is IntInput.Valid) out += "offset_days"
        if (CustomField.CALENDAR in fields && form.calendar.isBlank()) out += "calendar"
        if (CustomField.DATES in fields) {
            val list = dates(form.dates)
            if (list.isEmpty() || list.size > maxDates || list.any { !DATE.matches(it) || runCatching { LocalDate.parse(it) }.isFailure }) out += "dates"
        }
        if (IntInput.parse(form.duration, com.pinakes.app.data.model.Staff090LibraryRange(1, maxDuration.toLong())) !is IntInput.Valid) out += "duration_days"
        val years = com.pinakes.app.data.model.Staff090LibraryRange(1900, 2199)
        val from = IntInput.parse(form.yearFrom, years)
        val until = IntInput.parse(form.yearUntil, years)
        if (from is IntInput.Invalid) out += "year_from"
        if (until is IntInput.Invalid) out += "year_until"
        if (from is IntInput.Valid && until is IntInput.Valid && until.value < from.value) out += "year_until"
        return out
    }

    /** The whole holiday as the server stores it: the kind's fields, the others null. */
    fun body(form: CustomForm): JsonObject {
        val fields = fields(form.kind)
        fun int(text: String, on: Boolean) = if (on) text.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull else JsonNull
        return buildJsonObject {
            put("name", JsonPrimitive(form.name.trim()))
            put("kind", JsonPrimitive(form.kind))
            put("month", int(form.month, CustomField.MONTH in fields))
            put("day", int(form.day, CustomField.DAY in fields))
            put("weekday", if (CustomField.WEEKDAY in fields) JsonPrimitive(form.weekday) else JsonNull)
            put("occurrence", if (CustomField.OCCURRENCE in fields) JsonPrimitive(form.occurrence) else JsonNull)
            put("offset_days", int(form.offset, CustomField.OFFSET in fields))
            put("calendar", if (CustomField.CALENDAR in fields) JsonPrimitive(form.calendar) else JsonNull)
            put("dates", if (CustomField.DATES in fields) JsonArray(dates(form.dates).map(::JsonPrimitive)) else JsonArray(emptyList()))
            put("duration_days", form.duration.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(1))
            put("weekend_rule", form.weekendRule?.let(::JsonPrimitive) ?: JsonNull)
            put("year_from", form.yearFrom.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
            put("year_until", form.yearUntil.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
            put("branch_id", form.branchId?.let(::JsonPrimitive) ?: JsonNull)
            put("active", JsonPrimitive(form.active))
        }
    }
}
