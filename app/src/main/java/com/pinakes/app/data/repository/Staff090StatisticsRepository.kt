package com.pinakes.app.data.repository

import com.pinakes.app.data.model.Staff090Statistics
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.Staff090StatisticsApi
import com.pinakes.app.data.store.SessionStore
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Loan statistics (Mobile API 1.10.0), administrators and Staff. Every call goes through [staffCall]. */
@Singleton
class Staff090StatisticsRepository @Inject constructor(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.typedApi(Staff090StatisticsApi::class.java)

    suspend fun statistics(finesFrom: String?, finesTo: String?): ApiResult<Staff090Statistics> =
        session.staffCall { api.statistics(finesFrom.blankToNull(), finesTo.blankToNull()) }

    companion object {
        /** The fines CSV of the period, relative to `/api/v1/` for [StaffFilesRepository.download]. */
        fun finesCsvPath(from: String?, to: String?): String {
            val query = listOfNotNull(
                from.blankToNull()?.let { "fines_from=" + URLEncoder.encode(it, "UTF-8") },
                to.blankToNull()?.let { "fines_to=" + URLEncoder.encode(it, "UTF-8") },
            ).joinToString("&")
            return "staff/statistics/fines.csv" + if (query.isEmpty()) "" else "?$query"
        }
    }
}
