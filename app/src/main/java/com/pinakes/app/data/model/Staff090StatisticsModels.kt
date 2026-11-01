package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

// The statistics page (/admin/statistics) for the app (Mobile API 1.10.0, tag
// `staff-statistics`). Each 0.9.0 section follows its module switch: a section whose module is
// off is absent from the answer (`visitors`, `fines_report`, entries of `sections`).

@Serializable
data class Staff090StatisticsOverview(
    @SerialName("books_available") val booksAvailable: Int = 0,
    @SerialName("books_on_loan") val booksOnLoan: Int = 0,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("overdue_loans") val overdueLoans: Int = 0,
    @SerialName("completed_loans") val completedLoans: Int = 0,
    /** Distinct readers with at least one loan ever. */
    val borrowers: Int = 0,
)

/** Loans of a month, `month` = YYYY-MM (months without loans are absent). */
@Serializable
data class Staff090StatisticsMonth(val month: String = "", val total: Int = 0)

@Serializable
data class Staff090StatisticsByStatus(val status: String = "", val label: String = "", val total: Int = 0)

@Serializable
data class Staff090StatisticsByMedia(
    @SerialName("media_type") val mediaType: String = "",
    val label: String = "",
    val total: Int = 0,
)

@Serializable
data class Staff090StatisticsTopBook(
    val id: Int = 0,
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("total_loans") val totalLoans: Int = 0,
    @SerialName("completed_loans") val completedLoans: Int = 0,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("overdue_loans") val overdueLoans: Int = 0,
)

@Serializable
data class Staff090StatisticsTopReader(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    @SerialName("user_type") val userType: String? = null,
    @SerialName("total_loans") val totalLoans: Int = 0,
    @SerialName("completed_loans") val completedLoans: Int = 0,
    @SerialName("active_loans") val activeLoans: Int = 0,
    @SerialName("overdue_loans") val overdueLoans: Int = 0,
)

/** A row of a module section: `value` is already formatted for display (a number or a text with the currency). */
@Serializable
data class Staff090StatisticsRow(val label: String = "", val value: JsonPrimitive? = null) {
    val display: String get() = value?.takeUnless { it is JsonNull }?.content.orEmpty()
}

/** A 0.9.0 table section: `patron-types`, `fines` or `stocktake`, each only while its module is on. */
@Serializable
data class Staff090StatisticsSection(
    val id: String = "",
    val title: String = "",
    val rows: List<Staff090StatisticsRow> = emptyList(),
    val note: String? = null,
)

@Serializable
data class Staff090StatisticsVisitorDay(val date: String = "", val members: Int = 0, val guests: Int = 0, val total: Int = 0)

/** Last [days] days of the visitor log (only while `visitor_log` is on). */
@Serializable
data class Staff090StatisticsVisitors(
    val days: Int = 0,
    val members: Int = 0,
    val guests: Int = 0,
    val total: Int = 0,
    val daily: List<Staff090StatisticsVisitorDay> = emptyList(),
)

@Serializable
data class Staff090StatisticsMoney(
    @SerialName("charged_cents") val chargedCents: Int = 0,
    @SerialName("collected_cents") val collectedCents: Int = 0,
    @SerialName("waived_cents") val waivedCents: Int = 0,
    @SerialName("reversed_cents") val reversedCents: Int = 0,
)

@Serializable
data class Staff090StatisticsFinesMonth(
    val month: String = "",
    @SerialName("charged_cents") val chargedCents: Int = 0,
    @SerialName("collected_cents") val collectedCents: Int = 0,
    @SerialName("waived_cents") val waivedCents: Int = 0,
    @SerialName("reversed_cents") val reversedCents: Int = 0,
) {
    val money: Staff090StatisticsMoney get() = Staff090StatisticsMoney(chargedCents, collectedCents, waivedCents, reversedCents)
}

/** Charged / collected / waived / reversed per month of the period (only while `fines` is on). */
@Serializable
data class Staff090StatisticsFinesReport(
    val from: String = "",
    val to: String = "",
    /** ISO 4217. */
    val currency: String? = null,
    val months: List<Staff090StatisticsFinesMonth> = emptyList(),
    val totals: Staff090StatisticsMoney = Staff090StatisticsMoney(),
)

/** `GET /staff/statistics`. */
@Serializable
data class Staff090Statistics(
    val overview: Staff090StatisticsOverview = Staff090StatisticsOverview(),
    @SerialName("loans_by_month") val loansByMonth: List<Staff090StatisticsMonth> = emptyList(),
    @SerialName("loans_by_status") val loansByStatus: List<Staff090StatisticsByStatus> = emptyList(),
    @SerialName("loans_by_media_type") val loansByMediaType: List<Staff090StatisticsByMedia> = emptyList(),
    @SerialName("top_books") val topBooks: List<Staff090StatisticsTopBook> = emptyList(),
    @SerialName("top_readers") val topReaders: List<Staff090StatisticsTopReader> = emptyList(),
    val sections: List<Staff090StatisticsSection> = emptyList(),
    val visitors: Staff090StatisticsVisitors? = null,
    @SerialName("fines_report") val finesReport: Staff090StatisticsFinesReport? = null,
)
