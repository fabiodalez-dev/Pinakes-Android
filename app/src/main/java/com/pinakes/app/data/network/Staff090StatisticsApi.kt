package com.pinakes.app.data.network

import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090Statistics
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The statistics page for the app (Mobile API 1.10.0). The fines CSV
 * (`staff/statistics/fines.csv`, only while `fines` is on) goes through [StaffContentApi.download].
 */
interface Staff090StatisticsApi {

    /** [finesFrom] / [finesTo]: YYYY-MM-DD; the server defaults to the last twelve months and swaps a reversed range. */
    @GET("staff/statistics")
    suspend fun statistics(
        @Query("fines_from") finesFrom: String? = null,
        @Query("fines_to") finesTo: String? = null,
    ): Envelope<Staff090Statistics>
}
