package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Mobile API 1.7.0 Tier 2 bridges: the periodicals desk (emeroteca plugin,
// /periodicals/staff), book club management (book-club plugin, /bookclub/staff) and FRBR
// works (frbr-lrm plugin, /staff/works). All answer the core envelope; every field has a default.

// ---------- Periodicals desk ----------

/** `GET /periodicals/staff/scan-lookup`: `match` is `none` | `issue` | `title` | `ambiguous`. */
@Serializable
data class PeriodicalScan(
    val found: Boolean = false,
    val match: String = "none",
    /** `receive` when the matched issue is expected or claimed. */
    val action: String? = null,
    val issue: ScannedIssue? = null,
    val title: ScannedTitle? = null,
    val matches: Int? = null,
    val message: String? = null,
)

@Serializable
data class ScannedIssue(
    val id: Int = 0,
    @SerialName("numero") val number: String = "",
    @SerialName("stato") val status: String = "",
    @SerialName("annata_id") val yearId: Int = 0,
    @SerialName("anno") val year: Int = 0,
    @SerialName("testata_id") val periodicalId: Int = 0,
    @SerialName("titolo") val title: String = "",
)

@Serializable
data class ScannedTitle(val id: Int = 0, @SerialName("titolo") val title: String = "")

/** An issue as the desk writes answer it. `status`: posseduto | mancante | atteso | smarrito | reclamato | scartato. */
@Serializable
data class StaffIssue(
    val id: Int = 0,
    @SerialName("periodical_id") val periodicalId: Int = 0,
    val periodical: String = "",
    @SerialName("year_id") val yearId: Int = 0,
    val year: Int = 0,
    val number: String = "",
    val date: String? = null,
    val status: String = "",
    @SerialName("claimed_at") val claimedAt: String? = null,
    val claims: Int = 0,
)

@Serializable
data class StaffYear(
    val id: Int = 0,
    @SerialName("periodical_id") val periodicalId: Int = 0,
    val year: Int = 0,
    val volume: String? = null,
    val bound: Boolean = false,
)

@Serializable
data class IssueWrite(val number: String, val date: String? = null, val status: String? = null)

@Serializable
data class YearWrite(val year: Int, val volume: String? = null, val bound: Boolean = false)

@Serializable
data class StaffSubscription(
    val id: Int = 0,
    @SerialName("periodical_id") val periodicalId: Int = 0,
    val periodical: String = "",
    val supplier: String = "",
    val cost: Double? = null,
    val currency: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
    val active: Boolean = true,
    val notes: String? = null,
)

// ---------- Book clubs ----------

@Serializable
data class StaffClub(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val rules: String = "",
    val color: String = "",
    /** public | private | invite | hidden */
    val privacy: String = "public",
    @SerialName("max_members") val maxMembers: Int? = null,
    val active: Boolean = true,
    @SerialName("moderate_proposals") val moderateProposals: Boolean = false,
    @SerialName("max_proposals_per_member") val maxProposalsPerMember: Int? = null,
    val modules: List<String> = emptyList(),
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val workflow: List<ClubState> = emptyList(),
    val members: List<ClubMember> = emptyList(),
)

/** A workflow state; `flags` is an object of booleans, or an empty JSON array when none is set. */
@Serializable
data class ClubState(
    val key: String = "",
    val label: String = "",
    val color: String = "#6b7280",
    val flags: JsonElement? = null,
) {
    fun flag(name: String): Boolean = ((flags as? JsonObject)?.get(name) as? JsonPrimitive)?.booleanOrNull == true
}

@Serializable
data class ClubMember(
    val id: Int = 0,
    @SerialName("user_id") val userId: Int = 0,
    val name: String = "",
    val email: String = "",
    /** owner | moderator | member | guest */
    val role: String = "",
    @SerialName("role_name") val roleName: String = "",
    /** pending | active | suspended | left | banned */
    val status: String = "",
)

@Serializable
data class AddMemberRequest(@SerialName("user_id") val userId: Int, val role: String)

@Serializable
data class MemberUpdate(val status: String? = null, val role: String? = null)

// ---------- FRBR works ----------

@Serializable
data class StaffWork(
    val id: Int = 0,
    @SerialName("uniform_title") val uniformTitle: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("main_author_id") val mainAuthorId: Int? = null,
    @SerialName("main_author_name") val mainAuthorName: String? = null,
    @SerialName("created_from") val createdFrom: Int? = null,
    @SerialName("created_to") val createdTo: Int? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("viaf_work_id") val viafWorkId: String? = null,
    @SerialName("wikidata_id") val wikidataId: String? = null,
    val notes: String? = null,
    val slug: String? = null,
    @SerialName("editions_count") val editionsCount: Int? = null,
    val editions: List<WorkEdition> = emptyList(),
    val expressions: List<WorkExpression> = emptyList(),
)

@Serializable
data class WorkEdition(
    val id: Int = 0,
    val title: String = "",
    val year: Int? = null,
    val isbn13: String? = null,
    @SerialName("expression_id") val expressionId: Int? = null,
)

/** `type`: testo | traduzione | revisione | adattamento | edizione_critica | audio | altro. */
@Serializable
data class WorkExpression(
    val id: Int = 0,
    @SerialName("work_id") val workId: Int = 0,
    val type: String? = null,
    val language: String? = null,
    val title: String? = null,
    val year: Int? = null,
    @SerialName("translator_id") val translatorId: Int? = null,
    @SerialName("curator_id") val curatorId: Int? = null,
    @SerialName("reviser_id") val reviserId: Int? = null,
    val notes: String? = null,
)

@Serializable
data class BookWorkLink(
    @SerialName("book_id") val bookId: Int = 0,
    val work: IdTitleWork? = null,
    @SerialName("expression_id") val expressionId: Int? = null,
)

@Serializable
data class IdTitleWork(val id: Int = 0, @SerialName("uniform_title") val uniformTitle: String = "")
