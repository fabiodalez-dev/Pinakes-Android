package com.pinakes.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the current instance exposes the Book Club plugin's mobile surface.
 *
 * Sourced from `GET /api/v1/bookclub/health` (2xx → available, 404 → off) and persisted so the
 * Profile entry point doesn't flicker between launches. Like `registration_enabled` in
 * [FeatureStore], the default is DISABLED until discovery confirms it — a first-run or offline
 * probe keeps the section hidden rather than showing a dead entry.
 */
class BookClubStore(context: Context) {

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

    private val _available = MutableStateFlow(prefs.getBoolean(KEY_AVAILABLE, false))
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun setAvailable(value: Boolean) {
        prefs.edit().putBoolean(KEY_AVAILABLE, value).apply()
        _available.value = value
    }

    /** Reset when the instance is forgotten. */
    fun clear() {
        prefs.edit().clear().apply()
        _available.value = false
    }

    companion object {
        private const val FILE_NAME = "pinakes_bookclub_secure"
        private const val KEY_AVAILABLE = "available"
    }
}
