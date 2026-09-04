package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the Periodicals ("Emeroteca") plugin's mobile surface (`/api/v1/periodicals/…`).
 *
 * Unlike the Book Club plugin, this surface uses the CORE `{data, meta, error}` envelope
 * ([Envelope]) — so every call goes through the shared `apiCall`, and list pagination reads
 * `meta.next_cursor` exactly like the catalog search.
 *
 * The section is read-only (consultation of the periodicals archive): no write endpoints.
 * All fields mirror the server's English snake_case JSON; anything the server may omit or
 * null is declared nullable with a default so a lean payload can never fail decoding.
 */

/** GET periodicals/health → `{data:{status:"ok"}}` when the plugin is on (404 when off). */
@Serializable
data class PeriodicalsHealth(
    val status: String = "",
)

// ---------- Mastheads list ----------
// GET periodicals?q=&type=&cursor=&limit= → { data: [PeriodicalSummary], meta: {next_cursor,…} }
@Serializable
data class PeriodicalSummary(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val issn: String? = null,
    /** rivista | giornale | magazine | bollettino | fanzine */
    val type: String = "",
    /** quotidiano | settimanale | quindicinale | mensile | bimestrale | trimestrale | semestrale | annuale | irregolare */
    val frequency: String? = null,
    val publisher: PeriodicalPublisher? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("year_start") val yearStart: Int? = null,
    @SerialName("year_end") val yearEnd: Int? = null,
    @SerialName("collection_status") val collectionStatus: String? = null,
    @SerialName("years_count") val yearsCount: Int = 0,
    @SerialName("issues_count") val issuesCount: Int = 0,
)

@Serializable
data class PeriodicalPublisher(
    val id: Int = 0,
    val name: String = "",
)

// ---------- Masthead detail ----------
// GET periodicals/{id} → summary fields + description/place/language/holdings + years
@Serializable
data class PeriodicalDetail(
    val id: Int = 0,
    val title: String = "",
    val subtitle: String? = null,
    val issn: String? = null,
    val type: String = "",
    val frequency: String? = null,
    val publisher: PeriodicalPublisher? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("year_start") val yearStart: Int? = null,
    @SerialName("year_end") val yearEnd: Int? = null,
    @SerialName("collection_status") val collectionStatus: String? = null,
    @SerialName("years_count") val yearsCount: Int = 0,
    @SerialName("issues_count") val issuesCount: Int = 0,
    val description: String? = null,
    val place: String? = null,
    val language: String? = null,
    /** Free-text consistency note ("1946-1998, lacune 1953-1955", …). */
    val holdings: String? = null,
    val years: List<PeriodicalYear> = emptyList(),
)

@Serializable
data class PeriodicalYear(
    val id: Int = 0,
    val year: Int = 0,
    val volume: String? = null,
    /** True when the annata is bound into a single physical volume. */
    val bound: Boolean = false,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("issues_count") val issuesCount: Int = 0,
    @SerialName("owned_count") val ownedCount: Int = 0,
)

// ---------- Issues of a year ----------
// GET periodicals/years/{id}/issues → { data: [PeriodicalIssue] }
@Serializable
data class PeriodicalIssue(
    val id: Int = 0,
    val number: String? = null,
    val sequence: String? = null,
    val title: String? = null,
    @SerialName("cover_date") val coverDate: String? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    val pages: Int? = null,
    /** posseduto | mancante | danneggiato | in_restauro | smarrito | atteso */
    val status: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("has_public_pdf") val hasPublicPdf: Boolean = false,
)

// ---------- Issue detail ----------
// GET periodicals/issues/{id} → issue + pdf_url (only when public) + context + spoglio
@Serializable
data class PeriodicalIssueDetail(
    val id: Int = 0,
    val number: String? = null,
    val sequence: String? = null,
    val title: String? = null,
    @SerialName("cover_date") val coverDate: String? = null,
    @SerialName("publication_date") val publicationDate: String? = null,
    val pages: Int? = null,
    val status: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    /** Only present when the digitised PDF is public; null → hide the "Open PDF" action. */
    @SerialName("pdf_url") val pdfUrl: String? = null,
    val masthead: IssueMasthead? = null,
    val year: IssueYear? = null,
    val articles: List<IssueArticle> = emptyList(),
) {
    /** The PDF action is offered ONLY for a non-blank public URL (server-authoritative). */
    val canOpenPdf: Boolean get() = !pdfUrl.isNullOrBlank()
}

@Serializable
data class IssueMasthead(
    val id: Int = 0,
    val title: String = "",
)

@Serializable
data class IssueYear(
    val id: Int = 0,
    val year: Int = 0,
    val volume: String? = null,
)

@Serializable
data class IssueArticle(
    val title: String = "",
    val authors: String? = null,
    @SerialName("page_start") val pageStart: Int? = null,
    @SerialName("page_end") val pageEnd: Int? = null,
    val type: String? = null,
)
