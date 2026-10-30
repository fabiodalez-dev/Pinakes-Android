package com.pinakes.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CachedBook
import com.pinakes.app.data.local.CatalogDao
import com.pinakes.app.data.local.SearchText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The offline catalogue (Room v2). Runs on the JVM via Robolectric's SQLite. */
@RunWith(RobolectricTestRunner::class)
// API 34: Robolectric 4.13 supports up to API 34 (app compiles against 35).
// Plain Application: skip PinakesApplication.onCreate() (it builds EncryptedSharedPreferences
// via the AndroidKeyStore, which isn't available under Robolectric).
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CatalogDao

    private fun book(
        id: Int,
        title: String = "Book $id",
        updatedAt: String = "",
        author: String? = null,
        publisher: String? = null,
        isbn13: String? = null,
        language: String? = null,
        genre: String? = null,
        available: Int = 1,
    ) = CachedBook(
        id = id, title = title, author = author, publisher = publisher, isbn13 = isbn13,
        language = language, genre = genre, copiesTotal = 1, copiesAvailable = available,
        loanableNow = available > 0, updatedAt = updatedAt,
    )

    private suspend fun search(
        q: String = "", author: String = "", publisher: String = "", language: String = "",
        genre: String = "", availableOnly: Boolean = false, sort: String = "title_asc",
        limit: Int = 50, offset: Int = 0,
    ) = dao.search(
        SearchText.queryPattern(q), SearchText.filterPattern(author), SearchText.filterPattern(publisher),
        language, genre, availableOnly, sort, limit, offset,
    ).map { it.id }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.catalogDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun emptyByDefault() = runBlocking {
        assertEquals(0, dao.count())
        assertEquals(emptyList<CachedBook>(), dao.observeNewest(40).first())
    }

    @Test
    fun upsertAllReplacesById() = runBlocking {
        dao.upsertAll(listOf(book(1, title = "old")))
        dao.upsertAll(listOf(book(1, title = "new")))
        assertEquals(1, dao.count())
        assertEquals("new", dao.byId(1)?.title)
    }

    @Test
    fun deleteIdsRemovesOnlyThoseRows() = runBlocking {
        dao.upsertAll(listOf(book(1), book(2), book(3)))
        dao.deleteIds(listOf(1, 3, 99))
        assertEquals(listOf(2), search())
    }

    @Test
    fun byIdIsNullForUnknown() = runBlocking {
        assertNull(dao.byId(5))
    }

    @Test
    fun observeNewestOrdersByChangeTimeThenId() = runBlocking {
        dao.upsertAll(
            listOf(
                book(1, updatedAt = "2026-01-01 10:00:00"),
                book(2, updatedAt = "2026-03-01 10:00:00"),
                book(3, updatedAt = "2026-03-01 10:00:00"),
                book(4, updatedAt = ""),
            )
        )
        assertEquals(listOf(3, 2, 1, 4), dao.observeNewest(10).first().map { it.id })
        assertEquals(listOf(3, 2), dao.observeNewest(2).first().map { it.id })
    }

    @Test
    fun replaceAllSwapsTheSnapshotAtomically() = runBlocking {
        dao.upsertAll(listOf(book(1), book(2)))
        dao.replaceAll(listOf(book(9)))
        assertEquals(listOf(9), search())
    }

    @Test
    fun applyDeltaUpsertsAndDeletesTogether() = runBlocking {
        dao.upsertAll(listOf(book(1), book(2, title = "before")))
        dao.applyDelta(upserts = listOf(book(2, title = "after"), book(3)), deleteIds = listOf(1))
        assertEquals(listOf(2, 3), search().sorted())
        assertEquals("after", dao.byId(2)?.title)
    }

    @Test
    fun searchMatchesTitleAuthorPublisherAndIsbn() = runBlocking {
        dao.upsertAll(
            listOf(
                book(1, title = "Orlando", author = "Virginia Woolf"),
                book(2, title = "Ulysses", author = "James Joyce", publisher = "Shakespeare and Co"),
                book(3, title = "Dubliners", isbn13 = "9780140186475"),
            )
        )
        assertEquals(listOf(1), search(q = "orlan"))       // case-insensitive
        assertEquals(listOf(1), search(q = "woolf"))
        assertEquals(listOf(2), search(q = "shakespeare"))
        assertEquals(listOf(3), search(q = "0140186"))
        assertEquals(emptyList<Int>(), search(q = "proust"))
    }

    @Test
    fun searchFiltersByLanguageGenreAuthorAndAvailability() = runBlocking {
        dao.upsertAll(
            listOf(
                book(1, title = "A", language = "Italiano", genre = "Romanzo", author = "Calvino", available = 1),
                book(2, title = "B", language = "Deutsch", genre = "Romanzo", author = "Mann", available = 0),
                book(3, title = "C", language = "Italiano", genre = "Poesia", author = "Montale", available = 0),
            )
        )
        assertEquals(listOf(1, 3), search(language = "Italiano"))
        assertEquals(listOf(1, 2), search(genre = "Romanzo"))
        assertEquals(listOf(2), search(author = "mann"))
        assertEquals(listOf(1), search(availableOnly = true))
        assertEquals(listOf(1), search(language = "Italiano", genre = "Romanzo"))
    }

    @Test
    fun searchSortsAndPages() = runBlocking {
        dao.upsertAll(
            listOf(
                book(1, title = "beta", updatedAt = "2026-01-02 00:00:00"),
                book(2, title = "Alpha", updatedAt = "2026-01-01 00:00:00"),
                book(3, title = "gamma", updatedAt = "2026-01-03 00:00:00"),
            )
        )
        assertEquals(listOf(2, 1, 3), search(sort = "title_asc"))
        assertEquals(listOf(3, 1, 2), search(sort = "title_desc"))
        assertEquals(listOf(3, 1, 2), search(sort = "newest"))
        assertEquals(listOf(2, 1), search(limit = 2))
        assertEquals(listOf(3), search(limit = 2, offset = 2))
    }

    @Test
    fun clearEmptiesTheCache() = runBlocking {
        dao.upsertAll(listOf(book(1)))
        dao.clear()
        assertEquals(0, dao.count())
    }

    @Test
    fun searchIgnoresAccentsAndCaseBeyondAscii() = runBlocking {
        dao.upsertAll(
            listOf(
                book(1, title = "La città invisibile"),
                book(2, title = "Über den Fluss", author = "Kästner"),
                book(3, title = "Ærø"),
            )
        )
        assertEquals(listOf(1), search(q = "CITTA"))
        assertEquals(listOf(1), search(q = "Città"))
        assertEquals(listOf(2), search(q = "uber"))
        assertEquals(listOf(2), search(q = "kastner"))
        assertEquals(listOf(3), search(q = "ærø"))   // not decomposable: still case-folded
        assertEquals(listOf(3), search(q = "ÆRØ"))
    }

    @Test
    fun likeWildcardsInTheQueryMatchThemselves() = runBlocking {
        dao.upsertAll(listOf(book(1, title = "100% Cotone"), book(2, title = "1000 Cotone"), book(3, title = "snake_case"), book(4, title = "snakeXcase")))
        assertEquals(listOf(1), search(q = "100%"))
        assertEquals(listOf(3), search(q = "snake_case"))
        assertEquals(emptyList<Int>(), search(q = "\\"))
        assertEquals(listOf(3), search(author = "", q = "e_c"))
    }

    @Test
    fun isbnQueryWithDashesOrSpacesMatchesTheCompactIdentifier() = runBlocking {
        dao.upsertAll(listOf(book(1, isbn13 = "9788804668237"), book(2, isbn13 = "978-0-14-018647-5")))
        assertEquals(listOf(1), search(q = "978-88-04-66823-7"))
        assertEquals(listOf(1), search(q = "978 88 04"))
        assertEquals(listOf(2), search(q = "9780140186475"))   // stored with dashes, still found
    }

    @Test
    fun authorFilterEscapesWildcards() = runBlocking {
        dao.upsertAll(listOf(book(1, author = "A_B"), book(2, author = "AxB")))
        assertEquals(listOf(1), search(author = "A_B"))
    }

    @Test
    fun quotedQueriesAndFiltersFindTheirWordsOffline() = runBlocking {
        // Pinakes #501: quotes ask the server for the exact spelling; offline they are dropped.
        dao.upsertAll(
            listOf(
                book(1, title = "Il barone rampante", author = "Italo Calvino", publisher = "Einaudi"),
                book(2, title = "Altro", author = "Thomas Mann", publisher = "Fischer"),
            ),
        )
        assertEquals(listOf(1), search(q = "\"barone rampante\""))
        assertEquals(listOf(1), search(author = "\"Italo Calvino\""))
        assertEquals(listOf(1), search(publisher = "\u201CEinaudi\u201D"))
    }
}
