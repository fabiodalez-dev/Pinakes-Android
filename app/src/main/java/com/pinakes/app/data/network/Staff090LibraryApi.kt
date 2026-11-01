package com.pinakes.app.data.network

import com.pinakes.app.data.model.DeleteResult
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.model.Staff090LibraryCalendar
import com.pinakes.app.data.model.Staff090LibraryCards
import com.pinakes.app.data.model.Staff090LibraryClosure
import com.pinakes.app.data.model.Staff090LibraryCountries
import com.pinakes.app.data.model.Staff090LibraryCustomHoliday
import com.pinakes.app.data.model.Staff090LibraryIcsImported
import com.pinakes.app.data.model.Staff090LibraryLoans
import com.pinakes.app.data.model.Staff090LibraryModules
import com.pinakes.app.data.model.Staff090LibraryPatronType
import com.pinakes.app.data.model.Staff090LibraryPresetApplied
import com.pinakes.app.data.model.Staff090LibraryRules
import com.pinakes.app.data.model.Staff090LibraryTypeDeleted
import com.pinakes.app.data.model.Staff090LibraryTypes
import com.pinakes.app.data.model.Staff090LibraryYearlySaved
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Library settings of the Pinakes 0.9.0 modules (Mobile API 1.10.0): Settings → Modules,
 * Loans and Cards, patron types and the rules matrix, the closures calendar. Write bodies are
 * [JsonObject]s: a partial save names only the changed keys, and a JSON null is meaningful
 * ("inherit" for a type or a cell), which the app-wide Json (explicitNulls = false) would drop.
 */
interface Staff090LibraryApi {

    // ---------- Settings ----------

    @GET("staff/settings/modules")
    suspend fun modules(): Envelope<Staff090LibraryModules>

    @PUT("staff/settings/modules")
    suspend fun saveModules(@Body body: JsonObject): Envelope<Staff090LibraryModules>

    @GET("staff/settings/loans")
    suspend fun loans(): Envelope<Staff090LibraryLoans>

    @PUT("staff/settings/loans")
    suspend fun saveLoans(@Body body: JsonObject): Envelope<Staff090LibraryLoans>

    @GET("staff/settings/cards")
    suspend fun cards(): Envelope<Staff090LibraryCards>

    @PUT("staff/settings/cards")
    suspend fun saveCards(@Body body: JsonObject): Envelope<Staff090LibraryCards>

    // ---------- Patron types and rules ----------

    @GET("staff/circulation/types")
    suspend fun types(): Envelope<Staff090LibraryTypes>

    /** [key] is the submission's Idempotency-Key: a replay answers the type created the first time. */
    @POST("staff/circulation/types")
    suspend fun createType(@Body body: JsonObject, @Header("Idempotency-Key") key: String? = null): Envelope<Staff090LibraryPatronType>

    @GET("staff/circulation/types/{id}")
    suspend fun type(@Path("id") id: Int): Envelope<Staff090LibraryPatronType>

    @PUT("staff/circulation/types/{id}")
    suspend fun updateType(@Path("id") id: Int, @Body body: JsonObject): Envelope<Staff090LibraryPatronType>

    @DELETE("staff/circulation/types/{id}")
    suspend fun deleteType(@Path("id") id: Int): Envelope<Staff090LibraryTypeDeleted>

    @POST("staff/circulation/types/{id}/default")
    suspend fun setDefaultType(@Path("id") id: Int): Envelope<Staff090LibraryPatronType>

    /** Answers the whole list in its new order. */
    @POST("staff/circulation/types/{id}/move")
    suspend fun moveType(@Path("id") id: Int, @Body body: JsonObject): Envelope<Staff090LibraryTypes>

    @GET("staff/circulation/rules")
    suspend fun rules(): Envelope<Staff090LibraryRules>

    @PUT("staff/circulation/rules")
    suspend fun saveRules(@Body body: JsonObject): Envelope<Staff090LibraryRules>

    // ---------- Closures calendar ----------

    @GET("staff/calendar")
    suspend fun calendar(
        @Query("month") month: String? = null,
        @Query("branch_id") branchId: Int? = null,
    ): Envelope<Staff090LibraryCalendar>

    @PUT("staff/calendar/weekdays")
    suspend fun saveWeekdays(@Body body: JsonObject): Envelope<Staff090LibraryCalendar>

    @POST("staff/calendar/closures")
    suspend fun createClosure(@Body body: JsonObject, @Header("Idempotency-Key") key: String? = null): Envelope<Staff090LibraryClosure>

    @PUT("staff/calendar/closures/{id}")
    suspend fun updateClosure(@Path("id") id: Int, @Body body: JsonObject): Envelope<Staff090LibraryClosure>

    @DELETE("staff/calendar/closures/{id}")
    suspend fun deleteClosure(@Path("id") id: Int): Envelope<DeleteResult>

    @GET("staff/calendar/holidays/countries")
    suspend fun countries(
        @Query("country") country: String? = null,
        @Query("region") region: String? = null,
        @Query("year") year: Int? = null,
    ): Envelope<Staff090LibraryCountries>

    @POST("staff/calendar/holidays/preset")
    suspend fun applyPreset(@Body body: JsonObject): Envelope<Staff090LibraryPresetApplied>

    @PUT("staff/calendar/holidays/yearly")
    suspend fun saveYearly(@Body body: JsonObject): Envelope<Staff090LibraryYearlySaved>

    @POST("staff/calendar/custom-holidays")
    suspend fun createCustom(@Body body: JsonObject, @Header("Idempotency-Key") key: String? = null): Envelope<Staff090LibraryCustomHoliday>

    @PUT("staff/calendar/custom-holidays/{id}")
    suspend fun updateCustom(@Path("id") id: Int, @Body body: JsonObject): Envelope<Staff090LibraryCustomHoliday>

    @DELETE("staff/calendar/custom-holidays/{id}")
    suspend fun deleteCustom(@Path("id") id: Int): Envelope<DeleteResult>

    @POST("staff/calendar/custom-holidays/{id}/toggle")
    suspend fun toggleCustom(@Path("id") id: Int): Envelope<Staff090LibraryCustomHoliday>

    @Multipart
    @POST("staff/calendar/import-ics")
    suspend fun importIcs(@Part file: MultipartBody.Part, @Part("branch_id") branchId: RequestBody? = null): Envelope<Staff090LibraryIcsImported>
}
