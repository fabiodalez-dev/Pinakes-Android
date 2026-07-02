package com.pinakes.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CachedBook
import com.pinakes.app.data.local.CatalogDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The offline catalog cache (Room). Runs on the JVM via Robolectric's SQLite. */
@RunWith(RobolectricTestRunner::class)
// API 34: Robolectric 4.13 supports up to API 34 (app compiles against 35).
// Plain Application: skip PinakesApplication.onCreate() (it builds EncryptedSharedPreferences
// via the AndroidKeyStore, which isn't available under Robolectric).
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CatalogDao

    private fun book(id: Int, position: Int, title: String = "Book $id") = CachedBook(
        id = id, position = position, title = title, subtitle = null, author = null,
        publisher = null, genre = null, year = null, language = null, mediaType = null,
        isbn13 = null, coverUrl = null, copiesTotal = 1, copiesAvailable = 1, loanableNow = true,
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.catalogDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun emptyByDefault() = runBlocking {
        assertEquals(0, dao.count())
        assertEquals(emptyList<CachedBook>(), dao.observeAll().first())
    }

    @Test
    fun insertAndObserve() = runBlocking {
        dao.insertAll(listOf(book(1, 0), book(2, 1)))
        assertEquals(2, dao.count())
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun observeIsOrderedByPosition() = runBlocking {
        dao.insertAll(listOf(book(2, 1), book(1, 0), book(3, 2)))
        assertEquals(listOf(1, 2, 3), dao.observeAll().first().map { it.id })
    }

    @Test
    fun insertReplacesOnConflict() = runBlocking {
        dao.insertAll(listOf(book(1, 0, title = "old")))
        dao.insertAll(listOf(book(1, 0, title = "new")))
        assertEquals("new", dao.observeAll().first().single().title)
    }

    @Test
    fun replaceAllSwapsTheSnapshotAtomically() = runBlocking {
        dao.insertAll(listOf(book(1, 0), book(2, 1)))
        dao.replaceAll(listOf(book(9, 0)))
        assertEquals(listOf(9), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.count())
    }

    @Test
    fun clearEmptiesTheCache() = runBlocking {
        dao.insertAll(listOf(book(1, 0)))
        dao.clear()
        assertEquals(0, dao.count())
    }
}
