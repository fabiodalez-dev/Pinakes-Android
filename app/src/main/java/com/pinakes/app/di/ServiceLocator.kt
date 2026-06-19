package com.pinakes.app.di

import android.content.Context
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.data.repository.MessagesRepository
import com.pinakes.app.data.repository.NotificationsRepository
import com.pinakes.app.data.repository.ProfileRepository
import com.pinakes.app.data.repository.WishlistRepository
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.data.store.ThemeStore

/**
 * Manual dependency container (no DI framework, per the build spec). Holds the single
 * [SessionStore] and [NetworkModule] and lazily builds the repositories on top of them.
 *
 * Instantiated once in [com.pinakes.app.PinakesApplication] and read by ViewModels.
 */
class ServiceLocator(context: Context) {

    val session: SessionStore = SessionStore(context.applicationContext)

    val theme: ThemeStore = ThemeStore(context.applicationContext)

    /** Reactive instance feature flags (CATALOGUE-ONLY MODE gating). App-wide. */
    val features: FeatureStore = FeatureStore(context.applicationContext)

    val network: NetworkModule = NetworkModule(session)

    val authRepository: AuthRepository by lazy { AuthRepository(network, session, features) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(network) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(network) }
    val wishlistRepository: WishlistRepository by lazy { WishlistRepository(network) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(network) }
    val notificationsRepository: NotificationsRepository by lazy { NotificationsRepository(network) }
    val messagesRepository: MessagesRepository by lazy { MessagesRepository(network) }
}
