package com.pinakes.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The signed-in user's loans, reservations and wishlist, cached for offline reading. */
@Dao
interface LibraryDao {

    @Query("SELECT * FROM cached_loans ORDER BY bucket ASC, position ASC")
    fun observeLoans(): Flow<List<CachedLoan>>

    @Query("SELECT * FROM cached_loans WHERE bucket IN (:buckets) ORDER BY bucket ASC, position ASC")
    suspend fun loans(buckets: List<String>): List<CachedLoan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoans(rows: List<CachedLoan>)

    @Query("DELETE FROM cached_loans WHERE bucket IN (:buckets)")
    suspend fun deleteBuckets(buckets: List<String>)

    /** Replace the given buckets with [rows] atomically (other buckets are untouched). */
    @Transaction
    suspend fun replaceLoans(buckets: List<String>, rows: List<CachedLoan>) {
        deleteBuckets(buckets)
        insertLoans(rows)
    }

    /** Drop one loan/reservation from the given buckets (after a successful cancel). */
    @Query("DELETE FROM cached_loans WHERE id = :id AND bucket IN (:buckets)")
    suspend fun deleteLoan(id: Int, buckets: List<String>)

    @Query("DELETE FROM cached_wishlist WHERE bookId = :bookId")
    suspend fun deleteWishlistItem(bookId: Int)

    @Query("SELECT * FROM cached_wishlist ORDER BY position ASC")
    fun observeWishlist(): Flow<List<CachedWishlistItem>>

    @Query("SELECT * FROM cached_wishlist ORDER BY position ASC")
    suspend fun wishlist(): List<CachedWishlistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlist(rows: List<CachedWishlistItem>)

    @Query("DELETE FROM cached_wishlist")
    suspend fun clearWishlist()

    @Transaction
    suspend fun replaceWishlist(rows: List<CachedWishlistItem>) {
        clearWishlist()
        insertWishlist(rows)
    }

    @Query("DELETE FROM cached_loans")
    suspend fun clearLoans()

    /** Drop everything personal: called on sign-out and before a different user signs in. */
    @Transaction
    suspend fun clearAll() {
        clearLoans()
        clearWishlist()
    }
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: SyncMeta)

    @Query("DELETE FROM sync_meta")
    suspend fun clear()
}
