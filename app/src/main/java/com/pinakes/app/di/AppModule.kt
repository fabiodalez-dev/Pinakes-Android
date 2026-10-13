package com.pinakes.app.di

import android.content.Context
import com.pinakes.app.data.connectivity.ConnectivityObserver
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CatalogDao
import com.pinakes.app.data.local.LibraryDao
import com.pinakes.app.data.local.SyncMetaDao
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.repository.BookClubRepository
import com.pinakes.app.data.repository.CalendarRepository
import com.pinakes.app.data.repository.CatalogRepository
import com.pinakes.app.data.repository.CollectionsSource
import com.pinakes.app.data.repository.CollectionsRepository
import com.pinakes.app.data.repository.LibraryRepository
import com.pinakes.app.data.repository.MessagesRepository
import com.pinakes.app.data.repository.NotificationsRepository
import com.pinakes.app.data.repository.PeriodicalsRepository
import com.pinakes.app.data.repository.ProfileRepository
import com.pinakes.app.data.repository.ReviewsRepository
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.StaffPluginsRepository
import com.pinakes.app.data.repository.StaffRepository
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

    // The app cache dir backs the OkHttp response cache, so the server's ETags actually turn
    // into conditional requests instead of full re-downloads. Android reclaims this directory
    // under storage pressure, which is exactly the right lifetime for it.
    @Provides @Singleton
    fun network(@ApplicationContext context: Context, session: SessionStore): NetworkModule =
        NetworkModule(session, context.cacheDir)

    /** System default-network watcher: screens hide loan affordances the moment it drops. */
    @Provides @Singleton
    fun connectivity(@ApplicationContext context: Context): ConnectivityObserver = ConnectivityObserver(context)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.get(context)

    @Provides @Singleton
    fun catalogDao(database: AppDatabase): CatalogDao = database.catalogDao()

    @Provides @Singleton
    fun libraryDao(database: AppDatabase): LibraryDao = database.libraryDao()

    @Provides @Singleton
    fun syncMetaDao(database: AppDatabase): SyncMetaDao = database.syncMetaDao()

    @Provides @Singleton
    fun catalogRepository(network: NetworkModule, dao: CatalogDao, syncMeta: SyncMetaDao): CatalogRepository =
        CatalogRepository(network, dao, syncMeta)

    @Provides @Singleton
    fun bookClubRepository(network: NetworkModule, features: FeatureStore, session: SessionStore): BookClubRepository =
        BookClubRepository(network, features, session)

    @Provides @Singleton
    fun periodicalsRepository(network: NetworkModule, features: FeatureStore, session: SessionStore): PeriodicalsRepository =
        PeriodicalsRepository(network, features, session)

    @Provides @Singleton
    fun collectionsRepository(network: NetworkModule): CollectionsSource = CollectionsRepository(network)

    @Provides @Singleton
    fun authRepository(
        network: NetworkModule,
        session: SessionStore,
        features: FeatureStore,
        bookClub: BookClubRepository,
        periodicals: PeriodicalsRepository,
        catalog: CatalogRepository,
        libraryDao: LibraryDao,
    ): AuthRepository = AuthRepository(network, session, features, bookClub, periodicals, catalog, libraryDao)

    @Provides @Singleton
    fun libraryRepository(network: NetworkModule, libraryDao: LibraryDao): LibraryRepository =
        LibraryRepository(network, libraryDao)

    @Provides @Singleton
    fun wishlistRepository(network: NetworkModule, libraryDao: LibraryDao): WishlistRepository =
        WishlistRepository(network, libraryDao)

    @Provides @Singleton
    fun staffRepository(network: NetworkModule, session: SessionStore): StaffRepository =
        StaffRepository(network, session)

    @Provides @Singleton
    fun staffCirculationRepository(network: NetworkModule, session: SessionStore): StaffCirculationRepository =
        StaffCirculationRepository(network, session)

    @Provides @Singleton
    fun staffContentRepository(network: NetworkModule, session: SessionStore): StaffContentRepository =
        StaffContentRepository(network, session)

    @Provides @Singleton
    fun staffPluginsRepository(network: NetworkModule, session: SessionStore): StaffPluginsRepository =
        StaffPluginsRepository(network, session)

    /** Staff PDFs/CSVs land in `cache/exports/`, the directory the FileProvider shares. */
    @Provides @Singleton
    fun staffFilesRepository(@ApplicationContext context: Context, network: NetworkModule, session: SessionStore): StaffFilesRepository =
        StaffFilesRepository(network, session, context.cacheDir)

    @Provides @Singleton
    fun calendarRepository(network: NetworkModule): CalendarRepository = CalendarRepository(network)

    @Provides @Singleton
    fun reviewsRepository(network: NetworkModule): ReviewsRepository = ReviewsRepository(network)

    @Provides @Singleton
    fun profileRepository(network: NetworkModule): ProfileRepository = ProfileRepository(network)

    @Provides @Singleton
    fun notificationsRepository(network: NetworkModule): NotificationsRepository = NotificationsRepository(network)

    @Provides @Singleton
    fun messagesRepository(network: NetworkModule): MessagesRepository = MessagesRepository(network)
}
