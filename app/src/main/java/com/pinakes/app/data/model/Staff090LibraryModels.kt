package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Library settings of the Pinakes 0.9.0 modules (Mobile API 1.10.0, areas `library_settings`,
// `circulation_rules`, `closures_calendar`). Money is always integer cents in the server's
// `currency`; a null number or amount means "not set here, inherited from the layer above".

/** A `{min, max}` range of a numeric field. */
@Serializable
data class Staff090LibraryRange(val min: Long = 0, val max: Long = Long.MAX_VALUE)

// ---------- Settings → Modules ----------

@Serializable
data class Staff090LibraryModules(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    @SerialName("catalogue_mode") val catalogueMode: Boolean = false,
    @SerialName("attendance_retention_days") val attendanceRetentionDays: Int = 0,
    @SerialName("attendance_retention_max_days") val attendanceRetentionMaxDays: Int = 3650,
    val modules: List<Staff090LibraryModule> = emptyList(),
)

@Serializable
data class Staff090LibraryModule(
    val key: String = "",
    val label: String = "",
    val help: String = "",
    /** `modules` (Settings → Modules) or `loans` (Settings → Loans). */
    val group: String = "modules",
    /** The saved switch (what the form shows). */
    val stored: Boolean = false,
    /** Whether the module is actually on: off with its [parent] off, or circulation in catalogue mode. */
    val effective: Boolean = false,
    /** The module this one needs switched on to work. */
    val parent: String? = null,
    /** The module whose saved state this one copies until it is saved on its own. */
    @SerialName("inherits_from") val inheritsFrom: String? = null,
    val circulation: Boolean = false,
)

// ---------- Settings → Loans ----------

@Serializable
data class Staff090LibraryLoans(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    val currency: String? = null,
    /** Values by API key: integers, booleans, strings, cents (null = no amount). */
    val settings: Map<String, JsonElement> = emptyMap(),
    @SerialName("editable_fields") val editableFields: List<String> = emptyList(),
    @SerialName("admin_only_fields") val adminOnlyFields: List<String> = emptyList(),
    val limits: Map<String, Staff090LibraryRange> = emptyMap(),
    val options: Map<String, List<String>> = emptyMap(),
)

// ---------- Settings → Cards ----------

@Serializable
data class Staff090LibraryCards(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    @SerialName("module_enabled") val moduleEnabled: Boolean = false,
    val settings: Staff090LibraryCardSettings = Staff090LibraryCardSettings(),
    val options: Map<String, List<String>> = emptyMap(),
    val limits: Staff090LibraryCardLimits = Staff090LibraryCardLimits(),
)

@Serializable
data class Staff090LibraryCardSettings(
    val layout: String = "",
    @SerialName("code_type") val codeType: String = "",
    val format: String = "",
    val title: String = "",
    @SerialName("back_text") val backText: String = "",
    @SerialName("show_photo") val showPhoto: Boolean = false,
    @SerialName("show_logo") val showLogo: Boolean = false,
    @SerialName("show_expiry") val showExpiry: Boolean = false,
)

@Serializable
data class Staff090LibraryCardLimits(
    @SerialName("title_max") val titleMax: Int = 60,
    @SerialName("back_text_max") val backTextMax: Int = 600,
)

// ---------- Patron types ----------

@Serializable
data class Staff090LibraryTypes(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    @SerialName("module_enabled") val moduleEnabled: Boolean = false,
    val currency: String? = null,
    @SerialName("default_type_id") val defaultTypeId: Int? = null,
    /** Settings → Loans, the layer every empty type value inherits. */
    val globals: Staff090LibraryTypeValues = Staff090LibraryTypeValues(),
    val limits: Map<String, Staff090LibraryRange> = emptyMap(),
    val types: List<Staff090LibraryPatronType> = emptyList(),
)

/** The numbers of a patron type (also its `effective` policy and the `globals`); null = inherited. */
@Serializable
data class Staff090LibraryTypeValues(
    @SerialName("max_loans") val maxLoans: Int? = null,
    @SerialName("loan_duration_days") val loanDurationDays: Int? = null,
    @SerialName("max_request_duration_days") val maxRequestDurationDays: Int? = null,
    @SerialName("max_renewals") val maxRenewals: Int? = null,
    @SerialName("renewal_days") val renewalDays: Int? = null,
    @SerialName("reservations_enabled") val reservationsEnabled: Boolean? = null,
    @SerialName("max_reservations") val maxReservations: Int? = null,
    @SerialName("card_duration_days") val cardDurationDays: Int? = null,
    @SerialName("grace_days") val graceDays: Int? = null,
    @SerialName("daily_fine_cents") val dailyFineCents: Int? = null,
    @SerialName("max_fine_per_loan_cents") val maxFinePerLoanCents: Int? = null,
    @SerialName("debt_block_threshold_cents") val debtBlockThresholdCents: Int? = null,
)

@Serializable
data class Staff090LibraryPatronType(
    val id: Int = 0,
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    @SerialName("reservations_enabled") val reservationsEnabled: Boolean = true,
    val active: Boolean = true,
    @SerialName("is_default") val isDefault: Boolean = false,
    val order: Int = 0,
    @SerialName("user_count") val userCount: Int = 0,
    @SerialName("max_loans") val maxLoans: Int? = null,
    @SerialName("loan_duration_days") val loanDurationDays: Int? = null,
    @SerialName("max_request_duration_days") val maxRequestDurationDays: Int? = null,
    @SerialName("max_renewals") val maxRenewals: Int? = null,
    @SerialName("renewal_days") val renewalDays: Int? = null,
    @SerialName("max_reservations") val maxReservations: Int? = null,
    @SerialName("card_duration_days") val cardDurationDays: Int? = null,
    @SerialName("grace_days") val graceDays: Int? = null,
    @SerialName("daily_fine_cents") val dailyFineCents: Int? = null,
    @SerialName("max_fine_per_loan_cents") val maxFinePerLoanCents: Int? = null,
    @SerialName("debt_block_threshold_cents") val debtBlockThresholdCents: Int? = null,
    /** The policy this type actually gets (its values, else the global ones). */
    val effective: Staff090LibraryTypeValues? = null,
)

@Serializable
data class Staff090LibraryTypeDeleted(
    val id: Int = 0,
    val deleted: Boolean = false,
    @SerialName("users_reassigned") val usersReassigned: Int = 0,
)

// ---------- Rules matrix ----------

@Serializable
data class Staff090LibraryRules(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    @SerialName("module_enabled") val moduleEnabled: Boolean = false,
    val currency: String? = null,
    val media: List<Staff090LibraryMedia> = emptyList(),
    /** Patron types; `type_id` 0 is "all types". */
    val rows: List<Staff090LibraryRuleRow> = emptyList(),
    val limits: Map<String, Staff090LibraryRange> = emptyMap(),
    val cells: List<Staff090LibraryRuleCell> = emptyList(),
)

@Serializable
data class Staff090LibraryMedia(val key: String = "", val label: String = "")

@Serializable
data class Staff090LibraryRuleRow(@SerialName("type_id") val typeId: Int = 0, val name: String = "")

@Serializable
data class Staff090LibraryRuleCell(
    @SerialName("type_id") val typeId: Int = 0,
    val media: String = "",
    @SerialName("has_rule") val hasRule: Boolean = false,
    val loanable: Boolean? = null,
    @SerialName("max_loans") val maxLoans: Int? = null,
    @SerialName("loan_duration_days") val loanDurationDays: Int? = null,
    @SerialName("max_renewals") val maxRenewals: Int? = null,
    @SerialName("renewal_days") val renewalDays: Int? = null,
    @SerialName("grace_days") val graceDays: Int? = null,
    @SerialName("daily_fine_cents") val dailyFineCents: Int? = null,
    @SerialName("max_fine_per_loan_cents") val maxFinePerLoanCents: Int? = null,
    /** What an empty value of this cell falls back to. */
    val inherited: Staff090LibraryRuleInherited = Staff090LibraryRuleInherited(),
)

@Serializable
data class Staff090LibraryRuleInherited(
    val loanable: Boolean = true,
    @SerialName("max_loans") val maxLoans: Int? = null,
    @SerialName("loan_duration_days") val loanDurationDays: Int? = null,
    @SerialName("max_renewals") val maxRenewals: Int? = null,
    @SerialName("renewal_days") val renewalDays: Int? = null,
    @SerialName("grace_days") val graceDays: Int? = null,
    @SerialName("daily_fine_cents") val dailyFineCents: Int? = null,
    @SerialName("max_fine_per_loan_cents") val maxFinePerLoanCents: Int? = null,
)

// ---------- Closures calendar ----------

@Serializable
data class Staff090LibraryCalendar(
    @SerialName("can_configure") val canConfigure: Boolean = false,
    @SerialName("module_enabled") val moduleEnabled: Boolean = false,
    val today: String = "",
    /** `YYYY-MM` */
    val month: String = "",
    @SerialName("prev_month") val prevMonth: String = "",
    @SerialName("next_month") val nextMonth: String = "",
    @SerialName("branch_id") val branchId: Int? = null,
    val days: List<Staff090LibraryDay> = emptyList(),
    /** 0 = Sunday … 6 = Saturday. */
    @SerialName("closed_weekdays") val closedWeekdays: List<Int> = emptyList(),
    val closures: List<Staff090LibraryClosure> = emptyList(),
    @SerialName("custom_holidays_available") val customHolidaysAvailable: Boolean = false,
    @SerialName("custom_holidays") val customHolidays: List<Staff090LibraryCustomHoliday> = emptyList(),
    @SerialName("custom_holiday_options") val customHolidayOptions: Staff090LibraryCustomOptions = Staff090LibraryCustomOptions(),
    @SerialName("holiday_preset") val holidayPreset: Staff090LibraryHolidayPreset = Staff090LibraryHolidayPreset(),
    val branches: List<IdName> = emptyList(),
    val limits: Staff090LibraryCalendarLimits = Staff090LibraryCalendarLimits(),
)

@Serializable
data class Staff090LibraryDay(
    val date: String = "",
    val weekday: Int = 0,
    val closed: Boolean = false,
    val reasons: List<Staff090LibraryDayReason> = emptyList(),
)

@Serializable
data class Staff090LibraryDayReason(
    /** `weekly`, `closure`, `holiday` or `custom_holiday`. */
    val type: String = "",
    /** A closure or custom holiday id (number), a preset rule id (text), null for the weekly closure. */
    val id: JsonElement? = null,
    val name: String = "",
) {
    val numericId: Int? get() = (id as? JsonPrimitive)?.takeUnless { it.isString }?.contentOrNull?.toIntOrNull()
}

@Serializable
data class Staff090LibraryClosure(
    val id: Int = 0,
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
    val description: String = "",
    val yearly: Boolean = false,
    @SerialName("branch_id") val branchId: Int? = null,
    @SerialName("branch_name") val branchName: String? = null,
    /** Set on a closure made by a national holiday preset: edited from the preset, not here. */
    @SerialName("preset_rule") val presetRule: String? = null,
    val active: Boolean = true,
    val editable: Boolean = true,
)

@Serializable
data class Staff090LibraryCustomHoliday(
    val id: Int = 0,
    val name: String = "",
    /** `fixed`, `nth_weekday`, `easter`, `orthodox_easter`, `calendar` or `dates`. */
    val kind: String = "",
    val month: Int? = null,
    val day: Int? = null,
    val weekday: Int? = null,
    val occurrence: Int? = null,
    @SerialName("offset_days") val offsetDays: Int? = null,
    val calendar: String? = null,
    val dates: List<String> = emptyList(),
    @SerialName("duration_days") val durationDays: Int = 1,
    @SerialName("weekend_rule") val weekendRule: String? = null,
    @SerialName("year_from") val yearFrom: Int? = null,
    @SerialName("year_until") val yearUntil: Int? = null,
    @SerialName("branch_id") val branchId: Int? = null,
    @SerialName("branch_name") val branchName: String? = null,
    val active: Boolean = true,
    /** The rule in words, in the operator's language. */
    val description: String = "",
    @SerialName("next_dates") val nextDates: List<String> = emptyList(),
)

@Serializable
data class Staff090LibraryCustomOptions(
    val kinds: List<String> = emptyList(),
    @SerialName("weekend_rules") val weekendRules: List<String> = emptyList(),
    val occurrences: List<Int> = emptyList(),
    val calendars: List<Staff090LibraryWorldCalendar> = emptyList(),
    @SerialName("max_duration_days") val maxDurationDays: Int = 14,
    @SerialName("max_dates") val maxDates: Int = 400,
)

@Serializable
data class Staff090LibraryWorldCalendar(
    val code: String = "",
    val label: String = "",
    val available: Boolean = true,
    val months: List<String> = emptyList(),
)

@Serializable
data class Staff090LibraryHolidayPreset(
    val country: String = "",
    @SerialName("country_name") val countryName: String = "",
    val region: String = "",
    val regions: List<Staff090LibraryCodeName> = emptyList(),
    val year: Int = 0,
    val years: Staff090LibraryRange? = null,
    /** Countries computed from a dataset: the first and last year it covers. */
    @SerialName("data_years") val dataYears: List<Int>? = null,
    /** True when the library already saved a selection for this country. */
    val saved: Boolean = false,
    @SerialName("schema_ready") val schemaReady: Boolean = true,
    val missing: List<JsonElement> = emptyList(),
    val holidays: List<Staff090LibraryHoliday> = emptyList(),
)

@Serializable
data class Staff090LibraryCodeName(val code: String = "", val name: String = "")

@Serializable
data class Staff090LibraryHoliday(
    val id: String = "",
    val name: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val date: String? = null,
    val dates: List<String> = emptyList(),
    val yearly: Boolean = false,
    val regional: Boolean = false,
    val optional: Boolean = false,
    val category: String = "",
    val estimated: Boolean = false,
    /** Part of the saved selection (or of the default one). */
    val checked: Boolean = false,
)

@Serializable
data class Staff090LibraryCountries(
    val countries: List<Staff090LibraryCodeName> = emptyList(),
    val country: String = "",
    @SerialName("country_name") val countryName: String = "",
    val region: String = "",
    val regions: List<Staff090LibraryCodeName> = emptyList(),
    val year: Int = 0,
    val years: Staff090LibraryRange? = null,
    @SerialName("data_years") val dataYears: List<Int>? = null,
    val saved: Boolean = false,
    @SerialName("schema_ready") val schemaReady: Boolean = true,
    val holidays: List<Staff090LibraryHoliday> = emptyList(),
) {
    val preset: Staff090LibraryHolidayPreset
        get() = Staff090LibraryHolidayPreset(
            country = country, countryName = countryName, region = region, regions = regions, year = year, years = years,
            dataYears = dataYears, saved = saved, schemaReady = schemaReady, holidays = holidays,
        )
}

@Serializable
data class Staff090LibraryCalendarLimits(
    @SerialName("yearly_span_days") val yearlySpanDays: Int = 363,
    @SerialName("once_span_days") val onceSpanDays: Int = 3660,
    @SerialName("ics_max_bytes") val icsMaxBytes: Int = 1_048_576,
)

@Serializable
data class Staff090LibraryPresetApplied(
    val message: String = "",
    @SerialName("holiday_preset") val holidayPreset: Staff090LibraryHolidayPreset = Staff090LibraryHolidayPreset(),
)

@Serializable
data class Staff090LibraryYearlySaved(
    val changed: Int = 0,
    @SerialName("yearly_closures") val yearlyClosures: List<Staff090LibraryClosure> = emptyList(),
)

@Serializable
data class Staff090LibraryIcsImported(
    val added: Int = 0,
    @SerialName("added_ids") val addedIds: List<Int> = emptyList(),
    val message: String = "",
)
