package com.pinakes.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pinakes.app.data.model.Availability
import com.pinakes.app.data.model.AuthorRef
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.BookLocation
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.GenreLabel
import com.pinakes.app.data.model.SyncBook

/**
 * One catalogue title cached locally for offline browsing (Room schema v2).
 *
 * v1 held only the first 40 summaries in server order (`position`). v2 holds the whole
 * catalogue, kept current by the delta sync (`GET /catalog/sync`), plus the fields an offline
 * detail page needs. Ordering is `updatedAt DESC, id DESC` (newest first) or by title.
 * [updatedAt] is the server's `Y-m-d H:i:s` string, which sorts chronologically as text;
 * rows cached from a plain search page (older servers) keep it empty. [searchKey] (schema v3)
 * makes the offline search accent- and case-insensitive for every script.
 */
@Entity(
    tableName = "cached_books",
    indices = [Index("title"), Index("updatedAt")],
)
data class CachedBook(
    @PrimaryKey val id: Int,
    val title: String,
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val language: String? = null,
    val mediaType: String? = null,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val ean: String? = null,
    val pages: Int? = null,
    val description: String? = null,
    val locationLabel: String? = null,
    val coverUrl: String? = null,
    val copiesTotal: Int = 0,
    val copiesAvailable: Int = 0,
    val loanableNow: Boolean = false,
    val isWanted: Boolean = false,
    val updatedAt: String = "",
    /** Folded text the offline search matches against (see [SearchText]); derived, never sent. */
    val searchKey: String = SearchText.key(title, subtitle, author, publisher, isbn13, isbn10, ean),
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

/** A summary from a search page: no offline-only fields, no change timestamp. */
fun BookSummary.toCached(): CachedBook = CachedBook(
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

fun SyncBook.toCached(): CachedBook = CachedBook(
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
    isbn10 = isbn10,
    ean = ean,
    pages = pages,
    description = description,
    locationLabel = locationLabel,
    coverUrl = coverUrl,
    copiesTotal = copiesTotal,
    copiesAvailable = copiesAvailable,
    loanableNow = loanableNow || copiesAvailable > 0,
    isWanted = isWanted,
    updatedAt = updatedAt.orEmpty(),
)

/**
 * A summary-level book page built from the cache when the server is unreachable. Personal
 * history, digital files, reviews and the circulation calendar need the server and are left
 * empty; the screen shows that actions are unavailable offline.
 */
fun CachedBook.toOfflineDetail(): BookDetail = BookDetail(
    id = id,
    title = title,
    subtitle = subtitle,
    year = year,
    language = language,
    pages = pages,
    description = description,
    isbn13 = isbn13,
    isbn10 = isbn10,
    ean = ean,
    mediaType = mediaType,
    coverUrl = coverUrl,
    genre = genre?.let { GenreLabel(name = it) },
    publisher = publisher,
    // The cached label is the server's joined author string; it stays one entry rather than
    // being split on commas, which would break "Surname, Name" forms.
    authors = listOfNotNull(author?.takeIf { it.isNotBlank() }?.let { AuthorRef(name = it) }),
    availability = Availability(
        copiesTotal = copiesTotal,
        copiesAvailable = copiesAvailable,
        loanableNow = loanableNow,
    ),
    location = locationLabel?.takeIf { it.isNotBlank() }?.let { BookLocation(label = it) },
)
