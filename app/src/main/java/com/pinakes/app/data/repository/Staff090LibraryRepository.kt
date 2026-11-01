package com.pinakes.app.data.repository

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
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.Staff090LibraryApi
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** A write's outcome plus the per-field messages of a 422 (or a 403 on admin-only fields), `meta.fields`. */
data class Staff090LibrarySaved<T>(val result: ApiResult<T>, val fields: Map<String, String> = emptyMap())

/**
 * Library settings, patron types, rules matrix and closures calendar (Mobile API 1.10.0).
 *
 * The server refuses an administrator-only action with the same 403 `forbidden` its staff role
 * gate uses, and these areas are offered to Staff too (they read the settings; types, rules
 * and calendar are administrator-only). So a 403 on an administrator-only call does not by
 * itself mean the staff role is gone: [adminCall] asks a route every staff member may read,
 * through [staffCall], which drops the role only when that one is refused as well.
 */
@Singleton
class Staff090LibraryRepository @Inject constructor(
    private val network: NetworkModule,
    private val session: SessionStore,
) {
    private val api get() = network.typedApi(Staff090LibraryApi::class.java)

    // ---------- Settings ----------

    suspend fun modules(): ApiResult<Staff090LibraryModules> = session.staffCall { api.modules() }
    suspend fun saveModules(body: JsonObject) = adminWrite { api.saveModules(body) }

    suspend fun loans(): ApiResult<Staff090LibraryLoans> = session.staffCall { api.loans() }
    suspend fun saveLoans(body: JsonObject) = adminWrite { api.saveLoans(body) }

    /** 404 `feature_disabled` for Staff while patron cards are off: the tab is then not shown. */
    suspend fun cards(): ApiResult<Staff090LibraryCards> = adminCall { api.cards() }
    suspend fun saveCards(body: JsonObject) = adminWrite { api.saveCards(body) }

    // ---------- Patron types and rules ----------

    suspend fun types(): ApiResult<Staff090LibraryTypes> = adminCall { api.types() }
    suspend fun createType(body: JsonObject, key: String) = adminWrite { api.createType(body, key) }
    suspend fun updateType(id: Int, body: JsonObject) = adminWrite { api.updateType(id, body) }
    suspend fun deleteType(id: Int): ApiResult<Staff090LibraryTypeDeleted> = adminCall { api.deleteType(id) }
    suspend fun setDefaultType(id: Int): ApiResult<Staff090LibraryPatronType> = adminCall { api.setDefaultType(id) }
    suspend fun moveType(id: Int, up: Boolean): ApiResult<Staff090LibraryTypes> =
        adminCall { api.moveType(id, buildJsonObject { put("direction", JsonPrimitive(if (up) "up" else "down")) }) }

    suspend fun rules(): ApiResult<Staff090LibraryRules> = adminCall { api.rules() }
    suspend fun saveRules(cells: JsonArray) = adminWrite { api.saveRules(buildJsonObject { put("cells", cells) }) }

    // ---------- Closures calendar ----------

    suspend fun calendar(month: String?, branchId: Int?): ApiResult<Staff090LibraryCalendar> = adminCall { api.calendar(month, branchId) }

    suspend fun saveWeekdays(days: List<Int>) =
        adminWrite { api.saveWeekdays(buildJsonObject { put("closed_weekdays", JsonArray(days.sorted().map(::JsonPrimitive))) }) }

    suspend fun createClosure(body: JsonObject, key: String) = adminWrite { api.createClosure(body, key) }
    suspend fun updateClosure(id: Int, body: JsonObject) = adminWrite { api.updateClosure(id, body) }
    suspend fun deleteClosure(id: Int): ApiResult<DeleteResult> = adminCall { api.deleteClosure(id) }

    suspend fun countries(country: String?, region: String?, year: Int?): ApiResult<Staff090LibraryCountries> =
        adminCall { api.countries(country.blankToNull(), region, year) }

    suspend fun applyPreset(country: String, region: String?, rules: List<String>): Staff090LibrarySaved<Staff090LibraryPresetApplied> =
        adminWrite {
            api.applyPreset(buildJsonObject {
                put("country", JsonPrimitive(country))
                put("region", JsonPrimitive(region.orEmpty()))
                put("rules", JsonArray(rules.map(::JsonPrimitive)))
            })
        }

    suspend fun saveYearly(active: Map<Int, Boolean>): Staff090LibrarySaved<Staff090LibraryYearlySaved> =
        adminWrite {
            api.saveYearly(buildJsonObject {
                put("closures", JsonArray(active.map { (id, on) -> buildJsonObject { put("id", JsonPrimitive(id)); put("active", JsonPrimitive(on)) } }))
            })
        }

    suspend fun createCustom(body: JsonObject, key: String) = adminWrite { api.createCustom(body, key) }
    suspend fun updateCustom(id: Int, body: JsonObject) = adminWrite { api.updateCustom(id, body) }
    suspend fun deleteCustom(id: Int): ApiResult<DeleteResult> = adminCall { api.deleteCustom(id) }
    suspend fun toggleCustom(id: Int): ApiResult<Staff090LibraryCustomHoliday> = adminCall { api.toggleCustom(id) }

    /** `ics_file` (at most 1 MB) and an optional branch; the server answers how many closures it added. */
    suspend fun importIcs(fileName: String, bytes: ByteArray, mime: String?, branchId: Int?): Staff090LibrarySaved<Staff090LibraryIcsImported> {
        val type = (mime ?: "text/calendar").toMediaTypeOrNull() ?: "text/calendar".toMediaType()
        val part = MultipartBody.Part.createFormData("ics_file", fileName, bytes.toRequestBody(type))
        val branch = branchId?.toString()?.toRequestBody("text/plain".toMediaType())
        return adminWrite { network.longTypedApi(Staff090LibraryApi::class.java).importIcs(part, branch) }
    }

    // ---------- Calls ----------

    /** An administrator-only call: see the class comment for its 403. */
    private suspend fun <T> adminCall(block: suspend () -> Envelope<T>): ApiResult<T> = withFields(block).result

    private suspend fun <T> adminWrite(block: suspend () -> Envelope<T>): Staff090LibrarySaved<T> = withFields(block)

    private suspend fun <T> withFields(block: suspend () -> Envelope<T>): Staff090LibrarySaved<T> {
        var errorBody: String? = null
        val res = apiCall {
            try {
                block()
            } catch (e: HttpException) {
                // Keep a copy of the error body for meta.fields, and hand apiCall an equal one.
                val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                errorBody = raw
                val original = e.response()
                val rebuilt = Response.error<Any>(
                    (raw ?: "").toResponseBody("application/json".toMediaType()),
                    original?.raw() ?: throw e,
                )
                throw HttpException(rebuilt)
            }
        }
        if (res is ApiResult.Failure && res.isForbidden()) {
            // Is the role still there? The settings modules route answers every staff member.
            session.staffCall { api.modules() }
        }
        return Staff090LibrarySaved(res, if (res is ApiResult.Failure) fieldErrors(errorBody) else emptyMap())
    }

    companion object {
        private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

        /** `meta.fields` of an error body as field → message (keys such as `cells.0.max_loans` kept as sent). */
        fun fieldErrors(body: String?): Map<String, String> {
            if (body.isNullOrBlank()) return emptyMap()
            val fields = runCatching { lenient.parseToJsonElement(body).jsonObject["meta"]?.jsonObject?.get("fields")?.jsonObject }.getOrNull()
                ?: return emptyMap()
            return fields.mapNotNull { (key, value) -> runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()?.let { key to it } }.toMap()
        }
    }
}
