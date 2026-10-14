package com.pinakes.app.ui.common

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * The language the user picked in Profile (AppCompat per-app locale), falling back to the
 * device locale. Read it at formatting time, never cache it in a formatter: the user can
 * switch language while the process keeps running, and a formatter built at start-up would
 * keep the old language (dates stayed "Oct 9, 2026" in an Italian UI).
 */
object AppLocale {
    fun current(): Locale =
        runCatching { AppCompatDelegate.getApplicationLocales()[0] }.getOrNull() ?: Locale.getDefault()
}
