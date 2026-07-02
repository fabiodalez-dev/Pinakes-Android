package com.pinakes.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    /** Reactive cached catalog snapshot, in the server's order. */
    @Query("SELECT * FROM cached_books ORDER BY position ASC")
    fun observeAll(): Flow<List<CachedBook>>

    @Query("SELECT COUNT(*) FROM cached_books")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<CachedBook>)

    @Query("DELETE FROM cached_books")
    suspend fun clear()

    /** Atomically replace the whole cache with a fresh catalog snapshot. */
    @Transaction
    suspend fun replaceAll(books: List<CachedBook>) {
        clear()
        insertAll(books)
    }
}
