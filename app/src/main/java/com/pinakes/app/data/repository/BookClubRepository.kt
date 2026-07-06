package com.pinakes.app.data.repository

import com.pinakes.app.data.model.BookClubClubs
import com.pinakes.app.data.model.BookClubDashboard
import com.pinakes.app.data.model.BookClubDetail
import com.pinakes.app.data.model.ClubProgressRequest
import com.pinakes.app.data.model.ClubProposalRequest
import com.pinakes.app.data.model.ClubRsvpRequest
import com.pinakes.app.data.model.ClubVoteRequest
import com.pinakes.app.data.model.JoinResult
import com.pinakes.app.data.model.ProgressResult
import com.pinakes.app.data.model.RsvpResult
import com.pinakes.app.data.model.VoteResult
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.bookClubCall
import com.pinakes.app.data.store.BookClubStore
import com.pinakes.app.data.store.SessionStore

/**
 * Book Club plugin surface (`/api/v1/bookclub/…`): availability discovery, reads (clubs,
 * detail, personal dashboard) and the write actions (join, propose, vote, RSVP, progress).
 *
 * Availability is probed once per `/health` refresh and cached in [BookClubStore] so the app
 * only shows the section when the plugin's `mobile` module is active for this instance.
 */
class BookClubRepository(
    private val network: NetworkModule,
    private val store: BookClubStore,
    private val session: SessionStore,
) {

    /**
     * Re-probe `GET /bookclub/health`. A 2xx flips the section on; an explicit 404 flips it off;
     * a network/other failure keeps the last-known flag (never hide a working section on a blip).
     * No token is required, so this is safe to call at app start alongside the core health refresh.
     */
    suspend fun refreshAvailability() {
        if (!session.hasInstance()) return
        when (val res = bookClubCall { network.bookClubApi().health() }) {
            is ApiResult.Success -> store.setAvailable(true)
            is ApiResult.Failure -> if (res.httpStatus == 404 || res.code == ErrorCodes.NOT_FOUND) {
                store.setAvailable(false)
            }
        }
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

    suspend fun vote(slug: String, pollId: Int, optionIds: List<Int>): ApiResult<VoteResult> =
        bookClubCall { network.bookClubApi().vote(slug, pollId, ClubVoteRequest(optionIds)) }

    suspend fun rsvp(slug: String, meetingId: Int, response: String): ApiResult<RsvpResult> =
        bookClubCall { network.bookClubApi().rsvp(slug, meetingId, ClubRsvpRequest(response)) }

    suspend fun progress(slug: String, clubBookId: Int, percent: Int, finished: Boolean?): ApiResult<ProgressResult> =
        bookClubCall { network.bookClubApi().progress(slug, clubBookId, ClubProgressRequest(percent, finished)) }

    /** Forget the availability flag (instance forgotten). */
    fun clearAvailability() = store.clear()

    /** Origin (scheme://host[:port]) of the instance, for deep-linking web-only flows. */
    fun webBaseUrl(): String = session.instanceOrigin.orEmpty()
}
