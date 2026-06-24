package com.pinakes.app.data.repository

import android.os.Build
import com.pinakes.app.data.model.ForgotRequest
import com.pinakes.app.data.model.HealthPayload
import com.pinakes.app.data.model.LoginRequest
import com.pinakes.app.data.model.RegisterRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore

/**
 * Onboarding + authentication: instance discovery (`/health`), login (persists token + URL),
 * registration, password recovery and logout (revokes the current token).
 */
class AuthRepository(
    private val network: NetworkModule,
    private val session: SessionStore,
    private val features: FeatureStore,
) {

    /**
     * Discovery against a candidate instance URL. Does not persist anything — the caller decides
     * to continue based on [HealthPayload.appAccessEnabled] and the transport warning.
     */
    suspend fun discover(rawInstanceUrl: String): ApiResult<HealthDiscovery> {
        val apiBaseUrl = NetworkModule.deriveApiBaseUrl(rawInstanceUrl)
        val origin = NetworkModule.deriveOrigin(rawInstanceUrl)
        val api = network.api(apiBaseUrl)
        return when (val res = apiCall { api.health() }) {
            is ApiResult.Success -> ApiResult.Success(
                HealthDiscovery(
                    health = res.data,
                    origin = origin,
                    apiBaseUrl = apiBaseUrl,
                    insecureTransport = res.meta?.warning == "insecure_transport" ||
                        res.meta?.https == false,
                    transportAllowed = NetworkModule.isTransportAllowed(apiBaseUrl),
                ),
                res.meta,
            )
            is ApiResult.Failure -> res
        }
    }

    /** Persist the discovered instance so subsequent calls use it. */
    fun commitInstance(discovery: HealthDiscovery) {
        session.saveInstance(
            origin = discovery.origin,
            apiBaseUrl = discovery.apiBaseUrl,
            libraryName = discovery.health.name,
        )
        // Capture the instance feature flags from discovery so the UI is gated immediately.
        features.update(discovery.health)
        network.invalidate()
    }

    /**
     * Best-effort re-fetch of `/health` for the committed instance, to pick up a server-side
     * CATALOGUE-ONLY MODE change at app start / after login. On success the [FeatureStore] is
     * updated (reactively gating the UI); on failure the last-known flags are kept untouched.
     */
    suspend fun refreshHealth() {
        if (!session.hasInstance()) return
        when (val res = apiCall { network.api().health() }) {
            is ApiResult.Success -> features.update(res.data)
            is ApiResult.Failure -> { /* keep last-known flags; never lock the user out */ }
        }
    }

    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val api = network.api()
        val request = LoginRequest(
            email = email.trim(),
            password = password,
            deviceName = deviceName(),
            deviceId = session.deviceId,
            platform = "android",
        )
        return when (val res = apiCall { api.login(request) }) {
            is ApiResult.Success -> {
                session.saveToken(res.data.token)
                ApiResult.Success(Unit, res.meta)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun register(
        nome: String,
        cognome: String,
        email: String,
        telefono: String,
        indirizzo: String,
        password: String,
        passwordConfirm: String,
        privacyAcceptance: Boolean,
    ): ApiResult<Unit> {
        val api = network.api()
        return apiCall {
            api.register(
                RegisterRequest(
                    nome.trim(), cognome.trim(), email.trim(),
                    telefono.trim(), indirizzo.trim(),
                    password, passwordConfirm, privacyAcceptance,
                )
            )
        }
    }

    suspend fun forgotPassword(email: String): ApiResult<Unit> {
        val api = network.api()
        return apiCall { api.forgotPassword(ForgotRequest(email.trim())) }
    }

    /** Revoke the current token server-side, then clear it locally regardless of the result. */
    suspend fun logout(): ApiResult<Unit> {
        val api = network.api()
        val result = apiCall { api.logout() }
        session.clearToken()
        return result
    }

    /** Forget the instance entirely (back to onboarding). */
    fun forgetInstance() {
        session.clearAll()
        features.clear()
        network.invalidate()
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android"
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android device" }
    }
}

/** Result of `/health` discovery, ready to commit to [SessionStore]. */
data class HealthDiscovery(
    val health: HealthPayload,
    val origin: String,
    val apiBaseUrl: String,
    val insecureTransport: Boolean,
    val transportAllowed: Boolean,
)
