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

/** Extra edge cases for the offline-catalogue Room cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDaoEdgeTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CatalogDao

    private fun book(id: Int) = CachedBook(id = id, title = "Book $id", copiesTotal = 1, copiesAvailable = 1, loanableNow = true)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.catalogDao()
    }

    @After fun teardown() = db.close()

    @Test fun replaceAllWithEmptyListClears() = runBlocking {
        dao.upsertAll(listOf(book(1), book(2)))
        dao.replaceAll(emptyList())
        assertEquals(0, dao.count())
        assertEquals(emptyList<CachedBook>(), dao.observeNewest(40).first())
    }

    @Test fun replaceAllTwiceKeepsOnlyTheLatestSnapshot() = runBlocking {
        dao.replaceAll(listOf(book(1), book(2), book(3)))
        dao.replaceAll(listOf(book(7)))
        assertEquals(listOf(7), dao.observeNewest(40).first().map { it.id })
        assertEquals(1, dao.count())
    }

    @Test fun deleteIdsWithEmptyListIsANoOp() = runBlocking {
        dao.upsertAll(listOf(book(1)))
        dao.applyDelta(upserts = emptyList(), deleteIds = emptyList())
        assertEquals(1, dao.count())
    }

    @Test fun largeCatalogueReplacesInChunks() = runBlocking {
        // Larger than the 500-row chunk: the whole snapshot still lands in one transaction.
        val books = (1..1_250).map { book(it) }
        dao.replaceAll(books)
        assertEquals(1_250, dao.count())
        dao.applyDelta(upserts = emptyList(), deleteIds = (1..1_100).toList())
        assertEquals(150, dao.count())
    }

    @Test fun emptyQueryListsAllAndUnknownQueryListsNone() = runBlocking {
        dao.upsertAll(listOf(book(1)))
        // An empty query lists everything; a query with no match lists nothing.
        assertEquals(1, dao.search("", "", "", "", "", false, "title_asc", 10, 0).size)
        assertEquals(0, dao.search("zzz", "", "", "", "", false, "title_asc", 10, 0).size)
    }
}
