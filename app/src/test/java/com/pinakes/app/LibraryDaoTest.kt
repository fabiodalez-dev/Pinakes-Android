package com.pinakes.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pinakes.app.data.local.AppDatabase
import com.pinakes.app.data.local.CachedLoan
import com.pinakes.app.data.local.LibraryDao
import com.pinakes.app.data.local.SyncMeta
import com.pinakes.app.data.local.SyncMetaDao
import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toLoansData
import com.pinakes.app.data.local.toReservationItem
import com.pinakes.app.data.local.toWishlistItem
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.data.model.LoansData
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.data.model.WishlistItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Offline copies of the personal lists and the sync bookkeeping (Room v2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LibraryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LibraryDao
    private lateinit var meta: SyncMetaDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.libraryDao()
        meta = db.syncMetaDao()
    }

    @After fun teardown() = db.close()

    private val loans = LoansData(
        pending = listOf(LoanItem(id = 3, bookId = 30, title = "Pending", status = "pendente", cancellable = true)),
        active = listOf(
            LoanItem(id = 1, bookId = 10, title = "First", status = "in_corso", dueAt = "2026-11-01"),
            LoanItem(id = 2, bookId = 20, title = "Second", status = "in_ritardo", dueAttention = true),
        ),
        history = listOf(LoanItem(id = 4, bookId = 40, title = "Old", status = "restituito", returnedAt = "2026-01-01")),
    )

    @Test fun loansRoundTripPerBucketInServerOrder() = runBlocking {
        dao.replaceLoans(CachedLoan.LOAN_BUCKETS, loans.toCached())
        val back = dao.loans(CachedLoan.LOAN_BUCKETS).toLoansData()
        assertEquals(loans, back)
    }

    @Test fun replacingLoansKeepsReservations() = runBlocking {
        dao.replaceLoans(listOf(CachedLoan.RESERVATION), listOf(ReservationItem(id = 9, bookId = 90, title = "R", queuePosition = 2).toCached(0)))
        dao.replaceLoans(CachedLoan.LOAN_BUCKETS, loans.toCached())
        dao.replaceLoans(CachedLoan.LOAN_BUCKETS, LoansData().toCached())
        assertTrue(dao.loans(CachedLoan.LOAN_BUCKETS).isEmpty())
        val r = dao.loans(listOf(CachedLoan.RESERVATION)).single().toReservationItem()
        assertEquals(2, r.queuePosition)
    }

    @Test fun sameIdInDifferentBucketsDoesNotCollide() = runBlocking {
        dao.replaceLoans(listOf(CachedLoan.ACTIVE, CachedLoan.RESERVATION), listOf(
            LoanItem(id = 1, title = "Loan").toCached(CachedLoan.ACTIVE, 0),
            ReservationItem(id = 1, title = "Reservation").toCached(0),
        ))
        assertEquals(2, dao.loans(listOf(CachedLoan.ACTIVE, CachedLoan.RESERVATION)).size)
    }

    @Test fun wishlistReplaceKeepsOrder() = runBlocking {
        val items = listOf(WishlistItem(bookId = 5, title = "B"), WishlistItem(bookId = 2, title = "A", copiesAvailable = 1))
        dao.replaceWishlist(items.mapIndexed { i, w -> w.toCached(i) })
        assertEquals(items, dao.wishlist().map { it.toWishlistItem() })
        dao.replaceWishlist(emptyList())
        assertTrue(dao.wishlist().isEmpty())
    }

    @Test fun clearAllDropsEverythingPersonal() = runBlocking {
        dao.replaceLoans(CachedLoan.LOAN_BUCKETS, loans.toCached())
        dao.replaceWishlist(listOf(WishlistItem(bookId = 1, title = "W").toCached(0)))
        dao.clearAll()
        assertTrue(dao.loans(CachedLoan.LOAN_BUCKETS + CachedLoan.RESERVATION).isEmpty())
        assertTrue(dao.wishlist().isEmpty())
    }

    @Test fun syncMetaStoresAndReplacesValues() = runBlocking {
        assertNull(meta.get(SyncMeta.CATALOG_SERVER_TIME))
        meta.put(SyncMeta(SyncMeta.CATALOG_SERVER_TIME, "2026-10-13 10:00:00"))
        meta.put(SyncMeta(SyncMeta.CATALOG_SERVER_TIME, "2026-10-13 11:00:00"))
        assertEquals("2026-10-13 11:00:00", meta.get(SyncMeta.CATALOG_SERVER_TIME))
        meta.clear()
        assertNull(meta.get(SyncMeta.CATALOG_SERVER_TIME))
    }
}
