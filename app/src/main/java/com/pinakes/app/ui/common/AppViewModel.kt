package com.pinakes.app.ui.common

import androidx.lifecycle.ViewModel
import com.pinakes.app.data.store.BookClubStore
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.data.store.ThemeStore
import com.pinakes.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App-wide reactive state that several Composables need regardless of the current screen:
 * the auth state (start destination), the instance feature flags (catalogue-only gating)
 * and the theme mode. Replaces the old `LocalServices` CompositionLocal — any Composable
 * under an `@AndroidEntryPoint` Activity gets it with `hiltViewModel()`.
 *
 * The underlying stores are Hilt singletons, so every [AppViewModel] instance re-exposes the
 * SAME StateFlows — reads stay consistent across screens and [setThemeMode] is seen everywhere.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionStore,
    featureStore: FeatureStore,
    bookClubStore: BookClubStore,
    private val themeStore: ThemeStore,
) : ViewModel() {

    val authState = session.authState
    val features = featureStore.features
    val themeMode = themeStore.mode

    /** Whether the instance exposes the optional Book Club plugin (gates the Profile entry). */
    val bookClubAvailable = bookClubStore.available

    fun setThemeMode(mode: ThemeMode) = themeStore.setMode(mode)
}
