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

/** Extra edge cases for the offline-catalog Room cache (touched: offline cache #9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDaoEdgeTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CatalogDao

    private fun book(id: Int, position: Int) = CachedBook(
        id = id, position = position, title = "Book $id", subtitle = null, author = null,
        publisher = null, genre = null, year = null, language = null, mediaType = null,
        isbn13 = null, coverUrl = null, copiesTotal = 1, copiesAvailable = 1, loanableNow = true,
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.catalogDao()
    }

    @After fun teardown() = db.close()

    @Test fun replaceAllWithEmptyListClears() = runBlocking {
        dao.insertAll(listOf(book(1, 0), book(2, 1)))
        dao.replaceAll(emptyList())
        assertEquals(0, dao.count())
        assertEquals(emptyList<CachedBook>(), dao.observeAll().first())
    }

    @Test fun observeOrdersByPositionNotByInsertionOrId() = runBlocking {
        // Insert out of order with non-sequential positions; observeAll sorts by position.
        dao.insertAll(listOf(book(10, 5), book(20, 2), book(30, 9)))
        assertEquals(listOf(20, 10, 30), dao.observeAll().first().map { it.id })
    }

    @Test fun replaceAllTwiceKeepsOnlyTheLatestSnapshot() = runBlocking {
        dao.replaceAll(listOf(book(1, 0), book(2, 1), book(3, 2)))
        dao.replaceAll(listOf(book(7, 0)))
        assertEquals(listOf(7), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.count())
    }

    @Test fun countReflectsClear() = runBlocking {
        dao.insertAll(listOf(book(1, 0), book(2, 1), book(3, 2)))
        assertEquals(3, dao.count())
        dao.clear()
        assertEquals(0, dao.count())
    }

    @Test fun largeBatchRoundTripsInPositionOrder() = runBlocking {
        val books = (0 until 50).map { book(id = 1000 - it, position = it) }
        dao.replaceAll(books)
        val out = dao.observeAll().first()
        assertEquals(50, out.size)
        assertEquals((0 until 50).toList(), out.map { it.position })
    }
}
