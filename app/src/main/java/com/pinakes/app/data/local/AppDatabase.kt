package com.pinakes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local cache database. V4 adds the usable return day, the server lateness and the pickup deadline
 * of a loan while keeping data cached by v3.
 * Older schemas can be rebuilt from the server; clearing the sync watermark triggers a full sync.
 * The database is excluded from backups because it belongs to one instance and one user.
 */
@Database(
    entities = [CachedBook::class, CachedLoan::class, CachedWishlistItem::class, SyncMeta::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun libraryDao(): LibraryDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val FILE_NAME = "pinakes-cache.db"

        /** v4 keeps the v3 rows and adds the server calendar facts of a loan, null until the next refresh. */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_loans ADD COLUMN effectiveDueAt TEXT")
                db.execSQL("ALTER TABLE cached_loans ADD COLUMN isOverdue INTEGER")
                db.execSQL("ALTER TABLE cached_loans ADD COLUMN pickupDeadline TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                ).addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
