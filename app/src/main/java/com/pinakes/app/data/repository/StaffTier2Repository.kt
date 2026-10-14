package com.pinakes.app.data.repository

import com.pinakes.app.data.model.AddMemberRequest
import com.pinakes.app.data.model.BookWorkLink
import com.pinakes.app.data.model.ClubMember
import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.IssueWrite
import com.pinakes.app.data.model.MemberUpdate
import com.pinakes.app.data.model.PeriodicalScan
import com.pinakes.app.data.model.StaffClub
import com.pinakes.app.data.model.StaffIssue
import com.pinakes.app.data.model.StaffSubscription
import com.pinakes.app.data.model.StaffWork
import com.pinakes.app.data.model.StaffYear
import com.pinakes.app.data.model.WorkExpression
import com.pinakes.app.data.model.YearWrite
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Tier 2 staff bridges (Mobile API 1.7.0). Every call goes through [staffCall]. */
class StaffTier2Repository(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.staffTier2Api()

    // ---- periodicals desk ----

    /** Mastheads, years and issues come from the public periodicals API (same token). */
    suspend fun periodicals(query: String?, cursor: String?): ApiResult<StaffPage<com.pinakes.app.data.model.PeriodicalSummary>> =
        session.staffCall { network.periodicalsApi().periodicals(q = query.blankToNull(), cursor = cursor, limit = 30) }.toPage()

    suspend fun periodical(id: Int): ApiResult<com.pinakes.app.data.model.PeriodicalDetail> = session.staffCall { network.periodicalsApi().periodical(id) }

    suspend fun yearIssues(yearId: Int): ApiResult<List<com.pinakes.app.data.model.PeriodicalIssue>> =
        session.staffCall { network.periodicalsApi().yearIssues(yearId) }

    suspend fun scanLookup(code: String): ApiResult<PeriodicalScan> = session.staffCall { api.scanLookup(code.trim()) }
    suspend fun receiveIssue(id: Int): ApiResult<StaffIssue> = session.staffCall { api.receiveIssue(id) }
    suspend fun claimIssue(id: Int): ApiResult<StaffIssue> = session.staffCall { api.claimIssue(id) }

    suspend fun addIssue(yearId: Int, number: String, date: String?, status: String?): ApiResult<StaffIssue> =
        session.staffCall { api.addIssue(yearId, IssueWrite(number.trim(), date.blankToNull(), status.blankToNull())) }

    suspend fun addYear(periodicalId: Int, year: Int, volume: String?, bound: Boolean): ApiResult<StaffYear> =
        session.staffCall { api.addYear(periodicalId, YearWrite(year, volume.blankToNull(), bound)) }

    suspend fun subscriptions(periodicalId: Int?): ApiResult<List<StaffSubscription>> = session.staffCall { api.subscriptions(periodicalId) }

    suspend fun saveSubscription(id: Int?, body: JsonObject): ApiResult<StaffSubscription> =
        session.staffCall { if (id == null || id <= 0) api.createSubscription(body) else api.updateSubscription(id, body) }

    // ---- book clubs ----

    suspend fun clubs(): ApiResult<List<StaffClub>> = session.staffCall { api.clubs() }
    suspend fun club(id: Int): ApiResult<StaffClub> = session.staffCall { api.club(id) }

    suspend fun saveClub(id: Int?, body: JsonObject): ApiResult<StaffClub> =
        session.staffCall { if (id == null || id <= 0) api.createClub(body) else api.updateClub(id, body) }

    suspend fun deleteClub(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteClub(id) }
    suspend fun saveWorkflow(id: Int, body: JsonObject): ApiResult<StaffClub> = session.staffCall { api.saveWorkflow(id, body) }
    suspend fun addMember(id: Int, userId: Int, role: String): ApiResult<ClubMember> = session.staffCall { api.addMember(id, AddMemberRequest(userId, role)) }

    suspend fun updateMember(id: Int, memberId: Int, status: String? = null, role: String? = null): ApiResult<ClubMember> =
        session.staffCall { api.updateMember(id, memberId, MemberUpdate(status, role)) }

    // ---- FRBR works ----

    suspend fun works(query: String?): ApiResult<List<StaffWork>> = session.staffCall { api.works(query.blankToNull(), 100) }
    suspend fun work(id: Int): ApiResult<StaffWork> = session.staffCall { api.work(id) }

    suspend fun saveWork(id: Int?, body: JsonObject): ApiResult<StaffWork> =
        session.staffCall { if (id == null || id <= 0) api.createWork(body) else api.updateWork(id, body) }

    suspend fun deleteWork(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteWork(id) }

    suspend fun saveExpression(workId: Int, id: Int?, body: JsonObject): ApiResult<WorkExpression> =
        session.staffCall { if (id == null || id <= 0) api.createExpression(workId, body) else api.updateExpression(id, body) }

    suspend fun deleteExpression(id: Int): ApiResult<DeleteResult> = session.staffCall { api.deleteExpression(id) }

    /** Link [bookId] to [workId], or unlink it with null. */
    suspend fun attachBook(bookId: Int, workId: Int?): ApiResult<BookWorkLink> = session.staffCall {
        api.attachBook(bookId, buildJsonObject { put("work_id", workId?.let { JsonPrimitive(it) } ?: JsonNull) })
    }
}
