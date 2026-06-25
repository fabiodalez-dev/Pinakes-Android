package com.pinakes.app.data.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pinakes.app.data.model.HealthPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Instance feature flags that gate the UI for CATALOGUE-ONLY MODE.
 *
 * Sourced from `/health` (`features` + `catalogue_mode`) and persisted so they survive process
 * death and remain available app-wide. Exposed as a reactive [StateFlow] so the navigation and
 * screens recompose when the server-side mode changes.
 *
 * Robustness: when the flags have never been fetched (first run, `/health` unreachable) the
 * defaults keep the operational app features enabled — the app must never lock the user out
 * because discovery failed. Public registration is different: it stays hidden until `/health`
 * explicitly advertises `registration_enabled=true`.
 */
data class InstanceFeatures(
    val catalogueMode: Boolean = false,
    val catalog: Boolean = true,
    val loans: Boolean = true,
    val reservations: Boolean = true,
    val wishlist: Boolean = true,
    val messages: Boolean = true,
    val notifications: Boolean = true,
    val push: Boolean = true,
    val registrationEnabled: Boolean = false,
) {
    /** Library tab (loans + reservations) is shown only when at least one of them is enabled. */
    val showLibrary: Boolean get() = loans || reservations

    /** Any borrow/reserve action available at all. */
    val canBorrow: Boolean get() = loans || reservations

    val showWishlist: Boolean get() = wishlist

    companion object {
        /** Safe default before/without `/health`: app features enabled, registration hidden. */
        val AllEnabled = InstanceFeatures()
    }
}

class FeatureStore(context: Context) {

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

    private val _features = MutableStateFlow(read())
    val features: StateFlow<InstanceFeatures> = _features.asStateFlow()

    /** Persist + publish the flags from a fresh `/health` payload. */
    fun update(health: HealthPayload) {
        val f = health.features
        val value = InstanceFeatures(
            catalogueMode = health.catalogueMode,
            catalog = f.catalog,
            loans = f.loans,
            reservations = f.reservations,
            wishlist = f.wishlist,
            messages = f.messages,
            notifications = f.notifications,
            push = f.push,
            registrationEnabled = health.registrationEnabled,
        )
        prefs.edit()
            .putBoolean(KEY_KNOWN, true)
            .putBoolean(KEY_CATALOGUE_MODE, value.catalogueMode)
            .putBoolean(KEY_CATALOG, value.catalog)
            .putBoolean(KEY_LOANS, value.loans)
            .putBoolean(KEY_RESERVATIONS, value.reservations)
            .putBoolean(KEY_WISHLIST, value.wishlist)
            .putBoolean(KEY_MESSAGES, value.messages)
            .putBoolean(KEY_NOTIFICATIONS, value.notifications)
            .putBoolean(KEY_PUSH, value.push)
            .putBoolean(KEY_REGISTRATION_ENABLED, value.registrationEnabled)
            .apply()
        _features.value = value
    }

    /** Reset to all-enabled (e.g. when forgetting the instance). */
    fun clear() {
        prefs.edit().clear().apply()
        _features.value = InstanceFeatures.AllEnabled
    }

    /** True once a real `/health` has been observed at least once. */
    fun isKnown(): Boolean = prefs.getBoolean(KEY_KNOWN, false)

    private fun read(): InstanceFeatures {
        if (!prefs.getBoolean(KEY_KNOWN, false)) return InstanceFeatures.AllEnabled
        return InstanceFeatures(
            catalogueMode = prefs.getBoolean(KEY_CATALOGUE_MODE, false),
            catalog = prefs.getBoolean(KEY_CATALOG, true),
            loans = prefs.getBoolean(KEY_LOANS, true),
            reservations = prefs.getBoolean(KEY_RESERVATIONS, true),
            wishlist = prefs.getBoolean(KEY_WISHLIST, true),
            messages = prefs.getBoolean(KEY_MESSAGES, true),
            notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            push = prefs.getBoolean(KEY_PUSH, true),
            registrationEnabled = prefs.getBoolean(KEY_REGISTRATION_ENABLED, false),
        )
    }

    companion object {
        private const val FILE_NAME = "pinakes_features_secure"
        private const val KEY_KNOWN = "known"
        private const val KEY_CATALOGUE_MODE = "catalogue_mode"
        private const val KEY_CATALOG = "f_catalog"
        private const val KEY_LOANS = "f_loans"
        private const val KEY_RESERVATIONS = "f_reservations"
        private const val KEY_WISHLIST = "f_wishlist"
        private const val KEY_MESSAGES = "f_messages"
        private const val KEY_NOTIFICATIONS = "f_notifications"
        private const val KEY_PUSH = "f_push"
        private const val KEY_REGISTRATION_ENABLED = "registration_enabled"
    }
}
