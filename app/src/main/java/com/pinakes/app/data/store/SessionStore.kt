package com.pinakes.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Secure persistence for the session: the instance base URL and the bearer token.
 *
 * Backed by [EncryptedSharedPreferences] (AES-256, key in the Android Keystore). Exposes a
 * reactive [authState] so the app can route between onboarding/login and the main graph, and a
 * synchronous [token] accessor for the OkHttp [com.pinakes.app.data.network.AuthInterceptor].
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _authState = MutableStateFlow(readAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Instance base URL, normalized to end with `/api/v1/` (or null before onboarding). */
    val instanceUrl: String? get() = prefs.getString(KEY_INSTANCE_URL, null)

    /** Raw user-entered instance origin (no `/api/v1`), for display. */
    val instanceOrigin: String? get() = prefs.getString(KEY_INSTANCE_ORIGIN, null)

    /** Current bearer token, or null when logged out. Read synchronously by the interceptor. */
    val token: String? get() = prefs.getString(KEY_TOKEN, null)

    val libraryName: String? get() = prefs.getString(KEY_LIBRARY_NAME, null)

    /** Whether the committed instance was accepted over insecure (plain HTTP) transport. */
    val allowInsecureHttp: Boolean get() = prefs.getBoolean(KEY_ALLOW_INSECURE, false)

    /**
     * Transient "insecure HTTP allowed" flag for the ONBOARDING probe, before an instance is
     * committed (so [allowInsecureHttp] isn't persisted yet). Set by AuthRepository.discover()
     * from the onboarding toggle; read by the cleartext gate so the discovery request to a
     * plain-HTTP host isn't blocked. In-memory only — a fresh process re-derives it from the
     * next discover() call. The persisted flag takes over once the instance is committed.
     */
    @Volatile
    var pendingAllowInsecureHttp: Boolean = false

    /** A stable per-install device id used in the login request. Generated once. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    fun hasInstance(): Boolean = !instanceUrl.isNullOrBlank()

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    /**
     * Persist the chosen instance. [origin] is the user-entered URL; [apiBaseUrl] is the
     * derived `${origin}/api/v1/`. Clears any existing token (new instance ⇒ new session).
     */
    fun saveInstance(origin: String, apiBaseUrl: String, libraryName: String?, allowInsecure: Boolean = false) {
        prefs.edit()
            .putString(KEY_INSTANCE_ORIGIN, origin)
            .putString(KEY_INSTANCE_URL, apiBaseUrl)
            .putString(KEY_LIBRARY_NAME, libraryName)
            .putBoolean(KEY_ALLOW_INSECURE, allowInsecure)
            .remove(KEY_TOKEN)
            .apply()
        _authState.value = readAuthState()
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _authState.value = readAuthState()
    }

    /** Clear only the token (logout); keep the instance so the user lands on the login screen. */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _authState.value = readAuthState()
    }

    /** Wipe everything (forget instance) — back to onboarding. Device id is preserved. */
    fun clearAll() {
        val savedDevice = deviceId
        prefs.edit().clear().putString(KEY_DEVICE_ID, savedDevice).apply()
        pendingAllowInsecureHttp = false
        _authState.value = readAuthState()
    }

    private fun readAuthState(): AuthState = when {
        !hasInstance() -> AuthState.NeedsOnboarding
        !isLoggedIn() -> AuthState.NeedsLogin
        else -> AuthState.Authenticated
    }

    companion object {
        private const val FILE_NAME = "pinakes_session_secure"
        private const val KEY_INSTANCE_ORIGIN = "instance_origin"
        private const val KEY_INSTANCE_URL = "instance_api_url"
        private const val KEY_TOKEN = "bearer_token"
        private const val KEY_LIBRARY_NAME = "library_name"
        private const val KEY_ALLOW_INSECURE = "allow_insecure_http"
        private const val KEY_DEVICE_ID = "device_id"
    }
}

/** High-level auth state used to drive top-level navigation. */
enum class AuthState { NeedsOnboarding, NeedsLogin, Authenticated }
