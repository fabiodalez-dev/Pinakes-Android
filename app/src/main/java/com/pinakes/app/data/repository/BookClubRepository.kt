package com.pinakes.app.data.repository

import com.pinakes.app.data.model.BookClubClubs
import com.pinakes.app.data.model.BookClubDashboard
import com.pinakes.app.data.model.BookClubDetail
import com.pinakes.app.data.model.ClubProgressRequest
import com.pinakes.app.data.model.ClubProposalRequest
import com.pinakes.app.data.model.ClubRsvpRequest
import com.pinakes.app.data.model.ClubVoteRequest
import com.pinakes.app.data.model.JoinResult
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.bookClubCall
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore

/**
 * Book Club plugin surface (`/api/v1/bookclub/…`): availability discovery, reads (clubs,
 * detail, personal dashboard) and the write actions (join, propose, vote, RSVP, progress).
 *
 * Availability is probed alongside every `/health` refresh and stored as an
 * [com.pinakes.app.data.store.InstanceFeatures] flag, so the UI only shows the section when
 * the plugin's `mobile` module is active for this instance.
 */
class BookClubRepository(
    private val network: NetworkModule,
    private val features: FeatureStore,
    private val session: SessionStore,
) {

    /**
     * Probe `GET /bookclub/health` (public, no token).
     * Returns true on 2xx (plugin on), false on an explicit 404 (plugin off), and null on
     * any other failure — the caller keeps the last-known flag rather than hiding a working
     * section on a network blip.
     */
    suspend fun probeAvailability(): Boolean? =
        when (val res = bookClubCall { network.bookClubApi().health() }) {
            is ApiResult.Success -> true
            is ApiResult.Failure ->
                if (res.httpStatus == 404 || res.code == ErrorCodes.NOT_FOUND) false else null
        }

    /**
     * Apply a probe result, guarded against instance switches: a late response from the
     * previous instance (the user tapped "change library" mid-flight) must not resurrect
     * or clobber the flag of the instance now configured. Null keeps the last-known value.
     */
    fun applyAvailability(available: Boolean?, probedInstanceUrl: String?) {
        if (available == null) return
        if (probedInstanceUrl == null || session.instanceUrl != probedInstanceUrl) return
        features.setBookClubAvailable(available)
    }

    suspend fun clubs(): ApiResult<BookClubClubs> =
        bookClubCall { network.bookClubApi().clubs() }

    suspend fun clubDetail(slug: String): ApiResult<BookClubDetail> =
        bookClubCall { network.bookClubApi().clubDetail(slug) }

    suspend fun dashboard(): ApiResult<BookClubDashboard> =
        bookClubCall { network.bookClubApi().dashboard() }

    suspend fun join(slug: String): ApiResult<JoinResult> =
        bookClubCall { network.bookClubApi().join(slug) }

    suspend fun propose(slug: String, libroId: Int, motivation: String?): ApiResult<Unit> =
        bookClubCall { network.bookClubApi().propose(slug, ClubProposalRequest(libroId, motivation)) }

    suspend fun vote(slug: String, pollId: Int, optionIds: List<Int>): ApiResult<Unit> =
        bookClubCall { network.bookClubApi().vote(slug, pollId, ClubVoteRequest(optionIds)) }

    suspend fun rsvp(slug: String, meetingId: Int, response: String): ApiResult<Unit> =
        bookClubCall { network.bookClubApi().rsvp(slug, meetingId, ClubRsvpRequest(response)) }

    suspend fun progress(slug: String, clubBookId: Int, percent: Int, finished: Boolean?): ApiResult<Unit> =
        bookClubCall { network.bookClubApi().progress(slug, clubBookId, ClubProgressRequest(percent, finished)) }

    /** Web URL of a poll page — the deep-link target for ballots the app can't render. */
    fun pollWebUrl(slug: String, pollId: Int): String =
        "${session.instanceOrigin.orEmpty()}/book-club/$slug/polls/$pollId"
}
