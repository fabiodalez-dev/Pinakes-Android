package com.pinakes.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.pinakes.app.ui.common.LocalServices
import kotlinx.coroutines.launch
import com.pinakes.app.ui.navigation.PinakesNavHost
import com.pinakes.app.ui.theme.PinakesTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity is required for per-app locales (AppCompatDelegate
// .setApplicationLocales) to be applied to this Activity's configuration.
// @AndroidEntryPoint lets Composables hosted here obtain Hilt ViewModels via hiltViewModel().
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val services = (application as PinakesApplication).services
        // Best-effort re-fetch of /health on app start so a server-side CATALOGUE-ONLY MODE
        // change is picked up. Failure keeps the last-known flags (never blocks the UI).
        if (services.session.hasInstance()) {
            lifecycleScope.launch { services.authRepository.refreshHealth() }
        }
        setContent {
            // Read the persisted theme as state so switching it in Profile applies live.
            val themeMode by services.theme.mode.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalServices provides services) {
                PinakesTheme(mode = themeMode) {
                    PinakesNavHost()
                }
            }
        }
    }
}
