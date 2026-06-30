package com.pinakes.app.di

import android.app.Application
import com.pinakes.app.PinakesApplication
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — **incremental-migration bridge**. It exposes to Hilt the SAME singleton
 * instances the manual [ServiceLocator] already owns, so a Hilt-injected ViewModel and a
 * ServiceLocator-built one share one instance each (no auth-state / cache desync while the
 * migration is half-done).
 *
 * Once every screen is on Hilt, replace these bridges with real `@Provides` that construct
 * the dependencies (or `@Inject` constructors) and delete [ServiceLocator]. Hilt provides
 * the [Application] binding out of the box (from `@HiltAndroidApp`).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private fun services(app: Application) = (app as PinakesApplication).services

    @Provides @Singleton fun session(app: Application): SessionStore = services(app).session
    @Provides @Singleton fun theme(app: Application): ThemeStore = services(app).theme
    @Provides @Singleton fun features(app: Application): FeatureStore = services(app).features
    @Provides @Singleton fun network(app: Application): NetworkModule = services(app).network

    @Provides @Singleton fun catalogRepository(app: Application): CatalogRepository = services(app).catalogRepository
    @Provides @Singleton fun authRepository(app: Application): AuthRepository = services(app).authRepository
    @Provides @Singleton fun libraryRepository(app: Application): LibraryRepository = services(app).libraryRepository
    @Provides @Singleton fun wishlistRepository(app: Application): WishlistRepository = services(app).wishlistRepository
    @Provides @Singleton fun profileRepository(app: Application): ProfileRepository = services(app).profileRepository
    @Provides @Singleton fun notificationsRepository(app: Application): NotificationsRepository = services(app).notificationsRepository
    @Provides @Singleton fun messagesRepository(app: Application): MessagesRepository = services(app).messagesRepository
}
