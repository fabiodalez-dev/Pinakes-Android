package com.pinakes.app.di

import android.content.Context
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CatalogDao
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.repository.BookClubRepository
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.data.repository.MessagesRepository
import com.pinakes.app.data.repository.NotificationsRepository
import com.pinakes.app.data.repository.ProfileRepository
import com.pinakes.app.data.repository.ReviewsRepository
import com.pinakes.app.data.repository.WishlistRepository
import com.pinakes.app.data.store.FeatureStore
import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.data.store.ThemeStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The single Hilt module: builds the app-wide singletons (stores, network, DB, repositories)
 * — the same graph the old manual ServiceLocator used to construct, now owned by Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun session(@ApplicationContext context: Context): SessionStore = SessionStore(context)

    @Provides @Singleton
    fun theme(@ApplicationContext context: Context): ThemeStore = ThemeStore(context)

    @Provides @Singleton
    fun features(@ApplicationContext context: Context): FeatureStore = FeatureStore(context)

    @Provides @Singleton
    fun network(session: SessionStore): NetworkModule = NetworkModule(session)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.get(context)

    @Provides @Singleton
    fun catalogDao(database: AppDatabase): CatalogDao = database.catalogDao()

    @Provides @Singleton
    fun catalogRepository(network: NetworkModule, dao: CatalogDao): CatalogRepository =
        CatalogRepository(network, dao)

    @Provides @Singleton
    fun bookClubRepository(network: NetworkModule, features: FeatureStore, session: SessionStore): BookClubRepository =
        BookClubRepository(network, features, session)

    @Provides @Singleton
    fun authRepository(
        network: NetworkModule,
        session: SessionStore,
        features: FeatureStore,
        bookClub: BookClubRepository,
        catalog: CatalogRepository,
    ): AuthRepository = AuthRepository(network, session, features, bookClub, catalog)

    @Provides @Singleton
    fun libraryRepository(network: NetworkModule): LibraryRepository = LibraryRepository(network)

    @Provides @Singleton
    fun wishlistRepository(network: NetworkModule): WishlistRepository = WishlistRepository(network)

    @Provides @Singleton
    fun reviewsRepository(network: NetworkModule): ReviewsRepository = ReviewsRepository(network)

    @Provides @Singleton
    fun profileRepository(network: NetworkModule): ProfileRepository = ProfileRepository(network)

    @Provides @Singleton
    fun notificationsRepository(network: NetworkModule): NotificationsRepository = NotificationsRepository(network)

    @Provides @Singleton
    fun messagesRepository(network: NetworkModule): MessagesRepository = MessagesRepository(network)
}
