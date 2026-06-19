package com.pinakes.app.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.pinakes.app.di.ServiceLocator

/**
 * Provides the singleton [ServiceLocator] down the Compose tree so ViewModel factories can
 * reach the repositories without a DI framework (the build spec allows manual DI).
 */
val LocalServices = staticCompositionLocalOf<ServiceLocator> {
    error("ServiceLocator not provided")
}
