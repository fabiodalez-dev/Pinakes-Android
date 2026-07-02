package com.pinakes.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pinakes.app.data.model.BookSummary

/**
 * A catalog list item cached locally so the catalog renders instantly and works
 * offline (no per-open network round-trip for the list or its covers). Mirrors
 * [BookSummary]; [position] preserves the server's ordering for the snapshot.
 */
@Entity(tableName = "cached_books")
data class CachedBook(
    @PrimaryKey val id: Int,
    val position: Int,
    val title: String,
    val subtitle: String?,
    val author: String?,
    val publisher: String?,
    val genre: String?,
    val year: Int?,
    val language: String?,
    val mediaType: String?,
    val isbn13: String?,
    val coverUrl: String?,
    val copiesTotal: Int,
    val copiesAvailable: Int,
    val loanableNow: Boolean,
)

fun CachedBook.toSummary(): BookSummary = BookSummary(
    id = id,
    title = title,
    subtitle = subtitle,
    author = author,
    publisher = publisher,
    genre = genre,
    year = year,
    language = language,
    mediaType = mediaType,
    isbn13 = isbn13,
    coverUrl = coverUrl,
    copiesTotal = copiesTotal,
    copiesAvailable = copiesAvailable,
    loanableNow = loanableNow,
)

fun BookSummary.toCached(position: Int): CachedBook = CachedBook(
    id = id,
    position = position,
    title = title,
    subtitle = subtitle,
    author = author,
    publisher = publisher,
    genre = genre,
    year = year,
    language = language,
    mediaType = mediaType,
    isbn13 = isbn13,
    coverUrl = coverUrl,
    copiesTotal = copiesTotal,
    copiesAvailable = copiesAvailable,
    loanableNow = loanableNow,
)
