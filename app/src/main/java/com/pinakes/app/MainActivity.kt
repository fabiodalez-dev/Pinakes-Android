package com.pinakes.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.data.store.ThemeStore
import com.pinakes.app.ui.navigation.PinakesNavHost
import com.pinakes.app.ui.theme.PinakesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

// AppCompatActivity is required for per-app locales (AppCompatDelegate
// .setApplicationLocales) to be applied to this Activity's configuration.
// @AndroidEntryPoint enables Hilt field injection here AND lets hosted Composables
// obtain Hilt ViewModels via hiltViewModel().
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var session: SessionStore
    @Inject lateinit var theme: ThemeStore
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Best-effort re-fetch of /health on app start so a server-side CATALOGUE-ONLY MODE
        // change is picked up. Failure keeps the last-known flags (never blocks the UI).
        if (session.hasInstance()) {
            lifecycleScope.launch { authRepository.refreshHealth() }
        }
        setContent {
            // Read the persisted theme as state so switching it in Profile applies live.
            val themeMode by theme.mode.collectAsStateWithLifecycle()
            PinakesTheme(mode = themeMode) {
                PinakesNavHost()
            }
        }
    }
}
