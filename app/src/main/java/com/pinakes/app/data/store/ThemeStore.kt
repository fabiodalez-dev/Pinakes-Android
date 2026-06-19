package com.pinakes.app.data.store

import android.content.Context
import android.content.SharedPreferences
import com.pinakes.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight persistence for the user's theme preference (Light / Dark / System).
 *
 * Mirrors the [SessionStore] pattern (plain [SharedPreferences], reactive [StateFlow]). The
 * default is [ThemeMode.LIGHT] — a member checking a book in daylight wants a calm white
 * surface, so dark is opt-in (see PRODUCT.md / DESIGN.md).
 */
class ThemeStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    private fun read(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: ThemeMode.LIGHT.name) }
            .getOrDefault(ThemeMode.LIGHT)

    private companion object {
        const val FILE_NAME = "pinakes_theme_prefs"
        const val KEY_MODE = "theme_mode"
    }
}
