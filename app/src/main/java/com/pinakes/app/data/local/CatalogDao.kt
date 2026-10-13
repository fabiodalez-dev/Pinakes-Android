package com.pinakes.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    /** Newest titles first (sync change time, then id): the offline Home shelf. */
    @Query("SELECT * FROM cached_books ORDER BY updatedAt DESC, id DESC LIMIT :limit")
    fun observeNewest(limit: Int): Flow<List<CachedBook>>

    @Query("SELECT COUNT(*) FROM cached_books")
    suspend fun count(): Int

    @Query("SELECT * FROM cached_books WHERE id = :id")
    suspend fun byId(id: Int): CachedBook?

    /** Insert or replace by id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<CachedBook>)

    @Query("DELETE FROM cached_books WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Int>)

    @Query("DELETE FROM cached_books")
    suspend fun clear()

    /** Atomically replace the whole cache with a fresh catalogue snapshot (full resync). */
    @Transaction
    suspend fun replaceAll(books: List<CachedBook>) {
        clear()
        books.chunked(CHUNK).forEach { upsertAll(it) }
    }

    /** Apply one delta sync in a single transaction. */
    @Transaction
    suspend fun applyDelta(upserts: List<CachedBook>, deleteIds: List<Int>) {
        deleteIds.chunked(CHUNK).forEach { deleteIds(it) }
        upserts.chunked(CHUNK).forEach { upsertAll(it) }
    }

    /**
     * Offline catalogue search. [q] matches title, subtitle, author, publisher and the
     * identifiers (LIKE is case-insensitive for ASCII in SQLite); empty strings disable a
     * filter. [sort] is `title_asc`, `title_desc` or `newest`.
     */
    @Query(
        """
        SELECT * FROM cached_books
        WHERE (:q = '' OR title LIKE '%' || :q || '%' OR subtitle LIKE '%' || :q || '%'
               OR author LIKE '%' || :q || '%' OR publisher LIKE '%' || :q || '%'
               OR isbn13 LIKE '%' || :q || '%' OR isbn10 LIKE '%' || :q || '%' OR ean LIKE '%' || :q || '%')
          AND (:author = '' OR author LIKE '%' || :author || '%')
          AND (:publisher = '' OR publisher LIKE '%' || :publisher || '%')
          AND (:language = '' OR language = :language)
          AND (:genre = '' OR genre = :genre)
          AND (:availableOnly = 0 OR loanableNow = 1 OR copiesAvailable > 0)
        ORDER BY
          CASE WHEN :sort = 'newest' THEN updatedAt END DESC,
          CASE WHEN :sort = 'title_desc' THEN title END COLLATE NOCASE DESC,
          title COLLATE NOCASE ASC,
          id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun search(
        q: String,
        author: String,
        publisher: String,
        language: String,
        genre: String,
        availableOnly: Boolean,
        sort: String,
        limit: Int,
        offset: Int,
    ): List<CachedBook>
}

/** Stay well under SQLite's bound-variable limit on large catalogues. */
private const val CHUNK = 500
