package com.pinakes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local cache database. Everything in it can be rebuilt from the server, so schema changes use
 * destructive migration: v1 (40-row snapshot) → v2 (whole catalogue + loans + wishlist + sync
 * bookkeeping) simply starts empty and the next sync refills it. Excluded from backups
 * (`backup_rules.xml`, `data_extraction_rules.xml`): it belongs to one instance and one user.
 */
@Database(
    entities = [CachedBook::class, CachedLoan::class, CachedWishlistItem::class, SyncMeta::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun libraryDao(): LibraryDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val FILE_NAME = "pinakes-cache.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
