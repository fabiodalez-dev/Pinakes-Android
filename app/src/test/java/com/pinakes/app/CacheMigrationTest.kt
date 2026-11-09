package com.pinakes.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CachedLoan
import com.pinakes.app.data.local.toLoansData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Upgrading the offline cache keeps what v3 stored. The v3 schema below is the one Room generated
 * for version 3 and never changes; Room validates the migrated tables against the current
 * entities, so a column added to an entity without a migration fails here instead of on a phone.
 * No destructive fallback in this builder: a missing migration is an error, not an empty cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CacheMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var file: File

    @Before fun setup() {
        file = context.getDatabasePath("migration-test.db")
        file.parentFile?.mkdirs()
        file.delete()
    }

    @After fun teardown() {
        file.delete()
    }

    private fun createV3() {
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        V3_SCHEMA.forEach(raw::execSQL)
        raw.execSQL("INSERT INTO cached_loans (bucket,id,position,bookId,title,status,dueAt,dueAttention,cancellable) VALUES ('active',7,0,70,'Kept','da_ritirare','2030-12-30',0,1)")
        raw.execSQL("INSERT INTO sync_meta (`key`,`value`) VALUES ('watermark','w3')")
        raw.version = 3
        raw.close()
    }

    @Test fun v3RowsSurviveAndTheNewCalendarFieldsStartEmpty() = runBlocking {
        createV3()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val loan = db.libraryDao().loans(CachedLoan.LOAN_BUCKETS).toLoansData().active.single()
            assertEquals("Kept", loan.title)
            assertEquals("2030-12-30", loan.dueAt)
            assertNull(loan.effectiveDueAt)
            assertNull(loan.isOverdue)
            assertNull(loan.pickupDeadline)
            assertEquals("w3", db.syncMetaDao().get("watermark"))
            assertEquals(4, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private companion object {
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `cached_books` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT, `author` TEXT, `publisher` TEXT, `genre` TEXT, `year` INTEGER, `language` TEXT, `mediaType` TEXT, `isbn13` TEXT, `isbn10` TEXT, `ean` TEXT, `pages` INTEGER, `description` TEXT, `locationLabel` TEXT, `coverUrl` TEXT, `copiesTotal` INTEGER NOT NULL, `copiesAvailable` INTEGER NOT NULL, `loanableNow` INTEGER NOT NULL, `isWanted` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, `searchKey` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_cached_books_title` ON `cached_books` (`title`)",
            "CREATE INDEX IF NOT EXISTS `index_cached_books_updatedAt` ON `cached_books` (`updatedAt`)",
            "CREATE TABLE IF NOT EXISTS `cached_loans` (`bucket` TEXT NOT NULL, `id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `bookId` INTEGER NOT NULL, `title` TEXT NOT NULL, `coverUrl` TEXT, `status` TEXT NOT NULL, `statusLabel` TEXT, `requestedAt` TEXT, `loanedAt` TEXT, `dueAt` TEXT, `dueAttention` INTEGER NOT NULL, `returnedAt` TEXT, `renewals` INTEGER, `cancellable` INTEGER NOT NULL, `queuePosition` INTEGER, `requestedFrom` TEXT, `requestedTo` TEXT, `reservedAt` TEXT, `expiresAt` TEXT, PRIMARY KEY(`bucket`, `id`))",
            "CREATE TABLE IF NOT EXISTS `cached_wishlist` (`bookId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `year` INTEGER, `coverUrl` TEXT, `copiesAvailable` INTEGER NOT NULL, `loanableNow` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
            "CREATE TABLE IF NOT EXISTS `sync_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v3')",
        )
    }
}
