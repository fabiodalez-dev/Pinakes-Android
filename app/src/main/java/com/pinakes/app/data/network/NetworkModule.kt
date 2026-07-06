package com.pinakes.app.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pinakes.app.BuildConfig
import com.pinakes.app.data.store.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the [PinakesApi] against a runtime base URL (the instance origin + `/api/v1/`).
 *
 * The base URL is per-instance and only known after onboarding, so the Retrofit instance is
 * (re)created whenever the instance URL changes. The bearer token is read live from
 * [SessionStore] by the [AuthInterceptor], so the same client survives login/logout.
 */
class NetworkModule(private val session: SessionStore) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        builder.build()
    }

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedRetrofit: Retrofit? = null

    @Volatile
    private var cachedApi: PinakesApi? = null

    @Volatile
    private var cachedBookClubApi: BookClubApi? = null

    /** Shared Retrofit for a given base URL, rebuilt only when the instance URL changes. */
    @Synchronized
    private fun retrofit(baseUrl: String?): Retrofit {
        val target = baseUrl ?: session.instanceUrl
        requireNotNull(target) { "No instance URL configured. Complete onboarding first." }
        val normalized = if (target.endsWith("/")) target else "$target/"
        val existing = cachedRetrofit
        if (existing != null && normalized == cachedBaseUrl) return existing

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        cachedBaseUrl = normalized
        cachedRetrofit = retrofit
        cachedApi = null
        cachedBookClubApi = null
        return retrofit
    }

    /**
     * Returns a [PinakesApi] bound to [baseUrl] (must end with `/api/v1/`). Falls back to the
     * persisted instance URL when [baseUrl] is null. Throws if no base URL is available — call
     * sites in onboarding pass an explicit URL; authenticated repositories rely on the stored one.
     */
    @Synchronized
    fun api(baseUrl: String? = null): PinakesApi {
        val retrofit = retrofit(baseUrl)
        return cachedApi ?: retrofit.create(PinakesApi::class.java).also { cachedApi = it }
    }

    /**
     * Returns the [BookClubApi] bound to the same instance base URL as [api]. The Book Club
     * plugin lives under `/api/v1/bookclub/…` and reuses the same bearer token.
     */
    @Synchronized
    fun bookClubApi(baseUrl: String? = null): BookClubApi {
        val retrofit = retrofit(baseUrl)
        return cachedBookClubApi ?: retrofit.create(BookClubApi::class.java).also { cachedBookClubApi = it }
    }

    /** Drop the cached Retrofit so the next [api] call rebuilds against a new instance URL. */
    @Synchronized
    fun invalidate() {
        cachedBaseUrl = null
        cachedRetrofit = null
        cachedApi = null
        cachedBookClubApi = null
    }

    companion object {
        /**
         * Derive the API base URL from a user-entered instance URL.
         * - prepends `https://` when no scheme is given
         * - strips a trailing slash / `/api/v1` if the user pasted it
         * - appends `/api/v1/`
         */
        fun deriveApiBaseUrl(rawInput: String): String {
            var s = rawInput.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://", ignoreCase = true) &&
                !s.startsWith("https://", ignoreCase = true)
            ) {
                s = "https://$s"
            }
            s = s.trimEnd('/')
            // Strip any user-supplied /api or /api/v1 suffix to avoid duplication.
            s = s.removeSuffix("/api/v1").removeSuffix("/api").trimEnd('/')
            return "$s/api/v1/"
        }

        /** Origin (scheme://host[:port]) for display, derived from the same raw input. */
        fun deriveOrigin(rawInput: String): String {
            val api = deriveApiBaseUrl(rawInput)
            return api.removeSuffix("/api/v1/").trimEnd('/')
        }

        /** HTTPS is required except for localhost / loopback addresses. */
        fun isTransportAllowed(apiBaseUrl: String): Boolean {
            val lower = apiBaseUrl.lowercase()
            if (lower.startsWith("https://")) return true
            return lower.startsWith("http://localhost") ||
                lower.startsWith("http://127.0.0.1") ||
                lower.startsWith("http://10.0.2.2") ||
                lower.startsWith("http://[::1]")
        }
    }
}
