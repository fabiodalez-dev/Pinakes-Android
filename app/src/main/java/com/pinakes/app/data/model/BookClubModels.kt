package com.pinakes.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the Book Club plugin's mobile surface (`/api/v1/bookclub/…`).
 *
 * IMPORTANT: this surface uses a DIFFERENT envelope from the core Mobile API.
 * The Book Club controller returns `{"success":true,"data":{...}}` on success and
 * `{"success":false,"error":{"code","message"}}` on failure — no `meta`, no top-level
 * `data:null` on errors. [BookClubEnvelope] mirrors that; see `bookClubCall`.
 *
 * Field names mirror the live JSON emitted by
 * `storage/plugins/book-club/src/MobileApiController.php` (English snake_case).
 */
@Serializable
data class BookClubEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: BookClubError? = null,
)

@Serializable
data class BookClubError(
    val code: String = "unknown",
    val message: String = "",
)

// ---------- Discovery ----------
// GET /bookclub/health (no token). A 2xx means the plugin + mobile module are active.
@Serializable
data class BookClubHealth(
    val plugin: String = "",
    val enabled: Boolean = false,
    val version: String = "",
    val requires: List<String> = emptyList(),
    val endpoints: List<String> = emptyList(),
)

// ---------- Clubs list ----------
// GET /bookclub/clubs → { my_clubs, directory }
@Serializable
data class BookClubClubs(
    @SerialName("my_clubs") val myClubs: List<MyClub> = emptyList(),
    val directory: List<DirectoryClub> = emptyList(),
)

@Serializable
data class MyClub(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val color: String = "",
    val privacy: String = "",           // public | private | invite | hidden
    @SerialName("member_status") val memberStatus: String = "", // active | pending | banned
    val role: String = "member",        // owner | moderator | member | guest
) {
    val isPending: Boolean get() = memberStatus == "pending"
}

@Serializable
data class DirectoryClub(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val color: String = "",
    val privacy: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("max_members") val maxMembers: Int? = null,
) {
    val isFull: Boolean get() = maxMembers != null && memberCount >= maxMembers
}

// ---------- Club detail ----------
// GET /bookclub/clubs/{slug}
@Serializable
data class BookClubDetail(
    val club: ClubInfo = ClubInfo(),
    @SerialName("my_membership") val myMembership: Membership? = null,
    val workflow: List<WorkflowState> = emptyList(),
    val books: List<ClubBook> = emptyList(),
    val polls: List<ClubPoll> = emptyList(),
    val meetings: List<ClubMeeting> = emptyList(),
) {
    /** Guests are read-only: hide vote/propose/RSVP/progress actions. */
    val isActiveMember: Boolean get() = myMembership?.status == "active"
    val isGuest: Boolean get() = myMembership?.role == "guest"
    /** Can take part (vote, RSVP, track progress): active member that is not a guest. */
    val canParticipate: Boolean get() = isActiveMember && !isGuest
    val canJoin: Boolean
        get() = myMembership == null && (club.privacy == "public" || club.privacy == "private")
}

@Serializable
data class ClubInfo(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val rules: String = "",            // members-only; blank otherwise
    val color: String = "",
    val privacy: String = "",
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("max_members") val maxMembers: Int? = null,
)

@Serializable
data class Membership(
    val status: String = "",   // active | pending | banned
    val role: String = "member",
)

@Serializable
data class WorkflowState(
    val key: String = "",
    val label: String = "",
    val color: String = "",
)

@Serializable
data class ClubBook(
    val id: Int = 0,
    @SerialName("libro_id") val libroId: Int = 0,
    val title: String = "",
    val authors: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val state: String = "",
    @SerialName("state_label") val stateLabel: String = "",
    @SerialName("state_color") val stateColor: String = "#6b7280",
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("reading_starts") val readingStarts: String? = null,
    @SerialName("reading_ends") val readingEnds: String? = null,
    val motivation: String = "",
    @SerialName("my_progress") val myProgress: ReadingProgress? = null,
)

@Serializable
data class ReadingProgress(
    val percent: Int = 0,
    val finished: Boolean = false,
)

@Serializable
data class ClubPoll(
    val id: Int = 0,
    val title: String = "",
    val mode: String = "",         // simple | multi | weighted | stars | ranking | elimination
    val status: String = "",       // open | closed | ...
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("votes_per_member") val votesPerMember: Int = 1,
    @SerialName("voter_count") val voterCount: Int = 0,
    @SerialName("my_option_ids") val myOptionIds: List<Int> = emptyList(),
    val options: List<PollOption> = emptyList(),
    @SerialName("votable_in_app") val votableInApp: Boolean = false,
) {
    val isOpen: Boolean get() = status == "open"
    /** simple = single choice; multi/weighted = up to [votesPerMember]. */
    val maxChoices: Int get() = if (mode == "simple") 1 else votesPerMember.coerceAtLeast(1)
}

@Serializable
data class PollOption(
    val id: Int = 0,
    @SerialName("club_book_id") val clubBookId: Int = 0,
    val title: String = "",
    val score: Double = 0.0,
)

@Serializable
data class ClubMeeting(
    val id: Int = 0,
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String? = null,
    val kind: String = "",         // in_person | online | hybrid
    val status: String = "",       // scheduled | ...
    val location: String = "",
    @SerialName("video_url") val videoUrl: String = "",
    val agenda: String = "",
    @SerialName("book_title") val bookTitle: String = "",
    @SerialName("yes_count") val yesCount: Int = 0,
    val seats: Int? = null,
    @SerialName("my_rsvp") val myRsvp: String? = null, // yes | no | maybe | null
) {
    val isFull: Boolean get() = seats != null && yesCount >= seats
}

// ---------- Personal dashboard ----------
// GET /bookclub/me/dashboard → { clubs: [...] }
@Serializable
data class BookClubDashboard(
    val clubs: List<DashboardCard> = emptyList(),
)

@Serializable
data class DashboardCard(
    val club: DashboardClub = DashboardClub(),
    @SerialName("current_books") val currentBooks: List<DashboardBook> = emptyList(),
    @SerialName("next_meeting") val nextMeeting: DashboardMeeting? = null,
    @SerialName("open_polls") val openPolls: List<DashboardPoll> = emptyList(),
)

@Serializable
data class DashboardClub(
    val id: Int = 0,
    val slug: String = "",
    val name: String = "",
    val color: String = "",
    @SerialName("member_status") val memberStatus: String = "",
    val role: String = "member",
)

@Serializable
data class DashboardBook(
    val id: Int = 0,
    val title: String = "",
    val authors: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("reading_ends") val readingEnds: String? = null,
    @SerialName("my_progress") val myProgress: ReadingProgress? = null,
)

@Serializable
data class DashboardMeeting(
    val id: Int = 0,
    val title: String = "",
    @SerialName("starts_at") val startsAt: String = "",
)

@Serializable
data class DashboardPoll(
    val id: Int = 0,
    val title: String = "",
    @SerialName("closes_at") val closesAt: String? = null,
)

// ---------- Action request bodies ----------
@Serializable
data class ClubVoteRequest(val options: List<Int>)

@Serializable
data class ClubRsvpRequest(val response: String) // yes | no | maybe

@Serializable
data class ClubProgressRequest(
    val percent: Int,
    val finished: Boolean? = null,
)

@Serializable
data class ClubProposalRequest(
    @SerialName("libro_id") val libroId: Int,
    val motivation: String? = null,
)

// ---------- Action responses ----------
@Serializable
data class JoinResult(val status: String = "") // active | pending

@Serializable
data class VoteResult(
    @SerialName("poll_id") val pollId: Int = 0,
    val options: List<Int> = emptyList(),
)

@Serializable
data class RsvpResult(
    @SerialName("meeting_id") val meetingId: Int = 0,
    val response: String = "",
)

@Serializable
data class ProgressResult(
    @SerialName("club_book_id") val clubBookId: Int = 0,
    val percent: Int = 0,
    val finished: Boolean = false,
)
